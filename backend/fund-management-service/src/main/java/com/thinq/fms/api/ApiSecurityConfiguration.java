package com.thinq.fms.api;

import com.thinq.fms.api.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The service's own access control (Stage 11, HIGH-1).
 *
 * <p><b>Why this exists when a gateway already authenticates.</b> It did not exist, and that was the
 * finding. Every controller reads its caller from {@link java.security.Principal}, a value this
 * service never validated and never populated — so the entire authentication decision belonged to an
 * upstream gateway that is not configured here, not asserted by any test here, and not named in any
 * deployment artifact in this repository. That is one layer, and it is not in this artifact. One
 * misrouted ingress or service-mesh policy error and every endpoint was reachable by anything that
 * could set a principal.
 *
 * <p>This does not replace the gateway. It makes the service refuse on its own account, so the
 * control travels with the code rather than with an assumption about topology.
 *
 * <p><b>401, not 500.</b> Before this, a missing principal surfaced as a {@code NullPointerException}
 * and was translated to {@code internal_error}. The body leaked nothing, but a burst of
 * unauthenticated probing was indistinguishable from the service being broken: it paged whoever owns
 * availability instead of whoever owns security, and a 500 invites a retry where a 401 does not.
 */
@Configuration
public class ApiSecurityConfiguration {

    private final ObjectMapper json;

    public ApiSecurityConfiguration(ObjectMapper json) {
        this.json = json;
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // Every API path requires an authenticated caller. Nothing is permitted by default:
                // a new endpoint is protected the moment it is added, rather than protected once
                // somebody remembers to list it.
                .authorizeHttpRequests(requests -> requests
                        // Health only. Note what is deliberately NOT here: /v3/api-docs and
                        // /swagger-ui/**. An anonymous specification enumerates every endpoint,
                        // parameter and error code for anyone who asks, which is free
                        // reconnaissance against a money API.
                        //
                        // If you arrived here because Swagger UI returned 401: that is the decision,
                        // not a bug. Authenticate the request, or fetch the document out of band.
                        // Adding permitAll() here to unblock a browser gives that reconnaissance
                        // away permanently to unblock one developer temporarily.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())

                // No session. Each request carries its own identity from the gateway, and a session
                // would be a second source of truth about who the caller is.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF is disabled deliberately, and the reasoning matters because the obvious
                // one is WRONG here. HTTP Basic below IS an ambient credential: a browser caches it
                // for the origin and reattaches it to requests a third-party page initiates. So the
                // usual "stateless API, no ambient credential" argument does not apply.
                //
                // What actually protects the write endpoints is narrower and is pinned where it can
                // be checked: every write mapping declares `consumes = application/json`. The three
                // content types a cross-origin HTML form can send without a preflight
                // (x-www-form-urlencoded, multipart/form-data, text/plain) are refused with 415
                // before reaching a handler, and a cross-origin JSON request requires a preflight
                // that no CORS configuration here grants.
                //
                // TWO CHANGES MAKE CSRF LIVE AGAIN, and neither looks like a security change:
                // adding a permissive CORS configuration — which the unbuilt frontend will want —
                // or accepting a form-encoded content type on any write endpoint. Re-enable CSRF,
                // or replace Basic with token authentication, before either lands.
                // PayoutCsrfSurfaceTest asserts the 415 behaviour and that no preflight is granted,
                // so either change fails a test. Note it guards the BEHAVIOUR, not the `consumes`
                // declaration: removing that annotation alone changes nothing today, because the
                // message converters already refuse those content types.
                .csrf(csrf -> csrf.disable())

                // PROVISIONAL SCHEME — security review MEDIUM-2, still open.
                //
                // Basic transmits credentials on every request and has no expiry, no revocation, no
                // rotation and no scope; a browser caches it for the origin with no application
                // control over that lifetime, which is what makes the CSRF question above live at
                // all. It is here because a chain that refuses beats a correct scheme that does not
                // exist, not because it is the right scheme for a service that moves money.
                //
                // There is also no UserDetailsService in src/main, so with the starter on the
                // classpath and no user store configured, Boot generates a password at startup and
                // logs it. That is a development affordance in a money service's boot sequence.
                //
                // The replacement is oauth2ResourceServer(jwt) against the gateway's issuer, or a
                // pre-authentication filter trusting an upstream header from a verified source.
                // Choosing between them needs an issuer URI and key set, or a header name and the
                // means of establishing that upstream is genuine — and nothing in this repository
                // records either. Whoever holds those facts changes it here and flips the scheme in
                // OpenApiConfiguration to `bearer`. OpenApiSpecTest asserts the two agree, so the
                // specification can no longer describe a scheme this chain does not accept.
                .httpBasic(Customizer.withDefaults())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, failure) ->
                                write(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "unauthenticated", "this request carries no valid identity"))
                        .accessDeniedHandler((request, response, denied) ->
                                write(response, HttpServletResponse.SC_FORBIDDEN,
                                        "forbidden", "this identity may not perform that action")));

        return http.build();
    }

    /**
     * Write the refusal in the same envelope every other error uses.
     *
     * <p>Generic on purpose. Distinguishing "no such account" from "wrong credentials" would let a
     * caller enumerate accounts one refusal at a time.
     */
    private void write(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.json.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
