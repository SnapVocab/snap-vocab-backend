package vn.ptit.snapvocab.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.domain.enumeration.ThemeKey;

@Entity
@Table(
		name = "shop_items",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_shop_items_code", columnNames = {"code"}),
				@UniqueConstraint(name = "uq_shop_items_theme_key", columnNames = {"theme_key"}),
				@UniqueConstraint(name = "uq_shop_items_id_type", columnNames = {"id", "type"})
		},
		indexes = {
				@Index(name = "idx_shop_items_catalog", columnList = "status, purchasable, type, sort_order")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	// VARCHAR, not a MySQL ENUM: ddl-auto never adds an enum value, so a new type would fail to insert (shop.md §9).
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ShopItemType type;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "icon_key", length = 255)
	private String iconKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private ShopItemStatus status = ShopItemStatus.DRAFT;

	@Column(nullable = false)
	@Builder.Default
	private Boolean purchasable = false;

	@Column
	private Long price;

	@Column(name = "available_from")
	private Instant availableFrom;

	@Column(name = "available_until")
	private Instant availableUntil;

	@Column(name = "sort_order", nullable = false)
	@Builder.Default
	private Integer sortOrder = 0;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "theme_key", length = 50)
	private ThemeKey themeKey;

	@Column(name = "frame_asset_key", length = 255)
	private String frameAssetKey;

	@Column(name = "boost_duration_minutes")
	private Integer boostDurationMinutes;

	@Column(name = "max_quantity")
	private Integer maxQuantity;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Version
	@Column(nullable = false)
	@Builder.Default
	private Long version = 0L;

	/** shop.md §2.3 / streak_freeze.md PD-SF-03 defaults; publish validation normally guarantees the field is set. */
	public int maxQuantityOrDefault() {
		return maxQuantity != null ? maxQuantity : type == ShopItemType.STREAK_FREEZE ? 2 : 5;
	}

	public int boostDurationMinutesOrDefault() {
		return boostDurationMinutes != null ? boostDurationMinutes : 30;
	}

	public boolean isAvailableAt(Instant now) {
		if (!Boolean.TRUE.equals(purchasable) || status != ShopItemStatus.PUBLISHED) {
			return false;
		}
		if (availableFrom != null && now.isBefore(availableFrom)) {
			return false;
		}
		if (availableUntil != null && !now.isBefore(availableUntil)) {
			return false;
		}
		return true;
	}
}
