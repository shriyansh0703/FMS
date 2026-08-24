# Swagger Verification Report

**Validation status: PASS**

## What changed, and why this report reads differently now

Four implementation passes reported `N/A — no REST endpoints`, because there were none. That was
never a formality: writing the N/A is what distinguished a gate that had been considered from one
that had been forgotten, and each report named the four checks proving the tree held no controller,
no route, no web starter and no OpenAPI tooling.

This pass added the HTTP surface. So the specification is now generated, checked, and this report
records the result of that rather than the absence of a subject.

## How the specification is produced

**Generated from the controllers, not maintained beside them.** `springdoc-openapi` 2.8.17 reads
the annotated controllers at runtime; `OpenApiSpecTest` boots the application, fetches
`/v3/api-docs`, writes it to `target/openapi.json`, and asserts against it.

A hand-written specification drifts from the code silently, and the drift is discovered by whoever
generated a client from it. That is the failure this gate exists to catch, so the document is
derived rather than authored.

```
openapi 3.1.0 | Fund Management System v1

  POST   /api/v1/funds/payout                  -> 201, 400, 409, 422, 503
  GET    /api/v1/funds/payout                  -> 200, 204
  DELETE /api/v1/funds/payout/{requestId}      -> 200, 409
  GET    /api/v1/funds/payin/limits            -> 200
  GET    /api/v1/funds/transactions            -> 200, 400, 503
  GET    /api/v1/funds/transactions/{reference}-> 200, 404
  GET    /api/v1/funds/statement.csv           -> 200, 400

  security: platformJwt (HTTP bearer, applied at document level)
```

**The endpoint list is asserted as an exact set**, which is not pedantry: it caught the three
transaction routes the moment they were added, forcing them to be declared here rather than
shipping undocumented. A new endpoint fails this gate until someone has looked at it.

## What was validated, and why each check exists

Schema validity is not the interesting part — springdoc produces valid OpenAPI by construction. The
checks below are this project's own rules, which a perfectly valid document can still break. Each
one is a defect that propagates into every generated client, and those are not ours to fix
afterwards.

| Check | Result | Why it is checked |
|---|---|---|
| Every endpoint the controllers expose appears | 4 of 4 | An undocumented endpoint is one no client knows exists and no reviewer sees |
| `MoneyDto.paise` is typed `integer` | PASS | Rule R5 and HLD §9.1c. A `number` here becomes a float in every generated client |
| **No property anywhere in the document is typed `number`** | PASS | The check above only covers the money type by name; this one covers a decimal amount appearing anywhere else |
| No request schema accepts an account identifier | PASS | §4.3 resolves the account from the authenticated subject. A schema field for it invites a client to send one and a future handler to read it |
| `ErrorResponse` is published | PASS | Clients branch on `code`. An unpublished error shape means a generated client with a typed success and an untyped error |
| Every documented 4xx/5xx references `ErrorResponse` | PASS, 8 of 8 | A documented failure with an undocumented body is how error handling gets skipped |
| The JWT scheme is declared and applied globally | PASS | Every endpoint needs a validated platform token; a client omitting it fails at the gateway rather than here |
| The interactive UI is **not** served | PASS, returns 404 | A second surface to secure on a service that moves money, for no operational benefit |

## Two defects this gate found

**A wrong URL returned 500.** The catch-all exception handler swallowed Spring's own
`NoResourceFoundException`, so a mistyped path produced an internal error and a log entry reading
"unhandled exception on the API surface". Found because the UI-not-served check initially asserted
only "not 200" — tightening it to `404` exposed the real status.

The fix needed a second attempt. `NoResourceFoundException` extends `ServletException` and
*implements* `org.springframework.web.ErrorResponse`; it does not extend `ErrorResponseException`,
so the obvious handler missed it. The working version tests against the interface. A mistyped URL
now returns 404 and logs at debug, because it is not an incident.

**A validation annotation that never ran.** `PayoutRequestCommand` carried `@Positive` on an
`amountPaise()` accessor. That method is not a JavaBean getter, so Bean Validation never saw it —
the annotation implied a guard that did not exist. A non-positive amount is refused by
`PayoutOrchestrator`, which is where §4.3 puts it anyway, and the dead annotation was removed
rather than left to reassure a future reader.

## The dependency consequence of adding a web layer

Worth recording alongside the specification, because the two arrived together.
`spring-boot-starter-web` pulled in Tomcat 10.1.54, carrying **three CRITICAL and three HIGH**
advisories, plus `commons-lang3` 3.17.0. The dependency gate caught them on the first scan after
the change; both are pinned forward (Tomcat 10.1.59, commons-lang3 3.20.0) and the tree is back to
**88 packages, 0 advisories**. Details in `security-report.md`.

## What this gate does not cover

- **Seven endpoints of the thirteen in `lld-backend.md` §4.1.** The rest depend on work that is
  either unbuilt or halted: `/funds/summary` and `/funds/margin/breakdown` need a working
  `MarginSource`, which is blocked on two missing vendor contracts; `/funds/health` and the payin flow need their
  orchestration layers. This report is regenerated when they land, not amended.
