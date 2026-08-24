---
title: Thinq Event Taxonomy — Audit Close-Out
doc_id: THINQ-EVENTS-CLOSEOUT-001
version: 1.0.0
date: 20 Aug 2026
status: Record of what was applied. Supersedes nothing; THINQ-EVENTS-001 governs.
records: THINQ-EVENTS-AUDIT-001 (19 Aug 2026) and its application across 13 documents
---

# Audit Close-Out

Six waves. Four documents corrected, then five more, then three repair passes over the corrections
themselves. **Thirteen documents changed.** What follows is what landed, what my own audit got wrong,
and what is left.

---

## 1. The headline

**The single emission blocker is closed.** `context_type` was a 12-value closed, non-nullable envelope
property with no value for an account-level surface, so `Screen Viewed{screen_name: funds_home_*}` — the
opening node of five FMS funnels — could not construct a lawful envelope. It is now **14, closed**, gaining
`account` and `session` **in one pass** with a `module: marketing` pre-auth nullability rule. Not the
one-value fix FMS asked for: the registrar granted the version the adversarial suite argued for, which is
the right call, because `Session State Changed`, `Login Completed` and the pre-auth cookie-banner
`Consent Captured` all needed `session` and none of them is funds.

**OD-2 is answered: nothing has emitted.** That retires a second P0 outright and makes every rename in
this wave final rather than provisional. It also means the three bare `outcome_code`s — `PASTE_BLOCKED`,
`FATHER_NAME_TOO_SHORT`, `DUPLICATE_IDENTIFIER` — could be renamed to their R9 prefixes rather than
grandfathered. That window is now closed behind us; the next rename costs a permanent split vocabulary.

**§5.9 is a register rather than a list.** It was the one §5 subsection with no columns at all — a bare
comma-separated run of 30 property names. It is now a 36-row table with Values, Type, **Written by** and
Recompute cadence, built by migrating Appendix 22-A out of the KYC PRD before that section was retired.
The writer partition lands at **9 stated / 19 inferred / 8 open**, matching the audit's census exactly.
Without it, all 27 communications requirements had audiences nobody could author.

---

## 2. What changed, per document

| Document | Change |
|---|---|
| `THINQ_EVENT_TAXONOMY.md` | **v1.1.0, 20 Aug.** `context_type` → 14. Nullability rules for `session_id`, `account_state`, `engagement_state` on customer-less events. `otp_channel` into the OTP key. `transition_index` registered at product scope. `filled_quantity` declared cumulative. R1 restated with an **enumerated membership** in place of "exactly two". Four wrong counts corrected. Terminal-of-record column across all stage families. §5.9 rebuilt. OD-11 opened for DPDP erasure with no vocabulary registered. Free value additions across `channel`(+`sms`), `service_id`(→24), `reveal_group`(→14), `report_type`(→18), `artefact_code`, `blocked_reason`, `outcome_code`, `stage_name`, `request_type`, `sub_module`, `query_scope` |
| `THINQ_PROFILE_PRD.md` | **The highest-value item** — the only downstream doc the authority does not supersede. 13 property removals/renames, `field_group`→`reveal_group` on the reveal grain only, `request_type` 9→8, `unf_*` enumerated as an eighth family, P-29 closed, `freeze_type` added with the `voluntary_client` ruling, the `Account Request Raised` node deleted, `bank_*` and `request_type: bank_add` registered — which FMS's funnel F11 already depended on |
| `THINQ_KYC_ONBOARDING_PRD.md` | §22.0–§22.5 retired with a "was → read instead" mapping. **Appendix 22-A preserved, migrated into §5.9, then deleted** — a complete round trip rather than a deletion |
| `kyc-event-spec.html` | Six supersession blocks (§04, §05, §06, §07, §10, §14). Budget corrected to 40/472 in the masthead and §12. §17's R-4 and R-6 chips flipped from `fixed` to `superseded` with the overturned rulings inline; R-8 and R-9 from `open` to closed. §19–§23 mirrored to the FMS spec |
| `product-requirements-events-and-funnels.md` | **v0.2.0.** F3's node 6 stopped redefining `fund_approved`. Claims corrected to five — `settlement_run_index` **withdrawn** in favour of the registered `transition_index`. §6.1's self-contradiction fixed. Name count restated as 15 rows / 19 names. §3 recast from live complaint to the record of what was found and fixed. FMS-OD-1 closed. §12 audited row by row: **7 landed, 7 still owed** |
| `THINQ_TNC_PRD.md` | `by_proceeding` added — the mode that applies to every welcome-page artefact. `C-PANBANK`/`T-MITC`/`O-DDPI` recorded as pending. The `channel` collision resolved. The C-MKTG reading made consistent across §3.1, §4 and §11.1 |
| `THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md` | The nine `AUTH_*` `outcome_code`s published — the module had zero occurrences of `outcome_code` while the authority proposed nine against it. Session states mapped, `idle_warned` added. LOG-04 deliberately left alone outside the `mode` property |
| `THINQ_KYC_PANEL.md` | `sendback` → `send_back`. The four-of-seven `decision` gap declared rather than left to be discovered |
| `product-requirements-communications.md` + `web/` | The mandated-settlement advance announcement shipped — F9's node 1 had been filtering on a message that existed nowhere. ~11 wrong rule citations corrected in shipped code. C18 and C22 promoted to real rules. **A citation guard added to `.test-assert.js`** so the drift cannot recur |
| `architecture/event-model.md` | Stubbed, 63 lines → 16. Two generations stale, no banner, and the file a new engineer reaches for first |
| `api/digio.md` | Was instructing engineers to use a de-duplication key the authority calls "already inadequate". Now points at §3 per event family |
| `THINQ_EVENT_TAXONOMY_TESTS.md` | D-40 restated 22 → 26 with the four missed members named. Counts corrected. A §10 recording the audit's verdict: 0 of 50 applied, 21 standing, 29 refuted with the four refutation patterns named |

