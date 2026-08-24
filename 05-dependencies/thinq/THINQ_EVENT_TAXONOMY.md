---
title: Thinq Product Event Taxonomy
prd_id: THINQ-EVENTS-001
version: 1.1.0
date: 20 Aug 2026
status: Ratified — supersedes the taxonomy sections of the documents below
owner: Analytics (registrar unassigned — see §9, OD-1)
supersedes:
  - kyc-event-spec.html §01–§17 (all 17 sections)
  - THINQ_KYC_ONBOARDING_PRD.md §22.0–§22.5
  - Thinq_Arvind/architecture/event-model.md (entire file — two generations stale; stub it to a pointer)
related:
  - THINQ_KYC_ONBOARDING_PRD.md (v2.7.4) — §3 flow, §18 comms engine, §19 validation copy
  - THINQ_PROFILE_PRD.md (v1.16.0) — §7.4a, §7.10a, §7.11a, §7.13, §7.14a, §10a
  - THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md — Options Matrix, OTP policy, Session Lifecycle, Flow 4 Recovery, Audit Logging
  - THINQ_CUSTOMER_SUPPORT_PRD.md — §10.1 topic table, §10.4 SP-1..11, §10.5 AS-1..6, §10.6 AC-1..23
  - THINQ_KYC_PANEL.md — §7.4–§7.6, §7.5.1 escalation ladder
  - THINQ_TNC_PRD.md — §3.1–§3.4 consent artefacts
binding: |
  This document and the CleverTap implementation are one artefact. A change to either
  is a change to both. Where this document and any section listed under `supersedes`
  disagree, this document governs and the other is a defect to be corrected.
---

# Thinq Product Event Taxonomy

**What changed in 1.1.0.** Nothing in the model, in the eleven-slot envelope, or in the forty names. This revision applies **THINQ-EVENTS-AUDIT-001**, which counted the registry mechanically against every document that consumes it. Two envelope grains this document itself demanded be registered *in one pass* are registered (`context_type: account` and `session`), and three non-nullable envelope properties are made conditionally nullable where the wrapper has nothing to read (§2). The de-duplication ruling that §3's acceptance test silently contradicted is written down, two keys are corrected and two names that repeat are given keys (§3). R1's *"exactly two properties pass"* becomes an enumerated membership of sixteen, because a count is not a rule (§3). Four published counts were wrong and are corrected: the kyc `outcome_code` list is **53**, `stage_code` is **40**, `stage_name` is registered by **twelve** modules, and the widest payload is `Request Stage Changed` at **28**. Twenty-three properties named in §4 with no registry row are registered and three mis-scoped rows are widened (§5). The profile layer is given the table shape every other §5 subsection has, and the register grows from 30 to **36**: `THINQ_KYC_ONBOARDING_PRD.md` Appendix 22-A is absorbed so that every property carries a **writer** and a **recompute cadence** — 9 stated, 19 inferred, 8 open with a named owner — and the six properties the FMS registration registers are finally carried here (§5.9). The free value additions the FMS registration and the TnC PRD already depend on are made in one pass (§5). One open decision is added — **OD-11**, DPDP erasure — and no vocabulary is registered against it (§9). **OD-2 is closed: nothing has been emitted to CleverTap, confirmed by the document owner on 20 Aug 2026.** Every change here is therefore an addition or a **final** pre-emission correction rather than a provisional one — including the three bare `outcome_code`s, which take their R9 prefixes instead of the grandfathering exception (§5.2) — and the ~55-name fallback is retired. The pre-emission window for any *further* rename stays open and closes at the first emission (§8, §9).

---

## 1. The model

Two layers, unchanged in principle and repaired in discipline.

**Generic** — six frozen event names carry every interaction in the product: `Screen Viewed`, `Element Clicked`, `Overlay Opened`, `Overlay Dismissed`, `Field Errored`, `Media Captured`. Their schemas are closed. Nothing screen-specific is ever added, because a property added here is added everywhere and can never be reclaimed. These six already serve thirteen of the eighteen modules without alteration.

**Named** — thirty-four names carry business outcomes only. An outcome is a fact a person outside analytics would recognise as having happened: a step completed, a request moved, a vendor answered, an order filled, a message sent, a control refused.

**Why.** Event names are the scarce resource — 512 per account, permanent, shared product-wide, not reclaimable. Properties are cheap. A funnel step is therefore always a *filter* on an existing name, never a new name. That single rule is what buys forty names instead of one hundred and forty.

What changes here is not the architecture but its enforcement. Four names said "KYC" while the layer table already advertised them as reusable for any multi-step flow; two envelope properties were declared product-wide and closed at KYC-only values; and ten properties shipped that the model's own no-derivable-fact rule forbids. All three are corrected. The envelope stays at eleven. Twenty-nine of today's thirty-three names are untouched.

---

## 2. The envelope

Eleven properties, injected by a shared wrapper on **every** event. Never added by hand at call sites — omissions there are silent and unrecoverable. **The envelope is closed. A freed slot is not reallocated.** No property is added or removed by this document; the count stays at eleven and no slot is repurposed.

| # | Property | Values | Nullable | Change vs today | Why |
|---|---|---|---|---|---|
| 1 | `module` | 18, closed: `marketing` · `auth` · `kyc` · `segment_activation` · `profile` · `funds` · `markets` · `charts` · `orders` · `portfolio` · `reports` · `corporate_actions` · `ipo` · `alerts` · `support` · `referral` · `research` · `mutual_funds` | No | — | Unchanged. Follows the **destination**, never the route. Funds reached from the profile menu and Funds reached from a rejected order are both `funds`; the route rides `nav_source_element` and `referrer_screen`. Never create a module for a value that cuts across modules. |
| 2 | `sub_module` | Per-module lists in §5. **Three values registered:** `contact_details`, `demat_details`, `segment_list` | Yes (null on 11 of 18) | +3 values | These three are emitted by the Profile spec today and registered nowhere. Flagged four times across two documents and fixed in neither; until they are registered the closed enumeration is not closed. `segment_list`, not `segments` — `segments` is already a closed `step_name` value and the stem of `segments_selected` / `segments_on_aof` / `segments_active`. |
| 3 | `context_id` | Free string, e.g. `KYC-2026-0819442`, `ORD-20260818-88213`, `CHG-2026-0818-4471`, `NSE:RELIANCE-EQ` | No | — | The **record** this event concerns. Not the identity, not the customer-facing Application ID (minted at step 18, so it cannot join events emitted at step 3). One applicant may hold several cases; a rejection then a re-application is two records and one identity. |
| 4 | `context_type` | **14, closed:** `kyc_case` · `order` · `trade` · `fund_txn` · `ipo_application` · `support_ticket` · `segment_activation_case` · `service_request` · `instrument` · `corporate_action` · `alert` · `service_incident` · `account` **(new)** · `session` **(new)** | No — **except `module: marketing` pre-auth** | 6 → 14 | Done in **one pass**, not per module — nine uncoordinated additions produce nine grains that do not join. `service_request` replaces the unregistered `profile_request` and serves every tracked request (profile change, pledge, report generation, auth recovery, support ticket, IPO), because `request_type` already carries the distinction. `instrument` is how markets, charts, research and portfolio carry instrument identity on the **frozen** generic layer without unfreezing it. `service_incident` gives an outage an identity, which is what turns the per-outage messaged-audience into a join instead of a maintained list. **`account` and `session` are added under that same sentence, not under a module's request.** One module met the account-level gap first and asked for one value; four more hold the identical gap, and granting it one value at a time is precisely the nine-grains failure this row exists to prevent. `account` is the record for a surface that concerns the whole account and no single case — profile home, portfolio holdings, markets watchlist, support `account_lookup`. `session` is the record for `Session State Changed` (all six `session_state` values) and for the charts promise that layouts survive an idle lock, which `state_intact` proves against a session and nothing else. **`basket` is refused**: an order basket is a record and `context_type: order` already covers it; `basket` is a `sub_module` value and stays one. **Nullability, new:** `context_type` MAY be null on `module: marketing` **pre-auth** — a cookie banner and a landing page concern no record, and inventing one is a lie in the data. That is the only lawful null. |
| 5 | `session_id` | Free string, e.g. `s_8841ab` | No — **except `platform: system`** | Nullability | Unchanged in job. Never a substitute for `context_id` in a funnel: a customer who leaves for their bank app and returns tomorrow on a deep link is a new session and the same record. **Nullable where `platform = system`, new:** a sweep, an end-of-day settlement run and an automated credit have no session, and the wrapper's only alternative is a synthetic value — which passes the stated test and lies. **Not `ops_console`** — an ops session is a lawful session and SHALL carry an id. |
| 6 | `platform` | 9, closed: `mweb` · `desktop_web` · `tablet_web` · `ios_app` · `android_app` · `ios_webview` · `android_webview` · `ops_console` · `system` | No | — | Unchanged, and unchanged in job: it answers *what surface*, never *who started this* — that is `initiated_by`. Rejected at the wrapper if out of list. Never overloaded with OS or release (`os_name`, `os_version`, `app_version` are separate; `ios_app_17.2` is not groupable). Stamped at emission, never derived at query time from a stored user-agent. |
| 7 | `screen_name` | Open registry, engineering-owned and frozen. 17 `kyc_*` + `register` + 16 `profile_*` today; each module registers its own | Yes | — | A new value only where the rendered **controls** change, not where only the data changes — prefilled versus not is the same view. An overlay is never a screen. A card is never a screen. |
| 8 | `step_name` | **Module-scoped**, closed and additive per module. `module: kyc` — 11: `pan` · `profile` · `bank` · `address` · `aadhaar` · `selfie` · `segments` · `income_proof` · `nominee` · `signature` · `esign`. Other modules in §5 | **Yes — new** | Scope + nullability | **Structural change 1 of 2.** Today it is mandatory with only `sub_module` nullable, and closed at eleven KYC values — so it has no lawful value at `kyc_welcome`, at registration, on an ops decision, or on any funds, profile, orders or support event. Module-scoping reuses the exact pattern already mandated for `outcome_code`. It also settles the 11-vs-12 divergence in the spec's favour: there is no permissions screen in the build, so the four `PERM_*` codes attach to `step_name: selfie`, and the rogue twelfth header `registration` becomes lawful under `module: auth`. |
| 9 | `stage_name` | **Module-scoped**, closed and additive per module. `module: kyc` — the 38 slugs, unchanged. **Every other registering module is enumerated in §5.1 and nowhere else** — the list that stood here named nine modules against §5.1's twelve, which is exactly the two-places-one-fact defect R1 governs | **Yes — new** | Scope + nullability; absorbs `stage_code` | **Structural change 2 of 2, and the fix for the largest live collision in the taxonomy.** THINQ_KYC_ONBOARDING_PRD.md:2974 is unqualified — *"THE SYSTEM SHALL emit `stage_name`. THE SYSTEM SHALL NOT emit `stage_code`."* The Profile spec then emits a property literally named `stage_code` carrying a different 40-value vocabulary. The resolution is not to argue the axes differ. §22.1g's **deciding argument** is about which spelling travels: *"audiences are authored by people, and `bank_name_mismatch` is readable where `K8b` must be looked up."* `CHG_KRA_REGISTERING` is already a readable slug wearing a code's clothes. Lowercase it and it **is** a `stage_name`. One property, one axis, module-partitioned. Zero stages lost, zero properties added, the prohibition honoured. |
| 10 | `account_state` | **14, closed:** `anonymous` · `partial` · `prospect` · `kyc_in_progress` · `kyc_submitted` · `under_review` · `rejected` · `approved` · `partially_provisioned` **(new)** · `active` · `closing` **(new)** · `frozen` · `suspended` · `closed` | No — **except `context_type: service_incident`** | +2 values; nullability | Answers *whether the account works*, never *what it can trade* — that is `segments_active` and nowhere else. The two additions close the same structural defect raised twice under two numbers: S4 partial activation has no value (`active` contradicts "approval is not permission to trade", `approved` is the earlier decision) and a closure in progress has none (signed, submitted and days from closing all land on `active`, while a Profile lock is keyed on a state literally written `closing` that the enum does not contain). Both propagate product-wide the moment a second module emits. `provisioned` stays removed. The freeze **reason** rides `freeze_type`, never four extra states. **Nullable where `context_type = service_incident`, new:** an outage is not a customer and has no account state; the same rule applies to `engagement_state` one row below. |
| 11 | `engagement_state` | 5, closed: `new` · `active` · `lapsing` · `dormant` · `reactivated` | No — **except `context_type: service_incident`** | Nullability | Server-computed only — the client cannot hold cross-device last-seen. Nullable on a `service_incident` for the reason given in row 10. Boundaries remain unset and the `active`/`dormant` boundary may not be a product choice at all, since SEBI defines dormancy for trading accounts (OD-8). |

### Envelope rules

| Rule | Test |
|---|---|
| The envelope is injected by a wrapper on every event, never assembled at call sites | Sample any 100 events across all modules; all eleven present |
| State is **stamped at emission**, never read live from the profile | Re-running last quarter's funnel today returns the same numbers. A reactivated user reading as `active` on an event fired while they were `dormant` means state is being read, not stamped. **One suspension, and it is not a defect:** where a subject has exercised a DPDP erasure right, the guarantee does not hold for that subject's rows and a re-run legitimately returns fewer. OD-11 owns the shape; **no vocabulary is registered for it here** |
| **Value strings shared across envelope properties carry the property name in every published filter definition** — new | `active` is a lawful value of `account_state`, of `engagement_state` and of `visitor_state`, and the first two are injected on **every** event. A filter, audience, funnel or campaign trigger published as `active` rather than as `account_state = active` is a defect whatever it returns, and the error is invisible in the resulting audience size. Renaming is not available — `account_state: active` is the most-emitted value in the product. Same rule for `rejected`, which is both an `account_state` and a kyc `stage_name` slug |
| The envelope is closed; a freed slot is not reallocated | `journey_variant`, `compliance_step`, `trading_state`, `tenure_days`, `case_id`, `step_no` SHALL NOT be reinstated |
| `platform` out of list is **rejected at the wrapper**, not silently forwarded | Emit `platform: ios_app_17.2`; assert rejection |
| **Module-scoped enums are validated conditionally at the wrapper** — new | `module: funds` + `stage_name: pan_failed_terminal` is schema-lawful and semantically nonsense. A per-module validation table SHALL ship **with** this change, plus a CI job that diffs it against §5. Unenforced enums are exactly what produced `bank_manual` vs `bank_manual_entry` and an undeclared fifth `leg` value, both missed by a reconciliation pass written to catch them |
| `stage_code` is never an emitted property name, in any module | Grep every payload schema for `stage_code`; expect zero |

---

## 3. Standing rules

Nine rules govern every addition. They are the acceptance test for onboarding a module.

**R1 — One fact, one place.** No property may be sent whose value is a function of another property **on the same event**, except by the **named membership below**. *Carve-out, narrow:* a static function of another property MAY be emitted only where all three hold — (a) the function is owned by the emitting server, (b) the property is a mandatory break-down on more than one module's funnels, (c) it is never authored by a human. Every other derivable property shipped to date is removed in §7.

**R1 is a membership, not a count.** v1.0.0 published *"exactly two properties pass"* and shipped sixteen. A rule enforced at 12.5% is not a rule: it tells a reader nothing about what is lawful to add next, and four separate reviews each reported a different "fifth breach" from a different unstated baseline. The membership is therefore enumerated. **A property not on this list SHALL NOT be added, and a property on it SHALL carry its map published or be removed.**

| Class | # | Property | Determined by | Host event | Ruling |
|---|---|---|---|---|---|
| Carve-out | 1 | `error_class` | `outcome_code` | `Journey Step Failed` | Lawful — all three conditions hold. The map itself is still unratified (OD-7) |
| Carve-out | 2 | `leg` | `stage_name` | `Request Stage Changed` | Lawful — all three conditions hold |
| A — total static map | 3 | `is_statutory` | `report_type` | `Document Retrieved` | Publish the map with §5.7's eighteen report types, five of them statutory, or remove the property |
| A | 4 | `is_recoverable` | `reason_code` | `Application Rejected` | Has nothing to read until OD-9's registry exists. Publish the terminal/recoverable flag **in** that registry |
| A | 5 | `is_sanctions` | `reason_code` | `Application Rejected` | Same footing as row 4 — publish with the registry |
| A | 6 | `can_reapply` | `reason_code` | `Application Rejected` | Same footing as row 4 — publish with the registry |
| A | 7 | `is_terminal` | `outcome_code` | `Journey Step Failed` | Publish the per-code flag alongside OD-7's `error_class` map. **One meaning only** (§5.8) |
| A | 8 | `cap_reached` | `attempts_remaining == 0` | `Journey Step Failed` | Kept — a decision the server publishes rather than a derivation the client makes. The identity is stated here rather than left to be discovered |
| A | 9 | `tier` | `reveal_group` | `Sensitive Value Revealed` | Kept, map published in §5.7. Removing it would push the tier-A re-auth compliance count into a query-time lookup no document owns, and OD-10 shows what happens to unowned registries |
| A | 10 | `permission_state` | `outcome_code` (`PERM_*`) | `Journey Step Failed` | Publish the two-value map with OD-7's; null on every non-`PERM_*` code |
| A | 11 | `segments_dropped` | `segments_selected` ∖ `segments_on_aof` | `Agreement Generated` | Kept — the difference is exact over four closed scalars (§5.6), and the array is what the de-scoped-cohort read and the comms shell filter on |
| A | 12 | `errors_shown` | `len(error_codes[])` | `Element Clicked` | Layer 1 is frozen, so neither can be removed. Declare the identity; **never publish two numbers off one fact** |
| B — presence-derivability | 13 | `requires_reverification` | `verification_method` populated | `Account Detail Changed` | **Closed in 1.1.0** by adding `verification_method: none` (§5.7) — the two now carry different facts |
| B | 14 | `outcome` | `failure_reason` populated | `Login Completed` | **`outcome` stays.** §5.4 settled that spelling deliberately across three events. Fixed on the other side: `failure_reason` is explicitly nullable and **null on success** (§5.8) |
| B | 15 | `otp_outcome` | `seconds_to_entry` populated | `OTP Resolved` | Kept — `seconds_to_entry` exists iff `otp_outcome = entered`, and the other three outcomes are the whole reason the name was bought |
| C — declared and reasoned | 16 | `charge_paise` | `request_type` | `Request Stage Changed` | Kept, with §7's reason: a price is a fact about the world at the moment of emission, and pricing is a three-value constant only until the first repricing |

