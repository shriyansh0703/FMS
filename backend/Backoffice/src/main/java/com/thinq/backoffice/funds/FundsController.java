package com.thinq.backoffice.funds;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.thinq.backoffice.platform.VendorGateway;

/**
 * FUNDS — amounts held or blocked against a customer.
 *
 * <ul>
 *   <li>{@code virtual_debit_report} — the virtual debit entries in a client ledger for a period.</li>
 * </ul>
 *
 * <p>Read-only. The category is Trading/Funds in the FMS mapping and the responsibility is funds
 * and margin, which is why it sits here rather than beside the trading writes.
 */
@RestController
public class FundsController {

    private final VendorGateway gateway;
    private final FundsMock mock;

    FundsController(VendorGateway gateway, FundsMock mock) {
        this.gateway = gateway;
        this.mock = mock;
    }

    @PostMapping(path = {"/api/entry/virtual_debit_report",
                         VendorGateway.PREFIX + "/api/entry/virtual_debit_report"},
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Object virtualDebitReport(@RequestBody(required = false) String raw,
                                     HttpServletRequest request) {
        if (gateway.live()) {
            return gateway.proxy(request, raw);
        }
        return mock.virtualDebitReport(gateway.asObject(raw));
    }
}
