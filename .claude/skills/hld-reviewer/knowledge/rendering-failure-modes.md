# Rendering Failure Modes (SSR / Streaming SSR / React Server Components)

Consulted from Lens 8 (Performance & Scalability) and Lens 6 (Security) whenever the system under review uses server-side rendering in any form. These
failure modes are specific to modern hybrid/server-rendered frontend architectures (Next.js, Remix,
and similar) and are commonly invisible to a review that only thinks about performance in terms of
Core Web Vitals.

## Hydration mismatches
The server renders markup, the client re-renders and "hydrates" it — if the two don't match, React
either silently patches (masking a real bug) or throws a mismatch error. Common causes worth checking
for in the document: locale/timezone-dependent formatting rendered without a stated consistent source
of "now"; random values (IDs, ordering) generated during render; viewport/device checks (`window.
innerWidth`) used to decide what renders. A document describing dynamic, locale- or device-dependent
content with no mention of hydration risk is a gap.

## React Server Components (RSC) risks
- **Server/client boundary discipline** — is it clear which components are Server Components (no
  hooks, no browser APIs, can access backend resources directly) vs. Client Components (`"use
  client"`)? A document that doesn't address this boundary at all for an RSC-based architecture is
  thin.
- **Serialization boundary** — props passed from Server to Client Components must be serializable;
  does the document show awareness of this constraint anywhere it matters (e.g., passing functions or
  class instances across the boundary)?
- **Data-fetching duplication** — RSCs can fetch data directly; check whether the document has a clear
  rule for when to fetch in a Server Component vs. via the client-side data layer (React Query, etc.),
  to avoid fetching the same data twice or having two disagreeing sources.

## Streaming SSR failures
- **Partial-render/timeout handling** — if part of the page streams in after the initial shell, what
  does the user see if that slow part times out or errors? A document using streaming SSR with no
  mention of this is a gap.
- **Suspense boundary placement** — are boundaries placed deliberately around genuinely independent,
  slow-loading regions, or wrapped around the entire page (which defeats the purpose of streaming)?

## Browser-only API usage during SSR
Any use of `window`, `document`, `localStorage`, or similar during the render path will throw or
behave incorrectly on the server. A document that doesn't mention guarding against this at all for an
SSR system — even a one-line acknowledgment that a utility layer handles it — is worth flagging,
especially if the document integrates third-party UI libraries (a common source of accidental
browser-API calls during SSR).

## Server-side singleton leakage / cross-request memory contamination
This is the highest-severity rendering failure mode and should always be checked explicitly for any
SSR system: anything instantiated once at module scope on the server (a database/API client, an
in-memory cache, a per-request-looking variable that's actually shared) can leak state between
concurrent requests from *different users* on the same server process. If the document describes any
shared client/cache/singleton without explicitly stating it's re-created per request (or is stateless
and safe to share), treat this as a real risk to raise — severity **Blocker** if the leaked data could
plausibly include one user's information reaching another user's response, **Major** otherwise (e.g.,
leaking a stale but non-sensitive cached value).

## What "good" looks like in the document
A strong SSR-aware HLD will, at minimum: name which rendering mode applies per route (cross-reference
Lens 2's per-route rendering-strategy check), acknowledge the hydration-mismatch risk for any
dynamic/locale-dependent content, state that server-side clients/caches are request-scoped or
explicitly safe to share, and address what a user sees during a streaming failure. A document that
uses SSR/RSC terminology correctly but never touches any of the above is exhibiting security-by-
vocabulary's rendering-strategy cousin — the right words, no real mechanism.
