package com.zwf.aisupport.security;

import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.service.OAuthAuthenticationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    private final OAuthAuthenticationService authenticationService;
    private final OAuthProperties properties;

    public OAuthLoginSuccessHandler(OAuthAuthenticationService authenticationService,
                                    OAuthProperties properties) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            redirectWithError(response);
            return;
        }

        try {
            OAuth2User principal = oauthToken.getPrincipal();
            User user = authenticationService.authenticateProvider(
                    oauthToken.getAuthorizedClientRegistrationId(), principal.getAttributes());
            String code = authenticationService.createExchangeCode(user.getId());
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            response.sendRedirect(UriComponentsBuilder.fromUriString(properties.getFrontendCallbackUrl())
                    .fragment("code=" + code)
                    .build()
                    .encode()
                    .toUriString());
        } catch (RuntimeException exception) {
            log.warn("OAuth sign-in could not be completed for provider {}",
                    oauthToken.getAuthorizedClientRegistrationId());
            redirectWithError(response);
        }
    }

    private void redirectWithError(HttpServletResponse response) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(properties.getFrontendCallbackUrl())
                .fragment("error=oauth_failed")
                .build()
                .encode()
                .toUriString());
    }
}
