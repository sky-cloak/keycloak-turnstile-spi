package io.skycloak.keycloak.turnstile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnstileConfigTest {

    @Test
    void enabledWhenSiteKeyAndSecretPresent() {
        TurnstileConfig config = TurnstileConfig.fromEnv(Map.of(
                "SKYCLOAK_TURNSTILE_SITE_KEY", "0x4AAAAAAASITEKEY",
                "SKYCLOAK_TURNSTILE_SECRET", "0x4AAAAAAASECRET"));

        assertTrue(config.enabled());
        assertEquals("0x4AAAAAAASITEKEY", config.siteKey());
        assertEquals("0x4AAAAAAASECRET", config.secret());
    }

    @Test
    void dormantWhenEnvAbsentOrBlank() {
        // ADR-0030: the JAR ships in every cluster image; without delivered config it must be inert.
        assertFalse(TurnstileConfig.fromEnv(Map.of()).enabled());
        assertFalse(TurnstileConfig.fromEnv(Map.of(
                "SKYCLOAK_TURNSTILE_SITE_KEY", "0x4AAAAAAASITEKEY")).enabled());
        assertFalse(TurnstileConfig.fromEnv(Map.of(
                "SKYCLOAK_TURNSTILE_SITE_KEY", " ",
                "SKYCLOAK_TURNSTILE_SECRET", "0x4AAAAAAASECRET")).enabled());
    }
}
