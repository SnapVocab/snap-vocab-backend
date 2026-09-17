package vn.ptit.snapvocab.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseTimeEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "topics")
@Entity
public class Topic extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_id", nullable = false)
	private Collection collection;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Topic parent;

	@OneToMany(mappedBy = "parent")
	@Builder.Default
	private List<Topic> children = new ArrayList<>();

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 255)
	private String translation;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "description_translation", columnDefinition = "TEXT")
	private String descriptionTranslation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "active_template_id")
	private Template activeTemplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "schema_id")
	private Schema schema;



	@OneToMany(
			mappedBy = "topic",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@Builder.Default
	private List<TopicItem> items = new ArrayList<>();
}
