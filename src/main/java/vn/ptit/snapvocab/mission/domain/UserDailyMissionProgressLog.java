package vn.ptit.snapvocab.mission.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;

/** Append-only; one row per (mission, action) so an action advances each mission at most once (daily_mission.md §7.3). */
@Entity
@Table(name = "user_daily_mission_progress_logs",
		uniqueConstraints = @UniqueConstraint(name = "uq_mission_progress_logs_source",
				columnNames = {"user_daily_mission_id", "source_key"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyMissionProgressLog extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_daily_mission_id", nullable = false)
	private UserDailyMission userDailyMission;

	@Column(name = "source_key", nullable = false, length = 191)
	private String sourceKey;
}
