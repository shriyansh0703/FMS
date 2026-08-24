/* Self-test for the money-movement dashboard.
   dashboard.js does not touch the DOM until boot(), and boot() only runs where
   a document exists, so the whole metrics layer can be required straight into
   node and asserted against.

   What is checked here is not "does the number look right" — the population is
   synthetic, so any number is as right as the seed. It is the identities that
   have to hold whatever the data says, because those are the ones a real
   dataset would break silently. */

const D = require('./dashboard.js');
const fs = require('fs');

let fails = 0;
const is = (label, cond, detail) => {
  if (!cond) fails++;
  console.log((cond ? '  PASS  ' : '  FAIL  ') + label + (cond || !detail ? '' : '   ' + detail));
};
const near = (label, a, b, tol) => is(label, Math.abs(a - b) <= tol, `got ${a}, wanted ${b}`);
const pc = v => v == null ? 'null' : (v * 100).toFixed(2) + '%';

const M30 = D.metrics({ days: 30, countAbandoned: false });
const M30a = D.metrics({ days: 30, countAbandoned: true });
const M7 = D.metrics({ days: 7, countAbandoned: false });
const M90 = D.metrics({ days: 90, countAbandoned: false });

console.log('\n=== the page is reproducible ===');
/* Comments stripped first: the file talks ABOUT the wall clock and about
   Math.random in prose, and a check that cannot tell code from commentary
   would fail on its own explanation. */
