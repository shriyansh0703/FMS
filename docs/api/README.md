# Fund Management System — API Reference

Version `v1`. Four files in this directory:

| File | What it is |
|---|---|
| `README.md` | This document — the endpoint reference and the rules behind it |
| [openapi.json](openapi.json) | OpenAPI 3.1.0, generated from the controllers by springdoc and asserted by `OpenApiSpecTest`. Feed it to a client generator |
| [fund-management.postman_collection.json](fund-management.postman_collection.json) | Postman collection, all seven endpoints, with saved example responses including the failure cases |
| [fms-client.ts](fms-client.ts) | A typed, zero-dependency `fetch` client. Copy it into the frontend, or read it as the reference implementation |

This document is the client-facing companion to the spec: the same endpoints, plus the rules a
generated client cannot express — which fields are nullable, what a `null` means, which error
codes each call can produce, and what the UI is obliged to render.

Seven endpoints, all under `/api/v1/funds`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/funds/payout` | Create the account's single open withdrawal request |
| `GET` | `/api/v1/funds/payout` | The open request, if there is one |
| `DELETE` | `/api/v1/funds/payout/{requestId}` | Cancel a request that has not been instructed yet |
| `GET` | `/api/v1/funds/payin/limits` | Remaining daily headroom per payment route |
| `GET` | `/api/v1/funds/transactions` | Transaction history for a period, in one of two views |
| `GET` | `/api/v1/funds/transactions/{reference}` | One entry in full |
| `GET` | `/api/v1/funds/statement.csv` | The same view and period as a CSV download |

---

## 1. Before you write a single call

### 1.1 Base URL

The service runs on port `8080` with no context path, so paths are exactly as written above. The
host comes from the environment; the `servers` block in `openapi.json` says `http://localhost`
because that is what the generator sees at build time, not because it is the deployment address.
Take the base URL from your environment config.

### 1.2 Authentication

Every endpoint except `/actuator/health/**` requires an authenticated caller. The service enforces
HTTP Basic:

```
Authorization: Basic base64(<UCC>:<credential>)
```

**This scheme is provisional, and it is worth knowing why before you build against it.** The
specification declares it as `platformAuth` — named after its role rather than its mechanism — and
says the same thing there. Basic carries the credential on every request and has no expiry,
revocation, rotation or scope, which is not what a service moving money should ship long term. It is
what exists because a filter chain that refuses beats a correct scheme that does not exist yet.

The replacement is a platform-issued token validated against the gateway's issuer. When that issuer
is settled, the scheme becomes `bearer` and the requirement keeps the name `platformAuth`, so a
generated client sees a changed scheme rather than a renamed security requirement. Send the complete
header value from your own configuration rather than composing the scheme in client code, and the
switch costs you one line — this is exactly what `fms-client.ts` does with its `authorization`
option.

**An earlier version of this document described a bearer JWT here, and the service never accepted
one.** Every client generated from the specification sent `Authorization: Bearer …` and received
`401 unauthenticated` on every call. If you are holding an older generated client, that is the cause.

The deployment is still expected to place a gateway in front of this service. Where one is present
it refuses before the request arrives, with the gateway's error shape rather than the one described
in §1.4 — so treat a `401` whose body is not an `ErrorResponse` as having come from the edge.

**No endpoint takes an account identifier.** Not in a path, not in a query string, not in a body.
The account is resolved from the token's subject claim. If you find yourself wanting to pass an
account id, the call you want does not exist — and a field for one would be rejected by the schema
test on the backend.

A consequence worth planning for: another trader's data is not forbidden, it is absent. Asking for
someone else's transaction returns `404`, and cancelling someone else's payout returns `409` with
reason `NOT_FOUND`. Confirming that a record exists would itself leak, so these responses are
indistinguishable from the record not existing at all.

### 1.3 Money is an integer count of paise

Every monetary value on this API, in both directions, is this object:

```json
{ "paise": 12345, "currency": "INR" }
```

