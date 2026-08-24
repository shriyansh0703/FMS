---
title: "FMS — Configuration"
status: draft
version: "1.0"
part_of: product-requirements.md
---

[← Back to PRD index](product-requirements.md)

# FMS — Configuration

> Part 8 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.
>
> Every value here is **policy, not logic**: set by a regulator, a bank, or a commercial decision,
> and changing without the product changing. Requirements **REQ-701 – REQ-710 and REQ-706a**
> (11 in total). Rules **G1 – G5**.

---

## 1. Why this document exists

A number that lives in code is a number that needs a release to change. Every value below will be
changed by someone who is not an engineer, at a time nobody has scheduled, usually because a
regulator or a bank changed it first. Collecting them in one place is not tidiness — it is the
difference between a same-day change and a deployment.

**Rule G1 — A configured value is never duplicated in copy.** Message text reads the value; it does
not restate it. *"UPI transfers are capped at ₹2,00,000 per day"* takes that figure from
configuration, so raising the cap does not leave a stale number in a template nobody remembered to
edit.

**Rule G2 — Every configured value has a stated owner and a last-changed date.** A limit nobody
owns is a limit nobody updates.

**Rule G3 — Changing a value never changes a state machine.** If a limit drops below what some
users have already done, existing requests complete under the rules they were made under. The new
value applies to new requests only.

---

## 2. Payment routes

| Key | Value today | Owner | Basis |
|---|---|---|---|
| `upiDailyCap` | ₹2,00,000 | Payments | NPCI ceiling. Individual banks may enforce a lower one of their own, which we cannot see |
| `nbDailyCap` | ₹10,00,000 | Payments | Typical net-banking ceiling |
| `neftCap` | none | Payments | No ceiling on the rail itself |
| `nbFee` | **₹0** — not passed through in this phase | Commercial | Gateway charge. Benchmarked at ₹11.80 incl. GST at one competitor; **we absorb it**. Kept configurable so charging is a settings change, not a release |
| `minAdd` | ₹100 | Commercial | Our own floor |

### REQ-701 — Enforce caps per day per route (Must Have)

- **User Story:** As Priya, I want to be stopped by a limit before I pay rather than by my bank afterwards, so that a refused payment is not how I learn the rule.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL enforce each route's cap per day, measured against everything already sent on that route today.
  - [ ] THE SYSTEM SHALL NOT enforce a daily cap per transaction, because doing so permits the same amount twice and defers the refusal to the user's bank.
  - [ ] WHEN an amount exceeds the selected route's remaining headroom for today, THE SYSTEM SHALL state the remaining headroom rather than a generic refusal, per Rule A12.
  - [ ] THE SYSTEM SHALL read every cap from configuration and SHALL NOT restate a cap in message copy, per Rule G1.
  - [ ] WHERE a bank enforces a lower limit of its own that this module cannot see, THE SYSTEM SHALL treat the resulting refusal as a bank decline, per Rule A9a.

### REQ-702 — Select the route automatically and name the one that carried the payment (Must Have)

> **Decided 20 Aug 26 — the system selects, the user does not.** This resolves a direct conflict
> with REQ-202, which required every route to be presented, ordered and chosen from. With `nbFee`
> at ₹0 there is no cost on which to compare routes, so the choice offered the user a decision they
> had no basis to make and had not asked for. REQ-202 now covers disclosure — the arrival date
> before commitment, and the route named afterwards — and Rule A12's automatic re-route becomes the
> primary mechanism rather than an exception to a rule that contradicted it.

- **User Story:** As Priya, I want to enter an amount and have the money arrive, without being asked to choose between two things I cannot tell apart, so that funding is one decision and not three.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL select the payment route automatically, against the amount and each route's remaining headroom for today.
  - [ ] THE SYSTEM SHALL NOT require the user to select a route.
  - [ ] THE SYSTEM SHALL state the expected arrival date for the payment before the user commits, per Rule A3 and REQ-202. This is a property of the route, not of the payout cut-off — REQ-707 governs the arrival date of a *withdrawal* and does not apply here.
  - [ ] WHEN a payment completes, THE SYSTEM SHALL name the route that carried it, per REQ-202.
  - [ ] WHERE the selected route lacks headroom for the amount and another executable route can carry it, THE SYSTEM SHALL change route automatically and SHALL say so, per Rule A12.
  - [ ] WHERE no route can carry the amount, THE SYSTEM SHALL state that before payment is attempted and SHALL consume no attempt, per Rule A3.
  - [ ] IF a fee is ever configured above zero, THEN THE SYSTEM SHALL disclose it before the user commits, and SHALL disclose any fee introduced by an automatic route change, per REQ-202 and Rule A12.

### REQ-703 — Waive the minimum when the amount settles a debt exactly (Must Have)

