package com.company.chatdocs.user;

import com.company.chatdocs.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class DevUserSeederTests {

	@Autowired
	AppUserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void seedsDemoUsersWithHashedPasswords() {
		assertThat(users.findAll()).extracting(AppUser::getUsername).containsExactlyInAnyOrder("demo", "alice");

		AppUser demo = users.findByUsername("demo").orElseThrow();
		assertThat(demo.getPasswordHash()).startsWith("$2").isNotEqualTo("demo");
		assertThat(passwordEncoder.matches("demo", demo.getPasswordHash())).isTrue();
		assertThat(demo.getRole()).isEqualTo(AppUser.Role.USER);
		assertThat(demo.getCreatedAt()).isNotNull();
	}

}
