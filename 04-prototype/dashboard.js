/* ==========================================================================
   FMS — money-movement dashboard
   A product-facing quality-and-funnel view over payins and payouts.
   Built against product-requirements.md → Success Metrics / Tracking Requirements.

   Two principles, both carried from the funds prototype:

     1. Every number on this page is DERIVED. One array of movements, one
        metrics() function, nothing stored and nothing hardcoded — the same
        discipline app.js applies to balances (Rule L1, Rule B12).
     2. Every number declares its source. A figure FMS can compute from its own
        write path is server-truth. A figure that needs a client event is marked
        as such, because the two are not equally trustworthy and one of them is
        blocked on an open compliance question (PRD, Open Questions).

   The routes, reason codes and payout outcomes are not invented here. They are
   the catalogues the funds flow already emits — ROUTES, PAYIN_OUTCOMES,
   EOD_OUTCOMES in app.js — so the dashboard measures exactly what the product
   is able to produce, and no more.

   The population is synthetic and deterministic: one seed, no Math.random, so
   every figure is reproducible and the self-test can assert against it.
   ========================================================================== */

/* ---------- money ----------
   Deliberately duplicated from app.js rather than shared. These two pages have
   no module system and no build step by design; a four-line formatter is a
   cheaper duplication than a loader. Money is integer paise here too. */