- **User Story:** As Arun, I want to pay off a debt smaller than the minimum deposit, so that clearing ₹24.37 does not require me to deposit ₹100.
- **Acceptance Criteria:**
  - [ ] WHERE the amount entered exactly settles an outstanding debit balance, THE SYSTEM SHALL accept it even though it is below `minAdd`, per Rule H3.
  - [ ] THE SYSTEM SHALL offer the exact amount owed as a suggestion while the account is in debt, per REQ-502.
  - [ ] WHERE the selected route imposes its own minimum above the amount owed, THE SYSTEM SHALL select a route that permits the exact amount, per Rule H3 and REQ-702.
  - [ ] THE SYSTEM SHALL apply the waiver only to the amount that settles the debt, and SHALL apply `minAdd` to every other funding amount.

---

## 3. Bank accounts

> **Surface decision, 19 Aug 26 — bank account management lives in Profile, not in FMS.** Adding,
> deleting and setting a primary bank account are performed in the **Profile → Bank Accounts**
> section, alongside the verification that proves ownership. FMS **consumes** that list and never
> mutates it. The requirements below are retained here because the *values* are configuration and
> because FMS depends on the behaviour, but the **screens belong to Profile**. FMS's only
> obligations are the two marked *FMS* below.

| Key | Value today | Owner |
|---|---|---|
| `maxBankAccounts` | **3** | Operations |

### REQ-704 — Add, delete and re-designate a bank account, and nothing else *(Profile)* (Must Have)

- **User Story:** As Priya, I want to manage which of my bank accounts this product knows about, so that money goes where I intend without my having to prove ownership twice for the same account.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL allow a user to add a bank account, delete one, and change which account is primary.
  - [ ] THE SYSTEM SHALL NOT allow an account's details to be edited in place.
  - [ ] WHERE an account's details are wrong, THE SYSTEM SHALL require it to be deleted and re-added, so the verification that proved ownership is never inherited by details that have changed.
  - [ ] THE SYSTEM SHALL NOT permit deletion while a withdrawal to that account is open, per Rule G4.

### REQ-705 — Cap the number of accounts held, and name the limit reached *(Profile)* (Must Have)

- **User Story:** As Priya, I want to be told why I cannot add another account, so that I am not looking at a control that does nothing.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL permit at most `maxBankAccounts` accounts to be held at once.
  - [ ] WHEN the limit is reached, THE SYSTEM SHALL state which limit was reached rather than presenting a disabled control, per Rule W2 and Rule H6.
  - [ ] THE SYSTEM SHALL require an existing account to be deleted before another may be added beyond the limit.
  - [ ] THE SYSTEM SHALL read the limit from configuration and SHALL NOT restate it in copy, per Rule G1.

### REQ-706 — Default to the primary account in both directions *(FMS)* (Must Have)

- **User Story:** As Nikhil, I want my usual account chosen for me, so that routine funding and withdrawal do not ask me the same question every time.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL use the primary account as the default destination for withdrawals.
  - [ ] THE SYSTEM SHALL use the primary account as the default source shown when adding funds.
  - [ ] WHEN the primary account changes, THE SYSTEM SHALL NOT redirect a withdrawal already requested, which is pinned to its destination at request time, per Rule W12.
  - [ ] THE SYSTEM SHALL expose which accounts carry an open withdrawal, so that the deletion refusal in Rule G4 can be enforced.

### REQ-706a — Name the missing bank account as a blocker rather than offering a form *(FMS)* (Must Have)

- **User Story:** As Priya, I want to be taken to the one thing standing between me and funding my account, so that I do not fill in a form that cannot complete.
- **Acceptance Criteria:**
  - [ ] WHERE the user has no verified bank account, THE SYSTEM SHALL NOT present an add-funds or withdraw form.
  - [ ] WHERE the user has no verified bank account, THE SYSTEM SHALL name the blocker and present the action that resolves it as the primary action, per REQ-505 and Rule H6.
  - [ ] WHERE the user has exactly one verified account, THE SYSTEM SHALL use it without presenting a choice.
  - [ ] WHEN a verified account is added, THE SYSTEM SHALL restore the funding path without requiring the user to find it again, per REQ-505.

**Rule G4 — An account cannot be deleted while a withdrawal to it is open.** The money is in flight
to a destination that would no longer exist. Profile owns the refusal; **FMS owns the fact** — it
must expose which accounts have an open withdrawal against them, or Profile cannot enforce this.

---

## 4. Withdrawal timing

There is **no instant payout**. Every withdrawal settles on the mandated cycle, and when it lands
depends on one thing: whether end-of-day processing had completed when the request was placed.

| Request placed | Reaches the bank |
|---|---|
| Before EOD processing completes | **Next working day** |
| After EOD processing completes | **The working day after that** |

