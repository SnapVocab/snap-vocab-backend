package vn.ptit.snapvocab.common.dto;

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

}
