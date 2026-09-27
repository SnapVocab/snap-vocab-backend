package vn.ptit.snapvocab.xp.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.repository.UserRepository;
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
