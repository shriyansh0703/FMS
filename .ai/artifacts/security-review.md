# Security Review — Fund Management System (second pass)

Stage 11, re-review after the first pass returned CHANGES_REQUESTED. Static code-and-contract review
only. The first pass's findings are the baseline judged against here.

## Scope of Review

**Declared scope:** fullstack. **Reviewed:** backend only — the frontend does not exist, so every
client-side concern remains **not applicable rather than passed**, unchanged from the first pass.

**Newly reviewed since the first pass:** `api/ApiSecurityConfiguration`, `api/PerAccountRateLimit`,
`api/RateLimitInterceptor`, `api/WebMvcConfiguration`,
`integration/juspay/VerifiedGatewayCallback`, and the changed signature of
`PayinOrchestrator.onGatewayConfirmation`. Everything else is reviewed in its final post-QA state.

**Inputs read:** the previous version of this report, `review.md` (Stage 9, iteration 12),
`test-report.md` and `browser-report.md` (Stage 10, re-run and approved), `security-report.md`, the
generated OpenAPI specification, and `lld-backend.md`.

## Findings

### No CRITICAL findings

Stated explicitly. No authentication bypass, no broken object-level authorization, no injection, and
no path by which one trader's data or money reaches another was identified.

### No HIGH findings

Unchanged from the first re-review: HIGH-1 remains closed.

**HIGH-1 from the first pass is closed, and closed in substance rather than appearance.** The
service now performs its own access control: `ApiSecurityConfiguration:47-49` denies by default with
`anyRequest().authenticated()`, so the six unbuilt endpoints arrive protected rather than protected
once someone remembers to list them. `ApiSecurityTest` drives every endpoint unauthenticated and
asserts 401 with no body — including a malformed write body, which would have returned 400 if the
request had reached parsing, proving an unauthenticated caller cannot probe validation behaviour.
The 500-instead-of-401 problem (first pass MEDIUM-1) resolved with it.

Rate limiting exists where there was none, per account rather than globally, with a zero timeout on
every budget so a burst is refused rather than converted into held request threads.

### MEDIUM-1 — CLOSED in this pass: the CSRF reasoning is corrected and the real protection is pinned

**Location:** `api/ApiSecurityConfiguration.java:56-77`, `api/PayoutController.java:50-60`,
`api/PayoutCsrfSurfaceTest.java`.

**What was wrong.** The comment justified disabling CSRF because "this API is stateless and
token-authenticated, so there is no ambient credential for a cross-site request to ride on." That
describes a bearer-token API; the next line enabled HTTP Basic, which *is* an ambient credential a
browser reattaches to third-party-initiated requests. The stated reason was not true of the system as
configured, while the actual protection — that only JSON reaches a write handler — was undocumented.

**What changed.** The comment now states the real property, names HTTP Basic as an ambient credential
explicitly, and names the two changes that would make CSRF live again: a permissive CORS
configuration, or a form-encoded content type on any write endpoint. Every write mapping declares
`consumes = application/json`. `PayoutCsrfSurfaceTest` (5) asserts that all three cross-origin form
content types are refused with 415, that JSON is negotiated, and that no preflight from an arbitrary
origin is granted.

**Verified honestly, including where the verification failed.** Removing the `consumes` declaration
was expected to fail the new tests. It did not — all five still passed, because the 415 comes from
message-converter negotiation rather than from the annotation. The claim that the test guards the
declaration was therefore false and has been corrected in all three places it appeared. The
declaration remains for explicitness and to hold the refusal if a form-binding converter is ever
added; the tests guard the behaviour, which is the thing an attacker would exploit. Recording this
because a control believed to be tested and not actually tested is worse than one known to be
untested.

**Residual.** The protection still depends on no CORS configuration existing. That is now stated at
the point someone adding CORS will read it, and `noCorsPreflightIsGranted` fails the moment a
permissive one lands.

### MEDIUM-2 — HTTP Basic is shipped as the authentication scheme on a money API

**Location:** `api/ApiSecurityConfiguration.java:64`.

**Behaviour.** `httpBasic(Customizer.withDefaults())` is the only configured mechanism. It was, as
the author states, the smallest thing that made the filter chain real — and making the chain real
was the right call. But basic authentication transmits credentials on every request, has no expiry,
no revocation, no rotation, and no scope; browsers cache it for the session with no application
control over that lifetime; and it is the mechanism that makes MEDIUM-1's ambient-credential problem
possible at all.

