package com.thinq.fms.integration.communication;

import java.util.Locale;

/**
 * The Communication Service's error vocabulary (caller-integration.md §9).
 *
 * <p><b>The reason is the contract; the HTTP status is not.</b> The doc is explicit: every error
 * body is {@code {"error": "Bad Request", "reason": "contact_detail_invalid"}}, and
 * <i>"`reason` is the entire message. Branch on it; never parse `error`."</i> There are many
 * distinct reasons rather than one code with a detail string, precisely so a caller can branch.
 *
 * <p>An earlier version of this client branched on the status alone, which collapsed three
 * different 403 causes into one and reported every 422 as a vendor outage. Both misdirect whoever
 * investigates: a deactivated caller reported as a missing channel grant sends someone to request
 * a permission they already have.
 */
public enum CommunicationReason {

    // ---- 401: identity ----
    /** No {@code X-Service-Principal}. Usually the mesh or gateway, not this application. */
    NO_CALLER_IDENTITY("no_caller_identity", Kind.IDENTITY),

    // ---- 403: permission. None of these is fixable from our side. ----
    /** The principal is not a registered caller at all. */
    CALLER_NOT_REGISTERED("caller_not_registered", Kind.PERMISSION),
    /** Registered, then deactivated. Distinct from never having been registered. */
    CALLER_INACTIVE("caller_inactive", Kind.PERMISSION),
    /** The channel is not in the granted list. OA-2's failure mode for whatsapp. */
    CHANNEL_NOT_PERMITTED("channel_not_permitted", Kind.PERMISSION),

    // ---- 400: our payload, and we can fix it ----
    MALFORMED_BODY("malformed_body", Kind.OUR_PAYLOAD),
    REQUEST_ID_REQUIRED("request_id_required", Kind.OUR_PAYLOAD),
    CHANNELS_REQUIRED("channels_required", Kind.OUR_PAYLOAD),
    CHANNEL_UNSUPPORTED("channel_unsupported", Kind.OUR_PAYLOAD),
    CHANNEL_REPEATED("channel_repeated", Kind.OUR_PAYLOAD),
    TEMPLATE_KEY_REPEATED("template_key_repeated", Kind.OUR_PAYLOAD),
    TEMPLATE_CHANNEL_LENGTH_MISMATCH("template_channel_length_mismatch", Kind.OUR_PAYLOAD),
    MULTI_TEMPLATE_NOT_SUPPORTED("multi_template_not_supported", Kind.OUR_PAYLOAD),
    RECIPIENT_REQUIRED("recipient_required", Kind.OUR_PAYLOAD),
    RECIPIENT_ADDRESS_INVALID("recipient_address_invalid", Kind.OUR_PAYLOAD),
    CONTACT_DETAIL_INVALID("contact_detail_invalid", Kind.OUR_PAYLOAD),
    /** A PII rule: an address for a channel we did not request is refused, never ignored. */
    CONTACT_DETAIL_FOR_UNREQUESTED_CHANNEL("contact_detail_for_unrequested_channel", Kind.OUR_PAYLOAD),

    // ---- 422: well-formed, and the platform refuses it. Not fixable by editing the payload. ----
    /** No template with that key. A platform-side fix. */
    TEMPLATE_NOT_FOUND("template_not_found", Kind.PLATFORM_CONFIGURATION),
    /** The key exists but has no active version for that channel. Usually a deactivated version. */
    TEMPLATE_NOT_LIVE_ON_CHANNEL("template_not_live_on_channel", Kind.PLATFORM_CONFIGURATION),
    /** Our parameters do not match the template's declared variables exactly. */
    PARAMETER_CONTRACT("parameter_contract", Kind.PLATFORM_CONFIGURATION),

    // ---- 404 / 500 ----
    /** No such notification, <b>or</b> it belongs to another caller. Deliberately indistinguishable. */
    NOT_FOUND("not_found", Kind.NOT_FOUND),
    /** Theirs. On submit this is ambiguous — see {@link #isAmbiguousOnSubmit()}. */
    INTERNAL_ERROR("internal_error", Kind.THEIRS),

    /** A reason this client does not recognise. Treated as an outage, never guessed at. */
    UNRECOGNISED("", Kind.THEIRS);

    /** What a caller can actually do about it, which is the only distinction that matters here. */
    public enum Kind {
        /** The principal header is missing. Infrastructure, not this application. */
        IDENTITY,
        /** Registration or grants. Not fixable from this side; the platform team must act. */
        PERMISSION,
        /** A request this system built wrongly. Ours to fix, and a bug. */
        OUR_PAYLOAD,
        /** Templates and their parameter contracts. A platform-side fix, not an outage. */
        PLATFORM_CONFIGURATION,
        /** No such notification, or not ours. */
        NOT_FOUND,
        /** A failure on their side. */
        THEIRS
    }

    private final String wireValue;
    private final Kind kind;

    CommunicationReason(String wireValue, Kind kind) {
        this.wireValue = wireValue;
        this.kind = kind;
    }

    public String wireValue() {
        return this.wireValue;
    }

    public Kind kind() {
        return this.kind;
    }

    /**
     * Whether a {@code 500} carrying this reason leaves the submission ambiguous.
     *
     * <p>The doc is unusually direct: <i>"A `500` on submit is genuinely ambiguous — the
     * notification may or may not have been accepted. Retry with the <b>same</b> `request_id`."</i>
     *
     * <p>That is the one case where retrying is correct, and it does not contradict the
     * never-retries rule. Nothing retries a <i>send</i>; re-submitting with the same idempotency
     * key resolves whether a send happened at all, and returns the original result if it did.
     */
    public boolean isAmbiguousOnSubmit() {
        return this == INTERNAL_ERROR;
    }

    public static CommunicationReason fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNRECOGNISED;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (CommunicationReason r : values()) {
            if (r != UNRECOGNISED && r.wireValue.equals(v)) {
                return r;
            }
        }
        return UNRECOGNISED;
    }
}
