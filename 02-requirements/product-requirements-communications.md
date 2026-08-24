---
title: "FMS — Communications"
status: draft
version: "1.0"
part_of: product-requirements.md
---

[← Back to PRD index](product-requirements.md)

# FMS — Communications

> Part 7 of the [Fund Management System PRD](product-requirements.md). Tech-agnostic rule applies.
>
> Covers **outbound messages** for the two states where the account needs the user to act —
> **margin shortfall** and **account in debt** — and for the money movements the user needs told
> about. Requirements **REQ-601 – REQ-604, REQ-608, REQ-609, REQ-611 – REQ-627** (23 in total;
> REQ-605 to REQ-607 and REQ-610 were withdrawn 20 Aug 26, see §6). Rules **C1 – C19**.
>
> **[Read the message catalogue →](https://claude.ai/code/artifact/d91dde38-c18b-475c-b9b3-ba19f41225ba)** — every message rendered in the shape of the
> channel it goes on: SMS at its real width with its segment count, WhatsApp as a bubble with
> its buttons, email as a letter. Easier to review than the tables below, and generated from
> the same source.
>
> In-product copy for these states lives in
> [account health](product-requirements-account-health.md) (Rules H1, H7). This document is what
> we send **when the user is not looking at the screen**.

---

## 1. Digest

| | |
|---|---|
| **States covered** | Margin shortfall (5 causes) · Account in debt (6 causes) · Fund addition (4 outcomes) · Withdrawal (6 outcomes) · Mandated settlement (announced before the date, notified after it) |
| **Brand in messages** | **Thinq.** FMS is the internal module name and appears in these documents only. Nothing a user receives says FMS |
| **Channels** | SMS · WhatsApp · Email |
| **Not a channel** | Push. There is no mobile application, so there is no push surface to send to. If one ships later, it slots in as a supplementary channel and changes nothing below |
| **SMS templates needed** | **5**, for the two action states only. Money movement carries none at all — see §7, §8 and Rule C2 |
| **WhatsApp templates needed** | 2 for the action states, 3 for fund addition, 2 for withdrawal, 1 for the mandated settlement announcement |
| **Email variants** | 11 cause-specific for the action states, plus 9 for money movement |
| **Hard regulatory line** | Margin shortfall intimation is **mandatory and same-day**. It is not subject to preference, quiet hours, or frequency caps |
| **Biggest cost risk** | SMS template approval. Every template is separately registered and takes days to weeks. Design for few templates, not expressive ones |
| **Consequence of having no push** | SMS carries more load than it otherwise would, and it is the only channel that reaches every user without opt-in or an inbox. Rule C16 protects it |
| **SMS carries no link** | Deliberate. See Rule C16 |

---

## 2. The governing principle

> **Specificity increases with channel freedom.**

| Channel | Approval needed | So it carries |
|---|---|---|
| **SMS** | Per-template registration, slow, variables restricted | Amount, deadline, action. **No cause.** |
| **WhatsApp** | Per-template approval, variables permitted | Amount, deadline, cause, alternative fix, buttons |
| **Email** | None | Everything, including the breakdown that produced the number |

This is not a stylistic choice. An SMS template whose variable slot carries a whole sentence is
routinely rejected at registration, and 11 cause-specific templates would mean 11 separate
approvals before launch and a fresh approval every time copy changes. Two families of SMS
template, with the cause pushed to the channels that can carry it, is the design that ships.

---

## 3. Channel roles

| Channel | Role | Reaches | Fails when |
|---|---|---|---|
| **SMS** | The guaranteed channel. Regulatory record | Everyone, no internet, no app | Nothing rich fits. Easily mistaken for spam |
| **WhatsApp** | The action channel. Buttons go straight into payment | Opt-in only | Not opted in, or outside the 24-hour session window without an approved template |
| **Email** | The record and the explanation | Everyone | Not read in time. Never the sole channel for anything urgent |

**The funds screen is a destination, not a channel.** The banner a user meets on arrival is
specified in [account health](product-requirements-account-health.md) (Rules H1, H7). It is what
every message above links to, and it is the only surface that is always correct, because it reads
from `derive()` at the moment it is drawn rather than from whatever was true when a message was
queued.

**Rule C1 — No urgent state relies on one channel.** Margin shortfall goes out on SMS **and**
email at minimum, regardless of preferences, because either can silently fail. With no push
channel, there is no third free fallback, so neither of these two is ever optional.

---

<!-- BEGIN GENERATED — do not edit by hand. Run ./web/gen-comms.sh -->

## 4. The message catalogue

Generated from `web/app.js`. Every string below is the string the module sends: the
catalogue is emitted from the same definitions the running code reads, so a copy change
lands here without anyone remembering to update it.

### 4.1 SMS — the two action states only

Money movement carries no SMS at all (§7, §8). What remains is margin shortfall and dues,
where the message has to reach everyone.

| Template | Chars | Encoding | Segments |
|---|---:|---|---:|
| `THINQ_MARGIN_SHORTFALL` | 136 | GSM-7 | 1 |
| `THINQ_SHORTFALL_CLEARED` | 62 | GSM-7 | 1 |
| `THINQ_SQUARED_OFF` | 110 | GSM-7 | 1 |
| `THINQ_DUES_OUTSTANDING` | 112 | GSM-7 | 1 |
| `THINQ_DUES_CLEARED` | 97 | GSM-7 | 1 |

**`THINQ_MARGIN_SHORTFALL`**

```
Your Thinq account has a margin shortfall of Rs 38,400.00. Add funds by 2:30 PM to avoid your positions being closed. Ref: MS8841 -Thinq
```

**`THINQ_SHORTFALL_CLEARED`**

```
Margin shortfall cleared. No action needed. Ref: MS8841 -Thinq
```

**`THINQ_SQUARED_OFF`**

```
2 position(s) closed due to a margin shortfall of Rs 38,400.00. See order book for details. Ref: MS8841 -Thinq
```

**`THINQ_DUES_OUTSTANDING`**

```
Your Thinq account has Rs 24.37 due. Trading and withdrawals are blocked until it is cleared. Ref: DU2207 -Thinq
```

**`THINQ_DUES_CLEARED`**

```
Rs 24.37 received. Your Thinq account dues are cleared and trading is enabled. Ref: DU2207 -Thinq
```

### 4.2 Adding funds — outcomes

| Outcome | Entry becomes | Primary action | Message |
|---|---|---|---|
| Bank declines | `failed` | Try Again | Your bank declined this payment. If any amount was debited, it will be refunded within 1–3 business days. |
| Not enough in bank | `failed` | Try Again | Your bank reported insufficient balance. Try using another bank account or add funds and retry. |
| Bank’s own limit | `failed` | Try Again | The amount exceeded your bank’s per-payment limit. Try a smaller amount. |
| No answer from bank | `unknown` | Try Again | Your bank hasn’t confirmed the payment yet. Don’t pay again. If debited but unsuccessful, the amount will be returned within 3 working days. |
| Our service is down | `failed` | Try Again | This one’s on us. We couldn’t reach the payment service, so nothing was sent or debited. |
| User backs out | `failed` | Try Again | Payment cancelled before approval. Nothing was debited. Try again when you’re ready. |

The alternative action offered beside it is whichever **other route we can execute** still has
headroom for the amount today. A self-service route is never offered as a one-tap recovery —
the button would promise a payment and deliver a set of instructions.

### 4.3 Withdrawal — on submit

The only moment with a user in front of it.

| Outcome | Window title | Message |
|---|---|---|
| Accepted | Withdrawal request submitted | The available amount at the end of today will be sent to the destination account. |
| After the cut-off | Withdrawal requested | Your request was placed after the 3:00 PM cut-off. It will be processed on the next working day. You can still cancel it. |
| Held for review | Withdrawal under review | Your withdrawal is under review. We’ll update you within 1 working day — no action is needed. |

### 4.4 Withdrawal — at end of day

Decided by the payout run hours later, with nobody watching. **None of these is a dialog.**
They reach the user as a message and as a transaction that has changed by the time they
next open the screen.

| Outcome | Transaction shows | Request |
|---|---|---|
| Sent in full | sent in full | closed |
| Partly sent | part of the request was available at the end of the day | closed |
| Nothing left | no funds were available at the end of the day | closed |
| Bank refuses it | your bank could not accept the transfer | closed |
| Bank rail is down | the banking network was unavailable — queued for the next run | **stays open and cancellable** |

### 4.5 Mandated settlement — announced before, notified after

Rule W8 is two messages for one movement: the return is **announced before the date**,
executed on it, and **notified after it**. Rule C10 governs the copy of both. Neither carries
an SMS — money movement is not one of the two states SMS is reserved for (Rule C2). The
settlement date itself comes from the mandated calendar, so it appears below as a slot.

| Rung | Channel | Template | Fallback |
|---|---|---|---|
| Announced — three working days before | WhatsApp | `thinq_rac_advance_notice_v1` | Email, if this user has no WhatsApp opt-in |
| Unused funds returned | Email | — | — |

**`thinq_rac_advance_notice_v1`**

```
*₹5,000.00 of unused funds will be returned to your bank on {settlement date}*

Brokers are required to return funds left unused, on a set calendar. You did not request this, nothing is wrong, and no action is needed.

It will go to HDFC Bank ••4471. Money you are trading with is not returned, so if you would rather keep it here, use it before {settlement date}.

Reference: PO7742
```

**Email — subject line**

```
₹5,000.00 unused funds returned — monthly settlement
```

<!-- END GENERATED -->

---

## 5. Which channel carries which message

**Rule C2 — SMS reaches everyone; nothing else does.** It is the only channel that needs no
opt-in, no inbox and no internet, so it is reserved for the two states where the account cannot
wait: a margin shortfall and an outstanding debit balance. Spending it on a routine payment
confirmation teaches people to ignore it, and it then fails when it carries a shortfall.

| State | SMS | WhatsApp | Email |
|---|---|---|---|
| Margin shortfall | ✅ ladder of three | ✅ | ✅ |
| Dues outstanding | ✅ from day 14, or day 0 above ₹500 | ✅ above ₹500 | ✅ day 0 |
| **Adding funds** | — | ✅ failure, pending, reversal | ✅ success, and as fallback |
| **Withdrawal — on submit** | — | — | ✅ |
| **Withdrawal — at end of day** | — | ✅ sent, partly sent | ✅ every outcome |
| **Mandated settlement** | — | ✅ the announcement, three working days before the date | ✅ the return itself, on the date; and as the announcement's fallback |

**Rule C3 — Success is a receipt; failure is news.** A successful payment is something the user
just watched happen, so its message exists to be *found later* — email's job, and the only channel
that can carry the route, the fee and the effect on the balances. A failed or stalled one is
something they do **not** know, while holding a phone wondering where their money went.

**Rule C4 — Where WhatsApp is the only channel, email is the fallback, not the silence.**
Absence of opt-in drops the WhatsApp step. For a failed or pending payin that would leave us
sending nothing at all in exactly the case the user most needs to hear from us.

---

## 6. The two action states — margin shortfall and dues outstanding

> **Reconstructed 20 Aug 26.** These requirements were counted in the register and committed by
> the roadmap without ever being written; the behaviour existed only as templates in §4.1, the
> channel matrix in §5, the cadence in §10, and Rules C11 to C13. Each requirement below is
> derived from one of those, and names its source. **REQ-605, REQ-606, REQ-607 and REQ-610 were
> withdrawn rather than reconstructed** — their only surviving source was a passing clause, and
> writing them would have meant inventing a Must-Have. What they were meant to cover is folded
> into REQ-602 (the deep link) and REQ-603 (the email's arithmetic) below, where it is grounded
> in stated behaviour rather than in intent.

This is the band SMS exists for. Both states share one property that no money movement has: the
account needs the user to **do something**, and the cost of them not knowing is measured in
squared-off positions or compounding debt.

### REQ-601 — Escalate a margin shortfall on a capped ladder (Must Have)

- **User Story:** As Nikhil, I want to be told about a shortfall while I can still fix it, and told again if I have not, so that my positions are not closed for me because I missed one message.
- **Acceptance Criteria:**
  - [ ] WHEN a margin shortfall is identified, THE SYSTEM SHALL send the shortfall intimation on SMS and email at minimum, regardless of the user's preferences, per Rule C1 and Rule C13.
  - [ ] THE SYSTEM SHALL escalate the shortfall as a ladder of three steps, per the channel matrix in §5.
  - [ ] THE SYSTEM SHALL send at most three shortfall SMS in one day, per Rule C12.
  - [ ] THE SYSTEM SHALL send every step of the ladder regardless of quiet hours, per Rule C11.
  - [ ] THE SYSTEM SHALL state the amount short and the deadline before positions may be closed in every step, per Rule H7.
  - [ ] THE SYSTEM SHALL NOT send any shortfall message where the shortfall is under ₹1.00, per §10.

### REQ-602 — Carry the exact amount and a way to act into every action message (Must Have)

- **User Story:** As Nikhil, I want the message to take me straight to funding the exact amount I am short, so that I am not re-entering a figure under a deadline.
- **Acceptance Criteria:**
  - [ ] WHERE a channel supports an action control, THE SYSTEM SHALL present the action that resolves the state as that control, per Rule C2's channel allocation.
  - [ ] WHEN a message offers an action, THE SYSTEM SHALL carry the exact amount required into the surface it opens, so the user is not asked to retype it.
  - [ ] THE SYSTEM SHALL send SMS without any link, per Rule C16.
  - [ ] WHERE a channel cannot carry an action control, THE SYSTEM SHALL state the amount and the deadline in text so the message is actionable without one.
  - [ ] THE SYSTEM SHALL resolve every action control against the funds screen, which reads current figures at the moment it is drawn, rather than against the figures held when the message was queued, per REQ-621.

### REQ-603 — Show the shortfall email's arithmetic, and disclose the state in the subject (Must Have)

- **User Story:** As Nikhil, I want the email to show me how the shortfall was arrived at, so that I can tell whether it came from my own trade or from the market.
- **Acceptance Criteria:**
  - [ ] WHEN a shortfall email is sent, THE SYSTEM SHALL set out the figures that produced the shortfall in rows, because email is the only channel that can carry a breakdown.
  - [ ] THE SYSTEM SHALL state the requirement, the available margin and the resulting shortfall as separate named figures rather than the shortfall alone.
  - [ ] THE SYSTEM SHALL state the cause, distinguishing a shortfall the user caused from one caused by a market move, per Rule B8.
  - [ ] THE SYSTEM SHALL state the account's state in the subject line, so that the message is identifiable without being opened.
  - [ ] THE SYSTEM SHALL take every figure from the same source as the funds screen, per REQ-621.

### REQ-604 — Pair each channel to its role and drop a step rather than block the ladder (Must Have)

- **User Story:** As a compliance owner, I want the mandatory intimation to go out even when the richer channels are unavailable, so that a missing opt-in never suppresses a regulatory message.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL send each step of the ladder on the channels the matrix in §5 assigns to that state.
  - [ ] IF the user has not opted in to WhatsApp, THEN THE SYSTEM SHALL drop the WhatsApp step silently and SHALL NOT block or delay the remaining steps, per REQ-624.
  - [ ] THE SYSTEM SHALL NOT suppress a shortfall intimation on the basis of any user preference, per Rule C13.
  - [ ] WHERE a step is dropped, THE SYSTEM SHALL record the drop and its reason against the account, per REQ-623.
  - [ ] THE SYSTEM SHALL NOT rely on a single channel for either action state, per Rule C1.

### REQ-608 — Band dues messaging by amount and age, never by day count alone (Must Have)

- **User Story:** As Arun, I want to hear about money I owe in proportion to how much it is and how long it has been outstanding, so that a trivial debt does not read like an emergency and a real one does not go unmentioned.
- **Acceptance Criteria:**
  - [ ] WHEN an account first enters a debit balance, THE SYSTEM SHALL send the dues email on day 0, per the matrix in §5.
  - [ ] WHERE the amount owed is above ₹500, THE SYSTEM SHALL send the dues SMS on day 0 rather than deferring it, per the matrix in §5.
  - [ ] WHERE the amount owed is at or below ₹500, THE SYSTEM SHALL send the dues SMS from day 14, per the matrix in §5.
  - [ ] WHERE the amount owed is above ₹500 and the user has opted in, THE SYSTEM SHALL send the dues message on WhatsApp, per the matrix in §5.
  - [ ] THE SYSTEM SHALL send dues reminders on day 0, day 7, day 14, day 30 and monthly thereafter, and SHALL NOT send them daily, per §10.
  - [ ] THE SYSTEM SHALL state the rate the debt is accruing at, taken from configuration, per REQ-708 and Rule G1.

### REQ-609 — Confirm a cleared debt, once (Must Have)

- **User Story:** As Arun, I want to be told when I no longer owe anything, so that I know the matter is closed and stop receiving reminders.
- **Acceptance Criteria:**
  - [ ] WHEN a debit balance is cleared, THE SYSTEM SHALL send the clear-down confirmation stating that the account is no longer in debt.
  - [ ] WHEN a debit balance is cleared, THE SYSTEM SHALL stop the dues reminder sequence, per REQ-622, which queues messages against the event rather than the schedule.
  - [ ] WHERE an accrual posts for the period before clearance, THE SYSTEM SHALL notify the user of the residual rather than silently reopening the sequence, per REQ-502.
  - [ ] THE SYSTEM SHALL NOT send a clear-down confirmation more than once for one clearance.
  - [ ] WHEN a shortfall is cleared, THE SYSTEM SHALL send the corresponding cleared confirmation on the same channels the ladder used, per Rule C1.

---

## 7. Adding funds

**No SMS.** Nobody is defrauded by a deposit, so the out-of-band argument that earns an SMS
elsewhere does not apply. The outcomes and their copy are in §4.2.

### REQ-611 — Chase a pending payin twice, and not on a timer (Must Have)

- **User Story:** As Priya, I want to hear about a payment that has not completed without being messaged every few minutes, so that I know it is being watched without being alarmed.
- **Acceptance Criteria:**
  - [ ] WHILE a payin remains unconfirmed, THE SYSTEM SHALL send one chase message at 30 minutes from the attempt starting.
  - [ ] WHEN a payin is written off, THE SYSTEM SHALL send one further message at that point.
  - [ ] THE SYSTEM SHALL NOT send any other message about the same pending payin between those two, per Rule C12.
  - [ ] THE SYSTEM SHALL state that the outcome is unknown rather than failed while it remains unconfirmed, per Rule A9b.
  - [ ] WHILE the outcome is unknown, THE SYSTEM SHALL tell the user not to pay again.

### REQ-612 — Name a payin by its amount and last four digits, and nothing more (Must Have)

- **User Story:** As Priya, I want the confirmation to be enough for me to recognise which payment it was, so that I can match it without exposing my account details.
- **Acceptance Criteria:**
  - [ ] WHEN a payin is confirmed, THE SYSTEM SHALL state the amount and the last four digits of the source account.
  - [ ] THE SYSTEM SHALL NOT include the full bank account number in any message, per the PRD's security outcomes.
  - [ ] THE SYSTEM SHALL NOT include any balance figure in a payin confirmation, per REQ-621's single-source rule and the non-disclosure outcome in the index's Non-Functional Requirements.
  - [ ] THE SYSTEM SHALL state which route carried the payment, per REQ-702.

### REQ-613 — Show what the payin changed, including what it did not (Must Have)

- **User Story:** As Priya, I want the confirmation to tell me what my money can now do, so that I do not discover later that it cannot be withdrawn.
- **Acceptance Criteria:**
  - [ ] WHEN a payin is confirmed, THE SYSTEM SHALL state that available margin has risen, per Rule A5.
  - [ ] THE SYSTEM SHALL state explicitly that the withdrawable figure did not move, per Rule B4's *money added today* term.
  - [ ] THE SYSTEM SHALL state the date from which the money becomes withdrawable.
  - [ ] THE SYSTEM SHALL send this on email, which is the only channel that can carry the effect on more than one figure, per §2.

### REQ-614 — Give each payin failure its own message and its own recovery (Must Have)

> Reconstructed 20 Aug 26 from §4.2, which already carries the outcome set and the exact copy for each.

- **User Story:** As Priya, I want a failed deposit to tell me which thing went wrong, so that I do not retry the same thing and fail the same way.
- **Acceptance Criteria:**
  - [ ] WHEN a payin does not complete, THE SYSTEM SHALL send the message corresponding to its specific outcome from the set in §4.2, per Rule A9a.
  - [ ] THE SYSTEM SHALL distinguish a bank decline, insufficient funds at the bank, the bank's own per-payment limit, no answer from the bank, our service being unreachable, and the user cancelling before approval.
  - [ ] WHERE the outcome is *no answer from the bank*, THE SYSTEM SHALL present it as unknown rather than failed and SHALL instruct the user not to pay again, per Rule A9b.
  - [ ] WHERE a decline may have landed after a debit, THE SYSTEM SHALL state the refund conditionally rather than asserting that nothing was debited, per Rule C5.
  - [ ] WHERE the cause is ours rather than the bank's, THE SYSTEM SHALL say so, per Rule A9c.
  - [ ] THE SYSTEM SHALL offer an alternative route only where that route can be executed and has remaining headroom for the amount today, per Rule A9d.

### REQ-615 — State both figures when a payin moves one and not the other (Must Have)

> Reconstructed 20 Aug 26 from REQ-613's stated purpose and Rule B4's *money added today* term.

- **User Story:** As Priya, I want to see both what rose and what did not, so that the gap is explained at the moment it appears rather than when I try to withdraw.
- **Acceptance Criteria:**
  - [ ] WHEN a payin is confirmed, THE SYSTEM SHALL present the available-margin figure and the withdrawable figure together, with the change to each stated.
  - [ ] THE SYSTEM SHALL name the term of Rule B4 responsible for the withdrawable figure not moving.
  - [ ] THE SYSTEM SHALL take both figures from the same source as the funds screen, per REQ-621.
  - [ ] THE SYSTEM SHALL NOT send either figure to any third party, per the index's Non-Functional Requirements and the taxonomy rule they align with.

**Rule C5 — A message never asserts what it cannot know about the user's bank.** Where a
decline may have landed after a debit, the copy is conditional — *"If any amount was debited, it
will be refunded within 1–3 business days"* — because a flat *"no money was debited"* is a promise
we cannot keep, and a user who then finds a debit on their statement stops believing everything
else we send. Where the payment never reached the bank at all, the flat assertion is safe and is
used.

---

## 8. Withdrawal

**No SMS**, and two moments hours apart with different audiences.

**Rule C6 — Submitting a withdrawal and settling it are separate events, and only the first has
a user in front of it.** What the payout run decides — sent, sent short, not sent, returned by
the bank — happens during end-of-day processing with nobody watching. None of it can be a dialog;
all of it reaches the user as a message and as a transaction that has changed by the time they
next open the screen.

**Rule C7 — The request confirmation must say the amount can shrink, before the user commits.**
Under Rule W3 the request reserves nothing and is settled against whatever is available at end of
day. Every other part of this is bookkeeping; without that sentence, each partial transfer is a
complaint.

### REQ-616 — Confirm a cancelled withdrawal by email only (Must Have)

- **User Story:** As Arun, I want a cancellation I made myself recorded rather than announced, so that I am not messaged on every channel about something I just did.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal request is cancelled by the user, THE SYSTEM SHALL send the confirmation on email only.
  - [ ] THE SYSTEM SHALL NOT send an SMS or WhatsApp message for a user-initiated cancellation, per Rule C2.
  - [ ] THE SYSTEM SHALL state that no figure moved, because none was ever held, per Rule W3 and Rule W4.
  - [ ] THE SYSTEM SHALL record the cancellation in the account's history, per REQ-405.

### REQ-617 — Make a partial transfer its own message on both channels (Must Have)

- **User Story:** As Nikhil, I want to be told plainly when less was sent than I asked for, so that I am not left comparing my bank statement to a request I no longer remember the size of.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal settles for less than the amount requested, THE SYSTEM SHALL send its own message rather than a generic settlement message.
  - [ ] THE SYSTEM SHALL state the amount requested and the amount sent as two separate figures.
  - [ ] THE SYSTEM SHALL name the deduction accounting for the gap, per Rule W10 and REQ-308.
  - [ ] THE SYSTEM SHALL state that the request is now closed, per Rule W4a.
  - [ ] THE SYSTEM SHALL carry the same reason on the transaction itself, not only in the message, per Rule W4c.

### REQ-618 — State where the money is, never only its status (Must Have)

- **User Story:** As Arun, I want every terminal message to tell me where my money physically is, so that I know whether to look at my bank or at my account.
- **Acceptance Criteria:**
  - [ ] WHEN a withdrawal reaches a terminal outcome, THE SYSTEM SHALL state whether the money was sent to the bank account ending in the stated four digits, or was never deducted.
  - [ ] THE SYSTEM SHALL NOT send a terminal withdrawal message that states only a status.
  - [ ] THE SYSTEM SHALL NOT include the full bank account number, per the PRD's security outcomes.
  - [ ] WHERE the money was returned, THE SYSTEM SHALL state that nothing remains deducted and that the destination details need checking with the bank, per Rule W4c.

### REQ-619 — Give each end-of-day outcome its own message, with no dialog (Must Have)

> Reconstructed 20 Aug 26 from §4.4, which already carries the five outcomes and the exact transaction copy for each.

- **User Story:** As Nikhil, I want each way my payout can end to reach me as its own message, so that I understand what happened without being present when it did.
- **Acceptance Criteria:**
  - [ ] WHEN the payout run decides a request, THE SYSTEM SHALL send the message corresponding to its specific outcome from the set in §4.4.
  - [ ] THE SYSTEM SHALL distinguish sent in full, partly sent, nothing left, refused by the bank, and the banking rail being unavailable.
  - [ ] THE SYSTEM SHALL NOT interrupt the user with a dialog for any end-of-day outcome, because there is no user present, per Rule W4b.
  - [ ] WHERE the banking rail was unavailable, THE SYSTEM SHALL state that the request stays open and remains cancellable, and SHALL NOT close it.
  - [ ] WHERE any other outcome applies, THE SYSTEM SHALL state that the request is closed, per Rule W4a.
  - [ ] THE SYSTEM SHALL present the transaction as already changed by the time the user next opens the screen, per Rule W4b.

### REQ-620 — Carry the bank's own reference so the user can chase it (Must Have)

> Reconstructed 20 Aug 26 from Rule C8 and REQ-303's recorded-reference obligation.

- **User Story:** As Arun, I want the reference my bank can actually trace, so that chasing a payment does not end with the bank telling me my reference means nothing to them.
- **Acceptance Criteria:**
  - [ ] WHEN money has left for the user's bank, THE SYSTEM SHALL state the bank's own transfer reference in the message.
  - [ ] THE SYSTEM SHALL present the bank's reference and this module's own reference as separate named fields, per Rule C8.
  - [ ] THE SYSTEM SHALL NOT use one value for both, per Rule C8.
  - [ ] WHERE the bank's reference is not yet available, THE SYSTEM SHALL say so rather than presenting this module's reference in its place.
  - [ ] THE SYSTEM SHALL present the same reference on the transaction, so the message and the screen agree, per Rule C14.
  - [ ] THE SYSTEM SHALL NOT state a bank reference for a movement that was never deducted.

**Rule C8 — The UTR and our reference are different fields and never share a value.** The UTR is
the bank's identifier and the only one a bank can trace; the reference is ours and the only one
support can. Each message says which is which — *quote this number when contacting your bank*
against *keep this reference for your records* — so the user is sent to the party who can help.

**Rule C9 — Withdrawal notifications are not the account-takeover control, and must not be
relied on as one.** An earlier draft sent the request confirmation by SMS on the grounds that a
fraudulent request and a genuine one look identical to the system. That argument is sound but
belongs to **authentication, not notification**: whoever controls the account most likely controls
the inbox too, so an emailed alert is a record rather than a defence. This design is correct only
if the withdrawal request is itself protected out of band. See §12.

**Rule C10 — Money leaving an account nobody touched names the reason in its first clause.**
Unused funds are returned on the mandated calendar without anyone asking. To the user that is
money vanishing from an account they did not operate, which is indistinguishable from fraud until
something explains it.

---

## 9. Governing rules

**Rule C11 — Quiet hours are 9:00 PM to 8:00 AM.** No message is sent inside that window
**except**: margin shortfall at any step, and a returned payin on day 0. Both are time-critical
and both concern money the user cannot otherwise know about.

**Rule C12 — One SMS per event per day.** Margin shortfall is exempt; its ladder is capped at three
SMS in a day by REQ-601 itself.

**Rule C13 — Preferences do not suppress regulatory messages.** Margin shortfall intimation goes
out even to a user who has turned everything off. The preference screen must say so, rather than
appearing to offer a control it does not have.

**Rule C14 — Every message carries a reference.** The same reference appears on the funds screen,
so support and the user are looking at the same event.

**Rule C15 — Never send a full bank account number.** Last four digits only, everywhere.

**Rule C16 — Nothing we send ever asks for a PIN, password, OTP, or card detail, and no SMS
carries a link to a login page.** Every message that links, links to a page that requires the user
to already be signed in. This is stated once in the email footer.

**No Thinq SMS contains a link at all.** SMS is the only channel that reaches every user without
opt-in, which makes it both the most load-bearing channel and the most attractive one to imitate.
A user who has never received a link from us by SMS has a simple, absolute rule for spotting a
fake: any SMS claiming to be Thinq that asks you to tap something is not from Thinq. That rule
only holds if we never break it, including once, including for a shortfall with fifteen minutes
left on it.

**Rule C17 — Amounts are always written in full, with paise.** ₹38,400.00, never ₹38.4k and never
₹38,400. Rounded money in a message about money reads as an estimate.

**Rule C18 — A reference exists from the moment the attempt does, not from the moment it
succeeds.** A failed payment is precisely the one a user rings their bank about, so it cannot be
the one with no number to quote. The reference is minted with the attempt, and the failure
message, the funds screen and the statement all carry the same one.

**Rule C19 — Email is the only channel that may use structure.** An SMS is one paragraph and a
WhatsApp message is a bubble with buttons; only email can set a figure out in rows. A before-and-
after of a balance is a table or it is a run-on sentence, so nothing that needs structure is
attempted on a channel that cannot hold it, and nothing that email sets out in a table is
restated in prose elsewhere.

### REQ-621 — Generate every message from the same figures as the screen (Must Have)

- **User Story:** As a support owner, I want the message and the screen to agree, so that a customer reading one to me is describing the same account I am looking at.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL produce every figure in every message from the single definition of that figure, per Rule B12.
  - [ ] THE SYSTEM SHALL NOT allow any message to compute a figure of its own.
  - [ ] WHERE a figure has changed between a message being queued and being read, THE SYSTEM SHALL make the funds screen the current answer, because it reads its figures at the moment it is drawn.
  - [ ] THE SYSTEM SHALL carry the same reference on the message and on the transaction it concerns, per Rule C14.

### REQ-622 — Queue messages against the event, not against a schedule (Must Have)

- **User Story:** As Nikhil, I want a warning to stop arriving once the thing it warned about is over, so that I am not told to act on a state I have already fixed.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL queue each message against the event that caused it rather than against a fixed schedule.
  - [ ] WHEN the state that caused a queued message resolves before it is sent, THE SYSTEM SHALL drop that message rather than send it.
  - [ ] THE SYSTEM SHALL NOT send a message and then retract it.
  - [ ] WHERE a ladder step is dropped because its state resolved, THE SYSTEM SHALL record the drop and its reason, per REQ-623.

### REQ-623 — Log delivery per channel, with its outcome, visible to support (Must Have)

- **User Story:** As a support owner, I want to see what we tried to send and what happened to it, so that "I was never told" is a question I can answer from the account.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL record every send attempt against the account, per channel, with its outcome.
  - [ ] THE SYSTEM SHALL make that record visible alongside the account to support.
  - [ ] THE SYSTEM SHALL record a suppressed or dropped message with the reason it was not sent, not only the messages that were sent.
  - [ ] THE SYSTEM SHALL retain the delivery record for the applicable retention period, which is unconfirmed and tracked as C-Q6 in §11.

### REQ-624 — Require explicit WhatsApp opt-in, recorded with its provenance (Must Have)

- **User Story:** As a compliance owner, I want every WhatsApp opt-in to carry when and where it was given, so that a consent claim can be evidenced rather than asserted.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL send on WhatsApp only where the user has explicitly opted in.
  - [ ] THE SYSTEM SHALL record the date of the opt-in and the surface on which it was captured.
  - [ ] WHERE no opt-in exists, THE SYSTEM SHALL drop the WhatsApp step silently and SHALL NOT block or delay any other step, per REQ-604.
  - [ ] WHERE WhatsApp would have been the only channel for a message, THE SYSTEM SHALL send it by email instead rather than sending nothing, per Rule C4.

### REQ-625 — Version every template so a delivered message can be reconstructed (Must Have)

- **User Story:** As a compliance owner, I want to reproduce exactly what a customer was sent on a given date, so that a dispute is settled by the record rather than by recollection.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL treat a copy change as a new template version rather than an edit to the existing one.
  - [ ] THE SYSTEM SHALL record which template version was used for each message sent.
  - [ ] THE SYSTEM SHALL retain superseded versions for as long as the delivery record referencing them is retained.
  - [ ] THE SYSTEM SHALL NOT alter a template version once a message has been sent from it.

### REQ-626 — Let preferences cover the optional channels only (Must Have)

- **User Story:** As Arun, I want to turn off the messages I can turn off, and be told plainly which ones I cannot, so that the preference screen does not appear to offer a control it does not have.
- **Acceptance Criteria:**
  - [ ] THE SYSTEM SHALL allow the user to control WhatsApp and non-regulatory email.
  - [ ] THE SYSTEM SHALL NOT allow any preference to suppress margin shortfall intimation or dues messaging on SMS, per Rule C13.
  - [ ] THE SYSTEM SHALL state on the preference surface which messages cannot be turned off, rather than presenting a control that has no effect.
  - [ ] WHERE a user has disabled an optional channel, THE SYSTEM SHALL still send the regulatory messages on their mandated channels, per Rule C1.

### REQ-627 — Keep an SMS-only user reachable, and flag the account (Must Have)

- **User Story:** As a support owner, I want to know when the usual ladder cannot reach someone, so that their state is not treated as "notified" when it is not.
- **Acceptance Criteria:**
  - [ ] WHERE a user's email is bouncing and no WhatsApp opt-in exists, THE SYSTEM SHALL treat SMS as the only reachable channel.
  - [ ] WHEN such an account is identified, THE SYSTEM SHALL flag it so support can see that the usual ladder did not reach the user.
  - [ ] WHILE such an account is in an action state, THE SYSTEM SHALL keep the funds-screen banner present without allowing it to be dismissed.
  - [ ] THE SYSTEM SHALL record the bounce and the absent opt-in against the account, per REQ-623.

---

## 10. What we deliberately do not send

| Not sent | Why |
|---|---|
| Push notifications | There is no mobile application. Nothing here assumes one |
| A message for every charge below the debt threshold | The statement is the right surface for routine charges. Messaging each one devalues the channel |
| A daily reminder while dues are outstanding | Day 0, 7, 14, 30, then monthly. Daily dunning over ₹24.37 is harassment, and it does not make the user pay faster |
| Marketing or cross-sell inside any of these messages | A margin shortfall message that also advertises a product is the fastest way to lose the channel |
| A "your withdrawal is being processed" SMS | Not an action state. The funds screen shows it, and email carries the record |
| Anything at all when the shortfall is under ₹1.00 | Rounding artefacts are not events |

---

## 11. Open questions

*Three of the eight below closed on 19 Aug 26; five remain open and are marked as such. The
section was previously titled "resolved 19 Aug 26", which was true of the three and misleading
about the rest.*

| # | Question | Answer |
|---|---|---|
| C-Q1 | Interest rate on debit balances | **Configuration-driven.** A placeholder rate is used until the real one is set; see [Configuration](product-requirements-configuration.md) |
| C-Q2 | Is the square-off deadline uniform or per segment? | Open — arrives with the front office (Q4) |
| C-Q3 | Registered WhatsApp Business number and opt-in capture | Open — still gates the WhatsApp lane |
| C-Q4 | SMS header | **Resolved.** The registered header will be six characters, as DLT requires |
| C-Q5 | Mobile application on the roadmap? | Open |
| C-Q6 | Retention period for delivery logs | Open — assume seven years |
| C-Q7 | Support number and mailbox | Open — placeholder |
| **C-Q8** | **Is a withdrawal request protected by a one-time password?** | **No — and as of 20 Aug 2026 this is a BLOCKER ON PHASE 3.** Withdrawal does not ship until authentication rules. See §11 |

---

## 12. C-Q8 — the open security gap

**SMS is reserved for the two action states (Rule C2) and removed from money movement
entirely. The condition that makes that safe — that the withdrawal request is already protected
out of band (Rule C9) — does not hold.**

The consequence is specific, not theoretical. Someone who obtains access to an account can request
a withdrawal to a bank account already on file, and the only notification that leaves the building
is an email — most likely to an inbox the same person can reach, and in any case one that arrives
*after* the instruction rather than before it. There is currently **no point in the flow at which
the genuine account holder is required to act.**

Two ways to close it. They are not equivalent.

**A — Put a one-time password on the withdrawal request.** *(recommended)*
The OTP goes to the registered mobile and is required before the request is accepted. This stops
the withdrawal rather than reporting it, it arrives **before** the request is accepted, and it makes
Rule C9's assumption true rather than aspirational. Everything in §8 then stands unchanged.

**B — Restore the withdrawal-requested SMS.**
Reinstates `THINQ_PAYOUT_REQUESTED` with the number to call. Cheaper, and strictly weaker: it
tells the account holder what has already happened and depends on them reading it in time and
acting. It reduces the window; it does not close it.

**Neither is in scope for the funds module alone** — A belongs to authentication. This annex records
the gap so that the decision to leave SMS out of money movement is not mistaken for a decision that
the risk was assessed and accepted.

> **Ruled 20 Aug 2026 — this blocks Phase 3.** Withdrawal does not ship until authentication rules on
> option A. The gap is not theoretical and it is not FMS's to close, which is exactly why it can fall
> between two teams at a handover; it is recorded here as a gate rather than a risk so that it cannot.
> **Option A costs zero taxonomy change**: `otp_purpose: withdrawal_confirm` is already registered in
> THINQ-EVENTS-001 §5.8 and has no emitter, and `OTP Requested` / `OTP Resolved` already carry the
> funnel. The instrumentation is waiting for the product decision, not the other way round.

---

*(Removed 20 Aug 26. This section restated all eight questions from §11 in different words, with a
different Blocking column and no note saying which table governed. Two tables answering the same
question is how they drift apart, so the answers now live in §11 alone, with the blocking impact
recorded in the same row as the answer.)*
