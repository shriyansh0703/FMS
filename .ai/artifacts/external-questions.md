# Five questions that block the Fund Management System build

**What this is.** Five answers we need from people outside the design team before, or shortly after,
implementation starts. Each one is a task in the execution plan with real dependents — they are not a
checklist, they are the reason certain code cannot be written yet.

**Why they are here rather than in the design.** Each was found by reading a vendor contract or a
sibling PRD and discovering that the design had assumed something the document does not say. They are
written down instead of guessed.

**How long they hold things up.** Two of them block within days of starting. One could send us back to
the architecture. Two are narrower.

| # | Question | Owner | Blocks | Status |
|---|---|---|---|---|
| 1 | Does TechExcel's `Ledger` API page? | Back office / TechExcel | Transaction list, statement export | **ANSWERED from the contract** — §A1 |
| 2 | What is our trading and settlement calendar source? | Product owner with compliance | Payout arrival dates, mandated settlement | **ANSWERED in part** — no vendor has one; scope narrowed; §A2 |
| 3 | Does a withdrawal need out-of-band confirmation? | Authentication | All of Phase 3 | **Open — a ruling, not a fact. Instrumentation verified ready** — §A3 |
| 4 | Is FMS granted the WhatsApp channel? | The platform team who registers `X-Service-Principal` | Four messaging requirements | **Open — no registry exists in any document; empirical test given** — §A4 |
| 5 | Can we tell a duplicate payout instruction from a rejected one? | Back office / TechExcel | Nothing | **ANSWERED: no** — §A5 |

---

# Answers, 21 Aug 2026 — from the vendor contracts

Three of the five are now answered from the documents in `05-dependencies/`. **Two cannot be, and not
for want of looking** — question 3 is a decision nobody has taken, and question 4 is a grant no document
records. Both are answered as far as evidence allows: §A3 shows the instrumentation is already
provisioned, and §A4 gives an empirical test that settles it without waiting.

**One of the three answers reverses a decision this design made and propagated**, recorded in §A2
rather than quietly corrected.

## A1 — Yes to date-bounded per-account querying. No to paging.

`Ledger API` input parameters, from `TechExcel_API_Master.xlsx`:

| Parameter | Type | Required |
|---|---|---|
| `Client_code` | String(20) | **Mandatory** |
| `FromDate` | Date | **Mandatory** |
| `ToDate` | Date | **Mandatory** |
| `COCDLIST` | String(20) | Optional — segment filter |
| `TransType` | String(1) | Optional — R / P / … |
| `ShowAllData`, `ShowMargin`, `Merge_Company` | | |

**Date-bounded, per account: confirmed.** Both dates are mandatory, so every call is already bounded.
**Paging: there is none.** No offset, no limit, no cursor, no page token.

**The entry mirror is not needed, and OA-6's failure path does not trigger.** The transaction list is
served as a read-through, bounded by the period the trader has selected — which is exactly what REQ-403
specifies: a 30-day default, a custom range, a financial year.

**What the absence of paging does cost.** A very wide range returns everything in one response, so FMS
must bound what it requests rather than relying on the vendor to chunk it. Two consequences for the LLD:

- The transaction endpoint pages **within** a retrieved range rather than pushing paging to the vendor.
  At §5's estimate — roughly 5,000 rows for an active account's financial year, 60,000 worst case —
  this is a bounded in-memory operation, not a streaming problem.
- REQ-403's "every entry in the account's life remains reachable" is satisfied by widening the range,
  not by an unbounded scan.

**Two optional parameters are worth more than they look.** `TransType` filters by transaction type,
which serves REQ-402's two views directly. `COCDLIST` filters by segment, which is REQ-108's data
already available as a query rather than a post-filter.

## A2 — No vendor supplies a calendar. But the back office supplies the dates the calendar was needed for.

**Searched all three vendor workbooks for a calendar, holiday, trading-day or working-day surface.
There is none.** Not in TechExcel, not in Noren, not in Juspay. A calendar must come from an
authoritative published source or be maintained internally; that part of the question stands.

