package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import com.thinq.fms.api.dto.MoneyDto;
import com.thinq.fms.platform.error.AmountExceedsWithdrawableException;
import com.thinq.fms.platform.error.CalendarUnavailableException;
import com.thinq.fms.platform.error.FiguresStaleException;
import com.thinq.fms.platform.error.FmsClientException;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.FmsUpstreamException;
import com.thinq.fms.platform.error.RequestNotCancellableException;
import com.thinq.fms.platform.error.WithdrawableUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every domain exception's single edge representation (lld-backend.md §4.4).
 *
 * <p><b>One place, so there is one answer.</b> Mapping scattered across controllers gives the same
 * exception two statuses depending on which endpoint raised it, and a client cannot branch on that.
 *
 * <p>Three rules hold throughout:
 *
 * <ul>
 *   <li><b>No exception reaches a client as a stack trace.</b> The catch-all below returns a
 *       generic body and logs the detail, because an internal message is not user-facing copy and
 *       may name a vendor.
 *   <li><b>An upstream failure names no vendor.</b> A trader does not need to know which back
 *       office is down, only that a figure is unavailable. The upstream's identity is logged.
 *   <li><b>A refusal carries what the client needs to explain it.</b> Re-fetching to render an
 *       error is a second round trip on a path the trader is already stuck on.
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** REQ-102's error path: the figure is unavailable and no withdrawal may be requested. */
    @ExceptionHandler(WithdrawableUnavailableException.class)
    public ResponseEntity<ErrorResponse> withdrawableUnavailable(WithdrawableUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                e.code(), e.getMessage(), Map.of("verdict", e.verdict())));
    }

    /** Carries the figure so the client explains the refusal without re-fetching. */
    @ExceptionHandler(AmountExceedsWithdrawableException.class)
    public ResponseEntity<ErrorResponse> amountExceeds(AmountExceedsWithdrawableException e) {
        return ResponseEntity.unprocessableContent().body(ErrorResponse.of(
                e.code(), e.getMessage(), Map.of(
                        "requested", MoneyDto.of(e.requested()),
                        "withdrawable", MoneyDto.of(e.withdrawable()))));
    }

    /** REQ-305: why it cannot be cancelled, because the reasons mean opposite things. */
    @ExceptionHandler(RequestNotCancellableException.class)
    public ResponseEntity<ErrorResponse> notCancellable(RequestNotCancellableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                e.code(), e.getMessage(), Map.of("reason", e.reasonCode())));
    }

    /** REQ-107's obligation applies to a refusal too: say how old the figures are. */
    @ExceptionHandler(FiguresStaleException.class)
    public ResponseEntity<ErrorResponse> stale(FiguresStaleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                e.code(), e.getMessage(), Map.of(
                        "computedAt", e.computedAt().toString(), "computedBy", e.computedBy())));
    }

    /** OA-5. Fails safe rather than guessing a date the system cannot stand behind. */
    @ExceptionHandler(CalendarUnavailableException.class)
    public ResponseEntity<ErrorResponse> calendar(CalendarUnavailableException e) {
        log.warn("trading calendar unavailable", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(e.code(), "a required calendar is unavailable"));
    }

    /**
     * Every other client-actionable failure, at the status its own type declares.
     *
     * <p>Placed after the specific handlers above so those win; Spring selects the most specific
     * applicable handler, and each of the above is a subtype of this.
     */
    @ExceptionHandler(FmsClientException.class)
    public ResponseEntity<ErrorResponse> client(FmsClientException e) {
        return ResponseEntity.status(e.httpStatus()).body(ErrorResponse.of(e.code(), e.getMessage()));
    }

    /**
     * An upstream is unreachable. 503, and <b>the vendor is not named to the client</b>.
     *
     * <p>Naming it would tell a trader to chase a back office they have no relationship with, and
     * would disclose this system's supplier arrangements to anyone who can trigger an outage.
     */
    @ExceptionHandler(FmsUpstreamException.class)
    public ResponseEntity<ErrorResponse> upstream(FmsUpstreamException e) {
        log.error("upstream unavailable: {}", e.upstream(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(e.code(), "an upstream service is unavailable"));
    }

    /**
     * The system reached a state its own rules say is impossible. 500, and it pages.
     *
     * <p>The client gets nothing beyond a generic code: an invariant message names internal
     * structures and is written for whoever is woken up, not for a trader.
     */
    @ExceptionHandler(FmsInvariantException.class)
    public ResponseEntity<ErrorResponse> invariant(FmsInvariantException e) {
        log.error("INVARIANT VIOLATED (pages: {}): {}", e.pagesOnCall(), e.code(), e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("internal_error", "the request could not be completed"));
    }

    /** Bean Validation — the shape half of §4.3. Names the offending fields, nothing more. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>(e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                        f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a)));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("invalid_request", "the request is not well-formed", fields));
    }

    /**
     * A body this system could not read: broken JSON, a missing required field, a wrong type, or
     * a coercion this system refuses — a decimal where paise is declared.
     *
     * <p><b>Explicit, because the interface check below does not catch it.</b>
     * {@code HttpMessageNotReadableException} extends {@code HttpMessageConversionException}, a
     * plain {@code NestedRuntimeException}, and does <i>not</i> implement
     * {@code org.springframework.web.ErrorResponse}. So it fell through to the catch-all and every
     * malformed body — the most common integration mistake there is — returned <b>500</b> and
     * logged at ERROR. A client with a serialisation bug generated alarming entries indefinitely
     * and the log stopped being a signal.
     *
     * <p><b>The message is deliberately not returned.</b>
     * {@code HttpMessageNotReadableException.getMessage()} carries a fragment of the submitted
     * JSON and the fully qualified name of the target type. Echoing it would leak internal type
     * names and violate this system's own rule that no exception reaches a client as internal
     * detail. The detail is logged at debug, where a developer can find it.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        log.debug("unreadable request body", e);
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "invalid_request",
                "the request body could not be read: check that it is valid JSON, that every "
                        + "required field is present, and that each value has the declared type"));
    }

    /**
     * A query or path parameter whose value could not be converted to its declared type.
     *
     * <p><b>Explicit, for the same reason {@code unreadableBody} above is explicit, and it was the
     * same defect a second time.</b> {@code MethodArgumentTypeMismatchException} extends
     * {@code TypeMismatchException} — a {@code BeansException}, which implements neither
     * {@code org.springframework.web.ErrorResponse} nor {@code IllegalArgumentException} — so it
     * fell past every handler above to the catch-all and every stale enum value, mistyped date and
     * non-numeric id came back as <b>500</b>, logged at ERROR. The specification declares 400 on
     * these operations. A client with a wrong query parameter cannot act on an internal error, and
     * the entries it generated were indistinguishable from the service actually failing.
     *
     * <p><b>The parameter is named; the submitted value is not returned.</b> Echoing it would
     * reflect attacker-controlled text, and the required type's name is internal detail. Where the
     * target is an enum its constants are listed, because those are already published in the
     * specification and they turn "something is wrong" into a fix without a round trip through the
     * documentation.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parameterTypeMismatch(MethodArgumentTypeMismatchException e) {
        // Debug, not error: a wrong parameter is a caller mistake, and logging it at error is how a
        // log fills with entries nobody can act on.
        log.debug("unconvertible parameter '{}'", e.getName(), e);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", e.getName());
        Class<?> required = e.getRequiredType();
        if (required != null && required.isEnum()) {
            details.put("permitted", Arrays.stream(required.getEnumConstants())
                    .map(String::valueOf).toList());
        }

        return ResponseEntity.badRequest().body(ErrorResponse.of("invalid_request",
                "the value of parameter '" + e.getName() + "' is not one this endpoint accepts",
                details));
    }

    /** A malformed value the domain refused before any rule ran. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("invalid_request", e.getMessage()));
    }

    /**
     * Spring's own web exceptions, at the status they already carry.
     *
     * <p><b>This must precede the catch-all below, and its absence was a real defect.</b> Without
     * it, a request to an unknown path raised {@code NoResourceFoundException}, fell through to
     * {@code Exception}, and came back as a <b>500 logged as an unhandled exception</b>. A client
     * mistyping a URL got an internal error, and the log filled with alarming entries for what was
     * simply a wrong path.
     *
     * <p>Covers the whole family — unknown path, unsupported method, unsupported media type — each
     * of which already knows its correct status. The body is remapped into this system's shape so
     * a client parses one error format everywhere.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> springWebErrorOrUnhandled(Exception e) {
        // Spring signals these through the ErrorResponse INTERFACE rather than a common
        // superclass — NoResourceFoundException extends ServletException and implements it, while
        // ErrorResponseException extends RuntimeException and implements it. There is no single
        // type to catch, so the check is instanceof against the interface. An earlier attempt
        // caught ErrorResponseException and missed the unknown-path case entirely.
        if (e instanceof org.springframework.web.ErrorResponse spring) {
            HttpStatus status = HttpStatus.resolve(spring.getStatusCode().value());
            String code = status == null ? "request_failed" : status.name().toLowerCase();
            // Logged at debug: a mistyped URL is not an incident, and logging it at error is how
            // a log fills with alarming entries that nobody can act on.
            log.debug("web error {} for {}", spring.getStatusCode(), e.getMessage());
            return ResponseEntity.status(spring.getStatusCode())
                    .body(ErrorResponse.of(code, spring.getBody().getTitle()));
        }
        return unhandled(e);
    }

    /**
     * Anything unhandled.
     *
     * <p>Deliberately last and deliberately opaque. §4.4's rule is that no exception reaches the
     * client as a stack trace, and an unrecognised failure is the one most likely to carry internal
     * detail — a vendor's error text, a SQL fragment, a file path.
     */
    private ResponseEntity<ErrorResponse> unhandled(Exception e) {
        log.error("unhandled exception on the API surface", e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("internal_error", "the request could not be completed"));
    }
}
