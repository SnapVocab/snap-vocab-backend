package vn.ptit.snapvocab.study.domain.dto;

public record CollectionStudySettingsDTO(Long collectionId, Long userId, int newCardsPerDay,
        int reviewCardsPerDay, boolean customized) {}
