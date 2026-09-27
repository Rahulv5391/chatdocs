package com.company.chatdocs.service;

import com.company.chatdocs.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class AppUserDetailsServiceTests {

	@Autowired
	AppUserDetailsService userDetailsService;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void loadsSeededUserWithRole() {
		UserDetails demo = userDetailsService.loadUserByUsername("demo");

		assertThat(demo.getUsername()).isEqualTo("demo");
		assertThat(passwordEncoder.matches("demo", demo.getPassword())).isTrue();
		assertThat(demo.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
	}

	@Test
	void rejectsUnknownUser() {
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

}
