---
title: "FMS — Transactions & Statements"
status: draft
version: "1.0"
part_of: product-requirements.md
---

# Transactions & Statements

> Part 5 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.

Balance is a number; the history is the explanation. This part owns the record of every money event, the ability to find one, and the ability to satisfy oneself that the account is correct without contacting anyone.

## Contents

- [Functional Requirements](#functional-requirements) — REQ-401 to REQ-407
- [Business Rules](#business-rules) — Rules L1 to L9
- [User Flows](#user-flows)
- [Feature-Specific Edge Cases](#feature-specific-edge-cases)

---

## Functional Requirements

### REQ-401 — Record every money event in plain language (Must Have)

- **User Story:** As Arun, I want each entry in my history to tell me what happened in words I understand, so that I can work out where my money went.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL record every event that changes the account's balance as its own entry, per Rule L1.
  - [ ] THE SYSTEM SHALL describe each entry in language a user unfamiliar with settlement vocabulary can understand, per Rule L3.
  - [ ] WHERE an entry has an underlying reference from a settlement or payment process, THE SYSTEM SHALL retain it as secondary detail rather than as the entry's description, per Rule L3.
  - [ ] THE SYSTEM SHALL state, for each entry, whether money entered or left the account and the amount.
  - [ ] THE SYSTEM SHALL distinguish an entry the user caused from one they did not, per Rule L4.
  - [ ] WHERE a charge is recorded, THE SYSTEM SHALL state what the charge is for.

### REQ-402 — Separate "where is my money" from "explain my account" (Must Have)

- **User Story:** As Priya, I want to see just my deposits and withdrawals without wading through every charge and trade, so that I can answer my actual question.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present a view containing only money entering and leaving the account, per Rule L5.
  - [ ] THE SYSTEM SHALL present a separate view containing every entry, per Rule L5.
  - [ ] WHILE viewing money in and out, THE SYSTEM SHALL show each item's current status, including items not yet complete.
  - [ ] THE SYSTEM SHALL make each view reachable from the other without losing the selected period.
  - [ ] WHERE money is held under more than one separately-settled segment, THE SYSTEM SHALL allow the history to be viewed per segment, per Rule B11.

### REQ-403 — Let the user find a transaction by date, type and amount (Must Have, second phase deferred)

> **Promoted 20 Aug 26.** The date range and the money-in / money-out split ship in MVP, so the
> requirement could not sit wholly in Should-Have with only its second phase named — that left the
> shipping half in no priority bucket. Kind and amount filtering remain deferred, marked below.

- **User Story:** As Arun, I want to find one specific transaction without scrolling, so that I can check a single thing quickly.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present a default period of the last 30 days, per Rule L6.
  - [ ] THE SYSTEM SHALL allow the period to be changed, including to a custom range and to a full financial year.
  - [ ] THE SYSTEM SHALL state the period currently shown, in the same place the period is chosen.
  - [ ] THE SYSTEM SHALL allow entries to be narrowed by kind — money in, money out, charges, trading activity. *(Second phase, deferred 19 Aug 26. The date range plus the money-in / money-out split now shipping is accepted as sufficient for MVP; kind and amount filtering are revisited once real history volumes exist.)*
  - [ ] WHEN a period contains no entries, THE SYSTEM SHALL state that the period is empty and offer a wider period, per Rule L7.
  - [ ] THE SYSTEM SHALL make every entry in the account's life reachable, not only a recent window.

### REQ-404 — Show a running balance and pair every reversal with its original (Must Have)

- **User Story:** As Arun, I want to see the balance after each entry and see corrections against what they corrected, so that I can follow the account rather than reconstruct it.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present the resulting balance after each entry.
  - [ ] THE SYSTEM SHALL present a reversal against the entry it reverses, per Rule L2.
  - [ ] THE SYSTEM SHALL NOT present a reversal as an independent entry unrelated to its original, per Rule L2.
  - [ ] THE SYSTEM SHALL NOT alter or remove any entry once recorded, per Rule L2.
  - [ ] WHEN an entry has been reversed, THE SYSTEM SHALL make that visible on the original entry, so a user scanning the history does not count it.

### REQ-405 — Track one payin or payout through its whole life (Must Have)

- **User Story:** As Priya, I want to follow one deposit from the moment I paid to the moment it arrived, so that I know where it is when it is slow.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present, for each money movement, every state it has passed through with the time of each.
  - [ ] THE SYSTEM SHALL present the reason recorded at any state where the movement was refused, failed or reversed, per Rule L8.
  - [ ] WHERE a movement has a reference the user can quote to their bank, THE SYSTEM SHALL present it.
  - [ ] WHILE a movement is not complete, THE SYSTEM SHALL present its expected completion and its current state.
  - [ ] THE SYSTEM SHALL retain failed and cancelled movements in the history, per Rule L8.

> **Implementation note, 19 Aug 26.** Every state transition is **persisted in the FMS database** as
> it happens — state, timestamp, actor or source, and the reason recorded at any refusal, failure or
> reversal — rather than being reconstructed from the current status. The schema for that history is
> part of the technical design. A movement whose intermediate states were never written cannot have
> its timeline shown afterwards, so this is a write-path decision, not a display one.

### REQ-406 — Reconcile any period end to end (RELOCATED — owned by Ledger, not FMS)

> **Relocated 19 Aug 26.** The reconciliation view is delivered by the **Ledger**, not by FMS. FMS
> presents money movements and the balances derived from them; proving that a period's opening
> balance, movements, obligations and charges reconcile to its closing balance is a ledger function
> and belongs with the system of record. The requirement is retained here in full so the Ledger team
> inherits it as written, and Rule L9 stays in this document because FMS must not contradict it.
>
> **What FMS still owes:** the opening and closing balances for a chosen period, each stamped with
> the exact moment it was taken, so the Ledger's reconciliation and the FMS transaction list can
> never disagree about the endpoints.

- **User Story:** As Arun, I want to see that my opening balance, everything that happened, and my closing balance agree, so that I can satisfy myself the account is correct.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL present, for any chosen period, the opening balance, the totals of money in, money out, trading obligations and charges, and the closing balance, such that they reconcile, per Rule L9.
  - [ ] THE SYSTEM SHALL present each total as reachable to the entries that compose it. *(Second phase; see [Future Scope](product-requirements.md#future-scope).)*
  - [ ] IF the totals do not reconcile to the closing balance, THEN THE SYSTEM SHALL state that rather than adjusting a total, per Rule L9.
  - [ ] THE SYSTEM SHALL present the opening and closing balances with the exact moments they were taken.

### REQ-407 — Produce a statement the user can keep and submit (Must Have)

- **User Story:** As Arun, I want a statement I can save and give to someone else, so that I can meet an obligation without asking for help.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL produce a statement for any chosen period, containing every entry, its plain-language description and its resulting balance.
  - [ ] THE SYSTEM SHALL offer a financial-year period as a preset, because tax filing is the dominant use.
  - [ ] THE SYSTEM SHALL offer the statement as a **CSV download**, so that it can be opened in a spreadsheet, filtered, and submitted for further analysis. One row per entry; columns for date, plain-language description, type, reference, amount and resulting balance; amounts as plain unformatted numbers with no currency symbol or thousands separator, so that they are summable without cleaning.
  - [ ] THE SYSTEM SHALL deliver the export from the FMS transaction list itself, so that the period a user is already looking at is the period they export. There is no separate statements destination.

> **Format decision, 19 Aug 26 — CSV only.** No PDF ships in this phase. KB Q11 is closed. A PDF is a
> rendering pipeline with layout, pagination and branding; CSV is a serialisation of data FMS already
> holds. Consequence: a user who needs a presentation-grade document produces it from the CSV, and
> anything requiring a signed or branded statement is served by the back office, not by FMS.
  - [ ] THE SYSTEM SHALL state the period covered and the moment the statement was produced, in the file itself and not only in the filename.
  - [ ] THE SYSTEM SHALL name the downloaded file with the account and the period it covers, so that a user holding several is not required to open them to tell them apart.
  - [ ] THE SYSTEM SHALL NOT include another user's information in any statement.

---

## Business Rules

**Rule L1 — Every balance change is an entry.** There is no balance change without a corresponding entry, and no entry without a balance change. Money in, money out, trading obligations, charges, corrections, automatic returns — each is recorded, none is netted silently into another.

**Rule L2 — Entries are immutable; corrections are compensating entries.** No entry is ever altered or removed. A correction is a new entry that references what it corrects, and both remain visible. A reversal presented as an independent row causes a user scanning the history to count the same charge twice, which is why the pairing is a rule rather than a preference.

**Rule L3 — The description is written for the account holder, not for the back office.** Settlement identifiers, internal codes and process names are retained as secondary detail and are never the entry's description. The history is exactly where an anxious user goes to find out where their money went; it is the wrong place to be illegible.

**Rule L4 — An entry states whether the user caused it.** A deposit the user made and an automatic return required by the calendar are both outflows and inflows of money, and they are not the same kind of event. The distinction is visible without opening the entry.

**Rule L5 — Two views, two questions.** "Where is my money" is answered by money in and money out with their current status. "Explain my account" is answered by every entry with a running balance. Most user queries are the first; most of the content is the second. Presenting only the combined view buries the common question in the rare one.

**Rule L5a — The every-entry view is where money that arrived from trading lives.** Sale
proceeds, mark-to-market and charges are not payins — a payin is money the user moved from their
bank. Restricting the list to money in and money out therefore hides them entirely, which is what
the second view exists to prevent. Both views share one running balance.

**Rule L5b — One heading per family, with the state in the pill.** Every withdrawal row is headed
*Withdrawal*, whatever became of it, and the pill carries PAID, PARTLY PAID, IN PROGRESS,
CANCELLED, FAILED or RETURNED. Six different headings each restating what their own pill already
said made the column unscannable.

**Rule L5c — Only money the user moved can succeed or fail.** A brokerage charge or a
mark-to-market settlement simply happened; labelling it *Successful* adds a word without adding a
fact, so those rows carry no status pill.

**Rule L5d — The meta line carries facts, the (i) carries the explanation.** Date, method or
destination, and reference — nothing else. A row whose meta line explains its own state reads
differently from its neighbours at exactly the moment the user is scanning for something unusual.

**Rule L8a — An export returns precisely what is on screen.** The same view, the same period, the
same running balance. An export that quietly returned something else would be the one document the
user cannot check against the page they exported it from. Type is written as **Debit** or
**Credit** — the internal kinds are our words for our own plumbing, and the file is read against a
bank statement.

**Rule L6 — The default period is wide enough to contain the answer.** Thirty days, because the mandated return of unused funds runs on a monthly or quarterly cycle and is among the most-queried entries. A seven-day default routinely shows an empty table for a transaction the user knows happened.

**Rule L7 — An empty period says it is empty.** A period with no entries states that, states the period, and offers a wider one. Blank space is indistinguishable from a failure to load.

**Rule L8 — Failed and cancelled movements stay in the history.** A deposit that failed is part of what happened to the account, and it is the entry a user most often needs to discuss. Its recorded reason stays with it.

**Rule L9 — A period reconciles or says it does not.** Opening balance, plus money in, minus money out, plus or minus trading obligations, minus charges, equals closing balance. Where it does not, the discrepancy is stated. A total silently adjusted to make the arithmetic work destroys the only thing this view is for.

---

## User Flows

### Flow 1: Find one transaction

- **Persona:** Arun, Priya
- **Trigger:** The user is looking for a specific movement — a deposit that seems missing, a charge they did not expect, a withdrawal they are chasing.
- **Preconditions:** The account has history.

**Main Flow (Happy Path)**

1. User opens the history → System presents the last 30 days with the period stated (REQ-403, Rule L6), defaulting to the money-in-and-out view (REQ-402).
2. User sees the movement → System presents its amount, direction, current status and plain-language description (REQ-401).
3. User opens it → System presents every state it passed through with times, the reason where it failed or was refused, and any reference they can quote to their bank (REQ-405).
4. User has their answer without contacting anyone.

**Alternate Flows / Branches**

- **Branch A — the movement is older than the default period:**
  1. User widens the period, including to a full financial year → System presents it, and every entry in the account's life remains reachable (REQ-403).
- **Branch B — the user needs the full picture, not just money in and out:**
  1. User switches to the all-entries view → System retains the selected period across the switch (REQ-402) and presents the running balance (REQ-404).
- **Branch C — the entry is a charge the user does not recognise:**
  1. System states what the charge is for in plain language (REQ-401, Rule L3), with any underlying reference as secondary detail.
- **Branch D — the entry was reversed:**
  1. System presents the reversal against the original and marks the original as reversed, so the user does not count it twice (REQ-404, Rule L2).

**Error / Exception Flows**

- **If the period contains no entries** → System states that the period is empty, states the period, and offers a wider one (Rule L7) — never blank space.
- **If the movement is still in flight** → System presents its current state and expected completion rather than omitting it (REQ-405).
- **If a movement's reason was never recorded** → System states that the reason is unavailable rather than presenting the movement as unexplained, and offers a support route.

**Postconditions / Success State**

The user has found the movement, understands its state and its cause, and holds any reference needed to pursue it elsewhere.

**Related Edge Cases**

See [Feature-Specific Edge Cases](#feature-specific-edge-cases) below.

### Flow 2: Account for a period and take a record of it away

> **Rewritten 20 Aug 26, following REQ-406's relocation.** The end-to-end reconciliation this
> flow previously described — opening balance, period totals, obligations, charges, closing
> balance, proved to agree — is delivered by the **Ledger**, not by FMS. What FMS delivers is the
> movement record, the running balance, the two stamped period endpoints the Ledger reconciles
> between, and the export. The flow below is that, and only that. Arun's user story *"reconstruct
> what happened over a period, so that I can satisfy myself the account is correct"* is now met
> jointly: FMS answers *what happened*, the Ledger answers *and it adds up*.

- **Persona:** Arun, Nikhil
- **Trigger:** The user doubts a balance, is preparing a tax filing, or is deciding whether to keep using the account.
- **Preconditions:** The account has history over the period in question.

**Main Flow (Happy Path)**

1. User chooses a period → System states the period shown, in the same place the period is chosen (REQ-403).
2. System presents the opening and closing balances for that period, each stamped with the exact moment it was taken (REQ-406's retained FMS obligation), so that this list and the Ledger's reconciliation can never disagree about the endpoints.
3. System presents every entry in the period in the all-entries view, each in plain language, with the resulting balance after it (REQ-401, REQ-404).
4. User follows the running balance from the opening figure to the closing figure → System has recorded every balance change as its own entry (Rule L1), so nothing moves the balance without appearing here.
5. User produces a statement for the period → System exports it as CSV from the list they are already looking at, with every entry, its description and its resulting balance, stating the period and the moment of production (REQ-407, Rule L8a).

**Alternate Flows / Branches**

- **Branch A — the user is preparing a tax filing:**
  1. User selects the financial-year preset → System presents the whole year and exports it (REQ-407).
- **Branch B — the user wants the arithmetic proved, not just presented:**
  1. System states that the period reconciliation is produced by the Ledger and links to it, rather than presenting its own totals that would be a second opinion on the same question (REQ-406, Rule L9).
  2. The endpoints the Ledger reconciles between are the same two stamped balances shown at step 2, so a user comparing the two surfaces sees one set of figures.
- **Branch C — the user is chasing one movement rather than auditing a period:**
  1. User switches to the money-in-and-out view → System retains the selected period across the switch (REQ-402) and the flow rejoins Flow 1.

**Error / Exception Flows**

- **If the entries in the period do not sum to the difference between the stamped endpoints** → System states the discrepancy rather than adjusting an entry or restating an endpoint (Rule L9) → the account is treated as failing its integrity check and no money may leave it. FMS detects and reports this; it does not resolve it, because the resolution is a ledger correction.
- **If a period spans a correction made to an earlier period** → System presents the correction in the period it was made, with its reference to the original, so both periods remain internally consistent.
- **If an endpoint balance cannot be stamped for the chosen period** → System states that rather than presenting an unstamped figure, because an endpoint without its moment is not reconcilable against anything.
- **If the export cannot be produced for the chosen period** → System states why and offers a period it can produce, rather than producing a partial file presented as complete.

**Postconditions / Success State**

The user can follow every change in their balance across the period from one stamped endpoint to the other, holds a file they can keep or submit, and knows where the formal reconciliation lives if they want the arithmetic proved rather than shown.

**Related Edge Cases**

"The ledger's entries do not sum to the stated balance" — in the [main PRD's Edge Cases](product-requirements.md#edge-cases).

---

## Feature-Specific Edge Cases

- **A movement's status changes while the user is looking at it** → Expected: the view updates and states that it changed, rather than silently replacing one status with another.
- **A charge is reversed and then re-applied** → Expected: three entries, each paired to what it relates to. The running balance is correct at every point in the sequence.
- **Two entries carry the same time to the finest recorded precision** → Expected: a stable, repeatable order is presented, and the running balance is correct in that order. The same period always reads identically.
- **An entry belongs to a different day than the one it was recorded on, because it relates to an earlier trading day** → Expected: both the recording moment and the day it belongs to are available, and the period filter uses the day it belongs to.
- **A user requests a statement for a period before the account existed** → Expected: an empty statement stating the period and that the account held no history in it, not an error.
- **A statement is produced while a movement is in flight** → Expected: the in-flight movement is shown as in flight and is excluded from the reconciled balances, consistent with Rule A5.
- **The history is very long** → Expected: every entry remains reachable (REQ-403). A cap on how far back the user can look is a cap on their ability to answer their own question.
- **An entry's plain-language description is unavailable for an event type not yet mapped** → Expected: the underlying reference is presented with an explicit statement that a plain description is not yet available, rather than the raw reference presented as though it were the description (Rule L3).
- **A reversal arrives before the entry it reverses** → Expected: both are recorded; the pairing holds regardless of arrival order, and the running balance is correct once both exist.
