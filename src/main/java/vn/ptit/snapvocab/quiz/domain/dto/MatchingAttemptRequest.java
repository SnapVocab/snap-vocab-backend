package vn.ptit.snapvocab.quiz.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record MatchingAttemptRequest(@NotBlank String leftTileKey, @NotBlank String rightTileKey) {
}