**But the scope of what it is needed for has shrunk, and this reverses a decision this design made.**

`Ledger API` returns, per entry:

| Field | Meaning |
|---|---|
| `SETL_PAYINDATE` | The settlement date, for a transaction bill |
| `LAST2SETL` | Whether the last two settlements completed — `Y` / `Y1` / `NULL` / `B` |
| `EXPECTED_DATE` | Receipt or payment clearing date |

Rule B4 deducts *"proceeds of sales that have not yet settled"*. This design assumed that identifying
them required counting settlement days forward from a trade date, and therefore a calendar — and on
that basis **Stage 3 concluded that EB-9 gates Phase 1**, moving it earlier than the PRD had it.

**The back office already carries the settlement date on the entry.** If `SETL_PAYINDATE` and
`LAST2SETL` identify unsettled proceeds directly, Rule B4 needs no calendar arithmetic at all, and
EB-9 returns to gating Phase 3 — which is where the PRD originally put it.

**What still needs a calendar, regardless:**

- **REQ-303 and REQ-707** — the payout arrival date is computed *forward* from the cut-off across
  working days. Nothing in the ledger answers "what is the next working day".
- **REQ-307** — mandated settlement dates are calendar dates.
- **Rule B6** — naming a settlement *holiday* as the cause of a longer deduction. Without a calendar we
  would show the correct extended date and not be able to say why. Degraded, not wrong.

### The remaining point — closed on 21 Aug 2026

The open question was whether `SETL_PAYINDATE` is populated for the *sale* entries Rule B4 deducts, or
only for something narrower called a "transaction bill". The Ledger's own field set answers it:

| Field | Description | What it tells us |
|---|---|---|
| `MKT_TYPE` | *"In case of transaction bill shows market type **N-Normal / T-Trade to trade / M-T+1 Normal / Z-T+1 Trade to trade**"* | Market types and **T+1 settlement**. These describe *trades* and nothing else |
| `ContractNo` | *"Contract number of transaction bill"* | A contract note. Issued for a trade |
| `SETTLEMENT_NO` | *"Settlement number in case of transaction bill entry"* | The settlement the trade belongs to |
| `SETL_PAYINDATE` | *"In case of transaction bill settlement date"* | That settlement's date |

**A "transaction bill" in this back office is the contract note for a trade.** Market type, T+1
settlement, contract number and settlement number all hang off the same qualifier as `SETL_PAYINDATE`,
and none of them means anything for a receipt or a payment. A sale is a transaction bill, and it carries
its settlement date.

**So Rule B4 needs no calendar arithmetic.** Unsettled proceeds are identified by the entry's own
`SETL_PAYINDATE` against today, with `LAST2SETL` confirming completion — both supplied by the system
that already knows.

**EB-9 therefore gates Phase 3, not Phase 1**, which is where the PRD had it before this design moved
it. The move was made on a reasonable inference that turned out to be wrong, and §A6 lists what needs
correcting.

*Confidence: this is read off field descriptions rather than stated by the vendor. It is strong —
`M-T+1 Normal` is not ambiguous — but a one-line confirmation from the back office would cost nothing
and remove the last doubt before the correction is applied.*

## A3 — Cannot be answered by any document, and here is what the documents *do* establish.

**This is a ruling, not a fact.** No document can decide whether a withdrawal requires out-of-band
confirmation, because nobody has decided it. Searched the taxonomy, the Profile PRD and the vendor
contracts; there is no record of a decision either way.

**What is established, and it lowers the cost of saying yes.** The taxonomy's `otp_purpose` is a
**closed enumeration of fourteen values**, and `withdrawal_confirm` is one of them, marked new:

```
register · login · digilocker · esign · aa_consent · aa_bank · contact_change ·
unfreeze · recovery_authorise · recovery_new_address · pin_change ·
session_unlock · ipo_mandate · withdrawal_confirm
```