### REQ-707 — State the arrival date before commitment, from the EOD boundary (Must Have)

- **User Story:** As Arun, I want a date rather than a duration, so that I can decide whether to withdraw now or in the morning.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal amount is entered, THE SYSTEM SHALL state the expected arrival date before the request is submitted.
  - [ ] THE SYSTEM SHALL derive the date from the current time against the `payoutCutoff` boundary, per Rule W5.
  - [ ] THE SYSTEM SHALL NOT present a generic duration in place of a computed date.
  - [ ] WHERE the account's state defers arrival, THE SYSTEM SHALL name the cause, per REQ-303.
  - [ ] IF the arrival date cannot be computed, THEN THE SYSTEM SHALL state that rather than presenting a default as though it were computed, per REQ-303.
  - [ ] THE SYSTEM SHALL use the same boundary the back office uses, per Rule G5.

> **Dependency — EB-9.** Which days are working days is not derivable from the day of the week, so
> this requirement is not deliverable until the trading and settlement calendar has a nominated
> source. Recorded against REQ-303 and REQ-707 in the index's blocked-Must-Have table.

| Key | Value today | Owner |
|---|---|---|
| `payoutCutoff` | **3:00 PM** | Operations |

**Rule G5 — The EOD boundary is a configured time, and it is the same boundary the back office
uses.** Two systems disagreeing about when the day ended produces withdrawals that arrive a day
later than the screen promised, which is the one thing this rule exists to prevent.

---

## 5. Charges and interest

| Key | Value today | Owner | Note |
|---|---|---|---|
| `debitInterestRate` | **18% per annum** *(placeholder — to be configured in TechExcel)* | Finance | Charged daily on an outstanding debit balance. Configured in the **TechExcel back office** and read from there; the value above is a stand-in until TechExcel is set up. The figure appears in the dues email and the debt banner, both of which read it from configuration rather than restating it |

### REQ-708 — Read the debit interest rate from configuration, and never restate it (Must Have)

- **User Story:** As Arun, I want to be told what my debt is costing me, at a rate that is actually the rate, so that I can decide whether clearing it is urgent.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL state the debit interest rate in every message about an outstanding balance, per REQ-501 and REQ-608.
  - [ ] THE SYSTEM SHALL take the rate from configuration and SHALL NOT restate it in message copy, per Rule G1.
  - [ ] WHILE the configured rate remains a stand-in, THE SYSTEM SHALL mark it as provisional wherever it is displayed outside production.
  - [ ] WHILE the configured rate remains a stand-in, THE SYSTEM SHALL NOT send any message quoting a rate from production.
  - [ ] WHERE the rate cannot be obtained, THE SYSTEM SHALL state the debt and that the accrual figure is unavailable, rather than presenting the debt as static, per Account Health Flow 1.

> **Dependency — EB-8.** The rate is owned and configured in the back office and is not yet set.
> The obligation to disclose is not conditional on the value existing; sending a message that quotes
> a stand-in rate is.

---

## 6. Destinations

| Key | Value today | Owner |
|---|---|---|
| `postFundingDestination` | The order-placement surface | Product |

### REQ-709 — Offer a way into trading from the funding confirmation (Must Have)

- **User Story:** As Priya, I want to get on with buying something once my money has arrived, so that funding does not end by returning me to where I started.
- **Acceptance Criteria:**
  - [ ] WHEN a payin is confirmed, THE SYSTEM SHALL present an action leading to the configured post-funding destination.
  - [ ] THE SYSTEM SHALL take that destination from configuration rather than deciding it.
  - [ ] WHEN the configured destination changes, THE SYSTEM SHALL follow it without any change to this module.
  - [ ] THE SYSTEM SHALL state what the action does before it is taken, so a user who does not want to trade yet can decline it.

### REQ-710 — Fall back to a plain dismissal where no destination is configured (Must Have)

- **User Story:** As Priya, I want the confirmation to close cleanly when there is nowhere to send me, so that I am not offered a button that leads nowhere.
- **Acceptance Criteria:**
  - [ ] WHERE no post-funding destination is configured, THE SYSTEM SHALL present a plain dismissal.
  - [ ] THE SYSTEM SHALL NOT present an action that cannot complete, per Rule H6 and Rule W2.
  - [ ] THE SYSTEM SHALL record the payin identically whether or not a destination is configured.

---

## 7. Out of scope for configuration

These are **rules, not values**, and changing them is a product decision requiring its own
specification — not a settings edit.

| Not configurable | Why |
|---|---|
| The 50:50 cash-to-collateral ratio | Set by the exchange, and the margin engine's behaviour changes with it |
| The withdrawable derivation (Rule B4) | The definition of the number, not a parameter of it |
| Which channel carries which message | A design decision with a stated rationale per message |
| T+1 settlement itself | Regulatory, and the whole balance model assumes it |
