package io.skycloak.keycloak.turnstile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationLogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode jsonOf(String line) throws Exception {
        return MAPPER.readTree(line.substring(line.indexOf('{')));
    }

    @Test
    void carriesTheMarkerAndEveryField() throws Exception {
        String line = VerificationLog.line("reject", "my-realm", "203.0.113.5", "app.example.com", "invalid-input-response");

        assertTrue(line.startsWith(VerificationLog.MARKER + " "), "line must start with the stable marker");
        JsonNode json = jsonOf(line);
        assertEquals("reject", json.get("outcome").asText());
        assertEquals("my-realm", json.get("realm").asText());
        assertEquals("203.0.113.5", json.get("clientIp").asText());
        assertEquals("app.example.com", json.get("hostname").asText());
        assertEquals("invalid-input-response", json.get("reason").asText());
    }

    @Test
    void escapesFieldsThatContainJsonSpecialCharacters() throws Exception {
        String line = VerificationLog.line("reject", "realm\"quote", "203.0.113.5", "host\nline", "re\"ason");

        JsonNode json = jsonOf(line);
        assertEquals("realm\"quote", json.get("realm").asText());
        assertEquals("host\nline", json.get("hostname").asText());
        assertEquals("re\"ason", json.get("reason").asText());
    }

    @Test
    void toleratesNullReason() throws Exception {
        String line = VerificationLog.line("pass", "my-realm", "203.0.113.5", "app.example.com", null);

        JsonNode json = jsonOf(line);
        assertEquals("pass", json.get("outcome").asText());
        assertTrue(json.get("reason").isNull(), "a null reason serializes to JSON null");
    }
}
