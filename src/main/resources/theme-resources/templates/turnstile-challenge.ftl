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
                 data-callback="skycloakTurnstileSolved"></div>
            <noscript>
                <p>${msg("turnstileNoScript", "JavaScript is required to continue.")}</p>
            </noscript>
            <input type="submit"
                   id="kc-turnstile-submit"
                   class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                   value="${msg("doContinue", "Continue")}"
                   disabled/>
        </form>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
        <script>
            function skycloakTurnstileSolved() {
                var form = document.getElementById('kc-turnstile-form');
                var submit = document.getElementById('kc-turnstile-submit');
                if (submit) { submit.disabled = false; }
                if (form) { form.submit(); }
            }
        </script>
    </#if>
</@layout.registrationLayout>
