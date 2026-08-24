---
title: FMS — Development Handover
doc_id: FMS-HANDOVER-001
version: 1.0.0
date: 20 Aug 2026
status: Handover brief. The PRDs govern; this routes what is unanswered.
governs_nothing: This document decides nothing. It says what is ready, what is not, and who owns each gap.
---

# Fund Management System — Development Handover

Eight requirement files, 72 active requirements, a working prototype, and a complete instrumentation
spec. **Phase 1 has no open blocker.** What follows is everything that is not ready, who owns it, and
which phase it gates — so nothing falls between two teams.

---

## 1. Read in this order

| # | File | Why |
|---|---|---|
| 1 | [product-requirements.md](../02-requirements/product-requirements.md) | The spine. Engineering Digest lists all 72 requirements and the file that owns each. Start at the end-to-end flow |
| 2 | The six feature files | [Balances & Margin](../02-requirements/product-requirements-balances-and-margin.md) · [Add Funds](../02-requirements/product-requirements-add-funds.md) · [Withdraw Funds](../02-requirements/product-requirements-withdraw-funds.md) · [Transactions & Statements](../02-requirements/product-requirements-transactions-and-statements.md) · [Account Health](../02-requirements/product-requirements-account-health.md) · [Configuration](../02-requirements/product-requirements-configuration.md) |
| 3 | [Communications](../02-requirements/product-requirements-communications.md) | 27 requirements, 17 rules, the message catalogue. **Read §11 before building withdrawal** |
| 4 | [Events & Funnels](../03-instrumentation/product-requirements-events-and-funnels.md) | The instrumentation spec. **Not** the tracking table in the spine — that section is superseded and says so |
| 5 | [`04-prototype/`](../04-prototype/) | Working prototype. `derive()` in `app.js` is the single definition of every balance; `metrics()` in `dashboard.js` the single definition of every figure |

**The one trap.** `product-requirements.md` §Tracking Requirements was replaced on 19 Aug with a
pointer. If you are reading a copy that still lists twenty-one event names, it is stale — eight of
those rows sent balances as event properties, which the same document's own privacy NFR forbids.

---

## 2. Blockers, by the phase they gate

### Phase 1 — the account is legible · **NOTHING BLOCKS THIS**

Three balances, the withdrawable derivation, the ledger, CSV export, the blocked and empty states.
Ships without any answer below. Start here.

One dependency to confirm, not resolve: **EB-2** is settled in principle — front office supplies
margin in market hours, TechExcel outside — but the interface contract itself needs writing.
REQ-107's staleness stamp depends on knowing which source answered and when.

### Phase 2 — money can come in

| Gate | Owner | Detail |
|---|---|---|
| **Comms orchestration** | Product owner with engineering | CleverTap Journeys triggered by FMS events, or an FMS scheduler with CleverTap as delivery only. Blocks all 27 communications requirements. **It sits in front of SMS template registration, which the annex flags as the slowest item in the release** — a DLT queue, not a build task. Start it before development. Recommendation on record: **FMS renders, CleverTap delivers** |
| Bank module interface | Product owner | EB-5 is closed in principle — Profile owns add, delete and set-primary, FMS reads the list and never mutates it. The interface and its readiness date still need to exist |

### Phase 3 — money can go out

| Gate | Owner | Detail |
|---|---|---|
| **Withdrawal authentication (C-Q8)** | **Product owner with authentication** | **Ruled a blocker 20 Aug 2026.** Today no point in the flow requires the genuine account holder to act. Someone with account access can withdraw to a bank account already on file; the only notification is an email, probably to an inbox the same person controls, arriving after the instruction. **Belongs to authentication, not FMS** — which is exactly why it can be lost at a handover. Costs **zero** instrumentation change: `otp_purpose: withdrawal_confirm` is registered and unemitted. See [Communications §11](../02-requirements/product-requirements-communications.md) |
| **Trading & settlement calendar (EB-9)** | Product owner with compliance | The last fully-open estimation blocker. TechExcel or a direct download, under what licence and cadence. **Which days are working days is not derivable from the day of the week**, and the arrival date is wrong without it. Three requirements depend on it |

### Phase 4 — the account looks after itself

| Gate | Owner | Detail |
|---|---|---|
| **Debit interest rate (EB-8)** | Finance with TechExcel | The 18% p.a. in configuration is a stand-in. **No production message may quote a rate until the real one exists.** The obligation to disclose stands regardless |
| **Regulatory bypass configuration** | Product owner with compliance | How margin-shortfall intimation ignores preference, quiet hours and frequency capping *without every other message inheriting the exemption*. Wrong in one direction is a compliance failure, wrong in the other is a spam complaint |

