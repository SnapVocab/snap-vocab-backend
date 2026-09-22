package vn.ptit.snapvocab.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.word.WordSuggestDTO;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

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
			SELECT new vn.ptit.snapvocab.domain.dto.word.WordSuggestDTO(w.id, w.word)
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
}
