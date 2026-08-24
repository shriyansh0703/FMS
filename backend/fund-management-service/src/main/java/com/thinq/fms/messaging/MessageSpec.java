package com.thinq.fms.messaging;

import com.thinq.fms.integration.communication.MessageChannel;

import java.util.Map;
import java.util.Objects;

/**
 * One message to send: which template, on which channel, with which parameters.
 *
 * <p>Deliberately not a {@link MessageIntent}. An intent is a scheduled obligation with a state
 * assertion attached; this is the content resolved at dispatch, when REQ-621 requires the figures to
 * come from the same {@code derive()} call the screen uses. Keeping them separate is what stops a
 * figure being captured when the message was queued and sent hours later as though it were current.
 *
 * @param templateKey the Communication Service template
 * @param channel     the channel this instance is for; one submission per channel, per §10 of the
 *                    vendor contract
 * @param parameters  template variables, which must match the template's declared set exactly — the
 *                    service answers {@code parameter_contract} on any mismatch
 */
public record MessageSpec(String templateKey, MessageChannel channel, Map<String, String> parameters) {

    public MessageSpec {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(channel, "channel");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));

        if (templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey cannot be blank");
        }
    }
}
