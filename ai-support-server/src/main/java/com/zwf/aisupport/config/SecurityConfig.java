package com.zwf.aisupport.config;

import com.zwf.aisupport.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF because this backend uses stateless REST APIs.
                .csrf(csrf -> csrf.disable())
                // JWT authentication does not rely on server-side HTTP sessions.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints used before a user has a token.
                        .requestMatchers(
                                "/api/health",
                                "/api/users/register",
                                "/api/users/login",
                                "/api/users/forgot-password"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/ai/logs",
                                "/api/ai/stats",
                                "/api/ai/knowledge-gaps",
                                "/api/documents/categories/stats",
                                "/api/knowledge/articles/categories/stats",
                                "/api/tickets"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/ai/knowledge-gaps/*/article",
                                "/api/knowledge/articles",
                                "/api/knowledge/articles/upload"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/knowledge/articles/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/documents/*/archive",
                                "/api/knowledge/articles/*/archive",
                                "/api/tickets/*/assign"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Run JWT parsing before Spring Security checks whether the request is authenticated.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt automatically adds salt and is suitable for password storage.
        return new BCryptPasswordEncoder();
    }
}