There is also no `UserDetailsService` in `src/main`, so with the starter on the classpath and no
user store configured, Spring Boot generates a random password at startup and logs it. That is a
development affordance appearing in a money service's boot sequence.

**Why MEDIUM rather than HIGH.** It fails closed, it is not an exploit by itself, and the real
deployment is expected to place a gateway in front that performs token validation. The risk is
institutional rather than technical: a placeholder that works is a placeholder that ships, and
nothing in the code or configuration marks this one as temporary.

**Partially addressed; the finding stays open.** The scheme is now marked PROVISIONAL at the
configuration line, naming what Basic lacks (expiry, revocation, rotation, scope), why it is
nonetheless present (a chain that refuses beats a correct scheme that does not exist), and what
replaces it. That is the same discipline `DebitInterestRate.provisional` applies to a stand-in rate,
and it stops the placeholder becoming permanent by default.

**What is not done, and why it was not done here.** The replacement is `oauth2ResourceServer(jwt)`
against the gateway's issuer, or a pre-authentication filter trusting an upstream header only from a
verified source. Choosing between them requires knowing what the gateway actually issues — an issuer
URI and key set, or a header name and the means of establishing that upstream is genuine. **Nothing
in this repository records that**, and inventing it would produce a filter chain that compiles,
passes its own tests, and authenticates against something that does not exist. The finding therefore
stays open pending that input rather than being closed with a guess.

### LOW-1 — CLOSED: the rate-limit constants now name themselves per-instance

**Location:** `api/PerAccountRateLimit.java:33-55`.

**Assessment.** The Javadoc is honest that this is per-instance and that behind N replicas the
effective limit is N times the numbers. The finding is narrow: the budgets are quoted as "120/min",
"6/min" and "10/min" in the report and the class, and a reader who takes those as the system limit
will be wrong by the replica count. A floor is still worth having — it is the difference between an
unbounded endpoint and a bounded one, and it exists in the artifact rather than in the environment —
but it should not be mistaken for the ceiling.

**Closed.** The constants are `READS_PER_INSTANCE`, `EXPORTS_PER_INSTANCE` and
`MOVEMENTS_PER_INSTANCE`, and the Javadoc states that behind N replicas a caller gets N times the
figure. The distributed limit belongs to the gateway, which sees all traffic; this remains a floor
and now says so at every point it is read.

### LOW-2 — `VerifiedGatewayCallback` makes an omission visible; it does not prevent one

**Location:** `integration/juspay/VerifiedGatewayCallback.java`,
`movement/payin/PayinOrchestrator.java:160-166`.

**Assessment, answering the author's own question honestly.** A caller can write
`signatureVerified("anything")` without verifying anything, so this is not a control and should not
be counted as one. What it does buy is real but modest: the parameter cannot be defaulted or
forgotten, so the callback endpoint cannot be written without a decision being made in code; the
escape hatch is named `notFromAGatewayCallback` and is greppable, so its appearance in a callback
handler is conspicuous in review; and the constructor refuses a blank scheme, so "verified" always
carries a claim that can be audited against what the code actually does.

That is a speed bump and an audit trail, not enforcement. It is a genuine improvement over the prose
it replaces — first-pass MEDIUM-2 was that the constraint lived in a document nobody opens — but the
underlying property is unchanged: `onGatewayConfirmation` still finds an attempt by an enumerable
`FMS-PAYIN-<id>` reference and authenticates nothing itself.

**Remediation.** When the callback endpoint is built, the signature check must be a real
verification against the gateway's key, and this type should carry the verified payload digest
rather than a free-text scheme name so the receipt cannot be produced without the verification having
run.

### LOW-3 — CLOSED: the reason is recorded where the 401 will be hit

**Location:** `api/ApiSecurityConfiguration.java:47-49` (no permit for `/v3/api-docs` or
`/swagger-ui/**`).

**Assessment.** The decision is correct: an anonymous specification enumerates every endpoint,
parameter and error code for anyone who asks, which is free reconnaissance. But it breaks a workflow
developers expect to work — opening Swagger UI in a browser — and the fastest fix for a frustrated
developer is a `permitAll()` that nobody reviews.

**Closed.** The matcher now carries the reasoning, including a direct note addressed to whoever
arrives after a 401 from Swagger UI: it is the decision, not a bug, and adding `permitAll()` gives
the reconnaissance away permanently to unblock one developer temporarily.

### Confirmed safe — the rate-limit path classification

