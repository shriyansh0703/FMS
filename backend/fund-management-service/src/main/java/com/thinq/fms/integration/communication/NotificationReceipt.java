package com.thinq.fms.integration.communication;

import java.util.Objects;

/**
 * What the Communication Service returned for one accepted submission.
 *
 * @param notificationId the id to read status by later. <b>Store it</b> — the contract states
 *                       there is no way to look a notification up by {@code request_id}, so
 *                       losing this means losing the ability to reconcile the delivery at all
 * @param templateId     <b>the exact template version the service resolved.</b> This is how
 *                       REQ-625 — a delivered message must always be reconstructable — is
 *                       satisfied without this system versioning templates itself, and it is what
 *                       {@code fms_message_delivery.template_id} exists to store. An earlier
 *                       version of this client dropped the field, leaving that column unfillable
 *                       and the requirement's whole mechanism inert
 * @param submissionId   identifies the whole submission, derived from the request id
 * @param channel        which channel this receipt is for
 * @param status         always {@code accepted} on first acceptance
 * @param replayed       true when this repeated an earlier {@code request_id} and nothing was
 *                       sent a second time. Not an error — it is the idempotency working, and
 *                       the expected answer after a crash between writing an intent and
 *                       submitting it
 */
public record NotificationReceipt(
        String notificationId,
        String templateId,
        String submissionId,
        MessageChannel channel,
        DeliveryStatus status,
        boolean replayed) {

    public NotificationReceipt {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
    }
}
