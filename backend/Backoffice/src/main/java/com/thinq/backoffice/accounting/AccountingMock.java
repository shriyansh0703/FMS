package com.thinq.backoffice.accounting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.thinq.backoffice.platform.ApiError;
import com.thinq.backoffice.platform.Vendor;

/**
 * {@code ledger}, answered in the shape the vendor's document specifies.
 *
 * <p><b>THE RESPONSE IS AN ARRAY, THOUGH THE DOCUMENT'S SAMPLE SHOWS ONE OBJECT.</b> A ledger is by
 * definition many entries, and the sample is a single row printed on its own. A caller written
 * against an object would break the first time a client had two transactions — which is every real
 * client — so the array is the shape that is right in both readings. Verify this against the real
 * back office before going live; it is the one place this mock chooses between two readings of the
 * document.
 *
 * <p>The row below carries all fifty-eight documented columns in documented order, nulls included.
 * A caller that treated "absent" and "present and null" as the same thing would break on the first
 * row with no counter code, so nothing here drops a null key.
 */
@Component
public class AccountingMock {

    /** {@code POST /api/entry/ledger} */
    public Map<String, Object> ledger(Map<String, Object> body) {
        String clientCode = Vendor.required(body, "Client_code", "client code");
        String fromDate = Vendor.required(body, "FromDate", "from date");
        String toDate = Vendor.required(body, "ToDate", "to date");
        String showAllData = Vendor.required(body, "ShowAllData", "show all data");
        String cocdList = Vendor.optional(body, "COCDLIST");
        String showMargin = Vendor.optional(body, "ShowMargin");
        String mergeCompany = Vendor.optional(body, "Merge_Company");
        String transType = Vendor.optional(body, "TransType");

        for (String[] pair : new String[][]{
                {"Client_code", clientCode}, {"FromDate", fromDate}, {"ToDate", toDate},
                {"COCDLIST", cocdList}, {"ShowAllData", showAllData}, {"ShowMargin", showMargin},
                {"Merge_Company", mergeCompany}, {"TransType", transType}}) {
            Vendor.noSpecials(pair[0], pair[1], "%!");
        }
        Vendor.maxLength("Client_code", clientCode, 20);
        Vendor.maxLength("COCDLIST", cocdList, 20);
        Vendor.yesNo("ShowAllData", showAllData);
        Vendor.yesNo("ShowMargin", showMargin);
        Vendor.yesNo("Merge_Company", mergeCompany);
        Vendor.oneOf("TransType", transType, "J", "P", "SJ", "R");

        LocalDate from = Vendor.date("FromDate", fromDate);
        LocalDate to = Vendor.date("ToDate", toDate);
        // The document's own note: "From_Date & To_Date as per financial year wise or date range
        // between financial year". Its failure sample is exactly this rejection, timestamp shape
        // included, so it is reproduced rather than paraphrased.
        Vendor.sameFinancialYear(from, to);

        return ApiError.ok(rows(clientCode, cocdList, from, transType));
    }

    /** One opening-balance-adjacent receipt row per requested segment. */
    private List<Map<String, Object>> rows(String clientCode, String cocdList, LocalDate from,
                                           String transType) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String voucherDate = from.plusMonths(1).atStartOfDay().format(Vendor.SQL_TS);
        String voucherDateShort = from.plusMonths(1).format(Vendor.DMY);
        String type = Vendor.blank(transType) ? "R" : transType;

        for (String segment : Vendor.segments(cocdList, "BSE_CASH")) {
            rows.add(Vendor.row(
                    "faid", "0",
                    "COCD", segment,
                    "CONAME", null,
                    "KINDOFACCOUNT", "PARTY",
                    "ACCOUNTCODE", clientCode,
                    "ACCOUNTNAME", "KUMAR SINGH",
                    "TELNO", "",
                    "FAX", "",
                    "ADDR", "",
                    "OPENINGBALANCE", "0",
                    "DR_AMT", ".00",
                    "CR_AMT", "5000.00",
                    "VOUCHERDATE", voucherDate,
                    "SETTLEMENT_NO", "0",
                    "CTRCODE", "A1736",
                    "CTRNAME", "AXIS BANK A/C NO. 911020027537999 STOCK EXCHANGE CL. AC",
                    // Trailing space is the vendor's, from a CHAR(2) column. Trimming it here would
                    // hide a value a caller has to trim for itself against the real back office.
                    "TRANS_TYPE", type + " ",
                    "VOUCHERNO", "BR06874",
                    "NARRATION", "dummyMICR :395002005 Bank AcNo:10255503022",
                    "BILLNO", null,
                    "CHQNO", "0",
                    "EXPECTED_DATE", voucherDate,
                    "TRADING_COCD", null,
                    "PANNO", "",
                    "EMAIL", "",
                    "MANUALVNO", "1",
                    "BOOKTYPECODE", "3",
                    "BILL_DATE", voucherDate,
                    "MKT_TYPE", null,
                    "GROUPCODE", "",
                    "BRSFLAG", "Y",
                    "SETL_PAYINDATE", null,
                    "LAST2SETL", "",
                    "ACCOUNTCODE1", clientCode,
                    "GATEWAYID", null,
                    "PUNCH_TIME", voucherDate,
                    "voctype", "1.10",
                    "CHQIMAGEPATH", "\\\\150.0.0.97\\techexcel$\\ChequeImages\\R03082022154302_20.JPG",
                    "ContractNo", null,
                    "Row_ID", "190124",
                    "USERREFNO", "5465445",
                    "LAST_PAYMENT", null,
                    "LAST_RECEIPT", null,
                    "TRANS_TYPE1", "RECEIPT",
                    "CLOSING_AMT", "5000.00",
                    "VoucherNo1", "74",
                    "CounterCode", segment + "-A1736",
                    "ChqNo1", null,
                    "ExpectedDate1", voucherDateShort,
                    "DEFORDERBY", "ACCOUNTCODE, VOUCHERDATE,TRANS_TYPE,voctype desc,PUNCH_TIME,"
                            + "MKT_TYPE,SETTLEMENT_NO,VOUCHERNO desc,CR_AMT,FAID,NARRATION DESC",
                    "VoucherDate1", voucherDateShort,
                    "BILL_DATE1", voucherDateShort));
        }
        return rows;
    }
}
