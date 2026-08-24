---
title: "FMS — Withdraw Funds"
status: draft
version: "1.0"
part_of: product-requirements.md
---

# Withdraw Funds

> Part 4 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.

This part owns money leaving the account — both when the user asks for it and when the regulator requires it. Money leaving is irreversible in practice, which makes the order of checks a financial control rather than a design preference.

## Contents

- [Functional Requirements](#functional-requirements) — REQ-301 to REQ-308
- [Business Rules](#business-rules) — Rules W1 to W12
- [User Flows](#user-flows)
- [Feature-Specific Edge Cases](#feature-specific-edge-cases)

---

## Functional Requirements

### REQ-301 — Keep a withdraw entry point always visible, disabled with a reason (Must Have)

- **User Story:** As Arun, I want to always be able to find the way to take my money out, so that I never have to wonder whether the product can do it at all.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present a withdraw entry point whenever the account exists, per Rule W1.
  - [ ] WHILE nothing can be withdrawn, THE SYSTEM SHALL present the entry point as unavailable and SHALL state which deduction is responsible, per Rule W1.
  - [ ] THE SYSTEM SHALL NOT present a control that cannot act as though it can act, per Rule W2.
  - [ ] WHEN the user reaches the withdraw path with nothing withdrawable, THE SYSTEM SHALL offer the derivation of the withdrawable figure (REQ-102) as the next step.
  - [ ] WHILE the account balance is negative, THE SYSTEM SHALL state the debt and offer the route to clear it in place of the withdraw path, per REQ-501.

### REQ-302 — Accept one withdrawal request and settle it at end of day (Must Have)

- **User Story:** As Nikhil, I want to ask for a payout without my money being locked away from trading for the rest of the session, so that requesting it does not cost me a day.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal request is accepted, THE SYSTEM SHALL leave the withdrawable figure unchanged, per Rule W3.
  - [ ] THE SYSTEM SHALL state, before the request is submitted and again on confirming it, that the amount sent will be whatever is available at end of day, per Rule W3a.
  - [ ] WHEN the available balance at end of day is less than the amount requested, THE SYSTEM SHALL send what is available and close the request, per Rule W4a.
  - [ ] WHEN no balance is available at end of day, THE SYSTEM SHALL send nothing, close the request, and say so.
  - [ ] THE SYSTEM SHALL refuse a second request while one is open, per Rule W4.
  - [ ] THE SYSTEM SHALL record the withdrawable figure as it stood at the moment each request was accepted, per Rule W11.
  - [ ] WHEN a request reaches any outcome other than being paid, THE SYSTEM SHALL close it, leaving every figure untouched because none was ever held, per Rule W4.

### REQ-303 — Tell the user when the money will arrive, from their own account state (Must Have)

- **User Story:** As Arun, I want to be told when my money will actually reach my bank, so that I can plan around it rather than around a generic promise.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal amount is entered, THE SYSTEM SHALL state the expected arrival time computed from this account's own state, per Rule W5.
  - [ ] THE SYSTEM SHALL state the arrival time before the request is submitted, not after.
  - [ ] WHERE the account's state defers the arrival time — an unexecuted order, a trade placed today, a non-working day, a settlement holiday — THE SYSTEM SHALL name the cause of the deferral.
  - [ ] THE SYSTEM SHALL record the arrival time quoted, so that quoted and actual can be compared.
  - [ ] IF the arrival time cannot be computed, THEN THE SYSTEM SHALL state that rather than presenting a default duration as though it were computed.

> **Dependency — half resolved 19 Aug 26.** EB-4 is closed: the boundary is `payoutCutoff` 3:00 PM, before it the next working day and after it the working day following, per [Configuration](product-requirements-configuration.md#4-withdrawal-timing). **EB-9 is still open** — which days are working days is not derivable from the day of the week, and without a nominated calendar source the arrival date is a guess. This requirement is not deliverable until EB-9 resolves.

### REQ-304 — Offer a faster route where the account qualifies (PARKED — not in this phase)

> **Parked 19 Aug 26.** There is **no instant payout**. Every withdrawal settles on the mandated
> cycle against the EOD boundary in
> [configuration](product-requirements-configuration.md#4-withdrawal-timing). This requirement and
> Rule W6 are retained as written so the work is not lost, and are excluded from the requirement
> register, the priority table, the MVP scope and every phase until the decision is revisited.
>
> **What the module must still do now:** model `mode` on a withdrawal request with a single value
> today, so that adding a second route later is additive rather than structural. Nothing else about
> a faster route is built, displayed, or referred to in copy.

- **User Story:** As Nikhil, I want a faster payout when my account qualifies for one, so that I am not waiting two days for money I could have now.
- **Acceptance Criteria (parked):**
  - [ ] WHERE a faster route exists, THE SYSTEM SHALL present it alongside the standard route with its ceiling, its cost and its availability window stated in the choice itself, per Rule W6.
  - [ ] WHEN the account does not qualify for the faster route, THE SYSTEM SHALL state the specific condition that disqualifies it, per Rule W6.
  - [ ] THE SYSTEM SHALL NOT allow an amount above the faster route's ceiling to be submitted to it.
  - [ ] IF a faster-route request fails, THEN THE SYSTEM SHALL state whether it can be retried and SHALL offer the standard route.

### REQ-305 — Let the user cancel a request that has not yet been sent (Must Have)

- **User Story:** As Arun, I want to change my mind before my money leaves, so that a request made in error is not irreversible.
- **Acceptance Criteria:**
  - [ ] WHILE a request has not been sent, THE SYSTEM SHALL allow the user to cancel it.
  - [ ] WHEN a request is cancelled, THE SYSTEM SHALL close it and leave the withdrawable figure unchanged, as it has been since the request was made, per Rule W4.
  - [ ] WHEN a request can no longer be cancelled, THE SYSTEM SHALL state that and state why.
  - [ ] THE SYSTEM SHALL record the cancellation in the account's history, per REQ-405.

### REQ-306 — Return the money and state the reason when a payout fails (Must Have)

- **User Story:** As Arun, I want money that could not reach my bank to come back with an explanation, so that I know it is not lost.
- **Acceptance Criteria:**
  - [ ] WHEN a payout fails after being sent, THE SYSTEM SHALL return the amount by a compensating entry, per Rule W7.
  - [ ] THE SYSTEM SHALL NOT remove or alter the original entry, per Rule W7.
  - [ ] THE SYSTEM SHALL notify the user with the reason and the amount.
  - [ ] THE SYSTEM SHALL NOT automatically resend a failed payout to the same destination, per Rule W7.
  - [ ] WHEN the reason relates to the destination account, THE SYSTEM SHALL state that the destination needs attention before another request.

### REQ-307 — Return unused funds on the mandated calendar and explain each return (Must Have)

- **User Story:** As Arun, I want to be told when money leaves my account automatically, so that I am not left believing something has gone wrong.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL return unused funds on the dates the mandated settlement calendar requires, per Rule W8.
  - [ ] THE SYSTEM SHALL state the next such date before it occurs, on the funds view.
  - [ ] WHEN such a return occurs, THE SYSTEM SHALL notify the user with the amount and the reason.
  - [ ] THE SYSTEM SHALL record the return in the account's history as a movement the user did not request, distinguishable from one they did, per Rule W8.
  - [ ] IF a user's own request is open on the same date, THEN THE SYSTEM SHALL settle both from the same available balance in one payout and SHALL NOT send the same money twice, per Rule W9.
  - [ ] WHERE the user may choose the frequency of the return, THE SYSTEM SHALL present the current choice and the date it produces.

### REQ-308 — Re-check eligibility before the money actually leaves (Must Have)

- **User Story:** As a risk owner, I want eligibility confirmed at the moment of sending, so that an overnight change cannot result in money leaving that is no longer there.
- **Acceptance Criteria:**
  - [ ] WHEN a request is about to be sent, THE SYSTEM SHALL re-evaluate the withdrawable figure, per Rule W10.
  - [ ] IF the full amount is no longer available, THEN THE SYSTEM SHALL settle what is available, or send nothing where nothing is available, and SHALL state which deduction accounts for the gap, per Rule W10.
  - [ ] WHEN a reduced amount is sent, THE SYSTEM SHALL state the amount requested, the amount sent, and the deduction accounting for the difference — having warned before submission that this could happen, per Rule W3a.
  - [ ] THE SYSTEM SHALL notify the user of a refusal at this stage with the same specificity as a refusal at request time.
  - [ ] THE SYSTEM SHALL record both the figure at request and the figure at settlement, per Rule W11.

> **Owner, 19 Aug 26 — TechExcel.** The settlement check is performed by the **TechExcel back office**
> when it processes the withdrawal at end of day. FMS does not recompute eligibility at that point; it
> originates the request and consumes TechExcel's settlement outcome.
>
> **What FMS still owes for this to hold:** the outcome must come back specific enough to name
> *which deduction accounts for the gap* rather than a generic decline (per Rule W10), and both
> figures — at request and at settlement — must be stored so that a short settlement can be explained
> to the user months later. An outcome that arrives as an unexplained status code satisfies the
> control and fails the requirement.

---

## Business Rules

**Rule W1 — The way out is always visible.** A withdraw entry point exists whenever the account exists. Where nothing can be withdrawn it is shown as unavailable with the responsible deduction named. Removing the control teaches the user the product cannot do it; showing it live and having it do nothing teaches them the product is broken. Neither is acceptable.

**Rule W2 — A control that cannot act never looks like one that can.** Disabled state is visually distinct, carries its reason adjacent to it, and never absorbs an interaction silently.

**Rule W3 — A request reserves nothing. It is settled at end of day against whatever is available then.**
The money stays tradable for the whole session, and the amount that reaches the bank is the amount
left when the payout run happens — which may be less than was asked for, or nothing at all. A user
who withdraws in the morning is not made to choose between taking money out and trading with it
that day.

> This replaces an earlier rule under which validation and reservation were one indivisible action,
> and the withdrawable figure fell the moment a request was accepted. That rule existed to make
> double-withdrawal impossible. It is no longer needed for that: **Rule W4** already permits only one
> open request, so a second cannot be placed whether or not the first is reserved. What reservation
> additionally bought was idle capital — money the user could not trade with while it waited for a
> payout run that had not started — and that cost was paid every time, for a protection another rule
> already provided.

**Rule W3a — What the user is told before committing is the whole design.** Because the amount can
shrink, the request screen and the confirmation must both say so **before** the user commits, in
plain terms: *whatever is available at the end of today will be sent, and trading during the day may
reduce it.* Every other change here is bookkeeping. Without this sentence, each partial transfer is
a complaint.

**Rule W4 — One open request at a time.** A second request cannot be placed while one is open. This
is now the rule that prevents a user committing the same money twice, and it carries that load alone.

**Rule W4a — A settled request closes, whatever it paid.** Sent in full, sent in part, or not at
all — the request ends and the user places a new one if they want the remainder. A request that
silently persisted into the next day would be one the user cannot see, cannot cancel, and will not
remember placing.

**Rule W4b — Submitting and settling are two events, and only the first has a user present.**
What the payout run decides — sent in full, sent in part, not sent, returned by the bank, queued
for the next run — happens during end-of-day processing hours later. **None of it may interrupt
the user**, because there is no user to interrupt. Each outcome reaches them as a message and as
a transaction that has changed by the time they next open the screen.

| At submission | At end of day |
|---|---|
| Accepted · after the cut-off · held for review | Sent in full · partly sent · nothing left · returned by the bank · banking network unavailable |
| Confirmed in front of the user | No dialog. Message plus a changed transaction |

**Rule W4c — Every settled outcome carries its reason on the transaction itself.** Finding it in
an email is not the same as finding it where the figure is. A partial transfer states the amount
requested against the amount sent and that the request is closed; a returned transfer states that
nothing was deducted and to check the account details with the bank.

**Rule W4d — Only a request the user made is titled a withdrawal outcome; the automatic return is
not.** Unused funds returned on the mandated calendar are labelled as an automatic settlement
alongside their status, so a user cannot mistake a scheduled return for a request they forgot
making.

**Rule W5 — Arrival time is a function of account state, not a constant.** The expected arrival is computed from: the route chosen, the time relative to the day's cut-off, whether the day is a working day on the trading calendar, whether the user has traded today, and whether any order is outstanding. Each factor that defers arrival is named to the user. A fixed promise of "within 24 hours" that is sometimes wrong is worse than a computed time that is sometimes longer.

**Rule W6 — (PARKED with REQ-304 — no faster route ships in this phase.) A faster route states its constraints in the choice, not in an error.** Its ceiling, its cost, its availability window and its qualifying conditions are visible at the point of choosing. Where the account does not qualify, the specific disqualifying condition is named — an open position, an order placed today, an amount above the ceiling — never a generic ineligibility.

**Rule W7 — A failed payout is returned by a compensating entry and never automatically resent.** Both the original and the return remain in the history. Resending to a destination that has just refused the money risks repeating the failure and, where the destination is wrong, risks sending money somewhere it should not go.

**Rule W8 — Some money leaves without being requested.** The mandated return of unused funds is not initiated by the user. It is announced before the date, executed on it, notified after it, and recorded in a way that distinguishes it from a movement the user asked for. A user who sees an unexplained outflow assumes an error or a theft; both are worse than the truth.

**Rule W9 — A mandated return and the user's own request settle as one.** Where the user's own request is open on the same date, both are met from the same available balance in a single payout. The same money is never sent twice.

**Rule W10 — Eligibility is confirmed twice: at request and at settlement.** The window between the two is where trading, a loss, a charge or a settlement change moves the figure — which under Rule W3 is expected rather than exceptional. At settlement the request is met from whatever is available: in full, in part, or not at all. What is never silent is the difference. A partial or nil settlement names the amount asked for, the amount sent, and the deduction accounting for the gap, which is the promise Rule W3a made before the user committed.

**Rule W11 — What was true at each decision is recorded.** The withdrawable figure at the moment of acceptance and at the moment of settlement are both retained, so that the question "why did I receive less than I asked for?" has an answer months later.

**Rule W12 — The destination is fixed at request.** The bank account a payout will reach is determined when the request is made. A later change to the user's accounts does not redirect a request already in flight; the request completes to its original destination or is refused.

---

## User Flows

### Flow 1: Withdraw while holding open positions

- **Persona:** Nikhil
- **Trigger:** The user wants to take profits or free cash while positions remain open.
- **Preconditions:** The account has a positive withdrawable figure and at least one open position.

**Main Flow (Happy Path)**

1. User opens the withdraw path → System presents the withdrawable figure with the largest deduction named (REQ-102), and the mandated return date if one is approaching (REQ-307).
2. User enters an amount within the withdrawable figure → System states the expected arrival time computed from this account's state, naming any cause of deferral (REQ-303).
3. User submits → System accepts the request (REQ-302, Rule W3). The withdrawable figure does not move, and the money stays tradable until the end-of-day payout run.
4. System sends the request at the next processing point → System re-evaluates eligibility immediately beforehand (REQ-308).
5. Money reaches the bank → System records it as paid with a reference the user can quote to their bank, and notifies them.

**Alternate Flows / Branches**

- **Branch A — a faster route exists:** *(parked with REQ-304 — no faster route ships in this phase; there is one route and one arrival date.)*
  1. System presents both routes with the faster one's ceiling, cost and window stated in the choice (REQ-304).
  2. User chooses the faster route → arrival time is restated for that route.
- **Branch B — the account does not qualify for the faster route:** *(parked with REQ-304.)*
  1. System names the specific disqualifying condition — an open position, an order placed today — rather than a generic ineligibility (Rule W6).
- **Branch C — the user changes their mind before sending:**
  1. User cancels → System closes the request. The withdrawable figure does not move, because it never moved when the request was made (REQ-305).
- **Branch D — the request is made after the day's cut-off, or on a non-working day:**
  1. System states the arrival time reflecting the next working day and names the cut-off or the calendar as the cause (Rule W5).

**Error / Exception Flows**

- **If the entered amount exceeds the withdrawable figure** → System refuses before submission, states the figure and offers its derivation (REQ-102), rather than accepting and failing later.
- **If the withdrawable figure falls before settlement, because of trading during the session or a loss** → System settles against what is available, states the amount requested against the amount sent and which deduction accounts for the gap, and notifies the user (REQ-308, Rule W3a, Rule W10).
- **If the money is sent and the bank refuses it** → System returns it by a compensating entry, states the reason, does not automatically resend, and states that the destination needs attention if that is the cause (REQ-306).
- **If the arrival time cannot be computed** → System states that rather than presenting a default duration as though it were computed (REQ-303).

**Postconditions / Success State**

Either the money has reached the user's bank with a reference they can quote, or the user knows precisely why it did not and their money is back in the account.

**Related Edge Cases**

"A user requests a withdrawal and places a trade in the same moment"; "The withdrawable figure falls between a request being accepted and the money being sent"; "A user's proven bank account changes between requesting a withdrawal and it being sent" — all in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 2: Withdraw from an account with nothing withdrawable

- **Persona:** Priya, Arun
- **Trigger:** The user looks for the way to take money out and there is none available.
- **Preconditions:** The account exists. The withdrawable figure is zero.

**Main Flow (Happy Path — the refusal is handled well)**

1. User opens the funds view → System presents the withdraw entry point as unavailable, with the responsible deduction named (REQ-301, Rule W1).
2. User selects it anyway → System does not absorb the interaction silently; it presents the reason and offers the derivation as the next step (Rule W2).
3. User opens the derivation → System presents every term of the withdrawable calculation with its explanation (REQ-102).
4. User identifies the deduction and when it will no longer apply → System has stated the date alongside the term.

**Alternate Flows / Branches**

- **Branch A — the balance is zero because the account is empty:**
  1. System presents the empty-account state and what would change it (REQ-504) rather than a withdrawal refusal.
- **Branch B — the balance is positive but entirely committed:**
  1. System names the commitment and points to the margin decomposition (REQ-106).
- **Branch C — the balance is positive but entirely from today's deposit or unsettled sales:**
  1. System states the date from which the money becomes withdrawable (Rule B6).
- **Branch D — the balance is negative:**
  1. System states the debt and offers the route to clear it in place of the withdraw path (REQ-301, REQ-501).

**Error / Exception Flows**

- **If the withdrawable figure cannot be computed** → System presents the withdraw path as unavailable with that stated as the reason, and permits no request. It never presents a figure it cannot derive.
- **If the derivation does not reconcile to the stated figure** → System presents the figure as unavailable rather than presenting a derivation that does not add up.
- **If the account is blocked from moving money entirely** → System names the blocker and the action that clears it (REQ-505), rather than presenting a withdrawal refusal that misattributes the cause.

**Postconditions / Success State**

The user knows that the product can return their money, why it cannot right now, and when that changes. No interaction has been silently absorbed.

**Related Edge Cases**

"The account balance is negative and the user attempts a withdrawal"; "A user holds pledged securities and no cash at all" — both in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 3: Money leaves without being asked

- **Persona:** Arun
- **Trigger:** A mandated settlement date arrives and unused funds are returned to the user's bank.
- **Preconditions:** The account holds a credit balance not required against any commitment.

**Main Flow (Happy Path)**

1. Ahead of the date → System states the next mandated return date on the funds view (REQ-307).
2. On the date → System returns the unused funds to the user's proven bank account.
3. System notifies the user with the amount and the reason.
4. User opens the account's history → System presents the movement as one the user did not request, visibly distinct from one they did (Rule W8).
5. User opens the entry → System explains, in plain language, why the money was returned and that it was required rather than chosen.

**Alternate Flows / Branches**

- **Branch A — the user has a pending request on the same date:**
  1. System settles the mandated return and the user's open request from the same available balance in one payout, and does not send the same money twice (REQ-307, Rule W9).
- **Branch B — the user has been inactive long enough for a different return rule to apply:**
  1. System applies the applicable rule and states which one, so the user is not told a quarterly cycle applies when a monthly one did.
- **Branch C — funds are retained against a commitment:**
  1. System returns only the unused portion and states what was retained and why.

**Error / Exception Flows**

- **If the return fails at the bank** → System returns the amount to the account by a compensating entry and notifies the user, exactly as for a requested payout (REQ-306).
- **If the user contacts support believing money was taken in error** → System has already recorded the notification and the explanation against the entry, so the answer is in the account rather than in an agent's knowledge.
- **If the calendar date cannot be established** → System does not execute a return on an unverified date; the calendar source is a stated dependency (EB-9).

**Postconditions / Success State**

The money has reached the user's bank, the user was told before and after, and the entry in their history explains itself without assistance.

**Related Edge Cases**

"A mandated settlement sweep falls due on the same day as a user's own withdrawal request" — in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

---

## Feature-Specific Edge Cases

- **The user withdraws the exact withdrawable figure to the last unit** → Expected: succeeds. A rounding difference that refuses an exact-maximum withdrawal is a defect, not a safeguard.
- **A withdrawal is requested while margin figures are stale** → Expected: refused, with staleness stated as the reason (REQ-107). Money does not leave against figures of unknown age.
- **A request is cancelled and re-made repeatedly in quick succession** → Expected: the withdrawable figure never moves, because no request ever holds money (Rule W3). At most one request is open at any moment (Rule W4), and the end-of-day run settles whichever one is open at that point.
- **A payout is recorded as paid and then fails at the bank** → Expected: two entries, both visible — the original stands and a return is added (Rule W7). Never a deletion.
- **The user's only proven bank account is removed while a request is in flight** → Expected: the request completes to its original destination or is refused; it is never redirected (Rule W12).
- **A faster-route request fails and the user immediately retries on the same route** *(parked with REQ-304)* → Expected: permitted only if the route's own rules allow a retry; where they do not, System states so and offers the standard route (REQ-304).
- **A mandated return would take the account to zero while an unexecuted order is outstanding** → Expected: funds required against the outstanding order are retained and the retention is stated (Flow 3, Branch C).
- **The quoted arrival time passes and the money has not arrived** → Expected: the request's status remains accurate rather than silently expiring; the user can see where it is and quote a reference. Quoted-versus-actual is recorded (REQ-303).
- **A withdrawal request is accepted, then the account is blocked from moving money before settlement** → Expected: nothing is sent and the request is closed with the blocker named. No figure needs restoring, because none had moved (REQ-308, REQ-505).
