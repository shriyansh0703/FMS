package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * THE INPUT TRUST BOUNDARY, DRIVEN INTO FAILURE.
 *
 * <p>Every validator here had its accepting path covered by the route tests and its REJECTING path
 * covered by nothing — which is the wrong way round. A validator that never refuses in a test is
 * indistinguishable from one that cannot refuse.
 *
 * <p>The error CODE matters as much as the rejection. TechExcel has a closed vocabulary and a
 * caller branches on it: a character-filter problem reported as {@code Input_Validation} sends
 * somebody to check a date format.
 */
class VendorValidationTest {

    private static String codeOf(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected a rejection, got none");
        } catch (ApiError e) {
            return (String) e.envelope().get("Error Code");
        }
    }

    @Test
    void aMissingMandatoryFieldNamesItself() {
        ApiError e = org.junit.jupiter.api.Assertions.assertThrows(ApiError.class,
                () -> Vendor.required(Map.of(), "CLIENT_ID", "client id"));

        Map<String, Object> envelope = e.envelope();
        assertThat(envelope).containsEntry("Error Code", "Input_Validation");
        // Field -> list of messages, the shape the vendor's own failure samples show.
        assertThat(envelope.get("Error Description").toString())
                .contains("CLIENT_ID").contains("client id field is required");
    }

    @Test
    void aBlankStringCountsAsMissing() {
        assertThat(codeOf(() -> Vendor.required(Map.of("CLIENT_ID", "   "), "CLIENT_ID", "client id")))
                .isEqualTo("Input_Validation");
    }

    @Test
    void anOverlongFieldIsRejectedAndAFittingOneIsNot() {
        assertThat(codeOf(() -> Vendor.maxLength("CLIENT_ID", "M".repeat(21), 20)))
                .isEqualTo("Input_Validation");
        assertThatCode(() -> Vendor.maxLength("CLIENT_ID", "M".repeat(20), 20))
                .doesNotThrowAnyException();
        // Absent is not too long.
        assertThatCode(() -> Vendor.maxLength("CLIENT_ID", null, 20)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"M00%0", "M000!", "%", "!"})
    void aForbiddenCharacterGetsTheCharacterFilterCode(String value) {
        // NOT Input_Validation. The vendor separates these, and so does this service.
        assertThat(codeOf(() -> Vendor.noSpecials("Client_code", value, "%!")))
                .isEqualTo("SYSTEM_Character_Filter");
    }

    @Test
    void aCharacterForbiddenByOneDocumentIsAllowedByAnother() {
        // The forbidden set is per document and passed per call. '@' is forbidden on the virtual
        // debit report and is part of the documented login password, so a shared union would be
        // wrong in both directions.
        assertThatCode(() -> Vendor.noSpecials("password", "Api@123456", "%!"))
                .doesNotThrowAnyException();
        assertThat(codeOf(() -> Vendor.noSpecials("CLIENT_ID", "M@000", "%@")))
                .isEqualTo("SYSTEM_Character_Filter");
    }

    @ParameterizedTest
    @CsvSource({"31/02/2026", "2026-11-01", "01/13/2025", "notadate", "1/4/2025"})
    void animpossibleOrMisformattedDateIsRefused(String value) {
        // STRICT resolution: 31 February is refused rather than quietly moved to the 28th.
        assertThat(codeOf(() -> Vendor.date("FromDate", value))).isEqualTo("Input_Validation");
    }

    @Test
    void bothDateSeparatorsTheVendorUsesAreAccepted() {
        // Documents say DD/MM/YYYY; New_Interest_Process's own sample uses DD-MM-YYYY.
        assertThat(Vendor.date("Form_Date", "01/11/2024")).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(Vendor.date("Form_Date", "01-11-2024")).isEqualTo(LocalDate.of(2024, 11, 1));
    }

    @Test
    void aBackwardsDateRangeIsRefused() {
        assertThat(codeOf(() -> Vendor.orderedRange("FROM_DATE", "TO_DATE",
                LocalDate.of(2025, 6, 30), LocalDate.of(2025, 4, 1))))
                .isEqualTo("Input_Validation");
    }

    @Test
    void aRangeCrossingAFinancialYearIsRefusedWithTheVendorsOwnMessage() {
        ApiError e = org.junit.jupiter.api.Assertions.assertThrows(ApiError.class,
                () -> Vendor.sameFinancialYear(LocalDate.of(2022, 4, 1), LocalDate.of(2023, 6, 30)));

        assertThat(e.envelope().get("Error Description").toString())
                .contains("The to date must be a date before 2023-03-31 00:00:00.000.");
    }

    @Test
    void aRangeInsideOneFinancialYearIsAccepted() {
        assertThatCode(() -> Vendor.sameFinancialYear(
                LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31))).doesNotThrowAnyException();
        // A financial year that starts before April belongs to the year that ends this March.
        assertThatCode(() -> Vendor.sameFinancialYear(
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 3, 31))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"y", "n", "Yes", "1", "TRUE"})
    void yesNoIsCaseSensitiveAndSingleLetter(String value) {
        assertThat(codeOf(() -> Vendor.yesNo("ShowAllData", value)))
                .isEqualTo("Input_Value_Validation");
    }

    @Test
    void yesNoAcceptsTheTwoDocumentedValuesAndBlank() {
        assertThatCode(() -> Vendor.yesNo("ShowAllData", "Y")).doesNotThrowAnyException();
        assertThatCode(() -> Vendor.yesNo("ShowAllData", "N")).doesNotThrowAnyException();
        // Optional flags arrive empty; that is not a rejection.
        assertThatCode(() -> Vendor.yesNo("ShowMargin", "")).doesNotThrowAnyException();
        assertThatCode(() -> Vendor.yesNo("ShowMargin", null)).doesNotThrowAnyException();
    }

    @Test
    void oneOfRejectsAValueOutsideTheDocumentedSet() {
        assertThat(codeOf(() -> Vendor.oneOf("TransType", "X", "J", "P", "SJ", "R")))
                .isEqualTo("Input_Value_Validation");
        assertThatCode(() -> Vendor.oneOf("TransType", "SJ", "J", "P", "SJ", "R"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Vendor.oneOf("TransType", null, "J", "P")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"25", "20255", "twentytwentyfive", "2025.0"})
    void aYearMustBeFourDigits(String value) {
        assertThat(codeOf(() -> Vendor.year("datayear", value))).isEqualTo("Input_Validation");
    }

    @Test
    void segmentsSplitsOnCommasAndFallsBackWhenBlank() {
        assertThat(Vendor.segments("BSE_CASH, NSE_CASH ,", "X"))
                .containsExactly("BSE_CASH", "NSE_CASH");
        assertThat(Vendor.segments("", "NSE_CASH")).containsExactly("NSE_CASH");
        assertThat(Vendor.segments(null, "NSE_CASH")).containsExactly("NSE_CASH");
    }

    @Test
    void aRowKeepsTheVendorsFieldOrder() {
        // Insertion-ordered, because the samples are read column by column and a reordered
        // response is needlessly hard to diff against the document.
        assertThat(Vendor.row("cocd", "NSE_CASH", "debit", "-50000", "BRANCH_CODE", "MAIN"))
                .containsExactly(
                        Map.entry("cocd", "NSE_CASH"),
                        Map.entry("debit", "-50000"),
                        Map.entry("BRANCH_CODE", "MAIN"));
    }
}
