package com.thinq.fms.funding;

import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.platform.money.AccountRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Which bank account funding uses, and what to say when there is none (REQ-706, REQ-706a).
 *
 * <p><b>The blocker is the interesting half.</b> REQ-706a forbids presenting an add-funds or
 * withdraw form to an account with no verified destination, and requires the blocker named with the
 * action that resolves it — not a disabled button, and not a form that fails on submit. Rule H6's
 * position is that a refusal the user cannot act on is worse than no offer at all.
 *
 * <p>REQ-505 distinguishes two blockers that look alike and have different answers: no account at
 * all, and an account still being verified. Telling a trader to "add a bank account" when theirs is
 * mid-verification sends them to add a second one.
 */
public final class FundingSource {

    /** Why funding is unavailable, or that it is available. Each answer is actionable. */
    public enum Availability {
        /** A verified account exists and funding can proceed. */
        AVAILABLE,
        /** No bank account on record at all. The action is to add one. */
        NO_ACCOUNT,
        /** An account exists but is not verified yet. The action is to wait, not to add another. */
        AWAITING_VERIFICATION
    }

    /**
     * The resolved state of an account's funding path.
     *
     * @param availability what, if anything, blocks funding
     * @param account      the account to use, present only when {@link Availability#AVAILABLE}
     * @param choiceNeeded whether the trader has to pick; false where exactly one is verified, which
     *     REQ-706a requires used without presenting a choice
     */
    public record Resolution(Availability availability, Optional<VerifiedBankAccount> account,
                             boolean choiceNeeded) {
        public Resolution {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(account, "account");
        }

        /** Whether a funding form may be presented at all (REQ-706a). */
        public boolean mayPresentForm() {
            return this.availability == Availability.AVAILABLE;
        }
    }

    private final ProfileClient profile;

    public FundingSource(ProfileClient profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Resolve the account funding should use.
     *
     * <p>Read at the moment of the attempt rather than from a cached list (PR-28): verification
     * status changes without this system being told, and a stale "verified" is how money is sent to
     * an account the bank has since rejected.
     */
    public Resolution resolve(AccountRef account) {
        Objects.requireNonNull(account, "account");

        List<VerifiedBankAccount> all = this.profile.accountsOf(account);
        List<VerifiedBankAccount> verified = all.stream().filter(VerifiedBankAccount::verified).toList();

        if (verified.isEmpty()) {
            // REQ-505: which of the two blockers applies decides what the trader is told to do.
            return new Resolution(all.isEmpty()
                    ? Availability.NO_ACCOUNT
                    : Availability.AWAITING_VERIFICATION, Optional.empty(), false);
        }

        // The primary where one is marked, otherwise the only one. REQ-706 makes the primary the
        // default for both directions so the trader is not asked the same question twice.
        Optional<VerifiedBankAccount> primary = verified.stream()
                .filter(VerifiedBankAccount::primary).findFirst();

        return new Resolution(Availability.AVAILABLE,
                Optional.of(primary.orElse(verified.get(0))),
                verified.size() > 1 && primary.isEmpty());
    }
}