`12345` paise is ₹123.45. The field is a 64-bit integer and the server refuses to coerce anything
else into it:

| You send | Result |
|---|---|
| `{"paise": 100}` | Accepted |
| `{"paise": 100.9}` | `400 invalid_request` — floats are refused, not truncated |
| `{"paise": "100"}` | `400 invalid_request` — a quoted number means a serialisation bug |
| `{"paise": 100, "currency": "USD"}` | `400 invalid_request` — the system holds INR only |

Divide by 100 for display and never for arithmetic. If your codebase has a currency helper that
takes a float, do not route these values through it.

### 1.4 Errors are branched on by `code`

Every failure — validation, domain refusal, upstream outage, unknown path — returns the same body:

```json
{
  "code": "amount_exceeds_withdrawable",
  "message": "requested 5000000 against a withdrawable figure of 3200000",
  "details": {
    "requested":    { "paise": 5000000, "currency": "INR" },
    "withdrawable": { "paise": 3200000, "currency": "INR" }
  }
}
```

- **`code`** is stable, machine-readable, and safe to send to a client. Branch on it. Map it to
  your own copy.
- **`message`** is a developer-facing explanation. It is not user copy, it is not translated, and
  it is not guaranteed stable between releases. Do not render it to a trader.
- **`details`** is omitted entirely when there is nothing to add. When present it carries the
  values needed to explain the refusal without a second request — see §6 for the shape per code.

`details` is the only reason most refusals do not need a follow-up `GET`. Use it.

### 1.5 Null handling

`ErrorResponse` omits null fields. Every other response serialises nulls explicitly, so
`"suggestedWiderPeriod": null` and `"remainingToday": null` appear in the JSON rather than being
absent. Two of those nulls carry meaning and are called out where they occur — treat neither as
zero and neither as missing data.

### 1.6 Copy keys, not English

`descriptionKey`, `shrinkWarningKey` and the entry `kind` values are keys your client resolves into
its own strings. No user-facing English crosses this boundary, which is what lets wording change
without a client release. §5 lists every key you need to have a translation for, including the
fallback.

---

## 2. Withdrawals

### 2.1 `POST /api/v1/funds/payout` — request a withdrawal

Creates the account's single open withdrawal request.

**Request**

