# Vendor Dependency & Cost Risk — Deep Reference

Consulted from Lens 9 (Observability, Deployment & Operability) for depth beyond the checklist items already in Lens 9. Use this when the document
leans heavily on third-party services or when cost is plausibly a first-order concern for the stated
stage (most relevant for Startup/MVP and Growth stages; still worth a lighter pass at Enterprise).

## Categorizing vendor risk by blast radius

Not all third-party dependencies deserve equal scrutiny. Weight the review effort by blast radius:

- **Tier 1 — outage blocks core functionality:** auth providers (Auth0, Clerk, Cognito, Firebase
  Auth), payment processors (Stripe, Braintree), the BFF's own hosting/CDN. These deserve full
  scrutiny on all four checklist items (outage handling, lock-in, SLA assumptions, migration
  difficulty) regardless of stage.
- **Tier 2 — outage degrades but doesn't block:** analytics vendors, feature-flag platforms,
  observability/APM tools, AI/model providers used for non-critical features. Outage handling should
  default to "degrade gracefully, don't block the core flow" — flag it if the document doesn't
  distinguish these from Tier 1 in how failure is handled.
- **Tier 3 — cosmetic or easily substitutable:** a specific font-loading service, a specific icon
  library CDN. Light scrutiny is appropriate; don't manufacture findings here.

A document that gives a Tier 3 dependency the same risk treatment as a Tier 1 one (or vice versa —
treating Stripe as trivially replaceable) is itself a signal the risk analysis wasn't calibrated.

## Vendor lock-in: what "addressed" actually looks like

It's not enough for the document to name an abstraction layer in principle — check whether the
document's own architecture actually routes all calls to the vendor through that abstraction, or
whether the vendor's SDK is called directly from components/pages throughout. A document that says
"we'll abstract the payment provider" but then describes Stripe Elements embedded directly in checkout
components has an abstraction in name only — note this specifically rather than accepting the
stated intent at face value.

## SLA and outage-handling patterns worth recognizing as genuinely addressed

- A stated fallback/degraded mode (e.g., "if the feature-flag service is unreachable, default all
  flags to their last-known-good cached value" or "off").
- A stated cached/local fallback for data normally fetched from a Tier 2 vendor.
- An explicit acknowledgment that a Tier 1 vendor outage means the product is down, paired with a
  monitoring/alerting plan for that vendor's status (this counts as "addressed" — the point isn't that
  every outage must be engineered around, it's that the document must not be silent about it).

## Cost analysis: what "addressed" actually looks like

Precise dollar estimates aren't required — relative reasoning is enough to pass this check. Look for
any of:
- A comparison between two architectural options that explicitly weighs cost as a factor, even
  qualitatively ("ISR over full SSR here — the marginal freshness benefit doesn't justify the added
  compute cost at our traffic level").
- An acknowledgment of a cost driver tied to a scale assumption ("WebSocket fan-out cost scales with
  concurrent connections; revisit connection-pooling/regional gateways before this exceeds
  [stated threshold]").
- A stated cost ceiling or budget constraint that visibly influenced a decision.

What does **not** count as addressing cost: naming a pricing tier without reasoning about it, or
asserting a choice is "cost-effective" with no comparison to what it's more effective than.

## When cost concerns should weigh more heavily in the review

Weight this part of Lens 9 more heavily when: the document states or implies a small/self-funded team,
the domain is naturally expensive to run at scale (heavy SSR, always-on real-time, AI inference calls
per user action), or the document's own stated NFRs imply significant scale with no cost discussion
anywhere. Weight it less heavily for a well-resourced Enterprise system where operational cost is a
secondary concern to correctness and compliance — say so explicitly rather than penalizing every
system uniformly.
