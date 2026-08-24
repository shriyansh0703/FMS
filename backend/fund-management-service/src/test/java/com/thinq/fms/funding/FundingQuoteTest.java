package com.thinq.fms.funding;

import com.thinq.fms.movement.payin.PaymentRoute;
import com.thinq.fms.platform.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/** REQ-202 and REQ-207 — what a trader is told before committing. */
class FundingQuoteTest {

    private static final LocalDate ARRIVES = LocalDate.of(2026, 8, 24);

    @Test
    @DisplayName("a free quote credits exactly what was paid")
    void aFreeQuoteCreditsWhatWasPaid() {
        FundingQuote quote = FundingQuote.free(PaymentRoute.UPI, Money.ofPaise(500_000L), ARRIVES);

        assertThat(quote.amountCredited()).isEqualTo(quote.amountPaid());
        assertThat(quote.hasCost()).isFalse();
        assertThat(quote.routeWasChanged()).isFalse();
    }

    @Test
    @DisplayName("the two figures must reconcile against the cost")
    void theTwoFiguresMustReconcile() {
        // Rule A3 requires both shown together precisely so they can be checked against each other.
        // Figures that do not add up would be presented to a trader as the basis for committing.
        assertThatThrownBy(() -> new FundingQuote(PaymentRoute.NEFT, Money.ofPaise(500_000L),
                Money.ofPaise(499_000L), Money.ofPaise(500L), ARRIVES, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal");
    }

    @Test
    @DisplayName("a quote with a cost states both what is paid and what arrives")
    void aQuoteWithACostStatesBoth() {
        FundingQuote quote = new FundingQuote(PaymentRoute.NEFT, Money.ofPaise(500_000L),
                Money.ofPaise(499_500L), Money.ofPaise(500L), ARRIVES, Optional.empty());

        assertThat(quote.hasCost()).isTrue();
        assertThat(quote.amountPaid()).isEqualTo(Money.ofPaise(500_000L));
        assertThat(quote.amountCredited()).isEqualTo(Money.ofPaise(499_500L));
    }

    @Test
    @DisplayName("an automatic route change is disclosed, not silent")
    void anAutomaticRouteChangeIsDisclosed() {
        // Rule A12: the system changes route on the trader's behalf and has to say so, including any
        // cost the change introduces. A silent switch changes what they are paying for.
        FundingQuote quote = new FundingQuote(PaymentRoute.NEFT, Money.ofPaise(500_000L),
                Money.ofPaise(500_000L), Money.ZERO, ARRIVES, Optional.of(PaymentRoute.UPI));

        assertThat(quote.routeWasChanged()).isTrue();
        assertThat(quote.routeChangedFrom()).contains(PaymentRoute.UPI);
    }

    @Test
    @DisplayName("a change from a route to itself is refused as meaningless")
    void aChangeToItselfIsRefused() {
        assertThatThrownBy(() -> new FundingQuote(PaymentRoute.UPI, Money.ofPaise(1L),
                Money.ofPaise(1L), Money.ZERO, ARRIVES, Optional.of(PaymentRoute.UPI)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a quote cannot exist without an arrival date")
    void aQuoteCannotExistWithoutAnArrivalDate() {
        // Rule A3 forbids using a route whose arrival cannot be stated, so the type has no way to
        // represent one. There is nothing to render as a blank.
        assertThatThrownBy(() -> FundingQuote.free(PaymentRoute.UPI, Money.ofPaise(1L), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a shortfall prompt suggests the shortfall and names the fastest route")
    void aShortfallPromptSuggestsTheShortfall() {
        // Rule A11: a trader funding under a deadline should not be computing the figure or picking
        // a route.
        ShortfallFunding prompt = new ShortfallFunding(Money.ofPaise(250_000L),
                Optional.of(PaymentRoute.UPI), Optional.of(Duration.ofHours(2)));

        assertThat(prompt.suggestedAmount()).isEqualTo(Money.ofPaise(250_000L));
        assertThat(prompt.canBeFunded()).isTrue();
        assertThat(prompt.deadlineKnown()).isTrue();
    }

    @Test
    @DisplayName("an unknown deadline still shows the prompt, without inventing a countdown")
    void anUnknownDeadlineStillShowsThePrompt() {
        ShortfallFunding prompt = new ShortfallFunding(Money.ofPaise(250_000L),
                Optional.of(PaymentRoute.UPI), Optional.empty());

        assertThat(prompt.deadlineKnown()).isFalse();
        assertThat(prompt.canBeFunded()).isTrue();
    }

    @Test
    @DisplayName("no route able to carry the amount is stated rather than attempted")
    void noRouteIsStatedRatherThanAttempted() {
        ShortfallFunding prompt = new ShortfallFunding(Money.ofPaise(250_000L),
                Optional.empty(), Optional.of(Duration.ofHours(2)));

        assertThat(prompt.canBeFunded()).isFalse();
    }

    @Test
    @DisplayName("there is no shortfall prompt without a shortfall")
    void noPromptWithoutAShortfall() {
        assertThatThrownBy(() -> new ShortfallFunding(Money.ZERO, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
