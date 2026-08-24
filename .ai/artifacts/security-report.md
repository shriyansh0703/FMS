# Security Report

**Final status: PASS**

Stage 8, both implementation passes: TASK-06 to TASK-09, TASK-16 and TASK-30 (execution Stages 1
and 2), then TASK-10, TASK-12, TASK-14 and the interface half of TASK-13 (execution Stage 3), plus
the Stage 9 review remediation. Re-scanned on 21 August 2026 against the tree as written, not
against the plan.

Then the unblocked half of execution Stage 4 (TASK-21, TASK-27, TASK-32, TASK-34) and the
unblocked parts of Stages 7 and 8 (TASK-31, TASK-36, TASK-25). TASK-11 and TASK-17 are halted on
missing vendor inputs (see `external-questions.md` questions 6 and 7).

**Latest result: 154 main files and 66 test files scanned, 0 SAST findings, no secrets in source,
128 packages with 0 advisories, 740 tests passing.** Re-scanned 24 August 2026 against the
Spring Boot 4.1.0 / Java 25 tree; see the final section.

**Communication Service contract re-verified 21 Aug 2026** against the replacement document
(`05-dependencies/vendor-api/caller-integration.md`, superseding `Communication_API.md`). The
status vocabulary, the never-retries guarantee and the SMS `delivered` caveat are unchanged, so
`DeliveryStatus`, V26's CHECK constraint and the reconciler's reason for existing all still hold.
The error contract changed materially and the client was rewritten against it — details below.

**The web layer added attack surface, and the gate caught it immediately.**
`spring-boot-starter-web` brought Tomcat 10.1.54 — three CRITICAL and three HIGH advisories — plus
`commons-lang3` 3.17.0 with a MODERATE. All seven were fixed forward (Tomcat 10.1.59,
commons-lang3 3.20.0) and the tree returned to zero. This is the second time a routine dependency
addition introduced advisories that no code review would have surfaced, which is the argument for
scanning the resolved tree on every change rather than at milestones.

This run follows the Stage 9 iteration-3 review, which found two blocking defects in the vendor
integration layer. Both are fixed and both fixes were verified by re-introducing the defect. The
review's more important finding was that the layer had no tests at all — the one test touching it
reached a private method by reflection — so a NullPointerException on the most-used method in the
package passed 94 green tests. That layer now carries 37 test cases across five classes, all
against a real HTTP server. The dependency tree is unchanged from the first pass — the one
addition, `jackson-databind`, was already resolved transitively and was declared directly so a
Flyway change cannot silently remove the JSON parser the vendor gateways depend on.

## 1. What was scanned, and with what

| Class | Tool | Target | Coverage |
|---|---|---|---|
| Dependencies | `osv-scanner` 2.4.0 against OSV.dev | CycloneDX SBOM of the fully resolved Maven tree, test scope included | 66 packages |
| Static analysis | `semgrep` — rulesets `p/java`, `p/secrets`, `p/owasp-top-ten`, `p/sql-injection` | `src/main` | 98 files (91 Java, 7 SQL), 99 rules |
| Static analysis | `semgrep` — rulesets `p/java`, `p/secrets` | test sources | 21 files |
| SQL syntax | `sqlglot` 30.17.0, PostgreSQL dialect | the 7 migrations | All parse cleanly |
| **Schema constraints, executed** | PostgreSQL 16.15 in Docker, migrations applied in order | all 7 migrations, then `src/test/resources/db/constraint-verification.sql` | **Every constraint fires. See §8** |
| Secrets | `gitleaks` 8.30.1, `--no-git` | service directory, then the whole repository | 300 KB, then 4.15 MB |

The dependency scan reads a CycloneDX SBOM rather than `pom.xml` directly. That choice is not
cosmetic and it is the reason this report can be trusted: pointed at the POM, `osv-scanner` resolves
the parent BOM over the network, and during this pass Maven Central answered **HTTP 429**. The
scanner logged the failure, resolved nothing, reported `Total 0 packages affected by 0 known
vulnerabilities`, and **exited 0**. A clean-looking result produced by a scan that examined nothing
is the most dangerous output a security gate can emit, so every figure below was re-derived from an
SBOM generated locally by `cyclonedx-maven-plugin` from the tree Maven actually resolves.

Two controls confirm the final zero is a real zero rather than a repeat of that failure:

- the scanner reports `found 66 packages` for the run that produced it, so it demonstrably ingested
  the tree;
- the identical command against the pre-remediation SBOM still reports 30 advisories, so the
  detection path is live.

## 2. Dependency findings and their remediation

The first scan returned **30 advisories across 13 packages**. Every one of the 13 was confirmed
present in the resolved tree via `mvn dependency:tree`; none was an artifact managed by the BOM but
never depended on.

Remediation ran in two steps because the two halves have different owners.

**Step one — patch inside the declared line.** Bumping the Spring Boot parent from 3.3.4 to 3.3.13
and pinning seven libraries took the count from 30 to 7, with the test suite still passing.

**Step two — the seven that no patch could reach.** The residual carried `last_affected` rather than
`fixed` on our branch, which is the OSV encoding for a line that has stopped receiving fixes. Spring
Framework 6.1, Spring Boot 3.3 and Micrometer 1.13 are past open-source patch support, so no
published version of any of them clears these:

| Severity | Package | Advisory | Bound on the 3.3 line |
|---|---|---|---|
| HIGH | micrometer-core 1.13.15 | GHSA-g3pr-3p32-fp23 | `last_affected: 1.13.15` |
| HIGH | spring-boot 3.3.13 | GHSA-wwpq-f5c3-7hvx | `last_affected: 3.3.18` |
| HIGH | spring-core 6.1.21 | GHSA-jmp9-x22r-554x | `last_affected: 6.1.22` |
| HIGH | spring-expression 6.1.21 | GHSA-r5w3-xv2f-j59q | `last_affected: 6.1.21` |
| MODERATE | spring-expression 6.1.21 | GHSA-wxpp-56q6-5pcg | `last_affected: 6.1.21` |
| LOW | spring-core 6.1.21 | GHSA-659m-px2c-25wj | `last_affected: 6.1.21` |
| LOW | spring-expression 6.1.21 | GHSA-9f52-rjqv-25qv | `last_affected: 6.1.21` |

