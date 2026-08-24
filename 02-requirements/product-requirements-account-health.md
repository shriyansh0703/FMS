---
title: "FMS — Account Health"
status: draft
version: "1.0"
part_of: product-requirements.md
---

# Account Health

> Part 6 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.

This part owns the states users are actually in when they arrive: empty, blocked, in debt, or about to be forced out of a position. These are the states every product examined for this PRD leaves undesigned, and they are where users get stuck.

## Contents

- [Functional Requirements](#functional-requirements) — REQ-501 to REQ-506
- [Business Rules](#business-rules) — Rules H1 to H8
- [User Flows](#user-flows)
- [Feature-Specific Edge Cases](#feature-specific-edge-cases)

---

## Functional Requirements

### REQ-501 — Tell the user they owe money, why, and what it is costing (Must Have)

- **User Story:** As Arun, I want to know that I owe money, why I owe it, and what it is costing me, so that I can decide what to do about it.
- **Acceptance Criteria:**
  - [ ] WHILE the account balance is negative, THE SYSTEM SHALL present it as an amount owed rather than as an amount available, per Rule H1.
  - [ ] THE SYSTEM SHALL state the cause of the debt in plain language, naming the entry that created it.
  - [ ] THE SYSTEM SHALL state the rate at which the debt is accruing and the amount accrued so far.
  - [ ] WHEN an account first enters a negative balance, THE SYSTEM SHALL notify the user, per Rule H2.
  - [ ] WHILE the account remains in debt, THE SYSTEM SHALL notify the user on a recurring basis, per Rule H2.
  - [ ] THE SYSTEM SHALL present the route to clear the debt alongside the statement of it, per REQ-502.
  - [ ] THE SYSTEM SHALL NOT present a negative balance using the same treatment as a positive one, per Rule H1.

> **Dependency:** the rate to be stated depends on EB-8, which is unresolved. The obligation to state it is not conditional. See [Engineering Digest](product-requirements.md#engineering-digest).

### REQ-502 — Let the user clear dues exactly, below any minimum (Must Have)

- **User Story:** As Arun, I want to pay exactly what I owe, so that clearing a small debt does not require me to deposit more than it.
- **Acceptance Criteria:**
  - [ ] WHILE the account owes money, THE SYSTEM SHALL offer to pay the exact amount owed, per Rule H3.
  - [ ] THE SYSTEM SHALL permit that amount even where it is below the minimum deposit, per Rule H3.
  - [ ] THE SYSTEM SHALL state the amount owed at the moment of payment, including any accrual since it was last displayed.
  - [ ] WHEN a payment clears the debt exactly, THE SYSTEM SHALL confirm that the account is no longer in debt.
  - [ ] WHERE the user pays more than is owed, THE SYSTEM SHALL apply the excess as a normal balance and state that it has done so.
  - [ ] WHERE a route's own minimum prevents the exact amount, THE SYSTEM SHALL identify a route that permits it, per Rule H3.

### REQ-503 — Warn before a predictable charge puts the account into debit (Should Have)

- **User Story:** As Arun, I want to be warned before a scheduled charge takes my account negative, so that I can prevent the debt rather than discover it.
- **Acceptance Criteria:**
  - [ ] WHERE a charge is scheduled and its amount is known, THE SYSTEM SHALL state it in advance with its date, per Rule H4.
  - [ ] IF a scheduled charge would take the balance below zero, THEN THE SYSTEM SHALL notify the user before the charge date.
  - [ ] THE SYSTEM SHALL state the amount needed to prevent the account entering debt.
  - [ ] WHERE the account has been returned to zero by a mandated settlement, THE SYSTEM SHALL account for scheduled charges falling due afterwards, per Rule H4.

> **Dependency — source resolved 19 Aug 26.** Charge information originates in the **TechExcel back
> office**, where it posts to the ledger; FMS reads it from there and surfaces it on the relevant
> screens. FMS does not compute, schedule or own any charge.
>
> EB-6 is therefore no longer a question of *who owns the charge schedule* but of *what TechExcel
> exposes and when* — specifically, whether a charge is readable **before** it posts. REQ-501 needs
> only charges that have already posted, and is unaffected. REQ-503 is the preventive half and needs
> forward visibility of a scheduled charge and its date; it remains Should-Have until TechExcel
> confirms that a not-yet-posted charge is exposed at all.

### REQ-504 — Give an empty account a purpose (Must Have)

- **User Story:** As Priya, I want an empty account to tell me what to do next, so that I am not left guessing whether the product is broken or I am.
- **Acceptance Criteria:**
  - [ ] WHILE the account holds no money and owes none, THE SYSTEM SHALL present a single statement of that state rather than repeating a zero for every component, per Rule H5.
  - [ ] THE SYSTEM SHALL state what the account will be able to do once funded.
  - [ ] THE SYSTEM SHALL state the smallest amount that is useful to deposit, per Rule H5.
  - [ ] THE SYSTEM SHALL present the action that funds the account as the primary action available.
  - [ ] THE SYSTEM SHALL NOT present a full decomposition of margin components for an account with no money, per Rule H5.
  - [ ] WHERE the account has held money previously, THE SYSTEM SHALL make its history reachable from the empty state, per Rule H5.

### REQ-505 — Name the blocker when the account cannot receive money (Must Have)

- **User Story:** As Priya, I want to be told the single thing standing between me and funding my account, and be taken to it, so that I am not left at a dead end.
- **Acceptance Criteria:**
  - [ ] WHILE the account cannot receive money, THE SYSTEM SHALL name the specific blocker, per Rule H6.
  - [ ] THE SYSTEM SHALL present the action that resolves the blocker as the primary action available.
  - [ ] THE SYSTEM SHALL NOT present a funding path that cannot complete, per Rule H6.
  - [ ] WHERE the blocker is being resolved already, THE SYSTEM SHALL state its current state and the expected time to resolution.
  - [ ] WHEN more than one blocker exists, THE SYSTEM SHALL name the one the user must resolve first.
  - [ ] WHEN a blocker is resolved, THE SYSTEM SHALL restore the funding path without requiring the user to find it again.

### REQ-506 — Warn while a shortfall can still be fixed (Must Have)

> **Promoted to Must-Have 20 Aug 26.** It was marked Should-Have here while appearing in neither
> the index's Must-Have list nor its Should-Have list, and shipping in Phase 4 with both halves.
> A requirement in no priority bucket is one the register cannot account for.

- **User Story:** As Nikhil, I want to know about a margin shortfall while I can still act on it, so that my positions are not closed for me.
- **Acceptance Criteria:**
  - [ ] WHILE the account is short of what its positions require, THE SYSTEM SHALL present the shortfall amount and the time remaining before positions may be closed, per Rule H7.
  - [ ] THE SYSTEM SHALL present funding the shortfall as the primary action, per REQ-207.
  - [ ] THE SYSTEM SHALL state the cause of the shortfall, distinguishing one the user caused from one caused by a market move, per Rule B8.
  - [ ] WHILE a shortfall exists, THE SYSTEM SHALL deduct it in the withdrawable derivation as its own named term, so the figure the user sees and the derivation behind it reconcile, per Rule B4.
  - [ ] WHERE the outstanding shortfall equals or exceeds the balance, THE SYSTEM SHALL present the withdrawable figure as zero and SHALL name the shortfall as the deduction responsible, per REQ-102.
  - [ ] WHERE a shortfall is forecast rather than current, THE SYSTEM SHALL present it as a forecast and state what would cause it. *(Second phase; see [Future Scope](product-requirements.md#future-scope).)*
  - [ ] WHEN positions are closed to resolve a shortfall, THE SYSTEM SHALL record the resulting entries with that stated as their cause, per Rule H8.

---

## Business Rules

**Rule H1 — A debt is never presented as availability.** A negative balance is money owed. It is labelled as owed, given a treatment visually distinct from a positive balance, and never carried under a heading such as "available". This rule exists because a live product was observed presenting a balance of −₹24.37 under the label "Available for Investing", with no notification, cause or route to pay.

**Rule H2 — A debt is announced, not merely displayed.** The user is notified when the account first goes negative and on a recurring basis while it stays negative. A debt that accrues a charge every week while the user is not looking is a debt the product created the conditions for. Display alone is not disclosure for a user who has no reason to log in.

**Rule H3 — The exact amount owed is always payable.** A minimum deposit is a commercial floor for funding; it must never prevent a user settling what they owe. Where a route imposes its own minimum, a route that permits the exact amount is identified. A user unable to pay ₹24.37 because the minimum is ₹50 must either overpay or stay in debt, and both are the product's failure rather than theirs.

**Rule H4 — A predictable charge is announced before it posts.** Where a charge is scheduled and its amount is known, it is stated in advance with its date. The dormant-account debt path is entirely predictable: a mandated settlement returns the balance to zero, a scheduled charge posts against that zero, and an accrual begins. Every step is known in advance, which makes the resulting debt preventable rather than merely explainable.

**Rule H5 — An empty account states its state once and offers a purpose.** One statement that the account is empty, one statement of what it will do once funded, one statement of the smallest useful amount, and one action. Not a full decomposition of margin components all reading zero. This rule exists because a live product was observed rendering fifteen instances of ₹0.00 across two cards with no acknowledgement that the account was empty. Where the account has held money before, its history remains reachable — an empty balance is not an empty account.

**Rule H6 — A blocked account shows the blocker, not a blocked path.** Where the account cannot receive money, the funding path is replaced by the blocker and the action that clears it — not presented alongside it in a disabled state. A live, responsive amount entry leading to a permanently disabled button is an interface that says "go" four times and "stop" once, in the smallest text on the screen.

**Rule H7 — A shortfall is a deadline, and the deadline is stated.** The user is told the amount short and the time remaining before positions may be closed on their behalf. A warning without a deadline does not convey urgency; a deadline without an amount does not convey what to do.

**Rule H8 — Money moved on the user's behalf is recorded as such.** Where positions are closed to resolve a shortfall, the resulting entries state that as their cause. An outflow the user did not choose is never indistinguishable from one they did.

---

## User Flows

### Flow 1: Discover and clear a debt I did not create

- **Persona:** Arun
- **Trigger:** The user returns to a dormant account, or receives a notification that the account is in debt.
- **Preconditions:** The account balance is negative.

**Main Flow (Happy Path)**

1. Account goes negative → System notifies the user with the amount, the cause and the accrual (REQ-501, Rule H2). The user does not have to log in to find out.
2. User opens the funds view → System presents the amount owed under a treatment distinct from a positive balance, never as availability (Rule H1).
3. System states the cause in plain language, naming the entry that created it, and states the rate and the amount accrued so far (REQ-501).
4. User selects the route to clear it → System offers the exact amount owed, including accrual since it was last displayed (REQ-502).
5. User pays the exact amount, even though it is below the minimum deposit (Rule H3) → System confirms the account is no longer in debt.

**Alternate Flows / Branches**

- **Branch A — the user wants to understand how it happened before paying:**
  1. User opens the history → System presents the sequence: the mandated return that emptied the account, the charge that posted against zero, and each accrual since, each paired and in plain language (REQ-401, REQ-404).
- **Branch B — the user pays more than is owed:**
  1. System applies the excess as a normal balance and states that it has done so (REQ-502).
- **Branch C — the chosen route will not carry an amount that small:**
  1. System identifies a route that permits the exact amount (Rule H3) rather than requiring the user to overpay.
- **Branch D — the debt was created by a reversal of a deposit that had already been used:**
  1. System states that as the cause (REQ-206) rather than presenting an unexplained debt.

**Error / Exception Flows**

- **If the accrual rate is not available to display** → System states the debt and that the accrual figure is unavailable, rather than presenting the debt without indicating that it is growing.
- **If the payment fails** → System states the reason and offers a retry on a route that permits the amount (REQ-205), keeping the debt statement visible throughout.
- **If the debt grows between being displayed and being paid** → System states the new amount at the moment of payment (REQ-502) rather than accepting a payment that leaves a residue the user did not know about.
- **If the user attempts to withdraw while in debt** → System states the debt and offers the route to clear it in place of the withdraw path (REQ-301).

**Postconditions / Success State**

The account is at or above zero, the user understands what happened and why, and the whole sequence is legible in their history.

**Related Edge Cases**

"The account balance is negative and the user attempts a withdrawal"; "A deposit is confirmed and later found to have come from an account the user does not hold" — both in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 2: Arrive at an account that cannot yet hold money

- **Persona:** Priya
- **Trigger:** A new user opens the funds view before the account is able to receive money.
- **Preconditions:** The account exists but at least one blocker prevents it receiving money.

**Main Flow (Happy Path)**

1. User opens the funds view → System presents the named blocker in place of the funding path (REQ-505, Rule H6).
2. System states what completing it will unlock, in one line.
3. System presents the action that resolves the blocker as the primary action.
4. User completes it elsewhere and returns → System restores the funding path without requiring the user to find it again (REQ-505).
5. User funds the account → the flow continues into [Add Funds Flow 1](product-requirements-add-funds.md#flow-1-fund-an-account-for-the-first-time).

**Alternate Flows / Branches**

- **Branch A — the blocker is already being resolved:**
  1. System states its current state and the expected time to resolution, rather than presenting it as not started (REQ-505).
- **Branch B — more than one blocker exists:**
  1. System names the one the user must resolve first, so the user has one action and not a list (REQ-505).
- **Branch C — the account is unblocked but empty:**
  1. System presents the empty-account state: one statement, what it will do once funded, the smallest useful amount, one action (REQ-504, Rule H5).
- **Branch D — the account is empty now but has held money before:**
  1. System makes the history reachable from the empty state (Rule H5), because an empty balance is not an empty account.

**Error / Exception Flows**

- **If money arrives while the account is blocked** → System records it, because it has already arrived, and presents the blocker alongside it (Rule A7). Money that reached the firm is never discarded.
- **If the blocker cannot be identified** → System states that the account cannot currently receive money and offers a support route, rather than presenting a funding path that will fail.
- **If the blocker is resolved but the funding path does not become available** → System states the account's current state and the time it was last checked, rather than presenting a stale block.

**Postconditions / Success State**

The user knows the one thing standing between them and a funded account, and has been taken to it. No dead end, and no interactive path that cannot complete.

**Related Edge Cases**

"A user opens the funds view during a period when the account cannot receive money at all"; "A deposit is confirmed while the account is being blocked from receiving money" — both in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

---

## Feature-Specific Edge Cases

- **The debt is smaller than the smallest unit a route can carry** → Expected: a route that can carry it is identified, or the debt is stated as not currently payable with a support route offered. The user is never left owing an amount the product will not accept.
- **The account goes negative and positive repeatedly within one day** → Expected: notified on first entering debt and not on every oscillation. Rule H2 requires the user be told, not that they be told repeatedly for the same event.
- **A user clears a debt and a further accrual posts for the period before clearance** → Expected: the residual is stated at the moment of payment (REQ-502) so it does not appear afterwards as a surprise. Where it cannot be, the user is notified that a residual followed.
- **An empty account has never held money and has no history** → Expected: the empty state omits the history route rather than offering an empty one (Rule H5).
- **A shortfall and a debt exist at the same time** → Expected: the shortfall is presented first, because it has a deadline attached and the debt does not.
- **A shortfall is resolved by a market move rather than by the user** → Expected: stated as resolved with the cause named, so the user does not fund an account against a warning that no longer applies.
- **Positions are closed to resolve a shortfall while a deposit is in flight** → Expected: both are recorded, the closure states its cause (Rule H8), and the deposit is recorded normally. The user is told which resolved the shortfall.
- **A blocker is resolved for one purpose but not another — the account can receive money but not send it** → Expected: each direction states its own state; a funding path is not presented as blocked because the withdrawal path is.
- **A scheduled charge is announced and then does not post** → Expected: the announcement is corrected rather than left standing, so the user does not fund an account against a charge that never arrived.