const P = r => Math.round(r * 100);
const R = p => '₹' + (Math.abs(p) / 100).toLocaleString('en-IN',
  { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const R0 = p => '₹' + Math.round(Math.abs(p) / 100).toLocaleString('en-IN');
/* Aggregate money is read at a glance, not reconciled, so it is written the way
   an Indian reader scans it — and the unit is chosen by the amount rather than
   fixed, so nothing ever renders as "₹0.00 L" or as an eight-digit run of
   commas. Precision holds at roughly three significant figures at every rung,
   so ₹4.65 Cr and ₹8.54 K carry the same amount of information. */
const UNITS = [
  { at: 1e7, sfx: ' Cr' },   // crore
  { at: 1e5, sfx: ' L'  },   // lakh
  { at: 1e3, sfx: ' K'  },   // thousand
  { at: 0,   sfx: ''    }    // below a thousand, the rupees themselves
];
const Rc = p => {
  const r = Math.abs(p) / 100, sign = p < 0 ? '−' : '';
  const u = UNITS.find(x => r >= x.at) || UNITS[UNITS.length - 1];
  if (!u.at) return sign + '₹' + Math.round(r).toLocaleString('en-IN');
  const n = r / u.at;
  return sign + '₹' + n.toFixed(n >= 100 ? 0 : n >= 10 ? 1 : 2) + u.sfx;
};

/* ---------- time ---------- */
const MIN = 60000, HOUR = 3600000, DAY = 86400000;
/* Fixed, not Date.now(). A dashboard whose numbers move because the clock moved
   cannot be asserted against, and cannot be discussed twice. */
const NOW = Date.UTC(2026, 7, 19, 12, 0);
const SPAN = 90;

const dur = ms => {
  if (ms == null) return '—';
  const s = Math.round(ms / 1000);
  if (s < 90) return s + 's';
  /* Minutes alone would print both the 90th and the 95th percentile as "3m".
     Two different numbers that render identically are worse than one number. */
  if (s < 600) return Math.floor(s / 60) + 'm ' + String(s % 60).padStart(2, '0') + 's';
  const m = Math.round(ms / MIN);
  if (m < 90) return m + 'm';
  const h = ms / HOUR;
  if (h < 36) return h.toFixed(1).replace(/\.0$/, '') + 'h';
  return (ms / DAY).toFixed(1).replace(/\.0$/, '') + 'd';
};
const dayKey = t => Math.floor((t - (NOW - SPAN * DAY)) / DAY);
const dateOf = t => new Date(t).toLocaleDateString('en-IN',
  { day: 'numeric', month: 'short', year: '2-digit', timeZone: 'UTC' });

/* ---------- statistics ---------- */
const sortNum = a => a.slice().sort((x, y) => x - y);
const pctile = (arr, p) => {
  if (!arr.length) return null;
  const s = sortNum(arr);
  return s[Math.min(s.length - 1, Math.max(0, Math.ceil(p * s.length) - 1))];
};
const median = arr => pctile(arr, 0.5);
/* Percentages are computed in one place so an empty denominator is never
   rendered as 0% — an unmeasured metric and a failing metric are not the same
   thing and must not look the same. */
const rate = (n, d) => (d ? n / d : null);
const pc = v => v == null ? '—' : (v * 100).toFixed(1) + '%';
const pc0 = v => v == null ? '—' : Math.round(v * 100) + '%';

/* ---------- deterministic randomness ---------- */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const weighted = (rand, table) => {
  let r = rand(), acc = 0;
  for (const [k, w] of table) { acc += w; if (r < acc) return k; }
  return table[table.length - 1][0];
};
const oneOf = (rand, arr) => arr[Math.floor(rand() * arr.length)];

/* ==========================================================================
   VOCABULARY — mirrors the catalogues in app.js
   ========================================================================== */

const ROUTES = [
  { id: 'upi',  n: 'UPI',            short: 'UPI',         observable: true  },
  { id: 'nb',   n: 'Net banking',    short: 'Net banking', observable: true  },
  /* selfService in app.js. The user leaves and pays from their own banking app,
     so FMS never sees an attempt — only, sometimes, a credit. Every rate that
     needs a denominator of attempts is therefore blind to this route. */
  { id: 'neft', n: 'Bank transfer (NEFT / IMPS)', short: 'NEFT / IMPS', observable: false }
];
const routeName = id => (ROUTES.find(r => r.id === id) || {}).n || id;

/* fault: whose problem the outcome is. Straight from principle 3 in app.js's
   OUTCOMES comment — "name whose problem it is". A dashboard that pools our
   outages with the user's bank's declines cannot tell us what to fix. */
const PAYIN_OUTCOMES = [
  { id: 'success',      label: 'Succeeded',              st: 'done',    fault: null },
  { id: 'declined',     label: 'Bank declined',          st: 'failed',  fault: 'bank' },
  { id: 'insufficient', label: 'Not enough in bank',     st: 'failed',  fault: 'user' },
  { id: 'banklimit',    label: 'Above bank’s limit', st: 'failed', fault: 'bank' },
  { id: 'timeout',      label: 'No answer from bank',    st: 'unknown', fault: 'bank' },
  { id: 'gateway',      label: 'Our service was down',   st: 'failed',  fault: 'us'   },
  { id: 'abandoned',    label: 'User backed out',        st: 'failed',  fault: 'none' }
];
const payinOutcome = id => PAYIN_OUTCOMES.find(o => o.id === id);
const FAULTS = {
  bank: { label: 'The user’s bank', tone: 'info' },
  user: { label: 'The user’s balance', tone: 'warn' },
  us:   { label: 'Us', tone: 'bad' },
  none: { label: 'Nobody — a choice', tone: 'mute' }
};

const EOD_OUTCOMES = [
  { id: 'sent',       label: 'Sent in full',      good: true  },
  { id: 'partial',    label: 'Partly sent',       good: true  },
  { id: 'nothing',    label: 'Nothing available', good: false },
  { id: 'bankreject', label: 'Bank refused it',   good: false },
  { id: 'raildown',   label: 'Bank rail was down', good: false }
];

/* ==========================================================================
   POPULATION — synthetic, deterministic, generated once
   Shaped to be plausible rather than flattering: two launch thresholds are met
   and two are missed, because a dashboard on which everything is green teaches
   nobody how to read it.
   ========================================================================== */

function build() {
  const rand = mulberry32(20260819);
  /* A second stream for attributes that hang off a movement without changing its
     course. Drawing those from the main stream would reshuffle every number after
     them, so adding one presentational field could move the success rate — which
     is exactly the kind of coupling a reproducible dataset exists to avoid. */
  const aux = mulberry32(90210);
  const accounts = [], visits = [], intents = [], attempts = [], payouts = [];

  for (let i = 0; i < 3200; i++) {
    const openedAt = NOW - Math.floor(rand() * 150) * DAY;
    accounts.push({
      id: 'A-' + (41000 + i),
      openedAt,
      // becoming able to receive money is a separate event from opening (REQ-505)
      canReceiveAt: openedAt + Math.floor(rand() * 4) * DAY,
      firstDepositAt: null, payins: 0, credited: 0, retained: 0
    });
  }
  const byId = new Map(accounts.map(a => [a.id, a]));

  const AMOUNTS = [[P(500), .06], [P(1000), .12], [P(2000), .13], [P(5000), .21],
                   [P(10000), .19], [P(25000), .13], [P(50000), .09],
                   [P(100000), .05], [P(200000), .02]];

  /* Outcome weights per route and per attempt number. A retry is not a fresh
     draw of the same lottery: the user has usually changed something. */
  const OUT = {
    upi: [['success', .947], ['declined', .018], ['insufficient', .011],
          ['banklimit', .004], ['timeout', .009], ['gateway', .003], ['abandoned', .008]],
    nb:  [['success', .911], ['declined', .026], ['insufficient', .015],
          ['banklimit', .004], ['timeout', .018], ['gateway', .011], ['abandoned', .015]]
  };
  const RETRY_OUT = {
    upi: [['success', .400], ['declined', .200], ['insufficient', .130],
          ['banklimit', .040], ['timeout', .070], ['gateway', .040], ['abandoned', .120]],
    nb:  [['success', .350], ['declined', .225], ['insufficient', .145],
          ['banklimit', .040], ['timeout', .080], ['gateway', .050], ['abandoned', .110]]
  };
  /* How likely a user is to try again, by why they failed. "timeout" is low on
     purpose: REQ-205's copy tells them not to, and whether they obey is itself
     the metric. */
  const RETRY_P = { declined: .88, insufficient: .55, banklimit: .90,
                    gateway: .88, abandoned: .70, timeout: .17 };

  let pid = 0, oid = 0;

  for (let d = SPAN - 1; d >= 0; d--) {
    const t0 = NOW - (d + 1) * DAY;          // the bucket ENDS at NOW - d * DAY
    const dow = new Date(t0).getUTCDay();
    const weekend = dow === 0 || dow === 6;
    const visitP = weekend ? 0.048 : 0.112;

    for (const a of accounts) {
      if (a.canReceiveAt > t0) continue;
      /* An account that has just become able to receive money is not a dormant
         one that happens to be empty. Funding it is the reason the user is
         here at all, so it is modelled as its own behaviour rather than as the
         same coin flip with a different label. */
      const onboarding = a.firstDepositAt == null && t0 - a.canReceiveAt < 10 * DAY;
      if (rand() > (onboarding ? 0.38 : visitP)) continue;

      const t = t0 + Math.floor(rand() * DAY);
      const blocked = rand() < 0.021;
      const state = blocked ? 'blocked'
                  : a.firstDepositAt == null ? 'empty'
                  : rand() < 0.034 ? 'debit' : 'funded';

      const v = { t, acct: a.id, state, started: false, viewedRoutes: false,
                  intent: null, deadWithdraw: false, openedDerivation: false };
      visits.push(v);

      /* The zero-state and the blocked state are dead ends unless the one
         action offered there is taken (REQ-504, REQ-505). */
      if (state === 'blocked') continue;

      /* A funded user who taps withdraw and finds nothing withdrawable is the
         moment REQ-301 exists for. Tracked whether or not they recover. */
      if (state === 'funded' && rand() < 0.086) {
        v.deadWithdraw = true;
        v.openedDerivation = rand() < 0.437;
      }

      const startP = state === 'empty' ? (onboarding ? 0.68 : 0.33)
                   : state === 'debit' ? 0.279 : 0.318;
      if (rand() > startP) continue;
      v.started = true;
      /* The empty state offers a smallest-useful-amount alongside the button.
         Whether users take that figure or type their own is the difference
         between the suggestion working and the button working. */
      if (state === 'empty') v.usedSuggested = aux() < 0.583;

      if (rand() > 0.874) continue;          // payin started, route list never reached
      v.viewedRoutes = true;
      if (rand() > 0.812) continue;          // routes seen, nothing committed

      const route = weighted(rand, [['upi', .624], ['nb', .187], ['neft', .189]]);
      const paise = weighted(rand, AMOUNTS);
      /* Opening the funds page and committing a payment are not the same moment.
         Between them sits the user's own time: reading the balance, choosing an
         amount, picking a rail, and getting through the bank's authorisation.
         Most of it is minutes; a minority leave and come back. */
      const think = rand() < 0.083
        ? 5 * MIN + rand() * 35 * MIN
        : 25000 + rand() * 300000;
      const commitAt = t + Math.round(think);
      /* A user still deciding at the as-of moment has not committed yet. Letting
         the commit land in the future would date a payin after the day the page
         claims to describe. */
      if (commitAt > NOW) continue;
      const intent = { id: 'D-' + (++pid), acct: a.id, t: commitAt, viewT: t, route, paise,
                       observable: route !== 'neft', attempts: [],
                       firstDeposit: a.firstDepositAt == null };
      intents.push(intent);
      v.intent = intent.id;

      if (route === 'neft') {
        /* Handed off. FMS learns of it only if the money turns up, and cannot
           tell a user who never sent it from one whose transfer went astray. */
        if (rand() < 0.861) {
          const credited = commitAt + 30 * MIN + Math.floor(rand() * 165 * MIN);
          const at = { id: 'PI-' + (++oid), intent: intent.id, n: 1, acct: a.id,
                       route, paise, t: commitAt, outcome: 'success', st: 'done',
                       resolvedAt: credited, usableAt: credited,
                       observable: false, reversedAt: null, causedDebit: false,
                       firstDeposit: intent.firstDeposit };
          attempts.push(at); intent.attempts.push(at);
        }
        settle(intent);
        continue;
      }

      let n = 0, last = null;
      while (n < 3) {
        n++;
        /* The route is resolved BEFORE the draw. A user who switched rails is
           taking their chances on the new rail, not the old one, and drawing
           against the original route would quietly make switching look useless. */
        const curRoute = n > 1 && last && last.switched ? last.switchedTo : route;
        const outcome = weighted(rand, n === 1 ? OUT[curRoute] : RETRY_OUT[curRoute]);
        const o = payinOutcome(outcome);
        const at = { id: 'PI-' + (++oid), intent: intent.id, n, acct: a.id,
                     route: curRoute,
                     paise, t: commitAt + (n - 1) * (3 * MIN + Math.floor(rand() * 24 * MIN)),
                     outcome, st: o.st, observable: true,
                     resolvedAt: null, usableAt: null, reversedAt: null,
                     causedDebit: false, firstDeposit: intent.firstDeposit,
                     switched: false, switchedTo: null };

        if (o.st === 'done') {
          const lag = at.route === 'upi' ? 8000 + rand() * 82000 : 20000 + rand() * 220000;
          at.resolvedAt = at.t + lag; at.usableAt = at.resolvedAt;
        } else if (o.st === 'unknown') {
          /* An unknown outcome is its own state with its own dwell, and it is
             not a failure until it resolves as one. */
          at.resolvedAt = at.t + 20 * MIN + rand() * 2.4 * DAY;
          at.settledAs = rand() < 0.781 ? 'success' : 'failed';
          if (at.settledAs === 'success') at.usableAt = at.resolvedAt;
        } else {
          at.resolvedAt = at.t + 2000 + rand() * 40000;
        }

        attempts.push(at); intent.attempts.push(at); last = at;
        if (o.st === 'done') break;
        if (o.st === 'unknown' && at.settledAs === 'success') break;
        if (rand() > (RETRY_P[outcome] || 0.4)) break;
        if (rand() < 0.381) { at.switched = true; at.switchedTo = at.route === 'upi' ? 'nb' : 'upi'; }
      }
      settle(intent);
    }
  }

  /* An intent's outcome is the outcome of its last attempt, resolved. */
  function settle(intent) {
    const a = byId.get(intent.acct);
    const won = intent.attempts.find(x => x.usableAt != null);
    intent.credited = !!won;
    intent.creditedAt = won ? won.usableAt : null;
    if (!won) return;
    if (a.firstDepositAt == null) a.firstDepositAt = won.usableAt;
    a.payins++; a.credited += intent.paise;
    /* Whether this money was still there past the next mandated settlement —
       the float-retention KPI. It is a property of the payin, not of the
       account, so it can be scoped to whatever period is being looked at. */
    intent.retained = rand() < 0.743;
    /* Rare, high-consequence: a credit reversed days later (PAYIN_REVERSAL). */
    if (rand() < 0.0041) {
      won.reversedAt = won.usableAt + DAY + rand() * 4 * DAY;
      won.causedDebit = rand() < 0.223;
    }
  }

  /* ---------- payouts ---------- */
  const funded = accounts.filter(a => a.firstDepositAt != null);
  for (let d = SPAN - 1; d >= 0; d--) {
    const t0 = NOW - (d + 1) * DAY;
    const dow = new Date(t0).getUTCDay();
    if (dow === 0 || dow === 6) continue;         // the payout run is a working-day job
    for (const a of funded) {
      if (a.firstDepositAt > t0) continue;
      if (rand() > 0.0209) continue;
      const t = t0 + Math.floor(rand() * 12 * HOUR) + 6 * HOUR;
      const paise = weighted(rand, AMOUNTS);
      const submit = weighted(rand, [['accepted', .834], ['cutoff', .119], ['review', .047]]);
      const eod = weighted(rand, [['sent', .901], ['partial', .042],
                                  ['nothing', .019], ['bankreject', .023], ['raildown', .015]]);
      /* The arrival time is quoted from account state at request (REQ-303) and
         retained, so it can be held against what actually happened. */
      const quotedAt = t + (submit === 'cutoff' ? 1.6 * DAY : submit === 'review' ? 1.9 * DAY : 22 * HOUR);
      const good = eod === 'sent' || eod === 'partial';
      let arrivedAt = null;
      if (good) {
        const early = weighted(rand, [['before', .889], ['late1', .062],
                                      ['late6', .033], ['lateBad', .016]]);
        arrivedAt = quotedAt - (early === 'before' ? rand() * 9 * HOUR
                  : early === 'late1' ? -rand() * HOUR
                  : early === 'late6' ? -(HOUR + rand() * 5 * HOUR)
                  : -(6 * HOUR + rand() * 30 * HOUR));
      }
      payouts.push({ id: 'PO-' + (++oid), acct: a.id, t, paise, submit, eod,
                     quotedAt, arrivedAt });
    }
  }

  return { accounts, visits, intents, attempts, payouts };
}

const DATA = build();

/* ==========================================================================
   METRICS — the single definition
   Everything the page renders comes out of here. Counting rules that the PRD
   leaves open are parameters, not assumptions, so the effect of choosing one
   can be seen rather than argued about.
   ========================================================================== */

function metrics(opt) {
  /* A window, not a length: a custom range needs both ends, and every
     "so far this period" rule below has to be measured against the end of the
     window rather than against now — otherwise a range that closed last week
     would still be waiting for accounts to fund. */
  const to = opt.to != null ? opt.to : NOW;
  const from = opt.from != null ? opt.from
             : to - (opt.days != null ? opt.days : 30) * DAY;
  const days = Math.max(1, Math.round((to - from) / DAY));
  const label = opt.label || (days === 1 ? 'Today' : days + ' days');
  const countAbandoned = opt.countAbandoned;      // is backing out a failure?

  const inRange  = t => t >= from && t <= to;
  const visits   = DATA.visits.filter(v => inRange(v.t));
  const intents  = DATA.intents.filter(i => inRange(i.t));
  const attempts = DATA.attempts.filter(a => inRange(a.t));
  const payouts  = DATA.payouts.filter(p => inRange(p.t));

  const obsIntents  = intents.filter(i => i.observable);
  const obsAttempts = attempts.filter(a => a.observable);

  /* --- what counts as a failure ------------------------------------------ */
  const isAbandon = a => a.outcome === 'abandoned';
  const succeeded = a => a.usableAt != null;
  const countable = a => countAbandoned || !isAbandon(a);

  /* --- the three readings of "attempts succeed on the first try" --------- */
  const attemptPool = obsAttempts.filter(countable);
  const attemptLevel = rate(attemptPool.filter(succeeded).length, attemptPool.length);
  const firstTryValue = obsIntents
    .filter(i => i.attempts.length && succeeded(i.attempts[0]))
    .reduce((s, i) => s + i.paise, 0);

  const intentPool = obsIntents.filter(i => i.attempts.length && countable(i.attempts[0]));
  const firstTry = rate(intentPool.filter(i => succeeded(i.attempts[0])).length, intentPool.length);
  const eventual = rate(intentPool.filter(i => i.credited).length, intentPool.length);

  /* --- by route ---------------------------------------------------------- */
  const byRoute = ROUTES.map(r => {
    const pool = obsAttempts.filter(a => a.route === r.id && countable(a));
    const all  = attempts.filter(a => a.route === r.id);
    /* Split on the FIRST attempt's route, so these partition the headline's
       population exactly and the parts add back up to the whole. A retry that
       switched rails stays with the method the user actually started on. */
    const fa = intentPool.filter(i => i.attempts[0].route === r.id);
    /* Credited money is attributed to the rail that actually delivered it, not
       the one the user started on — the money arrived somewhere, and this is
       where. Every credited intent has exactly one delivering attempt, so these
       partition the credited total exactly. */
    const cr = intents.filter(i => i.credited &&
      (i.attempts.find(a => a.usableAt != null) || {}).route === r.id);
    return { id: r.id, n: r.n, short: r.short, observable: r.observable, attempts: pool.length,
             creditedN: cr.length,
             creditedValue: cr.reduce((s, i) => s + i.paise, 0),
             firstTryN: fa.length,
             firstTryWon: fa.filter(i => succeeded(i.attempts[0])).length,
             successN: pool.filter(succeeded).length,
             eventualWon: fa.filter(i => i.credited).length,
             firstTry: r.observable ? rate(fa.filter(i => succeeded(i.attempts[0])).length, fa.length) : null,
             firstTryValue: fa.filter(i => succeeded(i.attempts[0])).reduce((s, i) => s + i.paise, 0),
             /* the same three readings as the headline card, per method */
             eventual: r.observable ? rate(fa.filter(i => i.credited).length, fa.length) : null,
             volume: all.filter(succeeded).reduce((s, a) => s + a.paise, 0),
             credits: all.filter(succeeded).length,
             handoffs: r.observable ? null : intents.filter(i => i.route === r.id).length,
             success: r.observable ? rate(pool.filter(succeeded).length, pool.length) : null };
  });
  const selfServe = byRoute.find(r => !r.observable);
  selfServe.unaccounted = selfServe.handoffs - selfServe.credits;

  /* --- why the rest failed ------------------------------------------------ */
  const failed = obsAttempts.filter(a => !succeeded(a) && (countAbandoned || !isAbandon(a)));
  const reasons = PAYIN_OUTCOMES.filter(o => o.st !== 'done').map(o => {
    const rows = failed.filter(a => a.outcome === o.id);
    return { id: o.id, label: o.label, fault: o.fault, n: rows.length,
             share: rate(rows.length, failed.length),
             value: rows.reduce((s, a) => s + a.paise, 0) };
  }).filter(r => r.n).sort((a, b) => b.n - a.n);
  const byFault = Object.keys(FAULTS).map(f => ({
    id: f, label: FAULTS[f].label, tone: FAULTS[f].tone,
    n: reasons.filter(r => r.fault === f).reduce((s, r) => s + r.n, 0),
    value: reasons.filter(r => r.fault === f).reduce((s, r) => s + r.value, 0),
    /* the reasons that make up this group. A rollup nobody can decompose is a
       rollup nobody can act on — "the user's bank" is not a fix, "bank declined"
       against "above the bank's limit" points at two different ones. */
    parts: reasons.filter(r => r.fault === f).map(r => ({ label: r.label, n: r.n }))
  })).filter(f => f.n).sort((a, b) => b.n - a.n);

  /* --- retries ------------------------------------------------------------ */
  const failedFirst = obsIntents.filter(i => i.attempts.length && !succeeded(i.attempts[0]));
  const retried = failedFirst.filter(i => i.attempts.length > 1);
  const switchedRoute = retried.filter(i => i.attempts[0].switched);
  /* Which way they switched, and whether it worked. REQ-205 suggests a different
     rail after a failure; the direction says which suggestion is being taken and
     the success rate says whether it deserved to be. */
  const switchDirs = [['upi', 'nb'], ['nb', 'upi']].map(([from, to]) => {
    const rows = switchedRoute.filter(i => i.attempts[0].route === from && i.attempts[0].switchedTo === to);
    return { from: routeName(from), to: routeName(to), n: rows.length,
             share: rate(rows.length, switchedRoute.length),
             won: rate(rows.filter(i => i.credited).length, rows.length) };
  }).filter(d => d.n).sort((a, b) => b.n - a.n);
  const retryWon = retried.filter(i => i.credited);

  /* --- speed -------------------------------------------------------------- */
  const lag = a => a.usableAt - a.t;
  const usable = obsAttempts.filter(succeeded);
  const lags = usable.map(lag);
  const allLags = attempts.filter(a => a.usableAt != null).map(lag);
  const speed = {
    /* every rail, including the self-service one the cards leave out — so the
       cost of that exclusion is a number rather than an assumption */
    allRoutes: { n: allLags.length, p50: pctile(allLags, .5), p95: pctile(allLags, .95) },
    p50: pctile(lags, .5), p90: pctile(lags, .9),
    p95: pctile(lags, .95), n: usable.length,
    /* the ends of the distribution: a middle is meaningless without them */
    lo: lags.length ? Math.min.apply(null, lags) : null,
    hi: lags.length ? Math.max.apply(null, lags) : null,
    byRoute: ROUTES.filter(r => r.observable).map(r => {
      const l = attempts.filter(a => a.route === r.id && succeeded(a)).map(lag);
      return { id: r.id, n: r.n, count: l.length, p50: median(l), p95: pctile(l, .95) };
    })
  };
  /* The whole journey, page open to money usable. Deliberately across every rail
     including bank transfer: this is the one figure that answers "how long does
     funding an account take", which is the user's question rather than ours. */
  const journey = intents.filter(i => i.credited && i.viewT != null);
  const e2e = journey.map(i => i.creditedAt - i.viewT);
  const thinkLags = journey.map(i => i.t - i.viewT);
  const execLags = journey.map(i => i.creditedAt - i.t);

  const unknownDwell = obsAttempts.filter(a => a.st === 'unknown').map(a => a.resolvedAt - a.t);
  const dwellLo = unknownDwell.length ? Math.min.apply(null, unknownDwell) : null;
  const dwellHi = unknownDwell.length ? Math.max.apply(null, unknownDwell) : null;

  /* --- payouts ------------------------------------------------------------ */
  /* Reached a bank INSIDE the window. A payout still in the air is not late,
     it is undecided, and counting it either way would be a guess. */
  const arrived = payouts.filter(p => p.arrivedAt != null && p.arrivedAt <= to);
  const delta = arrived.map(p => p.arrivedAt - p.quotedAt);      // negative = early
  const onTime = arrived.filter(p => p.arrivedAt <= p.quotedAt);
  const late   = arrived.filter(p => p.arrivedAt > p.quotedAt);
  const arrival = {
    n: arrived.length,
    onTimeN: onTime.length, onTimeValue: onTime.reduce((s, p) => s + p.paise, 0),
    lateN: late.length,     lateValue: late.reduce((s, p) => s + p.paise, 0),
    lateShare: rate(late.length, arrived.length),
    withinQuote: rate(delta.filter(d => d <= 0).length, delta.length),
    medianEarly: median(delta.map(d => -d)),
    worst: delta.length ? Math.max.apply(null, delta) : null,
    buckets: [
      { label: 'More than 6h early', n: delta.filter(d => d <= -6 * HOUR).length },
      { label: 'Up to 6h early',     n: delta.filter(d => d > -6 * HOUR && d <= 0).length },
      { label: 'Up to 1h late',      n: delta.filter(d => d > 0 && d <= HOUR).length },
      { label: '1 to 6h late',       n: delta.filter(d => d > HOUR && d <= 6 * HOUR).length },
      { label: 'More than 6h late',  n: delta.filter(d => d > 6 * HOUR).length }
    ]
  };
  const eod = EOD_OUTCOMES.map(o => {
    const rows = payouts.filter(p => p.eod === o.id);
    return { id: o.id, label: o.label, good: o.good, n: rows.length,
             share: rate(rows.length, payouts.length),
             value: rows.reduce((s, p) => s + p.paise, 0) };
  }).filter(o => o.n);

  /* --- funnels ------------------------------------------------------------ */
  /* Each step carries its own definition. One source, two presentations: the ⓘ
     beside the step, and the summary card at the foot of the tab. A step whose
     meaning has to be guessed is a step whose number cannot be trusted. */
  const dep = [
    { k: 'Funds view opened', n: visits.length, src: 'client',
      def: `One open of the funds screen. Counted <b>per view, not per user</b> — someone who opens the
            page four times in a day is four. It is the denominator every rate below it is measured
            against.` },
    { k: 'Payin started', n: visits.filter(v => v.started).length, src: 'client',
      def: `The add-funds panel was opened and an amount entered. Intent, not commitment: nothing has
            been sent anywhere yet, and the user can still walk away at no cost.` },
    { k: 'Route list viewed', n: visits.filter(v => v.viewedRoutes).length, src: 'client',
      def: `The routes were shown with their fees and arrival times. This is REQ-202’s disclosure
            happening <b>before</b> the user commits rather than after, so the step exists to prove the
            disclosure occurred, not just to measure a drop.` },
    { k: 'Payment committed', n: intents.length, src: 'server',
      def: `A payment instruction FMS actually issued — <b>not</b> a tap on the pay button. This is the
            first step FMS can see for itself; everything above it depends on the app reporting it.` },
    { k: 'Money credited', n: intents.filter(i => i.credited).length, src: 'server',
      def: `The payment ended in money in the account. Counted once per intent, so a payin that
            succeeded on its third attempt counts here once, not three times.` },
    { k: 'Usable as margin', n: intents.filter(i => i.credited).length, src: 'server',
      note: 'median ' + dur(speed.p50) + ' later',
      def: `That money counted toward what the user can trade with. <b>This step never loses anyone —
            it only lags.</b> The figure beside it is the median delay, which is the part the user
            actually experiences.` }
  ];
  const fundedVisits = visits.filter(v => v.state === 'funded');
  const sent = payouts.filter(p => p.eod === 'sent' || p.eod === 'partial');
  const sentCount = sent.length;
  /* A request accepted before the cut-off goes out on the same working day. One
     after it, or one held for review, does not — so this step cannot be called
     "same day" without mislabelling those. */
  const sameDayN = sent.filter(p => p.submit === 'accepted').length;
  const sameDayShare = rate(sameDayN, sentCount);
  const wdr = [
    { k: 'Funds view opened (funded)', n: fundedVisits.length, src: 'client',
      def: `The same event as on the payin side, restricted to visits where the account had money.
            Someone with an empty account cannot withdraw, so counting them would put a denominator
            under a rate they could never join.` },
    { k: 'Withdrawal requested', n: payouts.length, src: 'server',
      def: `A request FMS accepted. Counts the <b>request, not the amount</b> — a payout that was only
            partly sent is still one request. The drop from the step before it is not a failure: most
            funded users open the funds page to look at it, not to take money out.` },
    { k: 'Sent to the bank', n: sentCount, src: 'server',
      note: pc0(sameDayShare) + ' same day',
      def: `Survived the end-of-day run and went to the bank. What falls out here was refused for a
            named reason — nothing available, the bank refused it, or the rail was down — and those
            reasons are broken out on Reliability.
            <br><br><b>${sameDayN.toLocaleString('en-IN')} of these
            (${pc(sameDayShare)}) went out on the same working day.</b> The rest did not, and the step
            therefore cannot be called “sent same day”: a request placed after the
            ${payouts.length ? '3:00 PM' : ''} cut-off goes out on the next working day, and one held
            for review can take a working day longer. Both are still sent, just not today.` },
    /* This step is not a loss. Money that left but has not landed yet is
       undecided, and folding it into the next step would report it as late. */
    { k: 'Reached the bank', n: arrived.length, src: 'server',
      note: (sentCount - arrived.length).toLocaleString('en-IN') + ' still in flight',
      def: `The money landed. <b>The drop here is not a failure</b> — it is payouts still in the air.
            Money that has left but not yet arrived is undecided, and folding it into the next step
            would report it as late when it may still be early.` },
    { k: 'Arrived by the time we quoted', n: onTime.length, src: 'server',
      def: `Reached the bank at or before the arrival time quoted when the request was made (REQ-303).
            This is the promise the product makes, and the rate here is the one on the headline tile.` }
  ];

  /* Not a funnel step. Tapping withdraw and finding nothing withdrawable is a
     branch OFF the path, and putting it inline would draw a funnel that grows
     — the users who go on to request a withdrawal are mostly not these users. */
  const deadWithdraws = visits.filter(v => v.deadWithdraw);
  const deadWithdraw = {
    n: deadWithdraws.length,
    share: rate(deadWithdraws.length, fundedVisits.length),
    recovered: rate(deadWithdraws.filter(v => v.openedDerivation).length, deadWithdraws.length)
  };

  /* --- adoption -----------------------------------------------------------
     A cohort metric needs a cohort, and a seven-day promise needs seven days to
     have passed before it can be judged. The window is the selected period
     shifted back by a week rather than the period itself — otherwise the
     shortest period reports on an empty set, and an unanswerable metric renders
     as a failing one. */
  const cohort = DATA.accounts.filter(a =>
    a.canReceiveAt >= from - 7 * DAY && a.canReceiveAt <= to - 7 * DAY);
  const firstIn7 = rate(
    cohort.filter(a => a.firstDepositAt != null && a.firstDepositAt - a.canReceiveAt <= 7 * DAY).length,
    cohort.length);

  const zeroVisits = visits.filter(v => v.state === 'empty');
  const zeroStarted = zeroVisits.filter(v => v.started);
  const zeroActed = rate(zeroStarted.length, zeroVisits.length);
  const zeroSuggested = rate(zeroStarted.filter(v => v.usedSuggested).length, zeroStarted.length);

  /* Held at 90 days whatever the period selector says. The KPI is stated per
     quarter; re-cutting it to a week would answer a different question using
     the same words, which is worse than not answering it. */
  const perAcct = new Map();
  for (const i of DATA.intents) {
    if (!i.credited || i.creditedAt < to - 90 * DAY || i.creditedAt > to) continue;
    perAcct.set(i.acct, (perAcct.get(i.acct) || 0) + 1);
  }
  const perQuarter = Array.from(perAcct.values());
  /* The median clears the bar at 2 and says nothing about the shape underneath
     it. The share that funded exactly once is the number the median hides. */
  const perQuarterDist = [1, 2, 3, 4, 5].map(k => ({
    k: k === 5 ? '5 or more' : k + (k === 1 ? ' payin' : ' payins'),
    n: perQuarter.filter(v => k === 5 ? v >= 5 : v === k).length
  })).filter(d => d.n);
  const perQuarterMean = rate(perQuarter.reduce((s, v) => s + v, 0), perQuarter.length);
  const onceOnly = rate(perQuarter.filter(v => v === 1).length, perQuarter.length);

  const creditedHere = intents.filter(i => i.credited);
  const creditedValue = creditedHere.reduce((s, i) => s + i.paise, 0);
  const stayed = creditedHere.filter(i => i.retained);
  const leftAgain = creditedHere.filter(i => !i.retained);
  const retainedValue = stayed.reduce((s, i) => s + i.paise, 0);
  const leftValue = leftAgain.reduce((s, i) => s + i.paise, 0);
  const retainedShare = rate(retainedValue, creditedValue);
  /* Cohort accounts fund with real money, and the rupees they brought in are a
     different fact from the share of them that funded at all. */
  const cohortIds = new Set(cohort.map(a => a.id));
  const cohortValue = DATA.intents
    .filter(i => i.credited && cohortIds.has(i.acct)).reduce((s, i) => s + i.paise, 0);

  /* Ticket size and per-account activity. A mean and a median are both given
     because money is skewed: a handful of ₹2,00,000 payins pull the average far
     above what a typical user actually moves. */
  const payinTickets = creditedHere.map(i => i.paise);
  const payoutTickets = arrived.map(p => p.paise);
  const activeAccts = new Set(intents.map(i => i.acct).concat(payouts.map(p => p.acct))).size;
  const ticket = {
    payinMean: rate(payinTickets.reduce((s, v) => s + v, 0), payinTickets.length),
    payinMedian: median(payinTickets),
    payoutMean: rate(payoutTickets.reduce((s, v) => s + v, 0), payoutTickets.length),
    payoutMedian: median(payoutTickets),
    perAccount: rate(payinTickets.length + payoutTickets.length, activeAccts),
    payinsPerAccount: rate(payinTickets.length, new Set(creditedHere.map(i => i.acct)).size),
    valuePerAccount: rate(payinTickets.reduce((s, v) => s + v, 0),
                          new Set(creditedHere.map(i => i.acct)).size),
    activeAccts
  };

  const reversals = attempts.filter(a => a.reversedAt != null);

  /* --- daily series, for the sparklines ----------------------------------- */
  const series = k => {
    const out = [];
    for (let d = days - 1; d >= 0; d--) {
      const lo = to - (d + 1) * DAY, hi = to - d * DAY;
      const pool = obsIntents.filter(i => i.t >= lo && i.t < hi && i.attempts.length
                                      && (countAbandoned || !isAbandon(i.attempts[0])));
      if (k === 'firstTry') out.push(rate(pool.filter(i => succeeded(i.attempts[0])).length, pool.length));
      if (k === 'volume')   out.push(intents.filter(i => i.t >= lo && i.t < hi && i.credited)
                                      .reduce((s, i) => s + i.paise, 0));
      if (k === 'speed')    out.push(median(obsAttempts.filter(a => a.t >= lo && a.t < hi && succeeded(a)).map(lag)));
      if (k === 'arrival')  {
        const p = arrived.filter(x => x.t >= lo && x.t < hi);
        out.push(rate(p.filter(x => x.arrivedAt <= x.quotedAt).length, p.length));
      }
    }
    return out;
  };

  return {
    days, from, to, label, empty: !intents.length && !payouts.length,
    volume: { credited: intents.filter(i => i.credited).reduce((s, i) => s + i.paise, 0),
              credits: intents.filter(i => i.credited).length,
              paidOut: arrived.reduce((s, p) => s + p.paise, 0),
              intents: intents.length, attempts: attempts.length, payouts: payouts.length,
              /* either direction — a payout-only account moved money too, and the
                 label says "moved", not "funded" */
              accounts: new Set(intents.map(i => i.acct)
                .concat(payouts.map(p => p.acct))).size },
    success: { attemptLevel, firstTry, eventual, firstTryValue,
               /* numerator and denominator for each reading, in count and in money,
                  so a reader can check the percentage rather than take it */
               attemptWonN: attemptPool.filter(succeeded).length,
               attemptValue: attemptPool.reduce((s, a) => s + a.paise, 0),
               attemptWonValue: attemptPool.filter(succeeded).reduce((s, a) => s + a.paise, 0),
               firstTryN: intentPool.filter(i => succeeded(i.attempts[0])).length,
               eventualN: intentPool.filter(i => i.credited).length,
               eventualValue: intentPool.filter(i => i.credited).reduce((s, i) => s + i.paise, 0),
               /* what the whole pool was worth, so the headline amount has a
                  denominator in money as well as in count */
               poolValue: intentPool.reduce((s, i) => s + i.paise, 0),
               pool: intentPool.length, attemptPool: attemptPool.length },
    byRoute, selfServe, reasons, byFault, failedTotal: failed.length,
    retry: { failedFirst: failedFirst.length, retried: retried.length,
             rate: rate(retried.length, failedFirst.length),
             switched: rate(switchedRoute.length, retried.length),
             switchedN: switchedRoute.length, switchDirs,
             won: rate(retryWon.length, retried.length) },
    journey: { n: journey.length,
               p50: median(e2e), p90: pctile(e2e, .9), p95: pctile(e2e, .95),
               lo: e2e.length ? Math.min.apply(null, e2e) : null,
               hi: e2e.length ? Math.max.apply(null, e2e) : null,
               think: median(thinkLags), exec: median(execLags),
               thinkShare: rate(median(thinkLags), median(thinkLags) + median(execLags)) },
    ticket,
    speed, unknownDwell: { n: unknownDwell.length, p50: median(unknownDwell),
                           p90: pctile(unknownDwell, .9), lo: dwellLo, hi: dwellHi },
    arrival, eod, dep, wdr, deadWithdraw,
    sameDay: { n: sameDayN, share: sameDayShare, of: sentCount },
    adoption: { firstIn7, cohort: cohort.length, cohortValue, zeroActed, zeroVisits: zeroVisits.length,
                zeroStarted: zeroStarted.length, zeroSuggested,
                perQuarterMedian: median(perQuarter), fundedAccts: perQuarter.length,
                perQuarterDist, perQuarterMean, onceOnly,
                retainedShare, retainedValue, retainedN: stayed.length,
                leftValue, leftN: leftAgain.length, creditedValue },
    reversals: { n: reversals.length, value: reversals.reduce((s, a) => s + a.paise, 0),
                 causedDebit: reversals.filter(a => a.causedDebit).length,
                 share: rate(reversals.length, intents.filter(i => i.credited).length) },
    series
  };
}

/* ==========================================================================
   LAUNCH THRESHOLDS — the PRD's numbers, not ours
   Stated as thresholds rather than improvements because no version of this is
   in production and there is no baseline. A metric with no threshold is shown
   with none, rather than being given one here.
   ========================================================================== */
const THRESHOLDS = {
  firstTry:  { target: 0.95, label: '95% of deposit attempts succeed on the first try' },
  firstIn7:  { target: 0.80, label: '80% of new accounts fund within 7 days' },
  zeroActed: { target: 0.60, label: '60% of zero-balance views lead to an action' },
  perQuarter:{ target: 2,    label: 'Median 2+ payins per funded account per quarter' },
  retained:  { target: 0.70, label: '70% of credited funds stay past the next settlement' },
  /* Set by the product team on 19 Aug 26, closing a gap the PRD left open: it
     names quoted-versus-actual as tracked from day one and never states a level.
     At 100% this stops being a trend and becomes an invariant, in the same family
     as the PRD's target-zero correctness metrics. A quoted arrival is a promise,
     so the bar is every one of them — and when it is missed the fix is a faster
     rail or a more honest quote, never a lower bar. */
  arrival:   { target: 1.00, label: 'Every payout arrives by the time we quoted' }
};

if (typeof module !== 'undefined') module.exports = { metrics, DATA, THRESHOLDS, NOW, DAY, pctile, median, rate };

/* ==========================================================================
   VIEW
   Nothing below computes a figure. Every number rendered here came out of
   metrics() above, so there is exactly one place a definition can live.
   ========================================================================== */

const esc = s => String(s).replace(/[&<>"]/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/* The data exists over a fixed 90-day span ending at the as-of moment. A custom
   range is clamped to it rather than silently returning nothing. */
const DATA_FROM = NOW - SPAN * DAY, DATA_TO = NOW;
const iso = t => new Date(t).toISOString().slice(0, 10);
const midnight = t => Date.parse(iso(t) + 'T00:00:00Z');

const PERIODS = [
  { id: 'today', label: 'Today' },
  { id: '7',     label: '7 days' },
  { id: '30',    label: '30 days' },
  { id: '90',    label: '90 days' },
  { id: 'custom', label: 'Custom' }
];

const state = {
  tab: 'funnel', period: '30', countAbandoned: false, serverOnly: false,
  def: null, fdef: null, rdef: null, sdef: null, cdef: null, ndef: null,
  cFrom: iso(NOW - 14 * DAY), cTo: iso(NOW)
};

/* One place resolves a period into a window, so no card can disagree with
   another about which days it is describing. */
function window_() {
  if (state.period === 'today') {
    return { from: midnight(NOW), to: NOW, label: 'Today' };
  }
  if (state.period === 'custom') {
    let from = Date.parse(state.cFrom + 'T00:00:00Z');
    let to   = Date.parse(state.cTo   + 'T00:00:00Z') + DAY - 1;   // the whole closing day
    if (isNaN(from)) from = DATA_FROM;
    if (isNaN(to)) to = DATA_TO;
    if (from > to) { const s = from; from = midnight(to); to = s + DAY - 1; }
    from = Math.max(DATA_FROM, from);
    to = Math.min(DATA_TO, to);
    return { from, to, label: dateOf(from) + ' – ' + dateOf(to) };
  }
  const d = +state.period;
  return { from: NOW - d * DAY, to: NOW, label: d + ' days' };
}

/* Where a number comes from, said on the number itself rather than in a legend
   nobody reads. The distinction is not cosmetic: one of these two sources is
   blocked on an open compliance question and the other is not. */
const SRC = {
  server: { cls: 'srv', tip: 'Computed by FMS from its own write path. Complete, and reconcilable to the ledger.' },
  client: { cls: 'cli', tip: 'Depends on an event sent from the app. Lossy by nature, and — where the event carries balances — blocked on the PRD’s open question about what may be sent to a third party.' }
};
const srcBadge = s => `<span class="src ${SRC[s].cls}" title="${esc(SRC[s].tip)}">${s === 'server' ? 'FMS' : 'client'}</span>`;

/* A verdict is only ever PASS, BELOW, or nothing. "Nothing" covers two very
   different cases — no threshold was ever set, and not enough data to say —
   and they are never collapsed into a number. */
function verdict(v, target) {
  if (target == null) return `<span class="vd none">no threshold</span>`;
  if (v == null) return `<span class="vd none">not measurable</span>`;
  return v >= target
    ? `<span class="vd ok">meets ${pc0(target)}</span>`
    : `<span class="vd no">below ${pc0(target)}</span>`;
}

function spark(vals, opt) {
  const pts = vals.map((v, i) => [i, v]).filter(p => p[1] != null);
  if (pts.length < 3) return '';
  const ys = pts.map(p => p[1]);
  let lo = Math.min.apply(null, ys), hi = Math.max.apply(null, ys);
  if (hi === lo) { hi = lo + 1; }
  const W = 108, H = 26;
  const x = i => (i / (vals.length - 1)) * W;
  const y = v => H - 2 - ((v - lo) / (hi - lo)) * (H - 4);
  const d = pts.map((p, i) => (i ? 'L' : 'M') + x(p[0]).toFixed(1) + ' ' + y(p[1]).toFixed(1)).join(' ');
  const last = pts[pts.length - 1];
  return `<svg class="spark ${(opt && opt.tone) || ''}" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}"
    role="img" aria-label="trend over the period"><path d="${d}"/>
    <circle cx="${x(last[0]).toFixed(1)}" cy="${y(last[1]).toFixed(1)}" r="2.2"/></svg>`;
}

/* A card's footnote is the reasoning behind its numbers, and reasoning is worth
   more on request than in the way. It moves behind an i, on the same pattern as
   every other explanation on this page. The key is the title, which is unique
   within a tab and survives a re-render. */
const slug = s => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
/* The definition in symbols, then the same thing with this period's numbers in
   it. A reader who can see the division can check the percentage; one who only
   sees the percentage has to trust it. */
const fx = (expr, calc) => `<div class="fx">
  <code>${esc(expr)}</code><code class="num">${esc(calc)}</code></div>`;

const card = (title, body, opt = {}) => {
  const key = slug(title), open = state.cdef === key;
  return `
  <div class="card ${opt.cls || ''}">
    <div class="card-hd"><h2>${esc(title)}</h2>
      ${opt.tag ? `<span class="tag">${esc(opt.tag)}</span>` : ''}
      ${opt.src ? srcBadge(opt.src) : ''}
      ${opt.foot ? `<button class="infobtn ${open ? 'on' : ''}" data-cdef="${key}" type="button"
        aria-expanded="${open}" aria-label="Why this card counts what it counts">i</button>` : ''}
      ${opt.stamp ? `<span class="stamp">${esc(opt.stamp)}</span>` : ''}
    </div>${body}
    ${opt.foot && open ? `<div class="foot"><p>${opt.foot}</p></div>` : ''}
  </div>`;
};

/* A bar whose scale is the largest value present, so a small category never
   reads as zero and a dominant one never runs off the card.

   `frac` overrides that: it fixes the bar to an absolute 0–100% scale, for the
   cards where the bar means a RATE rather than a count. Length and number have
   to mean the same thing, or the chart contradicts its own label. */
const bars = rows => {
  const max = Math.max.apply(null, rows.map(r => r.n).concat([1]));
  return `<div class="bars">` + rows.map(r => {
    const w = r.frac != null ? r.frac * 100 : r.n / max * 100;
    const main = r.display != null ? r.display : r.n.toLocaleString('en-IN');
    const sub = r.sub != null ? r.sub : r.pct != null ? pc(r.pct) : null;
    return `
    <div class="bar ${r.tone || ''}">
      <div class="bl">${esc(r.label)}${
        r.noteHtml ? `<small>${r.noteHtml}</small>` : r.note ? `<small>${esc(r.note)}</small>` : ''}</div>
      <div class="bt">${w > 0 ? `<i style="width:${w.toFixed(1)}%"></i>` : ''}</div>
      <div class="bv">${main}${sub ? `<small>${esc(sub)}</small>` : ''}</div>
    </div>`;
  }).join('') + `</div>`;
};

/* ---------- headline tiles ----------
   A tile is four words and a number, which is not enough to know what was
   counted. Each one therefore carries its full definition — what it counts,
   what it is out of, what it deliberately leaves out, and where its threshold
   comes from. A reader who has to ask is a reader the page failed. */
function kpiTiles(m) {
  const net = m.volume.credited - m.volume.paidOut;
  const moved = m.volume.credited + m.volume.paidOut;
  return [
    { label: 'Funds added', display: Rc(m.volume.credited), tone: 'in',
      pair: pc(rate(m.volume.credited, moved)) + ' of ' + Rc(moved) + ' moved (In + Out)',
      /* Only the rails FMS executes. The self-service rail is not shown, so the
         shares are taken against the money those two carried rather than against
         the headline — otherwise they would sum to 82% with no row explaining
         the missing fifth. */
      split: m.byRoute.filter(r => r.observable).map(r => ({
        n: r.short, v: rate(r.creditedValue, m.byRoute.filter(x => x.observable)
          .reduce((s, x) => s + x.creditedValue, 0)), amt: Rc(r.creditedValue) })),
      chip: `${m.volume.credits.toLocaleString('en-IN')} payins`,
      note: m.reversals.n
        ? `gross — before ${Rc(m.reversals.value)} later reversed`
        : 'no credits were reversed in this range', src: 'server',
      def: {
        counts: `Every payin that ended in money credited to an account in the period, summed in
                 integer paise. <b>Gross</b>: a credit later reversed is still counted here.
                 <br><br>The percentage beside it is a <b>flow share, not a success rate</b> — of all the
                 money that moved in either direction (${Rc(moved)}), this is the portion coming in.
                 How much of what was <i>attempted</i> actually landed is a different question, and it
                 is answered on the First-try payin success tile.`,
        denom: `${m.volume.credits.toLocaleString('en-IN')} credited payins across all routes.
                <br><br>The per-method split below the figure covers only <b>UPI and net banking</b>, and
                its shares are taken against what those two carried, not against the headline. Money that
                arrived by bank transfer —
                <b>${Rc((m.byRoute.find(r => !r.observable) || {}).creditedValue || 0)}</b> — is inside the
                headline but not in the split.`,
        excluded: `Payins that failed, and payins still awaiting confirmation.
                   ${Rc(m.reversals.value)} was credited and later reversed; that is reported on
                   Reliability rather than netted off here, because a reversal is its own event with
                   its own consequence for the account it hits.`,
        fx: fx('Σ amount of every payin that ended in money credited',
                `${m.volume.credits.toLocaleString('en-IN')} payins  =  ${Rc(m.volume.credited)}`) +
             fx('credited ÷ (credited + paid out)',
                `${Rc(m.volume.credited)} ÷ ${Rc(moved)}  =  ${pc(rate(m.volume.credited, moved))}`),
        period: `A <b>flow</b> figure: it is a sum, so it scales with the period. Ninety days holds
                 roughly three times the money of thirty. Comparing it across two different periods says
                 nothing unless the periods are the same length.`,
        source: `No threshold, and none is wanted. This is the denominator the rates on this page are
                 computed over, not a target to hit.`
      } },

    { label: 'Payout', display: Rc(m.volume.paidOut), tone: 'out',
      pair: pc(rate(m.volume.paidOut, m.volume.credited)) + ' of ' + Rc(m.volume.credited) + ' in',
      chip: `${m.arrival.n.toLocaleString('en-IN')} payouts`,
      note: `${Rc(net)} net stayed in accounts`, src: 'server',
      def: {
        counts: `Every withdrawal that reached the user’s bank in the period, summed in integer paise.`,
        denom: `${m.arrival.n.toLocaleString('en-IN')} payouts that reached a bank.`,
        fx: fx('Σ amount of every payout that reached a bank inside the window',
                `${m.arrival.n.toLocaleString('en-IN')} payouts  =  ${Rc(m.volume.paidOut)}`) +
             fx('paid out ÷ credited',
                `${Rc(m.volume.paidOut)} ÷ ${Rc(m.volume.credited)}  =  ${pc(rate(m.volume.paidOut, m.volume.credited))}`),
        excluded: `Requests the end-of-day run refused — nothing available, bank refused, rail down —
                   and requests still in flight. Money that has not left is not money paid out.`,
        source: `No threshold. Shown beside money credited so the net movement is legible:
                 <b>${Rc(net)}</b> more came in than went out over the period.`
      } },

    { label: 'First-try payin success', v: m.success.firstTry, target: THRESHOLDS.firstTry.target,
      pair: Rc(m.success.firstTryValue),
      /* n on every split too: at a short period one method can read 100% off a
         handful of intents, and a rate without its denominator invites that. */
      split: m.byRoute.filter(r => r.observable).map(r => ({
        n: r.short, v: r.firstTry,
        amt: `${Rc(r.firstTryValue)} · n ${r.firstTryN.toLocaleString('en-IN')}` })),
      /* The split directly above already names the two methods, and the ⓘ records
         why the third is absent. Saying it a third time here is noise. */
      note: `${m.success.pool.toLocaleString('en-IN')} payin intents worth ${Rc(m.success.poolValue)}`,
      src: 'server',
      def: {
        counts: `Payin intents whose <b>first</b> payment attempt ended in money credited. An
                 <i>intent</i> is one decision to add money — a user who is declined and retries has
                 made one intent and two attempts.`,
        denom: `${m.success.pool.toLocaleString('en-IN')} payin intents worth
                ${Rc(m.success.poolValue)}, committed in the period on the two routes FMS executes
                itself: UPI and net banking. Of that, <b>${Rc(m.success.firstTryValue)}</b> landed on
                the first attempt — the figure beside the percentage. The per-method split under the
                figure divides exactly this population — by the method the user <b>started</b> on, so a
                retry that switched rails stays with the method it began as — and Reliability breaks it
                out in full.`,
        excluded: `NEFT / IMPS. It is self-service — the user leaves and pays from their own banking
                   app — so FMS never observes an attempt and can never see a failure. Putting a route
                   with no visible failures into the denominator would lift this rate for free.`,
        fx: fx('intents whose FIRST attempt ended credited  ÷  intents committed',
                `${m.success.firstTryN.toLocaleString('en-IN')} ÷ ${m.success.pool.toLocaleString('en-IN')}` +
                `  =  ${pc(m.success.firstTry)}`),
        period: `A <b>rate</b> over movements: the denominator grows with the period but the figure itself
                 does not trend with it. Short periods are noisier rather than better or worse — the count
                 beside the percentage is what tells you which you are looking at.`,
        source: `PRD Success Metrics: <i>“95% of deposit attempts succeed on the first try.”</i> The PRD
                 does not define <i>attempt</i>. The Reliability tab shows all three defensible readings,
                 and they disagree about whether this bar was cleared.`
      } },

    { label: 'Funded within 7 days', v: m.adoption.firstIn7, target: THRESHOLDS.firstIn7.target,
      pair: Rc(m.adoption.cohortValue) + ' from the cohort',
      note: `${m.adoption.cohort.toLocaleString('en-IN')} accounts, each given a full 7 days to fund`,
      src: 'server',
      def: {
        counts: `Accounts whose <b>first successful credit</b> landed within 7 days of the account
                 becoming able to receive money. A credit, not an attempt.`,
        denom: `${m.adoption.cohort.toLocaleString('en-IN')} accounts that became able to receive money
                inside the period and have since had a full seven days.`,
        fx: fx('accounts whose first credit landed ≤ 7 days after they could receive money  ÷  cohort',
                `${Math.round(m.adoption.firstIn7 * m.adoption.cohort).toLocaleString('en-IN')} ÷ ` +
                `${m.adoption.cohort.toLocaleString('en-IN')}  =  ${pc(m.adoption.firstIn7)}`),
        period: `This is a <b>cohort</b> metric, and it does not respond to the period the way the tiles
                 beside it do. Changing the period changes <b>who is in the cohort</b> — not how long each
                 account gets. Every account is still judged over its own seven days, so a longer period
                 admits more accounts rather than giving anyone more time. The rate therefore moves with
                 the composition of the sample, not with the amount of time measured. At the shortest
                 periods the cohort is small enough that the figure is noise rather than signal, which is
                 why the count is printed beside it.`,
        excluded: `Accounts that became fundable in the last 7 days. They have not had their week yet,
                   and counting them as failures would make this metric look worse the faster
                   onboarding gets.`,
        source: `PRD Success Metrics: 80% within 7 days. Measured from <b>able to receive money</b>, not
                 from account opening — an account held in verification for three days has three fewer
                 days to fund, and that delay is not this module’s to answer for.`
      } },

    { label: 'Payouts by the quoted time', v: m.arrival.withinQuote, target: THRESHOLDS.arrival.target,
      pair: Rc(m.arrival.onTimeValue) + ' on time',
      split: [
        { n: 'On time', v: m.arrival.withinQuote,
          amt: `${Rc(m.arrival.onTimeValue)} · n ${m.arrival.onTimeN.toLocaleString('en-IN')}` },
        { n: 'Late', v: m.arrival.lateShare,
          amt: `${Rc(m.arrival.lateValue)} · n ${m.arrival.lateN.toLocaleString('en-IN')}` }
      ],
      note: `${m.arrival.n.toLocaleString('en-IN')} payouts that reached a bank`,
      src: 'server',
      def: {
        counts: `Payouts that reached the user’s bank at or before the arrival time we quoted when the
                 request was made (REQ-303). The quote is retained at request precisely so it can be
                 held against what happened.`,
        denom: `${m.arrival.n.toLocaleString('en-IN')} payouts that reached a bank in the period.`,
        fx: fx('payouts where arrived ≤ quoted  ÷  payouts that reached a bank',
                `${m.arrival.onTimeN.toLocaleString('en-IN')} ÷ ${m.arrival.n.toLocaleString('en-IN')}` +
                `  =  ${pc(m.arrival.withinQuote)}`),
        excluded: `Requests the end-of-day run refused — nothing available, bank refused, rail down.
                   They never had an arrival to be early or late for, and folding them in would mix a
                   speed question with a success question.`,
        source: `<b>100%</b>, set by the product team on 19 Aug 26 — not inherited from the PRD, which
                 names quoted-versus-actual as tracked from day one and never states a level. A quoted
                 arrival is a promise, so the bar is every one of them. That makes this an
                 <b>invariant rather than a trend</b>: it reads <i>below</i> the moment one payout is
                 late, and the fix is a faster rail or a more honest quote — never a lower bar.`
      } },

    { label: 'Payins still there past settlement', v: m.adoption.retainedShare, target: THRESHOLDS.retained.target,
      pair: Rc(m.adoption.retainedValue) + ' of ' + Rc(m.adoption.creditedValue),
      split: [
        { n: 'Stayed', v: m.adoption.retainedShare,
          amt: `${Rc(m.adoption.retainedValue)} · n ${m.adoption.retainedN.toLocaleString('en-IN')}` },
        { n: 'Left', v: rate(m.adoption.leftValue, m.adoption.creditedValue),
          amt: `${Rc(m.adoption.leftValue)} · n ${m.adoption.leftN.toLocaleString('en-IN')}` }
      ],
      note: 'value-weighted, not count-weighted', src: 'server',
      def: {
        counts: `The <b>value</b> of credits made in the period that was still in the account after that
                 account’s next mandated settlement date had passed.
                 <br><br><b>Money survives that date only by being committed</b> — blocked as margin
                 against an open position, pledged, or reserved against a pending obligation. A free
                 credit balance does not survive it. The mandated return takes <i>unused</i> funds, which
                 the requirements define as “a credit balance not required against any commitment”, and
                 Rule W8 executes it without asking.
                 <br><br><b>The date is per account, not a fixed 30 or 90 days.</b> Running-account
                 settlement runs on a <b>monthly or quarterly</b> cycle at the client’s choice
                 (<code>running_account_settlement_cycle</code>), so two accounts credited on the same day
                 can have different deadlines.
                 <br><br><b>Inactivity does not trigger a separate sweep, but it can change which rule
                 applies.</b> REQ-307’s Branch B covers a user inactive long enough for a different return
                 rule to apply, and requires the product to state which one — “so the user is not told a
                 quarterly cycle applies when a monthly one did”. The qualifying period is never stated
                 anywhere in the requirements, which makes it a gap rather than a rule.`,
        fx: fx('Σ value of credits still there past settlement  ÷  Σ value credited',
                `${Rc(m.adoption.retainedValue)} ÷ ${Rc(m.adoption.creditedValue)}` +
                `  =  ${pc(m.adoption.retainedShare)}`),
        denom: `${Rc(m.adoption.creditedValue)} credited in the period. Weighted by value rather than by
                count, because the question is how much money stayed deployed, not how many payins did.`,
        source: `PRD Success Metrics: 70% of deposited funds remain beyond the next mandated settlement
                 date.`,
        flags: [{
          label: 'What it cannot say',
          body: `The PRD offers this as <i>“a measure of whether users trust the account enough to leave
                 money in it.”</i> <b>The mechanism does not support that reading.</b> A user cannot
                 choose to leave idle cash: the mandated return takes it by obligation, automatically,
                 without asking. What survives is money the user <b>put to work</b> — so this measures
                 deployment, not passive trust, and an account that payins and never trades will score
                 zero however much it trusts us. The one real choice nearby is the settlement
                 <b>frequency</b> (monthly or quarterly, REQ-307), which decides how long idle cash may
                 sit before it is swept.
                 <br><br>Either the target is really about deployment and should say so, or trust needs a
                 different measurement — <b>payin frequency</b> and <b>how long money sits between
                 settlements</b> are both closer to it, and both are observable from FMS's own movements
                 without waiting on EB-9. Until that is settled, read this as a deployment figure whatever
                 the tile is called.`
        }, {
          label: 'Not real yet',
          body: `<b>This figure is not computable yet, and the prototype does not compute it.</b>
                 Deciding whether a payin outlived its settlement date needs each account’s cycle and a
                 trading-day and settlement-holiday calendar. The calendar has no nominated source —
                 <b>EB-9 is open</b>, and REQ-307 (the mandated settlement dates) depends on it. Retention
                 is therefore modelled here as a flag, not derived from a calendar. Every other figure on
                 this page is derived from the movements; this one is the exception, and it stays flagged
                 until EB-9 closes.`
        }]
      } }
  ];
}

function kpiStrip(tiles) {
  return tiles.map((k, i) => `
    <div class="kpi ${k.tone ? 'money ' + k.tone : ''} ${
      k.target != null && k.v != null && k.v < k.target ? 'miss' : ''}">
      <div class="kl">${esc(k.label)} ${srcBadge(k.src)}
        <button class="infobtn ${state.def === i ? 'on' : ''}" data-def="${i}" type="button"
          aria-expanded="${state.def === i}"
          aria-label="What ${esc(k.label)} counts">i</button></div>
      <div class="kvrow">
        <span class="kv">${k.display != null ? k.display : pc(k.v)}</span>
        ${k.pair ? `<span class="kp">${esc(k.pair)}</span>` : ''}
      </div>
      ${k.split ? `<div class="ksplit">${k.split.map(s =>
        `<span><i>${esc(s.n)}</i> <b>${pc(s.v)}</b><small>${esc(s.amt)}</small></span>`).join('')}</div>` : ''}
      <div class="kf">${k.chip ? `<span class="vd none">${esc(k.chip)}</span>` : verdict(k.v, k.target)
        }<span class="kn">${esc(k.note)}</span></div>
    </div>`).join('');
}

/* Rendered below the strip rather than floating over it: the strip is a 1px-gap
   grid with clipped corners, and a popover would either be cut off by it or
   force the corners open. A row of its own also leaves room to answer properly
   instead of in a tooltip's worth of words. */
function defCard(k) {
  const row = (label, body) => body ? `
    <div class="defrow"><span class="dlab">${label}</span><div class="prose">${body}</div></div>` : '';
  return `
    <div class="card defcard">
      <div class="card-hd"><h2>${esc(k.label)}</h2>
        <span class="tag">definition</span>${srcBadge(k.src)}
        <button class="defx" type="button" aria-label="Close definition">×</button>
      </div>
      ${row('Counts', k.def.counts)}
      ${row('Formula', k.def.fx)}
      ${row('Out of', k.def.denom)}
      ${row('Period', k.def.period)}
      ${row('Left out', k.def.excluded)}
      ${row('Threshold', k.def.source)}
      ${(k.def.flags || []).map(f => `<div class="defrow warn">
        <span class="dlab">${esc(f.label)}</span><div class="prose">${f.body}</div></div>`).join('')}
    </div>`;
}

/* An i small enough to sit inside a row's sub-label, answering in a panel under
   the card. Same contract as everywhere else: nothing is explained in a tooltip
   that a touch device cannot open. */
const nudge = key => `<button class="infobtn xs ${state.ndef === key ? 'on' : ''}"
  data-ndef="${key}" type="button" aria-expanded="${state.ndef === key}"
  aria-label="What this counts">i</button>`;

function nudgePanel(defs) {
  const d = defs[state.ndef];
  return !d ? '' : `
    <div class="fdef">
      <div class="fdef-hd"><b>${esc(d.t)}</b>
        <button class="fdefx" data-ndef="" type="button" aria-label="Close">×</button></div>
      <div class="prose">${d.b}</div>
    </div>`;
}

/* ---------- stat cells ----------
   A percentile is not self-explanatory, and a dashboard that prints "p95" and
   moves on has assumed a reader it does not have. Each cell can carry its own
   definition behind an i, on the same pattern as the tiles and the funnel. */
function stats(cells, key) {
  const open = state.sdef && state.sdef.split(':')[0] === key ? +state.sdef.split(':')[1] : null;
  return `<div class="three-up">` + cells.map((c, i) => `
    <div class="up ${c.spark ? 'spk' : ''}">
      ${c.spark || ''}
      <span class="ul">${esc(c.l)}${c.def ? `<button class="infobtn ${open === i ? 'on' : ''}"
        data-sdef="${key}:${i}" type="button" aria-expanded="${open === i}"
        aria-label="What ${esc(c.l)} means">i</button>` : ''}</span>
      ${c.spark ? '' : `<span class="uv ${c.tone || ''}">${c.v}</span>`}
    </div>`).join('') + `</div>` + (open == null ? '' : `
    <div class="fdef">
      <div class="fdef-hd"><b>${esc(cells[open].l)}</b>
        ${cells[open].v ? `<span class="tag">${esc(cells[open].v)}</span>` : ''}
        <button class="fdefx" data-sdef="" type="button" aria-label="Close">×</button></div>
      <div class="prose">${cells[open].def}</div>
    </div>`);
}

/* ---------- funnel ----------
   Left to right, one column per stage, height carrying the count. Height is the
   only encoding: the columns are equal width, so area stays proportional and the
   shape cannot overstate a drop the way a tapering silhouette does.
   Colour carries provenance, because where the client stops and FMS starts is
   the most consequential thing about this chart. */
function funnelHtml(steps, key) {
  const live = steps.filter(s => !(state.serverOnly && s.src === 'client'));
  const top = live.length ? live[0].n : 1;
  let prev = null;
  const open = state.fdef && state.fdef.split(':')[0] === key ? +state.fdef.split(':')[1] : null;
  return `<div class="hfun">` + steps.map((s, i) => {
    const dead = state.serverOnly && s.src === 'client';
    const conv = dead || !prev ? null : rate(s.n, prev.n);
    const lost = dead || !prev ? null : prev.n - s.n;
    const h = dead ? 100 : Math.max(2, s.n / (top || 1) * 100);
    const cell = `
      <div class="hstage ${s.src} ${dead ? 'dead' : ''} ${s.leak ? 'leak' : ''}">
        <div class="hs-val">${dead ? '<span class="na">n/a</span>' : s.n.toLocaleString('en-IN')}</div>
        <div class="hs-track"><div class="hs-bar" style="height:${h.toFixed(1)}%"></div></div>
        <div class="hs-lab">${esc(s.k)}</div>
        <div class="hs-src">${srcBadge(s.src)}<button class="infobtn ${open === i ? 'on' : ''}"
          data-fdef="${key}:${i}" type="button" aria-expanded="${open === i}"
          aria-label="What ${esc(s.k)} counts">i</button></div>
        <div class="hs-conv">${conv == null ? (dead ? 'unavailable' : '&nbsp;')
          : `<b>${pc0(conv)}</b> ${s.note ? esc(s.note) : lost > 0 ? '−' + lost.toLocaleString('en-IN') : 'no drop'}`}</div>
      </div>`;
    if (!dead) prev = s;
    return cell;
  }).join('') + `</div>` + (open == null ? '' : `
    <div class="fdef">
      <div class="fdef-hd"><b>${esc(steps[open].k)}</b>${srcBadge(steps[open].src)}
        <button class="fdefx" data-fdef="" type="button" aria-label="Close">×</button></div>
      <div class="prose">${steps[open].def}</div>
    </div>`);
}

/* ==========================================================================
   TABS
   ========================================================================== */

function tabFunnel(m) {
  /* Named, not counted. "The first three steps" makes the reader go back and
     count them, and the count changes the moment a step is added. */
  const nameList = arr => arr.length < 2 ? (arr[0] || '')
    : arr.slice(0, -1).join(', ') + ' and ' + arr[arr.length - 1];
  const fromApp = nameList(m.dep.filter(s => s.src === 'client').map(s => s.k));
  const fromFms = nameList(m.dep.filter(s => s.src === 'server').map(s => s.k));

  const seam = state.serverOnly
    ? `<b>${fromApp}</b> are gone. What remains cannot say how many users saw the funds page, how many
       started a payin, or where they left — only what happened to the payments that were actually
       submitted. Every conversion rate in the product becomes a rate over a denominator FMS cannot see.`
    : `<b>${fromApp}</b> exist only if the app reports them.
       <b>${fromFms}</b> are written by FMS itself as the money moves.
       Conversion across that line is a join between two systems, and it is only as good as the id that
       spans them — which is also the id the PRD’s open question is about.`;

  return `
    ${card('Payin funnel', funnelHtml(m.dep, 'dep'), {
      tag: m.label,
      foot: `The last step never drops, only lags: money credited becomes usable as margin a median of
             <b>${dur(m.speed.p50)}</b> later. A step that loses nobody is still worth counting, because
             the delay is what the user experiences.`
    })}
    ${card('Withdrawal funnel', funnelHtml(m.wdr, 'wdr'), {
      tag: m.label,
      foot: state.serverOnly
        ? `With no client events the funnel starts at the request, which is also where FMS’s knowledge starts.
           What is lost is the denominator: how many funded users considered a withdrawal and did not make one.`
        : `The first drop is not a failure and should not be read as one. Most funded users open the funds page
           to look at it, not to take money out. The step that matters is the last one, and it is the only one
           the user experiences as waiting.`
    })}

  ${card('Where the client stops and FMS starts', `<p class="say">${seam}</p>`, {
    cls: 'seam', tag: 'provenance'
  })}

  ${card('The dead end beside the funnel', `
    <div class="two-up">
      <div><div class="big">${pc(m.deadWithdraw.share)}</div>
        <p class="say">of funded funds views ended in a tap on withdraw that could not proceed —
        <b>${m.deadWithdraw.n.toLocaleString('en-IN')}</b> in the period.</p></div>
      <div><div class="big ${m.deadWithdraw.recovered < 0.5 ? 'miss' : ''}">${pc(m.deadWithdraw.recovered)}</div>
        <p class="say">of those users opened the derivation instead of leaving. This is the whole of
        REQ-301’s bet: that a disabled control <i>with a reason</i> beats a disabled control.</p></div>
    </div>`, {
    src: 'client', cls: state.serverOnly ? 'unavail' : '',
    foot: `<b>A funded user opened the funds page, tapped withdraw, and could not proceed</b> — their
           withdrawable figure was zero, or less than they wanted. It happened
           <b>${m.deadWithdraw.n.toLocaleString('en-IN')} times</b>, ${pc(m.deadWithdraw.share)} of all
           funded funds views.
           <br><br>REQ-301 requires the control to stay visible and be disabled <i>with the responsible
           deduction named</i>, rather than hidden or left silently inert — so reaching this state is the
           product working as specified, not a defect. What the number measures is how often users meet it.
           <br><br><b>It is not a drop-off.</b> A drop-off is someone on their way somewhere who stopped;
           these users were stopped. They are mostly not the same people who went on to request a
           withdrawal, which is why this sits beside the funnel rather than inside it — as a step it would
           make the bar grow at stage three, which is how a chart lies without a single wrong number in it.
           <br><br><b>The second figure is the bet.</b> REQ-301 requires the derivation of the withdrawable
           figure to be offered as the next step, and the PRD tracks “whether the derivation was then
           opened” as the measure of whether a disabled control with a reason converts confusion into
           understanding. Below half means most people meet the wall and leave without looking at why —
           and that is the moment most likely to arrive later as a support contact.`
  })}

  ${card('What each step counts', `
    <div class="two">
      <div><div class="grp">Payins</div><div class="rules">
        ${m.dep.map(s => `<div><b>${esc(s.k)}</b> — ${s.def}</div>`).join('')}
      </div></div>
      <div><div class="grp">Withdrawals</div><div class="rules">
        ${m.wdr.map(s => `<div><b>${esc(s.k)}</b> — ${s.def}</div>`).join('')}
      </div></div>
    </div>
    <div class="grp">Across both</div>
    <div class="rules">
      <div><b>An intent</b> is one decision to move money. Retries of the same decision belong to the
        same intent — the distinction the whole Reliability tab turns on.</div>
    </div>`, {
    cls: 'rules-card',
    foot: `The same text the ⓘ on each step shows, in one place. Both are rendered from one definition,
           so a step cannot mean one thing in the chart and another in the reference.`
  })}`;
}

function tabReliability(m) {
  const th = THRESHOLDS.firstTry.target;
  const spread = Math.abs(m.success.firstTry - m.success.attemptLevel) * 100;
  const S = m.success;
  const readings = [
    { k: 'Successful attempts ÷ all attempts', v: S.attemptLevel,
      num: S.attemptWonN, den: S.attemptPool, unit: 'attempts',
      val: S.attemptWonValue, valDen: S.attemptValue,
      why: fx('successful attempts ÷ all attempts',
              `${S.attemptWonN.toLocaleString('en-IN')} ÷ ${S.attemptPool.toLocaleString('en-IN')}` +
              `  =  ${pc(S.attemptLevel)}`) +
           `Every payment instruction FMS issued counts, retries included. A user declined on UPI who
             then succeeds on net banking made <b>two attempts, one of which worked</b> — this reading
             scores them 1 of 2.
             <br><br>It is the strictest of the three, and the only one whose money figure counts a
             retried payin twice: the bank genuinely saw two instructions for the same rupees, so the
             denominator here (${Rc(S.attemptValue)}) is larger than the money actually committed.` },
    { k: 'Credited on the first attempt', v: S.firstTry,
      num: S.firstTryN, den: S.pool, unit: 'intents',
      val: S.firstTryValue, valDen: S.poolValue,
      why: fx('intents whose first attempt succeeded ÷ intents',
              `${S.firstTryN.toLocaleString('en-IN')} ÷ ${S.pool.toLocaleString('en-IN')}` +
              `  =  ${pc(S.firstTry)}`) +
           `Attempts are grouped by the decision behind them, and only the <b>first</b> one is judged.
             Retries are neither credited nor penalised — they simply do not enter the calculation.
             <br><br>This is the most literal reading of “succeed on the first try”, which is why the
             headline tile publishes it. It is also the only one of the three that sits near the
             threshold, so it is the reading on which the launch verdict actually turns.
             <br><br>It is <b>not</b> a different outcome from the reading on its right — both mean the
             money was credited. This one additionally requires that it happened on the first attempt,
             which excludes the
             <b>${(S.eventualN - S.firstTryN).toLocaleString('en-IN')}</b> payins that got there on a
             retry.`,
      lead: true, headline: true },
    { k: 'Credited eventually, after any number of attempts', v: S.eventual,
      num: S.eventualN, den: S.pool, unit: 'intents',
      val: S.eventualValue, valDen: S.poolValue,
      why: fx('intents that ended credited ÷ intents',
              `${S.eventualN.toLocaleString('en-IN')} ÷ ${S.pool.toLocaleString('en-IN')}` +
              `  =  ${pc(S.eventual)}`) +
           `Did the user end up with their money? Nothing else is asked. A payin that took three
             attempts over twenty minutes counts here exactly like one that worked instantly.
             <br><br><b>This and the reading beside it both mean “money was credited” — they differ only
             on when.</b> The gap between them is precisely the payins that needed a retry:
             <b>${(S.eventualN - S.firstTryN).toLocaleString('en-IN')}</b> in this period. A further
             ${(S.pool - S.eventualN).toLocaleString('en-IN')} were never credited at all.
             <br><br>The most generous of the three, and the closest to what the user would say
             happened — which is why it is worth showing beside the others rather than instead of them.` }
  ];

  return `
  ${card('One KPI, three readings — and they do not agree', `
    <div class="reads">${readings.map((r, i) => `
      <div class="read ${r.lead ? 'lead' : ''} ${r.v < th ? 'miss' : 'hit'}">
        <div class="rv2">${pc(r.v)}</div>
        <div class="rsub">
          <span>${Rc(r.val)} <i>of</i> ${Rc(r.valDen)}</span>
          <span>${r.num.toLocaleString('en-IN')} <i>of</i> ${r.den.toLocaleString('en-IN')} ${r.unit}</span>
        </div>
        <div class="rk2">${esc(r.k)}</div>
        <div class="rd">${verdict(r.v, th)}
          <button class="infobtn ${state.rdef === i ? 'on' : ''}" data-rdef="${i}" type="button"
            aria-expanded="${state.rdef === i}"
            aria-label="How ${esc(r.k)} is counted">i</button></div>
      </div>`).join('')}</div>
    ${state.rdef == null ? '' : `
      <div class="fdef">
        <div class="fdef-hd"><span class="tag">${pc(readings[state.rdef].v)}</span>
          <button class="fdefx" data-rdef="" type="button" aria-label="Close">×</button></div>
        <div class="prose">${readings[state.rdef].why}</div>
      </div>`}
    <div class="switches">
      <button class="sw ${state.countAbandoned ? 'on' : ''}" data-sw="countAbandoned">
        <span class="dot"></span>Count “user backed out” as a failure
      </button>
      <span class="swnote">A user who changed their mind is not a system failure — but they are an attempt
      that did not succeed. The PRD does not say which, and the choice moves the headline across the
      threshold.</span>
    </div>`, {
    src: 'server',
    foot: `The PRD asks for <i>“95% of deposit attempts succeed on the first try”</i> and never defines
           <b>attempt</b>. A user declined on UPI who retries on net banking and succeeds is, all at once:
           one attempt out of two, a failed first try, and a satisfied customer. Three defensible ways to
           count the same period, and they do not agree on whether the bar was cleared.
           <br><br><b>The tile at the top of the page shows the middle reading</b> — the closest to the PRD’s
           wording. It is not a second opinion on the same number; it is that number, and this card is
           where you find out what it leaves out and what the alternatives would have said.
           <br><br>The launch threshold is <b>95%</b>. On this period, ${
      (m.success.firstTry >= th) !== (m.success.attemptLevel >= th)
        ? 'the release passes under one reading and fails under another.'
        : spread < 0.05
        ? 'the readings happen to agree — which they will not at every period, and a definition that only matters sometimes still has to be written down.'
        : `the readings differ by ${spread.toFixed(1)} points but agree on the verdict.`
    } Nothing in the PRD picks one. That sentence has to be written before anyone can say whether the bar was cleared.`
  })}

  <div class="two">
    ${card('First-try success by payment method', bars(m.byRoute.filter(r => r.observable).map(r => ({
      label: r.n, n: r.firstTryN, frac: r.firstTry,
      display: pc(r.firstTry),
      sub: `${r.firstTryWon.toLocaleString('en-IN')} of ${r.firstTryN.toLocaleString('en-IN')} intents`,
      noteHtml: `${Rc(r.firstTryValue)} landed first time
        <b>per attempt ${nudge('per-attempt')}</b> ${pc(r.success)} ·
        ${r.successN.toLocaleString('en-IN')} of ${r.attempts.toLocaleString('en-IN')} attempts
        <b>eventually ${nudge('eventually')}</b> ${pc(r.eventual)} ·
        ${r.eventualWon.toLocaleString('en-IN')} of ${r.firstTryN.toLocaleString('en-IN')} intents`,
      tone: r.firstTry != null && r.firstTry < th ? 'warn' : 'ok'
    }))) + nudgePanel({
      'per-attempt': { t: 'Per attempt',
        b: fx('successful attempts on this method ÷ all attempts on it', 'per row, shown beside the rate') +
           `Successful attempts divided by <b>all</b> attempts on this method. A retry that fails counts
            against it, so a rail whose users retry a lot scores lower here even when everyone eventually
            gets their money.
            <br><br>The strictest of the three, and the same definition as the leftmost reading on the card
            above. Use it to ask “how often does this rail work when we press it?”` },
      'eventually': { t: 'Eventually',
        b: fx('intents starting on this method that ended credited ÷ intents starting on it',
              'per row, shown beside the rate') +
           `Of the payins that <b>started</b> on this method, the share that ended with money credited —
            however many attempts it took.
            <br><br>Always the highest of the three, and the closest to what the user would say happened.
            Use it to ask “did people get their money?”, never “is this rail reliable?” — a rail can look
            excellent here while failing badly on every single attempt.` }
    }), {
      src: 'server', tag: 'against the 95% bar',
      foot: `Three readings per method, the same three the card above applies to the whole population.
             The bar is <b>first-try</b>. <b>Per attempt</b> is successful attempts divided by all attempts,
             so a retry that fails counts against it — the strictest. <b>Eventually</b> asks only whether
             the money got there in the end, however many tries it took, and is always the highest of the
             three. Reading the wrong one understates or overstates a rail by several points.
             <br><br>Split on the method the user <b>started</b> on, so these partition the headline’s
             ${m.success.pool.toLocaleString('en-IN')} intents exactly and add back up to
             <b>${pc(m.success.firstTry)}</b>. The bar is first-try, matching the headline; the smaller
             figure beside each is the same route measured across all attempts, which is lower because
             retries fail more often than first tries. The scale is a fixed 0–100%, so the gap between
             two methods is the gap you see.`
    })}

    ${card('Whose problem it was', bars(m.byFault.map(f => ({
      label: f.label, n: f.n, pct: rate(f.n, m.failedTotal), tone: f.tone,
      note: `${Rc(f.value)} did not arrive · ${f.parts.length} ` +
            `${f.parts.length === 1 ? 'reason' : 'reasons'}`
    }))), {
      src: 'server',
      foot: `Pooling our outages with the user’s bank’s declines would make the failure rate legible and
             useless. Each row here has a different owner and a different fix — and only one of them is ours.
             <br><br>This card aggregates and stops there. Which reason dominates a group, and what each
             group is made of, is the card below — every reason is ranked there and labelled with the owner
             it rolls up to, so repeating any of it here would be two places to maintain one fact.
             <br><br>The split earns its own card because the aggregate is the thing you cannot get by
             reading a ranked list: it answers <i>who do we talk to</i>, where the list answers
             <i>what do we fix</i>.`
    })}
  </div>

  ${card('Why payins failed', bars(m.reasons.map(r => ({
    label: r.label, n: r.n, pct: r.share,
    note: `${Rc(r.value)} did not arrive · ${FAULTS[r.fault].label.toLowerCase()}`,
    tone: FAULTS[r.fault].tone
  }))), {
    src: 'server',
    tag: `${m.failedTotal.toLocaleString('en-IN')} failures · all ${m.reasons.length} reasons`,
    foot: `<b>This is the complete list, not a top slice.</b> The product can emit
           ${m.reasons.length} distinct failure reasons and every one of them is shown; a seventh would
           need a seventh code path, not a taller chart.
           <br><br>They are the outcome ids the funds flow already emits, not a taxonomy invented for
           reporting. A reason category that no code path can produce is a category nobody will ever fix —
           and one the product can produce but the dashboard omits is a failure nobody will ever see.`
  })}

  <div class="two">
    ${card('What users do after a failure', `
      <div class="rows">
        <div class="row"><div class="rk">Intents whose first attempt failed</div>
          <div class="rv">${m.retry.failedFirst.toLocaleString('en-IN')}</div></div>
        <div class="row"><div class="rk">…that were retried</div><div class="rv">${pc(m.retry.rate)}</div></div>
        <div class="row sub"><div class="rk">…of those, changed route</div>
          <div class="rv">${pc(m.retry.switched)}<small>${m.retry.switchedN.toLocaleString('en-IN')}</small></div></div>
        ${m.retry.switchDirs.map(d => `
          <div class="row sub2"><div class="rk">${esc(d.from)} → ${esc(d.to)}
            <small>${pc(d.won)} of these ended in money credited</small></div>
            <div class="rv">${pc(d.share)}<small>${d.n.toLocaleString('en-IN')}</small></div></div>`).join('')}
        <div class="row sum"><div class="rk">Retries that ended in money credited</div>
          <div class="rv pos">${pc(m.retry.won)}</div></div>
      </div>`, {
      src: 'server',
      foot: `REQ-205 offers a different route after a failure. <b>${pc(m.retry.switched)}</b> of retries take
             it, and retries succeed <b>${pc(m.retry.won)}</b> of the time — the whole argument for
             suggesting one rather than repeating the same rail.
             <br><br>The direction is broken out because the two are not the same suggestion. A user leaving
             UPI has usually hit a bank-side decline; one leaving net banking has more often hit a limit or a
             gateway. Whether each switch actually recovers the payin is shown beside it, so a suggestion
             that does not work can be found rather than assumed.`
    })}

    ${card('Credits later reversed', `
      <div class="rows">
        <div class="row"><div class="rk">Payins reversed after crediting</div>
          <div class="rv">${m.reversals.n.toLocaleString('en-IN')}<small> ${pc(m.reversals.share)}</small></div></div>
        <div class="row"><div class="rk">Value reversed</div><div class="rv">${Rc(m.reversals.value)}</div></div>
        <div class="row sum"><div class="rk">…that pushed the account into debit</div>
          <div class="rv neg">${m.reversals.causedDebit.toLocaleString('en-IN')}</div></div>
      </div>`, {
      src: 'server',
      foot: `Rare and high-consequence: money that was shown as available, spent against, and then taken back.
             Every one of these is a candidate for the “no account reaches a debit balance without being told”
             invariant.`
    })}
  </div>

  ${card('How withdrawals ended', bars(m.eod.map(o => ({
    label: o.label, n: o.n, pct: o.share, note: Rc(o.value),
    tone: o.good ? 'ok' : 'bad'
  }))), { src: 'server', tag: 'end-of-day run' })}`;
}

function tabSpeed(m) {
  const maxB = Math.max.apply(null, m.arrival.buckets.map(b => b.n).concat([1]));
  return `
  ${card('Time from payment to usable margin', `
    ${stats([
      { l: 'median', v: dur(m.speed.p50),
        def: fx('median( usable-at − instruction-issued-at ), over credited payins',
                `${m.speed.n.toLocaleString('en-IN')} values  →  ${dur(m.speed.p50)}`) +
             `<b>The median of one number per credited payin</b>: the time from the instruction FMS issued
              to the moment that money counted toward margin. Half were faster, half slower.
              <br><br>Computed over the <b>${m.speed.n.toLocaleString('en-IN')} payins credited on UPI and
              net banking</b> — one measurement each, not one per attempt. A payin that succeeded on its
              third try contributes the lag of the try that worked, not the twenty minutes the user spent.
              <br><br><b>Out of a range running ${dur(m.speed.lo)} to ${dur(m.speed.hi)}.</b> A middle means
              little without the ends it sits between: the same ${dur(m.speed.p50)} would read very
              differently if the slowest were a minute rather than ${dur(m.speed.hi)}.
              <br><br>Deliberately <b>not the average</b>: a handful of very slow credits would drag a mean
              upward until it described nobody. The median cannot be moved by an outlier, only by the
              middle of the distribution actually shifting.` },
      { l: '90th percentile', v: dur(m.speed.p90),
        def: `Nine payins in ten were faster than this; the tenth was slower — one point along a range
              running ${dur(m.speed.lo)} to ${dur(m.speed.hi)}.
              <br><br>It describes the <b>tail</b> rather than the typical case. If you want to know what
              a bad experience looks like without chasing the single worst one, this is the number — and
              it is the one worth setting a target against, because a median target lets you fail one user
              in two hundred without noticing.` },
      { l: '95th percentile', v: dur(m.speed.p95),
        def: `Nineteen payins in twenty were faster than this — closer to the worst case, which was
              ${dur(m.speed.hi)}. The fastest was ${dur(m.speed.lo)}.
              <br><br>The <b>gap between the median and this figure is the spread</b>: ${dur(m.speed.p50)}
              against ${dur(m.speed.p95)} means the slow tail runs several times longer than the typical
              wait. A tight spread means the experience is consistent; a wide one means some users are
              having a materially different day from the median.` },
      { l: 'daily median', spark: spark(m.series('speed'), { tone: 'inv' }),
        def: `One point per day, each the median for that day alone.
              <br><br>A single figure over the whole period cannot say whether the typical wait is
              <b>drifting</b>. This can: a rising line means the median is getting worse even while the
              headline number still looks acceptable.` }
    ], 'speed')}
    <div class="rows tight">${m.speed.byRoute.map(r => `
      <div class="row"><div class="rk">${esc(r.n)}<small>${r.count.toLocaleString('en-IN')} credits</small></div>
        <div class="rv">${dur(r.p50)}<small>p95 ${dur(r.p95)}</small></div></div>`).join('')}
    </div>`, {
    src: 'server', tag: `${m.speed.n.toLocaleString('en-IN')} credits · UPI and net banking`,
    foot: `Measured from the instruction FMS issued to the moment the money counted toward margin — not from
           the tap, which FMS does not see, and not to the ledger write, which the user does not feel.
           <br><br><b>Bank transfer is not in these figures, and it is the slow rail.</b> Across all
           ${m.speed.allRoutes.n.toLocaleString('en-IN')} credited payins the median is
           <b>${dur(m.speed.allRoutes.p50)}</b> rather than ${dur(m.speed.p50)} — barely different — but the
           95th percentile is <b>${dur(m.speed.allRoutes.p95)}</b> rather than ${dur(m.speed.p95)}. The
           middle of the distribution is unaffected by the exclusion; the tail is almost entirely made of
           it. Read these as the speed of the rails FMS executes, never as the speed of funding an
           account.
           <br><br><b>These percentiles show the presentation, not a diagnosis.</b> In the prototype the
           credit lag is a flat random draw — 8 to 90 seconds on UPI, 20 to 240 on net banking — so the
           p95 sits 95% of the way up a straight line and <i>nothing</i> is driving it. A real spread has
           causes worth chasing: payment-service callback latency, webhook redelivery, ledger-write
           contention at market open, bank confirmation queues. None of those is modelled here, so read
           the shape of these cards rather than the shape of the distribution.`
  })}

  ${card('From opening the funds page to money usable', state.serverOnly
    ? `<p class="say">This is a client-to-server measurement: it starts at a page view the app has to
       report and ends at a ledger write FMS makes itself. With no third-party analytics the opening
       timestamp does not exist, and the journey can only be measured from the moment a payment
       instruction was already issued — which is the half the user was not waiting through.</p>`
    : stats([
      { l: 'median, end to end', v: dur(m.journey.p50),
        def: fx('median( money-usable-at − funds-page-opened-at )',
                `${m.journey.n.toLocaleString('en-IN')} journeys  →  ${dur(m.journey.p50)}`) +
             `The median of one number per credited payin: from the moment the funds page opened to the
              moment that money counted toward margin.
              <br><br>Across <b>${m.journey.n.toLocaleString('en-IN')} credited payins on every rail</b>,
              bank transfer included. This is the only card on the tab that does include it, because the
              question here is “how long does funding an account take”, which is the user's question
              rather than ours.
              <br><br>Range: ${dur(m.journey.lo)} to ${dur(m.journey.hi)}.` },
      { l: 'the user’s half', v: dur(m.journey.think),
        def: fx('median( instruction-issued-at − funds-page-opened-at )',
                `${dur(m.journey.think)}  of  ${dur(m.journey.p50)}  =  ${pc0(m.journey.thinkShare)}`) +
             `Median time between the funds page opening and a payment instruction being issued — reading
              the balance, choosing an amount, picking a rail, and getting through the bank's
              authorisation screen.
              <br><br><b>${pc0(m.journey.thinkShare)} of the median journey is this.</b> It is not dead
              time and not obviously ours to compress, but it is the larger half, and any target set on
              our execution alone is aimed at the smaller one.` },
      { l: 'our half', v: dur(m.journey.exec),
        def: fx('median( money-usable-at − instruction-issued-at )',
                `${dur(m.journey.exec)}  of  ${dur(m.journey.p50)}  =  ${pc0(1 - m.journey.thinkShare)}`) +
             `Median time from the instruction FMS issued to the money counting toward margin — the same
              measurement as the card above, over the same journeys.
              <br><br>This is the part a faster rail or a quicker ledger write would move. It is
              ${pc0(1 - m.journey.thinkShare)} of the median wait.` },
      { l: '95th percentile', v: dur(m.journey.p95), tone: 'warn',
        def: `Nineteen journeys in twenty finished faster than this.
              <br><br>The tail is bank transfer almost entirely: a rail measured in hours sitting inside a
              distribution whose middle is measured in minutes.` }
    ]), {
    src: 'client', cls: state.serverOnly ? 'unavail' : '', tag: `${m.journey.n.toLocaleString('en-IN')} journeys`,
    foot: `<b>Most of the wait is not ours.</b> The median journey is ${dur(m.journey.p50)}, of which
           ${dur(m.journey.think)} is the user deciding and authorising and ${dur(m.journey.exec)} is FMS
           moving the money. Optimising the second without knowing the first is how a team ships a
           latency win nobody feels.
           <br><br>It needs both halves of the seam — a client page view and a server ledger write —
           so it is the first figure to disappear if the analytics question is answered no.`
  })}

  ${card('What we quoted against what happened', `
    ${stats([
      { l: 'arrived by the quoted time', v: pc(m.arrival.withinQuote),
        tone: m.arrival.withinQuote < 1 ? 'warn' : '',
        def: `The share of payouts that reached the bank at or before the time quoted at request.
              <br><br>Counted over payouts that <b>arrived within the period</b>. One still in the air is
              undecided, not late, and is excluded rather than assumed either way.` },
      { l: 'median time to spare', v: dur(m.arrival.medianEarly),
        def: `Half of payouts arrived at least this far <b>ahead</b> of their quote.
              <br><br>This is the safety margin built into the quote. A large figure is not automatically
              good news: it can mean the quoted time is padded, which trades a promise kept for a promise
              worth less.` },
      { l: 'worst overrun', v: dur(m.arrival.worst), tone: 'warn',
        def: `The single latest payout in the period, measured past the time we quoted it.
              <br><br>A maximum, not a percentile — it is one payout, and it belongs to one user. It is
              shown because a target of 100% makes the worst case the thing that breaks it, and an
              average would hide it entirely.` },
      { l: 'daily, within quote', spark: spark(m.series('arrival')),
        def: `One point per day: the share arriving by their quote that day.
              <br><br>A single bad day and a slow decline produce the same period figure. Only the shape
              tells them apart, and they need different answers — one is an incident, the other is a
              quote that has stopped being true.` }
    ], 'arrival')}
    <div class="hist">${m.arrival.buckets.map(b => `
      <div class="hb ${b.label.includes('late') ? 'late' : ''}">
        <div class="hbar" style="height:${Math.max(3, b.n / maxB * 100).toFixed(1)}%"></div>
        <span class="hn">${b.n.toLocaleString('en-IN')}</span>
        <span class="hl">${esc(b.label)}</span>
      </div>`).join('')}</div>`, {
    src: 'server', tag: `${m.arrival.n.toLocaleString('en-IN')} payouts`,
    foot: `REQ-303 computes an arrival time from account state instead of promising a constant, and the quote
           is retained at request so it can be held against reality. The bar is <b>100%</b> — a promise kept
           every time, not most times — which makes this an invariant rather than a trend. The PRD stated no
           level; this one was set on 19 Aug 26. Missing it is answered by a faster rail or a more honest
           quote, never by moving the bar.`
  })}

  ${card('How long an unknown outcome stays unknown', `
    ${stats([
      { l: 'median', v: dur(m.unknownDwell.p50),
        def: `Half of unknown outcomes resolved faster than this — the typical time a user is asked to
              wait without knowing whether their money left.
              <br><br><b>Out of a range running ${dur(m.unknownDwell.lo)} to
              ${dur(m.unknownDwell.hi)}.</b> The worst end is the one that matters: that is a user sitting
              for ${dur(m.unknownDwell.hi)} without being told whether they have been charged.` },
      { l: '90th percentile', v: dur(m.unknownDwell.p90), tone: 'warn',
        def: `Nine in ten resolved faster than this. The tenth waited longer.
              <br><br>This is the number that matters more than the median here: the whole risk of an
              unknown outcome is a user who gives up waiting and pays again.` },
      { l: 'payins affected', v: m.unknownDwell.n.toLocaleString('en-IN'),
        def: `How many payins ended in “no answer from the bank” during the period.
              <br><br>Small counts make the percentiles above them fragile, which is why the count is
              shown beside them rather than left to be looked up.` }
    ], 'dwell')}`, {
    src: 'server',
    foot: `“No answer from the bank” is its own state, not a slow failure — the user is told to wait rather than
           retry. This is how long we ask them to wait while their money is somewhere neither of us can see.`
  })}`;
}

function tabAdoption(m) {
  const clientDead = state.serverOnly;
  return `
  ${card('Average ticket and per-account activity', stats([
    { l: 'mean payin', v: Rc(m.ticket.payinMean),
      def: fx('Σ value credited ÷ number of credited payins',
              `${Rc(m.volume.credited)} ÷ ${m.volume.credits.toLocaleString('en-IN')}  =  ${Rc(m.ticket.payinMean)}`) +
           `Total money credited divided by the number of credited payins.
            <br><br>Pulled upward by a small number of very large payins, which is why it sits well above
            the median beside it. Useful for sizing float and fee revenue; misleading for anything that
            asks what a person does.` },
    { l: 'median payin', v: Rc(m.ticket.payinMedian),
      def: `The middle payin: half were smaller, half larger.
            <br><br><b>This is the one to design around.</b> Amount suggestions, minimum payins and
            fee thresholds all aim at a typical user, and the mean describes a user who does not exist.` },
    { l: 'mean payout', v: Rc(m.ticket.payoutMean),
      def: `Total money that reached a bank divided by the number of payouts that reached one.
            <br><br>Skewed the same way as the payin mean, and by the same handful of large accounts.` },
    { l: 'median payout', v: Rc(m.ticket.payoutMedian),
      def: `The middle payout. Sitting close to the median payin suggests money is moving in and out in
            similar-sized pieces rather than accumulating and leaving in one go.` },
    { l: 'movements per active account', v: m.ticket.perAccount.toFixed(2),
      def: fx('(credited payins + payouts that reached a bank) ÷ accounts that moved money',
              `(${m.volume.credits.toLocaleString('en-IN')} + ${m.arrival.n.toLocaleString('en-IN')}) ÷ ` +
              `${m.ticket.activeAccts.toLocaleString('en-IN')}  =  ${m.ticket.perAccount.toFixed(2)}`) +
           `Payins and payouts together, divided by the
            ${m.ticket.activeAccts.toLocaleString('en-IN')} accounts that moved money in this period.
            <br><br>An <b>activity rate over active accounts</b>, not a penetration rate over all
            accounts — an account that did nothing is not in the denominator, so this cannot fall when
            people go quiet. It only moves when the people still moving money move it more or less often.` },
    { l: 'credited per funded account', v: Rc(m.ticket.valuePerAccount),
      def: `Total credited divided by the accounts that had at least one credited payin in the period.
            <br><br>Reads as revenue-per-account only if you remember it is money the user still owns and
            can take back.` }
  ], 'ticket'), {
    src: 'server', tag: m.label,
    foot: `<b>The mean is ${(m.ticket.payinMean / m.ticket.payinMedian).toFixed(1)}× the median</b>, which is
           what money always does: a few very large payins drag the average far above what a typical user
           moves. Quoting the average alone would describe a customer who does not exist.
           <br><br>Per-account figures are over accounts that <b>moved money in this period</b>, not over
           every account on the books, so they are an activity rate rather than a penetration rate.`
  })}

  <div class="two">
    ${card('First payin within 7 days', `
      <div class="big ${m.adoption.firstIn7 < THRESHOLDS.firstIn7.target ? 'miss' : ''}">${pc(m.adoption.firstIn7)}</div>
      <div class="kf">${verdict(m.adoption.firstIn7, THRESHOLDS.firstIn7.target)}
        <span class="kn">${m.adoption.cohort.toLocaleString('en-IN')} accounts in the cohort</span></div>`, {
      src: 'server',
      foot: `Measured from the day the account became <i>able to receive money</i>, not the day it was opened.
             Those are different dates and the gap is not ours to claim credit for — an account blocked for
             three days has three fewer days to fund.`
    })}

    ${card('Zero-balance views that led to an action', clientDead
      ? `<div class="big na">unavailable</div>
         <p class="say">This is a client event. With no third-party analytics, FMS knows a payin began only
         once a payment instruction exists — by which point the user has already acted. The share who saw the
         empty state and did nothing is unobservable.</p>`
      : `<div class="big ${m.adoption.zeroActed < THRESHOLDS.zeroActed.target ? 'miss' : ''}">${pc(m.adoption.zeroActed)}</div>
         <div class="kp">the action is <b>starting a payin</b> — it is the only one offered</div>
         <div class="kf">${verdict(m.adoption.zeroActed, THRESHOLDS.zeroActed.target)}
           <span class="kn">${m.adoption.zeroStarted.toLocaleString('en-IN')} of
           ${m.adoption.zeroVisits.toLocaleString('en-IN')} zero-balance views</span></div>
         <div class="ksplit" style="margin-top:12px">
           <span><i>used the suggested amount</i><b>${pc(m.adoption.zeroSuggested)}</b>
             <small>of those who acted</small></span>
           <span><i>typed their own</i><b>${pc(1 - m.adoption.zeroSuggested)}</b>
             <small>of those who acted</small></span>
         </div>`, {
      src: 'client', cls: clientDead ? 'unavail' : '',
      foot: `<b>“An action” is one thing: starting a payin.</b> The empty state is required to present the
             funding action as the <i>primary</i> action available, so there is no second thing to count —
             a user either began funding the account or left. Starting is the bar, not finishing; whether
             they got the money in is the funnel's question, not this one.
             <br><br>The split beneath the figure separates <b>the suggestion working from the button
             working</b>. The empty state must also state the smallest amount that is useful to deposit,
             and a user who takes that figure was helped by it, while one who types their own was only
             helped past it. The PRD tracks the two separately on purpose: suggested amounts are excluded
             from anchoring by deliberate decision, and separating them is what lets that trade be
             evaluated with data rather than reversed on instinct.
             <br><br>An account with no money in it is the state every user starts in, and the one they
             return to after taking everything out. The page can present that emptiness as a fact — a column of
             zeroes and a flat chart — or as one clear next step with the smallest useful amount already
             suggested.
             <br><br>This number is how often the second version works: the share of people who met an
             empty account and did something about it rather than closing the app. Below half means the
             empty state is behaving as a wall rather than a doorway, and every funnel measured downstream
             of it is being fed by a leak.`
    })}
  </div>

  <div class="two">
    ${card('Payins per funded account, per quarter', `
      <div class="kvrow">
        <span class="big ${m.adoption.perQuarterMedian < THRESHOLDS.perQuarter.target ? 'miss' : ''}"
          style="margin-bottom:0">${m.adoption.perQuarterMedian}</span>
        <span class="kp">mean ${m.adoption.perQuarterMean.toFixed(2)} — counts, unlike amounts, have no
          long tail, so the two agree</span>
      </div>
      <div class="kf" style="margin:8px 0 14px">${m.adoption.perQuarterMedian >= THRESHOLDS.perQuarter.target
        ? '<span class="vd ok">meets 2</span>' : '<span class="vd no">below 2</span>'}
        <span class="kn">median across ${m.adoption.fundedAccts.toLocaleString('en-IN')} funded accounts</span></div>
      ${bars(m.adoption.perQuarterDist.map(d => ({
        label: d.k, n: d.n, pct: rate(d.n, m.adoption.fundedAccts),
        tone: d.k === '1 payin' ? 'warn' : 'ok'
      })))}`, {
      src: 'server', tag: 'fixed 90 days',
      foot: `<b>How many separate times a funded account put money in over the last 90 days.</b> The median
             account did it twice — half did more, half did fewer.
             <br><br>It counts <b>payins, not amounts</b>: two payins of ₹5,000 count the same as two of
             ₹5,00,000. The question is whether an account is used again, not how much it holds. Someone
             who funds once and never returns has a different relationship with the product than someone
             who tops up every month, even where the two paid in the same total — and only one of them
             is a habit.
             <br><br><b>The median clears the bar and hides the shape.</b>
             ${pc(m.adoption.onceOnly)} of funded accounts —
             ${m.adoption.perQuarterDist[0].n.toLocaleString('en-IN')} of them — paid in <b>exactly once</b>
             all quarter and never came back. A median of ${m.adoption.perQuarterMedian} passes the
             threshold while close to half the funded base is one-and-done, which is why the distribution
             is drawn rather than summarised.
             <br><br><b>Held at 90 days whatever the period selector says</b>, because the target is stated
             per quarter. Cut to a week it would look like the same metric and mean something else
             entirely: most accounts make no payin in any given week, so the median would read zero with
             nothing whatsoever wrong.`
    })}

    ${card('Payin money still there past settlement', `
      <div class="big ${m.adoption.retainedShare < THRESHOLDS.retained.target ? 'miss' : ''}">${pc(m.adoption.retainedShare)}</div>
      <div class="kf">${verdict(m.adoption.retainedShare, THRESHOLDS.retained.target)}
        <span class="kn">of ${Rc(m.adoption.creditedValue)} credited in the period</span></div>`, {
      src: 'server',
      foot: `Money survives a mandated settlement date only by being <b>committed</b> — blocked as margin
             against an open position, pledged, or reserved against a pending obligation. A free credit
             balance does not survive it: the mandated return takes unused funds automatically, without
             asking, so a user cannot choose to leave idle cash sitting.
             <br><br>That makes this a measure of <b>deployment, not trust</b>, whatever the PRD calls it.
             An account that funds, trusts us completely and never trades scores zero here. The tile at the
             top of the page carries the same definition in full, along with why the figure is not yet
             computable at all.`
    })}
  </div>

  ${card('Volume behind every rate on this page', `
    <div class="rows">
      <div class="row"><div class="rk">Money credited</div><div class="rv pos">${Rc(m.volume.credited)}</div></div>
      <div class="row"><div class="rk">Money paid out</div><div class="rv neg">${Rc(m.volume.paidOut)}</div></div>
      <div class="row"><div class="rk">Payin intents</div><div class="rv">${m.volume.intents.toLocaleString('en-IN')}</div></div>
      <div class="row sub"><div class="rk">payment attempts behind them</div><div class="rv">${m.volume.attempts.toLocaleString('en-IN')}</div></div>
      <div class="row"><div class="rk">Withdrawal requests</div><div class="rv">${m.volume.payouts.toLocaleString('en-IN')}</div></div>
      <div class="row sum"><div class="rk">Accounts that moved money</div><div class="rv">${m.volume.accounts.toLocaleString('en-IN')}</div></div>
    </div>`, {
    src: 'server', tag: m.label,
    foot: `A rate with no denominator on the page is a rate nobody can challenge. Sample sizes are shown on every
           metric here for the same reason.`
  })}`;
}

/* ==========================================================================
   BOOT
   ========================================================================== */

const TABS = [
  { id: 'funnel',      label: 'Funnel',      fn: tabFunnel },
  { id: 'reliability', label: 'Reliability', fn: tabReliability },
  { id: 'speed',       label: 'Speed',       fn: tabSpeed },
  { id: 'adoption',    label: 'Adoption',    fn: tabAdoption }
];

const EMPTY = m => `
  ${card('No money moved in this range', `
    <p class="say">Nothing was credited and nothing was paid out between
    <b>${dateOf(m.from)}</b> and <b>${dateOf(m.to)}</b>. The prototype holds
    ${SPAN} days of movements ending ${dateOf(NOW)}; a range outside that window is empty
    rather than wrong.</p>`, { cls: 'seam' })}`;

function render() {
  const w = window_();
  const m = metrics({ from: w.from, to: w.to, label: w.label,
                      countAbandoned: state.countAbandoned });
  const tiles = kpiTiles(m);
  document.getElementById('kpis').innerHTML = kpiStrip(tiles);
  const dp = document.getElementById('defPanel');
  dp.innerHTML = state.def == null ? '' : defCard(tiles[state.def]);
  dp.hidden = state.def == null;
  document.getElementById('panel').innerHTML =
    m.empty ? EMPTY(m) : (TABS.find(t => t.id === state.tab) || TABS[0]).fn(m);
  document.querySelectorAll('[data-tab]').forEach(b =>
    b.classList.toggle('on', b.dataset.tab === state.tab));
  document.querySelectorAll('[data-period]').forEach(b =>
    b.classList.toggle('on', b.dataset.period === state.period));
  const rng = document.getElementById('range');
  rng.hidden = state.period !== 'custom';
  const rf = document.getElementById('rFrom'), rt = document.getElementById('rTo');
  rf.min = rt.min = iso(DATA_FROM); rf.max = rt.max = iso(DATA_TO);
  rf.value = state.cFrom; rt.value = state.cTo;
  document.getElementById('srvOnly').classList.toggle('on', state.serverOnly);
  document.body.classList.toggle('server-only', state.serverOnly);
}

function boot() {
  const nav = document.getElementById('tabs');
  nav.innerHTML = TABS.map(t =>
    `<button data-tab="${t.id}" type="button">${t.label}</button>`).join('');
  const per = document.getElementById('periods');
  per.innerHTML = PERIODS.map(p =>
    `<button data-period="${p.id}" type="button">${p.label}</button>`).join('');

  document.getElementById('asOf').textContent = 'as of ' + dateOf(NOW);

  document.addEventListener('click', e => {
    const t = e.target.closest('[data-tab]');
    if (t) { state.tab = t.dataset.tab; state.cdef = state.fdef = state.rdef = state.sdef = null;
             return render(); }
    const d = e.target.closest('[data-period]');
    if (d) { state.period = d.dataset.period; state.def = null; return render(); }
    const s = e.target.closest('[data-sw]');
    if (s) { state[s.dataset.sw] = !state[s.dataset.sw]; return render(); }
    const i = e.target.closest('[data-def]');
    if (i) { const n = +i.dataset.def; state.def = state.def === n ? null : n; return render(); }
    const f = e.target.closest('[data-fdef]');
    if (f) { const v = f.dataset.fdef; state.fdef = !v || state.fdef === v ? null : v; return render(); }
    const nd = e.target.closest('[data-ndef]');
    if (nd) { const v = nd.dataset.ndef; state.ndef = !v || state.ndef === v ? null : v; return render(); }
    const cd = e.target.closest('[data-cdef]');
    if (cd) { const v = cd.dataset.cdef; state.cdef = state.cdef === v ? null : v; return render(); }
    const sd = e.target.closest('[data-sdef]');
    if (sd) { const v = sd.dataset.sdef; state.sdef = !v || state.sdef === v ? null : v; return render(); }
    const rd = e.target.closest('[data-rdef]');
    if (rd) { const v = rd.dataset.rdef;
      state.rdef = v === '' || state.rdef === +v ? null : +v; return render(); }
    if (e.target.closest('.defx')) { state.def = null; return render(); }
    if (e.target.closest('#srvOnly')) { state.serverOnly = !state.serverOnly; return render(); }
  });

  document.addEventListener('change', e => {
    if (e.target.id === 'rFrom') { state.cFrom = e.target.value; render(); }
    if (e.target.id === 'rTo')   { state.cTo = e.target.value; render(); }
  });

  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    if (state.def != null || state.fdef != null || state.rdef != null
        || state.sdef != null || state.cdef != null || state.ndef != null) {
      state.def = state.fdef = state.rdef = state.sdef = state.cdef = state.ndef = null; render();
    }
  });

  render();
}

if (typeof document !== 'undefined' && document.getElementById) boot();