Four unpatchable HIGH advisories on the service that moves a trader's money is not a residual worth
carrying, so the choice went to the user rather than being settled here. The user directed a move to
Spring Boot 3.5.14.

That version alone does not finish the job, which is why it was measured rather than assumed: 3.5.14
ships Spring Framework 6.2.18 and Micrometer 1.15.11, one patch short of both fixed versions, and it
introduces GHSA-w737-wx49-qj23 against the newer Micrometer. Pinning `spring-framework.version` to
6.2.19 and `micrometer.version` to 1.15.12 on top of it closes all of them.

**Adopted configuration: Spring Boot 3.5.14 with nine security pins. Result: 66 packages, 0
advisories, 20 of 20 tests passing.**

The pins live in one commented block in `pom.xml` so a future reader knows they are load-bearing.
One deserves specific mention: `jackson-bom.version` is held at **2.18.9 deliberately, and must not
be raised to the 2.19 line**, which carries advisories of its own that remain unfixed below 2.21.4.
A routine "upgrade everything" sweep would reopen them.

## 3. Static analysis findings

Zero findings across 99 rules on 23 files of `src/main`, and zero on the test sources.

The test sources were scanned from a copy, because semgrep's default ignore list excludes paths
containing `test/`. The first run of this gate reported `Targets scanned: 23` while the tree held 25
files, and the two absent from the count were precisely the test files. Reporting that as full
coverage would have overstated the scan.

An honest reading of a zero here. The first pass was value objects, enums, exceptions and a pure
calculator, with nothing for the injection rules to bite on. The second pass changes that somewhat:
it introduces outbound HTTP, JSON parsing and four vendor clients, which is real attack surface —
just the consuming half of it rather than the serving half. Deserialisation of vendor responses is
the live risk, and it is handled by reading named fields off a `JsonNode` rather than binding to
types, so no polymorphic deserialisation gadget is reachable.

Still absent, and still where these rulesets earn their keep: any controller, any request parsing,
any template rendering, any string-concatenated SQL. This result establishes that nothing unsafe was
introduced. It does not establish that the application is secure.

## 4. Secrets

No secrets in the service directory.

The repository-wide sweep raised one finding, examined and dismissed as a false positive:

- **Rule** `generic-api-key`, `.claude/skills/lld-reviewer/SKILL.md:112`
- **Matched text** `API Contract referenced: yes/no/partial`
- **Assessment** The rule fires on the token following `API ... :` and captured the literal string
  `yes/no/partial` from a review-report output template. It is not a credential, it grants no
  access, and the file is pipeline tooling rather than part of the delivered service.

No suppression was added. A rule that occasionally over-fires on documentation is doing its job; a
suppression entry would silently cover the next real match in that file.

## 5. Security properties the code itself carries

These are design decisions in the pass that bear on security, recorded so the Stage 11 review can
check them rather than rediscover them.

- **Money cannot lose precision.** `Money` wraps a `long` count of paise. `ofVendorDecimal` accepts
  `String` and `BigDecimal` and there is **no `double` overload**, which makes
  `new BigDecimal(0.1)`-style binary imprecision unrepresentable at the boundary rather than merely
  discouraged. Summation uses `Math.addExact`, so an overflow raises rather than wrapping into a
  silently wrong figure.
- **The payout instruction key refuses to truncate.** `InstructionKey` encodes
  `(instructionSeq * 100_000) + runDateOrdinal` into TechExcel's twenty-digit `UserRefNo`. Out-of-range
  components and arithmetic overflow both throw `FmsInvariantException` and page. The failure being
  prevented is a truncated key that looks valid but belongs to a different instruction — one
  account's payout deduplicated against another's, with no error raised anywhere.
- **The vendor boundary owns no retries, on purpose.** `AbstractVendorGateway` provides timeout,
  circuit breaker, metrics, paise conversion and error translation. Retries are excluded because
  re-reading a ledger is safe and re-issuing a payout instruction is not, and a blanket retry at
  this layer would silently re-instruct payments. Retry policy sits with the caller that knows which
  kind of call it is making.
- **Unrecognised vendor responses fail closed.** The default `translate` treats anything it does not
  recognise as an outage rather than guessing at intent, because guessing is how a rejection gets
  read as a success.
- **Database invariants are enforced by the database.** Rule W4's one-open-request-per-account
  constraint and Rule A6's one-credit-per-payment constraint are partial unique indexes in V21 and
  V22, not service-layer checks, so concurrent requests cannot race past them.
- **The message-intent uniqueness gap is closed.** `fms_intent_once` did not constrain rows whose
  `asserted_ref` was NULL, because PostgreSQL treats NULLs as distinct in a unique index — so a
  ladder step written twice would have been sent twice. `asserted_ref` is now `NOT NULL` with a
  non-empty check. PostgreSQL 15's `NULLS NOT DISTINCT` was deliberately not used: no server version
  is pinned in any approved artifact, and a constraint that silently stops constraining on an older
  server is the same defect wearing a newer syntax.
- **State vocabularies are closed where the design fixes them.** V21 now carries a `CHECK` listing
  the eight payout states, which is what makes Rule W4's partial index equivalent to the rule it
  claims to enforce — the predicate names open states, and without a closed vocabulary a typo or a
  later addition slips past it silently. `PayoutStateTest` reads V21 and fails if the enum and the
  migration disagree in either direction. The payin and message vocabularies are deliberately left
  open, because no artifact assigns codes to them yet and inventing them in a migration would be
  the wrong place to decide.
- **No personal data reaches a payment gateway.** Juspay's `/orders` accepts 113 documented fields,
  most of them billing and shipping address components plus customer email and phone. The client
  populates the payment fields and the UCC, and nothing else — a field never sent cannot leak from
  a gateway's logs.
- **No unmasked bank account number can enter this system.** `VerifiedBankAccount` refuses a
  "masked" value carrying more than six digits at construction, so a Profile response that failed to
  mask server-side (PR-31) is rejected rather than persisted onto a payout request and rendered into
  a message months later.
- **Rule B9's clamp still has exactly one production caller.** Writing the route-cap ledger
  introduced a second use of `Money.flooredAtZero()` for payment headroom, which would have made
  that method's "Rule B9's single exception" documentation false and spread a clamp that is meant
  to be conspicuous. It was replaced with an explicit floor carrying its own reason, plus an
  `isOverCap` predicate so the anomaly it hides — a cap lowered below what has already gone out —
  stays detectable rather than being zeroed away.
