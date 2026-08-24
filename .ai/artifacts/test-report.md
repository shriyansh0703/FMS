# QA Test Report — Fund Management Service

Stage 10, backend, fourth pass. The browser validation is a separate document,
[`browser-report.md`](browser-report.md), because it reports a different kind of failure to a
different audience and merging the two would bury its conclusion inside a passing test summary.

**QA verdict: NO-GO**, on three reasons, none of which is a test that fails. Every automated test
passes. What is missing is the code three of them would have to run against.

This pass was driven by a request to enumerate every scenario the system must handle, write them so
a person can read them, and then execute them. That produced
[`docs/qa/test-cases.md`](../../docs/qa/test-cases.md) — **613 human-readable cases** derived from
the PRD, HLD and LLD rather than from reading the implementation — and 141 new automated tests
closing the gaps that enumeration exposed.

## 1. Results summary

| Property | Value |
|---|---|
| Build system | Maven (`pom.xml`), Java 21, Spring Boot 3.5.15 |
| Test framework | JUnit 5 via `maven-surefire-plugin` |
| Database under test | PostgreSQL 16-alpine via Testcontainers, migrated by Flyway |
| Command | `mvn clean test` |
| Result | **740 tests, 0 failures, 0 errors, 0 skipped** |
| Browser tests | none detected — no Playwright, Cypress, Selenium or `e2e/` anywhere |
| Historical CI signal | none — no results database in this repository |

Coverage is measured by invoking JaCoCo 0.8.12 from the command line, so no build file changed to
produce these figures.

**A note on the coverage run's log, because it looks worse than it is.** Maven on this machine
resolves JDK 26 while the module compiles to release 21, and JaCoCo 0.8.12 cannot instrument the
JDK 26 class files it meets at runtime — so the log carries 317 stack traces reading
`Unsupported class file major version 70`. Every one of them is a JDK-internal class
(`com.sun.net.httpserver.*`, `com.sun.security.*`). **No `com.thinq.fms` class is affected**, the
build exits zero, and the figures above are complete: 149 classes measured, the same count as the
previous pass. The underlying JDK mismatch is a real deployment question and is carried in §6; the
instrumentation noise is not evidence of it.

| Metric | Pass 1 | Pass 2 | Pass 3 | **This pass** |
|---|---|---|---|---|
| Tests | 260 | 290 | 577 | **740** |
| Instruction coverage | 87.0% | 88.7% | 90.5% | **94.5%** |
| Branch coverage | 72.2% | 73.4% | 74.7% | **85.4%** |

The branch figure moved most, and that is the informative one. The previous passes tested behaviour
through the services that use it; this pass tested the value types those services pass between
themselves, and those types are where the guards live. A guard is a branch, and a guard nothing
exercises is a rule the system only appears to enforce.

## 2. Acceptance criteria coverage — what the 613 cases actually cover

Every case in the catalogue names the requirement or business rule its expectation comes from, so a
disputed expectation is settled by reading the PRD rather than by arguing about a test.

| Section | Cases | Executed | Blocked | Manual |
|---|---:|---:|---:|---:|
| Balances & margin | 60 | 51 | 9 | 0 |
| Adding funds | 68 | 58 | 10 | 0 |
| Withdrawing funds | 84 | 68 | 16 | 0 |
| Transactions & statements | 68 | 60 | 8 | 0 |
| Account health | 34 | 24 | 10 | 0 |
| Communications | 117 | 114 | 2 | 1 |
| Configuration | 26 | 22 | 4 | 0 |
| API contract & edge layer | 30 | 30 | 0 | 0 |
| Security & disclosure | 35 | 32 | 1 | 2 |
| Persistence & constraints | 30 | 30 | 0 | 0 |
| Vendor integrations | 33 | 33 | 0 | 0 |
| Concurrency, resilience, performance | 28 | 18 | 4 | 6 |
| **Total** | **613** | **540** | **64** | **9** |

**Blocked means unexecutable, not untested.** A blocked case has no code to run against: there is no
frontend to render the funds view, no end-of-day run to settle a payout, and no margin source to
supply a figure. Writing a test for any of them would produce a test of a stub, which reports
coverage without reporting correctness.

**The distribution is the finding, not the total.** Sixty-four blocked cases across 613 sounds like a
tail. It is not a tail — it is three holes:

- **Withdrawal carries sixteen**, and every one describes the end-of-day run. That is the only path
  in this system where being wrong moves money irreversibly, and it is the path with no executable
  coverage of what actually decides the money. Rule W9's guarantee that a mandated return and a
  trader's own request never send the same money twice is designed, is asserted at the value level
  (the collision is detected, two rails refuse to start), and has never been executed end to end
  because there is no run to execute it in.
- **Account health carries ten**, and all ten are the states a trader is actually in when they
  arrive: empty, blocked, in debt, or about to be squared off. The arithmetic of a debt is complete
  and tested; nothing presents it.
- **Balances carries nine**, all of them presentation of the figure Rule B4 computes.

Communications carries two blocked cases out of 117, which is what a module looks like when it has
been built.