**Tested and cleared — not members, and not to be re-raised.** `last_error_class` ← `last_outcome_code`: R1 is scoped to *the same event* and the profile layer is not an event. `exchange` ← `instrument_id`: cleared **only** while §5.6 leaves the id format free-form; register the format and it becomes a member. `order_state` ← `filled_quantity`/`quantity`: partial map. `granted` / `is_withdrawal` on `Consent Captured`: a decline and a withdrawal are distinct facts. `reauth_outcome` ← `tier`: partial — only `not_required` is determined. `attempts_remaining` ← `step_name` + `attempt_index`: **conditional** — `attempt_index` is not on `Journey Step Failed` today, so it becomes a Class A member the moment it is added, and whoever adds it owns the map. `was_stp` is not a member: §7 deleted it, and it is the precedent for all of the above.

**R2 — Key on ids, never labels.** `element_id`, `screen_name`, `step_name`, `stage_name`, `outcome_code`, `answer_id` and `module` are engineering-owned and frozen. `element_label` travels for legibility and SHALL NOT appear in any filter, funnel definition, audience or campaign trigger. Send the **template**, never the interpolated string: `"Continue with PAN for {name}?"`, never the rendered name.

**R3 — Server emits outcomes, client emits interaction.** Every completion, failure, abandonment, milestone, vendor fact, request transition and ops decision is server-sourced. A client-emitted e-Sign completion is an instrumentation defect — the redirect has already closed the tab. Durations are server clocks at both ends. Vendor facts come from the gateway, never the browser.

**R4 — No regulated identifier, and no hash.** No PAN, Aadhaar, bank account number, IFSC, BO ID, CKYC number, DOB, address, selfie, signature — **and no customer or nominee name** — and no hash of any of them. PAN's five-letter/four-digit/one-letter structure leaves a space small enough to brute-force offline, so a salted hash remains personal data. The platform's reserved `Name` profile field SHALL NOT be populated: it fills silently from the sign-in identity call, which is precisely how a name reaches a processor without anyone choosing to send it. Only the **shape** of the fact travels: `outcome_code`, `face_match_band`, `method`.

**R5 — Money is an integer in paise, never a float.** What a thing **cost** is product data; what the customer **holds** is never sent. This is a deliberate blind spot, not an oversight: withdrawable-vs-total balance and available-margin-vs-balance are permanently unanswerable from product events, and their only proxy is support ticket topic distribution. That is a reason to get `answer_id` right, not a reason to relax the rule.

**R6 — Extend enumerations, never schemas.** A module needing a new **value** is doing what the design intends and needs no review. A module needing a new **property** on an existing name makes a claim that goes to the registrar. A module needing a new **name** must name a second consuming module, or a volume/grain class that cannot ride an existing name without corrupting it — and must say which.

**R7 — Every stage family registers its terminals first.** Before any happy-path stage is registered, a `stage_name` family SHALL register `<family>_completed` and SHALL explicitly record, with a reason, any of `_failed`, `_rejected`, `_withdrawn`, `_expired`, `_abandoned` it does not need. Enforced by CI. This closes four separately-catalogued gaps at once: no registered family has a `*_withdrawn` value although two rulings depend on one; seven terminal-failure stages are absent from the Profile enumeration; and `FUND_EXPIRED` was invented ad hoc for a single family.

**R8 — `expired` is not `failed` is not `withdrawn`.** Three different populations with three different owners. `failed` — something decided no. `expired` — nobody decided; a collect was never actioned, a mandate never approved, a vendor timed out. `withdrawn` — the customer decided no. A vendor timeout is `expired`, is **not** a failure, and SHALL NOT consume an attempt. Every module's stage family and outcome vocabulary SHALL keep the three separate.

**R9 — `outcome_code` is one namespace, partitioned by module, cased `<DOMAIN>_<CONDITION>`.** Two modules SHALL NOT assign different meanings to one code string. A code emitted from outside its owning module's published list is a defect, not a new value. SCREAMING_SNAKE with a domain prefix, without exception — the convention was broken once inside a single release by a bare `BELOW_MINIMUM`.

### The de-duplication key — corrected

The published key is `context_id + event_name`. It was written for milestones that fire once and is **already inadequate**: `Vendor Call Completed`, `OTP Requested`, `Request Stage Changed` and `Order State Changed` each fire many times against one `context_id`. A replayed webhook is silently accepted, or a legitimate repetition is silently dropped — and a drop looks like a conversion cliff, not like a bug.

| Event family | De-duplication key |
|---|---|
| Once-per-record milestones (`KYC Started`, `KYC Submitted`, `Agreement Generated`, `Application Approved`, `Application Rejected`, `Permitted To Trade`, `Activation Completed`, `Registration Completed`, `Mobile Verified`) | `context_id + event_name` — unchanged |
| `Journey Step Completed` | `context_id + event_name + step_name + attempt_index` |
| `Journey Step Failed` | `context_id + event_name + step_name + attempt_index + vendor_attempt` |
| `Journey Step Abandoned` | `context_id + event_name + step_name` |
| `Request Stage Changed` | `context_id + event_name + stage_name` |
| `Order State Changed` | `context_id + event_name + order_state + filled_quantity + transition_index` |
| `Vendor Call Completed` | `context_id + event_name + service_id + vendor_attempt` |
| `OTP Requested` / `OTP Resolved` | `context_id + event_name + otp_purpose + otp_channel + resend_index` |
| `Message Dispatched` | `context_id + event_name + template_id + touch_index` |
| `Document Retrieved` | `context_id + event_name + report_type + period_preset + delivery_method` |
| `Attempt Cap Reached` | `context_id + event_name + cap_type + total_submissions` — **new** |
| `Action Blocked` | `context_id + event_name + blocked_reason + element_id + session_id` — **new** |

`vendor_attempt` is added to `Journey Step Failed` for exactly one reason, and it is the reason a naive key is dangerous: **`attempt_index` is deliberately non-monotonic.** A vendor timeout does not consume an attempt, so two consecutive legitimate `PAN_VENDOR_TIMEOUT` failures carry the same `attempt_index` and would collapse into one row under any key that stops there — silently under-counting the exact number the vendor-reliability read exists to produce. `vendor_attempt` increments on every call including timeouts, so it discriminates them. This is the one schema addition to the spine and it is justified as the discriminator that makes replay-safe de-duplication possible.

**`transition_index` and `otp_channel` — the same defect, twice more.** `filled_quantity` is declared **cumulative** in §5.6, so two consecutive `order_state: modified` rows on an unfilled order carry identical values in all four key positions and are byte-identical. The modify-cap funnel — `cap_type: order_replace` is registered for it — then has no numerator; and under a per-fill reading instead, every repeated partial fill at the same size is dropped and reads as a conversion cliff. `transition_index` is a **server-assigned monotonic counter per `context_id`** (§5.3) and discriminates them, exactly as `vendor_attempt` does one paragraph above. It is registered on `Request Stage Changed` as well, for the families that declare a legitimately repeating stage, but that key is unchanged in this revision. `otp_channel` joins the OTP key because the sign-in OTP is dispatched to the registered mobile **and** the email at once: one `otp_purpose: login` at `resend_index: 0` produces **two** lawful rows and the published key collapses them — halving the denominator §4 L5 bought `OTP Resolved` to produce. It is widened onto `OTP Resolved` in §5.8, where it was out of scope.

**The two new keys cost nothing.** `total_submissions` is already registered on `Attempt Cap Reached` as *"raw submits including unchanged resubmits"* and is monotonic by construction. `blocked_reason`, `element_id` and `session_id` are already on `Action Blocked` or in its envelope. Neither is a schema addition.

**What is NOT de-duplicated on a business key — a ruling, and a forced one.** Seven names are declared **not de-duplicated on a business key**: the six frozen generic names — `Screen Viewed` · `Element Clicked` · `Overlay Opened` · `Overlay Dismissed` · `Field Errored` · `Media Captured` — plus `Notification Deep Link Opened`. To key any of them you must key on the property that separates two legitimate occurrences, and **no registered property does**: a screen can be entered twice with identical `entry_direction`, `referrer_screen` and `nav_source_element`; a control tapped twice with identical `element_id` and `item_value`; a notification opened twice under one `notification_id`. A business key therefore needs an **occurrence counter**, and Layer 1's schemas are **frozen** — adding a counter there is the single move that layer exists to prevent, and R6 routes any new property on an existing name to the registrar. Idempotency on these seven is delegated to the SDK's per-event id. This document already assumes it three times over: `is_disabled: true` is *"intent without ability"*, a count that exists only if repeated taps on one disabled control produce repeated rows; `seconds_on_screen`, `edits_made` and `retakes` are per-occurrence measurements and meaningless at one row per record; and §6 closes funnels on `entry_direction = forward` precisely because more than one `Screen Viewed` per record survives.

**Acceptance test — amended, and the amendment is the point.** Replay every event type twice and assert **one row each for the keyed names**. Then emit **two legitimate consecutive occurrences of every name** and assert **two rows**. Then replay two `PAN_VENDOR_TIMEOUT` failures at the same `attempt_index`; assert **two** rows. The first sentence alone was what v1.0.0 published, and on its own it certifies the exact collapse the ruling above forbids: it tests **replay**, not legitimate **repetition**, and every unkeyed name is `client`-sourced with no webhook to replay in the first place.

---

## 4. The event catalogue

**40 names. 29 kept · 4 renamed · 7 new · 0 absorbed · 0 deleted.**

### Layer 1 — Generic UI (6, schemas frozen)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Screen Viewed` | keep | Screen **entry** — once per entry, never on component mount or re-render | `entry_direction` · `nav_source_element` · `referrer_screen` · `seconds_since_prev_screen` | client |
| `Element Clicked` | keep | Any tap on any control, including a tap on a **disabled** control | `element_id` · `element_label` · `element_type` · `action_type` · `item_group` · `item_value` · `selected` · `is_disabled` · `opens_external` · `input_method` · `errors_shown` · `error_codes[]` · `edits_made` · `seconds_on_screen` · `seconds_since_overlay_open` | client |
| `Overlay Opened` | keep | Modal, sheet, drawer or inline edit region opens | `overlay_id` · `surface_type` · `is_dismissible` · `trigger` | client |
| `Overlay Dismissed` | keep | A **dismissible** overlay closes | `overlay_id` · `surface_type` · `dismiss_method` · `seconds_open` | client |
| `Field Errored` | keep | Inline validation surfaced with **no vendor call and no attempt consumed** | `field_id` · `error_code` · `attempt_no` | client |
| `Media Captured` | keep | Camera, draw pad or file upload produces an artefact | `capture_type` · **`capture_method`** *(renamed from `method`)* · `retakes` · `file_size_kb` · `file_type` · `is_password_protected` | client |

`Element Clicked` at 15 event-level + 11 envelope = **26 properties** — the published *"~19"* figure is wrong — but it is **not** the widest payload. **`Request Stage Changed` is**, at 17 event-level + 11 envelope = **28**; `Order State Changed` is second at 27 and `Element Clicked` third at 26. `transition_index` (§3) adds one to each of the first two. **No event approaches the 100-property server cap** — the true maximum is 29 of 100 — and the cap should stop being cited as a constraint on the design.

`Field Errored` must stay separate from a step failure or the attempt-counting rule breaks: an unchanged resubmit calls no vendor, emits `Field Errored{PAN_SAME_VALUE}`, and leaves `attempts_remaining` untouched.

### Layer 2 — Journey spine (3, renamed)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Journey Step Completed` | **renamed** from `KYC Step Completed` | Any step of **any** multi-step journey completes. An explicit exit is a completion, not an absence | `step_name` *(module-scoped)* · `method` · `attempt_index` · `duration_sec` · `vendor` · `vendor_ms` · `fallback_used` · per-step outcome fields | server |
| `Journey Step Failed` | **renamed** from `KYC Step Failed` | A step fails against a vendor or a rule. A timeout is `expired`, not a failure, and consumes no attempt | `step_name` · `outcome_code` · `error_class` · `is_terminal` · `attempts_remaining` · `cap_reached` · `vendor` · **`vendor_attempt`** *(new — see §3)* · `permission_state` | server |
| `Journey Step Abandoned` | **renamed** from `KYC Step Abandoned` | Server sweep once the applicant is idle past the per-step threshold. Never the browser — it is gone by definition | `step_name` · `dropoff_class` · `dropoff_context` · `last_outcome_code` · `time_on_step_sec` | server (sweep, `platform: system`) |

**Why renamed.** The layer table already advertises this spine as reusable for *"any multi-step flow"* while all three names literally begin with KYC. Six multi-step journeys are already demanded — segment activation, IPO apply, fund withdrawal, pledge, account recovery, report generation. Either these names generalise or every one of those buys three names: **3 renames now against 18 names later.** See §8 for the cost and the window.

`Journey Step Abandoned` still fires **nowhere** until per-step idle thresholds are set (OD-5). The rename generalises a blocker, not a capability — which is exactly why §6 defines the **open-step population** as a funnel rule that needs no sweep and no threshold.

### Layer 3 — Journey milestones (6)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `KYC Started` | keep | The Begin KYC tap | `days_since_registration` · `entry_source` | client |
| `KYC Submitted` | keep | Application submitted | `active_duration_sec` · `sessions_used` · `segments_dropped` · `nominee_outcome` · `fallbacks_used` · `steps_failed_count` | server |
| `Agreement Generated` | **renamed** from `AOF Generated` | **Any** versioned agreement is generated for signature — the KYC AOF today, the segment-addition form and DDPI form next | `agreement_type` · **`artefact_version`** *(replaces `agreement_version` — §7)* · `segments_on_aof` · `segments_selected` · `segments_dropped` · `drop_reason` · `ddpi_opted_in` · `nominee_outcome` | server |
| `Application Approved` | keep | STP or ops approval. **Not** permission to trade | `decision_hours` · `manual_touch_count` | server |
| `Application Rejected` | keep | Ops or automated rejection | `reason_code` · `is_sanctions` · `can_reapply` · **`is_recoverable`** *(new)* | server |
| `Permitted To Trade` | keep | UCC + BO ID + ≥1 active segment. **North star** | `total_journey_hours` · `segments_active` · `journey_variant` · `manual_touch_count` | server |

`Agreement Generated` is renamed because the Profile PRD explicitly forbids reusing the AOF event for the segment-addition form — *"a separate versioned artefact with its own e-Sign"*. Without the rename, segment activation buys a name. `is_recoverable` closes the open finding that five of six panel rejection reasons are recoverable and the comms engine cannot pick its rejection shell until the event says which.

**`Permitted To Trade` stays one name, one click.** This is deliberate. §22.1g's own deciding argument — *"audiences are authored by people, and a looked-up value is a mis-keyed value"* — applies with more force to event names than it did to stage spellings. Turning the north star into three or four filters was the sharpest cost of the alternatives considered and it is not paid here.

### Layer 4 — Auth (4)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Registration Started` | keep | First touch on a registration surface | `entry_point` · `utm_source` · `utm_campaign` · **`referral_code`** *(new)* | client |
| `Mobile Verified` | keep | Mobile OTP verified — identity binding | `otp_channel` · `resend_index` · `time_to_verify_sec` | server |
| `Registration Completed` | keep | Both factors verified. The point the 5–10% holdout is assigned | `second_factor_method` · `time_since_mobile_sec` | server |
| `Login Completed` | keep | A full sign-in resolves. **No longer carries the idle unlock** | `mode` (`LOG-00`…`LOG-03`) · **`resolved_mode`** *(new)* · `landed_on_screen` · `via_deeplink` · `outcome` · **`failure_reason`** *(new)* | server |

Removing `LOG-04` from `mode` stops a sub-1.5s single-factor session resume being counted as a full-2FA trading login — which today corrupts login volume, the per-mode SLA and the SEBI two-factor compliance split simultaneously. It moves to `Session State Changed`.

### Layer 5 — Cross-cutting (7 — one new)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `OTP Requested` | keep | Any OTP **dispatch** anywhere in the product | `otp_purpose` · `otp_channel` · `resend_index` | server |
| `OTP Resolved` | **new** | That OTP is entered, expires, is superseded by a resend, or is abandoned | `otp_purpose` · `resend_index` · `otp_outcome` · `seconds_to_entry` | server |
| `Vendor Call Completed` | keep | Every provider call including the backend chain. Facts from the gateway, never the browser | `service_id` · `vendor` · `outcome` · `latency_ms` · `failover_to` · `vendor_attempt` · **`related_context_id`** *(new)* | server |
| `Vendor Failure Detected` | keep | All failover tiers exhausted | `service_id` · `is_outage` · `vendors_exhausted` · `incident_id` | server |
| `Attempt Cap Reached` | keep | Any retry ceiling hit | `cap_type` · `distinct_values_tried` · `total_submissions` · `support_route_shown` | server |
| `Manual Fallback Entered` | keep | Any offline path taken | `fallback_type` · **`fallback_trigger`** *(renamed from `trigger`)* | server |
| `Journey Resumed` | keep | Re-entry onto a drop screen for **any** module's journey | `resume_source` · `campaign_id` · `days_since_dropoff` · `landed_on_screen` · `state_intact` | server |

**`OTP Resolved` — justification against R6.** Today an OTP is dispatched and **nothing records whether it was entered.** *"What fraction of contact-change OTPs are completed"* is unanswerable across all fourteen `otp_purpose` values, and the auth PRD's four hard ceilings (5 resends per session, 10/hour, 20/24h, 5 failed attempts → 15-minute lock) have a numerator and no denominator. Consuming modules: **auth** (register, login, session unlock, both recovery purposes), **profile** (contact change, unfreeze), **ipo** (mandate), **kyc** (digilocker, esign, AA consent), **funds** (withdrawal confirm). Five modules, one name, one uniform shape.

### Layer 6 — Ops and lifecycle (3)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Ops Decision Recorded` | keep | Any panel action on the ops event bus — KYC review, support agent, risk desk | `decision` · `reason_code` · `queue` · `sla_hours` · `hold_type` · **`escalated_to_level`** *(new)* | server (`platform: ops_console`) |
| `Activation Completed` | keep | First login after PTT — PIN set, 2FA armed | `biometric_enrolled` · `nudge_skipped` · `hours_since_ptt` | server |
| `Account Detail Changed` | keep — **scope widened** | Any mutation of a customer-owned setting or record in **any** module, not only profile | `field_group` · `action` · `requires_reverification` · `verification_method` · `outcome` · `freeze_type` · `initiated_by` · `sessions_ended` | server |

Widening `Account Detail Changed` beyond `module: profile` lets **markets** carry watchlist edits (`field_group: watchlist`) and **alerts** carry preference changes (`field_group: alert`) and **auth** carry credential mutations (`field_group: security`, `action: enroll · revoke`) with **no new name**. That is the single largest reuse win in this document.

