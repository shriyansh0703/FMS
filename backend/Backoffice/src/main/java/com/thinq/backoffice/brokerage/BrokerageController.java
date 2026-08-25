package com.thinq.backoffice.brokerage;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.thinq.backoffice.platform.VendorGateway;

/**
 * BROKERAGE AND CHARGES — what a trade costs, who shares it, and what unpaid funds accrue.
 *
 * <ul>
 *   <li>{@code brk_remeshire_view} — brokerage earned and its broker/remisier split.</li>
 *   <li>{@code new_interest_process} — interest charged on debit funds and margin shortfall.</li>
 * </ul>
 *
 * <p>Both are READS. {@code add_brokerage} is the write in this category and it is NOT here — the
 * FMS mapping assigns it to the Order Management Service, which owns the brokerage rates
 * themselves. This service only reports what those rates earned.
 *
 * <p>{@code new_interest_process} is filed here rather than under funds because its category in the
 * FMS mapping is Brokerage/Charges: it reports a CHARGE, computed from funds, not a funds movement.
 */
@RestController
public class BrokerageController {

    private final VendorGateway gateway;
    private final BrokerageMock mock;

    BrokerageController(VendorGateway gateway, BrokerageMock mock) {
        this.gateway = gateway;
        this.mock = mock;
    }

    @PostMapping(path = {"/api/entry/brk_remeshire_view",
                         VendorGateway.PREFIX + "/api/entry/brk_remeshire_view"},
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Object brkRemeshireView(@RequestBody(required = false) String raw,
                                   HttpServletRequest request) {
        if (gateway.live()) {
            return gateway.proxy(request, raw);
        }
        return mock.brkRemeshireView(gateway.asObject(raw));
    }

    @PostMapping(path = {"/api/entry/new_interest_process",
                         VendorGateway.PREFIX + "/api/entry/new_interest_process"},
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Object newInterestProcess(@RequestBody(required = false) String raw,
                                     HttpServletRequest request) {
        if (gateway.live()) {
            return gateway.proxy(request, raw);
        }
        return mock.newInterestProcess(gateway.asObject(raw));
    }
}
