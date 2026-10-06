package com.Let.s_Code.nexus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Let.s_Code.nexus.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Instant;

/** Makes unauthenticated (401) and forbidden (403) responses return the same JSON shape as every other API error. */
@Configuration
public class SecurityHandlers {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException ex) ->
                write(res, HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.", req);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.access.AccessDeniedException ex) ->
                write(res, HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", req);
    }

    private void write(HttpServletResponse res, HttpStatus status, String message, HttpServletRequest req) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .build();
        res.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
