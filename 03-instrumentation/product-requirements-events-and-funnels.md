---
title: FMS Event and Funnel Specification
prd_id: FMS-EVENTS-001
version: 0.2.0 — DRAFT for the registrar
date: 20 Aug 2026
status: Draft — registers `module: funds` against the ratified product taxonomy
owner: FMS product, with Analytics (registrar unassigned — THINQ-EVENTS-001 §9 OD-1)
changelog:
  - 0.2.0 (20 Aug 2026) — re-based on THINQ-EVENTS-001 v1.1.0. §3's four defects D1–D4 are
    all registered and the section is recast from a live complaint into the record of what was
    found and fixed. FMS-OD-1 is CLOSED — `context_type: account` and `session` were granted in
    one pass with the `module: marketing` pre-auth nullability rule (authority §2 row 4) — and
    §2.1 row 4, §5.12 and the §11 summary line are corrected with it. §12 is audited row by row
    against the authority on disk and every row is now marked LANDED or STILL OWED; seven rows
    landed, seven are still owed. §13 test 5 is restated as a regression test, since its
    precondition is historical. Two superseded strings (`funds_balance`, `unsettled_funds`) are
    marked as such in §2.3. No value, funnel, claim or count in this document changes.
  - 0.1.0 (19 Aug 2026) — first registration of `module: funds` against THINQ-EVENTS-001 v1.0.0.
governs:
  - product-requirements.md §Tracking Requirements (all 21 rows — superseded on adoption)
subordinate_to:
  - THINQ_EVENT_TAXONOMY.md (v1.1.0, 19 Aug 2026) — the authority. Where this document and
    that one disagree, that one governs and this one is a defect to be corrected.
    OD-2 is closed: nothing has been emitted to CleverTap, confirmed 20 Aug 2026.
related:
  - product-requirements-add-funds.md — Rules A1–A13, REQ-201 to REQ-207
  - product-requirements-withdraw-funds.md — Rules W1–W12, REQ-301 to REQ-308
  - product-requirements-balances-and-margin.md — Rules B1–B12, REQ-101 to REQ-108
  - product-requirements-account-health.md — Rules H1–H8, REQ-501 to REQ-506
  - product-requirements-transactions-and-statements.md — Rules L1–L9, REQ-401 to REQ-407
  - product-requirements-communications.md — Rules C1–C11, REQ-601 to REQ-627
  - product-requirements-configuration.md — REQ-701 to REQ-710
  - THINQ_EVENT_TAXONOMY_TESTS.md (THINQ-EVENTS-TESTS-001, 18 Aug 2026) — the adversarial suite
    against the authority. D-01 is FMS-OD-1's defect, filed a day before this document
  - web/dashboard.js — `metrics()`, the funnel definitions this document must be able to feed
binding: |
  FMS registers VALUES against an existing taxonomy. It does not register event names.
  Every funnel below is a filter on a name that already exists. Five property claims go to
  the registrar under R6; the one context_type claim was granted in v1.1.0 and FMS-OD-1 is
  closed; everything else needs no review.
---

# FMS Event and Funnel Specification

> Part 9 of the [Fund Management System PRD](../02-requirements/product-requirements.md), and the FMS module
> registration against [THINQ_EVENT_TAXONOMY.md](../05-dependencies/thinq/THINQ_EVENT_TAXONOMY.md).
> This part owns what FMS sends to CleverTap and the funnels those events can answer.
> It supersedes §Tracking Requirements in the parent document.
>
> **Review copy:** published as §19–§23 of the Thinq Event Spec artifact —
> <https://claude.ai/code/artifact/699af147-9ff1-4a94-b37b-539336ef5369>
> (source: `kyc-ops-console/kyc-event-spec.html`). The artifact adds the screen-to-screen
> flow tables; this file and those sections are one artefact and a change to either is a
> change to both.

**In one line: FMS registers 74 values, 5 property claims and 0 event names, and points at the six
profile properties the authority §5.9 already carries.**

## Contents