### Layer 7 — Product-wide requests, reveals and refusals (3)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Request Stage Changed` | keep | Any customer-trackable multi-step request moves. **One per transition**, never inferred from a tracker being opened | `request_type` · `stage_name` · `previous_stage_name` · `seconds_in_previous` · `seconds_in_request` · `leg` · `initiated_by` · `charge_paise` · `amount_paise` · `funding_method` · `source_bank_ref` · `amount_source` · `outcome_code` · **`related_context_id`** *(new)* | server |
| `Action Blocked` | keep | Any control or journey the product refuses. Once per refusal | `blocked_reason` · **`request_type`** *(new — §5.3)* · `element_id` · `was_journey_entry` · `seconds_to_cutoff` | client |
| `Sensitive Value Revealed` | keep | Any unmask on any surface. Carries the **group** and the **tier**, never the value or a masked derivative | **`reveal_group`** *(renamed from `field_group`)* · `tier` · `reauth_outcome` | server |

**`request_type` is added to `Action Blocked`, and it is a correction, not a widening.** §5.2 and §7 delete `blocked_reason: submitted` and `blocked_reason: contact_change` on the stated ground that *"the case is covered by `request_in_flight` plus `request_type`"* — naming a mechanism that did not exist on the event. Profile alone has three distinct locks (contact change, nominee change, closure) collapsing onto one `request_in_flight` value, and funds adds a fourth. `Action Blocked` is **Layer 7, not the frozen generic layer**, so R6 permits the property against a named second consumer, and it is one already-registered 🔶 enum reused rather than a new one.

`Request Stage Changed` is the highest-leverage existing name in the taxonomy. With module-scoped `stage_name` and eleven new `request_type` values it carries fund withdrawal, running-account settlement, pledge, unpledge, IPO application, report generation, auth recovery, support tickets, segment activation and SIP mandates — **at a cost of zero new names.**

### Layer 8 — Messaging-side (2, kept and finally given schemas)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Notification Deep Link Opened` | keep — **schema added** | A dispatched message's CTA is opened | `notification_id` · `message_type` · `campaign_id` · `channel` · `landed_on_screen` · `seconds_since_dispatch` · `related_context_id` | client |
| `Service Restored` | keep — **schema added** | A service transitions unavailable → available, after the debounce settle period | `service_id` · `incident_id` · `settle_period_sec` · `audience_size` · `outage_duration_sec` | server |

Both are named exactly once in the source with **no property list anywhere**. Three modules depend on the first — alerts (its entire reason to exist), corporate actions (notice-never-opened is the number that matters for delisting), support (ticket status) — and none can define a funnel on an event with no properties. `Service Restored` is written generically so funds (PSP), IPO (sponsor bank, registrar), orders (exchange gateway) and markets (feed) inherit it free: **extend `service_id`, never the event.** Its `SERVICE_RESTORED` / `Service Restored` two-spelling defect dies with `message_type` (§5).

### Layer 9 — New names (6 more)

| Event | Status | Fires when | Key properties | Source |
|---|---|---|---|---|
| `Consent Captured` | **new** | Any consent artefact is granted, acknowledged or withdrawn, on any surface **including pre-auth** | `artefact_code` · `artefact_version` · `acceptance_mode` · `granted` · `is_withdrawal` | client or server |
| `Message Dispatched` | **new** | Every outbound send attempt **and every suppression**, at the moment of dispatch evaluation | `message_type` · `dispatch_outcome` · `template_id` · `touch_index` · `channel` · `artefact_code` · `originating_stage_name` · `cancelling_stage_name` · `seconds_since_originating` · `related_context_id` | server |
| `Document Retrieved` | **new** | A statement, report, contract note, CMR or e-Signed form is viewed, downloaded or emailed | `report_type` · `period_preset` · `delivery_method` · `row_count` · `is_statutory` · `file_type` | server |
| `Order State Changed` | **new** | An order changes state at the exchange or in the RMS, **and each fill** | `order_state` · `order_type` · `product_type` · `validity` · `instrument_id` · `exchange` · `segment` · `quantity` · `filled_quantity` · `avg_price_paise` · `is_amo` · `seconds_to_cutoff` · `outcome_code` · `related_context_id` | server |
| `Session State Changed` | **new** | A session is created, warned, locked by inactivity, unlocked, logged out or terminated at End-of-Day | `session_state` · `unlock_factor` · `seconds_idle` · `state_intact` | server |
| `Query Answered` | **new** | A question or query is submitted and a result set returned | `query_scope` · `answer_id` · `resolution` · `confidence_ratio` · `candidates_shown` · `query_language` · `lookup_type` · `visitor_state` · `result_count` · `related_context_id` | server |

**Justification against R6, one line each — the second consumer is named or the grain class is.**

| Name | Consumers | Why it cannot ride an existing name |
|---|---|---|
| `Consent Captured` | marketing, referral, research, kyc, tnc | Already used as a live funnel step today while appearing in no layer table, no schema and no budget. This **registers** a name already being emitted against. The comms engine refuses any send without exactly one named consent artefact and currently has nothing to read. Marketing meets it **first**, pre-auth, at the cookie banner — before KYC's four-artefact question is reached. |
| `Message Dispatched` | kyc, alerts, corporate_actions, support, profile, funds | Closes the unnumbered P0: there is no send or suppression event of any kind. A cancelled communication must be logged as suppressed with originating stage, cancelling stage and elapsed interval — never as sent, and never counted against the frequency cap, because counting cancellations as sends corrupts every stage's lift denominator. Alerts' primary funnel has no denominator without it. |
| `Document Retrieved` | reports, profile, corporate_actions | The candidate the reconciliation pass itself left open — *"a shared Document Retrieved if reports needs the same."* Reports needs the same: eighteen report types, five statutory, and an FY of ~120 contract notes retrievable in one action. `Element Clicked` is client-frozen and cannot carry `row_count`. Also unblocks a profile property that today cannot be computed. |
| `Order State Changed` | orders (+ `context_type: trade` at fill grain) | **Grain class, declared under R6.** `context_type: order` is registered in the envelope with no event that ever sets it. `Request Stage Changed` technically fits and is the wrong instrument: it is human-grain (`seconds_in_previous`, one transition per customer-visible wait) and an order state machine is millisecond-grain, exchange-owned, and the highest-volume stream in the product. Riding it would force any volume, cost or sampling decision on orders onto the north-star funnel and onto comms lift — see §6 note. |
| `Session State Changed` | auth, charts | The auth audit set mandates logging session created / idle-warned / locked / unlocked / logged out / EoD-terminated and **zero names cover any of them.** Second consumer is charts: the testable promise of *"100% of open chart layouts, drawings and watchlists preserved across an idle lock"* has no event that can prove it — `state_intact` here does. |
| `Query Answered` | support, markets, research, funds | **Named generically on purpose.** A retrieval is not a form and not a request, and nothing in the catalogue covers a question asked and an answer returned. `query_scope` partitions it: support registers `assistant` · `help_centre` · `account_lookup`; markets registers `instrument_search`, whose `result_count: 0` is the instrument-coverage demand signal; research registers `screener`; funds registers `transaction_search`. |

### Reserved platform name (does not consume budget)

| Event | Status | Fires when | Key properties |
|---|---|---|---|
| `Charged` | keep — **scope ruled** | Thinq **earns** money: brokerage and charges on an executed order, and a collected service charge (`charge_paise > 0`). **Never** on a deposit or a withdrawal | `Items[]` · `Amount` · `charge_category` |

A deposit is the customer's own money moving between two accounts they own; nothing is earned. That leaves brokerage on an executed order plus collected service fees. Extending it to service fees is a **ruling, not a reading** — see OD-4, because firing it wrongly is not retrospectively fixable.

### Count

| | Names |
|---|---|
| Today, counted | 31 |
| Today, actually named (incl. 2 messaging-side with no schema) | **33** |
| Kept unchanged | 29 |
| Renamed | 4 (`Journey Step Completed` · `Journey Step Failed` · `Journey Step Abandoned` · `Agreement Generated`) |
| New | 7 (`Consent Captured` · `Message Dispatched` · `Document Retrieved` · `Order State Changed` · `Session State Changed` · `Query Answered` · `OTP Resolved`) |
| Absorbed or deleted | **0** |
| **Total after this change** | **40** |
| Delta vs today's 31 counted | **+9** |
| Delta vs today's 33 actually named | **+7** |
| Remaining of 512 | **472** (7.8% consumed) |

The three published remainders that disagree today — 484, 482, and 479 implied — are retired. The number is 472 and it is checkable by counting the table above.

---

## 5. The property registry

**Legend.** 🔶 = **module-scoped enumeration** — one closed namespace product-wide, partitioned by `module`, additive only, one registrar. A filter is always `module = X AND <property> = Y`, never a flat lookup. There are **six**: `step_name`, `stage_name`, `outcome_code`, `blocked_reason`, `request_type`, `cap_type`. Two id spaces sit on the same footing without being enums: `service_id`, `element_id`.

### 5.1 Envelope properties

Listed in full in §2. Module-scoped members: `sub_module`, `step_name` 🔶, `stage_name` 🔶.

**`sub_module` — complete registry**

| Module | Values |
|---|---|
| `profile` | `personal_details` · `bank_accounts` · `nominee` · `address` · `ddpi` · `security` · `preferences` · `tariff` · `documents` · `freeze` · `closure` · `contact_details` **(new)** · `demat_details` **(new)** · `segment_list` **(new)** |
| `funds` | `add` · `withdraw` · `ledger` · `margin` · `health` **(new)** |
| `markets` | `watchlist` · `search` · `quote` · `depth` · `indices` |
| `orders` | `place` · `modify` · `cancel` · `basket` · `gtt` · `order_book` · `trade_book` |
| `portfolio` | `positions` · `holdings` · `analytics` · `pledge` |
| `reports` | `contract_note` · `pnl` · `capital_gains` · `ledger` · `tax` |
| `mutual_funds` | `orders` · `sip` · `holdings` |
| The other 11 | null |

**`step_name` 🔶 — complete registry** *(nullable; null wherever no multi-step journey is in progress)*

| Module | Values | Status |
|---|---|---|
| `kyc` | `pan` · `profile` · `bank` · `address` · `aadhaar` · `selfie` · `segments` · `income_proof` · `nominee` · `signature` · `esign` — **11, closed** | existing |
| `segment_activation` | `segment_select` · `income_proof` · `form_sign` | new |
| `funds` | `add` · `withdraw` | new |
| `ipo` | `issue_select` · `bid` · `mandate` | new |
| `auth` | `register` · `recover` | new |
| `reports` | `select` · `generate` | new |
| All others | null | — |

`permissions` is **not** a value: there is no permissions screen in the build; camera, mic and location are requested at Selfie. The four `PERM_*` codes attach to `step_name: selfie`. The rogue twelfth header `registration` becomes lawful as `module: auth`, `step_name: register`.

**`stage_name` 🔶 — complete registry** *(nullable)*

| Module | Values | Terminal of record |
|---|---|---|
| `kyc` | The 38 slugs, **unchanged**: `visited_no_mobile` · `mobile_unverified` · `email_pending` · `registered_kyc_not_started` · `pan_not_submitted` · `pan_failed_fixable` · `pan_attempts_exhausted` · `pan_failed_terminal` · `pan_not_eligible` · `kra_ckyc_pending` · `digilocker_incomplete` · `address_not_confirmed` · `selfie_not_taken` · `selfie_no_match` · `video_verification_pending` · `bank_not_verified` · `bank_name_mismatch` · `bank_manual_exhausted` · `signature_not_done` · `profile_incomplete` · `segments_not_chosen` · `income_proof_not_given` · `income_proof_rejected` · `nominee_incomplete` · `esign_not_started` · `esign_otp_pending` · `esign_name_mismatch` · `esign_failed` · `sending_to_kra_ckyc` · `creating_broker_account` · `sending_to_exchange` · `partially_activated` · `awaiting_review` · `info_requested` · `on_hold_checks` · `rejected` · `approved_not_tradable` · `ready_to_trade` | `ready_to_trade` |
| `profile` — `chg_*` | `chg_submitted` · `chg_identity_verified` · `chg_esigned` · `chg_kra_registering` · `chg_kra_registered` · `chg_dp_updating` · `chg_dp_updated` · `chg_completed` · `chg_kra_rejected` **(new)** · `chg_dp_failed` **(new)** · `chg_withdrawn` **(new)** | `chg_completed` |
| `profile` — `nom_*` | `nom_submitted` · `nom_esigned` · `nom_dp_registering` · `nom_dp_registered` · `nom_completed` · `nom_rejected` **(new)** · `nom_withdrawn` **(new)** | `nom_completed` |
| `profile` — `nomedit_*` | `nomedit_submitted` · `nomedit_under_review` · `nomedit_reviewed` · `nomedit_dp_updating` · `nomedit_dp_updated` · `nomedit_completed` · `nomedit_rejected` **(new)** · `nomedit_withdrawn` **(new)** | `nomedit_completed` |
| `profile` — `ddpi_*` | `ddpi_submitted` · `ddpi_esigned` · `ddpi_dp_registering` · `ddpi_dp_registered` · `ddpi_active` · `ddpi_rejected` **(new)** · `ddpi_withdrawn` **(new)** | `ddpi_active` |
| `profile` — `clo_*` | `clo_submitted` · `clo_esigned` · `clo_exch_submitting` · `clo_exch_done` · `clo_dp_submitting` · `clo_dp_done` · `clo_completed` · `clo_withdrawn` **(new)** | `clo_completed` |
| `profile` — `bank_*` **(entire family new)** | `bank_submitted` · `bank_penny_in_flight` · `bank_name_matching` · `bank_verified` · `bank_name_mismatch` · `bank_penny_failed` · `bank_rejected` · `bank_withdrawn` | `bank_verified` |
| `profile` — `unf_*` **(new)** | `unf_submitted` · `unf_reviewed` · `unf_completed` · `unf_rejected` | `unf_completed` |
| `segment_activation` — `seg_*` | `seg_submitted` · `seg_proof_verifying` · `seg_proof_verified` · `seg_esigned` · `seg_thinq_reviewing` · `seg_thinq_approved` · `seg_exch_enabling` · `seg_exch_enabled` · `seg_active` · `seg_proof_rejected` **(new)** · `seg_rejected` **(new)** · `seg_withdrawn` **(new)** · `seg_deactivated` **(new)** | `seg_active` |
| `funds` — `fund_*` | `fund_initiated` · `fund_collect_sent` · `fund_approved` · `fund_credited` · `fund_failed` · `fund_expired` · `fund_awaiting_confirmation` **(new)** · `fund_reversed` **(new)** | `fund_credited` |
| `funds` — `wdl_*` **(new)** | `wdl_requested` · `wdl_cutoff_queued` · `wdl_approved` · `wdl_bank_sent` · `wdl_credited` · `wdl_rejected` · `wdl_cancelled` · `wdl_under_review` **(new)** · `wdl_part_sent` **(new)** · `wdl_nil_settled` **(new)** · `wdl_bank_returned` **(new)** · `wdl_rail_queued` **(new)** | `wdl_credited` — `wdl_rejected` is *we* refused, `wdl_bank_returned` is *the bank* refused, `wdl_nil_settled` is nobody refused (R8) |
| `funds` — `rac_*` **(new)** | `rac_due` · `rac_computed` · `rac_bank_sent` · `rac_credited` · `rac_failed` | `rac_credited` |
| `funds` — `due_*` **(new family)** | `due_raised` · `due_notified` · `due_part_paid` · `due_cleared` · `due_written_off` | `due_cleared` — `due_written_off` is the other terminal |
| `funds` — `shf_*` **(new family)** | `shf_raised` · `shf_notified` · `shf_cleared` · `shf_squared_off` · `shf_expired` | `shf_cleared` — `shf_squared_off` is the outcome where the firm acted instead |
| `ipo` — `ipo_*` **(new)** | `ipo_submitted` · `ipo_mandate_sent` · `ipo_mandate_approved` · `ipo_under_review` · `ipo_allotted` · `ipo_not_allotted` · `ipo_refunded` · `ipo_completed` · `ipo_expired` · `ipo_withdrawn` | `ipo_completed` |
| `reports` — `rep_*` **(new)** | `rep_requested` · `rep_ready` · `rep_failed` · `rep_expired` | **none** — `rep_ready` is a handover, not a completion. **Open** |
| `auth` — `rec_*` **(new)** | `rec_started` · `rec_old_channel_authorised` · `rec_new_address_verified` · `rec_credential_reset` · `rec_completed` · `rec_failed` · `rec_abandoned` | `rec_completed` |
| `support` — `tkt_*` **(new)** | `tkt_open` · `tkt_progress` · `tkt_awaiting` · `tkt_resolved` · `tkt_reopened` · `tkt_closed` · `tkt_withdrawn` | `tkt_resolved` — `tkt_closed` is administrative closure, not resolution |
| `portfolio` — `plg_*` / `unplg_*` **(new)** | `plg_submitted` · `plg_dp_in_flight` · `plg_completed` · `plg_rejected` · `plg_withdrawn` · `unplg_submitted` · `unplg_dp_in_flight` · `unplg_completed` · `unplg_rejected` · `unplg_withdrawn` **(new — the R7 gap the family carried at v1.0.0)** | `plg_completed` / `unplg_completed` |
| `corporate_actions` — `ca_*` **(new)** | `ca_announced` · `ca_record_date` · `ca_election_open` · `ca_elected` · `ca_credited` · `ca_expired` | `ca_credited`. **R7 waiver, recorded:** `_failed` · `_rejected` · `_withdrawn` have no customer-side population — a corporate action is the issuer's record and an election not made lands on `ca_expired`. An issuer-side cancellation has no stage today; corporate_actions registers `ca_cancelled` under R6 at zero cost if it needs one |
| `mutual_funds` — `sip_*` **(new)** | `sip_created` · `sip_active` · `sip_paused` · `sip_instalment_collected` · `sip_instalment_failed` · `sip_stopped` | **none, by design** — non-terminating (see below) |
| `referral` — `ref_*` **(new)** | `ref_shared` · `ref_registered` · `ref_qualified` · `ref_credited` · `ref_expired` | `ref_credited` |
| `alerts` | null — an alert has no multi-stage lifecycle; it is created, triggered or deleted (`Account Detail Changed`, `Message Dispatched`) | n/a |
| `marketing`, `markets`, `charts`, `research` | null | n/a |

**R7 is keyed on the Terminal-of-record column, not on a string pattern.** v1.0.0 asserted *"every family above satisfies R7"* against a rule demanding a literal `<family>_completed`, and **six families do not match `_completed`, `_active` or `_credited` at all**: `bank_*` ends at `bank_verified`, `tkt_*` at `tkt_resolved`, `kyc` at `ready_to_trade`, `due_*` at `due_cleared`, `shf_*` at `shf_cleared`, and `rep_*` has **no terminal of record whatsoever** — `rep_ready` is a handover to the customer, not a completion of the request. The R7 CI job in §8 SHALL therefore assert **(a)** every family names exactly one terminal of record in this column, and **(b)** every one of `_failed` · `_rejected` · `_withdrawn` · `_expired` · `_abandoned` it does not register is waived **with a reason recorded in this section**. It SHALL NOT grep for `<family>_completed`.