- **No authentication is enforced by this service.** The platform gateway validates the token
  before FMS sees the request, and the specification declares the scheme so clients send it. This
  gate confirms the contract is published; it does not confirm a gateway is deployed in front of it.
- **The specification has never been used to generate a client.** Generating one and compiling it
  is the strongest available check that the document is usable, and it has not been done.

## Re-verification after the F-30 remediation, 21 Aug 2026

The fix for F-30 changed `PayinOrchestrator.start`, `PayinAttempt` and `PayinState`. None of the
three carries an HTTP annotation, no controller signature changed, and no request or response
model gained, lost or retyped a field. The generated specification is byte-identical to the one
this report already covers, and `OpenApiSpecTest`'s seven checks pass unchanged.

The status above therefore stands on the same evidence, not on a fresh assertion. The gap already
recorded — that six of §4.1's thirteen endpoints remain unbuilt — is unaffected by this change.


## Re-verification after the Stage 11 security remediation, 22 Aug 2026

Adding the security filter chain changed no controller signature and no request or response model.
`OpenApiSpecTest`'s seven checks pass unchanged, including the money-shape and account-identifier
assertions.

One behavioural change worth recording: `/v3/api-docs` and the Swagger UI now require authentication,
because `anyRequest().authenticated()` covers them. That is deliberate — an unauthenticated
specification enumerates the API surface for anyone who asks — and the spec test authenticates like
any other client. Clients that need the document receive it out of band.


## Re-verification after the Postman defect fixes, 24 Aug 2026

This pass changed the contract, so the specification was regenerated rather than compared. Three
defects were found by running `docs/api/fund-management.postman_collection.json` through newman
against a live instance, which is the check the section above named as never having been done: the
specification had not been used to drive a client.

It found the document lying about authentication on its first request.

```
## Swagger Verification Report
Detected framework: Java — Spring Boot 3.5.15, detected from pom.xml's spring-boot-starter-parent
                    and spring-boot-starter-web
Swagger/OpenAPI library: springdoc-openapi-starter-webmvc-api 2.8.17
Generation command: mvn -o test -Dtest=OpenApiSpecTest (boots the app, fetches /v3/api-docs,
                    writes target/openapi.json, asserts against it), then copied to docs/api/openapi.json
Generated file location: docs/api/openapi.json (21,594 bytes, OpenAPI 3.1.0)
Documentation URL: not served — springdoc.swagger-ui.enabled=false, asserted 404 by OpenApiSpecTest
Specification URL: http://localhost:8081/v3/api-docs — 200 with a Basic credential, 401 without
Endpoint count: 7 operations / 7 routes registered
Schema count: 9
Validation status: PASS — redocly lint reports the document valid, 4 warnings, all
                   operation-4xx-response on operations that declare no 4xx of their own
Warnings: redocly's operation-4xx-response fires on the four operations whose only failure is the
          document-level 401. Pre-existing, and reduced by two in this pass rather than introduced:
          the two operations that gained a 400 below no longer trigger it. Declaring a global 401
          needs an OperationCustomizer and is recorded as follow-up, not done here.
```

### The security scheme described a mechanism the service does not accept

The document declared `platformJwt` — `type: http`, `scheme: bearer`, `bearerFormat: JWT` — applied
at the document level. `ApiSecurityConfiguration` enforces HTTP Basic. Every client generated from
this specification sent `Authorization: Bearer …` and received `401 unauthenticated` on all seven
endpoints; the newman run reproduced exactly that, 8 requests and 8 refusals.

This gate passed that document. The check it ran was "the JWT scheme is declared and applied
globally", and both halves were true — the scheme was declared, and it was applied. Nothing compared
the declaration against the filter chain, so the one property that mattered went unasserted while
the report recorded the scheme as verified.

The specification now declares `platformAuth`, `scheme: basic`, which is what the chain accepts.
Two things about that are deliberate:

- **The name no longer states the mechanism.** Security review MEDIUM-2 is open on Basic's
  suitability, and its replacement is a gateway-issued token. When that lands, the scheme object
  changes and the requirement name does not, so no generated client sees a renamed security
  requirement for a change of mechanism.
- **The description says it is provisional**, and names what Basic lacks — expiry, revocation,
  rotation, scope. A specification that presents a placeholder as settled is how the placeholder
  becomes permanent.

`OpenApiSpecTest.securitySchemeMatchesWhatIsEnforced` now asserts the declared scheme *and* that a
bearer credential is refused with 401. Declaring `bearer` again while the chain enforces Basic fails
that test.

### Two operations gained a status code they could already return

Both were reachable before and neither was documented, because both arrived as `500`:

| Operation | Added | Reachable how |
|---|---|---|
| `DELETE /api/v1/funds/payout/{requestId}` | `400` | The path variable is a `long`; an unparseable segment is refused during binding |
| `GET /api/v1/funds/transactions/{reference}` | `400` | `from` or `to` that will not parse as an ISO date |

