package io.skycloak.keycloak.turnstile;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Cluster-level Turnstile configuration, delivered as pod env by the Skycloak operator.
 * Absent or blank env means the extension is dormant (ADR-0030).
 */
public record TurnstileConfig(String siteKey, String secret, URI siteverifyUrl, Duration timeout) {

    public static final String ENV_SITE_KEY = "SKYCLOAK_TURNSTILE_SITE_KEY";
    public static final String ENV_SECRET = "SKYCLOAK_TURNSTILE_SECRET";
    public static final String ENV_SITEVERIFY_URL = "SKYCLOAK_TURNSTILE_SITEVERIFY_URL";
    public static final String ENV_TIMEOUT_MS = "SKYCLOAK_TURNSTILE_TIMEOUT_MS";

    private static final String DEFAULT_SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final long DEFAULT_TIMEOUT_MS = 2_000;

    public static TurnstileConfig fromEnv(Map<String, String> env) {
        String siteKey = trimmed(env.get(ENV_SITE_KEY));
        String secret = trimmed(env.get(ENV_SECRET));
        URI url = URI.create(env.getOrDefault(ENV_SITEVERIFY_URL, DEFAULT_SITEVERIFY_URL));
        Duration timeout = Duration.ofMillis(parseLong(env.get(ENV_TIMEOUT_MS), DEFAULT_TIMEOUT_MS));
        return new TurnstileConfig(siteKey, secret, url, timeout);
    }

    public boolean enabled() {
        return siteKey != null && secret != null;
    }

    private static String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
