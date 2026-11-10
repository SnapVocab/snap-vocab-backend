package vn.ptit.snapvocab.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.user.domain.User;

@Entity
@Table(
		name = "learning_events",
		uniqueConstraints = @UniqueConstraint(name = "uq_learning_events_source_key", columnNames = "source_key"),
		indexes = {
				@Index(name = "idx_learning_events_user_date", columnList = "user_id, learning_date"),
				@Index(name = "idx_learning_events_date_type", columnList = "learning_date, event_type")
		}
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40)
	private LearningEventType eventType;

	@Column(name = "source_key", nullable = false, length = 191)
	private String sourceKey;

	@Column(name = "learning_date", nullable = false)
	private LocalDate learningDate;

	@Convert(converter = JsonMapConverter.class)
	@Column(columnDefinition = "longtext")
	private Map<String, Object> metadata;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
