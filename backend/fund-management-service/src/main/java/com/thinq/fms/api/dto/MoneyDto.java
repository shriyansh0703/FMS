package com.thinq.fms.api.dto;

import com.thinq.fms.platform.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Money on the wire.
 *
 * <p><b>An integer count of paise and a currency, never a decimal.</b> The ratified taxonomy's
 * rule R5 and HLD §9.1c both require it, and putting a float in a published schema is worse than
 * putting one in code: every client generated from that schema inherits the defect, and they are
 * not ours to fix.
 *
 * <p>The currency travels with the figure rather than being assumed. A bare number in a response
 * is a unit a client has to guess at, and the guess is silent when it is wrong.
 */
@Schema(description = "A monetary amount as an integer number of paise. Never a decimal or a float.")
public record MoneyDto(
        @Schema(description = "Amount in paise. 12345 is ₹123.45.", example = "12345")
        long paise,

        @Schema(description = "ISO 4217 currency code.", example = "INR")
        String currency) {

    private static final String INR = "INR";

    public MoneyDto {
        Objects.requireNonNull(currency, "currency");
    }

    public static MoneyDto of(Money amount) {
        return new MoneyDto(amount.paise(), INR);
    }

    public Money toMoney() {
        if (!INR.equals(this.currency)) {
            // This system holds one currency. Accepting another would mean a figure whose unit
            // nothing downstream checks, added to figures that are all paise.
            throw new IllegalArgumentException("only " + INR + " is supported; got " + this.currency);
        }
        return Money.ofPaise(this.paise);
    }
}
