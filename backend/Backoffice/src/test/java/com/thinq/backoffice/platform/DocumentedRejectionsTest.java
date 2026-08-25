package com.thinq.backoffice.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * THE REJECTIONS THE VENDOR'S DOCUMENTS SAMPLE, AND THE MALFORMED-BODY CASES BENEATH THEM.
 *
 * <p>Every rejection here is one a real caller will meet. Reproducing them in mock mode is the
 * whole point of having a mock: a client that only ever sees success locally discovers its error
 * handling in production, against a broker's back office.
 *
 * <p>All of them are <b>HTTP 200</b>. That is not an oversight in the assertions — it is
 * TechExcel's contract, and the reason every test here checks a body rather than a status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "techexcel.live=false",
        "backoffice.ratelimit.enabled=false"})
class DocumentedRejectionsTest {

    @Autowired
    private MockMvc mvc;

    /** Named send, not post: a local `post` would shadow the static import it is built from. */
    private org.springframework.test.web.servlet.ResultActions send(String path, String body)
            throws Exception {
        return mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ------------------------------------------------------------------ malformed bodies

    @Test
    void anEmptyBodyIsRejectedInTheVendorsEnvelopeAndNotAsA400() throws Exception {
        send("/api/entry/ledger", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("Input_Validation"))
                .andExpect(jsonPath("$['Error Description']").value("Request body must not be empty."));
    }

    @Test
    void malformedJsonIsRejectedInTheVendorsEnvelope() throws Exception {
        // Jackson 3 throws unchecked here; a caller must still get the envelope it parses
        // everywhere else rather than a Spring error page.
        send("/api/entry/ledger", "{\"Client_code\": ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("Input_Validation"))
                .andExpect(jsonPath("$['Error Description']",
                        org.hamcrest.Matchers.containsString("Malformed JSON")));
    }

    @Test
    void aJsonArrayWhereAnObjectBelongsIsRejected() throws Exception {
        send("/api/entry/ledger", "[{\"Client_code\":\"M000\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Description']").value("Request body must be a JSON object."));
    }

    // ------------------------------------------------------------------ documented samples

    @Test
    void anUnknownRemisierCodeIsTheDocumentsOwnFailureSample() throws Exception {
        send("/api/entry/brk_remeshire_view", """
                {"TO_DATE":"27/02/2025","FROM_DATE":"27/02/2025","COMPANY_CODE":"BSE_CASH",
                 "Remshire_Code":"XY"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("Input_Value_Validation"))
                .andExpect(jsonPath("$['Error Description']").value("Invalid Remeshire Code"));
    }

    @Test
    void aPeriodWithNoEntriesIsNoDataFoundRatherThanAnEmptyArray() throws Exception {
        // The virtual debit document's own failure sample. Answering with an empty success array
        // would hide a rejection the caller has to handle.
        send("/api/entry/virtual_debit_report", """
                {"COMPANY_CODE":",","FROMDATE":"01/04/2025","TODATE":"30/06/2025","datayear":2025}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("Input_Value_Validation"))
                .andExpect(jsonPath("$['Error Description']").value("No data found"));
    }

    @Test
    void anUndocumentedReportTypeIsRefusedRatherThanGivenTypeOnesColumns() throws Exception {
        // The vendor documents response columns for Report_Type 1 only. Answering 2-5 with type
        // 1's columns would be inventing four contracts nobody has written down.
        send("/api/entry/new_interest_process", """
                {"Form_Date":"01/11/2024","TO_DATE":"30/11/2024","Report_Type":"3"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Error Code']").value("Input_Value_Validation"))
                .andExpect(jsonPath("$['Error Description']",
                        org.hamcrest.Matchers.containsString("not documented")));
    }

    @Test
    void theCorrectlySpelledFromDateIsAcceptedBesideTheVendorsTypo() throws Exception {
        // The vendor's table and sample both say Form_Date. The typo is authoritative, but a
        // caller who spells it correctly must not be refused either.
        send("/api/entry/new_interest_process", """
                {"From_Date":"01/11/2024","TO_DATE":"30/11/2024","Report_Type":"1"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Success").value("True"));
    }
}
