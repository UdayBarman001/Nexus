package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 256-bit base64 test-only secret; never used outside this test.
        ReflectionTestUtils.setField(jwtService, "secretKey", "AAudHgc8scwtJnTUFRCEMQWHvJhWmVRf3KHS0nP4kxf");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86_400_000L);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("irrelevant")
                .role(Role.USER)
                .build();
    }

    @Test
    void generatedTokenContainsCorrectSubject() {
        String token = jwtService.generateToken(testUser);
        assertEquals("test@example.com", jwtService.extractUsername(token));
    }

    @Test
    void tokenIsValidForTheUserItWasIssuedTo() {
        String token = jwtService.generateToken(testUser);
        assertTrue(jwtService.isTokenValid(token, testUser));
    }

    @Test
    void tokenIsInvalidForADifferentUser() {
        String token = jwtService.generateToken(testUser);
        User otherUser = User.builder().email("someone-else@example.com").role(Role.USER).build();
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String expiredToken = jwtService.generateToken(testUser);
        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(expiredToken, testUser));
    }
}
