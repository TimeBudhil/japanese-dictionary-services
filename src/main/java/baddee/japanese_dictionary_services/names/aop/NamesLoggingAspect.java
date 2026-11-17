package baddee.japanese_dictionary_services.names.aop;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs every call to {@code NamesApi} with its duration (D30), the same way as the vocabulary
 * module: DEBUG per call, WARN above {@link #SLOW_MS}, DEBUG with the exception type on
 * failure (the exception goes on unchanged). Arguments are not logged; the request id comes
 * from the log pattern (D80).
 */
@Aspect
@Component
public class NamesLoggingAspect {

	private static final Logger log = LoggerFactory.getLogger(NamesLoggingAspect.class);

	/** Calls slower than this log at WARN. */
	static final long SLOW_MS = 500;

	@Around("execution(* baddee.japanese_dictionary_services.names.NamesApi.*(..))")
	public Object logApiCall(ProceedingJoinPoint call) throws Throwable {
		String name = "names." + call.getSignature().getName();
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