The author asked whether `getRequestURI()` plus a `.csv` suffix is safe against path manipulation.
**It is, and by ordering rather than by luck.** The budgets are `EXPORTS` 6 < `MOVEMENTS` 10 <
`READS` 120, so `EXPORT` is the *tightest*. Every manipulation that changes the classification —
a matrix parameter, a trailing `.csv`, an encoded suffix — can only move a request into `EXPORT`,
which is stricter than what it would otherwise get. Moving into the permissive `READS` budget
requires the method to be GET (`RateLimitInterceptor.java:64-67`), and no GET endpoint moves money.
The failure mode points toward refusal, which is the correct direction for a classifier on a money
path. Worth stating explicitly so it is preserved: if the budgets are ever reordered so `EXPORTS`
becomes the most permissive, this property is lost.

## On the dependency finding the remediation surfaced

Adding `spring-boot-starter-security` introduced two advisories in `spring-security-web` 6.5.10
(GHSA-293q-567p-wmwq, CVSS 6.8; GHSA-x2r2-rvhq-2mqv, CVSS 6.1) and exposed one in
`spring-boot-autoconfigure` 3.5.14 (GHSA-ggg2-9786-hwc8, CVSS 5.3). Spring Boot moved to 3.5.15 and
the re-scan reports **67 packages scanned, no issues found**.

Quoting the package count is not padding: this report already records that osv-scanner pointed at
`pom.xml` resolved nothing, reported zero advisories and exited zero. A clean result is only
meaningful with the resolved count beside it, and that discipline held here — the scan that found
the three advisories reported 67 packages, and so did the one that found none.

Worth noting for its own sake: **fixing a security finding introduced two vulnerabilities.** That is
the ordinary case rather than an unlucky one, and it is the argument for re-scanning after every
security change rather than only after dependency changes.

## Checklist Results

### Engineering practices (`security-review-practices.md`)

| Item | Result | Justification |
|---|---|---|
| Rust `unsafe` / concurrency / FFI / cancellation safety | **N/A** | No Rust in scope; Java 21 |
| Boundary validation | **Pass** | `JsonConfiguration` refuses numeric coercion; `HostileBodyApiTest` (18); address validation refuses without normalising |
| Server-side authorization | **Pass** | Deny-by-default filter chain; account derived from the authenticated subject, never from the request |
| Secrets in logs or errors | **Pass with a note** | No secrets in source; error envelopes leak nothing. The note is MEDIUM-2: with no user store, Boot logs a generated password at startup |
| Dependency awareness | **Pass** | Three advisories found and fixed by moving to 3.5.15; re-scan clean with the package count quoted |
| API / Swagger surface | **Pass** | Spec generated from controllers, validated by `OpenApiSpecTest`, and no longer anonymously readable |
| Differential review of changed code | **Pass** | The new security components reviewed in final state; the CSRF question tested rather than reasoned about |
| Footgun awareness | **Pass** | Money is integer paise; the rate classifier fails toward refusal |

### OWASP API Security Top 10 (`api-security-owasp-top10.md`)

| # | Risk | Result | Justification |
|---|---|---|---|
| API1 | Broken object-level authorization | **Pass** | No endpoint accepts an account identifier; every read scoped by the authenticated subject |
| API2 | Broken authentication | **Pass with conditions** | A real filter chain now exists and is tested. MEDIUM-2 concerns the scheme's suitability, not its absence |
| API3 | Broken object property-level authorization | **Pass** | Explicit DTOs; `requireMasked` refuses over-disclosure; no ledger balance in a payin confirmation |
| API4 | Unrestricted resource consumption | **Pass with conditions** | Per-account budgets on every path, plus a 92-day window cap. LOW-1 concerns the per-instance ceiling |
| API5 | Broken function-level authorization | **Pass (vacuously)** | No privileged or administrative operations exposed |
| API6 | Unrestricted access to sensitive business flows | **Pass** | Withdrawal is metered at 10/min per account, and Rule W4's index permits one open request |
| API7 | Server-side request forgery | **Pass** | No caller-supplied URL is fetched |
| API8 | Security misconfiguration | **Pass with conditions** | MEDIUM-1: CSRF disabled on reasoning that does not match the scheme, though the protection holds |
| API9 | Improper inventory management | **Pass** | Spec generated and now authenticated; unbuilt endpoints named and protected by default |
| API10 | Unsafe consumption of third-party APIs | **Pass** | Vendor errors translated; unmapped gateway statuses become UNKNOWN, never assumed successful |

## Endpoint Authorization Matrix

