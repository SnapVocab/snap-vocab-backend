package vn.ptit.snapvocab.shop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

@Repository
public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {

	Optional<ShopItem> findByCode(String code);

	/** Shop visibility rule (shop.md §3.1): PUBLISHED ∧ purchasable ∧ inside the sale window. */
	@Query("SELECT i FROM ShopItem i "
			+ "WHERE i.status = vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus.PUBLISHED "
			+ "AND i.purchasable = true "
			+ "AND (i.availableFrom IS NULL OR i.availableFrom <= :now) "
			+ "AND (i.availableUntil IS NULL OR i.availableUntil > :now) "
			+ "AND (:type IS NULL OR i.type = :type) "
			+ "ORDER BY i.sortOrder, i.id")
	List<ShopItem> findCatalog(@Param("now") Instant now, @Param("type") ShopItemType type);

	/** Admin list; {@code q} is a lower-cased LIKE pattern over code and name. */
	@Query("SELECT i FROM ShopItem i "
			+ "WHERE (:status IS NULL OR i.status = :status) "
			+ "AND (:type IS NULL OR i.type = :type) "
			+ "AND (:q IS NULL OR LOWER(i.code) LIKE :q OR LOWER(i.name) LIKE :q)")
	Page<ShopItem> search(@Param("status") ShopItemStatus status, @Param("type") ShopItemType type,
			@Param("q") String q, Pageable pageable);

	boolean existsByCodeAndIdNot(String code, Long id);

	boolean existsByTypeAndStatusAndIdNot(ShopItemType type, ShopItemStatus status, Long id);
}
