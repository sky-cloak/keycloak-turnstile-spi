package io.skycloak.keycloak.turnstile;

/**
 * Outcome of a Turnstile siteverify check.
 *
 * <p>PASS and REJECT are explicit verdicts. FAIL_OPEN means the verdict could not be
 * obtained (infrastructure error); per ADR-0030 the caller lets the flow proceed but
 * the outcome stays distinct so it can be counted and alerted on.
 */
public record VerificationResult(Outcome outcome, String reason) {

    public enum Outcome {
        PASS,
        REJECT,
        FAIL_OPEN
    }

    public static VerificationResult pass() {
        return new VerificationResult(Outcome.PASS, "verified");
    }

    public static VerificationResult reject(String reason) {
        return new VerificationResult(Outcome.REJECT, reason);
    }

    public static VerificationResult failOpen(String reason) {
        return new VerificationResult(Outcome.FAIL_OPEN, reason);
    }
}
