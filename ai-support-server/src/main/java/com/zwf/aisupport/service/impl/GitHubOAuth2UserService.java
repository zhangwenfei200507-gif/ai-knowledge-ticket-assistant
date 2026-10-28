package com.zwf.aisupport.service.impl;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAIL_LIST =
            new ParameterizedTypeReference<>() { };

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient;

    public GitHubOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User githubUser = delegate.loadUser(request);
        String email = findVerifiedEmail(request);
        Map<String, Object> attributes = new java.util.HashMap<>(githubUser.getAttributes());
        attributes.put("email", email);
        attributes.put("email_verified", true);
        return new DefaultOAuth2User(githubUser.getAuthorities(), attributes, "id");
    }

    private String findVerifiedEmail(OAuth2UserRequest request) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .headers(headers -> headers.setBearerAuth(request.getAccessToken().getTokenValue()))
                    .retrieve()
                    .body(EMAIL_LIST);
            if (emails != null) {
                return emails.stream()
                        .filter(email -> Boolean.TRUE.equals(email.get("verified")))
                        .sorted((first, second) -> Boolean.compare(
                                Boolean.TRUE.equals(second.get("primary")),
                                Boolean.TRUE.equals(first.get("primary"))))
                        .map(email -> email.get("email"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(value -> !value.isBlank())
                        .findFirst()
                        .orElseThrow(this::unverifiedEmail);
            }
            throw unverifiedEmail();
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"), "Could not verify the GitHub account email");
        }
    }

    private OAuth2AuthenticationException unverifiedEmail() {
        return new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info"), "GitHub must provide a verified email address");
    }
}
