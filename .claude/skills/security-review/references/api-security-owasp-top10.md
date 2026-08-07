# API Security — OWASP API Top 10

`secrets-and-exposure-scanning.md` covers general endpoint exposure (auth presence, error leakage, CORS). This file goes deeper on API-specific vulnerability classes systematically, using the OWASP API Security Top 10 as the checklist — the standard reference for what actually goes wrong in real-world APIs, distinct from the general OWASP (web) Top 10.

**This is a defensive code-review checklist, not a testing tool that probes a live system.** If you need to actively verify a running service's authorization behavior, that's a QA/pentest-stage activity outside this skill's scope (Pipeline Position) — this file governs what to check for *while writing the code*, before it's ever exposed to be probed.

## The categories that matter most for this platform's API surface

- **Broken Object Level Authorization (BOLA/IDOR)** — the single most common and highest-impact API vulnerability class in practice: an endpoint like `GET /orders/{id}` that checks the caller is authenticated, but not that the caller actually owns order `{id}`. Every endpoint taking an object identifier from the client must verify the authenticated caller has a right to that specific object, not just that they're logged in. This is checkable systematically: for every endpoint with a path/query parameter identifying a resource, confirm an authorization check against the authenticated user's actual relationship to that resource exists in the code, not just an authentication check.
- **Broken Authentication** — weak token generation, missing token expiry, accepting tokens without proper signature verification.
- **Broken Object Property Level Authorization** — a subtler variant of BOLA: an endpoint correctly checks the caller owns the resource, but returns or accepts fields the caller shouldn't see/set (e.g. an account-update endpoint that lets a regular user set their own `is_admin` field because the API accepts any field in the request body). Check both read (over-exposure) and write (mass-assignment) directions.
- **Unrestricted Resource Consumption** — no limit on pagination size, no request body size cap, no rate limit — an attacker (or a misbehaving client) can degrade or exhaust the service. Connects to `scalability-and-caching.md`'s pagination/batching rule and `rust-backend.md`'s rate-limiting baseline, checked here specifically at the API-contract level.
- **Broken Function Level Authorization** — an admin-only operation reachable by a regular authenticated user because the endpoint doesn't re-check role/permission, only authentication. Distinct from BOLA (object-level) — this is about the *operation* being restricted, not the *object*.
- **Unsafe Consumption of APIs** — this service trusting data from a third-party/upstream API without the same validation it would apply to direct user input — a common gap since "it came from our own backend service" gets implicitly trusted more than it should be, especially for a service consuming a partner/exchange API.

## How to check systematically, not just spot-check

- **Walk the API contract (`api-contract-design.md`'s OpenAPI spec) endpoint by endpoint**, not just the ones that feel risky — BOLA in particular tends to hide in "boring" CRUD endpoints that got less security attention than the obviously sensitive ones (an order-placement endpoint gets scrutiny; a "list my saved watchlists" endpoint often doesn't, and is just as capable of leaking another user's data if the object-ownership check is missing).
- **For every endpoint accepting an object ID**: trace the code path and confirm there's an explicit check that the authenticated caller is authorized for that specific object — not merely that a valid session exists. If no such check is visible in the code, that's a finding, regardless of whether it's ever been actively exploited.
- **For every endpoint accepting a request body**: what fields does it actually process, versus what fields exist on the underlying model? A mismatch (accepting more fields than intended) is the mass-assignment risk above.

## Review checklist before calling API-security-Top-10 review "done"
- [ ] Every endpoint accepting an object ID was checked for object-level authorization (not just authentication) — including "boring" CRUD endpoints, not just obviously sensitive ones
- [ ] Every write endpoint was checked for mass-assignment risk (accepting fields the caller shouldn't be able to set)
- [ ] Admin/privileged operations were checked for function-level authorization, not just authentication
- [ ] Pagination limits, request body size caps, and rate limits are present on every public-facing endpoint
- [ ] Data consumed from any third-party/upstream API is validated the same way direct user input would be, not implicitly trusted
