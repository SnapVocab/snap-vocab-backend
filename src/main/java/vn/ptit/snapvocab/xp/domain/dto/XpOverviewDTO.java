package vn.ptit.snapvocab.xp.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.xp.domain.XpSourceType;

/** {@code GET /api/gamification/xp} (MH-STATS-02, Home). {@code level} is null when no level is configured. */
public record XpOverviewDTO(long totalXp, LevelInfo level, List<XpEvent> recentEvents) {

	/** Max level: {@code nextLevelXp = null}, {@code progress = 1}. */
	public record LevelInfo(int levelNumber, String title, long minXp, Long nextLevelXp, double progress) {
	}

	public record XpEvent(XpSourceType sourceType, int amount, Instant createdAt) {
	}
}
