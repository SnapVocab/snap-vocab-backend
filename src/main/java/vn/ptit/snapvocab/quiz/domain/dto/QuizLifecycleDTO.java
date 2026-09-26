package vn.ptit.snapvocab.quiz.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;

public record QuizLifecycleDTO(Long quizId, QuizStatus status, Instant cancelledAt) {
}