const CODE = fs.readFileSync('dashboard.js', 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
is('No Math.random anywhere in the dashboard', !/Math\.random/.test(CODE));
is('No wall clock anywhere in the dashboard', !/Date\.now|new Date\(\s*\)/.test(CODE));
is('The same period computes the same figure twice',
   D.metrics({ days: 30, countAbandoned: false }).success.firstTry === M30.success.firstTry);

console.log('\n=== a funnel may never grow ===');
const monotone = (steps, name) => steps.forEach((s, i) => {
  if (!i) return;
  is(`${name}: "${s.k}" ≤ "${steps[i - 1].k}"`, s.n <= steps[i - 1].n,
     `${s.n} > ${steps[i - 1].n}`);
});
monotone(M30.dep, 'deposits');
monotone(M30.wdr, 'withdrawals');
is('the withdrawal funnel ends on the same rate the headline tile reports',
   Math.abs(M30.wdr[4].n / M30.wdr[3].n - M30.arrival.withinQuote) < 1e-9,
   `funnel ${(M30.wdr[4].n / M30.wdr[3].n * 100).toFixed(1)}% vs tile ${(M30.arrival.withinQuote * 100).toFixed(1)}%`);

console.log('\n=== the three readings stand in a fixed order ===');
is('attempt-level ≤ first-try', M30.success.attemptLevel <= M30.success.firstTry,
   `${pc(M30.success.attemptLevel)} > ${pc(M30.success.firstTry)}`);
is('first-try ≤ eventual (an identity, not a coincidence)',
   M30.success.firstTry <= M30.success.eventual);
is('counting abandonment as failure can only lower the figure',
   M30a.success.firstTry <= M30.success.firstTry,
   `${pc(M30a.success.firstTry)} > ${pc(M30.success.firstTry)}`);
is('and it changes it — otherwise the switch is decoration',
   M30a.success.firstTry < M30.success.firstTry);

console.log('\n=== each reading publishes a numerator that makes its own percentage ===');
const S = M30.success;
is('attempt-level numerator ÷ denominator = the rate shown',
   Math.abs(S.attemptWonN / S.attemptPool - S.attemptLevel) < 1e-9);
is('first-try numerator ÷ denominator = the rate shown',
   Math.abs(S.firstTryN / S.pool - S.firstTry) < 1e-9);
is('eventual numerator ÷ denominator = the rate shown',
   Math.abs(S.eventualN / S.pool - S.eventual) < 1e-9);
is('money credited never exceeds money committed, in every reading',
   S.attemptWonValue <= S.attemptValue && S.firstTryValue <= S.poolValue
   && S.eventualValue <= S.poolValue);

is('credited-eventually is credited-on-first-attempt plus the retries that worked',
   S.eventualN >= S.firstTryN && S.eventualN <= S.pool,
   `${S.firstTryN} first, ${S.eventualN} eventual, ${S.pool} pool`);

console.log('\n=== the demonstration the Reliability tab is built on ===');
is('the launch threshold is met under one reading and missed under another',
   M30.success.firstTry >= 0.95 && M30.success.attemptLevel < 0.95,
   `first-try ${pc(M30.success.firstTry)}, attempt-level ${pc(M30.success.attemptLevel)}`);
is('and abandonment alone flips the verdict',
   M30a.success.firstTry < 0.95);

console.log('\n=== every breakdown accounts for its whole ===');
near('failure reasons sum to the failure count',
     M30.reasons.reduce((s, r) => s + r.n, 0), M30.failedTotal, 0);
near('fault attribution sums to the failure count',
     M30.byFault.reduce((s, f) => s + f.n, 0), M30.failedTotal, 0);
is('every fault group has a leading reason, and it is the largest one',
   M30.byFault.every(f => f.parts.length && f.parts.every(p => p.n <= f.parts[0].n)));
is('every fault group decomposes into reasons that sum back to it',
   M30.byFault.every(f => f.parts.reduce((s, p) => s + p.n, 0) === f.n),
   M30.byFault.map(f => `${f.id} ${f.parts.reduce((s,p)=>s+p.n,0)}/${f.n}`).join('; '));
near('fault attribution sums to the same money the reasons do',
     M30.byFault.reduce((s, f) => s + f.value, 0),
     M30.reasons.reduce((s, r) => s + r.value, 0), 0);
near('reason shares sum to 1', M30.reasons.reduce((s, r) => s + r.share, 0), 1, 1e-9);
near('payout outcomes sum to the requests',
     M30.eod.reduce((s, o) => s + o.n, 0), M30.volume.payouts, 0);
near('arrival buckets sum to the payouts that arrived',
     M30.arrival.buckets.reduce((s, b) => s + b.n, 0), M30.arrival.n, 0);

console.log('\n=== the method split adds back up to the headline ===');
const obs = M30.byRoute.filter(r => r.observable);
near('per-method intents sum to the headline population',
     obs.reduce((s, r) => s + r.firstTryN, 0), M30.success.pool, 0);
is('the value-weighted split reproduces the headline rate',
   Math.abs(obs.reduce((s, r) => s + r.firstTry * r.firstTryN, 0) / M30.success.pool
            - M30.success.firstTry) < 1e-9);
near('per-method credited value sums to the money-credited headline',
     M30.byRoute.reduce((s, r) => s + r.creditedValue, 0), M30.volume.credited, 0);
near('per-method credit counts sum to the payin count',
     M30.byRoute.reduce((s, r) => s + r.creditedN, 0), M30.volume.credits, 0);
is('the first-try amount never exceeds what the pool was worth',
   M30.success.firstTryValue <= M30.success.poolValue,
   `${M30.success.firstTryValue} > ${M30.success.poolValue}`);
near('per-method first-try value sums to the headline amount',
     obs.reduce((s, r) => s + r.firstTryValue, 0), M30.success.firstTryValue, 0);
is('every per-method numerator divides to the rate printed beside it',
   obs.every(r => Math.abs(r.successN / r.attempts - r.success) < 1e-9
                && Math.abs(r.firstTryWon / r.firstTryN - r.firstTry) < 1e-9
                && Math.abs(r.eventualWon / r.firstTryN - r.eventual) < 1e-9));
is('per method the three readings keep their order: per-attempt <= first-try <= eventually',
   obs.every(r => r.success <= r.firstTry && r.firstTry <= r.eventual),
   obs.map(r => `${r.id} ${(r.success*100).toFixed(1)}/${(r.firstTry*100).toFixed(1)}/${(r.eventual*100).toFixed(1)}`).join('; '));
is('first-try is at least as high as all-attempts, per method',
   obs.every(r => r.firstTry >= r.success),
   obs.map(r => `${r.id} ${(r.firstTry * 100).toFixed(2)} vs ${(r.success * 100).toFixed(2)}`).join('; '));

console.log('\n=== the route we cannot measure is excluded, not guessed at ===');
is('the self-service route reports no success rate',
   M30.byRoute.find(r => r.id === 'neft').success === null);
is('handoffs are never fewer than the credits they produced',
   M30.selfServe.handoffs >= M30.selfServe.credits);
is('unaccounted is exactly the difference',
   M30.selfServe.unaccounted === M30.selfServe.handoffs - M30.selfServe.credits);
is('no unobservable attempt reaches the success denominator',
   D.DATA.attempts.filter(a => !a.observable).every(a => a.route === 'neft'));

console.log('\n=== each two-way breakdown accounts for its whole ===');
near('on time + late = payouts that arrived',
     M30.arrival.onTimeN + M30.arrival.lateN, M30.arrival.n, 0);
near('and their shares sum to 1',
     M30.arrival.withinQuote + M30.arrival.lateShare, 1, 1e-9);
near('stayed + left = credited deposits', 
     M30.adoption.retainedN + M30.adoption.leftN, M30.volume.credits, 0);
near('stayed + left = money credited',
     M30.adoption.retainedValue + M30.adoption.leftValue, M30.adoption.creditedValue, 0);
is('the retained share is value-weighted, not a count share — the two differ',
   Math.abs(M30.adoption.retainedShare
            - M30.adoption.retainedN / (M30.adoption.retainedN + M30.adoption.leftN)) > 1e-6);

is('same-day sends are a subset of everything sent',
   M30.sameDay.n <= M30.sameDay.of && M30.sameDay.share <= 1);

is('route switches account for every retry that changed rail',
   M30.retry.switchDirs.reduce((s, d) => s + d.n, 0) === M30.retry.switchedN,
   `${M30.retry.switchDirs.map(d => d.from + '->' + d.to + ' ' + d.n).join(', ')} vs ${M30.retry.switchedN}`);

console.log('\n=== percentiles are ordered ===');
is('p50 ≤ p90 ≤ p95', M30.speed.p50 <= M30.speed.p90 && M30.speed.p90 <= M30.speed.p95);
is('unknown-outcome dwell p50 ≤ p90', M30.unknownDwell.p50 <= M30.unknownDwell.p90);
is('every route reports a slower p95 than p50',
   M30.speed.byRoute.every(r => !r.count || r.p50 <= r.p95));

is('excluding the self-service rail moves the tail far more than the middle',
   Math.abs(M30.speed.allRoutes.p50 - M30.speed.p50) < Math.abs(M30.speed.allRoutes.p95 - M30.speed.p95),
   `median ${M30.speed.p50}->${M30.speed.allRoutes.p50}, p95 ${M30.speed.p95}->${M30.speed.allRoutes.p95}`);
is('the percentiles sit inside the range they describe',
   M30.speed.lo <= M30.speed.p50 && M30.speed.p95 <= M30.speed.hi,
   `${M30.speed.lo} <= ${M30.speed.p50} .. ${M30.speed.p95} <= ${M30.speed.hi}`);
is('and so does the unknown-outcome dwell',
   M30.unknownDwell.lo <= M30.unknownDwell.p50 && M30.unknownDwell.p90 <= M30.unknownDwell.hi);

console.log('\n=== an unmeasured metric is never rendered as a zero ===');
is('rate() on an empty denominator is null, not 0', D.rate(0, 0) === null);
is('a percentile of nothing is null', D.pctile([], 0.5) === null);

is('the zero-state suggestion split covers only those who acted',
   M30.adoption.zeroStarted <= M30.adoption.zeroVisits
   && M30.adoption.zeroSuggested >= 0 && M30.adoption.zeroSuggested <= 1);

console.log('\n=== narrowing the period narrows the population ===');
is('7 days ⊆ 30 days ⊆ 90 days',
   M7.volume.intents <= M30.volume.intents && M30.volume.intents <= M90.volume.intents);
is('a quarterly KPI ignores the period selector',
   M7.adoption.perQuarterMedian === M90.adoption.perQuarterMedian);
is('a seven-day cohort still exists at the shortest period',
   M7.adoption.cohort > 0 && M7.adoption.firstIn7 != null);

is('the two "accounts that moved money" figures are the same set',
   M30.volume.accounts === M30.ticket.activeAccts,
   `volume ${M30.volume.accounts} vs ticket ${M30.ticket.activeAccts}`);

near('the per-quarter distribution accounts for every funded account',
     M30.adoption.perQuarterDist.reduce((s, d) => s + d.n, 0), M30.adoption.fundedAccts, 0);

console.log('\n=== money is counted, not estimated ===');
const creditedByHand = D.DATA.intents
  .filter(i => i.credited && i.t >= D.NOW - 30 * D.DAY && i.t <= D.NOW)
  .reduce((s, i) => s + i.paise, 0);
near('credited value is the sum of the credited deposits',
     M30.volume.credited, creditedByHand, 0);
is('every amount is an integer number of paise',
   D.DATA.intents.every(i => Number.isInteger(i.paise)));
is('money paid out and the payout count describe the same set',
   (M30.volume.paidOut > 0) === (M30.arrival.n > 0));
const today = D.metrics({ from: D.NOW - D.DAY / 2, to: D.NOW, countAbandoned: false });
is('and still agree on a half-day window, where most payouts are undecided',
   (today.volume.paidOut > 0) === (today.arrival.n > 0),
   `${today.volume.paidOut} paise across ${today.arrival.n} payouts`);

console.log('\n=== thresholds come from the PRD, and only where it states one ===');
is('quoted-versus-actual is held at 100% — a promise kept every time, not most times',
   D.THRESHOLDS.arrival.target === 1);
is('and at 100% it behaves as an invariant: any late payout reads as below',
   M30.arrival.withinQuote < 1 === (M30.arrival.buckets.slice(2).reduce((s, b) => s + b.n, 0) > 0));
is('first-try success is held at 95%', D.THRESHOLDS.firstTry.target === 0.95);
is('funding within 7 days is held at 80%', D.THRESHOLDS.firstIn7.target === 0.80);

console.log('\n' + (fails ? `  ${fails} FAILED\n` : '  all assertions passed\n'));
process.exit(fails ? 1 : 0);
