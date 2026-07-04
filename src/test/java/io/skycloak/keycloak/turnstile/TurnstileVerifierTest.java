package io.skycloak.keycloak.turnstile;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TurnstileVerifierTest {

    private HttpServer server;
    private URI endpoint;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void respondWith(int status, String json) {
        server.createContext("/siteverify", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    private TurnstileVerifier verifier() {
        return new TurnstileVerifier(endpoint, "0x4AAAAAAASECRETSECRETSECRETSECRETSECRET12", Duration.ofSeconds(2));
    }

    @Test
    void validTokenOnExpectedHostnamePasses() {
        respondWith(200, "{\"success\": true, \"hostname\": \"login.customer.example\"}");

        VerificationResult result = verifier().verify("tok-abc", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.PASS, result.outcome());
    }

    @Test
    void explicitInvalidTokenRejectsWithCloudflareReason() {
        respondWith(200, "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}");

        VerificationResult result = verifier().verify("tok-forged", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.REJECT, result.outcome());
        assertEquals("invalid-input-response", result.reason());
    }

    @Test
    void tokenSolvedOnAnotherTenantsHostnameRejects() {
        // Hostname binding (ADR-0031): clusters share widgets, so a token minted on
        // tenant A's hostname must never verify for tenant B.
        respondWith(200, "{\"success\": true, \"hostname\": \"login.other-tenant.example\"}");

        VerificationResult result = verifier().verify("tok-abc", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.REJECT, result.outcome());
        assertEquals("hostname_mismatch", result.reason());
    }

    @Test
    void cloudflareServerErrorFailsOpen() {
        // ADR-0030: bot protection must never become a login outage.
        respondWith(502, "Bad Gateway");

        VerificationResult result = verifier().verify("tok-abc", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.FAIL_OPEN, result.outcome());
    }

    @Test
    void unreachableSiteverifyFailsOpen() {
        server.createContext("/siteverify", exchange -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ignored) {
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        TurnstileVerifier slowTolerance = new TurnstileVerifier(
                endpoint, "0x4AAAAAAASECRETSECRETSECRETSECRETSECRET12", Duration.ofMillis(150));

        VerificationResult result = slowTolerance.verify("tok-abc", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.FAIL_OPEN, result.outcome());
    }

    @Test
    void garbageResponseBodyFailsOpen() {
        respondWith(200, "<html>maintenance page</html>");

        VerificationResult result = verifier().verify("tok-abc", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.FAIL_OPEN, result.outcome());
    }

    @Test
    void missingTokenRejectsWithoutCallingCloudflare() {
        respondWith(200, "{\"success\": true, \"hostname\": \"login.customer.example\"}");

        VerificationResult absent = verifier().verify(null, "login.customer.example", "203.0.113.7");
        VerificationResult blank = verifier().verify("  ", "login.customer.example", "203.0.113.7");

        assertEquals(VerificationResult.Outcome.REJECT, absent.outcome());
        assertEquals("missing_token", absent.reason());
        assertEquals(VerificationResult.Outcome.REJECT, blank.outcome());
        assertEquals("missing_token", blank.reason());
        assertNull(lastRequestBody.get(), "siteverify must not be called for a missing token");
    }
}
