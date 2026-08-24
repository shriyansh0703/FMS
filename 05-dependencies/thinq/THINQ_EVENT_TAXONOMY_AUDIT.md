---
title: Thinq Event Taxonomy — Final Audit
doc_id: THINQ-EVENTS-AUDIT-001
version: 1.0.0
date: 19 Aug 2026
status: Findings only — no edits made to any source document
authority_under_test: THINQ_EVENT_TAXONOMY.md (THINQ-EVENTS-001 v1.0.0, 17 Aug 2026)
also_under_test:
  - THINQ_EVENT_TAXONOMY_TESTS.md (THINQ-EVENTS-TESTS-001, 18 Aug 2026) — D-01..D-50 applied-or-not
  - fms/product-requirements-events-and-funnels.md (FMS-EVENTS-001, 19 Aug 2026) — the first module registration
  - kyc-event-spec.html — the rendered artefact, both halves
  - Thinq_Arvind/architecture/event-model.md — stale summary
  - THINQ_PROFILE_PRD.md, THINQ_KYC_ONBOARDING_PRD.md, THINQ_CUSTOMER_SUPPORT_PRD.md,
    THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md, THINQ_TNC_PRD.md, THINQ_KYC_PANEL.md
  - fms/web/app.js, dashboard.js, gen-comms.js, .test-assert.js, .dash-assert.js
method: |
  Two passes, ten independent audit lenses, every finding put to an adversarial verifier
  prompted to refute it. 200 findings raised; 74 survived refutation; 126 were refuted.
  Five contradictions between surviving findings were separately adjudicated against the
  primary sources (Annex A). Counts published here were re-counted, not quoted.
verdict_summary: |
  The registry is not emittable. One blocker: context_type is a 12-value closed non-nullable
  envelope property with no value for an account-level surface, so the first node of five of
  the first registering module's funnels cannot construct an envelope. Above it sits OD-1 —
  no registrar is named, and 74 values plus 6 property claims have been registered against
  nobody. 0 of the 50 known test findings have been applied to the authority. The design is
  sound; the enforcement machinery does not exist.
---

# Thinq Event Taxonomy — Final Audit

**Registrar's final consolidated result. This document supersedes the pass‑1 synthesis and the pass‑2 lens reports in full.**

Authority (**A**): `kyc-ops-console/THINQ_EVENT_TAXONOMY.md` — THINQ-EVENTS-001 v1.0.0, 17 Aug 26.
Adversarial suite: `kyc-ops-console/THINQ_EVENT_TAXONOMY_TESTS.md` (D‑01…D‑50), 18 Aug 26.
First registration (**C**): `fms/product-requirements-events-and-funnels.md` — FMS-EVENTS-001, 19 Aug 26.
Rendered artefact (**B**): `kyc-ops-console/kyc-event-spec.html` — §01–§18 superseded, §19–§23 = C rendered; C:37 declares the two halves **one artefact**.

Two passes, ten lenses, 138 raised findings. 67 survived pass‑1 refutation, 7 more survived pass 2, 71 + 43 were refuted. Five inter‑finding contradictions were adjudicated. What follows is the deduplicated result: **one defect, one row**.

---

## 1. Verdict — is the registry emittable, and what is the single blocker

**No. The registry is not emittable today, and it fails at the first node of every funnel the first registering module wrote.**

`context_type` is a **12‑value closed, non‑nullable** envelope property (A §2 row 4) with no value for an account‑level surface. `Screen Viewed{screen_name: funds_home_*}` — the opening node of FMS's **F2, F3, F5, F6 and F11** — cannot construct a lawful envelope. The adversarial suite filed it as **D‑01** on 18 Aug ("41 of 77 test cases cannot construct an envelope"); FMS filed it independently as **FMS-OD-1** on 19 Aug, calling it *"the only one that blocks emission."* Neither document cites the other.

**The single blocker is D‑01 / FMS-OD-1, and the correct fix is not the one FMS proposes.** FMS asks for one value (`account`). A §2 row 4 mandates the opposite discipline in its own text — *"Done in **one pass**, not per module — nine uncoordinated additions produce nine grains that do not join."* At least two values are needed now (`account`, `session`) plus a pre‑auth nullability rule for `module: marketing`. `basket` is **not** needed — an order basket is a record and `context_type: order` covers it.

**Above that blocker sits a meta‑blocker that is not technical.** A §9 **OD-1** — *"Who is the registrar?"* — is rated **P0** by the authority itself and has no named human. FMS has now registered **74 values, 4 declared property claims (6 actual) and 6 profile properties** against nobody. A's own thesis: *"Every enum-consistency defect found in this taxonomy … is a **registrar** defect, not an author defect. Name one person before any module registers its first value."* That sentence is the audit's finding as much as its own.

**Everything else in this report is one of three things**: a documentation defect that costs a reader rather than a row; a migration instruction the authority already wrote and nobody executed; or a rule stated more strictly than it is kept. **The taxonomy's design is sound.** The layer structure, the 40‑name budget, the eleven‑slot envelope, the `stage_code → stage_name` ruling, the `method`/`capture_method` split, the `blocked_reason`/`outcome_code` client/server split and the reuse discipline all survived adversarial attack. What does not exist is the **enforcement machinery** A §8 item 1 promises: the per‑module wrapper validation table for the six module‑scoped enums, and the CI job that diffs it against §5. Every "the wrapper will catch it" argument in this corpus is currently a promise.

---

## 2. The true inventory

Counted mechanically. Where a published figure is wrong I give the right one. Where a figure is uncertain I say so.

### Event names — **40. The published count is correct.**

Verified by extracting the Source cell of every row in A §4 layers 1–9: **exactly 40 rows.**

| Layer | § | Names |
|---|---|---|
| 1 Generic UI (schemas frozen) | §4 L1 | 6 |
| 2 Journey spine (renamed) | §4 L2 | 3 |
| 3 Journey milestones | §4 L3 | 6 |
| 4 Auth | §4 L4 | 4 |
| 5 Cross-cutting | §4 L5 | 7 |
| 6 Ops and lifecycle | §4 L6 | 3 |
| 7 Requests, reveals, refusals | §4 L7 | 3 |
| 8 Messaging-side | §4 L8 | 2 |
| 9 New names | §4 L9 | 6 |
| **Total** | | **40** |
| `Charged` | §4 :242 | Reserved platform name, outside the budget, **and the only §4 row with no Source cell** |

**Budget: 512 − 40 = 472. The authority's 472 is right.** The suite's 471 (D‑49) rests on `Charged` consuming a custom-event slot; A states the contrary rationale explicitly and I rule for **472**.

### The Source column — counted for the first time in this audit

| Source value | Rows |
|---|---|
| `server` | 27 |
| `client` | 10 |
| `server (sweep, platform: system)` | 1 |
| `server (platform: ops_console)` | 1 |
| `client or server` | 1 (`Consent Captured`, §4 :222) |
| **no Source cell** | 1 (`Charged`) |

The vocabulary is not five tokens — it is `client`, `server`, and three qualified forms of `server`. That is fine and needs no change.

### Envelope — **11 properties. Correct; the count is not the problem.**

`module` · `sub_module` · `context_id` · `context_type` · `session_id` · `platform` · `screen_name` · `step_name` · `stage_name` · `account_state` · `engagement_state`. Four nullable (`sub_module`, `screen_name`, and new in v1.0.0 `step_name`, `stage_name`). **Three of the eight non‑nullable have no source on customer‑less events** — `session_id`, `account_state`, `engagement_state` on `platform: system` and `context_type: service_incident` (D‑11).

`kyc-event-spec.html` §22.4 and `THINQ_KYC_ONBOARDING_PRD.md` §22.4 still publish **13** envelope properties. Both sit inside superseded ranges.

### Event properties — **approximately 175, and 26 named on events with no correct §5 row**

The registered total is **uncertain and nothing depends on it**: my extraction of the first column of every §5 table returns 181 distinct tokens including multi‑property rows; the pass‑1 lens counted ≈173. No document publishes a figure, so there is no contradiction. Treat it as **≈173–181**.

**The number that matters is published, and both published values were wrong.** The pass‑2 adjudication re‑ran the extraction (A §4 lines 118–266 diffed against §5 lines 267–563, italic parentheticals stripped, every candidate grep‑verified). The correct answer is a **partition, not a single integer**:

**26 total = 23 with no §5 registry row of any kind + 3 with a §5 row scoped to a different event.**

**Class A — no §5 row at all (23):** `fallback_used` · `days_since_registration` · `sessions_used` · `fallbacks_used` · `steps_failed_count` · `agreement_version` · `ddpi_opted_in` · `decision_hours` · `is_sanctions` · `can_reapply` · `time_to_verify_sec` · `time_since_mobile_sec` · `landed_on_screen` · `via_deeplink` · `biometric_enrolled` · `nudge_skipped` · `hours_since_ptt` · `requires_reverification` · `was_journey_entry` · `seconds_since_dispatch` · `charge_category` · `Items[]` · `Amount`. (The last two are CleverTap platform‑reserved on the reserved name `Charged`; the first 21 are Thinq‑owned.)

**Class B — a §5 row exists under a different scope (3):** `nominee_outcome` (§4 :154/:155, §5.9 registers it profile‑only) · `last_outcome_code` (§4 :143, §5.9 profile‑only) · `file_type` (§4 :224 `Document Retrieved`, §5.5 :440 scoped to `Media Captured` only).

- **D‑40's 22 is a correct enumeration of its own list but misses four**: `fallback_used`, `agreement_version`, `requires_reverification`, `last_outcome_code`. Restate D‑40 as **"22, corrected to 26"**.
- **PROP-06's 26 is arithmetically right but mislabelled** — it publishes 26 under D‑40's headline *"no entry in the §5 registry"*, false for 3 of the 26. **PROP-06 is retired as a standalone finding.**
- **The pass‑1 synthesis's "19 + 3" is wrong** and is withdrawn. It subtracted `charge_category` on the reasoning that §6 row 9 delegates it to orders. §6 is not §5; a mention in the module map is not a registry row. `charge_category` has no §5 row and **no values at all**, on the one name A §9 OD‑4 says is *"not retrospectively fixable"*. It stays in Class A.
- **D‑40's `fallback_used`/`fallbacks_used` "two spellings of one stem" claim is withdrawn as refuted.** Line 141 is a per‑step boolean on the spine; line 154 an integer count on a once‑per‑journey milestone. Both must be registered at their stated grains.
- **The suite's "Total addressable property names 222 (200 + 22)" (TESTS :1601) becomes 223 = 200 + 23**, since the three Class B members are already inside the 200 under the wrong scope.

### Widest payload

**`Request Stage Changed` at 17 event‑level + 11 envelope = 28**, rising to 30 once FMS's `settlement_run_index` and `arrival_variance_sec` land. A §4's claim that *"`Element Clicked` at 26 is the widest payload"* is **false** — `Element Clicked` is 15 + 11 = 26 and third, behind `Order State Changed` at 27. **No event approaches the 100‑property server cap**; the true maximum is 30 of 100. The cap is fine and should stop being cited as a constraint.

### Closed enumerations — **56–57**

52 declared inside §5 (`N, closed` / `Now enumerated, N`) plus 5 in the §2 envelope (`module` 18, `context_type` 12, `platform` 9, `account_state` 14, `engagement_state` 5). A re‑grep of the declaration strings returns 56; treat as **56–57 ± 1**. Nothing is published and nothing depends on it. **Six are module‑scoped 🔶** (`step_name`, `stage_name`, `outcome_code`, `blocked_reason`, `request_type`, `cap_type`) — these six and only these six are what the §8 wrapper table validates. Two id spaces (`service_id`, `element_id`) sit on the same footing without being enums. `screen_name`, `message_type`, `overlay_id`, `field_id` are **open registries** — an unregistered value there is not a defect, and four pass‑1 findings and three pass‑2 findings were refuted on exactly that ground.

### Profile properties — **36 (30 in A §5.9 + 6 in FMS §5.11)**

I counted both lists directly. A §5.9 :556 is a bare comma‑separated list of 30 with **no Values column, no Type column, no Scope column and no Nullability column** — unlike every other §5 subsection. FMS §5.11 :566–573 adds `funds_state` · `first_deposit_at` · `last_deposit_method` · `deposits_90d` · `dues_state` · `shortfall_state`, with a "Serves" column and no "Written by" column. See §6.

### FMS's own scale

