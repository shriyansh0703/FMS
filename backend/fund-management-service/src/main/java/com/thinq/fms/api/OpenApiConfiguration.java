package com.thinq.fms.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published API description.
 *
 * <p>Generated from the controllers rather than maintained beside them. A hand-written spec drifts
 * from the code silently and the drift is only discovered by whoever generated a client from it,
 * which is exactly the failure the Stage 8 Swagger gate exists to catch.
 *
 * <p><b>The declared scheme is the one this service enforces, which it previously was not.</b> The
 * document declared a bearer JWT while {@link ApiSecurityConfiguration} enforced HTTP Basic, so
 * every client generated from this specification sent {@code Authorization: Bearer …} and received
 * 401 on every call. Declaring the intended scheme rather than the implemented one moves the
 * failure to whoever generates the client, which is the exact drift the Stage 8 Swagger gate exists
 * to catch — and the gate passed, because it only checked that a scheme was declared and applied.
 *
 * <p><b>Basic is provisional, and the specification says so rather than implying it is settled.</b>
 * Security review MEDIUM-2 is open on the scheme's suitability: Basic transmits credentials on
 * every request and has no expiry, revocation, rotation or scope. Its replacement is
 * {@code oauth2ResourceServer(jwt)} against the gateway's issuer, or a pre-authentication filter
 * trusting an upstream header from a verified source — and choosing between them needs an issuer URI
 * and key set, or a header name and the means of establishing that upstream is genuine. Nothing in
 * this repository records either, and inventing one produces a filter chain that compiles and
 * validates nothing.
 *
 * <p>The scheme is named {@code platformAuth} rather than after its mechanism so that resolving
 * MEDIUM-2 changes one scheme object here instead of renaming a security requirement in every
 * generated client.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI fundManagementApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fund Management System")
                        .version("v1")
                        .description("""
                                The money surface of a broking account.

                                Two conventions hold across every endpoint:

                                **Money is an integer count of paise, never a decimal.** Rule R5 of
                                the ratified event taxonomy and HLD §9.1c. A float in a published
                                schema propagates into every generated client, and those are not
                                ours to fix.

                                **Errors are branched on by `code`, never by `message`.** The code
                                is stable and safe to send to a client; the message is a
                                developer-facing explanation and is not user copy."""))
                .components(new Components().addSecuritySchemes("platformAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("""
                                        HTTP Basic, which is what the service enforces today.

                                        **Provisional.** Security review MEDIUM-2 is open on this: \
                                        Basic carries credentials on every request and has no \
                                        expiry, revocation, rotation or scope. It is here because a \
                                        filter chain that refuses beats a correct scheme that does \
                                        not exist, and it is declared here because a specification \
                                        naming a scheme the service does not accept produces \
                                        clients that 401 on every call.

                                        The deployment is expected to place a gateway in front that \
                                        validates a platform-issued token. When that issuer is \
                                        known this scheme becomes `bearer`; the requirement name \
                                        does not change.""")))
                .addSecurityItem(new SecurityRequirement().addList("platformAuth"));
    }
}
