# Code & Architecture Review — Persistence Layer and QA Remediation

Stage 9, iteration 12. Scope is the two bodies of work since iteration 11: the JDBC persistence
layer with its Testcontainers harness, and the QA remediation that followed Stage 10's NO-GO.
Iterations 2 through 11 were not re-reviewed except where this pass changed them.

## Verdict

**Verdict:** APPROVED_WITH_CONDITIONS

The work is sound and the evidence behind it is better than the code it covers. The two positive
controls are what make this reviewable rather than assertable — particularly the Rule W4 one, where
narrowing the index predicate fails the second test while the first still passes, which is a
demonstration that one test per constraint would not have been enough.

**F-35 is confirmed closed.** The durability the F-30 fix depends on is now proven against a real
database, and proven to be load-bearing: running `start` inside a transaction leaves zero rows. The
constraint is recorded at the line where someone would break it.

Three should-fix findings and two nits. None blocks, and one of them — F-37 — is a contract the
code silently fails to honour and should be settled before the end-of-day run is built on it.

## 1. The seven questions, answered against running code

### 🟡 F-37 — `openRequestsForRun` ignores its `runDate`, and the contract says it should not

The author flagged uncertainty here, and the uncertainty was justified. The parameter is accepted
and never used: the query selects every open request ordered by `requested_at`, with no date bound.

The interface's own Javadoc explains why that matters:

> Ordered by request time so a re-run processes in the same order as the run it repeats. An
> unordered scan would make a partially completed run non-repeatable, and §6.3's recovery depends
> on repeating it.

Ordering is only half of repeatability. A re-run of a given date's run also has to see the **same
set**, and with no bound on `requested_at` it does not — any request created since the original run
is picked up by the re-run. That is precisely the non-repeatability the comment says §6.3's
recovery depends on avoiding, arrived at by a different route than the one it guards.

Nothing in `src/main` calls the method yet, so there is no live defect. But the implementation
contradicts its own interface, and the next person to build the end-of-day run will read the
Javadoc, not the SQL.

The test did not catch it because `theRunSeesEveryOpenStateOldestFirst` asserts `.contains(...)`
rather than exact contents, so it passes whatever the filter does — worth fixing alongside.

Recommendation: bound `requested_at` by the run date, or change the signature to drop a parameter
the design does not need. Either is fine; silently ignoring it is not, because it reads as
implemented.

### 🟡 F-38 — after an INSERT the argument is orphaned, and re-saving it duplicates a money row

Both `save` implementations return a **different instance** than the one passed in. The argument
keeps `id == 0` forever, so a caller that keeps using it inserts again. Demonstrated:

```
  original.id() after save = 0   returned.id() = 1
  rows after saving the SAME logical attempt twice = 2   <-- 2 means a duplicate payin row
```

This is conventional repository semantics and `PayinOrchestrator.start` uses it correctly — it
reassigns on both saves. The concern is that this is a money table, the failure is a duplicate
deposit row, and nothing in the type system or the interface says so. Rule A6's unique index does
not help: the duplicates are created before a gateway reference exists, so both rows are legal.

The author's specific question was whether the two INSERT paths are both right. They are, but for
different reasons, and the asymmetry is worth resolving:

- **Payout re-reads the row** it just inserted. Correct unconditionally: whatever the database
  filled in — defaults, triggers — comes back.
- **Payin builds the returned instance from the in-memory values** plus the new id. Correct only
  because every defaulted column is bound explicitly. `started_at` has `DEFAULT now()` and `version`
  has `DEFAULT 0`, and both are bound, so nothing diverges today. Add a defaulted column in a future
  migration and the payin path silently returns an instance that does not match the stored row,
  while payout keeps working.

`loadedVersion` itself is correct on both paths. Payin's rehydrate sets it from the value it wrote;
payout's re-read sets it from the database. The save-mutate-save sequence in `start` was traced and
is right: INSERT at version 0, `willUseGatewayReference` takes the in-memory version to 1 while the
anchor stays 0, and the UPDATE compares against 0 and writes 1.

Recommendation: make payin re-read like payout, so correctness stops depending on remembering to
bind every defaulted column. Then state on the interface that the returned instance is the live one
and the argument must be discarded.

### 🟡 F-39 — the repositories are correct, tested, and unreachable

The author asked whether "constructed explicitly by whatever assembles the module" is a real answer
given nothing assembles the module. It is not yet. Neither repository is referenced anywhere in
`src/main`; only tests construct them.

Removing `@Repository` was right — the API test contexts exclude `DataSourceAutoConfiguration` by
design, and the annotation broke 47 tests by forcing Spring to build a repository into contexts with
no `JdbcClient`. It could never have worked regardless, since the constructor also takes a `ZoneId`,
which is not a bean. Recording that reasoning on both classes was the right call.

But the honest description of where this leaves things: the persistence layer joins
`PayinMovementSource` and `PayinOrchestrator` as correct, tested code that no deployed path reaches.
This is F-31's problem grown larger rather than moved. It does not diminish the work — the
constraint verification and the F-35 proof are valuable precisely because they run — but no part of
it is load-bearing until something wires the module together.

Recommendation: one configuration class assembling the payin and payout modules, guarded on a
`DataSource` being present so the API tests keep their DB-free contexts. That closes F-31 and F-39
together and is the natural next task.

### 🟢 F-40 — `rehydrate` can build states the machine cannot reach

By design, and unavoidable for any rehydration. It can produce a `CONFIRMED` payin with no gateway
reference or a `PAID` payout with no settlement, because it assigns fields directly.

Checked whether anything downstream assumes otherwise: no caller in `src/main` calls `orElseThrow()`
or `get()` on `gatewayPaymentRef()`. Every use is `orElse(null)`. So an impossible object would not
crash anything — it would also not be noticed.

