package vn.ptit.snapvocab.badge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * Lifetime, append-only: a normalized word counts once toward {@code DISTINCT_WORDS_ADDED} (badges.md §6.2, §7.3).
 * Same hash as {@code user_word_credits} but a separate table: that one is written only when an add-word mission
 * advances. No FK to {@code topic_items}, so deleting items neither blocks nor lowers the count.
 */
@Entity
@Table(name = "user_badge_word_credits",
		uniqueConstraints = @UniqueConstraint(name = "uq_user_badge_word_credits_word",
				columnNames = {"user_id", "word_hash"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBadgeWordCredit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "word_hash", nullable = false, length = 32)
	private byte[] wordHash;

	/** {@code actionInstant} of the add-item. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
