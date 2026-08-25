package com.thinq.backoffice.brokerage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.thinq.backoffice.platform.ApiError;
import com.thinq.backoffice.platform.Vendor;

/**
 * The brokerage and charges READS, answered from the vendor's own documents.
 *
 * <p>Response column names are reproduced with the vendor's spelling, including
 * {@code REMESHIRE} — which is how the document spells "remisier" throughout, in the endpoint path
 * as well as the columns. It is not corrected here: a caller binds on the exact key.
 */
@Component
public class BrokerageMock {

    // ------------------------------------------------------------- brk_remeshire_view

    /** {@code POST /api/entry/brk_remeshire_view} */
    public Map<String, Object> brkRemeshireView(Map<String, Object> body) {
        String fromDate = Vendor.required(body, "FROM_DATE", "from date");
        String toDate = Vendor.required(body, "TO_DATE", "to date");
        String companyCode = Vendor.required(body, "COMPANY_CODE", "company code");
        String clientId = Vendor.optional(body, "CLIENT_ID");
        String branch = Vendor.optional(body, "BRANCH");
        String scripSymbol = Vendor.optional(body, "SCRIP_SYMBOL");
        String remisierCode = Vendor.optional(body, "Remshire_Code");
        String policy = Vendor.optional(body, "POLICY");
        String relationManager = Vendor.optional(body, "RELATION_MANAGER");

        for (String[] pair : new String[][]{
                {"CLIENT_ID", clientId}, {"FROM_DATE", fromDate}, {"TO_DATE", toDate},
                {"COMPANY_CODE", companyCode}, {"BRANCH", branch}, {"SCRIP_SYMBOL", scripSymbol},
                {"Remshire_Code", remisierCode}, {"POLICY", policy},
                {"RELATION_MANAGER", relationManager}}) {
            Vendor.noSpecials(pair[0], pair[1], "%!");
        }
        Vendor.maxLength("CLIENT_ID", clientId, 20);
        Vendor.maxLength("COMPANY_CODE", companyCode, 20);
        Vendor.maxLength("BRANCH", branch, 20);
        Vendor.maxLength("SCRIP_SYMBOL", scripSymbol, 20);
        Vendor.maxLength("Remshire_Code", remisierCode, 175);
        Vendor.maxLength("POLICY", policy, 50);
        Vendor.maxLength("RELATION_MANAGER", relationManager, 20);

        LocalDate from = Vendor.date("FROM_DATE", fromDate);
        LocalDate to = Vendor.date("TO_DATE", toDate);
        Vendor.orderedRange("FROM_DATE", "TO_DATE", from, to);

        // The document's own failure sample. A remisier code is looked up in the master, so an
        // unknown one is a rejection rather than an empty report — the caller has asked about
        // somebody who does not exist and should be told so.
        if (!Vendor.blank(remisierCode) && remisierCode.length() < 3) {
            throw new ApiError("Input_Value_Validation", "Invalid Remeshire Code");
        }

        // "As per policy display the column name in response" — the columns below are the ones the
        // document's own REMISIER SHARING REPORT sample carries. A different policy upstream
        // returns different columns, which is why nothing here treats them as a fixed schema.
        List<Map<String, Object>> rows = List.of(Vendor.row(
                "A1", "1",
                "TOTAL_BROKERAGE", "323.04",
                "REMESHIRE_NAME", "JYOTI GUPTA ",
                "SUBREMESHIRE_CODE", "",
                "REMESHIRE1_BROKERAGE", "274.59",
                "SUBREMESHIRE2_CODE", null,
                "REMESHIRE2_NAME", null,
                "REMESHIRE2_BROKERAGE", "0.00",
                "REMESHIRE3_NAME", null,
                "REMESHIRE3_BROKERAGE", "0.00",
                "OWNBRK", "48.45"));
        return ApiError.ok(rows);
    }

    // ------------------------------------------------------------ new_interest_process

