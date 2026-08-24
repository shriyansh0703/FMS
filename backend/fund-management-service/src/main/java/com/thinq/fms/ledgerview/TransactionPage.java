package com.thinq.fms.ledgerview;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A period's worth of entries, and what to say when there are none.
 *
 * <p><b>Rule L7 is why this is not just a list.</b> A period with no entries states that it is
 * empty, states the period, and offers a wider one — because blank space is indistinguishable from
 * a failure to load, and a trader who cannot tell the two apart assumes the second.
 *
 * @param view          which of Rule L5's two questions this answers
 * @param period        the period covered, echoed so the client renders what it actually got
 *                      rather than what it asked for
 * @param entries       newest first. Empty is a legitimate answer, not an error
 * @param widerPeriod   a period worth offering when this one is empty. Absent when entries exist
 */
public record TransactionPage(
        TransactionView view,
        TransactionPeriod period,
        List<TransactionEntry> entries,
        TransactionPeriod widerPeriod) {

    public TransactionPage {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(period, "period");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));

        if (!entries.isEmpty() && widerPeriod != null) {
            // Offering a wider period alongside results would suggest the results are incomplete.
            throw new IllegalArgumentException(
                    "a wider period is offered only when this one is empty");
        }
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public Optional<TransactionPeriod> widerPeriodIfEmpty() {
        return Optional.ofNullable(this.widerPeriod);
    }
}
