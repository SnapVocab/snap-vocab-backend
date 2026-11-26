package vn.ptit.snapvocab.dictionary.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

	/** [wordId, translation] in {@code lang}, oldest first, for "Từ của tôi". */
	@Query("SELECT t.word.id, t.translation FROM Translation t WHERE t.word.id IN :wordIds AND t.langCode = :lang ORDER BY t.id")
	List<Object[]> findTranslations(@Param("wordIds") Collection<Long> wordIds, @Param("lang") String lang);

	/** [wordId, definition] written in {@code lang}: the meaning when a word has no translation. */
	@Query("""
			SELECT wd.word.id, d.definition FROM WordDefinition wd JOIN wd.definition d
			WHERE wd.word.id IN :wordIds AND d.definitionLang = :lang ORDER BY wd.id
			""")
	List<Object[]> findDefinitions(@Param("wordIds") Collection<Long> wordIds, @Param("lang") String lang);

	@Query("""
			SELECT w FROM Word w
			LEFT JOIN FETCH w.pronunciations
			LEFT JOIN FETCH w.translations
			LEFT JOIN FETCH w.wordDefinitions wd
			LEFT JOIN FETCH wd.definition
			LEFT JOIN FETCH w.wordRelations
			WHERE w.word = :word AND w.langCode = 'en'
			""")
	Optional<Word> findByWordAndLangCodeEn(@Param("word") String word);

	@Query("""
			SELECT DISTINCT w FROM Word w
			LEFT JOIN FETCH w.pronunciations
			LEFT JOIN FETCH w.translations
			LEFT JOIN FETCH w.wordDefinitions wd
			LEFT JOIN FETCH wd.definition
			LEFT JOIN FETCH w.wordRelations
			WHERE w.word in :words AND w.langCode = 'en'
			""")
	List<Word> findWordsByLangCodeEn(@Param("words") Set<String> words);

	@Query("""
			SELECT new vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO(w.id, w.word)
			FROM Word w
			WHERE w.word LIKE CONCAT(:q, '%') AND w.langCode = 'en'
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
	@Query("SELECT LOWER(w.word) FROM Word w WHERE w.word IN :words AND w.langCode = 'en'")
	List<String> findExistingEnglish(@Param("words") Collection<String> words);
}

