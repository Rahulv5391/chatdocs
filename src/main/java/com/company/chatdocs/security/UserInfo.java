package com.company.chatdocs.security;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record UserInfo(@NonNull String username, @NonNull String displayName, @NonNull List<@NonNull String> roles) {
}
