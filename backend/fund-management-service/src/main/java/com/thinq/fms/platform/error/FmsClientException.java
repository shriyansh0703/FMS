package com.thinq.fms.platform.error;

/**
 * A failure the caller can act on. Maps to 4xx.
 *
 * <p>Never carries internal detail in its message — the message is a developer-facing
 * explanation and the {@code code} is what reaches the client.
 */
public abstract class FmsClientException extends FmsException {

    private final int httpStatus;

    protected FmsClientException(String code, int httpStatus, String message) {
        super(code, message);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return this.httpStatus;
    }
}
