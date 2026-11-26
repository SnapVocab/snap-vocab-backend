package vn.ptit.snapvocab.dictionary.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

	@Query("""
			SELECT w FROM Word w
			LEFT JOIN FETCH w.pronunciations
			LEFT JOIN FETCH w.translations
			LEFT JOIN FETCH w.wordDefinitions wd
			LEFT JOIN FETCH wd.definition
			LEFT JOIN FETCH w.wordRelations
			WHERE w.word = :word AND w.langCode = 'en' AND w.deletedAt IS NULL
			""")
	Optional<Word> findByWordAndLangCodeEn(@Param("word") String word);

	@Query("""
			SELECT DISTINCT w FROM Word w
			LEFT JOIN FETCH w.pronunciations
			LEFT JOIN FETCH w.translations
			LEFT JOIN FETCH w.wordDefinitions wd
			LEFT JOIN FETCH wd.definition
			LEFT JOIN FETCH w.wordRelations
			WHERE w.word in :words AND w.langCode = 'en' AND w.deletedAt IS NULL
			""")
	List<Word> findWordsByLangCodeEn(@Param("words") Set<String> words);

	@Query("""
			SELECT new vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO(w.id, w.word)
			FROM Word w
			WHERE w.word LIKE CONCAT(:q, '%') AND w.langCode = 'en' AND w.deletedAt IS NULL
			ORDER BY w.word ASC
			""")
	Page<WordSuggestDTO> findWordSuggestByLangCodeEn(
			@Param("q") String q,
			Pageable pageable
	);

	@Query("""
			SELECT w FROM Word w
			LEFT JOIN FETCH w.pronunciations
			LEFT JOIN FETCH w.translations
			LEFT JOIN FETCH w.wordDefinitions wd
			LEFT JOIN FETCH wd.definition
			LEFT JOIN FETCH w.wordRelations
			WHERE w.id = :id
			""")
	Optional<Word> findWordById(@Param("id") Long id);

	/** Which of {@code words} are English entries, lower-cased; no joins (scan stats, admin_overview_backend.md G2). */
	@Query("SELECT LOWER(w.word) FROM Word w WHERE w.word IN :words AND w.langCode = 'en' AND w.deletedAt IS NULL")
	List<String> findExistingEnglish(@Param("words") Collection<String> words);

	/** Admin editor: the row lock every write takes first, so two saves of one word serialize. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Word w WHERE w.id = :id")
	Optional<Word> lockById(@Param("id") Long id);

	/** Soft-deleted English words, newest deletion first; {@code q} narrows by prefix like suggest. */
	@Query("""
			SELECT w FROM Word w
			WHERE w.deletedAt IS NOT NULL AND w.langCode = 'en' AND w.word LIKE CONCAT(:q, '%')
			ORDER BY w.deletedAt DESC, w.id DESC
			""")
	Page<Word> findDeleted(@Param("q") String q, Pageable pageable);
}
