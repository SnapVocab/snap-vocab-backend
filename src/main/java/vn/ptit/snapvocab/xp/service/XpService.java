package vn.ptit.snapvocab.xp.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.Level;
import vn.ptit.snapvocab.user.repository.LevelRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.xp.domain.dto.XpOverviewDTO;
import vn.ptit.snapvocab.shop.domain.BoosterActivation;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.BoosterActivationRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.xp.domain.ExperienceLog;
import vn.ptit.snapvocab.xp.domain.XpSourceType;
import vn.ptit.snapvocab.xp.repository.ExperienceLogRepository;

/** The single place XP is written. Every XP source must go through {@link #award} so the booster applies exactly once. */
@Service
@RequiredArgsConstructor
public class XpService {

	private final UserRepository userRepository;
	private final ExperienceLogRepository experienceLogRepository;
	private final BoosterActivationRepository activationRepository;
	private final LevelRepository levelRepository;

	/** Level = highest {@code levels.required_exp <= users.exp}; null if no level is configured that low. */
	@Transactional(readOnly = true)
	public XpOverviewDTO getOverview(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		long exp = user.getExp();
		XpOverviewDTO.LevelInfo level = levelRepository.findFirstByRequiredExpLessThanEqualOrderByRequiredExpDesc(exp)
				.map(current -> levelInfo(current, levelRepository
						.findFirstByRequiredExpGreaterThanOrderByRequiredExpAsc(exp).orElse(null), exp))
				.orElse(null);
		return new XpOverviewDTO(exp, level, experienceLogRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)
				.stream()
				.map(log -> new XpOverviewDTO.XpEvent(log.getSourceType(), log.getAmount(), log.getCreatedAt()))
				.toList());
	}

	static XpOverviewDTO.LevelInfo levelInfo(Level current, Level next, long exp) {
		long min = current.getRequiredExp();
		if (next == null) {
			return new XpOverviewDTO.LevelInfo(current.getLevelNumber(), current.getTitle(), min, null, 1);
		}
		double progress = (double) (exp - min) / (next.getRequiredExp() - min);
		return new XpOverviewDTO.LevelInfo(current.getLevelNumber(), current.getTitle(), min, next.getRequiredExp(),
				progress);
	}

	/**
	 * Awards XP idempotently by {@code eventKey} (a replay returns the original log, bonus included, and adds nothing).
	 * The booster is evaluated at award time on server clock (shop.md §5): active booster and boostable source
	 * ⇒ {@code bonus = base × (multiplier − 1)}. {@code users.exp} grows by the total, so Weekly XP/Leaderboard count the bonus.
	 */
	@Transactional
	public ExperienceLog award(Long userId, XpSourceType source, int baseAmount, String eventKey) {
		if (baseAmount <= 0 || source == null || eventKey == null || eventKey.isBlank()) {
			throw new IllegalArgumentException("positive baseAmount, source and eventKey are required");
		}
		User user = Economy.lockUser(userRepository, userId);
		ExperienceLog existing = experienceLogRepository.findByEventKey(eventKey).orElse(null);
		if (existing != null) {
			return existing;
		}
		Instant now = Economy.now();
		BoosterActivation booster = source.isBoostable()
				? activationRepository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now).orElse(null)
				: null;
		int bonus = booster == null ? 0 : baseAmount * (ShopItemType.XP_BOOSTER_MULTIPLIER - 1);
		ExperienceLog log = experienceLogRepository.save(ExperienceLog.builder()
				.id(UUID.randomUUID().toString())
				.user(user)
				.baseAmount(baseAmount)
				.bonusAmount(bonus)
				.amount(baseAmount + bonus)
				.boosterActivationId(booster == null ? null : booster.getId())
				.sourceType(source)
				.eventKey(eventKey)
				.createdAt(now)
				.build());
		user.setExp(user.getExp() + log.getAmount());
		return log;
	}
}
