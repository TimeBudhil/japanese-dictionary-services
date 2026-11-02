package baddee.japanese_dictionary_services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Last-resort handler: turns any exception nothing else handled into a 500
 * {@link ProblemDetail} (RFC 9457) without leaking internals.
 *
 * <p>Expected errors do not come here. Spring's own exceptions (bad parameter, unknown
 * path, validation) and each module's own exceptions (which extend
 * {@code ErrorResponseException}) are rendered by Boot's ProblemDetail handler
 * ({@code spring.mvc.problemdetails.enabled=true}), which runs before this one.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnexpectedErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(UnexpectedErrorHandler.class);

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred.");
		problem.setProperty(RequestIdFilter.MDC_KEY, MDC.get(RequestIdFilter.MDC_KEY));
		return problem;
	}
}
