package com.company.chatdocs.service;

import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.AppUserRepository;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Creates new accounts from the sign-up page. The browser logs in right after, with the same credentials. */
@BrowserCallable
@AnonymousAllowed
public class SignupService {

	static final int MIN_PASSWORD_LENGTH = 8;

	/** BCrypt only uses the first 72 bytes of a password, and Spring Security rejects longer ones. */
	static final int MAX_PASSWORD_BYTES = 72;

	static final int MAX_DISPLAY_NAME_LENGTH = 100;

	private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");

	private static final String USERNAME_TAKEN = "That username is already taken.";

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;

	SignupService(AppUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * @param username    3–32 characters: letters, digits, dots, dashes, underscores; stored in lower case
	 * @param displayName the name shown in the app
	 * @param password    at least {@value #MIN_PASSWORD_LENGTH} characters
	 * @throws InvalidInputException with a message for the sign-up form if anything is invalid or taken
	 */
	@Transactional
	public void register(@NonNull String username, @NonNull String displayName, @NonNull String password) {
		String name = username.strip().toLowerCase(Locale.ROOT);
		String shownName = displayName.strip();
		validate(name, shownName, password);
		if (users.existsByUsernameIgnoreCase(name)) {
			throw new InvalidInputException(USERNAME_TAKEN);
		}
		try {
			users.saveAndFlush(new AppUser(name, passwordEncoder.encode(password), shownName, AppUser.Role.USER));
		}
		catch (DataIntegrityViolationException e) {
			// Someone else took the name between the check and the insert.
			throw new InvalidInputException(USERNAME_TAKEN);
		}
	}

	private static void validate(String username, String displayName, String password) {
		if (!USERNAME.matcher(username).matches()) {
			throw new InvalidInputException(
					"Usernames need 3 to 32 characters: letters, digits, dots, dashes or underscores.");
		}
		if (displayName.isEmpty() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
			throw new InvalidInputException("Please enter your name (up to " + MAX_DISPLAY_NAME_LENGTH + " characters).");
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidInputException("Passwords need at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new InvalidInputException("That password is too long.");
		}
	}

}
