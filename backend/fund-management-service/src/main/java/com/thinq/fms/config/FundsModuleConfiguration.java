package com.thinq.fms.config;

import com.thinq.fms.ledgerview.*;
import com.thinq.fms.movement.payin.*;
import com.thinq.fms.derivation.BalanceDerivationService;
import com.thinq.fms.integration.profile.ProfileClient;
import com.thinq.fms.movement.payout.JdbcPayoutRequestRepository;
import com.thinq.fms.movement.payout.PayoutRequestRepository;
import com.thinq.fms.platform.money.Money;
import com.thinq.fms.settings.FundsSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles the parts of this service that need a database and nothing else.
 *
 * <p><b>The {@code after} is load-bearing, and its absence was found by running the service rather
 * than by any test.</b> {@code @ConditionalOnBean} is evaluated in registration order, and that
 * ordering applies among auto-configurations too: without naming {@code DataSourceAutoConfiguration}
 * here, this class could be evaluated before the {@code DataSource} exists, answer "no", and
 * register nothing — while the application failed several beans later with a missing
 * {@code RouteCapLedger}, which points at the symptom rather than the cause.
 *
 * <p>This is the same trap as the one described below, one level deeper, and it survived a test that
 * appeared to cover it: {@code FundsModuleConfigurationTest} supplied the {@code DataSource} through
 * {@code withUserConfiguration}, and user configuration is registered before auto-configuration, so
 * the condition always saw it. The test now supplies it the way the application does.
 *
 * <p><b>Why an auto-configuration rather than a scanned {@code @Configuration}.</b>
 * {@code @ConditionalOnBean} is evaluated in bean-definition order, so on a component-scanned class
 * it asks "is there a DataSource yet?" before the DataSource auto-configuration has contributed one
 * — and answers no. Spring Boot documents the condition as reliable only for auto-configuration,
 * which is processed after all user configuration. Registered through
 * {@code AutoConfiguration.imports}; {@code @SpringBootApplication}'s scan excludes
 * auto-configuration classes, so it is loaded once, by the machinery that evaluates its condition
 * correctly.
 *
 * <p><b>Why a configuration class rather than annotations on the classes.</b> The API tests
 * deliberately run without a {@code DataSource} — they exercise the web layer against stubbed
 * collaborators — so a repository annotated {@code @Repository} is constructed into a context that
 * has no {@code JdbcClient} to give it, and every one of those tests fails on context load. That
 * happened: 47 at once. Guarding the whole group on {@code DataSource} being present keeps both
 * arrangements working, and puts the wiring in one readable place instead of spreading conditions
 * across the classes being wired.
 *
 * <p><b>What this deliberately does not assemble.</b> {@link PayinOrchestrator} needs a
 * {@code ProfileClient} and a {@code PayinGateway}, and {@code TechExcelLedgerGateway} needs a
 * session, a company code and vendor credentials. Those are deployment configuration and vendor
 * integration, not wiring, and inventing values for them here would produce a service that starts
 * and then fails on its first real call. They are supplied by whatever configures the environment;
 * the beans below degrade cleanly when they are absent rather than failing at startup.
 */
@AutoConfiguration(after = {
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        JdbcClientAutoConfiguration.class})
@ConditionalOnBean(DataSource.class)
public class FundsModuleConfiguration {

