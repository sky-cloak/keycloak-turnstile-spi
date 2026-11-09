package io.skycloak.keycloak.turnstile;

import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.RealmModel;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.keycloak.common.ClientConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnstileAuthenticatorTest {

    private HttpServer server;
    private URI endpoint;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"success\": true, \"hostname\": \"login.customer.example\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    @Test
    void remoteIpIsKeycloaksResolvedAddressNotAClientHeader() {
        AtomicBoolean headersRead = new AtomicBoolean();
        AtomicBoolean succeeded = new AtomicBoolean();

        UriInfo uriInfo = stub(UriInfo.class, (p, m, a) -> {
            if (m.getName().equals("getBaseUri")) {
                return URI.create("https://login.customer.example/");
            }
            throw new UnsupportedOperationException(m.getName());
        });
        ClientConnection connection = stub(ClientConnection.class, (p, m, a) -> {
            if (m.getName().equals("getRemoteAddr")) {
                return "203.0.113.7";
            }
            throw new UnsupportedOperationException(m.getName());
        });
        RealmModel realm = stub(RealmModel.class, (p, m, a) -> {
            if (m.getName().equals("getName")) {
                return "acme";
            }
            throw new UnsupportedOperationException(m.getName());
        });
        // The request carries forged forwarding headers; any attempt to read headers is recorded.
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.add(TurnstileAuthenticator.TOKEN_FORM_FIELD, "tok-abc");
        HttpRequest request = stub(HttpRequest.class, (p, m, a) -> {
            if (m.getName().equals("getDecodedFormParameters")) {
                return form;
            }
            if (m.getName().equals("getHttpHeaders")) {
                headersRead.set(true);
            }
            throw new UnsupportedOperationException(m.getName());
        });
        AuthenticationFlowContext context = stub(AuthenticationFlowContext.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getUriInfo": return uriInfo;
                case "getConnection": return connection;
                case "getRealm": return realm;
                case "getHttpRequest": return request;
                case "success": succeeded.set(true); return null;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });

        TurnstileConfig config = new TurnstileConfig(
                "0x4AAAAAAASITEKEY", "0x4AAAAAAASECRETSECRETSECRETSECRETSECRET12", endpoint, Duration.ofSeconds(2));
        new TurnstileAuthenticator(config).action(context);

        assertTrue(succeeded.get());
        assertEquals(false, headersRead.get());
        String body = lastRequestBody.get();
        assertTrue(body.contains("&remoteip=203.0.113.7"), body);
    }
}
