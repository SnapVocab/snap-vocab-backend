package vn.ptit.snapvocab.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import vn.ptit.snapvocab.domain.dto.common.RestResponse;

@ControllerAdvice
public class FormatRestResponse implements ResponseBodyAdvice<Object> {

	@Override
	public boolean supports(MethodParameter returnType,
			Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(
			Object body,
			MethodParameter returnType,
			MediaType selectedContentType,
			Class selectedConverterType,
			ServerHttpRequest request,
			ServerHttpResponse response) {

		HttpServletResponse servletResponse =
				((ServletServerHttpResponse) response).getServletResponse();

		HttpServletRequest servletRequest =
				((ServletServerHttpRequest) request).getServletRequest();

		String path = servletRequest.getRequestURI();

		if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
			return body;
		}

		int status = servletResponse.getStatus();

		if (body instanceof String || body instanceof Resource) {
			return body;
		}

		RestResponse<Object> res = new RestResponse<>();
		res.setStatusCode(status);

		// success
		if (status < 400) {
			res.setData(body);
			return res;
		}

		return body;
	}

}