    /**
     * The zone every date boundary in this service is measured in.
     *
     * <p>India has one, so this is a constant rather than a per-account value — but it is injected
     * rather than read from {@code ZoneId.systemDefault()} at each call site, because a statement's
     * day boundaries must not depend on which machine rendered it.
     */
    @Bean
    @ConditionalOnMissingBean
    public ZoneId fmsZone() {
        return ZoneId.of("Asia/Kolkata");
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock fmsClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public PayinAttemptRepository payinAttemptRepository(JdbcClient db, ZoneId fmsZone) {
        return new JdbcPayinAttemptRepository(db, fmsZone);
    }

    @Bean
    @ConditionalOnMissingBean
    public PayoutRequestRepository payoutRequestRepository(JdbcClient db) {
        return new JdbcPayoutRequestRepository(db);
    }

    /**
     * Route caps and fees.
     *
     * <p>Values live here rather than in a properties file only until someone needs to change them
     * without a deployment; the shape is what matters for wiring. NEFT carries no daily cap, which
     * {@link RouteCap} represents as an empty {@code Optional} rather than a large number — the
     * difference is visible to the trader as "no limit" instead of a limit they will never reach.
     */
    @Bean
    @ConditionalOnMissingBean
    public Map<PaymentRoute, RouteCap> routeCapConfiguration() {
        Map<PaymentRoute, RouteCap> caps = new EnumMap<>(PaymentRoute.class);
        caps.put(PaymentRoute.UPI, new RouteCap(PaymentRoute.UPI,
                Optional.of(Money.ofPaise(10_000_000L)), Money.ZERO));
        caps.put(PaymentRoute.NET_BANKING, new RouteCap(PaymentRoute.NET_BANKING,
                Optional.of(Money.ofPaise(100_000_000L)), Money.ZERO));
        caps.put(PaymentRoute.NEFT, new RouteCap(PaymentRoute.NEFT,
                Optional.empty(), Money.ZERO));
        return caps;
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteCapLedger routeCapLedger(JdbcClient db,
                                         Map<PaymentRoute, RouteCap> routeCapConfiguration,
                                         Clock fmsClock,
                                         ZoneId fmsZone) {
        return new JdbcRouteCapLedger(db, routeCapConfiguration, fmsClock, fmsZone);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.messaging.MessageOutbox messageOutbox(JdbcClient db) {
        return new com.thinq.fms.messaging.JdbcMessageOutbox(db);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.messaging.MessageLadder messageLadder(ZoneId fmsZone) {
        return new com.thinq.fms.messaging.MessageLadder(fmsZone);
    }

    /**
     * No WhatsApp opt-in on record for anyone, until REQ-624's capture is built.
     *
     * <p>False is the correct default rather than a placeholder: REQ-604 requires the step dropped
     * silently where there is no opt-in, and an unbuilt capture means there is none. Nothing
     * regulatory is suppressed by it — SMS and email are not askable through this interface.
     */
    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.messaging.MessagePreferences messagePreferences() {
        return com.thinq.fms.messaging.MessagePreferences.noOptIn();
    }

    /**
     * Exchange holidays, from configuration.
     *
     * <p>Empty by default and deliberately so: EB-6 has not nominated a source, and an empty list
     * makes {@link com.thinq.fms.settings.ConfiguredTradingCalendar} report the calendar as
     * unavailable rather than quote dates that fall on a holiday. Set
     * {@code fms.calendar.holidays} to a comma-separated list of ISO dates and arrival quoting
     * starts working; leave it unset and every arrival is honestly reported as uncomputable.
     */
    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.settings.ConfiguredTradingCalendar tradingCalendar(
            @org.springframework.beans.factory.annotation.Value("${fms.calendar.holidays:}")
            java.util.List<String> holidays) {
        java.util.Set<java.time.LocalDate> dates = holidays.stream()
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .map(java.time.LocalDate::parse)
                .collect(java.util.stream.Collectors.toSet());
        return new com.thinq.fms.settings.ConfiguredTradingCalendar(dates);
    }

    /**
     * The arrival-date quoter (REQ-707, REQ-303, Rule W5).
     *
     * <p>Adapts {@link com.thinq.fms.settings.ArrivalDateCalculator} to the orchestrator's
     * interface. The orchestrator's interface returns a bare date, so an uncomputable quote has to
     * become an exception here rather than an absent value — which is the correct failure: REQ-303
     * forbids presenting a default as though it were computed, and refusing the request is the only
     * remaining honest answer once the type cannot say "unknown".
     */
    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.movement.payout.PayoutOrchestrator.ArrivalDateQuoter arrivalDateQuoter(
            FundsSettingsHolder settings, ZoneId fmsZone,
            com.thinq.fms.settings.ConfiguredTradingCalendar tradingCalendar) {
        var calculator = new com.thinq.fms.settings.ArrivalDateCalculator(
                settings.settings().payoutCutoff(), fmsZone, tradingCalendar);
        return requestedAt -> calculator
                .quoteFor(requestedAt, false, false)
                .expectedOn()
                .orElseThrow(() -> new com.thinq.fms.platform.error.CalendarUnavailableException(
                        "the arrival date cannot be computed, so no date is quoted (REQ-303)"));
    }

    /** The tunables, so they are read rather than hard-coded (Rule G1). */
    @Bean
    @ConditionalOnMissingBean
    public FundsSettingsHolder fundsSettings() {
        return new FundsSettingsHolder(com.thinq.fms.settings.FundsSettings.defaults());
    }

    /** Wrapper so the settings record can be a bean without colliding on its component types. */
    public record FundsSettingsHolder(com.thinq.fms.settings.FundsSettings settings) {
    }

    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.movement.payout.PayoutOrchestrator.FmsReferenceGenerator fmsReferenceGenerator(
            JdbcClient db, Clock fmsClock, ZoneId fmsZone) {
        return new com.thinq.fms.movement.payout.SequentialFmsReferenceGenerator(db, fmsClock, fmsZone);
    }

    /**
     * The in-flight half of the transaction list.
     *
     * <p>This is the bean whose absence made the no-double-count guarantee untested: the source and
     * the ledger were each covered in isolation and never joined, so nothing exercised the one
     * property that matters when both are read together.
     */
    @Bean
    @ConditionalOnMissingBean
    public InFlightMovementSource inFlightMovementSource(PayinAttemptRepository payinAttemptRepository,
                                                         ZoneId fmsZone) {
        return new PayinMovementSource(payinAttemptRepository, fmsZone);
    }

    /**
     * Copy for the CSV export, resolving each {@link EntryKind} to plain language.
     *
     * <p>Rule L8a: the export has to match the screen. The enum name reaching a downloaded file is
     * how a trader ends up reading {@code ENTRY_CHARGES} in a document they send to an accountant.
     */
    @Bean
    @ConditionalOnMissingBean
    public StatementCopy statementCopy() {
        return StatementCopy.withDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public StatementCsvWriter statementCsvWriter() {
        return new StatementCsvWriter();
    }

    @Bean
    @ConditionalOnMissingBean
    public EntryDescriptionMapper entryDescriptionMapper() {
        return ConfiguredEntryDescriptionMapper.withDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.thinq.fms.movement.payin.RouteSelector routeSelector(
            RouteCapLedger routeCapLedger, Map<PaymentRoute, RouteCap> routeCapConfiguration) {
        return new com.thinq.fms.movement.payin.RouteSelector(routeCapLedger, routeCapConfiguration);
    }

    /**
     * The withdrawal orchestrator, assembled only once the figures and the destination can be read.
     *
     * <p>Conditional on {@code BalanceDerivationService} and {@code ProfileClient}, neither of which
     * this class can supply: the first needs {@code MarginSource} (TASK-11, halted on Noren) and the
     * second is an unbuilt vendor integration. Without them the application fails at startup naming
     * the missing bean, which is the correct outcome — a withdrawal path that cannot read the
     * withdrawable figure or verify the destination must not accept requests.
     */
    @Bean
    @ConditionalOnBean({BalanceDerivationService.class, ProfileClient.class})
    @ConditionalOnMissingBean
    public com.thinq.fms.movement.payout.PayoutOrchestrator payoutOrchestrator(
            PayoutRequestRepository payoutRequestRepository,
            BalanceDerivationService derivation,
            ProfileClient profile,
            com.thinq.fms.movement.payout.PayoutOrchestrator.FmsReferenceGenerator references,
            com.thinq.fms.movement.payout.PayoutOrchestrator.ArrivalDateQuoter arrivalDates,
            Clock fmsClock,
            com.thinq.fms.messaging.MessageOutbox outbox) {
        return new com.thinq.fms.movement.payout.PayoutOrchestrator(payoutRequestRepository,
                derivation, profile, references, arrivalDates, fmsClock, outbox);
    }

    /**
     * The funding orchestrator, assembled only once a gateway and a destination source exist.
     *
     * <p>{@code PayinGateway} is {@code JuspayGateway}, which needs vendor credentials this class has
     * no business inventing.
     */
    @Bean
    @ConditionalOnBean({com.thinq.fms.integration.juspay.PayinGateway.class, ProfileClient.class})
    @ConditionalOnMissingBean
    public com.thinq.fms.movement.payin.PayinOrchestrator payinOrchestrator(
            PayinAttemptRepository payinAttemptRepository,
            com.thinq.fms.movement.payin.RouteSelector routeSelector,
            RouteCapLedger routeCapLedger,
            com.thinq.fms.integration.juspay.PayinGateway gateway,
            ProfileClient profile,
            Clock fmsClock,
            com.thinq.fms.messaging.MessageOutbox outbox,
            com.thinq.fms.messaging.MessageLadder messageLadder,
            com.thinq.fms.messaging.MessagePreferences messagePreferences) {
        return new com.thinq.fms.movement.payin.PayinOrchestrator(payinAttemptRepository,
                routeSelector, routeCapLedger, gateway, profile, fmsClock, outbox, messageLadder,
                messagePreferences);
    }

    /**
     * The transaction list, assembled only once a ledger source exists.
     *
     * <p>Conditional because {@code TechExcelLedgerGateway} needs vendor credentials this class has
     * no business inventing. Without one, the query service is absent and the controller that needs
     * it fails to start — which is the correct outcome: a transaction list with no ledger behind it
     * would show a trader their pending deposits and none of their settled ones.
     */
    @Bean
    @ConditionalOnBean(LedgerEntrySource.class)
    @ConditionalOnMissingBean
    public TransactionQueryService transactionQueryService(LedgerEntrySource ledger,
                                                           InFlightMovementSource inFlightMovementSource,
                                                           EntryDescriptionMapper descriptions,
                                                           StatementCopy statementCopy,
                                                           Clock fmsClock) {
        return new TransactionQueryService(
                ledger, inFlightMovementSource, descriptions, statementCopy, fmsClock);
    }
}
