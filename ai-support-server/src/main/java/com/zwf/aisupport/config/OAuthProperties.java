package com.zwf.aisupport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;

@Data
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private String frontendCallbackUrl = "http://localhost:5173/auth/callback";
    private long exchangeCodeTtlSeconds = 60;
    private String bootstrapAdminEmails = "";
    private Provider google = new Provider();
    private Provider github = new Provider();

    public boolean isBootstrapAdminEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(bootstrapAdminEmails.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedEmail::equals);
    }

    @Data
    public static class Provider {
        private String clientId;
        private String clientSecret;

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
