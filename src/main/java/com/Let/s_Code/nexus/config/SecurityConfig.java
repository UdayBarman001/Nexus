package com.Let.s_Code.nexus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (Cross-Site Request Forgery)
                // We can disable this because we are using stateless JWTs, not session cookies.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure which endpoints are public vs private
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Anyone can register or login
                        .anyRequest().authenticated()                // EVERYTHING else requires a valid JWT
                )

                // 3. Make the session STATELESS
                // This tells Spring: "Do NOT save the user's session in memory. Force them to send the JWT every time."
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Tell Spring to use our database authentication provider
                .authenticationProvider(authenticationProvider)

                // 5. Put our custom Bouncer (JWT Filter) at the front door!
                // It must run BEFORE the standard UsernamePassword filter.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