## 3. What this pass added, and why each addition was chosen

141 tests in four new classes under `com.thinq.fms.qa`, targeted by measured coverage rather than by
intuition. Each class name maps to a catalogue section, and each test's display name carries its
catalogue ID, so a case in the document and the test that executes it are findable from each other.

| Class | Tests | What it covers | Coverage before → after |
|---|---:|---|---|
| `DerivationContractTest` | 32 | The types Rule B4 produces and consumes | `MarginFigures` 0% → **100%**, `Derivation` 70.7% → **100%**, `DerivationResult` 59.4% → **100%** |
| `MovementContractTest` | 36 | Route caps, settlement outcomes, instructions, the payout rail assertion | `PayoutRailConfiguration` 39.4% → **100%**, `SettlementOutcome` 74.7% → **100%**, `RouteCap` 81.6% → **100%** |
| `LedgerViewContractTest` | 25 | Ledger entries, statement rows, pages and periods | `StatementRow` 59.3% → **100%**, `TransactionPage` 89.4% → **100%** |
| `PlatformContractTest` | 48 | Identity, masking, channels, configuration, vendor error vocabulary | `AccountRef` 70.6% → **100%**, `AuthenticatedAccount` 75% → **100%**, `TechExcelErrorCode` 80% → **100%** |

Three of these were not merely under-covered, they were load-bearing and unexercised:

**`MarginFigures` was at zero.** Nothing in the repository constructed it, so every guard it carries
— a shortfall cannot be negative, collateral cannot be negative, a surplus belongs in available
margin rather than in a negative shortfall — was a rule the system only appeared to enforce. It is
now at 100% on both instruction and branch.

**`PayoutRailConfiguration` was at 39%, with its refusal branch untouched.** That bean exists for one
purpose: to stop the service starting when two payout rails are configured, because two live rails
instruct independently and Rule W9's combine-before-instruct step then protects nothing. Three
systems in this estate can move money out. The guard against the most expensive misconfiguration
available had never been fired in a test. Both directions are now executed — two rails refuse to
start, and zero rails refuse too, because a service that accepts withdrawals it can never settle
tells the trader at end of day.

**`StatementRow` was at 33% branch.** Rule L8a names two words for the type column, Debit and Credit,
because the file is read against a bank statement. The guard that keeps an internal kind out of that
column was unexercised, which is precisely the defect the rule was written against.

## 4. Findings

### QA-01 — `AccountRef` cannot itself exclude a regulated identifier (LOW)

Found by a test written from the class's own documentation, which says this is "a UCC code and
nothing else … deliberately not a PAN". The validator is `^[A-Za-z0-9]{1,20}$`. A PAN is ten
alphanumeric characters, so it passes.

The type bounds charset and length; it does not distinguish a UCC from any other alphanumeric token
of similar shape. Taxonomy rule R4's protection against a regulated identifier reaching an event
property therefore rests on the platform gateway putting a UCC in the subject claim — which is a
property of the deployment, not of this constructor, and which nothing in this repository asserts.

Not raised higher than LOW because the value is taken from the authenticated principal rather than
from a request body, and `qa.PlatformContractTest#theAccountComesFromThePrincipal` and
`OpenApiSpecTest#noRequestAcceptsAnAccountIdentifier` both hold that line. The limitation is now
pinned by a test that documents it, so if the rule is ever made structural the test fails and says
why. **Owner: whoever confirms the gateway's subject claim (TC-SEC-035, currently MANUAL).**

The refusal message was checked separately and does not echo the value it rejected, which matters
because a rejected identifier may well be the regulated value R4 forbids carrying — echoing it into
an exception puts it in a log.

### QA-02 — `TechExcelLedgerGateway` is the largest remaining untested surface (MEDIUM)

42.7% instruction and **21.9% branch** across 281 instructions. It is the read-through that supplies
every entry in the transaction list and every running balance the trader sees, and roughly four out
of five of its branches have never executed. The query service above it is at 98%, which is what
makes this easy to miss: the tested layer sits on an untested one.

The branches that are not covered are the vendor's failure modes — a window walked in several
requests, a malformed row, an absent closing balance, a session expiring mid-walk. Those are exactly
the paths where a silent wrong answer produces a running balance that does not reconcile, which
Rule L9 treats as a correctness failure rather than a display problem.

Not closed in this pass because doing it properly needs the vendor's response fixtures rather than
invented ones, and inventing them would test this system against a TechExcel that does not exist.
**Recommended as the first work of the next pass.**

### QA-03 — the working tree was modified by another process during a test run (PROCESS)

The first coverage run of this session failed one test:
`OpenApiSpecTest.securitySchemeIsDeclared` expected `bearer` and got an empty string. That test does
not exist in the current source, which does exist as `securitySchemeMatchesWhatIsEnforced` expecting
`basic`.

File timestamps explain it. `OpenApiConfiguration.java` was modified at 11:23:59 and
`OpenApiSpecTest.java` at 11:24:29; the failure was recorded at 11:24:08, between the two. Something
outside this session — another agent session or an editor — was changing the security scheme from
bearer to basic while the suite was compiling, and the run caught the half-applied state.