Recommendation, optional: a cheap invariant in `rehydrate` — a terminal state carries an outcome, a
non-`INITIATED` payin carries a reference — would turn silent data corruption into a loud failure at
load. The counter-argument is that it would refuse legitimate rows if the rules ever change, so this
is a judgement call rather than a defect.

### 🟢 F-41 — `PostgresTestSupport` races if parallel execution is ever enabled

`startAndMigrate` guards on `if (dataSource != null) return;`, which is safe today because surefire
is configured with no `forkCount`, `parallel` or `threadCount` — execution is sequential in one JVM.
Turn on parallel execution and two threads can both see null and both start a container and run
Flyway.

The shared static `AtomicLong` counters are fine either way, since `getAndIncrement` is atomic and
each test derives its own account identifiers from it. Never stopping the container is also correct:
Ryuk removes it on JVM exit, and per-class containers would add roughly a second each for no
isolation benefit.

Recommendation: synchronise the method, or note the assumption. A one-word change.

### 🎉 Deleting `Money.sum` was right, and `MoneyTest` is the more important half

The method had no caller in `src/main` or `src/test`, and carried a loop, a named-position null
check and a `Math.addExact` overflow guard — real logic, entirely unexercised, on the type every
figure in the product is expressed in. Speculative API is bad enough; speculative API with an
untested overflow path on a money primitive is worse, because the failure mode is a valid-looking
wrong number. Nothing will miss it, and `plus` covers the same need for a caller who appears later.

The larger finding is what its absence revealed: `Money` had no test class at all, which is how it
came to have the weakest branch coverage of anything exercised. It is now at 100% instruction and
100% branch, including the three overflow paths and the `Long.MIN_VALUE` negation case, where a
plain unary minus would return the value unchanged and a debit balance would read as a credit.

### 🎉 `ApiExceptionHandlerTest` is not a tautology

The author asked. Calling the handlers directly would be a tautology if it asserted only the status
code, which is visible in the method body. What it actually asserts is a **negative**: that the
invariant code and message do not reach the caller, and that the vendor name does not appear in an
upstream response. Those are security properties — an invariant message names internal structures,
and enumerating this system's failure modes one request at a time is a real capability to withhold —
and no amount of reading the method proves them, because the assertion is about what is absent.

Calling directly also avoids adding to F-23's static mutable state, which five test classes already
share. That was the right trade.

## 2. On the SQL and the schema mapping

Checked against V21 and V22 and found correct. Column lists match. `moneyOrNull` uses `wasNull()`
after `getLong`, which is the only correct way to distinguish a stored zero from a NULL — a real
trap, since `getLong` returns 0 for both and a NULL `amount_sent_paise` read as zero would report a
payout that sent nothing. Timestamps round-trip through `Timestamp.from(instant)` and
`getTimestamp().toInstant()`, which is exact regardless of JVM zone because both sides carry epoch
milliseconds. Dates round-trip through `Date.valueOf`/`toLocalDate`, verified in a JVM running at
+05:30 rather than UTC, so the conversion is not accidentally passing on a zero offset.

`inPeriod`'s window is genuinely inclusive at both ends: `>= from.atStartOfDay()` and
`< to.plusDays(1).atStartOfDay()`, both in the injected zone. A half-open upper bound is how the
last day's deposits vanish from a statement, and this is not that.

`openFor` returning `Optional` is right — the partial unique index permits at most one open request
per account, so the type matches the constraint.

## 3. Conditions carried, unaddressed

Restated as agreed, not re-derived. **F-35 is closed** and no longer carried.

**F-31** — `PayinMovementSource` unwired, and the `CONFIRMED`-before-ledger-posting window. See F-39,
which is the same problem grown.
**F-32** — nothing resolves `AWAITING_BANK`; `statusOf` has no caller.
**F-33** — §6's address obligations are unimplemented.
**F-34** — `parameter_contract` is classified `PLATFORM_CONFIGURATION`.
**F-36** — the widened `INITIATED` transition set gave up a guard.
**F-22** — the OpenAPI money check scans only `components/schemas`.
**F-23** — static mutable test state, still shared by five classes. Noted that this pass
deliberately did not add to it.

## 4. Assessment

The persistence layer is the most consequential work in this pipeline so far, because it converts
three business rules from claims into executed checks. Rule W4's one-open-request index, Rule A6's
gateway-reference uniqueness and Rule C8's bank-reference check each now have a test asserting they
**refuse** the row they exist to refuse, and the W4 test was confirmed to fail when the predicate is
narrowed. Constraints of this kind fail silently when wrong; reading them is not sufficient, which
the script they replace said in its own header and then relied on a human to act on.

The three should-fix items share a theme worth naming: each is a place where the code is correct but
says something slightly untrue about itself. `openRequestsForRun` accepts a parameter that reads as
implemented. `save` returns an instance whose relationship to the argument is undocumented. The
repositories describe an assembly that does not exist. None is a bug today and each becomes one when
someone builds on the description rather than the behaviour.

## 5. What should happen next

1. **F-39 and F-31 together** — one configuration assembling the payin and payout modules, guarded on
   a `DataSource`. Turns all of this from tested to load-bearing.
2. **F-37** — bound `openRequestsForRun` by the run date or drop the parameter, and tighten the test
   from `contains` to exact contents.
3. **F-38** — make the payin INSERT re-read, and document the returned-instance contract.
4. F-40, F-41 optional. F-32, F-33, F-34, F-36, F-22, F-23 remain on their existing terms.

330 tests pass on a clean build. Coverage is 90.3% instruction and 74.5% branch, up from 87.0 and
72.2 at iteration 11. Semgrep reports 0 findings across both new repositories.
