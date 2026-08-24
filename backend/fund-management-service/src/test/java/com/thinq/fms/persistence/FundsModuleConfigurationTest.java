package com.thinq.fms.persistence;

import com.thinq.fms.config.FundsModuleConfiguration;
import com.thinq.fms.ledgerview.*;
import com.thinq.fms.movement.payin.*;
import com.thinq.fms.movement.payout.PayoutRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the module actually assembles, which is the point of the configuration existing.
 *
 * <p>Until this ran, every part of the payin and ledger-view path was tested in isolation and
 * assembled by nothing: correct code that no deployed context reached. A configuration class is
 * only worth having if a context can load it, and "it compiles" does not establish that — bean
 * conditions and missing collaborators fail at refresh, not at build.
 *
 * <p>Also pins the guard in both directions. Without a {@code DataSource} the whole group must stay
 * absent, because that is what keeps the API tests — which exclude {@code DataSourceAutoConfiguration}
 * deliberately — from breaking on context load. That regression cost 47 tests once already.
 */
class FundsModuleConfigurationTest extends PostgresTestSupport {

    /**
     * The real {@code DataSourceAutoConfiguration}, driven by properties, exactly as the application
     * builds it. Not a hand-rolled stand-in: the property under test is the ORDERING between
     * {@code FundsModuleConfiguration} and the auto-configuration that contributes the
     * {@code DataSource}, and only the real one reproduces it. A stand-in with no declared
     * relationship orders arbitrarily, and a stand-in that declares {@code before} establishes the
     * ordering from the other side — which would make this test pass with the {@code after} removed,
     * the exact false assurance that let the defect reach a running service.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // Registered as an auto-configuration, exactly as the application loads it. Passing it
            // to withUserConfiguration would evaluate its @ConditionalOnBean before the DataSource
            // is contributed — the ordering trap the class was converted to avoid — and the test
            // would then be asserting a different arrangement than the one that ships.
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class, JdbcClientAutoConfiguration.class,
                    FundsModuleConfiguration.class));

    /** The container's connection, supplied the way a deployment supplies it. */
    private ApplicationContextRunner withDatabase() {
        return this.runner.withPropertyValues(
                "spring.datasource.url=" + PostgresTestSupport.jdbcUrl(),
                "spring.datasource.username=" + PostgresTestSupport.username(),
                "spring.datasource.password=" + PostgresTestSupport.password());
    }

    @Test
    @DisplayName("with a DataSource, the persistence and ledger-view beans are all present")
    void theModuleAssemblesWhenADataSourceIsPresent() {
        withDatabase().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PayinAttemptRepository.class);
            assertThat(context).hasSingleBean(PayoutRequestRepository.class);
            assertThat(context).hasSingleBean(RouteCapLedger.class);
            assertThat(context).hasSingleBean(InFlightMovementSource.class);
            assertThat(context).hasSingleBean(EntryDescriptionMapper.class);
            assertThat(context).hasSingleBean(StatementCopy.class);
        });
    }

    @Test
    @DisplayName("the in-flight source really is the payin source, not some other implementation")
    void theInFlightSourceIsThePayinSource() {
        // The bean this test exists for. An InFlightMovementSource of the wrong type would satisfy
        // every other assertion here and still leave pending deposits invisible.
        withDatabase().run(context ->
                assertThat(context.getBean(InFlightMovementSource.class))
                        .isInstanceOf(PayinMovementSource.class));
    }

    @Test
    @DisplayName("the transaction list assembles once a ledger source exists")
    void theTransactionListAssemblesWithALedgerSource() {
        withDatabase().withUserConfiguration(StubLedger.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TransactionQueryService.class);
        });
    }

    @Test
    @DisplayName("without a ledger source the transaction list is absent rather than half-built")
    void withoutALedgerSourceTheTransactionListIsAbsent() {
        // A transaction list with no ledger behind it would show a trader their pending deposits
        // and none of their settled ones, which is worse than the endpoint failing to start.
        withDatabase().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TransactionQueryService.class);
        });
    }

    @Test
    @DisplayName("without a DataSource the whole group stays absent")
    void withoutADataSourceNothingIsRegistered() {
        // This is what lets the API tests run their web layer with no database. Losing it is not a
        // subtle failure: it broke 47 tests at once.
        // No DataSource auto-configuration at all — the arrangement the API tests deliberately run
        // in. With it present but unconfigured the context fails outright, which is correct for a
        // real deployment and not the case under test here.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JdbcTemplateAutoConfiguration.class, JdbcClientAutoConfiguration.class,
                        FundsModuleConfiguration.class))
                .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PayinAttemptRepository.class);
            assertThat(context).doesNotHaveBean(RouteCapLedger.class);
            assertThat(context).doesNotHaveBean(InFlightMovementSource.class);
        });
    }


    @Configuration(proxyBeanMethods = false)
    static class StubLedger {
        @Bean
        LedgerEntrySource ledgerEntrySource() {
            return (account, from, to) -> List.of();
        }
    }
}
