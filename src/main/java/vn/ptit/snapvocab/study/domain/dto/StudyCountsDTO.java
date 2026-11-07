package vn.ptit.snapvocab.study.domain.dto;

/** Counts available today, including descendants. Child rows share the collection's remaining allowance. */
public record StudyCountsDTO(Long collectionId, Long topicId, long newCount, long reviewCount, long learningCount) {
    public long getDueCount() { return reviewCount + learningCount; }
}
