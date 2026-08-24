package com.thinq.fms.movement.payin;

import com.thinq.fms.integration.juspay.PayinGateway;
import com.thinq.fms.integration.juspay.JuspayOrder;
import com.thinq.fms.integration.juspay.PayinOutcome;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.NoRouteAvailableException;
import com.thinq.fms.platform.error.NoVerifiedSourceException;
import com.thinq.fms.platform.money.AccountRef;
import com.thinq.fms.platform.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Starting payins and applying what the gateway reports (REQ-203 to REQ-207, REQ-614).
 *
 * <h2>The two rules that shape this class</h2>
 *
 * <p><b>Rule A6 — a payment is recorded once, however many times it is confirmed.</b> Repeat
 * confirmations are an <i>expected</i> condition, not an exceptional one: the gateway retries on
 * anything that does not look like success. So {@link #onGatewayConfirmation} is idempotent and
 * returns normally on a repeat, having changed nothing. V22's partial unique index on
 * {@code gateway_payment_ref} is the guarantee underneath; this is the behaviour above it.
 *
 * <p><b>Rule A5 — money exists in the balances only once it is confirmed.</b> An in-flight attempt
 * is visible and attributable and affects no balance. Nothing here credits anything: confirmation
 * records the state, and the balance moves when RMS pushes it.
 *
 * <h2>What a failure must never become</h2>
 *
 * <p>Rule A9b: a bank that has not answered is its own state, because the recovery is the opposite
 * of a failure's — wait, and specifically do not retry. {@link PayinOutcome#UNKNOWN} maps to the
 * same state for the same reason: an unrecognised status is far more likely to be a new success
 * variant than a new failure, and telling a trader their payment failed when it succeeded is the
 * more expensive error.
 */
public final class PayinOrchestrator {

    private final com.thinq.fms.messaging.MessageOutbox outbox;
    private final com.thinq.fms.messaging.MessageLadder ladder;
    private final com.thinq.fms.messaging.MessagePreferences preferences;

    private final PayinAttemptRepository attempts;
    private final RouteSelector routes;
    private final RouteCapLedger caps;
    private final PayinGateway gateway;
    private final ProfileClient profile;
    private final Clock clock;

    public PayinOrchestrator(PayinAttemptRepository attempts,
                             RouteSelector routes,
                             RouteCapLedger caps,
                             PayinGateway gateway,
                             ProfileClient profile,
                             Clock clock,
                             com.thinq.fms.messaging.MessageOutbox outbox,
                             com.thinq.fms.messaging.MessageLadder ladder,
                             com.thinq.fms.messaging.MessagePreferences preferences) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.routes = Objects.requireNonNull(routes, "routes");
        this.caps = Objects.requireNonNull(caps, "caps");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Begin an attempt.
     *
     * @throws NoRouteAvailableException when no configured route has headroom today. The refusal
     *     carries the remaining figures, because REQ-701 requires the headroom stated rather than
     *     a generic "that did not work"
     */
    public StartedPayin start(AccountRef account, Money amount, String returnUrl) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a payin is for a positive amount; got " + amount);
        }

        // REQ-203: Profile is read at the moment of the attempt, never from a cached list
        // (PR-28). A trader with no verified account cannot fund, and the reason is recorded.
        if (this.profile.accountsOf(account).stream().noneMatch(a -> a.verified())) {
            throw new NoVerifiedSourceException(
                    "no verified bank account on record for this trader");
        }

        RouteSelector.Selection selection = this.routes.select(account, amount);
        if (!selection.isSelected()) {
            throw new NoRouteAvailableException(selection.unavailable());
        }
        SelectedRoute route = selection.selected();

        Instant now = this.clock.instant();
        PayinAttempt attempt = this.attempts.save(new PayinAttempt(
                0L, account, amount, route.route(), now, PayinState.INITIATED, 0));

        // The reference derives from the id, so it exists the moment the row does — and it is
        // committed here, before the gateway is called, rather than after the call returns. The
        // call may create an order and then time out; if the reference is not already on the row,
        // the confirmation that follows finds nothing and real money is refused. Committing it
        // first costs one write and makes the attempt recoverable no matter how createOrder ends.
        // DO NOT MAKE THIS METHOD @Transactional, and do not wrap a call to it in one.
        //
        // The save below must COMMIT before createOrder runs. A single transaction spanning the
        // vendor call defers it, and when createOrder throws, the rollback removes the row
        // entirely — leaving no record of a payment the gateway may already be holding. That is
        // worse than the bug this ordering fixes, which at least left a row behind.
        //
        // Verified rather than assumed: running start() inside a TransactionTemplate leaves zero
        // rows after the timeout. PayinDurabilityTest reads the row back on its own statement
        // after start() has thrown, so it fails if this ordering is ever collapsed.
        //
        // If this method later needs a transaction for other work, the vendor call goes outside
        // it, or this write gets its own REQUIRES_NEW boundary.
        String reference = gatewayReferenceFor(attempt);
        attempt.willUseGatewayReference(reference);
        attempt = this.attempts.save(attempt);

        JuspayOrder order = this.gateway.createOrder(reference, account, amount, returnUrl);

        attempt.sentToGateway(order.orderId());
        PayinAttempt started = this.attempts.save(attempt);

        // REQ-611's single chase, queued now against the attempt rather than set on a timer. It
        // asserts the payin is still unresolved, so a deposit that confirms inside the window drops
        // it instead of telling the trader their completed payment is pending.
        this.outbox.write(this.ladder.forPendingPayin(account, attemptReference(started),
                started.startedAt(), this.preferences.whatsappOptedIn(account)));

        return new StartedPayin(started, route, order.paymentLink());
    }

    /**
     * Apply a gateway confirmation.
     *
     * <p>Callable repeatedly with the same reference. The second call finds the attempt already in
     * the reported state and returns {@code false} having changed nothing — which is what Rule A6
     * requires and what stops a retrying gateway producing two credits.
     *
     * @return true when this call changed the attempt
     * @throws FmsInvariantException when the reference is unknown. That is not a repeat and not a
     *     late arrival: it is a confirmation for a payment this system never started, and
     *     recording it would credit money against nothing
     */
    public boolean onGatewayConfirmation(String gatewayPaymentRef, PayinOutcome reported,
                                        com.thinq.fms.integration.juspay.VerifiedGatewayCallback verified) {
        Objects.requireNonNull(gatewayPaymentRef, "gatewayPaymentRef");
        Objects.requireNonNull(reported, "reported");
        // Stage 11, MEDIUM-2. This method authenticates nothing: it finds the attempt by reference
        // alone, and the references are sequential over a BIGSERIAL. Requiring the receipt makes the
        // caller's obligation impossible to overlook — the callback endpoint cannot be written
        // without deciding, in code, what it verified.
        Objects.requireNonNull(verified,
                "a gateway confirmation must carry proof its signature was verified; this method "
                        + "credits money against an enumerable reference and checks no identity itself");

        PayinAttempt attempt = this.attempts.findByGatewayRef(gatewayPaymentRef)
                .orElseThrow(() -> new FmsInvariantException("payin_confirmation_unknown_reference",
                        "a confirmation arrived for a payment reference this system did not issue"));

        boolean changed = attempt.recordOutcome(reported, this.clock.instant());
        if (!changed) {
            return false;
        }

        // Headroom is consumed by money that actually arrived, not by an attempt that was made.
        // Consuming it on failure would refuse a trader's retry against a limit they never used.
        if (attempt.state() == PayinState.CONFIRMED) {
            this.caps.record(attempt.account(), attempt.route(), attempt.amount());
        }
        this.attempts.save(attempt);

        // REQ-614: the outcome's own message, queued against the attempt in the same unit of work
        // that recorded the outcome (REQ-622). An awaiting outcome queues nothing — Rule A9b holds
        // it open rather than announcing it, and the 30-minute chase already covers that window.
        this.outbox.write(this.ladder.forPayinOutcome(attempt.account(), reported,
                attemptReference(attempt), this.clock.instant(),
                this.preferences.whatsappOptedIn(attempt.account())));

        return true;
    }

    /**
     * Reverse a confirmed payin (REQ-206, Rule A10).
     *
     * <p>The account may fall into debit as a result. That is handed to the health module as a
     * debt rather than prevented by refusing the reversal — the money was not the trader's to keep,
     * and refusing would leave a wrong balance standing to avoid an awkward one.
     */
    public PayinAttempt reverse(AccountRef account, long attemptId) {
        PayinAttempt attempt = this.attempts.findFor(account, attemptId)
                .orElseThrow(() -> new FmsInvariantException("payin_not_found",
                        "no such payin attempt for this account"));

        attempt.reverse(this.clock.instant());
        return this.attempts.save(attempt);
    }

    /**
     * Alternatives to offer beside "Try Again" after a failure (Rule A9d).
     *
     * <p>Only routes this system can execute and whose remaining headroom covers the amount. Where
     * nothing qualifies, the list is empty and <i>Try Again</i> stands alone — an empty answer is
     * correct here, not a gap to fill.
     */
    public List<PaymentRoute> alternativesAfterFailure(AccountRef account, Money amount, PaymentRoute failed) {
        return this.routes.alternativesFor(account, amount, failed);
    }

    /** REQ-201, Rule A1: what the trader last added, or empty on a first deposit. */
    public Optional<Money> lastSuccessfulDeposit(AccountRef account) {
        return this.attempts.lastConfirmedFor(account).map(PayinAttempt::amount);
    }

    /**
     * The reference sent to the gateway, derived from the attempt's own id.
     *
     * <p>Deterministic and unique per attempt, so a retried {@code createOrder} for the same
     * attempt reuses one reference rather than creating a second payment the gateway would treat
     * as unrelated.
     */
    /**
     * The occurrence reference a payin's messages are keyed on.
     *
     * <p>Deliberately the attempt, not the gateway reference: {@code fms_intent_once} must bound
     * messages to one per attempt per channel, and the gateway reference is absent until the row is
     * written while the attempt id never is.
     */
    private static String attemptReference(PayinAttempt attempt) {
        return "PAYIN-" + attempt.id();
    }

    private static String gatewayReferenceFor(PayinAttempt attempt) {
        return "FMS-PAYIN-" + attempt.id();
    }

    /** A started attempt and what the client needs to complete it. */
    public record StartedPayin(PayinAttempt attempt, SelectedRoute route, String paymentLink) {
    }
}