In a taxonomy where event names are the scarce resource — 512 per account, permanent, not reclaimable —
and where rule R6 says *extend enumerations, never schemas*, adding a value to a **closed** enum is a
deliberate act. Someone provisioned for a withdrawal OTP.

`OTP Requested` and `OTP Resolved` already exist as events and already carry `otp_purpose`,
`otp_channel` and `resend_index`, and both are server-emitted.

**What this means for the ask.** The PRD claimed the OTP option *"costs zero instrumentation change"*.
That is now verified rather than asserted: the purpose value is registered, the events exist, and the
funnel is already there. So the question to authentication is not "would you build something new" but
**"the product taxonomy already carries `withdrawal_confirm` — will you emit against it, and by when?"**
That is a materially easier question to get a yes to.

**It still blocks Phase 3 until someone says yes.** A registered enum value is provisioning, not a
decision.

## A4 — There is no way to discover the grant. Ask the platform team, and here is the empirical check.

The Communication Service has no capability or self-description endpoint for granted channels. Its
setup section says the platform team must *"register your `X-Service-Principal` as a caller, **with the
channels you need granted**"*, and that a caller granted `{sms}` submitting on `email` is refused with
`403 channel_not_permitted` even when the template and the address are valid.

So the question is precisely addressed rather than answerable:

- **Ask:** the platform team who registers service principals — not the Communication Service team
  generally, and not whoever owns WhatsApp templates.
- **Ask for two things, because the doc says both are prerequisites:** the channel grant, *and* the
  `template_key` plus the exact `parameters` each template declares. A grant without templates is not
  usable.
- **The empirical check, if an answer is slow:** submit one notification on `whatsapp` in a
  non-production environment. A `403 channel_not_permitted` is a definitive no; anything else is a yes.
  This costs nothing and does not need a template to exist.
- Also worth confirming with infrastructure **who sets `X-Service-Principal`** — the guide notes it is
  usually written by the mesh or gateway rather than by application code, which means a grant can be
  correct and still fail if the header is not what the platform thinks it is.

## A5 — No, they are not distinguishable. The design's status query is necessary, not defensive.

The full error table for `Payout_Request_Addition`, including the description column:

| # | Validation | Error Code | Error Description |
|---|---|---|---|
| 3 | Input Value | `Input_Value_Validation` | `*` |
| **4** | **Duplication** | **`Input_Value_Validation`** | `*` |
| 6 | Token Missing | `Token Validation Missing` | `Please Get Credential` |
| 7 | Token Expired | `Token Validation Expired` | `Token Invalid After 24 Hours` |

The code is identical for rows 3 and 4, **and so is the description** — `*`, unspecified. Rows 6 and 7
show that this table does carry real description text where one exists, so the `*` is an absence rather
than a formatting artefact.

**Confirmed: a refusal cannot be read as "you already paid this" rather than "your request was
invalid".** The end-of-day run's decision to query payment status before reissuing an instruction is
therefore load-bearing, not belt-and-braces. Nothing in the design changes.

Worth stating in the code comment: this was checked against the contract on 21 Aug 2026 and found
indistinguishable, so a future engineer does not remove the status query as redundant.

## A6 — What changes if A2's single point confirms

These documents assert that EB-9 gates Phase 1. If `SETL_PAYINDATE` covers Rule B4, they are wrong and
need correcting together:

| Document | What asserts it |
|---|---|
| `hld.md` | §21 R1, §23 item 2, §22's phase sequencing note |
| `traceability.md` | Open item 2 |
| `planning.md` | The phase-frame note, TASK-04's placement in Stage 1 |
| `tasks.json` | TASK-04's `externalBlockers` escalation text |
| This document | §2 above |

**Nothing has been changed yet.** The correction depends on one confirmation, and reversing an approved
decision on an inference would be exactly the failure this pipeline keeps catching.

---

# The original questions, as written for sending

