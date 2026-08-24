---
title: "Thinq — Profile & Account Management"
status: Draft
version: "1.16.0"
prd_id: "THINQ-PROFILE-001"
owner: "Product (with Compliance sign-off on contact-change, nomination, segment activation, closure and the disclosure/masking policy)"
related:
  - "voluntaryaccountfreezecomplianceteardown5brokers.md · dreamstreetfreezeflowteardown.md · indmoneyaccountclosureflowteardown.md · dhanaccountclosurecomplianceteardown.md · paytmmoneyaccountclosurecomplianceteardown.md (freeze & closure, 15–16 Aug 2026 — §7.11a, §7.11b)"
  - "dhanstatementsreportsteardown.md · paytmmoneystatementsteardown.md · indmoneyreportsteardown.md (statements teardowns, 15 Aug 2026 — §2.4, §7.10a)"
  - "THINQ_KYC_ONBOARDING_PRD.md (§5 data inventory · §7 segments · §8 nominee · §9 DDPI · §0.9 entity · §18 comms)"
  - "THINQ_RETAIL_REGISTRATION_AND_LOGIN_PRD.md (auth engine; D-28 routes the KYC-complete contact change *here*)"
  - "THINQ_TNC_PRD.md (consent artefacts, C-MKTG withdrawal §8, consent history §6, T-RAS, T-ECN)"
  - "THINQ_CUSTOMER_SUPPORT_PRD.md (§10.6b — eight published `Profile → …` paths; H38)"
  - "THINQ_KYC_PANEL.md (ops-side view of the same records)"
  - "THINQ_EVENT_TAXONOMY.md · THINQ-EVENTS-001 (the product-wide event registry §10a is aligned to; it governs every name, property and value)"
  - "dhanprofileteardown.md · paytmmoneyteardown.html (competitive teardowns, 14 Aug 2026)"
artifacts:
  - "Thinq KYC Event Spec §14 Profile events — https://claude.ai/code/artifact/699af147-9ff1-4a94-b37b-539336ef5369#profileevents (source: kyc-event-spec.html)"
prototype:
  url: "https://thinq-profile.vercel.app"
  password: "Thinq@2019"
  source: "thinq-profile.html"
  deployed_from: "_deploy/thinq-profile/"
  note: "Client-side share gate — the password is readable in the page source. It stops a link being casually opened; it is not access control. Vercel password protection needs Advanced Deployment Protection, which this team's plan does not include."
---

<!-- Changelog
 v1.16.0 — §10a closed against THINQ-EVENTS-001 v1.1.0, 20 Aug 2026. The
   registry's own OD-2 is now closed on the document owner's confirmation that
   **nothing has been emitted to CleverTap**, so v1.15.0's four value renames
   land as final rather than provisional and §10a's alignment note moves to the
   20 Aug state of the authority. Three things follow from it here.

   **P-29 is closed, and an eighth `stage_name` family is registered.**
   THINQ-EVENTS-001 §5.1 enumerates the assisted unfreeze in full for
   `module: profile` — `unf_submitted` → `unf_reviewed` → `unf_completed`, with
   `unf_rejected` as the failure exit and **`unf_completed` as the terminal of
   record** — so `unf_*` is stated here rather than owed. It sits outside the
   forty on the same rule as `bank_*`: enumerated in full, failure exit
   included, where the six carry their transition values only. §7.11a takes the
   registry's enumeration rather than minting a second, and
   `request_type: unfreeze_assisted` finally has a family to move through —
   its tracker can be built.

   **`request_type` goes from nine to eight — `freeze_assisted` is removed**
   (§10a.3a). A freeze **is** a mutation, not a request: it has one transition,
   no `frz_*` family is registered, and the fact is carried by
   `Account Detail Changed{field_group: lifecycle, action: freeze, freeze_type}`
   instead. `unfreeze_assisted` stays, and the family above is the reason — an
   unfreeze **is** reviewed, and a reviewed request is what a stage family is
   for.

   **`freeze_type` is added to both rows that carry the mutation** (§10a.3,
   §10a.3a): **4, closed** — `demat_debit` · `trading` · `voluntary_client` ·
   `regulatory` — and a customer-initiated Profile freeze emits
   **`voluntary_client`**, never `trading`. `trading` names the *effect* (orders
   stop); the other three are set by the depository, broker or regulator and
   never by the customer. The **origin** names the freeze, and only
   `voluntary_client` originates in profile · freeze. The reason rides
   `freeze_type` and never four extra `account_state` values.

 v1.15.0 — §10a aligned to THINQ-EVENTS-001, 19 Aug 2026. The event registry
   now exists as a product-wide document, so §10a stops being where Profile's
   vocabulary is decided and becomes where Profile's share of it is stated.
   Where the two disagree, THINQ-EVENTS-001 governs.

   Fourteen property definitions rewritten against its §5. The duration pair
   moves from hours to **seconds** — `seconds_in_previous` and
   `seconds_in_request` — and no prose, example or SLA figure in this document
   carried the old unit, so nothing needed converting. `previous_stage` becomes
   `previous_stage_name`; `raised_via` and `initiated_via` collapse into
   **`initiated_by`**; `nominee_opted_out` becomes **`nominee_outcome`**, three
   values because a deferral is neither `nominated` nor `opted_out` (PR-192a).
   The forty `stage_name` values are §7.11a's enumerations lowercased, and the
   registry now carries the same forty — its published 46 was one of the four
   counts THINQ-EVENTS-AUDIT-001 corrected, so there is no count left to
   disagree about.

   **Four value renames, and none of them is provisional.** OD-2 is answered:
   **nothing has been emitted to CleverTap**, so each is a rename in place
   rather than an addition plus a permanent dead value. `request_type: closure`
   → `account_closure` · `field_group: account_state` → `lifecycle` ·
   `blocked_reason: closure` → `closure_in_progress` · `sub_module: segments` →
   `segment_list`. The forty `stage_code` values take their lowercase spelling
   on the same gate.

   A **seventh `stage_name` family** is registered — `bank_*` · 8 — built from
   §7.11a's own `bank_request.status` rather than imported, because the registry
   registers the family for `module: profile` and the bank-verification funnel
   had no filter to run on without it. It is enumerated in full, failure exits
   and withdrawal terminal included, where the other six carry their transition
   values only; the two counts are built on different rules and both rules are
   now stated. `reveal_group` and `tier` are restated against the registry's
   fourteen groups and three tiers, of which Profile emits nine and two. The
   contact-change funnel loses its raise step: a raise is the first
   `Request Stage Changed`, not a node.

   Two things carried rather than resolved. **`blocked_reason`** — the registry
   deletes three of §10a.3a's six values as collisions and registers three;
   §7.14a's five locks have to be re-read before that can be applied here, so
   the six stand and the divergence is recorded in §10a. And **`unf_*`** is
   registered as a Profile stage family that §7.11a never enumerated, leaving
   `request_type: unfreeze_assisted` with no family to move through: **P-29**.

 v1.14.0 — Bank account communications, §7.4b. Owner direction, 17 Aug 2026.
   Bank accounts sent **nothing** before this — no receipt, no completion, no
   failure notice — and it is the journey where silence costs the most, because
   verification resolves after the session ends and PR-28 makes the account
   unusable for withdrawal until it does. A customer added an account, read
   *Being verified*, and was never told either that it worked or that their next
   withdrawal would be refused.

   Four events: BANK_ACCOUNT_VERIFIED (**email + WhatsApp**),
   BANK_ACCOUNT_REJECTED (**WhatsApp**), BANK_PRIMARY_CHANGED (**email**),
   BANK_ACCOUNT_REMOVED (**email**). PR-183a … PR-189a.

   Three rules worth noting. The verified message **is** the security notice for
   the addition and needs no separate one at submission (PR-184a): an added
   account is a takeover step, but nothing can leave before verification clears,
   so this arrives at the first moment there is anything to warn about. No
   message fires for a failure the customer is **looking at** (PR-186a) — three
   typed attempts would otherwise be three messages for one intent. And the
   WhatsApp-only rejection keeps PR-171a's email fallback in full (PR-187a),
   because it is the one message in the set that requires the customer to act.

   **DP-18's second limb restated**: *WhatsApp carries what the customer needs to
   know now*, not *says a thing is now true*. A rejection is not a completion and
   still belongs on WhatsApp — it blocks a withdrawal the customer may be about
   to attempt. Urgency is what the channel was really selecting on.

   Two gaps found and filled. **`bank_request.status` never existed** (§7.11a),
   though PR-133a, PR-168a, AT-P-48 and §7.14a's locks all read it — seven values
   added, with the two failure modes kept distinct because their recoveries differ
   (PR-190a, PR-191a). And **there is no way to remove a bank account** while the
   build caps accounts at three and AT-P-64 refuses a fourth even when one is
   merely verifying, so a customer cannot free a slot: PR-189a cannot fire, the
   three constraints that follow from existing requirements are stated, the
   screens are not, and the decision is **P-28 (P0)**.

   AT-P-72 … AT-P-76 added; AT-P-76 documents the removal gap rather than passing.

 v1.13.0 — Communications channel model, owner-set 17 Aug 2026. One rule now
   governs every Profile message (DP-18): **email states and files what
   happened; WhatsApp says a thing is now true.** No Profile communication uses
   SMS, which also settles a live contradiction — §18.0 forbids SMS across §18
   and the four freeze rows carried "Email + SMS" against it.

   The five e-Sign receipts stay email-only and each journey gains a **WhatsApp
   completion notice** keyed to the request's terminal status, not to the
   signature (§7.12a, PR-169a … PR-173a). This closes a gap rather than adding a
   channel: every receipt is *barred* from saying the thing is live — the
   segment one by PR-45, DDPI by PR-100, a contact change by §7.11a — because
   each becomes true later and from a third party. Nothing was sent when it did.
   Five new events: NOMINEE_REGISTERED, MOBILE_CHANGE_COMPLETED,
   EMAIL_CHANGE_COMPLETED, SEGMENT_ACTIVATED, DDPI_ACTIVATED.

   Freeze and closure channels restated per direction: FREEZE_REQUESTED,
   ACCOUNT_FROZEN and UNFREEZE_REQUESTED email-only; **ACCOUNT_UNFROZEN on
   WhatsApp**, which is what makes DP-18 a pattern rather than an exception;
   CLOSURE_REQUESTED, CLOSURE_ESIGNED and ACCOUNT_CLOSED email-only. Closure is
   the deliberate non-WhatsApp completion — the last message the customer ever
   gets has to be findable months later, and by then no deep link has an account
   behind it.

   The **CMR is download-only** (PR-68, DP-19), so `CMR_REQUESTED` is not a
   communication Profile fires. It still rides PTT_CONFIRMED at activation.

   Two corrections found on the way through: the **closure form was the sixth
   e-Signed artefact** and was missing from §7.12's table, which is why that
   document said five; and PR-171a supplies the email fallback the WhatsApp
   notices need, without which a customer who is not on WhatsApp would receive
   nothing at the one moment their instruction takes effect.

   AT-P-67 … AT-P-71 added. Three open items: **P-25** (ACCOUNT_FROZEN is
   email-only while the freeze signs every device out), **P-26** (an email change
   now lands two messages on the mobile at once), **P-27** (WhatsApp is still an
   unowned channel — C57 — and six Profile events now rest on it).

 v1.12.0 — Event instrumentation, §10a. Built to the KYC PRD's §22 contract
   rather than beside it: the envelope, the six generic UI events, the module
   enumeration and the 512-name ceiling are all inherited, and `Account Detail
   Changed` already covers a field mutation, so nothing here re-mints what
   exists.

   Three `sub_module` values are missing from KYC §22.1c for surfaces this build
   has — `contact_details`, `demat_details`, `segment_list` — and two surfaces
   inside Profile belong to other modules entirely: Add funds is `funds`, the
   segment-activation journey is `segment_activation` (PR-163a).

   **Three** named events are added, not six, and none of them is Profile's:
   `Request Stage Changed`, `Action Blocked` and `Sensitive Value Revealed` are
   written product-wide with module-scoped enumerations, so orders and funds
   adopt them without a rename (PR-176a). Three earlier candidates collapsed:
   a raise is the first stage transition, a withdrawal is a terminal one, and a
   freeze is `Account Detail Changed` with `field_group: lifecycle`.
   Everything else reuses a name that already exists — §10a.3 maps ten
   behaviours onto them, including the bank 3-attempt ceiling onto
   `Attempt Cap Reached`, which fits without schema change. Where an existing
   name fits, only its **enumeration** grows, never its schema (PR-177a).

   The stage event is what makes §7.11a's enumerations measurable — how long the
   depository and exchange legs actually take — and it is server-emitted,
   because a client event measures who opened the tracker rather than what
   happened (PR-164a). `Action Blocked` is what makes §7.14a's five locks
   measurable at all (PR-166a).

   Two privacy rules restated for this surface: a reveal event carries the reveal
   group and the tier, never the value (PR-165a); a charged journey carries what
   it cost, never what the customer holds (PR-167a).

   §10a.3a enumerates every property and every value — fourteen properties, the
   forty `stage_name` values across six families plus the eight-value `bank_*`
   family, and the four legs — on the same closed-and-additive-only rule KYC
   §22.1d sets for its own (PR-174a, PR-175a). Five funnels and eleven profile
   properties defined. Three open items — **P-24** is the one to note: KYC
   publishes an event-spec artefact and binds the PRD to it; Profile's catalogue
   is prose only, so every `element_id` and `screen_name` has nowhere to live
   yet.

 v1.11.0 — Adding a bank account, rebuilt. 17 Aug 2026. §7.4a is the as-built
   record; PR-153a … PR-161a are the rules behind it.

   The journey now **leads with the scan** and demotes typed entry to a link,
   because the account number and the IFSC are the two things a customer least
   reliably has to hand — and mistyping either is the one error here that
   validation cannot catch. The *how should we check it* question is gone: the
   scanned route is the UPI ₹1, the typed route is the ₹1 credit, and asking
   again after the route is chosen re-asks what was already answered.

   Ported from the onboarding journey so the two behave alike: the loader with
   its escalating message, the **Name doesn't match** screen showing both names
   side by side, the three-attempt ceiling on typed entry, and the *Limit
   reached* wording. A **UPI** mismatch does not consume an attempt. Typed
   values survive a failure. The **Name on PAN** row reads from the record, not
   from the onboarding fixture (PR-77), so it says Arvind Kumar Sharma here.

   Leaving the IFSC field now echoes the bank and the branch it resolves to —
   the only check available before the ₹1 moves (PR-155a).

   The scanned route has no receipt screen; both routes confirm in the same
   words, **Bank account added successfully**. Add funds gained an in-place
   dropdown for the source account, verified accounts only, with **Add a bank
   account** as its last item.

   **One defect found in test and fixed:** `fnext()` walked straight past the
   mismatch screen onto the receipt, so Enter added an account that had just
   failed its name check. AT-P-60 covers it. AT-P-59 … AT-P-66 added.

   §7.14 gained five rows — the name-match rule on the Bank accounts surface,
   the IFSC help text, the own-name rule and the running count against the
   3-account limit, the limit screen's reassurance, and the funding picker's
   primary/verified detail.

 v1.10.0 — Locks, and the settlement trim. 17 Aug 2026.

   **§7.14a is new** — the five states that withdraw a control, in one table,
   because the failure mode is never one lock: it is two of them speaking on the
   same screen, or a control greyed by one while a journey reached by another
   route still runs. PR-138a … PR-145a.

   PR-139a is the sharp one: while a mobile or email change is in flight,
   nothing else about the account may change — banks, nominee, segments, DDPI,
   settlement, consents, closure, the other contact field, the PIN and the
   authenticator. A contact change is the takeover vector, and that window is
   exactly when the rest must hold still. Two exceptions, both deliberate: the
   **freeze** and **Log out of all devices**, because each narrows access rather
   than widening it.

   PR-138a locks the settlement cycle within three business days of a settlement
   date, on both cycles. PR-140a … PR-142a close the pre-activation states.
   PR-143a requires every lock at the journey, not only at the control.
   PR-144a and PR-145a stop a lock saying itself twice, or saying it on a page
   with nothing to withhold.

   Also: freeze became a section of the Security page (PR-148a); devices are a
   collapsed accordion with a count and a Log out of all devices control
   (PR-149a, PR-150a); nomination rules appear only where a nomination can still
   be made (PR-152a); Account documents lists forms only, each with the date it
   was signed (PR-151a). §8's matrix gains a column naming what withdraws each
   control. AT-P-49 … AT-P-58.

   **Two new open items, one at P0.** P-20: PR-140a contradicts Registration
   D-28 / REC-M12 and §3.2 — a prospect who mistyped their email now has no
   route at all. P-21: the three-day settlement lock counts weekends but not
   exchange holidays.

   **Eight statements lost their surface**, seven of them from settlement: the
   225% retention ceiling and its worked example, the pledged-securities
   ordering, never-a-book-entry, the running-account authorisation row, the
   5-working-day retention statement with its 30-working-day dispute window,
   every statement that a cycle change is reversible, the NSE circular citation,
   and quarterly-as-the-regulatory-floor. Plus the by-job route into reports.
   All in §7.14. The behaviour is unchanged in every case; the disclosure is
   not.

 v1.9.0 — Four owner directions, 17 Aug 2026, all of them naming.

   **Freeze moved inside the Security page** — a section beneath the PIN and the
   devices, not a destination of its own. PR-134a is rewritten accordingly.

   **Documents is now Account services**, holding Account documents and Account
   closure.

   **Log out, never Sign out** — every customer-facing string in the prototype
   and in the PRDs (PR-03, PR-112a, §7.11a, Support §10.6a, Registration §12).
   `signOut()` and `data-go="signout"` stay as identifiers. One asymmetry left
   standing: Support's visitor-state row now reads *Logged out* against a
   *Sign in* action, because the instruction covered one half of the pair.

   **Account documents now lists everything downloadable** (PR-135a … PR-137a):
   account records, every consent artefact one row each, and the forms the
   customer has e-Signed since opening. Two documents were added to the
   catalogue — the KYC application form and the FATCA and CRS declaration —
   because a customer who cannot get them here goes to support for a record we
   already hold. Every row states its version and its date.

   PR-70 (file type and size before a download) was built with them and removed
   within the hour on owner direction, so AT-P-20 still fails and §7.14 keeps
   the row.

 v1.8.0 — The IA split, 17 Aug 2026. `Freeze or close` is gone as a page.
   **Freeze** now sits in **Security**, next to the PIN and the devices — which
   is where its own first screen already sent people (PR-112a offers change your
   PIN, disable biometric and log out everywhere as the alternatives to
   freezing). **Account closure** sits in **Documents**, with the signed forms,
   the client master report and the statements a closing customer is told to
   download first. PR-134a carries the reasoning.

   Consequence, recorded as **P-19**: Support §10.6b publishes
   `Profile → Account` for both actions and there is no longer an *Account*
   surface. The build aliases `account` → closure so a closure question still
   lands; a freeze question does not. Two published strings need updating and
   AT-P-01 re-running.

   Also: the freeze control reads *Not available* while a closure is in
   progress. The shared page withheld it by returning the closure tracker
   instead; separating the surfaces must not hand it back.

 v1.7.0 — Two rules and one loss, all 17 Aug 2026.

   PR-133a — a closure request cannot be accepted while another request against
   the account is open: a contact change, a nomination, a segment activation, a
   DDPI (Instant Sell) activation or a bank verification. Each ends in a change
   to a record the closure would then remove. The refusal names the request and
   routes to its tracker rather than greying a control, and it is enforced at
   the journey rather than the button, because a C-PROC consent withdrawal
   routes into closure without passing the button. The assisted route is
   withdrawn on the same condition. AT-P-46 … AT-P-48.

   DDPI is labelled **DDPI (Instant Sell)** wherever it is named to a customer.

   The loss: **PR-76 now has no surface at all.** The explanation went first,
   then the Withdraw control. A raised closure cannot be withdrawn from the
   product at any stage, and the point of no return is named only in the
   sentence saying it has been crossed. AT-P-42 and AT-P-44 fail by design; the
   handler is retained in source, so restoring it is one line.

 v1.6.0 — Closure, as revised on 17 Aug 2026. The confirmation and the
   in-progress card were both cut back, and a tracker replaced the card's
   contents.

   New: PR-130a (a closure tracker carrying only the downstream legs — the
   exchange deregistration and the depository closure — because every
   account-level check is cleared before the request can be signed), PR-131a
   (the two mandated clocks are stated to the customer as the single outer
   bound, 1–3 business days), PR-132a (one sentence for the read-only lock, and
   no route back offered from it). AT-P-43 … AT-P-45 cover them.

   §7.14 gained two rows, and one of them costs a numbered requirement its
   surface: PR-76's first limb. The point of no return is now named only in the
   notice that says it has been crossed — the warning *before* crossing is gone
   from both the confirmation and the tracker, and the withdrawal right survives
   as an unexplained button. AT-P-42 is amended and fails on two limbs by
   design. The second row is post-closure retrieval: how to obtain a statement
   after the account is closed is no longer stated anywhere, which compounds
   P-16.

 v1.5.0 — Brings §7.11a and §7.11b level with the prototype as built on
   16 Aug 2026, after the freeze, unfreeze and closure journeys were walked and
   revised through the day. Adds §7.11a.1 and §7.11b.1 — the as-built step
   tables — so the document and the build cannot drift apart silently.

   New requirements from what the build settled: PR-123a (a freeze ends every
   session, including the one raising it), PR-124a (a third factor to unfreeze —
   PAN or date of birth, checked against the vault), PR-125a (a progress state
   where the change is not instant), PR-126a (an assisted request returns a
   quotable reference and does not re-ask an intent the route established), and
   PR-127a with DP-17 (a reversible assisted request raises itself; an
   irreversible one collects intent for a person).

   PR-109a is now four comms events, not two — FREEZE_REQUESTED / ACCOUNT_FROZEN
   and UNFREEZE_REQUESTED / ACCOUNT_UNFROZEN — because the gap between asking and
   the state changing is real and disclosed. All four are specified in Onboarding
   §18.3 (v2.11.0); copy still belongs to §18.2a.

   §7.14 grew by nine rows. The sharpest is PR-111a: the freeze no longer names
   the demat account or the UCC and offers no route to freeze them, which was the
   teardown's single biggest cross-cutting finding. AT-P-36 … AT-P-42 added.

 v1.4.0 — Adds §7.11a (freeze, against the SEBI voluntary-freezing framework of
   12 Jan 2024) and §7.11b (closure), from six teardowns including two screen
   recordings analysed 16 Aug 2026. PR-107a … PR-122a. The sharpest finding is
   PR-111a: all five brokers freeze trading access only, all five are also
   depositories, and none cross-references the depository freeze facility — a
   client who believes they have secured their assets has secured only their
   login. P-6 is now answerable in shape, if not yet decided.

 v1.3.0 — Adds §2.4 and §7.10a, Statements & reports, from teardowns of Dhan,
   Paytm Money and INDmoney (15 Aug 2026). Nineteen findings become PR-107 …
   PR-124, DP-14 … DP-16, AT-P-27 … AT-P-35 and three open items. The surface
   carries five statutory documents, so the inventory starts from what SEBI and
   the depositories oblige Thinq to produce rather than from a feature list.

   Also brings the document level with the interactive prototype built
   14–15 Aug 2026, which exercised every journey end to end and surfaced
   decisions this PRD did not yet hold.

   Adds §7.13, charged actions: two journeys cost money (registered-contact
   change ₹50 + GST, DDPI ₹150 inclusive), and neither had a specified
   disclosure moment, funding pre-flight or failure-to-fund route. Adds
   PR-100 … PR-105 and DP-9 … DP-12.

   Extends §7.11a with two enumerations the prototype needed and the document
   did not carry: segment activation (SEG_*) and DDPI (DDPI_*), both with their
   terminal failures. Extends §7.12 from three e-Signed artefacts to five — the
   segment activation form and the DDPI form are signed instructions too, and
   were being produced with no receipt specified.

   Adds §7.14, an honest record of the requirements this build has no surface
   for. Twelve requirements lost their surface to owner-directed simplification
   and four acceptance tests now fail by design. They are listed rather than
   quietly dropped, because a requirement with no surface and no record is
   indistinguishable from one that was never written.

 v1.2.0 — Adds §7.11a: closed status enumerations for the three trackable
   requests (contact change, nomination, nominee correction), each value paired
   with the customer-facing label it renders as. Adds PR-97 … PR-99 and open
   item P-11 — the failure values are specified but no tracker models them.

 v1.1.0 — Adds §7.12: the three e-Signed artefacts Profile produces after
   activation — a nomination, a registered-mobile change and a registered-email
   change — are emailed to the customer with the signed form attached, and filed
   in Account documents. Follows the AOF_ESIGNED precedent in Onboarding §18.3,
   which already attaches the signed AOF on e-Sign. Adds PR-94 … PR-96, AT-P-21,
   and open item P-10 (attachment password convention). Requires three events to
   be added to Onboarding §18.3 and their copy to §18.2a — raised there, not
   resolved here.

 v1.0.0 — First issue. Establishes the Profile section as a named product surface.

   Scope was set by working backwards from commitments other documents have
   already made to customers: eight `Profile → …` paths published in the help
   centre and spoken by the assistant (Support §10.6b), two "you can do this
   anytime from Settings" lines inside approved onboarding copy (Onboarding §7,
   §8), one consent-withdrawal control that Legal is relying on as the mitigation
   for a P0 (TnC T21), and one explicit out-of-scope hand-off from the auth
   engine (Registration D-28 / REC-M12). None of those destinations exist.

   Competitive teardowns of Dhan and Paytm Money (14 Aug 2026) were used as a
   defect catalogue rather than a feature list — §2 records what both got wrong
   and turns each into a requirement here.

   Nine open items, five at P0. The sharpest are P1 (the section is named
   "Profile" in eight live paths and "Settings" in two approved onboarding
   messages) and P3 (three published answers describe regulated multi-step
   journeys as self-service edits).
