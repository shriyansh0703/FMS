package com.thinq.backoffice.accounting;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.thinq.backoffice.platform.VendorGateway;

/**
 * ACCOUNTING — the customer's running account.
 *
 * <ul>
 *   <li>{@code ledger} — every credit and debit for a client over a financial year.</li>
 * </ul>
 *
 * <p>The single most expensive read in this mapping: a full financial year for a merged family
 * group is thousands of rows, and the vendor's own capture of it took over three seconds. It is the
 * endpoint most worth giving its own entry in {@code backoffice.ratelimit.per-endpoint}.
 */
@RestController
public class AccountingController {

    private final VendorGateway gateway;
    private final AccountingMock mock;

    AccountingController(VendorGateway gateway, AccountingMock mock) {
        this.gateway = gateway;
        this.mock = mock;
    }

    @PostMapping(path = {"/api/entry/ledger", VendorGateway.PREFIX + "/api/entry/ledger"},
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Object ledger(@RequestBody(required = false) String raw,
                         HttpServletRequest request) {
        if (gateway.live()) {
            return gateway.proxy(request, raw);
        }
        return mock.ledger(gateway.asObject(raw));
    }
}
