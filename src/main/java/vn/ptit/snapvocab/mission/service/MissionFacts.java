package vn.ptit.snapvocab.mission.service;

/** What a producer knows about its action, for the snapshot filters (daily_mission.md §6.1, §6.2). */
public record MissionFacts(int totalUnits, int correctCount, int requestedCount, boolean quizReady, byte[] wordHash) {

	public static final MissionFacts NONE = new MissionFacts(0, 0, 0, false, null);

	public static MissionFacts quiz(int totalUnits, int correctCount, int requestedCount) {
		return new MissionFacts(totalUnits, correctCount, requestedCount, false, null);
	}

	/** {@code wordHash} is null when the item is not quiz-ready. */
	public static MissionFacts topicItem(byte[] wordHash) {
		return new MissionFacts(0, 0, 0, wordHash != null, wordHash);
	}
}
