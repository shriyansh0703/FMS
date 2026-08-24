package com.thinq.fms.integration.communication;

import java.util.Map;
import java.util.Objects;

/**
 * One notification, as the Communication Service accepts it.
 *
 * <p>One channel per call. The wire contract takes arrays because the multi-channel shape is
 * already there, but the service refuses more than one element today, so Rule C1's "SMS and
 * email at minimum" is <b>two submissions with two request ids that fail independently</b> —
 * which is why V26 stores two rows rather than one row with two statuses.
 *
 * @param requestId    the idempotency key, and one per <i>intent</i> rather than per attempt.
 *                     This is {@code fms_message_intent.id}
 * @param templateKey  which message, by key and never by version id. A template is authored per
 *                     channel, so the SMS and email wordings are different keys rather than one
 *                     key on two channels
 * @param channel      where it goes, paired with the template key
 * @param address      the recipient's address for that channel
 * @param parameters   template variables, which must match the template's declared set exactly
 */
public record NotificationSubmission(
        String requestId,
        String templateKey,
        MessageChannel channel,
        String address,
        Map<String, String> parameters) {

    public NotificationSubmission {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(address, "address");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));

        if (requestId.isBlank()) {
            // The service answers 400 request_id_required. Caught here so the failure names the
            // caller's bug rather than arriving as a vendor error.
            throw new IllegalArgumentException("requestId is the idempotency key and cannot be blank");
        }
        requireDeliverable(channel, address);
    }

    /**
     * Refuse an address that would be accepted, sent, billed and misdelivered (§6).
     *
     * <p>Every failure this guards against is silent. The platform validates shallowly by design and
     * the providers accept anything address-shaped, so none of these produces an error anywhere —
     * the message simply goes to someone else, and the delivery log records a success.
     *
     * <p>Three specific traps, all named in the contract:
     *
     * <ul>
     *   <li><b>A bare national number.</b> Ten digits is inside the platform's 8–15 bound, so it is
     *       accepted and billed. It is just the wrong number.
     *   <li><b>Parentheses around the country code.</b> The platform strips punctuation but keeps
     *       {@code +} only at position 0, so {@code (+91) 9451 740121} normalises to a number that
     *       has silently lost its plus.
     *   <li><b>A lower-cased email local part.</b> The platform folds the domain only; the local
     *       part is case-sensitive per RFC 5321. The contract calls this the most commonly shipped
     *       normalisation bug and notes that it misdelivers rather than mis-sorting.
     * </ul>
     *
     * <p><b>The address is passed through exactly as given.</b> Nothing here trims, folds or
     * rewrites it — this method only refuses. That is deliberate: a validator that "helpfully"
     * normalises is how the third trap gets shipped, and the correct handling of a case-sensitive
     * local part is to leave it alone.
     */
    private static void requireDeliverable(MessageChannel channel, String address) {
        if (address.isBlank()) {
            throw new IllegalArgumentException(
                    "a blank address is refused as contact_detail_invalid; there is no directory to "
                            + "fall back on and what is supplied is where the message goes");
        }

        switch (channel) {
            case SMS, WHATSAPP -> {
                if (address.indexOf('(') >= 0 || address.indexOf(')') >= 0) {
                    throw new IllegalArgumentException(
                            "a phone number must not wrap its country code in parentheses: the "
                                    + "platform keeps '+' only at position 0, so '" + address
                                    + "' would reach the provider without its plus. Send plain E.164.");
                }
                if (!E164.matcher(address).matches()) {
                    throw new IllegalArgumentException(
                            "a phone number must be E.164 with its country code, as +<country><number> "
                                    + "with 8 to 15 digits in total; got '" + address
                                    + "'. A bare national number is accepted by the platform, sent, and "
                                    + "billed — it is simply the wrong number, and nothing reports it.");
                }
            }
            case EMAIL -> {
                if (!EMAIL_SHAPE.matcher(address).matches()) {
                    throw new IllegalArgumentException(
                            "an email address needs one '@' with something either side and a dot in "
                                    + "the domain; got '" + address + "'");
                }
            }
        }
    }

    /**
     * E.164: a leading plus, then 8 to 15 digits.
     *
     * <p>The leading plus is the part that carries the requirement. Without it the platform has no
     * numbering plan to guess a country from and does not add one, which is the failure that has no
     * error attached to it.
     */
    private static final java.util.regex.Pattern E164 =
            java.util.regex.Pattern.compile("\\+[1-9][0-9]{7,14}");

    /**
     * Deliberately as shallow as the platform's own check: one {@code @} with something either side
     * and a dot in the domain. It catches a value that was never an address. The provider remains
     * the authority on whether an address is real, and will accept and bill a well-formed one that
     * does not exist.
     */
    private static final java.util.regex.Pattern EMAIL_SHAPE =
            java.util.regex.Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");
}