Questions 3 and 4 still need sending as-is. Questions 1, 2 and 5 are retained below for the record and
because question 2's remaining part — nominating a source — is still a live decision.

---

## 1. Does TechExcel's `Ledger` API support date-bounded paging per account?

**Ask them exactly this:** for a single account code, can we request ledger entries between two dates
and page through the result — and if so, is paging by offset or by cursor, and is the ordering stable
across pages?

**Why we are asking.** The Fund Management System deliberately stores no ledger entries of its own.
TechExcel is the system of record, and the transaction screen reads through to it on every request.
That decision is what keeps us from running a second set of books that has to agree with the first.

**What we do with each answer.**

| Answer | Consequence |
|---|---|
| **Yes, it pages** | Nothing changes. The transaction list is a read-through, as designed |
| **No, it returns everything or nothing** | We cannot serve a trader's history without copying entries into our own database — an entry mirror, its ingest path, and a standing obligation to reconcile the copy against the source |

**This is the one question that can undo an architecture decision.** The choice to make TechExcel the
system of record was taken deliberately and reviewed; a mirror partially reverses it. If the answer is
no, this goes back to the architects rather than being absorbed by the build team, because "we'll just
cache it" is how a second set of books gets created by accident.

**What it stops us building meanwhile:** the transaction list, the movement detail view and the
statement export — which is most of what Phase 1 puts on screen.

---

## 2. Which authoritative source supplies the trading and settlement calendar?

**Ask them exactly this:** do we take the trading-day and settlement-holiday calendar from TechExcel,
or download it from an authoritative published source — and under what licence and refresh cadence?

**Why we are asking.** This looks like a Phase 3 concern and is not. The withdrawable balance is
computed by subtracting, among other things, the proceeds of sales that have not yet settled. **That
period is measured in settlement days, not weekdays.** Without a calendar we would count Diwali as a
working day and show a wrong withdrawable figure — on the screen whose entire purpose is to be the
number a trader can trust.

**Phase 1 ships the three balances.** So this gates the first delivery, not the third. The original
plan recorded it as a Phase 3 dependency and that was wrong; the design review corrected it.

**What we do with each answer.**

| Answer | Consequence |
|---|---|
| **A named source** | We build the adapter and Phase 1 proceeds |
| **Not yet decided** | The withdrawable figure returns "unavailable" rather than a wrong number, and no mandated settlement executes on an unverified date. It fails safely and ships nothing |

**What it stops us building meanwhile:** the calendar adapter, and behind it the balance derivation —
which is the single point every other screen and every message depends on.

---

## 3. Does a withdrawal request require out-of-band confirmation, and is it authentication's to build?

**Ask them exactly this:** should a withdrawal request require a one-time password to the registered
mobile before it is accepted — and if so, will authentication provide it, on what contract, and by when?

**Why we are asking.** Today, someone who obtains access to an account can withdraw to a bank account
already on file. The only thing that leaves the building is an email — most likely to an inbox the same
person can reach, and arriving *after* the instruction rather than before it. **There is no point in
the flow at which the genuine account holder is required to act.**

This was recorded during requirements as a gate on Phase 3 rather than as a risk, specifically so it
could not fall between two teams at handover. It is not the fund system's to fix: the control belongs
to authentication.

**What we have already done.** The withdrawal endpoint accepts an optional step-up assertion and treats
a configured requirement as a precondition it verifies. **The seam exists and is inert.** When
authentication rules, turning it on is configuration plus an integration — not a change to how
withdrawals work.

**What we do with each answer.**

| Answer | Consequence |
|---|---|
| **Yes, authentication will provide it** | We integrate against their contract. Phase 3 proceeds once it exists |
| **No, accept the risk** | Record the acceptance explicitly, with who accepted it. The exposure is real and should not be inherited silently |
| **Not yet** | Phase 3 does not ship. Phases 1 and 2 are unaffected |

---

## 4. Is the Fund Management System granted the WhatsApp channel, and what address format does it use?