- **The statement export's account-number guard is scoped to the free-text fields.** It applies to
  the description and the reference, which could carry one, and not to the amount columns, which
  this system generates from a `long` of paise and which cannot. Scanning the amounts refused every
  export for an account holding roughly a crore or more — the guard was right and its scope was
  wrong, which is a reminder that a security control applied too widely fails a requirement just as
  surely as one applied too narrowly.
- **Communication errors branch on the reason, not the HTTP status.** The contract states that
  `reason` is the entire message and that a caller must never parse `error`. Branching on status
  alone collapsed three distinct 403 causes into one — a deactivated caller was reported as a
  missing channel grant, sending someone to request a permission they already held — and reported
  every 422 template fault as a vendor outage, sending someone to investigate a service that was
  working. `CommunicationReason` now carries the full vocabulary, classified by what a caller can
  actually do about it.
- **An ambiguous 500 on submit is resolved by re-sending under the same request_id.** This is the
  one documented case where re-sending is correct and it does not contradict the never-retries
  rule: nothing retries a *send*, and re-submitting the same idempotency key resolves whether a
  send happened at all. The alternative is a message this system believes it never sent, sitting
  delivered in the trader's inbox. Exactly once, and only for `internal_error`.
- **The resolved template version is captured.** `fms_message_delivery.template_id` exists so a
  delivered message stays reconstructable without this system versioning templates (REQ-625). The
  client had been dropping the field, leaving the column unfillable and the requirement's whole
  mechanism inert.
- **A stuck hand-off is read, not inferred.** The platform reports `stuck` directly; the reconciler
  had been deriving it from a poll window, which spends the part of a live deadline that was still
  usable. `address_known: false` with a null mask is now carried as positive proof of a non-send —
  a stronger finding than an absent mask, and the difference matters for a regulatory intimation.
- **A repeat payment confirmation credits nothing.** Rule A6 treats repeat confirmations as an
  expected condition, because a gateway retries on anything that does not look like success.
  `PayinOrchestrator.onGatewayConfirmation` is idempotent and returns normally on a repeat having
  changed nothing, and route headroom is consumed only on the confirmation that actually changed
  state. V22's partial unique index on `gateway_payment_ref` is the guarantee underneath.
- **A confirmation for an unknown reference is refused.** It is neither a repeat nor a late
  arrival: it is a confirmation for a payment this system never started, and recording it would
  credit money against nothing.
- **An unknown gateway status never becomes a failure.** Rule A9b: the recovery for "the bank has
  not answered" is the opposite of a failure's — wait, and specifically do not retry. Offering a
  retry there is how one payment becomes two, so `PayinOutcome.UNKNOWN` maps to the awaiting state
  and reports `mayRetry() == false`.
- **A confirmed deposit cannot appear twice in the history.** The movements view is a union of the
  ledger and the in-flight attempt source; `PayinMovementSource` excludes anything that
  `affectsBalance()`, keyed on the state's own property rather than a hand-written list, so a future
  crediting state cannot be forgotten. Tested, including the mutation that removes the filter.
- **The statement export validates before a byte is streamed.** The PR-32 check lives inside the
  writer, which runs inside the streaming response body — by which point the 200 status line has
  already been sent, so a violation arrived as a truncated file rather than as a refusal. The
  controller now validates the rows first and answers 400. Found by a test asserting the status,
  not by reading the code, which read correctly.
- **The statement export refuses to write a possible account number.** Profile PR-32 forbids an
  unmasked number anywhere in an export, and `StatementCsvWriter` throws on any field carrying nine
  or more consecutive digits rather than redacting it. Refusing is the deliberate choice: a
  redaction produces a plausible file built from a value that should never have reached that layer,
  and the upstream defect goes unnoticed. The threshold can produce false positives on a long
  back-office reference, which is the direction to err in — a failed export is visible and fixable,
  a saved CSV containing an account number is neither.
- **The statement export neutralises spreadsheet formula injection.** A field beginning `=`, `+`,
  `-` or `@` is prefixed with an apostrophe, because Excel and Sheets execute those on open. The
  content originates from a back office rather than a trader, which lowers the likelihood and not
  the consequence.
- **Authorisation is a parameter, not a path.** Every `PayoutRequestRepository` method takes an
  `AccountRef`, so a caller cannot forget to scope a read. A request belonging to another trader
  answers "not found" rather than "forbidden" — confirming existence would itself leak.
- **A pending payout is no longer read as a completed one.** TechExcel places a payout entry in an
  authorisation queue and reports `AUTHO` 1 or 0. That flag was not read, so a queued payout was
  classified `PARTLY_PAID` with nothing sent — a terminal state that closed the request and told
  the trader they had received zero while the money had not moved. `InstructionResult` now
  distinguishes a settled outcome from a pending one, and the request stays `INSTRUCTED` until the
  rail actually acts.
- **Vendor operation names cannot go high-cardinality.** `requireLowCardinality` refuses any metric
  tag that is not short, lowercase and digit-free, so an account identifier interpolated into an
  operation name fails at the first call rather than as a metrics bill weeks later.
- **The instruction key's bound is now correct at every run date.** `MAX_INSTRUCTION_SEQ` reserved
  no room for the ordinal added after the multiplication, so the largest permitted sequence
  overflowed for most dates. It fails closed either way, but the constant no longer misstates its
  own guarantee.
- **The account is resolved from the authenticated principal, never from the request.** Every
  endpoint takes it from the token's subject via `AuthenticatedAccount`, and every repository
  method takes it as a parameter — so another trader's request answers "not found" rather than
  "forbidden", because confirming existence would itself leak. The published schema carries no
  account field at all, and a test asserts that: a schema field would invite a client to send one
  and a future handler to read it.
- **No exception reaches a client as a stack trace.** Every domain exception has one edge
  representation, an upstream failure names no vendor to the client, and an invariant failure
  returns a generic body while logging the detail. The catch-all was found returning 500 for a
  mistyped URL, which is now a 404 logged at debug.
- **The interactive API UI is not served.** It is a second surface to secure on a service that
  moves money, for no operational benefit; a test asserts it returns 404.
- **Vendor credentials are never logged.** `TechExcelSession` holds the login name and password and
  passes them only in the login body; `VendorHttpException` retains response bodies for operational
  mapping and is documented as never trader-facing.

## 6. Limits of this report

