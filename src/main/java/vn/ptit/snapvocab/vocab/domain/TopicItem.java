package vn.ptit.snapvocab.vocab.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;

@Entity
@Table(name = "topic_items", indexes = {
		@Index(name = "idx_topic_items_created", columnList = "created_at"),
		@Index(name = "idx_topic_items_source_scan", columnList = "source_scan_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicItem extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_id", nullable = false)
	private Topic topic;

	/** Scan the learner saved this word from (admin_analytics_backend.md G3); written only by the Analytics path. */
	@Column(name = "source_scan_id", length = 36)
	private String sourceScanId;

	@OneToMany(
			mappedBy = "item",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@OrderBy("position ASC")
	@Builder.Default
	private List<TopicItemAttributeGroup> groupInstances =
			new ArrayList<>();
}