**13 funnels, F1–F13** (verified from C :51 "F1 to F13"; the pass‑1 note of "twelve" was wrong). **74 value additions** (verified: C §5.12's thirteen terms sum to 74). **9 open decisions** FMS-OD-1…9. **10 open decisions** in A, OD-1…OD-10, four of them P0 (OD-1, OD-2, OD-3, OD-7). **18 modules** in A §6. **17 comms rules** C1–C17.

### Every published count that is wrong, with the right number

| Where | Published | Correct | Note |
|---|---|---|---|
| A §5.2 :337 (asserted 3×, *"not 52 and not ~50"*) | kyc `outcome_code` **54** | **53** | Sub‑counts sum to 53; token extraction returns 53; critic's independent machine check returns 53 unique with zero duplicates. The lost 54th is **`DUPLICATE_IDENTIFIER`**, which §5.2 itself reassigns to `module: auth`. **A §8's instruction to the KYC PRD to "correct the count to 54" must be reissued as 53**, and **OD-7's "all 54 KYC codes" as 53** |
| A §2 row 9, §7, §8 (3 places, *"All 46 values survive"*) | `stage_code` **46** | **40** | `THINQ_PROFILE_PRD.md` :134 and :1978 both say 40; `kyc-event-spec.html` §14 enumerates exactly 40 (CHG 8 · NOM 5 · NOMEDIT 6 · DDPI 5 · CLO 7 · SEG 9). **A is wrong, the downstream PRDs are right** |
| A §2 row 9 | **9** modules register `stage_name` | **12** in §5.1 | `segment_activation`, `corporate_actions`, `referral` are missing. Replace the enumeration with a pointer to §5.1 rather than re-listing |
| A §6 "New names required" column | sums to **5** | A §4 declares **7** | `Consent Captured` and `Message Dispatched` are charged to no module row. Cosmetic — §4's R6 table names real consumers for both |
| A §4 | "`Element Clicked` at 26 is the widest payload" | `Request Stage Changed` 28 | See above |
| **TESTS** :1601 | addressable property names **222** | **223** | 200 registered + 23 §4‑only |
| **TESTS** §5 coverage table | "the 30 unexercised **of 54**" | **of 53** | Inherits A's error |
| **TESTS** :1302, :1600 | §4‑only properties **22** | **26** (23 + 3) | See partition above |
| **C** :46, :100 | "the **twelve** names FMS emits" | **15 rows, 19 names** | |
| **C** :120 | "fifteen rows over **twelve to fourteen** names depending on how the pairs are counted" | 15 rows, **19 names** — delete the hedge | Neither 12 nor 14 is reachable by any counting rule; the pairs‑as‑one floor is 15 |
| **B** :2191 (§19.2 heading) | "The **fourteen** names FMS emits" | **19** | Same declared artefact, third wrong number |
| **B** masthead | 31 names · 472 of 512 remaining | **40 · 472** | Arithmetically impossible pair — 512 − 31 = 481 |
| **B** §12 "Our usage" | 28 + 3 = 31 · **482** remaining | **40 · 472** | 482 is the remainder of the retired 30‑name model, and inconsistent with its own 31 |
| `Thinq_Arvind/architecture/event-model.md` :11 | "Six layers · **28 names**" | 40, nine layers | Two generations stale |
| `THINQ_KYC_ONBOARDING_PRD.md` §22.2 :3033 / §22.0 :2734 | **28 names** / **482 remaining** | 40 / 472 | Superseded range |
| `fms/product-requirements-communications.md` §10.1 topic table | HC‑DMT **12** answers | **11** (158 total, verified) | The registry's 158 is right; one stale cell in a descriptive column. **P3** |
| `THINQ_TNC_PRD.md` §3.5 | "23 artefacts" | **34** distinct codes at :53–:120 | A stale version artefact (§3.5 stamped v1.7.0, doc at v1.11.0), inside a drafting register that governs no registered property. **P3** |

**B §18/§19 are the only two places on that page that agree with the authority (40 · 472).**

**What is fine and should not be relitigated:** the 40‑name count, the 472 remainder, the eleven‑slot envelope, the nine‑layer structure, the `stage_code → stage_name` ruling, the `method`/`capture_method` split, the `segment`/`segments_active` grain distinction, the `blocked_reason`/`outcome_code` client/server split, and FMS §5.12's 74‑value arithmetic.

---

## 3. Duplicates and collisions

One row per defect, deduplicated across both passes and all ten lenses. Ranked by severity then blast radius.

| # | Sev | What | The two sides | Which wins | Cost of leaving it |
|---|---|---|---|---|---|
| **C1** | P1 | **`funding_method` and `amount_paise` on the spine — undeclared property claims** | A §5.3 scopes both to `Request Stage Changed` (funds, ipo) · C §4 prose and §8 rows 7 and 10 emit them on `Journey Step Completed`/`Failed`; C's own §4 split table contradicts that | **Register the widening in §5.3.** `ipo` is a named second consumer with the identical submit‑then‑settle split; §5.8's `state_intact` row shows a widening is recorded *in the registry*, not asserted in a module's prose | FMS declares 4 claims and makes 6. The §8 item‑1 wrapper table, when it ships, rejects F3 node 4, F4's entire route‑change read and §8 row 10. F4 is the funnel testing Rule A9a's "six failures, six answers" claim; it collapses to nothing |
| **C2** | P1 | **FMS §7 F3 node 6 assigns a second meaning to the registered stage value `fund_approved`** *(re‑cut from SYNC-02)* | A §5.1 and C §5.4 :314 both order `fund_initiated → fund_collect_sent → fund_approved → fund_credited` — PSP approval **before** credit. B §19.4 :2254 agrees: *"Approved at the bank; money not yet with us"*. C §7 F3 node 6 :739 places `fund_approved` **after** node 5 `fund_credited` and calls it *"→ margin. This node never loses anyone; it lags"* | **§5.4 and B are right; node 6 is the defect.** Strike the stage filter. Node 6 is a **latency annotation on node 5** — same population, reporting the median commit→credit interval, exactly as `dashboard.js` step 6 renders it (`n: intents.filter(i => i.credited).length`, byte‑identical to step 5). **Do not register `fund_margin_available`** — no artefact and no build evidences a distinct margin moment | Under FMS §9's own key `context_id + event_name + stage_name` ("each stage fires once per record"), a `fund_approved` **after** `fund_credited` on one `context_id` never occurs. The module's headline funnel converts at ~0% on its last node, and the **95% first‑try / 30s‑to‑margin‑at‑p95** KPI is read off a broken tail. This is the class §7 forbids by name when it kills `tier: C` — *"a redefinition-in-place that the additive-only rule forbids"* |
| **C3** | P1 | **`agreement_version` vs `artefact_version`** on `Agreement Generated` | A §4 L3 :155 lists `agreement_version` — its only occurrence in the entire authority, with no §5 row · A §5.7 scopes `artefact_version` to the same event and says it *"absorbs `aof_version` + `declaration_version`"* | **`artefact_version`.** The absorption was performed in §5 and §7 and never applied to §4 | Both lawful on one event. The AOF version travels under whichever spelling the implementer reads first, and the consent↔agreement join (`Consent Captured` carries only `artefact_version`) breaks entirely for the other half. The suite already emits `agreement_version` six times |
| **C4** | P1 | **`WDL_BANK_REJECTED` (bank refused) pairs with `wdl_rejected` (we refused)** | C §3 D4 draws the line and §5.4 registers `wdl_bank_returned` for the bank's refusal — then §5.5 maps that outcome to the code `WDL_BANK_REJECTED` | **Rename the code to `WDL_BANK_RETURNED`** (marked `(new)`, so free under the §7/§8 pre‑emission gate). Do **not** rename the stage — *"returned by a compensating entry"* is the accurate product fact and Rule W7 depends on it | `Request Stage Changed{stage_name: wdl_rejected, outcome_code: WDL_BANK_REJECTED}` is schema‑lawful and self‑contradictory, and the per‑module table cannot catch it — both are registered to `module: funds`. Corrupts F5 node 3, the number saying whether the rail or the firm is losing payouts |
| **C5** | P1 | **FMS publishes its emitted‑name count three ways in one declared artefact** *(NAME-01 + VAL-12 + SYNC-03 merged)* | C :46 "twelve" · C :100 "twelve" · C :120 "twelve to fourteen" · B :2191 "fourteen". The table is **15 rows carrying 19 distinct names** | **15 rows, 19 names.** Restate all four locations identically and delete the "depending on how the pairs are counted" hedge | C §13 acceptance test 2 is *"Assert **zero** new event names… Grep every schema against the taxonomy's 40."* A grep run against a stated 12 or 14 is how an omitted schema passes. It also understates blast radius: **19 of the taxonomy's 40 names carry funds traffic**, and that is the figure any sampling, retention or cost decision must be priced against |
| **C6** | P2 | **`incident_id` duplicates the envelope `context_id`** | A §5.8 :531 defines `incident_id` as *"`context_id` of the `service_incident`"*, on the two events (`Vendor Failure Detected`, `Service Restored`) the envelope already stamps `context_type: service_incident`. §7 removed `request_id` for exactly this. FT‑08 step 5 emits `incident_id=I-441 · context_type=service_incident · context_id=I-441` on one row | **`context_id` + `context_type: service_incident`.** Removal **stands** — the pass‑1 objection that it breaks §6 read 3 is refuted: `incident_id` is registered on neither middle event (`Journey Step Failed` §4 :141, `Message Dispatched` §4 :221), and FT‑08's cohort note describes a **time‑window join**. **Sequence the deletion after D‑11** (§11 row 6), or the incident loses both carriers at once | Nothing today. The real defect it exposed belongs to **D‑21**: §6 read 3 has no incident carrier on its middle two nodes at all. Replacement carriers, stated: (1) envelope on the two host events; (2) `related_context_id: <incident>` on outage and restore `Message Dispatched` rows — already registered on that event, so a referent ruling not a schema change; (3) widen `related_context_id` onto `Journey Step Failed` |
| **C7** | P2 | **`uploaded` and `upload` inside one closed `method` enum** | A §5.6 registers 21 values including both, with no rule for which step uses which — and declares the collision *resolved* | **One spelling, 20 values closed** | `Journey Step Completed{step_name: income_proof}` broken down by `method` splits document uploads across two strings — the `bank_manual`/`bank_manual_entry` class inside the pass that claims to have fixed it. **Do not delete `draw`** from `method`: it is the signature step's completion method, and the `draw`/`upload` overlap with `capture_method` is two properties on two events, which is what the rename achieved |
| **C8** | P2 | **`deduction_reason: negative_balance` vs `blocked_reason: negative_balance` on the same event** | C §5.10 claim 1 registers `deduction_reason` with `negative_balance` and `unsettled_credits` on `Action Blocked` · A §5.2 already registers `blocked_reason: negative_balance` for funds; C supersedes `blocked_reason: unsettled_funds` then re‑registers the same deduction as `unsettled_credits` | **Rename the deduction to `debit_balance`**; align the settlement spelling to one of `unsettled_funds`/`unsettled_credits` | Two meanings on one string on one event. `unsettled_funds` is *"superseded, not reclaimed"*, so both spellings live in the registry for one concept — in a document whose §3 opens by naming that trap |
| **C9** | P2 | **One funds state is spelled three ways** *(PROP-17 + VAL-22 merged)* | C §5.11 declares `funds_state` is *"the `screen_name` set as a **state**"*; four of five members strip the prefix exactly and the fifth does not — `funds_state: in_debit` vs `screen_name: funds_home_debit`. The build uses a third: `fms/web/app.js:355 key: 'debt'` | **Align on one stem and state the derivation rule in §5.11.** Changing the profile value is the cheaper edit — `screen_name` is load‑bearing in seven funnel nodes, `funds_state` in three. (The owning PRD `product-requirements-account-health.md` uses the English phrase *"in debt"*, not an identifier stem; it does not settle the choice, and the adjacent family is already `due_*`/`dues_state`) | The document asserts a correspondence and breaks it in one member. A dunning audience written `funds_state = debit`, or a funnel written `screen_name = funds_home_in_debit`, returns zero rows with no error — and the debt state is F8's day‑0/7/14/30 dunning audience |
| **C10** | P2 | **`active` in three closed enums, two of them injected on every event** | A §2 row 10 `account_state: active` · §2 row 11 `engagement_state: active` · §5.7 `visitor_state: active`. Also `rejected` (`account_state` vs the kyc stage slug) | **State the rule in §2:** value strings shared across envelope properties require the property name in every published filter definition. Renaming `account_state: active` is impossible — it is the most‑emitted value in the product | §7 already deleted `blocked_reason: submitted` and `field_group: account_state` for exactly this class. An audience author picking `active` from a value list gets whichever property the tool defaulted to, and the error is invisible in the resulting audience size |
| **C11** | P2 | **`segment: equity` vs `segments_active: cash`** | A §5.6 registers `segment` as `equity · fno · commodity` and the entitlement scalars as `cash · cash_fno · …`, with only a display note | **Publish the three‑row mapping in §5.6.** Do not rename `segment: equity` — it is a product‑wide atom on `Order State Changed` and inside `segments_dropped` arrays | The entitlement→first‑order activation read needs a decomposition rule that exists nowhere. `segments_dropped` is *"an array of `segment`"*, so a dropped equity leg is `["equity"]` against a scalar spelling it `cash`. Logged as D‑42/F‑17; fix never written |
| **C12** | P2 | **Three bare `outcome_code`s against R9's "domain prefix, without exception"** | `PASTE_BLOCKED` (in the kyc `bank` group of 7, all siblings `BANK_*`) · `FATHER_NAME_TOO_SHORT` (`address` group, siblings `ADDR_*`) · `DUPLICATE_IDENTIFIER` (the one auth code left unprefixed while nine new ones got `AUTH_`) | **Rename, or grandfather by name.** §7 renamed `BELOW_MINIMUM` → `FUNDS_BELOW_MINIMUM` for precisely this. All three are marked existing/unchanged, so the rename is contingent on **OD-2** confirming nothing is emitting | `outcome_code` is one namespace partitioned by module. A bare condition word is unclaimed; `DUPLICATE_IDENTIFIER` is read cross‑module by the KYC 0→1 funnel and is the string most likely to be re‑minted as `AUTH_DUPLICATE_IDENTIFIER` |
| **C13** | P2 | **`sendback` vs `send_back`** | `THINQ_KYC_PANEL.md` :703 Decision entity `action(approve/reject/sendback/escalate)` · A §5.8 registers `decision: send_back` | **`send_back`.** The panel's own prose uses the spaced form everywhere else; :703 is the only unspaced occurrence, and it is the line an emitter is written from | `decision` is the primary break‑down on the only event carrying manual‑review throughput. A split spelling halves the send‑back rate — the number the L1→L4 ladder is tuned on. **The panel is not in A §8's downstream table at all** |
| **C14** | P2 | **`THINQ_TNC_PRD.md` registers three `artefact_code` values outside A's closed ten** *(SYNC-13)* | `C-PANBANK` (:63), `T-MITC` (:85, :232), `O-DDPI` (:102, :232) — the latter two are real SEBI‑relevant artefacts. The same PRD enumerates `acceptance_mode` at four (:51, :235) against A's five, missing **`by_proceeding`** — the mode that applies to every welcome‑page artefact (:71 *"Moving ahead IS the acceptance"*). :164 introduces a second `channel` vocabulary (`proceed`/`checkbox`) | **Free value additions under R6.** Add `C-PANBANK`, and `T-MITC`/`O-DDPI` if they are the real artefacts; add `by_proceeding` to the PRD's §3 and §11; settle `channel = proceed\|checkbox` against A's closed four or state it never reaches the event stream | The TnC ledger holds **34** distinct codes at :53–:120 against A's closed ten. That is a registrar ruling, not an established shortfall of 24 — A scopes `artefact_code` to `Consent Captured` **and `Message Dispatched`**, and the ten are exactly the consents that gate a communication. But a consent record that cannot say **how** consent was given is the one record type where the gap is legal rather than analytic |
| **C15** | — | **`sub_module: ledger` under funds and reports** | A §5.1 registers it twice | **Fine — leave it.** §5's legend makes every module‑scoped filter `module = X AND property = Y`, and `report_type` is not module‑scoped, so "all ledger retrievals" is a single flat query | **No cost.** Reported as D‑48 and correctly dismissed |

---

## 4. Rule violations

### R1 — "exactly two properties pass" is kept at 12.5%

**R1 (A :77):** *"No property may be sent whose value is a function of another property **on the same event** … **Exactly two properties pass**: `error_class` (from `outcome_code`) and `leg` (from `stage_name`). Every other derivable property shipped to date is removed in §7."*

R1 names a **closed membership**, not a three‑condition test. Four pass‑1 findings each claimed to have found "the fifth" breach, counting from four different unstated baselines. **All ordinal language is retired.** This is the definitive enumeration.

**Carve‑out — lawful as R1 is written (2)**

| # | Property | Determined by | Host event |
|---|---|---|---|
| C1 | `error_class` | `outcome_code` | `Journey Step Failed` |
| C2 | `leg` | `stage_name` | `Request Stage Changed` |

**Class A — total static value maps; unambiguous breaches (10)**

| # | Property | Determined by | Host event | Source of the map |
|---|---|---|---|---|
| A1 | `is_statutory` | `report_type` | `Document Retrieved` | §4 L9: *"twelve report types, **five statutory**"* |
| A2 | `is_recoverable` | `reason_code` | `Application Rejected` | §9 OD-9 — *"has nothing to read until"* the registry exists |
| A3 | `is_terminal` | `outcome_code` | `Journey Step Failed` | §5.8 :549, *"One meaning only"* |
| A4 | `cap_reached` | `attempts_remaining == 0` | `Journey Step Failed` | §5.4; suite :81 emits `attempts_remaining=0 · cap_reached=true` |
| A5 | `tier` | `reveal_group` | `Sensitive Value Revealed` | §5.7 :470–471, 13 values → 3 tiers, total; suite FT‑23 step 5 *"SHALL NOT vary by screen"* |
| A6 | `is_sanctions` | `reason_code` | `Application Rejected` | §5.2 — *"mapped ops list, never free text"* |
| A7 | `can_reapply` | `reason_code` | `Application Rejected` | Same footing as A2/A6; suite XC‑04 step 4 emits all three together |
| A8 | `permission_state` | `outcome_code` | `Journey Step Failed` | §5.2 `PERM_*` group → §5.8 `denied`·`blocked`; suite :189/:190 emit `PERM_CAMERA_DENIED→denied`, `PERM_CAMERA_BLOCKED→blocked`, `null` elsewhere |
| A9 | `segments_dropped` | `segments_selected` **∖** `segments_on_aof` | `Agreement Generated` | All three co‑travel (§4 :155); §5.6 fixes the nesting `segments_active ⊆ segments_on_aof ⊆ segments_selected` over 4 closed scalars, so the difference is exact. Suite :224 and :139 — 2 of 2 emissions are exact set differences |
| A10 | `errors_shown` | `len(error_codes[])` | `Element Clicked` | §5.5 :439 registers both on one event with no published distinction; suite :63, :295, :550, :583, :682 — 5 of 5 emissions satisfy the identity |

**Class B — presence‑derivability (the boolean/enum restates whether its neighbour is populated) (3)**

| # | Property | Determined by | Host event | Note |
|---|---|---|---|---|
| B1 | `requires_reverification` | `verification_method` populated | `Account Detail Changed` | §5.7 :475 enumerates 4 values, all re‑verifications, no `none` |
| B2 | `outcome` | `failure_reason` populated | `Login Completed` | §5.8 closes `failure_reason` at 5, all failures |
| B3 | `otp_outcome` | `seconds_to_entry` populated | `OTP Resolved` | §5.8 — `seconds_to_entry` is dispatch→entry, so it exists iff `otp_outcome = entered` |

**Class C — declared, reasoned, and still a third exception to a rule that says "exactly two" (1)**

| # | Property | Determined by | Host event | §7's own words |
|---|---|---|---|---|
| C-1 | `charge_paise` | `request_type` | `Request Stage Changed` | *"derivable from `request_type` **today** — but only while pricing is a three-value constant"* |

**The number to publish: sixteen properties in the current authority are a static function of another property on the same event — fourteen beyond the two R1 admits.** Not four, not five, not six, not seven. Severity **P1**.

**Fix:** restate R1 as a rule with a **listed membership** rather than a count, and publish or remove each map. A rule enforced at 12.5% is not a rule, and a reader cannot tell what is lawful to add next. **Do not remove `outcome` from `Login Completed`** — §5.4 settled that spelling deliberately across three events; fix B2 by declaring `failure_reason` explicitly nullable and null‑on‑success.

**Candidates tested and cleared — do not re‑raise:**
- `last_error_class` ← `last_outcome_code` on the **profile** — R1 is scoped to "the same event"; the profile layer is not an event.
- `was_stp` — deleted in §7. It is the precedent (*"R1's own worked example, shipped anyway"*), not a live member.
- `attempts_remaining` ← `step_name` + `attempt_index` — **conditional.** `attempt_index` is absent from `Journey Step Failed`'s §4 property list today (D‑31), so the determining property is not on the event as published. **This becomes an A‑class breach the moment D‑31 is fixed** — flag it in D‑31's fix, do not raise it now.
- `exchange` ← `instrument_id` — cleared only because §5.6 declares `instrument_id` free‑form and `NSE:RELIANCE-EQ` illustrative. The clearance depends on the format staying unregistered, which is itself unsatisfactory; register the format and this becomes a breach.
- `order_state` ← `filled_quantity`/`quantity` — partial map only; cleared.
- `granted`/`is_withdrawal` on `Consent Captured` — a decline and a withdrawal are distinct; cleared.
- `reauth_outcome` ← `tier` — partial (only `not_required` determined); cleared. Note §7 already removed `reauth_required` for the total form of this same relation.

### R3 — server emits outcomes, client emits interaction

**One breach, P2, and it is a single table cell.**

`kyc-event-spec.html:2208` renders one Source cell covering the pair `Message Dispatched · Notification Deep Link Opened` and stamps it **`server`**. A :213 sources `Notification Deep Link Opened` **client**. Every other Source chip in B §19.2 matches A; the artifact's other two multi‑name rows are homogeneous (Overlay pair = client; Vendor trio = server). This is the single mismatch.

**Compounding it:** the markdown half of the same declared artefact, C §2.2 :100–121, is a **two‑column table with no Source column at all** against B's three. A reader working from the markdown has no source assignment for any FMS event; a reader working from the rendered half has one wrong one.

**Fix:** split the pair row, set `Notification Deep Link Opened` = client at B :2208, add the Source column to C §2.2. Nothing else. **The claimed consequence — that believing the open event is server‑emitted lets FMS assume the dispatch→open join is free — is struck**: the join *is* registered, A §5.7 :484 scopes `notification_id` to both events.

**Eight further R3 findings were raised in pass 2 and all eight were refuted.** Recording the rulings so they are not re‑raised: §7's removal of the `source` property is **compelled by R1** (with one exception, `source` is a static function of `event_name`) and the fact is permanently recoverable from a 40‑row lookup; `Sensitive Value Revealed` being server‑sourced with two no‑re‑auth tiers is not a contradiction (*"no re-auth"* ≠ *"no server call"*, and TESTS :424 records *"masking is server-side (PR-31)"*); R3's *"Durations are server clocks at both ends"* governs the server class R3 defines, not the six client durations §7 deliberately declined to remove; `Consent Captured`'s "client or server" is lawful because a consent grant is in none of R3's seven named classes; `Action Blocked{stale_margin_data}` is a client interaction fact about a server‑computed state the client was given to draw; `Charged`'s missing Source cell follows from its being outside the 40‑name catalogue and outside §5.

### R4 — no regulated identifier, no hash

**No live leak. This is the cleanest area of the registry, and it should be said plainly.**

A sweep of every free‑form id property found: `source_bank_ref` holds `b1`/`b2`/`b3` in all seven occurrences (A §5.3 :380 pins it — *"Internal account id… **never** the account number, never the IFSC"*); `answer_id` is structured `HC-<TOPIC>-<NN>`; `instrument_id` is `NSE:RELIANCE-EQ`; `template_id` is `THINQ_*`; `notification_id` is `N-90144`; `element_label` passes R2. **None is a leak.** A §4 L7 :205 further pins `Sensitive Value Revealed` to *"the **group** and the **tier**, never the value or a masked derivative"*.

**One live reopening, gated and recommended against.** **FMS-OD-4** (C :1091) records: *"CleverTap-side rendering requires the UTR, last-four and amounts to travel as properties, reopening §6.1."* C §6.4 :666–673 answers it in the safe direction — R2's *"send the **template**, never the interpolated string"* plus `product-requirements.md:441`'s NFR *"Balance figures and account identifiers must not be disclosed to third parties."* R4's own closing sentence is a positive whitelist — *"Only the **shape** of the fact travels: `outcome_code`, `face_match_band`, `method`"* — and a four‑digit truncation is a partial value, not a shape, so it is already excluded. **No action beyond keeping FMS-OD-4 gated.**

**Do not** add a regex/entropy screen on `context_id`/`element_label`/`item_value`/`template_id` — it would reject `NSE:RELIANCE-EQ` and `CHG-2026-0818-4471`, both published as canonical in A §2 row 3.

**The genuine R4‑adjacent P0 is OD‑3 (position reconstruction)**, which the authority already rates P0 and owns to Compliance + Analytics. It is not a new finding.

### R5 — money in paise; what a thing cost is product data, what the customer holds is never sent

**Fine, with one internal contradiction in FMS.**

C §6.1 row 2 forbids *"the amount **returned** by a mandated settlement"*; C §6.2's final row says the amount cannot be sent **and** notes *"this one is lawful and was miscounted as forbidden."* B §22.1 row 2 omits the clause and row 8's Cannot‑send cell reads *"nothing"*. **B is right; C contradicts itself.** Failure direction is over‑suppression — an emitter drops a lawful property and F9's float‑retention KPI (*"70% surviving to the next settlement"*) loses its numerator. This is not a disclosure event. **P1.**

### R7 — every stage family registers its terminals first

**Violated in six families, and the CI check A §8 item 4 promises cannot pass as written.**

R7 requires *"a `stage_name` family SHALL register `<family>_completed` and SHALL explicitly record, with a reason, any of `_failed`, `_rejected`, `_withdrawn`, `_expired`, `_abandoned` it does not need. Enforced by CI."* §5.1 relaxes it once to *"a `_completed` (or `_active` / `_credited` terminal of record)"* and then asserts *"Every family above satisfies R7."* It does not: `bank_*` ends at `bank_verified`; `rep_*` at `rep_ready` and registers **no terminal of record at all**; `tkt_*` at `tkt_resolved`/`tkt_closed`; the 38‑slug `kyc` family at `ready_to_trade`. FMS adds two more — `due_cleared` and `shf_cleared`, each explicitly called *"the completion of record"*. **None of the six matches `_completed`, `_active` or `_credited`.**

**Fix (option b only): publish a "terminal of record" column in §5.1 and in FMS §5.4, and key the CI check on that column.** Do **not** mint literal `<family>_completed` aliases — that duplicates one transition under two values, which is the R1 problem one layer down. `rep_*` needs a terminal of record either way; `rep_ready` is a handover, not a completion. **P2**, and it is the upstream cause of `open_request_types` (the profile set defined as "requests with no terminal stage") having nothing to read.

**Also unfixed:** R7 is asserted and violated in the same §5.1 section for `unplg_*` and `ca_*` (D‑22, P1). FMS fixes `wdl_*` by explicit waiver with a reason — the right pattern — but entrenches the `_cancelled`/`_withdrawn` spelling split.

### R8 — expired ≠ failed ≠ withdrawn

**One breach.** FMS registers `FUNDS_USER_CANCELLED` with **no `fund_*` terminal to carry it**, so R8's "withdrawn" population lands on `fund_failed` alongside bank declines — and the build already merges them (`app.js:141` / `dashboard.js:132` both carry `{ id: 'abandoned', … st: 'failed' }`). This is the population FMS-OD-2 says moves the 95% KPI across its launch threshold. **P2.** Fix by recording an R7 waiver sentence in the `fund_*` paragraph stating the customer back‑out rides `outcome_code: FUNDS_USER_CANCELLED` on `fund_failed` and is excluded by filter — **do not mint a new stage**.

**R8 does not reach the profile layer.** Its operative sentence (A :91) binds *"Every module's **stage family and outcome vocabulary**"*. The pass‑2 claim that `dues_state`/`shortfall_state` breach R8 by lacking `written_off`/`expired` members was refuted: a profile state property is neither a stage family nor an outcome vocabulary, and §6.1 :616 defines these properties as carrying *"the **shape** of the account"* — present state, not resolution history.

### R9 — outcome_code is one namespace partitioned by module, `<DOMAIN>_<CONDITION>`

**Three bare codes survive** — C12 above. That is the whole R9 exposure. Two pass‑2 attempts to widen it were refuted: `SUPPORT_NO_MATCH` vs `resolution: no_match` are different strings on different properties on different events; `BANK_PENNY_DROP_FAILED` vs `BANK_PENNY_DROP_FAIL` are two strings in two registries at two grains, which is a style hazard, not a rule breach.

---

## 5. De-duplication and grain

### The published table covers 19 of the 40 names. Twenty-one have no key.

A §3's table has 10 rows, one covering a 9‑name milestone group and one covering the OTP pair = **19 names keyed**. §3 opens by conceding the fallback is *"already inadequate"* and closes with an acceptance test that says *"replay every event type twice; assert one row each"* — **no carve‑out**.

**The 21 unkeyed names of the 40** (plus `Charged`, outside the budget): `Screen Viewed` · `Element Clicked` · `Overlay Opened` · `Overlay Dismissed` · `Field Errored` · `Media Captured` · `Login Completed` · `Registration Started` · `Vendor Failure Detected` · `Attempt Cap Reached` · `Manual Fallback Entered` · `Journey Resumed` · `Ops Decision Recorded` · `Account Detail Changed` · `Action Blocked` · `Sensitive Value Revealed` · `Notification Deep Link Opened` · `Service Restored` · `Consent Captured` · `Session State Changed` · `Query Answered`.

**Severity P1** (corrected down from the suite's P0 — nothing is mis‑emitted today because nothing is emitting).

### The ruling §3 is missing, and it is forced rather than chosen

**The frozen generic six SHALL NOT be de-duplicated on a business key, and the alternative is not available.**

To de‑duplicate a generic‑six event you must key on the property that separates two legitimate occurrences. `Screen Viewed` can be entered twice with identical `entry_direction`, `referrer_screen`, `nav_source_element` (:126); `Element Clicked` tapped twice with identical `element_id`/`item_value` (:127); `Overlay Opened` opened twice with identical `overlay_id`/`trigger` (:128). **No registered property discriminates them**, so a key requires an occurrence counter — and §4 L1 is headed *"Generic UI (6, **schemas frozen**)"* while R6 routes any new property on an existing name to the registrar. Adding a counter to a frozen schema is the one move the layer exists to prevent.

**Three independent confirmations that the authority already assumes no de-duplication:** (a) :428 — *"`is_disabled: true` is *intent without ability*"* — a count that only exists if repeated taps on one disabled control produce repeated rows; (b) :609 — *"Close on `entry_direction = forward` for first-pass conversion; include `back`, `edit` and `retry` only when rework is what is being measured"* — inoperative if one `Screen Viewed` per `context_id` survives; (c) :127 — `Element Clicked` carries `seconds_on_screen`, `edits_made`, `errors_shown`, `retakes`: per‑occurrence measurements, meaningless at one row per record.

**Why it must be written down:** five of the six are `Source: client` and have no webhook, so §3's stated replay rationale does not reach them — but the acceptance test says *"every event type"* with no carve‑out, and FMS restates it as its own test 6 (C :1127). **As written, a conformance run today certifies exactly the collapse this ruling forbids, and certifies it silently, because it tests replay rather than legitimate repetition.**

**Two names are not on the frozen layer and do need keys — both at zero property cost:**
- `Attempt Cap Reached` (§4 L7, server) → `context_id + event_name + cap_type + total_submissions`. `total_submissions` is registered at :401 as *"raw submits including unchanged resubmits"*, monotonic by construction.
- `Action Blocked` (§4 :204, *"Once per refusal"*) → `context_id + event_name + blocked_reason + element_id + session_id`. All four already on the event or in the envelope.

**The load-bearing edit is the amended acceptance test:** *"replay every event type twice, assert one row each **for the keyed names**; emit two legitimate consecutive occurrences of every name, assert **two** rows."* Without it the current test certifies the failure.

### KPI grain mismatches

| Where | The mismatch |
|---|---|
| **FMS's only engagement KPI** — *"40% of users who view a withdrawable figure lower than their balance open its derivation"* | Numerator is `Overlay Opened{overlay_id: funds_withdrawable_derivation}`, denominator involves `Element Clicked`/`Screen Viewed`. **Neither name has a key in A §3 or in FMS §9.** The build counts **per view** (`dashboard.js:562`). Under the ruling above (not de‑duplicated) the KPI is coherent; under a naive read of §3's fallback it collapses to one row per record. **The KPI is uncomputable until §3 states which reading governs** |
| **D‑03 (P0)** — `Order State Changed` key is `context_id + event_name + order_state + filled_quantity` | `filled_quantity` is **never declared cumulative or per-fill**. Two consecutive `order_state: modified` rows on an unfilled order are byte-identical, so the modify‑cap funnel (`cap_type: order_replace` is registered) has no numerator. Under the per‑fill reading, every repeated partial fill at the same size is dropped and reads as a conversion cliff. **This is the identical defect §3 already fixed for `Journey Step Failed` by adding `vendor_attempt`** |
| **D‑05 (P0)** — the OTP key omits `otp_channel`, and `otp_channel` is out of scope for `OTP Resolved` | The auth PRD rules the sign‑in OTP is dispatched to **both** the registered mobile and email at once, so one `otp_purpose: login` at `resend_index: 0` produces two lawful rows the key collapses — on `OTP Requested`, where `otp_channel` *is* in scope. §4 L5's entire stated justification for buying the name `OTP Resolved` (the four auth ceilings *"have a numerator and no denominator"*) is halved by the key |
| **D‑37 (P2)** — `touch_index` is *"first touch on this **application**"* | KYC‑seeded language in a product‑wide document. FMS applies it to `context_type: service_request` records (`DU2207`, `MS8841`), which are not applications, and puts **both** escalation ladders on it after deleting the stage‑based alternatives. §3's key already scopes the counter per record, so the natural implementation is right — but the definition must be redefined as *"first touch on this `context_id`"*, one sentence. **Note:** comms §5's channel matrix varies channel membership per rung by amount band, so `touch_index` indexes **sends**, not calendar rungs; do not read it as a day index, and do not split `thinq_dues_outstanding` into per‑rung templates (comms :29–30 caps the SMS set at five for DLT approval cost) |

---

## 6. The profile layer

**36 properties. Three name a writing event. Six inherit one from a shared §5 Scope cell. Twenty-seven name none.**

Census run against A §5.9 :554–560 (30) and C §5.11 :560–573 (6).

**Named a writer in prose — 3.** §5.9 :558: *"Two properties become computable for the first time: `banks_pending` (**via the new `bank_*` family**) and `docs_downloaded_30d` (**via `Document Retrieved`**)"*, and *"`consent_state` is a **map** of `artefact_code → granted`, **recomputed from `Consent Captured`**"*.

**Given a writer only by a shared §5 Scope cell — 6.** `segments_selected` (:449) · `segments_on_aof` (:450) · `segments_active` (:451) · `dropoff_class` (:535) · `permission_state` (:537) · `journey_variant` (:546).

**No writer named anywhere — 27.** Nineteen have an obvious unstated candidate: `kyc_stage_name`/`kyc_screen_name`/`kyc_step_name` from the envelope; `last_outcome_code`/`last_error_class` from `Journey Step Failed`; `registered_at`/`kyc_started_at`/`kyc_submitted_at`/`ptt_at` from their milestones; `marketing_opt_in` from `Consent Captured{artefact_code: C-MKTG}`; `banks_linked`, `nominee_count`, `nominee_outcome`, `ddpi_active`, `last_reveal_at`, `first_deposit_at`, `last_deposit_method`, and the raise/clear halves of `dues_state`/`shortfall_state`. **Eight have no candidate at all**: `kyc_status`, `dropoff_at`, `holdout_group`, `resume_deeplink`, `settlement_cycle`, `open_request_types`, `deposits_90d`, `funds_state`.

**"Stamped server-side" is the whole mechanism for the three funds states.** C §5.11 :568 — *"The `screen_name` set as a *state*, **stamped server-side**"*. `dues_state` and `shortfall_state` carry no source phrase at all. A case‑insensitive grep of both documents for `stamp` returns, besides that cell, only envelope rules about **events** (A :65 — *"State is **stamped at emission**, never read live from the profile"*).

### Why this is a self-inflicted gap, not an inherited one

A's frontmatter supersedes `THINQ_KYC_ONBOARDING_PRD.md §22.0–§22.5`. **§22.3 (:3166–3177) carried a "Source of truth" column and was the only place in the corpus where profile properties named one.** Ratification therefore *destroyed* the specification layer without replacing it. §5.9's heading — *"the profile layer is unchanged bar two"* — is false on its own terms once §22.3 is superseded.

**Severity P2, not P0.** These properties are not *uncomputable*: 19 of 27 have an unambiguous inferable writer. The concrete exposure is that **three teams will infer three different writers and three different recompute cadences for the same property** — precisely the failure §5's per‑property Scope column prevents everywhere else in the document. §5.9 is the only §5 subsection with no Scope, no Values, no Type and no Nullability column, and §13's ten acceptance tests exercise **no profile property at all**.

**Fix:** give §5.9 and §5.11 the same table shape every other §5 subsection has — **one row per property with Values · Type · Written by (event or named server job) · Recompute cadence**. Populate the 9 that are determined and the 19 that are inferable; mark the 8 that are not as open with an owner. Correct the *"unchanged bar two"* heading. Add one §13 test: for each profile property, assert a write occurs within the stated cadence of its stated trigger.

### FMS registers six profile properties and never schedules them into the register that governs them — P1

C :586 counts *"| New profile properties | **6** |"* and :41 declares *"FMS registers 74 values, 4 property claims, **6 profile properties** and 0 event names."* C §12 *"What changes elsewhere on adoption"* (:1103–1119) schedules **nine rows, seven of them precise section-level edits to A** — §5.7 twice (`channel` gains `sms`; `reveal_group` gains two), §5.8 (`service_id` gains three), §5.1 (`stage_name` gains 17), §2 (`context_type` gains `account`), §3 (the de‑duplication table gains four rows). **There is no row for §5.9.**

The pattern is otherwise exceptionless — every other thing FMS adds to A gets a §12 row. So this is a self‑inconsistency inside FMS. The consequence is checkable: A :554–558 remains a **30‑item list with no funds state of any kind**, so A §8's downstream migration table has nothing to propagate, and the six new properties inherit none of §5's discipline: no scope, no nullability, no CI. It is the same defect as NAME‑02 (§6's event column), one layer down. **Six today; twelve modules will each want their own state scalar, and none of them will land in §5.9 either.**

**Fix:** add one §12 row — *"`THINQ_EVENT_TAXONOMY.md` §5.9 — gains six profile properties: `funds_state`, `dues_state`, `shortfall_state`, `first_deposit_at`, `last_deposit_method`, `deposits_90d`"* — and add a §13 acceptance test that greps every profile property FMS names against §5.9, exactly as test 2 already greps event names against A's 40. **The mechanism exists and is simply not applied to this layer.**

### What is fine on this layer, in one line each

`consent_state` as a **map** is lawful — the CleverTap constraint the pass‑2 lens cited bans arrays *of objects* on **event** properties, and it lives in superseded §22.4 anyway. `account_state`/`engagement_state` are not "missing" from §5.9 — they are envelope properties stamped on every event, and a profile mirror is exactly what R1's principle exists to eliminate. `segments_active` has a stated writer: §5.6 :462 — *"evaluated server-side against the underlying segment records and **never** against these scalars."* Rolling‑window counters (`_30d`, `_90d`) are decayed by the profile store; demanding an event for the decrement is a category error.

---

## 7. Drift between documents — per pair, with the ruling on each stale document

### A ↔ C (`THINQ_EVENT_TAXONOMY.md` ↔ the FMS registration)

| What | A says | C says | Ruling |
|---|---|---|---|
| Events carried by `module: funds` | §6 row 6 :575: **7 names**, no spine, no `Screen Viewed`/`Element Clicked`, no `Sensitive Value Revealed`, no OTP | §2.2: **19 names** | **A's §6 row 6 is the defect (P1).** Rewrite to FMS's 19 plus `OTP Requested`/`OTP Resolved` — both of which A itself already justifies for funds (§4 L5 names *"funds (withdrawal confirm)"*; §5.8 registers `otp_purpose: withdrawal_confirm`). **Do not add `Ops Decision Recorded`** — B :2674 itself labels it *"Another module's event"*, §2's destination rule puts it outside `module: funds`, and the FLOW C3 join is on `context_id`. **Add the §6 row 6 edit to C §12** — it is not there |
| `funding_method`, `amount_paise` scope | §5.3: `Request Stage Changed` only | §4 prose widens to the spine; §5.12 counts 4 claims | **C is under-declared.** See C1 |
| `otp_purpose: withdrawal_confirm` | §4 L5 names funds one of five `OTP Resolved` consumers | §2.3: *"registered and **has no emitter**"* | **Not a defect — a disclosed bank.** C :133–137 states it plainly and points at the remedy: comms §11 C‑Q8's recommended option A (an OTP on the withdrawal request) *"therefore costs **zero** taxonomy change."* One product decision restores the fifth consumer. What *is* missing is an adoption row |
| `amount_source`, `source_bank_ref` | §5.3 registers both specifically for `module: funds` | **Zero occurrences in C.** Neither adopted, waived nor retired | Add an explicit adoption line to C §5.9. F5's destination‑fixed‑at‑request rule already depends on `source_bank_ref` |
| `FUNDS_REVERSED`, `overlay_id: funds_source`, `element_id: funds_chip`/`funds_add` | Registered for funds | Zero occurrences; C mints `fund_reversed` + three new reversal codes, 6 new overlay ids, 14 new element ids | **`FUNDS_REVERSED` needs an explicit supersession line** — C already knows the pattern, it uses it for `unsettled_funds` and `funds_balance`. The three ids are open engineering‑owned registries; a note is housekeeping, not a defect |
| `context_type` for an account surface | 12, closed, non-nullable | FMS-OD-1: register a 13th value `account` | **Take the suite's fix, not C's.** See §11 |
| The adversarial suite | — | C's frontmatter names one governing document and never cites `THINQ_EVENT_TAXONOMY_TESTS.md`; grep returns 0 hits for `D-01` | Add the suite to C's `related:` block. **C's claim that other modules are *"silent about it"* is false as of 18 Aug** and reads to the registrar as FMS finding it first |

### C ↔ B (declared **one artefact**: *"a change to either is a change to both"*)

| What | C (md) | B (html) | Ruling |
|---|---|---|---|
| Names FMS emits | "twelve" ×2, "twelve to fourteen" | "fourteen" | **19 names across 15 rows.** Retitle all four locations identically, delete the hedge. (The claim that §13 test 2 needs a flat name list is wrong — it greps against A's 40 and works today) |
| Source column | **absent entirely** from §2.2 | present, and wrong on one cell | See §4 R3 |
| F3 payin funnel | node 6 `fund_approved` → margin, **after** node 5 | §21 B1 orders them correctly, margin at step 7 = `fund_credited` | **B is right.** See C2 |
| Mandated-settlement amount | §6.1 row 2 forbids it; §6.2's final row says it is *"lawful and was miscounted as forbidden"* | §22.1 row 2 omits the clause; row 8 Cannot‑send reads *"nothing"* | **B is right. C contradicts itself.** See §4 R5 |
| `amount_source` | absent | §21 B1 step 2 and §22.2 row 4 attach it to `Element Clicked`/`Screen Viewed` | **Keep it on `Request Stage Changed` where A put it.** Rewrite B's two rows to read the chip from `Element Clicked{element_id: funds_amount_suggestion, item_value}` alone. (These are funnel Identified‑by columns, not payload schemas, so the frozen layer is not actually breached — but the text invites the breach) |
| `trigger` on the derivation overlay | no `trigger` value anywhere | `Overlay Opened trigger = user` at :2522 and :2712 | **`trigger = cta`.** `user` is not one of A §5.5's five closed values, and :2712 is by B's own label the module's headline 40% KPI |
| Blind-spot register | 7 rows, including *"Whether a user read an SMS"* | §23.2 has 6 — the SMS row is absent | Add the row to B verbatim. It is the one blind spot attached to a **regulatory-bypass** message path (comms D1 requires SMS for shortfall and dues) |

### C ↔ `fms/product-requirements-communications.md` — **new in pass 2, and the worst-documented pair in the corpus**

| # | Sev | What |
|---|---|---|
| **1** | **P2** | **F9's first node filters on a message the comms PRD does not specify anywhere.** C :902 F9 node 1 = `Message Dispatched{message_type: thinq_rac_advance_notice}` — *"Rule W8's announcement, **before** the date"*; C :352 rules it deliberately (*"a `Message Dispatched`, not a stage"*). The owning file does not have it: a grep for rac/mandated/settlement/unused across `product-requirements-communications.md` returns only **Rule C10** at :253 — *"Unused funds are returned on the mandated calendar without anyone asking"* — a copy rule with **no template, no channel and no timing**. §1's "States covered" digest (:22) lists four state groups and not this one. §5's channel matrix (:174–180) has five rows and not this one. §4's generated catalogue (:86–163) has no such message. **§9's deliberately-not-sent table (:318–326) does not list it either** — the gap survives review by falling between two tables that are each individually complete. The requirement is real and owned: `product-requirements-withdraw-funds.md:191` Rule W8 — *"It is **announced before the date**, executed on it, **notified after it**"* — two distinct messages, and the build implements only the second (`app.js:2088`, asserted at `.test-assert.js:178` under a rule id that does not exist). **Consequence: F9 loses its first node.** Its 70% float‑retention KPI survives — C :911–913 computes it from `amount_paise` on `rac_credited` against `fund_credited` inside node 2's window, not from node 1. **Fix:** add the advance‑announcement template to `web/app.js` and extend `gen-comms.sh` so it lands in comms §4 automatically (the generated section is the catalogue of record), add the row to §5's matrix by hand, and mark F9 node 1 **not-yet-computable** in C §7 until it ships |
| **2** | **P2** | **The build cites the comms rules by ids the PRD does not contain.** comms :5 declares *"Rules **C1 – C17**"*; the body defines exactly C1–C17 (verified: 17 `**Rule C` headings). **Three cited ids do not exist**: `app.js:1614` "(Rule C18)", `app.js:1967` "Rule C22 — email is the only channel that may use structure", `.test-assert.js:178` "Rule C21 automatic return is its own message". **At least eight more point at the wrong rule**: `app.js:1924` cites C3 for the trailer (it is **C14**, :273 *"Every message carries a reference"*; C3 is *"Success is a receipt; failure is news"*); `app.js:1942` cites C9 for channel assignment (C9 :246 is *"Withdrawal notifications are not the account-takeover control"*); `app.js:1955/:2027/:2102` carry the **runtime string** `fallback: '… — Rule C10'` for the WhatsApp→email fallback (that is **C4**, :187); `app.js:2126` "Rule C15: amounts in full" (amounts-in-full is **C17** :289 — **C15 :276 is *"Never send a full bank account number"***); `app.js:2149`/`:2154` miss on C5 and C7. `.test-assert.js` repeats the numbering at :130, :146, :159, :162, :163. **Why it matters:** comms §4 opens by claiming the catalogue *"is emitted from the same definitions the running code reads, so a copy change lands here without anyone remembering to update it"* — the document's stated anti-drift defence is that the code is the source, and the code and the document disagree about what the rules are. And the direction of the C15 error is the dangerous one: anyone reading `app.js:2126` to find the PII rule finds a formatting rule. **Two of these are not comments** — the `Rule C10` string is shipped message metadata. **Fix:** one correction pass; **promote C18 and C22 into comms §8 as real rules** (both name substantive behaviours with no home) rather than deleting the citations; then add a guard to `.test-assert.js` extracting every `Rule C\d+` and `REQ-6\d+` from `app.js` and asserting each appears in the PRD — the same shape as the existing `no Math.random` / `no wall clock` checks in `.dash-assert.js:33-34` |
| **3** | **P2** | **Eighteen of the twenty-seven communications requirements have no text in the owning file, and four ids are each defined twice with different content.** comms :5 declares *"Requirements **REQ-601 – REQ-627**"*. The body defines **nine**: REQ-619 (:198), 620 (:201), 621 (:204), 622 (:232), 623 (:235), 624 (:238), 625 (:304), 626 (:307), 627 (:310). REQ-601's only other occurrence is :267 citing itself. **Four collide**: REQ-621 = *"The success email shows what the payin changed"* (:204) **and** *"Every message is generated from the same `derive()` result as the screen"* (:292); REQ-622 = cancelled-withdrawal-email-only (:232) **and** event-queued (:295); REQ-623 = partial-transfer-both-channels (:235) **and** per-channel delivery logging (:298); REQ-624 = terminal-withdrawal-states-where-the-money-is (:238) **and** WhatsApp opt-in (:301). **FMS hangs real registrations on both problems**: C :573 justifies the registered profile property `shortfall_state` solely as *"The REQ-601–604 ladder and its regulatory bypass audience"*; :857 cuts F7 node 2 by `touch_index` *"for the REQ-601–604 ladder"*; :868 says node 3 *"measures REQ-605's deep link"*; :385 rules out a `shf_escalated` stage because *"The REQ-601 to REQ-604 ladder is a *communications* fact"*; and :337/:208/:210 cite the two duplicated ids. **The corrected reading:** the 18 are not textless — `product-requirements.md:103–109` is a routing table giving each a one‑line statement, and the substance is carried by Rules C1, C2, C11, C12, C13 and §9's *"Day 0, 7, 14, 30, then monthly"*. **And §8's REQ-621–627 is the CORRECT block** — `product-requirements.md:109` assigns exactly those ids to *"Governing rules: one source of figures, event-queued, delivery logging, opt-in, versioning, preferences, reachability"*. **Fix (direction matters):** renumber the four in §6/§7 into the ranges the same routing table already reserves — :198/:201/:204 into REQ-611–615 (fund-addition messaging), :232/:235/:238 into REQ-616–620 (withdrawal messaging) — then update FMS's citations at :208, :210, :337. **Do not renumber §8.** Until REQ-601–618 are published in full, C §5.11's `shortfall_state` row should cite **Rules C11/C12/C13 plus §9's calendar**, which exist and carry the regulatory‑bypass and cap semantics |

### A ↔ `kyc-event-spec.html` §01–§17

| What | Status |
|---|---|
| Nine value spellings A renamed are still published: `pan_verify`/`face_match` (§05:555), `mobile_to_bank` (§07:973), `broker_submission`/`exchange_submission` (§06:851), `stage_code`/`previous_stage`/`hours_in_previous`/`raised_via`/`esign_used` and a four‑value `leg` (§14:1815), `account_state` as "12, closed" (:369) | **Unfixed.** (`BELOW_MINIMUM` and `service_id: upi_collect` *are* covered by the red supersession block at :1951) |
| **Two live splits inside one page**: `cap_type: bank_manual` (:671, :1080, :1620) vs `bank_manual_entry` (:1796); `surface_type` at eight values without `menu` (:431, :1344) vs `surface_type: menu` emitted (:1961) | **Unfixed**, on a page that certifies at §17 that *"Every property appears in exactly one glossary with one vocabulary"* |
| §17 resolution chips: R‑4 "fixed — two **non-overlapping** grains" (A: *"the **same subjects at two resolutions**"*, split into `reveal_group`); R‑6 "fixed — a Profile freeze emits `freeze_type: trading`" (A: *"the Profile ruling is **overturned** … Origin names it"* → `voluntary_client`); R‑1 `context_type: profile_request` (A: `service_request`); R‑8 and R‑9 still "open" although A registered `Document Retrieved` and `account_state: closing`; sanity note still 31 names and `segments` | **Unfixed**, and both R‑4 and R‑6 are named verbatim in A §8's downstream table. **A `resolved` chip is a stronger signal than a page banner** |
| Three mutually incompatible budgets on one page | See §2 |

**Ruling: prefix §04, §05, §06, §07, §10 and §14 with the same red supersession block §15 already carries** — the pattern is precedented on the page. §15 needs no second block.

### A ↔ `THINQ_KYC_ONBOARDING_PRD.md` §22 — **none of A §8's nine corrections has been applied**

§22.2 (:3033) still headed *"28 names"*, still specifying `KYC Step Completed/Failed/Abandoned`, `AOF Generated`, `channel_used`, `resend_count`, `was_stp`, `manual_touches`, `aof_version`, `mode (LOG-00…04)`. §22.2a (:3096) still lists `Help Requested`, which A §7 **refuses to register**. :2762 still de‑duplicates on `case_id + event_name` while §22.1b's own obsolescence table renames `case_id` → `context_id`. :2734 still says 482 remaining and still names `segments` rather than `segment_list`. §22.1d carries the 53‑code list under a header of 54; §22.1e has no `dropoff_class` row.

**Ruling: replace §22.0–§22.5 wholesale with a pointer**, as the FMS PRD did with its own §Tracking Requirements. Nine corrections across six subsections of a section A already declares superseded in full is more work than deletion and leaves a second source alive. **Note that this deletion removes §22.3, the corpus's only "Source of truth" column for profile properties — §5.9 must absorb it first (see §6).** And A §8's instruction *"correct the outcome_code count to 54"* must be reissued as **53**.

### A ↔ `THINQ_PROFILE_PRD.md` §10a — **the highest-value migration item, and the only downstream document A does not supersede**

A lists it under `related` only and §8 orders it **edited in place**. Nothing has been edited. **P0.**

| Line | Still specifies | A requires |
|---|---|---|
| :1943 | `request_id`, `stage_code`, `previous_stage`, `hours_in_previous`, `is_terminal`, `raised_via`, `esign_used` | removed / renamed |
| :1944 | `control_id`, `surface` | removed |
| :1945 | `field_group`, `reauth_required`, `surface` on the reveal event | `reveal_group`; `reauth_required` removed |
| :1972, :1988 | `raised_via` **and** `initiated_via` both defined | merged into `initiated_by` |
| :1981 | *"`tier` \| `A` · `B`. `C` never appears"* | three values incl. the new `F` |
| :1987 | value `field_group: account_state` | renamed `lifecycle` (*"used the exact string of an envelope property as a value"*) |
| :1970 | `request_type: closure`, `segment_activate` under profile | `account_closure`; `segment_activate` moves to `segment_activation` |
| :2012 | contact-change funnel *"→ e-Signed → **`Account Request Raised`** → `CHG_COMPLETED`"* | A §8 orders the node deleted; §7 lists the name among those that *"SHALL NOT be reinstated"* |
| :134, :1978 | `stage_code` *"**40 values**, six families"* | A says 46 in three places — **A is wrong, the PRD is right** |
| — | **No `bank_*` family, no `request_type: bank_add`** | FMS F11 filters on exactly `Request Stage Changed{module: profile, request_type: bank_add, stage_name: bank_verified}` |

A §8's correction row for this document names four removed properties. **Four more are genuinely unaddressed anywhere: `esign_used`, `funds_topped_up`, `hours_open`, `reauth_required`.** (`previous_stage`, `hours_in_previous`, `raised_via`, `initiated_via` and `stage_code` *are* covered by §8's own rename paragraphs one line below — do not restate them.) **The `hours_in_previous` → `seconds_in_previous` migration is × 3600**, and §8's phrasing *"divide by 3600 → multiply by 3600"* must read cleanly or every SLA number moves silently.

### A ↔ remaining PRDs

| Document | Drift | Sev |
|---|---|---|
| `THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md` | **0 occurrences of `outcome_code`, 0 of `Session State Changed`.** :519 audits five session states in prose with `idle_warned` absent despite the PRD's own 14:30 pre‑warning. A proposes nine `AUTH_*` codes against a module whose PRD enumerates none (OD‑6) | P2. **Do not strike LOG-04** — :512 is an *audit-log* line; A removes LOG-04 only from the `mode` property on `Login Completed`, and LOG-04 remains valid at :49, :68, :192, :202, :204 |
| `THINQ_TNC_PRD.md` | See C14 | P2 |
| `THINQ_KYC_PANEL.md` | `sendback` (C13). Registers only four of A's seven `decision` values — `query`, `hold`, `clear` have no panel action behind them, which is either a panel gap or three values A registered speculatively. **`hold_type`'s four values are grounded in the panel's L3 routing list (:462, :467)** — a modelling preference, not an orphan | P2. **Not in A §8's downstream table at all — add it** |
| `THINQ_CUSTOMER_SUPPORT_PRD.md` | H41 (:1346) specifies a live parameter `?as=guest\|new\|kyc\|submitted\|active` against A's `visitor_state: logged_out · no_application · in_progress · submitted · active` | **P3, not a defect.** `?as=` is a demo URL query parameter in a prototype, not a registered event vocabulary; AC-14 already rules *"State SHALL come from the session and the KYC stage"*, and H41 is already logged as a P0 in its own PRD. No taxonomy action |

### C ↔ the build (`fms/web/app.js`, `dashboard.js`)

C claims F3/F5/F6 *"are written to match its `metrics()` definitions exactly, so the prototype's numbers and production's numbers are the same query."* **Two places where they cannot be:**

- **`app.js:490` bundles NEFT and IMPS** into one route (`{ id: 'neft', n: 'Bank transfer (NEFT / IMPS)' }`, mirrored `dashboard.js:118` with `observable: false`) while A §5.3 registers `imps` and `neft` as two of eight values and F3 excludes `funding_method: neft` only. IMPS deposits are either mislabelled `neft` or land inside a rate the PRD says must exclude them. **No route-id → `funding_method` mapping is published anywhere**, so the `upi` route id is also ambiguous between `upi_collect` and `upi_intent` — the axis the 95% first‑try KPI is broken down by.
- **`app.js:141` / `dashboard.js:132`** both carry `{ id: 'abandoned', … st: 'failed' }`, merging the customer back‑out into bank declines — the population FMS-OD-2 says moves the 95% KPI across its launch threshold. See R8 above.

### Ruling on `Thinq_Arvind/architecture/event-model.md` — **delete the body, replace with a five-line stub**

It is the shortest and most readable description of the event model in the corpus and the one a new engineer or agent reaches for first. Line 3 points at two **superseded** sections as its source of truth. Line 11 publishes *"Six layers · 28 names"* with four names that no longer exist. Its cross‑cutting row is 6 (now 7). **Layer 7 of the ratified model — `Request Stage Changed`, `Action Blocked`, `Sensitive Value Revealed` — is absent entirely.** Line 25 says *"eight design rules"* against nine. Line 43 de‑duplicates on `context_id + event_name`, which A calls *"already inadequate"*. Line 61 points at A1–A13, replaced by OD-1..OD-10.

It carries no supersession banner and — decisively — **is absent from A §8's downstream table**, so nothing in the ratified migration plan will ever reach it. **Do not rewrite it as a summary; a summary is what produced the drift.** Stub it to title, one sentence, a pointer to THINQ-EVENTS-001 v1.0.0 and the test suite. Point `[[02_ARCHITECTURE]]` at A §1 and `[[13_OPEN_QUESTIONS]]` at A §9. **And add the file to A's frontmatter `supersedes:` list**, or the stub has no authority behind it and the drift reappears at the next rewrite.

---

## 8. D-01..D-50 status and every unfixed P0

**Fixed in the authority: 0 of 50.** A is unchanged at v1.0.0 since 17 Aug; the suite ran 18 Aug; FMS registered 19 Aug without citing it.

**Fixed downstream by FMS: 1 fully, 2 partly.**
- **D-30 fixed** — `reveal_group` gains `available_margin` and `withdrawable` (C §5.9, routed to A in §12).
- **D-22 half-fixed** — `wdl_*` gains `wdl_under_review · wdl_part_sent · wdl_nil_settled · wdl_bank_returned · wdl_rail_queued` and an explicit `_expired` waiver **with a reason** — the right pattern. The `_cancelled`/`_withdrawn` spelling split is **entrenched**, not fixed.
- **D-24 part-fixed** — the payout-rail `service_id` and the margin-shortfall family are closed; fourteen moments remain.

**Standing after adversarial refutation: 21 of 50.** D-01, D-02, D-03, D-05, D-07, D-11, D-12, D-13, D-18, D-22, D-23, D-26, D-29, D-33, D-34, D-37, D-40, D-41, D-42, D-45, D-46.

**Refuted or dismissed: 29 of 50.** D-04, D-06, D-08, D-09, D-10, D-14, D-15, D-16, D-17, D-19, D-20, D-21, D-25, D-27, D-28, D-30, D-31, D-32, D-35, D-36, D-38, D-39, D-43, D-44, D-47, D-48, D-49, D-50 — plus D-24 as filed.

**The four dominant refutation patterns, named because they will recur:** (a) *registering* an unregistered value is free under R6 and needs no review, so *"no lawful value exists for module X"* is not a defect for a module that has not onboarded (D-08, D-10, D-16, D-17, D-06); (b) open registries (`screen_name`, `message_type`, `element_id`, `overlay_id`) cannot have unlawful values by construction (D-15, D-16, D-17); (c) several "defects" are declared, owned, severity-rated open decisions restated under a new number (D-14/OD-5, D-15/OD-10, D-44/OD-1, D-50); (d) the per-module wrapper table validates the **six 🔶 module-scoped enums only**, so three findings predicting wrapper rejection of `funding_method`, `initiated_by` or a stage/code pair are wrong (D-09, D-32, D-35).

### Severity corrections applied in pass 2 — these override the suite and pass 1

| Finding | Was | Now | Reason |
|---|---|---|---|
| D-02 | P0 | **P1** | Nothing is emitting; the harm is a certified-wrong conformance test, not lost rows |
| D-07 | P0 | **P2** | A genuine §4↔§5 contradiction, but `report_type` gains values free under R6 |
| D-11 | P0 | **P1** | Real and unsourceable, but blocks no funnel until first emission |
| D-12 | P0 | **P1** | Ruled in pass 1 as one defect with NAME-02 and SYNC-19: §5 is the operative registry and §8 item 1 names §5, not §6, as the CI diff target — so nothing is mis-emitted. But §6 is the only per-module build specification in the document, it under-counts six modules, and its closing sentence presents that column as proof |
| D-13 | P0 | **P1** | A compliance gap with no vocabulary implication until compliance rules |
| D-37 | P0 | **P2** | The wording defect is real; the claimed consequence is not — §3's key already scopes the counter per record |
| SYNC-02 | P0 | **P1** | Re-cut as C2. The "sixth step neither can express" claim and the "no second timestamp" claim are both **withdrawn as refuted** by `dashboard.js:280/308/497` (`usableAt === credited`; `speed.p50` = commit→credit) |
| SYNC-10 | P0 | **P1** | Superseded range; the fix is deletion, not correction |
| PROP-09 | P0 | **P1** | Undeclared claims against a wrapper table that does not yet exist |
| D-33 / NAME-04 | P1 | **P2** | §4's R6 table charges both names to real consumers and §6's Events-used column lists them for four modules — nothing is ownerless; the column simply does not sum |

### Every unfixed P0 — there are four

| ID | Title | Why it is still P0 |
|---|---|---|
| **D-01 / FMS-OD-1** | `context_type` is 12 closed values, non-nullable; 41 of 77 test cases cannot construct an envelope | **The single emission blocker.** Blocks the first node of F2, F3, F5, F6, F11. Unrescued by `account` alone: `Session State Changed` (all six values), `Login Completed`, and the pre-auth `Consent Captured` at the cookie banner that §4 L9 mandates all need `session` or a nullability rule |
| **D-03** | `Order State Changed` key is `context_id + event_name + order_state + filled_quantity`; `filled_quantity` is never declared cumulative or per-fill | Two consecutive `order_state: modified` rows on an unfilled order are byte-identical, so the modify-cap funnel has no numerator; under the per-fill reading, every repeated partial fill at the same size is dropped and reads as a conversion cliff. **Blocks `orders`, not FMS** |
| **D-05** | The OTP de-duplication key omits `otp_channel`, and `otp_channel` is out of scope for `OTP Resolved` | One `otp_purpose: login` at `resend_index: 0` produces two lawful rows (mobile + email) that the key collapses. **Blocks `auth`, not FMS.** The fix must cover **both** `OTP Requested` and `OTP Resolved` |
| **SYNC-11** | `THINQ_PROFILE_PRD.md` §10a still specifies every property A removed from it, plus the funnel step A ordered deleted | A **documentation** P0: it blocks no emission, but it is **the only downstream document A does not supersede**, so it is a live second source that A §8 explicitly ordered edited in place and nobody edited. Fifteen targeted changes, listed in §7 |

**Not blockers, despite appearances:** D-03 and D-05 belong to `orders` and `auth`, neither of which has onboarded — they must be fixed before those modules emit, not before FMS does. `Journey Step Abandoned` firing nowhere is OD-5, declared and owned. Every *"module X has no lawful value for Y"* finding against an unbuilt module is R6 working as designed.

**Test-suite defects to correct in the same pass:** D-40's 22 → 26 with the four missed members named; the addressable total 222 → 223; the coverage table's *"of 54"* → *"of 53"*; and the `fallback_used`/`fallbacks_used` two-spelling claim struck.

---

## 9. What is unverifiable — the self-certified half of the registry

This is a limitation of the audit and it must be stated, because the audit's standard is *"confirmed on both sides in the primary sources"* and **that standard cannot be met for roughly half the registry.**

**No source PRD exists anywhere under `/Users/arvind.thinq/Documents/AI Projects` for twelve of A §6's eighteen modules.** A `find` for order/portfolio/ipo/alert/market/chart/research/mutual/referral/corporate PRDs returns nothing.

**What this makes unfalsifiable:**
- §5.1's `ipo_*` (10 values), `ca_*` (6), `sip_*` (6), `ref_*` (5), `plg_*`/`unplg_*` (9), `seg_*` (13) stage families.
- §5.6's `order_type`, `validity`, `order_state`, `product_type`, `exchange` enumerations.
- `charge_category` — named on `Charged` with **no §5 row and no values at all**, on the one name A §9 OD-4 says is *"not retrospectively fixable"*.
- §6's *"registers values only, 0 new names"* claim for rows 1, 7, 8, 12, 13, 14, 16, 17, 18. **NAME-02's charge against the funds row (7 events listed vs 19 emitted) is untested for the other seventeen rows** — and funds was the *only* row with a registration to test it against.

**Nine of eighteen modules have no finding against them at all** — `marketing`, `markets`, `charts`, `alerts`, `research`, `mutual_funds`, `ipo`, `segment_activation`, `referral`. Two more are near-empty: `orders` (D-03 only) and `corporate_actions` (D-22/D-34 in passing). **Absence of findings there is absence of evidence, not evidence of soundness.**

**This is not a defect in the taxonomy — A §9 declares it.** *"This document registers `context_type` values, stage families and outcome codes for products that may not exist — cheaply and additively, which is the right bet either way, but the speculation is declared rather than hidden."* Two pass-2 findings attacking this were refuted on exactly that ground. **But A §9's own closing paragraph makes it a decision someone must take:** the module registry marks `ipo`, `corporate_actions`, `portfolio · pledge` and `orders · gtt` as **Live** while the customer-facing help centre publishes the opposite — *"IPO applications not currently supported"*, *"pledging not on Thinq yet"*, *"mutual funds not yet"*. **Someone must say whether the module registry is a build roadmap or a statement of what exists.** The two readings produce different budgets and different priorities.

**Two smaller unverifiable areas:**
- **FMS registers ahead of the build routinely and legitimately** — `rac_*`, `due_*`, `dues_settlement`, `margin_shortfall` are registered for surfaces nobody has built. That is the additive-only design working. It does mean nothing in FMS §5 can be checked against a running system.
- **The three comms-side pairs (§7 C↔comms) are checkable only against a demo build.** `web/app.js` and `gen-comms.js` are the *only* place several of these facts exist; where the PRD and the build disagree, neither is self-evidently the source of record.

---

## 10. Reuse and consolidation, ranked, with named second consumers

**All fifteen pass-1 REUSE findings were refuted on their merits, and the refutations are correct. The taxonomy's reuse discipline is genuinely good and this should be said plainly.** `stage_name` should not be dismantled into three enums (R1's carve-out names `stage_name` as the *primary* fact and `leg` as the permitted derivation). `blocked_reason` and `outcome_code` should not merge (client refusal vs server rejection, R3). `Mobile Verified` should not be retired (`DUPLICATE_IDENTIFIER` and `AUTH_CONTACT_COLLISION` mean a resolved registration OTP is not an identity binding). `Vendor Failure Detected` and `Service Restored` should not merge (different triggers, and `audience_size` makes the messaged set a join rather than a maintained list).

**What *should* be generalised now, because a named second module already needs it:**

| Rank | Generalise | Named second consumers | Why now |
|---|---|---|---|
| **1** | **`context_type` gains `account` and `session` in one pass**, plus a nullability rule for `module: marketing` pre-auth | **`account`**: profile home, portfolio holdings, markets watchlist, support `account_lookup` — all with the identical gap. **`session`**: auth (`Session State Changed`, all six values), charts (`state_intact` chart persistence) | A §2 row 4 mandates it — *"Done in one pass, not per module — nine uncoordinated additions produce nine grains that do not join."* Accepting FMS-OD-1 as a one-value change is the exact pattern A was written to prevent. **Drop `basket`** — an order basket is a record and `context_type: order` covers it; `basket` is registered only as a `sub_module` |
| **2** | **`funding_method` scope widened to `Journey Step Completed`/`Failed`** in §5.3, authority-side, on the recorded `state_intact` precedent | **`ipo`** — already registers `funding_method: upi_mandate` and has the identical submit-then-settle split | It is being emitted there by FMS regardless. Recording it in §5.3 costs one Scope-column edit; leaving it in prose costs F4 |
| **3** | **`otp_channel` scoped to `OTP Resolved`, and added to the OTP de-dup key for both events** | Every module in §4 L5's five-consumer list; the **profile** contact-change dual dispatch and the **auth** dual-OTP sign-in | Required to fix D-05 at all. Cannot be deferred to a module registration — the key lives in §3 |
| **4** | **`deduction_reason` registered product-wide, not as a funds claim** | **`portfolio`** — C §5.10 already names it in R6's own words. **`orders`** carries the same axis folded into `blocked_reason` | FMS filed the claim correctly; the registrar should grant it at product scope so portfolio does not re-mint it. Rename the colliding value first (C8) |
| **5** | **`transition_index` instead of `settlement_run_index`** — a server-assigned monotonic counter on `Request Stage Changed` and `Order State Changed` | **`ipo`, `support`, `profile`, `reports`** (the suite's F-2 list); and **it is the fix for D-03**, where no per-family alternative exists | Two documents recommend the same thing to the same unassigned registrar. The generic property subsumes the read; keep `settlement_run_index`'s business semantics as a separate integer if the payout-run number is wanted. Nothing is emitting, so the merge is free |
| **6** | **`request_type` added to `Action Blocked`** | **`profile`** (three distinct locks — contact change, nominee change, closure — all collapsing onto one `request_in_flight` value), **`funds`** | §5.2 and §7 justify deleting two `blocked_reason` values by naming *"`request_in_flight` plus `request_type`"* — a mechanism that does not exist on the event. `Action Blocked` is Layer 7, not the frozen generic layer, so R6 permits it |
| **7** | **`file_type` scoped to `Document Retrieved` and mandated MIME** | **`reports`**, **`funds`** (FMS commits to bare `csv`), **`kyc`** (the upload path naturally produces MIME) | `docs_downloaded_30d` is the profile property §5.9 says becomes computable *for the first time* via this event — and it would be computed over a split vocabulary from day one |
| **8** | **Publish the `reveal_group → tier` map as a table in §5.7** | **`funds`** (two new values, `available_margin` and `withdrawable`, currently untiered), **`portfolio`** (`holdings_value`, `pnl`, `tier: F`) | Prefer this to removing `tier` (R1 A5): removing it leaves `Sensitive Value Revealed` with two properties and pushes the tier-A re-auth compliance count into a query-time lookup no document owns — and OD-10 shows unowned registries do not get written |

**Do not generalise:**
- `related_context_id` onto the **envelope** — which other record this one relates to is a call-site fact no wrapper can source. FMS F12 says so outright: *"stamp `related_context_id` with the rejected order's id."* (Widening its **scope** onto `Journey Step Failed` is a different and correct move — see C6.)
- `channel` and `otp_channel` into one enum — §7 already considered and rejected this when it folded `channel_used` into `otp_channel`.
- `agreement_type`/`report_type`/`artefact_code` into one document registry — `artefact_code` is a **consent** registry: policies, not retrievable documents.
- FMS's three profile state scalars into a `module_state` map — `consent_state` maps a homogeneous key space onto a boolean; a `module → state` map has a different vocabulary per key and is not filterable in the 60-second real-time window the frequency cap demands. **The three scalars are orthogonal and combinable by design**, which is why an account can be `funds_state: shortfall` and `dues_state: outstanding` at once.

---

## 11. What blocks emission today, ordered, with owners

Everything here is unresolvable by a module document and must be settled by the registrar or a named product owner.

| # | Blocker | Owner | Fix |
|---|---|---|---|
| **1** | **OD-1 — no registrar is named.** A §9 rates it P0: *"Every enum-consistency defect found in this taxonomy … is a **registrar** defect, not an author defect. **Name one person before any module registers its first value.**"* FMS has now registered 74 values, 6 property claims and 6 profile properties against nobody | Head of Product / Head of Data | **Name the person.** Nothing below can be decided without it |
| **2** | **D-01 / FMS-OD-1 — `context_type` has no account-level or session value and is non-nullable.** Blocks the first node of F2, F3, F5, F6, F11 | Registrar, before any FMS event fires | Register `account` **and** `session` in §2 row 4 in one pass; add the pre-auth nullability note for `module: marketing`. **Do not accept FMS-OD-1 as a one-value change** |
| **3** | **OD-2 — has anything been emitted?** Every value rename in this report (`WDL_BANK_REJECTED`, `wdl_cancelled`, the three bare `outcome_code`s, `uploaded`/`upload`, `funds_state: in_debit`) is lawful **only** on the pre-emission gate. A §8: *"the only ones permitted, and only because none has been emitted"* | Engineering + Analytics | **Answer it in writing.** If the answer is "yes, some", the rename list splits into rename-now and grandfather-by-name |
| **4** | **§8 item 1's artefacts do not exist** — the per-module wrapper validation table for the six 🔶 enums, and the CI job that diffs it against §5 | Engineering | Ship both before first emission. **Generate §6's Events-used column from §5 in the same job**, and extend the same job to grep every profile property FMS names against §5.9 |
| **5** | **C1 / PROP-09 — FMS's two undeclared property claims.** The wrapper table, when it ships, rejects `Journey Step Completed{funding_method, amount_paise}` — F3 node 4, F4's route-change read, §8 row 10 | Registrar + FMS author | Either promote to claims 5 and 6 and correct §5.12 to 6, **or** widen §5.3's Scope column authority-side. **Not prose** |
| **6** | **D-11 — three non-nullable envelope properties have no source on customer-less events.** `session_id` on `platform: system` (FMS emits `system` on every EOD transition — *"Rule W4b's whole point is that nobody is watching"*); `account_state`/`engagement_state` on `context_type: service_incident` | Registrar | Make `session_id` nullable where `platform = system` (**not** `ops_console` — an ops session is a lawful session); make the two state properties nullable where `context_type = service_incident`. Otherwise the wrapper passes the stated test with a synthetic value, which is a lie in the data. **Sequence C6 (removing `incident_id`) after this** |
| **7** | **OD-7 (P0) — the `outcome_code` → `error_class` map has never been ratified.** Described in its source as *"a proposal, not a ruling"*. It decides nudge vs corrective guidance vs apology vs silence for all **53** KYC codes | Product + Content | Ratify before the first comm fires. Reissue the scope as 53, not 54 |
| **8** | **OD-3 (P0) — position reconstruction.** `quantity` + `instrument_id` + `product_type` on an order stream lets anyone with dashboard access reconstruct what a `context_id` holds — materially the disclosure the balance ban exists to prevent, arriving by a different route | Compliance + Analytics | Gate dashboard access to `module: orders` raw events, as A already recommends. **`Charged` is inside that gate by construction** (it carries `module: orders`), contrary to a pass-2 claim now refuted |
| **9** | **OD-6 — the auth `outcome_code` list is unpublished.** A proposes nine `AUTH_*` codes against a PRD containing **zero** occurrences of `outcome_code`. Under R9, a code emitted before its owner publishes the list cannot safely be renamed afterwards | Auth PRD owner | Publish the list. The PRD's own text names every condition (OTP expiry, invalid OTP, 5-resend ceiling, 15-minute lock, lockout, 10/hour, 20/24h, abuse challenge, lost channel) |
| **10** | **D-13 — DPDP erasure has no representation of any kind.** Case-insensitive grep of A for `dpdp\|eras\|tombstone` returns **zero**. §2 guarantees *"Re-running last quarter's funnel today returns the same numbers"*; the erasure right is precisely a demand that it not | Compliance | Open **OD-11** with a named compliance owner and state in §2 that the stamp-at-emission guarantee is suspended for erased subjects. **Hold the vocabulary** (`action: erase`, `account_state: erased`, `dispatch_outcome: suppressed_erased`) until compliance rules — §5 is additive-only once emitted, and registering now presumes the shape of a ruling not yet made |

---

## 12. The change list

Numbered, grouped by target document, each with a one-line reason.

### Before anything else

1. **Name the registrar (OD-1).** Every consistency defect in this report is a registrar defect and there is nobody to route the decisions to.
2. **Answer OD-2 in writing — is anything emitting?** It gates every value rename below, and the window closes permanently at first emission.

### `THINQ_EVENT_TAXONOMY.md` (A) — blocking

3. **§2 row 4: register `context_type: account` and `context_type: session` in one pass, plus a `module: marketing` pre-auth nullability rule.** One-value acceptance of FMS-OD-1 recreates the "nine uncoordinated grains" failure §2 was written to prevent.
4. **§2 rows 5, 10, 11: make `session_id` nullable where `platform = system`, and `account_state`/`engagement_state` nullable where `context_type = service_incident`.** Otherwise the wrapper invents three values it has no source for.
5. **§3: add `otp_channel` to the de-duplication key for `OTP Requested` **and** `OTP Resolved`, and widen its §5.8 scope to `OTP Resolved`.** The auth PRD's dual-channel dispatch makes the current key drop half of every sign-in OTP.
6. **§5.6: declare `filled_quantity` cumulative; §3: add a server-assigned `transition_index` to the `Order State Changed` key.** Otherwise two consecutive `modified` rows are byte-identical and the modify-cap funnel has no numerator.
7. **§8 item 1: ship the per-module wrapper validation table and the §5-diff CI job — and generate §6's Events-used column from §5 in the same job.** Six modules register values whose host events their own row omits; funds omits twelve names including the entire spine.

### `THINQ_EVENT_TAXONOMY.md` (A) — correctness

8. **§3: complete the de-duplication ruling.** (a) Name the eight events declared **not de-duplicated on a business key** — the frozen six plus `Action Blocked` and `Notification Deep Link Opened` — with the one-sentence reason (a business key needs an occurrence counter; Layer 1's schemas are frozen), idempotency delegated to the SDK's per-event id. (b) Give keys to the remaining repeating server names, starting with the two zero-cost ones: `Attempt Cap Reached` → `+ cap_type + total_submissions`; `Action Blocked` → `+ blocked_reason + element_id + session_id`. (c) **Amend the acceptance test** to *"replay every event type twice, assert one row each for the keyed names; emit two legitimate consecutive occurrences of every name, assert two rows."* **(c) is load-bearing — without it the current test certifies the failure.**
9. **§3 R1: restate the carve-out as a rule with an enumerated membership, not a count.** Sixteen properties are a static function of another property on the same event, fourteen beyond the two admitted (table in §4). Publish or remove each map. A rule enforced at 12.5% tells a reader nothing about what is lawful to add next.
10. **Correct the four wrong counts:** kyc `outcome_code` **53** not 54 (and reissue §8's KYC-PRD instruction and OD-7's scope as 53; the missing 54th is `DUPLICATE_IDENTIFIER`, reassigned to auth); `stage_code` **40** not 46, in all three places; §2 row 9's `stage_name` module list replaced by a pointer to §5.1; §4's widest-payload sentence rewritten as *"`Request Stage Changed` at 17 + 11 = 28 (`Order State Changed` 27, `Element Clicked` 26), all inside the 100-property cap."*
11. **Give a §5 row — values, scope, nullability — to the 23 properties named in §4 with none, or delete them from §4; and widen the three mis-scoped rows** (`nominee_outcome`, `last_outcome_code`, `file_type`). `was_journey_entry` alone carries every refusal funnel and both FMS §8 rows 9 and 18; `charge_category` sits on the one name A says is not retrospectively fixable.
12. **§5.9 and §5.11: give the profile layer the table shape every other §5 subsection has — Values · Type · Written by (event or named server job) · Recompute cadence.** Populate the 9 determined and 19 inferable; mark the 8 open with an owner. **Correct the "unchanged bar two" heading**, which is false once §22.3 is superseded. Add a §13 test asserting a write within the stated cadence of the stated trigger.
13. **Strike `agreement_version` from §4 L3 (replace with `artefact_version`) and add a §7 removal row.** Two names for one template version, both lawful on one event, one unregistered.
14. **Add `request_type` to `Action Blocked`.** §5.2 and §7 delete two `blocked_reason` values by naming a mechanism that does not exist on the event.
15. **§5.1: publish a per-family "terminal of record" column and key the R7 CI check on it.** `bank_*` ends at `bank_verified`, `rep_*` at `rep_ready` (no terminal of record at all), `tkt_*` at `tkt_resolved`, kyc at `ready_to_trade`, plus FMS's `due_cleared`/`shf_cleared`. **Do not mint literal `<family>_completed` aliases** — that duplicates one transition under two values.
16. **§6 row 6: rewrite to the 19 names FMS emits plus `OTP Requested`/`OTP Resolved`; adopt one convention for the Events-used column across all 18 rows.** Do **not** add `Ops Decision Recorded` — module follows the destination.
17. **Add the missing free value additions in one pass** (all R6-free, no review needed): `report_type` gains `cmr · aof · nomination_form · ddpi_form · closure_form · capital_gains`; `verification_method` gains `knowledge_factor` (with the R4 caution — PAN and DOB may travel only as a shape) and `none`; `agreement_type` gains `contact_change_form · nomination_form`; `artefact_code` gains `C-PANBANK` and, if real, `T-MITC`/`O-DDPI`; `unplg_withdrawn` and the `ca_*` terminals or their recorded waivers; a `frz_*` family **or** the deletion of `request_type: freeze_assisted` (§7 already rules *"a freeze **is** a mutation"*). Enumerate the five categorical per-step KYC properties (`marital_status`, `occupation`, `income_band`, `trading_experience`, `face_match_band`) and publish the five statutory `report_type` values.
18. **§5.6: publish the three-row `segment ↔ segments_*` mapping; §5.7: publish the `reveal_group → tier` table; §5.6: pick one of `uploaded`/`upload` and close `method` at 20; §5.2: rename or grandfather the three bare `outcome_code`s.** Four collisions, four one-line fixes, all free before first emission.
19. **§2: state the shared-value rule — value strings shared across envelope properties require the property name in every published filter definition.** `active` means three things in three closed enums, two of them on every event.
20. **§5.7: redefine `touch_index` as "first touch on this `context_id`."** One sentence. "Application" is KYC-seeded language in a product-wide document, and FMS applies it to `service_request` records.
21. **§9: open OD-11 for DPDP erasure with a named compliance owner. Register no vocabulary yet.**
22. **Frontmatter: add `Thinq_Arvind/architecture/event-model.md` to the `supersedes:` list, and add `THINQ_KYC_PANEL.md` to §8's downstream table.** Neither is reachable by the ratified migration plan today.

### `THINQ_EVENT_TAXONOMY_TESTS.md`

23. **Restate D-40 as "22, corrected to 26"** with the four missed members `fallback_used` · `agreement_version` · `requires_reverification` · `last_outcome_code`, and the 23/3 partition. **Drop D-40's `fallback_used`/`fallbacks_used` two-spelling claim** — refuted; both are real at different grains.
24. **Correct the addressable property total from 222 to 223, and the coverage table's "of 54" to "of 53."**

### `fms/product-requirements-events-and-funnels.md` (C) and `kyc-event-spec.html` §19–§23 (B) — one artefact, edit both

25. **Retitle C :46, C :100, C :120 and B :2191 identically to "fifteen rows, nineteen names" and delete the "depending on how the pairs are counted" hedge.** §13 test 2's grep is run against a stated count; a wrong count is how an omitted schema passes.
26. **Strike `fund_approved` from F3 node 6 and render node 6 as a latency annotation on node 5** (same population, median commit→credit), mirroring `dashboard.js` step 6. **State in §5.4 that `fund_approved` means PSP approval before credit and nowhere else, and that margin availability is simultaneous with `fund_credited`. Do not register `fund_margin_available`.**
27. **Add the Source column to C §2.2; split B's `Message Dispatched · Notification Deep Link Opened` row and set the open event to `client` at B :2208.** The markdown half asserts no source at all and the rendered half asserts one wrong one.
28. **Promote `funding_method` and `amount_paise` to claims 5 and 6, correct §5.12 to 6, and add the §5.3 scope amendment to §12.** Or drop both and read the withdrawal amount and route from `Request Stage Changed{stage_name: wdl_requested}`, where both are already lawful.
29. **Add a §12 row: "`THINQ_EVENT_TAXONOMY.md` §5.9 — gains six profile properties."** It is the one thing FMS declares itself to register that its adoption list does not carry back to the authority. **Add the §6-row-6 rewrite as a second missing §12 row.**
30. **Rename `deduction_reason: negative_balance` → `debit_balance`, align `unsettled_credits`/`unsettled_funds` to one spelling, and rename `WDL_BANK_REJECTED` → `WDL_BANK_RETURNED`.** All three are pre-emission and free today.
31. **Align `funds_state` and `screen_name` on one stem, state the derivation rule in §5.11, and fix `app.js:355 key: 'debt'` to match.** Add a §13 test asserting the two sets correspond.
32. **Fix §6.1** — delete *"the amount returned by a mandated settlement"* from the Unlawful column and change §6.2's Cannot-send cell to *"nothing"*, matching B. C forbids in one table what it declares lawful in the next, and the failure direction is over-suppression of F9's KPI numerator.
33. **Mark F9 node 1 as not-yet-computable in §7** until the advance-announcement message exists, and **correct the `shortfall_state` row's citation from REQ-601–604 to Rules C11/C12/C13 plus comms §9's calendar** — the cited requirements have no text.
34. **Publish the route-id → `funding_method` map** (one row per `ROUTES` entry), split the build's NEFT/IMPS option or record that IMPS is not offered, and restate F3's exclusion as `funding_method in (neft, imps)`.
35. **Record the missing declarations:** an R7 waiver sentence in the `fund_*` paragraph for `_withdrawn`/`_cancelled` (the back-out rides `outcome_code: FUNDS_USER_CANCELLED` on `fund_failed` and is excluded by filter — **do not mint a new stage**); a supersession line for `FUNDS_REVERSED`; adoption lines for `amount_source` and `source_bank_ref`; the SMS-read blind-spot row into B §23.2; `trigger = cta` in place of `trigger = user` at B :2522 and :2712; and rewrite B's two `amount_source` rows to read the chip from `Element Clicked{element_id: funds_amount_suggestion, item_value}`.
36. **Add `THINQ_EVENT_TAXONOMY_TESTS.md` to C's `related:` block, rewrite FMS-OD-1 to endorse the two-value + nullability fix, and delete the claim that other modules are "silent about it."** It is false as of 18 Aug and reads to the registrar as FMS finding it first.

### `fms/product-requirements-communications.md` and `fms/web/`

37. **Add the mandated-settlement advance announcement to `web/app.js` and extend `gen-comms.sh` to emit it**, so it lands in comms §4 automatically; add the sixth row to §5's channel matrix by hand and the fifth state to the §1 digest. Both halves of Rule W8 — announce before, notify after — need a channel and a timing each. **Rule W8's rationale is that an unannounced outflow *"assumes an error or a theft"*.**
38. **Correct every `Rule C*` and `REQ-6*` citation in `web/app.js` and `web/.test-assert.js` against comms §5–§8** — at least eleven are wrong, and `app.js:2126`'s "Rule C15" points a reader looking for the PII rule at a formatting rule. **Move the `Rule C10` string out of the shipped `fallback` field or fix it to C4.**
39. **Promote C18 (*"a failed payment cannot be the one without a number to quote"*) and C22 (*"email is the only channel that may use structure"*) into comms §8 as real rules.** Both name substantive behaviours the code implements and the PRD does not own.
40. **Add a guard to `.test-assert.js`: extract every `Rule C\d+` and `REQ-6\d+` from `app.js` and assert each appears in `product-requirements-communications.md`** — the same shape as the existing `no Math.random` / `no wall clock` checks in `.dash-assert.js:33-34`. This is the durable half of items 38–39.
41. **Renumber the four mis-numbered requirements in comms §6/§7 into the ranges `product-requirements.md:103–109` already reserves** — :198/:201/:204 → REQ-611–615, :232/:235/:238 → REQ-616–620 — then update FMS's citations at :208, :210, :337. **Do not renumber §8; the parent routing table assigns it REQ-621–627.** Publishing REQ-601–618 in full is the right second step.
42. **Fix comms §10.1's HC-DMT cell from 12 to 11** (P3; the registry's 158 total is correct).

### Downstream documents

43. **Edit `THINQ_PROFILE_PRD.md` §10a in place — the highest-value migration item, because it is the one downstream document A does *not* supersede.** Fifteen targeted changes (table in §7), including deleting the `Account Request Raised` node at :2012, adding `tier: F`, renaming `field_group: account_state` → `lifecycle`, the `hours_in_previous` → `seconds_in_previous` **× 3600** grain change, and registering the `bank_*` family and `request_type: bank_add` that FMS F11 already depends on. **Extend A §8's correction row with the four properties it genuinely omits: `esign_used`, `funds_topped_up`, `hours_open`, `reauth_required`.**
44. **Replace `THINQ_KYC_ONBOARDING_PRD.md` §22.0–§22.5 wholesale with a pointer to THINQ-EVENTS-001**, as the FMS PRD did with its own tracking section — **but only after item 12 lands**, since §22.3 is the corpus's only "Source of truth" column for profile properties.
45. **Stub `Thinq_Arvind/architecture/event-model.md` to title + one sentence + a pointer to THINQ-EVENTS-001 v1.0.0 and the test suite.** It is two generations stale, points at superseded sections as its source of truth, has no banner, and is the file a new engineer reaches for first. **Do not rewrite it as a summary — a summary is what produced the drift.**
46. **Add red supersession blocks to `kyc-event-spec.html` §04, §05, §06, §07, §10 and §14** (the pattern §15 already proves), **fix the masthead and §12 to 40/472**, and flip §17's R-4 and R-6 chips from `fixed` to `superseded` with the new rulings inline, R-8 and R-9 from `open` to `closed`, and the sanity note to 40 names and `segment_list`.
47. **Auth PRD:** publish the `outcome_code` list and map the audit set onto `Session State Changed{session_state}` including `idle_warned`. **Leave LOG-04 alone** — it is an audit-log mode, not a `mode` property value. **TnC PRD:** add `by_proceeding` to §3 and §11, and settle the `channel = proceed|checkbox` field against A's closed four or state it never reaches the event stream. **KYC panel:** `sendback` → `send_back` at :703.
48. **Answer A §9's registry-versus-product question in writing: is the module registry a build roadmap or a statement of what exists?** Twelve of eighteen modules have no source PRD anywhere in the corpus, so half the registry is unfalsifiable and the two readings produce different budgets and different priorities.

---

# Annex A — Adjudication of contradictions between findings

# Registrar rulings — contradictions between confirmed findings (pass 1 §D)

All five re-adjudicated against primary sources. Line numbers are 1-indexed against the files named in the brief.

---

## 1. PROP-06 vs TESTS-D-40 — the §4-property-with-no-§5-row count

### Method (re-run, not inherited)

I extracted the **Key properties** column of every table in §4 (`THINQ_EVENT_TAXONOMY.md` lines 118–266 — 41 rows across 9 layers plus the reserved `Charged` table), stripped italic parentheticals (`*(renamed from …)*`, `*(new)*`, `*(module-scoped)*`) so that a *mention* of a retired name is not counted as a property *on* the event, and diffed the result against the **Property** column of every table in §5 (lines 267–563), plus §5.1's three module-scoped envelope members (`sub_module`, `step_name`, `stage_name`) and §5.2's prose registrations (`outcome_code`, `error_code`, `error_class`, `blocked_reason`, `reason_code`). Every candidate was then grep-verified individually.

### The definitive answer

Both published numbers are artefacts of two different questions being asked under one label. The correct answer is a **partition, not a single integer**:

**Class A — named on a §4 event, zero occurrence anywhere in §5: 23.**

Grep-confirmed (every hit falls inside lines 118–266; `charge_category`'s second hit is line 578, which is §6, not §5):

| # | Property | Host event | §4 line |
|---|---|---|---|
| 1 | `fallback_used` | `Journey Step Completed` | 141 |
| 2 | `days_since_registration` | `KYC Started` | 153 |
| 3 | `sessions_used` | `KYC Submitted` | 154 |
| 4 | `fallbacks_used` | `KYC Submitted` | 154 |
| 5 | `steps_failed_count` | `KYC Submitted` | 154 |
| 6 | `agreement_version` | `Agreement Generated` | 155 |
| 7 | `ddpi_opted_in` | `Agreement Generated` | 155 |
| 8 | `decision_hours` | `Application Approved` | 156 |
| 9 | `is_sanctions` | `Application Rejected` | 157 |
| 10 | `can_reapply` | `Application Rejected` | 157 |
| 11 | `time_to_verify_sec` | `Mobile Verified` | 169 |
| 12 | `time_since_mobile_sec` | `Registration Completed` | 170 |
| 13 | `landed_on_screen` | `Login Completed`, `Journey Resumed`, `Notification Deep Link Opened` | 171, 185, 213 |
| 14 | `via_deeplink` | `Login Completed` | 171 |
| 15 | `biometric_enrolled` | `Activation Completed` | 194 |
| 16 | `nudge_skipped` | `Activation Completed` | 194 |
| 17 | `hours_since_ptt` | `Activation Completed` | 194 |
| 18 | `requires_reverification` | `Account Detail Changed` | 195 |
| 19 | `was_journey_entry` | `Action Blocked` | 204 |
| 20 | `seconds_since_dispatch` | `Notification Deep Link Opened` | 213 |
| 21 | `charge_category` | `Charged` | 244 |
| 22 | `Items[]` | `Charged` (CleverTap platform-reserved) | 244 |
| 23 | `Amount` | `Charged` (CleverTap platform-reserved) | 244 |

**Class B — a §5 row exists but scopes the property to a different event: 3.** This is a *scope* defect, not an *unregistered* defect, and must not be summed into Class A without saying so.

| # | Property | §4 host | §5 row and its scope |
|---|---|---|---|
| 24 | `nominee_outcome` | `KYC Submitted` (154), `Agreement Generated` (155) | §5.9 line 556 — **profile property only** |
| 25 | `last_outcome_code` | `Journey Step Abandoned` (143) | §5.9 line 556 — **profile property only** |
| 26 | `file_type` | `Document Retrieved` (224) | §5.5 line 440 — scoped to **`Media Captured`** only |

### The delta, named

- **D-40's 22 is a correct enumeration of its own list** (suite line 1302, restated in the SANITY table line 1600). It misses **four**: `fallback_used`, `agreement_version`, `requires_reverification`, `last_outcome_code`.
- **PROP-06's 26 is arithmetically right** (22 + 4) and is the correct *union*, but it publishes 26 under D-40's headline "*no entry in the §5 registry*", which is false for 3 of the 26.
- **The pass-1 synthesis's "19 + 3" is wrong.** It derives 19 by subtracting `charge_category` and `file_type` from 22 on the reasoning that §6 row 9 delegates `charge_category` to orders. §6 is not §5; a mention in the module map is not a registry row, and the critic's §C.5 machine-check agrees — `charge_category` has **no §5 row and no values at all**, on the one name §9 OD-4 says is unrecoverable if fired wrongly. It stays in Class A.

Two derived figures also need restating: D-40's `fallback_used` / `fallbacks_used` claim of "two spellings of one stem" is **withdrawn** (the refutation is right — line 141 is a per-step boolean on the spine, line 154 an integer count on the once-per-journey milestone; both must be registered, neither collapsed). And the suite's "Total addressable property names **222** (200 registered + 22 §4-only)" (line 1601) becomes **223 = 200 + 23**, since the three Class B members are already inside the 200 under the wrong scope.

**RULING: publish one number as a partition — 26 total, being 23 properties with no §5 registry row of any kind (21 taxonomy-owned + `Items[]` and `Amount`, which are CleverTap platform-reserved on the reserved name `Charged`) and 3 with a §5 row scoped to a different event (`nominee_outcome`, `last_outcome_code`, `file_type`); restate D-40 as "22, corrected to 26" with the four missed members `fallback_used` · `agreement_version` · `requires_reverification` · `last_outcome_code`, retire PROP-06 as a standalone finding, correct the suite's addressable total from 222 to 223, and drop D-40's `fallback_used`/`fallbacks_used` two-spelling claim as refuted — severity P1, unchanged.**

---

## 2. The "fifth R1 breach" — one ordered list, ordinals retired

R1 (line 77): *"No property may be sent whose value is a function of another property **on the same event** … **Exactly two properties pass**: `error_class` (from `outcome_code`) and `leg` (from `stage_name`). Every other derivable property shipped to date is removed in §7."*

R1 names a **closed membership**, not a three-condition test. Any third member is therefore a defect in the property or a defect in the rule — the document asserts both. Ordinal language ("a fifth", "two further beyond D-18's four") is retired because four findings were each counting from a different, unstated baseline.

### The complete list, ordered

**Carve-out — lawful as R1 is written (2)**

| # | Property | Determined by | Host event |
|---|---|---|---|
| C1 | `error_class` | `outcome_code` | `Journey Step Failed` |
| C2 | `leg` | `stage_name` | `Request Stage Changed` |

**Class A — total static value maps; unambiguous breaches (10)**

| # | Property | Determined by | Host event | Source of the map | First raised |
|---|---|---|---|---|---|
| A1 | `is_statutory` | `report_type` | `Document Retrieved` | §4 L9: "twelve report types, **five statutory**" | D-18 |
| A2 | `is_recoverable` | `reason_code` | `Application Rejected` | §9 OD-9 — "has nothing to read until" the registry exists | D-18 |
| A3 | `is_terminal` | `outcome_code` | `Journey Step Failed` | §5.8 line 549, "One meaning only" | D-18 |
| A4 | `cap_reached` | `attempts_remaining` (`== 0`) | `Journey Step Failed` | §5.4; suite line 81 emits `attempts_remaining=0 · cap_reached=true` | D-18 |
| A5 | `tier` | `reveal_group` | `Sensitive Value Revealed` | §5.7 lines 470–471; 13 values → 3 tiers, total; suite FT-23 step 5 "SHALL NOT vary by screen" | PROP-03 |
| A6 | `is_sanctions` | `reason_code` | `Application Rejected` | §5.2 — "mapped ops list, never free text" | PROP-18 |
| A7 | `can_reapply` | `reason_code` | `Application Rejected` | Same footing as A2/A6; suite XC-04 step 4 emits all three together | PROP-18 (fix text only — never titled) |
| A8 | **`permission_state`** | `outcome_code` | `Journey Step Failed` | §5.2 `PERM_*` group → §5.8 `denied`·`blocked`; suite lines 189/190 emit `PERM_CAMERA_DENIED→denied`, `PERM_CAMERA_BLOCKED→blocked`, and `null` elsewhere (lines 81, 184) | **NEW — this pass** |
| A9 | **`segments_dropped`** | `segments_selected` **∖** `segments_on_aof` | `Agreement Generated` | All three co-travel on the event (§4 line 155); §5.6 fixes the nesting `segments_active ⊆ segments_on_aof ⊆ segments_selected` over 4 closed scalars, so the difference is exact. Suite line 224: `cash_fno_commodity` ∖ `cash` = `["fno","commodity"]`; line 139: `cash_fno` ∖ `cash_fno` = `[]`. 2 of 2 emissions are exact set differences | **NEW — this pass** |
| A10 | **`errors_shown`** | `len(error_codes[])` | `Element Clicked` | §5.5 line 439 registers both on one event with no published distinction; suite lines 63, 295, 550, 583, 682 — 5 of 5 emissions satisfy `errors_shown == len(error_codes[])` | **NEW — this pass** |

**Class B — presence-derivability (the weaker form: the boolean/enum restates whether its neighbour is populated) (3)**

| # | Property | Determined by | Host event | Note |
|---|---|---|---|---|
| B1 | `requires_reverification` | `verification_method` populated | `Account Detail Changed` | §5.7 line 475 enumerates 4 values, all re-verifications, no `none` | PROP-04 |
| B2 | `outcome` | `failure_reason` populated | `Login Completed` | §5.8 closes `failure_reason` at 5, all failures | PROP-18 |
| B3 | **`otp_outcome`** | `seconds_to_entry` populated | `OTP Resolved` | §5.8 — `seconds_to_entry` is dispatch→entry, so it exists iff `otp_outcome = entered` | **NEW — this pass** |

**Class C — declared, reasoned, and still a third exception to a rule that says "exactly two" (1)**

| # | Property | Determined by | Host event | §7's own words |
|---|---|---|---|---|
| C-1 | `charge_paise` | `request_type` | `Request Stage Changed` | §7 "Not removed, and why": "derivable from `request_type` **today** — but only while pricing is a three-value constant" |

### Candidates tested and cleared (so they are not re-raised)

- `error_class` **on profile** (`last_error_class` ← `last_outcome_code`) — **not an R1 breach.** R1 is scoped to "the same event"; the profile layer is not an event. Retire it from the candidate set.
- `was_stp` — **deleted in §7.** It is the precedent ("R1's own worked example, shipped anyway"), not a live member.
- `attempts_remaining` ← `step_name` + `attempt_index` — **conditional.** `attempt_index` is absent from `Journey Step Failed`'s §4 property list today (D-31), so the determining property is not on the event as published. **This becomes an A-class breach the moment D-31 is fixed** — flag it in the D-31 fix, do not raise it now.
- `exchange` ← `instrument_id` — cleared: §5.6 declares `instrument_id` free-form and `NSE:RELIANCE-EQ` illustrative, so the prefix convention is not registered. (Recommend registering the format; the clearance depends on it staying unregistered, which is itself unsatisfactory.)
- `order_state` ← `filled_quantity`/`quantity` — cleared: partial map only (`pending`, `rejected`, `cancelled`, `expired`, `modified` are not derivable).
- `granted` / `is_withdrawal` on `Consent Captured` — cleared: a decline and a withdrawal are distinct, so neither determines the other.
- `reauth_outcome` ← `tier` — cleared as partial (only `not_required` is determined). Note that §7 already removed `reauth_required` for the total form of this same relation.

### The number to publish

**Sixteen** properties in the current authority are a static function of another property on the same event: 2 carve-out members + **10 undeclared total-map breaches** + 3 presence-derivability breaches + 1 declared-and-reasoned exception. **Fourteen beyond the two R1 admits.** Not four, not five, not six, not seven.

**RULING: retire D-18, PROP-03, PROP-04 and PROP-18 into one finding — "R1 says exactly two properties pass its carve-out; sixteen properties are a static function of another property on the same event, fourteen of them beyond the two admitted" — carrying the sixteen-row table above with `permission_state`, `segments_dropped` and `errors_shown` newly added and `can_reapply` promoted from fix-text to a titled member; the fix is to restate R1 as a rule with a listed membership rather than a count, and to publish or remove each map; severity P1, and no finding may use an ordinal.**

---

## 3. PROP-05 — removing `incident_id`

### What `incident_id` actually carries

`incident_id` is registered on **exactly two events** (§5.8 line 531; §4 lines 182, 214): `Vendor Failure Detected` and `Service Restored`. On both, §5.8's Values column states outright that it holds "`context_id` of the `service_incident`" — and §2 row 4 already mints `context_type: service_incident` for precisely that identity. The suite emits the duplication in the same row: FT-08 step 5 (line 202) `incident_id=I-441 · context_type=service_incident · context_id=I-441`. PROP-05's core claim is confirmed and is the unapplied `request_id` removal (§7: "Duplicates `context_id`").

### Does removing it break §6 read 3?

**No — and the critic's premise is wrong on the primary source.** §6 read 3 (line 597) runs `Vendor Failure Detected{incident_id: I-441}` → `Journey Step Failed{outcome_code: DIGI_VENDOR_DOWN}` **in the window** → `Message Dispatched{related_context_id}` → `Service Restored{incident_id: I-441}`.

`incident_id` is **not registered on either middle event.** It is not on `Journey Step Failed` (§4 line 141) and not on `Message Dispatched` (§4 line 221). The suite proves it: FT-08 step 6's `Message Dispatched` carries no `incident_id` and no `related_context_id` at all, and its note describes the cohort as a **time-window join**, not an id join. So the join the critic says removal would delete was never carried by `incident_id` in the first place. Deleting a property that appears only on the two events whose envelope already holds the same string changes nothing about read 3.

What *is* true is worse and separate: **read 3 has no incident carrier on its middle two nodes today**, which is D-21 (P1, confirmed) — "`related_context_id` … is absent from … the whole spine" and from `Service Restored`, "and it is also **single-valued**". Read 3 is broken before the removal and equally broken after it. PROP-05 did not create that; it exposed it.

### Replacement carriers, stated explicitly

1. **`Vendor Failure Detected` and `Service Restored`** — envelope `context_type: service_incident` + `context_id: <incident>`. Direct swap; both events are system-emitted and *are about* the incident. Nothing is lost. This also discharges D-21's `Service Restored` clause: the event names itself, so it needs no `related_context_id` for its own identity.
2. **`Message Dispatched` on an outage or restore send** — `related_context_id: <the incident's context_id>`. Already registered on that event (§5.3), so this is a **referent ruling, not a schema change**. There is no collision with "the record it concerns": the envelope `context_id` is the customer's case and the message concerns the incident, so the single-valued constraint (D-21) is respected. With this in place `Service Restored{context_id: I-441}` joins `Message Dispatched{related_context_id: I-441}`, and `audience_size` becomes checkable — the assertion suite lines 205 and 1213 both flag as unsupported.
3. **`Journey Step Failed`** (the cohort node) — **add `related_context_id`**, a scope widening of an existing property, zero new names, and already the substance of D-21's spine clause. Until it lands, the cohort remains the time-window join §6 read 3 itself specifies, so the read is no worse after the removal than before.

### Sequencing

The refutation's caveat stands and must ride the fix: D-11 (P0, suite line 1273) shows `Vendor Failure Detected` and `Service Restored` cannot populate the non-nullable envelope members `session_id`, `account_state` and `engagement_state` on a record with no customer. **Do not delete `incident_id` before D-11 is settled**, or the incident loses both carriers at once.

**RULING: the removal stands and is not withdrawn — it deletes no headline unlock, because `incident_id` was never the cross-event join; carry the incident on the envelope (`context_type: service_incident` + `context_id`) on the two host events, mandate `related_context_id: <incident>` on outage and restore `Message Dispatched` rows, and widen `related_context_id` onto `Journey Step Failed` under D-21; sequence the deletion after D-11; PROP-05 stays P2 and its unlock-breaking half is folded into D-21 (P1), which is the finding that actually owns the missing join.**

---

## 4. SYNC-02 — re-cut

### What the three artefacts and the build actually say

| Source | `fund_approved` means | Where margin lands |
|---|---|---|
| Authority §5.1, funds `fund_*` | ordered `fund_initiated · fund_collect_sent · fund_approved · fund_credited` | — |
| FMS §5.4 (line 314) | same order, unchanged | — |
| Artifact §19.4 (line 2254) | "**Approved at the bank; money not yet with us**", `leg: psp` | §21 flow B1 step 7 = `fund_credited`, "Ledger **and available margin** rise" (line 2609) |
| Artifact §21 FLOW (line 2534) | one of six outcomes on **return from the bank app** — `fund_approved \| fund_failed \| fund_awaiting_confirmation` | — |
| **FMS §7 F3 node 6** (line 739) | "`Request Stage Changed{stage_name: fund_approved}` → **margin**. This node never loses anyone; it lags" — **placed after node 5 `fund_credited`** | node 6 |
| Build `dashboard.js` 555–583 | — | step 6 "Usable as margin", `n: intents.filter(i => i.credited).length` |

### The critic is right on both counts

**(a) The "sixth step neither can express" claim is false.** `dashboard.js` lines 561–583 define exactly six payin steps (view → started → route list → committed → credited → usable-as-margin) and FMS F3 defines exactly six nodes in the same order. Both express six. And the build does **not** evidence a distinct sixth moment: step 6's count is byte-identical to step 5's, `usableAt` is assigned `= resolvedAt` at dashboard.js:308 and `= credited` at :280 — the *same timestamp as credit* — and its "median … later" is `speed.p50`, which line 497 defines as `usableAt - a.t`, i.e. the **commit→credit** latency, not a credit→margin lag. The build implements the artifact's reading: margin availability *is* `fund_credited`.

**(b) The real defect is a value redefinition-in-place.** One registered `stage_name` value carries two meanings inside one document: FMS §5.4 registers `fund_approved` as the PSP approval **before** credit; FMS §7 F3 node 6 uses the same string to mean **margin availability after credit**. FMS's own prose proves which meaning node 6 intends — "*This node never loses anyone; it lags*" is true only of a step whose population is identical to node 5's, which PSP approval is not (approvals fail to credit; §5.4 registers `fund_reversed` and `fund_awaiting_confirmation` for exactly that gap).

This is the class §7 forbids by name when it kills `tier: C`: "*reusing it inverts a registered meaning … a redefinition-in-place that the additive-only rule forbids*", and the class R9 forbids for `outcome_code` ("Two modules SHALL NOT assign different meanings to one code string") — here inside one module, one document.

### Consequences, corrected

- As written, node 6 filters for `fund_approved` **after** `fund_credited` on the same `context_id`. Under FMS §9's own de-dup key `context_id + event_name + stage_name` (line 1048, "each stage fires once per record"), that ordering never occurs, so the module's headline funnel converts at ~0% on its last node and the **95% first-try / 30s-to-margin-at-p95** KPI is read off a broken tail.
- **Withdraw** SYNC-02's secondary claim that the 30-second payin-to-margin NFR "has no second timestamp". It has one: `fund_credited`. The build measures it as commit→credit and the artifact §22 F9 step 5 (line 2797) attaches the same NFR to `fund_credited`.
- **Withdraw** SYNC-02's fix option (b). Do **not** register `fund_margin_available`. No artefact and no build evidences a distinct margin moment; adding a stage would mint an unemittable value.

### Severity

P0 is wrong: nothing is mis-emitted, the registration (§5.4) is correct, the artifact carrying the published stage semantics (§19.4, §21 B1) is correct, and the fix is one table row in a v0.1.0 draft. P2 is too low: the defect is a value redefinition inside the module's headline funnel, and its class is the one the taxonomy exists to prevent. **P1.**

### The fix

Strike `fund_approved` from F3 node 6. Mirror the build: node 6 is not a stage filter but a **latency annotation on node 5** — same population, reporting the median commit→credit interval — exactly as `dashboard.js` step 6 renders it. State in FMS §5.4 that `fund_approved` means PSP approval before credit and **nowhere else**, and that margin availability is simultaneous with `fund_credited`.

**RULING: re-cut SYNC-02 as "FMS §7 F3 node 6 assigns a second meaning to the registered stage value `fund_approved` — margin availability after credit — contradicting FMS's own §5.4, the artifact §19.4/§21 B1, and the build, which all place it before credit as PSP approval"; drop the "sixth step neither can express" claim and the "no second timestamp" claim as refuted by `dashboard.js:280/308/497` (`usableAt === credited`, `speed.p50` = commit→credit); fix by striking the stage filter from node 6 and rendering it as a latency annotation on node 5; do not register `fund_margin_available`; severity P1, down from P0.**

---

## 5. NAME-01 / VAL-12 / SYNC-03 / NAME-02 — one defect, four reports

### Counted myself, from the tables

`fms/product-requirements-events-and-funnels.md` §2.2, table body lines 102–119 — **15 rows.** Twelve rows name one event; three rows name several:

- `Overlay Opened` / `Overlay Dismissed` → 2
- `Vendor Call Completed` / `Vendor Failure Detected` / `Service Restored` → 3
- `Message Dispatched` / `Notification Deep Link Opened` → 2

12 + 7 = **19 distinct names**: Screen Viewed · Element Clicked · Overlay Opened · Overlay Dismissed · Field Errored · Journey Step Completed · Journey Step Failed · Journey Step Abandoned · Request Stage Changed · Action Blocked · Attempt Cap Reached · Vendor Call Completed · Vendor Failure Detected · Service Restored · Message Dispatched · Notification Deep Link Opened · Document Retrieved · Sensitive Value Revealed · Query Answered.

**The only two defensible numbers are 15 rows and 19 names.** The pairs-as-one floor is 15, so "fourteen" is unreachable by any rule; "twelve" is reachable only as *the count of rows that name exactly one event*, which is not a count of names. `kyc-event-spec.html` §19.2 renders the identical 15 rows.

### Every location where a wrong number is published — four, verified by grep

| # | File | Line | Text | Wrong number |
|---|---|---|---|---|
| 1 | `fms/product-requirements-events-and-funnels.md` | 46 (Contents) | "the envelope, **the twelve names**, what is already registered" | 12 |
| 2 | `fms/product-requirements-events-and-funnels.md` | 100 (§2.2 heading) | "### 2.2 The **twelve** names FMS emits — all of them already exist" | 12 |
| 3 | `fms/product-requirements-events-and-funnels.md` | 120 | "That is fifteen rows over **twelve to fourteen** names depending on how the pairs are counted." | 12–14 (rows count is right) |
| 4 | `kyc-ops-console/kyc-event-spec.html` | 2191 (§19.2 heading) | "19.2 · The **fourteen** names FMS emits — every one already exists" | 14 |

No fifth location exists: `grep -n "twelve\|fourteen\|nineteen"` over both files returns only these plus unrelated uses (`kyc-event-spec.html:1365` "twelve-screen journey", `:2055` "twelve surfaces", `:2078` `account_state` enumerates twelve, `:2379` fourteen `element_id` values). The two files declare themselves one artefact (md line 37: "a change to either is a change to both"), so this is one artefact publishing three different wrong numbers.

### Why it matters, and the merge

FMS §13 acceptance test 2 is "Assert **zero** new event names in the FMS payload schemas. Grep every schema against the taxonomy's 40." A grep run against a stated 12 or 14 is how an omitted schema passes. It also understates blast radius: 19 of the taxonomy's 40 names carry funds traffic, and that is the figure any sampling, retention or cost decision must be priced against.

**NAME-01, VAL-12 and SYNC-03 are the same defect** — same table, same numbers, same fix — differing only in which of the three wrong numbers each foregrounds. They merge to one P2 finding.

**NAME-02 does not merge.** Its subject is the *authority's* §6 row 6 (`THINQ_EVENT_TAXONOMY.md:575`), which grants `module: funds` seven events while FMS emits 19, and which FMS §12's adoption table never schedules for correction. Only the number 19 is shared. NAME-02 keeps its P1 and cites the merged finding for the count; its already-accepted correction stands (do **not** add `Ops Decision Recorded` to row 6 — §2's destination rule puts an ops-console decision outside `module: funds`, and the FLOW C3 join is on `context_id`, which needs no row-6 grant).

**RULING: merge NAME-01, VAL-12 and SYNC-03 into one P2 finding — "FMS publishes its emitted-name count as 12, as 12–14, and as 14 across four locations in one declared artefact; the table is 15 rows carrying 19 distinct names" — listing md:46, md:100, md:120 and kyc-event-spec.html:2191, fixed by restating all four as "fifteen rows, nineteen names" and deleting the "depending on how the pairs are counted" hedge; NAME-02 stays a separate P1 against the authority's §6 row 6 and cites 19 from the merged finding.**