Stated plainly, because a gate that overstates its coverage is worse than no gate.

- The scans cover execution Stages 1 to 3 of the plan. No authentication, authorisation, endpoint
  or session-handling code exists yet, so nothing here speaks to those.
- **No vendor call has ever been executed.** Every gateway is written against a contract and tested
  against constructed JSON. Nothing here demonstrates that a real TechExcel, Juspay or Communication
  Service endpoint behaves as its documentation says, and the settlement mapping in particular
  carries a trader-facing consequence if it does not.
- TLS, certificate pinning and outbound credential storage are deployment concerns and are not
  configured in this pass. `JsonHttp` refuses to follow redirects, which is the one transport-level
  control it does make.
- No dynamic analysis and no dependency-confusion or typosquatting check ran.
- **Closed.** The migrations have now been applied to a real PostgreSQL 16.15 and every constraint
  exercised. See §8 below. This was the top outstanding item across five review iterations.
- REQ-503 remains undesigned and blocked on EB-6, so no code exists for it to be scanned.
- Phase 3 remains gated on the authentication team's ruling on out-of-band protection for
  withdrawal. That control is not FMS's to build, and this report does not assert it is handled.

The final security gate is Stage 11, which reviews the system rather than one implementation pass.

## 8. The schema, executed

Five review iterations named this the highest-value outstanding verification, and three of them
found constraint-level defects by reading. It has now been done.

All seven migrations applied to PostgreSQL 16.15 in order, on a fresh database, followed by
`src/test/resources/db/constraint-verification.sql`. Every constraint refused exactly the row it
exists to refuse, and — as importantly — permitted the rows it must not block.

| Constraint | Rule | Verified |
|---|---|---|
| `fms_payout_one_open_per_account` | W4 — one open request per account | A second open request is rejected; a new one after the first reaches `PAID` succeeds |
| `fms_payout_state_vocabulary` | closes the vocabulary the W4 predicate depends on | A lowercase `'accepted'` is rejected |
| `fms_payout_refs_differ` | C8 — bank and FMS references never coincide | Setting them equal is rejected |
| `fms_payout_sent_within_request` | a settlement sends less, never more | Both over-send and negative are rejected |
| `fms_payin_gateway_ref` | A6 — one credit per payment | A repeat confirmation is rejected; two attempts not yet at the gateway both succeed |
| `fms_intent_once` + `asserted_ref NOT NULL` | one intent per occurrence per channel | A repeated ladder step is rejected; null and empty refs are rejected |
| `fms_snapshot_*_vocabulary` | source, reconciliation, context | Unknown values rejected |
| `fms_msg_status_vocabulary`, `fms_msg_channel_vocabulary` | the service's ten values; whatsapp excluded under OA-2 | Both rejected |
| `fms_mse_kind_vocabulary`, `fms_mse_actor_vocabulary` | movement kind and actor | Both rejected |
| Range partitioning + DEFAULT | audit rows are never lost to a missing partition | A September row lands in its month; a December row lands in the overflow rather than failing |

**The finding that justified the whole exercise.** `fms_intent_once` over a nullable
`asserted_ref` was reported as a blocking defect in the first Stage 9 review and fixed on the
strength of PostgreSQL's documented `NULLS DISTINCT` behaviour rather than an executed test. That
reasoning is now confirmed: a copy of the table with the column made nullable again accepted
**three identical intents**, which is a trader receiving the same margin-shortfall message three
times.

**A second claim confirmed, and it carries an operational deadline.** With a row sitting in the
DEFAULT partition, `ATTACH PARTITION` for the range covering it is refused —
`updated partition constraint for default partition ... would be violated by some row`. Draining
the default first then permits the attach and the row re-homes correctly. So a non-empty default
partition is the point at which catching up stops being free. **The declared partitions end
2026-10-31, and monthly partition creation must be automated before then.**

The script is checked in and takes about ten seconds. It is deliberately not a Testcontainers
test: adding a container framework to a service that does not otherwise need one is a dependency, a
cost on every build, and another thing to patch.

## 7. Reproducing this

```bash
cd backend/fund-management-service
mvn -B clean test
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
    -DoutputFormat=json -DincludeTestScope=true -DoutputName=final
osv-scanner scan source -L target/final.cdx.json
semgrep --config=p/java --config=p/secrets --config=p/owasp-top-ten --config=p/sql-injection src/main
gitleaks detect --no-git --source . --redact

# schema constraints, against a real database
docker run -d --name fms-pg -e POSTGRES_PASSWORD=fms -e POSTGRES_DB=fms -p 55432:5432 postgres:16-alpine
for f in src/main/resources/db/migration/V2*.sql; do
  docker exec -i fms-pg psql -U postgres -d fms -v ON_ERROR_STOP=1 < "$f"; done
docker exec -i fms-pg psql -U postgres -d fms < src/test/resources/db/constraint-verification.sql
```

Check the scanner's `found N packages` line before trusting any dependency result. A rate-limited
run reports zero findings and exits successfully.

## Re-verification after the F-30 remediation, 21 Aug 2026

F-30 was an availability-and-integrity defect rather than a vulnerability: a Juspay call that timed
out left the payin attempt without the reference its confirmation would arrive under, so money that
reached the firm was refused. The remediation commits the reference before the vendor call and
widens `PayinState` so an unacknowledged attempt can still resolve.

Scans re-run over the changed package:

- Semgrep, `--config=auto`, 11 files under `movement/payin/`: **0 findings**.
- No dependency added, removed or upgraded, so the 88-package / 0-advisory result from the SBOM
  scan in §4 is unchanged and was not re-run.
- No secret, credential or token is introduced by the change; the gateway reference is a derived
  public identifier of the form `FMS-PAYIN-<id>` and carries no account data.

One property worth recording, because the change makes an attempt addressable earlier than before:
the reference is derived from the attempt's own primary key and is looked up through
`findByGatewayRef`, which scopes nothing by account. `onGatewayConfirmation` therefore trusts the
reference alone. That was already true before this change and is the correct shape for a gateway
callback, which has no user session to scope by — but it means the callback endpoint's own
authentication is what stands between a guessed reference and a state transition. That endpoint is
among the six not yet built, and this constraint is carried forward to whoever builds it.

**Final status: PASS**

## Re-verification after the persistence work, 21 Aug 2026

Two JDBC repositories, a Testcontainers harness and thirty tests were added. Scans and review over
the new code:

