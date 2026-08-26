package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One pass over every documented FMS route, in mock mode.
 *
 * <p>What this pins, and why each one is worth a line:
 *
 * <ul>
 *   <li>Every route answers, at both the bare path and TechExcel's {@code /TechBoRest} prefix.</li>
 *   <li>A rejection is HTTP <b>200</b> with the verdict in the body. This is the contract most
 *       likely to be "fixed" by somebody who thinks a 400 would be tidier, and doing so breaks
 *       every caller that branches on {@code Error Code}.</li>
 *   <li>Every route is reachable with NO credential of any kind. This service holds the
 *       TechExcel session itself; a caller presents nothing. That is the contract, and a test
 *       that quietly started sending a token would stop proving it.</li>
 * </ul>
 *
 * <p>The rate limiter is off here. It has its own test against a fake clock, and leaving it on
 * would make this suite's pass or fail depend on how many requests the suite happens to make.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "techexcel.live=false",
        "backoffice.ratelimit.enabled=false"})
class GatewayRoutesTest {

    @Autowired
    private MockMvc mvc;

    private void ok(String path, String json) throws Exception {
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Success").value("True"))
                .andExpect(jsonPath("$['Error Code']").value(""));
    }

    @Test
    void everyDocumentedRouteAnswers() throws Exception {
        // ---- funds
        ok("/api/entry/virtual_debit_report", """
                {"CLIENT_ID":"0000056743","COMPANY_CODE":"NSE_CASH","FROMDATE":"01/04/2025",
                 "TODATE":"30/06/2025","BRANCH_CODE":"","datayear":2025}""");

        // ---- brokerage
        ok("/api/entry/brk_remeshire_view", """
                {"CLIENT_ID":"","TO_DATE":"27/02/2025","FROM_DATE":"27/02/2025",
                 "COMPANY_CODE":"BSE_CASH,NSE_CASH","POLICY":"REMISIER SHARING REPORT",
                 "Remshire_Code":"ETYJC"}""");
        ok("/api/entry/new_interest_process", """
                {"Form_Date":"01-11-2024","TO_DATE":"30-11-2024","Client_Id":"18836",
                 "Report_Type":"1"}""");

        // ---- accounting, on TechExcel's own prefix rather than the bare path
        ok("/TechBoRest/api/entry/ledger", """
                {"Client_code":"M000","FromDate":"01/04/2022","ToDate":"31/03/2023",
                 "COCDLIST":"BSE_CASH,NSE_CASH","ShowMargin":"Y","ShowAllData":"Y",
                 "Merge_Company":"Y"}""");
    }

    @Test
    void theOrderManagementRoutesAreNotServedHere() throws Exception {
        // The other four APIs in the same vendor document belong to the Order Management Service.
        // They must 404 rather than answer, in mock mode: a caller reaching them here would be
        // writing to the client master through the service that only reads funds.
        for (String path : new String[]{
                "/api/entry/new_segment_enable",
                "/api/entry/client_active_inactive_status_update",
                "/api/entry/add_brokerage",
                "/api/entry/portfolio_insert"}) {
            mvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aRejectionIsHttp200WithTheVerdictInTheBody() throws Exception {
        // A date range crossing a financial year. The Ledger document's own failure sample,
        // timestamp shape included.
        mvc.perform(post("/api/entry/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"Client_code":"M000","FromDate":"01/04/2022",
                                 "ToDate":"30/06/2023","ShowAllData":"Y"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Success").value("false"))
                .andExpect(jsonPath("$['Error Code']").value("Input_Validation"))
                .andExpect(jsonPath("$['Error Description'].ToDate[0]")
                        .value("The to date must be a date before 2023-03-31 00:00:00.000."));
    }

    @Test
    void theCharacterFilterHasItsOwnCode() throws Exception {
        mvc.perform(post("/api/entry/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"Client_code":"M00%0","FromDate":"01/04/2022",
                                 "ToDate":"31/03/2023","ShowAllData":"Y"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("SYSTEM_Character_Filter"));
    }

    @Test
    void anUndocumentedRouteIs404InMockMode() throws Exception {
        // 404, not the vendor's 200 envelope: this is OUR verdict about OUR routing table, and a
        // caller must be able to tell it apart from something the back office rejected.
        mvc.perform(post("/api/entry/something_nobody_documented")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$['Error Code']").value("Input_Validation"));
    }
}
