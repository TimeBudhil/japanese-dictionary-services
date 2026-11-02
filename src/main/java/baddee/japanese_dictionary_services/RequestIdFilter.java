package baddee.japanese_dictionary_services;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request one id, before any module code runs. The id is taken from the
 * incoming {@code X-Request-Id} header when it is safe, otherwise generated. It is put
 * in the logging context (MDC key {@code requestId}, printed by
 * {@code logging.pattern.correlation}) and returned in the response header.
 *
 * <p>Modules read it with {@code MDC.get("requestId")}, never by importing this class.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	/** Accept caller ids only if short and plain, so they cannot inject into logs. */
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
				? incoming
				: UUID.randomUUID().toString();

		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}
}
