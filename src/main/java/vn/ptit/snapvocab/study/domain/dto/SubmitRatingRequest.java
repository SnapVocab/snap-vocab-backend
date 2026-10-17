package vn.ptit.snapvocab.study.domain.dto;

import jakarta.validation.constraints.NotNull;
import io.github.openspacedrepetition.Rating;

public record SubmitRatingRequest(@NotNull Rating rating) {
}
