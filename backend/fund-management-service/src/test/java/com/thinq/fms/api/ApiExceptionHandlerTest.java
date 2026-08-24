package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import com.thinq.fms.platform.error.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error boundary, tested directly.
 *
 * <p>These handlers decide what a caller sees when something goes wrong, and five of them ran in no
 * test at all — including {@code invariant}, which is reached from more call sites than any other
 * and which nobody could have said the response shape of. {@code HostileBodyApiTest} covers
 * malformed <i>input</i> thoroughly and created the impression this was covered.
 *
 * <p>Called as plain methods rather than through MockMvc on purpose: it needs no Spring context, it
 * does not touch the static mutable state in {@code ApiTestConfiguration} that five test classes
 * already share, and the thing under test is the mapping from exception to response, which is
 * exactly what a direct call exercises.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("an invariant violation returns 500 and tells the caller nothing about it")
    void anInvariantViolationLeaksNothing() {
        // The message names internal structures and is written for whoever is woken up. A trader —
        // or anyone probing the API — must not receive it, and neither must the code, which would
        // enumerate this system's internal failure modes to an attacker one request at a time.
        FmsInvariantException e = new FmsInvariantException(
                "payin_attempt_stale_write",
                "attempt 4471 was modified by another writer since it was read (expected version 3)");

        ResponseEntity<ErrorResponse> response = this.handler.invariant(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("internal_error");
        assertThat(response.getBody().message()).isEqualTo("the request could not be completed");

        assertThat(response.getBody().code() + response.getBody().message())
                .as("neither the invariant code nor its message reaches the caller")
                .doesNotContain("payin_attempt_stale_write")
                .doesNotContain("4471")
                .doesNotContain("version");
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    @DisplayName("an upstream outage returns 503 without naming the vendor")
    void anUpstreamOutageDoesNotNameTheVendor() {
        // Which vendor is down is operational detail. Naming it tells a caller who this system
        // depends on, and it is in the log where the person fixing it will look.
        ResponseEntity<ErrorResponse> response = this.handler.upstream(
                new VendorUnavailableException("juspay", "juspay did not answer create_order in time"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("an upstream service is unavailable");
        assertThat(response.getBody().message()).doesNotContain("juspay");
    }

    @Test
    @DisplayName("an unavailable calendar returns 503 rather than a guessed date")
    void anUnavailableCalendarFailsSafe() {
        // OA-5. A guessed settlement date is worse than no date: the trader plans around it.
        ResponseEntity<ErrorResponse> response = this.handler.calendar(
                new CalendarUnavailableException("settlement calendar not loaded"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("a required calendar is unavailable");
    }

    @Test
    @DisplayName("stale figures return 409 and say when and by what they were computed")
    void staleFiguresCarryTheirProvenance() {
        // The one error that deliberately does carry detail: the caller needs to know how old the
        // figure was and which system produced it, because the answer decides whether to retry.
        Instant computedAt = Instant.parse("2026-08-21T09:00:00Z");
        ResponseEntity<ErrorResponse> response = this.handler.stale(
                new FiguresStaleException(computedAt, "FRONT_OFFICE", Duration.ofMinutes(15)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details())
                .containsEntry("computedAt", computedAt.toString())
                .containsEntry("computedBy", "FRONT_OFFICE");
    }
}
