package vn.ptit.snapvocab.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Word;

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
}
