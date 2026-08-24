package com.thinq.fms.ledgerview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export's wording, and the guard that stops a new entry kind shipping as a machine key.
 *
 * <p>{@link StatementCopy} is a second source of copy alongside the client's, which is a real cost.
 * The first test below is what bounds it: a kind added without wording fails the build rather than
 * appearing in a trader's statement as {@code ENTRY_SOMETHING}.
 */
class StatementCopyTest {

    private final StatementCopy copy = StatementCopy.withDefaults();
    private final EntryDescriptionMapper mapper = ConfiguredEntryDescriptionMapper.withDefaults();

    @ParameterizedTest
    @EnumSource(EntryKind.class)
    @DisplayName("every entry kind has wording — a new one cannot ship as a copy key")
    void everyKindHasWording(EntryKind kind) {
        // The whole reason this test exists. Adding a kind to the enum and forgetting the table
        // would put a machine key in the one artifact a trader keeps and submits.
        assertThat(this.copy.covers(kind))
                .as("StatementCopy has no wording for %s", kind)
                .isTrue();
    }

    @Test
    @DisplayName("no wording is a copy key in disguise")
    void wordingIsNeverACopyKey() {
        for (EntryKind kind : EntryKind.values()) {
            String text = this.copy.describe(new EntryDescriptionMapper.Description(
                    kind, "ENTRY_" + kind.name(), Map.of(), "VCH-1", false));

            assertThat(text).doesNotStartWith("ENTRY_").doesNotContain("_");
            assertThat(text).as("%s wording is blank", kind).isNotBlank();
        }
    }

    @Test
    @DisplayName("an unmapped entry says a description is unavailable, not the reference")
    void unavailableSaysSoRatherThanSubstitutingTheReference() {
        // Rule L3's edge case. The raw reference is already its own column; putting it in the
        // description column would be exactly the substitution Rule L3 forbids.
        String text = this.copy.describe(EntryDescriptionMapper.Description.unavailable("VCH-77"));

        assertThat(text).isEqualTo("Description not available");
        assertThat(text).doesNotContain("VCH-77");
    }

    @Test
    @DisplayName("the wording reaches the CSV through the real mapper")
    void wordingFlowsThroughTheMapper() {
        // End to end through the production mapper rather than a constructed Description, so a
        // mismatch between the mapper's kinds and the copy table's keys is caught.
        LedgerEntry payin = new LedgerEntry("VCH-1", "NSE_CASH", LocalDate.of(2026, 8, 20),
                com.thinq.fms.platform.money.Money.ZERO,
                com.thinq.fms.platform.money.Money.ofPaise(100L),
                com.thinq.fms.platform.money.Money.ofPaise(100L),
                "Fund transfer received", "R", null, null, null, false, null, null);

        assertThat(this.copy.describe(this.mapper.describe(payin))).isEqualTo("Funds added");
    }
}
