package vn.ptit.snapvocab.xp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.user.domain.Level;
import vn.ptit.snapvocab.xp.domain.dto.XpOverviewDTO;

class XpServiceLevelTest {

	private static Level level(int number, long requiredExp) {
		Level level = new Level();
		level.setLevelNumber(number);
		level.setRequiredExp(requiredExp);
		level.setTitle("L" + number);
		return level;
	}

	@Test
	void progressIsFractionOfTheWayToNextLevel() {
		XpOverviewDTO.LevelInfo info = XpService.levelInfo(level(7, 2000), level(8, 3000), 2450);
		assertThat(info.minXp()).isEqualTo(2000);
		assertThat(info.nextLevelXp()).isEqualTo(3000);
		assertThat(info.progress()).isEqualTo(0.45);
	}

	@Test
	void maxLevelIsFull() {
		XpOverviewDTO.LevelInfo info = XpService.levelInfo(level(10, 9000), null, 12000);
		assertThat(info.nextLevelXp()).isNull();
		assertThat(info.progress()).isEqualTo(1);
	}
}
