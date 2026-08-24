package com.thinq.fms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A request to withdraw (lld-backend.md §4.2).
 *
 * <p>Validation here is <b>shape only</b> — that a positive amount and a destination were supplied.
 * Whether the amount is within the withdrawable figure, and whether the destination is verified at
 * this instant, are domain rules and live in {@code PayoutOrchestrator}. §4.3 draws that line
 * deliberately: a rule enforced only at the edge is a rule a second caller can skip.
 *
 * <p>Note what is <b>not</b> here. No account identifier: the account is resolved from the
 * authenticated principal, never from the body, so a caller cannot name someone else's.
 *
 * <p><b>There is no {@code @Positive} on the amount, deliberately.</b> An earlier version carried
 * one on an {@code amountPaise()} accessor, where it did nothing: the method is not a JavaBean
 * getter, so Bean Validation never saw it, and the annotation implied a guard that did not exist.
 * A non-positive amount is refused by {@code PayoutOrchestrator}, which is where §4.3 puts it
 * anyway — and being refused in one real place beats being annotated in two, one of which is
 * decorative.
 */
@Schema(description = "Create the account's single open withdrawal request.")
public record PayoutRequestCommand(
        @NotNull(message = "amount is required")
        @Valid
        @Schema(description = "How much to withdraw, in paise.", requiredMode = Schema.RequiredMode.REQUIRED)
        MoneyDto amount,

        @NotBlank(message = "destinationRef is required")
        @Schema(description = "Profile's reference for a verified bank account of this trader.",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "acc-4471")
        String destinationRef) {
}
