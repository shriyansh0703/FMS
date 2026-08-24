package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;
import com.thinq.fms.movement.payout.PayoutState;
import com.thinq.fms.movement.payout.SettlementReasonCode;
import com.thinq.fms.platform.money.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a withdrawal's outcome says to the trader — REQ-616, 617, 618, 619 and 620.
 *
 * <p>Pure, for the same reason as {@link PayinMessages}: REQ-621 requires the message and the screen
 * to be one computation.
 *
 * <h2>The two rules that are easiest to break here</h2>
 *
 * <p><b>Rule C8 — the bank's reference and ours are different fields and one is never substituted
 * for the other.</b> They look alike, and a trader given ours goes to their bank with a value the
 * bank has never seen. Where the bank's reference is not yet known, the message says so; it does not
 * quietly carry ours in its place.
 *
 * <p><b>REQ-618 — a terminal message states where the money is, never only its status.</b> "Your
 * withdrawal failed" leaves the trader unable to tell whether their money is in their bank, in their
 * trading account, or somewhere in between.
 */
public final class PayoutMessages {

    /**
     * A user-initiated cancellation (REQ-616). Email only.
     *
     * <p>No SMS and no WhatsApp: the trader performed this action themselves and is looking at the
     * screen, so anything louder than a receipt is noise. Rule W3 is the substance — a request
     * reserves nothing, so nothing moved and nothing has to be restored.
     */
    public static MessageSpec cancelledByUser(Money amountRequested, String fmsReference) {
        Objects.requireNonNull(fmsReference, "fmsReference");

        Map<String, String> p = new LinkedHashMap<>();
        p.put("amountRequested", PayinMessages.rupees(amountRequested));
        p.put("fmsReference", fmsReference);
        // Rule W3/W4: nothing was ever held, so there is no figure to restore. Saying "your funds
        // have been returned" would imply they had been taken.
        p.put("nothingMoved", "true");

        return new MessageSpec("WITHDRAWAL_CANCELLED", MessageChannel.EMAIL, Map.copyOf(p));
    }

    /**
     * A terminal end-of-day outcome (REQ-617, 618, 619, 620).
     *
     * <p>One message per outcome rather than one template with a status parameter, because REQ-619
     * requires each outcome distinguished and a shared template is how they converge on generic
     * copy. A partial transfer in particular gets its own message (REQ-617), not a settlement
     * message with two numbers in it.
     *
     * @param bankReference the bank's own transfer reference, absent until the rail supplies one.
     *     Never defaulted to {@code fmsReference} — Rule C8
     * @param deductionReason what accounts for the gap on a partial transfer (Rule W10)
     */
    public static List<MessageSpec> settled(PayoutState outcome,
                                            Money amountRequested,
                                            Money amountSent,
                                            String destinationMasked,
                                            String fmsReference,
                                            Optional<String> bankReference,
                                            Optional<SettlementReasonCode> deductionReason,
                                            boolean whatsappOptedIn) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(fmsReference, "fmsReference");
        Objects.requireNonNull(destinationMasked, "destinationMasked");
        Objects.requireNonNull(bankReference, "bankReference");
        Objects.requireNonNull(deductionReason, "deductionReason");

        if (!outcome.isTerminal() && outcome != PayoutState.INSTRUCTED) {
            throw new IllegalArgumentException(
                    outcome + " is not an end-of-day outcome; nothing is announced until the run decides");
        }
        bankReference.ifPresent(reference -> {
            if (reference.equals(fmsReference)) {
                throw new IllegalArgumentException(
                        "Rule C8: the bank's reference and ours are different values and one is never "
                                + "used for the other; both were '" + reference + "'");
            }
        });

        boolean moneyLeft = amountSent.isPositive();

        Map<String, String> p = new LinkedHashMap<>();
        p.put("amountRequested", PayinMessages.rupees(amountRequested));
        p.put("amountSent", PayinMessages.rupees(amountSent));
        p.put("fmsReference", fmsReference);

        // REQ-618: where the money is, as a fact rather than as a status word.
        p.put("moneyLeftForBank", Boolean.toString(moneyLeft));
        p.put("destinationMasked", moneyLeft ? destinationMasked : "");
        p.put("nothingDeducted", Boolean.toString(!moneyLeft));

        // REQ-620: no bank reference for a movement that was never deducted, and never ours in its
        // place. "Not yet available" is a statement the trader can act on; our reference is not.
        p.put("bankReference", moneyLeft ? bankReference.orElse("") : "");
        p.put("bankReferencePending", Boolean.toString(moneyLeft && bankReference.isEmpty()));

        // REQ-619: the rail being unavailable leaves the request open and cancellable. Every other
        // outcome closes it (Rule W4a).
        boolean staysOpen = outcome == PayoutState.INSTRUCTED;
        p.put("requestClosed", Boolean.toString(!staysOpen));
        p.put("stillCancellable", Boolean.toString(staysOpen));

        if (outcome == PayoutState.PARTLY_PAID) {
            // REQ-617: the gap has to be named. Two figures and no explanation reads as an error.
            p.put("shortfall", PayinMessages.rupees(amountRequested.minus(amountSent)));
            p.put("deductionReason", deductionReason
                    .orElseThrow(() -> new IllegalArgumentException(
                            "a partial transfer must name the deduction accounting for the gap (Rule W10)"))
                    .name());
        } else {
            deductionReason.ifPresent(reason -> p.put("reason", reason.name()));
        }

        Map<String, String> parameters = Map.copyOf(p);
        String key = "WITHDRAWAL_" + outcome.name();

        // Rule C2's matrix: every outcome on email; WhatsApp carries sent and partly-sent only, and
        // only with an opt-in.
        List<MessageSpec> specs = new ArrayList<>();
        specs.add(new MessageSpec(key, MessageChannel.EMAIL, parameters));
        if (whatsappOptedIn && moneyLeft) {
            specs.add(new MessageSpec(key, MessageChannel.WHATSAPP, parameters));
        }
        return List.copyOf(specs);
    }

    private PayoutMessages() {
    }
}
