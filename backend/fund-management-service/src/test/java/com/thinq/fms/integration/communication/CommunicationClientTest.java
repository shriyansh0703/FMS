package com.thinq.fms.integration.communication;

import tools.jackson.databind.ObjectMapper;
import com.thinq.fms.integration.JsonHttp;
import com.thinq.fms.integration.StubVendor;
import com.thinq.fms.platform.error.FmsException;
import com.thinq.fms.platform.error.VendorUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Communication Service call path, against a real HTTP server. */
class CommunicationClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String ACCEPTED =
            "{\"submission_id\":\"sub-1\",\"replayed\":false,"
                    + "\"notifications\":[{\"id\":\"ntf-1\",\"template_id\":\"tmpl-91dec3c1\","
                    + "\"channel\":\"sms\",\"status\":\"accepted\"}]}";

    @Test
    @DisplayName("a submission returns the notification id, which is the only way to read status later")
    void submissionReturnsTheNotificationId() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications", ACCEPTED)) {
            NotificationReceipt receipt = client(vendor).submit(submission());

            // The contract states there is no way to look a notification up by request_id, so
            // losing this id means losing the ability to reconcile the delivery at all.
            assertThat(receipt.notificationId()).isEqualTo("ntf-1");
            assertThat(receipt.status()).isEqualTo(DeliveryStatus.ACCEPTED);
            assertThat(receipt.replayed()).isFalse();
        }
    }

    @Test
    @DisplayName("template keys and channels are sent as arrays of one, paired by position")
    void arraysOfOnePairedByPosition() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications", ACCEPTED)) {
            client(vendor).submit(submission());

            String body = vendor.requestBodies().get(0);
            assertThat(body).contains("\"template_keys\":[\"shortfall_sms\"]");
            assertThat(body).contains("\"channels\":[\"sms\"]");
            assertThat(body).contains("\"contact_details\":{\"sms\":\"+919451740121\"}");
            // One request_id per intent, never per attempt.
            assertThat(body).contains("\"request_id\":\"4242\"");
        }
    }

    @Test
    @DisplayName("a replayed submission is reported as such rather than as a fresh send")
    void replayedSubmissionIsFlagged() throws Exception {
        // The expected answer after a crash between writing an intent and submitting it. Not an
        // error — it is the idempotency working, and nothing was sent a second time.
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications",
                "{\"replayed\":true,\"notifications\":[{\"id\":\"ntf-1\",\"channel\":\"sms\",\"status\":\"dispatched\"}]}")) {

            NotificationReceipt receipt = client(vendor).submit(submission());

            assertThat(receipt.replayed()).isTrue();
            assertThat(receipt.status()).isEqualTo(DeliveryStatus.DISPATCHED);
        }
    }

    @Test
    @DisplayName("REQ-625: the resolved template version is captured, not dropped")
    void templateVersionIsCaptured() throws Exception {
        // fms_message_delivery.template_id exists precisely for this — it is how "a delivered
        // message must always be reconstructable" is satisfied without this system versioning
        // templates itself. Dropping the field left that column unfillable.
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications", ACCEPTED)) {
            assertThat(client(vendor).submit(submission()).templateId()).isEqualTo("tmpl-91dec3c1");
        }
    }

    @Test
    @DisplayName("an ambiguous 500 on submit is re-sent under the SAME request_id")
    void ambiguous500IsResubmittedWithTheSameKey() throws Exception {
        // §9: "A 500 on submit is genuinely ambiguous — the notification may or may not have been
        // accepted. Retry with the same request_id." This does not contradict the never-retries
        // rule: nothing retries a SEND, and re-submitting the same key resolves whether a send
        // happened at all. The alternative is a message this system believes it never sent,
        // sitting delivered in the trader's inbox.
        try (StubVendor vendor = new StubVendor()
                .respond("/v1/notifications", 500,
                        "{\"error\":\"Server Error\",\"reason\":\"internal_error\"}")
                .respond("/v1/notifications", 200, ACCEPTED)) {

            NotificationReceipt receipt = client(vendor).submit(submission());

            assertThat(receipt.notificationId()).isEqualTo("ntf-1");
            assertThat(vendor.callsTo("/v1/notifications"))
                    .as("exactly two attempts: the ambiguous one and the resolving re-send")
                    .isEqualTo(2);
            // The same request_id both times. A NEW one would send a second message, which is
            // exactly what idempotency exists to prevent here.
            assertThat(vendor.requestBodies()).allMatch(b -> b.contains("\"request_id\":\"4242\""));
        }
    }

    @Test
    @DisplayName("a 500 with any other reason is not re-sent")
    void other500sAreNotResubmitted() throws Exception {
        // Re-submitting on a reason this client does not recognise would be retrying a send on a
        // guess, which is the thing the never-retries rule forbids.
        try (StubVendor vendor = new StubVendor()
                .respond("/v1/notifications", 500,
                        "{\"error\":\"Server Error\",\"reason\":\"something_else\"}")) {

            assertThatThrownBy(() -> client(vendor).submit(submission()))
                    .isInstanceOf(VendorUnavailableException.class);
            assertThat(vendor.callsTo("/v1/notifications"))
                    .as("one attempt only").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("stuck and address_known are read, not inferred")
    void stuckAndAddressKnownAreRead() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications/",
                "{\"status\":\"dispatched\",\"stuck\":true,\"address_known\":false,"
                        + "\"recipient_mask\":null,\"user_id\":\"\"}")) {

            NotificationStatus status = client(vendor).statusOf("ntf-1");

            assertThat(status.stuck()).isTrue();
            // §7: a positive statement, not an absence. Together with a null mask it is proof of
            // a non-send — stronger than a null mask alone, which could mean either.
            assertThat(status.provenNonSend()).isTrue();
            assertThat(status.recipientMaskIfAny()).isEmpty();
        }
    }

    @Test
    @DisplayName("a missing address_known is read as known, the conservative direction")
    void missingAddressKnownIsConservative() throws Exception {
        // Claiming a proven non-send on a field the service did not send would be a false negative
        // on a regulatory intimation.
        try (StubVendor vendor = new StubVendor()
                .respond("/v1/notifications/", "{\"status\":\"sent\"}")) {

            assertThat(client(vendor).statusOf("ntf-1").provenNonSend()).isFalse();
        }
    }

    @Test
    @DisplayName("the reason is the contract, so three 403s become three different answers")
    void the403ReasonsAreDistinguished() throws Exception {
        // §9: "reason is the entire message. Branch on it; never parse error." Branching on the
        // status alone reported a deactivated caller as a missing channel grant, sending someone
        // to request a permission they already held.
        assertThat(codeFor(403, "channel_not_permitted")).isEqualTo("channel_not_permitted");
        assertThat(codeFor(403, "caller_not_registered")).isEqualTo("communication_caller_not_permitted");
        assertThat(codeFor(403, "caller_inactive")).isEqualTo("communication_caller_not_permitted");
    }

    @Test
    @DisplayName("a 422 template problem is a platform fix, not a vendor outage")
    void templateProblemsAreNotOutages() throws Exception {
        // These previously fell through to VendorUnavailableException, which would send someone to
        // investigate a service that is working perfectly.
        for (String reason : new String[]{
                "template_not_found", "template_not_live_on_channel", "parameter_contract"}) {
            assertThat(codeFor(422, reason))
                    .as("422 %s", reason)
                    .isEqualTo("notification_template_unusable");
        }
    }

    @Test
    @DisplayName("a missing caller identity is a deployment fault, not a dropped message")
    void missingIdentityIsADeploymentFault() throws Exception {
        // The principal header is written by the mesh or gateway. Reaching here means the
        // deployment is misconfigured.
        assertThat(codeFor(401, "no_caller_identity")).isEqualTo("communication_no_caller_identity");
    }

    @Test
    @DisplayName("a 400 is a request this system built wrongly")
    void badPayloadIsOurBug() throws Exception {
        for (String reason : new String[]{
                "malformed_body", "request_id_required", "multi_template_not_supported",
                "contact_detail_for_unrequested_channel"}) {
            assertThat(codeFor(400, reason)).as("400 %s", reason)
                    .isEqualTo("notification_request_rejected");
        }
    }

    @Test
    @DisplayName("an unrecognised reason is an outage, never guessed at")
    void unrecognisedReasonIsAnOutage() throws Exception {
        // A reason this client has not seen is one it does not understand, and guessing what it
        // meant is how a rejection gets read as a success.
        assertThat(codeFor(418, "some_new_reason")).isEqualTo("upstream_unavailable");
    }

    @Test
    @DisplayName("an accepted submission with no notification id pages rather than being ignored")
    void acceptedWithoutAnIdPages() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications",
                "{\"submission_id\":\"sub-1\",\"notifications\":[]}")) {

            assertThatThrownBy(() -> client(vendor).submit(submission()))
                    .isInstanceOf(FmsException.class)
                    .satisfies(e -> assertThat(((FmsException) e).code())
                            .isEqualTo("notification_accepted_without_id"));
        }
    }

    @Test
    @DisplayName("reading a status maps the service's own vocabulary")
    void statusReadMapsTheVocabulary() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications/", "{\"status\":\"bounced\"}")) {
            assertThat(client(vendor).statusOf("ntf-1").status()).isEqualTo(DeliveryStatus.BOUNCED);
        }
    }

    @Test
    @DisplayName("a 502 is an outage")
    void gatewayErrorIsAnOutage() throws Exception {
        try (StubVendor vendor = new StubVendor().respond("/v1/notifications", "{}").withStatus(502)) {
            assertThatThrownBy(() -> client(vendor).submit(submission()))
                    .isInstanceOf(VendorUnavailableException.class);
        }
    }

    /** The domain code an error body produces. */
    private String codeFor(int status, String reason) throws Exception {
        try (StubVendor vendor = new StubVendor()
                .respond("/v1/notifications",
                        "{\"error\":\"Error\",\"reason\":\"" + reason + "\"}")
                .withStatus(status)) {
            try {
                client(vendor).submit(submission());
                throw new AssertionError("expected a failure for " + reason);
            } catch (FmsException e) {
                return e.code();
            }
        }
    }

    private static NotificationSubmission submission() {
        return new NotificationSubmission("4242", "shortfall_sms", MessageChannel.SMS,
                "+919451740121", Map.of("amountPaise", "500000"));
    }

    private static CommunicationClient client(StubVendor vendor) {
        return new CommunicationClient(new JsonHttp(vendor.baseUri(), Duration.ofSeconds(2), JSON),
                "svc-fms", Duration.ofSeconds(5),
                CircuitBreaker.ofDefaults("t-" + System.nanoTime()), new SimpleMeterRegistry());
    }
}
