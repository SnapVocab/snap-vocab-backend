package vn.ptit.snapvocab.shop.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.ptit.snapvocab.shop.domain.CoinTransaction;

/** Append-only ledger: only insert and read are exposed, no update/delete. */
public interface CoinTransactionRepository extends Repository<CoinTransaction, Long> {

	CoinTransaction save(CoinTransaction transaction);

	Optional<CoinTransaction> findByEventKey(String eventKey);

	/** Newest first; {@code cursor} is the last id of the previous page (exclusive), null for the first page. */
	@Query("SELECT t FROM CoinTransaction t WHERE t.user.id = :userId AND (:cursor IS NULL OR t.id < :cursor) "
			+ "ORDER BY t.id DESC")
	List<CoinTransaction> findPage(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
