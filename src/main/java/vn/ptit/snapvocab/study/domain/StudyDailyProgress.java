package vn.ptit.snapvocab.study.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.Collection;

/** Per-user daily counter scope for one collection. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "study_daily_progress", uniqueConstraints = @UniqueConstraint(
		name = "uq_study_daily_progress_user_collection_date",
		columnNames = { "user_id", "collection_id", "study_date" }))
public class StudyDailyProgress extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_id", nullable = false)
	private Collection collection;

	@jakarta.persistence.Column(name = "study_date", nullable = false)
	private LocalDate studyDate;
}
