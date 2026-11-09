package vn.ptit.snapvocab.badge.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.audit.service.AuditReason;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.domain.dto.RefillEstimateDTO;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeErrorCode;
import vn.ptit.snapvocab.badge.exception.BadgeException;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.service.ShopMapper;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.service.StorageService;

/**
 * Admin catalog (badges.md §8.4, §10): P1 reads, P2 edits the display fields and rewards (PD-BDG-14) and switches
 * badges on/off. Never syncs, grants or touches user_badges: a reward change applies to later earners only. One row's {@code version} is enough: unlike missions, no rule spans several rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBadgeService {

	static final int NAME_MAX = 100;
	static final int DESCRIPTION_MAX = 255;
	/** Badge art in MinIO: keys come from {@link #imageUploadUrl}; the seeded art is {@code public/badges/<code>.png}. */
	static final String IMAGE_PREFIX = "public/badges/";
	static final Pattern IMAGE_KEY = Pattern.compile("^public/badges/[A-Za-z0-9_-]+\\.(png|webp)$");
	static final long IMAGE_MAX_BYTES = 500 * 1024;
	/** §5.3: the condition is immutable; a new threshold is a new badge. */
	static final List<String> LOCKED = List.of("code", "metric", "threshold");
	static final Set<String> EDITABLE = Set.of("name", "description", "imageKey", "sortOrder", "isActive",
			"rewardCoin", "rewardXp", "rewardItemCode");
	/** Audit "changes", in form order. */
	static final List<String> AUDIT_FIELDS = List.of("name", "description", "imageKey", "sortOrder", "rewardCoin",
			"rewardXp", "rewardItemCode", "isActive");
	static final int REWARD_MAX = 1000;
	/** Badge reward items: frames only, never sold (nobody owns one before earning it, so no Coin fallback). */
	static final Set<ShopItemType> REWARD_ITEM_TYPES = Set.of(ShopItemType.AVATAR_FRAME, ShopItemType.CARD_FRAME);

	private final BadgeRepository badgeRepository;
	private final UserBadgeRepository userBadgeRepository;
	private final ObjectMapper objectMapper;
	private final AdminAuditService audit;
	private final ShopItemRepository shopItemRepository;
	private final ShopMapper shopMapper;
	private final StorageService storageService;

	/** Editable state; JSON names match AdminBadgeDTO. */
	record Form(String name, String description, String imageKey, Integer sortOrder, Integer rewardCoin,
			Integer rewardXp, String rewardItemCode, @JsonProperty("isActive") Boolean isActive) {
	}

	public record Violation(String field, BadgeErrorCode code, String message) {
	}

	// ---- queries ----

	public List<AdminBadgeDTO> list() {
		List<Badge> catalog = badgeRepository.findAllByOrderBySortOrderAscIdAsc();
		if (catalog.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> counts = userBadgeRepository.countEarnersByBadge().stream()
				.collect(Collectors.toMap(UserBadgeRepository.BadgeEarnerCount::getBadgeId,
						UserBadgeRepository.BadgeEarnerCount::getEarnerCount));
		List<String> codes = catalog.stream().map(Badge::getRewardItemCode).filter(Objects::nonNull).distinct().toList();
		Map<String, ShopItem> items = codes.isEmpty() ? Map.of()
				: shopItemRepository.findByCodeIn(codes).stream()
						.collect(Collectors.toMap(ShopItem::getCode, Function.identity()));
		return catalog.stream()
				.map(b -> AdminBadgeDTO.of(b, shopMapper.url(b.getImageKey()), counts.getOrDefault(b.getId(), 0L),
						rewardItem(b.getRewardItemCode() == null ? null : items.get(b.getRewardItemCode()))))
				.toList();
	}

	public AdminBadgeDTO get(Long id) {
		return dto(find(id));
	}

	/**
	 * PD-BDG-13d: read-only count of activated accounts that reached the threshold and do not hold the badge. Grants
	 * nothing; badges are only granted by each learner's sync. One aggregate query, per metric.
	 */
	public RefillEstimateDTO refillEstimate(Long id) {
		Badge b = find(id);
		long eligible = switch (b.getMetric()) {
			case LONGEST_STREAK_DAYS -> badgeRepository.countRefillLongestStreak(b.getId(), b.getThreshold());
			case QUIZ_COMPLETED -> badgeRepository.countRefillQuizCompleted(b.getId(), b.getThreshold());
			case PERFECT_QUIZ -> badgeRepository.countRefillPerfectQuiz(b.getId(), b.getThreshold());
			case DISTINCT_WORDS_ADDED -> badgeRepository.countRefillDistinctWords(b.getId(), b.getThreshold());
		};
		return new RefillEstimateDTO(eligible, Instant.now().truncatedTo(ChronoUnit.MICROS), b.getVersion());
	}

	// ---- commands ----

	/** Presigned PUT for a badge image under {@code public/badges/}; the admin then PATCHes the key as imageKey. */
	public PresignedUploadResponse imageUploadUrl(String fileName) {
		String ext = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		String contentType = switch (ext) {
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			default -> throw invalid(List.of(violation("fileName", "Only PNG or WebP images are allowed")));
		};
		return storageService.createUploadUrl(IMAGE_PREFIX + UUID.randomUUID() + "." + ext, contentType);
	}

	/**
	 * P2 (§8.4): display fields and/or {@code isActive}, with the {@code version} the admin read. Editing content keeps
	 * every earner's badge, earned date and seen state (PD-BDG-13b); switching on/off creates or removes no grant. A body
	 * that changes nothing saves and records nothing. Optional {@code reason} goes to the activity log.
	 */
	@Transactional
	public AdminBadgeDTO patch(Long id, JsonNode body) {
		Badge badge = find(id);
		if (body == null || !body.isObject()) {
			throw invalid(List.of(violation("body", "JSON object required")));
		}
		ObjectNode request = ((ObjectNode) body).deepCopy();
		String reason = AuditReason.check(AuditReason.take(request), false, null, null);
		List<String> locked = LOCKED.stream().filter(request::has).toList();
		if (!locked.isEmpty()) {
			throw new BadgeException(BadgeErrorCode.FIELD_LOCKED, "Immutable fields: " + locked, Map.of("fields", locked));
		}
		JsonNode version = request.remove("version");
		if (version == null || !version.isIntegralNumber()) {
			throw invalid(List.of(violation("version", "version is required")));
		}
		if (version.asLong() != badge.getVersion()) {
			throw conflict(badge.getVersion());
		}

		List<Violation> errors = new ArrayList<>();
		request.properties().forEach(e -> {
			if (!EDITABLE.contains(e.getKey())) {
				errors.add(violation(e.getKey(), "not an editable field"));
			}
		});
		Form before = formOf(badge);
		Form next = new Form(
				text(request, "name", before.name(), NAME_MAX, errors),
				text(request, "description", before.description(), DESCRIPTION_MAX, errors),
				imageKey(request, before.imageKey(), errors),
				sortOrder(request, before.sortOrder(), errors),
				reward(request, "rewardCoin", before.rewardCoin(), errors),
				reward(request, "rewardXp", before.rewardXp(), errors),
				rewardItemCode(request, before.rewardItemCode(), errors),
				isActive(request, before.isActive(), errors));
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
		List<AuditChange> changes = AuditDiff.of(objectMapper, before, next, AUDIT_FIELDS);
		if (changes.isEmpty()) {
			return get(id);
		}
		badge.setName(next.name());
		badge.setDescription(next.description());
		badge.setImageKey(next.imageKey());
		badge.setSortOrder(next.sortOrder());
		badge.setRewardCoin(next.rewardCoin());
		badge.setRewardXp(next.rewardXp());
		badge.setRewardItemCode(next.rewardItemCode());
		badge.setActive(next.isActive());
		Badge saved;
		try {
			saved = badgeRepository.saveAndFlush(badge);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw conflict(null);
		}
		AuditAction action = before.isActive().equals(saved.getActive()) ? AuditAction.BADGE_UPDATED
				: saved.getActive() ? AuditAction.BADGE_ENABLED : AuditAction.BADGE_DISABLED;
		audit.record(action, AuditTarget.of(AuditTargetType.BADGE, saved.getId(), saved.getName(), saved.getCode()),
				changes, reason, null, saved.getVersion());
		return dto(saved);
	}

	// ---- field rules (§8.4) ----

	/** Trimmed before the length check, so what is stored is what was validated. */
	private static String text(ObjectNode body, String field, String current, int max, List<Violation> errors) {
		if (!body.has(field)) {
			return current;
		}
		JsonNode v = body.get(field);
		String s = v.isTextual() ? v.asText().strip() : null;
		if (s == null || s.isEmpty() || s.length() > max) {
			errors.add(violation(field, "1.." + max + " characters, not blank"));
			return current;
		}
		return s;
	}

	/** Replace only, never clear: a new key must be uploaded already; an unchanged key is not rechecked. */
	private String imageKey(ObjectNode body, String current, List<Violation> errors) {
		if (!body.has("imageKey")) {
			return current;
		}
		JsonNode v = body.get("imageKey");
		String key = v.isTextual() ? v.asText() : null;
		if (key == null || !IMAGE_KEY.matcher(key).matches()) {
			errors.add(violation("imageKey", "must be a key issued by /api/admin/badges/assets/upload-url"));
			return current;
		}
		if (key.equals(current)) {
			return current;
		}
		Optional<Long> size = storageService.sizeOf(key);
		if (size.isEmpty()) {
			errors.add(violation("imageKey", "image has not been uploaded"));
			return current;
		}
		if (size.get() > IMAGE_MAX_BYTES) {
			errors.add(violation("imageKey", "image exceeds " + IMAGE_MAX_BYTES / 1024 + " KB"));
			return current;
		}
		return key;
	}

	private static Integer sortOrder(ObjectNode body, Integer current, List<Violation> errors) {
		if (!body.has("sortOrder")) {
			return current;
		}
		JsonNode v = body.get("sortOrder");
		if (!v.isIntegralNumber() || !v.canConvertToInt()) {
			errors.add(violation("sortOrder", "integer required"));
			return current;
		}
		return v.asInt();
	}

	private static Integer reward(ObjectNode body, String field, Integer current, List<Violation> errors) {
		if (!body.has(field)) {
			return current;
		}
		JsonNode v = body.get(field);
		if (!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() < 0 || v.asInt() > REWARD_MAX) {
			errors.add(violation(field, "integer 0.." + REWARD_MAX));
			return current;
		}
		return v.asInt();
	}

	/** null clears the item. A new code must name a PUBLISHED, non-purchasable frame; an unchanged code is not rechecked. */
	private String rewardItemCode(ObjectNode body, String current, List<Violation> errors) {
		if (!body.has("rewardItemCode")) {
			return current;
		}
		JsonNode v = body.get("rewardItemCode");
		if (v.isNull()) {
			return null;
		}
		String code = v.isTextual() ? v.asText().strip() : null;
		if (code == null || code.isEmpty()) {
			errors.add(violation("rewardItemCode", "shop item code or null"));
			return current;
		}
		if (!code.equals(current) && shopItemRepository.findByCode(code)
				.filter(i -> i.getStatus() == ShopItemStatus.PUBLISHED && !Boolean.TRUE.equals(i.getPurchasable())
						&& REWARD_ITEM_TYPES.contains(i.getType()))
				.isEmpty()) {
			errors.add(new Violation("rewardItemCode", BadgeErrorCode.REWARD_ITEM_NOT_ELIGIBLE,
					"shop item " + code + " must exist and be a PUBLISHED, non-purchasable AVATAR_FRAME or CARD_FRAME"));
			return current;
		}
		return code;
	}

	private static Boolean isActive(ObjectNode body, Boolean current, List<Violation> errors) {
		if (!body.has("isActive")) {
			return current;
		}
		JsonNode v = body.get("isActive");
		if (!v.isBoolean()) {
			errors.add(violation("isActive", "true or false"));
			return current;
		}
		return v.asBoolean();
	}

	// ---- helpers ----

	static Form formOf(Badge b) {
		return new Form(b.getName(), b.getDescription(), b.getImageKey(), b.getSortOrder(), b.getRewardCoin(),
				b.getRewardXp(), b.getRewardItemCode(), b.getActive());
	}

	private AdminBadgeDTO dto(Badge b) {
		ShopItem item = b.getRewardItemCode() == null ? null
				: shopItemRepository.findByCode(b.getRewardItemCode()).orElse(null);
		return AdminBadgeDTO.of(b, shopMapper.url(b.getImageKey()), userBadgeRepository.countByBadgeId(b.getId()),
				rewardItem(item));
	}

	private AdminBadgeDTO.RewardItem rewardItem(ShopItem item) {
		return item == null ? null : new AdminBadgeDTO.RewardItem(item.getName(), item.getType(), item.getStatus(),
				item.getPurchasable(), shopMapper.config(item).frameUrl());
	}

	private Badge find(Long id) {
		return badgeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("BADGE_NOT_FOUND"));
	}

	private static Violation violation(String field, String message) {
		return new Violation(field, BadgeErrorCode.VALIDATION_ERROR, message);
	}

	private static BadgeException invalid(List<Violation> errors) {
		Violation first = errors.get(0);
		return new BadgeException(first.code(), first.field() + ": " + first.message(),
				Map.of("errors", errors));
	}

	/** {@code currentVersion} null when a concurrent writer slipped in after our read (the flush lost the race). */
	private static BadgeException conflict(Long currentVersion) {
		return new BadgeException(BadgeErrorCode.VERSION_CONFLICT, "Badge was modified by someone else; reload it",
				currentVersion == null ? null : Map.of("currentVersion", currentVersion));
	}
}