```http
POST /api/v1/funds/payout
Authorization: Basic base64(<UCC>:<credential>)
Content-Type: application/json

{
  "amount": { "paise": 5000000, "currency": "INR" },
  "destinationRef": "acc-4471"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `amount` | `MoneyDto` | yes | Must be positive. A non-positive amount is refused by the domain, not by shape validation |
| `destinationRef` | string | yes | Profile's reference for a bank account already verified against this trader. Non-blank |

**Response — `201 Created`**

```json
{
  "requestId": 4242,
  "fmsReference": "FMS-2026-0821-4242",
  "arrivalDateQuoted": "2026-08-25",
  "shrinkWarningKey": "WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT",
  "state": "ACCEPTED",
  "withdrawableAtRequest": { "paise": 8000000, "currency": "INR" },
  "destinationMasked": "••••4471"
}
```

| Field | Type | Notes |
|---|---|---|
| `requestId` | int64 | Pass this to the cancel endpoint |
| `fmsReference` | string | What support quotes. Distinct from the bank's own transfer reference, which appears only after the money has moved |
| `arrivalDateQuoted` | date | When the money should arrive, computed from the settlement calendar. A quote, not a guarantee |
| `shrinkWarningKey` | string | Always present, always the same value in this phase. See below |
| `state` | string | One of the eight states in §5.1. Always `ACCEPTED` on creation |
| `withdrawableAtRequest` | `MoneyDto` | The withdrawable figure at the instant of the request |
| `destinationMasked` | string | Masked. The full account number is never returned by any endpoint |

**A withdrawal request reserves nothing, and the UI must say so before the trader commits.**
The request is settled at end of day against whatever is available then, so the amount that
actually leaves can be smaller than the amount requested. `shrinkWarningKey` is the copy key for
that warning and it is unconditional — it is on every response because the shrink is always
possible. Showing it after submission defeats its purpose; it belongs on the confirmation step.

**One open request per account.** Rule W4, enforced by a partial unique index in the database
rather than by a check in the service, so there is no window in which two requests both pass a
pre-check. A second request while one is open returns `409 request_already_open`. Do not build a
"pre-check then submit" flow around this — call `GET /api/v1/funds/payout` to render current state,
and handle the `409` on submit regardless of what that read said.

**Failures**

| Status | `code` | What happened | What the UI should do |
|---|---|---|---|
| `400` | `invalid_request` | Malformed body, missing field, wrong type, decimal paise | Field-level errors from `details`, where present |
| `409` | `request_already_open` | Rule W4 — one is already open | Show the open request; offer cancel |
| `409` | `withdrawable_unavailable` | The balance derivation and RMS disagree, or a source could not be reached | Block the action. The figure cannot be stood behind, and neither side is picked as the winner |
| `409` | `figures_stale` | The figures backing the check are too old | Offer a refresh. `details` carries `computedAt` and `computedBy` |
| `422` | `amount_exceeds_withdrawable` | Over the withdrawable figure | `details` carries `requested` and `withdrawable`; show the figure that would have worked |
| `422` | `destination_not_verified` | The destination is not a verified account of this trader | Send the trader to bank-account verification |
| `503` | `upstream_unavailable` | A back-office system is unreachable | Retryable. The vendor is deliberately not named |
| `503` | `calendar_unavailable` | The trading calendar is unreachable, so no arrival date can be quoted | Retryable. The system fails safe rather than guessing a date |

### 2.2 `GET /api/v1/funds/payout` — the open request

**Response — `200 OK`** with the same body as §2.1, or **`204 No Content`** with an empty body when
there is no open request.

The `204` is an ordinary state, not an error. `fetch` and `axios` both hand you an empty body here;
check the status before parsing. A request is "open" while its state is `ACCEPTED`,
`QUEUED_FOR_RUN` or `INSTRUCTED` (§5.1).

### 2.3 `DELETE /api/v1/funds/payout/{requestId}` — cancel

Cancellation is permitted while the request is `ACCEPTED` or `QUEUED_FOR_RUN`. It stays available
after a rail outage has pushed the request to `QUEUED_FOR_RUN`, because a trader whose payout was
deferred has more reason to want it stopped, not less.

**Response — `200 OK`**, the same body as §2.1 with `state: "CANCELLED"`.

**Failure — `400 invalid_request`** when `{requestId}` is not a number. The path variable is a
64-bit integer, so an unparseable segment is refused while the request is being bound and never
reaches the handler; `details.parameter` is `requestId`. This used to answer `500 internal_error`.

**Failure — `409 not_cancellable`**, with the reason in `details.reason`:

```json
{
  "code": "not_cancellable",
  "message": "request 4242 has been instructed to the payout rail and can no longer be stopped",
  "details": { "reason": "ALREADY_INSTRUCTED" }
}
```

| `details.reason` | Meaning | Suggested copy direction |
|---|---|---|
| `ALREADY_INSTRUCTED` | The money is on its way to the rail and cannot be stopped | Reassure — it will arrive |
| `ALREADY_TERMINAL` | The request already finished or was already cancelled | No action needed |
| `NOT_FOUND` | No such request for this account | Refresh the list |

The three reasons mean different things to a trader, so branch on them rather than showing one
generic refusal. `NOT_FOUND` is returned instead of `403` for a request belonging to someone else.

---

## 3. Adding funds

### 3.1 `GET /api/v1/funds/payin/limits` — remaining headroom today

**Response — `200 OK`**

```json
{
  "routes": [
    { "route": "UPI",         "remainingToday": { "paise": 10000000, "currency": "INR" }, "fee": { "paise": 0, "currency": "INR" } },
    { "route": "NET_BANKING", "remainingToday": { "paise": 100000000, "currency": "INR" }, "fee": { "paise": 0, "currency": "INR" } },
    { "route": "NEFT",        "remainingToday": null,                                      "fee": { "paise": 0, "currency": "INR" } }
  ]
}
```

**`remainingToday: null` means the route is uncapped. It does not mean zero.** Rendering it as
zero tells a trader NEFT is exhausted when it has no limit at all. Branch on `null` before
formatting.

The cap is daily and is measured against everything already sent on that route today, not per
transaction. Two transfers of the cap amount will not both pass.

**Do not hardcode limit figures into your copy.** A message naming ₹1,00,000 becomes wrong the day
Payments changes the value, and nobody looks in message templates when they change a limit. Render
from these figures. `fee` is ₹0 on every route in this phase and is still returned as a figure for
the same reason.

Routes in this phase: `UPI`, `NET_BANKING`, `NEFT`. Only rails the system can execute appear here —
a route the trader would have to complete in their own banking app is never offered, because the
button would promise a payment and deliver instructions.

---

## 4. Transactions

### 4.1 The two views

`MOVEMENTS` and `ALL_ENTRIES` answer different questions, and the split is the point:

- **`MOVEMENTS`** — money the trader moved in or out. Only `PAYIN`, `PAYOUT` and
  `MANDATED_RETURN`. This is "where is my money", and it is the common question, so it is the
  default.
- **`ALL_ENTRIES`** — every entry with its running balance. This is "explain my account".

Sale proceeds, purchase costs, charges and margin movements are **not** payins and never appear in
`MOVEMENTS`. Putting a trading outcome in the "where is my money" view answers a question nobody
asked and hides the one they did.

Each view must be reachable from the other **without losing the selected period** — that is why
both are one parameter on one endpoint. Keep `from` and `to` when the trader toggles the view.

### 4.2 `GET /api/v1/funds/transactions` — history for a period

**Query parameters**

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `view` | `MOVEMENTS` \| `ALL_ENTRIES` | `MOVEMENTS` | |
| `from` | date (`YYYY-MM-DD`) | — | Inclusive |
| `to` | date (`YYYY-MM-DD`) | — | Inclusive |

**Both bounds or neither.** Sending only one is treated as sending none, and you get the default
period back. The response echoes the period actually used, so render from that rather than from
what you asked for.

**The default period is the last 30 days**, inclusive of today. The number is not a round-figure
guess: the mandated return of unused funds runs monthly or quarterly and is among the most-queried
entries, so a shorter default routinely shows an empty table for a transaction the trader knows
happened.

**The maximum window is 92 days.** The back office's ledger endpoint has no pagination of any kind —
no offset, no cursor, no row limit — so the date window is the only bound on response size. A wider
range must be walked in windows by the caller. Anything over 92 days returns `400 invalid_request`,
as does an inverted period (`to` before `from`).

There is no pagination on this endpoint either, for the same upstream reason. Every entry in the
period arrives in one response. Budget for a few hundred rows on an active account over 92 days and
virtualise the list if that is a problem for your table component.

**Response — `200 OK`**

```json
{
  "view": "MOVEMENTS",
  "period": { "from": "2026-07-23", "to": "2026-08-21" },
  "entries": [
    {
      "reference": "VCH-4471",
      "date": "2026-08-19",
      "kind": "PAYIN",
      "descriptionKey": "ENTRY_PAYIN",
      "descriptionParameters": { "amountPaise": "5000000", "direction": "IN", "segment": "NSE_CASH" },
      "secondaryDetail": "SETT-2026-0819-11",
      "amount": { "paise": 5000000, "currency": "INR" },
      "direction": "IN",
      "runningBalance": { "paise": 13200000, "currency": "INR" },
      "segment": "NSE_CASH",
      "userCaused": true,
      "reversedBy": null,
      "reverses": null
    }
  ],
  "suggestedWiderPeriod": null
}
```

| Field | Type | Notes |
|---|---|---|
| `view` | string | Echoed back |
| `period` | `{from, to}` | The period **actually** covered. Render from this |
| `entries` | array | Possibly empty |
| `suggestedWiderPeriod` | `{from, to}` or `null` | Present only when `entries` is empty |

**Entry fields**

| Field | Type | Notes |
|---|---|---|
| `reference` | string | The back-office voucher number. This is the value the detail endpoint takes |
| `date` | date | |
| `kind` | string | One of the ten kinds in §5.2 |
| `descriptionKey` | string | Copy key. Resolve it; never display it raw |
| `descriptionParameters` | `map<string,string>` | Interpolation values for the copy key. See §5.3 |
| `secondaryDetail` | string, nullable | The back-office settlement or voucher reference. Show it **beside** the description, never as it |
| `amount` | `MoneyDto` | Always positive; the sign lives in `direction` |
| `direction` | `"IN"` \| `"OUT"` | |
| `runningBalance` | `MoneyDto` | The back office's own running balance. Never accumulate your own from `amount` — they will diverge |
| `segment` | string, nullable | e.g. `NSE_CASH` |
| `userCaused` | boolean | Whether the trader caused this entry. Show it in the list row, not only in the detail view |
| `reversedBy` | string, nullable | The reference of a later entry that reverses this one |
| `reverses` | string, nullable | The reference of the earlier entry this one reverses |

**An empty period is never a bare empty array.** It comes back with the period echoed and a wider
one suggested, because "no transactions" on its own is indistinguishable from a failure to load.
When `entries` is empty and `suggestedWiderPeriod` is present, offer it as a one-tap action.

**Reversals are entries, not deletions.** An entry is never removed or edited after the fact; a
reversal is added as its own entry, and both remain visible. Use `reversedBy` to strike through or
otherwise mark the original so a reader scanning the list does not count it twice. `userCaused`
exists for the same reason: a deposit the trader made and an automatic return of unused funds are
not the same event, and the difference has to be visible without opening the entry.

**Failures**

| Status | `code` | Cause |
|---|---|---|
| `400` | `invalid_request` | Inverted period, or a window wider than 92 days |
| `503` | `upstream_unavailable` | The back office is unreachable |

### 4.3 `GET /api/v1/funds/transactions/{reference}` — one entry

Takes the same optional `from` and `to` parameters, defaulting the same way, and returns a single
`EntryDto` — the object from the `entries` array above, not wrapped.

The lookup runs across `ALL_ENTRIES` regardless of which view the trader is in, so an entry filtered
out of `MOVEMENTS` is still reachable by its reference.

`404` when there is no such entry for this account in this period. That covers a genuinely unknown
reference and an entry belonging to another trader alike, and also a real entry outside the period
you passed — so if the trader navigated in from a list, pass the same `from` and `to` the list used.
This `404` carries an **empty body** rather than an `ErrorResponse`; check the status before parsing.

`400 invalid_request` when `from` or `to` will not parse as an ISO date, with the offending
parameter in `details.parameter`. The refusal does not echo what you sent.

### 4.4 `GET /api/v1/funds/statement.csv` — export

Takes the same `view`, `from` and `to` parameters, **but `view` defaults to `ALL_ENTRIES`** here
rather than `MOVEMENTS`. Pass the view explicitly from whatever the trader is looking at — an
export returns precisely what is on screen, and defaulting silently to the other view breaks that.

**Response — `200 OK`**

```
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="statement-2026-07-23-to-2026-08-21.csv"
```

The body is streamed. Columns:

```
Date,Description,Type,Reference,Amount,Balance
2026-08-19,Funds added,Credit,SETT-2026-0819-11,50000.00,132000.00
```

| Column | Notes |
|---|---|
| `Date` | ISO `YYYY-MM-DD` |
| `Description` | Resolved English, not a copy key — the server resolves it here because a spreadsheet cannot |
| `Type` | `Debit` or `Credit`, because the file is read against a bank statement |
| `Reference` | Back-office reference, possibly empty |
| `Amount` | Plain decimal, two places, no currency symbol, no thousands separator |
| `Balance` | Same format |

Amounts are deliberately plain so the file is summable in a spreadsheet without cleaning. Line
endings are CRLF per RFC 4180. Fields beginning `=`, `+`, `-` or `@` are prefixed with an apostrophe
so a spreadsheet does not execute them as formulas.

For the download itself: hit the URL with the `Authorization` header from your HTTP client and save
the blob, rather than pointing `window.location` at it — a plain navigation carries no
`Authorization` header.
Read the filename from `Content-Disposition` rather than composing your own.

A `400 invalid_request` here can also mean the export was refused because a field looked like it
might contain an unmasked account number. That check runs before any bytes are written, so you get
a clean status rather than a truncated file. It is rare and it is a backend defect when it happens —
surface it as a failed export, not as a validation message about the trader's input.

---

## 5. Enumerations and keys

### 5.1 `PayoutState`

| State | Terminal | Counts as open | Meaning |
|---|---|---|---|
| `ACCEPTED` | no | yes | Submitted. Reserves nothing |
| `QUEUED_FOR_RUN` | no | yes | A rail outage deferred it; the next run retries. Still cancellable |
| `INSTRUCTED` | no | yes | Instructed to the rail. No longer cancellable |
| `PAID` | yes | no | The full amount was sent |
| `PARTLY_PAID` | yes | no | Less than requested was sent; the gap needs explaining in the UI |
| `NOTHING_SENT` | yes | no | The rail sent nothing |
| `RETURNED` | yes | no | The bank refused the money after the rail sent it |
| `CANCELLED` | yes | no | The trader cancelled before the money left |

`RETURNED` is reachable from `PAID` and `PARTLY_PAID`, so a request the trader already saw as
complete can change once more. Do not treat a terminal state as a reason to stop refreshing.

### 5.2 `EntryKind`

| Kind | In `MOVEMENTS` | Meaning |
|---|---|---|
| `PAYIN` | yes | Money the trader moved in from their own bank |
| `PAYOUT` | yes | Money the trader asked to send to their own bank |
| `MANDATED_RETURN` | yes | Funds returned because the settlement calendar required it, not because anyone asked |
| `SALE_PROCEEDS` | no | Proceeds from a sale |
| `PURCHASE_COST` | no | Cost of a purchase |
| `CHARGES` | no | Brokerage and statutory charges |
| `MARGIN_MOVEMENT` | no | Margin blocked or released |
| `ACCOUNT_ACCRUAL` | no | Interest, penalties and other accruals |
| `OPENING_BALANCE` | no | The period's opening balance |
| `REVERSAL` | no | A reversal of an earlier entry |

### 5.3 Copy keys your client must resolve

Description keys, one per kind:

```
ENTRY_PAYIN                     ENTRY_MARGIN_MOVEMENT
ENTRY_PAYOUT                    ENTRY_ACCOUNT_ACCRUAL
ENTRY_MANDATED_RETURN           ENTRY_OPENING_BALANCE
ENTRY_SALE_PROCEEDS             ENTRY_REVERSAL
ENTRY_PURCHASE_COST             ENTRY_CHARGES
ENTRY_DESCRIPTION_UNAVAILABLE
```

`ENTRY_DESCRIPTION_UNAVAILABLE` is the fallback for an entry the backend's mapping table does not
recognise. It reaches the client with `secondaryDetail` still populated. Give it a real translation —
the server counts these for alerting, but the trader is looking at the row now.

The one other key: `WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT`, on every payout response.

`descriptionParameters` supplies the interpolation values:

| Parameter | Present on | Notes |
|---|---|---|
| `amountPaise` | every entry | A **string** holding the paise integer, not a number |
| `direction` | every entry | `IN` or `OUT` |
| `segment` | when the entry has one | e.g. `NSE_CASH` |
| `settlementDate` | `SALE_PROCEEDS`, `PURCHASE_COST` | ISO date |
| `marketType` | `SALE_PROCEEDS`, `PURCHASE_COST` | |

Every value in that map is a string, including `amountPaise`. Parse before formatting.

---

## 6. Every error code, in one place

| `code` | Status | Endpoints | `details` |
|---|---|---|---|
| `invalid_request` | 400 | all | Field errors as `{field: message}` on a body validation failure; `parameter` (and `permitted`, for an enum) when a query or path parameter will not convert; absent otherwise |
| `request_already_open` | 409 | `POST /payout` | — |
| `withdrawable_unavailable` | 409 | `POST /payout` | `verdict`: `DIVERGENT` or `UNAVAILABLE` |
| `figures_stale` | 409 | `POST /payout` | `computedAt` (timestamp), `computedBy` (string) |
| `not_cancellable` | 409 | `DELETE /payout/{id}` | `reason`: `ALREADY_INSTRUCTED`, `ALREADY_TERMINAL`, `NOT_FOUND` |
| `amount_exceeds_withdrawable` | 422 | `POST /payout` | `requested`, `withdrawable`, both `MoneyDto` |
| `destination_not_verified` | 422 | `POST /payout` | — |
| `upstream_unavailable` | 503 | all reads and `POST /payout` | — |
| `calendar_unavailable` | 503 | `POST /payout` | — |
| `internal_error` | 500 | all | — |
| `not_found` | 404 | any unknown path | — |

Two further codes exist in the backend — `no_verified_source` and `no_route_available`, both `422` —
and belong to the payin flow, which has no endpoint yet. They are listed so you recognise them when
that surface lands; nothing you can call today returns them.

`GET /api/v1/funds/transactions/{reference}` is the one exception to the uniform error body: its
`404` returns an **empty body**, not an `ErrorResponse`. Check the status before parsing.

`withdrawable_unavailable` with `verdict: DIVERGENT` means the account's balance derivation and the
risk system disagree about what may be withdrawn. Neither is picked as the winner: showing the risk
system's figure would show a number the derivation cannot explain, and showing the derivation's
would let a trader request money the risk system will refuse at settlement. So the figure is
unavailable and the action is blocked. Treat it as "temporarily cannot say", not as an error the
trader caused.

A `503` names no vendor, by design. The upstream's identity is logged server-side. Do not try to
infer which system is down from the message.

`internal_error` carries no detail at all. Show a generic failure and let the backend's alerting
handle it. Three cases used to arrive here and no longer do: a second open withdrawal request
(now `409 request_already_open`), an unconvertible query parameter and a non-numeric path id (now
`400 invalid_request`). If you have retry or alerting logic that treats a `500` on those paths as a
service outage, it can be narrowed.

---

## 7. Client, collection, and notes for the build

### 7.1 The TypeScript client

[fms-client.ts](fms-client.ts) is a complete typed client: no dependencies, `fetch` only, no build
step. Every type in §2–§5 is exported, and the four shapes a generated client gets wrong are
handled in it rather than left to each call site.

```ts
import { createFmsClient, paise, rupeesToPaise, formatMoney, FmsError } from './fms-client';

