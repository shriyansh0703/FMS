package com.thinq.fms.api;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * How this system reads JSON off the wire.
 *
 * <h2>The rule this exists for</h2>
 *
 * <p>Money is an integer count of paise (rule R5, HLD §9.1c), and every layer of this codebase
 * enforces it — {@code Money} wraps a {@code long}, offers no {@code double} conversion, and the
 * published schema declares {@code paise} as an integer.
 *
 * <p>Every one of those defences sits upstream or downstream of the one place a caller actually
 * controls the value. Jackson's default is to coerce a JSON float into a {@code long} by
 * truncating it, so {@code {"paise": 100.9}} was accepted, stored as {@code 100}, and answered
 * <b>201 Created</b>. A client sending a computed value that happened to carry a fraction lost
 * money with no error, no warning and no log line.
 *
 * <p>The schema said one thing and the deserialiser did another, which is worse than having no
 * schema: a client author reads {@code integer}, sends a decimal, and is told it worked.
 *
 * <h2>Why the shape of this class changed with Spring Boot 4</h2>
 *
 * <p>The rule is identical; only where it is installed moved. Boot 3 auto-configured a mutable
 * Jackson 2 {@code ObjectMapper} and this class reconfigured it after the fact through
 * {@code Jackson2ObjectMapperBuilderCustomizer#postConfigurer}. Boot 4 auto-configures Jackson 3,
 * whose mapper is immutable once built, so coercion rules are declared on the builder instead.
 *
 * <p>That is a stricter arrangement rather than a weaker one: there is no window in which a
 * partially configured mapper exists, and no way for a later customiser to mutate the rule back
 * out. {@code HostileBodyApiTest} asserts the behaviour rather than this wiring, so a refactor
 * that loses the customiser fails a test rather than silently restoring the truncation.
 */
@Configuration
public class JsonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictJsonReading() {
        return builder -> builder.withCoercionConfig(LogicalType.Integer, config -> {
            // A float where an integer is declared is refused, not truncated. This is the fix.
            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);

            // A quoted number is refused too. "100" where an integer is declared means the client
            // has a serialisation bug, and silently accepting it hides the bug until the day it
            // sends something that is not a number at all.
            config.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        });
    }
}