**No defect. A real hazard.** Ten source files carry today's date, and a test run whose inputs change
underneath it produces a result that describes neither the before state nor the after state. The
final figures in §1 come from a clean run against a tree whose hash was recorded before and after,
but this is worth naming: a green suite is only evidence if the tree was still when it ran.

## 5. Standard of evidence

Each of the previous passes recorded a positive control behind every non-trivial claim, because a
passing test establishes nothing on its own. Those controls stand and are not re-derived here.

For this pass, one control was found rather than constructed. Case TC-SEC-019 was written expecting a
PAN-shaped value to be refused, and it failed — which is what an honest requirements-derived test is
for. The catalogue is generated from the PRD's stated intent, so a case can fail because the code
diverges from the intent rather than because the test is wrong. That is the whole argument for
deriving cases from requirements rather than from reading the implementation, and it paid out once
in 613 cases.

The remaining 140 new tests assert guards that were already correct and simply unexercised. That is
worth stating plainly rather than dressing up: this pass raised confidence in code that was already
right, and found one documented limitation. It did not find a defect in the money paths, because the
money paths that exist were already covered — and the money path that is not covered is not covered
because it does not exist.

## 6. Coverage that remains, and its shape

Sixteen classes remain below 90% instruction. Of those:

- **`TechExcelLedgerGateway`** is QA-02 above and is the one that matters.
- **`AbstractVendorGateway`** (73.2% / 50% branch) is the circuit breaker. Its open and half-open
  transitions are reachable only by driving a vendor to fail repeatedly, which the current stubs do
  not do. Worth closing with a fake that fails on a schedule; not closed here.
- **`PayoutRequest`** (84.6%) and **`InstructionKey`** (83.5%) are mostly package-private accessors
  added for the repository — the same scoping artefact the mutation-testing pass identified.
- **`FundsModuleConfiguration`** (79.1%) and **`LocalOnlyStubConfiguration`** (66.2%) are wiring,
  covered where it matters by `FundsModuleConfigurationTest` and `LocalOnlyStubConfigurationTest`.
- **`RateLimitInterceptor`** (74.7% / 80% branch) has its refusal path covered; what is missing is
  the dispatcher plumbing around it.
- **`MandatedReturnSchedule`** (86.6% / 70%) has one uncovered branch: the "no quarter end within a
  year" exception, which is unreachable by construction and would need a test that asserts nothing.
- Three exception classes sit at 46–73% because their message-formatting constructors have one
  unused overload each.

**`MarginFigures` is no longer on this list, and that changes the shape of the balances gap.** The
module remains unimplemented and blocked on TASK-11, but the value types it will produce are now
fully specified by executable tests. When Noren's transport is resolved, the calculator has a
contract to build against rather than a blank page.

## 7. Verdict and what must exist before sign-off

**NO-GO**, on three reasons.

1. **There is no frontend.** Absolute blocker for system sign-off; see `browser-report.md`. Thirty-one
   catalogue cases across four sections describe behaviour a person sees, and no backend result is
   partial credit toward any of them.
2. **The end-of-day payout run does not exist.** Sixteen catalogue cases describe it. It is the only
   path where being wrong moves money irreversibly, and it is the least covered thing in the system —
   not because it is hard to test, but because `PayoutOrchestrator` has `request` and `cancel` and
   nothing that settles.
3. **The margin source does not exist.** Blocked on TASK-11. Nine catalogue cases in balances and the
   whole of REQ-101, 103–108 depend on it.

The backend is in the strongest state it has been: 740 tests, 94.5% instruction and 85.4% branch
coverage, business-rule constraints executed against a real PostgreSQL on every build, and a
613-case catalogue that states what is covered, what is not, and — for every uncovered case — why it
cannot be covered yet rather than that nobody got to it.

Carried review conditions, not re-derived: F-32, F-33, F-34, F-36, F-37, F-38, F-22, F-23. New this
pass: QA-01 (LOW, documented and pinned), QA-02 (MEDIUM, recommended as next work), QA-03 (process
hazard, no defect).

## 8. Recommended next work, in order

1. **Fixture-drive `TechExcelLedgerGateway`** from the vendor's own response samples (QA-02). It is
   the largest untested surface and it sits under the most-read screen.
2. **Build the end-of-day run**, then execute catalogue cases TC-WDR-069 to TC-WDR-077 against it.
   The catalogue already states what each must do; the tests are a transcription exercise once the
   run exists.
3. **Drive `AbstractVendorGateway`'s breaker** with a fake that fails on a schedule, closing the 50%
   branch gap on the resilience path every vendor call depends on.
4. **Confirm the gateway's subject claim** (TC-SEC-035), which is what QA-01 rests on.
5. **Measure the four performance targets** (TC-NFR-023 to TC-NFR-027). They are `[PROPOSED]` in the
   PRD and have never been measured, so they are currently assertions rather than targets.
