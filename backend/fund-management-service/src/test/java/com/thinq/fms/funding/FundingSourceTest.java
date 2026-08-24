package com.thinq.fms.funding;

import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.integration.profile.VerifiedBankAccount;
import com.thinq.fms.platform.money.AccountRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-706 and REQ-706a — the default destination, and the two blockers that look alike. */
class FundingSourceTest {

    private static final AccountRef ACCOUNT = AccountRef.of("JYOTHI01");

    private FundingSource sourceWith(VerifiedBankAccount... accounts) {
        List<VerifiedBankAccount> all = List.of(accounts);
        return new FundingSource(new ProfileClient() {
            public List<VerifiedBankAccount> accountsOf(AccountRef a) { return all; }
            public Optional<VerifiedBankAccount> accountOf(AccountRef a, String r) { return all.stream().findFirst(); }
            public Optional<VerifiedBankAccount> primaryAccountOf(AccountRef a) {
                return all.stream().filter(VerifiedBankAccount::primary).findFirst();
            }
        });
    }

    private static VerifiedBankAccount account(String ref, boolean primary, boolean verified) {
        return new VerifiedBankAccount(ref, "••••" + ref, "HDFC", primary, verified);
    }

    @Test
    @DisplayName("no bank account at all names that blocker, and no form is presented")
    void noAccountNamesThatBlocker() {
        // REQ-706a forbids presenting a form the trader cannot complete, and Rule H6 prefers naming
        // the blocker to showing a disabled control.
        FundingSource.Resolution resolution = sourceWith().resolve(ACCOUNT);

        assertThat(resolution.availability()).isEqualTo(FundingSource.Availability.NO_ACCOUNT);
        assertThat(resolution.mayPresentForm()).isFalse();
        assertThat(resolution.account()).isEmpty();
    }

    @Test
    @DisplayName("an unverified account is a different blocker to no account at all")
    void unverifiedIsADifferentBlocker() {
        // REQ-505: the two have different answers. Telling a trader to "add a bank account" when
        // theirs is mid-verification sends them to add a second one.
        FundingSource.Resolution resolution =
                sourceWith(account("acc-1", true, false)).resolve(ACCOUNT);

        assertThat(resolution.availability())
                .isEqualTo(FundingSource.Availability.AWAITING_VERIFICATION);
        assertThat(resolution.mayPresentForm()).isFalse();
    }

    @Test
    @DisplayName("exactly one verified account is used without presenting a choice")
    void oneVerifiedAccountNeedsNoChoice() {
        FundingSource.Resolution resolution =
                sourceWith(account("acc-1", false, true)).resolve(ACCOUNT);

        assertThat(resolution.mayPresentForm()).isTrue();
        assertThat(resolution.choiceNeeded()).isFalse();
        assertThat(resolution.account()).get().extracting(VerifiedBankAccount::reference)
                .isEqualTo("acc-1");
    }

    @Test
    @DisplayName("the primary account is the default where several are verified")
    void thePrimaryIsTheDefault() {
        FundingSource.Resolution resolution = sourceWith(
                account("acc-1", false, true), account("acc-2", true, true)).resolve(ACCOUNT);

        assertThat(resolution.account()).get().extracting(VerifiedBankAccount::reference)
                .isEqualTo("acc-2");
        assertThat(resolution.choiceNeeded()).as("a marked primary settles it").isFalse();
    }

    @Test
    @DisplayName("several verified accounts with no primary needs a choice")
    void severalWithNoPrimaryNeedsAChoice() {
        FundingSource.Resolution resolution = sourceWith(
                account("acc-1", false, true), account("acc-2", false, true)).resolve(ACCOUNT);

        assertThat(resolution.choiceNeeded()).isTrue();
    }

    @Test
    @DisplayName("an unverified primary does not win over a verified non-primary")
    void anUnverifiedPrimaryDoesNotWin() {
        // Money must not be sent to an account the bank has not confirmed, whatever it is marked as.
        FundingSource.Resolution resolution = sourceWith(
                account("acc-1", true, false), account("acc-2", false, true)).resolve(ACCOUNT);

        assertThat(resolution.account()).get().extracting(VerifiedBankAccount::reference)
                .isEqualTo("acc-2");
    }
}
