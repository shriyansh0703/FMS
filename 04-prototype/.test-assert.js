
/* ================= assertions ================= */
let fails=0;
const eq=(label,got,want)=>{ const ok=got===want; if(!ok)fails++;
  console.log((ok?'  PASS  ':'  FAIL  ')+label.padEnd(48)+String((got/100).toFixed(2)).padStart(13)+(ok?'':'   expected '+(want/100).toFixed(2))); };
const is=(label,cond)=>{ if(!cond)fails++; console.log((cond?'  PASS  ':'  FAIL  ')+label); };

console.log('\n=== ACTIVE TRADER — a real trading day ===');
let a=SCENARIOS.trader(), d=derive(a);
eq('Opening balance', d.openingBalance, P(125000));
eq('Payin today (shown, never counted)', d.payinToday, P(25000));
eq('Payout today', d.payoutToday, P(-12000));
eq("Today's trading and charges", d.todayOther, P(28860));
eq('Ledger balance', d.ledger, P(166860));
eq('Total collateral', d.collateral, P(225000));
eq('MARGIN AVAILABLE (top card)', d.availableMargin, P(308260));
eq('Unrealised ledger (raises margin, not cash)', d.unrealised, P(6200));
eq('MARGIN BLOCKED (top card)', d.usedMargin, P(96200));
eq('Net option premium is a credit, not margin used', d.optPremium, P(6400));
is('Option premium is excluded from margin used',
   d.usedMargin === a.used.span + a.used.exposure + a.used.delivery + a.used.orderBlocks);
eq('Available cash', d.availableCash, P(118760));
eq('WITHDRAWABLE (top card)', d.withdrawable, P(53080));

console.log('\n=== the identities that must never break ===');
is('Ledger is summed from entries, never stored (Rule L1)',
   d.ledger === a.entries.filter(e=>e.status==='done').reduce((s,e)=>s+e.amt,0));
is('opening + payin + payout + today = ledger',
   d.openingBalance + d.payinToday + d.payoutToday + d.todayOther === d.ledger);
eq('Counted terms reconcile to the figure',
   d.terms.filter(t=>!t.excluded).reduce((s,t)=>s+t.v,0), d.withdrawable);
is('Exactly one term is excluded from the sum', d.terms.filter(t=>t.excluded).length===1);
is('Withdrawable never exceeds the ledger balance', d.withdrawable <= d.ledger);
is('Available margin identity still holds in the model',
   d.ledger + a.collateral.equity + a.collateral.liquid + d.unrealised + d.optPremium - d.usedMargin === d.availableMargin);
is('Unrealised ledger never reaches withdrawable',
   derive(Object.assign({}, a, {unrealisedLedger: P(99999)})).withdrawable === d.withdrawable);
eq('Matrix totals equal used margin',
   d.matrix.posCash+d.matrix.posCollat+d.matrix.ordCash+d.matrix.ordCollat, d.usedMargin);

console.log('\n=== the agreed labels ===');
const names = d.terms.map(t=>t.n);
['Opening balance','Payin Today','Payout','Today’s trading and charges',
 'Margin set aside for your open positions','Pledged margin','Delivery sell benefit',
 'Unposted charges'].forEach((n,i)=>
   is('term '+(i+1)+' is "'+n+'"', names[i]===n));

console.log('\n=== Rule B4 — money added today is NOT withdrawable ===');
const before=d.withdrawable;
a.entries.push(E(0,'Funds added',P(20000),'payin',{}));
d=derive(a);
eq('Ledger rose by 20,000', d.ledger, P(186860));
eq('Margin available rose by 20,000', d.availableMargin, P(328260));
eq('Opening balance is untouched', d.openingBalance, P(125000));
eq('WITHDRAWABLE is unchanged', d.withdrawable, before);

