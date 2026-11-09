# keycloak-turnstile-spi

Server-side [Cloudflare Turnstile](https://developers.cloudflare.com/turnstile/) enforcement
for Keycloak login, registration, and reset-credentials flows.

Most CAPTCHA integrations for Keycloak only render a widget in the theme and never verify
the token on the server, which means a bot can simply POST the form and skip the challenge
entirely. This authenticator closes that gap: every protected request is verified against
Cloudflare's `siteverify` endpoint from inside Keycloak, with sane availability semantics.

Built and maintained by [Skycloak](https://skycloak.io), where it powers the managed
CAPTCHA Protection feature. It works on any Keycloak 26.x installation.

## Behavior

- **Fast path**: if the incoming form POST already carries `cf-turnstile-response`
  (your login page renders the widget inline), the token is verified in the same
  request and no extra step is shown.
- **Fallback challenge page**: when no token is present, the authenticator serves its
  own challenge page (`turnstile-challenge.ftl`, rendered through the realm's active
  theme), so it works with any theme and no theme changes are required.
- **Hostname binding**: the hostname returned by `siteverify` must match the host the
  request arrived at. A token solved on one hostname never verifies on another.
- **Fail-open on infrastructure errors** (timeout, 5xx, unparseable response): the
  login proceeds, a warning is logged, and the `skycloak_turnstile_verifications`
  Micrometer counter records `outcome=fail_open` so you can alert on it. Explicit
  verdicts (invalid token, hostname mismatch) always reject. Bot protection should
  never become a login outage.
- **Dormant without config**: with no environment variables set, the execution passes
  through (`attempted`) and does nothing. Safe to ship in an image fleet-wide and
  activate per cluster.

## Install

Download the JAR from the [latest release](../../releases/latest) and drop it into
Keycloak's providers directory, then restart:

```bash
cp keycloak-turnstile-spi.jar /opt/keycloak/providers/
```

Or in a Dockerfile:

```dockerfile
ADD --chmod=0644 https://github.com/sky-cloak/keycloak-turnstile-spi/releases/download/v0.1.0/keycloak-turnstile-spi.jar /opt/keycloak/providers/keycloak-turnstile-spi.jar
```

## Configuration (environment variables)

| Variable | Meaning |
|---|---|
| `SKYCLOAK_TURNSTILE_SITE_KEY` | Turnstile widget site key (public) |
| `SKYCLOAK_TURNSTILE_SECRET` | Turnstile widget siteverify secret |
| `SKYCLOAK_TURNSTILE_SITEVERIFY_URL` | Override for tests/dev (default: Cloudflare) |
| `SKYCLOAK_TURNSTILE_TIMEOUT_MS` | siteverify timeout in ms (default 2000) |

Create the widget (site key + secret) in the
[Cloudflare Turnstile dashboard](https://dash.cloudflare.com/?to=/:account/turnstile) and
make sure your Keycloak hostname is on the widget's hostname list. Cloudflare's test keys
(`1x00000000000000000000AA` always passes, `2x00000000000000000000AB` always fails) work
for local testing.

## Wiring it into your flows

The provider ID is `skycloak-turnstile` and it appears in the admin console as
**Skycloak CAPTCHA Protection (Turnstile)**. For each surface you want to protect:

1. In the Keycloak admin console, go to **Authentication** and copy the built-in flow
   (built-in flows are read-only): `browser`, `registration`, or `reset credentials`.
2. Add an execution to the copy, pick **Skycloak CAPTCHA Protection (Turnstile)**,
   and set it to **Required**. For the browser flow, place it after the
   username/password form so the inline fast path works.
3. Bind the realm to the copied flow (browser flow / registration flow /
   reset credentials flow in the realm settings, or the **Bind flow** action).

If your login theme renders the Turnstile widget inline (a `cf-turnstile` div with your
site key on the login form), users verify in one step. If it does not, users get the
standalone challenge page after submitting the form. Both are enforced server-side.

## Metrics

`skycloak_turnstile_verifications{outcome, realm}` (Micrometer counter) with outcomes
`pass`, `reject`, and `fail_open`. Alert on `fail_open`: it means logins are proceeding
without a verdict because Cloudflare could not be reached.

## Client address

The `remoteip` sent to Cloudflare siteverify is the address Keycloak resolved for the
connection (`AuthenticationFlowContext.getConnection().getRemoteAddr()`). The extension
never reads `X-Forwarded-For`, `CF-Connecting-IP`, `X-Real-IP` or any other request header
itself, so a client cannot choose it by sending a header. Keycloak decides which proxy
headers it honours through its own `proxy-headers` and trusted-proxy settings; configure
those so only your gateway can set the forwarded address.

## Build

```bash
mvn test                                  # unit tests
mvn package                               # target/keycloak-turnstile-spi.jar
mvn -Dkeycloak.version=26.0.7 package     # build against another Keycloak version
```

## License

Apache 2.0
