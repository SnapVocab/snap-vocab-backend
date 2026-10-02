package vn.ptit.snapvocab.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

@Entity
@Table(
		name = "user_items",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_user_items_owner", columnNames = {"user_id", "shop_item_id"}),
				@UniqueConstraint(name = "uq_user_items_equipped", columnNames = {"user_id", "equipped_slot"})
		},
		indexes = {
				@Index(name = "idx_user_items_user", columnList = "user_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_item_id", nullable = false)
	private ShopItem item;

	@Enumerated(EnumType.STRING)
	@Column(name = "item_type", nullable = false, length = 30)
	private ShopItemType itemType;

	@Column(nullable = false)
	@Builder.Default
	private Integer quantity = 1;

	@Column(nullable = false)
	@Builder.Default
	private Boolean equipped = false;

	/**
	 * Virtual/Generated column sinh tự động bởi MySQL: IF(equipped = 1, item_type, NULL).
	 * Phục vụ DB unique constraint uq_user_items_equipped (user_id, equipped_slot).
	 * JPA không insert hay update trường này.
	 */
	@Column(name = "equipped_slot", insertable = false, updatable = false,
			columnDefinition = "VARCHAR(30) GENERATED ALWAYS AS (IF(equipped = 1, item_type, NULL)) STORED")
	private String equippedSlot;

	@Column(name = "acquired_at", nullable = false, updatable = false)
	private Instant acquiredAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	public void prePersist() {
		if (item != null && itemType == null) {
			itemType = item.getType();
		}
		Instant now = Instant.now();
		if (acquiredAt == null) {
			acquiredAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	public void preUpdate() {
		updatedAt = Instant.now();
	}
}
