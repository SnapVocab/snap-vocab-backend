package vn.ptit.snapvocab.progress.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.progress.domain.StreakFreezeDay;
import vn.ptit.snapvocab.progress.repository.StreakFreezeDayRepository;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.user.domain.User;

/**
 * Streak Freeze settlement (streak_freeze.md §3). Each missed study day takes one unit while units last, oldest day
 * first; running out mid-gap breaks the chain, and a broken chain is closed ({@code streak_days = 0}) so a unit bought
 * later never reaches back. Lazy, no cron: writers settle under the users lock before anything that changes the units
 * held; readers evaluate the same rule without writing. Either way the outcome equals a midnight job's (§3.3).
 */
@Service
@RequiredArgsConstructor
public class StreakFreezeService {

	private final UserItemRepository userItemRepository;
	private final StreakFreezeDayRepository dayRepository;

	/**
	 * {@code coveredThrough}: last day the chain reaches, studied or frozen (null before the first study day).
	 * {@code remaining}: units left; {@code lastFrozen}: last frozen day, this settlement included.
	 */
	public record Settlement(int consumed, boolean broken, LocalDate coveredThrough, int remaining,
			LocalDate lastFrozen) {

		/** Streak to show on {@code today}: the stored chain while it reaches yesterday, else 0. */
		public int streak(int stored, LocalDate today) {
			return stored > 0 && !broken && coveredThrough != null && !coveredThrough.isBefore(today.minusDays(1))
					? stored : 0;
		}

		/** What the app shows: never below 1, also for a new learner or a broken chain (master 2026-10-31). */
		public int shownStreak(int stored, LocalDate today) {
			return Math.max(streak(stored, today), 1);
		}
	}

	/** Study day of the server clock, for writers that are not a learning action (purchase, reward grant). */
	public static LocalDate today() {
		return LocalDate.ofInstant(Economy.now(), ProgressService.ZONE);
	}

	/** The rule of §3.2 as a pure function of the stored chain, the units held and the study day. */
	static Settlement settle(int stored, LocalDate lastStudied, LocalDate lastFrozen, int held, LocalDate today) {
		LocalDate covered = lastFrozen != null && (lastStudied == null || lastFrozen.isAfter(lastStudied))
				? lastFrozen : lastStudied;
		long missed = covered == null ? 0 : ChronoUnit.DAYS.between(covered, today) - 1;
		// ponytail: stored streak never drops below 1 since master 2026-10-31, so 1 doubles as "chain closed" and a
		// 1-day chain is never frozen; add a chain-closed column if a 1-day chain must spend a unit.
		if (stored <= 1 || lastStudied == null || missed <= 0) {
			return new Settlement(0, false, covered, held, lastFrozen);
		}
		int consumed = (int) Math.min(missed, held);
		LocalDate reached = covered.plusDays(consumed);
		return new Settlement(consumed, consumed < missed, reached, held - consumed, consumed > 0 ? reached : lastFrozen);
	}

	/** Settles and writes (§3.4); the caller holds the users lock. Units come from the oldest catalog item first. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Settlement settle(User user, LocalDate today) {
		List<UserItem> units = userItemRepository.findAllByUserIdAndType(user.getId(), ShopItemType.STREAK_FREEZE);
		Settlement s = evaluate(user, units, today);
		if (s.consumed() > 0) {
			Iterator<UserItem> source = units.iterator();
			UserItem unit = source.next();
			List<StreakFreezeDay> days = new ArrayList<>();
			for (LocalDate day = s.coveredThrough().minusDays(s.consumed() - 1L); !day.isAfter(s.coveredThrough());
					day = day.plusDays(1)) {
				while (unit.getQuantity() == 0) {
					unit = source.next();
				}
				unit.setQuantity(unit.getQuantity() - 1);
				days.add(StreakFreezeDay.builder().user(user).frozenDate(day).shopItem(unit.getItem()).build());
			}
			dayRepository.saveAll(days);
			userItemRepository.saveAllAndFlush(units);
		}
		if (s.broken()) {
			user.setStreakDays(1);
		}
		return s;
	}

	/** The same rule without writing (§3.2), for screens that show the streak. */
	public Settlement preview(User user, LocalDate today) {
		return evaluate(user, userItemRepository.findAllByUserIdAndType(user.getId(), ShopItemType.STREAK_FREEZE),
				today);
	}

	/**
	 * Units left per Streak Freeze item id once the pending settlement is applied, for screens that list holdings
	 * (catalog, inventory). {@code owned}: all of the user's items; empty when none is a Streak Freeze.
	 */
	public Map<Long, Integer> unitsLeft(User user, List<UserItem> owned, LocalDate today) {
		List<UserItem> units = owned.stream().filter(ui -> ui.getItemType() == ShopItemType.STREAK_FREEZE)
				.sorted(Comparator.comparing(ui -> ui.getItem().getId())).toList();
		if (units.isEmpty()) {
			return Map.of();
		}
		int consumed = evaluate(user, units, today).consumed();
		Map<Long, Integer> left = new HashMap<>();
		for (UserItem unit : units) {
			int taken = Math.min(consumed, unit.getQuantity());
			consumed -= taken;
			left.put(unit.getItem().getId(), unit.getQuantity() - taken);
		}
		return left;
	}

	private Settlement evaluate(User user, List<UserItem> units, LocalDate today) {
		LocalDate lastStudied = user.getLastStudiedAt() == null ? null
				: LocalDate.ofInstant(user.getLastStudiedAt(), ProgressService.ZONE);
		return settle(user.getStreakDays(), lastStudied, dayRepository.findLastFrozenDate(user.getId()),
				units.stream().mapToInt(UserItem::getQuantity).sum(), today);
	}
}