| Endpoint | Authentication | Object-level authz | Function-level authz | Input bounds |
|---|---|---|---|---|
| `GET /api/v1/funds/transactions` | Filter chain, 401 (`ApiSecurityConfiguration.java:47`); verified by `ApiSecurityTest` | Account from subject (`TransactionsController.java:90`) | N/A — trader-scoped | 92-day cap; READ budget 120/min |
| `GET /api/v1/funds/transactions/{reference}` | As above | `detail(account, period, reference)` — another account's reference returns 404 | N/A | As above |
| `GET /api/v1/funds/statement.csv` | As above | Account from subject | N/A | 92-day cap; EXPORT budget 6/min |
| `POST /api/v1/funds/payout` | As above | Account from subject; destination re-read from Profile | N/A | Bean Validation; JSON only (415 otherwise); MOVEMENT budget 10/min |
| `GET /api/v1/funds/payout` | As above | Account from subject | N/A | READ budget |
| `DELETE /api/v1/funds/payout/{id}` | As above | `findFor(account, requestId)`; not-found and not-yours are one answer | N/A | MOVEMENT budget |
| `GET /api/v1/funds/payin/limits` | As above | Account from subject | N/A | READ budget |
| `GET /v3/api-docs`, `/swagger-ui/**` | Authenticated (LOW-3) | N/A | N/A | N/A |
| `GET /actuator/health/**` | Permitted deliberately | N/A | N/A | N/A |
| *(unbuilt)* Juspay callback | Not built | Reference alone, by design; `VerifiedGatewayCallback` now forces the obligation into code (LOW-2) | N/A | Rate-limited by default as MOVEMENT when built |

## Assessment

Both first-pass findings are genuinely closed. HIGH-1 is closed in substance: the control exists in
this artifact, denies by default, and is proven by tests that would fail if it were removed.
MEDIUM-2 is closed as stated — the constraint has moved from prose into a signature — with LOW-2
recording honestly that what it buys is visibility rather than enforcement.

MEDIUM-1 was raised and closed within this stage: the CSRF reasoning is corrected, the real
protection is stated where someone changing it will read it, and the behaviour is under test. The
attempt to verify that fix also failed usefully — the test proved insensitive to the `consumes`
declaration it was thought to guard, and saying so is worth more than a tidier report.

What remains is MEDIUM-2: the authentication scheme works and is the wrong long-term choice. It is
not exploitable; it is the kind of placeholder that ships because it functions.

## Verdict

**Verdict:** APPROVED_WITH_CONDITIONS

Conditions, each to be carried into the work that follows rather than closed here:

1. **MEDIUM-2** — replace HTTP Basic with token validation before this service is exposed beyond a
   trusted network. The scheme is marked provisional in the meantime. **Blocked on an input this
   repository does not hold:** what the gateway issues. Supply the issuer and key set, or the header
   and how upstream is verified, and the replacement is a small change.
2. **LOW-2** — when the callback endpoint is built, the receipt must carry a verified payload digest
   rather than a free-text scheme name.

LOW-1 and LOW-3 were closed after the verdict was written; their sections above record what changed.

The condition that carried a deadline — correcting the CSRF reasoning before CORS lands — is closed.
What survives it is narrower: `noCorsPreflightIsGranted` will fail when a CORS configuration is
added, and whoever adds it must revisit the CSRF decision at that point rather than silence the test.

## Approval Record

- **Approved by:** *PENDING — name not yet supplied.* The approver stated at the gate that the name
  would follow. It is deliberately left blank rather than filled with the repository's commit author
  or the session account, because neither was confirmed as the person accountable, and a name nobody
  chose is worse than an visible gap.
- **Date:** 2026-08-22
- **Verdict at approval:** APPROVED_WITH_CONDITIONS
- **Findings accepted:** *(LOW-1 and LOW-3 have since been closed; the remaining acceptances are)*
  - MEDIUM-2 — HTTP Basic is shipped as the authentication scheme on a money API
  - LOW-2 — `VerifiedGatewayCallback` makes an omission visible; it does not prevent one
- **Basis:** Accepted via the approval gate. MEDIUM-1 was raised and closed within the stage; the
  remaining conditions carry into subsequent work rather than blocking this increment.

> **This record is incomplete until the approver's name is entered above.** Until then the pipeline
> shows an approval that no individual has attached themselves to, which is precisely the state the
> named-approval requirement exists to prevent. Anyone auditing this increment should treat the
> acceptance as provisional.
