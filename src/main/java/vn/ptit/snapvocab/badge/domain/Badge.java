package vn.ptit.snapvocab.badge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;

/**
 * Badge catalog entry (badges.md §7.1). {@code code}, {@code metric} and {@code threshold} never change after
 * creation: a new threshold is a new badge (§5.3). Deactivating stops new grants; earners keep seeing it (§5.4).
 */
@Entity
@Table(name = "badges",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_badges_code", columnNames = "code"),
				@UniqueConstraint(name = "uq_badges_metric_threshold", columnNames = {"metric", "threshold"})})
@Check(name = "chk_badges_threshold", constraints = "threshold >= 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64, updatable = false)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 255)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30, updatable = false)
	private BadgeMetric metric;

	@Column(nullable = false, updatable = false)
	private Integer threshold;

	/** lucide-react-native icon name; the app falls back to a default icon for unknown keys. */
	@Column(name = "icon_key", nullable = false, length = 50)
	private String iconKey;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "is_active", nullable = false)
	@ColumnDefault("true")
	@Builder.Default
	private Boolean active = true;
}
