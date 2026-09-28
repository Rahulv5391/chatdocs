package com.company.chatdocs.repository;

import com.company.chatdocs.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByUsername(String username);

	boolean existsByUsername(String username);

	/** Usernames are unique regardless of case, so "Alice" can't sign up next to "alice". */
	Optional<AppUser> findByUsernameIgnoreCase(String username);

	boolean existsByUsernameIgnoreCase(String username);

}
