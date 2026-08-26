package com.thinq.backoffice.platform;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns any rejection into TechExcel's envelope, with HTTP 200.
 *
 * <p>The status is the point. TechExcel answers 200 for a rejected call and puts the verdict in
 * {@code Success} and {@code Error Code}; a caller branching on the status code would read every
 * rejection as a success. Spring's default would return 400 or 500 here and quietly break that
 * contract for anyone who later swapped this gateway for the real back office.
 *
 * <p>The three answers that are NOT 200 are this gateway's own, never the vendor's: 404 for a
 * route mock mode does not serve, 502 for an unreachable upstream, 429 for our rate limit.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ApiError.class)
    ResponseEntity<Map<String, Object>> onApiError(ApiError e) {
        return ok(e.envelope());
    }

    /**
     * The documented catch-all. Never echo a stack trace to a caller: the message is all they get,
     * and all they should get.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> onAnythingElse(Exception e) {
        return ok(ApiError.envelope("Database_Exception", String.valueOf(e.getMessage())));
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> envelope) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(envelope);
    }
}
