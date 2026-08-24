# Browser Validation Report — Fund Management System

Stage 10, browser half, fourth pass. The backend test results are a separate document,
[`test-report.md`](test-report.md). This one is kept separate precisely so its conclusion cannot be
read as a footnote to a passing suite.

**Browser validation status: NOT PERFORMED — there is nothing to validate.**

The backend suite now stands at 740 passing tests and 94.5% instruction coverage. That figure has no
bearing on this document, and the gap between the two is the reason this document exists.

## 1. Detection result — what was looked for, and what was found

Detection ran again across the whole repository, on 24 Aug 2026, for any browser-testable surface or
any browser automation already configured. It ran twice: once during the main pass, and once more
after the backend source tree was modified by another process mid-session (see `test-report.md`
§4, QA-03). Both runs returned the same result, and the second confirmed the backend suite still
passes at 740 tests against the changed tree.

| Looked for | Found |
|---|---|
| A frontend application directory | none — no `frontend/`, `web/` or `ui/` at any level |
| An application `package.json` | none. The only two are `hooks/package.json` and `.ai/dashboard/package.json`, both pipeline tooling, neither a product surface |
| `playwright.config.*` | absent |
| `cypress.config.*` | absent |
| `selenium/` or `tests/e2e/` or `e2e/` | absent |
| Any rendered page, route or component | none |

The React 18 and TypeScript client specified in `lld-frontend.md` was never built. This is not a case
of an application existing without tests, which would be a coverage gap. There is no application. No
browser was launched, no page was loaded, and no screenshot, trace or video exists, because there is
no URL to point one at.

## 2. Why this is reported as its own document

A combined Stage 10 report would have opened with 740 passing backend tests and reached this section
several pages later, by which point the reader has already formed the impression that the system was
validated. The two halves have different failure modes and different readers. A backend engineer
needs §3 of the test report; whoever owns the product's delivery date needs this page, and needs it
not to be buried.

## 3. Unvalidated scenarios — now enumerated rather than described

This pass produced [`docs/qa/test-cases.md`](../../docs/qa/test-cases.md), a 613-case catalogue
derived from the PRD, HLD and LLD. That makes the browser gap countable for the first time.
**Thirty-one cases are blocked specifically on the absent client**, distributed like this:

| Section | Blocked on the client | What they describe |
|---|---:|---|
| Balances & margin | 8 | The three figures presented separately, the derivation panel, the largest deduction named without opening it, the stale-figures treatment, collateral shown apart from cash |
| Adding funds | 6 | The amount field's keystroke filtering (Rule A13), suggestion pills stating set-or-add, the minimum stated before entry, the support route after three failures, the post-funding destination and its plain-dismissal fallback |
| Withdrawing funds | 4 | The always-visible entry point disabled with a reason, a disabled control that does not silently absorb an interaction, the Rule W3a shrink warning shown before commitment |
| Transactions & statements | 6 | The statement's own header content and filename, the period surviving a view switch, the financial-year preset, a status changing while displayed |
| Account health | 5 | The debt treatment distinct from a positive balance, the empty-account state, the blocker replacing the funding path rather than disabling it |
| Communications | 2 | The preference surface stating which messages cannot be turned off, the non-dismissible banner for an SMS-only account |

Every one of these is a requirement whose acceptance criterion is something a person sees or does.
None is partially satisfied by a backend result.

**The distinction that matters:** these are *unverifiable in the current state of the repository*
rather than merely untested. A test cannot be written for them, so they will not appear as a gap in
any coverage report, any mutation score, or any CI signal. They are invisible to every automated
measure this project uses, which is exactly why they are counted here by hand.

## 4. Three rules that are structurally unenforceable without a client

Beyond the case count, three PRD rules govern behaviour that has no backend expression at all, and no
amount of backend work moves them:

**Rule A13 — a money field never acts on a value it did not display.** The rule exists because an
earlier parser stripped a leading minus and `-500` silently became ₹500. That defect lives at the
keystroke, in a field that does not exist. The backend refuses a malformed amount at the edge
(`HostileBodyApiTest`, 18 cases), which is a different control: it stops a bad value being
*processed*, not a bad value being *shown to the trader as though they had typed it*.

**Rule W2 — a control that cannot act never looks like one that can.** Wholly presentational. The
backend supplies the availability and the reason; nothing renders either.

**Rule H6 — a blocked account shows the blocker, not a blocked path.** The rule was written against
an observed product that presented a live amount entry leading to a permanently disabled button. The
backend returns the ordered blocker list; the choice between replacing the path and disabling it is
made in a component that was designed and never written.

## 5. Accessibility — not assessed, and not assessable

The PRD commits the funds view to WCAG 2.1 Level AA, on the stated basis that one benchmarked
competitor was found with none of its eight money actions reachable by keyboard and 130 contrast
failures in a single view. No assessment was performed and none is possible: there is no markup to
inspect, no focus order to traverse, and no rendered colour to measure.

This is worth recording separately from §3 because accessibility is the requirement most often
discovered late, and the commitment here is not aspirational — it is a stated obligation with a
named competitive failure behind it.

## 6. What would change this verdict

Not a test framework decision. The blocker is upstream of that: **there is no application**. Once one
exists, Playwright is the reasonable choice for this stack, and the 31 cases in §3 are already
written in a form that transcribes into specs — each states its precondition, its action and its
expected result, in the words of the requirement it comes from.

Until then, the correct reading of this pass is that the backend is well covered and the product is
unvalidated, and that those two statements are about different things.
