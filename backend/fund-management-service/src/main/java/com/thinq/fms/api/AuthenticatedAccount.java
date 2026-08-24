package com.thinq.fms.api;

import com.thinq.fms.platform.money.AccountRef;

import java.security.Principal;
import java.util.Objects;

/**
 * Resolves the account from the authenticated principal.
 *
 * <p><b>§4.3: authorisation is per object and is never inferred from the path.</b> The account is
 * taken from the token's subject claim and passed as a parameter to every repository method, so a
 * request for another trader's movement finds nothing rather than being forbidden — confirming
 * existence would itself leak.
 *
 * <p>This exists as a named seam rather than as a line inside each controller so that "where does
 * the account come from?" has exactly one answer. A controller that read an account id from a path
 * variable or a body would be doing something this type makes conspicuously absent.
 */
public final class AuthenticatedAccount {

    private AuthenticatedAccount() {
    }

    /**
     * The caller's account.
     *
     * @throws IllegalStateException when there is no principal. The platform gateway rejects an
     *     absent or expired token before this system sees the request, so reaching here without
     *     one means the gateway is misconfigured — which is an outage to fix, not a 401 to render
     */
    public static AccountRef of(Principal principal) {
        Objects.requireNonNull(principal, "principal");
        String subject = principal.getName();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException(
                    "authenticated principal carries no subject; the gateway should have rejected this");
        }
        return AccountRef.of(subject);
    }
}
