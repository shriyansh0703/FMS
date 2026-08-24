package com.thinq.fms.integration.profile;

import java.util.Objects;

/**
 * One bank account Profile has verified the trader holds.
 *
 * <p><b>There is no unmasked account number on this type, and there must never be one.</b>
 * Profile PR-31 masks server-side and treats unmasking as a distinct, re-authenticated, audited
 * call — so FMS never receives the full number, and a field to hold it would be a place for one
 * to arrive. Profile PR-32 additionally forbids an unmasked number anywhere in a statement
 * export; that requirement is satisfied structurally here rather than by remembering to redact.
 *
 * @param reference Profile's opaque identifier for the account. This is what
 *                  {@code fms_payout_request.destination_ref} pins at request time under
 *                  Rule W12, so a later change to the trader's accounts never redirects a
 *                  request already in flight
 * @param masked    the display form, e.g. {@code ••••4471}. Recognisable to the trader and not
 *                  worth reading over their shoulder (Support SP-11). Stored alongside the
 *                  reference on the request because a payout message months later must still
 *                  name the account without a live Profile call
 * @param bankName  for display alongside the masked number
 * @param primary   whether this is the trader's default source and destination (REQ-706)
 * @param verified  whether Profile considers it proven <b>at this instant</b>
 */
public record VerifiedBankAccount(
        String reference,
        String masked,
        String bankName,
        boolean primary,
        boolean verified) {

    public VerifiedBankAccount {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(masked, "masked");

        // A "masked" value containing a long run of digits is an unmasked number that slipped
        // through, and it would be persisted onto the payout request and rendered into a
        // message. Refused at construction rather than trusted, because the whole point of
        // PR-31 is that this system never holds the full value.
        if (masked.chars().filter(Character::isDigit).count() > 6) {
            throw new IllegalArgumentException(
                    "masked account number carries too many digits to be masked; PR-31 masks server-side");
        }
    }
}
