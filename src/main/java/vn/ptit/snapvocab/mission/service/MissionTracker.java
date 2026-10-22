package vn.ptit.snapvocab.mission.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.UserDailyMissionProgressLog;
import vn.ptit.snapvocab.mission.domain.UserWordCredit;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionProgressLogRepository;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionRepository;
import vn.ptit.snapvocab.mission.repository.UserWordCreditRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.BoosterActivationRepository;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.shop.service.ShopPurchasePolicy;
import vn.ptit.snapvocab.study.service.StudyService;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/**
 * Assignment and progress of Daily Missions (daily_mission.md §4, §6). Both always join the caller's transaction
 * and assume the caller already holds the {@code users} row lock, which serializes every writer of one learner.
 */
@Service
public class MissionTracker {

	private static final Logger LOG = LoggerFactory.getLogger(MissionTracker.class);

	/** Same day boundary as Progress (daily_mission.md §3.1). */
	public static final ZoneId ZONE = ProgressService.ZONE;

	/** Slot → categories, daily_mission.md §2.2. */
	static final List<Set<MissionCategory>> SLOTS = List.of(
			EnumSet.of(MissionCategory.SCAN),
			EnumSet.of(MissionCategory.VOCABULARY),
			EnumSet.of(MissionCategory.FLASHCARD, MissionCategory.SRS),
			EnumSet.of(MissionCategory.QUIZ),
			EnumSet.of(MissionCategory.RETENTION, MissionCategory.SHOP));

	public static final String POOL_INSUFFICIENT_METRIC = "mission.pool.insufficient";

	/** Comma-separated triggers the calling app can render (PD-DM-13). */
	public static final String TRIGGERS_HEADER = "X-Mission-Triggers";

	public enum Assignment {
		EXISTING, ASSIGNED, POOL_INSUFFICIENT
	}

	private final MissionTemplateRepository templateRepository;
	private final UserDailyMissionRepository missionRepository;
	private final UserDailyMissionProgressLogRepository progressLogRepository;
	private final UserWordCreditRepository wordCreditRepository;
	private final UserRepository userRepository;
	private final UserItemRepository userItemRepository;
	private final ShopItemRepository shopItemRepository;
	private final BoosterActivationRepository activationRepository;
	private final StreakFreezeService streakFreezeService;
	private final ObjectProvider<StudyService> studyService;
	private final Counter poolInsufficient;

