package com.thinq.backoffice.platform;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything under {@code /api} that is not one of the endpoints this service holds a document for.
 *
 * <p>In LIVE mode these are forwarded: TechExcel serves far more endpoints than the eight in the
 * FMS mapping, and a gateway that silently drops them is a worse lie than a 404.
 *
 * <p>In MOCK mode they 404 and name the flag that would make them work. A fake answer for an
 * endpoint nobody has documented would be inventing a contract.
 */
@RestController
public class UnmappedApiController {

    private final VendorGateway gateway;

    UnmappedApiController(VendorGateway gateway) {
        this.gateway = gateway;
    }

    @RequestMapping(path = {"/api/**", VendorGateway.PREFIX + "/api/**"},
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> unmapped(@RequestBody(required = false) String body,
                                           HttpServletRequest request) {
        if (gateway.live()) {
            return gateway.proxy(request, body);
        }
        return gateway.jsonError(ApiError.envelope("Input_Validation",
                "No route " + request.getRequestURI()
                        + ". Mock mode serves only the documented FMS endpoints; set "
                        + "techexcel.live=true to reach the rest of TechExcel."), 404);
    }
}
