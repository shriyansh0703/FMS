package com.thinq.fms.integration.communication;

import tools.jackson.databind.JsonNode;
import com.thinq.fms.integration.AbstractVendorGateway;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.VendorHttpException;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.ChannelNotPermittedException;
import com.thinq.fms.platform.error.FmsInvariantException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The Communication Service client (caller-integration.md).
 *
 * <h2>The one guarantee that shapes this class</h2>
 *
 * <p><b>The service never retries a failed send.</b> A {@code failed} status is terminal, and
 * recovery means submitting again with a <i>new</i> {@code request_id}. So this client does not
 * retry either, and the delivery reconciler (§7.9) is what resubmits — a retry loop reusing the
 * same key would return the original result and send nothing, which is the failure mode that
 * looks most like success.
 *
 * <h2>Non-disclosure is structural here, not a review item</h2>
 *
 * <p>The service requires {@code parameters} to match a template's declared set exactly. That
 * rule is what enforces the PRD's prohibition on disclosing balances and account identifiers to
 * third parties: a parameter the template does not declare is refused rather than sent. This
 * client passes parameters through unchanged and adds none of its own, so nothing can be
 * appended below the level a template author reviewed.
 */
public final class CommunicationClient extends AbstractVendorGateway implements NotificationSubmitter {

    private static final String VENDOR = "communication";
    private static final String SUBMIT_PATH = "/v1/notifications";
    private static final String READ_PATH = "/v1/notifications/";

    private final JsonHttp http;
    private final String servicePrincipal;

    /**
     * @param servicePrincipal the value for {@code X-Service-Principal}, or null when the
     *     deployment's infrastructure writes that header. The contract is explicit that a mesh
     *     sidecar or mTLS terminator usually sets it and application code does not — so passing
     *     null is a supported configuration and not a missing value. Setting it here when
     *     infrastructure also sets it is the mistake worth avoiding.
     */
    public CommunicationClient(JsonHttp http,
                               String servicePrincipal,
                               Duration callTimeout,
                               CircuitBreaker circuitBreaker,
                               MeterRegistry meters) {
        super(VENDOR, callTimeout, circuitBreaker, meters);
        this.http = Objects.requireNonNull(http, "http");
        this.servicePrincipal = servicePrincipal;
    }

    /**
     * Submit one notification on one channel.
     *
     * <p>A repeat submission with the same {@code request_id} returns the original notification
     * and sends nothing, which the result reports through {@code replayed}. That is the intended
     * behaviour after a crash between writing the intent row and submitting it.
     *
     * @throws FmsException translated. A {@code 403 channel_not_permitted} becomes an invariant
     *     failure rather than an outage: it means this system tried to send on a channel it was
     *     never granted, which is a configuration error nobody will notice if it is counted as a
     *     vendor being down (OA-2)
     */
    @Override
    public NotificationReceipt submit(NotificationSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        return call("submit_notification", () -> {
            try {
                return post(submission);
            } catch (VendorHttpException e) {
                // THE ONE CASE WHERE RE-SUBMITTING IS CORRECT, and it does not contradict the
                // never-retries rule. §9: "A 500 on submit is genuinely ambiguous — the
                // notification may or may not have been accepted. Retry with the SAME request_id;
                // if it was accepted, you get the original result back with replayed: true and
                // nothing is sent twice. This is the case idempotency exists for."
                //
                // Nothing retries a SEND. Re-submitting the same idempotency key resolves whether
                // a send happened at all — the alternative is a message this system believes it
                // never sent, sitting delivered in the trader's inbox.
                //
                // Exactly once, and only for this reason. Anything else propagates.
                if (e.status() == 500 && reasonOf(e).isAmbiguousOnSubmit()) {
                    return post(submission);
                }
                throw e;
            }
        });
    }

    private NotificationReceipt post(NotificationSubmission submission) throws Exception {
        {
            Map<String, Object> body = new LinkedHashMap<>();
            // Arrays of one. The service refuses more than one element today, and sending them
            // as arrays means nothing here changes when multi-channel submission ships.
            body.put("template_keys", java.util.List.of(submission.templateKey()));
            body.put("channels", java.util.List.of(submission.channel().wireValue()));
            body.put("contact_details", Map.of(submission.channel().wireValue(), submission.address()));
            body.put("request_id", submission.requestId());
            body.put("parameters", submission.parameters());

            JsonNode response = this.http.post(SUBMIT_PATH, body, headers());
            return toReceipt(response, submission);
        }
    }

