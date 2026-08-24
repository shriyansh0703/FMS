# FMS prototype

A working prototype of the funds page specified in [`../product-requirements.md`](../02-requirements/product-requirements.md).
No build step, no dependencies, no network calls.

## Run

```bash
cd web
python3 -m http.server 5173 --bind 127.0.0.1
```

Then open <http://127.0.0.1:5173>.

Two pages, cross-linked from the black demo strip:

| Page | What it is |
|---|---|
| `index.html` | The **funds page** — what the customer sees |
| `dashboard.html` | The **money-movement dashboard** — what the product team sees |

## Test

```bash
./test.sh            # the funds page
./dashboard-test.sh  # the dashboard
```

`test.sh` boots `app.js` headlessly against a minimal DOM shim and asserts the money
arithmetic against the rules the PRD states — including the worked example from the
knowledge base (₹1,00,000 ledger / ₹1,50,000 available margin / ₹25,000 withdrawable).

`dashboard-test.sh` requires `dashboard.js` straight into node — it touches no DOM until
`boot()` — and asserts the identities that must hold whatever the data says: a funnel may
never grow, a breakdown accounts for its whole, percentiles are ordered, an unmeasured
metric is `null` rather than zero, and the page never reads a clock or a random number.

## What it demonstrates

Switch between the five demo accounts in the top strip. Each is a state the PRD requires
a designed answer for.

| Demo account | Shows |
|---|---|
| **Active trader** | The three balances; the withdrawable derivation; margin decomposition; committed money by source × commitment state; per-trade-kind deployability |
| **Margin shortfall** | Shortfall with a deadline, funding as the primary action, withdrawable forced to zero |
| **New & empty** | One statement of the state, what the account will do once funded, the smallest useful amount, one action |
| **Cannot receive money** | The blocker replaces the deposit form rather than sitting next to it disabled |
| **In debt (dormant)** | A negative balance presented as a debt, its cause, its accrual rate, and an exact-amount payment below the deposit minimum |

Things worth trying:

- **Add ₹5,000 as the Active trader.** Ledger and available margin rise; **withdrawable does not move**. That gap is the point of the whole PRD.
- **Click "Why this number?"** — the full derivation, every term explained, reconciling to the figure.
- **Click the "Updated 2 min ago" stamp** — simulates stale margin data. Both money paths refuse to act and say why (REQ-107).
- **Withdraw as the Active trader** — the request is accepted and **the withdrawable figure does not move**. Nothing is held; the money stays tradable all session and whatever is left at the end-of-day run is what gets sent (Rule W3), which the screen says before you commit (Rule W3a).
- **In debt → All entries → All time** — the mandated sweep is tagged "you did not request this", and the reversed charge is struck through and paired with its reversal.
- Deposits fail about one time in five, on purpose, so the failure path is visible: a reason, and a different route suggested (REQ-205).

## The dashboard — `dashboard.html`

A **product-facing quality and funnel view over payins and payouts**, built against the
Success Metrics and Tracking Requirements in [`../product-requirements.md`](../02-requirements/product-requirements.md).
Four tabs: **Funnel**, **Reliability**, **Speed**, **Adoption**.

Money movements only. Trading obligations and charges post or they do not; they have no
lifecycle to watch and no funnel to draw, so they are not on the page.

The population is synthetic and deterministic — one seed, no `Math.random`, no wall clock —
so every figure is reproducible and can be asserted against.

Six headline tiles: two of volume, four of verdict. **Every tile shows both a rupee
amount and a percentage**, because neither answers a question on its own — 95.4% is not
worth knowing without the ₹3.31 Cr behind it, and ₹4.15 Cr is not worth knowing without
the share of movement it represents. Amounts pick their own unit (₹850 → ₹8.54 K →
₹86.8 L → ₹4.65 Cr), so nothing renders as `₹0.00 L` or as eight digits of commas.

Period: **Today · 7 days · 30 days · 90 days · Custom**, the last opening a date range
clamped to the 90 days of movements the prototype holds. One `window_()` resolves a period
into a `{from, to}`, so no two cards can disagree about which days they describe.

