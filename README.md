# FMS — Development Handover

Fund Management System. Eight requirement files, 60 owned requirements registered as 72,
a complete instrumentation spec and a working prototype.

**Phase 1 has no open blocker. Start there.**

Packaged 20 Aug 2026. Standalone: no git remote, no build step, no dependencies.

---

## Read in this order

| Folder | What |
|---|---|
| **01-start-here** | `HANDOVER.md` — what is ready, what is gated, who owns each gate. Open `handover.html` in a browser for the same thing laid out by phase |
| **02-requirements** | `product-requirements.md` is the spine: the register of all 72 requirements and the file that owns each. The seven feature files sit beside it — read the one that owns what you are building |
| **03-instrumentation** | What FMS sends to CleverTap and the funnels those events answer. **Not optional** — see the warning below |
| **04-prototype** | Working reference. `derive()` in `app.js` is the single definition of every balance; `metrics()` in `dashboard.js` the single definition of every dashboard figure |
| **05-dependencies** | Documents and interfaces FMS is subordinate to. `thinq/` holds the internal specs it registers against; `vendor-api/` holds the external API references it calls |

## One trap

`02-requirements/product-requirements.md` once carried a Tracking Requirements table listing
twenty-one event names. **It was replaced on 19 Aug 2026 with a pointer to
03-instrumentation.** Eight of those rows sent account balances as event properties, which
the same document's own privacy requirement forbids. If you are reading a copy that still
lists those events, it is stale — build from 03-instrumentation.

## Run the prototype

```bash
cd 04-prototype
python3 -m http.server 5173 --bind 127.0.0.1
```

`index.html` is the funds page, `dashboard.html` the money-movement dashboard.
`./test.sh` asserts the arithmetic against the PRD; `./dashboard-test.sh` asserts the
funnel invariants. No build step, no dependencies, no network calls.

## Four gates, four owners

| Gate | Phase | Owner |
|---|---|---|
| Withdrawal authentication — no out-of-band control today | 3 | Product owner with **authentication** |
| Trading and settlement calendar | 3 | Product owner with **compliance** |
| Comms orchestration — sits in front of SMS template registration, the slowest item in the release | 2 | Product owner with **engineering** |
| Debit interest rate | 4 | **Finance** with TechExcel |

Full routing, and what the dev team owes that is in no PRD, in `01-start-here/HANDOVER.md`.

## External APIs

`05-dependencies/vendor-api/` carries one spreadsheet per upstream provider, each with the
same three sheets — the call list, its input parameters, its output parameters.

| File | Covers |
|---|---|
| `juspay_api_reference.xlsx` | Juspay payment gateway. 113 REST calls |
| `kambala_noren_api_reference.xlsx` | Kambala Noren CAPI, the OMS. 52 calls; 16 fully specified, the rest name-only |

The Noren file is a C++ SDK, not REST, so its Method and Endpoint columns carry the call
type and the broker lane instead. Four response structs — `tsPayinStatusRespParams`,
`tsPayoutStatusRespParams`, `tsFundsUpdatesParams` and `tsFundsReportParams` — are named by
the vendor PDFs but never defined in them. That is the whole read side of money movement.
Closing it needs `include/noren_cpp_data_structs.h` from the CAPI package.

## What is not in this package

Screenshots, backups and the audit working files. The two published artifacts —
the event spec and the audit — are linked from `HANDOVER.md` and are private to the
Thinq account.
