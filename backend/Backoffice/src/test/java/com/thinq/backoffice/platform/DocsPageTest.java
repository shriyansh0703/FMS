package com.thinq.backoffice.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The docs page, and the one property of it worth pinning.
 *
 * <p>SWAGGER UI IS SERVED FROM THIS HOST, NOT A CDN. Documentation that needs the internet fails
 * exactly when it is most wanted — on a locked-down desk, on a plane, inside a broker's network.
 * The assertion below is what stops a well-meaning CDN link creeping back in the next time
 * somebody updates the page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "techexcel.live=false",
        "backoffice.ratelimit.enabled=false"})
class DocsPageTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void thePageLoadsAndReachesNoExternalHost() throws Exception {
        String html = mvc.perform(get("/docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("swagger-ui-bundle.js").contains("/openapi.yaml");
        assertThat(html).doesNotContain("http://").doesNotContain("https://");
        assertThat(html).contains("MOCK");
        // NO LOGIN CONTROL. This service holds the TechExcel session itself, so a sign-in box here
        // would advertise a flow that does not exist and invite somebody to paste a real
        // back-office credential into a browser.
        assertThat(html).doesNotContain("password").doesNotContain("Log in");
    }

    @Test
    void theSpecIsServedAndCoversEveryRoute() throws Exception {
        String yaml = mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String path : new String[]{
                "/api/entry/virtual_debit_report",
                "/api/entry/brk_remeshire_view", "/api/entry/new_interest_process",
                "/api/entry/ledger"}) {
            assertThat(yaml).as("spec documents %s", path).contains("  " + path + ":");
        }

        // And documents nothing that belongs to the Order Management Service. A spec that
        // advertises a route this service does not serve is worse than no spec.
        for (String path : new String[]{
                "/api/login",
                "/api/entry/new_segment_enable",
                "/api/entry/client_active_inactive_status_update",
                "/api/entry/add_brokerage", "/api/entry/portfolio_insert"}) {
            assertThat(yaml).as("spec must not document %s", path)
                    .doesNotContain("  " + path + ":");
        }
    }
}
