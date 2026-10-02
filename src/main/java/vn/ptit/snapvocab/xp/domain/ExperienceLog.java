package vn.ptit.snapvocab.xp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import vn.ptit.snapvocab.user.domain.User;

/** Append-only XP ledger. {@code amount = baseAmount + bonusAmount}; Weekly XP / Leaderboard use {@code amount}. */
@Entity
@Table(
		name = "experience_logs",
		uniqueConstraints = @UniqueConstraint(name = "uq_experience_logs_event_key", columnNames = "event_key"),
		indexes = @Index(name = "idx_experience_logs_user_time", columnList = "user_id, created_at")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperienceLog {

	@Id
	@Column(length = 36)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private Integer amount;

	@Column(name = "base_amount", nullable = false)
	private Integer baseAmount;

	@Column(name = "bonus_amount", nullable = false)
	private Integer bonusAmount;

	@Column(name = "booster_activation_id")
	private Long boosterActivationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private XpSourceType sourceType;

	@Column(name = "event_key", nullable = false, length = 191)
	private String eventKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
