package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.platform.money.Money;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a payin's outcome says to the trader — REQ-612, 613, 614 and 615.
 *
 * <p>Pure. Every figure arrives as an argument, because REQ-621 requires the message and the screen
 * to be the same computation rather than two that agree, and the only way to guarantee that is for
 * this class to be incapable of fetching anything itself.
 *
 * <h2>Two rules that constrain the parameters more than they first appear</h2>
 *
 * <p><b>REQ-612 forbids a balance figure in a confirmation, and REQ-615 requires two figures in
 * it.</b> Those read as contradictory and are not: REQ-101 defines three distinct figures and Rule
 * B12 gives each one definition. "Balance" is the settled ledger balance. Available margin and the
 * withdrawable figure are margin figures, and REQ-615 names both explicitly. So the confirmation
 * carries the two margin figures and never the ledger balance. This reading is recorded because the
 * alternative — omitting the margin figures — would make REQ-613 and REQ-615 unimplementable, and a
 * future reader will hit the same apparent conflict.
 *
 * <p><b>The full account number never appears.</b> Only {@code sourceMasked}, which is the last four
 * digits and the only form stored.
 */
public final class PayinMessages {

    /**
     * The confirmation (REQ-612, 613, 615). Email only.
     *
     * <p>Email is the only channel that can carry the effect on more than one figure (§2), and the
     * effect is the point: a payin raises available margin and leaves the withdrawable figure
     * untouched, which is Rule B4's *money added today* term subtracting exactly what was added.
     * Saying only "received" invites the trader to try withdrawing it.
     *
     * @param withdrawableFrom the date the money becomes withdrawable, which REQ-613 requires
     *     stated rather than left to be inferred from a settlement rule
     */
    public static MessageSpec confirmed(Money amount,
                                        String sourceMasked,
                                        PaymentRoute route,
                                        Money availableMarginAfter,
                                        Money availableMarginChange,
                                        Money withdrawableAfter,
                                        LocalDate withdrawableFrom) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(withdrawableFrom, "withdrawableFrom");
        requireMasked(sourceMasked);

        Map<String, String> p = new LinkedHashMap<>();
        p.put("amount", rupees(amount));
        p.put("sourceMasked", sourceMasked);
        p.put("route", route.name());
        p.put("availableMargin", rupees(availableMarginAfter));
        p.put("availableMarginChange", rupees(availableMarginChange));
        p.put("withdrawable", rupees(withdrawableAfter));
        // REQ-615: name the term responsible for the withdrawable figure not moving. Without it the
        // trader reads two figures, one of which did not change, and no reason why.
        p.put("withdrawableUnchangedTerm", "ADDED_TODAY");
        p.put("withdrawableFrom", withdrawableFrom.toString());

        return new MessageSpec("PAYIN_CONFIRMED", MessageChannel.EMAIL, Map.copyOf(p));
    }

    /**
     * A payin that did not complete (REQ-614), one message per Rule A9a outcome.
     *
     * <p>The six outcomes are not interchangeable and the recovery differs for each, which is why
     * the template key carries the outcome rather than a single failure template carrying it as a
     * parameter — a shared template invites shared copy, and shared copy is how "no answer from the
     * bank" comes to read as "failed".
     *
     * @param alternativeRoutes routes that can actually be executed for this amount today (Rule
     *     A9d). Offering a route without headroom sends the trader into a second refusal
     */
    public static MessageSpec failed(PayinOutcome outcome,
                                     Money amount,
                                     PaymentRoute attemptedRoute,
                                     List<PaymentRoute> alternativeRoutes,
                                     boolean whatsappOptedIn) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(attemptedRoute, "attemptedRoute");
        Objects.requireNonNull(alternativeRoutes, "alternativeRoutes");

        if (outcome == PayinOutcome.CONFIRMED) {
            throw new IllegalArgumentException(
                    "CONFIRMED is not a failure; use confirmed() so the effect on both figures is stated");
        }

        Map<String, String> p = new LinkedHashMap<>();
        p.put("amount", rupees(amount));
        p.put("route", attemptedRoute.name());
        p.put("alternativeRoutes", String.join(",", alternativeRoutes.stream().map(Enum::name).toList()));

        // Rule A9b: an unresolved payment is unknown, not failed, and the instruction is the
        // opposite of a failure's — do not pay again.
        boolean unresolved = outcome.isAwaitingResolution();
        p.put("resolution", unresolved ? "UNKNOWN" : "FAILED");
        p.put("doNotRetry", Boolean.toString(unresolved));

        // Rule C5: a decline may have landed after the bank debited. Asserting that nothing was
        // taken is the one thing that must not be said, because it is sometimes false and the
        // trader stops looking for the money.
        p.put("refundConditional", Boolean.toString(mayHaveDebited(outcome)));

        // Rule A9c: whose problem it was. A trader told "declined" for our outage calls their bank.
        p.put("causeIsOurs", Boolean.toString(outcome == PayinOutcome.SERVICE_UNREACHABLE));

        MessageChannel channel = whatsappOptedIn ? MessageChannel.WHATSAPP : MessageChannel.EMAIL;
        return new MessageSpec("PAYIN_" + outcome.name(), channel, Map.copyOf(p));
    }

    /**
     * Whether this outcome could have left the bank having debited the trader.
     *
     * <p>True for anything the bank itself decided late or did not answer. False only where the
     * payment demonstrably never reached the bank — a user cancelling before approval, or our own
     * service being unreachable before submission.
     */
    private static boolean mayHaveDebited(PayinOutcome outcome) {
        return switch (outcome) {
            case CANCELLED_BY_USER, SERVICE_UNREACHABLE -> false;
            default -> true;
        };
    }

    /** REQ-612: last four digits, and the full number is never stored or rendered. */
    private static void requireMasked(String sourceMasked) {
        Objects.requireNonNull(sourceMasked, "sourceMasked");
        String digits = sourceMasked.replaceAll("\\D", "");
        if (digits.length() > 4) {
            throw new IllegalArgumentException(
                    "a payin message carries the last four digits only; '" + sourceMasked
                            + "' has " + digits.length() + " digits and would disclose the account number");
        }
    }

    static String rupees(Money amount) {
        return Objects.requireNonNull(amount, "amount").toVendorDecimal().toPlainString();
    }

    private PayinMessages() {
    }
}