**Ask them exactly this:** is `whatsapp` in FMS's permitted channel list on the Communication Service,
what address format does it expect, and what does template registration require?

**Why we are asking.** The Communication Service names `whatsapp` as a valid channel and gates channel
use per caller. Its integration guide documents an address format for SMS and email only. We know the
channel is live in the estate — Profile sends bank-account verification results on it — so the question
is narrower than "does it exist": **are we in the grant?**

**What depends on it.** Four requirements were written around WhatsApp as the channel that carries an
action the trader can tap: the shortfall message with the exact amount, the ladder step that drops
silently when a trader has not opted in, the opt-in record itself, and the preference that covers it.

**What we do with each answer.**

| Answer | Consequence |
|---|---|
| **Granted** | Nothing changes. The design already models it |
| **Not granted** | Four requirements lose the channel they were written for. That is a product decision requiring a requirements amendment — not something the build team should improvise a substitute for |

**One thing worth knowing regardless:** the Communication Service sends **one channel per call**. A
message that must reach a trader on both SMS and email is two submissions that can fail independently,
which is already designed for.

---

## 5. Can a duplicate payout instruction be told apart from a rejected one?

**Ask them exactly this:** the `Payout_Request_Addition` error table lists a Duplication validation
that returns `Input_Value_Validation` — the same code as the Input Value validation above it. Which
fields does the duplication check compare, and is there anything in the response that distinguishes
"you have already sent this" from "this request was invalid"?

**Why we are asking.** The end-of-day payout run must be safe to re-run after a crash. If the database
fails after TechExcel accepted an instruction but before we recorded it, recovery leaves a request
looking unfinished while the money has already gone. Re-instructing then would pay twice, which is a
figure the requirements set to zero.

**This one does not block us, because we designed around it.** Rather than reissue and hope the
duplication check refuses us, the run **queries the payment status for the same reference first** and
only reissues if no record exists. An unreadable status stops that account and raises an alert — an
unread status is not an absent payment.

**What we do with each answer.**

| Answer | Consequence |
|---|---|
| **It keys on our reference and is distinguishable** | The duplication check becomes a useful second line behind the status query. No change required |
| **It is not distinguishable** | Also no change. This is why we do not depend on it |

**Asked anyway** because it is cheap to ask and it would let a future engineer simplify the run with
confidence rather than removing a safeguard they did not understand.

---

## What happens if none of these are answered

Phases 1 and 2 stall at the calendar and the transaction list; Phase 3 does not start; the messaging
requirements ship without their action channel. **Nothing ships wrong** — every one of these fails to
a safe state rather than a plausible-looking incorrect one, which was deliberate. But the balance
screen, which is the whole product, needs question 2 answered before it can show a correct number.

**Questions 1 and 2 are the two to chase first.**

---

# Two further blockers, found 21 Aug 2026 while building execution Stage 3

Both surfaced from the vendor contracts during implementation rather than from review, and both
stop TASK-11 (the RMS gateway) rather than slowing it. Neither can be answered from anything in
this repository.

## 6. How does a Java service call Kambala Noren?

**Blocks:** TASK-11, and through it REQ-103, REQ-105, REQ-106, REQ-107 and the reconciliation that
OA-1 turns on.

**What the documents establish.** Noren's money surface is a **C++ request/response protocol** with
`Start` / `Response` / `End` callback envelopes, not REST. `GetWithdrawalAmt` takes six function
parameters — `pEchoBackData` as a `void *`, then `char *` for broker id, account id, segment,
exchange segment and product — and answers on three callbacks. `hld.md` §7 records the protocol
correctly and says it "is an anti-corruption-layer concern (§22) and changes nothing above it".

**What no document establishes.** How a Spring Boot service on Java 21 invokes it. The options
differ enormously in cost, risk and operational shape:

- **JNI or the Foreign Function & Memory API** binding the CAPI library into the service process. A
  crash in vendor C++ takes the whole service down, which on this service means the money surface.