	public MissionTracker(MissionTemplateRepository templateRepository, UserDailyMissionRepository missionRepository,
			UserDailyMissionProgressLogRepository progressLogRepository, UserWordCreditRepository wordCreditRepository,
			UserRepository userRepository, UserItemRepository userItemRepository, ShopItemRepository shopItemRepository,
			BoosterActivationRepository activationRepository, StreakFreezeService streakFreezeService,
			ObjectProvider<StudyService> studyService, ObjectProvider<MeterRegistry> meterRegistry) {
		this.templateRepository = templateRepository;
		this.missionRepository = missionRepository;
		this.progressLogRepository = progressLogRepository;
		this.wordCreditRepository = wordCreditRepository;
		this.userRepository = userRepository;
		this.userItemRepository = userItemRepository;
		this.shopItemRepository = shopItemRepository;
		this.activationRepository = activationRepository;
		this.streakFreezeService = streakFreezeService;
		// Provider: StudyService itself depends on this tracker.
		this.studyService = studyService;
		// Exported as mission_pool_insufficient_total (daily_mission.md §12); expected to stay 0, alert on any increase.
		this.poolInsufficient = Counter.builder(POOL_INSUFFICIENT_METRIC)
				.description("ensureAssigned results that could not fill 5 slots (catalog misconfigured)")
				.register(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
	}

	public static LocalDate missionDate(Instant instant) {
		return LocalDate.ofInstant(instant, ZONE);
	}

	/**
	 * Gives the learner exactly five missions for {@code date} once; later calls return EXISTING (daily_mission.md §4.2).
	 * Eligibility is evaluated on the state at {@code now}, before the trigger's own mutation (PD-DM-03).
	 * POOL_INSUFFICIENT is a business result: nothing is written and the caller decides (§4.4).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Assignment ensureAssigned(Long userId, LocalDate date, Instant now) {
		if (missionRepository.existsByUserIdAndMissionDate(userId, date)) {
			return Assignment.EXISTING;
		}
		Eligibility eligibility = new Eligibility(userId, now);
		Set<MissionTriggerEvent> renderable = clientTriggers();
		List<MissionTemplate> eligible = templateRepository.findByActiveTrueOrderById().stream()
				.filter(t -> renderable.contains(t.getTriggerEvent()))
				.filter(eligibility)
				.toList();
		List<MissionTemplate> chosen = choose(eligible, new HashSet<>(missionRepository.findCodes(userId, date.minusDays(1))));
		if (chosen == null) {
			poolInsufficient.increment();
			LOG.error("MISSION_POOL_INSUFFICIENT user={} date={}: active catalog cannot fill 5 slots", userId, date);
			return Assignment.POOL_INSUFFICIENT;
		}
		User user = userRepository.getReferenceById(userId);
		for (int i = 0; i < chosen.size(); i++) {
			missionRepository.save(UserDailyMission.assign(user, chosen.get(i), date, i + 1));
		}
		return Assignment.ASSIGNED;
	}

	/**
	 * The five templates in slot order, or null when a slot has no candidate even after the core fallback.
	 * Slots with their own candidates pick first; only then do empty slots fall back to the core templates left, so a
	 * fallback never takes another slot's only template (daily_mission.md §4.2, PD-DM-07).
	 */
	static List<MissionTemplate> choose(List<MissionTemplate> eligible, Set<String> yesterdayCodes) {
		List<List<MissionTemplate>> one = walk(eligible, pool -> List.of(pickFresh(pool, yesterdayCodes)));
		return one.isEmpty() ? null : one.get(0);
	}

	/**
	 * Every set {@link #choose} can return: weights are at least 1 and the yesterday preference only changes odds,
	 * so every branch is possible. Empty when no set can be filled. The Admin outlook computes the same in the browser;
	 * shared vectors keep the two in step (TD-ADS-09).
	 */
	static List<List<MissionTemplate>> outcomes(List<MissionTemplate> eligible) {
		return walk(eligible, pool -> pool);
	}

	/**
	 * Min/max Coin of any 5-mission set the current app can be handed from {@code active}, over every eligibility mix;
	 * null when no mix fills the 5 slots. Max counts an item reward's fallback Coin. Mirror of the admin's
	 * dailyOutlook(…, [CLIENTS.current]).coin (domains/admin-missions/mappers.ts), so every trigger is renderable.
	 */
	static int[] dailyCoinRange(List<MissionTemplate> active) {
		List<MissionEligibilityRule> gates = Arrays.stream(MissionEligibilityRule.values())
				.filter(r -> r != MissionEligibilityRule.NONE).toList();
		int[] range = null;
		for (int mask = 0; mask < 1 << gates.size(); mask++) {
			Set<MissionEligibilityRule> mix = EnumSet.of(MissionEligibilityRule.NONE);
			for (int g = 0; g < gates.size(); g++) {
				if ((mask & 1 << g) != 0) {
					mix.add(gates.get(g));
				}
			}
			List<MissionTemplate> eligible = active.stream().filter(t -> mix.contains(t.getEligibilityRule())).toList();
			for (List<MissionTemplate> set : outcomes(eligible)) {
				int coin = set.stream().mapToInt(MissionTemplate::getRewardCoin).sum();
				int max = coin + set.stream().filter(t -> t.getRewardItemCode() != null)
						.mapToInt(t -> t.getRewardItemFallbackCoin() == null ? 0 : t.getRewardItemFallbackCoin()).sum();
				range = range == null ? new int[] {coin, max}
						: new int[] {Math.min(range[0], coin), Math.max(range[1], max)};
			}
		}
		return range;
	}

	/** Fills the slots in choose's order; {@code branch} picks one template (choose) or all of them (outcomes). */
	private static List<List<MissionTemplate>> walk(List<MissionTemplate> eligible,
			Function<List<MissionTemplate>, List<MissionTemplate>> branch) {
		List<Integer> order = new ArrayList<>();
		List<Integer> fallback = new ArrayList<>();
		for (int slot = 0; slot < SLOTS.size(); slot++) {
			(hasOwn(eligible, slot) ? order : fallback).add(slot);
		}
		order.addAll(fallback);
		List<List<MissionTemplate>> results = new ArrayList<>();
		step(0, order, new MissionTemplate[SLOTS.size()], eligible, branch, results);
		return results;
	}

	private static void step(int k, List<Integer> order, MissionTemplate[] picks, List<MissionTemplate> eligible,
			Function<List<MissionTemplate>, List<MissionTemplate>> branch, List<List<MissionTemplate>> results) {
		if (k == order.size()) {
			results.add(List.of(picks));
			return;
		}
		int slot = order.get(k);
		List<MissionTemplate> chosen = Arrays.stream(picks).filter(Objects::nonNull).toList();
		List<MissionTemplate> pool = hasOwn(eligible, slot)
				? candidates(eligible, chosen, t -> SLOTS.get(slot).contains(t.getCategory()))
				: candidates(eligible, chosen, t -> t.getCategory().isCore());
		if (pool.isEmpty()) {
			return; // POOL_INSUFFICIENT on this path
		}
		for (MissionTemplate t : branch.apply(pool)) {
			picks[slot] = t;
			step(k + 1, order, picks, eligible, branch, results);
			picks[slot] = null;
		}
	}

	private static boolean hasOwn(List<MissionTemplate> eligible, int slot) {
		return eligible.stream().anyMatch(t -> SLOTS.get(slot).contains(t.getCategory()));
	}

	private static MissionTemplate pickFresh(List<MissionTemplate> pool, Set<String> yesterdayCodes) {
		List<MissionTemplate> fresh = pool.stream().filter(t -> !yesterdayCodes.contains(t.getCode())).toList();
		return weighted(fresh.isEmpty() ? pool : fresh);
	}

	private static List<MissionTemplate> candidates(List<MissionTemplate> eligible, List<MissionTemplate> chosen,
			Predicate<MissionTemplate> filter) {
		return eligible.stream().filter(filter).filter(t -> !chosen.contains(t)).toList();
	}

	private static MissionTemplate weighted(List<MissionTemplate> pool) {
		long roll = ThreadLocalRandom.current().nextLong(pool.stream().mapToLong(MissionTemplate::getWeight).sum());
		for (MissionTemplate t : pool) {
			roll -= t.getWeight();
			if (roll < 0) {
				return t;
			}
		}
		throw new IllegalStateException("weights must be >= 1");
	}

	/**
	 * Triggers the app behind the current request can render: its {@code X-Mission-Triggers} header, or
	 * {@link MissionTriggerEvent#LEGACY} when it sends none (older builds). Unknown names are ignored. Outside an HTTP
	 * request (jobs, service-level tests) nothing is filtered.
	 */
	public static Set<MissionTriggerEvent> clientTriggers() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)) {
			return EnumSet.allOf(MissionTriggerEvent.class);
		}
		String header = request.getRequest().getHeader(TRIGGERS_HEADER);
		if (header == null || header.isBlank()) {
			return MissionTriggerEvent.LEGACY;
		}
		Set<MissionTriggerEvent> triggers = EnumSet.noneOf(MissionTriggerEvent.class);
		for (String name : header.split(",")) {
			Arrays.stream(MissionTriggerEvent.values()).filter(t -> t.name().equals(name.trim())).forEach(triggers::add);
		}
		return triggers;
	}

	/**
	 * Catalog invariant (daily_mission.md §4.2): a learner eligible only for {@code NONE} templates, on an app that
	 * renders only the legacy triggers, still gets 5 missions; newer apps see a superset (PD-DM-13).
	 * Mirrored by {@code dm_assert_catalog_invariant()} in migration_daily_mission_triggers_v2.sql.
	 */
	public static boolean satisfiesInvariant(List<MissionTemplate> activeTemplates) {
		Map<Boolean, Long> byCore = activeTemplates.stream()
				.filter(t -> t.getEligibilityRule() == MissionEligibilityRule.NONE)
				.filter(t -> MissionTriggerEvent.LEGACY.contains(t.getTriggerEvent()))
				.collect(Collectors.partitioningBy(t -> t.getCategory().isCore(), Collectors.counting()));
		long core = byCore.get(true);
		return core >= 4 && (byCore.get(false) >= 1 || core >= 5);
	}

	/**
	 * Advances every open mission of the day this action matches, inside the action's transaction (daily_mission.md §6.2).
	 * No missions for {@code date} (e.g. POOL_INSUFFICIENT) ⇒ nothing happens. A constraint violation propagates and
	 * rolls the whole action back; it is never caught here.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void apply(Long userId, LocalDate date, MissionTriggerEvent trigger, String sourceKey, MissionFacts facts,
			Instant actionInstant) {
		for (UserDailyMission mission : missionRepository.findOpenForUpdate(userId, date, trigger)) {
			if (!mission.accepts(facts)
					|| progressLogRepository.existsByUserDailyMissionIdAndSourceKey(mission.getId(), sourceKey)) {
				continue;
			}
			if (trigger == MissionTriggerEvent.TOPIC_ITEM_CREATED) {
				// Lifetime word credit: a word advances at most one add-word mission, ever (§5.5).
				if (!facts.quizReady() || wordCreditRepository.existsByUserIdAndWordHash(userId, facts.wordHash())) {
					continue;
				}
				wordCreditRepository.save(UserWordCredit.builder()
						.user(mission.getUser())
						.wordHash(facts.wordHash())
						.userDailyMission(mission)
						.createdAt(actionInstant)
						.build());
			}
			progressLogRepository.save(UserDailyMissionProgressLog.builder()
					.userDailyMission(mission)
					.sourceKey(sourceKey)
					.build());
			mission.advance(actionInstant);
		}
	}

	/**
	 * SHA-256 of the normalized target word (daily_mission.md §5.5): NFC → trim → collapse whitespace → lower-case (ROOT).
	 * Trim and collapse use Unicode whitespace so a pasted no-break space is not a different word.
	 */
	public static byte[] wordHash(String targetWord) {
		String normalized = Normalizer.normalize(targetWord, Normalizer.Form.NFC)
				.replaceAll("(?U)\\s+", " ")
				.strip()
				.toLowerCase(Locale.ROOT);
		try {
			return MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every JVM", e);
		}
	}

	/** daily_mission.md §4.3, evaluated lazily and at most once per rule. */
	private final class Eligibility implements Predicate<MissionTemplate> {

		private final Long userId;
		private final Instant now;
		private final Map<MissionEligibilityRule, Boolean> cache = new EnumMap<>(MissionEligibilityRule.class);
		private List<UserItem> owned;
		private Integer studyCards;

		Eligibility(Long userId, Instant now) {
			this.userId = userId;
			this.now = now;
		}

		@Override
		public boolean test(MissionTemplate template) {
			if (template.getEligibilityRule() == MissionEligibilityRule.HAS_STUDY_CARDS_FOR_TARGET) {
				return studyCards() >= template.getTargetValue();
			}
			return cache.computeIfAbsent(template.getEligibilityRule(), this::evaluate);
		}

		/** Cards in today's study session, read once; 0 when StudyService is not in the context. */
		private int studyCards() {
			if (studyCards == null) {
				StudyService study = studyService.getIfAvailable();
				studyCards = study == null ? 0 : study.getToday(userId).sessionCount();
			}
			return studyCards;
		}

		private boolean evaluate(MissionEligibilityRule rule) {
			return switch (rule) {
				case NONE -> true;
				case HAS_IDLE_XP_BOOSTER -> owned().stream()
						.anyMatch(ui -> ui.getItemType() == ShopItemType.XP_BOOSTER && ui.getQuantity() >= 1)
						&& activationRepository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now).isEmpty();
				case HAS_PURCHASABLE_ITEM -> {
					User user = userRepository.getReferenceById(userId);
					Map<Long, Integer> quantities = owned().stream()
							.collect(Collectors.toMap(ui -> ui.getItem().getId(), UserItem::getQuantity));
					// Same quantities the Shop catalog shows: pending Streak Freeze settlement applied (streak_freeze.md §3.2).
					quantities.putAll(streakFreezeService.unitsLeft(user, owned(), missionDate(now)));
					yield shopItemRepository.findCatalog(now, null).stream()
							.anyMatch(item -> ShopPurchasePolicy.canBuyNow(item, quantities.getOrDefault(item.getId(), 0),
									user.getCoin(), now));
				}
				case HAS_STUDY_CARDS_FOR_TARGET -> throw new IllegalStateException("depends on the template: see test()");
			};
		}

		private List<UserItem> owned() {
			if (owned == null) {
				owned = userItemRepository.findAllByUserId(userId);
			}
			return owned;
		}
	}
}
