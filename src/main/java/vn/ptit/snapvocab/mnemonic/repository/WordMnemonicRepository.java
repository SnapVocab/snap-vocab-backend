package vn.ptit.snapvocab.mnemonic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mnemonic.domain.WordMnemonic;

@Repository
public interface WordMnemonicRepository extends JpaRepository<WordMnemonic, Long> {

	Optional<WordMnemonic> findByUserIdAndTopicItemId(Long userId, Long topicItemId);

	@Query("SELECT m FROM WordMnemonic m JOIN FETCH m.topicItem WHERE m.user.id = :userId AND m.topicItem.id IN :itemIds")
	List<WordMnemonic> findByUserIdAndTopicItemIds(@Param("userId") Long userId,
			@Param("itemIds") Collection<Long> itemIds);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM WordMnemonic m WHERE m.user.id = :userId AND m.topicItem.id = :topicItemId")
	int deleteByUserIdAndTopicItemId(@Param("userId") Long userId, @Param("topicItemId") Long topicItemId);
}
