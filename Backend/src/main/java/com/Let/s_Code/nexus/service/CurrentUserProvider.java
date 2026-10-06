package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import com.Let.s_Code.nexus.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Resolves the fully-hydrated User entity for the currently authenticated principal. */
@Service
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public User resolve(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + email));
    }
}