- Semgrep, `--config=auto`, over both new repository classes: **0 findings**.
- **No SQL is built from caller input.** Every value binds through `params(...)`. The only string
  concatenation in either statement builder is of two private static constants — the column list and
  the open-state list — and both are compile-time literals. Grepping for concatenation into SQL
  returns only exception-message construction.
- **Reads are scoped by account in the WHERE clause**, not by a caller remembering to check
  afterwards. `findByGatewayRef` is the single deliberate exception, and it is the one the gateway
  callback uses, which has no session to scope by. That reinforces the constraint already recorded
  in this report: the callback endpoint's own authentication is what stands between a guessed
  reference and a state transition, and that endpoint is not yet built.
- No dependency with a runtime footprint was added. `spring-boot-testcontainers`,
  `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` are all `test` scope and
  do not ship. Versions come from the Spring Boot parent's BOM rather than being pinned separately.
- **No secret is introduced.** The PostgreSQL container's credentials are Testcontainers' generated
  per-run values, not committed strings, and the container is bound to an ephemeral port and removed
  when the JVM exits.

One security-relevant improvement rather than a finding: the constraints that carry business rules
are now executed on every build instead of being verified by reading. Rule W4's one-open-request
index, Rule A6's gateway-reference uniqueness and Rule C8's bank-reference check each have a test
asserting they **refuse** the row they exist to refuse, and the W4 test was confirmed to fail when
the index predicate is narrowed. Constraints of this kind fail silently when wrong, so executing
them is the only evidence that carries.

**Final status: PASS**

## Re-verification after the module wiring, 21 Aug 2026

Added since the last entry: `JdbcRouteCapLedger`, `FundsModuleConfiguration` (an auto-configuration),
and 21 tests.

- Semgrep, `--config=auto`, over the new main sources: **0 findings**.
- The cap ledger's `record` is a single `INSERT ... ON CONFLICT DO UPDATE`, with every value bound
  through `params(...)`. No SQL is assembled from caller input. The accumulation happens in the
  database against the row the conflict locked, not against a value this process read earlier —
  verified under twenty concurrent writers, and confirmed to fail when replaced with a
  read-modify-write.
- **A latent misconfiguration was found and fixed, and it is worth recording as a security-relevant
  class of defect.** `FundsModuleConfiguration` began as a component-scanned `@Configuration`
  carrying `@ConditionalOnBean(DataSource.class)`. That condition is evaluated in bean-definition
  order, so in the running application it would have answered "no DataSource" and registered nothing
  — silently. A guard that silently does not apply is the same failure shape as a constraint that
  silently does not constrain, and neither shows up in a build. It is now an `@AutoConfiguration`
  with a test asserting the beans in both the present and absent cases.
- No new dependency. No secret. The auto-configuration hardcodes route caps and the `Asia/Kolkata`
  zone as defaults, all non-sensitive, and every bean is `@ConditionalOnMissingBean` so a deployment
  can override without editing code.

The constraint already recorded twice in this report still stands and is unchanged by this work:
`onGatewayConfirmation` trusts the gateway reference alone with no account scoping, which is correct
for a callback that has no session — and means the callback endpoint's own authentication is the
control on that path. That endpoint is still not built.

**Final status: PASS**

## Re-verification after mutation testing, 21 Aug 2026

Added: the `pitest-maven` plugin (build-time only, skipped by default), `PayinStateTest` and
`PayinAttemptTest`. No production source changed.

- No runtime dependency added. PIT and `pitest-junit5-plugin` are Maven plugin dependencies and are
  never packaged.
- One security-relevant result: mutation testing showed that replacing `PayinState.canTransitionTo`
  with `return true` — which disables every transition rule in the payin state machine — survived
  the whole suite. Nothing asserted that an illegal state change is refused. A state machine that
  can be silently defeated is an integrity control in name only, and on this table the moves it
  forbids include crediting an attempt already recorded as failed. Now covered, and the class is at
  a 100% mutation score.
- The `version++` mutation surviving is the same class of problem one layer down: that field is the
  optimistic lock on a money row, and an increment going the wrong way makes concurrent writers
  agree when they should collide. Also now covered.

Neither was exploitable as it stood — both are guards that were correct and merely unverified — but
an unverified guard is indistinguishable from a broken one until something checks, which is the
argument for having run this at all.

**Final status: PASS**

## Re-verification after the communications ladder, 21 Aug 2026

Added: `MessageLadder`, address validation in `NotificationSubmission`, and 55 tests.

- Semgrep over both changed sources: **0 findings**.
- **F-33 is closed, and it was a PII-adjacent defect rather than a cosmetic one.** An unvalidated
  address is not rejected anywhere downstream: the platform validates shallowly by design, and both
  providers accept and bill anything address-shaped. A margin-shortfall intimation — which names an
  amount owed and an account state — sent to a mistyped number is a disclosure of personal financial
  information to a stranger, recorded in the delivery log as a success. The three traps the contract
  names are now all refused at the boundary.
- **The validator refuses; it never normalises.** That is a deliberate security property, not a
  style choice. The contract states that the local part of an email is case-sensitive and that
  folding it changes who receives the message, so a validator that tidied its input would introduce
  exactly the misdelivery it exists to prevent. `noAddressIsRewritten` asserts the absence.
- No new dependency, no secret, no new external surface. `MessageLadder` performs no I/O at all.
- The ladder deliberately cannot suppress a regulatory message: no preference is an input to
  `forMarginShortfall`'s SMS and email steps, so Rule C13 is enforced by the shape of the method
  rather than by a check someone could later reorder.

**Final status: PASS**

## Re-verification after the message catalogue, 22 Aug 2026

Added: `MessageSpec`, `PayinMessages`, `PayoutMessages` and 46 tests. All three classes are pure and
perform no I/O.

- Semgrep over the new sources: **0 findings**.
- **Two disclosure controls are now enforced in code rather than left to template authoring.**
  `PayinMessages.confirmed` refuses a source value carrying more than four digits, so a full bank
  account number cannot reach a message even if a caller passes one; and the confirmation carries no
  ledger balance, which is asserted as an absence because that is the only way a non-disclosure can
  be tested.
- **Rule C8 is enforced by refusal.** Passing this module's own reference as the bank's throws rather
  than being accepted and rendered. A trader given our reference takes it to their bank, which has
  never seen it — a support cost rather than a breach, but the same class of substitution error that
  makes reconciliation impossible later.
