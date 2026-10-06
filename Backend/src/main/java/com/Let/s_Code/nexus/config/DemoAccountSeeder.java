package com.Let.s_Code.nexus.config;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nexus.demo-accounts.enabled", havingValue = "true", matchIfMissing = false)
public class DemoAccountSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${nexus.demo-accounts.admin-email:admin@example.com}")
    private String adminEmail;

    @Value("${nexus.demo-accounts.admin-password:Password123}")
    private String adminPassword;

    @Value("${nexus.demo-accounts.user-email:user@example.com}")
    private String userEmail;

    @Value("${nexus.demo-accounts.user-password:Password123}")
    private String userPassword;

    @Override
    public void run(String... args) {
        seedUserIfMissing(adminEmail, adminPassword, Role.ADMIN);
        seedUserIfMissing(userEmail, userPassword, Role.USER);
    }

    private void seedUserIfMissing(String email, String rawPassword, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isEmpty()) {
            User user = User.builder()
                    .email(normalizedEmail)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .enabled(true)
                    .build();
            userRepository.save(user);
            log.info("Seeded demo account: {} with role {}", normalizedEmail, role);
        }
    }
}
