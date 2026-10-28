package com.zwf.aisupport.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.ttl}")
    private Long ttl;

    public String createToken(Long userId, String username, String role) {
        return createToken(userId, username, role, 0);
    }

    public String createToken(Long userId, String username, String role, Integer tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttl);

        // Sign the token so the server can verify it was issued by this application.
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    public Claims parseToken(String token) {
        // Verify the signature before trusting any claims inside the token.
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
