package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.RoleRepository;
import com.Let.s_Code.nexus.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(String email, String rawPassword, String roleName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Error: Email is already in use!");
        }

        Role userRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Error: Role not found!"));

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        newUser.setRole(userRole);

        return userRepository.save(newUser);
    }
}
