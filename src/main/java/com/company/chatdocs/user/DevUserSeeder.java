package com.company.chatdocs.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inserts the demo users on startup in the dev profile. Skips users that already exist.
 */
@Component
@Profile("dev")
class DevUserSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;

	DevUserSeeder(AppUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		seed("demo", "demo", "Demo User");
		seed("alice", "alice", "Alice");
	}

	private void seed(String username, String password, String displayName) {
		if (users.existsByUsername(username)) {
			return;
		}
		users.save(new AppUser(username, passwordEncoder.encode(password), displayName, AppUser.Role.USER));
		log.info("Seeded dev user '{}'", username);
	}

}
