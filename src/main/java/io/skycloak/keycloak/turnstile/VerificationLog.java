package io.skycloak.keycloak.turnstile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the single-line, structured telemetry emitted per Turnstile verification
 * outcome: a stable marker followed by a JSON object. Kept dependency-free of the
 * flow so it is trivially unit-testable, and deliberately generic so the line is
 * useful to any operator shipping Keycloak logs.
 */
final class VerificationLog {

    /** Stable prefix so log processors can select these lines cheaply. */
    static final String MARKER = "turnstile_verification";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VerificationLog() {
    }

    /**
     * @param outcome  pass | reject | fail_open
     * @param realm    the realm the verification ran in
     * @param clientIp the end user's IP (as seen by Keycloak)
     * @param hostname the request hostname the token was bound to
     * @param reason   the siteverify reason / error code (may be null)
     */
    static String line(String outcome, String realm, String clientIp, String hostname, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("outcome", outcome);
        fields.put("realm", realm);
        fields.put("clientIp", clientIp);
        fields.put("hostname", hostname);
        fields.put("reason", reason);
        try {
            return MARKER + " " + MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            // Telemetry must never break the login path; fall back to a minimal line.
            return MARKER + " {\"outcome\":\"" + outcome + "\"}";
        }
    }
}