console.log('\n=== an open request does NOT reduce withdrawable ===');
// The money stays tradable until end of day, so the number cannot pretend it
// has already gone. What stops a second request is the one-at-a-time rule.
a=SCENARIOS.trader(); const w0=derive(a).withdrawable;
a.entries.push(Object.assign(E(0,'Withdrawal requested',P(-7000),'payout',{status:'pending'}),
  {_amt:P(7000), _requested:P(7000)}));
eq('Withdrawable is unchanged by an open request', derive(a).withdrawable, w0);
is('Available margin is unchanged too',
   derive(a).availableMargin === derive(SCENARIOS.trader()).availableMargin);
is('No derivation term mentions a reservation',
   !derive(a).terms.some(t=>/pending|set aside for a withdrawal/i.test(t.n+t.x)));

console.log('\n=== Rule A5 — money in flight touches no balance ===');
a=SCENARIOS.trader(); const base=derive(a).ledger, pend=derive(a).pending.length;
a.entries.push(E(0,'Funds added',P(50000),'payin',{status:'pending'}));
eq('Ledger unchanged while pending', derive(a).ledger, base);
is('In-flight item surfaced separately', derive(a).pending.length===pend+1);

console.log('\n=== MARGIN SHORTFALL ===');
a=SCENARIOS.shortfall(); d=derive(a);
eq('Margin available is NEGATIVE and not clamped (Rule B9)', d.availableMargin, P(-1600));
eq('Margin used', d.usedMargin, P(254000));
eq('Withdrawable clamped to zero', d.withdrawable, 0);
is('Raw sum kept for honest display: '+(d.raw/100).toFixed(2), d.clamped && d.raw < 0);
is('Shortfall present: '+R(a.shortfall), a.shortfall>0);

console.log('\n=== IN DEBT — the dormant-account path ===');
a=SCENARIOS.debt(); d=derive(a);
eq('Ledger balance is negative', d.ledger, P(-24.37));
eq('Withdrawable clamped to zero', d.withdrawable, 0);
is('inDebt flag set', d.inDebt);
is('Reversal is paired to its original',
   a.entries.filter(e=>e.reverses).length===1 && a.entries.filter(e=>e.reversedBy).length===1);
is('No entry is ever mutated away (Rule L2)', a.entries.length===16);

console.log('\n=== EMPTY / BLOCKED ===');
d=derive(SCENARIOS.empty());
eq('Empty: three figures sum to zero', d.ledger+d.availableMargin+d.withdrawable, 0);
const bl=SCENARIOS.blocked();
is('Blocked: cannot receive, blocker named', bl.canReceive===false && !!bl.blocker.action);

console.log('\n=== FULL RENDER — every scenario, both panels, plus the drawer ===');
Object.keys(SCENARIOS).forEach(k=>{
  try{ setScenario(k); openDerivation(); state.act='out'; render(); state.act='add'; render(); is('renders clean: '+k, true); }
  catch(e){ is('renders clean: '+k+' -> '+e.message, false); }
});