-->

# Thinq — Profile & Account Management (PRD)

## Document Conventions

> **Capability-Level Rule.** This document describes WHAT the customer can see and do in the account area and WHY. It does not specify implementation: no frameworks, datastores, API contracts, component libraries, CSS values or architecture. Where it names a screen or a path it is naming a customer-visible destination that other published copy already points at, not a routing table.

**Requirement language:** SHALL = mandatory. SHOULD = strong recommendation, deviation requires sign-off. MAY = optional.

**Identifier scheme:** requirements **PR-nn** · acceptance tests **AT-P-nn** · decisions **DP-n** · open items **P-n**. Open items in this document use the prefix **P**, as onboarding uses **C**, support uses **H** and consent uses **T**.

**Authority.** Where this document and another Thinq PRD describe the same record, the owning PRD is authoritative for the record and this one is authoritative for its *presentation and self-service route*. Any conflict is a documentation defect to be raised, not resolved locally.

---

## 0.1 Why this PRD exists

The Profile section is not a new idea being proposed here. It is a destination **four other documents have already sent customers to**, and it does not exist.

| Commitment | Made in | Says |
| :--- | :--- | :--- |
| **Eight published `Profile → …` paths**, resolving to seven destinations | Support §10.6b (live in the assistant) | `Profile → Bank Accounts` · `Contact Details` · `Nominee` · `Segments` · `Security → Change PIN` · `Account` (close **and** freeze) · `Documents` (CMR). Eleven self-serve paths are published in total; the other three point at Funds and Orders. |
| *"You can add F&O and Commodity anytime from Settings"* | Onboarding §7 — **approved copy inside a non-dismissible confirmation** | The de-scope confirmation an applicant reads before giving up a segment |
| *"You can add a nominee anytime from Settings"* | Onboarding §8 — **approved copy inside the opt-out confirmation** | The words that make a nomination opt-out defensible |
| Self-service marketing-consent withdrawal, effective < 60s, *"surfaced prominently after activation"* | TnC §8, D-7, **T21** | The stated mitigation for requiring marketing consent at onboarding |
| KYC-complete contact change → *"the demat profile modification flow"* | Registration **D-28 / REC-M12**, marked **Out of Scope** there | The auth engine hands the change off and names no recipient |

Two of those are load-bearing in a way a normal feature request is not.

**The nominee opt-out.** Onboarding §8 forbids recording an opt-out from a label reading only *"later"*, on the grounds that *"recording a decline against someone who believed they were postponing is precisely the harm the nomination rule exists to prevent."* The approved copy resolves this by promising a route: *add a nominee anytime from Settings.* If that route does not exist, the confirmation is back to being a mis-labelled decline — this time with the mis-labelling written into the approved text.

**The marketing consent.** TnC **T21** records an owner decision, taken against advice, to make C-MKTG required at onboarding, and states the mitigation explicitly: *"a customer can turn marketing off within 60 seconds of activation, so the consent is required to open but not to keep. Legal SHALL assess whether that mitigation is sufficient before publication."* The withdrawal control is the entire mitigation. Building it late, or burying it, is not a UX shortfall — it removes the only argument the decision has.

Support **H38 (P0)** already flags the general form of this: *"a customer told 'Open Profile → Bank Accounts' will hunt for a menu that may not exist and conclude the app is broken, not the help centre."* **This PRD closes H38 by building the seven destinations those eight paths name.**

---

## 0.2 Entity

Not restated here. **Onboarding §0.9 is authoritative** and two of its constraints bind directly on this section:

- **CDSL only.** DP ID **12063900**, SEBI DP Reg. No. IN-DP-22-2015. Every BO ID is 16 digits beginning `12063900`. The demat block SHALL NOT offer a depository choice, and SHALL NOT print "CDSL/NSDL".
- **No MCX membership.** Commodity is offered on an NSE or BSE commodity-derivatives segment (**C54**, open). The Segments surface SHALL NOT name MCX as the venue until C54 resolves.

Screens say **Thinq**. Documents that identify the contracting entity — CMR, AOF, contract notes, statutory footers — carry **Money Logix Securities Private Limited**.

---

## 1. Purpose & Scope

### 1.1 Purpose

Give an activated customer one place to **see the account record Thinq holds about them, understand which parts they can change, and start the change** — without raising a ticket, and without being told to go somewhere that does not exist.

### 1.2 In scope

The authenticated account area for retail customers on **responsive web** (desktop and mobile web): Profile home, Personal & KYC details, Contact details, Bank accounts, Nominee, Segments, Preferences, Consents & privacy, Security, Documents, and Account (freeze/close). Includes the pre-activation states of the same section, because a prospect and an in-KYC applicant both reach it.

### 1.3 Out of scope, with reasons

| Out of scope | Reason |
| :--- | :--- |
| **Analytics & event instrumentation for these surfaces** | Owned by onboarding §22 and the CleverTap event spec, which is a two-layer model with a **512-name account-wide ceiling** and closed, additive-only enumerations. Naming Profile events here would fork that list. §9.5 records only the seam: what must be added there before this ships. |
| Native iOS/Android app | No native app PRD exists. The IA in §3 is built to survive the port; nothing here assumes a desktop rail. |
| Trading, funds, orders, holdings, portfolio | Different surfaces. Profile links out, holds nothing. |
| Statement *generation* (tax P&L, capital gains, contract-note archive) | Profile owns **account documents** (CMR, AOF, consent history). The reports engine is unspecified and is its own PRD. §7.10 draws the line. |
| Legal text of any artefact | Legal owns the words (TnC §3.5 register). Profile owns presentation, version display and the consent record's readability. |
| The ops-side view of the same records | THINQ_KYC_PANEL.md. |
| Assisted (Ops-performed) identity verification procedures | Registration open items explicitly assign these to Operations/Compliance. Profile owns the customer's side of the hand-off, never the verification itself. |

### 1.4 Non-goals

Profile is **not a growth surface**. No upsell tiles, no `✦New` badges on products, no cross-sell in the rail. §6.4 makes this a requirement rather than a preference, and §2 records where the market does the opposite.

---

## 2. Competitive teardown — Dhan and Paytm Money, 14 Aug 2026

Both were reviewed on live authenticated sessions. Both are useful mainly as a **defect catalogue**: the failures are structural and repeat across the market, so each one below is turned into a requirement rather than a note.

### 2.1 The nine findings that became requirements

| # | Observed | Where | Becomes |
| :--- | :--- | :--- | :--- |
| **T-1** | **Masking protects the wrong field.** Dhan masks the account holder's PAN and mobile, then prints the **nominee's full 12-digit Aadhaar**, mobile, email and address two cards below. Paytm masks PAN to `******175R` while showing the 16-digit BOID, 8-digit DP ID, 14-digit CKYC number, full email, full mobile and exact DOB on the same screen. | Dhan §2 · Paytm F-06 | **§6** — one disclosure model, ranked by sensitivity, with the nominee treated as a third party |
| **T-2** | **Masking is a render-layer trick.** Paytm's profile API is called with `panNumber=true`; the unmasked PAN is delivered to the client and hidden in the UI. | Paytm F-14 | **PR-31** — masking is server-side; unmasking is a distinct, re-authenticated, audited call |
| **T-3** | **A locked field with no recourse.** Paytm's "Name as per your KYC" is a `disabled` input with a padlock, no tooltip and no route. Dhan displays mobile and email as immutable text with no path forward. | Paytm F-05 · Dhan §4.4 | **PR-05** — every non-editable field carries either a route or a stated reason |
| **T-4** | **"Edit" promises six fields and delivers one**, as a full-page replacement with no Cancel, no Back, and the heading still reading "Profile". | Paytm F-05 | **PR-06, PR-07** — the edit affordance names its scope; every flow has a non-destructive exit |
| **T-5** | **Read-only data dressed as editable.** Dhan renders "Name as per KYC" as a bordered text input with a pencil icon, twelve pixels above the same data type rendered as label/value text. | Dhan §5.1 | **PR-04** — one visual language per data type; read-only is text |
| **T-6** | **The status pill contradicts the CTA.** Dhan's nomination card shows *"Nomination request under progress"* (amber, implies waiting on Dhan) beside *"Complete Nomination →"* (green, implies waiting on you). | Dhan §3 | **PR-09** — status and action agree, or it is a defect |
| **T-7** | **A regulatory status rendered as a dead-end hand-off.** Dhan shows *"Your KYC status in KRA — [Check ↗]"* plus three lines explaining what a KRA is, then sends the user to a third-party site to learn whether they may trade. | Dhan §5.5 | **PR-25** — fetch and render the status; never ask the customer to go and look it up |
| **T-8** | **Marketing and ad infrastructure inside the authenticated account area.** Paytm's profile pages load GTM, GA4, legacy UA, two Google Ads conversion properties, Google remarketing, DoubleClick, the Facebook pixel and AppsFlyer, transmitting the full page path alongside stable advertising identifiers. Dhan puts a product upsell (`Journal by Dhan ✦New`) in the account-management rail. | Paytm F-10 · Dhan §4.1 | **PR-49, PR-50** — no ad or remarketing tags on authenticated account pages; no promotion in the rail |
| **T-9** | **Controls that are not controls.** Paytm's Statements page presents ~44 radio-looking options with **zero** real radio inputs, no roles, no keyboard access and no group names — including the ELSS statement used as §80C tax proof. Transaction History has 0 headings, 0 tables, 0 buttons, 2 tabbable elements and 42 of 79 images unlabelled. | Paytm F-04, F-07 | **§9.2** — real controls, real semantics, keyboard-complete |

### 2.2 Three more worth carrying

- **The section is self-referential and never marks itself.** Dhan's page is titled *Profile*, the rail is titled *Manage Account*, and inside the rail sits *Profile & Account Details* — the page you are on — **with no active state**. Users click it and nothing appears to happen. → **PR-02**.
- **A settings value is displayed but not settable.** Paytm shows *"Frequency for Receiving Unused Funds: Quarterly"* with helper text, no control, and no pointer to where it can be changed. It is a regulated setting a customer may well want to switch. → **PR-38**.
- **Profile home does the work of a dashboard.** Paytm's profile page fires **142 network requests** to display about fifteen static fields, fetching watchlist, holdings, funds summary, MTF scrips and market movers, several of them two or three times. Dhan pins two rows of market ticker (~12% of viewport) to a page with zero market context, and duplicates the ticker ~10× in the DOM so a screen reader reads the index list ten times before reaching the greeting. → **PR-46, PR-47**.

### 2.3 Deliberately not adopted

- **Paytm's split account area** — `/stocks/*` and `/mutual-funds/*` are separate applications with different headers, different sidebars (20 items vs 3 vs 6), different fonts, two live versions of the same readiness API, and Auto Pay existing twice under different URL spellings. There is no single "my account". Thinq has one account area; **DP-1** fixes its name once.
- **Dhan's "Personal Statistics"** — an unexplained tile with an emoji, in a different container from everything around it, that does not say what statistics. A label nobody can act on is not a feature.

### 2.4 Statements & reports — Dhan, Paytm Money and INDmoney, 15 Aug 2026  **[NEW v1.3.0]**

A second round of teardowns, this time on one surface across three brokers. It is
the surface with the widest quality spread in the market and the one carrying the
most statutory weight, so it gets its own findings table. These become **§7.10a**.

| # | Observed | Where | Becomes |
| :--- | :--- | :--- | :--- |
| **R-1** | **Five "reports" are the same trade book with different titles.** DOM inspection returns byte-identical headers for Contract Note, Daily Margin Statement, P&L Statement and Tax Report: `Date, Name, B/S, Order Type, Qty/Lot, Order Value, Price`. No brokerage, no STT, no GST, no stamp duty, no net amount, no margin required/available/shortfall, no realised/unrealised split. Three of the four are regulated artefacts with legally defined contents. | Dhan S1-1 | **PR-107** — a report renders its own defined contents or is not offered |
| **R-2** | **The default period is 7 days**, so on an account with two years of history every one of eight reports opened empty, behind a confident illustrated empty state reading *"No Traded History"* rather than *"nothing in this window"*. | Dhan S1-3 | **PR-110, PR-111** — default to the period the job implies; empty states name the window |
| **R-3** | **Contract notes are one email per trading day.** A financial year is ~120 individual button clicks producing ~120 separate emails. No range, no ZIP, no PDF anywhere in the flow. INDmoney is worse: single-date-only, and the server config shows it is a data-model constraint rather than a UI omission. | Dhan S1-4 · IND §3 | **PR-114** — statutory documents are retrievable for a range, in one action |
| **R-4** | **Nothing renders on screen.** All eleven Paytm statement types are fire-and-forget: no preview, no table, no result state. ~800 × 550 px of empty card sits below the filter row. The page's model is *"tell us what you want and we'll mail it"*; the user's is *"show me my trades"*. | Paytm §4.1 | **PR-112** — the report renders in the product |
| **R-5** | **Three action verbs — Email, Download, View — across eleven types with no visible logic.** Four of eleven support Download; the two most needed (Trade Book, Ledger) are email-only. | Paytm §4.2 | **PR-113** — one delivery model, the same on every report |
| **R-6** | **Email-only with no job state.** No confirmation of which address, no "sent" state surviving reload, no resend, no delivery status, no history, no in-app fallback if it bounces. Async generation is defensible; fire-and-forget is not. | IND §3 · Paytm §4.6 | **PR-115, PR-116** — a request has a state and a home |
| **R-7** | **Lookback depth differs per report and is stated nowhere.** Paytm: Trade Book 1 year, Tax P&L 5 years — discoverable only by noticing 2024 is greyed out. INDmoney: Tax P&L from FY 22-23, Dividend from FY 23-24, Holding Statement two years, no explanation, no older-periods route. | Paytm §4.5 · IND §3 | **PR-109** — each report states its own depth, on the report |
| **R-8** | **The date picker has no bounds and no validation.** Dhan's year grid offers 2003–2026; an 11-year range was accepted silently; a pre-account range rendered `Opening Balance ₹0.00 / Closing Balance ₹0.00` — a fabricated-looking zero for a period in which the account did not exist. | Dhan S2-6 | **PR-118** — bound to the account, and say so rather than render zeros |
| **R-9** | **The period resets unpredictably when switching reports.** Four reports share a filter component and retain state among themselves; crossing the family boundary silently drops back to a 7-day window and re-triggers the empty state. | Dhan S2-3 | **PR-117** — the period belongs to the question, not the report |
| **R-10** | **No presets, against the calendar that matters.** Indian retail thinks in financial years and quarters. Paytm's date types offer neither, while three other types on the same page are FY-based — the page knows about financial years and does not expose the vocabulary where ranges are used. | Paytm §4.4 | **PR-108** — FY-first, with the presets the job needs |
| **R-11** | **A live product with no reporting behind it.** INDmoney is an MCX member selling commodity F&O with a dedicated scalper mode; the reports hub has no commodity report and the Tax Centre has no commodity tab. Commodity income has no export path. | IND §1 | **PR-119** — every segment Thinq sells is reportable |
| **R-12** | **"View Charges & Brokerage" opens the public rate card.** A logged-in user is shown everyone's rates instead of their own charges — while the actual per-trade charge decomposition already exists, computed and rendered, one surface away in the P&L Calendar, and neither surface knows about the other. | IND §5 | **PR-120** — a charges report shows the customer's charges |
| **R-13** | **The downloads destination is a naming trap.** Paytm's top-nav "Download Center" is not your downloads — it is blank account-opening forms. A user who just pressed Email finds it and finds nothing of theirs. | Paytm §4.7 | **PR-121** — Account documents and Statements are named for what they hold |
| **R-14** | **The destination email is never stated.** For a document containing a full trading history, no screen says which address it goes to. | Paytm §4.8 | **PR-116** — show the masked destination at the point of sending |
| **R-15** | **Four surfaces, four segment taxonomies.** INDmoney's reports hub has none, Tax Centre has LTCG/STCG/F&O/Intraday/Dividends/Interest, P&L Calendar has Delivery/Intraday/MTF/Commodity/F&O/Dividend, Orders has Delivery/Intraday/MTF/SIP/Pledge. MTF exists in two, Commodity in one, Interest in one, Pledge in one. | IND §2 | **PR-122** — one vocabulary, and it is §7.6's |
| **R-16** | **The module is a degraded duplicate of a better product.** Dhan ships Journal — real P&L, FY selectors, CSV/Excel, ~13 report types — one menu row above a legacy shell **with the same name**, and Dhan's own help documentation routes users past the module under review. | Dhan §3 | **DP-14** — one reports surface, or a link, never both |
| **R-17** | **An app bug reported as the user's fault.** An unguarded `.map()` on a null field throws in render; the error boundary says *"Please check your network connection"*. The only escape is Reload, which discards the form. | Paytm §3.3 | **PR-123** — failures are attributed honestly |
| **R-18** | **Zero accessibility scaffolding**, on the surface that holds tax documents: 0 `<label>`, 0 headings, `aria-haspopup="listbox"` with no listbox, arrow keys inert, `outline: none` on the submit button, alt text reading `INFO_TOAST` / `MY_ACCOUNT` / `LINK`. | Paytm §5 | **§9.2**, unchanged and now load-bearing |
| **R-19** | **Ad and analytics beacons on an authenticated reports page**, and the session bearer JWT — carrying email, mobile and user_id — embedded in server-rendered HTML on the page that also renders report-download URLs. | IND §6.3 | **PR-49**, **§9.4** — already required; this is the cost of not doing it |

**One idea worth stealing, unmodified.** INDmoney's Tax Centre offers a period
selector bucketed to the **advance-tax instalment windows** — 1 Apr–15 Jun,
16 Jun–15 Sep, 16 Sep–15 Dec, 16 Dec–15 Mar, 16 Mar–31 Mar — rather than arbitrary
quarters. It is the single best piece of tax-domain design across all three
products, and it costs nothing to adopt. → **PR-108**.

---

## 3. Information architecture

### 3.1 The section is called Profile

Eight published paths in the assistant say `Profile → …`. Two approved onboarding confirmations say *Settings*. They cannot both be right, and the onboarding lines are inside non-dismissible legal confirmations, which makes them the more expensive to be wrong.

**DP-1: the section is named Profile everywhere.** The two onboarding strings change *Settings* → *Profile*. Recorded as **P-1** because it is a change to approved copy in another PRD and needs that owner's action, not this one's.

### 3.2 Groups

Five groups, each a single mental model. Dhan's rail was rejected for mixing five models in one list — identity, developer tooling, security, a product upsell, and an inline preference toggle that flips a setting in place while every neighbour navigates.

```
YOU              Personal & KYC details · Contact details · Nominee
ACCOUNT          Bank accounts · Segments · Demat & trading IDs
PREFERENCES      Settlement cycle · Contract notes & statements · DDPI
                 · Notifications · Language
PRIVACY          Marketing preference · Consent history · Your data (DPDP)
SECURITY         PIN · Passkeys & devices · Sign-in activity · Connected apps
                 · Freeze account · Log out

ACCOUNT SERVICES Account documents · Account closure
```