**Literal `<family>_completed` aliases SHALL NOT be minted** to satisfy the old reading. An alias makes one transition emit under two values, which is the R1 problem one layer down and the exact defect `bank_manual` / `bank_manual_entry` already cost this registry once.

**Two open items this column exposes, stated rather than buried.** `rep_*` needs a terminal of record before `open_request_types` — the profile set defined as *"requests with no terminal stage"* — can be computed at all; it currently reads `reports` as permanently open. And `sip_*` is the one **non-terminating** family by design: a standing instruction rests at `sip_active` or `sip_paused` indefinitely and its instalments are separate `context_type: fund_txn` records joined by `related_context_id`; `rac_*` is cyclic on the same pattern. Terminality is read from the stage and **never** sent as a property.

### 5.2 Outcome and reason vocabularies

**`outcome_code` 🔶** — one namespace, partitioned by module, `<DOMAIN>_<CONDITION>` SCREAMING_SNAKE (R9). On `Journey Step Failed`, `Request Stage Changed` (failure stages), `Order State Changed`, and — same registry, different property name — as `error_code` on `Field Errored`.

| Module | Values |
|---|---|
| `kyc` | **53, unchanged.** `permissions` group → `step_name: selfie`: `PERM_CAMERA_DENIED` · `PERM_CAMERA_BLOCKED` · `PERM_LOCATION_DENIED` · `PERM_UNSUPPORTED_BROWSER`. `pan` (11): `PAN_FORMAT_INVALID` · `PAN_SAME_VALUE` · `PAN_NOT_FOUND` · `PAN_INOPERATIVE` · `PAN_AADHAAR_UNLINKED` · `PAN_DECEASED` · `PAN_DUPLICATE` · `PAN_NON_INDIVIDUAL` · `PAN_MINOR` · `PAN_NON_RESIDENT` · `PAN_VENDOR_TIMEOUT`. `bank` (7): `BANK_NAME_MISMATCH` · `BANK_PENNY_DROP_FAIL` · `BANK_IFSC_INVALID` · `BANK_ACCOUNT_INACTIVE` · `BANK_MANUAL_LIMIT` · `BANK_UPI_CANCELLED` · `BANK_PASTE_BLOCKED` *(renamed from `PASTE_BLOCKED`)*. `address` (3): `ADDR_DISPUTED_BY_USER` · `ADDR_NO_RECORD` · `ADDR_FATHER_NAME_TOO_SHORT` *(renamed from `FATHER_NAME_TOO_SHORT`)*. `aadhaar` (6): `DIGI_VENDOR_DOWN` · `DIGI_OTP_FAIL` · `DIGI_PIN_FAIL` · `DIGI_NO_RECORD` · `DIGI_SESSION_EXPIRED` · `DIGI_NAME_MISMATCH`. `selfie` (6): `SELFIE_DOC_MISMATCH` · `SELFIE_LIVENESS_FAIL` · `SELFIE_BLUR` · `SELFIE_MULTI_FACE` · `SELFIE_NO_FACE` · `SELFIE_SPOOF`. `income_proof` (7): `INC_NAME_MISMATCH` · `INC_BELOW_THRESHOLD` · `INC_UNREADABLE` · `INC_STALE_PERIOD` · `AA_FETCH_FAIL` · `AA_CONSENT_DECLINED` · `DOC_PASSWORD_WRONG`. `nominee` (3): `NOM_GUARDIAN_UNDER_18` · `NOM_SHARE_NOT_100` · `NOM_NAME_REQUIRED`. `signature` (2): `SIG_TOO_FEW_POINTS` · `SIG_TOO_SMALL`. `esign` (4): `ESIGN_OTP_FAIL` · `ESIGN_NAME_MISMATCH` · `ESIGN_SESSION_EXPIRED` · `ESIGN_PROVIDER_DOWN`. **The count is 53** — 4 + 11 + 7 + 3 + 6 + 6 + 7 + 3 + 2 + 4, which is what the groups above sum to. Not 52 and not ~50 either. v1.0.0 published 54 three times over; the missing 54th was `DUPLICATE_IDENTIFIER` — now `AUTH_DUPLICATE_IDENTIFIER` — which this same section reassigns to `module: auth` and which was therefore being counted in two modules at once. |
| `auth` | `AUTH_DUPLICATE_IDENTIFIER` *(existing, renamed from the bare `DUPLICATE_IDENTIFIER`)* + **new:** `AUTH_OTP_EXPIRED` · `AUTH_OTP_INVALID` · `AUTH_RESEND_CEILING` · `AUTH_LOCKOUT` · `AUTH_ABUSE_CHALLENGE` · `AUTH_IDLE_LOCK` · `AUTH_EOD_TERMINATED` · `AUTH_CONTACT_COLLISION` · `AUTH_UNSUPPORTED_DEVICE`. **Still incomplete** — OD-6. |
| `funds` | `FUNDS_PSP_DECLINED` · `FUNDS_INSUFFICIENT_BALANCE` · `FUNDS_TIMEOUT` · `FUNDS_REVERSED` · `FUNDS_LIMIT_EXCEEDED` · `FUNDS_BELOW_MINIMUM` *(renamed from `BELOW_MINIMUM`)* · `FUNDS_EXCEEDS_WITHDRAWABLE` **(new)** · `WDL_NEW_TRADES_PLACED` **(new)** · `WDL_BANK_RETURNED` **(new — spelled `RETURNED`, never `REJECTED`: `wdl_rejected` is *our* refusal and `wdl_bank_returned` the bank's, and one string cannot carry both)** · **eleven added:** `FUNDS_BANK_TXN_LIMIT` · `FUNDS_GATEWAY_UNREACHABLE` · `FUNDS_USER_CANCELLED` · `FUNDS_SOURCE_UNPROVEN` · `FUNDS_BANK_RECALL` · `FUNDS_DUPLICATE_CREDIT` · `WDL_AFTER_CUTOFF` · `WDL_HELD_FOR_REVIEW` · `WDL_PARTIAL_AVAILABLE` · `WDL_NOTHING_AVAILABLE` · `WDL_RAIL_UNAVAILABLE`. `FUNDS_LIMIT_EXCEEDED` is **ruled** to mean *our* daily route cap only; the bank's per-payment limit is `FUNDS_BANK_TXN_LIMIT`, because two limits with two owners and two recoveries cannot share one code (R9) |
| `orders` **(all new)** | `ORD_INSUFFICIENT_FUNDS` · `ORD_CIRCUIT_LIMIT` · `ORD_FREEZE_QTY_EXCEEDED` · `ORD_ASM_GSM_RESTRICTED` · `ORD_T2T_RESTRICTED` · `ORD_MARKET_CLOSED` · `ORD_NOT_SELLABLE_T1` · `ORD_PLEDGED_QTY` · `ORD_MARGIN_SHORT` · `ORD_PEAK_MARGIN` · `ORD_ALREADY_EXECUTED` |
| `profile` **(new)** | `PROFILE_KRA_REJECTED` · `PROFILE_DP_FAILED` · `PROFILE_BANK_PENNY_FAILED` · `PROFILE_BANK_NAME_MISMATCH` |
| `segment_activation` **(new)** | `SEG_PROOF_REJECTED` · `SEG_REJECTED` · `SEG_OPEN_POSITIONS` |
| `ipo` **(new)** | `IPO_MANDATE_NOT_ACTIONED` · `IPO_NOT_ALLOTTED` · `IPO_ISSUE_WITHDRAWN` |
| `reports` **(new)** | `REP_GENERATION_FAILED` · `REP_PERIOD_UNAVAILABLE` |
| `support` **(new)** | `SUPPORT_NO_MATCH` · `SUPPORT_REFUSED_ADVICE` · `SUPPORT_REFUSED_WRITE` |

**Orders is the urgent case.** Its reject reasons are **already published to customers** in the help centre and the assistant is already required to decode *"the exchange reject code into plain language and the fix"*. The vocabulary is externally committed before it is internally registered — the exact inversion of the closed-and-additive-only guarantee.

**`error_class`** — 4, closed: `validation` · `friction` · `hard_blocker` · `technical`. On `Journey Step Failed`; profile as `last_error_class`. Survives R1 under the carve-out (server-owned static map, multi-module break-down, never human-authored). **The per-code mapping is still unratified** — OD-7. It decides nudge vs corrective guidance vs apology vs silence for all 53 KYC codes.

**`blocked_reason` 🔶** — on `Action Blocked`.

| Module | Values |
|---|---|
| `profile` | `settlement_window` · `request_in_flight` · `closure_in_progress` *(renamed from `closure`)* |
| `funds` | `no_verified_bank` · `account_frozen` · ~~`unsettled_funds`~~ *(superseded — see below)* · `negative_balance` **(all new in 1.0.0 — the module registered zero despite four refusals described in prose)** · `nothing_withdrawable` **(new)** · `stale_margin_data` **(new)** · `request_in_flight` **(new)** · `route_cap_exhausted` **(new)** · `below_minimum` **(new)** |
| `orders` **(new)** | `market_closed` · `segment_inactive` · `margin_short` · `peak_margin` · `circuit_limit` · `freeze_qty` · `asm_gsm` · `t2t` · `order_executed` |
| `portfolio` **(new)** | `not_sellable_t1` · `pledged` · `blocked_against_order` |
| `ipo` **(new)** | `issue_closed` · `cutoff_passed` · `no_upi_mandate` · `insufficient_funds` |
| `segment_activation` **(new)** | `open_positions_exist` |

**Three values removed, each fixing a collision.** `pre_activation` is derivable from the envelope's `account_state` (`prospect` / `kyc_in_progress` / `kyc_submitted`). `submitted` collided with `account_state: kyc_submitted` and with every `*_submitted` stage. `contact_change` collided with `otp_purpose: contact_change` — the case is covered by `request_in_flight` plus `request_type`, **which is why `request_type` is now scoped onto `Action Blocked` (§4 L7, §5.3)**: v1.0.0 justified both deletions by naming a mechanism that did not exist on the event, leaving profile's three locks and funds' fourth collapsed onto one `request_in_flight` value. This also settles the count defect: the source calls these *"six, one per lock"* against a table with **five** rows whose own header says **four** states.

**`blocked_reason: unsettled_funds` is superseded, not reclaimed.** It names one *deduction* where `nothing_withdrawable` names the *refusal*; **which** deduction controlled the refusal is a second axis and the funds registration owns it. One value cannot be both. It is also the retired spelling of `unsettled_credits`, the live string every filter and audience list uses; carrying both would be the `bank_manual` / `bank_manual_entry` trap inside the section that names it. The value SHALL NOT be emitted and the string SHALL NOT be re-minted against a different refusal.

**Three bare codes — renamed, not grandfathered. OD-2 is closed and the answer is that nothing has emitted.** R9 requires a domain prefix *"without exception"* and three codes carried none: `PASTE_BLOCKED` (in the kyc `bank` group of 7, every sibling `BANK_*`), `FATHER_NAME_TOO_SHORT` (`address` group, siblings `ADDR_*`) and `DUPLICATE_IDENTIFIER` (the one `auth` code left unprefixed while nine new ones took `AUTH_`). All three **predate this taxonomy**, so the rename was lawful only on the pre-emission gate — and that gate is confirmed open, so the rename lands rather than the exception. They are **`BANK_PASTE_BLOCKED`** · **`ADDR_FATHER_NAME_TOO_SHORT`** · **`AUTH_DUPLICATE_IDENTIFIER`**, listed in §8's value renames, and R9 now has no exceptions at all. The three retired strings are **claimed**: none SHALL be emitted and none SHALL be re-minted against a different condition. A bare condition word is unowned in a shared namespace, and `DUPLICATE_IDENTIFIER` is the string most likely to be re-minted against a code the KYC 0→1 funnel already reads cross-module.

**`reason_code`** — mapped ops list, never free text, on `Application Rejected` and `Ops Decision Recorded`. **No registry exists in either source document.** OD-9 — it must be written and each ground marked terminal or recoverable before the rejection templates can render.

### 5.3 Request, stage and money properties

| Property | Scope | Complete values | Status |
|---|---|---|---|
| `request_type` 🔶 | `Request Stage Changed`, **`Action Blocked`** | **23.** `profile`: `contact_mobile` · `contact_email` · `nominee_add` · `nominee_correct` · `ddpi_activate` · `account_closure` *(renamed from `closure`)* · ~~`freeze_assisted`~~ *(removed — §7)* · `unfreeze_assisted` · `bank_add` **(new)**. `segment_activation`: `segment_activate` *(moved from profile)* · `segment_deactivate` **(new)**. `funds`: `fund_deposit` · `fund_withdrawal` **(new)** · `running_account_settlement` **(new)** · `dues_settlement` **(new)** · `margin_shortfall` **(new)** — both carry `context_type: service_request` and `initiated_by: system`; nobody asked for either, and the alternative was a thirteenth `context_type`. `portfolio`: `pledge` **(new)** · `unpledge` **(new)**. `ipo`: `ipo_application` **(new)**. `reports`: `report_generation` **(new)**. `auth`: `recovery_session` **(new)**. `support`: `support_ticket` **(new)**. `referral`: `referral_reward` **(new)**. `mutual_funds`: `sip_mandate` **(new)** | extended |
| `previous_stage_name` | `Request Stage Changed` | Any `stage_name` for the same module, or **null on the raising transition** | renamed from `previous_stage` |
| `seconds_in_previous` | `Request Stage Changed`, `Order State Changed` | Integer seconds, server-computed | renamed from `hours_in_previous` |
| `seconds_in_request` | `Request Stage Changed`, every transition | Integer seconds since the first transition on this `context_id` | new — replaces both `hours_open` and `time_to_credit_sec` |
| `leg` | `Request Stage Changed` | **8, closed:** `thinq` · `kra` · `depository` · `exchange` · **`psp`** · `bank` **(new)** · `registrar` **(new)** · `sponsor_bank` **(new)** | **defect fixed** |
| `initiated_by` | `Request Stage Changed`, `Account Detail Changed`, `Order State Changed` | **4, closed:** `self_serve` · `assisted` · `ops` · **`system`** | **merges `raised_via` + `initiated_via`** |
| `charge_paise` | `Request Stage Changed` | Integer paise. `0` · `5900` (contact change) · `15000` (DDPI); **open, not enumerated** | kept |
| `amount_paise` | `Request Stage Changed` | Integer paise | kept — host event now declared |
| `avg_price_paise` | `Order State Changed` | Integer paise | new |
| `funding_method` | `Request Stage Changed` (funds, ipo) | **8:** `upi_collect` · `upi_intent` · `upi_mandate` **(new, IPO ASBA)** · `netbanking` · `imps` · `neft` · `rtgs` **(new)** · `cheque` **(new)** | extended |
| `source_bank_ref` | `Request Stage Changed` (funds) | Internal account id (`b1`, `b2`) — **never** the account number, never the IFSC | kept |
| `amount_source` | `Request Stage Changed` (funds) | `chip` · `typed` · `prefilled` | kept |
| `related_context_id` | `Request Stage Changed`, `Order State Changed`, `Vendor Call Completed`, `Message Dispatched`, `Query Answered`, `Notification Deep Link Opened` | Any `context_id` | **new — the record-to-record join** |
| `transition_index` | `Order State Changed`, `Request Stage Changed` | Integer, **server-assigned and monotonic per `context_id`**, 1 = first transition. Never client-set, never reset | **new — the de-duplication discriminator (§3)** |
| `was_journey_entry` | `Action Blocked` | Boolean — the refusal was hit **entering** a journey rather than inside one. Non-nullable | **§4 orphan, now registered** |
| `charge_category` | `Charged` | **Unwritten — the registry Finance owns and OD-4 must produce before `Charged` fires.** §4 names two categories and no more: brokerage on an executed order, and a collected service charge. Anything beyond those two is a Finance ruling, not a registration | **§4 orphan, now registered — values open** |
| `Items[]` | `Charged` | **Platform-reserved.** Array of charge lines. Each line SHALL carry a `charge_category` and its own paise amount, and SHALL NOT carry a regulated identifier or an instrument the customer holds (R4) | **§4 orphan, now registered** |
| `Amount` | `Charged` | **Platform-reserved.** R5 governs it unchanged: **integer paise, never a float**, even though the platform field accepts one | **§4 orphan, now registered** |

**`leg` — confirmed live defect, fixed.** The Profile section declares *"four values: `thinq` · `kra` · `depository` · `exchange`"* at line 1865 and the Funds section emits `psp` at lines 1962, 1963, 1965 and 1966 and depends on it in prose at 1973 — a closed enum contradicted one section later, in the two newest sections, and the sanity check that certifies *"every property appears in exactly one glossary with one vocabulary"* missed it. All eight registered now, before IPO and withdrawals arrive the same way. Survives R1 under the carve-out.

**`initiated_by` — two properties, one question.** `raised_via` (`self_serve` · `assisted`) and `initiated_via` (`self_serve` · `assisted` · `ops`) split one question across two events. `system` is the value **four** demanded behaviours need and neither property has: a running-account settlement payout, a referral reward credit, an SIP instalment, an automated corporate-action credit. `ops` is retained despite overlapping `platform: ops_console`, because an ops action taken through an API is not on the console.

**`related_context_id` — what it unlocks.** Nothing in the taxonomy joins one record to another today. This closes: `trade → order` (one order, many fills at different prices — the fill grain that `context_type: trade` was registered for and nothing set), `fund_txn → ipo_application` (the allotment refund), `fund_txn → order` (margin-rejection recovery, the most valuable funnel in a broking product), `message → the record it concerns`, `support_ticket → kyc_case`, `fund_txn → sip_mandate`, and the referral invitee → sharer link. **Note the exception it forces:** funnels are defined over `context_id`, never identity, but a referral funnel joins the sharer's action to the invitee's registration. Carrying it as a `related_context_id` rather than an identity keeps the funnel keyed on records. The exception is stated here rather than discovered in build.

### 5.4 Attempt, outcome and duration properties

| Property | Scope | Values | Status |
|---|---|---|---|
| `attempt_index` | `Journey Step Completed`, `Journey Step Failed` | Integer, 1 = first. **Increments only on a submission that reaches a vendor or a state machine.** Not incremented by a timeout | wins the six-way collision |
| `attempt_no` | `Field Errored` **only** | Integer — tries at a single **field**, where nothing reached a vendor | survives, boundary now written down |
| `retakes` | `Media Captured` **only** | Integer — recaptures before acceptance. **A recapture is not a submission** | survives |
| `attempts_remaining` | `Journey Step Failed` | Integer | kept |
| `cap_reached` | `Journey Step Failed` | Boolean | kept (a decision the server publishes, not a derivation the client makes) |
| `cap_type` 🔶 | `Attempt Cap Reached` | **10:** `pan` · **`bank_manual_entry`** · `selfie_retry` · `esign_otp` · `fund_collect` · `otp_resend` **(new)** · `otp_hourly` **(new)** · `otp_daily` **(new)** · `login_attempts` **(new)** · `order_replace` **(new)** | **defect fixed** |
| `distinct_values_tried` | `Attempt Cap Reached` | Integer, deduplicated on value. PAN cap = **5 distinct values reaching the vendor** | kept |
| `total_submissions` | `Attempt Cap Reached` | Integer — raw submits including unchanged resubmits. The gap against `distinct_values_tried` is *confusion, not attempts* | kept |
| `support_route_shown` | `Attempt Cap Reached` | Boolean | kept |
| `outcome` | `Vendor Call Completed`, `Login Completed`, `Account Detail Changed` | `success` · **`failure`** · `timeout` · `no_record` (vendor); `success` · `failure` (login); `success` · `failure` · `pending` (detail change) | **spelling settled** |
| `duration_sec` | `Journey Step Completed` | Integer seconds, server clock, step-entry to step-exit | kept |
| `active_duration_sec` | `KYC Submitted` | Integer seconds of **active** time, not wall-clock | renamed from `total_duration_min` |
| `time_on_step_sec` | `Journey Step Abandoned` | Integer seconds, step-entry to sweep | kept |
| `total_journey_hours` | `Permitted To Trade` | Hours, wall-clock, `registered_at` → `ptt_at` | kept |
| `seconds_to_cutoff` | `Order State Changed`, `Action Blocked` | **Signed** integer; negative means past the cut-off | new |

**`cap_type` — confirmed live defect, fixed.** `bank_manual` appears as a cap value at lines 661, 1070 and 1610; `bank_manual_entry` at lines 1786 and 2025. Two spellings of one value already in circulation, identical in kind to the `failure`/`failed` collision the reconciliation pass *did* catch, and it missed this one. **`bank_manual_entry` wins** — it is the more specific string and it encodes the rule that the ceiling is on manual **entry** (a UPI mismatch does not count against it).

**`outcome` — spelling settled.** The source flags it and refuses to decide: *"Same fact, one letter apart, and no query will catch both. One spelling has to win before anything is emitted."* **`failure` wins** — it is on two of the three events, and on `Vendor Call Completed`, which will be the highest-volume of the three. Every module inherits one spelling.

**The six-way attempt collision, settled with a stated boundary.** `attempt_index` · `attempt_no` · `attempts` · `retry_count` · `pin_attempts` · `retakes` were *"the same fact under three spellings"* — actually six. Three survive with a written rule: **`attempt_index` counts submissions that reached a vendor; `attempt_no` counts tries at one field that reached nothing; `retakes` counts recaptures of an artefact.** `attempts`, `retry_count` and `pin_attempts` become `attempt_index` scoped by `step_name`.

### 5.5 Interaction properties (frozen generic layer)

| Property | Scope | Complete values | Status |
|---|---|---|---|
| `entry_direction` | `Screen Viewed` | 7, closed: `forward` · `back` · `edit` · `retry` · `resume` · `auto_after_fetch` · `fallback` | kept |
| `nav_source_element` · `referrer_screen` · `seconds_since_prev_screen` | `Screen Viewed` | `element_id` values / `screen_name` values / integer | kept |
| `element_id` | `Element Clicked`, `Action Blocked` | Open registry, engineering-owned and frozen. 39 KYC values + `funds_chip` · `funds_add`. **Zero profile values registered** — OD-10 | kept |
| `element_label` | `Element Clicked` | Free text, Content-owned. **Template only.** Never in a filter | kept |
| `element_type` | `Element Clicked` | **10, closed:** `button` · `link` · `icon` · `toggle` · `chip` · `card` · `tab` · `list_item` · `select` · `stepper` **(new — steppers are named with no value for them)** | extended |
| `action_type` | `Element Clicked` | 6, closed: `primary` · `secondary` · `skip` · `edit` · `help` · `close` | kept |
| `item_group` | `Element Clicked` | Open, **wrapper-validated**: `marital` · `occupation` · `income` · `experience` · `declarations` · `nominee_toggle` · `running_account` · `relationship` · `product_conversion` · `answer_helpfulness` **(new)** · `search_result` **(new)** · `closure_reason` **(new)** · `ipo_issue` · `lots` · `bid_price` | extended + validated |
| `item_value` | `Element Clicked` | Open. Where it carries an `answer_id`, `document_id` or `instrument_id`, the value **SHALL be wrapper-validated against that registry** | tightened |
| `selected` · `is_disabled` · `opens_external` | `Element Clicked` | Booleans. `is_disabled: true` is *intent without ability* | kept |
| `input_method` | `Element Clicked` | 3, closed: `typed` · `sms_autofill` · `paste` | kept |
| `errors_shown` · `error_codes[]` · `edits_made` · `seconds_on_screen` · `seconds_since_overlay_open` | `Element Clicked` | Integer / array of `outcome_code` / integers | kept |
| `overlay_id` | `Overlay Opened`, `Overlay Dismissed` | Open registry. 12 KYC values + `funds_source` + `contact_change_charge` | kept |
| `surface_type` | `Overlay Opened`, `Overlay Dismissed` | **9, closed:** `center_modal` · `bottom_sheet` · `inline_drawer` · `full_screen` · `popover` · `tooltip` · `toast` · `menu` **(new — already emitted against a closed 8)** · `system_dialog` **[RESERVED, never emitted]** | extended |
| `is_dismissible` | `Overlay Opened` | Boolean. Sets what the **absence** of a follow-up means: after `false` a gap is a tracking bug, after `true` it is a walk-away | kept |
| `trigger` | `Overlay Opened` **only** | **Now enumerated, 5, closed:** `cta` · `auto_after_fetch` · `auto_on_enter` · `cap_reached` · `validation_failure` | enumerated |
| `dismiss_method` | `Overlay Dismissed` | 5, closed: `backdrop` · `close_button` · `cancel` · `system_back` · `timeout`. `backdrop` = walk-away, `cancel` = decision | kept |
| `seconds_open` | `Overlay Dismissed` | Integer | kept |
| `field_id` · `error_code` · `attempt_no` | `Field Errored` | Open registry (OD-10) / the `outcome_code` registry / integer | kept |
| `capture_type` | `Media Captured` | 3, closed: `selfie` · `signature` · `document` | kept |
| `capture_method` | `Media Captured` | 3, closed: `camera` · `draw` · `upload` | **renamed from `method`** |
| `file_size_kb` · `file_type` · `is_password_protected` | `Media Captured`; **`file_type` also on `Document Retrieved`** | Integer / string / boolean | kept; **`file_type` scope widened** |

**The headline `method` collision is settled** by the source's own proposed fix, never applied: renaming the capture vocabulary to `capture_method`. `upload` and `draw` no longer mean two things across two events, and `method` now belongs to exactly one event. Funds already dodged a third instance with `funding_method`; orders follows the same precedent with `order_type`.

### 5.6 Method, segment and instrument properties

| Property | Scope | Complete values | Status |
|---|---|---|---|
| `method` | `Journey Step Completed` **only** | **21, closed:** `confirm_prefill` · `manual_entry` · `fetched_confirm` · `upi_app` · `manual_penny_drop` · `cheque_ocr` · `prefetched_confirm` · `digilocker` · `uploaded` · `passive_liveness` · `account_aggregator` · `upload_bank_stmt` · `upload_holdings` · `upload_salary_slip` · `upload_itr` · `upload_form16` · `descoped` · `nominated` · `opted_out` · `draw` · `upload` | kept, collision resolved |
| `segments_selected` | `Journey Step Completed` (segments), `Agreement Generated`, profile | 4-value **scalar**, closed: `cash` · `cash_fno` · `cash_commodity` · `cash_fno_commodity` | unchanged |
| `segments_on_aof` | `Agreement Generated`, profile | Same scalar | unchanged |
| `segments_active` | `Permitted To Trade` and every later activation, profile | Same scalar | unchanged |
| `segments_dropped` | `Agreement Generated`, `KYC Submitted` | **Array of `segment`**: `["fno"]` · `["commodity"]` · `["fno","commodity"]` | **vocabulary settled** |
| `drop_reason` | `Agreement Generated` | 3, closed: `descoped` · `proof_rejected` · `proof_pending` | kept |
| `segment` | product-wide | **3, closed:** `equity` · `fno` · `commodity` | **new** |
| `instrument_id` | `Order State Changed`, `Account Detail Changed` (alert/watchlist), `Request Stage Changed` (pledge, CA) | Free-form engineering-owned id, e.g. `NSE:RELIANCE-EQ` | new |
| `exchange` | orders, portfolio, markets | 3, closed: `nse` · `bse` · `mcx` | new |
| `product_type` | orders, portfolio | 3, closed: `intraday` · `delivery` · `mtf` | new |
| `order_type` · `validity` · `quantity` · `filled_quantity` · `is_amo` · `order_state` | `Order State Changed` | `market` · `limit` · `sl` · `sl_m` / `day` · `ioc` · `gtt` / integers / boolean / **8, closed:** `pending` · `open` · `partially_filled` · `executed` · `rejected` · `cancelled` · `expired` · `modified` | new |

**Instrument identity — the ruling, and its one knowing cost.** Nine modules need to cut by instrument and `instrument_id` appears **zero times** in the source. The rule: **where the instrument IS the record the event concerns** (a quote view, a chart, a watchlist row, a research idea), it rides `context_type: instrument` + `context_id` — which needs no new property and works on the **frozen** generic layer. **Where some other record is the subject** (an order, an alert, a corporate action, a pledge), it rides `instrument_id` as an event property. This is the one place one fact has two homes, and it is forced: the generic layer is frozen, and *"a property added here is added everywhere and can never be reclaimed."* The cost is that *"how many people looked at Reliance"* is a union of two queries — stated, not hidden.

**Segments — unchanged end to end**, the one part of the source both documents agree on. Three moments, never merged, never recomputed from one another. `segments_active ⊆ segments_on_aof ⊆ segments_selected`, evaluated server-side against the underlying segment records and **never** against these scalars; a violation is a defect, not a data point. The AOF invariant holds: `segments_on_aof` ⊆ segments whose income proof **cleared**. The **display label** renders to customers, never the identifier — `cash` is register vocabulary; the screen says Equity.

`segments_dropped`'s vocabulary mismatch is settled: it is an **array of `segment`**, which is now a defined product-wide enum, so the `["fno","commodity"]` values that appeared in no enum table are lawful. `descoped_segments` is removed as a second name for it.

### 5.7 Reveal, consent, message, document and query properties

| Property | Scope | Complete values | Status |
|---|---|---|---|
| `reveal_group` | `Sensitive Value Revealed` | **14:** `pan` · `dob` · `ckyc` · `boid` · `bank_account` · `mobile` · `email` · `nominee_contact` · `nominee_id` · `holdings_value` **(new)** · `pnl` **(new)** · `ledger_balance` **(new)** · `available_margin` **(new)** · `withdrawable` **(new)**. ~~`funds_balance`~~ *(superseded — see below)* | **renamed from `field_group`** |
| `tier` | `Sensitive Value Revealed` | **3:** `A` (regulated identifier, PIN re-auth) · `B` (contact and third-party, single tap) · **`F`** (financial value, not a regulated identifier — concealed by default, no re-auth) | **`F`, not `C`** |
| `reauth_outcome` | `Sensitive Value Revealed` | 4: `passed` · `failed` · `abandoned` · `not_required` | kept |
| `field_group` | `Account Detail Changed` **only** | **9:** `bank` · `nominee` · `address` · `contact` · `security` · `preferences` · `lifecycle` *(renamed from `account_state`)* · `watchlist` **(new)** · `alert` **(new)** | split from reveal grain |
| `action` | `Account Detail Changed` | **8:** `add` · `edit` · `remove` · `freeze` · `unfreeze` · `close` · `enroll` **(new)** · `revoke` **(new)** | extended |
| `freeze_type` | `Account Detail Changed` (`field_group: lifecycle`) | 4, closed: `demat_debit` · `trading` · `voluntary_client` · `regulatory`. **A customer-initiated Profile freeze emits `voluntary_client`** | **ruling overturned** |
| `verification_method` | `Account Detail Changed` | **Now enumerated, 5:** `otp` · `esign` · `ops_review` · `pin` · `none` **(new)** — a change that required no verification step emits `none`, never null | enumerated |
| `sessions_ended` | `Account Detail Changed` (freeze) | Integer | kept — declared exception to R6 |
| `artefact_code` | `Consent Captured`, `Message Dispatched` | **11, closed:** `C-PROC` · `C-KRA` · `C-AADHAAR` · `C-MKTG` · `U-TOU` · `U-PRIV` · `U-TAR` · `U-REF` · `U-DISC` · `U-COOKIE` · `C-PANBANK` **(new)** | new |
| `artefact_version` | `Consent Captured`, `Agreement Generated` | Free-form template version | new — absorbs `aof_version` + `declaration_version` |
| `acceptance_mode` | `Consent Captured` | 5, closed: `esign` · `acknowledge` · `tick` · `opt` · `by_proceeding` | new |
| `granted` · `is_withdrawal` | `Consent Captured` | Booleans | new |
| `message_type` | `Message Dispatched`, `Notification Deep Link Opened` | Open registry, **seeded with the 43 comms-trigger names lowercased** | new |
| `dispatch_outcome` | `Message Dispatched` | **7, closed:** `sent` · `suppressed_stale` · `suppressed_consent` · `suppressed_cap` · `suppressed_quiet_hours` · `suppressed_unverified_channel` · `failed` | new |
| `template_id` · `notification_id` | `Message Dispatched`, `Notification Deep Link Opened` | Free-form | new |
| `touch_index` | `Message Dispatched` | Integer, 1 = **first touch on this `context_id`**. **Increments on `sent` only** | new — **scope corrected** |
| `channel` | `Message Dispatched`, `Notification Deep Link Opened` | **5, closed:** `whatsapp` · `email` · `in_app` · `push` · `sms` **(new)** | new |
| `originating_stage_name` · `cancelling_stage_name` · `seconds_since_originating` | `Message Dispatched` | `stage_name` values / integer | new |
| `report_type` | `Document Retrieved` | **18, closed:** `contract_note` · `statement_of_accounts` · `daily_margin` · `demat_txn` · `holding_statement` · `annual_global` · `tax_pnl` · `trade_book` · `ledger` · `brokerage_charges` · `dividends_ca` · `stt_certificate` · `cmr` **(new)** · `aof` **(new)** · `nomination_form` **(new)** · `ddpi_form` **(new)** · `closure_form` **(new)** · `capital_gains` **(new)** | new |
| `period_preset` | `Document Retrieved` | **10:** `this_fy` · `last_fy` · `this_quarter` · `last_30d` · `custom` · `adv_tax_q1` · `adv_tax_q2` · `adv_tax_q3` · `adv_tax_q4` · `adv_tax_final` | new |
| `delivery_method` | `Document Retrieved` | 3, closed: `view` · `download` · `email` | new |
| `row_count` · `is_statutory` | `Document Retrieved` | Integer (0 = the empty-result signal, which is **not** a validation failure) / boolean | new |
| `query_scope` 🔶 | `Query Answered` | `support`: `assistant` · `help_centre` · `account_lookup`. `markets`: `instrument_search`. `research`: `screener`. `funds`: `transaction_search` **(new)** | new |
| `answer_id` | `Query Answered`, `Element Clicked` (`item_group: answer_helpfulness`, wrapper-validated) | **158 stable permalinks** `HC-<TOPIC>-<NN>` across twelve topics | new |
| `resolution` | `Query Answered` | **6, closed:** `answered` · `candidates` · `refused_advice` · `refused_write` · `no_match` · `handed_off` | new |
| `confidence_ratio` · `candidates_shown` · `result_count` | `Query Answered` | Number (the confident-answer threshold is 1.45× the runner-up) / integers | new |
| `query_language` | `Query Answered` | 2, closed: `en` · `hi` | new |
| `lookup_type` | `Query Answered` | 7, closed: `margin` · `order_status` · `payin_payout` · `squareoff` · `charges` · `pledge` · `kyc_stage` | new |
| `visitor_state` | `Query Answered` | 5, closed: `logged_out` · `no_application` · `in_progress` · `submitted` · `active` | new |

**`tier: F`, not `tier: C` — a correctness fix, not a preference.** The source states plainly: *"`C` never appears — a tier-C value is rendered in full and is not a reveal."* Assigning that letter to a **concealed** financial value inverts a published meaning, which is a redefinition-in-place that the additive-only rule forbids. `F` (financial) is a new letter and is the only lawful way to open the model so portfolio can adopt the name already reserved for it (*"holdings and funds later"*).

**`reveal_group` — the two grains split.** The reconciliation pass calls them *"non-overlapping"*; they are the **same subjects at two resolutions** — `bank` vs `bank_account`, `contact` vs `mobile`/`email`, `nominee` vs `nominee_contact`/`nominee_id` — so a filter on one silently misses the other. Two properties, two grains, no ambiguity.

**`funds_balance` is superseded, not reclaimed.** A broking account answers *"how much do I have?"* with **three** numbers — ledger balance, available margin, withdrawable — and a registry carrying two of the three does in the analytics layer the conflation the product layer exists to refuse. Worse, it conflated the wrong pair: `funds_balance` is not a term the funds PRD uses at all, so a filter written against it silently misses whichever figure the author meant. `available_margin` and `withdrawable` are registered and `ledger_balance` already was. The retired string is **claimed**: it SHALL NOT be emitted and SHALL NOT be re-minted against any of the three, because a filter written before this revision would mean a different balance after it.

**`freeze_type` — the Profile ruling is overturned.** The reconciliation ruled a Profile freeze emits `trading` *"since it stops orders"*. That directly contradicts two documents which both state only `voluntary_client` **originates** in profile · freeze and the other three are set by the depository, broker or regulator and never by the customer. The ruling confused the **effect** (orders stop) with the **origin** (the customer asked). **Origin names it.**

**`verification_method: knowledge_factor` is held, and the deferral is recorded here rather than left in a review thread.** THINQ-EVENTS-AUDIT-001 item 17 asks for two additions and only `none` lands. A knowledge factor is a PAN, a date of birth or a security answer, and **which** was asked is the whole fact — the value is inert without a companion `knowledge_factor_type`, which is registered in no §5 subsection. R4 governs that companion: PAN and DOB may travel only as a **shape**, never as a value. Minting the member now would close the enum around a discriminator whose vocabulary nobody has written, so both land in one pass or neither does. Owner: the registrar (OD-1), on the auth PRD's list of factors.

**`answer_id` — the free-form join, tightened.** The helpfulness signal rides `Element Clicked`'s `item_value` because the generic layer is frozen, and a typo in an `HC-` id would silently drop a row from the deflection funnel with no error anywhere. Fixed by making `item_group: answer_helpfulness` a **wrapper-validated** value whose `item_value` is checked against the answer registry. The signal has a destination for the first time.

**`artefact_code` — one addition, two held back.** `C-PANBANK` is registered: the TnC PRD captures it as a distinct consent, and `artefact_code` is scoped to `Consent Captured` **and** `Message Dispatched`, so a consent that gates a communication belongs in this registry. `T-MITC` and `O-DDPI` are named in the same PRD and are **not** registered here — they are held pending confirmation that each is a separate artefact with its own acceptance record rather than a section of one already listed. A closed-enum value can be added at any time and never withdrawn, so the cost of waiting is a week and the cost of guessing is permanent. The same PRD's ledger holds 34 codes against these eleven; that gap is a registrar ruling about which artefacts gate a communication, not an established shortfall.

**`channel` gains `sms`, and it is not an optional fifth.** SMS is the only channel that needs no opt-in, no inbox and no internet, which is why it is the one channel the funds module is *required* to use: the margin-shortfall and dues ladders are the two states carrying a regulatory bypass on preference, quiet hours and frequency capping, and five SMS templates ship for them. Without the value every one of those messages is either unloggable or logged under a false channel, and `dispatch_outcome: suppressed_consent` is silently wrong for a channel that requires no consent. `push` is kept and has **no emitter today** — there is no mobile application — so a `Message Dispatched{channel: push}` row is a defect, not a dispatch.

**`touch_index` counts touches on a record, not on an application.** *"Application"* is KYC-seeded language sitting in a product-wide definition, and five of the six modules that emit `Message Dispatched` have no application to count against — funds applies it to `service_request` records, alerts to an `alert`, corporate_actions to a `corporate_action`. The counter is per `context_id`, which is also the first term of this event's de-duplication key (§3), so any other reading breaks the key as well as the frequency cap.

### 5.8 Auth, session and miscellaneous properties

| Property | Scope | Complete values | Status |
|---|---|---|---|
| `otp_purpose` | `OTP Requested`, `OTP Resolved` | **14, closed:** `register` · `login` · `digilocker` · `esign` · `aa_consent` · `aa_bank` · `contact_change` · `unfreeze` · `recovery_authorise` **(new)** · `recovery_new_address` **(new)** · `pin_change` **(new)** · `session_unlock` **(new)** · `ipo_mandate` **(new)** · `withdrawal_confirm` **(new)** | extended |
| `otp_channel` | `OTP Requested`, `Mobile Verified` | **4, closed:** `sms` · `whatsapp` · `email` · `voice` | enumerated; absorbs `channel_used` |
| `resend_index` | `OTP Requested`, `OTP Resolved`, `Mobile Verified` | Integer, **0 = first** | absorbs `resend_count` |
| `otp_outcome` | `OTP Resolved` | **4, closed:** `entered` · `expired` · `superseded` · `abandoned` | new |
| `seconds_to_entry` | `OTP Resolved` | Integer seconds, dispatch → entry, both server-stamped | new |
| `session_state` | `Session State Changed` | **6, closed:** `created` · `idle_warned` · `locked` · `unlocked` · `logged_out` · `eod_terminated` | new |
| `unlock_factor` | `Session State Changed` | 4, closed: `passkey` · `pin` · `mobile_otp` · `none` | new |
| `seconds_idle` | `Session State Changed` | Integer | new |
| `state_intact` | `Journey Resumed`, `Session State Changed` | Boolean | **scope widened, not duplicated** |
| `mode` | `Login Completed` | `LOG-00` · `LOG-01` · `LOG-02` · `LOG-03` — **`LOG-04` removed** | narrowed |
| `resolved_mode` | `Login Completed` | 5, closed: `passkey` · `security_key` · `pin` · `mobile_otp` · `password` | new |
| `failure_reason` | `Login Completed` | **5, closed, and closed for a privacy reason:** `bad_credential` · `locked_out` · `challenge_failed` · `expired` · `unsupported_device`. **Never which factor failed** | new |
| `second_factor_method` | `Registration Completed` | **Now enumerated, 4:** `google` · `apple` · `email_otp` · `passkey` | enumerated |
| `entry_point` | `Registration Started` | **Now enumerated, 6:** `landing` · `campaign` · `referral` · `deeplink` · `organic_search` · `partner` | enumerated |
| `entry_source` | `KYC Started` | 4, closed: `direct` · `whatsapp` · `email` · `push` | kept |
| `referral_code` | `Registration Started` | Free-form, the sharer's code | new |
| `utm_source` · `utm_campaign` · `campaign_id` · `resume_source` · `days_since_dropoff` | `Registration Started`, `Journey Resumed` | Open / open / open / open / integer | kept |
| `service_id` | `Vendor Call Completed`, `Vendor Failure Detected`, `Service Restored` | **Closed registry, 24:** `pan_nsdl` · `pan_mobile_fetch` · `bank_fetch` · `penny_drop` · `name_match` · `liveness_face_match` · `digilocker` · `aadhaar_esign` · `pmla` · `kra` · `ckyc` · `broker_bo` · `exchange` · `psp_collect` · `cdsl` **(new)** · `sponsor_bank` **(new)** · `registrar` **(new)** · `exchange_gateway` **(new)** · `rms` **(new)** · `depository_pledge` **(new)** · `market_feed` **(new)** · `psp_payout` **(new)** · `margin_front_office` **(new)** · `margin_back_office` **(new)** | **3 conflicts settled** |
| `vendor` · `vendor_ms` · `latency_ms` · `failover_to` · `vendor_attempt` | `Vendor Call Completed`, spine | Open (`digio` · `signzy` · `setu` · `nsdl` · …) / integers | `vendor` **wins over `provider`** |
| `is_outage` · `vendors_exhausted` · `incident_id` · `settle_period_sec` · `audience_size` · `outage_duration_sec` | `Vendor Failure Detected`, `Service Restored` | Booleans / integers / `context_id` of the `service_incident` | `incident_id` new |
| `decision` | `Ops Decision Recorded` | **7:** `query` · `hold` · `approve` · `reject` · `clear` · `escalate` **(new)** · `send_back` **(new)** | extended |
| `escalated_to_level` | `Ops Decision Recorded` | 4: `1` · `2` · `3` · `4` | new |
| `queue` · `sla_hours` · `hold_type` | `Ops Decision Recorded` | Open (**no registry — OD-10**) / integer / **now enumerated, 4:** `edd` · `pep` · `sanctions_review` · `fraud_review` | partly enumerated |
| `dropoff_class` | `Journey Step Abandoned`, profile | 5, closed: `friction` · `deferral` · `hard_blocker` · `technical` · `never_intended` | kept — **now enumerated in the authority** |
| `dropoff_context` | `Journey Step Abandoned` | 2, closed: `in_app` · `external_app` | kept |
| `permission_state` | `Journey Step Failed`, profile | 2, closed: `denied` · `blocked` | **now declared on an event** |
| `is_terminal` | `Journey Step Failed` **only** | Boolean — *the applicant cannot proceed on this path at all*. **One meaning only** | kept, meaning fixed |
| `is_recoverable` | `Application Rejected` | Boolean | new |
| `agreement_type` | `Agreement Generated` | **6, closed:** `aof` · `segment_addition` · `ddpi_authorisation` · `closure_form` · `contact_change_form` **(new)** · `nomination_form` **(new)** | new |
| `alert_type` | `Account Detail Changed` (`field_group: alert`) | 4, closed: `price_above` · `price_below` · `pct_change` · `volume` | new |
| `ca_type` | `Request Stage Changed` (corporate actions) | 10, closed: `dividend` · `bonus` · `split` · `rights` · `buyback` · `merger` · `demerger` · `delisting` · `ofs` · `capital_reduction` | new |
| `application_category` | `Request Stage Changed` (ipo) | 4, closed: `retail` · `hni` · `employee` · `shareholder` | new |
| `recurrence` | `Request Stage Changed` (`sip_*`, `rac_*`) | 5, closed: `none` · `daily` · `weekly` · `monthly` · `quarterly` | new |
| `fallback_type` · `fallback_trigger` | `Manual Fallback Entered` | 3, closed: `cheque` · `address_upload` · `vipv_manual` / 3, closed: `vendor_down` · `attempts_exhausted` · `user_choice` | `trigger` renamed |
| `journey_variant` | `Permitted To Trade`, profile | 2, closed: `new_kyc` · `kra_verified` | kept — backend-set at Step 14 |
| `manual_touch_count` | `Application Approved`, `Permitted To Trade` | Integer | **wins over `manual_touches`** |
| Per-step KYC properties | `Journey Step Completed` | `marital_status` · `occupation` · `income_band` · `trading_experience` · `fatca_declared` · `pep_declared` · `running_account_days` (`30` · `90`) · `questions_reopened` · `upi_app` · `father_name_edited` · `confirm_latency_sec` · `liveness_pass` · `face_match_band` · `equity_deselect_attempts` · `threshold_required` · `nominee_count` · `has_minor_nominee` · `address_same_as_applicant` · `clears` · `rejections_before_pass` · `accounts_offered` | kept; **three dictionary misfilings corrected** |

**`service_id` — the registry that never existed, with three internal conflicts settled by ruling.** It is *"the logical check, not the vendor, so failover does not change it."* (1) The PAN lookup is **`pan_nsdl`**, not `pan_verify` — which also ends the collision with the `element_id` of that name. (2) The Setu pre-fetch is **`pan_mobile_fetch`**, not `mobile_to_bank` — the latter describes a route, and a `service_id` is a check. (3) The face check is **`liveness_face_match`**, not `face_match` — the emitted value wins over the cited one. (4) The backend chain is **`broker_bo` · `exchange`**, the shorter pair already used in the funnels. (5) `upi_collect` becomes **`psp_collect`** because it was simultaneously a `service_id` and a `funding_method` value; the customer's chosen method stays `upi_collect`, the call is `psp_collect`. Service-specific restore copy is required **per `service_id`**, so an unregistered id means an outage with no recovery template. **`psp_payout` is the addition that matters.** The registry held `psp_collect` and no outbound payment service of any kind, and FMS's end-of-day *rail is down* result — `stage_name: wdl_rail_queued`, the one withdrawal outcome that leaves the request open and cancellable — is the only funds state whose resolution **is** a `Service Restored`. Without the id there is no incident to close and no template to send. `margin_front_office` and `margin_back_office` are registered separately from `rms` because the margin figure has two sources — front office in market hours, back office outside — and `blocked_reason: stale_margin_data` is a refusal about **which** source answered and how long ago, which one id cannot carry.

**Three misfiled per-step properties corrected:** `accounts_offered` belongs to `income_proof`, not bank; `confirm_latency_sec` belongs to `address`, not bank; `rejections_before_pass` belongs to `signature`, not selfie.

### 5.9 Profile properties — 36, each with a writer and a recompute cadence

**Structural change to this subsection.** It was a bare comma-separated list — the one §5 subsection with no Values, no Type, no Scope and no Nullability column — and it is now a table like every other. Three columns are load-bearing and were absent from the corpus entirely once `THINQ_KYC_ONBOARDING_PRD.md §22.3` was superseded: **Values**, **Written by** and **Recompute cadence**. That supersession destroyed the specification layer rather than replacing it, which is why the register is rebuilt here from **Appendix 22-A** — the source-of-truth column §22.3 left behind for exactly this migration — and from §5's own Scope cells, rather than re-derived by each consuming team.

**The register is 36, not 30.** The FMS registration registers six profile properties — `funds_state`, `dues_state`, `shortfall_state`, `first_deposit_at`, `last_deposit_method`, `deposits_90d` — and its own §12 schedules them into this section. They are carried here, so the closed register and the module that registers into it agree for the first time.

**Three grades of writer, and the grade is stated on every row.** A cell with no marker is **stated** — the writer is named in this document, in §5's Scope cell for that property, or in the FMS registration. *(inferred)* means the writer is unambiguous but is written down nowhere; the registrar (OD-1) ratifies each before it is authored into an audience. **OPEN** means no candidate exists anywhere in the corpus and the row names the owner who must supply one. The split is **9 stated · 19 inferred · 8 open**. Two of the eight — `funds_state` and `deposits_90d` — are FMS's own, so the comms audiences that qualify on them stay unauthorable until someone names a writer.

| Property | Values | Type | Written by | Recompute cadence |
|---|---|---|---|---|
| `kyc_stage_name` | The 38 kyc `stage_name` slugs (§2 row 9) | string, module-scoped enum | Envelope `stage_name` on the latest `module: kyc` event *(inferred)* | Real time, last write wins |
| `kyc_screen_name` *(renamed from `kyc_screen_id` — it was named for a registry called `screen_name`)* | Any `kyc_*` value of the `screen_name` registry (§2 row 7) | string, open registry | Envelope `screen_name` on the latest `module: kyc` event *(inferred)* | Real time, last write wins |
| `kyc_step_name` | The 11 kyc `step_name` values (§2 row 8) | string, module-scoped enum | Envelope `step_name` on the latest `module: kyc` event *(inferred)* | Real time, last write wins |
| `kyc_status` | **Unpublished.** Drives board reporting and nothing else | — | **OPEN — KYC PRD owner + Analytics** | **OPEN** |
| `last_outcome_code` | Any kyc `outcome_code` (§5.2, 53) | string, module-scoped enum | `Journey Step Failed{outcome_code}` *(inferred)* | Real time, last write wins |
| `last_error_class` | 4, closed: `validation` · `friction` · `hard_blocker` · `technical` | string, enum | `Journey Step Failed{error_class}` *(inferred)* | Real time, last write wins. The per-code map is unratified — OD-7 |
| `dropoff_class` | 5, closed: `friction` · `deferral` · `hard_blocker` · `technical` · `never_intended` (§5.8) | string, enum | `Journey Step Abandoned{dropoff_class}` — §5.8 Scope | On abandonment. **Fires nowhere until OD-5 sets the per-step thresholds**; §6's open-step rule gives the audience meanwhile |
| `dropoff_at` | — | DATE, never a string | **OPEN — Product (OD-5).** The sweep that would stamp it has no trigger | **OPEN** |
| `permission_state` | 2, closed: `denied` · `blocked` (§5.8) | string, enum | `Journey Step Failed{permission_state}` on a `PERM_*` code — §5.8 Scope | On a `PERM_*` failure. The detection rule that separates denied from capture-failed is unbuilt — OD-8 |
| `holdout_group` | **Unpublished.** The permanent 5–10% holdout | — | **OPEN — Marketing + Analytics** | **OPEN** |
| `marketing_opt_in` | Boolean | boolean | `Consent Captured{artefact_code: C-MKTG}` → `granted` *(inferred)* | Real time |
| `resume_deeplink` | Free string — the link rendered into every recovery template | string, open | **OPEN — Eng + Content** | **OPEN** |
| `registered_at` | — | DATE | `Registration Completed` *(inferred)* | Once, at the milestone; never rewritten |
| `kyc_started_at` | — | DATE | `KYC Started` *(inferred)* | Once per record — see the re-application note below |
| `kyc_submitted_at` | — | DATE | `KYC Submitted` *(inferred)* | Once per record — see the re-application note below |
| `ptt_at` | — | DATE | `Permitted To Trade` *(inferred)* | Once, at the milestone; never rewritten |
| `segments_selected` | 4-value scalar, closed: `cash` · `cash_fno` · `cash_commodity` · `cash_fno_commodity` (§5.6) | string, closed scalar | `Journey Step Completed{step_name: segments}` — §5.6 Scope | On the segments step |
| `segments_on_aof` | Same scalar | string, closed scalar | `Agreement Generated` — §5.6 Scope | On generation |
| `segments_active` | Same scalar | string, closed scalar | `Permitted To Trade` and every later activation — §5.6 Scope. **Evaluated server-side against the underlying segment records, never against these scalars** | On every activation |
| `journey_variant` | 2, closed: `new_kyc` · `kra_verified` (§5.8) | string, enum | `Permitted To Trade{journey_variant}` — §5.8 Scope. **Backend-set at the KRA outcome, never client-emitted** | Once, at PTT |
| `banks_linked` | Integer count of verified banks | integer | `Request Stage Changed{module: profile, stage_name: bank_verified}` on add; `Account Detail Changed{field_group: bank, action: remove}` on removal *(inferred)* | Real time |
| `banks_pending` | Integer count of banks in the `bank_*` family before `bank_verified` | integer | The `bank_*` stage family — **stated in this section** | Real time |
| `nominee_count` | Integer | integer | `Journey Step Completed{step_name: nominee}`, then `Request Stage Changed{stage_name: nom_* · nomedit_*}` *(inferred)* | Real time |
| **`nominee_outcome`** *(replaces the boolean `nominee_opted_out`)* | 3, closed: `nominated` · `opted_out` · `none` | string, enum | `KYC Submitted{nominee_outcome}` *(inferred)* | Real time |
| `ddpi_active` | Boolean | boolean | `Request Stage Changed{request_type: ddpi_activate}` at its terminal stage *(inferred)* | On the terminal transition |
| `settlement_cycle` | **Unpublished.** `running_account_days` (`30` · `90`, §5.8) is the KYC-side selection and may or may not be the same fact — the registrar rules | — | **OPEN — registrar (OD-1)** | **OPEN** |
| `open_request_types` | A **set** over `request_type` (§5.3, 23 values), never a boolean. §5.1 defines the membership — *requests with no terminal stage* — and notes it reads `reports` as permanently open until `rep_*` has a terminal of record | set of enum | **OPEN — registrar (OD-1).** §5.1 defines the membership and no event or job writes it | **OPEN** |
| `last_reveal_at` | — | DATE | `Sensitive Value Revealed` *(inferred)* | Real time, last write wins |
| `docs_downloaded_30d` | Integer count | integer | `Document Retrieved` — **stated in this section** | Rolling 30-day window; the decay is the profile store's, not an event's |
| **`consent_state`** *(new)* | Map of `artefact_code` → `granted` over the 11 codes of §5.7 | map | `Consent Captured` — **stated in this section** | Real time. It SHALL be readable **inside the 60-second window** the frequency cap demands |
| `funds_state` | 5, closed: `blocked` · `empty` · `funded` · `debit` · `shortfall` — the `funds_home_*` `screen_name` set with the prefix stripped | string, enum | **OPEN — FMS registration owner.** Its §5.11 says only *"stamped server-side"* and names no event and no job | **OPEN** |
| `dues_state` | 3, closed: `none` · `outstanding` · `cleared_30d` | string, enum | `Request Stage Changed{stage_name: due_raised}` raises it, `{stage_name: due_cleared}` clears it *(inferred)* | Real time on both halves; the `cleared_30d` decay is a profile-store window |
| `shortfall_state` | 4, closed: `none` · `open` · `cleared` · `squared_off` | string, enum | `Request Stage Changed{stage_name: shf_raised}` raises it, `{shf_cleared}` / `{shf_squared_off}` resolve it *(inferred)* | Real time |
| `first_deposit_at` | — | DATE, never a string | `Request Stage Changed{stage_name: fund_credited}`, first occurrence only *(inferred)* | Once; never rewritten |
| `last_deposit_method` | A `funding_method` value (§5.3, 8 closed) | string, enum | `Request Stage Changed{stage_name: fund_credited}` → `funding_method` *(inferred)* | Real time, last write wins |
| `deposits_90d` | Integer **count**, never an amount | integer | **OPEN — FMS registration owner.** The increment is named nowhere; the decay is a profile-store window | **OPEN** |

**The eight OPEN rows are the blocker, and they are named so they can be closed.** `kyc_status`, `dropoff_at`, `holdout_group`, `resume_deeplink`, `settlement_cycle`, `open_request_types`, `deposits_90d` and `funds_state` have no writing event, server job or candidate anywhere in the corpus. Each row names an owner because the failure mode of leaving them blank is not that the property is uncomputable — nineteen more are inferable and will be computed — it is that **three teams infer three different writers and three different cadences for one property**, which is precisely what §5's per-property Scope column prevents everywhere else in this document.

**Two reading rules carried over from the migration.** This register is rebuilt from `THINQ_KYC_ONBOARDING_PRD.md` Appendix 22-A, the corpus's only record of which section or event was the source of truth for each property. That appendix cites §22.1b, §22.1d, §22.1f and §22.1g — all retired by this document's `supersedes` clause — and they translate as: **§22.1b → §2 rows 10 and 11**, **§22.1d → §5.8 `permission_state`**, **§22.1f → §5.6's display-label rule**, **§22.1g → §2 row 9**. And `kyc_screen_id` is registered here as `kyc_screen_name`. With that translation applied the appendix is fully absorbed and can be deleted.

**Two rows are also event properties, and that is the scope widening §4 forced.** `nominee_outcome` and `last_outcome_code` were registered on this layer alone while §4 puts both on an event — `nominee_outcome` on `KYC Submitted`, `last_outcome_code` on `Journey Step Abandoned`. Both are registered on **both** scopes, with one vocabulary each: `nominee_outcome` is the 3-value enum above wherever it travels, and `last_outcome_code` carries a kyc `outcome_code` (§5.2) wherever it travels. `file_type` is the third row of the same defect and is widened in §5.5. None of the three is a new property and none widens the registry — a property registered under the wrong scope was already counted.

**`account_state` and `engagement_state` are not missing from this table.** Appendix 22-A carries both. They are **envelope** properties, stamped on every event by the wrapper (§2 rows 10 and 11), and a profile mirror of an envelope property is exactly the second home R1's principle exists to eliminate. They are refused here deliberately, not overlooked.

`kyc_stage_name` keeps the 38 readable slugs **exactly where §2 row 9's argument applies** — campaigns qualify on the profile, and a looked-up value is a mis-keyed value. `consent_state` is a **map** of `artefact_code → granted`, recomputed from `Consent Captured`, and it is what the refuse-to-send gate reads **at dispatch time** — an event stream cannot be queried inside the 60-second real-time window the frequency cap demands. `nominee_opted_out` as a boolean reintroduced exactly what is forbidden: nomination is `nominated` or `opted_out`, SEBI recognises only those two, and a deferral is neither. Two properties become computable for the first time: `banks_pending` (via the new `bank_*` family) and `docs_downloaded_30d` (via `Document Retrieved`).

**The re-application note.** `kyc_started_at`, `kyc_submitted_at` and `ptt_at` are stamped once per **record**, and the profile is keyed on the **identity** — a rejection followed by a re-application is two records and one identity (§2 row 3). Whether the second record overwrites the first date or the profile carries the earliest is a cadence question this document cannot answer for the store; the registrar rules it, and every "days since" audience depends on the answer.

`marketing_opt_in` SHALL NOT be implemented as a channel-level subscription flag — that suppresses the Utility activation and rejection notices that must reach a customer who declined marketing. It is an audience filter on marketing campaigns only.

---

## 6. Module map

For each of the eighteen modules: the funnels it needs, the events those funnels use, and what it registers. **The proof is the last column: every module registers values only.** Only orders, reports, support, auth and the comms path consume any of the seven new names, and each of those was justified in §4.

| # | Module | Funnels needed | Events used | New names required | Registers (values only) |
|---|---|---|---|---|---|
| 1 | `marketing` | Landing → CTA → registration, cut by campaign · pricing → open-account intent · campaign deep link → resume · cookie consent | `Screen Viewed` · `Element Clicked` · `Consent Captured` · `Registration Started` · `Journey Resumed` · `Notification Deep Link Opened` · `Message Dispatched` | **0** | `artefact_code`: `U-COOKIE` · `U-TOU` · `U-PRIV` · `U-TAR`; `entry_point` values |
| 2 | `auth` | Registration · prospect access → resume · trading login · recovery branches A/B/C/E · session lifecycle | `Registration Started` · `Mobile Verified` · `Registration Completed` · `Login Completed` · **`Session State Changed`** · `OTP Requested` · **`OTP Resolved`** · `Request Stage Changed` · `Account Detail Changed` · `Attempt Cap Reached` | **2** (both justified in §4) | `step_name`: `register` · `recover`; `stage_name`: `rec_*`; `request_type`: `recovery_session`; `outcome_code`: `AUTH_*`; `cap_type`: `otp_resend` · `otp_hourly` · `otp_daily` · `login_attempts`; `otp_purpose`: 4 new; `field_group`: `security`; `action`: `enroll` · `revoke` |
| 3 | `kyc` | The reference implementation — 12 step funnels, 0→1 registration, 12→PTT | The full spine + milestones + cross-cutting + `Consent Captured` + `Message Dispatched` | **0** | Unchanged |
| 4 | `segment_activation` | Entry → segment → income proof → e-Sign → active · the de-scoped cohort's return · proof-rejection loop · approved→enabled gap · deactivation | `Journey Step Completed/Failed/Abandoned` · `Request Stage Changed` · **`Agreement Generated`** · `Media Captured` · `Action Blocked` · `Vendor Call Completed` | **0** | `step_name`: `segment_select` · `income_proof` · `form_sign`; `stage_name`: `seg_*` (13); `request_type`: `segment_activate` · `segment_deactivate`; `blocked_reason`: `open_positions_exist`; `agreement_type`: `segment_addition`; `segment` |
| 5 | `profile` | Contact change · nominee · DDPI · bank add · closure · freeze · reveals | `Request Stage Changed` · `Action Blocked` · `Sensitive Value Revealed` · `Account Detail Changed` · `OTP Requested/Resolved` · `Vendor Call Completed` · `Document Retrieved` · generic six | **0** | `stage_name`: `chg_*` · `nom_*` · `nomedit_*` · `ddpi_*` · `clo_*` · `bank_*` · `unf_*`; `request_type` (8); `blocked_reason` (3); `reveal_group`; `service_id`: `cdsl` |
| 6 | `funds` | Add money · **withdraw** · running-account settlement · margin shortfall · withdrawable-vs-total *(deliberately unanswerable — R5)* | `Request Stage Changed` · `Action Blocked` · `Attempt Cap Reached` · `Field Errored` · `Vendor Call Completed` · `Message Dispatched` · `Notification Deep Link Opened` · **`Query Answered`** | **0** | `stage_name`: `fund_*` · `wdl_*` · `rac_*`; `request_type`: 5; `blocked_reason`: 8; `outcome_code`: `FUNDS_*` · `WDL_*`; `funding_method`: `rtgs` · `cheque`; `leg`: `bank`; `query_scope`: `transaction_search`; `direction` via `request_type` |
| 7 | `markets` | Search → quote → order pad → placed · watchlist → first order · quote → depth → alert · **zero-result search** | `Screen Viewed` · `Element Clicked` · **`Query Answered`** · `Account Detail Changed` (`field_group: watchlist`) · `Order State Changed` | **0 of its own** | `query_scope`: `instrument_search`; `screen_name`; `sub_module`; `exchange`; `service_id`: `market_feed` |
| 8 | `charts` | Chart opened → indicator → drawing → order from chart · **chart state survives the idle lock** · indicator adoption | `Screen Viewed` · `Element Clicked` · **`Session State Changed`** (`state_intact`) | **0 of its own** | `screen_name`; `element_id` |
| 9 | `orders` | Place → accepted → executed/rejected · rejection recovery · modify/cancel · **intraday→delivery before cut-off** · GTT/AMO | **`Order State Changed`** · `Action Blocked` · `Field Errored` · `Screen Viewed` · `Element Clicked` · `Charged` | **1** (grain class, §4) | `outcome_code`: `ORD_*` (11); `blocked_reason`: 9; `order_type` · `product_type` · `validity` · `order_state`; `cap_type`: `order_replace`; `service_id`: `exchange_gateway` · `rms`; `charge_category` |
| 10 | `portfolio` | Holdings → instrument → exit · **sellable vs holdings** · pledge/unpledge · reveal on holdings and P&L | `Request Stage Changed` · `Action Blocked` · `Sensitive Value Revealed` · `Screen Viewed` · `Element Clicked` | **0** | `stage_name`: `plg_*` · `unplg_*`; `request_type`: `pledge` · `unpledge`; `blocked_reason`: 3; `reveal_group`: `holdings_value` · `pnl`; **`tier: F`**; `service_id`: `depository_pledge` |
| 11 | `reports` | Report → period → generated → delivered · async requested→ready→failed · empty result → widen window · statutory bundle | **`Document Retrieved`** · `Request Stage Changed` · `Screen Viewed` · `Element Clicked` | **1** (§4) | `report_type` (18); `period_preset` (10); `delivery_method`; `stage_name`: `rep_*`; `request_type`: `report_generation`; `outcome_code`: `REP_*` |
| 12 | `corporate_actions` | Notice sent → opened → acted *(delisting: never-opened is the number)* · quantity changed → holdings → ticket · entitlement → dividend paid · buyback/rights/OFS | `Message Dispatched` · `Notification Deep Link Opened` · `Request Stage Changed` · `Element Clicked` · `Document Retrieved` | **0** | `ca_type` (10); `stage_name`: `ca_*`; `context_type`: `corporate_action`; `instrument_id` |
| 13 | `ipo` | List → issue → bid → mandate → allotted/refunded · **the UPI mandate handover** · cut-off refusal · allotment day | `Request Stage Changed` · `Action Blocked` · `OTP Requested/Resolved` · `Vendor Call Completed` · `Screen Viewed` · `Element Clicked` | **0** | `stage_name`: `ipo_*` (10); `request_type`: `ipo_application`; `blocked_reason`: 4; `outcome_code`: `IPO_*`; `leg`: `registrar` · `sponsor_bank`; `application_category`; `funding_method`: `upi_mandate`; `service_id`: 2 |
| 14 | `alerts` | Alert created → triggered → notification opened → order placed · preference → volume → opt-out · dead-alert cohort | `Account Detail Changed` (`field_group: alert`) · `Message Dispatched` · `Notification Deep Link Opened` · `Element Clicked` | **0** | `alert_type` (4); `field_group`: `alert`; `context_type`: `alert`; `message_type` |
| 15 | `support` | Deflection: help centre → answer → helpful? → ticket or not · assistant: answered/candidates/refused/handed off · account lookup gated by visitor state · ticket lifecycle · escalation L1→L4 → regulator | **`Query Answered`** · `Request Stage Changed` · `Ops Decision Recorded` · `Element Clicked` · `Message Dispatched` | **1** (§4) | `query_scope`: 3; `answer_id` (158); `resolution` (6); `lookup_type` (7); `visitor_state` (5); `stage_name`: `tkt_*`; `request_type`: `support_ticket`; `decision`: `escalate` · `send_back`; `escalated_to_level`; `outcome_code`: `SUPPORT_*` |
| 16 | `referral` | Referral screen → link shared → invitee registers → invitee reaches PTT → reward credited · U-REF acceptance · reward earned → credited | `Request Stage Changed` · `Registration Started` (`referral_code`) · `Consent Captured` · `Element Clicked` | **0** | `stage_name`: `ref_*`; `request_type`: `referral_reward`; `artefact_code`: `U-REF`; `referral_code`; **`related_context_id`** carries the sharer↔invitee join |
| 17 | `research` | Idea viewed → instrument opened → order placed · screener run → results → watchlist · news → alert | `Screen Viewed` · `Element Clicked` · **`Query Answered`** · `Consent Captured` | **0 of its own** | `query_scope`: `screener`; `artefact_code`: `U-DISC`; `context_type`: `instrument` |
| 18 | `mutual_funds` | Discovery → order → units · SIP created → instalment → paused/stopped/failed · redemption → payout | `Request Stage Changed` · `Document Retrieved` · `Screen Viewed` · `Element Clicked` | **0** | `stage_name`: `sip_*` (non-terminating, R7); `request_type`: `sip_mandate`; `recurrence`; `related_context_id` joins each instalment to its mandate |

### The four cross-module reads this unlocks

Every one is a single query today's taxonomy cannot express at any price.

**1. Margin-rejection recovery (orders → funds → orders).** `Order State Changed{order_state: rejected, outcome_code: ORD_INSUFFICIENT_FUNDS}` → `Request Stage Changed{request_type: fund_deposit, related_context_id: <the order>}` → `Order State Changed{order_state: executed}`. Joined on `related_context_id`, **not** `session_id` — the customer leaves for their bank app and returns tomorrow on a deep link, which is a new session and the same records.

**2. IPO refund (ipo → funds).** `Request Stage Changed{stage_name: ipo_not_allotted}` → `Request Stage Changed{request_type: fund_deposit, direction in, initiated_by: system, related_context_id: <the application>}`. `initiated_by: system` is what separates a refund from a customer deposit; without it they are one undifferentiated number.

**3. Outage cohort reclassification (any module → comms).** `Vendor Failure Detected{incident_id: I-441, is_outage: true}` → the affected cohort is `Journey Step Failed{outcome_code: DIGI_VENDOR_DOWN}` in the window → `Message Dispatched{related_context_id}` gives the per-outage messaged set → `Service Restored{incident_id: I-441}` notifies **exactly that set**. A drop during an outage is `technical`, never `friction`, and must never count against the screen's own conversion — that reclassification is now a query rather than a manual cohort operation.

**4. Deflection (support → any module).** `Screen Viewed{screen_name: support_help_centre}` → `Query Answered{query_scope: help_centre, resolution: answered, answer_id: HC-ORD-07}` → **did NOT** `Request Stage Changed{request_type: support_ticket}` = deflected. Broken down by `answer_id`, `confidence_ratio` band and `visitor_state`.

### Two funnel rules that come free

**The open-step population — partial relief on the abandonment blocker, at zero name cost.** `Journey Step Abandoned` cannot fire until per-step idle thresholds are set (OD-5). But the population it would chase is addressable **today**, with no sweep, no threshold and no product decision:

> `did Screen Viewed{screen_name: X}` **AND** `did NOT Journey Step Completed{step_name: Y}` **AND** `did NOT Journey Step Failed{step_name: Y}`

Four dispositions instead of three — *never tried* · *tried and still open* · *tried and failed* · *converted*. "Tried and still open" does not exist in the current taxonomy at all. The same shape applies to every request family, which is why **R7** requires each to register a stage that fires at submission: `wdl_requested`, `tkt_open`, `rep_requested`, `ipo_submitted`, `bank_submitted`. The sweep is still needed to **stamp** `dropoff_class`, which is what the comms engine routes on — but the audience exists before the threshold does.

**Three funnel nodes, never two.** `screen → Journey Step Completed → screen`. The middle node is the whole diagnosis: it separates *never tried* from *tried and failed*. Every funnel is keyed on `context_id`, never identity. Every funnel step is a **filter**, never a distinct event name. Close on `entry_direction = forward` for first-pass conversion; include `back`, `edit` and `retry` only when rework is what is being measured. Mixed client/server funnels get a generous convergence window.

**A note on volume isolation.** `Order State Changed` and `Message Dispatched` are separate names precisely so that any sampling, retention or cost decision forced by order throughput or comms volume lands on those names alone. If order transitions shared a name with the KYC spine or with milestones, sampling orders would corrupt the north-star funnel and the per-touch lift denominator simultaneously. That isolation is a reason to keep names distributed, not a side effect.

---

## 7. What was removed, and what carries the fact now

### Event names

**None.** No name is deleted and none is absorbed. Four are renamed (§8). Three stay deleted or collapsed from earlier rulings and SHALL NOT be reinstated: `KYC Step Deferred` (the spine is three events, not four), `Form Submitted` (derivable from `Element Clicked`), and `Account Request Raised` / `Account Request Withdrawn` / `Account State Changed` (a raise **is** the first transition with `previous_stage_name: null`; a withdrawal **is** a terminal transition; a freeze **is** a mutation).

**`Help Requested` is refused, not registered.** It appears in one PRD row as an event fired at the PAN step and is in no catalogue in either document. A help tap is `Element Clicked{element_id: pan_help, action_type: help}` plus `Overlay Opened{overlay_id: pan_help}` — both of which that same step already emits, and `help` is already in the closed `action_type` enum. **The row is the defect.**

### Properties

| Removed | Reason | What carries the fact now |
|---|---|---|
| `stage_code` (the emitted property, 40 values) | Violates an unqualified `SHALL NOT` (PRD:2974) | Module-scoped `stage_name`, lowercased. **All 40 values survive.** |
| `step_name` as a KYC-only closed enum | No lawful value outside a KYC step | Module-scoped, nullable `step_name` |
| `was_stp` | Derivable from `manual_touch_count = 0` — R1's own worked example, shipped anyway | `manual_touch_count` |
| `esign_used` | Derivable from a `*_esigned` stage on the same `context_id` | The stage stream |
| `reauth_required` | Self-declared: *"True if and only if `tier` is `A`… derivable today"* | `tier` |
| `is_first_deposit` | Derivable from credit history; orphan, no host event | Query over `stage_name: fund_credited` |
| `source_changed` | Derivable from `source_bank_ref` against the default; orphan | `source_bank_ref` |
| `funds_topped_up` | Orphan on no event **and** derivable from a deposit in the same session | The deposit record, via `related_context_id` |
| `hours_open` | Orphan on no event **and** derivable from first/last transition | `seconds_in_request` |
| `time_to_credit_sec` | Derivable from two server timestamps; a grain outlier | `seconds_in_request` — serves every family, not just deposits |
| `request_id` | Duplicates `context_id` | `context_id` + `context_type: service_request` |
| `is_terminal` on the request event | Two meanings; also derivable | Read from the stage (R7). The name survives on `Journey Step Failed` with one meaning |
| `control_id` · `surface` | Second names for `element_id` · `screen_name` | `element_id`, envelope `screen_name` |
| `raised_via` · `initiated_via` | Two properties, one question, split across two events | **`initiated_by`** (+ `system`) |
| `source` (`server` or `client`) | Metadata about the schema, not a fact about the event | Envelope `platform` (`system` vs a client surface) |
| `manual_touches` | *"Two names, one fact"* — flagged in the source, unflagged in the PRD | `manual_touch_count` |
| `channel_used` | Same fact as `otp_channel`, neither enumerated | `otp_channel`, now enumerated |
| `resend_count` | Count vs index of one fact | `resend_index` (0 = first) |
| `attempts` · `retry_count` · `pin_attempts` | Three of the six spellings of "how many tries" | `attempt_index`, scoped by `step_name` |
| `attempt_no` **on the spine** | A frozen `Field Errored` property leaking onto `Journey Step Completed` at Bank and Selfie | `attempt_index` |
| `provider` | Same thing as `vendor` under a second name | `vendor` |
| `all_providers_exhausted` | Duplicates `vendors_exhausted` | `vendors_exhausted` |
| `descoped_segments` | Second name for `segments_dropped` | `segments_dropped` (array of `segment`) |
| `nominee_outcome` as a boolean (`nominee_opted_out`, profile) | Reintroduced exactly what is forbidden: a deferral is neither `nominated` nor `opted_out` | Profile `nominee_outcome` (3 values incl. `none`) |
| `aof_version` · `declaration_version` | Parallel purpose, no shared convention | `artefact_version` |
| `total_duration_min` | Grain and name both invited comparison with wall-clock hours | `active_duration_sec` — the name now states the difference |
| `hours_in_previous` | Grain collision with seconds everywhere else | `seconds_in_previous` |
| `previous_stage` | Held `stage_name` values under an asymmetric name | `previous_stage_name` |
| `method` on `Media Captured` | The headline two-vocabulary collision | `capture_method` |
| `trigger` on `Manual Fallback Entered` | Collided head-on with an unenumerated `trigger` on `Overlay Opened` | `fallback_trigger`; `trigger` now belongs to the overlay schema alone and is enumerated |
| `field_group` on `Sensitive Value Revealed` | Two grains wrongly declared non-overlapping | `reveal_group` |
| `field_group: account_state` (the **value**) | Used the exact string of an envelope property as a value | `field_group: lifecycle` |
| `blocked_reason: pre_activation` | Derivable from `account_state` | `account_state` |
| `blocked_reason: submitted` | Collided with `account_state: kyc_submitted` and every `*_submitted` stage | `request_in_flight` + `request_type` |
| `blocked_reason: contact_change` | Collided with `otp_purpose: contact_change` | `request_in_flight` + `request_type` |
| `request_type: freeze_assisted` | A freeze **is** a mutation, not a request — the ruling above that keeps `Account Request Raised` deleted. No `frz_*` family is registered, because a lifecycle is what a stage family is for and a freeze has one transition | `Account Detail Changed{field_group: lifecycle, action: freeze, freeze_type}`. `unfreeze_assisted` stays: an unfreeze **is** reviewed, and it has the `unf_*` family to prove it |
| `error_class` **as an event property** | *Kept* under the R1 carve-out — listed here only to note it remains **unratified** (OD-7) | `Journey Step Failed` |
| `LOG-04` from `mode` | A single-factor session resume was riding the full-2FA login event | `Session State Changed{session_state: unlocked}` |
| `mobile_to_bank` · `face_match` · `broker_submission` · `exchange_submission` · `pan_verify` (as a `service_id`) · `upi_collect` (as a `service_id`) | Six conflicting spellings across five checks | `pan_mobile_fetch` · `liveness_face_match` · `broker_bo` · `exchange` · `pan_nsdl` · `psp_collect` |
| `cap_type: bank_manual` | Confirmed live two-spelling defect | `cap_type: bank_manual_entry` |
| `outcome: failed` | Confirmed spelling collision | `outcome: failure` |
| `outcome_code: BELOW_MINIMUM` | Broke the `<DOMAIN>_<CONDITION>` convention | `FUNDS_BELOW_MINIMUM` |
| `outcome_code: PASTE_BLOCKED` · `FATHER_NAME_TOO_SHORT` · `DUPLICATE_IDENTIFIER` | Three bare condition words in a prefixed namespace — the last three R9 exceptions, renamed rather than grandfathered once OD-2 confirmed nothing has emitted (§5.2) | `BANK_PASTE_BLOCKED` · `ADDR_FATHER_NAME_TOO_SHORT` · `AUTH_DUPLICATE_IDENTIFIER` |
| `tier: C` (as a value for concealed financial data) | Published as *"never appears — rendered in full"*; reusing it inverts a registered meaning | **`tier: F`** |
| `freeze_type: trading` for a customer-initiated Profile freeze | Confused effect with origin | `freeze_type: voluntary_client` |

**Thirty-four properties removed.** Each is an orphan on no event, a duplicate under a second name, or derivable from another property on the same event. None of the facts is lost.

**Not removed, and why.** `charge_paise` is derivable from `request_type` **today** — but only while pricing is a three-value constant. A price is a fact about the world at the moment of emission, not about the schema; the first repricing proves it. Same reasoning that stamps `account_state` onto the event rather than reading it. Its integer-typed-but-three-value-enumerated defect is fixed by dropping the enumeration: it is an integer.

---

## 8. Migration

### What is already emitting

**Nothing. Confirmed by the document owner on 20 Aug 2026, closing OD-2.** The repository holds four prototypes — the KYC journey, the ops panel, the profile surface and support. No CleverTap account is cited as receiving traffic in either source document; the budget line at kyc-event-spec.html:1736 reads *"Remaining for trading, funds, MF, portfolio, charts | 482"*, which is a forward-looking budget, not a usage report. The abandonment event *"fires nowhere today"* by the source's own statement, and the funds instrumentation is *"read off the build"*.

**The gate is confirmed open, and it closes at first emission.** OD-2 asked whether anything was emitting; the answer is no, so every rename below lands as written rather than as a proposal, the ~55-name fallback is not taken, and no change in this revision is provisional. The window for any *further* rename stays open only until the first event fires. After that each becomes an addition plus a permanent dead value, and the cost changes by an order of magnitude in one direction only.

### The four renames and their cost

| Old name | New name | Cost today | Cost after first emission |
|---|---|---|---|
| `KYC Step Completed` | `Journey Step Completed` | A spec edit + a prototype edit | A permanently split longitudinal funnel at the deploy date |
| `KYC Step Failed` | `Journey Step Failed` | Same | Same |
| `KYC Step Abandoned` | `Journey Step Abandoned` | Same | Same |
| `AOF Generated` | `Agreement Generated` | Same | Same |

**The trade, stated plainly.** Three spine renames now, against three names per journey forever for six already-demanded multi-step journeys — segment activation, IPO apply, fund withdrawal, pledge, account recovery, report generation. **3 against 18.** A rename splits every longitudinal funnel at the deploy date; that is precisely why it is done before the first event fires and not after. The `Agreement Generated` rename is forced independently: reusing the AOF event for the segment-addition form is explicitly forbidden, so without it segment activation buys a name.

**The fallback is retired, and recorded so nobody rebuilds the argument.** Had a live stream existed, the honest answer would have been to keep the four KYC-prefixed names, accept that every other journey buys three, and add roughly 15 names — taking the total to ~55 and the remainder to ~457. OD-2 rules that stream out, so the four renames stand and the count stays at 40.

### Property renames — mechanical, no name cost

`method` → `capture_method` (Media Captured only) · `trigger` → `fallback_trigger` (Manual Fallback Entered only) · `field_group` → `reveal_group` (Sensitive Value Revealed only) · `previous_stage` → `previous_stage_name` · `hours_in_previous` → `seconds_in_previous` (**grain change: divide by 3600 → multiply by 3600; a straight rename here would silently change every SLA number**) · `total_duration_min` → `active_duration_sec` (**same warning: minutes → seconds**) · `aof_version` → `artefact_version` · `manual_touches` → `manual_touch_count` · `raised_via` + `initiated_via` → `initiated_by` · `kyc_screen_id` → `kyc_screen_name` (profile) · `nominee_opted_out` → `nominee_outcome` (profile, boolean → 3-value enum).

### Value renames — the only ones permitted, and permitted because nothing has been emitted (OD-2)

`cap_type: bank_manual` → `bank_manual_entry` · `outcome: failed` → `failure` · `outcome_code: BELOW_MINIMUM` → `FUNDS_BELOW_MINIMUM` · `blocked_reason: closure` → `closure_in_progress` · `request_type: closure` → `account_closure` · `field_group: account_state` → `lifecycle` · `service_id`: five settlements listed in §5.8 · `sub_module: segments` → `segment_list` · `outcome_code: PASTE_BLOCKED` → `BANK_PASTE_BLOCKED` · `outcome_code: FATHER_NAME_TOO_SHORT` → `ADDR_FATHER_NAME_TOO_SHORT` · `outcome_code: DUPLICATE_IDENTIFIER` → `AUTH_DUPLICATE_IDENTIFIER` · the 40 `stage_code` values → their lowercase `stage_name` equivalents.

**Every one of these is a rename-in-place, which the additive-only rule forbids once emitted.** They are lawful here and only here, on the pre-emission gate — and OD-2 confirms that gate is open, so each is **final rather than provisional**. The last three are the R9 prefix corrections §5.2 rules on: the exception that would have grandfathered them is not taken. After the first emission every remaining rename becomes an addition plus a permanent dead value.

### What ships with the change, not after it

1. **The per-module wrapper validation table** for `step_name`, `stage_name`, `outcome_code`, `blocked_reason`, `request_type` and `cap_type`, plus a CI job that diffs it against §5. Module-scoping removes single-point enum validation; without the table, `module: funds` + `stage_name: pan_failed_terminal` is schema-lawful and semantically nonsense. **Unenforced enums are exactly what produced `bank_manual` vs `bank_manual_entry` and an undeclared fifth `leg` value — both missed by a reconciliation pass written to catch them.**
2. **The de-duplication key table** from §3, with the two-timeout replay test.
3. **Wrapper validation of `item_value`** where `item_group` is `answer_helpfulness`, `search_result` or `closure_reason`.
4. **The R7 CI check**: every `stage_name` family declares its terminals or waives them with a reason.

### Downstream documents that become defects on adoption

| Document | Correction required |
|---|---|
| THINQ_KYC_ONBOARDING_PRD.md §22 | **Delete Appendix 22-A** — its source-of-truth column is absorbed into §5.9, with the §22.1b/d/f/g citations translated and `kyc_screen_id` registered as `kyc_screen_name`, so §22.0–§22.5 can now be replaced wholesale by a pointer; retitle the catalogue from "28 names"; adopt module-scoped `step_name`/`stage_name`; register the three `sub_module` values; enumerate `dropoff_class`; add `partially_provisioned` and `closing`; delete the `stage_code` break-down reference in §22.2b; correct the outcome_code count to 53; remove `Help Requested` from §22.2a; correct the de-dup key from `case_id` to `context_id` |
| THINQ_PROFILE_PRD.md §10a | Remove `request_id`, `is_terminal`, `control_id`, `surface` (already removed upstream and still specified); adopt `reveal_group`; adopt `initiated_by`; adopt `freeze_type: voluntary_client`; delete the `Account Request Raised` funnel step |
| kyc-event-spec.html §14/§15/§17 | Adopt the eight-value `leg`; adopt `bank_manual_entry`; register `surface_type: menu`; declare host events for the eight orphan funds properties; correct the R-4 "non-overlapping" claim; correct the R-6 freeze ruling |
| THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md | Publish the auth `outcome_code` list (OD-6); adopt `Session State Changed` for the five audited session states |
| THINQ_KYC_PANEL.md §7.4–§7.6 | Adopt `decision: send_back` — the panel spells it `sendback`, and `Ops Decision Recorded` carries one spelling; adopt `escalated_to_level` for the §7.5.1 ladder |

---

## 9. Open decisions

Eleven raised, **one closed, ten open.** Each open one needs a named human. Nine pre-exist this document and are carried forward honestly, because **a taxonomy cannot fix a missing product decision, and structural tidiness must not be presented as progress on any of them.** Two are opened here rather than designed for: OD-10, and **OD-11**, which THINQ-EVENTS-AUDIT-001 raised after a case-insensitive count of this document for `dpdp`, `eras` and `tombstone` returned zero. **OD-2 is closed** — answered on 20 Aug 2026, and the row is kept in place because every rename in this revision is priced on that answer. Four of the ten that remain open are P0 — OD-1, OD-3, OD-6 and OD-7 — and OD-8 is P0 on one half and P1 on the other.

| # | Decision | Owner | Sev | Recommendation |
|---|---|---|---|---|
| **OD-1** | **Who is the registrar?** Six module-scoped enumerations plus two id registries are now shared artefacts no single PRD owns, and orders, funds, portfolio, IPO, reports, support and auth are about to register into them simultaneously. Funds has no PRD at all — *"a registry with no registrar"* | Analytics + Eng leadership | **P0** | **Name one person before any module registers its first value.** Every enum-consistency defect found in this taxonomy — the fifth `leg` value, two `cap_type` spellings, two `outcome` spellings, two `method` vocabularies — is a **registrar** defect, not an author defect. A reconciliation pass is not a substitute for an owner: the pass that certified *"every property appears in exactly one glossary with one vocabulary"* missed two live defects in the two newest sections it was written to check. Adopting this document without an owner adopts the defect rather than the design |
| **OD-2** ✅ | **Is anything emitting today? — CLOSED 20 Aug 2026. Nothing has been emitted to CleverTap.** Every rename here was priced on "no", and "no" is the answer | Analytics + Eng — **answered by the document owner** | **Closed** (was P0) | **Consequence, stated plainly: every value rename in v1.1.0 is final, not provisional.** The four name renames, the eleven property renames and the value renames in §8 all land as written; the ~55-name fallback is retired and SHALL NOT be revived. The pre-emission window for *further* renames is **still open and closes at the first emission** — after that every rename becomes an addition plus a permanent dead value. Re-check the account immediately before the first deploy; the answer is only durable until something fires |
| **OD-3** | **Position reconstruction.** `quantity` + `instrument_id` + `product_type` on an order stream lets anyone with dashboard access reconstruct what a `context_id` holds — materially the disclosure the balance ban exists to prevent, arriving by a different route | Compliance + Analytics | **P0** | **A compliance read before orders is instrumented, not after.** Recommend keeping `quantity` (freeze-quantity and circuit-limit analysis is impossible without it) and gating dashboard access to `module: orders` raw events. If compliance refuses, band the quantity |
| **OD-4** | **Does `Charged` cover collected service fees, or only brokerage?** This document rules it covers both (a ₹59 contact-change fee and a ₹150 DDPI fee are earned revenue) | Finance + Product | **P1** | Confirm with finance **before orders ships**. Firing it wrongly is **not retrospectively fixable**, and if service fees are counted in a different ledger this ruling double-counts irreversibly. If unsure, restrict to brokerage and revisit |
| **OD-5** | **Per-step idle thresholds** are unset, so `Journey Step Abandoned` fires nowhere and the recovery engine has no trigger. Five more journeys now adopt a name that cannot fire | Product | **P1** | Set a default (recommend 30 min in-app, 24 h external-app) and tune per step. **Meanwhile use the open-step population rule from §6** — it is addressable today with no threshold and gives the comms engine a real audience |
| **OD-6** | **The auth `outcome_code` list is unwritten.** Nine codes are proposed here against a module whose PRD names OTP expiry, invalid OTP, a 5-resend ceiling, a 15-minute resend lock, a 15-minute lockout, 10/hour and 20/24h caps, an abuse challenge and a lost-channel route | Auth PRD owner | **P0** | Publish the full list. Until it exists the auth half of a shared stream sits outside the closed-and-additive guarantee, and a code emitted today cannot safely be renamed tomorrow |
| **OD-7** | **The `outcome_code` → `error_class` mapping has never been ratified.** It is described in the source as *"a proposal, not a ruling"*. It decides nudge vs corrective guidance vs apology vs silence for all 53 KYC codes | Product + Content | **P0** | Ratify the table before the first comms message sends. This is a blocker this document does not remove |
| **OD-8** | **Permission detection (`A2`) and dormancy boundaries (`A5`).** No rule separates permission-denied from capture-failed, so `permission_state` now has a home and no source. The `active`/`dormant` boundary may not be a product choice — SEBI defines dormancy for trading accounts | Eng / Product + Compliance | **P0** / P1 | Build the detection rule (it is the instrumentation half of an already-open content item). Take the dormancy boundary to compliance before choosing a number |
| **OD-9** | **`reason_code` has no registry** in either document, on two events, and four real terminal grounds (applicant ineligible, EDD not satisfied, confirmed document fraud, auto-close on non-response) have no code at all | Ops + Compliance | **P1** | Write the registry with a terminal/recoverable flag per ground. `is_recoverable` on `Application Rejected` has nothing to read until it exists |
| **OD-10** | **Four id registries are empty or unwritten:** zero profile `element_id` values, no `field_id` registry, no `queue` list, no closure-reason registry | Eng + Ops | **P2** | Populate before each surface ships. `element_id` and `field_id` are engineering-owned and frozen by rule; an unregistered id is an unqueryable funnel |
| **OD-11** | **DPDP erasure has no representation of any kind.** No `request_type`, no stage family, no `action` value (`remove` is a record, `close` is the account), no `account_state` value (`closed` already means closed-and-retained), no `dispatch_outcome` for a legally obligated suppression, and no mechanism to mark an already-emitted `context_id` as erased. §2's strongest guarantee — *"re-running last quarter's funnel today returns the same numbers"* — is in direct opposition to the erasure right; §2 now records the one suspension rather than hiding it, and nothing else about the shape is settled | Compliance — **name the person, not the function** | **P1** | **Open it and register no vocabulary yet.** `action: erase`, `account_state: erased` and `dispatch_outcome: suppressed_erased` are the obvious shapes and all three are **held**: §5 is additive-only once emitted, and minting a value now presumes the shape of a ruling not yet made. Compliance decides first whether an erased subject's rows are deleted, tombstoned or aggregated — the three answers need three different registrations, and only one of them needs any at all |

### Registry-versus-product disagreement

Not a decision this document can make, and it affects five modules designed for above. The module registry marks `ipo`, `corporate_actions`, `portfolio · pledge` and `orders · gtt` as **Live**; the customer-facing help centre publishes the opposite — IPO applications *"not currently supported"*, buyback and OFS *"not yet"*, pledging *"not on Thinq yet"*, GTT and AMO *"isn't available on Thinq yet"*, MTF *"no"*, mutual funds *"not yet"*.

**Someone must say whether the module registry is a build roadmap or a statement of what exists.** The two readings produce different budgets and different priorities. This document registers `context_type` values, stage families and outcome codes for products that may not exist — cheaply and additively, which is the right bet either way, but the speculation is declared rather than hidden.

---

*End of THINQ-EVENTS-001 v1.1.0. 40 event names. 11 envelope properties. 472 of 512 remaining.*
