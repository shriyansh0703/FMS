package com.thinq.fms.integration.juspay;

import java.util.Objects;

/**
 * Proof that a gateway callback's signature was checked (Stage 11, MEDIUM-2).
 *
 * <p><b>This type exists to make an obligation impossible to overlook.</b> The confirmation path
 * looks an attempt up by its gateway reference alone, with no account scoping — correct, because a
 * callback carries no user session to scope by. But the references are
 * {@code FMS-PAYIN-}{@literal <}id{@literal >} over a {@code BIGSERIAL}, so valid values are
 * sequential and trivially enumerable, and the only thing between an enumerated reference and a
 * state transition that credits money is the callback endpoint's own authentication.
 *
 * <p>That endpoint is not built yet. The constraint used to live in prose in a review document,
 * which whoever writes it has no particular reason to open. Now it is a parameter they cannot avoid
 * constructing, and constructing it requires naming what was verified.
 *
 * <p>This is not itself a signature check — it cannot be, since the verification algorithm belongs
 * to the gateway client. It is the receipt, and a receipt that has to be produced deliberately is
 * harder to forge by accident than a comment is to skip.
 */
public final class VerifiedGatewayCallback {

    private final String scheme;

    private VerifiedGatewayCallback(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Record that the callback's signature was verified against the gateway's key.
     *
     * @param scheme what was checked, for the audit trail — e.g. the signature algorithm and key id.
     *     Blank is refused, because "verified" with nothing behind it is the state this type exists
     *     to prevent
     */
    public static VerifiedGatewayCallback signatureVerified(String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        if (scheme.isBlank()) {
            throw new IllegalArgumentException(
                    "name the verification that was performed; an unnamed one cannot be audited and "
                            + "is indistinguishable from none");
        }
        return new VerifiedGatewayCallback(scheme);
    }

    /**
     * For tests and for a trusted internal caller that is not a gateway callback at all — an
     * operator replaying a confirmation, or a reconciliation sweep reading status directly from
     * Juspay rather than being told by it.
     *
     * <p>Named so that it is obvious in a diff. A reviewer seeing this in the callback endpoint
     * knows immediately that the signature check is missing.
     */
    public static VerifiedGatewayCallback notFromAGatewayCallback(String why) {
        Objects.requireNonNull(why, "why");
        if (why.isBlank()) {
            throw new IllegalArgumentException("say why this is not a gateway callback");
        }
        return new VerifiedGatewayCallback("not-a-callback:" + why);
    }

    public String scheme() {
        return this.scheme;
    }

    @Override
    public String toString() {
        return "VerifiedGatewayCallback[" + this.scheme + "]";
    }
}
