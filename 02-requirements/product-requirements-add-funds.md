---
title: "FMS — Add Funds"
status: draft
version: "1.0"
part_of: product-requirements.md
---

# Add Funds

> Part 3 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.

This part owns money entering the account: choosing an amount, choosing a route with its cost and timing known in advance, the money's journey while in flight, and what happens when it does not arrive.

## Contents

- [Functional Requirements](#functional-requirements) — REQ-201 to REQ-207
- [Business Rules](#business-rules) — Rules A1 to A11
- [User Flows](#user-flows)
- [Feature-Specific Edge Cases](#feature-specific-edge-cases)

---

## Functional Requirements

### REQ-201 — Let the user choose an amount without being anchored (Must Have)

- **User Story:** As Priya, I want to choose a small first amount without being pushed toward a large one, so that I can start at a level I am comfortable with.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present the amount entry pre-filled with the most recent successfully added amount, and the funding account pre-selected to the one it came from, per Rule A1.
  - [ ] WHERE no successful deposit exists, THE SYSTEM SHALL present the amount entry empty.
  - [ ] THE SYSTEM SHALL allow the pre-filled amount to be edited or cleared without any additional step.
  - [ ] WHERE suggested amounts are offered, THE SYSTEM SHALL state whether selecting one sets the amount or adds to it, per Rule A2.
  - [ ] THE SYSTEM SHALL allow the amount to be reduced or cleared by the same means it can be increased.
  - [ ] THE SYSTEM SHALL state the minimum acceptable amount before the user has entered anything, not after they enter something below it.
  - [ ] WHEN the account owes money, THE SYSTEM SHALL offer the exact amount owed as a suggestion, per REQ-502.
  - [ ] THE SYSTEM SHALL accept only a well-formed money amount, and SHALL indicate the amount in a form the user can verify at a glance.

### REQ-202 — Show each route's cost and arrival time before the user commits (Must Have)

> **Reduced 19 Aug 26 — no fee is passed through.** `nbFee` is set to ₹0 and we absorb the gateway
> charge, so there is no cost to disclose in this phase and the obligation reduces to **the route used
> and when the money arrives**. The requirement itself is not relaxed: if a fee is ever configured
> above zero, the disclosure obligation applies again with no change to this text. Route selection is
> automatic via the gateway integration, per REQ-702.

- **User Story:** As Priya, I want to know what a deposit will cost and when it will arrive before I pay, so that I am not surprised afterwards.
> **Narrowed 20 Aug 26 — this requirement discloses; it no longer offers a choice.** It previously
> required every route to be presented, ordered and chosen from, which contradicted REQ-702's
> automatic selection outright. With `nbFee` at ₹0 the routes differ only by ceiling, so the choice
> asked the user to decide something they had no basis to decide. Selection is REQ-702's; disclosure
> — the arrival date before commitment, the route named after — is this requirement's, and the cost
> clauses below reactivate the moment a fee is configured above zero.

- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL state the expected arrival date for the entered amount before the user commits, per Rule A3, computed from the route carrying it.
  - [ ] WHEN a payment completes, THE SYSTEM SHALL name the route that carried it, per REQ-702.
  - [ ] WHERE a cost applies, THE SYSTEM SHALL state it before the user commits, and SHALL present the amount that will reach the account alongside the amount being paid, per Rule A3.
  - [ ] WHEN the entered amount exceeds the selected route's remaining headroom, THE SYSTEM SHALL change route automatically and SHALL say so, including any cost the change introduces, per Rule A12.
  - [ ] THE SYSTEM SHALL NOT use a route whose cost or arrival time cannot be stated, per Rule A3.
  - [ ] WHERE no route can carry the amount, THE SYSTEM SHALL state that before payment is attempted, state when a route is expected to return, and consume no attempt.
  - [ ] THE SYSTEM SHALL NOT require the user to choose a route, per REQ-702.

> **Dependency — resolved 19 Aug 26.** EB-3 is closed: UPI and netbanking are the v1 routes and their caps are in [Configuration](product-requirements-configuration.md#2-payment-routes). Outstanding is per-partner failure-reason fidelity, which REQ-205 depends on rather than this requirement.

### REQ-203 — Accept money only from an account the user has proven they hold (Must Have)

- **User Story:** As Priya, I want the system to only accept money from my own bank account, so that my funds cannot be mixed up with anyone else's.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL offer only bank accounts the user has proven they hold, per Rule A4.
  - [ ] THE SYSTEM SHALL present each such account in a form that identifies it to its owner without exposing its full number.
  - [ ] IF money arrives from an account the user has not proven they hold, THEN THE SYSTEM SHALL not treat it as a deposit to that user and SHALL reverse it, per REQ-206.
  - [ ] WHEN the user has proven no bank account, THE SYSTEM SHALL state that as the blocker and offer the action that resolves it, per REQ-505.
  - [ ] WHERE a route requires the paying account to be chosen in advance, THE SYSTEM SHALL record which account was nominated so the arriving money can be matched to it.
  - [ ] IF a payment arrives from a bank account not held in the Bank module for that user, THEN THE SYSTEM SHALL reject the transaction and SHALL state the reason — both as a message at the point of failure and as a reason recorded against the transaction itself, so it remains readable in history afterwards.

> **Source of truth, 19 Aug 26 — Profile.** The list of accounts a user has proven they hold is owned
> by **Profile**, and is already available. FMS reads that list and never adds to it. A user may fund
> only from an account held there; a payment from anywhere else is rejected with its reason shown at
> the time and retained at transaction level. **EB-5 is closed** — both the rule and the dependency.

### REQ-204 — Credit confirmed money at once and show money still in flight (Must Have)

- **User Story:** As Nikhil, I want confirmed money to be usable immediately, and money still travelling to be visible, so that I never wonder whether my payment vanished.
- **Acceptance Criteria:**
  - [ ] WHEN a payment is confirmed, THE SYSTEM SHALL record it and raise available margin, per Rule A5.
  - [ ] WHILE a payment is in flight, THE SYSTEM SHALL present it as in progress, with the amount, the route and the time it started.
  - [ ] THE SYSTEM SHALL NOT include money in flight in any of the three balances, per Rule A5.
  - [ ] IF a confirmation for the same payment is received more than once, THEN THE SYSTEM SHALL record the money once, per Rule A6.
  - [ ] WHEN a confirmation arrives after the user has stopped waiting, THE SYSTEM SHALL still record the money and SHALL notify the user, per Rule A7.
  - [ ] THE SYSTEM SHALL accept deposits at any hour, including when markets are closed, per Rule A8.

### REQ-205 — Explain a failed attempt and offer a way forward (Must Have)

- **User Story:** As Priya, I want a failed deposit to tell me what went wrong and what to try, so that I do not retry the same thing five times.
- **Acceptance Criteria:**
  - [ ] WHEN a payment fails, THE SYSTEM SHALL state the reason in language the user can act on, per Rule A9.
  - [ ] WHEN a payment fails, THE SYSTEM SHALL offer to retry with the same amount, and SHALL identify a different route where the reason suggests one.
  - [ ] IF money left the user's bank for a payment that failed, THEN THE SYSTEM SHALL state when it will be returned and by whom.
  - [ ] THE SYSTEM SHALL record every failed attempt in the account's history with its reason, per REQ-405.
  - [ ] WHERE the reason cannot be obtained, THE SYSTEM SHALL say so explicitly and offer a support route, rather than presenting an unexplained failure.
  - [ ] WHEN three attempts fail in succession, THE SYSTEM SHALL offer a support route alongside the retry rather than only the retry.

### REQ-206 — Reverse a payin that should not have been accepted (Must Have)

- **User Story:** As a compliance owner, I want money that should not have been accepted to be returned and recorded, so that the account's history remains a true account of what happened.
- **Acceptance Criteria:**
  - [ ] WHEN a recorded deposit is found to be invalid, THE SYSTEM SHALL reverse it by a compensating entry, per Rule A10.
  - [ ] THE SYSTEM SHALL NOT remove or alter the original entry, per Rule A10.
  - [ ] THE SYSTEM SHALL present both entries in the account's history, paired, per REQ-404.
  - [ ] WHEN a reversal takes the account into debit, THE SYSTEM SHALL treat that as a debt and hand off to REQ-501, rather than blocking the reversal.
  - [ ] WHEN a deposit is reversed, THE SYSTEM SHALL notify the user with the reason and the amount.

### REQ-207 — Treat funding during a shortfall as urgent (Must Have)

- **User Story:** As Nikhil, I want my deposit to relieve a margin shortfall immediately, so that my positions are not squared off while my money sits in transit.
- **Acceptance Criteria:**
  - [ ] WHILE the account has a margin shortfall, THE SYSTEM SHALL present the shortfall amount as a suggested deposit amount and identify the fastest available route, per Rule A11.
  - [ ] WHEN a payment is confirmed while a shortfall exists, THE SYSTEM SHALL apply it to the shortfall without waiting for any scheduled process.
  - [ ] WHILE a shortfall exists, THE SYSTEM SHALL state the time remaining before positions may be closed, per REQ-506.
  - [ ] WHEN a confirmed payment clears the shortfall, THE SYSTEM SHALL state that it is cleared.

---

## Business Rules

**Rule A1 — The amount opens on what was last added, from the account it was last added from.** Adding funds is a repeated act, and the amount is stable far more often than it varies, so an empty field asks the same question every visit and receives the same answer every visit. The pre-filled value is a **default, not a decision**: it is editable in place, the suggestion pills adjust it, and clearing it costs one keystroke. Where no successful deposit exists there is nothing to default to, and the field opens empty.

> An earlier version of this rule forbade pre-filling on the grounds that a pre-filled value is a suggestion the user did not ask for, occupying the position of a decision already made. That holds for a value we invent; it does not hold for the user's own last action, which is a fact about them rather than a guess about them. The safeguard that matters is that the amount stays trivially editable, which the acceptance criteria above require.

**Rule A2 — A suggestion states what it does.** Where suggested amounts are offered, each states plainly whether selecting it sets the amount to that value or adds that value to what is already entered. Both behaviours are legitimate; presenting one while behaving as the other is not.

**Rule A3 — A route is used only where its cost, its ceiling and its arrival time are known.** Three facts must be established before the user commits: what the payment costs for the amount entered, the largest amount the route can carry, and when the money will be usable. A route whose cost or timing cannot be established is not used, because using it means the user discovers those facts afterwards. The user is not asked to choose between routes — REQ-702 selects — so what this rule governs is what must be *known* to select, and what must be *stated* before the user commits: the arrival date beforehand, the route that carried the payment afterwards, and any cost in both places.

**Rule A4 — Money may only enter from an account the user has proven they hold.** This is not a product choice. Money arriving from any other source is not the user's deposit, regardless of the amount or the sender's name, and is handled under Rule A10.

**Rule A5 — Money exists in the balances only once it is confirmed.** A payment in flight is visible, is attributable to the user, and affects no balance. The moment it is confirmed it affects all three balances that apply to it — noting that under Rule B4 it raises the ledger balance and available margin but not the withdrawable figure until the following settlement day.

**Rule A6 — A payment is recorded once, however many times it is confirmed.** Repeat confirmations for one payment are an expected condition. The second and subsequent confirmations change nothing and produce no additional entry.

**Rule A7 — Money that reached the firm is never discarded because the user stopped watching.** A confirmation arriving after the user abandoned the attempt, closed the session, or was told the attempt had lapsed still results in the money being recorded and the user being told.

**Rule A8 — Deposits are accepted at any hour.** Markets being closed does not prevent money entering the account. What the money can then do is governed by the settlement rules in Rule B4, not by the time it arrived.

**Rule A9 — A failure states a reason the user can act on.** "Failed" is not a reason. The reason distinguishes at minimum: the user cancelled; the bank refused; the route was unavailable; the amount exceeded a limit; the attempt timed out with the outcome unknown. The last of these is distinct from a failure and is stated as unknown rather than as failed.

**Rule A9a — A well-formed request can still fail in six ways, and each has its own answer.**
Bank declined · not enough in the bank · above the bank's own per-payment limit · no answer from
the bank · our service unreachable · cancelled before approval. They are not interchangeable: the
recovery differs, and so does whose problem it is. Copy for each is in
[Communications §4.2](product-requirements-communications.md).

**Rule A9b — Never say "failed" when the outcome is unknown.** A bank that has not answered is its
own state, because the recovery is the opposite of a failure's: **wait, and specifically do not
retry**. It is titled *Awaiting confirmation*, it withholds every action for 30 seconds, and it is
the one outcome that does not restore the amount to the field.

**Rule A9c — Name whose problem it is.** Our outage reported as "your bank declined" sends the
user to a bank that cannot help and will not send them back to us.

**Rule A9d — A recovery action must be able to work.** The alternative route offered beside
*Try Again* is only shown if we can execute it and its remaining daily headroom covers the amount.
A self-service route is never offered — the button would promise a payment and deliver
instructions. Where nothing qualifies, *Try Again* stands alone.

**Rule A12 — Route limits are daily, per route, and enforced against what is already spent today.**
Enforcing per transaction while telling the user the limit is daily lets them pass the same amount
twice and be refused by their bank instead of by us. Where an amount exceeds the selected route's
remaining headroom and another executable route can carry it, the route changes automatically and
says so, including any fee the change introduces.

**Rule A13 — A money field never acts on a value it did not display.** Anything but digits and a
single decimal point is refused at the keystroke, paste included, rather than accepted and
corrected afterwards. An earlier parser stripped a leading minus, so `-500` silently became ₹500
and the button offered to add a number the user had not typed.

**Rule A10 — A deposit is reversed, never deleted.** Where a recorded deposit must be undone — money from an account the user has not proven they hold, a reversal by the paying bank, a duplicate that escaped Rule A6 — a compensating entry is added and both remain visible. The account may legitimately go into debit as a result if the money was already used; that is handled as a debt, not prevented by refusing the reversal.

**Rule A11 — A shortfall changes what the funding path is for.** While the account is short of what its positions require, the funding path leads with the shortfall amount and the fastest route, because the user's deadline is set by the risk process rather than by their own convenience.

---

## User Flows

### Flow 1: Fund an account for the first time

- **Persona:** Priya
- **Trigger:** The user wants to buy something and has no money in the account.
- **Preconditions:** The account can receive money, and the user has proven at least one bank account.

**Main Flow (Happy Path)**

1. User opens the funds view → System presents the empty-account state with what the account can do once funded and the smallest useful amount (REQ-504).
2. User starts a deposit → System presents an empty amount entry with the minimum stated (REQ-201).
3. User enters an amount → System selects the route automatically against the amount and today's remaining headroom, and states the expected arrival date for that route before the user commits (REQ-702, REQ-202, Rule A3).
4. User confirms the funding account → System offers only accounts the user has proven they hold, and uses the single account without presenting a choice where only one exists (REQ-203, REQ-706a).
5. User authorises the payment → System presents the attempt as in progress, affecting no balance (REQ-204).
6. Payment is confirmed → System records the money, raises available margin, and states that it is available to trade with but not withdrawable until the next settlement day (Rules A5, B4).

**Alternate Flows / Branches**

- **Branch A — the entered amount exceeds the selected route's remaining headroom for today:**
  1. System changes route automatically to one that can carry the amount and says so, including any cost the change introduces, before payment is attempted (REQ-702, Rule A12).
  2. Where no route can carry the amount, System states that and consumes no attempt (REQ-202).
- **Branch B — the user selects a suggested amount:**
  1. System applies it as stated — set or add — and the entry remains editable and clearable (REQ-201, Rule A2).
- **Branch C — the confirmation arrives after the user has left:**
  1. System records the money anyway and notifies the user (Rule A7).
- **Branch D — the account owes money:**
  1. System offers the exact amount owed as a suggestion and permits it even if it is below the minimum (REQ-502).

**Error / Exception Flows**

- **If the user has proven no bank account** → System states that as the single blocker and offers the action that resolves it (REQ-505) → User completes it elsewhere and returns.
- **If the payment fails** → System states the reason and offers a retry, identifying a different route where the reason suggests one (REQ-205) → User retries on the suggested route.
- **If money left the user's bank but no confirmation arrives** → System presents the attempt as in progress with its start time, affecting no balance → System states when the outcome will be established and when the money returns if it failed.
- **If every route is unavailable** → System states this plainly with an expected return time and consumes no attempt, rather than presenting routes that will fail.

**Postconditions / Success State**

The money is recorded, available margin has risen, the user has been told what the money can and cannot yet be used for, and the deposit appears in the account's history with its route and cost.

**Related Edge Cases**

"A deposit confirmation arrives twice for one payment"; "A deposit confirmation arrives after the user has abandoned the attempt"; "Every payment route is unavailable at once" — all in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 2: Fund urgently to relieve a shortfall

- **Persona:** Nikhil
- **Trigger:** The account is short of what its open positions require.
- **Preconditions:** The account holds open positions and a shortfall has been identified.

**Main Flow (Happy Path)**

1. User is notified of the shortfall → System states the amount short and the time remaining before positions may be closed (REQ-506).
2. User opens the funding path → System leads with the shortfall amount pre-selected as a suggestion and the fastest available route identified (REQ-207, Rule A11).
3. User authorises the payment → System presents it as in progress with the deadline still visible.
4. Payment is confirmed → System applies it to the shortfall immediately, without waiting for any scheduled process (REQ-207).
5. Shortfall is cleared → System states that it is cleared and restates the time it was cleared at.

**Alternate Flows / Branches**

- **Branch A — the deposit is smaller than the shortfall:**
  1. System applies it, restates the remaining shortfall and the remaining time, and keeps the funding path in its urgent state.
- **Branch B — the fastest route's ceiling is below the shortfall:**
  1. System states this and presents the combination of routes that would cover it, with each one's arrival time.
- **Branch C — the shortfall is cleared by a market move before the payment confirms:**
  1. System states that the shortfall no longer exists, and the deposit is recorded normally.

**Error / Exception Flows**

- **If the payment fails while the deadline is approaching** → System states the reason and immediately offers the next-fastest route rather than the same one (REQ-205).
- **If the margin figures are stale** → System states that the shortfall figure may not be current, with its computed-at time (REQ-107), and does not suppress the warning on that basis.
- **If positions are closed before the payment confirms** → System records the deposit normally, states that the shortfall was resolved by position closure rather than by the deposit, and the money remains in the account.

**Postconditions / Success State**

Either the shortfall is cleared and the user has been told, or the user knows exactly how much is still short and how long they have.

**Related Edge Cases**

"The margin figures are stale because their source has not reported" — in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 3: A deposit that does not complete

- **Persona:** Priya, Arun
- **Trigger:** A payment is authorised but does not result in money being recorded.
- **Preconditions:** A deposit attempt has been started.

**Main Flow (Happy Path — the failure is handled well)**

1. Payment does not complete → System establishes which of the outcomes in Rule A9 applies.
2. System states the reason in language the user can act on (REQ-205).
3. System offers a retry with the same amount, and identifies a different route where the reason suggests one.
4. Where money left the user's bank, System states when it will be returned and by whom.
5. User retries on the suggested route → the flow rejoins Flow 1 at step 5.

**Alternate Flows / Branches**

- **Branch A — the outcome is unknown rather than failed:**
  1. System presents the attempt as in progress with an unknown outcome, distinct from a failure (Rule A9).
  2. System does not offer a retry until the outcome is established, so that the user cannot pay twice for one intent.
- **Branch B — three attempts have failed in succession:**
  1. System offers a support route alongside the retry (REQ-205), because a fourth identical attempt is unlikely to differ.
- **Branch C — the failure is caused by the route rather than the user or their bank:**
  1. System identifies the route as the cause and presents an alternative first.

**Error / Exception Flows**

- **If the reason cannot be obtained** → System says so explicitly and offers a support route, rather than presenting an unexplained failure (REQ-205).
- **If the money is later confirmed after being reported as failed** → System records the money, corrects the earlier status in the history, and notifies the user (Rule A7).
- **If the user's money is returned by their bank but the deposit was already recorded** → System reverses by a compensating entry with both entries visible (REQ-206, Rule A10).

**Postconditions / Success State**

The user knows what happened, whether their money is at risk, when it returns if it is not, and what to try next. The attempt and its reason are in the account's history.

**Related Edge Cases**

"A deposit is confirmed and later found to have come from an account the user does not hold" — in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

---

## Feature-Specific Edge Cases

- **The user enters an amount below the minimum while the account owes less than the minimum** → Expected: permitted, because REQ-502 requires a debt to be clearable exactly. The minimum applies to funding, not to settling a debt.
- **A route's cost changes between being displayed and the payment being made** → Expected: the payment completes at the cost displayed, or is refused and re-quoted. It never completes at an undisclosed cost.
- **The user pays from a bank account they have proven, but the money arrives bearing a different name** → Expected: treated as arriving from an unproven source under Rule A4 and handled by REQ-206, not credited on the basis of the account number alone.
- **Two deposits are authorised in quick succession for the same amount** → Expected: both are real and both are recorded. Rule A6 deduplicates confirmations of one payment, not two payments that happen to match.
- **A deposit is confirmed for a different amount than was requested** → Expected: the amount confirmed is recorded, the difference is stated to the user, and the attempt is flagged for review rather than silently reconciled.
- **A deposit is confirmed while the account is being blocked from receiving money** → Expected: the money is recorded — it has already arrived — and the blocker is presented alongside it. Money that reached the firm is never discarded (Rule A7).
- **The suggested shortfall amount would over-fund the account** → Expected: presented as a suggestion with the shortfall named; the user may reduce it. A suggestion is never a floor.
- **The user starts a deposit, abandons it partway, and returns to find an amount already entered** → Expected: the field opens on the last amount that was *successfully added*, never on the abandoned attempt (Rule A1). A completed deposit is a fact about what this user funds; an abandoned attempt is not, and carrying one forward would restore the anchor Rule A1 exists to avoid. Where no successful deposit exists, the field opens empty however many attempts were abandoned.
