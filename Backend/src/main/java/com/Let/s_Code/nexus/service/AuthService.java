package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import com.Let.s_Code.nexus.dto.AuthResponse;
import com.Let.s_Code.nexus.dto.LoginRequest;
import com.Let.s_Code.nexus.dto.RegisterRequest;
import com.Let.s_Code.nexus.dto.UserResponse;
import com.Let.s_Code.nexus.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        // request.getRole() defaults to "USER" via field initializer, but a client sending an
        // explicit `"role": null` in the JSON body overwrites that default with null — guard
        // against it rather than NPE-ing on .toUpperCase().
        // Public registration only provisions standard USER accounts. Admin accounts cannot be self-registered.
        String requestedRole = request.getRole() == null || request.getRole().isBlank()
                ? "USER"
                : request.getRole().trim();

        if (!"USER".equalsIgnoreCase(requestedRole)) {
            throw new IllegalArgumentException("Public registration is only permitted with role USER");
        }

        Role role = Role.USER;

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} ({})", saved.getEmail(), saved.getRole());

        String jwtToken = jwtService.generateToken(saved);
        return AuthResponse.builder()
                .token(jwtToken)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .user(UserResponse.fromEntity(saved))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // The AuthenticationManager securely checks the email and password against the
        // database; a bad credential throws BadCredentialsException, translated centrally.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished from the database"));

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
