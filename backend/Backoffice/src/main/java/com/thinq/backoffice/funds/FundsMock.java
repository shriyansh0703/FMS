package com.thinq.backoffice.funds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.thinq.backoffice.platform.ApiError;
import com.thinq.backoffice.platform.Vendor;

/**
 * {@code virtual_debit_report}, answered in the shape the vendor's document specifies.
 *
 * <p>The response rows carry exactly the six documented columns, in the documented order:
 * {@code cocd}, {@code account_text}, {@code vdate}, {@code debit}, {@code Narration},
 * {@code BRANCH_CODE}. THE CASING IS THE VENDOR'S, INCONSISTENCY INCLUDED — four lower case, one
 * capitalised, one upper — because a caller binds on the exact key and a tidied-up name would work
 * here and fail against the real back office.
 */
@Component
public class FundsMock {

    /** {@code POST /api/entry/virtual_debit_report} */
    public Map<String, Object> virtualDebitReport(Map<String, Object> body) {
        String fromDate = Vendor.required(body, "FROMDATE", "fromdate");
        String toDate = Vendor.required(body, "TODATE", "todate");
        String companyCode = Vendor.required(body, "COMPANY_CODE", "company code");
        String dataYear = Vendor.required(body, "datayear", "datayear");
        String clientId = Vendor.optional(body, "CLIENT_ID");
        String branchCode = Vendor.optional(body, "BRANCH_CODE");

        Vendor.noSpecials("FROMDATE", fromDate, "%@");
        Vendor.noSpecials("TODATE", toDate, "%@");
        Vendor.noSpecials("CLIENT_ID", clientId, "%@");
        Vendor.noSpecials("BRANCH_CODE", branchCode, "%@");
        Vendor.noSpecials("COMPANY_CODE", companyCode, "%@");
        Vendor.noSpecials("datayear", dataYear, "%@");
        Vendor.maxLength("CLIENT_ID", clientId, 10);
        Vendor.maxLength("BRANCH_CODE", branchCode, 20);
        Vendor.maxLength("COMPANY_CODE", companyCode, 175);
        Vendor.year("datayear", dataYear);

        LocalDate from = Vendor.date("FROMDATE", fromDate);
        LocalDate to = Vendor.date("TODATE", toDate);
        Vendor.orderedRange("FROMDATE", "TODATE", from, to);

        // The vendor's own failure sample for this endpoint is Input_Value_Validation / "No data
        // found", and an empty period is the one case that reaches it deterministically. Returning
        // an empty success array instead would hide a documented rejection a caller must handle.
        List<Map<String, Object>> rows = rows(clientId, companyCode, branchCode, from, to);
        if (rows.isEmpty()) {
            throw new ApiError("Input_Value_Validation", "No data found");
        }
        return ApiError.ok(rows);
    }

    /**
     * One virtual debit row per segment, dated inside the requested period.
     *
     * <p>Same request in, same rows out. A mock whose output moves between two identical calls is
     * one nobody can diff a change against.
     */
    private List<Map<String, Object>> rows(String clientId, String companyCode, String branchCode,
                                           LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String client = Vendor.blank(clientId) ? "0000056743" : clientId;
        String branch = Vendor.blank(branchCode) ? "MAIN" : branchCode;
        LocalDate vdate = from.plusDays(Math.min(12, java.time.temporal.ChronoUnit.DAYS.between(from, to)));

        for (String segment : Vendor.segments(companyCode, "NSE_CASH")) {
            rows.add(Vendor.row(
                    "cocd", segment,
                    "account_text", client + "-AMRUT NAVIN SHAH",
                    "vdate", vdate.format(Vendor.DMY),
                    "debit", "-50000",
                    "Narration", "BEING PAID TO VICHARE COURIER TWDS. MONTH OF & SERVICE TAX ON IT",
                    "BRANCH_CODE", branch));
        }
        return rows;
    }
}
