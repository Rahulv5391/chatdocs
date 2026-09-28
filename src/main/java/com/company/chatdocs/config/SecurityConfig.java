package com.company.chatdocs.config;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// Our own REST controllers (e.g. file downloads) need a logged-in user; they check ownership themselves.
		// Vaadin doesn't know these paths, so without this rule it denies them (403).
		// Health checks (hosting platform, keep-alive pings) must work without a login; they only report UP/DOWN.
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/**").authenticated()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll());
		// Vaadin permits its static resources and the login view, and protects everything else.
		http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView("/login"));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
