package baddee.japanese_dictionary_services;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catch-all security chain for paths no feature module owns (D8).
 *
 * <p>Each feature module defines its own {@link SecurityFilterChain} in its
 * {@code integration/} package, limited with {@code securityMatcher("/api/v1/<module>/**")}
 * and an {@code @Order} value below {@code LOWEST_PRECEDENCE} (so it is checked first),
 * plus its own CORS from {@code app.cors.allowed-origins}.
 * This chain runs last: it opens the API docs, Actuator health/info and the error
 * page, and denies everything else, including {@code /api/**} paths no module claims.
 */
@Configuration
public class SecurityConfig {

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	SecurityFilterChain rootSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
				// Spring Boot forwards errors here; without it, error responses become 403s.
				.requestMatchers("/error").permitAll()
				.anyRequest().denyAll())
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	/**
	 * No users exist (D1). An empty store stops Boot from creating a default user with a
	 * generated password.
	 */
	@Bean
	UserDetailsService noUsers() {
		return new InMemoryUserDetailsManager();
	}
}
