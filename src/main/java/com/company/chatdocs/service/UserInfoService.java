package com.company.chatdocs.service;

import com.company.chatdocs.dto.UserInfo;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Tells the React frontend who is logged in. Anonymous callers get a 401, which the frontend treats as logged out.
 */
@BrowserCallable
@PermitAll
public class UserInfoService {

	private final CurrentUser currentUser;

	UserInfoService(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	public @NonNull UserInfo getUserInfo() {
		var user = currentUser.get();
		return new UserInfo(user.getUsername(), user.getDisplayName(), List.of(user.getRole().name()));
	}

}
