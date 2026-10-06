package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import com.Let.s_Code.nexus.dto.RegisterRequest;
import com.Let.s_Code.nexus.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("New@Example.com", "Password123", "USER");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.findByEmail("new@example.com"))
                .thenReturn(Optional.of(User.builder().email("new@example.com").role(Role.USER).build()));

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerNormalizesEmailAndEncodesPassword() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("fake-jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        var response = authService.register(registerRequest);

        assertEquals("fake-jwt", response.getToken());
        assertEquals("new@example.com", response.getUser().getEmail());
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void registerRejectsInvalidRole() {
        RegisterRequest badRole = new RegisterRequest("x@example.com", "Password123", "SUPERADMIN");
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.register(badRole));
    }

    @Test
    void registerRejectsAdminRole() {
        RegisterRequest adminRole = new RegisterRequest("admin@example.com", "Password123", "ADMIN");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.register(adminRole));
    }
}
