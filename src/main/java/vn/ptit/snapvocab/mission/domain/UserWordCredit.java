package vn.ptit.snapvocab.mission.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.user.domain.User;

/** Lifetime, append-only: a normalized word advances an add-word mission at most once per account (daily_mission.md §5.5, §7.6). */
@Entity
@Table(name = "user_word_credits",
		uniqueConstraints = @UniqueConstraint(name = "uq_user_word_credits_word", columnNames = {"user_id", "word_hash"}),
		indexes = @Index(name = "idx_user_word_credits_mission", columnList = "user_daily_mission_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWordCredit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "word_hash", nullable = false, length = 32)
	private byte[] wordHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_daily_mission_id", nullable = false)
	private UserDailyMission userDailyMission;

	/** {@code actionInstant} of the add-item that spent the credit. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
