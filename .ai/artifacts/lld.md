# LLD Consistency Pass — Fund Management System

| | |
|---|---|
| Stage | 5c — orchestrator cross-check. No skill runs here, and this is not a regeneration |
| Inputs | `lld-backend.md` (Stage 5a, APPROVED) and `lld-frontend.md` (Stage 5b, APPROVED) |
| Purpose | Verify that the API contract the client consumes is the one the server defines |
| Date | 2026-08-21 |

---

## 1. What this consistency pass checks, and how

Two documents were written against the same HLD by two passes that could not see each other's output.
5c exists because "both were approved" does not mean "both describe the same interface".

Four checks, each run mechanically against the documents rather than by reading them sympathetically:

1. **Endpoint surface** — every path the client calls exists on the server, and every path the server exposes has a consumer or a stated reason not to.
2. **Field parity** — every field the client's view models read exists in a server DTO with the same name.
3. **Error parity** — every error code the server returns has a client treatment, and the client handles no code the server never sends.
4. **Decision parity** — neither document reopened a decision the HLD settled, and neither assumed the other would do something it does not do.

---

## 2. Result — agreement and discrepancies

**Iteration 1 found one blocking defect and one gap. Both are now closed, and this document records
both the finding and its closure** — the finding is kept rather than deleted, because how the hole got
there is more useful to a future reader than the fact it no longer exists.

| Check | Iteration 1 | Now | Verified by |
|---|---|---|---|
| Endpoint surface | Pass, one expected asymmetry | **Pass** | Every `/funds/*` path in both documents compared as sets |
| Field parity | **Fail** — two fields consumed and not defined | **Pass** | Seven contract fields checked in both documents; all present |
| Error parity | **Gap** — one server code with no client treatment | **Pass** | All ten server error codes checked for a client treatment; all ten found |
| Decision parity | Pass | **Pass** | Eight settled decisions checked against both documents |

### Re-run after Stage 6 (21 Aug 26)

Both LLDs changed substantially in response to the Stage 6 review — the backend gained a status-query
step in the payout run, an instruction-key encoding, a scheduled-intent table, a mapper contract and a
message-resubmission path; the frontend rewrote a component spec, replaced a context with lifted state
and specified a loading state. All four checks were re-run against the amended documents and all four
still pass.

No new contract divergence was introduced by the fixes, which is the specific thing this re-run exists
to establish: the backend added `fms_message_intent` and `EntryDescriptionMapper` without changing any
DTO the client reads, and the frontend's changes were internal to components rather than to what it
consumes.

### Closure

| Finding | Fix applied | Where |
|---|---|---|
| §3.1 `lastSuccessfulDepositPaise`, `postFundingDestination` absent from the server DTO | Both added to `FundsSummaryResponse`; §7.8's REQ-201 row rewritten from "5b" to state what the backend supplies; the REQ-709/710 row likewise | `lld-backend.md` §4.2 and §7.8, Stage 5a iteration 1 |
| §3.2 `calendar_unavailable` falling through to the generic upstream banner | Its own row in the error table: the withdrawable figure renders unavailable with its own reason, the other two figures stay visible, the withdraw action is disabled | `lld-frontend.md` §15, Stage 5b iteration 1 |

Neither fix required a schema change, and neither reversed a decision.

---

## 3. API contract findings

### 3.1 Blocking — the client reads two fields the server does not return

`lld-frontend.md` §8 defines `FundsSummaryView` with:

```ts
readonly lastSuccessfulDepositPaise: number | null;
readonly postFundingDestination: string | null;
```

`lld-backend.md` §4.2 defines `FundsSummaryResponse` with ten fields, and **neither of these is among
them.**

This is not a naming mismatch a mapper could paper over. The data does not exist on the server side of
the contract, and two approved requirements depend on it:

| Field | Requirement | What breaks without it |
|---|---|---|
| `lastSuccessfulDepositPaise` | **REQ-201**, Rule A1 | The amount field cannot open on the last successful deposit. It would open empty for every user on every visit — the behaviour Rule A1 was *revised away from*, with the revision and its reasoning recorded in the PRD |
| `postFundingDestination` | **REQ-709, REQ-710** | The confirmation cannot decide between offering the destination and dismissing plainly. Both requirements are a branch on this value |

**How it happened, because the pattern will recur.** `lld-backend.md` §7.8 marks REQ-201 as
*"the backend supplies the applicable minimum and the debt waiver; everything the requirement is about
is the field. **Stage 5b**"*, and marks REQ-709 and REQ-710 as **5b** outright. Stage 5a read "the
substance is presentation" and concluded the requirement was wholly the client's. Stage 5b read the
same requirement and correctly concluded it needs server data to render.

Both readings are defensible in isolation. **A requirement being presentational does not mean it needs
no data** — and that assumption is what put a hole in the contract.

**Fix — a Stage 5a iteration, not a 5c edit.** This pass does not regenerate either document. The
correction belongs where the contract is defined:

1. Add both fields to `FundsSummaryResponse` in `lld-backend.md` §4.2.
2. Add a row to §7.8 stating what the backend supplies for REQ-201, REQ-709 and REQ-710 — the last
   successful deposit amount and the configured destination — rather than marking them wholly 5b.
3. `lastSuccessfulDepositPaise` is the most recent `fms_payin_attempt` in a terminal successful state
   for the account, null where none exists, which is REQ-201's first-time case. **No schema change** —
   V22 already holds it.
4. `postFundingDestination` is the configured value REQ-709 names, null where unconfigured, which is
   REQ-710's branch. **No schema change** — it is configuration.

