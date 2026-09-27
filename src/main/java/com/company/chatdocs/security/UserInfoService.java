package com.company.chatdocs.security;

import com.company.chatdocs.user.AppUserRepository;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Tells the React frontend who is logged in. Anonymous callers get a 401, which the frontend treats as logged out.
 */
@BrowserCallable
@PermitAll
public class UserInfoService {

	private final AppUserRepository users;

	UserInfoService(AppUserRepository users) {
		this.users = users;
	}

	public @NonNull UserInfo getUserInfo() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		var user = users.findByUsername(username).orElseThrow();
		return new UserInfo(user.getUsername(), user.getDisplayName(), List.of(user.getRole().name()));
	}

}
