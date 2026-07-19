package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import com.Let.s_Code.nexus.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // Was previously missing @GetMapping, so Spring never exposed this as an endpoint.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {

        String currentPrincipalName = authentication.getName();

        User currentUser = userRepository.findByEmail(currentPrincipalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Return a DTO instead of the raw entity so the hashed password never
        // gets serialized into the JSON response.
        return ResponseEntity.ok(UserResponse.fromEntity(currentUser));
    }
}
