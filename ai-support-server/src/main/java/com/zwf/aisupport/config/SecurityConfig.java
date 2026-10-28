package com.zwf.aisupport.config;

import com.zwf.aisupport.security.JwtAuthenticationFilter;
import com.zwf.aisupport.security.OAuthLoginSuccessHandler;
import com.zwf.aisupport.security.OAuthLoginFailureHandler;
import com.zwf.aisupport.service.impl.GitHubOAuth2UserService;
import com.zwf.aisupport.service.impl.GoogleOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuthLoginSuccessHandler oauthLoginSuccessHandler;
    private final OAuthLoginFailureHandler oauthLoginFailureHandler;
    private final GitHubOAuth2UserService gitHubOAuth2UserService;
    private final GoogleOidcUserService googleOidcUserService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          OAuthLoginSuccessHandler oauthLoginSuccessHandler,
                          OAuthLoginFailureHandler oauthLoginFailureHandler,
                          GitHubOAuth2UserService gitHubOAuth2UserService,
                          GoogleOidcUserService googleOidcUserService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oauthLoginSuccessHandler = oauthLoginSuccessHandler;
        this.oauthLoginFailureHandler = oauthLoginFailureHandler;
        this.gitHubOAuth2UserService = gitHubOAuth2UserService;
        this.googleOidcUserService = googleOidcUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST APIs authenticate with bearer tokens; OAuth's transient state is kept in a short-lived session.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/oauth/providers",
                                "/api/auth/oauth/exchange",
                                "/oauth2/**",
                                "/login/oauth2/**"
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
                        .requestMatchers(HttpMethod.DELETE, "/api/ai/logs/*").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(gitHubOAuth2UserService)
                                .oidcUserService(googleOidcUserService))
                        .successHandler(oauthLoginSuccessHandler)
                        .failureHandler(oauthLoginFailureHandler)
                )
                // Run JWT parsing before Spring Security checks whether the request is authenticated.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