Neither addition changes a decision. Both are fields the design already implies and neither document
wrote down.

### 3.2 Gap — `calendar_unavailable` has no client treatment

`lld-backend.md` §4.4 returns `503 calendar_unavailable` when the settlement calendar cannot be
reached, which under OA-5 is the expected state until EB-9 resolves.

`lld-frontend.md` §15 handles `503 upstream_unavailable` with a page-level banner and has no entry for
`calendar_unavailable`, so it would fall through to the generic upstream treatment.

That is wrong in a specific way. The generic treatment says *"cached figures remain visible with their
age"* — but a calendar failure means the withdrawable figure could not be **computed at all**, not that
a refresh failed. Rule B4's unsettled-proceeds deduction is measured in settlement days, so without the
calendar there is no figure to show at any age.

**Fix — a Stage 5b iteration.** Add a row to §15: `calendar_unavailable` renders the withdrawable
figure as unavailable with its own reason, leaves the ledger balance and available margin visible, and
disables the withdraw action. It is closer to `withdrawable_unavailable` than to an upstream outage.

A gap rather than a blocker because the failure mode is a wrong explanation, not a wrong figure — the
user is told something unhelpful rather than something false.

### 3.3 Expected asymmetry — `/funds/payin/callback`

The server defines it; the client never calls it. Correct: it is Juspay's callback endpoint,
signature-verified before the body is parsed. Recorded so a future reader does not file it as dead
code.

No endpoint is called by the client and undefined by the server.

---

## 4. Decision parity — where the two documents agree

Checked because a contract can match field-for-field and still embody two different designs.

| Decision, settled upstream | Backend | Frontend | Agree |
|---|---|---|---|
| The derivation ships inside `/funds/summary` | `FundsSummaryResponse.derivation` is part of the summary payload | §7.1 renders it as a disclosure with no fetch and no error state | Yes |
| A disabled control's reason comes from the same payload | `ActionAvailabilityDto` carries `blockedReasonCode` and `responsibleTermCode` on the summary | §7.4 reads it from the summary; no second request | Yes |
| No client-side money arithmetic | Every rule enforced server-side; §4.3 re-checks in the domain | §14 states the client never compares an amount to a figure to gate a submit | Yes |
| Money is integer paise | `MoneyDto(long paise, String currency)`, taxonomy R5 | `MoneyView` formats at the API boundary; components never format | Yes |
| Withdrawable can be absent | `withdrawable` null when `withdrawableState != RECONCILED` | `WithdrawableView` is a three-arm union with no nullable amount | Yes — and the client's union is *stricter* than the wire shape, which is the right direction |
| One open withdrawal request | Partial unique index; `409 request_already_open` | §11 and §15 handle 409 with a link to the open request | Yes |
| Validation is shape at the edge, rule in the domain | §4.3's two-layer table | §14 restates the same split and defers every business rule | Yes |
| The period survives a view switch | `/funds/transactions` takes view and period independently | `PeriodContext` sits above the switcher | Yes |

**Neither document reopened an HLD decision.** No new bounded context, no second rendering model, no
alternative payout path, no client-side derivation.

### 4.1 One place each is stricter than the other, deliberately

The client's `WithdrawableView` union cannot represent "reconciled with no amount", which the wire
shape technically permits (`withdrawableState: 'RECONCILED'` with a null `withdrawable`). The mapper in
`api/client.ts` is where that impossible combination is rejected.

This is correct — a client type narrower than the wire type is a client that cannot render an
impossible state — and it is recorded here so the mapper's throw reads as intentional rather than as
defensive noise.

---

## 5. Traceability effect

No cell changes as a result of this pass. The two requirements behind §3.1 are already cited:

| Req | Cell | Still correct after the fix? |
|---|---|---|
| REQ-201 | `lld-frontend.md#73-amountinput--req-201-rule-a13` | Yes. The field's behaviour is the requirement; the backend addition is the data it renders |
| REQ-709, REQ-710 | `lld-frontend.md#75-postfundingconfirmation--req-709-req-710` | Yes, on the same reasoning |

REQ-503 remains the only empty cell, blocked on EB-6 and correctly flagged by the Stage 9 gate.

---

## 6. Verdict on the pass

**The two LLDs describe the same system, and the interface between them now closes.** All four checks
pass on re-run: no field consumed and undefined, no server error without a client treatment, no
endpoint called and unimplemented, and no decision reopened by either side.

The hole this pass found was small in effort and real in consequence: two fields, no schema change, no
decision reversed. Left unfixed it would have surfaced at Stage 8 as a frontend unable to implement
REQ-201 or REQ-709 against the API it was handed — precisely the failure this stage exists to prevent,
and precisely the point at which it becomes expensive.

**The lesson is worth more than the fix, and is recorded here so Stage 6 can check for it elsewhere.**
Stage 5a marked three requirements "5b — the substance is presentation" and stopped. Stage 5b read the
same requirements and correctly saw they need server data. Neither was careless; the assumption that
did the damage is that *presentational* implies *data-free*. Any requirement one side marks as wholly
the other's is worth a second look for exactly this.

The decision parity result is the more reassuring half. Two passes, written independently against one
HLD, agreed on all eight decisions that mattered — including the two subtle ones: that the client's
types should be narrower than the wire shape, and that validation splits by kind rather than by layer.

**Stage 5 is complete.** Ready for Stage 6, where `lld-reviewer` and `frontend-lld-review` assess both
halves independently.
