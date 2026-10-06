package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.dto.AuthResponse;
import com.Let.s_Code.nexus.dto.UserResponse;
import com.Let.s_Code.nexus.exception.DuplicateResourceException;
import com.Let.s_Code.nexus.service.AuthService;
import com.Let.s_Code.nexus.service.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A controller "slice" test: real request/JSON mapping and validation, with the service layer
 * mocked out and the security filter chain disabled (addFilters = false) — we're verifying HTTP
 * contract behavior here, not authentication, which is covered separately.
 */
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.Let.s_Code.nexus.config.SecurityConfig;
import com.Let.s_Code.nexus.config.ApplicationConfig;
import com.Let.s_Code.nexus.config.JwtAuthenticationFilter;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, ApplicationConfig.class, JwtAuthenticationFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private CurrentUserProvider currentUserProvider;

    @Test
    void registerWithValidBodyReturns201() throws Exception {
        AuthResponse fakeResponse = AuthResponse.builder()
                .token("fake-jwt")
                .tokenType("Bearer")
                .expiresInMs(86_400_000L)
                .user(UserResponse.builder().id(1L).email("new@example.com").role(Role.USER).build())
                .build();
        when(authService.register(any())).thenReturn(fakeResponse);

        String body = """
                {"email":"new@example.com","password":"Password123","role":"USER"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(jsonPath("$.user.email").value("new@example.com"));
    }

    @Test
    void registerWithMalformedEmailReturns400WithFieldError() throws Exception {
        String body = """
                {"email":"not-an-email","password":"Password123","role":"USER"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void registerWithWeakPasswordReturns400() throws Exception {
        // No uppercase, no digit — should fail the @Pattern rule before ever reaching the service.
        String body = """
                {"email":"new@example.com","password":"weakpass","role":"USER"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateResourceException("An account with this email already exists."));

        String body = """
                {"email":"existing@example.com","password":"Password123","role":"USER"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithMissingPasswordReturns400() throws Exception {
        String body = """
                {"email":"someone@example.com"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
