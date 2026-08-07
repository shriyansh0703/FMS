---
name: "security-review"
description: "Conduct a dedicated security review of implemented, code-reviewed and QA-passed source code as the final gate before release: server-side authorization (object-level, property-level, function-level), input validation at trust boundaries, secrets/PII exposure in logs and responses, dependency risk, Rust unsafe/concurrency/FFI/cancellation safety, and a systematic OWASP API Security Top 10 walk of every REST endpoint against its OpenAPI contract. Produces a severity-graded findings report with a machine-readable verdict. Use when the user asks for a security review, security audit of code, vulnerability review, OWASP review, or when the prd-to-prod pipeline reaches its Security Review stage. Do not use for writing code, generating fixes beyond remediation guidance, penetration testing a live system, general code review (style/bugs), or infrastructure/deployment security audits."
---

# Security Review

You are performing a dedicated security review of implemented source code. This is a **review stage**: you read code and contracts, you find and grade vulnerabilities, and you write a report with a verdict. You do not rewrite the implementation — remediation guidance in a finding is as far as you go. Fixes happen back in the implementation stage after this review's verdict routes there.

In this repository's `prd-to-prod` pipeline, this skill is **Stage 11 — Security Review**, the pipeline's **final gate**. It runs after Stage 9 (Code & Architecture Review) and Stage 10 (QA Testing & Browser Validation) are both APPROVED, and nothing runs after it. Running last is deliberate: the code is reviewed here in the exact state QA exercised, including every fix QA forced, so no later change can invalidate the review.

Stage 9 judges correctness, completeness, and architecture conformance; this stage judges only whether the code is safe to expose. Do not duplicate Stage 9's work — a style violation or a missed LLD field is out of scope here unless it is also a security defect.

When a CRITICAL or HIGH finding routes work back to Stage 8 (Implementation), the resulting fix invalidates QA too: Stage 10 must re-run and be re-approved before this stage is re-entered. A security fix that never went back through the tests is exactly the change most likely to break behavior silently.

## Inputs

Read, in this order, before writing a single finding:

1. **The implemented source code** — every file Stage 8 produced or modified. The file list comes from `tasks.json` file targets plus the Stage 9 review's coverage; if neither is available, review every source file changed for the feature.
2. **`.ai/artifacts/review.md`** — the Stage 9 review. Its verdict must be APPROVED or APPROVED_WITH_CONDITIONS before this stage runs.
3. **`.ai/artifacts/test-report.md` and `browser-report.md`** — the Stage 10 QA results, which must be approved before this stage runs. Read them for two things: any code changed in response to a test failure (that code is reviewed here in its final state, not its pre-QA state), and any behavior QA could not verify, since an unverified path is where a security assumption most often goes unchallenged.
4. **The API contract** — the OpenAPI/Swagger spec if one exists (`docs/api/`, generated spec location, or the LLD's API section). The spec-versus-implementation cross-check below depends on it.
5. **The LLD** (`lld.md`, `lld-backend.md`, `lld-frontend.md` — whichever exist per scope) — for what authorization, validation, and data-exposure behavior was *designed*, so drift between designed and implemented security behavior is itself reportable.

If a required input is missing (no source code, no Stage 9 review, no QA reports), HALT and report exactly what is missing. Never review a partial picture silently.

## Review procedure

Work through both reference files in full — they are the review body, not optional background:

1. **`references/security-review-practices.md`** — the engineering-practices review: language-specific focus (Rust `unsafe`/concurrency/FFI/cancellation safety where Rust is in scope), general application security (boundary validation, server-side authorization, secrets in logs/errors, dependency awareness), API/Swagger surface security, the differential review mindset for changed code, and footgun awareness. Its end-of-file checklist must be answered item by item.
2. **`references/api-security-owasp-top10.md`** — the systematic OWASP API Security Top 10 pass: walk the API contract endpoint by endpoint (including the "boring" CRUD endpoints), trace object-level authorization for every ID-taking endpoint, check mass assignment on every write endpoint, function-level authorization on every privileged operation, resource-consumption bounds on every public endpoint, and validation of third-party/upstream API data. Its checklist must also be answered item by item.

For every endpoint, trace the actual code path — do not accept the presence of an auth middleware as proof of object-level authorization. A finding you cannot anchor to a specific `file:line` (or a specific spec path) is not a finding; go confirm it or drop it.

## Severity grading

Grade every finding:

| Severity | Meaning |
|---|---|
| **CRITICAL** | Exploitable now with direct impact on funds, orders, positions, or another user's data (e.g. BOLA on an order endpoint, auth bypass, secrets in a client-visible response) |
| **HIGH** | Exploitable with modest effort, or a missing control the platform's threat model requires (e.g. no rate limit on an expensive public endpoint, mass assignment on a writable model, spec declares auth the handler doesn't enforce) |
| **MEDIUM** | A real weakness needing specific circumstances to exploit, or a security-relevant drift from the LLD's designed behavior |
| **LOW** | Hardening opportunity or footgun worth naming; no concrete exploit path identified |

