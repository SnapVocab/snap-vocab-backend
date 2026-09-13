package vn.ptit.snapvocab.domain.dto.topic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.dto.template.TemplateDTO;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SchemaDTO {

	private Long id;
	private String code;
	private boolean isSystem;

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
	private String name;
	private String description;
	private Instant createdAt;
	private Instant updatedAt;

	@Builder.Default
	private List<SchemaAttributeGroupDTO> groups = new ArrayList<>();

	@Builder.Default
	private List<TemplateDTO> templates = new ArrayList<>();
}