- **PR-134a** — A **freeze** SHALL sit **inside the Security page**, as a section beneath the PIN and the devices — not as its own destination. A **closure** SHALL sit in **Account services**, the group that also holds Account documents. A freeze is a security control — it is reached in the same state of mind as *change my PIN* and *sign out everywhere*, and the freeze journey's own first screen offers exactly those three as alternatives (PR-112a) — so the heavier answer belongs on the same page as the lighter ones, after them. A closure is a records event: it ends in signed forms, statements the customer is told to download first, and a client master report — all of which live in Account documents. They shared a page, *Freeze or close*, until 17 Aug 2026; a reversible pause and a permanent exit are not one mental model, which is the grouping defect PR-01 exists for. **Consequence:** the published support path `Profile → Account` (Support §10.6b) now names neither destination — see **P-19**.
- **PR-01** — THE SYSTEM SHALL place every item in exactly one group, and SHALL NOT place a control that acts in place (a toggle, a switch) in a list whose other items navigate.
- **PR-02** — THE SYSTEM SHALL render the current destination in an active state, and SHALL NOT list the current page as a navigable item within itself.
- **PR-03** — THE SYSTEM SHALL expose **Log out** in the account area on every viewport. *(Absent entirely from Dhan's account-management page.)*

### 3.3 Profile home

A summary, not a dashboard. It answers *"is anything wrong or unfinished, and where do I go"* and nothing else.

- Who you are: name as per KYC, masked PAN, account status, UCC.
- **Attention band** — zero or more items requiring the customer's action, each with one verb: no nominee on record · a segment de-scoped at onboarding · a bank account pending verification · re-KYC due · a consent awaiting re-acceptance after a version change (TnC §6).
- Group entries, each with a one-line current-state summary rather than a chevron alone.

- **PR-46** — Profile home SHALL NOT fetch trading, market, portfolio or funds data. *(T-9 / Paytm's 142 requests.)*
- **PR-47** — THE SYSTEM SHALL NOT render a market ticker on any Profile surface. Profile is a task page reached because something needs fixing or checking; it carries no market context. *(Dhan §6.)*

---

## 4. Account states

The same section is reachable long before activation, and shows different things. Every surface in §7 declares its behaviour in each state.

| State | Who | Profile shows |
| :--- | :--- | :--- |
| **Prospect** | Registered (mobile + email verified, R2), KYC not started | Contact details (**read-only as built — PR-140a, P-20**), Security (no PIN yet — D-24), Privacy, Log out. Everything else absent, not greyed. |
| **In KYC** | Application in flight, PAN verified | The above, plus **resume the application**, keyed to the same stage code the §18 comms use. Contact change is now Ops-assisted (D-28 zone 2). |
| **Submitted** | e-Signed, awaiting activation | Read-only view of what was submitted, plus application status. No edits — the AOF is a closed record (Onboarding §7). |
| **Activated** | PTT: UCC + BO ID + ≥1 segment active | Everything. |
| **Frozen** | Customer-initiated or dormant | Everything readable; changes that alter the trading account are blocked with the reason and the unfreeze route. |
| **Closure requested** | Closure in progress | Read-only, plus closure status and outstanding items. Withdraw-the-request remains available until the point of no return. |

- **PR-08** — THE SYSTEM SHALL derive the in-KYC resume point from the **same stage code the §18 drop-off communications are keyed to** (K1–K13). *(Support **H39** records the live instance of this defect: an email saying the customer stopped in one place and an assistant saying another. A third surface disagreeing would make it worse.)*
- **PR-09** — WHERE a surface shows both a status and a call to action, THE SYSTEM SHALL ensure they name the same party as the actor. A status meaning *"waiting on Thinq"* SHALL NOT sit beside a CTA meaning *"waiting on you"*. THE SYSTEM SHALL treat a disagreement as a defect, not a copy preference. *(T-6.)*
- **PR-10** — WHERE something is pending, THE SYSTEM SHALL name **what** is pending. A pill reading only *"under progress"* is not a status. *(Dhan §3.)*

---

## 5. Field inventory

What Profile displays, how it is disclosed, and whether the customer can change it. Sources are the onboarding §5 data inventory and §0.9 entity block.

### 5.1 Identity & KYC

| Field | Source | Disclosure | Changeable | Route |
| :--- | :--- | :--- | :--- | :--- |
| Name as per KYC | PAN / CKYC | Full | No | KRA/depository modification (§7.3) |
| Display name | Customer | Full | **Yes** | Inline, with consequence copy (PR-13) |
| PAN | S1 | **Masked, reveal on re-auth** | No | Immutable — it is the identity anchor |
| Date of birth | PAN / CKYC | Masked to year, reveal on re-auth | No | KRA modification |
| Father's name | Aadhaar / CKYC | Full | No | KRA modification |
| Aadhaar | DigiLocker | **Last 4 only — the full number is never stored plain** (Onboarding §5, TnC §256) | No | — |
| Address | DigiLocker / CKYC | Full | Yes | KRA modification (§7.3) |
| Photograph | Selfie capture | Shown | No | Re-KYC only |
| Signature specimen | S5.5 | **Not displayed** | No | See PR-11 |
| CKYC KIN | CERSAI | Masked, reveal | No | — |
| KRA status | KRA | **Fetched and rendered** (PR-25) | n/a | — |

- **PR-11** — THE SYSTEM SHALL NOT render the signature specimen image anywhere in the customer-facing account area. It is a reproducible authentication artefact and displaying it on a screen that gets shared adds risk with no user benefit. *(Neither competitor displays it; recorded so nobody adds it.)*

### 5.2 Contact

| Field | Disclosure | Changeable | Route |
| :--- | :--- | :--- | :--- |
| Mobile | Masked `99•••••939`, reveal | **Yes, by lifecycle zone** | §7.2 |
| Email | Masked `ar•••@gmail.com`, reveal | **Yes, by lifecycle zone** | §7.2 |

Masking pattern follows Support **SP-11** — *recognisable to the customer, not worth reading over their shoulder*.

### 5.3 Trading & demat

| Field | Format | Disclosure | Copy control |
| :--- | :--- | :--- | :--- |
| UCC | As issued | Full | **Yes** |
| Depository | CDSL (fixed) | Full | — |
| DP ID | `12063900` | Full | Yes |
| Demat account (BO ID) | 16 digits, `12063900…` | **Masked, reveal** | Yes, after reveal |
| Segments active | Enumeration (§7.6) | Full | — |
| Account opening date | Date | Full | — |

- **PR-12** — THE SYSTEM SHALL offer a copy control on **every** identifier a customer is legitimately asked to quote — UCC, DP ID, BO ID, CKYC, client code, request references — and SHALL apply it consistently. *(Dhan offers copy on Client ID and Demat ID but not on PAN or UCC; Paytm offers it on none, including the BOID, which is the field most often copied.)*

### 5.4 Nominee — third-party data

Onboarding §8 captures, per nominee: name, relationship (16 SEBI relations), identity proof as **Aadhaar last-4 / PAN / Driving Licence — self-declared, not authenticated**, mobile, email, DOB, share %, and address (or *"same as mine"*).

Thinq holds only the last 4 of a nominee's Aadhaar, so Dhan's specific defect — printing a full 12-digit nominee Aadhaar — cannot occur here. The **principle** still binds, and applies to the fields Thinq does hold in full:

- **PR-14** — THE SYSTEM SHALL mask nominee mobile, email and address by default, to the same standard as the account holder's own. The nominee is not the logged-in user, never saw this interface, and consented to nothing in it. A person's data does not become less sensitive because it is displayed on somebody else's screen.
- **PR-15** — THE SYSTEM SHALL render the nominee's identity proof as **type + last 4** (`Aadhaar •••• 3121`) and SHALL label it as self-declared, so a customer does not read it as verified.

---

## 6. Disclosure & masking policy

### 6.1 One model

The market failure in §2 T-1 is not that these apps mask too little. It is that **each has three unstated models running at once** — permanently masked, masked-with-reveal, and fully exposed — assigned by no visible rule, which is how Dhan ends up protecting a PAN and publishing an Aadhaar.

**DP-2: default masked, single reveal control, one re-authentication tier.**

| Tier | Fields | Default | Reveal |
| :--- | :--- | :--- | :--- |
| **A — Regulated identifiers** | PAN, BO ID, CKYC KIN, DOB, bank account number | Masked | Reveal requires **PIN re-authentication**; auto-remasks on navigation and after 60s |
| **B — Contact & third-party** | Mobile, email, nominee mobile/email/address | Masked | Single tap, no re-auth |
| **C — Public within the account** | Name, UCC, DP ID, depository, segments, dates | Full | — |
| **Never** | Aadhaar beyond last-4, signature image, full nominee Aadhaar | Not held / not rendered | — |

- **PR-30** — THE SYSTEM SHALL use one reveal control, one masking format per data type, and one re-auth tier across every Profile surface. A field's tier SHALL NOT vary by which screen it appears on.
- **PR-31** — **THE SYSTEM SHALL mask server-side.** A masked field SHALL be delivered masked; the unmasked value SHALL be obtainable only through a distinct, re-authenticated request, and every such request SHALL be logged with the field, the actor and the timestamp. *(T-2. Paytm's UI masks a PAN that its own API has already handed to the browser in full — which is not masking, it is a render-layer courtesy, and it is invisible to a customer who assumes otherwise.)*
- **PR-32** — THE SYSTEM SHALL apply the same policy to every export, download and printed view. *(Support **H44** records this exact gap: the exported support transcript carries the client code and full name in plain text, defensible because it is the customer's own download, but a decision nobody had taken.)*

### 6.2 Why re-auth on tier A

Profile is a page people open **on a call with support** and while screen-sharing. That is the realistic threat, not a stolen session. A reveal that costs four digits is proportionate to it; a reveal that costs nothing is decoration.

### 6.3 Locked fields

- **PR-05** — WHERE a field cannot be edited, THE SYSTEM SHALL show either **the route to change it** or **the reason it cannot change**, in the same place. THE SYSTEM SHALL NOT render a padlock, a `disabled` state or an immutable value with neither. *(T-3.)*
- **PR-04** — THE SYSTEM SHALL render read-only values as label/value text and editable values as fields, and SHALL NOT use a disabled input to display a read-only value. *(T-5: Dhan uses two visual languages for the same data type twelve pixels apart.)*

### 6.4 No promotion

- **PR-49** — THE SYSTEM SHALL NOT load advertising, remarketing or conversion-tracking tags on any authenticated account page, and SHALL NOT transmit the account-area path to an advertising partner. *(T-8. Remarketing on an authenticated broker account area is a different risk category from the same tags on marketing pages, and it should be a decision rather than an inherited default.)*
- **PR-50** — THE SYSTEM SHALL NOT place product promotion, `New` badges or cross-sell in the account rail. *(Dhan §4.1 — acquisition work in the one place customers come to when something is wrong with their account.)*

---

## 7. Functional requirements per surface

### 7.1 Profile home

Covered by PR-01 … PR-03, PR-46, PR-47 above.

### 7.2 Contact details — the auth engine's named recipient

**This surface is the flow Registration D-28 hands off to and marks Out of Scope.** REC-M12 tells the customer *"your [email/mobile] is part of your account record, so it's updated from your account profile rather than here"* and deliberately states neither the reason nor the steps, *"which belong to that flow"*. This is that flow.

Three lifecycle zones, per D-28. The gate is **PAN verification**, not KYC completion.

| Zone | Who owns the change | Profile's job |
| :--- | :--- | :--- |
| **Pre-PAN** | The auth engine, self-service (Branches B1/E1) | ~~Deep-link into it.~~ **As built 17 Aug 2026: neither row offers anything.** Both read *Not available*, under one line — *Your mobile number and email address can't be changed until your account is opened — they are what your application is running against*. PR-140a |
| **PAN verified, KYC pending** | **Operations**, under assisted verification | ~~Explain, collect the request, hand off, show status.~~ **As built: the same refusal.** The assisted route (*Lost access to your old number or email?*) renders only in the KYC-complete zone. See **P-20** |
| **KYC complete** | **This flow** — KRA + depository modification | Own it end to end (below) |

- **PR-16** — THE SYSTEM SHALL resolve the zone before presenting any control, and SHALL NOT present a self-service edit in a zone that does not permit one.
- **PR-17** — In the KYC-complete zone, THE SYSTEM SHALL state plainly, before the customer starts, that the registered mobile and email are part of the KYC record held at the KRA and propagated to the depository, that the change requires identity verification, and that it takes effect at Thinq before it reflects downstream.
- **PR-18** — THE SYSTEM SHALL require **two factors** for any registered-contact change, per SEBI's credential-change rule and Registration §918. The OTP to the *new* address is **address verification, not an authentication factor** (D-29), and SHALL NOT be counted as one.
- **PR-19** — WHEN a contact change completes, THE SYSTEM SHALL send the out-of-band security notification **to the other, unchanged channel** — mobile change notified by email, email change notified by mobile (REC-M06 / REC-M18 pattern) — carrying no action link.
- **PR-20** — THE SYSTEM SHALL show the change's state until it is complete downstream: submitted → verified → updated at Thinq → reflected at the depository. A change that is done at Thinq and pending at CDSL is two facts and SHALL be shown as two.
- **PR-21** — WHERE the customer has **lost access to the old channel**, THE SYSTEM SHALL route to assisted support rather than offering a self-service path. *(Registration records lost-access as a separate open case; Profile SHALL NOT invent a path the auth engine deliberately left open.)*
- **PR-22** — THE SYSTEM SHALL NOT commit a completion timeline in this flow. *(Support **H34** — five published answers quote timelines against a standing owner rule that none is committed. `HC-ACC-01` is one of the five.)*

⚠️ **`HC-ACC-01` overstates this.** The published answer reads *"Go to `Profile → Contact Details` and update your mobile number or email address"*, which describes a self-service edit. The change is a two-factor, identity-verified, KRA-propagated modification. See **P-3**.

### 7.3 Personal & KYC details

Read-only, with routes. Address and name changes are KRA/depository modifications and follow the §7.2 pattern: explain, verify, hand off, show state.

- **PR-23** — THE SYSTEM SHALL group these as label/value text under one heading, with each field's change route stated once (PR-05).
- **PR-24** — THE SYSTEM SHALL surface **re-KYC** when it falls due, with the period and what completing it requires. *(Support **H35** records periodic re-KYC as one of seven mandated disclosures absent from all 158 published answers. Profile is where a customer would look for it.)*
- **PR-25** — THE SYSTEM SHALL **fetch and render** the KRA status. THE SYSTEM SHALL NOT present a link that sends the customer to a third-party site to discover their own KYC status. *(T-7. Thinq holds the PAN and already calls the KRA API at onboarding step 14; the customer clicking out to find the answer is a support ticket the product generated on purpose.)*
- **PR-26** — WHERE the status needs explaining, THE SYSTEM SHALL explain it in **one line**. *(Dhan's KRA block runs three lines of explanation longer than everything it explains, and still does not give the answer.)*

### 7.4 Bank accounts — `HC-ACC-02`

- **PR-27** — THE SYSTEM SHALL list every linked account with bank, masked account number, IFSC, type and **which one is primary**, and SHALL state that the primary account is used for settlements and withdrawals.
- **PR-28** — Adding an account SHALL run the same verification the onboarding journey runs — penny-drop / reverse-penny-drop / UPI, plus the **PAN ↔ holder-name match** — and SHALL NOT accept an unverified account. A new account SHALL be unusable for withdrawal until verification clears.
- **PR-29** — THE SYSTEM SHALL show a pending or failed verification with its reason and next step, and SHALL NOT leave a half-added account in the list with no state.
- **PR-33** — Changing the primary account SHALL state the effect on in-flight settlements before it is confirmed.

#### 7.4a Adding an account — as built 17 Aug 2026  **[NEW v1.11.0]**

Rebuilt to lead with UPI, and to behave the same way the onboarding journey does
when a name does not match. The two journeys now share their failure rules, their
loader and their wording, because a customer meeting this screen for the second
time in their life should not have to learn it again.

| # | Screen | Carries |
| :--- | :--- | :--- |
| 1 | **Scan this with your UPI app** | The code, *₹1 will be temporarily debited and refunded within 1–3 days*, the name-match rule, a **waiting** state, and *Cannot scan? Enter your account number and IFSC* |
| 2 | **Your account details** *(typed route only)* | Account number, confirm, IFSC. Reached by the link, not by a choice |
| 3 | **Verifying your bank account…** | The KYC loader, escalating to *Confirming details with your bank…* |
| 4 | **Name doesn't match** *(on failure)* | Both names side by side, the attempt count on the typed route, and the route-appropriate way back |
| 5 | **Bank account added successfully** *(typed route only)* | Masked account, IFSC, method, name check, and the *Being verified* promise |

- **PR-153a** — Adding an account SHALL **lead with the scan**, and typed entry SHALL be a link beneath it rather than a peer choice. The account number and the IFSC are the two things a customer least reliably has to hand, and mistyping either is the one error here that **validation cannot catch** — a well-formed IFSC with a plausible account number still fails days later at the name check. Scanning removes both fields.
- **PR-154a** — THE SYSTEM SHALL NOT ask how to verify. The scanned route **is** a UPI ₹1 debit-and-reverse; the typed route **is** a ₹1 credit. A method question after the route is chosen asks the customer to re-state what they have already said.
- **PR-155a** — On leaving the IFSC field, THE SYSTEM SHALL echo back **the bank and the branch** the code resolves to. This is the only check available to a customer before the ₹1 moves. An unrecognised code SHALL NOT read as a confirmation — *We will confirm the branch when the ₹1 clears*.
- **PR-156a** — A name mismatch SHALL show **both names side by side** — bank account holder and name on PAN. *Verification failed* without them leaves the customer guessing which of the two is wrong. The **Name on PAN** value SHALL be read from the record (PR-77), never from a fixture.
- **PR-157a** — Typed entry SHALL be limited to **3 attempts**, after which only UPI is offered: *Limit reached. You've reached the maximum of 3 attempts to verify manually. Please verify instantly with UPI.* A **UPI** mismatch SHALL NOT count against that ceiling — the limit is on manual entry, as it is in onboarding.
- **PR-158a** — A failed attempt SHALL **keep what the customer typed**. Retyping eleven characters to fix one of them is friction we added, not verification we need.
- **PR-159a** — The scanned route SHALL have **no receipt screen**. An approved payment lands on Bank accounts with the row already reading *Being verified*, so a receipt would restate the page behind it. Both routes SHALL confirm in the same words: **Bank account added successfully**.
- **PR-160a** — The verification screen SHALL be the **onboarding journey's loader**, with its escalating message. Two products' worth of visual language for one operation is the defect DP-1 exists for at the naming level.

- **PR-161a** — The **Add funds** card SHALL name the account the money comes from, and that name SHALL be the control that changes it — an in-place dropdown of **verified accounts only** (PR-28), with **Add a bank account** as its last item. A dialog for a two-item list is a heavier promise than the action deserves.

#### 7.4b Bank account communications  **[NEW v1.14.0]**

Bank accounts sent **nothing** until now — no receipt, no completion, no failure
notice — and it is the one journey in Profile where that silence has a cost the
customer can measure. Verification resolves **after the session ends**: a ₹1
debit-and-reverse or a ₹1 credit, plus the PAN ↔ holder-name match. Until it
clears, PR-28 makes the account **unusable for withdrawal**. So a customer added
an account, read *Being verified*, closed the app, and was never told either that
it worked or that their next withdrawal would be refused.

Owner direction, 17 Aug 2026:

| Event | Fires at | Channel |
| :--- | :--- | :--- |
| `BANK_ACCOUNT_VERIFIED` | The ₹1 **and** the name match both clear | **Email + WhatsApp** |
| `BANK_ACCOUNT_REJECTED` | A terminal refusal resolved **after** the session ended | **WhatsApp** |
| `BANK_PRIMARY_CHANGED` | The settlement and withdrawal destination changes | **Email** |
| `BANK_ACCOUNT_REMOVED` | An account is removed | **Email** ⚠ **no control exists — P-28** |

- **PR-183a** — WHEN verification clears, THE SYSTEM SHALL send `BANK_ACCOUNT_VERIFIED`
  on **email and WhatsApp**, and it SHALL state that the account **can now be used
  for withdrawals**. That sentence is the point of the message: PR-28 blocks
  withdrawal until this moment and nothing else ever announces that the block has
  lifted. It SHALL name the masked account and deep-link to Bank accounts.
- **PR-184a** — This is also **the security notice for the addition**, and it is
  sufficient as one. An account being added is a takeover step — it is where
  withdrawals go — but nothing can leave the account before verification clears
  (PR-28), so this message arrives at the **first moment there is anything to
  warn about**. It SHALL therefore carry, in plain text and with no action link,
  the route to take if the customer did not add this account. *(This is why no
  separate notice fires at submission; the decision is deliberate, not an
  omission.)*
- **PR-185a** — WHERE a verification **fails terminally after the session has
  ended**, THE SYSTEM SHALL send `BANK_ACCOUNT_REJECTED` on **WhatsApp**, carrying
  the reason, **both names** where it is a mismatch (PR-156a), and the route that
  remains — UPI only, where the three typed attempts are exhausted (PR-157a). It
  SHALL state that **nothing was added and no money moved**, which is the
  reassurance §7.14 records as having been removed from the limit screen and which
  nothing else in the journey now supplies.
- **PR-186a** — THE SYSTEM SHALL NOT send a message for a failure **the customer is
  looking at**. A UPI mismatch resolves on screen in seconds and typed entry allows
  three attempts; one message per attempt is three messages for one intent. The
  trigger is a terminal outcome landing **outside** a live session, never a retry
  inside one.
- **PR-187a** — WHERE the customer is **not reachable on WhatsApp**, PR-171a's email
  fallback applies to `BANK_ACCOUNT_REJECTED` in full. WhatsApp-only is a channel
  preference, not a decision to leave a population unnotified — and this is the one
  message in the set that **requires the customer to act**.
- **PR-188a** — WHEN the primary account changes, THE SYSTEM SHALL send
  `BANK_PRIMARY_CHANGED` by **email**, naming the account that now receives
  settlements and withdrawals and restating the effect on in-flight settlements
  (PR-33), **with no action link**. A redirected payout destination is a stronger
  takeover signal than a device revocation, which already fires an out-of-band
  notice (PR-64).
- **PR-189a** — WHEN an account is removed, THE SYSTEM SHALL send
  `BANK_ACCOUNT_REMOVED` by **email**, naming the masked account and, where the one
  removed was the primary, **which account is primary now**. ⚠ **This requirement
  cannot fire in the current build: there is no removal control.** See **P-28**.
- These are **Utility** communications under §18.0, SHALL NOT be gated on any
  preference (PR-55), and SHALL NOT be dropped by the pooled frequency cap.

⚠️ **Removal is missing, and the three-account cap is what makes it missing rather
than merely absent.** AT-P-64 refuses a fourth account even when one of the three
is only *verifying*, so a customer who mistyped an account or closed that bank has
**no way to free a slot** — and P-18 already records that the cap itself is
asserted nowhere but the build. The constraints on a removal journey follow from
requirements that already exist and are stated here so the journey cannot be
designed against them: the **primary account SHALL NOT be removable while it is
the primary** (PR-27 — it is the settlement destination), the **last verified
account SHALL NOT be removable** (PR-161a — Add funds needs one), and removal
SHALL be refused while a **closure or a settlement is in flight** (§7.14a). The
screens are not specified here. **P-28.**

### 7.5 Nominee — `HC-ACC-03`

The destination of the onboarding §8 opt-out promise. Three entry states: **none on record** (opted out), **one or more on record**, **change in progress**.

- **PR-34** — WHERE the customer opted out at onboarding, THE SYSTEM SHALL show that an **opt-out declaration is on record**, with its date and version, and SHALL offer adding a nominee. THE SYSTEM SHALL NOT display this as "no nominee added" — the customer signed a declaration, and the record should say so.
- **PR-35** — THE SYSTEM SHALL capture the same fields, the same 16 SEBI relations, the same 100% allocation rule and the same guardian block (guardian ≥ 18) as onboarding §8, so the two surfaces cannot diverge.
- **PR-36** — **A nominee added after activation requires its own Aadhaar e-Sign** (Onboarding §8). THE SYSTEM SHALL tell the customer this before they begin, and SHALL NOT present nomination as an inline edit.
- **PR-37** — THE SYSTEM SHALL state that **nomination is mandatory or an explicit opt-out is required** — it is not optional. *(Support **H35** calls this the sharpest of the seven absent disclosures: `HC-ACC-03` explains how to add a nominee and never says you must nominate or file an opt-out.)*
- **PR-13a** — THE SYSTEM SHALL support more than one nominee with allocations totalling exactly 100%, and SHALL show the split. *(Dhan shows "Share of Nomination 100%" with no add-nominee affordance anywhere — a percentage field that can only ever read 100 is a field that was built for a feature that was not.)*

⚠️ **Data-binding check.** Dhan's nominee DOB rendered identically to the account holder's DOB two cards above — suspected binding of the holder's value into the nominee row. **AT-P-14** exists to catch this class before it ships.

- **PR-152a** — The two nomination rules — *submitted only once, review before you submit* and *up to 3 nominees, shares totalling exactly 100%, all named in one submission* — SHALL appear **only where a nomination can still be made**: no nominee on record and no request in flight. Once anyone is on record the submission has happened, and repeating either rule only tells a customer what they can no longer do.

### 7.6 Segments — `HC-ACC-04`, `HC-ACC-06`

The destination of the onboarding §7 de-scope promise, and the surface most at risk of being built wrong, because the published copy calls it a switch.

**Adding a derivative segment is a journey, not a toggle.** Onboarding §7 is unambiguous and the constraints are non-negotiable:

- income proof clearing **both** the amount threshold **and** the PAN ↔ document-holder-name match;
- its **own segment-specific form**, carrying its own income declaration and its own risk disclosures — F&O and Commodity carry disclosures the equity AOF does not;
- its **own e-Sign** of that form;
- **the KYC AOF is never re-opened.** *"The AOF signed at step 11 is a closed legal record of what was declared and established at that time; a segment added months later was not."* Earlier drafts called this an "AOF addendum", which *"invited exactly the wrong build: amending an executed document."*

Requirements:

- **PR-39** — THE SYSTEM SHALL present segment activation as a multi-step journey with its stages named up front, and SHALL NOT render it as a switch that can be flipped. *(A toggle that silently opens a document-upload-and-e-Sign flow is a lie about the cost of the action.)*
- **PR-40** — THE SYSTEM SHALL show, per segment: **active** · **not active** · **in progress** (with the stage) · **de-scoped at account opening** (with `drop_reason`: `descoped` / `proof_rejected` / `proof_pending`).
- **PR-41** — WHERE a segment was de-scoped at onboarding, THE SYSTEM SHALL say so and offer the activation journey. This is the exact population the §7 confirmation made a promise to.
- **PR-42** — THE SYSTEM SHALL NOT re-open, amend or re-execute the KYC AOF. The activation journey SHALL produce a separate versioned artefact with its own e-Sign, linked to the account and standing on its own.
- **PR-43** — Equity SHALL be shown as **mandatory and not deselectable** (Onboarding §7).
- **PR-44** — Deactivation SHALL require open positions in that segment to be closed first, SHALL say so before the customer starts, and SHALL name the open positions.
- **PR-45** — THE SYSTEM SHALL distinguish **Thinq approving the segment** from **the exchange enabling it**, and SHALL show both. *(`HC-ACC-05` exists solely because customers hit this gap: "I activated a segment but still can't trade in it.")*
- **PR-45a** — THE SYSTEM SHALL NOT name **MCX** as the commodity venue. There is no MCX membership (Onboarding §0.9); the venue is an NSE or BSE commodity-derivatives segment, pending **C54**.

⚠️ **`HC-ACC-04` and `HC-ACC-06` are optimistic.** `HC-SEG-04` reads *"enable or disable F&O and Commodity anytime from your account settings"* and `HC-ACC-04` commits *"activation usually completes within 1–2 business days"* — against the standing no-timeline rule (**H34**). Neither mentions the separate form or the second e-Sign. See **P-3**.

### 7.7 Preferences

| Preference | Source | Requirement |
| :--- | :--- | :--- |
| **Running account settlement cycle** | TnC **T-RAS** (Opt, monthly/quarterly) | **PR-38** — THE SYSTEM SHALL make the cycle **changeable**, not merely displayed. *(Paytm displays "Quarterly" with no control and no pointer — §2.2.)* THE SYSTEM SHALL state the next settlement date. **Blocked on P-5:** Support **H31** flags that `HC-FUND-10` describes the cycle as a rolling 90/30-day clock while SEBI's is understood to be **calendar-aligned** — first Friday of the quarter or month. A customer shown the wrong settlement date has a documented grievance. |
| **Contract notes & statements by email** | TnC **T-ECN** | **PR-51** — THE SYSTEM SHALL show the e-CN election, and SHALL state the **right to switch to physical at any time** together with any physical-copy charge disclosed in the Tariff. *(T-ECN requires the right to be stated; TnC Appendix J: "If your email stops working, tell us straight away. A contract note that bounces is still a contract note we were required to deliver.")* |
| **DDPI** | Onboarding §9 | **PR-52** — WHERE DDPI was not opted at onboarding, THE SYSTEM SHALL offer later activation and SHALL disclose the **one-time charge (₹150, GST included)** before the customer commits, since it is free at onboarding and chargeable afterwards. THE SYSTEM SHALL show the current election (DDPI or e-DIS) and what each means for selling. **v1.3.0:** the charge is disclosed in the confirmation that opens the journey (§7.13, DP-9), the activation is e-Signed and produces a receipt (§7.12), and it is tracked to the depository like any other registered change (§7.11a, `DDPI_*`). |
| **Notifications** | — | **PR-53** — THE SYSTEM SHALL let the customer manage notification channels **for non-mandatory communications only**, and SHALL state which communications cannot be turned off. |
| **Language** | Support §10.7 | **PR-54** — THE SYSTEM SHALL expose the English/Hindi choice already built for the help centre, and SHALL persist it across surfaces. |

**Settlement cycle, as built 17 Aug 2026.** One card: a **Your cycle** row carrying
the next date and **Change**, and one accordion, *Every settlement date this
financial year*, holding the quarterly and monthly calendars, the 30-day
override (*Your entire balance is settled to your bank account on the next
**monthly** settlement date* · `Overrides your cycle`), the exchange-calendar
note, and the T+1 distinction. The change journey is three steps: choose,
*What changes*, confirm.

- **PR-146a** — The 30-day inactivity override SHALL be stated in the **same words** on the surface and inside the journey. They drifted once; a rule that reads differently in two places is two rules to a customer.
- **PR-147a** — The card SHALL NOT restate what a control already says. *"It is your choice and you can change it whenever you like"* was removed because **Change** says it; *"whichever cycle you are on"* because the `Overrides your cycle` pill says it.

⚠️ **Six statements were removed from settlement on 17 Aug 2026 and are recorded
in §7.14**: the 225% retention ceiling and its worked example, the
pledged-securities ordering, the never-a-book-entry statement, the running-account
authorisation row (voluntary, withdrawable without notice), the five-working-day
retention statement with its 30-working-day dispute window, and every statement
that the cycle choice is reversible. The behaviour is unchanged in all six cases;
only the disclosure is gone.

- **PR-55** — THE SYSTEM SHALL NOT offer a control that suppresses a **Utility** communication. Onboarding §18.0 classifies every drop-off, lifecycle and ops message as Utility precisely so that none can be gated on a preference; a notification screen that appears to switch off a contract note or an activation notice is the same defect from the other end.

### 7.8 Privacy & consents

- **PR-56** — THE SYSTEM SHALL surface the **marketing-consent withdrawal control prominently** in the account area — reachable from Profile home in one step — and it SHALL take effect in **under 60 seconds** across all channels. *(TnC §8 and D-7. This control is the whole of the **T21** mitigation; burying it removes the mitigation.)*
- **PR-57** — WHEN marketing consent is withdrawn, THE SYSTEM SHALL confirm plainly that **account communications continue**: *"Messages about your own account are not marketing, and are always sent"* (TnC Appendix). A customer who fears losing their contract notes will not withdraw, which defeats the control.
- **PR-58** — THE SYSTEM SHALL expose the **consent history** — artefact, version, timestamp, status — to the customer. *(TnC §6 requires this view "to Compliance and to the user on request"; Profile is the on-request surface.)*
- **PR-59** — WHERE an artefact version has changed materially and re-consent is pending, THE SYSTEM SHALL surface it in the Profile home attention band, and SHALL NOT treat the prior acceptance as covering the new version.
- **PR-60** — WHERE the customer attempts to withdraw a consent that is **legally required to hold the account** (C-PROC), THE SYSTEM SHALL explain that withdrawal means account closure and SHALL route to the closure flow rather than silently continuing (TnC §8).
- **PR-61** — THE SYSTEM SHALL provide the **DPDP data-subject rights** — access, correction, erasure, grievance, consent manager — from this surface. ⚠️ **Blocked on TnC T14 (P0):** the Privacy Policy does not exist, and *"it owns the DPDP rights — access, correction, erasure, grievance, consent-manager — none of which appear in any Thinq document today."* Profile cannot invent the policy; it can only be the place the rights are exercised once it exists. **P-2.**
- **PR-62** — THE SYSTEM SHALL name the **grievance officer** and the escalation route (SCORES / ODR), and SHALL link the **Investor Charter** and monthly complaints data. *(Support **H35** — prescribed-format artefacts absent from all 158 answers.)*

### 7.9 Security

Profile **presents** the security surface; the auth engine **owns** every mechanism. Nothing here re-specifies a factor.

- **PR-63** — `Profile → Security → Change PIN` SHALL exist as a destination and SHALL route into the auth engine's flow (`HC-SEC-03` publishes this exact path).
- **PR-64** — THE SYSTEM SHALL list **enrolled passkeys and devices**, with last-used, and SHALL allow revocation. Revocation SHALL fire the out-of-band notification (REC-M09).
- **PR-65** — THE SYSTEM SHALL show **sign-in activity** — time, approximate location, device, outcome — for a stated recent window. *(Absent from both competitors; Dhan has "Connected Applications" but no session history.)*
- **PR-66** — THE SYSTEM SHALL list **connected applications** and third-party API access with the scope granted, and SHALL allow revocation.
- **PR-148a** — Security SHALL hold the **freeze** as a section of the page, beneath the PIN and the devices — not as its own destination (PR-134a). The freeze journey's own first screen offers a PIN change, disabling biometric and logging out everywhere as the alternatives to freezing, so the heavier answer belongs after the lighter ones on the same page.
- **PR-149a** — **Passkeys & devices** SHALL be an accordion, **closed by default**, with the count on the summary (*3 signed in*). The list is audited occasionally, not a control anyone arrives for, and the count answers the usual question without opening it.
- **PR-150a** — THE SYSTEM SHALL offer **Log out of all devices**, distinct from per-device revocation, because the per-row control cannot answer the case the customer has — a device they no longer hold, or one they do not recognise. It SHALL fire the same out-of-band notice a single revocation does (PR-64), and SHALL survive every lock in §7.14a.
- **PR-67** — Security SHALL be a **top-level group**. *(Paytm buries Change Password, Change PIN, TOTP and Connected Apps at the bottom of a ~20-item sidebar, under a Settings group that also holds Dark Mode and Sticky Order, and **below the Brokerage and SIP calculators**.)*

### 7.10 Documents — `HC-REP-03`

- **PR-68** — THE SYSTEM SHALL offer re-issue of the **Client Master Report**, free of charge, as a **download** — the same CMR sourced from the TechExcel back office that onboarding §18 attaches to the PTT notice. ~~Delivered to the registered email.~~ **Owner direction, 17 Aug 2026: download only — no email delivery.** This aligns the CMR with every other document on this surface, where only **Download** survived the 16 Aug simplification (§7.14, PR-112 … PR-116), and it means **`CMR_REQUESTED` is not a communication Profile fires** — the §18.3 row has no trigger on this surface. The CMR still travels as an **attachment on `PTT_CONFIRMED`** at activation; that is onboarding's row and is untouched. Recorded as **DP-19**.
- **PR-69** — THE SYSTEM SHALL offer the **signed account opening form** and the **consent records**, and SHALL show for each document what it is, when it was generated and its version.
- **PR-70** — Before a download, THE SYSTEM SHALL state the file type and size. **Built and withdrawn the same day, 17 Aug 2026** — the rows carried `PDF · 214 KB · CMR v1 · 22 April 2026` and now carry `CMR v1 · 22 April 2026`. See §7.14; **AT-P-20 fails**. *(Paytm's account-opening-form link fires a download with none of it disclosed.)*
- **PR-135a** — Account documents SHALL hold the **forms** — the client master report, the account opening form, and everything the customer has e-Signed since opening (a mobile or email change form, a nomination form, a segment activation form, a DDPI form, a closure form). One flat list, no group headings; each row states what the document is and when it was generated, and signed forms are marked *e-Signed with Aadhaar*. **Consents and agreements are not filed here** — owner direction, 17 Aug 2026 — so §7.8 on Privacy remains the only place they appear, where they can be read but not downloaded (see §7.14, PR-69).
- **PR-136a** — Period-based documents SHALL NOT be duplicated here. Contract notes, ledgers, P&L and holding statements need a period, a range and a segment, which is what §7.10a is built for; this surface SHALL name the route to it and nothing more. *(This is PR-71 applied to Thinq's own surfaces rather than to a third-party engine.)*
- **PR-137a** — *(Withdrawn 17 Aug 2026.)* The signed-forms group and its empty state were removed the same day they were built. Signed forms still file themselves into the list; a customer with none is told nothing about what would add one. §7.14.
- **PR-151a** — A signed form SHALL carry the date it was signed — *e-Signed on 02 June 2026* — and the two generated records (client master report, account opening form) SHALL carry the date they were generated. No version, no file type, no size: all three were removed on 17 Aug 2026 (§7.14).
- **PR-71** — Where Profile links out to the reports engine (tax P&L, capital gains, contract-note archive), it SHALL link, not reimplement. *(Paytm's Statements page renders the same block of controls four times, with financial-year depth silently differing between them — six years for two types, five for the other two, explained nowhere.)*

### 7.10a Statements & reports  **[NEW v1.3.0]** — `HC-REP-01`, `HC-REP-02`

Derived from three teardowns on 15 Aug 2026 (§2.4). This is the surface a
customer opens at tax time, at loan-application time, and when reconciling a
charge they do not recognise — never casually, and rarely calmly.

**Where DP-7 now stands.** DP-7 said Profile links to the reports engine rather
than reimplementing it, and that stands — but "link, don't reimplement" is not a
licence to ship a stub. Dhan's failure was not that it linked; it was that it kept
a degraded copy **with the same name** one menu row from the real one. **DP-14**
makes the rule explicit: one surface owns statements, and Profile is either that
surface or a link to it, never a second implementation of it.

#### The inventory, and which parts are not optional

Five of these are not product decisions. They are documents Thinq is obliged to
produce, and the obligation is to *produce and deliver* them — self-serve
retrieval is what turns a compliance artefact into something a customer can
actually use when they need it.

| Report | Basis | Periodicity |
| :--- | :--- | :--- |
| **Contract note** | SEBI *Rights & Obligations of Stock Brokers*, cl. 32 | Within **one working day** of the trade |
| **Statement of accounts — funds and securities** | R&O cl. 34 | Exchange-prescribed periodicity |
| **Daily margin statement** | R&O cl. 35 | Daily |
| **Transaction statement (demat)** | SEBI Depositories Master Circular, para 1.8.5 | Quarterly |
| **Statement of holding (demat)** | Depositories Master Circular, para 1.8.6 | Annual. **Electronic free**; physical ≤ ₹25 |
| **Annual Global Statement** | Exchange rules (NSE prescribed format) | Annual |
| Tax P&L · capital gains | Product | FY |
| Trade book | Product | Range |
| Ledger | Product | Range |
| Brokerage & charges, with GST invoice | Product | FY / range |
| Dividends & corporate actions | Product | FY |
| STT certificate | Product | FY |

- **PR-107** — Every report SHALL render **its own defined contents**. A report
  SHALL NOT be shipped as a renamed copy of another. Specifically: a contract note
  SHALL carry order and trade numbers, trade time, brokerage, STT, exchange
  transaction charges, SEBI turnover fee, GST, stamp duty and the net amount
  receivable or payable; a daily margin statement SHALL carry margin required,
  margin available by segment, shortfall and peak margin. *(R-1. A user who opens
  "Tax Report", sees a table and exports nothing has been shown something that
  looks authoritative and is not — which is worse than an empty state.)*
- **PR-108** — Period controls SHALL be **financial-year first**, and SHALL offer
  the presets the job implies: `This FY`, `Last FY`, `This quarter`, `Last 30 days`,
  `Custom`. Tax-facing reports SHALL additionally offer the **advance-tax
  instalment windows** (1 Apr–15 Jun · 16 Jun–15 Sep · 16 Sep–15 Dec ·
  16 Dec–15 Mar · 16 Mar–31 Mar). *(R-10. Indian retail thinks in financial years;
  a rolling-30-day default is a different product's calendar.)*
- **PR-109** — Each report SHALL state **how far back it goes**, on the report.
  Where reports differ in depth, the difference SHALL be visible before the
  customer discovers it as a greyed-out year, and an **older-periods route** SHALL
  be named. *(R-7.)*
- **PR-110** — The default period SHALL be the one the report's job implies —
  current financial year for tax and annual documents, and a stated recent window
  only where that is genuinely the question. THE SYSTEM SHALL NOT default every
  report to a short window and let the customer discover their history that way.
  *(R-2.)*
- **PR-111** — An empty result SHALL **name the window and offer to widen it** —
  *"No transactions between 8 and 15 August 2026. Try this financial year."* — with
  the widening action inline. It SHALL NOT read as though the customer has no
  history. *(R-2. The failure mode here is the most expensive one on the surface:
  a customer who believes their records are gone.)*
- **PR-112** — Where a report can be rendered, THE SYSTEM SHALL **render it in the
  product**, not only deliver it. *(R-4.)*
- **PR-113** — Delivery SHALL be **one model across every report**: view it,
  download it, or have it emailed — the same three, named the same way, wherever
  they apply. Where one does not apply to a report, the reason SHALL be stated.
  *(R-5. Three verbs distributed with no visible logic is worse than one verb,
  because the customer cannot predict what a button will do.)*
- **PR-114** — Statutory documents SHALL be retrievable **for a date range in one
  action**, as a single file or archive. One document per day, one action each, is
  not retrieval. *(R-3. A financial year of contract notes is ~120 documents; any
  design that makes that 120 interactions has not solved the problem.)*
- **PR-115** — Where a report is generated asynchronously, the request SHALL have
  a **state the customer can see** — requested, ready, failed — and the artefact
  SHALL be retrievable from Profile once ready. Email SHALL be a **notification
  that it is ready**, not the only channel. *(R-6.)*
- **PR-116** — Before sending, THE SYSTEM SHALL show the **masked destination
  address**, and after sending SHALL confirm what went where. *(R-14. It is also
  the cheapest way to catch a stale email on file, on the surface where a stale
  address costs the most.)*
- **PR-117** — The selected period SHALL **persist across reports** within the
  session. It is a property of the question the customer is asking, not of the
  report they happen to be looking at. *(R-9.)*
- **PR-118** — Date controls SHALL be bounded by the **account-opening date** and
  today. A request for a period before the account existed SHALL say so, and SHALL
  NOT render zero balances for it. Any server-side range cap SHALL be surfaced
  **before** the query. *(R-8.)*
- **PR-119** — **Every segment Thinq sells SHALL be reportable.** Commodity is in
  scope (Onboarding C54), so commodity SHALL appear in the reports that carry
  segment breakdowns. *(R-11. Selling a segment with no reporting behind it is the
  single most indefensible item in the INDmoney teardown, and it is a trap Thinq
  is currently walking towards.)*
- **PR-120** — A **charges and brokerage report** SHALL show the **customer's own
  charges**, broken down per trade, with a downloadable GST invoice. It SHALL be
  distinct from the Pricing surface (§3.2), which is the published tariff, and
  neither SHALL be labelled in a way that could be mistaken for the other.
  *(R-12.)*
- **PR-121** — **Account documents** (§7.10) and **Statements & reports** SHALL be
  named for what they hold: signed artefacts and account paperwork in the first,
  generated statements and reports in the second. Neither SHALL be named in a way
  that sends a customer looking for a statement into a folder of blank forms.
  *(R-13.)*
- **PR-122** — Segment vocabulary SHALL be **§7.6's, everywhere**: Equity, F&O,
  Commodity. Reports SHALL NOT introduce a second taxonomy. *(R-15.)*
- **PR-123** — A generation or delivery failure SHALL be attributed honestly. THE
  SYSTEM SHALL NOT report an application error as a network problem, and a failed
  request SHALL NOT discard what the customer had already selected. *(R-17.)*
- **PR-124** — This surface SHALL meet §9.2 in full. It carries tax documents; a
  dropdown that cannot be operated from the keyboard is not a minor defect here.
  *(R-18.)*

⚠ **Two things this section cannot decide alone.** Whether Thinq's reports are
generated by the back office (as the CMR is, Onboarding §18) or by a reports
engine, and what the retention and range caps actually are, is unstated in every
Thinq document. §7.10a specifies the customer-facing contract; the generation
path behind it is **P-14**.

### 7.11 Account — freeze and close

- **PR-72** — **Freeze** SHALL block trading while leaving holdings and history intact, SHALL require open positions to be closed first, and SHALL be reversible from the same place (`HC-DMT-08`).
- **PR-73** — **Closure** SHALL show what is outstanding before the request is raised — holdings to sell or transfer out, dues to settle — and SHALL show the state of each.
- **PR-74** — THE SYSTEM SHALL make closure reachable from the account area. *(Absent from both competitors. Dhan's teardown puts it bluntly: its absence "reads as dark-pattern-ish.")*
- **PR-75** — THE SYSTEM SHALL explain **dormancy** — that prolonged inactivity disables trading pending a short re-verification, and that **holdings and money remain safe and withdrawable** (`HC-ACC-07`).
- **PR-76** — A closure request SHALL be withdrawable up to a stated point of no return, and that point SHALL be named. **Not met as built** (§7.14): the point is named only once it has been crossed.
- **PR-133a** — A closure request SHALL NOT be accepted while another request against the account is still open — a contact change, a nomination, a segment activation, a DDPI (Instant Sell) activation, or a bank verification. Each of those ends in a change to a record the closure would then remove, and two instructions racing over one record is how a customer ends up with a closed account carrying an open request against it. The refusal SHALL **name** the request and route to its tracker — a disabled control with no reason is the defect PR-05 exists for. Wording, owner-set 17 Aug 2026: *Some requests are still being processed. Please wait for them to complete before closing your account* — one sentence, whatever is open. The notice therefore does not name what is in progress; the **Track «request»** controls beneath it do, one per open request, and they are what satisfies the naming limb of this requirement. The rule SHALL be enforced at the journey, not only at the control that usually opens it, because TnC §8 routes a C-PROC consent withdrawal straight into closure. The **assisted** route is withdrawn on the same condition; a request taken by a person is still a request.
- **PR-130a** — While a closure request is open, the customer SHALL be able to see where it has reached. The tracker SHALL carry the reference, the stage, and the **downstream** legs only — the exchange deregistration (NSE, BSE) and the depository closure (CDSL) — because every account-level check was cleared before the request could be signed. Stages: `CLO_SUBMITTED` → `CLO_ESIGNED` → `CLO_EXCH_SUBMITTING` → `CLO_EXCH_DONE` → `CLO_DP_SUBMITTING` → `CLO_DP_DONE` → `CLO_COMPLETED`. Additive only.
- **PR-131a** — The two mandated clocks — 3 working days for the trading account, 2 for the demat account — SHALL be stated to the customer as the single outer bound they are waiting on: **1–3 business days**. The individual legs remain the operational commitment; they are not what the customer is asking.
- **PR-132a** — While closure is in progress every other Profile setting is read-only, and the reason SHALL be stated once: *Settings are locked while account closure is in progress*. No route back to the closure page is offered from the banner — the customer arrived at the setting deliberately, and the tracker is one tap away in the rail.

#### 7.11a Freeze — the voluntary-freezing framework  **[NEW v1.4.0]**

SEBI/HO/MIRSD/POD-1/P/CIR/2024/4 (12 Jan 2024), operationalised by NSE/INSP/61529
and BSE Notice 20240408-12 (both 8 Apr 2024), in force **1 July 2024**. A
five-broker teardown (Groww, Angel One, Dhan, Sahi, Fyers) found **no broker
compliant on the timeline-disclosure limb**, and one cross-cutting failure worse
than any individual one.

- **PR-107a** — THE SYSTEM SHALL offer **at least two ways to request a freeze**,
  one of which does not depend on the app, because the app may be the compromised
  surface. THE SYSTEM SHALL name a **dedicated address on the customer-facing
  domain**. *(Sahi publishes `stoptrade@aaritya.com` while every surface a client
  has seen says `sahi.com`; in an emergency they must guess a domain that does
  not itself publish the policy.)*
- **PR-108a** — THE SYSTEM SHALL publish **both limbs of the framework's
  timeline**: within **15 minutes of receiving the request** during trading
  hours, and **before the start of the next trading session** if it arrives
  outside them. Per SEBI/HO/MIRSD/POD-1/P/CIR/2024/4.

  Both limbs, in full, on every surface that states a timeline — and the
  **`stoptrade@` channel is held to the same matrix**, so the second request
  route is not quietly slower than the first (PR-107a).

  *(All five brokers publish an incomplete matrix or none at all. Angel One
  states a flat 15 minutes with no after-hours limb, and its own phone line is
  staffed only 08:30–17:30, so the promise is unachievable by that route out of
  hours. Groww's after-hours language addresses order cancellation rather than
  the freeze itself. The teardown calls the missing matrix "the clearest
  systemic gap" — five of five fail C9's timeline-disclosure limb.)*
- **PR-109a** — THE SYSTEM SHALL acknowledge in **two messages, not one**.
  **Channels, owner-set 17 Aug 2026:** the three that report a *request or a
  restriction* go by **email only**; the one that reports the customer's access
  being **given back** goes by **WhatsApp**, on the same rule as §7.12a's
  completion notices — WhatsApp is the channel for a thing that is now true, and
  the email fallback in **PR-171a** applies to it identically. No freeze
  communication uses SMS. *(This also settles a live contradiction: §18.0 states
  that no §18 communication may use SMS, and the four rows carried "Email + SMS"
  against it.)*

  | Event | Channel |
  | :--- | :--- |
  | `FREEZE_REQUESTED` | Email only |
  | `ACCOUNT_FROZEN` | Email only |
  | `UNFREEZE_REQUESTED` | Email only |
  | `ACCOUNT_UNFROZEN` | **WhatsApp** (email fallback, PR-171a) |

  ⚠️ `ACCOUNT_FROZEN` is the one message in this document a customer may read in
  a panic, and the freeze journey ends by **signing every device out** (PR-123a),
  so email-only means the confirmation is reachable only from an inbox the
  customer may be opening on the device they just suspected. Recorded as
  **P-25**; the owner's direction stands.

  The two messages:
  - **`FREEZE_REQUESTED`**, within 5 minutes of the request being accepted —
    that the request is in hand, **when the freeze takes effect** (within 15
    minutes in trading hours, or before the start of the next trading session),
    and that **holdings and funds are unaffected**. It SHALL NOT state or imply
    the account is already frozen.
  - **`ACCOUNT_FROZEN`**, when the freeze is actually in force — that it is now
    in effect, the **steps to unfreeze**, and the same reassurance.

  The split exists because **the gap between the two is real and disclosed**: a
  request raised after trading hours may not take effect until the next session.
  One message is wrong at one end or the other — a receipt claiming the account
  is frozen before it is would be false, and holding the confirmation back until
  the freeze lands leaves the customer with nothing for hours at the moment they
  are most anxious. *(Only Angel One commits to the unfreeze route in the message
  itself, of five brokers; none sends a receipt at all, and none states the
  reassurance a customer freezing in a panic actually needs.)*

  **Lifting a freeze splits the same way** — **`UNFREEZE_REQUESTED`** when the
  request is accepted, **`ACCOUNT_UNFROZEN`** when trading is actually enabled
  again. The receipt matters most on the **assisted route**, where a person
  verifies identity first and the customer otherwise has no signal that their
  request landed at all. On the self-service route the two may arrive close
  together; that is fine, and they remain distinct events.

  All four belong in Onboarding §18.3 for trigger, channel and SLA, and §18.2a
  for copy, on the same basis as §7.12's e-Sign receipts. These are **Utility**
  communications and SHALL NOT be gated on any preference.
- **PR-110a** — WHERE the customer holds open positions, THE SYSTEM SHALL
  **freeze anyway** and SHALL send the position list with **contract expiry
  information within one hour**. THE SYSTEM SHALL NOT refuse the freeze.
  *(Fyers disqualifies clients with open positions, which defeats the facility in
  the exact scenario it exists for.)*
- **PR-111a** — THE SYSTEM SHALL state that a freeze covers **online access to
  the trading account only**, that the **demat account is separate and stays
  open**, and SHALL name the route to freeze that too. THE SYSTEM SHALL state
  that the freeze is **not a request to mark the UCC inactive**.
  ⚠ **This is the most consequential requirement in this section.** All five
  brokers freeze trading access only; all five are also depositories; **none
  cross-references the depository freeze facility in either direction**. In the
  teardown's words: *"A client who believes they have secured their assets has
  secured only their login."*
- **PR-112a** — Alternatives to a freeze — PIN change, log-out-everywhere,
  demat freeze — SHALL be offered **alongside** the freeze, never as a gate in
  front of it, and the freeze SHALL NOT be visually subordinated to them.
  *(DreamStreet puts three alternatives on an interstitial and labels the
  requested action **"Freeze Account Anyway"** in a pale button beneath them.)*
- **PR-113a** — Unfreeze SHALL be **self-service**, SHALL state its turnaround,
  and any friction beyond "necessary due diligence" SHALL be labelled as Thinq's
  own choice rather than the regulator's. *(Groww requires a booked video-KYC
  call with a physical PAN and publishes no turnaround; DreamStreet takes up to
  72 hours with no in-app route at all; Dhan takes 24. The framework requires
  none of it.)*
- **PR-123a** — Raising a freeze SHALL **end every signed-in session, including
  the one that raised it**, and SHALL say so before the customer commits. A
  freeze is usually raised because a session may not be the customer's own;
  leaving any of them alive defeats it. The freeze SHALL survive the log-out —
  signing back in SHALL NOT lift it.
- **PR-124a** — Lifting a freeze SHALL require a **third factor beyond PIN and
  OTP** — PAN or date of birth, at the customer's choice. PIN and OTP prove
  someone can get into the account, which is exactly what whoever prompted the
  freeze may also be able to do; the third factor is something only the account
  holder knows. It SHALL be checked server-side against the vault (§6.1), never
  against anything the render layer holds, and SHALL NOT report a mismatch until
  a complete value has been entered.
- **PR-125a** — Where the state change is not instantaneous, THE SYSTEM SHALL
  show a **progress state with no actionable control**, and SHALL NOT report
  success until the change has actually been made.
- **PR-126a** — An **assisted** freeze or unfreeze request SHALL return a
  **reference the customer can quote**, and SHALL NOT ask them to restate an
  intent the route they took has already established. *(Tapping "Contact us" from
  a frozen account is itself the request; a form asking what they want is
  friction we added, not verification we needed.)*

#### 7.11a.1 As built — the freeze and unfreeze journeys

Recorded so the PRD and the prototype cannot drift. Owner-directed trims are in
§7.14; this is the shape that ships.

**Freeze account (Security), active account** — one card, since 17 Aug 2026;
closure moved to its own surface under Documents (PR-134a). It carries the
action and an accordion, **What a freeze stops, and what it does not**, holding
one canonical list (online access · pending orders · open positions · SIPs and
mandates · every signed-in device · holdings · money · statements · this
section), the two timing statements (PR-108a), and a **Contact us** route
(PR-107a). **Account closure (Documents)** carries the same shape on its own
page (§7.11b). Below the freeze card, the dormancy accordion (PR-75); the
`stoptrade@thinq.in` fallback is gone (§7.14). While a closure is in progress
the freeze control reads *Not available* — the surfaces are separate now, and
splitting them must not hand back a control the shared page withheld.

**Freeze — four steps.**

| # | Screen | Carries |
| :--- | :--- | :--- |
| 1 | **Suspect unauthorised access?** | Change your PIN → the PIN journey · Disable biometric → live toggle · Log out of everywhere → Security. Alternatives as peers; the freeze keeps the primary button (PR-112a) |
| 2 | **You have open positions** *(only when held)* | Each position, pending orders, expiry handling, and the one-hour commitment (PR-110a) |
| 3 | **Confirm it's you** | Thinq PIN. `1111` is the demo failure |
| 4 | **Your account is frozen** | Confirmation + how to unfreeze. Single **OK**, which also signs the customer out (PR-123a) |

**Unfreeze — three steps**, plus a progress state: **Unfreeze your account?**
(PIN + OTP) → **Verify it's you** (PAN or date of birth, PR-124a) →
*Unfreezing your account* (PR-125a) → **Your account is unfrozen**, ending on
**Start trading**.

**Assisted unfreeze.** *Contact us* on a frozen account raises the request
directly and returns **Unfreeze request received** with a reference, the contact
channels and the PAN reminder (PR-126a). The row then reports the open request
instead of offering the link again, and the state clears when the freeze is
lifted or re-applied.

#### 7.11b Closure — the four defects the market ships  **[NEW v1.4.0]**

- **PR-114a** — Before a closure request is signed, THE SYSTEM SHALL present a
  **records checkpoint** — a step, not a hint — offering the full ledger,
  contract notes and tax statements, and SHALL name the **post-closure retrieval
  route and its timeframe**. *(Paytm's principal finding: the disclosure
  "You will not be able to download any reports" describes the problem without
  solving it. The broker's retention obligation survives closure; only the
  customer's channel to reach the records does not — and ledger and P&L are
  exactly what they need months later, for a return or a tax notice.)*
- **PR-115a** — Where holdings must leave, **every route named SHALL be built**.
  *(INDmoney offers "either sell your holdings or proceed with transfer cum
  closure" and provides a control for one of them.)*
- **PR-116a** — A transfer-out form SHALL state the **format** of the DP ID and
  client ID, **name the Client Master Report as their source**, and **echo the
  receiving participant's name back** before signing. *(INDmoney takes both as
  bare text with no hint, no example, no validation and no echo — and a typo
  sends the holdings to a stranger's demat account, irreversibly.)*
- **PR-117a** — THE SYSTEM SHALL state the **statutory closure clocks** — 3
  working days for the trading account, 2 working days for the demat account on
  a clean request — and SHALL NOT present a **callback SLA** in their place.
  *(Dhan publishes "our team will get in touch within 1–2 working days", which a
  client reasonably reads as the closure timeline. A contact commitment is not a
  completion commitment.)*
- **PR-118a** — THE SYSTEM SHALL name the **demat account** in the closure
  journey. *(Dhan is a CDSL DP and never names the demat account, offers no DP
  path, and mentions no BO ID — the teardown's finding #2.)*
- **PR-119a** — THE SYSTEM SHALL NOT state or imply that closing bars the
  customer from opening an account again, unless a specific rule or policy
  imposes it, in which case that rule SHALL be named. *(INDmoney: "You will not
  be able to Buy/Sell **and create a new account with us** once your account is
  closed." No regulation imposes this.)*
- **PR-120a** — The closure journey SHALL carry **no retention marketing and no
  confirmshaming**. *(INDmoney runs a "You will miss out on.." card and calls the
  account "FREE" inside the confirmation heading; Dhan's three retention screens
  are dismissible only via labels reading "Keep issues unresolved" and "Give up
  features"; both subordinate the closure action to a stay button. **DP-6**
  already forbids this and it is restated here because closure is where every
  competitor breaks it.)*
- **PR-121a** — WHERE the stated reason for closing is inactivity, THE SYSTEM
  SHALL offer **freeze as the reversible alternative**. *(Paytm offers
  "I have stopped trading" as a reason and never mentions the freeze facility.)*

#### 7.11b.1 As built — the closure journey

**Close account card** — the action, plus an accordion **What closing your
account means**: both accounts close · holdings sold or moved first · money to
the primary bank account · statements stop being downloadable · you can open an
account again · it costs nothing. A **Contact us** route sits beneath it
(PR-74).

- **PR-128a** — WHEN a closure request is raised **online** — at submission on
  the self-service journey, or when an assisted request is logged — THE SYSTEM
  SHALL **email** a **receipt** (**`CLOSURE_REQUESTED`**) carrying the
  reference, **both statutory clocks**, what is still outstanding, and the right
  to withdraw up to the point of no return. It SHALL NOT state or imply the
  account is closed. It is **distinct from the `CLOSURE_ESIGNED` artefact
  receipt** (§7.12): one is a status message, the other carries the signed form,
  and merging them would make the attachment the only proof the request landed.
- **PR-129a** — WHEN the accounts actually close, THE SYSTEM SHALL **email** a
  **closure confirmation** (**`ACCOUNT_CLOSED`**) stating that the account
  is closed and from when, where the final balance was paid, that Thinq retains
  the records for the period the rules require **with the address to request them
  from and the response window**, and that the customer may open an account again
  with their KYC record intact.

  This is the **last message the customer will ever receive from us**, sent to
  someone who by then has no account to log into. Anything they will need
  afterwards has to be in it — the records route above all, since it is the only
  remaining answer to Paytm's principal finding once the in-app one is gone.
- **PR-127a** — The assisted closure route SHALL **collect what the customer
  wants and hand it to a person**, and SHALL NOT raise a closure on a single tap.
  This is deliberately unlike the assisted *unfreeze*, which does raise its
  request immediately: an unfreeze is reversible and its intent is unambiguous,
  a closure is neither.

**Closure — eight steps.**

| # | Screen | Carries |
| :--- | :--- | :--- |
| 1 | **Why are you closing?** | Optional reason, never blocking. Beside it, what the account costs to keep — AMC, that not trading costs nothing, that closing is free, that they can return. Choosing *Not trading any more* surfaces freeze as the reversible alternative (PR-121a) |
| 2 | **What is still outstanding** | Holdings, money, dues, open positions, pledged securities, cost — as state, not instruction (PR-73) |
| 3 | **Take your statements first** | The records checkpoint, with a one-tap export and the post-closure retrieval route and its 5-working-day window (PR-114a) |
| 4 | **What should happen to your holdings?** | Sell, or move to another demat account — both built (PR-115a) |
| 5 | **Where should the shares go?** *(transfer only)* | DP ID and client ID with formats, the CMR named as their source, and the receiving participant echoed back or rejected by name (PR-116a) |
| 6 | **Review and sign** | Both statutory clocks, the demat account named, the artefact named, the charge, and that they can return (PR-117a, PR-118a, PR-119a) |
| 7 | **e-Sign the closure form** | Aadhaar OTP, document and version named (§7.12) |
| 8 | **Account closure request signed and submitted** | Reference, **one** clock — *close within 1–3 business days*, the outer bound of the trading account's 3 working days and the demat account's 2 — status *Being requested*, and one line pointing at the tracker: *We've emailed the details to ar•••@gmail.com. You can track the closure status from Profile → Account closure*. The point of no return and the withdrawal right were removed here 17 Aug 2026 — see §7.14 |

**Closure in progress** replaces the whole surface: reference, both clocks, the
demat account, the chosen holdings route, the four outstanding items with their
state, the records reminder, and **Withdraw my closure request**.

⚠ **Three unverified regulatory claims, in three products, all placed where they
discourage a customer's chosen action:** Groww's *"Running Account Authorization
is mandatory as per SEBI regulations"*, DreamStreet's `Required by SEBI` badge on
a PIN reset, and INDmoney's re-opening bar. **PR-122a** — any claim that an action
is mandated, barred or final SHALL be verified against the primary source before
it ships, and the burden sits with whoever wrote it.

### 7.11a Request status enumerations  **[NEW v1.2.0]**

Every request Profile raises against a regulated record is trackable (PR-20), so
each needs a **closed status enumeration**. These are the values, per request
type. Like §22's event lists they are **closed and additive-only** — a value
already emitted cannot be renamed without splitting the history at the deploy.

**Registered-contact change** — `contact_change.status`

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `CHG_SUBMITTED` | Request submitted | The customer asked for the change |
| `CHG_IDENTITY_VERIFIED` | Identity verified | Two factors cleared (PR-18) and the new address answered its OTP (D-29) |
| `CHG_ESIGNED` | e-Signed with Aadhaar | The change form is signed; the copy is emailed (PR-94) |
| `CHG_KRA_REGISTERING` | Registering with the KRA | Submitted to CVL KRA, not yet acknowledged |
| `CHG_KRA_REGISTERED` | Registered with the KRA | CVL KRA has acknowledged and updated the record |
| `CHG_DP_UPDATING` | Updating at the depository | KRA accepted; CDSL not yet updated |
| `CHG_DP_UPDATED` | Updated at the depository | CDSL holds the new value on the demat record |
| `CHG_COMPLETED` | Update completed | Live at Thinq, at the KRA and at the depository |
| `CHG_KRA_REJECTED` | Not accepted by the KRA | Terminal. Needs a reason code and a route |
| `CHG_DP_FAILED` | Could not be updated at the depository | Retryable; Thinq-side value may already differ |

**Nomination** — `nominee_request.status`, `type = add`

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `NOM_SUBMITTED` | Request submitted | Details captured |
| `NOM_ESIGNED` | e-Signed with Aadhaar | Nomination form signed; the copy is emailed (PR-94) |
| `NOM_DP_REGISTERING` | Registering with the depository | Submitted to CDSL, not yet acknowledged |
| `NOM_DP_REGISTERED` | Registered with the depository | CDSL holds the nomination on the demat record |
| `NOM_COMPLETED` | Nomination completed | On the account and at the depository |
| `NOM_REJECTED` | Not registered | Terminal. Needs a reason code |

**Nominee correction** — `nominee_request.status`, `type = edit`

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `NOMEDIT_SUBMITTED` | Request submitted | The customer told us what is wrong |
| `NOMEDIT_UNDER_REVIEW` | Under review | With the team |
| `NOMEDIT_REVIEWED` | Reviewed | The team has reviewed it; the correction follows |
| `NOMEDIT_DP_UPDATING` | Updating at the depository | Submitted to CDSL, not yet acknowledged |
| `NOMEDIT_DP_UPDATED` | Updated at the depository | CDSL holds the corrected nomination |
| `NOMEDIT_COMPLETED` | Record corrected | Corrected on the account and at the depository |
| `NOMEDIT_REJECTED` | Not corrected | Terminal. Needs a reason code |

**Segment activation** — `segment_request.status`  **[NEW v1.3.0]**

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `SEG_SUBMITTED` | Request submitted | The customer asked to activate the segment |
| `SEG_PROOF_VERIFYING` | Verifying your income proof | Checking the amount and that the document is in the customer's own name |
| `SEG_PROOF_VERIFIED` | Income proof verified | Both the amount and the name check passed |
| `SEG_ESIGNED` | e-Signed | The segment activation form is signed; the copy is emailed (PR-94) |
| `SEG_THINQ_REVIEWING` | Under review by Thinq | Thinq is reviewing the proof and the signed form |
| `SEG_THINQ_APPROVED` | Approved by Thinq | Thinq has accepted the proof and the form |
| `SEG_EXCH_ENABLING` | Enabling at the exchange | With the exchange, not yet switched on for the client code |
| `SEG_EXCH_ENABLED` | Enabled at the exchange | The exchange has switched the segment on |
| `SEG_ACTIVE` | Active | Tradeable |
| `SEG_PROOF_REJECTED` | Income proof not accepted | Terminal for this attempt. Needs a reason code, and re-upload is the recovery |
| `SEG_REJECTED` | Not activated | Terminal. Needs a reason code |

The two-leg split at the end is **PR-45** made into state: *Approved by Thinq* and
*Enabled at the exchange* are different actors and are separately observable. The
gap between them is the whole reason `HC-ACC-05` exists.

`SEG_PROOF_REJECTED` SHALL carry the same reason taxonomy the ops panel already
holds for onboarding income proof (`KYC_PANEL §7.4`, onboarding `K12b`), including
the **name mismatch** case — a document in a parent's or spouse's name is the most
common rejection and reads as arbitrary unless it is named.

**DDPI** — `ddpi_request.status`  **[NEW v1.3.0]**

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `DDPI_SUBMITTED` | Request submitted | The customer asked to activate DDPI |
| `DDPI_ESIGNED` | e-Signed with Aadhaar | The DDPI form is signed; the copy is emailed (PR-94) |
| `DDPI_DP_REGISTERING` | Registering with the depository | Submitted to CDSL, not yet acknowledged |
| `DDPI_DP_REGISTERED` | Registered with the depository | CDSL holds the DDPI against the demat account |
| `DDPI_ACTIVE` | Active | Selling no longer needs a separate CDSL OTP |
| `DDPI_REJECTED` | Not registered | Terminal. Needs a reason code, and the charge SHALL be reversed |

- **PR-100** — DDPI SHALL NOT read as **Active** until `DDPI_ACTIVE`. Between
  signing and registration the election is not yet in force and CDSL still sends
  an OTP for every sale; a row that says *Active* the moment the form is signed
  is PR-09's disagreement between state and label, and the customer discovers it
  at the worst possible moment — mid-sale.

**Bank account verification** — `bank_request.status`  **[NEW v1.14.0]**

| Value | Shown as | Meaning |
| :--- | :--- | :--- |
| `BANK_SUBMITTED` | Request submitted | Details captured, or the UPI mandate approved |
| `BANK_PENNY_IN_FLIGHT` | Verifying with your bank | The ₹1 has moved and is not yet confirmed |
| `BANK_NAME_MATCHING` | Confirming details with your bank | The ₹1 cleared; the PAN ↔ holder-name check is running. *(The loader's escalated message, PR-160a, is this state)* |
| `BANK_VERIFIED` | Verified | Usable for withdrawal. The state PR-28 gates on |
| `BANK_NAME_MISMATCH` | Name doesn't match | Retryable, and **counted** against PR-157a's ceiling on the typed route only |
| `BANK_PENNY_FAILED` | Could not be verified | Retryable. The ₹1 did not clear; nothing to do with the name |
| `BANK_REJECTED` | Not added | Terminal — the three typed attempts are exhausted (PR-157a). Only UPI remains |

- **PR-190a** — **Bank verification is a tracked request like any other**, and this
  enumeration is what four existing requirements have been assuming. PR-133a refuses
  a closure while a bank verification is open, PR-168a makes `open_request_types` a
  set precisely so that audience is addressable, AT-P-48 tests the refusal, and
  §7.14a's locks depend on knowing the state. All four read a status that was never
  defined. **Closed and additive-only**, on the same rule as every enumeration above.
- **PR-191a** — The two failure values SHALL be **distinguishable to the customer**,
  because their recoveries differ: a name mismatch means this account is not theirs
  to add, a failed ₹1 means try again. Collapsing both into *Verification failed* —
  the market default — tells a customer to retry something that cannot succeed.

All five *regulated-record* request types reach the depository, so all five carry
the same in-progress / completed pair on that leg (PR-97a). A **correction** is not
the exception: a registered nomination lives on the demat record, so amending one
has to reach CDSL exactly as adding one does. The correction's extra step is the
human review before it goes downstream, not the absence of a downstream. **Bank
verification is the one request that has no depository leg** — it settles between
Thinq, NPCI and the bank — which is why its enumeration ends at `BANK_VERIFIED`
rather than at a `*_DP_*` pair.

- **PR-97** — THE SYSTEM SHALL drive every tracking view from these values, and
  SHALL NOT render a status string that is not one of them (PR-81 — no raw enum
  on screen either; each value has a customer-facing label above).
- **PR-97a** — **In-progress and completed are separate values with separate
  labels.** A step that has finished SHALL NOT still read as though it were
  running: `CHG_KRA_REGISTERING` renders *Registering with the KRA*,
  `CHG_KRA_REGISTERED` renders *Registered with the KRA*, and the same pair
  applies to the depository leg. A tracker showing a completed tick beside
  *"Registering…"* is PR-09's disagreement between state and label.
- **PR-98** — WHERE a request reaches a **terminal failure** — `CHG_KRA_REJECTED`,
  `CHG_DP_FAILED`, `NOM_REJECTED`, `NOMEDIT_REJECTED`, `SEG_PROOF_REJECTED`,
  `SEG_REJECTED`, `DDPI_REJECTED` — THE SYSTEM SHALL name what
  failed, why, and what the customer can do next. A tracker that models only the
  happy path leaves a failed request looking permanently in progress.
- **PR-99** — WHERE `CHG_DP_FAILED` occurs, THE SYSTEM SHALL state which record
  holds which value, because Thinq and the depository may now disagree (PR-20).

⚠ **The failure values are specified but unbuilt.** The prototype models the happy
path only. Raised as **P-11**.

### 7.12 e-Signed artefacts — receipt and filing  **[NEW v1.1.0]**

Profile produces **six** e-Signed artefacts after activation *(three at v1.1.0;
segment and DDPI added v1.3.0; the closure form written into this table at
v1.13.0)*. Each is a signed instruction against a regulated record, and the
customer has nothing in hand once the journey closes unless it is sent to them.
**Every receipt in this table is email only** — it carries an attachment, and no
other channel does.

| Artefact | Produced by | Event | Channel |
| :--- | :--- | :--- | :--- |
| Nomination form | §7.5, a nominee added after activation | `NOMINEE_ESIGNED` | Email only |
| Registered-mobile change form | §7.2, KYC-complete zone | `MOBILE_CHANGE_ESIGNED` | Email only |
| Registered-email change form | §7.2, KYC-complete zone | `EMAIL_CHANGE_ESIGNED` | Email only — **to the new address** |
| Segment activation form (F&O / Commodity) | §7.6, activation after onboarding | `SEGMENT_ESIGNED` | Email only |
| DDPI form | §7.7, DDPI activated after onboarding | `DDPI_ESIGNED` | Email only |
| Closure form | §7.11b, step 7 of the closure journey | `CLOSURE_ESIGNED` | Email only |

⚠️ **The closure form was the sixth artefact all along and was missing from this
table.** §7.11b step 7 e-Signs it and PR-128a distinguishes it from the
`CLOSURE_REQUESTED` status message, but only Onboarding §18.3 carried the
artefact row — so the document that owns the five said there were five. Added
v1.13.0; the behaviour is unchanged.

The last two were being produced by journeys this document already specified,
with no receipt specified anywhere. A segment activation form is the document
that carries the customer's own risk acknowledgement, and the DDPI form is a
standing authorisation over their demat account — of the five, they are the two
a customer is most likely to want later and least likely to be able to recover.

- **PR-94** — WHEN an e-Sign completes on any of the three, THE SYSTEM SHALL email
  the customer **the signed form as an attachment**, to the registered email, within
  the same window §18.3 applies to `AOF_ESIGNED` (< 5 minutes). This follows the
  existing precedent rather than inventing one: §18.3 already attaches the signed
  AOF on e-Sign.
- **PR-95** — THE SYSTEM SHALL file the same artefact in **Account documents**
  (§7.10), with what it is, when it was generated and its version (PR-69), so a
  customer who loses the email is not dependent on it.
- **PR-96** — WHERE the artefact is a **registered-email change**, THE SYSTEM SHALL
  send it to the **new** address, and SHALL separately send the out-of-band security
  notice to the mobile (PR-19). The two are different messages with different
  recipients and SHALL NOT be merged.
- These are **Utility** communications under §18.0 — a receipt for a document the
  customer just signed is not marketing, and SHALL NOT be gated on any preference
  (PR-55).

⚠ **Depends on another document.** The five events above and their approved copy
belong to Onboarding §18 — §18.3 for trigger, channel and SLA, §18.2a for the
words. Both enumerations are **closed and additive-only**, so the events must be
added there before this ships. Raised as **P-10**; §9.5's instrumentation seam
applies equally.

#### 7.12a Completion notices — WhatsApp when the request is actually done  **[NEW v1.13.0]**

Owner direction, 17 Aug 2026: the five receipts above stay **email only**, and
each journey additionally sends a **WhatsApp notice once the request completes**.

The two are not variants of one message. A receipt is a receipt for a
*signature*, and every one of the five is explicitly **barred from saying the
thing the customer actually wants to hear** — the segment receipt SHALL NOT imply
tradeable (PR-45), the DDPI receipt SHALL NOT imply active (PR-100), the
contact-change receipt SHALL NOT imply the depository holds the new value
(§7.11a). Each of those becomes true **later, from a third party — a KRA, a
depository, an exchange** — and until now nothing was sent when it did. The
customer was told what they signed and never told it had worked. That is the gap
this fills, and it is why the trigger is a **status**, not a signature.

| Journey | Completion event | Fires at | Channel |
| :--- | :--- | :--- | :--- |
| Nomination (§7.5) | `NOMINEE_REGISTERED` | `NOM_COMPLETED` | WhatsApp |
| Registered-mobile change (§7.2) | `MOBILE_CHANGE_COMPLETED` | `CHG_COMPLETED` | WhatsApp — **to the new number** |
| Registered-email change (§7.2) | `EMAIL_CHANGE_COMPLETED` | `CHG_COMPLETED` | WhatsApp |
| Segment activation (§7.6) | `SEGMENT_ACTIVATED` | `SEG_ACTIVE` | WhatsApp |
| DDPI activation (§7.7) | `DDPI_ACTIVATED` | `DDPI_ACTIVE` | WhatsApp |

**Two completions are deliberately not on this list.** **Closure** completes as
`ACCOUNT_CLOSED` and stays **email only** (PR-129a) — it is the last message the
customer ever receives, it has to be findable months later when they need the
records-retrieval route, and by then there is no account behind any deep link.
**Unfreeze** completes as `ACCOUNT_UNFROZEN`, which **is** on WhatsApp (PR-109a)
and follows every rule below. The pattern across all of §7: **email states and
files what happened; WhatsApp tells the customer a thing is now true.**

- **PR-169a** — THE SYSTEM SHALL send the completion notice on the **terminal
  completed status** of the request, and SHALL NOT send it on the e-Sign. The two
  are **separate events with separate SLAs** and SHALL NOT be merged even where
  they fall close together. *(The gap is the point. On a segment activation it
  spans Thinq's review and the exchange's enablement — the gap `HC-ACC-05` exists
  because of.)*
- **PR-170a** — The completion notice is the **one message in the set permitted to
  say the thing is live**, and it SHALL say it: the nomination is registered, the
  new number or address is on the record at Thinq, the KRA and the depository, the
  segment is tradeable, DDPI is in force and selling no longer needs a CDSL OTP.
  Everything upstream is barred from this; a completion notice that hedges leaves
  the customer with five messages and no answer.
- **PR-171a** — WHERE the registered mobile is **not reachable on WhatsApp** — not
  opted in, no WhatsApp account, or the template undeliverable — THE SYSTEM SHALL
  fall back to **email** and SHALL NOT drop the notice. A channel decision must not
  cost the customer the only message that tells them their instruction took
  effect. *(§18.2's WhatsApp → email ladder, applied to a lifecycle event.)*
- **PR-172a** — The completion notice SHALL carry **no attachment** — the signed
  form went by email at e-Sign (PR-94) and is filed in Account documents (PR-95) —
  and SHALL carry values **masked to §6.1's standard**. It SHALL deep-link to the
  Profile surface that now holds the change, not to a generic home.
- **PR-173a** — WHERE the completed request is a **registered-contact change**, the
  completion notice SHALL remain distinct from the **out-of-band security notice**
  (PR-19), which fires on the same status. They are different messages doing
  different jobs — one confirms the customer's own instruction and carries a
  deep link, the other warns a possible victim and carries **no action link
  whatsoever** — and SHALL NOT be merged, on the same basis as PR-96. WHERE both
  land on the mobile (an **email** change: security notice to the mobile, per
  PR-19), the **security notice SHALL be sent first**, and the completion notice
  SHALL NOT restate the change. ⚠️ Whether Compliance accepts two mobile-channel
  messages at one moment, or requires the security notice to stay on SMS, is
  **P-26**.
- These are **Utility** communications under §18.0, SHALL NOT be gated on any
  preference (PR-55), and — being §18.3 lifecycle events rather than §18.2
  drop-off recovery — SHALL NOT be dropped by the pooled frequency cap.

⚠ **Same dependency as above.** Five new event names and five new templates land
in Onboarding §18.3 and §18.2a. Both enumerations are closed and additive-only;
adding is permitted, and the copy is owed by Content and Compliance under **C55**.

### 7.13 Charged actions and the funding pre-flight  **[NEW v1.3.0]**

Two Profile journeys cost money. Everything else in this document is free, which
is exactly why these two need stating: a customer has no reason to expect a
charge in the middle of correcting their own record.

| Journey | Charge | Why it is chargeable |
| :--- | :--- | :--- |
| Registered mobile or email change (§7.2) | **₹50 + GST = ₹59** | It is a KRA-propagated modification of the KYC record, not an edit |
| DDPI activation (§7.7) | **₹150, GST included** | It needs a fresh document and a fresh stamp; free only during onboarding |

- **PR-101** — THE SYSTEM SHALL disclose the charge **before the customer commits
  anything**, and SHALL state what it buys. Disclosure after the first field is
  entered is not disclosure; disclosure after the e-Sign is a complaint.
- **PR-102** — Charges SHALL be displayed **inclusive of GST**, as a single figure
  the customer will recognise on their ledger. A price quoted as "₹50 + GST"
  makes the customer do arithmetic to find out what they are paying, and the
  figure they arrive at is not the one that gets debited.
- **PR-103** — THE SYSTEM SHALL check the account balance **before the journey
  starts**, and WHERE it is short SHALL say so, name the shortfall, and offer to
  add funds. A customer SHALL NOT be walked through identity verification, an OTP
  and an e-Sign only to be stopped at the charge.
- **PR-104** — WHERE the balance is sufficient, THE SYSTEM SHALL NOT show a
  balance figure or an add-funds control in the charge confirmation. With the
  money already there, funding is not a decision the customer has to make, and
  offering it is an invitation to leave the journey.
- **PR-105** — WHERE the customer adds funds mid-journey, THE SYSTEM SHALL return
  them to the **step after** the one that raised the charge, not to the start.
  Paying is progress; a top-up that lands the customer back on a page they had
  already read and agreed to reads as though it undid their work.
- The top-up SHALL be pre-filled to the **shortfall**, not to a round number or a
  last-used amount, so the customer is asked for exactly what the journey needs.
- **Failure to fund is not a failure of the request.** A customer who abandons at
  the charge has raised nothing; no `*_SUBMITTED` is emitted and no tracker
  appears. This is the one place in Profile where leaving is the clean outcome.

⚠ **Charge reversal on terminal failure is unspecified.** `DDPI_REJECTED` and
`CHG_KRA_REJECTED` both occur after the money is taken. Whether the charge is
reversed, retained or re-applied on retry is asserted nowhere. Raised as **P-12**.

### 7.14a Locks — what withholds a control, and why  **[NEW v1.10.0]**

Five different states can withdraw a control on Profile. They were built one at a
time between 14 and 17 Aug 2026 and are gathered here because the failure mode is
not any one of them: it is two of them speaking on the same screen, or a control
greyed by one while a journey reached by another route still runs.

| Lock | When | What it withholds | What stays |
| :--- | :--- | :--- | :--- |
| **Pre-activation** | `prospect`, `in_kyc`, `submitted` | Contact change (PR-140a); PIN and the authenticator, since no PIN exists before activation — D-24 (PR-141a); freeze, unfreeze and closure, since there is no trading to stop (PR-142a) | Reveal, Log out, the device list |
| **Submitted** | e-Signed, not yet live | Everything. *These settings are locked for now. Your application has been e-Signed and is being processed. These details can't be changed until your account is live because they are part of your signed application record* | Reading the record |
| **Contact change in flight** | `CHG_*` before `CHG_COMPLETED` | Every other change: banks, nominee, segments, DDPI, settlement, consents, closure, the other contact field, **and the PIN and authenticator** (PR-139a) | **Freeze**, **unfreeze**, and **Log out of all devices** |
| **Closure in progress** | `closing` | The same set, plus freeze (PR-132a) | Log out of all devices; the closure tracker |
| **Settlement window** | Next settlement ≤ 3 business days | The settlement cycle only (PR-138a) | Everything else |

- **PR-138a** — A settlement cycle SHALL NOT be changed when a settlement date is within **3 business days** — checked against **both** cycles, the one held and the one being moved to, because switching into a cycle whose date has already been frozen creates a payout inside the window. The row SHALL state the reason in place of the next date, in the same colour as the withdrawn control: *Your next settlement is on 17 / 18 August 2026. You can change your settlement cycle after this cycle is completed.* ⚠️ **As built the three days count only weekends, not exchange holidays** — see **P-21**.
- **PR-139a** — WHILE a mobile or email change is in flight, THE SYSTEM SHALL NOT permit any other change to the account. A contact change is the takeover vector: whoever controls the registered mobile receives every OTP that authorises everything else, so the window between asking and the depository confirming is exactly when a bank account, a nominee, a segment, a PIN or a second factor must not move. **Two exceptions, both deliberate: the freeze and Log out of all devices.** Each narrows access rather than widening it, and they are what a customer who has spotted the takeover reaches for. Wording: *Your mobile number change is in progress. You can't make other changes until it's complete.* with a **Track progress** control.
- **PR-140a** — Contact details SHALL NOT be changed before the account is open. *(Owner direction, 17 Aug 2026.)* ⚠️ **This overrides §7.2's zone table in two places** — the pre-PAN **deep-link into the auth engine's own change flow**, and the PAN-verified **collect-and-hand-off to Operations**. D-28 itself is untouched: the auth engine still owns the pre-PAN change on its own surface. What is lost is Profile's route into it, and §3.2's prospect row still reads *Contact details (self-service, pre-PAN)*. See **P-20**.
- **PR-141a** — The PIN and the authenticator SHALL be unavailable until the account is activated (D-24 — no PIN exists before then) and while any lock above is in force.
- **PR-142a** — Freeze, unfreeze and closure SHALL NOT appear before activation. There is no trading to stop.
- **PR-143a** — Every lock SHALL be **enforced at the journey, not only at the control**. `flow()` refuses a locked journey and states the same sentence the control does. This is not defensive coding: TnC §8 routes a C-PROC withdrawal straight into closure, the attention band routes into surfaces directly, and a published support path may name a journey rather than a page.
- **PR-144a** — A lock SHALL state its reason **once per screen**. Where a page banner already carries it, the control is withdrawn silently: the settlement row keeps its next date, and Account closure drops its *Some requests are still being processed* block. Two refusals of the same thing on one screen read as two different problems.
- **PR-145a** — A lock banner SHALL appear only on a surface that **has a control to withhold**. Basic details, Demat details, Statements & reports, Pricing and Account documents render none. *(Exception: the submitted state, where nothing anywhere is editable.)*

### 7.14 Requirements with no surface in the current build  **[NEW v1.3.0]**

The prototype was simplified repeatedly under owner direction between 14 and
15 Aug 2026. Each simplification was the right call for the screen it touched;
together they cost thirteen requirements their only surface. They are recorded here
rather than dropped, because a requirement with no surface and no record is
indistinguishable from one that was never written.

| Requirement | What it required | Status in the build |
| :--- | :--- | :--- |
| **PR-05** | A locked field carries a route or a stated reason | No route or reason shown. **AT-P-15 fails** |
| **PR-24** | Re-KYC prompt | No surface. Also blocked on **P-9** |
| **PR-34** | Marketing opt-out record | Reduced to the control; the record is not shown |
| **PR-37** | Nomination is mandatory-or-opt-out, stated as such | Not stated. Compounds **P-3** |
| **PR-39** | Journey stages named up front | Removed from the segment journey |
| **PR-42** | Income declaration alongside income proof | Only the proof half survives |
| **PR-44** | Segment deactivation, positions closed first | No deactivation control at all. **AT-P-17 fails** |
| **PR-51** | e-CN election, right to switch to physical, physical charge | Removed from Preferences entirely |
| **PR-69** *(consent records limb)* | The **consent records** are offered as a download | **No surface.** The combined pack and the per-artefact rows were removed together, 17 Aug 2026. §7.8 lists every consent on Privacy with its version and date, so a customer can read what they agreed to but cannot keep a copy of it. The DPDP right of access sits behind support |
| **PR-135a** *(KYC and FATCA limb)* | The KYC application form and the FATCA and CRS declaration are retrievable | Added and removed the same day, 17 Aug 2026. What was submitted to the KRA, and the tax-residency declaration, are retrievable nowhere in Profile |
| **PR-137a** | The signed-forms empty state names what would put a form there | Removed 17 Aug 2026. Withdrawn rather than pending |
| **PR-70** | File type and size before download | Built 17 Aug 2026 and removed the same day on owner direction. The version and the date survive; the type and the size do not, so a customer on a metered connection still cannot tell what a download will cost them. **AT-P-20 fails** |
| **PR-28** *(surface limb)* | The name-match rule is stated on the **Bank accounts** page | Removed 17 Aug 2026. It is now stated only on the scan screen inside the journey, so a customer reading the surface before starting is not told what the check is against |
| **IFSC help** | Where to find an IFSC — *On your cheque book, or in your bank's app* | Removed 17 Aug 2026. The `HDFC0001204` placeholder is the only remaining indication of its shape |
| **Own-name rule at the route step** | *It has to be an account in your own name… This will be account 3 of 3* | Removed 17 Aug 2026 from both the scan and the typed screens. The **running count against the 3-account limit** now appears only on the Bank accounts surface and in the at-limit screen, so a customer adding their third is not told it is their last |
| **Limit-screen reassurance** | *Nothing has been added to your account, and no money has moved* | Removed 17 Aug 2026. After three failures nothing states the two things a customer is most likely wondering |
| **Funding-source detail** | Which account in the picker is the **primary** one, and that the others are verified; the count of accounts still being verified | Removed 17 Aug 2026. The dropdown is names alone; a customer choosing between two rows has nothing distinguishing them, and one that is missing is unexplained |
| **Settlement retention (225%)** | A settlement may return less than the balance, and why: the pay-in obligation plus up to 225% of end-of-day margin, the worked example, pledged securities counted first, and *never a book entry* | **No surface** on the card. Removed 17 Aug 2026. The 225% sentence survives only inside step 2 of the change journey, which a customer who is not switching cycles never sees |
| **R&O settlement statement** | The retention statement within **5 working days**, and the **30 working days** to dispute it | No surface. Removed 17 Aug 2026. These are the customer's recourse when a settlement returns less than expected, and Profile now states neither the explanation nor the route to challenge it |
| **T-RAS disclosure** | The running-account authorisation named, with its date and version, stated as **voluntary** and **withdrawable without notice** | No surface. Removed 17 Aug 2026. `rasOn`/`rasVer` remain on the record and C-RAS is still listed on Privacy; the disclosure is gone. Withdrawability is the customer's only lever over this whole surface |
| **Settlement reversibility** | That a cycle change can be undone at any time, without notice or cost | No surface. Three removals on 17 Aug 2026 — the step-1 lede, the *Changing back* review row, and the confirmation note. The **Change** control never goes away, so the behaviour is unchanged |
| **Exchange-calendar citation** | *FY 2026-27, per NSE circular 4/2026* | Removed 17 Aug 2026. The dates are read from the published calendar and are correct; nothing on screen lets a customer, an agent or a reviewer check them against the source, which is what **P-5** turns on |
| **Quarterly as the floor** | *the longest gap the rules allow* | Removed 17 Aug 2026. Quarterly is the regulatory maximum, not a Thinq preference; *why can I not have it yearly* now has no answer on screen |
| **Reports by job** | The by-task route into reports — filing your return → Tax P&L, reconciling your bank → Ledger, proof of a trade → Contract notes, what you paid us → Brokerage and charges | Removed 17 Aug 2026. The dropdown is the only way in, so a customer who knows their task but not the report's name must recognise it in a list of twelve. This is the shape Paytm's own teardown proposes for its empty state and never built |
| **PR-137a** *(restated)* | The signed-forms empty state names what would put a form there | Removed 17 Aug 2026 |
| **PR-53** | Notification channel management | No surface |
| **PR-55** | No control may suppress a Utility communication | Vacuously true — there are no controls |
| **PR-59** | Pending re-consent in the attention band | Reduced; not in the band. **AT-P-19 fails** |
| **PR-65** | Sign-in activity | No surface |
| **PR-66** | Connected applications | No surface |
| **PR-109** | Each report states how far back it goes | Removed 16 Aug 2026. The account-level floor survives; the per-report differences do not. **AT-P-30 fails** |
| **PR-112** … **PR-116** | Reports render in the product; one delivery model; range retrieval in one action; the masked destination before sending | Removed 16 Aug 2026. Only **Download** survives, and only once the window is answerable. **AT-P-28, AT-P-33, AT-P-34 fail** |
| **PR-115** *(reports)* | Requests have a visible state | *Your requests* removed 16 Aug 2026 |
| **DP-16** | The obliged documents are marked | No surface. The *Required* tag, the statutory clause and the report descriptions all came off; only a dropdown group name remains, and it does not cover contract notes |
| **§7.10a inventory** | Five statutory documents retrievable | **Two remain.** *Daily margin statement* (R&O cl. 35) and *Statement of accounts* (cl. 34) were removed 16 Aug 2026 and have no other home in Profile |
| **PR-111a** | A freeze names what it does not reach — the demat account, the UCC — and offers the route to freeze that too | The rows and the route were removed 16 Aug 2026. Only the accordion's lede still says the demat account is unaffected. **This is the teardown's single biggest cross-cutting finding, now near-unstated** |
| **PR-109a** | The acknowledgement is stated in the journey | No surface. The events are specified (`FREEZE_REQUESTED` … `ACCOUNT_UNFROZEN`) but nothing on screen tells the customer to expect them |
| **PR-113a** *(second limb)* | The lighter unfreeze is named as Thinq's choice, not a regulatory floor | Removed. The turnaround also came off both unfreeze surfaces, so at the moment a customer is waiting, nothing states one |
| **PR-76** *(both limbs)* | A closure request is **withdrawable** up to a stated point of no return, and that point is **named** | **No surface.** The explanation went 17 Aug 2026, then the **Withdraw my closure request** control went with it. A raised closure can no longer be withdrawn from the product at any stage, and the only sentence naming the point of no return is the one that says it has been crossed. The `withdrawclose` handler is retained in source so restoring the control is a one-line change. **AT-P-42 and AT-P-44 fail** |
| **Post-closure retrieval** | How to obtain statements once the account is closed, and the window for it | Removed 17 Aug 2026 from the tracker. The download-first step inside the closure journey survives; nothing tells a customer who has already closed how to retrieve a record afterwards. Compounds **P-16** |
| **Freeze receipt** | A reference the customer can quote | The freeze confirmation carries none. The assisted *unfreeze* does (PR-126a) — the two are inconsistent |

- **PR-106** — Before this ships, each row above SHALL be either **built** or
  **explicitly withdrawn with a rationale in §11**. A requirement left in this
  table at ship time is an undocumented deviation, and the four failing
  acceptance tests are the evidence.
- **PR-55 deserves separate attention.** It reads as satisfied only because §7.7
  now offers nothing to switch off. The moment PR-53 is built, PR-55 becomes live
  again and the constraint has to be honoured — a notification screen that
  appears to switch off a contract note is the same defect it was written to stop.

---

## 8. Editability matrix

One table, so no surface can quietly disagree with another. This is the answer to *"can I change this, and how"* for every field Profile shows.

**Read this with §7.14a.** A ✅ below means *the mechanism exists*; it does not mean
the control is on screen right now. Five states withdraw controls, and the
rightmost column names which ones apply.

| Field | Self-service | Verified change | Ops / KRA route | Never | Withdrawn by (§7.14a) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Display name | ✅ | | | | pre-activation · contact-change · closure |
| Profile photo | ✅ | | | | pre-activation · contact-change · closure |
| Mobile · Email | ~~pre-PAN only~~ **not before the account opens (PR-140a)** | **KYC-complete: §7.2** | PAN-verified zone | | pre-activation · a change already in flight on either field · closure |
| Name as per KYC · DOB · Father's name | | | ✅ KRA | |
| Address | | | ✅ KRA | |
| PAN | | | | ✅ identity anchor |
| Aadhaar | | | | ✅ last-4 only, never full |
| Bank account (add / primary) | | ✅ penny-drop + name match | | | pre-activation · contact-change · closure |
| Nominee | | ✅ own Aadhaar e-Sign | depository propagation | | pre-activation · contact-change · closure · anyone already on record (PR-13a) |
| Segment (add) | | ✅ income proof + own form + own e-Sign | | | pre-activation · contact-change · closure · frozen |
| Segment (remove) | ✅ positions closed first | | | | pre-activation · contact-change · closure · frozen |
| Settlement cycle | ✅ | | | | pre-activation · contact-change · closure · **next settlement ≤ 3 business days (PR-138a)** |
| Contract-note delivery | ✅ | | | | no surface (§7.14, PR-51) |
| DDPI (Instant Sell) | | ✅ e-Sign + charge disclosed | | | pre-activation · contact-change · closure · already active or in flight |
| Marketing consent | ✅ < 60s | | | | pre-activation · contact-change · closure |
| Required consents (C-PROC etc.) | | | → closure flow | | pre-activation · contact-change · closure |
| PIN · authenticator | | ✅ auth engine | | | **pre-activation (D-24) · contact-change · closure (PR-141a)** |
| Devices — per-row logout, Log out of all | ✅ | | | | **nothing** (PR-150a) |
| Freeze · unfreeze | ✅ positions stay open | | | | **pre-activation (PR-142a) · closure** — *not* a contact change (PR-139a) |
| Close account | | ✅ dues settled | | | pre-activation · contact-change · **any other request in flight (PR-133a)** |

- **PR-06** — An edit affordance SHALL name its scope. A control labelled **Edit** on a card of six fields SHALL open all six or SHALL be labelled for what it actually edits. *(T-4.)*
- **PR-07** — Every edit flow SHALL have a **non-destructive exit** — Cancel or Back — that returns the customer where they were without saving, and the heading SHALL name the flow, not the section it replaced. *(T-4: Paytm's edit mode is a full-page replacement with no exit, still headed "Profile".)*
- **PR-13** — A save control SHALL read **Save**, SHALL be disabled until the field is dirty, and SHALL have explicit success and failure states. *(Dhan's is labelled "Go", has no feedback contract at all, and is half-covered by the avatar.)* Where a change has a consequence the customer cannot infer — does the display name appear on contract notes? — one line of helper text SHALL say so.

---

## 9. Non-functional requirements

### 9.1 Correctness of the record

- **PR-77** — Every value SHALL be read from the record of truth for that field. A value that renders identically to a neighbouring field's is a binding defect to be tested for, not a coincidence to be explained. *(Dhan's nominee DOB.)*
- **PR-78** — THE SYSTEM SHALL NOT render a null as a real value. *(Paytm renders every SIP timestamp as `12:00 AM`; a null time shown as midnight is a false fact. Date-only is honest.)*
- **PR-79** — Dates SHALL render in one product-wide human format. *(Paytm leaks ISO `1993-01-15` into the profile card while every other date in the product is formatted.)*
- **PR-80** — One condition SHALL have one representation. *(Paytm shows `—` and `₹0` for the same "no amount" state in adjacent rows.)*
- **PR-81** — THE SYSTEM SHALL NOT render a raw API enum. *(Paytm's `REDEEM` badge, in shouting caps, against a "Sell" filter tab and a "Withdrawal" status — three vocabularies for one concept.)*

### 9.2 Accessibility

Both teardowns failed here in the same way, and one of the failures blocks a tax document.

- **PR-82** — Every control SHALL be a real control: native inputs and buttons, correct roles, accessible names, keyboard-operable, with visible focus. *(T-9: ~44 radio-looking options with 0 radios, 0 roles, 0 group names — a keyboard or screen-reader user cannot request the ELSS statement used as §80C proof. WCAG 2.1 **2.1.1** and **4.1.2**.)*
- **PR-83** — Every page SHALL have a `main` landmark, a correct heading hierarchy starting at one `h1`, and a skip link. *(Zero `main` landmarks across all four Paytm pages; zero headings on three of them.)*
- **PR-84** — Tabular data SHALL be a table with real semantics.
- **PR-85** — Every meaningful image SHALL have a text alternative; decorative images SHALL be hidden from assistive technology. *(42 of 79 unlabelled on one page.)*
- **PR-86** — Repeated decorative content SHALL NOT be duplicated in the reading order. *(Dhan's marquee repeats the ticker ~10× in the DOM, so a screen reader reads the index list ten times before reaching the greeting.)*
- **PR-87** — Text SHALL meet WCAG AA contrast, including small-size grey field labels, which is where both products are most likely to fail.
- **PR-88** — Content SHALL NOT ghost through a sticky header, and overlays SHALL NOT cover interactive elements. *(Dhan ships two live overlap bugs — the avatar covers the "Go" button, and the "View" chip renders on top of the income value so "> 25 lakhs" reads as "V…25 lakhs" — plus content legible through the sticky bar. Both overlaps are the same class of defect and warrant an app-wide audit, not two patches.)*

### 9.3 Performance

- **PR-89** — A Profile surface SHALL fetch only what it renders. *(142 requests for ~15 static fields, with pairs and triples of identical GETs — a pattern that points at effects re-running on state change rather than deliberate polling.)*
- **PR-90** — THE SYSTEM SHALL NOT issue duplicate concurrent reads of the same resource in one page load.

### 9.4 Privacy & security posture

- PR-31 (server-side masking), PR-32 (exports), PR-49 (no ad tags) apply as NFRs.
- **PR-91** — Profile endpoints SHALL derive the subject from the session, not from a client-supplied identifier. *(T-2 / Paytm F-14: the numeric user ID is a path parameter on every profile API returning KYC and PAN data. That is not evidence of a vulnerability — the backend may authorise correctly — but it is the exact shape IDOR testing targets, and it warrants an explicit authorisation test with a second account's ID.)*
- **PR-92** — No placeholder or non-production configuration SHALL reach a live build. *(Paytm ships the literal template default `UA-XXXXX-X` to production, alongside a library sunset in July 2023.)*
- **PR-93** — The build version SHALL be shown, **labelled**. *(Dhan's `web v1.0.2.16` is the last unlabelled string on the page and reads as a leaked artefact; it is genuinely useful to support, so label it rather than remove it.)*

### 9.5 The instrumentation seam

Event instrumentation is out of scope (§1.3) and belongs to onboarding §22. Two things SHALL be settled there **before** this ships, because both enumerations are **closed and additive-only** and a value already emitted cannot be renamed without splitting every longitudinal funnel at the deploy date:

- `module` and the Profile `screen_name` values must be added to §22's closed lists.
- §22.1's rule that **no regulated identifier reaches an analytics processor** — no PAN, Aadhaar, bank account, IFSC, DOB, address, "and no hash of any of them" — binds hardest here, because Profile is the surface that renders all of them. The related rule against emitting interpolated labels applies to every reveal control and every masked field.

---

## 10. Acceptance tests

| # | Given | Action | Expected |
| :--- | :--- | :--- | :--- |
| **AT-P-01** | Assistant tells a customer `Profile → Bank Accounts` | Follow the path literally | The destination exists at that path, with that name. Repeat for all eight §10.6b Profile paths. **Closes H38.** |
| **AT-P-02** | Applicant de-scoped F&O at onboarding | Open Profile | Segments shows F&O as de-scoped with `drop_reason`, and offers the activation journey |
| **AT-P-03** | Applicant opted out of nomination | Open Profile → Nominee | Shows the opt-out declaration with date and version, and offers to add a nominee. Does **not** read "no nominee added" |
| **AT-P-04** | Activated customer | Withdraw marketing consent | Reachable from Profile home in one step; effective in < 60s; confirmation states account communications continue |
| **AT-P-05** | Any masked field | Inspect the response that populates it | Value arrives masked. Unmasked value requires a separate re-authenticated call, and that call is logged |
| **AT-P-06** | PAN revealed | Navigate away and back; or wait 60s | Re-masked |
| **AT-P-07** | KYC-complete account | Request an email change | Two factors required; new-address OTP not counted as a factor; out-of-band notice to the **mobile**; downstream state shown separately from Thinq-side state |
| **AT-P-08** | Prospect (pre-PAN) | Request an email change | Self-service, in the auth engine. Profile performs no change |
| **AT-P-09** | Any Profile page | Load with a screen reader | One `h1`, a `main` landmark, no repeated decorative content in the reading order |
| **AT-P-10** | Any Profile page | Keyboard only, no mouse | Every control reachable, operable, with visible focus. Every choice group has an accessible name |
| **AT-P-11** | Profile home | Inspect network activity | No trading, market, portfolio or funds requests; no duplicate concurrent reads; no advertising or remarketing tags |
| **AT-P-12** | Nominee with a full mobile, email and address on record | View the nominee card | All three masked by default; identity proof shown as type + last 4, labelled self-declared |
| **AT-P-13** | Any card showing a status and a CTA | Read both | They name the same actor |
| **AT-P-14** | Account holder DOB and nominee DOB differ in the source record | View both | They render differently. *(Regression test for the Dhan binding defect.)* |
| **AT-P-15** | Any non-editable field | View it | Carries either a route to change it or a stated reason. No bare padlock |
| **AT-P-16** | Any edit flow | Open, then abandon | A non-destructive exit exists and returns the customer unchanged; the heading named the flow |
| **AT-P-17** | Customer with an open F&O position | Attempt to deactivate F&O | Blocked, with the open positions named |
| **AT-P-18** | Segment approved by Thinq, not yet enabled at the exchange | View Segments | Both states shown separately |
| **AT-P-19** | Artefact version changed materially | Open Profile | Re-consent surfaces in the attention band; prior acceptance not treated as covering the new version |
| **AT-P-20** | Any downloadable document | View before downloading | File type and size stated |
| **AT-P-21** | A nomination, a mobile change and an email change, each e-Signed | Check the registered email and Account documents | The signed form arrives as an attachment within 5 minutes, and the same artefact is filed in Account documents. For the email change, the form goes to the **new** address and the security notice to the mobile, as two separate messages |
| **AT-P-22** | Balance ₹0 | Start a mobile change | The charge is disclosed before any field is entered, the shortfall is named, and the only way on is to add funds. Nothing is submitted and no tracker appears if the customer leaves |
| **AT-P-23** | Balance ₹0, mid-journey | Add funds from inside the change journey | The top-up is pre-filled to the shortfall (₹59), and on completion the customer lands on the **step after** the charge, not back at the start |
| **AT-P-24** | Balance ₹5,000 | Start a mobile change | The charge confirmation shows the price and no balance figure and no add-funds control |
| **AT-P-25** | DDPI e-Signed, not yet registered at CDSL | View Preferences | DDPI reads **Activation in progress**, not Active, and the tracker names the depository leg. It reads Active only at `DDPI_ACTIVE` |
| **AT-P-26** | Any charged journey | Read every price on screen | Every figure is GST-inclusive and matches what the ledger will show. No screen says "+ GST" for these two journeys |
| **AT-P-27** | Account with two years of history | Open every report on Statements & reports | None opens on a window so short that it renders empty. Where a result is empty, the copy names the window and offers to widen it inline |
| **AT-P-28** | Contract notes for a full financial year | Retrieve them | One action produces the range. Not one action per trading day |
| **AT-P-29** | Any two reports | Set a period on the first, switch to the second | The period persists. It does not silently revert to a shorter default |
| **AT-P-30** | Any report | Read it before selecting a period | It states how far back it goes, and where older periods come from |
| **AT-P-31** | Date control on any report | Try to select a date before the account opened | Not selectable. If reached another way, the result says the account did not exist then — it does not render ₹0.00 |
| **AT-P-32** | An account with Commodity active | Open the reports that break down by segment | Commodity appears, using the same vocabulary as §7.6 |
| **AT-P-33** | Any report offering email | Read the screen before sending | The masked destination address is shown, and the confirmation states what went where |
| **AT-P-34** | Contract note and daily margin statement | Inspect the rendered contents | Each carries its own statutory fields (PR-107). Neither is the trade book with a different heading |
| **AT-P-35** | Statements & reports | Keyboard only, screen reader | Every control reachable and operable, one `h1`, real labels, real listbox semantics, visible focus. **This surface holds tax documents** |
| **AT-P-36** | Active account with open F&O positions | Freeze | The freeze is not blocked. The positions are named, and the one-hour expiry commitment is made before the customer commits |
| **AT-P-37** | Any account | Complete a freeze | Every session ends, including this one, and the customer was told so on a step before the confirmation |
| **AT-P-38** | Frozen account | Unfreeze | PIN and OTP alone are not enough. PAN or date of birth is required, checked against the vault, and a mismatch is not reported until a whole value is entered |
| **AT-P-39** | Frozen account | Tap Contact us | The request is raised on the tap, a quotable reference is returned, and the row then reports the open request instead of offering the link again |
| **AT-P-40** | Unfreeze in progress | Leave mid-progress | The account stays frozen. No timer fires into a closed flow |
| **AT-P-41** | Closure, transfer route | Enter an unrecognised DP ID | Named as unrecognised, and the journey does not advance |
| **AT-P-42** | Closure | Reach the signing step | The wait is stated as **1–3 business days** (PR-131a), and nothing says the customer cannot open an account again. **Fails on two limbs by design:** the two clocks are no longer stated separately, and the point of no return is no longer named before it is crossed (PR-76, §7.14) |
| **AT-P-43** | Closure request signed | Open Account closure | The tracker shows the reference, the stage, and only the downstream legs — exchanges, then depository. No account-level check is re-shown, because none can still be outstanding at this point (PR-130a) |
| **AT-P-44** | Closure at any stage | Look for the withdraw control | **Fails by design.** There is none, at any stage. PR-76 has no surface — see §7.14 |
| **AT-P-46** | Contact change in progress | Open Account closure | Closure is *Not available*, the notice reads *Some requests are still being processed. Please wait for them to complete before closing your account*, and a **Track Mobile number change** control routes to its tracker. The assisted *Contact us to close* route is withdrawn too (PR-133a) |
| **AT-P-47** | Contact change in progress | Withdraw a C-PROC consent and follow the route to closure | The journey does not open. The customer lands on Account closure with the reason stated (PR-133a) |
| **AT-P-59** | Add an account, typed route, account number ending in an odd digit | Submit three times | Each attempt returns **Name doesn't match** with both names and the attempt count; the third shows **Limit reached** and offers only UPI. The typed values survive every failure (PR-156a … PR-158a) |
| **AT-P-60** | The mismatch screen | Press Enter, or fire `fnext()` | Nothing advances. The two controls on the screen are the only ways off it — a failed name check must not reach the receipt |
| **AT-P-61** | Add an account by scanning, name mismatch | Look at the attempt count, then retry | There is none — a UPI mismatch does not consume a typed-entry attempt — and the control reads **Scan again**, not *Edit account details* (PR-157a) |
| **AT-P-62** | Add an account by scanning, approved | Complete | No receipt screen. The customer lands on Bank accounts, the new row reads **Being verified**, and the toast says **Bank account added successfully** — the same words the typed route's receipt uses (PR-159a) |
| **AT-P-63** | Typed route | Leave the IFSC field with `INDB0000588`, then with an unrecognised code | The first echoes *IndusInd Bank · Andheri West, Mumbai*; the second says the branch will be confirmed when the ₹1 clears, and does not read as a confirmation (PR-155a) |
| **AT-P-64** | Three accounts linked, one still verifying | Open Add an account | Refused at the journey, not only by hiding the button, and the refusal states that an account being verified counts as one of the three (PR-29a) |
| **AT-P-65** | Add funds, two verified accounts and one pending | Open the source dropdown | Only verified accounts are listed, the selected one is marked, and **Add a bank account** is offered. Clicking outside closes it (PR-161a) |
| **AT-P-66** | Add funds, one verified account | Open the source dropdown | It still opens, and offers **Add a bank account** — the answer to *can I pay from a different bank* is a route, not a dead end (PR-161a) |
| **AT-P-49** | Mobile change at `CHG_DP_UPDATING` | Try to change a bank account, a nominee, a segment, DDPI, the settlement cycle, a consent, the email address, the PIN and the authenticator | Every one is *Not available*, each affected page states the reason once, and every corresponding `flow()` refuses with the same sentence (PR-139a, PR-143a) |
| **AT-P-50** | Mobile change in flight | Freeze the account, then log out of all devices | Both work. Neither is withdrawn by a contact change (PR-139a) |
| **AT-P-51** | Mobile change in flight | Open Basic details, Demat details, Statements & reports, Pricing, Account documents | No lock banner on any of them — none has a control to withhold (PR-145a) |
| **AT-P-52** | Mobile change in flight | Open Preferences | The banner states it once. The settlement row keeps *Next settlement …* and only loses its control (PR-144a) |
| **AT-P-53** | Next settlement 1 business day away | Open Preferences | The cycle row states the reason in place of the date, in the withdrawn-control colour, and `flow('settle')` refuses (PR-138a) |
| **AT-P-54** | Next **monthly** date 1 business day away, customer on quarterly | Try to switch to monthly | Refused, naming the monthly date. The check covers the cycle being moved to, not only the one held (PR-138a) |
| **AT-P-55** | `prospect`, then `in_kyc`, then `submitted` | Open Contact details and Security | Contact rows read *Not available* with the reason above them; PIN reads *Not set yet*; the authenticator reads *Not available*; the freeze section is absent. Every corresponding `flow()` refuses (PR-140a … PR-143a) |
| **AT-P-56** | Closure in progress | Open Security | The banner and the controls agree: PIN, authenticator and freeze all *Not available*. Nothing is greyed while its journey still runs (PR-132a, PR-143a) |
| **AT-P-57** | One nominee on record | Open Nominee | Neither the once-only rule nor the 3-nominee/100% rule is shown — both apply to a submission that has already happened (PR-152a) |
| **AT-P-58** | Any activated account | Open Account documents | Every downloadable form is listed — client master report, account opening form, and each e-Signed form with the date it was signed. No consent artefacts, no period-based statements (PR-135a, PR-136a, PR-151a) |
| **AT-P-48** | Bank verification pending | Open Account closure | Closure is *Not available* — a verification in flight is the account the balance would be paid into |
| **AT-P-67** | A segment activation e-Signed, then approved by Thinq, then enabled at the exchange | Watch email and WhatsApp across the whole request | **Two** messages, not one: the signed form by email at `SEG_ESIGNED` saying nothing about tradeability, and the WhatsApp notice at `SEG_ACTIVE` saying the segment is tradeable. Neither is sent at the other's moment (PR-169a, PR-170a) |
| **AT-P-68** | A registered **email** change reaching `CHG_COMPLETED` | Check the new address and the mobile | Three messages, three jobs: the signed form to the **new** address, the security notice to the mobile with **no action link**, and the completion notice on WhatsApp. The security notice arrives first and the completion notice does not restate the change (PR-96, PR-173a) |
| **AT-P-69** | A registered mobile with no WhatsApp account, any request completing | Wait out the SLA | The completion notice arrives **by email**. It is not silently dropped (PR-171a) |
| **AT-P-70** | An account frozen, then unfrozen | Watch every channel | `FREEZE_REQUESTED`, `ACCOUNT_FROZEN` and `UNFREEZE_REQUESTED` arrive by **email only**; `ACCOUNT_UNFROZEN` arrives on **WhatsApp**. **No SMS is sent at any point** (PR-109a, DP-18) |
| **AT-P-71** | Any activated account | Request a Client Master Report | It **downloads**. No email is sent and no *sent to ar•••@gmail.com* confirmation appears anywhere (PR-68, DP-19) |
| **AT-P-72** | An account added, session closed, ₹1 and name check then clearing | Check email and WhatsApp | Both arrive, and both state the account **can now be used for withdrawals** — the sentence that says PR-28's block has lifted. Each names the masked account and deep-links to Bank accounts (PR-183a) |
| **AT-P-73** | An account added, session closed, the name check then failing terminally | Check every channel | **WhatsApp only**, carrying the reason, **both names**, the UPI-only route, and *nothing was added and no money moved*. No email unless the number is unreachable on WhatsApp, in which case the fallback sends it (PR-185a, PR-187a) |
| **AT-P-74** | Typed route, in session | Fail the name check three times | **No message is sent on any attempt** — the customer is looking at the failure. The terminal notice fires only once the session has ended (PR-186a) |
| **AT-P-75** | Two verified accounts | Change which one is primary | An **email** names the new settlement destination and restates the in-flight-settlement effect, with **no action link**. No WhatsApp (PR-188a) |
| **AT-P-76** | Any activated account | Look for a way to remove a bank account | **There is none.** The test documents the gap rather than passing — PR-189a cannot fire and a customer at the three-account cap cannot free a slot (**P-28**) |
| **AT-P-45** | Closure in progress | Open any other Profile surface | Every control is read-only and the banner states one reason, with no route offered back to the closure page (PR-132a) |

---

## 10a. Analytics & event instrumentation  **[NEW v1.12.0]**

> **Aligned to `THINQ_EVENT_TAXONOMY.md` (THINQ-EVENTS-001) as of 20 Aug 2026.**
> That document is the product-wide registry of event names, properties and
> values; this section states Profile's rules and contracts against it and
> enumerates only what Profile emits. **Where the two disagree, THINQ-EVENTS-001
> governs.** One divergence is carried rather than resolved that way — the
> `blocked_reason` enumeration in §10a.3a, where the authority registers three
> values for `module: profile` and deletes three as collisions. §7.14a's five
> locks have to be re-read against those deletions before they can be applied
> here, so the six below stand and the divergence is recorded.

Built to the KYC PRD's §22 contract rather than beside it. Onboarding already
owns the envelope, the generic UI layer, the `module` / `sub_module`
enumeration and the 512-name account-wide ceiling; Profile adds surfaces and
outcomes to that scheme and invents nothing it can reuse.

| | |
| :--- | :--- |
| **Thinq KYC Event Spec · §14 Profile events** | **https://claude.ai/code/artifact/699af147-9ff1-4a94-b37b-539336ef5369#profileevents** |
| Source | `kyc-event-spec.html` in the repo |

**This section and §14 of that page are one artefact**, on the same rule KYC §22.0
sets for itself: a change to either is a change to both. The division of labour is
the same too — this PRD owns the **rules and contracts**, the spec owns the
**enumerated rows**. Reviewers comment in the page; the comment boxes save to
their own browser, so a comment that needs to be seen has to be copied out.

### 10a.1 What is inherited, and must not be restated

- **The envelope** (KYC §22.1b, eleven properties) is attached by the shared wrapper. Profile SHALL NOT add an envelope property; a Profile-only key in a product-wide envelope is the mistake `case_id` made.
- **The generic layer** (KYC §22.2, six names) covers the bulk of this surface as it stands: `Screen Viewed`, `Element Clicked`, `Overlay Opened` / `Overlay Dismissed`, `Field Errored`, `Media Captured`. Every accordion, reveal, dropdown, tab and stepper on Profile is an `Element Clicked` with a stable `element_id`.
- **`Account Detail Changed`** already exists for *any mutation in* `module: profile`. Profile SHALL use it rather than minting a per-field event.
- **The rules carry over unchanged**: no regulated identifier reaches a processor; no customer name; no interpolated label; nothing derivable is sent twice; identifiers are engineering-owned and frozen.

### 10a.2 `sub_module` — the values Profile needs

KYC §22.1c enumerates `profile` with eleven sub-modules. This build has surfaces
that list does not name, and two that belong to other modules entirely.

| Surface | `module` · `sub_module` | Note |
| :--- | :--- | :--- |
| Basic details | `profile` · `personal_details` | ✅ exists |
| Contact details | `profile` · `contact_details` | **add** — §7.2 is the D-28 destination and its own funnel |
| Demat details | `profile` · `demat_details` | **add** — tier-A reveals concentrate here |
| Bank accounts | `profile` · `bank_accounts` | ✅ |
| Segments | `segment_activation` | Activation is that module's, not `profile`'s. Viewing the list is `profile` · `segment_list` — **add**, on the registry's spelling |
| Nominee · Preferences · DDPI · Security · Documents · Freeze · Closure | `profile` · … | ✅ all seven exist |
| Statements & reports | `reports` · `contract_note` \| `pnl` \| `capital_gains` \| `ledger` \| `tax` | ✅ — Profile hosts the surface, `reports` owns the event |
| Add funds card | `funds` · `add` | ✅ — the card is embedded in Profile, the money is not |
| Pricing · Margin / Brokerage calculators | `profile` · `tariff` | Pricing fits; the two calculators have no home. **P-22** |

- **PR-163a** — A surface SHALL carry the module of **what it does**, not where it is drawn. The Add funds card and the segment-activation journey sit inside Profile and belong to `funds` and `segment_activation`; the reports surface is Profile's page but `reports`' events. *(KYC §22.1c: set `module` from the destination, never the navigation route.)*

### 10a.3 Reuse first — what Profile emits through names that already exist

The catalogue is the **product's**, not onboarding's; KYC was simply the first
module built against it. So the question for every Profile behaviour is *which
existing name carries this*, and only what survives that question earns a new one.

| Profile behaviour | Carried by | How |
| :--- | :--- | :--- |
| Every accordion, reveal tap, dropdown, chip, stepper | `Element Clicked` | `element_id` from the shared registry |
| Every surface and panel | `Screen Viewed` | New `screen_name` values — §22.1 says create one wherever the rendered controls change |
| Every modal, sheet, inline edit region | `Overlay Opened` / `Overlay Dismissed` | `overlay_id` |
| Account-number mismatch, invalid IFSC, empty required field | `Field Errored` | `field_id`, `error_code`, `attempt_no` |
| Income proof upload (segment activation) | `Media Captured` | Unchanged |
| **The bank 3-attempt ceiling** (§7.4a) | `Attempt Cap Reached` | `cap_type: bank_manual_entry`, `distinct_values_tried`, `support_route_shown` — the schema fits without alteration |
| Penny drop, PAN↔holder-name match, CDSL and KRA calls raised from Profile | `Vendor Call Completed` · `Vendor Failure Detected` | New `service_id` values only |
| OTP on a contact change, an unfreeze, an e-Sign | `OTP Requested` | Extend `otp_purpose` — `contact_change` · `unfreeze` |
| Any field mutation, **and** a freeze, unfreeze or closure taking effect | `Account Detail Changed` | `field_group` gains `lifecycle`; `action` gains `freeze` · `unfreeze` · `close`; **`freeze_type`** names the reason — `demat_debit` · `trading` · `voluntary_client` · `regulatory`, and a customer-initiated Profile freeze emits **`voluntary_client`** |
| Re-entering an abandoned Profile journey | `Journey Resumed` | Unchanged |

### 10a.3b Three new names — and all three are product-wide

Not *Profile's* events. Each one names a shape the product repeats outside
Profile, which is why it is worth a name at all.

| Event | Fires when | Key properties | Src |
| :--- | :--- | :--- | :--- |
| `Request Stage Changed` | **Any** multi-step request moves — here the `chg_*` / `nom_*` / `nomedit_*` / `bank_*` / `seg_*` / `ddpi_*` / `clo_*` / `unf_*` families; later an IPO application, a withdrawal, a pledge | `request_type`, `stage_name`, `previous_stage_name`, `seconds_in_previous`, `seconds_in_request`, `leg`, `initiated_by`, `charge_paise` | server |
| `Action Blocked` | **Any** control or journey the product refuses — a Profile lock here; elsewhere a closed market, an inactive segment, insufficient margin, an IPO past cut-off | `blocked_reason`, `element_id`, `was_journey_entry` — the surface is the envelope's `screen_name` | client |
| `Sensitive Value Revealed` | **Any** unmask, on any surface — Profile's tier-A and tier-B fields today, holdings and funds later | `reveal_group`, `tier`, `reauth_outcome` | server |

**Three of the six earlier candidates collapsed into these**, and the collapse is
the point:

- **`Account Request Raised` is the first stage transition.** A raise is `previous_stage_name: null` on `Request Stage Changed` — a separate name would have described the same fact twice, and made "how many requests were raised" a two-source question.
- **`Account Request Withdrawn` is a terminal transition.** Each family gains a `*_withdrawn` value; `clo_withdrawn` is the only one the build can reach today (PR-76's control, itself now removed — §7.14).
- **`Account State Changed` is `Account Detail Changed`.** A freeze is a mutation of the account's state, and `field_group: lifecycle` says so inside a name that already exists.

- **PR-163a** *(restated)* — A surface SHALL carry the module of **what it does**, not where it is drawn. Add funds is `funds`, segment activation is `segment_activation`, statements are `reports` — all three drawn inside Profile, none of them Profile's events.
- **PR-164a** — `Request Stage Changed` SHALL be **server-emitted, one per transition**, and SHALL NOT be inferred from a customer opening a tracker. The whole value of the §7.11a enumerations is that they measure how long each downstream leg actually takes; a client event measures only who looked.
- **PR-165a** — `Sensitive Value Revealed` SHALL carry the **reveal group and the tier, never the value** or a masked derivative of it. It is the analytics twin of `REVEAL_LOG` (PR-31), not a copy: the audit log is the record of truth and lives under the §18.6 DPDP deletion job.
- **PR-166a** — `Action Blocked` SHALL fire **once per refusal**, whether the customer met a withdrawn control or a guarded journey (`was_journey_entry` distinguishes them). Without it, §7.14a's five locks are unmeasurable and nobody can answer *how often does a lock stop a real customer doing a real thing*.
- **PR-167a** — A charged journey (§7.13) SHALL carry `charge_paise` on the raising transition and SHALL NOT emit the customer's **balance** on any event. What a request cost is product data; what they hold is not.
- **PR-176a** — A new event name SHALL be justified by a **shape the product repeats**, not by the module that needed it first. Each of the three above is written module-agnostic and takes a module-scoped enumeration, so trading and funds adopt them without a rename. *(A name is permanent: the 512 ceiling is a budget, but renaming is the cost that actually bites.)*
- **PR-177a** — Where an existing name fits, THE SYSTEM SHALL extend its **enumeration**, never its schema. `otp_purpose`, `cap_type`, `service_id`, `field_group` and `action` all take new values here; not one of them takes a new property.

### 10a.3a Property glossary — every value

Closed and **additive only**, on the same rule KYC §22.1d sets for its own: a
value may be added, never renamed or reused. A property not enumerated here is
free-form and said to be.

| Property | Type | Every value |
| :--- | :--- | :--- |
| `request_type` | string | **Module-scoped, additive.** `module: profile` registers eight: `contact_mobile` · `contact_email` · `nominee_add` · `nominee_correct` · `bank_add` · `ddpi_activate` · `account_closure` · `unfreeze_assisted`. **~~`freeze_assisted`~~ is removed** (THINQ-EVENTS-001 §7): an assisted freeze is a *mutation*, not a request — it has one transition, no `frz_*` family is registered, and the fact is carried by `Account Detail Changed{field_group: lifecycle, action: freeze, freeze_type}` instead. `unfreeze_assisted` stays, because an unfreeze **is** reviewed and has the `unf_*` family to prove it. `segment_activate` registers under `module: segment_activation`, not here (PR-163a); other modules register their own. A **self-serve** freeze or unfreeze is not a request — it takes effect in the session and emits `Account Detail Changed` with `field_group: lifecycle` |
| `charge_paise` | integer | `0` · `5900` (contact change) · `15000` (DDPI). **Paise**, so no float reaches a report. The balance is never sent (PR-167a) |
| `seconds_in_previous` · `seconds_in_request` | integer | **Integer seconds, server-computed** — the client cannot know when the previous stage began. `seconds_in_previous` is time in the stage just left; `seconds_in_request` is time since the first transition on this `context_id` — the envelope's record identity, THINQ-EVENTS-001 §2, which for these families is the request. **Seconds, not hours**: the grain is shared with every other duration in the registry so two legs can be added without a conversion |
| `stage_name` | string | **40 values, six families**, lowercase — enumerated below, plus the eight-value `bank_*` and four-value `unf_*` families. Module-scoped and nullable, on the envelope. *(THINQ-EVENTS-001 v1.1.0 carries the same forty; its published figure of 46 was one of the four counts THINQ-EVENTS-AUDIT-001 corrected.)* |
| `previous_stage_name` | string | Any `stage_name` for the same module, or **null on the raising transition** — which is what makes a raise countable without a second event name |
| `leg` | string | `thinq` · `kra` · `depository` · `exchange` |
| `reveal_group` | string | On `Sensitive Value Revealed`. `pan` · `dob` · `ckyc` · `boid` · `bank_account` · `mobile` · `email` · `nominee_contact` · `nominee_id` — **the nine groups Profile emits**. A *group*, never a field instance: three linked accounts share `bank_account`, so the event cannot say which was revealed. The registry carries **fourteen**: these nine plus five financial groups for holdings and funds — `holdings_value` · `pnl` · `ledger_balance` · `available_margin` · `withdrawable` — and Profile emits none of the five |
| `tier` | string | **3 in the registry:** `A` (regulated identifier, PIN re-auth) · `B` (contact and third-party, single tap) · **`F`** (financial value, no re-auth). Profile emits **two**, `A` and `B`. **`C` never appears**: a tier-C value renders in full and is not a reveal, which is why the registry gives concealed financial value the new letter `F` rather than reusing `C`, and Profile emits no `F` today |
| `reauth_outcome` | string | `passed` · `failed` · `abandoned` · `not_required` — **`not_required` is a tier-B or tier-F reveal**, and it is why no separate boolean is sent. **`failed` is the one worth an alert** — repeated PIN failures against a reveal is what an over-the-shoulder attempt looks like |
| `blocked_reason` | string | **Module-scoped, additive.** `module: profile` registers six — one per lock in §7.14a: `pre_activation` · `submitted` · `contact_change` · `closure_in_progress` · `settlement_window`, plus `request_in_flight`, the refusal PR-133a makes while any request is open. `orders` will register its own — market closed, segment inactive, margin short — against the same property |
| `was_journey_entry` | boolean | True where a guarded journey refused, false where a withdrawn control was met. One is a customer who tried; the other, a customer who looked |
| `field_group` | string | On `Account Detail Changed` **only** — the mutation grain, not the reveal grain. `lifecycle` is the freeze, unfreeze and closure value, carried with `action`: `freeze` · `unfreeze` · `close`, the existing `outcome`, and — on a freeze or unfreeze — **`freeze_type`**: **4, closed:** `demat_debit` · `trading` · `voluntary_client` · `regulatory`. **A customer-initiated Profile freeze emits `voluntary_client`**, never `trading`: `trading` names the *effect* (orders stop), and the other three are set by the depository, broker or regulator and never by the customer. The **origin** names the freeze — the customer asked — and only `voluntary_client` originates in profile · freeze (THINQ-EVENTS-001 §5.7, ruling overturned in this document's favour). The reason rides `freeze_type` and never four extra `account_state` values. It is `lifecycle` and not `account_state` because `account_state` is already an **envelope** property and a value SHALL NOT be the exact string of one. The old `state_from` / `state_to` pair is unnecessary for the same reason — the envelope is stamped at emission, so the before-state is on the previous event and the after-state on this one |
| `initiated_by` | string | **4, closed:** `self_serve` · `assisted` · `ops` · `system`. One property for one question, on both `Request Stage Changed` and `Account Detail Changed`. **`ops`** is a freeze Thinq applied, which the customer did not ask for and must be separable in every report; `system` is a transition nobody asked for at all |

**`stage_name` — the six families, in order.** Additive only; these are the same
enumerations §7.11a specifies for the trackers, **lowercased**. The status value on
the record is `CHG_COMPLETED`; the value on the event is `chg_completed`. One
vocabulary, one case rule, no lookup between them.

| Family | Values, first to last |
| :--- | :--- |
| **Contact change** · 8 | `chg_submitted` → `chg_identity_verified` → `chg_esigned` → `chg_kra_registering` → `chg_kra_registered` → `chg_dp_updating` → `chg_dp_updated` → `chg_completed` |
| **Nomination** · 5 | `nom_submitted` → `nom_esigned` → `nom_dp_registering` → `nom_dp_registered` → `nom_completed` |
| **Nominee correction** · 6 | `nomedit_submitted` → `nomedit_under_review` → `nomedit_reviewed` → `nomedit_dp_updating` → `nomedit_dp_updated` → `nomedit_completed` |
| **Segment activation** · 9 | `seg_submitted` → `seg_proof_verifying` → `seg_proof_verified` → `seg_esigned` → `seg_thinq_reviewing` → `seg_thinq_approved` → `seg_exch_enabling` → `seg_exch_enabled` → `seg_active` — emitted under `module: segment_activation` (PR-163a), not `profile` |
| **DDPI (Instant Sell)** · 5 | `ddpi_submitted` → `ddpi_esigned` → `ddpi_dp_registering` → `ddpi_dp_registered` → `ddpi_active` |
| **Closure** · 7 | `clo_submitted` → `clo_esigned` → `clo_exch_submitting` → `clo_exch_done` → `clo_dp_submitting` → `clo_dp_done` → `clo_completed` |

**And a seventh, added at the 19 Aug alignment: `bank_*` · 8.** Bank-account
management is Profile's (§7.4), §7.11a already enumerates the request, and the
registry registers the family for `module: profile` with `request_type: bank_add`.
Without both, the bank-verification funnel has no filter to run on.

| Family | Values, first to last |
| :--- | :--- |
| **Bank add** · 8 | `bank_submitted` → `bank_penny_in_flight` → `bank_name_matching` → `bank_verified` — with `bank_name_mismatch`, `bank_penny_failed` and `bank_rejected` as the failure exits, and `bank_withdrawn` registered as the family's withdrawal terminal, which no control in this build reaches. **`bank_verified` is the terminal of record**, not a `*_completed` value — the request settles between Thinq, NPCI and the bank and has no depository leg (§7.11a) |

**The counts are built on different rules, and every rule is stated so no count
moves another.** The forty above are the six families' **transition** values.
§7.11a specifies seven more across those families — `chg_kra_rejected`,
`chg_dp_failed`, `nom_rejected`, `nomedit_rejected`, `seg_proof_rejected`,
`seg_rejected`, `ddpi_rejected` — and §10a.3b's withdrawal rule gives each family
a `*_withdrawn`; none of either is inside the forty. `bank_*` · 8 is enumerated
in full, failure exits and withdrawal terminal included, because §7.11a's bank
enumeration has no separate happy path to take a subset of.

**And an eighth: `unf_*` · 4.** THINQ-EVENTS-001 §5.1 enumerates the assisted
unfreeze in full for `module: profile`, so the family is stated here rather than
owed. §7.11a carries no stage list of its own for it, and the registry's
enumeration governs until it does — the values below are the registry's,
lowercased on the same rule as the rest.

| Family | Values, first to last |
| :--- | :--- |
| **Assisted unfreeze** · 4 | `unf_submitted` → `unf_reviewed` → `unf_completed`, with `unf_rejected` as the failure exit. **`unf_completed` is the terminal of record.** Not inside the forty, on the same rule as `bank_*`: the family is enumerated in full, failure exit included |

This family is also why **`request_type: unfreeze_assisted` survives** the removal
of `freeze_assisted` (§10a.3a): an unfreeze **is** reviewed, and a reviewed
request is what a stage family is for. A freeze has one transition and no family,
which is why it is a mutation instead.

- **PR-174a** — `leg` exists because every family above crosses a boundary outside Thinq's control, and the customer-visible wait is usually on the far side of it. Without it, *a contact change takes four days* is one number nobody can act on; with it, the KRA and CDSL halves separate and only one of them is ours to fix.
- **PR-175a** — These events SHALL carry **no** PAN, Aadhaar, bank account number, IFSC, BO ID, CKYC number, date of birth, address, mobile or email — masked or otherwise — **no** customer or nominee name, **no** balance, and **no** document contents. If a property would answer *what is their PAN* even approximately, it is the wrong property.

### 10a.4 Funnels Profile owns

Defined here so they are not invented per-dashboard (KYC §22.2b makes the same
demand of its own).

| Funnel | Steps | Answers |
| :--- | :--- | :--- |
| **Contact change** | `Screen Viewed:profile_contact` → `Element Clicked:contact_change` → charge accepted → OTP verified → `chg_esigned` → `chg_completed` | Where a paid, two-factor change is abandoned — and whether the charge is where it dies. The raise is the first `Request Stage Changed` on the request — `chg_submitted` with `previous_stage_name: null` — and needs no step of its own |
| **Bank add** | scan shown → approved **or** typed → `Field Errored` → `bank_name_matching` → `bank_verified` | Which route wins, and what the 3-attempt ceiling costs (§7.4a). Filters on `request_type: bank_add` |
| **Segment activation** | segment picked → income proof uploaded → `seg_esigned` → `seg_exch_enabled` | Where the proof step loses people |
| **Closure** | closure opened → reason given → retention screen → blockers cleared → `clo_esigned` → `clo_completed` | Which reason converts to a retention save, and which blocker stalls it |
| **Freeze** | freeze opened → alternatives screen → PIN → frozen → unfrozen | Whether the alternatives (PR-112a) divert anyone, and how long a freeze lasts |

### 10a.5 Profile properties — state the comms engine reads

Mirrors KYC §22.3. Server-computed, never client-set.

`banks_linked` · `banks_pending` · `nominee_count` ·
`nominee_outcome` *(3, closed: `nominated` · `opted_out` · `none`)* ·
`segments_active` *(§22.1f scalar)* · `ddpi_active` · `settlement_cycle` ·
`account_state` · `open_request_types` · `last_reveal_at` · `docs_downloaded_30d`

- **PR-192a** — `nominee_outcome` SHALL be the **three-value enum**, never the boolean `nominee_opted_out` it replaces. SEBI recognises `nominated` and `opted_out` and nothing else; a customer who has done neither is `none`, and a boolean folds that third state into one of the two it is not.
- **PR-168a** — `open_request_types` SHALL be a set, not a boolean. PR-133a refuses a closure while *any* request is open, so the audience that rule creates is only addressable if the property says which.

### 10a.6 Open items

| # | Item | Owner | Priority |
| :--- | :--- | :--- | :--- |
| **P-22** | **The margin and brokerage calculators have no module.** They are in Profile's rail (§3.2) against DP-6, which is itself unresolved, and `profile · tariff` fits Pricing but not a calculator. Decide whether they leave the rail or take a module. | Product + Analytics | P2 |
| **P-23** | **The three shared names need an owner outside Profile.** `Request Stage Changed`, `Action Blocked` and `Sensitive Value Revealed` are written product-wide (PR-176a), so their enumerations are shared registries — `request_type`, `blocked_reason`, `field_group` and `reveal_group` — and no single PRD owns them. The stage event also needs a server emitter: the Panel's §18.6 event bus already carries ops-side decisions and is the likely source for the depository and exchange legs. Decide the registry owner before trading or funds registers its first value. | Analytics + Eng | P1 |
| ~~**P-29**~~ | ~~`unf_*` is registered as a Profile stage family and enumerated nowhere.~~ **CLOSED 20 Aug 2026** — THINQ-EVENTS-001 v1.1.0 §5.1 enumerates the family in full: `unf_submitted` · `unf_reviewed` · `unf_completed` · `unf_rejected`, with `unf_completed` as the terminal of record. `request_type: unfreeze_assisted` — one of the **eight** registered — now has a family to move through and its tracker can be built. Restated in §10a.3a; §7.11a takes the registry's enumeration rather than minting a second. | — | closed |
| ~~**P-24**~~ | ~~No event spec artefact exists for Profile.~~ **CLOSED 17 Aug 2026** — published as **§14 Profile events** in the existing KYC Event Spec rather than as a second document, so the six new names sit in the same page as the envelope and the module enumeration they inherit. | — | closed |

---

## 11. Decision log

| ID | Decision | Rationale |
| :--- | :--- | :--- |
| **DP-1** | The section is named **Profile** everywhere. The two onboarding *"from Settings"* strings change to *"from Profile"*. | Eight published paths already say Profile and are spoken aloud by the assistant. The two Settings references sit inside non-dismissible legal confirmations, which makes them more expensive to leave wrong than to change. One name, chosen by weight of existing commitment. |
| **DP-2** | One disclosure model: default masked, single reveal control, PIN re-auth for regulated identifiers only (§6.1 tiers). | The market failure is not insufficient masking but three unstated models running simultaneously, which is how a product masks a PAN and publishes an Aadhaar. A stated rule fails visibly; an unstated one fails silently. |
| **DP-3** | Masking is enforced server-side; the unmasked value is a separate audited call. | A UI that hides a value its own API already delivered is not protecting anything, and the customer cannot tell the difference. |
| **DP-4** | Profile owns the **KYC-complete contact-change flow** that Registration D-28 hands off and marks Out of Scope. | Someone has to. REC-M12 already tells customers it happens "from your account profile", and the auth engine deliberately declines to state the steps because they belong here. |
| **DP-5** | Segment activation is presented as a journey, never a toggle. | Onboarding §7 requires income proof, a separate segment-specific form and its own e-Sign, and forbids re-opening the AOF. A switch is a false statement about what the action costs. |
| **DP-6** | Profile carries no promotion, no upsell and no advertising tags. | It is the surface people open when something is wrong and while sharing their screen with support. Both teardowns violate this, in opposite ways. |
| **DP-7** | Profile links to the reports engine rather than reimplementing statements. | Paytm demonstrates the cost of the alternative: one control set rendered four times with silently different history depth. |
| **DP-8** | Third-party (nominee) data is masked to the same standard as the account holder's. | The nominee never saw this interface and consented to nothing in it. Sensitivity is a property of the data, not of whose screen it lands on. |
| **DP-9** | A charge with no choices in it is disclosed in a **confirmation dialog**, not a full page. | Three read-only rows do not need a screen. A page implies there is something to decide; a dialog says "this costs ₹150, yes or no", which is the actual decision. Applies to DDPI and to both contact changes. |
| **DP-10** | All Profile prices are quoted **GST-inclusive**, as one figure. | "₹50 + GST" asks the customer to compute what they owe, and the number they compute is not the number that gets debited. The tariff table's statutory charges stay at-cost and separately labelled, because those genuinely vary. |
| **DP-11** | A mid-journey top-up resumes at the **next** step, and is pre-filled to the shortfall. | Both follow from treating payment as progress. Returning to the start and asking for a round number both make the customer redo work they had already done. |
| **DP-12** | The charge is disclosed **after** the journey's "before you start" page, at the moment of commitment — not before it. | The customer needs to know what the change involves before the price means anything. Asked first, the price has no context; asked at the commit, it is the last thing they weigh. |
| **DP-13** | Requirements that lose their surface to simplification are **listed in §7.14**, not silently dropped. | Twelve did. Four acceptance tests now fail by design. An undocumented deviation and a deliberate scope cut look identical six months later; only one of them is defensible. |
| **DP-14** | **One surface owns statements.** Profile is either that surface or a link to it — never a second, lesser implementation carrying the same name. | Dhan ships a legacy shell called "Statements & Reports" one menu row from Journal's "Statements & Reports", and its own help centre routes users past the first. DP-7's "link, don't reimplement" was right; it just needed the second half — that a stub with the real name is the same defect as a duplicate. |
| **DP-15** | Reports are grouped by **what the customer came to do**, not by which system produces them. | Both teardowns show one flat list mixing statutory documents with analytical reports and account summaries. "I'm filing my return" and "I'm reconciling a charge" are different jobs and want different shelves; the back office that generates them is not the customer's concern. |
| **DP-17** | An **assisted** request that is reversible raises itself on a single tap; one that is not, collects intent and hands it to a person. | Tapping "Contact us" from a frozen account is itself the unfreeze request, and an unfreeze is reversible. A closure is not, so the same gesture opens a message rather than a permanent action. The asymmetry is deliberate and is the rule for any future assisted route. |
| **DP-16** | The five statutory documents are **labelled as such** and shown together. | A customer cannot tell which of a dozen reports is the one their CA, their bank or SEBI actually recognises. Marking the obliged ones costs a label and removes the guess. |
| **DP-18** | **One rule for every Profile communication: email states and files what happened; WhatsApp carries what the customer needs to know now.** Receipts, request acknowledgements and restrictions go by email; completions go by WhatsApp, with email as the fallback. **No Profile communication uses SMS.** **Second limb restated v1.14.0** — it read *"WhatsApp says a thing is now true"*, which the bank rejection notice (PR-185a) does not do and still belongs on WhatsApp: a rejection blocks a withdrawal the customer may be about to attempt. Urgency is the property the channel is actually selecting on, and completions are the largest class of urgent message rather than the definition of one. Restating it keeps two of the four bank rows from reading as exceptions to a rule a day old. | Owner direction, 17 Aug 2026. The two channels were being chosen message by message, which is how the four freeze rows ended up on SMS against §18.0's own prohibition. The split also fixes a real gap rather than just tidying one: every e-Sign receipt is *barred* from saying the thing is live (PR-45, PR-100), so before this there was no message that ever said it. Email keeps what has to be findable months later and can carry an attachment; WhatsApp is read in minutes and needs no account behind it. |
| **DP-19** | The **CMR is a download, not an email**. `CMR_REQUESTED` is not a communication Profile fires. | Every other document on this surface lost its email and view verbs on 16 Aug (§7.14) and kept only **Download**. A CMR arriving by mail while its eleven neighbours download is PR-113's "three verbs with no visible logic" reintroduced for one row. The CMR still travels as an attachment on `PTT_CONFIRMED` at activation, where the customer has no account to download it from yet. |

---

## 12. Open items

| # | Item | Owner | Priority |
| :--- | :--- | :--- | :--- |
| **P-1** | **The section has two names in approved copy.** Eight live help-centre and assistant paths say `Profile → …`; the onboarding §7 de-scope confirmation and §8 opt-out confirmation say *"from Settings"*. Both are approved copy inside non-dismissible confirmations. DP-1 resolves it in favour of Profile; **the change must be made in THINQ_KYC_ONBOARDING_PRD.md by that document's owner**, not here. Until then a customer who reads the opt-out confirmation is sent to a section that does not exist under that name. | Onboarding owner + Content | **P0** |
| **P-2** | **DPDP rights have no policy behind them (PR-61).** TnC **T14** records that the Privacy Policy does not exist, and that it owns access, correction, erasure, grievance and the consent manager — *"none of which appear in any Thinq document today"*. Profile is where a customer would exercise these rights and cannot invent them. **PR-61 is blocked, not deferred.** | Legal + Product | **P0 — inherited from T14** |
| **P-3** | **Three published answers describe regulated journeys as self-service edits.** `HC-ACC-01` ("go to Profile → Contact Details and update") is a two-factor, identity-verified, KRA-propagated modification. `HC-ACC-04` / `HC-SEG-04` ("enable or disable anytime") require income proof, a separate form and a second e-Sign. `HC-ACC-03` never states that nomination is mandatory-or-opt-out (Support **H35**). Either the answers change or the flows will read as broken to every customer who follows them. Compounds **H38**. | Content + Support owner | **P0** |
| **P-4** | **Timelines are published against a standing rule that none is committed.** Support **H34** lists five, of which three land here: `HC-ACC-01` (contact change "a few working days"), `HC-ACC-04` (segment activation "1–2 business days"), `HC-ACC-08` (closure "7–10 working days"). None is verified. PR-22 forbids Profile from committing one, which will contradict the website unless H34 resolves. | Product + Content | P1 |
| **P-5** | **The settlement cycle may be described wrongly (PR-38).** Support **H31**: `HC-FUND-10` presents a rolling 90/30-day clock; SEBI's cycle is understood to be calendar-aligned (first Friday of the quarter or month). Profile is about to render a **next settlement date** from whichever model is implemented. A customer shown the wrong date has a documented grievance. | Compliance | **P0 — inherited from H31** |
| **P-6** | **Freeze semantics are unspecified.** `HC-DMT-08` promises customer-initiated freeze. Whether that is a trading-account suspension, a CDSL demat freeze, or both — and which can be reversed self-service — is asserted nowhere. The two have different mechanics, different reversal paths and different regulatory footing. | Compliance + Operations | **P0** |
| **P-7** | **Closure has no owning flow.** `HC-ACC-08` publishes the path and the outstanding-items behaviour; no PRD specifies the process, the dues calculation, the holdings-transfer route or the point of no return (PR-76). TnC §8 additionally routes a C-PROC withdrawal into it. | Operations + Compliance | P1 |
| **P-8** | **Nominee post-activation e-Sign has no named provider or journey.** Onboarding §8 states that a nominee added after activation requires its own Aadhaar e-Sign, and stops there. Whether it reuses the onboarding e-Sign chain (Digio → Signzy → Setu, with the §18 failover-exhaust behaviour) is unstated. | Product + Eng | P1 |
| **P-11** | **No request tracker models failure (§7.11a).** `CHG_KRA_REJECTED`, `CHG_DP_FAILED`, `NOM_REJECTED`, `NOMEDIT_REJECTED`, `SEG_PROOF_REJECTED`, `SEG_REJECTED` and `DDPI_REJECTED` are enumerated but not built, and none has a reason taxonomy. A KRA rejection is the likeliest and the one with the least obvious recovery; `SEG_PROOF_REJECTED` is the most frequent, and its commonest cause — a document in someone else's name — reads as arbitrary unless named. The Panel's reason taxonomies (KYC_PANEL §7.4) and onboarding's `K12b` are the models to follow. | Product + Operations | **P0** |
| **P-12** | **No charge-reversal rule on terminal failure (§7.13).** `DDPI_REJECTED` and `CHG_KRA_REJECTED` both occur after the customer has been debited. Whether the charge is reversed automatically, retained, or credited against a retry is asserted nowhere — and a customer charged ₹59 for a change the KRA then refused will treat silence as the answer. Also unstated: whether a retry after a rejection is charged again. | Finance + Operations | **P0** |
| **P-14** | **No Thinq document says who generates statements, how far back they go, or what a range request costs.** §7.10a specifies the customer-facing contract — inventory, periods, delivery, states — and cannot specify the engine behind it. Needed: whether reports come from the TechExcel back office (as the CMR does, Onboarding §18) or a separate reports engine; the retention window per report; any per-request range cap; and whether contract-note archives are held as generated PDFs or re-rendered on demand. PR-109, PR-114, PR-115 and PR-118 all depend on the answers. | Eng + Operations | **P0** |
| **P-15** | **Commodity reporting is on the same path INDmoney is being criticised for (PR-119).** Onboarding C54 confirms commodity is sold; no Thinq document mentions commodity in any report. The gap is currently invisible because neither the reports nor the segment has shipped — which is exactly the moment to close it. Confirm commodity appears in the tax P&L, the charges report and the trade book, and that the venue question in C54 does not leave it unreportable. | Product + Compliance | P1 |
| **P-20** | **PR-140a leaves the two pre-activation zones with no route at all.** §7.2 gives Profile a job in each: deep-link into the auth engine's flow before PAN, and collect-and-hand-off to Operations between PAN and KYC completion (REC-M17 already promises *"our team will make this change after a quick identity check"*). As built, both read *Not available* and the assisted link renders only when KYC is complete — so an applicant who mistyped their email has nothing to click and a promise from another PRD that nothing honours. Decide one of: restore the deep-link and the assisted collection, or state the refusal with the route out (support number, or back to the auth engine). §3.2's prospect row needs updating either way. | Product + Registration | **P0** |
| **P-21** | **The settlement lock counts weekends, not exchange holidays (PR-138a).** Three business days is enforced against Saturday and Sunday only. The exchange settlement calendar is already in the build (`CAL`), and the real rule should count against it — otherwise a Diwali or a Republic Day inside the window makes the lock a day short and a change lands on a frozen payout file. No document states the freeze window; confirm it with Operations. | Operations + Eng | P1 |
| **P-19** | **A published support path now names no destination (PR-134a).** Support §10.6b publishes `Profile → Account` for **both** freeze and closure. Since 17 Aug 2026 there is no *Account* surface: closure is `Profile → Documents → Account closure` and freeze is `Profile → Security → Freeze account`. The build aliases `account` → closure, so a closure question still lands; a **freeze** question lands on the wrong page. Both published strings need updating in the assistant, and AT-P-01 re-run across all eight paths. Reopens part of **H38**. | Product + Support | P1 |
| **P-18** | **The three-account bank limit is asserted nowhere but the build (PR-29a).** No PRD states a cap, its value, or whether an in-flight verification occupies a slot — this build says three and counts pending, which is the safe reading but an invented one. Confirm the number against the back office and the risk policy, and state it in §7.4 before it ships in copy the customer will hold us to. | Product + Operations | P1 |
| **P-17** | **Both freeze limbs need an owner who can meet them (PR-108a).** The in-app route can be automated; `stoptrade@` is a mailbox, and 15 minutes during trading hours means someone or something is reading it continuously through the session. Angel One publishes 15 minutes against a phone line staffed 08:30–17:30, which is the failure mode to avoid. Confirm both routes can meet both limbs before the timeline is published. | Operations + Eng | P1 |
| **P-16** | **The statutory delivery obligations have no owner in any PRD (§7.10a).** SEBI R&O cl. 32 (contract notes within one working day), cl. 34 (statement of accounts) and cl. 35 (daily margin statements), plus the Depositories Master Circular's quarterly transaction statement and annual holding statement, are **delivery** obligations. §7.10a covers self-serve retrieval and explicitly does not discharge them. Which document owns the daily dispatch, and whether it is the §18 comms engine, is unasserted. | Compliance + Operations | **P0** |
| **P-13** | **§7.14's fourteen requirements need a build-or-withdraw decision (PR-106).** Twelve requirements have no surface and four acceptance tests fail by design. Each needs to be either built or explicitly withdrawn with a rationale in §11 before ship. PR-51 (e-CN election and the right to physical) and PR-44 (segment deactivation) are the two with regulatory weight; PR-70 (file type and size) is the cheapest to restore. | Product | P1 |
| **P-10** | **The three e-Signed artefacts have no events, no copy and no attachment convention (§7.12).** `NOMINEE_ESIGNED`, `MOBILE_CHANGE_ESIGNED` and `EMAIL_CHANGE_ESIGNED` must be added to Onboarding §18.3 and their templates to §18.2a, both closed additive-only enumerations. The AOF precedent password-protects the attachment on **PAN in caps** — confirm the same convention applies here, and who generates the forms (the AOF comes from the e-Sign chain; the CMR from TechExcel). | Onboarding owner + Product | P1 |
| **P-9** | **Re-KYC has no trigger, period or journey (PR-24).** Named as a mandated disclosure in Support **H35** and as dormancy re-verification in `HC-ACC-07`. Profile is the natural surface for the prompt and owns none of the logic. | Compliance + Product | P1 |
| **P-25** | **`ACCOUNT_FROZEN` is email-only, and the freeze signs every device out (PR-109a, PR-123a).** The one message in this document a customer may read in a panic is now reachable only from an inbox — quite possibly on the device they have just stopped trusting. The owner's direction stands and is recorded; what needs a decision is whether the **freeze confirmation specifically** is the exception to DP-18 that also goes to WhatsApp, given `ACCOUNT_UNFROZEN` already does. | Product + Compliance | P1 |
| **P-26** | **An email change now lands two messages on the mobile at one moment (PR-173a).** At `CHG_COMPLETED` the out-of-band security notice goes to the mobile (PR-19) and the completion notice goes to WhatsApp. Ordering and non-duplication are specified; what is not settled is whether Compliance accepts both on one device seconds apart, or requires the security notice to remain on SMS — which would reintroduce the SMS channel DP-18 has just removed. | Compliance + Content | P1 |
| **P-28** | **There is no way to remove a bank account, and the three-account cap makes that a trap rather than an omission (§7.4b).** AT-P-64 refuses a fourth account even when one of the three is only *verifying*, so a customer who mistyped an account or whose bank has closed cannot free a slot — and P-18 records that the cap itself is asserted nowhere but the build. PR-189a specifies the removal notice and **cannot fire until the control exists**. §7.4b states the three constraints that follow from existing requirements — not the primary, not the last verified account, not while a closure or settlement is in flight — and deliberately does not design the screens. Decide whether removal is built, and by whom. | Product | **P0** |
| **P-27** | **WhatsApp as a lifecycle channel is still unowned (Onboarding C57).** **Eight** Profile events now depend on it — five completions, `ACCOUNT_UNFROZEN`, and both bank outcomes (`BANK_ACCOUNT_VERIFIED` on its WhatsApp half, `BANK_ACCOUNT_REJECTED` outright). Each needs an approved Utility template, an opted-in number, and a deliverability signal the fallbacks in PR-171a and PR-187a can actually read. C57 raised this for one optional KYC-link send and remains open; the two bank events sharpen it, because `BANK_ACCOUNT_REJECTED` is the only WhatsApp message in Profile that **requires the customer to act**. | Content + Compliance + Growth | **P0** |

---

## 13. Future scope

Deliberately deferred, not rejected: native app parity · a consolidated "download everything" DPDP export · nominee change history visible to the customer · per-device notification granularity · Profile surfaces in languages beyond English and Hindi · in-app display of contract-note archive rather than a link out.

---

## Output Status

```
PRD Status: THINQ-PROFILE-001 v1.3.0 — DRAFT

Scope derived from commitments already made to customers in four other PRDs, not
from a feature proposal. v1.3.0 brings the document level with the interactive
prototype built 14–15 Aug 2026, which walked every journey end to end.

Ready to build: §3 IA · §5 field inventory · §6 disclosure policy · §7.1, §7.3–7.6,
§7.9–7.11 · §7.11a enumerations · §7.13 charged actions · §8 editability matrix ·
§9 NFRs · §10 acceptance tests.

Specified, blocked on the engine behind it:
  §7.10a Statements & reports — P-14, no document names the generation path,
  the retention window or the range caps. The customer-facing contract is
  settled; what can honestly be offered is not.

Blocked, and blocked on other owners:
  PR-61 (DPDP rights)        — TnC T14, the Privacy Policy does not exist
  PR-38 (settlement cycle)   — Support H31, the cycle model may be wrong
  §7.11 freeze               — P-6, freeze semantics unspecified
  §7.11 closure              — P-7, no owning flow
  §7.12 e-Sign receipts      — P-10, five events and their copy live in Onboarding §18

Needs another document's owner to act before this ships:
  P-1  two names for one section, in approved onboarding copy
  P-3  three published answers describing regulated journeys as simple edits

Open and owned here:
  P-11 no tracker models failure — seven terminal values, no reason taxonomy
  P-12 no charge-reversal rule when a paid request is rejected
  P-13 fourteen requirements in §7.14 need a build-or-withdraw decision
  P-14 no owner for the reports engine, retention or range caps
  P-15 commodity is sold and appears in no report
  P-16 the statutory delivery obligations have no owner in any PRD

Known deviations: ten acceptance tests — AT-P-15, AT-P-17, AT-P-19, AT-P-20,
AT-P-28, AT-P-30, AT-P-33, AT-P-34, AT-P-42 and AT-P-44 — fail by design in the
current build. See §7.14, which now lists twenty-five requirements with no
surface.

Sharpest of them: PR-111a. A freeze no longer names the demat account or the
UCC and offers no route to freeze them — the teardown's single biggest
cross-cutting finding, and the one that leaves a customer believing they have
secured their assets when they have secured their login.

Closes on delivery: Support H38 (eight published paths with no destinations).
```