    /**
     * The current state of one notification, by the id the submission returned.
     *
     * <p>Reads {@code stuck} and {@code address_known} as well as the status. Both are answers the
     * service gives directly, and the reconciler previously inferred the first from a poll window.
     *
     * <p><b>{@code user_id} is deliberately not read.</b> §7: "it is a remnant of an earlier
     * contract, it is always empty, and there is nothing you can put in the request to populate
     * it. Your client will have the field and should never read it."
     */
    public NotificationStatus statusOf(String notificationId) {
        Objects.requireNonNull(notificationId, "notificationId");

        return call("read_notification", () -> {
            JsonNode response = this.http.get(READ_PATH + notificationId, headers());
            JsonNode status = response.get("status");
            if (status == null || status.isNull()) {
                throw new VendorUnavailableException(VENDOR,
                        "notification " + notificationId + " returned no status field");
            }
            return new NotificationStatus(
                    DeliveryStatus.fromWire(status.asString()),
                    response.path("stuck").asBoolean(false),
                    // Absent is read as "known", the conservative direction: claiming a proven
                    // non-send on a field the service did not send would be a false negative on a
                    // regulatory intimation.
                    response.path("address_known").asBoolean(true),
                    text(response, "recipient_mask", null));
        });
    }

    private Map<String, String> headers() {
        return this.servicePrincipal == null
                ? Map.of()
                : Map.of("X-Service-Principal", this.servicePrincipal);
    }

    private static NotificationReceipt toReceipt(JsonNode response, NotificationSubmission submission) {
        JsonNode notifications = response.get("notifications");
        if (notifications == null || !notifications.isArray() || notifications.isEmpty()) {
            throw new FmsInvariantException("notification_accepted_without_id",
                    "the Communication Service accepted request_id " + submission.requestId()
                            + " without returning a notification id, so its delivery cannot be read");
        }
        JsonNode first = notifications.get(0);
        JsonNode id = first.get("id");
        if (id == null || id.isNull() || id.asString().isBlank()) {
            throw new FmsInvariantException("notification_accepted_without_id",
                    "notification for request_id " + submission.requestId() + " has no id");
        }
        JsonNode replayed = response.get("replayed");
        JsonNode submissionId = response.get("submission_id");

        return new NotificationReceipt(
                id.asString(),
                text(first, "template_id", null),
                submissionId == null || submissionId.isNull() ? null : submissionId.asString(),
                submission.channel(),
                DeliveryStatus.fromWire(text(first, "status", DeliveryStatus.ACCEPTED.wireValue())),
                replayed != null && replayed.asBoolean(false));
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? fallback : n.asString();
    }

    @Override
    protected FmsException translate(String operation, Exception e) {
        if (!(e instanceof VendorHttpException http)) {
            return super.translate(operation, e);
        }

        // §9: the reason IS the contract. "Branch on it; never parse `error`." Branching on the
        // status alone collapsed three different 403 causes into one and reported every 422 as a
        // vendor outage — a deactivated caller shown as a missing channel grant sends someone to
        // request a permission they already hold.
        CommunicationReason reason = reasonOf(http);

        return switch (reason.kind()) {
            // The channel case keeps its own type because the messaging module recognises it and
            // records a suppression rather than a failure (OA-2).
            case PERMISSION -> reason == CommunicationReason.CHANNEL_NOT_PERMITTED
                    ? new ChannelNotPermittedException(vendorName(),
                            "the Communication Service refused a channel this system is not granted")
                    : new FmsInvariantException("communication_caller_not_permitted",
                            "this system's principal is not permitted to send: " + reason.wireValue()
                                    + ". Not fixable from here — the platform team must act.");

            // The gateway or mesh should have written X-Service-Principal. Reaching here means the
            // deployment is misconfigured, which is an outage to fix rather than a message to drop.
            case IDENTITY -> new FmsInvariantException("communication_no_caller_identity",
                    "no caller identity reached the Communication Service; the principal header is "
                            + "written by infrastructure and is absent");

            case OUR_PAYLOAD -> new FmsInvariantException("notification_request_rejected",
                    "the Communication Service rejected a request this system constructed: "
                            + reason.wireValue());

            // 422: well-formed and refused. A missing or unpublished template is a platform-side
            // fix, and reporting it as an outage would send someone to look at a service that is
            // working perfectly.
            case PLATFORM_CONFIGURATION -> new FmsInvariantException("notification_template_unusable",
                    "the Communication Service cannot use the template this system named: "
                            + reason.wireValue());

            case NOT_FOUND -> new FmsInvariantException("notification_not_found",
                    "no such notification, or it belongs to another caller");

            case THEIRS -> new VendorUnavailableException(VENDOR,
                    "the Communication Service returned HTTP " + http.status()
                            + (reason == CommunicationReason.UNRECOGNISED
                                    ? "" : " (" + reason.wireValue() + ")"), http);
        };
    }

    /**
     * The reason an error body carries.
     *
     * <p>Falls back to {@link CommunicationReason#UNRECOGNISED} when the body is unreadable, which
     * routes to an outage. An unrecognised response is one this system does not understand, and
     * guessing what it meant is how a rejection gets read as a success.
     */
    private CommunicationReason reasonOf(VendorHttpException http) {
        String body = http.body();
        if (body == null || body.isBlank()) {
            return CommunicationReason.UNRECOGNISED;
        }
        try {
            return CommunicationReason.fromWire(
                    this.http.mapper().readTree(body).path("reason").asString(null));
        } catch (Exception unreadable) {
            return CommunicationReason.UNRECOGNISED;
        }
    }

}
