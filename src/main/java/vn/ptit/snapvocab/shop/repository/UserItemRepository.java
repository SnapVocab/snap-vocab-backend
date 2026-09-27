package vn.ptit.snapvocab.shop.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

	@Query("SELECT ui FROM UserItem ui JOIN FETCH ui.item WHERE ui.user.id = :userId AND ui.item.id = :itemId")
	Optional<UserItem> findByUserIdAndItemId(@Param("userId") Long userId, @Param("itemId") Long itemId);

	@Query("SELECT ui FROM UserItem ui JOIN FETCH ui.item i WHERE ui.user.id = :userId ORDER BY i.type, i.sortOrder, i.id")
	List<UserItem> findAllByUserId(@Param("userId") Long userId);

	Optional<UserItem> findByUserIdAndItemTypeAndEquippedTrue(Long userId, ShopItemType itemType);
}
