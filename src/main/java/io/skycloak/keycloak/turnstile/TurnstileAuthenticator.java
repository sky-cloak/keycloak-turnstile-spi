package io.skycloak.keycloak.turnstile;

import io.micrometer.core.instrument.Metrics;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Enforces CAPTCHA Protection (ADR-0030) in browser, registration and reset-credentials flows.
 *
 * <p>Fast path: when the incoming form POST already carries a Turnstile token (the login page
 * rendered the widget inline), verification happens in the same request and no extra step is
 * shown. Fallback: the authenticator serves its own challenge page, so realms on customer
 * themes work without any theme contract.
 */
public class TurnstileAuthenticator implements Authenticator {

    public static final String TOKEN_FORM_FIELD = "cf-turnstile-response";
    private static final String CHALLENGE_TEMPLATE = "turnstile-challenge.ftl";
    private static final String METRIC = "skycloak_turnstile_verifications";

    private static final Logger log = Logger.getLogger(TurnstileAuthenticator.class);

    private final TurnstileConfig config;
    private final TurnstileVerifier verifier;

    public TurnstileAuthenticator(TurnstileConfig config) {
        this.config = config;
        this.verifier = config.enabled()
                ? new TurnstileVerifier(config.siteverifyUrl(), config.secret(), config.timeout())
                : null;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (!config.enabled()) {
            // Dormant: bundled in every image, inert without delivered config (ADR-0030).
            log.warnf("Turnstile execution reached but no config delivered; passing through (realm=%s)",
                    context.getRealm().getName());
            context.attempted();
            return;
        }
        process(context, formToken(context));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        process(context, formToken(context));
    }

    private void process(AuthenticationFlowContext context, String token) {
        String hostname = context.getUriInfo().getBaseUri().getHost();
        String remoteIp = context.getConnection().getRemoteAddr();

        VerificationResult result = verifier.verify(token, hostname, remoteIp);
        count(context, result);
        logOutcome(context, result, hostname, remoteIp, token);

        switch (result.outcome()) {
            case PASS -> context.success();
            case FAIL_OPEN -> {
                // No verdict was obtained; never turn bot protection into a login outage.
                log.warnf("Turnstile fail-open (reason=%s realm=%s hostname=%s)",
                        result.reason(), context.getRealm().getName(), hostname);
                context.success();
            }
            case REJECT -> {
                boolean firstSight = token == null || token.isBlank();
                Response page = context.form()
                        .setAttribute("turnstileSiteKey", config.siteKey())
                        .setAttribute("turnstileError", firstSight ? null : result.reason())
                        .createForm(CHALLENGE_TEMPLATE);
                context.challenge(page);
            }
        }
    }

    private static String formToken(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        return form == null ? null : form.getFirst(TOKEN_FORM_FIELD);
    }

    // Emit one structured telemetry line per terminal verification outcome. Only
    // a submitted token is terminal; the initial no-token entry just shows the
    // challenge and is deliberately not recorded.
    private static void logOutcome(AuthenticationFlowContext context, VerificationResult result,
            String hostname, String remoteIp, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            log.info(VerificationLog.line(
                    result.outcome().name().toLowerCase(),
                    context.getRealm().getName(),
                    remoteIp,
                    hostname,
                    result.reason()));
        } catch (Throwable t) {
            // Telemetry must never affect the login path.
            log.debugf("Turnstile outcome log failed: %s", t.getMessage());
        }
    }

    private static void count(AuthenticationFlowContext context, VerificationResult result) {
        try {
            Metrics.counter(METRIC,
                    "outcome", result.outcome().name().toLowerCase(),
                    "realm", context.getRealm().getName()).increment();
        } catch (Throwable t) {
            // Metrics must never affect the login path.
            log.debugf("Turnstile metric increment failed: %s", t.getMessage());
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions.
    }

    @Override
    public void close() {
        // Stateless.
    }
}
