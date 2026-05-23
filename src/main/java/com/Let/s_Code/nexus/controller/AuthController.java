package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestParam String email,
                                           @RequestParam String password,
                                           @RequestParam String role) {
        authService.registerUser(email, password, role);
        return ResponseEntity.ok("User registered successfully!");
    }
}
