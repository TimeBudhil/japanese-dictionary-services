package baddee.japanese_dictionary_services.names.integration;

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
 * The names module's own security chain and CORS (D25), copied from the vocabulary module's.
 */
@Configuration
public class NamesSecurityConfig {

	static final List<String> PATHS = List.of("/api/v1/names/**");

	/** Below the root chain's {@code LOWEST_PRECEDENCE}. Same value in every module. */
	static final int MODULE_CHAIN_ORDER = 100;

	/** The request id header (set by the root RequestIdFilter). Named here, not imported (D30). */
	static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Bean
	@Order(MODULE_CHAIN_ORDER)
	SecurityFilterChain namesSecurityFilterChain(HttpSecurity http,
			@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) throws Exception {
		http
			.securityMatcher(PATHS.toArray(String[]::new))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.cors(cors -> cors.configurationSource(corsSource(allowedOrigins)))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	/** Read-only CORS, as in the vocabulary module. */
	static CorsConfigurationSource corsSource(List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins.stream().map(String::strip).filter(o -> !o.isEmpty()).toList());
		config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()));
		config.setAllowedHeaders(List.of(REQUEST_ID_HEADER));
		config.setExposedHeaders(List.of(REQUEST_ID_HEADER));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		PATHS.forEach(path -> source.registerCorsConfiguration(path, config));
		return source;
	}
}
