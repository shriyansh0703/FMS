package com.thinq.fms.integration.communication;

/**
 * The one operation the messaging module needs from the Communication Service.
 *
 * <p>Extracted as an interface so {@code MessageRelay} depends on the capability rather than on
 * {@link CommunicationClient}, which is final and holds an HTTP client. Without this seam the
 * relay could only be tested by standing up a server, and the behaviour worth testing there —
 * that a resolved state is dropped <i>before</i> anything is submitted — is exactly the behaviour
 * a live server makes hardest to observe.
 *
 * <p>Deliberately narrower than the client. Reading a notification's status belongs to the
 * delivery reconciler, and a relay that could read status might be tempted to poll after
 * submitting, which is not its job.
 */
public interface NotificationSubmitter {

    /**
     * Submit one notification on one channel.
     *
     * <p>A repeat with the same {@code request_id} returns the original and sends nothing.
     */
    NotificationReceipt submit(NotificationSubmission submission);
}
