package vn.ptit.snapvocab.domain.dto.template;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateDTO {

	private Long id;
	private Long schemaId;
	private String code;
	private String name;
	private boolean isDefault;

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
	private Instant createdAt;
	private Instant updatedAt;

	@Builder.Default
	private List<TemplateElementDTO> elements = new ArrayList<>();
}
