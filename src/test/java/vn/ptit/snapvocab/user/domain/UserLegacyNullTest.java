package vn.ptit.snapvocab.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserLegacyNullTest {

	@Test
	void legacyCountersUseZeroBalancesAndZeroBaselineStreak() {
		User user = new User();
		user.setExp(null);
		user.setCoin(null);
		user.setStreakDays(null);

		assertThat(user.getExp()).isZero();
		assertThat(user.getCoin()).isZero();
		assertThat(user.getStreakDays()).isZero();
		user.normalizeStreak();
		assertThat(user.getStreakDays()).isZero();
		user.setStreakDays(-2);
		user.normalizeStreak();
		assertThat(user.getStreakDays()).isZero();
	}
}
