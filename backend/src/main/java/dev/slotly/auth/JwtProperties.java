package dev.slotly.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("slotly.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        // HS256 needs a key of at least 256 bits
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("slotly.jwt.secret must be at least 32 bytes");
        }
    }
}
