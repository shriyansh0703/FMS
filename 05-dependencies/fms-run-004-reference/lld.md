# LLD Consistency Pass — Settlement And Funding Experience (Run 004)

| | |
|---|---|
| Stage | 5c — orchestrator cross-check. No skill runs here |
| Inputs | `lld-backend.md` (Stage 5a, APPROVED) and `lld-frontend.md` (Stage 5b) |
| Purpose | Verify the two documents describe the same API surface, the same types, and the same failure semantics |
| Not in scope | Regenerating or deepening either document. This pass compares; it does not design |

---

## 1. Method

Every endpoint, request shape, response shape, enum and failure behaviour named in either document was matched against its counterpart in the other. Three categories of outcome are recorded below: **agreements** where both documents say the same thing, **discrepancies** where they disagreed or one was silent, and **resolutions** for each discrepancy, applied in the owning sub-stage rather than here.

Two contract gaps were already known coming into this pass — `lld-frontend.md` raised them as R-1 and R-3 — and were closed in 5a and 5b before the cross-check ran. They are recorded in §4 because the pass is the record of how they were settled, not because they were found here.

---

## 2. API Contract Agreement

### 2.1 Endpoint inventory

| Endpoint | In 5a | In 5b | Verb | Agreement |
|---|---|---|---|---|
| `/funds/payment-memory` | §4.2 | §11 | GET | **Agree.** Required `direction` parameter; 200 with nulls for absence; 400 for an unknown direction |
| `/funds/summary` | §4.6 | §11 | GET | **Agree.** Additive `CycleView` fields; 200 in both available and unavailable cases |
| `/funds/settlement-cycle` | §4.1 | §11 | POST | **Agree.** Accepts only selectable cycles; 400 on the applied one, which the client never sends |
| `/funds/screen-open` | §4.3 | §11 | POST | **Agree.** `{ screen }`, 202, fire-and-forget |
| `/funds/features` | §4.4 | §11 | GET | **Agree** on shape. Failure semantics were a discrepancy — see D-3 |
| `/funds/deposits` | §4.1, §4.3 | §11 | POST | **Agree** after D-1, and re-verified after Stage 6: three optional telemetry fields, identically named on both sides |
| `/funds/withdrawals` | §4.1 | §11 | POST | **Agree.** Added on both sides after Stage 6, carrying `preselectionKept` for K6's withdrawal half |
| `/internal/funds/settlements/destination-faults` | §4.5 | — | GET | **Correctly absent** from the client. An operations endpoint on the internal prefix; the client has no business reading it |

### 2.2 Shared types

| Type | 5a definition | 5b definition | Agreement |
|---|---|---|---|
| `SettlementCycle` | Three-value enum, `MANDATORY_MONTHLY` not selectable | Three-value union, `SelectableCycle` derived by `Exclude` | **Agree**, and the client's derived type is stronger — widening the enum cannot silently widen what the selector offers |
| `CycleView` | `cycle`, `nextSettlementDue`, `chosenByTrader`, `appliedReason`, `endsWhen` | Identical five fields | **Agree.** Both state that `chosenByTrader` is read rather than the enum compared, so a second applied cycle would not need every comparison site found |
| `PaymentMemoryView` | `direction`, nullable `lastDepositRail`, nullable `lastDestination` | Identical, with `Rail` narrowed | **Agree.** Both state that the three reasons for absence are deliberately indistinguishable to the client |
| `MemoryDirection` | `DEPOSIT`, `PAYOUT` | Same two literals | **Agree** |
| `MoneyScreen` | `ADD_FUNDS`, `WITHDRAW` | Same two literals | **Agree** after D-2 |
| `ClientFeatureFlags` | Three booleans; the judgement switch deliberately excluded | Same three | **Agree.** Both state the judgement switch is not the client's business |

### 2.3 Behavioural agreements worth recording

These are places where both documents independently reached the same rule, which is the useful signal from a consistency pass — an agreement neither side had to be told about is a rule that will survive implementation.

| Rule | 5a | 5b |
|---|---|---|
| A memory failure returns absent rather than an error, because a convenience must not fail a payment screen | §6.5 catches and returns absent | §15 renders absent identically to no-history |
| Rail availability is **not** applied server-side | §4.2 states the client does it | §10.1 implements the intersection |
| Settlements cannot seed the withdrawal memory | §6.5 — structural, no settlement rows in `withdrawal_request` | §23 — trader who withdrew only via settlement sees nothing pre-selected |
| The re-choice screen is a rule, not a structural guarantee | §6.5 distinguishes the two | §7.5 says so explicitly and asserts the absence of the request |
| Zero is displayed; unknown is not | Implicit in the summary contract | §7.2 renders a confirmed zero rather than omitting the line |

---

## 3. Discrepancies Found By This Pass

Three, all closed in their owning sub-stage. None required a design change; all three were one side being silent where the other was specific.

### D-1 — `/funds/deposits` was changed in prose but absent from the endpoint table

- **Found:** `lld-backend.md` §4.3 adds an optional `screenElapsedMillis` to the existing deposit request, but §4.1's endpoint inventory did not list `/funds/deposits` as changed. `lld-frontend.md` §11 listed it.
- **Why it matters:** Stage 7 plans work from the endpoint inventory. A change described only in a subsection three screens further down is a change that gets planned as nothing and discovered in code review.
- **Resolved in 5a:** the endpoint table now carries the row, marked as changed, with the field named and the scope of the change stated.

