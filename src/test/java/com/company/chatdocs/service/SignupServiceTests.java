package com.company.chatdocs.service;

import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class SignupServiceTests {

	private static final Set<String> SEEDED = Set.of("demo", "alice");

	@Autowired
	SignupService signupService;

	@Autowired
	AppUserDetailsService userDetailsService;

	@Autowired
	AppUserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@AfterEach
	void removeNewUsers() {
		users.findAll().stream().filter(user -> !SEEDED.contains(user.getUsername())).forEach(users::delete);
	}

	@Test
	void createsUserWhoCanLogIn() {
		signupService.register("  Bob.Smith ", " Bob Smith ", "correct horse");

		AppUser bob = users.findByUsername("bob.smith").orElseThrow();
		assertThat(bob.getDisplayName()).isEqualTo("Bob Smith");
		assertThat(bob.getRole()).isEqualTo(AppUser.Role.USER);
		assertThat(bob.getPasswordHash()).isNotEqualTo("correct horse");

		UserDetails login = userDetailsService.loadUserByUsername("BOB.SMITH");
		assertThat(passwordEncoder.matches("correct horse", login.getPassword())).isTrue();
		assertThat(login.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
	}

	@Test
	void usernamesAreUniqueIgnoringCase() {
		assertThatThrownBy(() -> signupService.register("Demo", "Someone", "password123"))
				.isInstanceOf(InvalidInputException.class)
				.hasMessage("That username is already taken.");
	}

	@Test
	void rejectsInvalidInput() {
		assertThatThrownBy(() -> signupService.register("ab", "Name", "password123"))
				.isInstanceOf(InvalidInputException.class).hasMessageContaining("3 to 32 characters");
		assertThatThrownBy(() -> signupService.register("has space", "Name", "password123"))
				.isInstanceOf(InvalidInputException.class).hasMessageContaining("3 to 32 characters");
		assertThatThrownBy(() -> signupService.register("carol", "   ", "password123"))
				.isInstanceOf(InvalidInputException.class).hasMessageContaining("your name");
		assertThatThrownBy(() -> signupService.register("carol", "Carol", "short"))
				.isInstanceOf(InvalidInputException.class).hasMessageContaining("at least 8");
		assertThatThrownBy(() -> signupService.register("carol", "Carol", "x".repeat(73)))
				.isInstanceOf(InvalidInputException.class).hasMessage("That password is too long.");

		assertThat(users.findByUsername("carol")).isEmpty();
	}

}
