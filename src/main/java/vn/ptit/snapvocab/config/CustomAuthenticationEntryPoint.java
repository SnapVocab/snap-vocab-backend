package vn.ptit.snapvocab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.domain.dto.common.RestResponse;
import vn.ptit.snapvocab.security.SecurityAuditLogger;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper mapper;
	private final SecurityAuditLogger securityAuditLogger;

	public CustomAuthenticationEntryPoint(ObjectMapper mapper,
			SecurityAuditLogger securityAuditLogger) {
		this.mapper = mapper;
		this.securityAuditLogger = securityAuditLogger;
	}

	@Override
	public void commence(HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		securityAuditLogger.unauthorizedAccess(
				request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

		response.reset();
		response.setStatus(401);
		response.setContentType("application/json;charset=UTF-8");

		RestResponse<Object> res = new RestResponse<>();
		res.setStatusCode(401);
		res.setError("Unauthorized");

		mapper.writeValue(response.getOutputStream(), res);
	}

}