`MethodArgumentTypeMismatchException` reached the boundary's catch-all, because it implements
neither Spring's `ErrorResponse` interface nor `IllegalArgumentException`. This is the third defect
of that exact shape in this file — `NoResourceFoundException` and `HttpMessageNotReadableException`
were the first two — and the pattern is worth naming: the catch-all is the destination for anything
Spring signals through a type the handler chain does not enumerate, and every arrival there is a
`500` on a caller mistake.

`POST /api/v1/funds/payout`'s declared `409 request_already_open` was also unreachable, for a
different reason recorded in `security-report.md`: the repository never translated the constraint
violation. The specification was right and the implementation was not, which is the mirror image of
the authentication defect and would not have been caught by any check on this document alone.

### What was verified live, and what still is not

Verified against a running instance on port 8081, local stub profile, PostgreSQL 16.15:
`/v3/api-docs` returns 200 and the declared scheme matches; the Swagger UI still returns 404; all
seven endpoints answer; the full collection runs 8 requests with 9 assertions and no failures.

Still not covered: the specification has now driven a hand-written client and a Postman collection,
but no code-generated client has been produced from it and compiled. Six of `lld-backend.md` §4.1's
thirteen endpoints remain unbuilt, unchanged by this pass.

**Validation status: PASS**


## Re-verification after the Spring Boot 4.1.0 / Java 25 platform upgrade, 24 Aug 2026

The platform moved under the generator: Spring Boot 3.5.15 to 4.1.0, Java 21 to 25, and
springdoc-openapi 2.8.17 to 3.1.0 — a major-version step on the library that produces this
document. Boot 4 also replaced Jackson 2 with Jackson 3 as the JSON binding, which is what
serialises every schema in the output.

The document did not move with it. The regenerated specification is **byte-identical** to
`docs/api/openapi.json`, compared as normalised JSON with sorted keys. Nothing about the contract
changed, so no client, no Postman collection and no hand-written TypeScript client needed
regenerating.

That result is worth stating rather than assuming, because three of the four changes above are
exactly the kind that alter generated output without touching a controller: a springdoc major
version can rename schema components, a Jackson change can alter how a record's properties are
introspected, and a Java release can change how parameter names survive compilation. The check ran
because a byte-identical answer and an unverified answer look the same in a report.

```
## Swagger Verification Report
Detected framework: Java — Spring Boot 4.1.0, detected from pom.xml's spring-boot-starter-parent
                    and spring-boot-starter-web
Swagger/OpenAPI library: springdoc-openapi-starter-webmvc-api 3.1.0
Generation command: mvn -B test -Dtest=OpenApiSpecTest (boots the app, fetches /v3/api-docs,
                    writes target/openapi.json, asserts against it)
Generated file location: docs/api/openapi.json (21,594 bytes, OpenAPI 3.1.0) — unchanged
Documentation URL: not served — springdoc.swagger-ui.enabled=false, asserted 404 by OpenApiSpecTest
Specification URL: http://localhost:18080/v3/api-docs — 200 with a Basic credential, 401 without
Endpoint count: 7 operations / 7 routes registered
Schema count: 9
Validation status: PASS — redocly lint 2.47.0 reports the document valid, 4 warnings, all
                   operation-4xx-response, the same four as the previous pass
Warnings: unchanged. The four operations whose only failure is the document-level 401 still
          trigger operation-4xx-response. Declaring a global 401 needs an OperationCustomizer and
          remains follow-up.
```

### What was exercised live

A packaged jar on JDK 25.0.4.1 (`Build-Jdk-Spec: 25`, class file version 69), local stub profile,
PostgreSQL 16 in Docker, port 18080. The first run of this table used JDK 26 because that was the
only JDK on the machine above 21; openjdk@25 was installed afterwards and the table re-run on it,
so the runtime now matches the `release 25` the build targets:

| Check | Result |
|---|---|
| `/v3/api-docs` without credentials | 401 |
| `/v3/api-docs` with a Basic credential | 200, `openapi 3.1.0`, all 7 paths |
| Swagger UI | 404, still not served |
| `GET /api/v1/funds/transactions` | 200 |
| `GET /api/v1/funds/payin/limits` | 200, `remainingToday: null` on the uncapped route |
| `GET /api/v1/funds/statement.csv` | 200, header row |
| `POST /api/v1/funds/payout` with `"paise": 100.9` | 400 `invalid_request` |
| `POST /api/v1/funds/payout` with `"paise": "100"` | 400 `invalid_request` |
| `POST /api/v1/funds/payout` as `application/x-www-form-urlencoded` | 415 |
| Wrong password on any endpoint | 401 |

The two coercion rows are the ones that mattered. The rule refusing a float where an integer is
declared — the defect that accepted `100.9` paise, stored `100` and answered 201 — was installed
through a Jackson 2 mechanism that Boot 4 removed. Jackson 3's mapper is immutable once built, so
the rule moved onto the builder in `JsonConfiguration`. Both refusals were re-confirmed against a
running service rather than inferred from the test suite.

**Validation status: PASS**
