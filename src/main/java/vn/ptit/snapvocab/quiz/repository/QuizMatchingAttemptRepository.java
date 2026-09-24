package vn.ptit.snapvocab.quiz.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingAttempt;

@Repository
public interface QuizMatchingAttemptRepository extends JpaRepository<QuizMatchingAttempt, Long> {

	Optional<QuizMatchingAttempt> findByQuizIdAndIdempotencyKey(Long quizId, String idempotencyKey);
}
