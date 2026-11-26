package vn.ptit.snapvocab.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class CursorPaginationResponse<T> {

	private T data;
	private Long nextCursor; // id of the next item, null if no more items
	private Boolean hasNext; // true if there are more items to fetch

	/** Items in the whole list, sent with the first page only (no cursor) so a list header can say "(150)". */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long total;

}
