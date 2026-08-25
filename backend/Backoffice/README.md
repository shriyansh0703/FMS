# FMS Back Office Gateway

TechExcel's own request and response shapes, served either from a built-in mock or proxied to the
real back office. Java 25, Spring Boot 4.1.0, Gradle.

## Scope

**Only the endpoints the FMS API → Category → Service mapping assigns to the Fund Management
Service.** Four of them, plus the `/api/login` all four need.

| Endpoint | Category → package | Vendor document |
|---|---|---|
| `POST /api/entry/virtual_debit_report` | Trading / Funds → `funds` | Virtual debit report.pdf |
| `POST /api/entry/brk_remeshire_view` | Brokerage → `brokerage` | Brk Remeshire View.pdf |
| `POST /api/entry/new_interest_process` | Brokerage / Charges → `brokerage` | New Interest Process.pdf |
| `POST /api/entry/ledger` | Accounting → `accounting` | Ledger.pdf |

All four are **reads**. Nothing in this service writes to the back office.

### Deliberately not here

These four appear in the same vendor documents and the same mapping screenshot, but the mapping
assigns them to the **Order Management Service**. They return `404`, and adding them would mean
two services answering the same write:

| Endpoint | Owning service |
|---|---|
| `new_segment_enable` | Order Management |
| `client_active_inactive_status_update` | Order Management |
| `add_brokerage` | Order Management |
| `portfolio_insert` | Order Management |

Every path is also served under TechExcel's own `/TechBoRest` prefix, so a caller can point at this
service by changing a host and nothing else.

## Layout

```
src/main/java/com/thinq/backoffice/
  platform/    the vendor's envelope, its validation vocabulary, the login, and the ONE place a
               call leaves this process (VendorGateway)
  scheduler/   TokenRefresher — keeps the upstream token alive ahead of its 24h expiry
  ratelimit/   RateLimiter + the interceptor that applies it
  funds/       virtual_debit_report
  brokerage/   brk_remeshire_view, new_interest_process
  accounting/  ledger
```

One package per category from the mapping, so "where does `ledger` live" has one answer.
The two cross-cutting mechanisms sit apart from all of them: neither belongs to a category, and
both are configuration-driven.

## Running

```sh
./gradlew bootRun            # mock mode — nothing leaves this host
./gradlew test               # the checks
```

The banner on startup says which mode the process is in, what the rate limit is, and — in managed
auth mode — that it is holding a credential.

```sh
# log in, then call an endpoint
TOKEN=$(curl -s localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"name":"api","password":"Api@123456"}' | tr -d '"')

curl -s localhost:8080/api/entry/ledger \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"Client_code":"M000","FromDate":"01/04/2022","ToDate":"31/03/2023",
       "COCDLIST":"BSE_CASH,NSE_CASH","ShowAllData":"Y","ShowMargin":"Y","Merge_Company":"Y"}'
```

## Switching to the real TechExcel

Two properties in `properties/techexcel.properties`, which sits outside the jar:

```properties
techexcel.live=true
techexcel.base-url=http://<host>:<port>/TechBoRest
```

No code change, no rebuild. Callers do not change either: same paths, same request bodies, same
bearer flow, same response envelope. Live mode is a straight pass-through — the request goes
upstream verbatim and the answer comes back verbatim, including anything TechExcel serves that this
service holds no document for.

Startup **fails** if `live=true` and `base-url` is empty. A gateway that boots and then answers
nothing is harder to diagnose than one that refuses to boot and says why.

## The token, and the 24 hours

The Login document is explicit: a token is valid **24 hours from the moment it is generated**, and
every other document carries a `Token Expired` / "Token Invalid After 24 Hours" row.

Two independent things handle that.

**In mock mode**, `platform/TokenStore` issues a token, expires it at 24 hours, and drops it — so a
caller meets the expiry here rather than in production. Nothing needs a real credential.

**In live mode**, `techexcel.auth.mode` decides:

- `passthrough` (default) — this service holds no credential. The caller logs in for itself and
  TechExcel's token comes straight back. Nothing to leak, nothing to rotate.
- `managed` — `scheduler/TokenRefresher` logs in as a service account, holds the token in memory,
  and **replaces it before it expires**. Requests arriving with no `Authorization` header get that
  token attached; a caller's own token always wins.

```properties
techexcel.auth.mode=managed
techexcel.auth.refresh-cron=0 0 * * * *   # how often to LOOK (six-field Spring cron)
techexcel.auth.refresh-after=20h          # how old is too old — must be under 24h
```

The cron decides how often to look; the **age of the held token** decides whether to act. A missed
tick, a clock change, a restart or a suspended process then costs nothing — the next tick sees the
age and refreshes. A single timer armed for "expiry minus an hour" has none of those recoveries.

Credentials come from the environment (`TECHEXCEL_AUTH_USERNAME`, `TECHEXCEL_AUTH_PASSWORD`), never
from the committed properties file. Managed mode without them fails at startup and says so.

## The rate limit

A token bucket per (caller, endpoint). "Caller" is the bearer token — the only identity this API
has — falling back to the remote address where there is no token, which is what makes a login
brute-force cost something. The token itself is never stored: only a SHA-256 prefix is used as a
map key.

```properties
backoffice.ratelimit.enabled=true
backoffice.ratelimit.defaults.requests=60
backoffice.ratelimit.defaults.window=1m
backoffice.ratelimit.per-endpoint.ledger.requests=10
backoffice.ratelimit.per-endpoint.ledger.window=1m
```

**Every number is a guess and is meant to be changed.** Nobody has measured what a real consumer
does and TechExcel documents no quota of its own, so they are configuration precisely so the first
person who needs a different number does not need a release.

A refusal is **429** with `Retry-After`, `X-RateLimit-Limit` and `X-RateLimit-Window` — not the
vendor's 200 envelope, because it is this service's verdict about its own capacity and not
something the back office said.

Buckets are in memory and per replica: two instances permit twice the configured rate in total, and
a restart forgives everyone. That is fine for protecting a vendor back office from one runaway
consumer and is **not** a security control. The upgrade path is a shared counter in Redis keyed the
same way; nothing above `RateLimiter` would change.

## Status codes

Everything is **HTTP 200**, rejections included — that is TechExcel's contract, and the verdict is
in `Success` and `Error Code`. A caller that branches on the status line would read every rejection
as a success.

The three exceptions are this service's own verdicts, never the vendor's:

| Status | Meaning |
|---|---|
| 404 | mock mode, route not documented here |
| 429 | this service's rate limit — the back office was not called |
| 502 | live mode, upstream unreachable |

## Known gap

`ledger` returns its rows as an **array**. The vendor's document samples a single object, but a
ledger is by definition many entries and a caller written against an object breaks on the first
client with two transactions. Confirm the real shape before going live — it is the one place this
mock chooses between two readings of a document.