/* ===== communications — product-requirements-communications.md ===== */
console.log('\n=== COMMUNICATIONS ===');
(function () {
  const ok = (cond, label) => is(label, cond);
  const trailer = /Ref: [A-Z0-9]+ -Thinq$/;                       // Rule C14, byte for byte
  const all = [];
  ['trader', 'shortfall', 'debt', 'empty', 'blocked'].forEach(k => {
    const vs = VARIANTS[k] ? Object.keys(VARIANTS[k]) : [null];
    vs.forEach(v => {
      const a = SCENARIOS[k](v), d = derive(a);
      commsFor(a, d).concat(commsForMovement(a))
        .filter(m => m.ch === 'SMS').forEach(m => all.push([k + '/' + (v || '-'), m]));
    });
  });

  ok(all.length > 20, 'SMS generated across every scenario (' + all.length + ')');

  let bad = [];
  all.forEach(([tag, m]) => {
    const t = m.body, k7 = smsMetrics(t);
    if (!trailer.test(t))      bad.push('trailer: ' + tag);
    if (t.indexOf('\u20b9') > -1) bad.push('rupee sign: ' + tag);
    if (k7.enc !== 'GSM-7')    bad.push('not GSM-7: ' + tag);
    if (k7.segments !== 1)     bad.push('multi-segment (' + k7.units + '): ' + tag + ' \u2014 ' + m.rung);
  });
  ok(!bad.length, 'Rule C14 trailer, no \u20b9, GSM-7, one segment \u2014 all ' + all.length
     + ' messages' + (bad.length ? '  [' + bad.join('; ') + ']' : ''));

  // REQ-621 — a message and the screen cannot disagree about an amount
  const sa = SCENARIOS.shortfall('mtm');
  const ssms = commsFor(sa, derive(sa)).find(m => m.ch === 'SMS').body;
  ok(ssms.indexOf('Rs ' + R(sa.shortfall).slice(1)) > -1,
     'REQ-621 shortfall SMS amount === derive()');

  const da = SCENARIOS.debt('depository'), dd = derive(da);
  const dsms = commsFor(da, dd).find(m => m.ch === 'SMS' && /due/.test(m.body)).body;
  ok(dsms.indexOf('Rs ' + R(-dd.ledger).slice(1)) > -1,
     'REQ-621 dues SMS amount === derive()');

  // REQ-621 — by the time the ladder clears, derive() says zero, so an outcome
  // message that quoted a figure would be quoting a stale one
  ok(!/\d[\d,]*\.\d\d/.test(SMS.shortfallCleared('MS8841')),
     'REQ-621 cleared SMS quotes no amount');

  // Rule C2 / REQ-616 — who gets an SMS and who does not
  const tr = SCENARIOS.trader();
  const cancelled = tr.entries.find(e => e.kind === 'payout' && e.status === 'cancelled');
  ok(!!cancelled && commsForEntry(cancelled).ch !== 'SMS',
     'REQ-616 cancelled withdrawal sends no SMS');

  // \u00a76 \u2014 fund addition leaves SMS entirely
  const payins = tr.entries.filter(e => e.kind === 'payin');
  ok(payins.length > 2 && payins.every(e => commsForEntry(e).ch !== 'SMS'),
     '\u00a76 no payin outcome sends an SMS (' + payins.length + ' checked)');
  ok(commsForEntry(payins.find(e => e.amt > 0 && (e.status || 'done') === 'done')).ch === 'Email',
     'Rule C3 payin success is a receipt \u2014 email');
  ['failed', 'pending'].forEach(st => {
    const m = commsForEntry(tr.entries.find(x => x.kind === 'payin' && x.status === st));
    ok(m.ch === 'WhatsApp', 'Rule C3 payin ' + st + ' is news \u2014 WhatsApp');
    ok(/email/i.test(m.fallback || ''), 'Rule C4 payin ' + st + ' declares an email fallback');
  });
  // The in-product modal still carries the do-not-pay-again instruction; the
  // WhatsApp template no longer does. Asserted where it now lives.
  ok(/Don.t pay again/.test(textOf(PAYIN_OUTCOMES.find(o => o.id === 'timeout'))),
     'the pending outcome still tells the user not to pay again');
  // \u00a77 \u2014 money movement carries no SMS at all
  const payouts = tr.entries.filter(e => e.kind === 'payout');
  ok(payouts.length > 3 && payouts.every(e =>
       [].concat(commsForEntry(e)).every(m => m.ch !== 'SMS')),
     '\u00a77 no withdrawal outcome sends an SMS (' + payouts.length + ' checked)');
  const req = [].concat(commsForEntry(tr.entries.find(e => e.kind === 'payout' && e.status === 'pending')))[0];
  ok(req.ch === 'Email' && new RegExp(SUPPORT_TEL).test(req.html),
     'Rule C9 requested email carries the number to call');
  // Rule W8 is two messages, not one: announced before the date, notified after
  // it. Rule C10 governs the copy of both. F9 node 1 joins on the announcement.
  const auto = [].concat(commsForEntry(tr.entries.find(e => e.kind === 'payout' && e.auto)));
  ok(auto.length === 2, 'Rule C10 the mandated return is announced before and notified after');
  ok(auto[0].tpl === 'thinq_rac_advance_notice_v1' && auto[0].ch === 'WhatsApp',
     'Rule C10 the announcement is its own message on the action channel');
  ok(/email/i.test(auto[0].fallback || ''), 'Rule C4 the announcement falls back to email');
  ok(auto[1].ch === 'Email' && /unused funds/i.test(auto[1].subj),
     'Rule C10 the return itself is its own message');
  ok([auto[0].body, auto[1].html].every(t => /returned to your bank/.test(t)),
     'Rule C10 both name the reason before anything else');
  const paid = [].concat(commsForEntry(tr.entries.find(
    e => e.kind === 'payout' && (e.status || 'done') === 'done' && !e.auto)));
  ok(paid.length === 2, 'paid produces two messages, one per channel');
  ok(/UTR /.test(paid.find(m => m.ch === 'WhatsApp').body), 'paid WhatsApp carries the UTR');
  ok(/UTR/.test(paid.find(m => m.ch === 'Email').html), 'paid email carries the UTR');
  ok(paid.every(m => m.body !== undefined || m.html !== undefined), 'both paid messages have a body');

  // the only SMS left anywhere is the two action states
  const kinds = {};
  ['trader','shortfall','debt','empty','blocked'].forEach(k => {
    const vs = VARIANTS[k] ? Object.keys(VARIANTS[k]) : [null];
    vs.forEach(v => { const a = SCENARIOS[k](v);
      commsForMovement(a).forEach(m => kinds[m.ch] = 1); });
  });
  ok(!kinds.SMS, 'money movement produces no SMS in any scenario');

  // a money field must never act on a value it did not display
  ok(parseAmt('-500') === 0, 'parseAmt rejects a negative rather than stripping the sign');
  ok(parseAmt('1.2.3') === 0, 'parseAmt rejects a malformed decimal');
  ok(parseAmt('abc') === 0, 'parseAmt rejects letters');
  ok(parseAmt('1,000') === 100000, 'parseAmt accepts grouped digits');
  ok(parseAmt('\u20b9500.50') === 50050, 'parseAmt accepts a rupee sign and paise');

  ok(/digits only/.test(amtError('-500')), 'a non-numeric entry is named as such');
  ok(/greater than zero/.test(amtError('0')), 'zero gets its own message, not the numeric one');
  ok(amtError('1,000') === null, 'a valid amount raises nothing');
  ok(amtError('') === null, 'an empty field is not an error');

  // the field refuses the character rather than accepting and complaining
  ok(sanitiseAmt('-500') === '500', 'a minus sign never lands in the field');
  ok(sanitiseAmt('abc') === '', 'letters never land in the field');
  ok(sanitiseAmt('1.2.3') === '1.23', 'a second decimal point is refused');
  ok(sanitiseAmt('12.345') === '12.34', 'no more than paise');
  ok(sanitiseAmt('1,000') === '1000', 'a pasted grouping separator is dropped');
  ok(sanitiseAmt('\u20b9500') === '500', 'a pasted rupee sign is dropped');
  ok(sanitiseAmt('500') === '500', 'a valid amount passes through untouched');
  ok(sanitiseAmt('.5') === '.5', 'a leading point is left for the user to finish');

  // pills add to the field; they do not replace it
  ok(addToAmt('', P(5000)) === '5000', 'a pill on an empty field gives the pill value');
  ok(addToAmt('5000', P(10000)) === '15000', 'a second pill adds to the first');
  ok(addToAmt('500.50', P(5000)) === '5500.50', 'paise already typed survive the addition');
  ok(addToAmt('abc', P(5000)) === '5000', 'an unusable field is treated as zero');

  // the encoder itself, or every check above is worthless
  ok(smsMetrics('Rs 100.00').enc === 'GSM-7', 'smsMetrics: plain text is GSM-7');
  ok(smsMetrics('\u20b9100').enc === 'UCS-2', 'smsMetrics: \u20b9 forces UCS-2');
  ok(smsMetrics('\u20b9100').cap === 70, 'smsMetrics: UCS-2 caps at 70 characters');
  ok(smsMetrics('a'.repeat(161)).segments === 2, 'smsMetrics: 161 GSM-7 chars is 2 segments');
})();