Any CRITICAL or HIGH finding forces `CHANGES_REQUESTED`. MEDIUM findings force at most `APPROVED_WITH_CONDITIONS`, with each condition stated. LOW findings alone permit `APPROVED`.

## Output — `.ai/artifacts/security-review.md`

Write the report to `.ai/artifacts/security-review.md`. It is a full-prose engineering document handed to people who were not in this conversation — never compress it. Required structure:

```markdown
# Security Review — [feature name]

## Scope of Review
[What code, endpoints, and contracts were reviewed; the scope declared in the PRD
(backend | frontend | fullstack); what was explicitly out of scope and why.]

## Findings
[One subsection per finding, ordered CRITICAL → LOW. Each finding states:
severity, title, file:line (or spec path), the vulnerable behavior, a concrete
exploit scenario, and specific remediation guidance. If there are no findings at
a severity, say so explicitly — an empty section is evidence the pass ran.]

## Checklist Results
[Both reference checklists reproduced item by item with pass / fail / not
applicable and a one-line justification each. "Not applicable" requires a reason
(e.g. no Rust in scope), never a shrug.]

## Endpoint Authorization Matrix
[A table: every reviewed endpoint × authentication check, object-level
authorization, function-level authorization, input bounds — with file:line
evidence per cell. This is the systematic OWASP walk made visible.]

**Verdict:** APPROVED | APPROVED_WITH_CONDITIONS | CHANGES_REQUESTED
```

The `**Verdict:**` line is machine-parsed by the workflow's hooks — emit exactly one of the three canonical values. `CHANGES_REQUESTED` hard-blocks the QA stage's artifacts until implementation fixes land and this review is re-run.

## Pipeline gate

After writing the report, present it to the user and call `AskUserQuestion` with APPROVE / ITERATE / REJECT / JUMP per the workflow's Approval Protocol. If the verdict is `CHANGES_REQUESTED`, the expected route is back to Stage 8 (Implementation) — never forward to QA with known CRITICAL or HIGH findings.

### Named approval when findings are accepted

Approving a security review that contains **any open finding** (any severity, including an `APPROVED_WITH_CONDITIONS` verdict, or a user explicitly overriding a `CHANGES_REQUESTED` one) is an accountability decision, not a click. When the user chooses APPROVE and the report has one or more findings:

1. Ask a **second** `AskUserQuestion` collecting the approver's identity: "You are approving a security review with open findings. Whose name should the approval record carry?" Offer the git-configured user name (`git config user.name`) as the recommended option and let "Other" capture any other name. Never fill the name in yourself and never default silently to the git name without the user picking it — the point is a deliberate, named acceptance.
2. Append an **Approval Record** section to `.ai/artifacts/security-review.md` (append — never rewrite the findings above it):

```markdown
## Approval Record

- **Approved by:** [name the user gave]
- **Date:** [YYYY-MM-DD]
- **Verdict at approval:** [verdict as written above]
- **Findings accepted:** [each open finding's severity + title, one per line — or "none open"]
- **Basis:** [the user's stated reason, if they gave one; otherwise "accepted via approval gate"]
```

3. Add one appended note-row to `.ai/artifacts/traceability.md` (appended, never regenerated, consistent with that file's rules) recording the security approval: the approver's name, the date, and the count of accepted findings by severity — so the acceptance is visible in the same document the pipeline's coverage gate already audits.

A clean report (zero findings, verdict `APPROVED`) needs no name prompt — the ordinary APPROVE click suffices, and the Approval Record section is omitted.

## What this skill never does

- Never probes, scans, or exploits a running system — this is static, code-and-contract review only; live verification belongs to QA/pentest.
- Never softens a severity to avoid blocking the pipeline, and never omits a finding because it is inconvenient.
- Never fabricates a `file:line` anchor — an unverified suspicion is stated as such under the finding, not dressed up as evidence.
- Never rewrites source code. Remediation guidance names the fix; Stage 8 makes it.
