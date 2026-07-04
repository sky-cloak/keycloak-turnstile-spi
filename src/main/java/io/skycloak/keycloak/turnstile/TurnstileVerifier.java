package io.skycloak.keycloak.turnstile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Verifies Cloudflare Turnstile tokens against the siteverify endpoint.
 *
 * <p>Hostname binding is the tenancy control (ADR-0031): a token only counts for the
 * hostname it was solved on, compared against the hostname the request arrived at.
 */
public class TurnstileVerifier {

    private final URI endpoint;
    private final String secret;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public TurnstileVerifier(URI endpoint, String secret, Duration timeout) {
        this.endpoint = endpoint;
        this.secret = secret;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    private static String errorCodes(JsonNode body) {
        JsonNode codes = body.path("error-codes");
        if (codes.isArray() && !codes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode code : codes) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(code.asText());
            }
            return sb.toString();
        }
        return "verification_failed";
    }

    public VerificationResult verify(String token, String expectedHostname, String remoteIp) {
        if (token == null || token.isBlank()) {
            return VerificationResult.reject("missing_token");
        }
        String form = "secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&remoteip=" + URLEncoder.encode(remoteIp, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return VerificationResult.failOpen("http_" + response.statusCode());
            }
            JsonNode body;
            try {
                body = mapper.readTree(response.body());
            } catch (Exception e) {
                return VerificationResult.failOpen("unparseable_response");
            }
            if (!body.isObject()) {
                return VerificationResult.failOpen("unparseable_response");
            }
            if (!body.path("success").asBoolean(false)) {
                return VerificationResult.reject(errorCodes(body));
            }
            if (!expectedHostname.equalsIgnoreCase(body.path("hostname").asText())) {
                return VerificationResult.reject("hostname_mismatch");
            }
            return VerificationResult.pass();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.failOpen("interrupted");
        } catch (Exception e) {
            // Timeouts, connect errors, DNS failures: no verdict was obtained.
            return VerificationResult.failOpen(e.getClass().getSimpleName());
        }
    }
}