- **A sidecar** speaking the C++ protocol and exposing REST or gRPC to FMS. Another deployable,
  another failure domain, and a place the `Start`/`Response`/`End` stream can be turned into a
  request/response.
- **An existing bridge in the estate.** If the OMS already has one, this question is answered and
  the answer is "reuse it" — which is why this is worth asking before building anything.

**What is needed:** which of these the platform team already runs or intends, and if a bridge
exists, its interface.

**Why it was not assumed.** `MarginSource` is written and its semantics are fixed. Picking a
transport is an architecture decision that belongs to the HLD, and inventing one here would produce
a gateway that compiles, reviews cleanly, and cannot connect to anything.

## 7. What is `GetRmsLimits`' signature?

**Blocks:** the `margin(AccountRef)` half of `MarginSource`, and with it every margin figure the
product shows. `withdrawableAuthority` is unaffected — `GetWithdrawalAmt` is fully specified.

**What the documents establish.** The API reference names `GetRmsLimits` and then states plainly:
*"NOT SPECIFIED in the supplied PDFs. The name is all these documents give; the signature,
parameters and callbacks are in the CAPI package (`include/noren_cpp_data_structs.h` and
`reference_code/AppMain.cpp`)."*

Meanwhile `lld-backend.md` §7.8 cites `GetRmsLimits` as the source for REQ-105's per-trade-kind
deployable figure, and `tech-stack.md` lists it as integrated. **The design depends on a contract
this repository does not contain.**

**What is needed:** the CAPI package, or the two named files from it. Specifically: the parameter
list, the callback structs, and which fields carry available margin, used margin, collateral value,
the portion of the requirement met from collateral, and shortfall — the five figures
`MarginFigures` is defined in terms of.

**One thing to check when it arrives.** `GetWithdrawalAmt` returns `MaxWithdrawalAmt` as a
**`double`, in rupees**. If `GetRmsLimits` does the same, every margin figure crosses into this
system as a binary floating-point value, which collides directly with the paise-integer rule in
HLD §9.1c. `Money` offers no double conversion, deliberately. The anti-corruption layer will have
to take the shortest exact decimal representation of each double and refuse anything with sub-paise
residue — and that decision should be made with the real field types in hand, not guessed at now.


---

# Question 4 revisited, 21 Aug 2026 — the replacement contract narrows it

The Communication Service document was replaced (`caller-integration.md`, superseding
`Communication_API.md`). It settles half of question 4 and leaves the other half exactly where it
was.

**Settled: `whatsapp` is a channel the platform supports.** §9's `400 channel_unsupported` reads
*"Not one of `sms`, `email`, `whatsapp`"*, so the channel exists and the wire value is `whatsapp`.
That is the address-format half of the original question — answered.

**Not settled: whether FMS is granted it.** Channel support and channel permission are different
things in this contract, and the document is explicit about the separation (§3, "Registration is
separate from identity"): a principal must be registered as a caller *with an explicit list of
permitted channels*, and a channel outside that list returns `403 channel_not_permitted`. Nothing
in the document reveals what FMS's granted list contains, and §"Getting set up" confirms it is
configured by the platform's operators before a caller can send anything.

**So OA-2 stands, and the code is unchanged.** V26 still excludes `whatsapp` from
`fms_msg_channel_vocabulary`, `MessageChannel.WHATSAPP` still exists unused, and
`VocabularyDriftTest` still asserts the deliberate difference — so the day the grant is confirmed,
the build fails and says so.

**What to ask, now narrower than before:** is `svc-fms` (or whatever principal the mesh writes for
this service) registered as a caller, and does its granted channel list include `whatsapp`? The
empirical check is unchanged and now has a precise expected answer: submit on `whatsapp` and read
the `reason`. `channel_not_permitted` means registered without the grant; `caller_not_registered`
means the principal was never registered at all. The two need different people to fix them, which
is why the client now distinguishes them.
