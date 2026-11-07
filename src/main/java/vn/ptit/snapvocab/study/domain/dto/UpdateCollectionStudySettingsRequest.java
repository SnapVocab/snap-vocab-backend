package vn.ptit.snapvocab.study.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCollectionStudySettingsRequest(
        @NotNull @Min(0) @Max(9999) Integer newCardsPerDay,
        @NotNull @Min(0) @Max(99999) Integer reviewCardsPerDay) {}
