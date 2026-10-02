package vn.ptit.snapvocab.vocab.domain;

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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;

@Entity
@Table(name = "templates")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Template extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "schema_id", nullable = false)
	private Schema schema;

	@Column(length = 50)
	private String code;

	@Column(nullable = false)
	private String name;

	@Builder.Default
	@Column(name = "is_default", nullable = false)
	private boolean isDefault = false;

	public boolean isDefault() {
		return isDefault;
	}

	public boolean getIsDefault() {
		return isDefault;
	}

	public void setDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public void setIsDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	@OneToMany(
			mappedBy = "template",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@OrderBy("position ASC")
	@Builder.Default
	private List<TemplateElement> elements = new ArrayList<>();

	public void addElement(TemplateElement element) {
		elements.add(element);
		element.setTemplate(this);
	}

	public void removeElement(TemplateElement element) {
		elements.remove(element);
		element.setTemplate(null);
	}

}

