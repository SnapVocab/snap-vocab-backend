package vn.ptit.snapvocab.mission.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.UserDailyMissionClaim;
import vn.ptit.snapvocab.mission.domain.dto.DailyMissionsDTO;
import vn.ptit.snapvocab.mission.domain.dto.MissionClaimResultDTO;
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;
import vn.ptit.snapvocab.mission.exception.MissionException;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionClaimRepository;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionRepository;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.shop.service.ShopRewardService;
import vn.ptit.snapvocab.shop.service.ShopRewardService.RewardGrant;
import vn.ptit.snapvocab.shop.service.WalletService;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.xp.domain.XpSourceType;
import vn.ptit.snapvocab.xp.service.XpService;

/** Learner Daily Mission API (daily_mission.md §8.1, §8.2). */
@Service
public class DailyMissionService {

	private static final Logger LOG = LoggerFactory.getLogger(DailyMissionService.class);

	private final UserRepository userRepository;
	private final UserDailyMissionRepository missionRepository;
	private final UserDailyMissionClaimRepository claimRepository;
	private final MissionTracker tracker;
	private final WalletService walletService;
	private final XpService xpService;
	private final ShopRewardService rewardService;
	private final EntityManager entityManager;
	private final TransactionTemplate transaction;

	public DailyMissionService(UserRepository userRepository, UserDailyMissionRepository missionRepository,
			UserDailyMissionClaimRepository claimRepository, MissionTracker tracker, WalletService walletService,
			XpService xpService, ShopRewardService rewardService, EntityManager entityManager,
			PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.missionRepository = missionRepository;
		this.claimRepository = claimRepository;
		this.tracker = tracker;
		this.walletService = walletService;
		this.xpService = xpService;
		this.rewardService = rewardService;
		this.entityManager = entityManager;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	/** Today's five missions, assigning them first if this is the day's first trigger. Never writes progress. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DailyMissionsDTO getToday(Long userId) {
		Economy.lockUser(userRepository, userId);
		Instant now = Economy.now();
		LocalDate today = MissionTracker.missionDate(now);
		if (tracker.ensureAssigned(userId, today, now) == MissionTracker.Assignment.POOL_INSUFFICIENT) {
			throw new MissionException(MissionErrorCode.MISSION_POOL_INSUFFICIENT, "Daily missions are unavailable");
		}
		return new DailyMissionsDTO(today, MissionTracker.ZONE.getId(), now,
				today.plusDays(1).atStartOfDay(MissionTracker.ZONE).toInstant(),
				missionRepository.findByUserIdAndMissionDateOrderBySlot(userId, today).stream()
						.map(DailyMissionsDTO.Mission::of)
						.toList());
	}

	/**
	 * Pays one completed mission of today, exactly once (daily_mission.md §8.2). Coin, XP, item, receipt and status
	 * commit together. The mission is the idempotency anchor: a claimed mission always replays, whatever the key.
	 */
	public MissionClaimResultDTO claim(Long userId, Long missionId, String idempotencyKey) {
		String key = Economy.requireKey(idempotencyKey);
		try {
			return transaction.execute(status -> doClaim(userId, missionId, key));
		} catch (DataIntegrityViolationException e) {
			// Same last line of defence as ShopService: the failed transaction is gone; re-evaluate on committed state.
			LOG.info("Mission claim {}:{} hit a constraint, re-evaluating: {}", missionId, key,
					e.getMostSpecificCause().getMessage());
			entityManager.clear();
			return transaction.execute(status -> doClaim(userId, missionId, key));
		}
	}

	private MissionClaimResultDTO doClaim(Long userId, Long missionId, String key) {
		User user = Economy.lockUser(userRepository, userId);
		UserDailyMission mission = missionRepository.findOwnedForUpdate(missionId, userId)
				.orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND,
						"Mission " + missionId + " not found"));
		UserDailyMissionClaim byKey = claimRepository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
		if (byKey != null && !byKey.getUserDailyMission().getId().equals(mission.getId())) {
			throw new MissionException(MissionErrorCode.IDEMPOTENCY_KEY_REUSED,
					"Idempotency-Key was already used to claim another mission");
		}
		if (mission.getStatus() == DailyMissionStatus.CLAIMED) {
			UserDailyMissionClaim claim = claimRepository.findByUserDailyMissionId(mission.getId()).orElseThrow();
			return result(mission, claim, user, true);
		}
		// Deadline is judged after the lock is held (§3.3): a request sent at 23:59:59 that waits past 00:00 expires.
		Instant now = Economy.now();
		if (mission.getMissionDate().isBefore(MissionTracker.missionDate(now))) {
			throw new MissionException(MissionErrorCode.MISSION_EXPIRED, "Mission " + missionId + " has expired");
		}
		if (mission.getStatus() == DailyMissionStatus.IN_PROGRESS) {
			throw new MissionException(MissionErrorCode.MISSION_NOT_COMPLETED,
					"Mission " + missionId + " is not completed");
		}

		String eventKey = "MISSION_REWARD:" + mission.getId();
		if (mission.getRewardCoin() > 0) {
			walletService.apply(user, mission.getRewardCoin(), CoinSourceType.MISSION_REWARD, mission.getId().toString(),
					mission.getName(), eventKey);
		}
		if (mission.getRewardXp() > 0) {
			// MISSION_REWARD is not boostable (shop.md S6): the booster never multiplies mission XP.
			xpService.award(userId, XpSourceType.MISSION_REWARD, mission.getRewardXp(), eventKey);
		}
		RewardGrant grant = mission.getRewardItemCode() == null
				? new RewardGrant(0, 0)
				: rewardService.grantShopItemReward(userId, mission.getRewardItemCode(), 1,
						mission.getRewardItemFallbackCoin(), CoinSourceType.MISSION_REWARD, eventKey + ":ITEM");
		UserDailyMissionClaim claim = claimRepository.save(UserDailyMissionClaim.builder()
				.userDailyMission(mission)
				.user(user)
				.idempotencyKey(key)
				.rewardCoin(mission.getRewardCoin())
				.rewardXp(mission.getRewardXp())
				.rewardItemCode(mission.getRewardItemCode())
				.grantedItemQuantity(grant.grantedQuantity())
				.fallbackCoin(grant.fallbackCoin())
				.build());
		mission.setStatus(DailyMissionStatus.CLAIMED);
		mission.setClaimedAt(now);
		return result(mission, claim, user, false);
	}

	private static MissionClaimResultDTO result(UserDailyMission mission, UserDailyMissionClaim claim, User user,
			boolean replayed) {
		return new MissionClaimResultDTO(mission.getId(), mission.getStatus(), MissionClaimResultDTO.Granted.of(claim),
				user.getCoin(), user.getExp(), replayed);
	}
}