/* ===== the citations themselves =====
   §4 of the comms PRD claims the catalogue "is emitted from the same definitions
   the running code reads, so a copy change lands here without anyone remembering
   to update it." That defence only holds while the code and the document agree
   about what the rules are called. Three ids the build cited had never existed in
   the PRD and eight more pointed at a different rule than the one implemented, so
   the agreement is asserted here rather than assumed. */
console.log('\n=== every rule and requirement the build cites is one the PRD carries ===');
(function () {
  const fs = require('fs');
  const SRC = fs.readFileSync('app.js', 'utf8');
  const PRD = fs.readFileSync('../02-requirements/product-requirements-communications.md', 'utf8');

  const cited = [...new Set(SRC.match(/\bRule C\d+|\bREQ-6\d+/g) || [])];
  is('the build cites the comms PRD at all (' + cited.length + ' distinct ids)', cited.length > 8);

  const absent = cited.filter(id => !new RegExp(id + '\\b').test(PRD));
  is('every cited id appears in product-requirements-communications.md'
     + (absent.length ? '   [' + absent.sort().join(', ') + ']' : ''), !absent.length);

  /* Appearing is the weak half. Every rule the build names must be a rule the PRD
     DEFINES — "**Rule Cn —" — or a citation can be satisfied by a passing mention
     of the id somewhere in prose. A requirement is held to the same test wherever
     the PRD writes it out. Only the ones it does not — REQ-601 to REQ-610, routed
     here by the parent PRD and not yet written out in full — fall back to presence,
     which is all that can be asked of them today. */
  const defined = new Set((PRD.match(/\*\*Rule C\d+/g) || []).map(m => m.slice(2)));
  const undef = cited.filter(id => /^Rule/.test(id) && !defined.has(id));
  is('every cited rule is one §3–§8 defines, not one it merely mentions'
     + (undef.length ? '   [' + undef.sort().join(', ') + ']' : ''), !undef.length);

  const written = new Set((PRD.match(/\*\*REQ-6\d+\*\* —/g) || []).map(m => m.slice(2, 9)));
  const loose = cited.filter(id => /^REQ/.test(id) && +id.slice(4) > 610 && !written.has(id));
  is('every cited requirement above REQ-610 is one §6–§8 writes out'
     + (loose.length ? '   [' + loose.sort().join(', ') + ']' : ''), !loose.length);

  const hi = Math.max(...[...defined].map(id => +id.slice(6)));
  is('the header declares the range the body actually defines (C1 \u2013 C' + hi + ')',
     new RegExp('Rules \\*\\*C1 \u2013 C' + hi + '\\*\\*').test(PRD));

  /* A rule id is a document reference, not copy. It belongs in a comment, never
     in a field a user can be shown. */
  const shipped = [];
  ['trader', 'shortfall', 'debt', 'empty', 'blocked'].forEach(k => {
    const vs = VARIANTS[k] ? Object.keys(VARIANTS[k]) : [null];
    vs.forEach(v => { const a = SCENARIOS[k](v);
      commsFor(a, derive(a)).concat(commsForMovement(a)).forEach(m =>
        ['subj', 'body', 'html', 'note', 'fallback'].forEach(f => {
          if (m[f] && /Rule [A-Z]\d+|REQ-\d+/.test(m[f])) shipped.push(k + '/' + m.ch + '.' + f);
        })); });
  });
  is('no rule or requirement id reaches a field the user can be shown'
     + (shipped.length ? '   [' + [...new Set(shipped)].join(', ') + ']' : ''), !shipped.length);
})();

console.log('\n'+(fails?'*** '+fails+' FAILURE(S) ***':'All checks passed.')+'\n');
process.exit(fails?1:0);