Things worth trying:

- **Switch to Today.** Half a day of movements, and the page says so rather than papering
  over it: “Payouts by the quoted time” reads **—  not measurable** against 0 arrived
  payouts, not 0%. An unmeasured metric and a failing metric must never look the same.
- **The first-try tile is split by payment method** — UPI and net banking, each with its
  rate, the rupees behind it and its sample size. The split is on the method the user
  *started* on, so a retry that switched rails stays with the method it began as; that makes
  the parts partition the headline exactly and add back up to it, which the test suite
  asserts. Reliability breaks the same split out in full, with the all-attempts rate beside
  each first-try rate.
- **Click the ⓘ on any headline tile.** Four words and a percentage cannot say what was
  counted, so every tile carries its full definition: what it counts, what it is out of,
  what it deliberately leaves out, and where its threshold comes from. “1,869 payin intents”
  and “622 accounts” are denominators with rules behind them, and the rules are on the page
  rather than in someone's head.
- **Reliability → “One KPI, three readings.”** The PRD says *95% of deposit attempts succeed
  on the first try* and never defines an attempt. The three defensible readings of that
  sentence land on either side of the threshold: the release passes under one and fails
  under another. Flip **“Count *user backed out* as a failure”** and the verdict moves again.
  Nothing in the PRD picks one, and this is what that costs.
- **“Assume no third-party analytics”** in the demo strip. The PRD carries an open question
  about whether balances may be sent to CleverTap at all. Turning this on shows what survives
  if the answer is no: the funnel loses its top three steps, the zero-state metric goes dark,
  and every conversion rate loses its denominator. Every launch threshold on the headline
  strip stays, because all four are answerable from FMS's own write path.
- **Funnel → “The route FMS cannot measure.”** NEFT is `selfService` in the funds flow, so
  FMS never sees an attempt — only, sometimes, a credit. It cannot tell a user who changed
  their mind from one whose transfer went astray. Those handoffs are excluded from every rate
  rather than guessed at, and counted where they can be seen.
- **Speed → “What we quoted against what happened.”** REQ-303 computes an arrival time and
  the quote is retained so it can be held against reality. The bar is **100%**, set on
  19 Aug 26 to close a gap the PRD left open — it names quoted-versus-actual as tracked from
  day one and never states a level. At 100% this is an *invariant rather than a trend*, in
  the same family as the PRD's target-zero correctness metrics: it reads *below* the moment
  one payout is late, and the answer is a faster rail or a more honest quote, never a lower
  bar.
- **Switch the period to 7 days.** Sample sizes are printed on every metric, because a rate
  over 25 failures and a rate over 286 are not the same claim.

### The one thing that matters in the code

`metrics()` in `dashboard.js` is the **only** place any figure is computed. The view layer
renders; it never calculates. That is the same rule `derive()` enforces on the funds page,
applied to a different kind of number — and it means a counting rule can only be defined
once. Where the PRD leaves a rule open, it is a **control on the page** rather than an
assumption buried in the code.

The routes, reason codes and payout outcomes are not invented for reporting. They are the
catalogues `app.js` already emits — `ROUTES`, `PAYIN_OUTCOMES`, `EOD_OUTCOMES` — so the
dashboard can only report failures the product is actually able to produce.

## Files

| File | Purpose |
|---|---|
| `index.html` | Funds page — structure only |
| `app.js` | Scenarios, the single `derive()` definition, rendering, interactions |
| `dashboard.html` | Dashboard — structure only |
| `dashboard.js` | Synthetic population, the single `metrics()` definition, tabs |
| `dashboard.css` | Dashboard components, on top of the shared visual system |
| `styles.css` | Visual system, theme-aware, shared by both pages |
| `test.sh` · `dashboard-test.sh` | Headless self-tests |

## The one thing that matters in the code

`derive()` in `app.js` is the **only** place any balance is computed — Rule B12. Every
surface reads from it; nothing computes its own figure. The ledger balance is summed from
entries rather than stored (Rule L1), and all money is integer paise.