- Rule C5's conditional refund wording is decided from the outcome rather than written per template,
  so no message can assert that nothing was debited for an outcome where the bank may have taken the
  money.
- No new dependency, no secret, no new external surface.

**Final status: PASS**

## Remediation of the Stage 11 findings, 22 Aug 2026

HIGH-1 and MEDIUM-2 from `security-review.md` are fixed. MEDIUM-1 resolved with HIGH-1.

**HIGH-1 — the service now performs its own access control.** `ApiSecurityConfiguration` adds a
`SecurityFilterChain` with `anyRequest().authenticated()`, stateless sessions, and a 401 entry point
returning the same generic envelope every other error uses. The default is deny, so the six unbuilt
endpoints arrive protected rather than protected once someone remembers to list them. CSRF is
disabled deliberately, not by omission: the API is stateless and token-authenticated, so there is no
ambient credential for a cross-site request to ride on.

`PerAccountRateLimit` adds three budgets keyed on the authenticated account — reads, exports and
money movement — applied by an interceptor across `/api/**`. Per account rather than global, because
a global limit on a multi-tenant money API is a denial-of-service primitive: one trader exhausting it
locks out every other. Every budget has a zero timeout, since a limiter that waits converts a burst
into held request threads, which is the exhaustion it was added to prevent. Anything that is not a
plain GET is classified as money movement by default, so a write endpoint added later is metered
tightly until someone decides otherwise.

This is a per-instance floor, not a complete answer: behind N replicas the effective limit is N times
these numbers. A distributed limiter belongs at the gateway, which sees all traffic. The point is
that the control now exists in this artifact.

**MEDIUM-2 — the callback obligation is now compile-time.** `onGatewayConfirmation` requires a
`VerifiedGatewayCallback`, a receipt that cannot be constructed without naming what was verified. The
method still authenticates nothing itself and still finds the attempt by an enumerable reference —
that is correct for a callback — but the endpoint cannot now be written without confronting the
obligation in code rather than in a document nobody opens. The escape hatch is deliberately named
`notFromAGatewayCallback` so a reviewer seeing it in the callback endpoint knows at a glance the
signature check is missing.

**Verified, not asserted.** `ApiSecurityTest` (8) drives every endpoint unauthenticated and asserts
401 with no data, including a malformed write body — a 400 there would prove an unauthenticated
caller can probe validation. `PerAccountRateLimitTest` (6) proves the budget refuses, that one
account cannot exhaust another's, and that the budgets are separate.

**A dependency finding this work surfaced, and fixed.** Re-scanning after adding
`spring-boot-starter-security` reported **three advisories**, two of them introduced by the new
dependency: `spring-security-web` 6.5.10 (GHSA-293q-567p-wmwq, CVSS 6.8; GHSA-x2r2-rvhq-2mqv, CVSS
6.1) and `spring-boot-autoconfigure` 3.5.14 (GHSA-ggg2-9786-hwc8, CVSS 5.3). Spring Boot moved to
**3.5.15**, and the re-scan reports **67 packages scanned, no issues found** — the package count is
quoted because a scan that resolves nothing also reports zero, which is the failure mode this report
already records against osv-scanner.

Semgrep over the new security code: **0 findings across 18 files**. 577 tests pass; coverage 92.5%
instruction, 79.9% branch.


---

## Re-scan after the Postman defect fixes, 24 August 2026

Three defects found by running the published Postman collection through newman against a live
instance. Two are security-relevant and one is the authentication contract itself.

```
## Security Report
Total scans run: 4
  1. Dependencies — osv-scanner 2.4.0 against a CycloneDX SBOM of the fully resolved tree,
     test scope included:
     mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DoutputFormat=json -DincludeTestScope=true
     osv-scanner scan source -L target/bom.json
  2. SAST (main) — semgrep 1.172.0, rulesets p/java p/secrets p/owasp-top-ten p/sql-injection,
     over src/main
  3. SAST (tests) — semgrep 1.172.0, rulesets p/java p/secrets, over the test sources
  4. Secrets — gitleaks 8.30.1, `gitleaks dir`, over the service directory and then docs/ and .ai/

Findings per scan:
  Dependencies — 0 critical, 0 high, 2 MEDIUM, 0 low. Both on
    org.apache.commons:commons-compress 1.24.0 (test scope), reached via
    spring-boot-testcontainers 3.5.15 -> testcontainers 1.21.4:
      GHSA-4265-ccf5-phj5, CVSS 6.7  — resource exhaustion on malformed archive input
      GHSA-4g9r-vxhx-9pgx, CVSS 5.9  — infinite loop on crafted input
    Both fixed in 1.26.0. Confirmed absent from the built jar (`unzip -l` finds no match),
    so nothing shipped carried them.
  SAST (main) — 0 findings. 124 rules over 154 targets, 144 of them Java, which is every
    file in src/main.
  SAST (tests) — 0 findings. 96 rules over 67 targets.
  Secrets — 0 real secrets. 3 pattern hits, all examined, all false or inert. See below.

Fixes applied:
  commons-compress 1.24.0 -> 1.26.0, pinned in pom.xml's dependencyManagement with the
    reasoning beside it. The Spring Boot BOM does not manage this artifact, so a property
    alone would not have moved it. Root cause: a transitive test dependency ages independently
    of the direct ones, so nothing in this repository would have raised it.

Rescans: 1.
  Before the pin: 110 packages, 2 medium advisories.
  After the pin:  111 packages, "No issues found".
  The pre-pin result is quoted because it proves the detection path is live — a scan that
  resolves nothing also reports zero, which this report already records as osv-scanner's most
  dangerous failure mode.

Final status: PASS — no unresolved findings at any severity
```

### The authentication contract described a scheme the service does not accept

`OpenApiConfiguration` declared `platformJwt` — HTTP bearer, JWT — while `ApiSecurityConfiguration`
enforces HTTP Basic. Every client generated from the published specification sent
`Authorization: Bearer …` and received `401 unauthenticated` on all seven endpoints.

**Not exploitable, and still worth recording here.** Nothing was reachable that should not have
been; the chain refused, which is the correct direction to fail. What was wrong was the published
description of how to authenticate, and the consequence is a security control that appears broken
to everyone integrating against it. A control that everybody works around is a control on its way to
being disabled, and "make Swagger work" is precisely how `permitAll()` gets added to a matcher —
which LOW-3 in this report already warns about.