---

## 3. What my audit got wrong

Agents found **43 distinct errors** in the audit and in my briefs. The pattern matters more than the count:
most were **file-state probes**, not analysis. I checked whether a fix had landed by grepping for a string,
and a string can be absent for reasons other than the work being undone.

The consequential ones:

| # | The error |
|---|---|
| 1 | **D-40 never made the claim I told an agent to strike.** I attributed a `fallback_used`/`fallbacks_used` two-spellings claim to it. It only ever mentions `fallbacks_used`. The refutation was right on the merits — they are different grains — but there was nothing to strike |
| 2 | **D-37's origin severity was P1, not P0.** My severity table's destination (P2) was right, its origin wrong |
| 3 | **Item 42's premise was simply false.** The HC-DMT count lives in the Support PRD, not the comms PRD, which has no §10.1 at all |
| 4 | **The ×3600 warning had no target.** I warned at length that every hour-based figure tied to `hours_in_previous` must be converted. There was not one — the only carrier was a glossary definition. The dangerous edit was safe, established by an agent grepping rather than trusting me |
| 5 | **The artifact brief was inverted.** I reported six supersession blocks missing; all six were present and complete |
| 6 | **`stage_code` 46 vs 40** — the audit was right that the authority is wrong and the PRDs right, but I then propagated 46 into a change note claiming the correction had been made when it had not |
| 7 | Several line-number citations had drifted beyond ordinary drift, and one cited two properties as sixteen lines apart when they were not |

Two errors the agents caught in the authority that the audit missed entirely: §9's lead asserting "Ten…
nine pre-exist it" in one sentence, and OD-11 half-applied — §2 forward-referencing an open decision §9
never opened.

---

## 4. What the process cost, and what it caught

Three repair passes were needed because **the fixes committed the defect they were fixing**. A count
restated in §5.7 and left stale in three mirrors — including a line written fresh in the same revision.
Supersession blocks whose job was to correct stale counts, publishing stale counts. That is the single
most useful finding here for anyone editing these documents: **the mirror is the defect, not the value.**

Three agent behaviours are worth keeping:

- One **refused an instruction I gave it**, having checked that the block quote I told it to correct was
  accurate and its divergence still live. Deleting it "would have introduced a defect, not removed one."
- Three separate agents independently **refused to rewrite dated changelog entries** to match the present,
  citing the documents' own convention. Falsifying version history is worse than the inconsistency.
- One **declined to register `verification_method: knowledge_factor`** because the value depends on a
  companion property registered nowhere — recording the deferral in the authority instead of closing an
  additive-only enum around a discriminator nobody has written.

---

## 5. What is left

### Yours, and nobody else can do it

| # | Decision | Why it matters |
|---|---|---|
| **1** | **Name the registrar (OD-1, P0)** | The authority's own thesis: *"Every enum-consistency defect found in this taxonomy is a registrar defect, not an author defect. Name one person before any module registers its first value."* 74 values and 5 property claims are now registered against nobody |
| **2** | **Is the module registry a roadmap or a statement of what exists?** | The registry marks IPO, corporate actions, pledge and GTT **Live**; the help centre says none of them ships. Half the registry is unverifiable until this is settled |
| **3** | **Two compliance items** | OD-11 (DPDP erasure — no representation anywhere, vocabulary deliberately unregistered pending a ruling) and OD-3 (position reconstruction, rated P0 by the authority) |
| **4** | **OD-7 (P0)** | The `outcome_code` → `error_class` map has never been ratified. It decides nudge vs guidance vs apology vs silence for all 53 KYC codes |

### Engineering, before first emission

**§8 item 1's artefacts do not exist** — the per-module wrapper validation table for the six module-scoped
enums, and the CI job that diffs it against §5. Every *"the wrapper will catch it"* argument in this corpus
is currently a promise. Generate §6's Events-used column from §5 in the same job.

### Mechanical, and small

FMS §12 lists **7 items still owed**, each naming the authority section that must change. Two P2s need a
ruling rather than an edit: §7's *"thirty-four properties removed"* against a 45-row table — pre-existing,
and a genuine ambiguity about whether the number counts property removals or all removals; and §6's
preamble sentence about which modules consume the new names, which now has counter-examples.

---

## 6. State

All thirteen documents verified: markdown tables well-formed (the three flagged rows are escaped pipes
inside code spans, which render correctly), HTML tag-balanced at 23 sections, all four JS files pass
`node --check`. 18 pre-edit snapshots archived in `_pre-audit-backups/` with a README explaining why they
are stale by design; zero strays beside the corrected files.

**Zero P0 findings remain open against the documents.** The four decisions above are open against *people*.
