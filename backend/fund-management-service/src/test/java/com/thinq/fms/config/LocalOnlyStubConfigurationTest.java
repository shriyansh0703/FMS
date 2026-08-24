package com.thinq.fms.config;

import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.movement.payout.PayoutRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard on the stub beans: they must not exist without the {@code local} profile.
 *
 * <p>These beans fabricate bank accounts and margin figures. The only thing between them and a
 * deployment is a profile check, so that check is asserted rather than trusted — and asserted in
 * the refusing direction especially, because a guard never tested for refusing is the shape of
 * guard that turns out not to refuse.
 */
class LocalOnlyStubConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalOnlyStubConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("without the local profile, no fabricating bean exists")
    void withoutTheProfileNoStubExists() {
        // The important direction. If this ever passes vacuously — the class renamed, the profile
        // string changed — a deployment could serve invented financial figures.
        this.runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ProfileClient.class);
            assertThat(context).doesNotHaveBean(BalanceDerivationService.class);
            assertThat(context).doesNotHaveBean(PayoutRail.class);
        });
    }

    @Test
    @DisplayName("with the local profile, the stubs are present so the module can be exercised")
    void withTheProfileTheStubsArePresent() {
        this.runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProfileClient.class);
            assertThat(context).hasSingleBean(BalanceDerivationService.class);
            assertThat(context).hasSingleBean(PayoutRail.class);
        });
    }

    @Test
    @DisplayName("the stub payout rail refuses to settle rather than inventing a settlement")
    void theStubRailRefusesToSettle() {
        // The one stub that must not invent anything. A fake rail reporting PAID would write a
        // settled withdrawal into the ledger and tell a trader their money had been sent.
        this.runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            PayoutRail rail = context.getBean(PayoutRail.class);
            assertThat(rail.statusOf(null, null))
                    .as("it knows about no instruction, because it never issued one")
                    .isEmpty();
        });
    }
}
