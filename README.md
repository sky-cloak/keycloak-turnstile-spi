# keycloak-turnstile-spi

Server-side Cloudflare Turnstile enforcement for Keycloak login, registration and
reset-credentials flows. This is the enforcement half of Skycloak's CAPTCHA Protection
add-on (see the app repo's ADR-0030 and ADR-0031).

## Behavior

- **Fast path**: if the incoming form POST already carries `cf-turnstile-response`
  (the login page rendered the widget inline), the token is verified in the same
  request and no extra step is shown.
- **Fallback**: when no token is present, the authenticator serves its own challenge
  page (`turnstile-challenge.ftl`, rendered through the realm's active theme), so
  realms on customer themes need no theme contract.
- **Hostname binding**: siteverify's returned hostname must match the request's own
  host. Clusters share widgets, so this is the tenancy boundary, not defense in depth.
- **Fail-open on infrastructure errors** (timeout, 5xx, unparseable body): the login
  proceeds, a warning is logged and the `skycloak_turnstile_verifications` counter
  records `outcome=fail_open`. Explicit verdicts (invalid token, hostname mismatch)
  always reject.
- **Dormant without config**: the JAR ships in every Skycloak Keycloak image. Without
  `SKYCLOAK_TURNSTILE_SITE_KEY` and `SKYCLOAK_TURNSTILE_SECRET` in the pod env the
  execution passes through (`attempted`) and does nothing.

## Configuration (delivered by the Skycloak operator as pod env)

| Variable | Meaning |
|---|---|
| `SKYCLOAK_TURNSTILE_SITE_KEY` | Widget site key (public) |
| `SKYCLOAK_TURNSTILE_SECRET` | Widget siteverify secret |
| `SKYCLOAK_TURNSTILE_SITEVERIFY_URL` | Override for tests/dev (default: Cloudflare) |
| `SKYCLOAK_TURNSTILE_TIMEOUT_MS` | siteverify timeout (default 2000) |

## Provider ID

`skycloak-turnstile` — a stable contract with the Skycloak control plane's flow
converge loop. Never change it without a coordinated migration.

## Build

```bash
mvn test                       # unit tests
mvn package                    # target/keycloak-turnstile-spi.jar
mvn -Dkeycloak.version=26.2.0 package   # build against another fleet version
```

The JAR is baked into the Skycloak Keycloak image (`app` repo,
`build/package/keycloak/Dockerfile`) and dropped into `/opt/keycloak/providers/`.
