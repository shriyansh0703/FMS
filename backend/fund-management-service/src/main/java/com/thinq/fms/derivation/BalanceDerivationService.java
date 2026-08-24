package com.thinq.fms.derivation;

import com.thinq.fms.platform.money.AccountRef;

/**
 * Produces the withdrawable figure and its explanation for one account.
 *
 * <p>An interface rather than a class so that everything downstream — the payout orchestrator, the
 * message dispatcher, the summary endpoint — depends on the capability rather than on the
 * assembler, the calculator and four vendor gateways underneath it.
 *
 * <p>That matters more than usual right now: the concrete implementation is blocked on
 * {@link MarginSource} having a working Noren gateway, which is halted on two missing vendor
 * inputs. Everything above this interface is buildable and testable in the meantime, which is the
 * point of depending on an abstraction rather than on what supplies it.
 */
public interface BalanceDerivationService {

    /**
     * Derive the withdrawable figure for one account.
     *
     * <p><b>Never throws for an unavailable figure.</b> A source being unreachable, a calendar
     * being unnominated, or the derivation disagreeing with RMS are all product states the trader
     * is shown — see {@link WithdrawableVerdict} — and turning them into exceptions would make the
     * summary endpoint's job a catch block. Callers that must refuse an action check the verdict.
     *
     * @param context why the figure is being computed, which decides whether a snapshot is
     *     persisted. A payout decision is disputed months later and is stamped; a page view is not
     */
    DerivationResult derive(AccountRef account, DerivationContext context);

    /** Why {@link #derive} was called, and therefore whether the result is retained as evidence. */
    enum DerivationContext {
        /** A trader creating a withdrawal request. Persisted — Rule W11. */
        PAYOUT_REQUEST,
        /** The end-of-day run deciding what to send. Persisted. */
        SETTLEMENT,
        /** Generating a message, so REQ-621's figures match the screen's. Persisted. */
        MESSAGE,
        /** Rendering a screen. Not persisted: a view is not disputed months later. */
        VIEW
    }
}
