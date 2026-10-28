package com.zwf.aisupport.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientConfigurationTest {

    private final OAuthClientConfiguration configuration = new OAuthClientConfiguration();

    @Test
    void providersWithoutCredentialsAreNotRegistered() {
        ClientRegistrationRepository repository = configuration.clientRegistrationRepository(new OAuthProperties());

        assertThat(repository.findByRegistrationId("google")).isNull();
        assertThat(repository.findByRegistrationId("github")).isNull();
    }

    @Test
    void onlyProvidersWithBothCredentialsAreRegistered() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGoogle().setClientId("google-client");
        properties.getGoogle().setClientSecret("google-secret");
        properties.getGithub().setClientId("github-client");

        ClientRegistrationRepository repository = configuration.clientRegistrationRepository(properties);

        assertThat(repository.findByRegistrationId("google").getScopes()).contains("openid", "email");
        assertThat(repository.findByRegistrationId("github")).isNull();
    }
}
