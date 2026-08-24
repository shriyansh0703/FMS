---
title: "FMS — Balances & Margin"
status: draft
version: "1.0"
part_of: product-requirements.md
---

# Balances & Margin

> Part 2 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.

This part owns the central idea of the whole document: a broking account answers "how much do I have?" with three different numbers, and the product's job is to name them apart and explain the gap. Every other part computes against what is defined here.

## Contents

- [Functional Requirements](#functional-requirements) — REQ-101 to REQ-108
- [Business Rules](#business-rules) — Rules B1 to B12
- [User Flows](#user-flows)
- [Feature-Specific Edge Cases](#feature-specific-edge-cases)

---

## Functional Requirements

### REQ-101 — Show three distinct balances and never conflate them (Must Have)

- **User Story:** As Nikhil, I want each balance figure to have its own name and its own meaning, so that I know which question I am looking at the answer to.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present the ledger balance, the available margin and the withdrawable balance as three separately named figures, per Rule B1.
  - [ ] THE SYSTEM SHALL use exactly one name for each figure everywhere it appears, per Rule B2.
  - [ ] WHEN two of the three figures are equal, THE SYSTEM SHALL still present them separately rather than collapsing them into one.
  - [ ] THE SYSTEM SHALL NOT define any of the three figures in terms of another of the three, per Rule B3.
  - [ ] WHEN the account holds no money, THE SYSTEM SHALL present the three figures with a single statement of the account's state rather than repeating a zero for each component.

### REQ-102 — Explain the withdrawable figure line by line (Must Have)

- **User Story:** As Nikhil, I want to see why my withdrawable amount is lower than my balance, so that I stop guessing and stop contacting support.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL make the derivation of the withdrawable figure reachable from that figure in one interaction.
  - [ ] THE SYSTEM SHALL show every term of the derivation defined in Rule B4, including terms whose value is zero, each with its own plain-language explanation.
  - [ ] THE SYSTEM SHALL state the sign of each term, so that a term that increases the withdrawable figure is distinguishable from one that reduces it.
  - [ ] WHEN the withdrawable figure is lower than the ledger balance, THE SYSTEM SHALL name the largest single deduction without requiring the user to open the full derivation.
  - [ ] WHEN the withdrawable figure is zero, THE SYSTEM SHALL state which deduction is responsible rather than stating only that the figure is zero.
  - [ ] WHEN a deduction exists because of a settlement holiday, THE SYSTEM SHALL name the holiday as the cause, per Rule B6.

### REQ-103 — Decompose available margin into its named components (Must Have)

- **User Story:** As Nikhil, I want to see what my available margin is made of, so that I can tell whether a change came from my own action or from the market.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present available margin as a set of named contributing and reducing components, per Rule B7.
  - [ ] THE SYSTEM SHALL present the components such that they sum to the stated total.
  - [ ] WHERE a component's value cannot be obtained, THE SYSTEM SHALL state that it is unavailable rather than presenting it as zero, per Rule B10.
  - [ ] WHILE the account has no activity in a component, THE SYSTEM SHALL allow that component to be collapsed from view without being removed from the derivation.
  - [ ] WHEN a component's value is negative, THE SYSTEM SHALL present it as negative rather than clamping it to zero, per Rule B9.

### REQ-104 — Show collateral separately and never as withdrawable (Must Have)

- **User Story:** As Nikhil, I want pledged securities shown apart from cash, so that I never mistake margin I can trade with for money I can take out.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present the cash portion and the collateral portion of available margin as separate figures.
  - [ ] THE SYSTEM SHALL exclude collateral entirely from the withdrawable figure, per Rule B5.
  - [ ] THE SYSTEM SHALL distinguish collateral that counts toward a cash requirement from collateral that does not, per Rule B5.
  - [ ] WHEN the account holds collateral and no cash, THE SYSTEM SHALL state that the account can trade and cannot withdraw, and why.
  - [ ] WHEN a collateral value changes without any user action, THE SYSTEM SHALL make the change visible in the account's history, per Rule B8.

### REQ-105 — Answer how much can be deployed on each kind of trade (Should Have)

- **User Story:** As Nikhil, I want to know how much I can commit to a specific kind of trade, so that I do not discover a restriction at the moment I place an order.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL state, for each kind of trade the account is enabled for, how much of the available margin can be committed to it.
  - [ ] WHERE a kind of trade requires a cash portion, THE SYSTEM SHALL state the cash amount available for it separately from the total, per Rule B5.
  - [ ] WHEN a figure for one kind of trade is lower than the overall available margin, THE SYSTEM SHALL state the restriction responsible.
  - [ ] WHERE the account is not enabled for a kind of trade, THE SYSTEM SHALL omit it rather than presenting it as zero.

### REQ-106 — Show blocked money by funding source and commitment state (Must Have)

- **User Story:** As Nikhil, I want to see what my committed money is committed to, so that I can work out what to close in order to free some up.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present blocked money split by whether it was met from cash or from collateral, and by whether it is committed to an open position or to an order not yet executed, per Rule B7.
  - [ ] THE SYSTEM SHALL present each named margin component that makes up the blocked total.
  - [ ] WHEN money is released from a commitment, THE SYSTEM SHALL reflect the release in the same components that recorded the block, per Rule B7.
  - [ ] WHEN blocked money exists against an order that has not executed, THE SYSTEM SHALL present it separately from money committed to a position, because the user can free the former by cancelling.

### REQ-107 — State how current every margin figure is (Must Have)

- **User Story:** As Nikhil, I want to know when a figure was last computed, so that I do not act on a number that is hours old.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL display the time at which the margin figures presented were computed.
  - [ ] WHEN the figures are older than the expected refresh interval, THE SYSTEM SHALL state that they are stale rather than presenting them as current, per Rule B10.
  - [ ] WHILE the figures are stale, THE SYSTEM SHALL refuse any action that would commit or release money against them, and state that as the reason.
  - [ ] WHEN figures are refreshed, THE SYSTEM SHALL update the stated time even if no value changed.

### REQ-108 — Keep separately-settled segments separately presented (Must Have, segment split deferred)

> **Decision, 19 Aug 26 — no segment-level transaction segregation.** Balances and the transaction
> list are presented as one merged figure. Nothing in this phase splits by separately-settled
> segment, and no segment selector, column or filter is built.
>
> **The one thing that must still be true:** the acceptance criteria below are conditional on the
> account holding more than one segment, and today no account does — so the requirement is satisfied
> by its own final criterion, which says to omit the distinction entirely rather than present a
> single-item grouping. Reintroducing segments later is a change to what is *displayed*, not to what
> is *recorded*, only if entries carry the segment they belong to from day one. That is a data-model
> obligation on the technical design, not a UI one.

- **User Story:** As Nikhil, I want money held under separate settlement kept visibly separate, so that I do not believe I can spend in one place money that is held in another.
- **Acceptance Criteria:**
  - [ ] WHERE the account holds money under more than one separately-settled segment, THE SYSTEM SHALL present each segment's three balances separately, per Rule B11.
  - [ ] THE SYSTEM SHALL NOT present a combined figure across separately-settled segments as though it were spendable in any one of them.
  - [ ] WHERE money can be moved between segments, THE SYSTEM SHALL present that as a movement with its own effect on each segment's figures, per Rule B11.
  - [ ] WHERE the account holds only one segment, THE SYSTEM SHALL omit the segment distinction entirely rather than presenting a single-item grouping.

---

## Business Rules

**Rule B1 — There are exactly three balances, and they answer different questions.**

| Figure | Question it answers | Includes | Excludes |
|---|---|---|---|
| **Ledger balance** | What does my account record? | Every settled money event | Pledged securities |
| **Available margin** | What can I commit to a trade right now? | Cash, plus pledged securities after their discount | Everything already committed |
| **Withdrawable balance** | What can reach my bank today? | Settled, uncommitted cash | Pledged securities, money added today, unsettled sale proceeds, committed margin, any outstanding margin shortfall |

**Rule B2 — One name per figure, everywhere.** Each of the three figures has exactly one user-facing name, used identically on every surface. No abbreviation, no synonym, no second name for the same quantity in a different context.

**Rule B3 — No figure may be defined using another of the three.** Each is defined from money events and commitments, never from its siblings. A definition that reads "A equals B minus C" where B is also one of the three explains nothing, because the user has no independent grip on B.

**Rule B4 — The withdrawable derivation.** This is the single most consequential rule in the PRD and it is defined only here.

```
Withdrawable balance
  =  settled ledger balance
   −  money added today
   −  proceeds of sales that have not yet settled
   −  charges incurred but not yet posted
   −  any margin shortfall currently outstanding
   +  the portion of committed margin that was met from collateral rather than cash
```

Each term carries a plain-language explanation shown alongside it:

| Term | Explanation shown to the user |
|---|---|
| Settled ledger balance | What the account records as at the last completed settlement |
| Money added today | Funds added today cannot be withdrawn today |
| Unsettled sale proceeds | Money from sales that have not completed settlement can be traded with but not withdrawn |
| Charges not yet posted | Costs already incurred that will appear on the account shortly |
| Margin shortfall outstanding | Your positions currently require more than the account holds, so nothing can leave until that is met |
| Committed margin met from collateral | Your pledged securities covered part of what your positions require, so that much of your cash was never actually locked |

> **The shortfall term was added 20 Aug 26** to make [REQ-506](product-requirements-account-health.md#functional-requirements)
> expressible. REQ-506 requires the withdrawable figure to be zero while a shortfall is outstanding,
> and the derivation had no term that could produce that — so the figure would have read zero while
> the five terms above summed to something else, which REQ-102 and both of Flow 2's error paths
> treat as a correctness failure severe enough to block withdrawal entirely. The term is deducted at
> its full outstanding value and floors the result at zero, so a shortfall larger than the balance
> produces zero rather than a negative withdrawable figure. It is the one term whose presence is
> itself the explanation the user needs.

The final term is the counter-intuitive one and must always be explained, never shown bare. The account blocks the full margin requirement against cash; where pledged securities covered part of it, that cash was not truly committed and is added back.

**Rule B5 — Collateral creates margin and never creates withdrawable money.** Pledged securities raise available margin by their value after a risk discount. They never appear in the withdrawable figure, because there is no money behind them to send to a bank. Where a kind of trade requires a portion of its margin to be met in cash, that requirement is stated separately, and the cash-equivalent portion of collateral is distinguished from the rest.

**Rule B6 — Deduction periods follow the trading calendar, not the clock.** The period during which sale proceeds are not withdrawable is measured in settlement days. A settlement holiday extends it. When a deduction is larger because of a holiday, the holiday is named as the cause, so that a figure falling with no user action is explained rather than merely observed.

**Rule B7 — Every commitment is paired with its release, and is attributed twice over.** Money committed against a position or an unexecuted order is recorded as a commitment, and its later release is recorded against the same components. Every commitment is attributed on two axes at once: whether it was met from cash or from collateral, and whether it is held against an open position or an order not yet executed. A commitment with no possible release is a defect, not a state.

**Rule B8 — Figures may change with no user action, and the cause is always stated.** A market move can change collateral value, a position's requirement, or an unrealised loss. A settlement holiday can extend a deduction. Charges post on a schedule. In every such case the change is attributable to a named cause, and that cause is available to the user.

**Rule B9 — Negative values are legitimate and are never clamped, with one exception.** A ledger balance may be negative. A committed-margin component may be negative where a user has generated funds by closing a position. Presenting a negative value as zero misrepresents the account; presenting it as negative and explaining it does not.

**The withdrawable figure is the exception, and floors at zero.** It answers "what can reach my bank today", and nothing can leave an account in an amount below nothing — so where Rule B4's terms sum below zero, the figure is zero and the deduction responsible is named (REQ-102). This is not clamping a value the user needs to see: the debt itself is still shown, as a debt, by Rule H1, and the shortfall is still shown as its own term in the derivation. What floors is the answer to a question that has no negative answer, not the underlying position.

**Rule B10 — An unavailable figure is stated as unavailable, never as zero.** Where a component cannot be obtained from its source, it is shown as unavailable with the time of the last successful value. A missing figure rendered as ₹0.00 is indistinguishable from a real zero and is the more damaging of the two errors.

**Rule B11 — Separately-settled segments are separate accounts of money.** Where money is held under more than one settlement regime, each has its own three balances. Money in one is not spendable in another until moved, and the movement is itself a money event.

**Rule B12 — The three figures are computed from one definition.** There is exactly one definition of each figure in the product. Any surface presenting a balance presents the result of that definition; no surface computes its own.

---

## User Flows

### Flow 1: Understand why I cannot place this trade

- **Persona:** Nikhil
- **Trigger:** An order is refused, or the user is deciding whether to place one.
- **Preconditions:** The account is funded and holds at least one open position or pledged security.

**Main Flow (Happy Path)**

1. User opens the funds view → System presents the three balances (REQ-101) with the time the margin figures were computed (REQ-107).
2. User sees available margin lower than expected → System presents the decomposition: what contributes to it, and what is committed against it (REQ-103).
3. User opens the committed portion → System presents it split by funding source and by whether it is held against a position or an unexecuted order (REQ-106).
4. User identifies money held against an order not yet executed → System states that cancelling the order would release it.
5. User cancels the order elsewhere → System reflects the release in the same components that recorded the block (Rule B7) and updates the computed-at time.

**Alternate Flows / Branches**

- **Branch A — the user's question is about a specific kind of trade rather than the total:**
  1. User opens the per-trade-kind view → System states how much can be committed to each kind the account is enabled for (REQ-105), naming any restriction that makes one figure lower than the total.
  2. Where the kind requires a cash portion, System states the cash amount separately (Rule B5).
- **Branch B — the account holds collateral and little cash:**
  1. System presents available margin as substantial and the cash portion as small (REQ-104).
  2. System states that a trade requiring cash cannot be placed for more than the cash portion, and why.
- **Branch C — the shortfall is caused by a market move rather than a user action:**
  1. System attributes the change to its named cause (Rule B8) rather than presenting an unexplained difference.

**Error / Exception Flows**

- **If the margin figures are stale** → System states their age and marks them stale (REQ-107) → System refuses any action that would commit money against them and states staleness as the reason → User waits for a refresh or contacts support with the stated time.
- **If a component cannot be obtained at all** → System presents that component as unavailable with the time of its last known value (Rule B10) → System presents the total as incomplete rather than presenting a sum that omits the missing part.
- **If the components do not sum to the stated total** → System presents the discrepancy rather than adjusting a component to hide it, and treats it as a correctness failure.

**Postconditions / Success State**

The user can state, without assistance, which specific commitment is preventing the trade and what action would release it.

**Related Edge Cases**

"The margin figures are stale because their source has not reported"; "A user holds pledged securities and no cash at all" — both in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

### Flow 2: Understand why I cannot withdraw my balance

- **Persona:** Nikhil, Arun
- **Trigger:** The withdrawable figure is lower than the balance, and the user notices.
- **Preconditions:** The account has a non-zero ledger balance.

**Main Flow (Happy Path)**

1. User opens the funds view → System presents the withdrawable figure alongside the balance, and names the largest single deduction without being asked (REQ-102).
2. User opens the derivation → System presents every term of Rule B4, each with its plain-language explanation and its sign.
3. User reads the term responsible → System has already explained it in the user's own terms rather than in settlement vocabulary.
4. User closes the derivation → System returns them to the funds view with the figure unchanged.

**Alternate Flows / Branches**

- **Branch A — the deduction is money added today:**
  1. System states that funds added today cannot be withdrawn today, and states the date from which they can.
- **Branch B — the deduction is unsettled sale proceeds:**
  1. System states that the money can be traded with but not withdrawn, and states the settlement date.
  2. Where a settlement holiday extends that date, System names the holiday (Rule B6).
- **Branch C — the withdrawable figure is higher than the cash portion of available margin:**
  1. System presents both without treating the difference as an error, because collateral covering committed margin legitimately produces this (Rule B4, final term).
- **Branch D — the withdrawable figure is zero:**
  1. System states which deduction is responsible rather than stating only that the figure is zero (REQ-102).

**Error / Exception Flows**

- **If the derivation's terms do not sum to the stated withdrawable figure** → System presents the figure as unavailable rather than presenting a derivation that does not reconcile, and no withdrawal may be requested against it.
- **If the ledger balance is negative** → System presents the debt rather than a withdrawable figure, and hands off to [Account Health](product-requirements-account-health.md) (REQ-501).
- **If the withdrawable figure falls while the user is looking at it** → System updates the figure, states that it changed, and names the cause (Rule B8) rather than silently replacing the number.

**Postconditions / Success State**

The user can state which deduction accounts for the gap, and when it will no longer apply.

**Related Edge Cases**

"Available margin and withdrawable are both correct and differ by the entire balance"; "A settlement holiday extends the period during which recent sale proceeds cannot be withdrawn" — both in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

---

## Feature-Specific Edge Cases

- **Every term of the derivation is zero but the withdrawable figure is still lower than the balance** → Expected: impossible by construction; the derivation is the definition. If observed, the figure is presented as unavailable and no withdrawal is permitted against it.
- **A user pledges securities while a withdrawal is pending** → Expected: available margin rises, withdrawable is unchanged, the pending withdrawal is unaffected. Presented without implying the pledge made more money withdrawable.
- **Committed margin exceeds the ledger balance** → Expected: legitimate where collateral is covering the difference. Presented as-is, with the collateral-met portion named. Available margin remains positive; withdrawable is zero.
- **A component is negative because the user generated funds by closing a position** → Expected: presented as negative with its cause named (Rule B9). This is a normal intraday state and must not be presented as an error.
- **A segment is enabled but has never held money** → Expected: presented with its zero state and a single statement, not with a full decomposition of zeros (REQ-101).
- **The margin source reports a figure that contradicts the ledger** → Expected: both are presented with their computed-at times, the disagreement is stated, and no money may be committed or released until it resolves. The product does not choose a winner.
- **Two surfaces would display the same figure at different moments** → Expected: impossible by construction (Rule B12); both read one definition. Any divergence is a defect in that definition, not in a surface.
- **A user holds only cash-equivalent collateral and no cash** → Expected: the cash requirement for a trade kind may still be unmet; the distinction between cash-equivalent collateral and cash is stated (Rule B5) rather than allowing the user to infer they are the same.
