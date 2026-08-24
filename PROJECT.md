# fms-handover-standalone

## What this is

The Fund Management System: the money surface of a broking account — pay-ins, payouts, route caps,
balance derivation, the transaction ledger view, and the messaging that tells a trader what happened
to their money. It replaces the fund-movement paths of the estate's existing platform, continuing
that platform's PostgreSQL schema from V21 rather than standing up a new one.

The repository is also the pipeline that built it. `.ai/workflows/prd-to-prod.md` defines a locked
11-stage PRD-to-production process, `hooks/` enforces it mechanically, and `.ai/artifacts/` holds
each stage's output. `CLAUDE.md` is the operating contract for any agent working here and outranks
this file.

## Stack

| Layer | Choice |
|---|---|
| Service | Java 25, Spring Boot 4.1.0, modular monolith, Maven |
| Web | Spring MVC on Tomcat 11, springdoc-openapi 3.1.0, OpenAPI generated from controllers |
| JSON | Jackson 3 (`tools.jackson`); Jackson 2 arrives transitively through swagger-core |
| Data | PostgreSQL, Spring JDBC / `JdbcClient`, Flyway forward-only from V21 |
| Resilience | Resilience4j circuit breaker and rate limiter |
| Security | Spring Security 7, HTTP Basic (provisional — see Decisions) |
| Tests | JUnit 6, Testcontainers 2, PITest for mutation coverage |
| Vendors | TechExcel, Juspay, Communication Service (JSON over HTTP); Kambala Noren (not REST) |
| Pipeline tooling | Node.js hooks, no dependencies |

Local toolchain on this machine: Homebrew `openjdk@25` leads PATH and `JAVA_HOME`; `openjdk@21` and
`openjdk` (26) remain installed behind it.

## Architecture

`backend/fund-management-service/src/main/java/com/thinq/fms/` splits by concern, not by layer:

- `api` — controllers, DTOs, the security filter chain, JSON coercion rules, rate limiting
- `movement` — payin and payout orchestration and their repositories
- `derivation` — balance derivation
- `ledgerview` — the transaction and statement read model
- `messaging` — intent, delivery, reconciliation
- `integration` — vendor gateways over a shared `JsonHttp` and `AbstractVendorGateway`
- `platform` — `Money` and the error taxonomy
- `config` — `FundsModuleConfiguration` (an `@AutoConfiguration` guarded on `DataSource`) and the
  `local` profile's stubs

State lives in PostgreSQL. Migrations under `src/main/resources/db/migration` run V21 through V27.
The service assembles nothing that needs vendor credentials it has not been given, so an
unconfigured environment degrades rather than starting and failing on its first real call.

Generated API artifacts live in `docs/api/`: the OpenAPI document, a TypeScript client, and a
Postman collection.

## Decisions

| Date | Decision | Why | Alternatives rejected |
|---|---|---|---|
| 2026-08-24 | Spring Boot 4.1.0 and Java 25 | User request | Staying on 3.5.x |
| 2026-08-24 | Migrate to Jackson 3 rather than keep Jackson 2 | Boot 4 auto-configures Jackson 3 only. Keeping Jackson 2 would mean hand-wiring message converters on a money path — more code, not less | Pinning Jackson 2 and declaring converters by hand |
| 2026-08-24 | Retire the nine 3.5-era security pins instead of carrying them forward | Against the 4.1.0 BOM every one pins *downward*. `jackson-bom.version` also changed meaning: in Boot 4 it selects Jackson 3, so the old `2.18.9` value named a release that does not exist | Keeping the block and adjusting values |
| 2026-08-24 | Depend on `spring-boot-starter-flyway`, not `flyway-core` alone | Boot 4 moved `FlywayAutoConfiguration` into a module only the starter pulls in. Without it the service started clean, ran no migrations, and 500'd on the first query | Leaving the direct dependency and setting Flyway up by hand |
| 2026-08-24 | Pin Jackson to the fixed versions (3.1.5 / 2.21.5), not the newest patch | Each pin stays auditable against the advisory it answers | Taking 3.1.6 / 2.21.6 |
| 2026-08-24 | Make `openjdk@25` the machine default | The build targets `release 25`; `java` on PATH was 21 and could not run the jar | A project-local wrapper script; running on the installed JDK 26 |
| earlier | HTTP Basic as the service's own authentication | A filter chain that refuses beats a correct scheme that does not exist. Open as security review MEDIUM-2 — replacement is `oauth2ResourceServer(jwt)` or a pre-auth filter, and neither issuer nor header is recorded anywhere in this repository | Continuing to trust an upstream gateway that no artifact here configures |
| earlier | Refuse a JSON float where an integer is declared | Jackson truncated `{"paise": 100.9}` to `100` and answered 201. The schema said integer and the deserialiser disagreed | Validating in the domain only |

## Changelog

### 2026-08-24 — Spring Boot 4.1.0 and Java 25

- **Changed**: `pom.xml` (parent 3.5.15 → 4.1.0, `java.version` 21 → 25, springdoc 2.8.17 → 3.1.0,
  resilience4j 2.2.0 → 2.4.0, pitest 1.19.1 → 1.25.9, Testcontainers 2.0 artifact renames, added
  `spring-boot-starter-webmvc-test` and `spring-boot-starter-flyway`, retired nine pins, added two
  Jackson pins). 27 Java files: Jackson 2 → 3 package and API migration, Boot 4 autoconfigure
  package moves, `JsonConfiguration` rewritten onto `JsonMapperBuilderCustomizer`,
  `ResponseEntity.unprocessableEntity()` → `unprocessableContent()`. `~/.zshrc` now leads with
  `openjdk@25`. `.ai/artifacts/security-report.md` and `swagger-verification.md` carry re-scan
  sections.
- **Why**: user request. The dependency scan and both Stage 8 gate reports described a tree that no
  longer existed once the platform moved.
- **Impact**: the service needs Java 25+ at runtime — a JDK 21 environment fails with
  `UnsupportedClassVersionError`. Deployment images need the same bump; nothing in this repository
  pins one. The generated OpenAPI document is byte-identical, so no client regeneration is needed.
  Watch the Flyway starter: removing it reintroduces a service that starts cleanly and creates no
  tables.

## Open threads

- **Security review MEDIUM-2** — HTTP Basic is provisional and there is no `UserDetailsService`, so
  Boot generates a password at startup and logs it. Needs an issuer URI and key set, or a header
  name and the means of establishing upstream is genuine. Nobody has supplied either.
- **No actuator dependency.** `ApiSecurityConfiguration` permits `/actuator/health/**` and the path
  returns 404, because the starter is not on the classpath.
- **TASK-11 and TASK-17 are halted** on missing vendor inputs — `external-questions.md` questions 6
  and 7.
- **Six of `lld-backend.md` §4.1's thirteen endpoints are unbuilt.**
- **No code-generated client** has been produced from the OpenAPI document and compiled.
- **No deployment artifact** — no Dockerfile, no CI configuration, no base image to bump for Java 25.
- **`sqlglot` and `newman` are not installed here**, so the migration syntax check and the Postman
  run recorded in earlier passes were not repeated on the upgraded tree.
- Duplicate `repackage` execution in `pom.xml`: the Boot parent already declares one, so the jar is
  built twice. Pre-existing, harmless, untouched.
