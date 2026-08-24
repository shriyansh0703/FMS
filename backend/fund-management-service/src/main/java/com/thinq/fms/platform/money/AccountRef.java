package com.thinq.fms.platform.money;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A trader's account identity, as the Fund Management System holds it.
 *
 * <p>This is a <b>UCC code</b> and nothing else. It is deliberately not a PAN, not a bank
 * account number, not a BO ID and not a CKYC number — the ratified taxonomy's rule R4
 * forbids a regulated identifier from reaching an event property, <i>and forbids a hash of
 * one</i>, so an account identity that could carry a regulated value would eventually leak
 * into analytics as a pseudonymous key.
 *
 * <p>There is no foreign key to an account table anywhere in this system's schema, because
 * this system does not own the account. This type is validated at the service boundary
 * from the authenticated principal; referential integrity would require defining a table
 * we have no right to define.
 */
public record AccountRef(String ucc) {

    /**
     * UCC codes in this estate are alphanumeric and bounded by the back office's own
     * {@code Client_code} field width of 20.
     */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    public AccountRef {
        Objects.requireNonNull(ucc, "ucc");
        if (!VALID.matcher(ucc).matches()) {
            // Parse, don't validate: an AccountRef that exists is an AccountRef that is
            // well-formed, so no downstream code needs to re-check it.
            throw new IllegalArgumentException(
                    "UCC must be 1-20 alphanumeric characters; got a value of length " + ucc.length());
        }
    }

    public static AccountRef of(String ucc) {
        return new AccountRef(ucc);
    }

    @Override
    public String toString() {
        return this.ucc;
    }
}
