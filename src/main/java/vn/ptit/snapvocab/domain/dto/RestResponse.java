package vn.ptit.snapvocab.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestResponse<T> {
	private int statusCode;
	private T data;
	private String error;
}
