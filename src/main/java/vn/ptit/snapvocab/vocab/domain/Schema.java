package vn.ptit.snapvocab.vocab.domain;

import vn.ptit.snapvocab.user.domain.User;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;

@Entity
@Table(name = "`schemas`")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schema extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 50, unique = true)
	private String code;

	@Builder.Default
	@Column(name = "is_system", nullable = false)
	private boolean isSystem = false;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false, length = 255)
	private String name;
	@Column(columnDefinition = "TEXT")
	private String description;
	@OneToMany(
			mappedBy = "schema",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@OrderBy("position ASC")
	@Builder.Default
	private Set<SchemaAttributeGroup> attributeGroups = new LinkedHashSet<>();
	@OneToMany(mappedBy = "schema")
	@Builder.Default
	private List<Topic> topics = new ArrayList<>();

	@OneToMany(
			mappedBy = "schema",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@Builder.Default
	private List<Template> templates = new ArrayList<>();

	public boolean isSystem() {
		return isSystem;
	}

	public boolean getIsSystem() {
		return isSystem;
	}

	public void setSystem(boolean isSystem) {
		this.isSystem = isSystem;
	}

	public void setIsSystem(boolean isSystem) {
		this.isSystem = isSystem;
	}

	public void addTemplate(Template template) {
		templates.add(template);
		template.setSchema(this);
	}

	public void removeTemplate(Template template) {
		templates.remove(template);
		template.setSchema(null);
	}
}

