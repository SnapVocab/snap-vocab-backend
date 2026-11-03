package vn.ptit.snapvocab.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform;
import vn.ptit.snapvocab.user.domain.User;

/**
 * Expo push token of one device. A device belongs to one user at a time: registering the same token from another
 * account moves it (notification.md §4.3 A5). Written only through {@code DeviceTokenRepository#upsert}.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "device_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uq_device_tokens_token", columnNames = "token"),
		indexes = {
				@Index(name = "idx_device_tokens_user", columnList = "user_id"),
				@Index(name = "idx_device_tokens_last_seen", columnList = "last_seen_at")
		})
public class DeviceToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_device_tokens_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** {@code ExponentPushToken[...]}. */
	@Column(nullable = false, length = 255)
	private String token;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private DevicePlatform platform;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Refreshed on every register; tokens unseen for 60 days are purged (§4.4 J3). */
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;
}
