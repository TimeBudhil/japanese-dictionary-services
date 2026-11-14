package baddee.japanese_dictionary_services.vocabulary.aop;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs every call to the module's public API ({@code VocabularyApi}, D37) with its duration
 * (D30). One place for this module only; other modules write their own.
 *
 * <ul>
 * <li>DEBUG: each call and its time, e.g. {@code vocabulary.searchEntries ok in 12 ms}.</li>
 * <li>WARN: calls slower than {@link #SLOW_MS}.</li>
 * <li>DEBUG: calls that throw (exception type only). The exception goes on unchanged; the
 * error handlers decide how to report it, so it is not logged twice.</li>
 * </ul>
 * Arguments are not logged (queries can be long and are user input).
 *
 * <p>The request id is not added here: {@code logging.pattern.correlation} already prints the
 * MDC {@code requestId} on every line, including these.
 */
@Aspect
@Component
public class VocabularyLoggingAspect {

	private static final Logger log = LoggerFactory.getLogger(VocabularyLoggingAspect.class);

	/** Calls slower than this log at WARN. */
	static final long SLOW_MS = 500;

	@Around("execution(* baddee.japanese_dictionary_services.vocabulary.VocabularyApi.*(..))")
	public Object logApiCall(ProceedingJoinPoint call) throws Throwable {
		String name = "vocabulary." + call.getSignature().getName();
		long start = System.nanoTime();
		try {
			Object result = call.proceed();
			long ms = elapsedMs(start);
			if (ms > SLOW_MS) {
				log.warn("{} slow: {} ms", name, ms);
			}
			else {
				log.debug("{} ok in {} ms", name, ms);
			}
			return result;
		}
		catch (Throwable ex) {
			log.debug("{} failed in {} ms: {}", name, elapsedMs(start), ex.getClass().getSimpleName());
			throw ex;
		}
	}

	private static long elapsedMs(long startNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	}
}