### D-2 — the screen-open signal was specified for one screen and metered for two

- **Found:** `lld-backend.md` §7.4 makes `fms.money.screen.opened` the denominator for guardrails G1, G2 **and** G4, and its `MoneyScreen` enum carries `WITHDRAW`. `lld-frontend.md` mounted `useScreenOpenReport` on `AddFunds` only.
- **Why it matters:** G2 is the withdrawal completion rate. Without a withdrawal screen-open signal it is a numerator over nothing — the metric would exist, report a number, and be meaningless, which is worse than being absent because it would be read. This is the same class of defect the Stage 4 review raised against G4's original instrumentation, arriving on the sibling guardrail.
- **Resolved in 5b:** both surfaces mount the hook with their own `MoneyScreen` value. Only `AddFunds` sends a duration, because only G4 measures one — the two screens report the same event for different metrics.

### D-3 — the feature-switch fallback defeated the switches' purpose

- **Found:** `lld-frontend.md` §11 originally specified that a failed `/funds/features` read treats every switch as on, reasoning that an outage should not silently disable a shipped feature. `lld-backend.md` §4.4 states the switches exist so a guardrail regression can be reverted without a release.
- **Why it matters:** the two are directly opposed at the only moment that matters. Operations turns the funding position off because G1 is falling; the features endpoint then has a bad minute; every client fails open and turns the position back on. The switch would be least effective exactly when it was being used, and nobody would see it happen — the clients would report the feature enabled and operations would report it disabled.
- **Resolved in 5b:** the last successfully fetched value stands through a failure; only a client that has never fetched defaults to all-on. A first-load failure is the one case where defaulting on is right, because a trader who has never reached the service has no switched-off state to preserve.

---

## 4. Contract Gaps Closed Before This Pass

Both were raised by `lld-frontend.md` as risks rather than resolved unilaterally, which is the correct handling — a sub-stage that invents the other side's contract produces agreement on paper and a mismatch in code.

### R-1 — guardrail G4 had no wire representation

`hld.md` §13.2 said the screen-open moment rides the payment-memory read. Neither LLD could implement that, and the reason was found on the client side: `QueryProvider` sets `retry: 2`, so a memory GET that failed twice and succeeded on the third attempt would have recorded three opens. G4's denominator would inflate precisely when the service was degraded — the condition the guardrail exists to detect. A recording side effect on a cacheable GET is also wrong independently of the retry count.

**Settled as:** `POST /funds/screen-open` for the open, `screenElapsedMillis` on the deposit request for the duration. The HLD's design intent — report at open so abandonment is visible as opens without submissions — is preserved exactly; only the carrier changed, and `lld-backend.md` §4.3 states the deviation and its reason rather than making it silently.

### R-3 — the client switches had no delivery mechanism

**Settled as:** `GET /funds/features`, a dedicated resource. Fields on `/funds/summary` and `/funds/deposits/limits` were rejected for two reasons stated in `lld-backend.md` §4.4: two carriers drift, and a trader whose summary is unavailable would lose their switches with it, tying a feature flag's availability to an unrelated upstream's health.

---

## 5. Deliberate Asymmetries — Checked And Correct

Not every difference between the two documents is a discrepancy. These were examined and are right as they stand.

| Asymmetry | Why it is correct |
|---|---|
| The rail intersection appears only in 5b | The server does not hold the trader's offered rails at memory-read time; the client already does. Both documents say so, in the same terms |
| The projection appears only in 5b | It is client-side arithmetic by design — `hld.md` §5.5. The server has no projection concept at all, and should not |
| `/internal/funds/settlements/destination-faults` appears only in 5a | Operations surface, internal prefix, not routed from the public gateway |
| The judgement, migrations and chunk locking appear only in 5a | No client concern touches them |
| REQ-SF-04 requires no backend work | Both documents state this. The absence of backend work is the design decision, not an omission |

---

## 6. Verdict Of The Consistency Pass

The two low-level designs describe the same system. Six endpoints, six shared types and five behavioural rules match. Three discrepancies were found and closed in their owning sub-stages; two earlier contract gaps were settled before the pass ran, both with the reasoning recorded on the side that discovered the constraint.

D-2 is the finding worth carrying forward: it is the same defect class the Stage 4 review found in G4's instrumentation — a guardrail whose denominator does not exist — reappearing on the sibling metric. Stage 6 should check the remaining guardrails on the same basis rather than assuming the class is now closed.

**Postscript, after Stage 6.** That carry-forward was worth making: applying D-2's method across the whole metric set found four more instances — K4, K5, K6's tag and K7 had no instrumentation in either design. Both LLDs had specified the behaviour the PRD asked for and neither had walked the PRD's own metric table row by row. All four are now closed, and the re-verified endpoint rows above reflect the resulting contract changes. The class took three passes to close because each pass found it in a different place; a fourth pass over the PRD's tracking table, rather than over the designs, is what finally exhausted it.

No discrepancy required a design change. Both documents are internally consistent and consistent with `hld.md` v3.