The specification now declares `platformAuth`, `scheme: basic`. Three deliberate choices:

- **The name states the role, not the mechanism.** MEDIUM-2 below remains open and its resolution
  changes the mechanism. Naming the requirement after the mechanism guarantees a rename in every
  generated client when it is resolved.
- **The description says the scheme is provisional**, and names what Basic lacks: expiry,
  revocation, rotation, scope. A specification presenting a placeholder as settled is how a
  placeholder becomes permanent by default.
- **`OpenApiSpecTest.securitySchemeMatchesWhatIsEnforced` asserts the declaration against
  behaviour**, driving a bearer credential at a live endpoint and requiring 401. Declaring `bearer`
  while the chain enforces Basic now fails a test rather than reaching a client.

**MEDIUM-2 is unchanged and stays open.** The replacement is still `oauth2ResourceServer(jwt)`
against the gateway's issuer or a pre-authentication filter over a verified upstream header, and
this repository still records neither an issuer URI and key set nor a header name. Nothing was
invented to close it. What did change: the scheme is now marked PROVISIONAL at
`ApiSecurityConfiguration`'s `httpBasic` line, naming what it lacks and what replaces it. The
earlier revision of this report stated that marker existed; it did not, and the wording has been
made true rather than left as a claim.

### Rule W4 was enforced by the database and lost at the boundary

`PayoutRequestRepository.save` contracts `@throws RequestAlreadyOpenException translated from the
unique-index violation`. `JdbcPayoutRequestRepository` contained no `catch` at all, so Spring's
`DuplicateKeyException` escaped to the error boundary's catch-all and a second withdrawal
submission answered **500 internal_error** where the specification declares **409
request_already_open**.

**The rule itself never failed.** The partial unique index refused the row, which is the property
that matters and which no amount of boundary code can substitute for. What failed is everything
downstream of the refusal: the caller could not distinguish "you already have one" from "this
service is broken", the response invited a retry that would fail identically, and the failure logged
at ERROR as an unhandled exception — so a trader double-submitting paged whoever owns availability.

**Why no test caught it, which is the more useful finding.** Both tests of Rule W4 ran against
doubles that threw `RequestAlreadyOpenException` directly — `ApiTestConfiguration:265` and
`PayoutOrchestratorTest:292` — so the translation was assumed by every test that appeared to cover
it. `SchemaConstraintTest` asserted the raw `DataIntegrityViolationException`, pinning the database's
behaviour rather than the repository's contract. The rule had three tests and a gap that all three
were shaped around.

The translation is keyed on the index name rather than on "a duplicate key occurred", because this
table carries a second unique index (`fms_payout_fms_reference`); reporting its violation as
`request_already_open` would tell a trader they hold an open withdrawal when the reference generator
had issued a number twice — an invariant failure that must page, dressed as an ordinary refusal.
`JdbcPayoutRequestRepositoryTest` now asserts both halves against a real PostgreSQL server.

### Unconvertible parameters returned 500 and logged at ERROR

`MethodArgumentTypeMismatchException` implements neither Spring's `ErrorResponse` interface nor
`IllegalArgumentException`, so it reached the catch-all. A stale enum value, an unparseable date or
a non-numeric path id each returned **500 internal_error**.

The availability consequence is the one this report has recorded twice before, for
`NoResourceFoundException` and `HttpMessageNotReadableException`: unauthenticated or careless
callers generate ERROR-level entries indefinitely, and the log stops distinguishing a real incident
from a client with a typo. There is also a mild denial-of-service shape to it — a cheap malformed
request producing an alerting-grade log line is an amplification a rate limiter alone does not
address.

**The refusal is written to leak nothing.** It names the parameter and, when the target is an enum,
its permitted constants — values already published in the specification. It does not echo the
submitted value, which is attacker-controlled text this API has no reason to reflect, and it does
not name the required type, which is internal. `TransactionsApiTest` asserts both omissions by
sending `?view=<script>` and requiring that neither the payload nor the type name appears in the
response.

### The three secret-scanner hits, examined

| Hit | Location | Assessment |
|---|---|---|
| `API Contract referenced: yes/no/partial` | `.ai/artifacts/security-report.md:135` | False positive. A checklist template line in this document, matched by the generic-api-key rule |
| `API Contract referenced: yes/no/partial` | `.claude/skills/lld-reviewer/SKILL.md:112` | False positive. The same template line in the skill this document's checklist came from |
| `password: 539e9687-36ea-40d8-b45f-fd95baeaef06` | `backend/fund-management-service/target/surefire-reports/TEST-…HostileBodyApiTest.xml:86` | Inert, and evidence for MEDIUM-2. Build output, not source, and the value is the ephemeral password Spring Boot generates at startup when no user store is configured — regenerated every boot and valid nowhere |

The third is worth more than its severity. MEDIUM-2 records that Boot logs a generated password in a
money service's boot sequence; this shows that password is also being captured into build artifacts,
where it outlives the process that made it. Harmless while it stays local and regenerates per boot.
It stops being harmless the moment a fixed development credential is configured to silence it and
CI publishes its test reports — a plausible next step that would turn a nuisance into a disclosure.
Configuring a real user store, which MEDIUM-2 already calls for, closes both.

### What this pass did not scan

`sqlglot` is not installed in this environment, so the migration syntax check that earlier passes ran
was not repeated. No migration was touched by this work — the SQL that changed is one `catch` block
around an existing statement — so the check has nothing new to examine, but the omission is recorded
rather than left implied by its absence from the table above.

740 tests pass. The full Postman collection runs 8 requests and 9 assertions with no failures against
a live instance.

**Final status: PASS**


## Re-scan after the Spring Boot 4.1.0 / Java 25 platform upgrade, 24 August 2026

The upgrade replaced the entire resolved tree, so every figure in this report above it describes a
dependency set that no longer exists. Spring Boot 3.5.15 became 4.1.0, which carries Spring
Framework 7.0.8, Spring Security 7.1.0, Tomcat 11.0.22, Micrometer 1.17.0, Flyway 12.4.0,
Testcontainers 2.0.5 and JUnit 6.0.3. The compiler target moved from Java 21 to 25.

### The nine pins were retired, and one of them would have been destructive

