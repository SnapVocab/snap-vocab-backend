package vn.ptit.snapvocab.quiz.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

	@Query("SELECT q.quiz.id FROM QuizQuestion q WHERE q.id = :id")
	Optional<Long> findQuizIdById(@Param("id") Long id);
}