- [1. The rule this document obeys](#1-the-rule-this-document-obeys)
- [2. What FMS inherits unchanged](#2-what-fms-inherits-unchanged) — the envelope, fifteen rows and nineteen names, what is already registered
- [3. Four registered enums FMS could not ship around](#3-four-registered-enums-fms-could-not-ship-around--found-and-closed) — D1 to D4, all four now registered
- [4. The instrument split](#4-the-instrument-split--which-event-carries-which-moment) — spine versus request lifecycle
- [5. What FMS registers](#5-what-fms-registers) — values, five property claims, the count
- [6. The money rule](#6-the-money-rule--and-the-prd-open-question-it-closes) — closes the balances-to-CleverTap open question
- [7. The funnels](#7-the-funnels) — F1 to F13
- [8. The PRD's tracking table, mapped row by row](#8-the-prds-tracking-table-mapped-row-by-row) — all 21 rows
- [9. De-duplication keys](#9-de-duplication-keys)
- [10. What FMS deliberately cannot measure](#10-what-fms-deliberately-cannot-measure)
- [11. Open decisions](#11-open-decisions) — FMS-OD-1 to FMS-OD-9, one closed
- [12. What changes elsewhere on adoption](#12-what-changes-elsewhere-on-adoption)
- [13. Acceptance tests](#13-acceptance-tests-for-this-specification)

---

## 1. The rule this document obeys

THINQ-EVENTS-001 already lists FMS. Module 6 of 18, `module: funds`, **"New names required: 0"**.
This document is the discharge of that line: it takes the eight FMS requirement files written
since, and shows they need no name either.

**Event names are the scarce resource** — 512 per account, permanent, product-wide, not
reclaimable. 40 are spent; 472 remain. FMS is the third-largest module in the product by
surface count and it spends **zero**. A funnel step is a *filter* on an existing name, never a
new name (R6). That is the whole discipline, and it is the reason the KYC taxonomy is worth
inheriting rather than paralleling.

**What FMS actually contributed back** is four corrections. The taxonomy was ratified on
17 Aug 26. Four FMS decisions dated 19 Aug 26 — SMS as the only mandatory channel, the payout
rail, the three-balance split, and Rule W3's replacement of reservation with end-of-day
settlement — each landed on a registered enum that could not express them. **All four were taken
into THINQ-EVENTS-001 v1.1.0**, so §3 is now the record of what was found and fixed rather than a
list of blockers. The diagnosis is kept there in full rather than deleted, because a module that
quietly works around a closed enum is how `bank_manual` and `bank_manual_entry` both came to
exist — and a registry that forgets why a value exists is how the retired string gets re-minted
against a different fact.

---

## 2. What FMS inherits unchanged

### 2.1 The envelope — eleven properties, none added, none removed

| # | Property | What it carries for FMS |
|---|---|---|
| 1 | `module` | Always `funds`. Follows the **destination**: funds reached from a rejected order, from a shortfall SMS, or from the profile menu are all `funds`. The route rides `nav_source_element` and `referrer_screen` |
| 2 | `sub_module` | `add` · `withdraw` · `ledger` · `margin` — registered. **`health` is added** (§5.2) |
| 3 | `context_id` | **Our reference, which the customer already holds.** `MS8841` for a shortfall, `DU2207` for a due, the payout reference for a withdrawal. Never the bank's UTR — Rule C8 keeps those two apart and so does this |
| 4 | `context_type` | `fund_txn` for a payin, a payout and a mandated return. `service_request` for a due and a shortfall. **`account` on every funds home** — the account-level surface had no lawful value when this document was written; `account` and `session` were registered in one pass in the authority §2 row 4, closing FMS-OD-1 (§11). The synthetic `fund_txn` id is dead |
| 5 | `session_id` | Present, and never the funnel key except in the one pre-record funnel that has no record yet (F2, §7) |
| 6 | `platform` | `mweb` · `desktop_web` today. `system` on every end-of-day transition — Rule W4b's whole point is that nobody is watching |
| 7 | `screen_name` | Nine values registered in §5.1 |
| 8 | `step_name` | `add` · `withdraw` — **already registered, unchanged** |
| 9 | `stage_name` | `fund_*` · `wdl_*` · `rac_*` registered; **`due_*` and `shf_*` added**, 17 values in total (§5.4) |
| 10 | `account_state` | Read, never written by FMS. **Never confused with the state of the money** — see §5.1 |
| 11 | `engagement_state` | Server-computed. FMS neither sets nor reads it |

### 2.2 The names FMS emits — fifteen rows, nineteen names, all of them already exist

**Source** is the authority's own column (§4), carried here so the markdown half of this artefact
states it rather than leaving it to be inferred.

| Name | Source | What FMS uses it for |
|---|---|---|
| `Screen Viewed` | client | Every funnel's denominator. Frozen schema; the funds state rides `screen_name` (§5.1) |
| `Element Clicked` | client | Amount entry, suggestion pills, route choice, derivation lines, statement export |
| `Overlay Opened` / `Overlay Dismissed` | client / client | The withdrawable derivation, the margin breakdown, the deployability panel, the payin outcome modal, the withdrawal confirmation |
| `Field Errored` | client | Rule A13's keystroke refusal, and the below-minimum and above-headroom messages — **no vendor call, no attempt consumed** |
| `Journey Step Completed` | server | The in-session act: a payment instruction FMS issued, a withdrawal request FMS accepted |
| `Journey Step Failed` | server | That act failing against the PSP or a rule. Carries `attempt_index`, `outcome_code`, `error_class` |
| `Journey Step Abandoned` | server (sweep, `platform: system`) | Fires nowhere until per-step idle thresholds exist (OD-5). §7's open-step rule covers the population without it |
| `Request Stage Changed` | server | **The workhorse.** Every payin, payout, mandated return, due and shortfall lifecycle |
| `Action Blocked` | client | Every refusal: no verified bank, nothing withdrawable, stale margin, one request already open, route cap, below minimum, frozen, in debit |
| `Attempt Cap Reached` | server | `cap_type: fund_collect` — registered, unchanged |
| `Vendor Call Completed` / `Vendor Failure Detected` / `Service Restored` | server / server / server | PSP collect, PSP payout, and the two margin sources (§5.9) |
| `Message Dispatched` / `Notification Deep Link Opened` | server / **client** | All 27 communications requirements |
| `Document Retrieved` | server | REQ-407's CSV statement. Zero new properties |
| `Sensitive Value Revealed` | server | The concealed-by-default balances, `tier: F` |
| `Query Answered` | server | REQ-403's transaction search, and its `result_count: 0` |

That is **fifteen rows and nineteen distinct names**, which is the figure §13's test 2 greps
against. **None of them is new.**

**The one row whose two halves differ is the last pair.** `Message Dispatched` is server; per the
authority §4 Layer 8, `Notification Deep Link Opened` is **client** — the CTA is opened in a
browser. A funnel that assumes both are server-emitted assumes the dispatch-to-open join is free;
it is not free, it is registered (`notification_id` is scoped to both events), and F7 node 3 and
F9 depend on it.

### 2.3 What was already registered for `module: funds` and stands

`stage_name` families `fund_*` (6) · `wdl_*` (7) · `rac_*` (5) · `request_type` `fund_deposit` ·
`fund_withdrawal` · `running_account_settlement` · `blocked_reason` `no_verified_bank` ·
`account_frozen` · ~~`unsettled_funds`~~ *(superseded — §5.6)* · `negative_balance` ·
`outcome_code` nine `FUNDS_*` / `WDL_*` · `funding_method` `upi_collect` · `upi_intent` ·
`netbanking` · `neft` · `imps` · `rtgs` · `cheque` · `leg` `psp` · `bank` · `cap_type`
`fund_collect` · `otp_purpose` `withdrawal_confirm` · `reveal_group` ~~`funds_balance`~~
*(superseded — D3)* · `ledger_balance` at `tier: F` · `service_id` `psp_collect` · `sub_module`
`add` · `withdraw` · `ledger` · `margin`.

**Two of those no longer stand.** `blocked_reason: unsettled_funds` and `reveal_group:
funds_balance` are both **superseded, not reclaimed** in the authority v1.1.0 — §5.2 and §5.7
respectively. Neither SHALL be emitted and neither string SHALL be re-minted against a different
refusal or a different balance. Everything else in the list above is unchanged.

**One of those is already worth banking.** `otp_purpose: withdrawal_confirm` is registered and
has no emitter. Communications §11 C-Q8 records an open security gap whose recommended fix —
option A, an OTP on the withdrawal request — therefore costs **zero taxonomy change**. Closing
the gap is an authentication decision, not an instrumentation one.

---

## 3. Four registered enums FMS could not ship around — found and closed

Each was a closed enumeration that a decision dated after ratification had made unable to carry
its fact. Each was a **value addition under R6**, which needed no review — but each was recorded
here rather than added quietly, because the taxonomy's own history is a list of enums that were
extended in two places under two spellings.

**All four are now registered.** THINQ-EVENTS-001 **v1.1.0** (19 Aug 2026) took every one, and
OD-2's closure on 20 Aug 2026 — nothing has been emitted to CleverTap — makes each of them a final
addition rather than a provisional one. The diagnosis below is kept in full rather than deleted:
it is the reasoning that justified each value, and the strings it retires are *claimed* rather
than freed, which is a fact that has to survive in writing or it will be re-minted against
something else. Each block ends with its resolution and the authority section that now carries it.

### D1 — `channel` had no `sms`, and SMS is the only channel FMS is required to use

`channel` was closed at four: `whatsapp` · `email` · `in_app` · `push`.

Communications Rule C2 is unambiguous: *"SMS reaches everyone; nothing else does. It is the only
channel that needs no opt-in, no inbox and no internet, so it is reserved for the two states
where the account cannot wait."* Five SMS templates ship — `THINQ_MARGIN_SHORTFALL`,
`THINQ_SHORTFALL_CLEARED`, `THINQ_SQUARED_OFF`, `THINQ_DUES_OUTSTANDING`, `THINQ_DUES_CLEARED` —
and they carry the two states with a regulatory bypass on preference, quiet hours and frequency
capping.

Meanwhile §9 of the same file says **push is not sent at all**: *"There is no mobile
application."* So the registered enum contained a channel FMS will never use and omitted the only
one it must.

**Registered.** `channel` is **5, closed**: `whatsapp` · `email` · `in_app` · `push` · **`sms`** —
authority **§5.7**, which carries the argument verbatim and states the consequence FMS named:
without the value every margin-shortfall and dues message would have been either unloggable or
logged under a false channel, and `dispatch_outcome: suppressed_consent` silently wrong for a
channel that requires no consent. The authority also settles the other half of the observation
this document only raised in passing — `push` is kept and **has no emitter today**, so a
`Message Dispatched{channel: push}` row is a defect, not a dispatch.

### D2 — no `service_id` for the payout rail, so a payout outage had no restore template

`service_id` was a closed registry of 21. It contained `psp_collect`. It contained no outbound
payment service of any kind.

§5.8 of the taxonomy states the consequence itself: *"Service-specific restore copy is required
per `service_id`, so an unregistered id means an outage with no recovery template."* FMS has
exactly that outage as a **named product outcome**: EOD outcome *bank rail is down* is the one
withdrawal result that leaves the request **open and cancellable** and queues it for the next run.
It was the only FMS state whose resolution is a `Service Restored`, and it had no id to fire on.

**Registered.** `service_id` is now a **closed registry of 24** and carries **`psp_payout`** —
authority **§5.8**, which calls it *"the addition that matters"* and gives FMS's
`stage_name: wdl_rail_queued` as the reason in its own words: *"Without the id there is no
incident to close and no template to send."* The margin feed's two ids landed in the same pass
(§5.9), and the authority records why they are separate from `rms` rather than folded into it —
the margin figure has two sources, front office in market hours and back office outside, and
`blocked_reason: stale_margin_data` is a refusal about **which** source answered and how long ago,
which one id cannot carry.

### D3 — `reveal_group` conflated the three balances the module exists to separate

Registered at the time: `funds_balance` and `ledger_balance`. The entire wedge of this PRD is that
a broking account answers *"how much do I have?"* with **three** numbers — ledger balance,
available margin, withdrawable — and *"every product examined for this PRD conflates at least two
of those three."*

A reveal registry that carries two of the three does the conflation in the analytics layer that
the product layer refuses to do on screen. Worse, it conflated the wrong pair: `funds_balance`
is not a term the PRD uses at all, so a filter written against it would silently miss whichever
figure the author meant.

**Registered.** `reveal_group` is now **14** and carries **`available_margin`** and
**`withdrawable`** beside `ledger_balance`, all three at `tier: F` — authority **§5.7**, whose
ruling note restates this diagnosis almost word for word: a registry carrying two of the three
*"does in the analytics layer the conflation the product layer exists to refuse."*
`funds_balance` is **superseded, not reclaimed**, and the
authority claims the retired string in terms stronger than this document asked for: it SHALL NOT
be emitted and SHALL NOT be re-minted against **any** of the three, because a filter written
before that revision would mean a different balance after it.

### D4 — `wdl_*` predated Rule W3 and could not express the end-of-day outcome set

The registered family assumed a request that is validated, reserved, approved and sent:
`wdl_requested` · `wdl_cutoff_queued` · `wdl_approved` · `wdl_bank_sent` · `wdl_credited` ·
`wdl_rejected` · `wdl_cancelled`.

Rule W3 replaced that on 19 Aug 26: **a request reserves nothing and is settled at end of day
against whatever is available then.** Rule W4b splits the lifecycle into two moments with
different audiences, and Communications §4.4 enumerates what the second one decides. Five of
those eight outcomes had no stage:

| Outcome | Rule | Stage as registered then | Stage now — authority §5.1 |
|---|---|---|---|
| Held for review (at submit) | Comms §4.3 | **none** | `wdl_under_review` |
| Partly sent | W4a, W4c, REQ-617 | **none** — and it is neither `wdl_credited` nor `wdl_rejected` | `wdl_part_sent` |
| Nothing left at end of day | W4a | **none** — nobody refused; there was nothing | `wdl_nil_settled` |
| Returned by the bank | W7, REQ-618 | **none** — `wdl_rejected` is *our* refusal, not the bank's | `wdl_bank_returned` |
| Banking network unavailable | Comms §4.4 | **none** — and it is the only outcome that **keeps the request open** | `wdl_rail_queued` |

R8 is the rule that was being broken: *"`expired` is not `failed` is not `withdrawn`. Three
different populations with three different owners."* FMS has five, and forcing them into
`wdl_rejected` merges a bank refusal, an empty account and a rail outage into one number — which
is precisely the number the withdrawal funnel exists to break apart.

**Registered — all five.** `wdl_*` now runs to twelve values and the authority **§5.1** states the
R8 split in its own Terminal-of-record cell: *"`wdl_credited` — `wdl_rejected` is *we* refused,
`wdl_bank_returned` is *the bank* refused, `wdl_nil_settled` is nobody refused (R8)."* The paired
outcome code is registered as **`WDL_BANK_RETURNED`**, never `REJECTED`, for the same reason
(§5.2, and §5.5 here). The full FMS stage registration — seventeen values across four families —
stands unchanged at §5.4.

---

## 4. The instrument split — which event carries which moment

FMS is the first module in the product to use **both** the journey spine and the request
lifecycle for the same act. Drawing that line wrongly produces double-counted funnels, so it is
drawn here once and everything else follows from it.

The line is the PRD's own. Rule W4b: *"Submitting and settling are two events, and only the
first has a user present."*

| | `Journey Step Completed` / `Failed` | `Request Stage Changed` |
|---|---|---|
| **When** | In session, seconds to minutes | Across hours and days |
| **Who is there** | The user | Nobody — `platform: system` on every EOD transition |
| **Grain** | One *attempt* | One *record's* transition |
| **Keyed by** | `step_name` + `attempt_index` | `stage_name` |
| **Carries** | `attempt_index` · `outcome_code` · `error_class` · `attempts_remaining` · `cap_reached` · `vendor` · `vendor_attempt` · `duration_sec` · `fallback_used` | `stage_name` · `previous_stage_name` · `seconds_in_previous` · `seconds_in_request` · `leg` · `amount_paise` · `funding_method` · `initiated_by` · `related_context_id` |
| **FMS example** | Third try at a ₹5,000 UPI payin, second on net banking after the first route was capped | That payin's record moving `fund_initiated → fund_credited`, and eight hours later a payout moving `wdl_requested → wdl_part_sent` |
| **Fires** | Once per attempt | Once per transition |

**The three consequences worth stating.**

**Retry-and-route-change is a spine question, not a lifecycle one.** The PRD's tracking table
asks for *"whether a retry followed, whether the retry changed route"*. That is
`Journey Step Failed{attempt_index: 1, funding_method: upi_collect}` followed by
`Journey Step Completed{attempt_index: 2, funding_method: netbanking}`. Under **R1 no
`route_changed` boolean is sent** — it is a function of two events and is computed at query
time. Rule A12's *automatic* route switch, where the user chose nothing, rides the already
registered `fallback_used: true` on the same event.

**`method` stays null for funds.** `Journey Step Completed.method` is closed at 21 values, none
of which is a payment route. Adding four would create a second vocabulary for a fact
`funding_method` already carries, which is the one-fact-one-place violation R1 exists to stop.
`funding_method`'s scope widens to the spine — a scope widening, not a duplication, on the
precedent of `state_intact`.

**A withdrawal's `Journey Step Completed` is the submission, not the payout.** It fires when FMS
accepts the request. Everything §4.4 of Communications decides is `Request Stage Changed`. A
funnel that closes on the spine event is measuring *whether the user asked*, and a funnel that
closes on `wdl_credited` is measuring *whether they were paid*. They are different questions and
F5 keeps them in one funnel with the boundary marked.

---

## 5. What FMS registers

**Legend.** 🔶 = module-scoped enumeration; the filter is always `module = funds AND <property> = Y`.

### 5.1 `screen_name` — nine values, and the reason there are five funds homes

The registered test is: *"A new value only where the rendered **controls** change, not where only
the data changes."* FMS's account states pass that test, and passing it is what lets the funnel's
top step carry the account's condition **on a frozen event**.

| Value | Controls rendered | Authority |
|---|---|---|
| `funds_home_blocked` | The blocker and the one action that clears it. **The deposit form is replaced, not disabled beside it** | Rule H6, REQ-505 |
| `funds_home_empty` | One statement of state, one of purpose, one of the smallest useful amount, one action. Not a decomposition of zeros | Rule H5, REQ-504 |
| `funds_home_funded` | Three balances, the derivation affordance, both money paths live | REQ-101 to REQ-107 |
| `funds_home_debit` | Amount owed, its cause, its accrual, and a **pay-the-exact-amount control that overrides the ₹100 minimum** | Rules H1–H3, REQ-501, REQ-502 |
| `funds_home_shortfall` | Shortfall amount and deadline lead; funding is the primary action and leads with the fastest route; withdrawable is forced to zero | Rules H7, A11, REQ-207, REQ-506 |
| `funds_add` · `funds_withdraw` | The two money paths | REQ-201–207, REQ-301–308 |
| `funds_transactions` | The list, its filters, and the export — REQ-407 puts the export here and **not** on a statements destination | REQ-401–404, REQ-407 |
| `funds_transaction_detail` | One movement's full state history with times and reasons | REQ-405 |

Five homes is a genuine control difference in every case, and each is a different product promise
being tested. It is also what makes F2 and F11 (§7) expressible at all.

**Staleness is not a tenth screen.** REQ-107's stale margin data changes no controls until it
refuses one, and it refuses at the moment of action. It rides `Action Blocked{blocked_reason:
stale_margin_data}`, which is the only moment it matters and the only moment it is measurable.

**`account_state` is not touched.** The envelope's `account_state` answers *whether the account
works*; these values answer *what state the money is in*. Adding `debit` or `empty` to
`account_state` would propagate product-wide the moment a second module emitted it — §2's stated
failure mode — and would collide with `frozen`, which FMS reads and never writes.

### 5.2 `sub_module` — one value added

`add` · `withdraw` · `ledger` · `margin` are registered. **`health` is added** for dues,
shortfall and blockers: REQ-501 to REQ-506 are a fifth surface, not a variant of the other four,
and `funds_home_debit` / `funds_home_shortfall` have no lawful `sub_module` without it.

### 5.3 `step_name` — nothing added

`add` and `withdraw` are registered and sufficient. **Clearing a due is not a third step**: it is
`step_name: add` on a deposit whose `related_context_id` is the due, which is what makes REQ-502's
minimum-waiver measurable without a new value.

### 5.4 `stage_name` 🔶 — seventeen values added across four families

**`funds` — `fund_*`, 6 registered + 2**

`fund_initiated` · `fund_collect_sent` · `fund_approved` · `fund_credited` · `fund_failed` ·
`fund_expired` · **`fund_awaiting_confirmation`** · **`fund_reversed`**

`fund_awaiting_confirmation` exists because **R8 and Rule A9b are the same rule discovered twice.**
R8: *"`expired` — nobody decided; a vendor timed out. A vendor timeout is not a failure and SHALL
NOT consume an attempt."* Rule A9b: *"Never say 'failed' when the outcome is unknown. A bank that
has not answered is its own state, because the recovery is the opposite of a failure's: wait, and
specifically do not retry."* The registered `fund_expired` is the write-off at the end of that
wait; the wait itself had no stage, and it is the state that carries the *do not retry* instruction.

`fund_reversed` carries Rule A10: *"A deposit is reversed, never deleted."* It joins its original
by `related_context_id` and, where the money was already used, raises a `due_*` record that joins
back the same way (F13).

**`fund_approved` means PSP approval *before* credit, and nowhere else.** The order above is the
order the authority §5.1 registers: approved at the bank, money not yet with us, credit next.
**Margin availability is simultaneous with `fund_credited`** and has no stage of its own — there is
no `fund_margin_available` and none is registered, because no artefact and no build evidences a
distinct margin moment. F3 node 6 is written as a latency annotation for that reason (§7).

**R7 waiver — `_withdrawn` and `_cancelled`.** The `fund_*` family registers neither, and the
reason is recorded rather than left to be discovered. A customer who backs out before approval
rides `outcome_code: FUNDS_USER_CANCELLED` on `fund_failed` and is separated **by filter**, which
is exactly what makes FMS-OD-2's threshold choice a filter rather than a rewrite. A stage would
duplicate a distinction the code already carries; none is minted.

**`funds` — `wdl_*`, 7 registered + 5** *(the D4 fix)*

`wdl_requested` · `wdl_cutoff_queued` · `wdl_approved` · `wdl_bank_sent` · `wdl_credited` ·
`wdl_rejected` · `wdl_cancelled` · **`wdl_under_review`** · **`wdl_part_sent`** ·
**`wdl_nil_settled`** · **`wdl_bank_returned`** · **`wdl_rail_queued`**

| New value | Fires when | Terminal? |
|---|---|---|
| `wdl_under_review` | Held at submission, resolved within one working day | No |
| `wdl_part_sent` | Less was available at end of day than was asked for. **Closes the request** (Rule W4a) and states requested-against-sent (Rule W4c, REQ-617) | Yes |
| `wdl_nil_settled` | Nothing was available. Nobody refused — R8's `expired` case, in its funds spelling | Yes |
| `wdl_bank_returned` | The destination bank could not accept it; returned by a compensating entry and **never automatically resent** (Rule W7) | Yes |
| `wdl_rail_queued` | The banking network was unavailable. **The one non-terminal EOD outcome** — the request stays open and cancellable and re-enters the next run | No — repeats |

R7 is satisfied: `wdl_credited` is the completion of record; `wdl_rejected` (we refused),
`wdl_bank_returned` (the bank refused), `wdl_nil_settled` (nobody refused) and `wdl_cancelled`
(the customer refused) are four distinct owners, exactly as R8 requires. `_expired` is explicitly
waived: a withdrawal request does not expire, it settles — a rail outage requeues it and Rule W4a
closes it whatever it paid.

**`funds` — `rac_*`, 5 registered, unchanged**

`rac_due` · `rac_computed` · `rac_bank_sent` · `rac_credited` · `rac_failed`. Cyclic and
non-terminating on the `sip_*` pattern; each cycle is its own `context_id`. Rule W8's
advance announcement is a `Message Dispatched`, not a stage — the announcement is a
communication about a stage, not a stage of its own. Rule W9's interaction with an open payout
is arithmetic inside `rac_computed`, and rides `related_context_id` to the payout it accounted for.

**`funds` — `due_*`, new family, 5**

`due_raised` · `due_notified` · `due_part_paid` · `due_cleared` · `due_written_off`

Raised by `initiated_by: system` — a debt is not a request anyone made, and `system` is the value
the taxonomy added for exactly this class (a running-account payout, a referral credit, an
automated corporate-action credit). Cleared by `initiated_by: self_serve` with the clearing
deposit joined by `related_context_id`.

R7: `due_cleared` is the completion of record, `due_written_off` the other terminal. `_expired`
and `_withdrawn` are **explicitly waived** — a debt does not lapse and a customer cannot withdraw
one. `_failed` is waived: a failed attempt to clear a due is a failure of the *deposit*, and lands
on `fund_failed` with `related_context_id` pointing here.

**Interest accrual emits nothing.** REQ-708's accrual runs daily and is not customer-visible until
it is notified. A daily `due_interest_accrued` would fire against one `context_id` forever and
collapse under the published de-duplication key (§9). The dunning ladder — day 0, 7, 14, 30, then
monthly (Comms §9) — is carried by `Message Dispatched{touch_index}`, which is what `touch_index`
is for.

**`funds` — `shf_*`, new family, 5**

`shf_raised` · `shf_notified` · `shf_cleared` · `shf_squared_off` · `shf_expired`

R7: `shf_cleared` is the completion of record; `shf_squared_off` is the outcome where the firm
acted instead (Rule H8 — *"money moved on the user's behalf is recorded as such"*); `shf_expired`
covers a shortfall that resolves by market movement rather than by anyone acting. `_withdrawn` is
waived — a customer cannot withdraw a shortfall. `_rejected` is waived — nobody adjudicates one.

**There is no `shf_escalated`.** The shortfall escalation ladder — Rules C11–C13, capped at three SMS a day — is a *communications* fact and
rides `Message Dispatched{message_type, touch_index, channel}`. Modelling each rung as a stage
would fire repeatedly against one `context_id`, collapse under the de-duplication key, and split
one fact across two instruments.

**Module boundary, stated rather than discovered.** A shortfall is caused by positions and
resolved against them, but its *destination* is funds: it is shown on the funds view, its message
sends the customer to the funding path, and its 27 communications requirements sit in the FMS
communications file. `module` follows the destination, so it is `funds`. **The square-off orders
are not** — they are `Order State Changed` under `module: orders`, joined to the shortfall by
`related_context_id`, on exactly the pattern of the taxonomy's margin-rejection-recovery read.

### 5.5 `outcome_code` 🔶 — eleven added, `<DOMAIN>_<CONDITION>` per R9

Nine are registered. The six payin outcomes and eight withdrawal outcomes the product can
actually produce — the catalogues `web/app.js` emits as `PAYIN_OUTCOMES`, `SUBMIT_OUTCOMES` and
`EOD_OUTCOMES` — map as follows.

| Product outcome | Code | Status |
|---|---|---|
| Bank declines | `FUNDS_PSP_DECLINED` | registered |
| Not enough in the **bank** | `FUNDS_INSUFFICIENT_BALANCE` | registered — the customer's *bank* balance, reported by the bank. Not ours, so R5 is untouched |
| Above the **bank's** per-payment limit | **`FUNDS_BANK_TXN_LIMIT`** | **new** |
| Above **our** daily route cap | `FUNDS_LIMIT_EXCEEDED` | registered — **ruled** to mean our cap only. Two limits with two owners and two recoveries cannot share one code (R9) |
| No answer from the bank | `FUNDS_TIMEOUT` | registered — lands on `fund_awaiting_confirmation`, **not** `fund_failed`, and **consumes no attempt** (R8) |
| Our service is unreachable | **`FUNDS_GATEWAY_UNREACHABLE`** | **new.** Rule A9c: *"Our outage reported as 'your bank declined' sends the user to a bank that cannot help."* Sharing `FUNDS_PSP_DECLINED` would make that error unmeasurable |
| User backs out before approval | **`FUNDS_USER_CANCELLED`** | **new.** Kept separate because whether it counts as a failure moves the 95% KPI across its threshold — the dashboard makes it a control rather than an assumption |
| Below the ₹100 minimum | `FUNDS_BELOW_MINIMUM` | registered — and **not emitted** where REQ-502's exact-debt exception applies |
| Reversal: source not proven | **`FUNDS_SOURCE_UNPROVEN`** | **new** — Rule A4 |
| Reversal: recalled by the paying bank | **`FUNDS_BANK_RECALL`** | **new** — Rule A10 |
| Reversal: duplicate that escaped Rule A6 | **`FUNDS_DUPLICATE_CREDIT`** | **new** — and its count is a correctness invariant with a target of zero |
| Withdrawal after the cut-off | **`WDL_AFTER_CUTOFF`** | **new** |
| Withdrawal held for review | **`WDL_HELD_FOR_REVIEW`** | **new** |
| Partly sent | **`WDL_PARTIAL_AVAILABLE`** | **new** |
| Nothing available at end of day | **`WDL_NOTHING_AVAILABLE`** | **new** |
| Bank refused the transfer | **`WDL_BANK_RETURNED`** | **renamed** from the registered `WDL_BANK_REJECTED`. It pairs with the stage `wdl_bank_returned` — *the bank* refused — where `wdl_rejected` is **our** refusal. `{stage_name: wdl_rejected, outcome_code: WDL_BANK_REJECTED}` is schema-lawful and self-contradictory, and the per-module table cannot catch it because both are registered to `module: funds`. Never emitted, so the rename is free |
| Banking network unavailable | **`WDL_RAIL_UNAVAILABLE`** | **new** |
| Re-check at sending: withdrawable fell | `FUNDS_EXCEEDS_WITHDRAWABLE` | registered — REQ-308, Rule W10 |
| Re-check at sending: traded today | `WDL_NEW_TRADES_PLACED` | registered |

`error_class` is inherited unchanged, and its mapping matters here: `FUNDS_GATEWAY_UNREACHABLE`
is `technical` and must never be counted against the screen's own conversion, while
`FUNDS_USER_CANCELLED` is `friction` and must.

**The registered `FUNDS_REVERSED` is superseded.** It names the reversal without its cause, which
is the one thing a reversal read needs. FMS carries the event on the stage `fund_reversed` (§5.4)
and the cause on one of `FUNDS_SOURCE_UNPROVEN` · `FUNDS_BANK_RECALL` · `FUNDS_DUPLICATE_CREDIT` —
the three F13 cuts node 1 by. FMS SHALL NOT emit `FUNDS_REVERSED`. It is not reclaimed.

### 5.6 `blocked_reason` 🔶 — five added

Registered: `no_verified_bank` · `account_frozen` · `unsettled_funds` · `negative_balance`.

| New value | Refusal | Authority |
|---|---|---|
| `nothing_withdrawable` | The withdraw control is visible, disabled, and names the responsible deduction | Rules W1, W2, REQ-301 |
| `stale_margin_data` | Both money paths refuse to act on a margin figure they cannot vouch for | REQ-107 |
| `request_in_flight` | A second withdrawal cannot be placed while one is open — the rule that now carries the double-spend load alone | Rule W4 |
| `route_cap_exhausted` | The amount exceeds the route's *remaining daily* headroom and no executable route can carry it | Rule A12, REQ-701 |
| `below_minimum` | Below ₹100 and not the exact settlement of a debt | REQ-703, Rule H3 |

`unsettled_funds` is **superseded**: it names one deduction, where `nothing_withdrawable` names
the refusal and `deduction_reason` (§5.10) names which deduction controlled it. FMS SHALL NOT
emit it. It is not reclaimed.

**One concept, one spelling: `unsettled_credits`.** `unsettled_funds` is the retired spelling of
the same fact and no value is registered under it anywhere else; the live string is the
`deduction_reason` value in §5.10 and every filter, audience and wrapper list uses that one.
Carrying both live would be the `bank_manual` / `bank_manual_entry` trap inside the section that
names it.

### 5.7 `request_type` 🔶 — two added

Registered: `fund_deposit` · `fund_withdrawal` · `running_account_settlement`.
Added: **`dues_settlement`** · **`margin_shortfall`**.

Both carry `context_type: service_request`, which the taxonomy defined as serving *"every tracked
request... because `request_type` already carries the distinction."* A due and a shortfall strain
the word *request* — nobody asked for either — but the alternative was a thirteenth
`context_type`, and
`initiated_by: system` already marks them as things that happened to the customer rather than
things they did. The strain is recorded rather than hidden.

### 5.8 Interaction ids — the frozen layer carries FMS's central metric

`Overlay Opened` / `Overlay Dismissed` — `overlay_id`:
`funds_withdrawable_derivation` · `funds_margin_breakdown` · `funds_deployability` ·
`funds_route_list` · `funds_payin_outcome` · `funds_withdraw_confirm`

`Element Clicked` — `item_group` adds two values, **both wrapper-validated** on the
`answer_helpfulness` precedent:

| `item_group` | `item_value` validated against | Carries |
|---|---|---|
| `deduction_line` | the `deduction_reason` registry (§5.10) | Which line of the withdrawable derivation the user expanded — REQ-102, the module's central bet |
| `margin_component` | the same registry | Which component of available margin was expanded — REQ-103 |

This is the reuse that matters most: **the derivation's per-line engagement rides `item_value` on
a frozen event and costs zero properties**, exactly as `answer_id` does for the help centre.

`element_id` — engineering-owned; the funnels in §7 depend on these:
`funds_empty_primary` · `funds_blocker_action` · `funds_add_cta` · `funds_withdraw_cta` ·
`funds_amount_suggestion` · `funds_route_change` · `funds_pay_exact_dues` ·
`funds_shortfall_add` · `funds_derivation_open` · `funds_retry` · `funds_retry_alt_route` ·
`funds_cancel_withdrawal` · `funds_statement_export` · `funds_txn_filter`.

### 5.9 Comms, vendor and retrieval values

| Property | Added | Why |
|---|---|---|
| `channel` | **`sms`** | D1 |
| `service_id` | **`psp_payout`** · **`margin_front_office`** · **`margin_back_office`** | D2, and EB-2's dual margin source: front office in market hours, TechExcel outside. REQ-107's staleness is a property of *which* source answered and how long ago |
| `reveal_group` | **`available_margin`** · **`withdrawable`** | D3 |
| `query_scope` 🔶 | **`transaction_search`** | REQ-403. `Query Answered` already carries `result_count`, and **`result_count: 0` is the empty-result signal the taxonomy explicitly defined as "not a validation failure"** — which is exactly REQ-403's *empty result → widen the window* case |
| `message_type` | The five SMS templates lowercased, plus the payin, payout, dues and shortfall email set generated from `web/app.js` | Open registry, seeded on the KYC precedent |
| `report_type` | none — `ledger` and `statement_of_accounts` are registered | REQ-407 |
| `delivery_method` · `file_type` | none — `download` and `csv` | REQ-407 ships CSV only |
| `funding_method` · `leg` · `cap_type` · `otp_purpose` · `account_state` | **none** | All sufficient |
| `amount_source` | **none — adopted as registered** | The authority §5.3 registers it to `module: funds` on `Request Stage Changed` at `chip` · `typed` · `prefilled`, and FMS emits it there unchanged. It stays on the record transition: the pill *tap* is `Element Clicked{element_id: funds_amount_suggestion, item_value}` (§8 row 4), which is a different event and a different grain, and REQ-201's anti-anchoring exclusion needs both |
| `source_bank_ref` | **none — adopted as registered** | Also registered to `module: funds` on `Request Stage Changed`: the internal account id (`b1`, `b2`), **never** the account number and never the IFSC. It is what fixes a withdrawal's destination at request rather than at settlement, which F5's Rule W4b boundary depends on, and Rule A1's *"the account it was last added from"* reads it against the profile's `last_deposit_method` |

**The build's route ids are not `funding_method` values.** `web/app.js`'s `ROUTES` publishes three
ids against a registry of eight, and an emitter written from the build would mint a third
vocabulary. The map, once:

| `ROUTES` id | Label in the build | `funding_method` |
|---|---|---|
| `upi` | UPI | `upi_collect` — FMS issues the collect request, and the call behind it is `service_id: psp_collect`. `upi_intent` is registered and has **no build route today** |
| `nb` | Net banking | `netbanking` |
| `neft` | Bank transfer (NEFT / IMPS) | **`neft` or `imps`** — one build route carrying two registered values |

The third row is the only ambiguity, and it is a build gap rather than a registry one: the route
is `selfService: true`, FMS does not execute it, and the value emitted is whichever rail the
credit actually arrives on. Until the build splits the option or records that IMPS is not offered,
nothing at the call site can tell them apart — so **every filter written against that route is
`funding_method in (neft, imps)`, never `neft` alone** (F3, §10).

### 5.10 Five property claims to the registrar (R6)

A new property on an existing name is a claim, not an addition. Six are made, in descending
order of strength. The last two are **scope widenings** of properties already registered
elsewhere, not new names, and they are declared here because §4 asserted them in prose and no
claim carried them.

**1. `deduction_reason` — closed, 10 values. On `Action Blocked`, and as the validated
`item_value` for `item_group: deduction_line` / `margin_component`.**

`unsettled_credits` · `unposted_charges` · `open_order_blocks` · `margin_utilised` ·
`collateral_not_cash` · `option_premium` · `unrealised_loss` · `payout_in_flight` ·
**`debit_balance`** · `mandated_settlement_pending`

**`debit_balance`, not `negative_balance`.** `blocked_reason: negative_balance` is already
registered for funds and rides the **same event** (§5.6, F11 node 2). One string meaning two things
on one event is unfilterable, and the wrapper cannot catch it because both are lawful there.

*Why it cannot ride an existing property.* The refusal is `blocked_reason:
nothing_withdrawable`; *which* deduction caused it is a second axis. Folding them
(`nothing_withdrawable_unsettled`, `nothing_withdrawable_charges`, …) multiplies one enum by
another and violates one-fact-one-place.

*Why it earns a property.* It is the module's thesis. The PRD's single engagement KPI is
*"40% of users who view a withdrawable figure lower than their balance open its derivation"*,
its quality KPI is *"under 1 support contact per 1,000 movements relating to why my balance
differs from what I can withdraw"*, and the Executive Summary names this gap as *"the dominant
source of user distrust in the category."* Without this property the funnel can say the
explanation was opened and can never say **what it was explaining** — so it can never say which
deduction to design away. **It carries a category, never an amount**, so R5 is untouched.

*Second consumer.* `portfolio` — *sellable vs holdings* is the same shape (a figure lower than
the headline, with a controlling reason), and `blocked_reason: not_sellable_t1` · `pledged` ·
`blocked_against_order` is that module already naming the same three classes on a refusal event.

**2. ~~`settlement_run_index`~~ — WITHDRAWN. The authority registered `transition_index` instead,
and it is the better property.**

*The claim, as filed.* The published de-duplication key for `Request Stage Changed` was
`context_id + event_name + stage_name`. `wdl_rail_queued` legitimately repeats: a rail outage
lasting three nights produces three genuine transitions that the key silently collapses into one.
This is **identical in kind to the case §3 of the taxonomy already fixed** — two consecutive
`PAN_VENDOR_TIMEOUT` failures at the same `attempt_index` collapsing under a naive key — and it was
fixed there by adding `vendor_attempt` as a discriminator. Same defect, same remedy. A drop looks
like a conversion cliff, not like a bug.

*What was registered.* THINQ-EVENTS-001 v1.1.0 §5.3 registers **`transition_index`** — *"Integer,
**server-assigned and monotonic per `context_id`**, 1 = first transition. Never client-set, never
reset"* — scoped to **`Request Stage Changed` and `Order State Changed` both**, and carries it into
§3's key for `Order State Changed`. The registrar granted the discriminator at product scope rather
than the funds-scoped one FMS asked for, which is the right call and the one THINQ-EVENTS-AUDIT-001
§10 recommended: the same defect exists on `Order State Changed`, where no per-family alternative
was available, and `ipo`, `support`, `profile` and `reports` all need the same read. A funds-only
property would have been re-minted four more times.

*Consequence for this document.* FMS registers **no** property here. The `wdl_*` and `rac_*` keys in
§9 take `transition_index`, and the payout-run number — if the business ever wants the ordinal of the
settlement run itself rather than of the transition — is a separate integer nobody has yet asked for.

**3. `arrival_variance_sec` — signed integer. On `Request Stage Changed{stage_name: wdl_credited}`.**

Seconds between the arrival time quoted at request (REQ-303) and the arrival that happened.
Negative is early.

*Why it cannot be derived.* The quote is computed from the account's own state — route, cut-off,
trading calendar, whether the user traded today, whether an order is outstanding — and it exists
nowhere in the event stream. `seconds_in_request` gives elapsed time against no promise.

*Why it earns a property.* This is the module's one **invariant** metric rather than a trend: the
dashboard sets the bar at 100% and states why — *"it reads below the moment one payout is late,
and the answer is a faster rail or a more honest quote, never a lower bar."* A boolean would meet
the invariant and lose the distribution the Speed tab needs to tell a bad rail from a bad quote.

**4. `amount_is_max` — boolean. On `Journey Step Completed{step_name: withdraw}`.** *(weakest claim)*

Whether the user asked for the whole withdrawable figure.

*Why.* Rule W3 is the riskiest design decision in the PRD — the amount can shrink between request
and payout, and Rule W3a says the whole design rests on telling the user so before they commit.
A user who asked for the maximum is the population most likely to be shrunk, and separating them
is how *"each partial transfer is a complaint"* gets measured rather than assumed. It leaks
nothing: a boolean about the user's own instruction reconstructs no balance.

*Named consumer: one.* If the registrar wants only five claims, this is the one to drop; the
question then survives as a support-topic read.

**5. `funding_method` — scope widening onto `Journey Step Completed` / `Journey Step Failed`.**

*What it is.* The authority §5.3 registers `funding_method` on `Request Stage Changed` for `funds`
and `ipo`. FMS emits it on the spine as well, and has done so in prose since §4: the route is a
fact about an **attempt**, §8 row 7 reads route change from `attempt_index` + `funding_method`, and
F3 node 4 and the whole of F4 are spine filters.

*Why it is a widening and not a duplication.* The fact is one — which rail this money took — and
one property carries it on both instruments. The alternative is four new `method` values, which is
the one-fact-two-places failure R1 exists to stop (§4). *Second consumer:* `ipo`, already named on
the registered scope and with the identical submit-then-settle split — the bid is an attempt, the
mandate and the allotment are record transitions. *Precedent:* `state_intact`, widened across two
events and recorded **in the registry** rather than asserted in a module's prose.

**6. `amount_paise` — the same widening, on the same two events.**

*What it is.* Registered on `Request Stage Changed` as integer paise (§5.3). §8 row 10 sends it on
`Journey Step Completed{step_name: withdraw}` — what the user **asked for**, at the moment they
asked — which is a different fact from what settled hours later under Rule W3, and cannot be read
from a record transition that has not happened yet.

*Why it is lawful.* R5 is untouched: an amount instructed to move is a movement, not a holding
(§6.1). *Second consumer:* `ipo`, on the same reading. *Precedent:* as claim 5.

**Both are declared rather than assumed, and both have a cheaper alternative.** The registrar may
instead refuse the widening and require the withdrawal amount and route to be read from
`Request Stage Changed{stage_name: wdl_requested}`, where both are already lawful. That costs F4,
which is a spine funnel with no record transition to read, and it costs the amount-at-request
against amount-at-settlement comparison Rule W3a rests on.

### 5.11 Profile properties — six, now carried by the authority §5.9

Campaigns qualify on the profile, not on the event stream: *"an event stream cannot be queried
inside the 60-second real-time window the frequency cap demands."* All 27 communications
requirements need audience qualification.

**This is a pointer, not a registration.** The authority §5.9 was rebuilt as a 36-row table with
**Values**, **Type**, **Written by** and **Recompute cadence** columns, and the register grew from
30 to 36 by absorbing exactly these six. `funds_state`, `dues_state`, `shortfall_state`,
`first_deposit_at`, `last_deposit_method` and `deposits_90d` are registered **there**, and the
authority governs their vocabulary, their writer and their cadence. What follows is only what this
module needs them **for**, and the one derivation rule that table cites back to this section.

**Four of the six are expressible; two are not, and both are ours to close.** The authority §5.9
marks `funds_state` and `deposits_90d` **OPEN — FMS registration owner**: `funds_state` because
this section said only *"stamped server-side"* and named no event and no job, `deposits_90d`
because the increment is named nowhere and the decay is a profile-store window. The other four carry a
writer there — `dues_state` and `shortfall_state` off their `due_*` and `shf_*` stage
transitions, `first_deposit_at` and `last_deposit_method` off `fund_credited` — and their
audiences are authorable now. Only the audiences that qualify on the two OPEN rows are still
unwritable, which is the narrow form of what this section used to claim of all six.

| Property | Values (as the authority §5.9 registers them) | Serves |
|---|---|---|
| `funds_state` | `blocked` · `empty` · `funded` · `debit` · `shortfall` | The `screen_name` set as a *state*, stamped server-side. F1, F2, F11 |
| `first_deposit_at` | DATE, never a string | The 7-day first-deposit KPI, which is a cohort read and not an event funnel (§7) |
| `last_deposit_method` | a `funding_method` value | Route-aware recovery copy; Rule A1's *"the account it was last added from"* |
| `deposits_90d` | integer **count**, never an amount | The 2-per-quarter engagement KPI. A count is not a balance |
| `dues_state` | `none` · `outstanding` · `cleared_30d` | The dunning ladder audience — day 0, 7, 14, 30, monthly |
| `shortfall_state` | `none` · `open` · `cleared` · `squared_off` | The shortfall escalation ladder and its regulatory-bypass audience — **Rules C11, C12 and C13** (quiet-hours exemption, the three-SMS cap, preferences do not suppress) and Comms §5's channel matrix, *ladder of three*. **Not REQ-601–604**: those ids are declared in the comms PRD's header and have no text in its body |

**`funds_state` is `screen_name` with the `funds_home_` prefix stripped, and that is the rule.**
Every member is the suffix of exactly one `funds_home_*` value in §5.1, and a sixth funds home
would add its state under the same rule and no other. `in_debit` is corrected to **`debit`** for
that reason — it was the one member of five that did not strip, and a dunning audience written
against either spelling returns zero rows with no error. §13 test 11 asserts the correspondence.
The build's third spelling (`web/app.js` `key: 'debt'`) is a build defect against this rule and is
fixed there, not here.

`open_request_types` is registered as a **set** and is reused unchanged: it is what Rule W4's
one-open-request refusal and Configuration Rule G4's *"an account cannot be deleted while a
withdrawal to it is open"* both read.

### 5.12 The count

| | Registers |
|---|---|
| **New event names** | **0** |
| New properties (claims to the registrar) | **5** — two of them scope widenings of properties the authority §5.3 already registers, one marked droppable. A sixth, `settlement_run_index`, is **withdrawn**: the authority registered `transition_index` at product scope instead (§5.10 claim 2) |
| New `context_type` | 1 claim — **granted.** `account` is registered (authority §2 row 4, now 14 closed), and FMS-OD-1 is closed with it |
| New profile properties | **0** — the six are already carried by the authority §5.9, which registers 36. §5.11 is a pointer, not a claim |
| Value additions (free under R6) | `screen_name` 9 · `stage_name` 17 · `outcome_code` 11 · `blocked_reason` 5 · `overlay_id` 6 · `element_id` 14 · `request_type` 2 · `service_id` 3 · `reveal_group` 2 · `item_group` 2 · `sub_module` 1 · `query_scope` 1 · `channel` 1 — **74** |
| **Product-wide name budget after FMS** | **40 spent, 472 remaining — unchanged** |

Three of those value additions are the D1–D3 defect fixes and would have been needed by whichever
module arrived next, and all three are now registered (§3).

**43 of the 74 are carried by the authority v1.1.0** — `channel` 1 · `stage_name` 17 ·
`outcome_code` 11 · `blocked_reason` 5 · `request_type` 2 · `service_id` 3 · `reveal_group` 2 ·
`sub_module` 1 · `query_scope` 1. The other **31** are the four open, engineering-owned id
registries — `screen_name` 9 · `element_id` 14 · `overlay_id` 6 · `item_group` 2 — which every
module registers for itself and which the authority holds under **OD-10** as *"empty or
unwritten"*. They are not blocked and not landed; they are FMS's to populate before each surface
ships, and an unregistered id is an unqueryable funnel.

---

## 6. The money rule — and the PRD open question it closes

> **Open Question, product-requirements.md:** *"May balance figures be sent to CleverTap at all?
> The non-functional requirements forbid disclosing balances and account identifiers to third
> parties; the tracking table sends them as event properties. Both cannot be true."*

**Answer: no, and the taxonomy decided this before FMS asked.** R5 is unambiguous —
*"What a thing **cost** is product data; what the customer **holds** is never sent. This is a
deliberate blind spot, not an oversight: withdrawable-vs-total balance and available-margin-
vs-balance are permanently unanswerable from product events."* The taxonomy names FMS's own
central figures as the example of what it will not carry.

The two are therefore consistent, and the FMS NFR wins. **The tracking table is the defect.**

### 6.1 The line, drawn once

| Lawful | Unlawful |
|---|---|
| `amount_paise` — what **moved**. A deposit amount, a withdrawal amount, a mandated return, a charge. Integer paise, never a float | Any **balance** — ledger, available margin, withdrawable, collateral, blocked margin |
| `charge_paise` — what a thing cost | The shortfall **amount**, the amount **owed** |
| `deduction_reason` — **which** deduction controls the gap | The **size** of any deduction |
| `funds_state`, `dues_state`, `shortfall_state` — the **shape** of the account | Any figure from which a balance can be reconstructed |
| `deposits_90d` — a count | Aggregates of held money in any form |
| `amount_is_max` — whether the instruction was for everything available | The everything-available figure itself |

An amount that moved is a fact about a transaction the customer initiated and already knows.
A balance is a standing fact about their wealth. The first is product data; the second is not
ours to hand to a processor.

### 6.2 What the eight forbidden rows cost, and what replaces them

The PRD's tracking table sends balances on eight of its twenty-one rows. Seven lose the figure
and keep the question; the eighth was never forbidden.

| Row wanted | Cannot send | Carries the fact instead |
|---|---|---|
| Funds view opened → *balances shown* | the three figures | `screen_name` (five states) + `funds_state` on the profile |
| Withdrawable derivation opened → *balance, withdrawable, largest deduction* | two figures | `Overlay Opened{overlay_id: funds_withdrawable_derivation}` + `deduction_reason` on the refusal + `item_value` per expanded line |
| Withdrawal attempted while nothing withdrawable → *balance, controlling deduction* | the balance | `Action Blocked{blocked_reason: nothing_withdrawable, deduction_reason: X}` |
| Withdrawal requested → *withdrawable at request* | the figure | `amount_paise` (what was asked) + `amount_is_max` (whether that was everything) |
| Margin shortfall shown → *shortfall amount* | the amount | `shf_*` stage + `related_context_id` to the square-off orders + time-to-deadline via `seconds_in_request` |
| Debit balance entered → *amount* | the amount | `due_raised` + `outcome_code` for the cause + `related_context_id` to the reversal or charge |
| Debit balance cleared → *amount* | the amount | `due_cleared` + `amount_paise` on the **clearing deposit**, which is a movement and therefore lawful |
| Mandated settlement executed → *amount returned* | **nothing** | `rac_credited` + `amount_paise`. A mandated return is a **movement**, lawful under §6.1 row 1 — this row was miscounted as forbidden, and the failure direction is over-suppression: an emitter that drops it costs F9's float-retention KPI its numerator |

**Seven genuinely lost figures, and one that was never a problem.** The recovery in every case is
the same move: send the *category* and the *movement*, never the *holding*.

### 6.3 Two rows that leave the taxonomy entirely

**Ledger integrity check** — *"period, result, discrepancy if any"*. This is a system assertion
about our own correctness, fires whether or not a customer exists, and has no `context_id` in any
registered `context_type`. It belongs in engineering observability and alerting. A correctness
invariant with a target of zero is not a funnel; sending it to a customer-engagement platform
puts a compliance signal somewhere no one is on call for it.

**Support contact linked to a money movement** — this is `Request Stage Changed{module: support,
request_type: support_ticket, related_context_id: <the fund_txn>}`, already registered by support.
FMS registers nothing and reads it through the join. It is the taxonomy's fourth cross-module read
applied to funds, and it is the **only available proxy** for the withdrawable-vs-total question
R5 permanently closes — which is why §5.10's first claim matters and why `answer_id` accuracy in
support matters to this module.

### 6.4 One consequence of an unresolved PRD decision

> **Open Question:** *"Which system orchestrates outbound communications — CleverTap Journeys
> triggered by FMS events, or an FMS scheduler with CleverTap as delivery only."*

The instrumentation contract is **the same under both** — `Message Dispatched` is emitted at
dispatch evaluation, including every suppression, by whichever system evaluates. So this question
does not block instrumentation, only orchestration. **It does change one thing:** if CleverTap
renders the copy, every interpolated fact — the UTR, the last four digits, the amount, the
deadline — must travel as an event property to be available at render time, and several of those
are figures §6.1 forbids.

**Recommendation: FMS renders, CleverTap delivers.** R2 already requires that the *template*
travels and the interpolated string does not, and this is the reading that keeps §6.1 and the NFR
intact. Choosing the other way reopens the balance question for the comms path specifically, and
should be costed as such rather than discovered during build.

---

## 7. The funnels

Every one obeys the three registered rules. **Three nodes, never two** — the middle node
separates *never tried* from *tried and failed*. **Every step is a filter**, never a name.
**Keyed on `context_id`**, with the two exceptions marked and justified.

`web/dashboard.js` already draws F3, F5 and F6 against a synthetic population. Those three are
written to match its `metrics()` definitions exactly, so the prototype's numbers and production's
numbers are the same query.

---

### F1 — First deposit *(cohort read, not an event funnel)*

**Question.** Do newly funded-capable accounts actually fund? **KPI: 80% within 7 days.**

This one spans four records — a KYC case, a bank-add request, an account, a fund transaction —
and no `context_id` joins them. It is a **profile cohort read**, and saying so is the point:
forcing it into an event funnel is what produces a funnel keyed on identity, which the taxonomy
forbids for good reason.

| | Definition |
|---|---|
| Cohort | Profile where `ptt_at` is set **and** `banks_linked ≥ 1` — the day the account could first receive money |
| Converted | `first_deposit_at` − that day ≤ 7 |
| Cut by | `journey_variant` · `platform` · `last_deposit_method` · `entry_source` |
| Watch | Accounts where `funds_state = blocked` on day 7 are **not** failures of this metric; they are F11's population and must be excluded or the number measures the Bank module |

---

### F2 — The zero-state doorway

**Question.** Does an empty account convert into an action? **KPI: 60% take the next action offered.**

| Node | Filter |
|---|---|
| 1 | `Screen Viewed{screen_name: funds_home_empty}` |
| 2 | `Element Clicked{element_id: funds_empty_primary}` |
| 3 | `Journey Step Completed{step_name: add}` |

**Keyed on `session_id`, and this is the one place that is correct.** No `fund_txn` exists until a
payment is committed, so there is no record to key on. Node 3 is where a `context_id` first
exists, and from there F3 takes over.

*Close on `entry_direction = forward`.* Rule H5 exists because a live competitor rendered fifteen
instances of ₹0.00 with no acknowledgement the account was empty; the drop from node 1 to node 2
is the direct measurement of whether that was fixed.

---

### F3 — The payin funnel

**Question.** Of the people who open funds meaning to add money, how many end up with usable
margin? **KPIs: 95% first-try success; 30s to margin at p95.**

| Node | Filter | Source |
|---|---|---|
| 1 | `Screen Viewed{screen_name: funds_home_*}` | client |
| 2 | `Screen Viewed{screen_name: funds_add}` — the panel opened and an amount entered | client |
| 3 | `Overlay Opened{overlay_id: funds_route_list}` — **REQ-202's disclosure, proven to have happened before commitment** | client |
| 4 | `Journey Step Completed{step_name: add}` — a payment instruction FMS issued. **The first node FMS can see for itself** | server |
| 5 | `Request Stage Changed{request_type: fund_deposit, stage_name: fund_credited}` — once per record, however many attempts | server |
| 6 | **Not a filter — a latency annotation on node 5.** Same population, same count; report the **median commit-to-credit interval** (node 4 to node 5), never a rate | server |

**Node 3 is a proof, not a drop.** It exists because REQ-202 is a disclosure obligation and the
funnel is the evidence it was met.

**Node 6 carries no stage filter, and specifically not `fund_approved`.** That value is PSP
approval *before* credit (§5.4) and cannot also mean margin availability; margin is simultaneous
with `fund_credited`, so the sixth row is node 5's population with a duration beside it. Under §9's
own key — `context_id + event_name + stage_name`, each `fund_*` stage firing once per record — a
`fund_approved` placed *after* `fund_credited` is a second row on a key that permits one, and it
collapses. `dashboard.js` already renders it correctly: its *Usable as margin* step counts
`intents.filter(i => i.credited).length`, identical to *Money credited*, with the median delay as
a note. The 30s-to-margin p95 is that interval and needs no stage of its own.

**What this funnel deliberately excludes.** `funding_method in (neft, imps)` is self-service —
the build offers both under one route id (§5.9) — and the user leaves for their own banking app,
so FMS never sees the attempt, only sometimes the credit. Excluding `neft` alone silently readmits
every IMPS transfer to the denominator. Those movements are **excluded from every rate rather than
guessed at**, and counted where they can be seen — `fund_credited` with no preceding
`Journey Step Completed`. A funnel that includes them cannot tell a user who changed their mind
from one whose transfer went astray.

**The 95% needs its definition chosen, not assumed.** *Attempt* is undefined in the PRD and the
three defensible readings land on either side of the threshold:

- attempts = `Journey Step Completed{step_name: add}` at `attempt_index: 1`, succeeded = reached `fund_credited`
- attempts = every `attempt_index`, succeeded = every credit
- as above, with `outcome_code: FUNDS_USER_CANCELLED` excluded from the denominator

The third is the recommendation — a user changing their mind is not a reliability failure — and
`FUNDS_USER_CANCELLED` exists as its own code precisely so the choice is a filter rather than a
rewrite. **State which reading the launch threshold uses before the first release, or the release
passes under one reading and fails under another.**

---

### F4 — Payin failure recovery

**Question.** When a deposit fails, does REQ-205's guidance actually recover the user?

| Node | Filter |
|---|---|
| 1 | `Journey Step Failed{step_name: add}` — cut by `outcome_code` and `error_class` |
| 2 | `Element Clicked{element_id: funds_retry}` **or** `{element_id: funds_retry_alt_route}` |
| 3 | `Journey Step Completed{step_name: add, attempt_index: n+1}` |
| 4 | `Request Stage Changed{stage_name: fund_credited}` |

Keyed on `context_id`. **The route change is computed, not sent** — `funding_method` differing
across `attempt_index` is the fact (R1).

**Three reads this gives that no single rate does.** Recovery rate **per `outcome_code`** — Rule
A9a says six failures have six different answers, and this is where that claim is tested. Whether
`funds_retry_alt_route` beats `funds_retry`, which is Rule A9d's *"a recovery action must be able
to work"* under measurement. And `FUNDS_TIMEOUT`, where the correct behaviour is **not** to
retry: a high retry rate on that code means Rule A9b's copy is failing, and it will read as
recovery under any naive success metric.

---

### F5 — The withdrawal funnel

**Question.** Of funded users who ask for money, how many get it, in full, by the time we said?

| Node | Filter | Source |
|---|---|---|
| 1 | `Screen Viewed{screen_name: funds_home_funded}` | client |
| 2 | `Journey Step Completed{step_name: withdraw}` — the request FMS accepted, user present | server |
| 3 | `Request Stage Changed{stage_name: wdl_bank_sent}` | server (`platform: system`) |
| 4 | `Request Stage Changed{stage_name: wdl_credited}` | server |
| 5 | Node 4 filtered `arrival_variance_sec ≤ 0` — **arrived by the time we quoted** | server |

**The Rule W4b boundary sits between nodes 2 and 3**, and it is the most important line in this
funnel: everything above it had a user in front of it, everything below it happened hours later
with nobody watching. A drop across that boundary is never a UX finding.

**Node 3's losses are named, not aggregated** — `wdl_part_sent`, `wdl_nil_settled`,
`wdl_bank_returned`, `wdl_rejected`, `wdl_cancelled` are five different populations with five
different owners (§5.4), and merging them is the reporting failure D4 was raised to prevent.

**Node 4 is not a failure step.** Money that left but has not landed is **undecided**; folding it
into node 5 reports it as late when it may still be early.

**Node 5 is an invariant at 100%, not a trend.** It reads below the moment one payout is late, and
the answer is a faster rail or a more honest quote — never a lower bar. Same family as the PRD's
target-zero correctness metrics.

**`wdl_part_sent` needs its own read beside the funnel**, cut by `amount_is_max`. Rule W3a bets
the whole design on one sentence shown before commitment; the partial-transfer support-contact
rate against that population is whether the bet paid.

---

### F6 — The dead end beside the withdrawal funnel

**Question.** When someone taps withdraw and finds nothing withdrawable, does the explanation
land? **KPI: 40% open the derivation.**

**This is not a funnel step.** Putting it inline would draw a funnel that *grows* — the users who
go on to request a withdrawal are mostly not these users.

| Node | Filter |
|---|---|
| 1 | `Action Blocked{blocked_reason: nothing_withdrawable, element_id: funds_withdraw_cta, was_journey_entry: true}` — cut by **`deduction_reason`** |
| 2 | `Overlay Opened{overlay_id: funds_withdrawable_derivation}` |
| 3 | `Element Clicked{item_group: deduction_line}` — cut by `item_value` |
| 4 | Later: `Journey Step Completed{step_name: withdraw}` within 7 days |

**Node 1 cut by `deduction_reason` is the highest-value number in this document.** It ranks the
reasons users cannot withdraw, which is the input to designing them away rather than explaining
them better. Node 3 says which lines of the explanation people actually read — the same signal
`answer_id` gives the help centre, on the same frozen event.

**One design decision this funnel depends on.** The 40% KPI is worded *"users who view a
withdrawable figure lower than their balance"*. The derivation affordance should be **rendered
only when a gap exists** — then `funds_home_funded` is a clean denominator and no `screen_name`
split is needed. If it renders unconditionally, the denominator silently includes users with
nothing to explain and the metric reads low forever. Splitting `screen_name` into gap and no-gap
variants would be lawful (the rendered controls differ) but is the worse answer: a gap is
near-universal on a funded trading account, so the split buys a value to carry a rounding error.

---

### F7 — Margin shortfall

**Question.** Does REQ-506 let users fix a shortfall themselves before the firm does it for them?

| Node | Filter |
|---|---|
| 1 | `Request Stage Changed{request_type: margin_shortfall, stage_name: shf_raised, initiated_by: system}` |
| 2 | `Message Dispatched{message_type: thinq_margin_shortfall, channel: sms}` — cut by `touch_index` for the escalation ladder (Rules C11–C13) |
| 3 | `Notification Deep Link Opened` **or** `Screen Viewed{screen_name: funds_home_shortfall}` |
| 4 | `Request Stage Changed{stage_name: fund_credited, related_context_id: <the shortfall>}` |
| 5a | `shf_cleared` — the user fixed it |
| 5b | `shf_squared_off` — the firm did, and the orders are `Order State Changed` under `module: orders`, joined by `related_context_id` |

**5a against 5b is the whole metric**, and it is the clearest cross-module read FMS owns: the
funds side and the orders side of the same event, joined on a record rather than a session.

Node 2's `dispatch_outcome` must read `sent` inside quiet hours — the regulatory bypass. A
`suppressed_quiet_hours` on `thinq_margin_shortfall` is a compliance failure, and this funnel is
where it becomes visible. Node 3 measures REQ-605's deep link carrying the exact amount into the
funding surface.

---

### F8 — Dues

**Question.** Do users clear a debt they did not create? **KPI: 60% within 14 days of notification.**

| Node | Filter |
|---|---|
| 1 | `Request Stage Changed{request_type: dues_settlement, stage_name: due_raised, initiated_by: system}` — cut by `outcome_code` for the cause |
| 2 | `due_notified` + `Message Dispatched{message_type: thinq_dues_outstanding}` — the ladder via `touch_index` at day 0, 7, 14, 30 |
| 3 | `Screen Viewed{screen_name: funds_home_debit}` |
| 4 | `Element Clicked{element_id: funds_pay_exact_dues}` — **REQ-502's minimum waiver under measurement** |
| 5 | `due_cleared` |

**Node 1 to node 2 is a correctness invariant with a target of zero**, not a conversion rate:
*"zero accounts that reach a debit balance without having been told."* Any `due_raised` with no
`due_notified` inside the notification window is a defect, and Rule H2 is explicit that display
alone is not disclosure for a user who has no reason to log in.

**Node 4 is where the ₹100 minimum is proven not to trap anyone.** A user who cannot pay ₹24.37
because the floor is ₹50 must overpay or stay in debt, and both are the product's failure. A
`Field Errored{error_code: FUNDS_BELOW_MINIMUM}` on the dues screen is that failure firing.

---

### F9 — Money that leaves without being asked

**Question.** Is an unrequested outflow understood, or does it read as an error or a theft?

| Node | Filter |
|---|---|
| 1 | `Message Dispatched{message_type: thinq_rac_advance_notice}` — Rule W8's announcement, **three working days before** the date, cut by `channel`. The message now exists: `product-requirements-communications.md` §4.5 (*Mandated settlement — announced before, notified after*) generates it as the WhatsApp template **`thinq_rac_advance_notice_v1`**, with **email as the fallback where the user has no WhatsApp opt-in** (Rule C4); §5's channel matrix carries the Mandated settlement row, and §1's digest counts the announcement among the WhatsApp templates needed. Rule C10 still governs the copy. The `_v1` is the template version REQ-625 mandates, not the registry value — the funnel filters the stem, or it goes dark the day the copy changes |
| 2 | `Request Stage Changed{request_type: running_account_settlement, stage_name: rac_credited, initiated_by: system}` + `amount_paise` |
| 3 | `Screen Viewed{screen_name: funds_transactions}` within 72 hours |
| 4 | **Did NOT** `Request Stage Changed{module: support, request_type: support_ticket, related_context_id: <the rac record>}` |

**Node 4 is a negative step and that is the design.** The metric is a support contact that did not
happen. `initiated_by: system` is what separates this outflow from a withdrawal the user forgot
making — without it they are one undifferentiated number, which is exactly Rule W4d's concern.

**The float-retention KPI does not depend on node 1, and every node is now computable.** 70% of
deposited funds surviving to the next settlement date is `amount_paise` on `rac_credited` at node
2 against `amount_paise` on the `fund_credited` records inside that window — a window that
**opens at the previous mandated settlement date** and closes at this one, because money deposited
before that date has already been through a settlement and belongs to the prior cycle's
denominator, not this one's. Rule W8 has two halves, announce before and notify after, and the
comms PRD now specifies both: the announcement on WhatsApp three working days out with email
behind it, the return itself by email on the date. So the funnel can finally answer the question
it was built for — whether the announcement is what makes the outflow understood — by reading
node 1 against nodes 3 and 4, and the `channel` cut answers it separately for the opted-in
population and for the email fallback.

---

### F10 — Finding and keeping the record

**Question.** Does the ledger answer questions in-product, or is it escaped from?

| Node | Filter |
|---|---|
| 1 | `Screen Viewed{screen_name: funds_transactions}` |
| 2 | `Query Answered{query_scope: transaction_search}` — cut by `result_count`, where **`result_count: 0` is the empty-result signal and not a failure** |
| 3 | `Screen Viewed{screen_name: funds_transaction_detail}` |
| 4 | `Document Retrieved{report_type: ledger, delivery_method: download, file_type: csv}` — cut by `period_preset` |

Node 2 validates REQ-403 **before** its second phase is built, which is what the PRD asks of it.
The zero-result rate cut by `period_preset` is the *widen the window* case, and it is the argument
for the 30-day default: a 7-day default routinely hides the transaction users most often query.

Node 4 is a **two-sided** read. A high export rate against a low node-3 rate means users are
escaping to a spreadsheet to answer questions the product should answer — which is the case for
the reconciliation view now owned by the Ledger. It also makes the registered profile property
`docs_downloaded_30d` computable for the first time.

---

### F11 — The blocked account

**Question.** Does REQ-505 turn a dead end into a path?

| Node | Filter |
|---|---|
| 1 | `Screen Viewed{screen_name: funds_home_blocked}` |
| 2 | `Action Blocked{blocked_reason: no_verified_bank \| account_frozen \| negative_balance}` |
| 3 | `Element Clicked{element_id: funds_blocker_action}` |
| 4 | `Request Stage Changed{module: profile, request_type: bank_add, stage_name: bank_verified}` — **the blocker cleared, in another module** |
| 5 | `Request Stage Changed{module: funds, stage_name: fund_credited}` |

Nodes 3 to 4 cross the FMS/Profile boundary, which is the point: Configuration §3 moved bank
account management to Profile, so REQ-706a's *"name the blocker and link to Profile"* can only be
proven end to end by a funnel that spans both. The `bank_*` stage family Profile registered is
what makes node 4 exist.

---

### F12 — Margin-rejection recovery *(FMS is the middle of the taxonomy's most valuable read)*

| Node | Filter |
|---|---|
| 1 | `Order State Changed{module: orders, order_state: rejected, outcome_code: ORD_INSUFFICIENT_FUNDS}` |
| 2 | `Request Stage Changed{module: funds, request_type: fund_deposit, related_context_id: <the order>}` |
| 3 | `Request Stage Changed{stage_name: fund_credited}` |
| 4 | `Order State Changed{order_state: executed}` |

**Joined on `related_context_id`, never `session_id`** — the customer leaves for their bank app
and returns tomorrow on a deep link, which is a new session and the same records.

**FMS's obligation is one line of instrumentation**: stamp `related_context_id` with the rejected
order's id when the funding path is entered from an order rejection. The 30-second
payin-to-margin NFR is set by this funnel's clock, not by user patience.

---

### F13 — Reversal into debt and out again

| Node | Filter |
|---|---|
| 1 | `Request Stage Changed{stage_name: fund_reversed}` — cut by `outcome_code`: `FUNDS_SOURCE_UNPROVEN` · `FUNDS_BANK_RECALL` · `FUNDS_DUPLICATE_CREDIT` |
| 2 | `Request Stage Changed{request_type: dues_settlement, stage_name: due_raised, related_context_id: <the reversal>}` — only where the money had already been used |
| 3 | F8 from node 2 |

`FUNDS_DUPLICATE_CREDIT` at node 1 is a **correctness invariant with a target of zero**, and this
is the only place in the taxonomy it becomes visible. Rule A10 is explicit that the account may
legitimately go into debit here; node 2's rate is how often a reversal creates a debt the customer
did not cause and cannot have anticipated.

---

### The open-step population — four dispositions, no sweep required

`Journey Step Abandoned` fires nowhere until per-step idle thresholds exist (OD-5). The population
is addressable today with no threshold and no product decision:

> `did Screen Viewed{screen_name: funds_add}` **AND** `did NOT Journey Step Completed{step_name: add}`
> **AND** `did NOT Journey Step Failed{step_name: add}`

*Never tried* · *tried and still open* · *tried and failed* · *converted*. The middle two do not
exist in the PRD's tracking table at all. The same shape applies to withdrawals against
`wdl_requested`, which is why R7 requires every family to register a stage that fires at
submission — `fund_initiated`, `wdl_requested`, `due_raised`, `shf_raised` all do.

---

## 8. The PRD's tracking table, mapped row by row

All 21 rows of `product-requirements.md` §Tracking Requirements. **Every one becomes a filter.**
This table supersedes that one on adoption.

| # | PRD row | Event | Filter | Note |
|---|---|---|---|---|
| 1 | Funds view opened | `Screen Viewed` | `screen_name: funds_home_{blocked\|empty\|funded\|debit\|shortfall}` | Balances dropped (§6.2). The state rides `screen_name`, the age of margin data rides the refusal it causes |
| 2 | Withdrawable derivation opened | `Overlay Opened` | `overlay_id: funds_withdrawable_derivation` | Per-line engagement rides `Element Clicked{item_group: deduction_line}` |
| 3 | Deployability breakdown opened | `Overlay Opened` | `overlay_id: funds_deployability` | Validates REQ-105 before it is built out further, as the PRD asks |
| 4 | Deposit started | `Screen Viewed` + `Element Clicked` | `screen_name: funds_add`; `element_id: funds_amount_suggestion` | *Whether a suggestion was used* rides `item_value`; the anti-anchoring exclusion (REQ-201) depends on this pair |
| 5 | Deposit route list viewed | `Overlay Opened` | `overlay_id: funds_route_list` | REQ-202's disclosure proof. Route **selected** rides `funding_method` on node 4 — REQ-702 made selection automatic, so `fallback_used` carries an auto-switch |
| 6 | Deposit completed | `Request Stage Changed` | `request_type: fund_deposit, stage_name: fund_credited` | First-vs-repeat is a profile read (`first_deposit_at`), not an event property — R1 |
| 7 | Deposit failed | `Journey Step Failed` | `step_name: add` + `outcome_code` | Retry and route-change computed from `attempt_index` + `funding_method` (R1) |
| 8 | Deposit reversed | `Request Stage Changed` | `stage_name: fund_reversed` + `outcome_code` | *Whether the account went into debit* is the `due_raised` join, not a property |
| 9 | Withdrawal attempted, nothing withdrawable | `Action Blocked` | `blocked_reason: nothing_withdrawable` + **`deduction_reason`** | F6 |
| 10 | Withdrawal requested | `Journey Step Completed` | `step_name: withdraw` + `amount_paise` + `amount_is_max` | The quoted arrival is retained server-side and surfaces as `arrival_variance_sec` at credit |
| 11 | Withdrawal state changed | `Request Stage Changed` | `stage_name: wdl_*` (12 values) | `previous_stage_name` and `seconds_in_previous` are already registered — the PRD's *from state / to state / elapsed* is three properties it need not invent |
| 12 | Mandated settlement executed | `Request Stage Changed` | `request_type: running_account_settlement, stage_name: rac_credited, initiated_by: system` + `amount_paise` | *Whether notified in advance* is the `Message Dispatched` join; *whether support was contacted* is F9's negative step |
| 13 | History searched or filtered | `Query Answered` | `query_scope: transaction_search` + `result_count` | *Whether a result was opened* is `Screen Viewed{screen_name: funds_transaction_detail}` |
| 14 | Reconciliation view opened | — | — | **Relocated to the Ledger with REQ-406.** FMS registers nothing; the Ledger registers its own `screen_name` |
| 15 | Statement exported | `Document Retrieved` | `report_type: ledger, delivery_method: download, file_type: csv, period_preset` | Zero new properties. Makes `docs_downloaded_30d` computable |
| 16 | Debit balance entered | `Request Stage Changed` | `request_type: dues_settlement, stage_name: due_raised` + `outcome_code` | *Whether the user was notified and how fast* is `due_notified` and `seconds_in_previous` |
| 17 | Debit balance cleared | `Request Stage Changed` | `stage_name: due_cleared` | *Whether the exact-amount exception was used* is `Element Clicked{element_id: funds_pay_exact_dues}` |
| 18 | Blocker shown on funds view | `Action Blocked` | `blocked_reason` + `was_journey_entry` | F11 |
| 19 | Margin shortfall shown | `Request Stage Changed` | `request_type: margin_shortfall, stage_name: shf_*` | *Time remaining* is `seconds_in_request` against the deadline; *whether a deposit followed* is the `related_context_id` join |
| 20 | Ledger integrity check | — | — | **Leaves the taxonomy.** Engineering observability (§6.3) |
| 21 | Support contact linked to a movement | `Request Stage Changed` | `module: support, request_type: support_ticket, related_context_id` | Registered by support; FMS reads it through the join |

**19 of 21 rows become filters on existing names. Two leave. Zero become names.**

---

## 9. De-duplication keys

The taxonomy's §3 table is extended for the four FMS families. The published
`Request Stage Changed` key — `context_id + event_name + stage_name` — is **inadequate for two of
them**, in exactly the way §3 warned: *"a legitimate repetition is silently dropped — and a drop
looks like a conversion cliff, not like a bug."*

| Family | Key | Why |
|---|---|---|
| `fund_*` | `context_id + event_name + stage_name` | Each stage fires once per record. Rule A6's repeat confirmations change nothing and produce no entry, so a second `fund_credited` is a genuine duplicate and must collapse |
| `wdl_*` | `context_id + event_name + stage_name + transition_index` | **`wdl_rail_queued` legitimately repeats.** A three-night rail outage produces three genuine transitions the published key collapses into one, silently under-counting the exact number the rail-reliability read exists to produce |
| `rac_*` | `context_id + event_name + stage_name + transition_index` | Cyclic by design |
| `due_*` | `context_id + event_name + stage_name + related_context_id` | **`due_part_paid` legitimately repeats**, once per clearing deposit. The deposit's id is the discriminator and **needs no new property** |
| `shf_*` | `context_id + event_name + stage_name` | Each stage fires once; the escalation ladder is `Message Dispatched{touch_index}`, which has its own registered key |
| `Journey Step Completed/Failed{step_name: add\|withdraw}` | inherited: `+ step_name + attempt_index` (`+ vendor_attempt` on failure) | `FUNDS_TIMEOUT` consumes no attempt, so two consecutive timeouts carry the same `attempt_index` — the identical case `vendor_attempt` was added to discriminate |

**Acceptance test.** Replay every FMS event type twice; assert one row each. Then queue one payout
across two settlement runs and assert **two** `wdl_rail_queued` rows. Then clear one due with two
part-payments and assert **two** `due_part_paid` rows.

---

## 10. What FMS deliberately cannot measure

Stated rather than discovered, on the taxonomy's own principle that a blind spot named is a
decision and a blind spot found is a defect.

| Unanswerable | Why | Nearest proxy |
|---|---|---|
| Withdrawable-versus-total, in rupees | R5. The taxonomy names this exact question as permanently closed | Support ticket topic distribution, and `deduction_reason` ranking |
| Available margin against ledger balance, in rupees | R5 | `deduction_reason` on `margin_component` expansions |
| NEFT and IMPS deposit attempts | Self-service; the user leaves for their own banking app and FMS never sees the attempt. Both are excluded together — the build carries them under one route id (§5.9) | `fund_credited` with no preceding `Journey Step Completed` — counted where visible, excluded from every rate |
| Whether a declined payment actually debited the customer's bank | We cannot know, which is why Rule C5 makes the copy conditional | None. Do not construct one |
| Abandonment **class** | `Journey Step Abandoned` needs per-step idle thresholds (OD-5) | The open-step population (§7) gives the audience without the class |
| Period reconciliation | REQ-406 relocated to the Ledger | The Ledger's own instrumentation |
| Whether a user read an SMS | No delivery receipt semantics for SMS beyond dispatch | `Message Dispatched{dispatch_outcome}` plus the downstream `Screen Viewed` |

**FMS emits no `Charged`.** The reserved platform name fires when Thinq *earns* — brokerage on an
executed order, and collected service fees. A deposit is the customer's own money moving between
two accounts they own, and a withdrawal likewise; the taxonomy already rules both out. Debit
interest under REQ-708 **is** earned revenue and is the one arguable case; it is raised as
FMS-OD-8 rather than fired, because firing `Charged` wrongly is not retrospectively fixable.

---

## 11. Open decisions

**Nine raised, three closed, six open.**

| # | Decision | Why it cannot be deferred past build | Owner |
|---|---|---|---|
| **FMS-OD-1** ✅ | **An account-level surface had no lawful `context_type` — CLOSED 19 Aug 2026, granted in full.** `context_id` and `context_type` are non-nullable, and `Screen Viewed{screen_name: funds_home_funded}` concerns no record — no `fund_txn` exists, no `service_request` exists. FMS met this on the **first node of every funnel**. The recommendation was to register `account` *and* `session` in one pass with a `module: marketing` pre-auth nullability rule, **not one value**, because §2 row 4 states the rule against itself: additions are *"done in **one pass**, not per module — nine uncoordinated additions produce nine grains that do not join."* **THINQ-EVENTS-001 v1.1.0 granted exactly that.** §2 row 4 now closes `context_type` at **14** with `account` **(new)** and `session` **(new)** — added, in that row's own words, *"under that same sentence, not under a module's request"* — and records the one lawful null: `context_type` MAY be null on `module: marketing` **pre-auth**. `account` is defined there for the four surfaces holding the identical gap (`profile` home, `portfolio` holdings, `markets` watchlist, support's `account_lookup`) and `session` for `Session State Changed` and the charts idle-lock promise; `basket` was refused in the same pass. FMS emits `context_type: account` on every `funds_home_*` screen (§2.1 row 4) and the synthetic `fund_txn` id is dead. **`THINQ_EVENT_TAXONOMY_TESTS.md` D-01, filed 18 Aug 26, closes on the same registration** | — | **Closed** — registrar, in THINQ-EVENTS-001 v1.1.0 |
| **FMS-OD-2** ✅ | **What counts as a deposit "attempt" — CLOSED 20 Aug 2026.** The 95% launch threshold counts **first payment instructions FMS issued** (`Journey Step Completed{step_name: add, attempt_index: 1}`), succeeded = the record reached `fund_credited`, and **`FUNDS_USER_CANCELLED` is excluded from the denominator**. A user changing their mind before authorising is not a reliability failure; the code exists as its own value precisely so the exclusion is a filter rather than a rewrite. Retries are measured separately by F4, which is where Rule A9a's "six failures, six answers" claim is tested. | — | **Closed** — product owner, 20 Aug 2026 |
| **FMS-OD-3** ✅ | **Derivation affordance — CLOSED 20 Aug 2026: render-conditional.** The withdrawable derivation is rendered **only where a gap exists** between the withdrawable figure and the ledger balance. That makes `screen_name: funds_home_funded` a clean denominator for the 40% KPI with **no `screen_name` split** — the `funds_home_funded_gap` / `_nogap` pair is not registered and SHALL NOT be. A gap is near-universal on a funded trading account, so the split would have spent a registry value to carry a rounding error. **Build consequence:** the affordance's presence is itself the qualifying condition, so F6 needs no extra property to express "users who saw a gap". | — | **Closed** — product owner with design, 20 Aug 2026 |
| **FMS-OD-4** | **Who renders comms copy.** Does not block instrumentation — the `Message Dispatched` contract is identical either way — but CleverTap-side rendering requires the UTR, last-four and amounts to travel as properties, reopening §6.1. Recommendation: FMS renders, CleverTap delivers | — | Product owner with engineering |
| **FMS-OD-5** | **`amount_is_max` — keep or drop.** One named consumer. Drop it if the registrar wants five claims rather than six. **Now dated by OD-2's closure:** nothing has been emitted, so keeping or dropping it is free today and is an addition plus a permanent dead property after the first FMS event fires. Settle it before that, not before Phase 2 | — | Registrar, **before first emission** |
| **FMS-OD-6** | **Shortfall module ownership.** Filed under `funds` because `module` follows the destination and every shortfall surface and all 27 messages are FMS's. Needs ratifying against `orders`, which owns the cause and the square-off. **Narrowed, not closed:** the authority v1.1.0 registers `shf_*` and `due_*` as `module: funds` stage families (§5.1) and `request_type: margin_shortfall` · `dues_settlement` as funds values (§5.3), which is the registration FMS asked for — but `orders` has registered nothing against it and the registrar seat is still unfilled (authority OD-1), so nobody has ratified it. `module` is an envelope value: reversing it after first emission splits every shortfall funnel at the deploy date | — | Registrar with orders |
| **FMS-OD-7** | **`error_class` mapping for the eleven new codes.** Inherits the taxonomy's OD-7, which is still **P0 and open**. The eleven codes themselves are now registered (authority §5.2), so the mapping has a complete list to map — what is missing is the ruling, not the vocabulary. `FUNDS_GATEWAY_UNREACHABLE` must be `technical` and must not count against screen conversion; `FUNDS_USER_CANCELLED` must be `friction` and must. Unmapped, the comms engine cannot pick between apology, guidance and silence | — | Analytics with FMS |
| **FMS-OD-8** | **Does debit interest fire `Charged`?** Earned revenue, so arguably yes; but it is not an executed order and OD-4 has not ruled on service fees. Recommendation: **no** until OD-4 rules. The authority v1.1.0 registers `charge_category` on `Charged` with its **values deliberately unwritten** — *"the registry Finance owns and OD-4 must produce before `Charged` fires"* (§5.3) — so there is now a property waiting for the ruling and still nothing lawful to put in it. OD-2 confirms `Charged` has never fired, so the not-retrospectively-fixable cost is still fully avoidable | — | Registrar with finance |
| **FMS-OD-9** | **Event retention.** The PRD's statutory-retention question is unanswered, and money-movement events are the ones most likely to be inside it. The authority opened **OD-11** on 19 Aug 26 — DPDP erasure has no representation of any kind and **no vocabulary is registered against it** — under the same owner and against §2's *"re-running last quarter's funnel today returns the same numbers"* guarantee. Retention and erasure are one compliance ruling and are taken together or neither is answerable | — | Compliance |

**Nothing in this list blocks emission any more.** FMS-OD-1 was the only one that did, and it was
granted in full on 19 Aug 2026. What now gates the first FMS event is not a taxonomy question:
it is the **registrar** (authority OD-1, still unfilled, still P0 — *"name one person before any
module registers its first value"*) and the **per-module wrapper validation table** the authority
§8 requires to ship *with* the change rather than after it. The eight that remain open can be
settled while instrumentation is built — but FMS-OD-5 and FMS-OD-6 are cheap only until the first
event fires, because OD-2's closure means the pre-emission window is open now and closes then.

---

## 12. What changes elsewhere on adoption

**Audited row by row against THINQ-EVENTS-001 v1.1.0 on 20 Aug 2026.** Fourteen rows: **seven
landed, seven still owed.** A row marked **LANDED** was verified against the authority file itself, not
against a summary of it; a row marked **STILL OWED** was checked the same way and is genuinely
outstanding. The distinction matters because this table is the only place FMS records what it is
still waiting on, and a table that says *pending* about work already done stops being read.

| Document | Change | Status |
|---|---|---|
| `product-requirements.md` §Tracking Requirements | **Superseded.** Replaced by a pointer to this file. Eight rows lose their balance properties, two rows leave the taxonomy, and nineteen become filters | **STILL OWED** — the parent PRD still carries all 21 rows |
| `product-requirements.md` §Open Questions | *"May balance figures be sent to CleverTap at all?"* — **closed** (§6). *"Which system orchestrates outbound communications"* — **no longer blocks instrumentation** (§6.4), still blocks orchestration | **STILL OWED** — answered here, not yet written back |
| `THINQ_EVENT_TAXONOMY.md` §5.7 | `channel` gains `sms` — D1 | **LANDED** — §5.7, `channel` is 5 and closed |
| `THINQ_EVENT_TAXONOMY.md` §5.8 | `service_id` gains `psp_payout`, `margin_front_office`, `margin_back_office` — D2 | **LANDED** — §5.8, the registry is now 24 |
| `THINQ_EVENT_TAXONOMY.md` §5.7 | `reveal_group` gains `available_margin`, `withdrawable`; `funds_balance` superseded, not reclaimed — D3 | **LANDED** — §5.7, `reveal_group` is 14 and the retired string is claimed |
| `THINQ_EVENT_TAXONOMY.md` §5.1 | `stage_name` gains 17 funds values across four families — D4 and §5.4 | **LANDED** — §5.1, with the R8 split written into the `wdl_*` Terminal-of-record cell |
| `THINQ_EVENT_TAXONOMY.md` §2 row 4 | `context_type` gains `account` **and** `session`, plus the `module: marketing` pre-auth nullability rule — the two-value fix FMS-OD-1 recommended, taken in one pass under that row's own rule | **LANDED** — §2 row 4, `context_type` is 14 and closed. FMS-OD-1 closes on it (§11) |
| `THINQ_EVENT_TAXONOMY.md` §3 | The de-duplication table gains rows for the four FMS families — §9 | **STILL OWED** — §3's table stands at twelve rows and names no funds family. `settlement_run_index`, the discriminator two of them need, is registered in no §5 subsection either. This is the same defect §3 of the authority fixed for `vendor_attempt` and `transition_index`, and it is unfixed for `wdl_rail_queued` and `due_part_paid` |
| `THINQ_EVENT_TAXONOMY.md` §5.2 | `WDL_BANK_REJECTED` renamed **`WDL_BANK_RETURNED`** to pair with the stage `wdl_bank_returned` — §5.5 | **LANDED** — §5.2 registers `WDL_BANK_RETURNED` and states the rule: spelled `RETURNED`, never `REJECTED`, because `wdl_rejected` is *our* refusal and one string cannot carry both. **Final, not free:** OD-2's closure makes it a settled rename rather than a pre-emission proposal |
| `THINQ_EVENT_TAXONOMY.md` §5.3 | `funding_method` and `amount_paise` scopes widen onto `Journey Step Completed` / `Journey Step Failed` — §5.10 claims 5 and 6. Recorded in the registry, on the `state_intact` precedent, not left in this document's prose | **STILL OWED** — §5.3 scopes `funding_method` to `Request Stage Changed` (funds, ipo) and `amount_paise` to `Request Stage Changed`, both unchanged. Until the registrar rules, F4 and the amount-at-request comparison Rule W3a rests on have no lawful property |
| `THINQ_EVENT_TAXONOMY.md` §5.9 | **Nothing to adopt — already carried.** The profile register was rebuilt as a 36-row table with **Values**, **Type**, **Written by** and **Recompute cadence**, growing from 30 to 36 by absorbing exactly these six: `funds_state`, `dues_state`, `shortfall_state`, `first_deposit_at`, `last_deposit_method`, `deposits_90d`. §5.11 is a pointer to it, not a claim on it | **LANDED**, and the debt runs the other way: §5.9 marks `funds_state` and `deposits_90d` **OPEN — FMS registration owner**, and it is this document that owes each a writing event or job |
| `THINQ_EVENT_TAXONOMY.md` §6 row 6 | Rewritten. It grants `module: funds` **eight** event names — no spine, no `Screen Viewed`, no `Element Clicked`, no `Sensitive Value Revealed`, no `Document Retrieved` — against the **nineteen** FMS emits (§2.2), plus `OTP Requested` / `OTP Resolved` when C-Q8 option A ships. **`Ops Decision Recorded` is not added**: `module` follows the destination, and the support join is on `context_id` | **STILL OWED** — row 6's Events-used cell is still the eight, and its Registers cell still reads `stage_name`: `fund_*` · `wdl_*` · `rac_*`, omitting `due_*`, `shf_*`, `service_id`, `reveal_group`, `channel` and `sub_module: health` that §5.1, §5.2, §5.7 and §5.8 now carry. The registry and its own module map disagree |
| `web/dashboard.js` | Its `serverOnly` toggle stops being a hypothesis: the client/server split in §7's funnel tables is the real one. Its *"assume no third-party analytics"* mode is now exactly F3 nodes 1–3 and F2 going dark | **STILL OWED** — a build change, unaffected by the taxonomy revision |
| `product-requirements-communications.md` §11 | C-Q8 option A costs **zero** taxonomy change — `otp_purpose: withdrawal_confirm` is already registered and has no emitter | **STILL OWED** — the finding is confirmed against §5.8, which registers `withdrawal_confirm` unchanged; recording it in C-Q8 is the outstanding edit |

**One class of value is in neither column.** The 31 open, engineering-owned ids FMS registers —
`screen_name` 9 · `element_id` 14 · `overlay_id` 6 · `item_group` 2 (§5.12) — are not the
authority's to land. It holds them under **OD-10** as *"empty or unwritten"* and requires each
module to populate its own before the surface ships. They are FMS's outstanding work, not the
registrar's.

---

## 13. Acceptance tests for this specification

1. Sample 100 FMS events across all five screens; assert all eleven envelope properties present and no property assembled at a call site.
2. Assert **zero** new event names in the FMS payload schemas. Grep every schema against the taxonomy's 40.
3. Assert no payload contains a balance, a shortfall amount, an amount owed, or any figure not attached to a movement (§6.1).
4. Emit `module: funds` with `stage_name: pan_failed_terminal`; assert rejection by the per-module validation table.
5. **Regression.** `channel: sms` is registered (authority §5.7; D1 closed), so the original two-part form of this test has no precondition left to establish. Emit `channel: sms` and assert **acceptance**; emit an unregistered sixth value and assert **rejection at the wrapper**. The test now guards the addition rather than proving the gap.
6. Replay every FMS event twice; assert one row each. Queue one payout across two settlement runs; assert two `wdl_rail_queued`.
7. Fail a payin with `FUNDS_TIMEOUT`; assert `attempts_remaining` unchanged and the record at `fund_awaiting_confirmation`, not `fund_failed`.
8. Emit `Element Clicked{item_group: deduction_line, item_value: <not in registry>}`; assert wrapper rejection.
9. Raise a due; assert a `due_notified` inside the notification window. Zero tolerance.
10. Run F3 and F5 against `web/dashboard.js`'s synthetic population; assert the funnel definitions here return its `metrics()` numbers.
11. Assert `funds_state` and `screen_name` correspond: strip `funds_home_` from every `funds_home_*` value in §5.1 and assert the resulting set equals the `funds_state` enum in §5.11 exactly — same members, same spellings, no extras on either side.