Every pin in the 3.5-era block raised a 3.5-era library to a version that cleared a named advisory.
Held against the 4.1.0 BOM, all nine pin **downward** — which is the failure mode those entries
exist to prevent. The BOM selects at or above each of them, so they were removed rather than
carried forward: Framework 7.0.8 over the 6.2.19 pin, Micrometer 1.17.0 over 1.15.12, Logback
1.5.34 unchanged, commons-lang3 3.20.0 unchanged, json-smart 2.6.0 over 2.5.2, xmlunit2 2.11.0 over
2.10.0, AssertJ 3.27.7 unchanged. The Tomcat pin is moot: the 10.1.x line that carried the three
CRITICAL and three HIGH advisories is not on this classpath at all.

`jackson-bom.version` is the one that had to be understood rather than compared. In Boot 4 that
property no longer names the Jackson 2 line — it selects **Jackson 3** (3.1.4), and the 2.x line
moved to `jackson-2-bom.version` (2.21.4). Carrying the old `2.18.9` value forward would have asked
Maven for a Jackson 3 release that does not exist. The old comment's substantive requirement — that
the Jackson 2 line sit at or above 2.21.4, because the 2.19 line carries advisories unfixed below
it — is satisfied by the BOM on its own.

Two pins survive on merit: `postgresql` 42.7.12 (BOM selects 42.7.11) and `log4j2` 2.25.5 (BOM
selects 2.25.4). `commons-compress` 1.26.0 stays, still unmanaged by the BOM.

### Dependency scan

Same method as every pass in this report: a CycloneDX SBOM generated locally by
`cyclonedx-maven-plugin` from the tree Maven actually resolves, test scope included, then
`osv-scanner` 2.4.0 against it. The SBOM is scanned rather than the POM for the reason recorded in
§1 — pointed at a POM, the scanner resolves the parent BOM over the network and reports a clean
zero when that fetch fails.

The first scan of the upgraded tree returned **four MODERATE advisories across two packages**, both
Jackson:

| Severity | Package | Advisory | Present | Fixed in |
|---|---|---|---|---|
| MODERATE (6.5) | tools.jackson.core:jackson-databind | GHSA-5gvw-p9qm-jgwh | 3.1.4 | 3.1.5 |
| MODERATE (6.5) | com.fasterxml.jackson.core:jackson-databind | GHSA-5gvw-p9qm-jgwh | 2.21.4 | 2.21.5 |
| MODERATE (6.5) | com.fasterxml.jackson.core:jackson-databind | GHSA-mhm7-754m-9p8w | 2.21.4 | 2.21.5 |
| MODERATE (5.3) | com.fasterxml.jackson.core:jackson-databind | GHSA-5jmj-h7xm-6q6v | 2.21.4 | 2.21.5 |

**Both Jackson lines are on this classpath**, which is a fact worth stating plainly because nothing
in `src/main` calls the older one. Boot 4 binds JSON with Jackson 3; springdoc's swagger-core still
reads and writes the OpenAPI model with Jackson 2. Both are in the resolved tree, both are scanned,
and both are now pinned: `jackson-bom.version` 3.1.5 and `jackson-2-bom.version` 2.21.5, held to the
fixed version rather than the newest patch so the change is auditable against the advisories it
answers.

**Result after the pins: 128 packages, 0 advisories, 740 of 740 tests passing.**

The package count rose from 66 to 128 mostly because Boot 4 split its single autoconfigure jar into
per-technology modules — more artifacts describing the same code, not more third-party surface.

The detection path is demonstrably live in this run rather than asserted: the identical command
against the pre-pin SBOM reports the four advisories above, and the scanner reports ingesting 128
packages for the run that produced the zero.

### Static analysis and secrets

| Class | Tool | Target | Coverage | Result |
|---|---|---|---|---|
| Static analysis | `semgrep` 1.172.0 — `p/java`, `p/secrets`, `p/owasp-top-ten`, `p/sql-injection` | `src/main` | 154 files | 0 findings |
| Static analysis | `semgrep` 1.172.0 — `p/java`, `p/secrets` | test sources | 66 files | 0 findings |
| Secrets | `gitleaks` 8.30.1, `--no-git` | whole repository, 4.15 MB | 6 hits | 0 in source |
| Schema constraints, executed | PostgreSQL 16 in Docker via Testcontainers | all 8 migrations | `SchemaConstraintTest` | every constraint fires |
| Specification | `redocly` 2.47.0 lint | `docs/api/openapi.json` | 7 operations | valid, 4 pre-existing warnings |

Semgrep skips a directory named `test` by default, so the test sources were copied outside that path
before scanning. A scan reporting zero findings over zero files is the same failure the SBOM
discipline in §1 exists to prevent, and it happened once here before being caught.

All six gitleaks hits are the same false positive class already recorded above: UUIDs matched by
`generic-api-key`, three of them inside this report's own tables, one in a skill document, and one
in `target/surefire-reports/` — build output carrying Boot's ephemeral startup password. None is in
source. MEDIUM-2's finding about that password reaching build artifacts is unchanged.

### A silent failure the upgrade introduced, found before it shipped

Boot 4 moved `FlywayAutoConfiguration` out of the single `spring-boot-autoconfigure` jar into a
`spring-boot-flyway` module that only `spring-boot-starter-flyway` brings in. This service declared
`flyway-core` directly and had never needed the starter.

With the libraries present and that module absent, the service **started cleanly, logged nothing
about migrations, created no tables**, and answered 500 on the first query against a table that was
never created. `spring.flyway.enabled: true` in `application.yml` had nothing left to read it.

Nothing failed loudly and no test caught it: the container-backed tests apply schema from
`src/test/resources/db` rather than through the application's own Flyway run. It surfaced only from
booting the packaged jar against a real PostgreSQL, which is now the argument for doing that on
every platform change rather than trusting a green suite. The fix is a dependency, not a setting.

### What this pass did not re-run

`sqlglot` remains uninstalled here, so the standalone migration syntax check was not repeated. No
migration changed in this work, and all eight applied in order against PostgreSQL 16 during the boot
verification, which exercises the same SQL through the database rather than through a parser.

The Postman collection was not re-run: `newman` is not installed in this environment. The generated
specification is byte-identical to the committed `docs/api/openapi.json`, so the contract the
collection asserts against did not move. `swagger-verification.md` records the live endpoint checks
that were run instead.

**Final status: PASS**
