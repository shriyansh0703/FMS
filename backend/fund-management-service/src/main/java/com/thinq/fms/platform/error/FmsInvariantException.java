package com.thinq.fms.platform.error;

/**
 * The system reached a state its own rules say is impossible. Maps to 500, and pages.
 *
 * <p>This is not an error a caller can fix and not a dependency being slow. It means a
 * guarantee this system makes about itself has been violated — a derivation that does not
 * reconcile, a state transition the machine forbids, an instruction key that overflowed.
 *
 * <p>The correct response is to stop rather than to degrade. Every throw site should be a
 * place where continuing would move money on a false premise.
 */
public class FmsInvariantException extends FmsException {

    public FmsInvariantException(String code, String message) {
        super(code, message);
    }

    public FmsInvariantException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    @Override
    public boolean pagesOnCall() {
        return true;
    }
}
