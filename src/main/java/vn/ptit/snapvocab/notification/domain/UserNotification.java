package vn.ptit.snapvocab.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.user.domain.User;

/** Inbox row; its id is the id the API exposes and pages by (notification.md §4.3 A1). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
		name = "user_notifications",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_user_notification",
						columnNames = {"user_id", "notification_id"}
				)
		},
		indexes = {
				@Index(name = "idx_un_user_id", columnList = "user_id, id"),
				@Index(name = "idx_un_user_read", columnList = "user_id, is_read")
		}
)
public class UserNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "notification_id", nullable = false)
	private Notification notification;

	@Builder.Default
	@Column(name = "is_read", nullable = false)
	private Boolean isRead = false;

	@Column(name = "read_at")
	private Instant readAt;
}