const fms = createFmsClient({
  baseUrl: import.meta.env.VITE_FMS_BASE_URL,
  // The complete header value. The client adds no scheme of its own, so the provisional Basic
  // scheme becoming a bearer token later changes this line and nothing else.
  authorization: () => auth.getAuthorizationHeader(),   // read per request, so a rotation keeps working
});

// 204 comes back as null rather than a parse error.
const open = await fms.getOpenPayout();

// The 30-day default: send both bounds or neither.
const { period, entries, suggestedWiderPeriod } = await fms.listTransactions({ view: 'MOVEMENTS' });

try {
  const created = await fms.requestPayout({
    amount: paise(rupeesToPaise(form.amount)),   // string in, integer paise out, no float
    destinationRef: form.destinationRef,
  });
  showWarning(t(created.shrinkWarningKey));      // before the trader commits
} catch (error) {
  if (error instanceof FmsError) {
    const figures = error.withdrawableFigures;   // populated on amount_exceeds_withdrawable
    if (figures) showError(t('WITHDRAWAL_TOO_LARGE', { max: formatMoney(figures.withdrawable) }));
    else if (error.code === 'request_already_open') refreshOpenRequest();
    else if (error.isRetryable) offerRetry();
  }
}

// The CSV needs the Authorization header, so it is fetched and saved rather than navigated to.
saveStatement(await fms.downloadStatement({ view: currentView, ...period }));
```

What the client does beyond typing the responses:

- `getOpenPayout()` returns `null` on `204`; `getTransaction()` returns `null` on the empty-bodied `404`.
- `rupeesToPaise()` parses a typed amount through strings, never a float, and refuses a third
  decimal place rather than rounding a trader's input.
- `requestPayout()` rejects a non-integer `paise` before the request leaves, so the stack trace
  points at the code that built the amount rather than at the transport.
- `assertValidPeriod()` catches a half-specified, inverted or over-92-day period at the call site
  instead of letting the server's default silently replace it.
- `FmsError` carries `code`, `status`, `details`, plus `cancelReason`, `withdrawableFigures`,
  `fieldErrors` and `isRetryable`. A non-JSON failure from a gateway ahead of this service still
  arrives as an `FmsError` rather than a parse crash.
- `downloadStatement()` reads the filename out of `Content-Disposition`; `saveStatement()` is kept
  separate so the fetch is testable outside a browser.

[fms-client.test.ts](fms-client.test.ts) covers all of the above — 21 checks, no framework, `fetch`
injected:

```
node --test docs/api/fms-client.test.ts
```

Node 22.6 or newer strips the types natively, so that command needs nothing installed. Under
Vitest or Jest the file runs unchanged apart from the import of `node:test`.

### 7.2 The Postman collection

Import [fund-management.postman_collection.json](fund-management.postman_collection.json), then set
three collection variables: `baseUrl`, `username` and `password`. Basic auth is inherited by every
request, so no request carries its own header. The username is the UCC, because it is the
authenticated subject every endpoint resolves the account from.

The collection captures `requestId` from a created payout and `reference` from the first
transaction, so the cancel and detail requests work without editing a variable by hand. Saved
examples cover the failure paths as well as the happy ones — `422 amount_exceeds_withdrawable`,
`409 request_already_open`, the `400` for a decimal `paise`, the empty-bodied `404`, and a `200`
CSV with its headers.

### 7.3 If you would rather generate the client

[openapi.json](openapi.json) is produced from the controllers at build time and asserted by a test,
so it does not drift. `openapi-typescript` and `orval` both consume it directly:

```
npx openapi-typescript docs/api/openapi.json -o src/api/fms-schema.d.ts
```

Generated types will not capture these, so encode them yourself — or read how `fms-client.ts` does
it:

- `remainingToday: null` is unbounded, not zero.
- `suggestedWiderPeriod` is meaningful only when `entries` is empty.
- `descriptionParameters.amountPaise` is a string.
- `GET /payout` returns `204` with no body.
- `GET /transactions/{reference}` returns `404` with no body, unlike every other failure.
- `view` defaults to `MOVEMENTS` on the JSON endpoints and `ALL_ENTRIES` on the CSV export.

### 7.4 What this API does not expose yet

There is no balance endpoint, no withdrawable figure on its own, and no payin initiation. The
withdrawable figure is visible only as `withdrawableAtRequest` on a payout response, and the limits
endpoint is the only payin surface. Plan the screens around that, and raise it if you need a figure
that has no endpoint rather than deriving one from transactions.

### 7.5 The interactive Swagger UI is not served

`/swagger-ui.html` returns 404 on every environment — a second surface to secure on a service that
moves money, for no operational benefit. The spec file in this directory is the artifact to work
from. Use the Postman collection if you want something to click.
