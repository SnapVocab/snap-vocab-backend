package vn.ptit.snapvocab.shop.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import vn.ptit.snapvocab.shop.domain.enumeration.Behavior;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/** Dynamic-form metadata (shop.md §8.4): common fields plus, per type, its own fields and developer constants. */
public record ShopItemTypeMetadataDTO(List<FieldSpec> commonFields, List<TypeSpec> types) {

	public record TypeSpec(ShopItemType type, Behavior behavior, boolean stackable, Map<String, Object> fixed,
			List<FieldSpec> fields) {
	}

	/**
	 * {@code required}: must be set on every save; {@code requiredToPublish}: may stay empty in DRAFT.
	 * {@code editableIn}: statuses in which the field may change (otherwise FIELD_LOCKED).
	 */
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FieldSpec(String name, InputType inputType, boolean required, boolean requiredToPublish,
			Set<ShopItemStatus> editableIn, Long min, Long max, Object defaultValue, String pattern,
			List<String> options, List<String> formats, Long maxBytes, String dimensions) {
	}

	public enum InputType {
		TEXT,
		TEXTAREA,
		NUMBER,
		BOOLEAN,
		DATETIME,
		SELECT,
		IMAGE
	}
}
