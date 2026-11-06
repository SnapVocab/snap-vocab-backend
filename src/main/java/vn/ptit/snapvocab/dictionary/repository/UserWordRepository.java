package vn.ptit.snapvocab.dictionary.repository;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.ptit.snapvocab.dictionary.domain.UserWord;
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;

public interface UserWordRepository extends JpaRepository<UserWord, Long> {

    boolean existsByUser_IdAndWord_Id(Long userId, Long wordId);

    @Query("select uw.word.id from UserWord uw where uw.user.id = :userId and uw.word.id in :wordIds")
    Set<Long> findSavedWordIds(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

    // Atomic and idempotent even when two requests save the same word concurrently.
    @Modifying
    @Query(value = """
            INSERT INTO user_words (user_id, word_id, created_at)
            VALUES (:userId, :wordId, CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void addWord(@Param("userId") Long userId, @Param("wordId") Long wordId);

    @Modifying
    @Query("delete from UserWord uw where uw.user.id = :userId and uw.word.id = :wordId")
    void removeWord(@Param("userId") Long userId, @Param("wordId") Long wordId);

    @Query("""
            select new vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO(uw.word.id, uw.word.word)
            from UserWord uw where uw.user.id = :userId
            order by uw.createdAt desc, uw.id desc
            """)
    Page<WordSuggestDTO> findMyWords(@Param("userId") Long userId, Pageable pageable);
}