### Phase 5 — refinements

**EB-6** — does TechExcel expose a *scheduled, not-yet-posted* charge, or only posted ones? Determines
whether REQ-503 is buildable at all. Finance with TechExcel.

---

## 3. What the dev team owes that is in no PRD

| # | Item | Why it lands on you |
|---|---|---|
| 1 | **The wrapper validation table and the §5-diff CI job** | THINQ-EVENTS-001 §8 requires both to ship **with** the instrumentation: per-module validation for the six module-scoped enums, and CI diffing it against §5. Every *"the wrapper will catch it"* argument in the corpus is currently a promise. Nobody has scheduled this |
| 2 | **EB-10 — volumes and behavioural assumptions** | Explicitly deferred to technical design: funding volume, concurrent peaks at market open and settlement dates, history depth per account, dormant and debit proportions. Technical design is your first task, so this is yours on day one |
| 3 | **State-transition persistence** | REQ-405 requires every state a movement passed through, with times and the reason at any refusal, **written as it happens** rather than reconstructed from current status. A movement whose intermediate states were never written cannot have its timeline shown afterwards. This is a write-path decision, not a display one |
| 4 | **A registrar to route value registrations to** | THINQ-EVENTS-001 OD-1, still P0 and unfilled. You will need to register values as you build, and there is nobody to approve them |

---

## 4. Decisions already taken — do not reopen

| Decision | Ruling |
|---|---|
| **The 95% deposit target (FMS-OD-2)** | **Closed 20 Aug.** Counts **first** payment instructions FMS issued; succeeded = reached `fund_credited`; **`FUNDS_USER_CANCELLED` excluded from the denominator**. Retries are measured separately by funnel F4 |
| **The derivation affordance (FMS-OD-3)** | **Closed 20 Aug — render-conditional.** Shown only where a gap exists. `funds_home_funded` is therefore a clean denominator and the `_gap` / `_nogap` screen split is **not** registered and SHALL NOT be |
| **Balances to CleverTap** | **No.** Rule R5 and the privacy NFR agree. `amount_paise` — what *moved* — is lawful; any balance is not. What carries each question instead is in [Events & Funnels §6](../03-instrumentation/product-requirements-events-and-funnels.md) |
| **Reservation at request** | **Removed.** Rule W3: a request reserves nothing and settles at end of day against whatever is available then. Rule W4 (one open request) now carries the double-spend protection alone |
| **Statement format** | CSV only. No PDF in this phase |
| **Bank account management** | Profile owns add / delete / set-primary. FMS consumes the list and never mutates it |
| **Balances** | Merged. No segment-level segregation this phase, but entries must still record their segment |

---

## 5. The invariants — target zero, not a trend

These are not KPIs to improve. Any occurrence is a defect.

- No withdrawal accepted against a correctly-computed withdrawable figure is later refused for insufficiency
- No duplicate credit for one payment, however many confirmations arrive
- No day on which the ledger's entries fail to sum to its stated balance
- No account reaches a debit balance without having been told
- Every payout arrives by the time quoted at request — the bar is **100%**, and it reads below the moment one payout is late

---

## 6. Instrumentation, in one paragraph

FMS registers **74 values, 5 property claims and zero new event names**. Event names are the scarce
resource — 512 per CleverTap account, permanent, product-wide, not reclaimable — so **every funnel step
is a filter on a name that already exists**. Thirteen funnels, F1 to F13, are defined in
[§7](../03-instrumentation/product-requirements-events-and-funnels.md) with their nodes and close conditions. The taxonomy's
one emission blocker was closed on 19 Aug. **Nothing has been emitted yet** (OD-2, confirmed 20 Aug),
which means value renames are still free — and stop being free the moment the first event fires.

---

## 7. Open, non-blocking

Carried so they are not lost: the exact-amount debt exception below the ₹100 minimum (finance);
statutory retention for movements, corrections and statements (compliance); whether *available margin*
gets a derivation as well as *withdrawable* (product); two-person ledger correction when the ops console
is scoped (compliance); the SLA for handling a returned payout (operations); and six open instrumentation
decisions, FMS-OD-4 to FMS-OD-9, in [§11](../03-instrumentation/product-requirements-events-and-funnels.md).