    /** {@code POST /api/entry/new_interest_process} */
    public Map<String, Object> newInterestProcess(Map<String, Object> body) {
        // The field is spelled Form_Date in the vendor's table AND in its sample. It is a typo for
        // "From", and it is theirs — accepting only the corrected spelling would reject the
        // document's own example, so the typo is authoritative and From_Date is accepted beside it.
        String formDate = Vendor.optional(body, "Form_Date");
        if (Vendor.blank(formDate)) {
            formDate = Vendor.required(body, "From_Date", "form date");
        }
        String toDate = Vendor.required(body, "TO_DATE", "to date");
        String reportType = Vendor.required(body, "Report_Type", "report type");
        String clientId = Vendor.optional(body, "Client_Id");
        String branchCode = Vendor.optional(body, "Branch_Code");
        String clientFilter = Vendor.optional(body, "Client_Filter");

        for (String[] pair : new String[][]{
                {"Form_Date", formDate}, {"TO_DATE", toDate}, {"Client_Id", clientId},
                {"Branch_Code", branchCode}, {"Client_Filter", clientFilter},
                {"Report_Type", reportType}}) {
            Vendor.noSpecials(pair[0], pair[1], "%@");
        }
        Vendor.maxLength("Client_Id", clientId, 10);
        Vendor.maxLength("Branch_Code", branchCode, 10);
        Vendor.oneOf("Client_Filter", clientFilter, "1", "2");
        Vendor.oneOf("Report_Type", reportType, "1", "2", "3", "4", "5");

        LocalDate from = Vendor.date("Form_Date", formDate);
        LocalDate to = Vendor.date("TO_DATE", toDate);
        Vendor.orderedRange("Form_Date", "TO_DATE", from, to);

        // The document specifies response columns for REPORT TYPE 1 only ("API response parameter
        // details as follows when select report type 1"). Answering types 2-5 with type 1's columns
        // would be inventing four contracts nobody has written down.
        if (!"1".equals(reportType)) {
            throw new ApiError("Input_Value_Validation",
                    "Report_Type " + reportType + " is accepted upstream, but its response columns "
                            + "are not documented. Only Report_Type 1 (Interest Report) is served in "
                            + "mock mode; set techexcel.live=true for the rest.");
        }
        return ApiError.ok(interestRows(clientId, branchCode, from, to));
    }

    /**
     * The type 1 Interest Report row, all fifty-one documented columns in documented order.
     *
     * <p>The nulls are load-bearing. Every {@code Rem_*} column comes back null on a row with no
     * remisier sharing, and a caller that treated "absent" and "present and null" as the same thing
     * would break the first time it met a shared row. Nothing here drops a null key.
     */
    private List<Map<String, Object>> interestRows(String clientId, String branchCode,
                                                   LocalDate from, LocalDate to) {
        String client = Vendor.blank(clientId) ? "18836" : clientId;
        String branch = Vendor.blank(branchCode) ? "HO" : branchCode;
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Vendor.row(
                "Grp", "Grp1",
                "TradeDate", from.atStartOfDay().format(Vendor.SQL_TS),
                "COCD", "Grp1",
                "Client_Id", client,
                "Funds_Dr", "0.00",
                "Funds_Cr", "12000.00",
                "Sec_B1", "0.00",
                "Sec_Applicable", "0.00",
                "FD_C", "0.00",
                "OTH_D", "0.00",
                "Total_Collatral_E", "12000.00",
                "Total_Margin", "0.00",
                "Pick_Margin", "0.00",
                "Applicable_Margin", "0.00",
                "Margin_Short", "0.00",
                "DR_INTEREST", "18.00",
                "MIN_CASH_MARGIN", "0.00",
                "Funds_Dr_Interest", "0.00",
                "MarginShort_Dr_Interest", "0.00",
                "Total_Interest", "0.00",
                "Posted", "1",
                "From_Date", from.atStartOfDay().format(Vendor.SQL_TS),
                "To_Date", to.atStartOfDay().format(Vendor.SQL_TS),
                "PostLogic", null,
                "MARGIN_INTEREST_PER", "18.00",
                "ADD_INTEREST", "0.00",
                "FIX_INT_AMT", "0.00",
                "INT_AMOUNT_RANGE", "0.00",
                "Pick_Margin_No", "0",
                "Funds_Cr_Interest", "0.00",
                "CR_INTEREST", "50.00",
                "MTF_DR_INTEREST", "19.00",
                "MTF_Funds_Dr", "0.00",
                "MTF_CASH_Funds_Dr", "0.00",
                "MTF_Net_Dr", "0.00",
                "MTF_Funds_Dr_Interest", "0.00",
                "MTF_NON_Funds_Cr", "12000.00",
                "EPI_E", "0.00",
                "Int_cocd", null,
                "Rem_Sharing_Typ", null,
                "Remeshire_Active_Sharing", null,
                "Remeshire_Code", null,
                "Rem_Funds_Dr_Interest", null,
                "Rem_DR_INTEREST", null,
                "Rem_MarginShort_Dr_Interest", null,
                "Rem_MARGIN_INTEREST_PER", null,
                "Rem_MTF_DR_INTEREST", null,
                "Rem_MTF_Funds_Dr_Interest", null,
                "Rem_Total_Interest", null,
                "Rem_PostLogic", null,
                "CLIENT_NAME", client,
                "Mobile_NO", "9999",
                "PAN_NO", "ANFPG5000Z",
                "BRANCH_CODE", branch,
                "FAMILY_GROUP", "CHANDRAKANT SHAH"));
        return rows;
    }
}
