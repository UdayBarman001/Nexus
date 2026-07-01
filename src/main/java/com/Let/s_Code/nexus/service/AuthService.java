package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.dto.AuthResponse;
import com.Let.s_Code.nexus.dto.LoginRequest;
import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager; // We added this!

    // Your existing register logic (I'll keep it simple here, make sure it matches yours!)
    public void registerUser(String email, String password, String role) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.valueOf(role.toUpperCase()))
                .build();
        userRepository.save(user);
    }

    // 🚀 NEW: The Login Logic
    public AuthResponse login(LoginRequest request) {
        // 1. The AuthenticationManager securely checks the email and password against the database.
        // If the password is wrong, this line immediately throws an error and stops the process.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. If we reach this line, the user is 100% authenticated. We fetch their profile.
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. We print their VIP Wristband!
        var jwtToken = jwtService.generateToken(user);

        // 4. Return the token inside our structured response
        return AuthResponse.builder()
                .token(jwtToken)
                .build();
    }
}
