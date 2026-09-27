package com.company.chatdocs.service;

import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Looks up the logged-in user. Services use it to scope every query to the caller's own data.
 */
@Component
public class CurrentUser {

	private final AppUserRepository users;

	CurrentUser(AppUserRepository users) {
		this.users = users;
	}

	public AppUser get() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		return users.findByUsername(username)
				.orElseThrow(() -> new IllegalStateException("Logged-in user not found: " + username));
	}

}
