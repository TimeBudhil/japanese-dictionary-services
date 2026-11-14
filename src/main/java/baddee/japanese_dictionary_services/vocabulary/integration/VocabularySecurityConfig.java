package baddee.japanese_dictionary_services.vocabulary.integration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The vocabulary module's own security chain and CORS (D25). <b>Template for the other
 * modules</b>: copy it, then change {@link #PATHS} and the bean name.
 *
 * <ul>
 * <li>Only applies to {@code /api/v1/vocabulary/**} ({@code securityMatcher}); also matches
 * {@code /api/v1/vocabulary} itself.</li>
 * <li>{@code @Order(MODULE_CHAIN_ORDER)} puts it before the root catch-all chain
 * ({@code LOWEST_PRECEDENCE}). Module paths never overlap, so all modules can use the same
 * value.</li>
 * <li>Public reads ({@code permitAll}, D8). Stateless; CSRF, basic auth, form login and
 * logout are off. Spring Security's default security headers stay on.</li>
 * <li>CORS for this chain only, from the one key {@code app.cors.allowed-origins}. The
 * source is built here, not as a bean, so no module's CORS leaks into another chain.</li>
 * </ul>
 */
@Configuration
public class VocabularySecurityConfig {

	static final String PATHS = "/api/v1/vocabulary/**";

	/** Below the root chain's {@code LOWEST_PRECEDENCE}. Same value in every module. */
	static final int MODULE_CHAIN_ORDER = 100;

	/** The request id header (set by the root RequestIdFilter). Named here, not imported (D30). */
	static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Bean
	@Order(MODULE_CHAIN_ORDER)
	SecurityFilterChain vocabularySecurityFilterChain(HttpSecurity http,
			@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) throws Exception {
		http
			.securityMatcher(PATHS)
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.cors(cors -> cors.configurationSource(corsSource(allowedOrigins)))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	/**
	 * Read-only CORS: {@code GET}, {@code HEAD}, {@code OPTIONS} from the allowed origins. Callers
	 * may send {@code X-Request-Id} and can read it on the response. No credentials (public API).
	 */
	static CorsConfigurationSource corsSource(List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins.stream().map(String::strip).filter(o -> !o.isEmpty()).toList());
		config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()));
		config.setAllowedHeaders(List.of(REQUEST_ID_HEADER));
		config.setExposedHeaders(List.of(REQUEST_ID_HEADER));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration(PATHS, config);
		return source;
	}
}
