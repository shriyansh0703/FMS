package com.thinq.fms.platform.error;

/**
 * A well-formed request whose values the domain refuses. Maps to 422.
 *
 * <p>The distinction from a 400 matters here: 400 means this system could not read the request,
 * 422 means it read it and the rule says no. A trader who asked to withdraw more than they have
 * made a perfectly well-formed request, and telling them it was malformed sends them looking for
 * a typo.
 */
public abstract class FmsUnprocessableException extends FmsClientException {

    protected FmsUnprocessableException(String code, String message) {
        super(code, 422, message);
    }
}
