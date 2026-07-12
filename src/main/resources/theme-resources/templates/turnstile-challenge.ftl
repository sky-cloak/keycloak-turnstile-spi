<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("turnstileChallengeTitle", "Quick security check")}
    <#elseif section = "form">
        <form id="kc-turnstile-form" action="${url.loginAction}" method="post">
            <#if turnstileError??>
                <div class="alert alert-error ${properties.kcAlertClass!} pf-m-danger" role="alert">
                    <span class="${properties.kcAlertTitleClass!}">${msg("turnstileChallengeFailed", "Verification failed, please try again.")}</span>
                </div>
            </#if>
            <div class="cf-turnstile"
                 data-sitekey="${turnstileSiteKey}"
                 data-callback="skycloakTurnstileSolved"
                 data-error-callback="skycloakTurnstileError"
                 data-expired-callback="skycloakTurnstileExpired"></div>
            <div id="kc-turnstile-error" class="alert alert-error ${properties.kcAlertClass!} pf-m-danger" role="alert" style="display:none; margin-top:12px;">
                <span class="${properties.kcAlertTitleClass!}">${msg("turnstileChallengeFailed", "Verification failed, please try again.")}</span>
            </div>
            <noscript>
                <p>${msg("turnstileNoScript", "JavaScript is required to continue.")}</p>
            </noscript>
            <#-- Fallback only. The managed widget submits automatically on success,
                 so there is no visible submit button in the happy path; this stays
                 hidden until a hard error, when it lets a stuck user re-run the
                 check instead of being dead-ended on a refresh. -->
            <button type="button"
                    id="kc-turnstile-retry"
                    class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                    style="display:none; margin-top:12px;"
                    onclick="skycloakTurnstileRetry()">${msg("turnstileRetry", "Try again")}</button>
        </form>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
        <script>
            // Managed widget UX: on success we submit immediately (no Continue
            // button to click). Expiry silently re-challenges; a hard error surfaces
            // a retry so the user is never stuck.
            function skycloakTurnstileSolved() {
                var form = document.getElementById('kc-turnstile-form');
                if (form) { form.submit(); }
            }
            function skycloakTurnstileExpired() {
                if (window.turnstile) { window.turnstile.reset(); }
            }
            function skycloakTurnstileError() {
                var err = document.getElementById('kc-turnstile-error');
                var retry = document.getElementById('kc-turnstile-retry');
                if (err) { err.style.display = 'block'; }
                if (retry) { retry.style.display = 'block'; }
            }
            function skycloakTurnstileRetry() {
                var err = document.getElementById('kc-turnstile-error');
                var retry = document.getElementById('kc-turnstile-retry');
                if (err) { err.style.display = 'none'; }
                if (retry) { retry.style.display = 'none'; }
                if (window.turnstile) { window.turnstile.reset(); }
            }
        </script>
    </#if>
</@layout.registrationLayout>
