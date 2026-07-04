package io.skycloak.keycloak.turnstile;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class TurnstileAuthenticatorFactory implements AuthenticatorFactory {

    /**
     * Stable contract with the Skycloak control plane's flow converge loop.
     * Never change without a coordinated migration.
     */
    public static final String PROVIDER_ID = "skycloak-turnstile";

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED,
    };

    @Override
    public Authenticator create(KeycloakSession session) {
        return new TurnstileAuthenticator(TurnstileConfig.fromEnv(System.getenv()));
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Skycloak CAPTCHA Protection (Turnstile)";
    }

    @Override
    public String getHelpText() {
        return "Verifies a Cloudflare Turnstile challenge server-side. Managed by Skycloak; "
                + "configuration is delivered as cluster environment variables.";
    }

    @Override
    public String getReferenceCategory() {
        return "captcha";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public void init(Config.Scope config) {
        // Env-configured; nothing to init.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Nothing to post-init.
    }

    @Override
    public void close() {
        // Stateless.
    }
}
