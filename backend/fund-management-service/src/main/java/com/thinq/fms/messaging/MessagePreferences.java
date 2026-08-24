package com.thinq.fms.messaging;

import com.thinq.fms.platform.money.AccountRef;

/**
 * The optional channels a trader has agreed to (REQ-624, REQ-626).
 *
 * <p><b>Only the optional ones.</b> SMS and email carry regulatory messages and Rule C13 forbids
 * suppressing those on any preference, which is why this interface has no method that could answer
 * "may I send an SMS" — the question is not askable, so no caller can accidentally honour an answer
 * to it.
 */
@FunctionalInterface
public interface MessagePreferences {

    /**
     * Whether the trader has explicitly opted in to WhatsApp.
     *
     * <p>Absence of an opt-in is the default and is not an error. REQ-604 requires the step dropped
     * silently, without blocking or delaying anything else.
     */
    boolean whatsappOptedIn(AccountRef account);

    /** No opt-in on record, which is every account until REQ-624's capture is built. */
    static MessagePreferences noOptIn() {
        return account -> false;
    }
}
