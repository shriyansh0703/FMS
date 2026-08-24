/* ==========================================================================
   FMS prototype — application logic
   Built against product-requirements.md.

   Two principles carried straight from the PRD:
     Rule L1  every balance change is an entry; balance is DERIVED, never stored
     Rule B12 the three figures come from ONE definition (derive() below)
   Money is integer paise everywhere. No floats in the money path.
   ========================================================================== */

/* ---------- money ---------- */
const P = r => Math.round(r * 100);                       // rupees -> paise
const R = p => '₹' + (Math.abs(p) / 100).toLocaleString('en-IN',
  { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const Rs = p => (p < 0 ? '−' : '') + R(p);                // signed
const Rp = p => (p < 0 ? '−' : '+') + R(p);               // explicit sign
const big = p => {
  const s = R(p).split('.');
  return { r: (p < 0 ? '−' : '') + s[0], p: '.' + s[1] };
};

/* ---------- entry helper ---------- */
let _eid = 0;
const E = (ago, t, amt, kind, opt = {}) => Object.assign(
  { id: ++_eid, ago, t, amt, kind, status: 'done', user: true }, opt);

// links a reversal to the entry it reverses, in both directions (Rule L2)
const pair = (orig, rev) => { orig.reversedBy = rev.id; rev.reverses = orig.id; return [orig, rev]; };

/* ==========================================================================
   SCENARIOS — every state the PRD requires a designed answer for
   ========================================================================== */

const BANKS = [
  { id: 'hdfc', name: 'HDFC Bank', last4: '4471', def: true },
  { id: 'icici', name: 'ICICI Bank', last4: '9082', def: false }
];


/* ==========================================================================
   CAUSE CATALOGUES
   A debit balance and a margin shortfall each arise several ways, and the
   product has to name the right one. Both are data so every case is testable.
   ========================================================================== */

const DEBT_CAUSES = {
  depository: { label: 'Depository bill', t: 'Monthly depository bill', kind: 'charge',
    add: 'This is a routine monthly bill, not an error.',
    m: 'Includes GST \u00b7 charged after your account was settled to zero',
    why: 'Your account went below zero after a monthly depository charge was applied' },
  amc: { label: 'Maintenance charge', t: 'Annual maintenance charge', kind: 'charge',
    add: 'It is charged even in a year with no trades.',
    m: 'Demat account maintenance for the year',
    why: 'Your account went below zero after the annual maintenance charge was applied' },
  dp: { label: 'DP charge on a sale', t: 'Depository charges on shares sold', kind: 'charge',
    add: 'Charged per company whose shares you sold, not per order and not per share.',
    m: 'Per scrip \u00b7 billed after the sale had settled',
    why: 'Your account went below zero after depository charges for shares you sold' },
  auction: { label: 'Short-delivery penalty', t: 'Auction penalty for short delivery', kind: 'charge',
    add: 'The cost is set by the auction price, which can be well above the price you sold at.',
    m: 'Shares were not delivered, so the exchange bought them in',
    why: 'Your account went below zero after a short-delivery penalty from the exchange' },
  penalty: { label: 'Margin shortfall penalty', t: 'Margin shortfall penalty', kind: 'charge',
    add: 'This came from a shortfall that was not cleared in time.',
    m: 'Exchange penalty passed through',
    why: 'Your account went below zero after a margin shortfall penalty from the exchange' },
  reversal: { label: 'Payin reversed', t: 'Funds addition reversed', kind: 'payin',
    add: 'If you did not expect this, check with your bank — the reversal came from them.',
    m: 'Your bank returned the payment after the money had been used',
    why: 'Your account went below zero after your bank returned a payment you had already used' }
};

const SHORT_CAUSES = {
  mtm:        { label: 'Overnight MTM loss',
    alt: 'Closing part of a position releases the margin it uses, which clears the shortfall without adding money.',
    why: 'Your positions moved against you overnight', left: '1h 12m', until: '2:30 pm today' },
  collateral: { label: 'Collateral revalued',
    alt: 'Pledging more holdings raises your collateral, but only half of derivatives margin can come from collateral, so cash may still be needed. If your holdings fall further, the shortfall grows even if you do nothing.',
    why: 'The market fell, so your pledged holdings are worth less after their haircut',
    left: '2h 40m', until: '3:00 pm today' },
  exchange:   { label: 'Exchange raised margin',
    alt: 'Reducing the position reduces what the new requirement applies to.',
    why: 'The exchange raised the margin required on a scrip you hold',
    left: '45m', until: '2:00 pm today' },
  cashhalf:   { label: 'Cash half short',
    alt: 'Pledging more holdings will not fix this — the gap is in the cash half. Add cash, or reduce your derivatives positions.',
    why: 'You hold enough collateral, but not enough cash for the half that derivatives require',
    left: '1h 12m', until: '2:30 pm today' },
  unpledge:   { label: 'Holdings unpledged',
    alt: 'Re-pledging the same holdings restores the margin, with no money needed.',
    why: 'You unpledged holdings, which reduced the collateral supporting your positions',
    left: '3h 05m', until: '3:20 pm today' }
};

/* ==========================================================================
   OUTCOMES — what can happen after the user commits
   Field validation stops a bad request being made. These are the ways a
   well-formed request still does not end in money moving. Three principles,
   all of them learned from what the earlier single "declined" message got wrong:

     1. Every message says what happened TO THE MONEY, not just to the request.
        "Payment failed" leaves the user to work out whether they were charged.
     2. Never say "failed" when the outcome is unknown. An unknown outcome is
        its own state and needs its own copy, because the recovery differs:
        wait, do not retry.
     3. Name whose problem it is. Blaming the user's bank for our outage sends
        them to the wrong place, and they will not come back to us to fix it.
   ========================================================================== */
const PAYIN_OUTCOMES = [
  { id: 'success', label: 'Succeeds', st: 'done' },

  { id: 'declined', label: 'Bank declines', st: 'failed',
    row: 'your bank declined the payment',
    // the alternative route is offered as a button, not named in the sentence
    k: 'err', t: 'Your bank declined this payment. If any amount was debited, it will be refunded within 1–3 business days.',
  },

  { id: 'insufficient', label: 'Not enough in bank', st: 'failed',
    row: 'not enough balance in the bank account',
    k: 'err', t: 'Your bank reported insufficient balance. Try using another bank account or add funds and retry.',
    // The money has to appear in the bank before any route will work, so the
    // primary action names both halves. A different ROUTE cannot help here —
    // the same account is short whichever rail it is asked down — and the
    // sentence already points at both ways out, so the button is plain Retry.
    altKind: 'none' },

  { id: 'banklimit', label: 'Bank’s own limit', st: 'failed',
    row: 'above your bank’s per-payment limit',
    k: 'err', t: 'The amount exceeded your bank’s per-payment limit. Try a smaller amount.' },

  { id: 'timeout', label: 'No answer from bank', st: 'unknown',
    row: 'awaiting confirmation from your bank',
    k: 'warn', t: 'Your bank hasn’t confirmed the payment yet. Don’t pay again. If debited but unsuccessful, ' +
       'the amount will be returned within 3 working days.' },

  { id: 'gateway', label: 'Our service is down', st: 'failed',
    row: 'we could not reach the payment service',
    k: 'err', t: 'This one’s on us. We couldn’t reach the payment service, so nothing was sent or debited.' },

  { id: 'abandoned', label: 'User backs out', st: 'failed',
    row: 'payment was not completed',
    k: 'note', t: 'Payment cancelled before approval. Nothing was debited. Try again when you’re ready.' }
];

/* Two lists, because they happen at two different times to two different
   audiences. SUBMIT_OUTCOMES is what the user is told while standing there.
   EOD_OUTCOMES is what the payout run decides hours later, and reaches them by
   message and by a changed transaction — never by a dialog. */
const SUBMIT_OUTCOMES = [
  { id: 'accepted', label: 'Accepted', modal: { kind: 'good', title: 'Withdrawal request submitted' },
    t: () => 'The available amount at the end of today will be sent to {bank}.' },

  { id: 'cutoff', label: 'After the cut-off', modal: { kind: 'good', title: 'Withdrawal requested' },
    row: 'requested after today\u2019s cut-off',
    t: () => `Your request was placed after the ${LIMITS.payoutCutoff} cut-off. It will be processed ` +
       `on the next working day. You can still cancel it.` },

  { id: 'review', label: 'Held for review', modal: { kind: 'warn', title: 'Withdrawal under review' },
    row: 'held for review',
    t: () => 'Your withdrawal is under review. We\u2019ll update you within 1 working day \u2014 ' +
       'no action is needed.' }
];

const EOD_OUTCOMES = [
  { id: 'sent',       label: 'Sent in full' },
  { id: 'partial',    label: 'Partly sent',
    row: 'part of the request was available at the end of the day' },
  { id: 'nothing',    label: 'Nothing left',
    row: 'no funds were available at the end of the day' },
  { id: 'bankreject', label: 'Bank refuses it',
    row: 'your bank could not accept the transfer' },
  { id: 'raildown',   label: 'Bank rail is down',
    row: 'the banking network was unavailable \u2014 queued for the next run' }
];

const outcomeFor = (list, id) => list.find(o => o.id === id) || list[0];
/* An outcome's text may be a function where it depends on configuration. */
const textOf = o => (typeof o.t === 'function' ? o.t() : o.t);

/* The catalogues sit above SCENARIOS because the sample transactions are built
   from them: one row per outcome, with the same text the live flow produces.
   Defining them below would leave SCENARIOS reading a const still in its
   temporal dead zone at module load. */

const SCENARIOS = {

  // A real trading day: money in, money out, a delivery sale that has not settled,
  // a mark-to-market loss, charges both posted and not yet posted, and a payout
  // still in flight. Every term of the withdrawable derivation carries a value.
  trader: () => ({
    key: 'trader', label: 'Active trader', who: 'Nikhil Rao', sub: 'Equity & F&O',
    canReceive: true, blocker: null, stale: false, asOfMin: 2,
    tradedToday: true, ordersOpen: true, shortfall: 0,
    collateral: { equity: P(175000), liquid: P(50000) },
    used: { span: P(62000), exposure: P(18500), delivery: P(7700), orderBlocks: P(8000) },
    optionPremium: P(6400),
    collateralUtilised: P(48100),
    unsettledCredits: P(38500), unpostedCharges: P(2180),
    unrealisedLedger: P(6200),
    banks: BANKS,
    entries: [
      // 1,55,000 + 12,000 — the extra offsets the partly-sent sample below, so the
      // scenario's headline figures stay the ones every assertion is written against
      E(18, 'Funds added', P(167000), 'payin', { m: 'UPI \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY274119' }),
      E(16, 'Withdrawal', P(-5000), 'payout',
        { m: 'HDFC Bank \u2022\u20224471', ref: 'UTR7710233', user: false, auto: true,
          note: 'Unused funds are automatically returned as part of the mandated settlement cycle. You did not request this' }),
      E(14, 'Net settlement for equity', P(-15000), 'trade', { m: 'Settlement 2026167 \u00b7 shares bought' }),
      E(11, 'Brokerage & charges', P(-1180), 'charge', { m: 'Brokerage \u20b91,000 \u00b7 GST \u20b9180' }),
      E(8, 'Futures MTM', P(2400), 'trade', { m: 'NIFTY futures \u00b7 profit settled in cash' }),
      E(5, 'Futures MTM', P(-11220), 'trade', { m: 'NIFTY futures \u00b7 loss settled in cash' }),
      E(1, 'Withdrawal', P(-10000), 'payout', { status: 'pending', m: 'To HDFC Bank \u2022\u20224471', note: 'Expected to reach your bank tomorrow' }),
      E(0, 'Funds added', P(25000), 'payin', { m: 'UPI \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY418823' }),
      E(0, 'Withdrawal', P(-12000), 'payout', { m: 'HDFC Bank \u2022\u20224471', ref: 'UTR9930241' }),
      E(0, 'Received from Stocks', P(38500), 'trade', { m: 'Sold 100 shares \u00b7 settles tomorrow' }),
      E(0, 'Futures MTM', P(-8400), 'trade', { m: 'NIFTY futures \u00b7 loss settled in cash' }),
      E(0, 'Brokerage & charges', P(-1240), 'charge', { m: 'On trades executed today' }),

      // --- one sample per state the PRD's state machines define ---
      E(0, 'Funds added', P(12000), 'payin',
        { status: 'pending', m: 'UPI \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY530118', note: 'Waiting for your bank to confirm' }),
      /* One sample per way a payin can end. The row text and the explanation both
         come from PAYIN_OUTCOMES, so the samples cannot drift from the messages
         the live flow produces. */
      ...['timeout', 'declined', 'insufficient', 'banklimit', 'gateway', 'abandoned']
        .map((id, i) => {
          const o = PAYIN_OUTCOMES.find(x => x.id === id);
          const route = i % 2 ? 'Net banking' : 'UPI';
          return E(i + 1, 'Funds added', P([15000, 10000, 25000, 250000, 6000, 4000][i]), 'payin',
            // the meta line stays standard — method, account, reference. The reason
            // for the state lives behind the (i), as it does on every other row.
            { status: o.st, m: `${route} \u00b7 HDFC Bank \u2022\u20224471`,
              ref: 'PAY' + (530900 + i * 137), note: textOf(o) });
        }),
      ...pair(
        E(6, 'Funds added', P(8000), 'payin',
          { m: 'Net banking \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY221904' }),
        E(5, 'Funds addition reversed', P(-8000), 'payin',
          { m: 'Net banking \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY221904', note: 'Your bank returned the payment, so the earlier credit has been reversed.' })),

      E(4, 'Withdrawal', P(-5000), 'payout',
        { status: 'cancelled', m: 'To HDFC Bank \u2022\u20224471', note: 'Withdrawal cancelled before it was sent to your bank.' }),
      E(7, 'Withdrawal', P(-12000), 'payout',
        { outcome: 'partial', m: 'HDFC Bank \u2022\u20224471', ref: 'UTR4471902', _requested: P(30000),
          note: 'You requested \u20b930,000.00, but only \u20b912,000.00 was available at the end-of-day ' +
                'payout. \u20b912,000.00 was processed, and the request is now closed.' }),

      /* The other three end-of-day answers. None of them settles, so none of
         them moves the ledger and no offset is needed. They live here because
         the 30-day view is meant to show every state the module can produce. */
      E(3, 'Withdrawal', P(-20000), 'payout',
        { outcome: 'notsent', status: 'failed', m: 'To HDFC Bank \u2022\u20224471', _requested: P(20000),
          note: 'You requested \u20b920,000.00, but no funds were available at the end-of-day payout. ' +
                'Nothing was processed, and the request is now closed.' }),
      E(6, 'Withdrawal', P(-9000), 'payout',
        { outcome: 'notaccepted', status: 'returned', m: 'To HDFC Bank \u2022\u20224471', _requested: P(9000),
          note: 'Your \u20b99,000.00 transfer was returned by your bank. Nothing was deducted. ' +
                'Please check your bank account details with your bank before requesting the ' +
                'withdrawal again.' }),
      E(2, 'Withdrawal', P(-7500), 'payout',
        { outcome: 'raildown', status: 'pending', m: 'To HDFC Bank \u2022\u20224471', _amt: P(7500), _requested: P(7500),
          note: 'The banking network was unavailable at the end-of-day payout, so this could not be ' +
                'sent that day. Your request is still open and goes out with the next payout run. ' +
                'You can still cancel it.' }),
      ...pair(
        E(9, 'Withdrawal', P(-6000), 'payout',
          { m: 'HDFC Bank \u2022\u20224471', ref: 'UTR8814772' }),
        E(8, 'Withdrawal', P(6000), 'payout',
          { m: 'HDFC Bank \u2022\u20224471', note: 'Your bank could not accept the withdrawal. The money is back in your account.' }))
    ]
  }),

  // Positions moved against him two days running. Available margin is negative,
  // which is a legitimate state and must not be clamped (Rule B9).
  shortfall: (v) => ({
    key: 'shortfall', label: 'Margin shortfall', who: 'Nikhil Rao', sub: 'Equity & F&O',
    canReceive: true, blocker: null, stale: false, asOfMin: 1,
    tradedToday: true, ordersOpen: true, shortfall: P(38400),
    cause: SHORT_CAUSES[v] || SHORT_CAUSES.mtm,
    collateral: { equity: P(175000), liquid: P(25000) },
    used: { span: P(200000), exposure: P(42000), delivery: 0, orderBlocks: P(12000) },
    optionPremium: P(8600),
    collateralUtilised: P(100000),
    unsettledCredits: 0, unpostedCharges: P(1860),
    banks: BANKS,
    entries: [
      E(18, 'Funds added', P(155000), 'payin', { m: 'UPI \u00b7 HDFC Bank \u2022\u20224471', ref: 'PAY274119' }),
      E(16, 'Funds returned to your bank', P(-5000), 'payout',
        { m: 'HDFC Bank \u2022\u20224471', ref: 'UTR7710233', user: false, auto: true,
          note: 'Unused funds are automatically returned as part of the mandated settlement cycle. You did not request this' }),
      E(14, 'Net settlement for equity', P(-40000), 'trade', { m: 'Settlement 2026167 \u00b7 shares bought' }),
      E(11, 'Brokerage & charges', P(-1180), 'charge', { m: 'Brokerage \u20b91,000 \u00b7 GST \u20b9180' }),
      E(2, 'Futures MTM', P(-46420), 'trade', { m: 'NIFTY futures \u00b7 loss settled in cash' }),
      E(0, 'Futures MTM', P(-18600), 'trade', { m: 'NIFTY futures \u00b7 loss settled in cash' })
    ]
  }),

  empty: () => ({
    key: 'empty', label: 'New & empty', who: 'Priya Nair', sub: 'Equity',
    canReceive: true, blocker: null, stale: false, asOfMin: 1,
    tradedToday: false, ordersOpen: false, shortfall: 0,
    collateral: { equity: 0, liquid: 0 },
    used: { span: 0, exposure: 0, delivery: 0, orderBlocks: 0 },
    collateralUtilised: 0,
    unsettledCredits: 0, unpostedCharges: 0,
    banks: [BANKS[0]],
    entries: []
  }),

  blocked: () => ({
    key: 'blocked', label: 'Cannot receive money', who: 'Priya Nair', sub: 'Equity',
    canReceive: false, stale: false, asOfMin: 1,
    blocker: {
      name: 'Your bank account is not verified yet',
      detail: 'Money can only enter from an account you have proven you hold. Verifying takes about two minutes with UPI.',
      action: 'Verify bank account',
      state: 'Not started'
    },
    tradedToday: false, ordersOpen: false, shortfall: 0,
    collateral: { equity: 0, liquid: 0 },
    used: { span: 0, exposure: 0, delivery: 0, orderBlocks: 0 },
    collateralUtilised: 0,
    unsettledCredits: 0, unpostedCharges: 0,
    banks: [],
    entries: []
  }),

  debt: (v) => {
    const cz = DEBT_CAUSES[v] || DEBT_CAUSES.depository;
    // The dormant-account debt path, reproduced exactly:
    // a mandated sweep empties the account, a scheduled bill posts against zero,
    // and a weekly charge compounds silently from there.
    const e = [
      E(220, 'Funds added', P(5000), 'payin', { m: 'Net banking · HDFC Bank ••4471' }),
      E(185, 'Funds returned to your bank', P(-5000), 'payout',
        { m: 'Quarterly settlement · unused funds returned as required', user: false, auto: true }),
      E(169, 'Monthly depository bill', P(-23.60), 'charge',
        { m: 'Includes GST · charged after your account was settled to zero', user: false })
    ];
    // 11 weekly interest charges, plus one that was reversed
    const weeks = [70, 63, 56, 49, 42, 35, 28, 21, 14, 7, 0];
    weeks.forEach((w, i) => {
      e.push(E(w, 'Interest on debit balance', P(-0.07), 'charge',
        { m: 'Charged weekly while your balance is below zero', user: false }));
      if (i === 4) {
        const orig = E(w - 2, 'Interest on debit balance', P(-0.05), 'charge',
          { m: 'Charged weekly while your balance is below zero', user: false });
        const rev = E(w - 4, 'Reversal of interest on debit balance', P(0.05), 'charge',
          { m: 'Charged in error and returned', user: false, reverses: orig.id });
        orig.reversedBy = rev.id;
        e.push(orig, rev);
      }
    });
    return {
      key: 'debt', label: 'In debt (dormant)', who: 'Arun Mehta', sub: 'Equity',
      canReceive: true, blocker: null, stale: false, asOfMin: 4,
      tradedToday: false, ordersOpen: false, shortfall: 0,
      collateral: { equity: 0, liquid: 0 },
      used: { span: 0, exposure: 0, delivery: 0, orderBlocks: 0 },
      collateralUtilised: 0,
      unsettledCredits: 0, unpostedCharges: 0,
      debtRate: '0.05% per day (about 18% a year)', cause: cz,
      banks: BANKS,
      entries: e.sort((a, b) => b.ago - a.ago)
    };
  }
};

/* ==========================================================================
   THE ONE DEFINITION — Rule B12
   Every figure on every surface is read from here. Nothing computes its own.
   ========================================================================== */

function derive(a) {
  const settled = a.entries.filter(e => e.status === 'done');
  const ledger = settled.reduce((s, e) => s + e.amt, 0);          // Rule L1

  const collateral = a.collateral.equity + a.collateral.liquid;
  const unrealised = a.unrealisedLedger || 0;
  const usedMargin = a.used.span + a.used.exposure + a.used.delivery + a.used.orderBlocks;
  const optPremium = a.optionPremium || 0;          // net premium received: a credit

  const availableMargin = ledger + collateral + unrealised + optPremium - usedMargin;
  const cashUtilised = Math.max(0, usedMargin - a.collateralUtilised);
  const availableCash = ledger - cashUtilised;

  // Rule B4 — the withdrawable derivation, defined once, here.
  // Built from the OPENING balance, so today's movements are shown rather than
  // baked in. Money added today is displayed but deliberately never added,
  // which avoids adding it and then subtracting it on a later line.
  const today       = settled.filter(e => e.ago === 0);
  const payinToday  = today.filter(e => e.kind === 'payin').reduce((s, e) => s + e.amt, 0);
  const payoutToday = today.filter(e => e.kind === 'payout').reduce((s, e) => s + e.amt, 0);
  const todayOther  = today.filter(e => e.kind !== 'payin' && e.kind !== 'payout')
                           .reduce((s, e) => s + e.amt, 0);
  const openingBalance   = ledger - payinToday - payoutToday - todayOther;
  const closingWithMargin = ledger - usedMargin;

  const terms = [
    { n: 'Opening balance', v: openingBalance, sign: 0,
      x: 'Balance at the start of today' },
    { n: 'Payin Today', v: payinToday, excluded: true,
      x: 'Funds added today. Available for trading and counts toward Margin Available, but can\u2019t be withdrawn until tomorrow' },
    { n: 'Payout', v: payoutToday, sign: 0,
      x: 'Money already paid out to your bank today' },
    { n: 'Today\u2019s trading and charges', v: todayOther, sign: 0,
      x: 'Profit, loss and charges that settled today' },
    { n: 'Margin set aside for your open positions', v: -usedMargin, sign: -1, isMargin: true,
      x: '' },   // heading is self-explanatory: no sub-text, and therefore no (i)
    { n: 'Pledged margin', v: a.collateralUtilised, sign: 1,
      x: 'Your pledged holdings are covering part of the margin required for your positions, so this amount of cash remains available instead of being blocked' },
    { n: 'Delivery sell benefit', v: -a.unsettledCredits, sign: -1,
      x: 'Proceeds from today\u2019s delivery sales available for trading' },
    { n: 'Unposted charges', v: -a.unpostedCharges, sign: -1,
      x: 'Charges you have incurred but that have not yet been deducted from your account' }
  ];
  const raw = terms.filter(t => !t.excluded).reduce((s, t) => s + t.v, 0);
  const withdrawable = Math.max(0, raw);

  // Which single reason is doing the most damage (REQ-102).
  // Candidates are the terms exactly as the drawer displays them, so the figure
  // named here is the figure the reader will find when they open it. An earlier
  // version used a netted margin number, which showed two different values
  // under one heading.
  const causes = terms.filter(t => t.sign === -1 && t.v < 0).slice();
  if (payinToday > 0) causes.push({ n: 'Payin Today', v: -payinToday, sign: -1 });
  const worst = causes.sort((x, y) => x.v - y.v)[0] || null;

  // blocked money by source x commitment state (REQ-106)
  const posTotal = a.used.span + a.used.exposure + a.used.delivery;
  const ordTotal = a.used.orderBlocks;
  const posCollat = Math.min(a.collateralUtilised, posTotal);
  const posCash = posTotal - posCollat;
  const ordCollat = Math.max(0, a.collateralUtilised - posCollat);
  const ordCash = Math.max(0, ordTotal - ordCollat);

  // deployability (REQ-105) — margin is not fungible across trade kinds
  const deploy = [
    { k: 'Delivery', v: Math.max(0, availableCash),
      n: availableCash < availableMargin ? 'Cash only — pledged holdings can’t be used for delivery buys' : '' },
    { k: 'Intraday', v: Math.max(0, availableMargin), n: '' },
    { k: 'F&O', v: Math.max(0, Math.min(availableMargin, Math.max(0, availableCash) * 2)),
      n: availableCash * 2 < availableMargin ? 'Limited by the cash component required for derivatives' : '' }
    // MTF is out of scope for now. Its capacity depends on the broker's funding
    // ratio and per-scrip eligibility, not on available margin, so the row would
    // have been a placeholder duplicating Intraday.
  ];

  return {
    ledger, collateral, usedMargin, availableMargin, availableCash,
    closingWithMargin, openingBalance, payinToday, payoutToday, todayOther, unrealised, optPremium,
    terms, raw, withdrawable, worst, clamped: raw < 0,
    matrix: { posCash, posCollat, ordCash, ordCollat, posTotal, ordTotal },
    deploy, inDebt: ledger < 0,
    pending: a.entries.filter(e => e.status === 'pending')
  };
}

/* ==========================================================================
   ROUTES (REQ-202) — a route is shown with cost, ceiling and arrival, or not at all
   ========================================================================== */
/* ==========================================================================
   LIMITS — policy, not logic
   Every number here is set by a regulator, a bank or a commercial decision, and
   every one of them changes without the product changing. They are named and
   collected so a change is an edit in one place rather than a search through
   the file, and so it is obvious which figures are ours to choose and which
   are not. The knowledge base marks all of these ⚠️ VERIFY.
   ========================================================================== */
const LIMITS = {
  upiDailyCap:  P(200000),     // NPCI ceiling; banks may set a lower one of their own
  nbDailyCap:   P(1000000),    // typical net-banking ceiling
  neftCap:      Infinity,      // no ceiling on the rail itself
  nbFee:        P(11.80),      // gateway charge passed through, incl. GST
  minAdd:       P(100),        // our own floor, waived when settling dues
  payoutCutoff: '3:00 PM',     // after this, a request goes out next working day
  /* Where "Trade Now" goes after funds land. Configured rather than hardcoded
     because the right destination is a product decision that will change — the
     order pad, the watchlist, a segment-specific screen — and it changes
     without the funds module changing. */
  tradeNowHref: '#/orders'
};

const ROUTES = [
  { id: 'upi', n: 'UPI', d: 'Money is usable straight away', fee: 0, cap: LIMITS.upiDailyCap, eta: 'Instant' },
  { id: 'nb', n: 'Net banking', d: 'Money is usable straight away', fee: LIMITS.nbFee, cap: LIMITS.nbDailyCap, eta: 'Instant' },
  /* selfService: we do not execute this one. The user leaves and does it in
     their own banking app, so it can be chosen in the panel — where the
     instructions live — but it can never be a one-tap recovery button. */
  { id: 'neft', n: 'Bank transfer (NEFT / IMPS)', d: 'You transfer from your own banking app', fee: 0, cap: LIMITS.neftCap, eta: '30 minutes to 3 hours', selfService: true }
];

/* The cap is a DAILY one, which is what the message says, so it has to be
   measured against everything already sent on that route today rather than
   against this payment alone. Checking one payment at a time would let a user
   pass ₹1,50,000 twice and be refused by their bank instead of by us. */
/* The one place that answers "can this route carry this amount right now?"
   Everything that needs the answer asks here, so the cap cannot be enforced on
   one surface and forgotten on another. */
function roomOn(a, routeId) {
  const r = ROUTES.find(x => x.id === routeId);
  return r ? Math.max(0, r.cap - usedTodayOn(a, routeId)) : 0;
}
/* The route an amount should take when nobody has chosen one: the first we can
   execute that still has room for it today. */
function routeFor(a, paise) {
  return ROUTES.find(r => !r.selfService && paise <= roomOn(a, r.id)) || ROUTES[0];
}

function usedTodayOn(a, routeId) {
  const name = (ROUTES.find(r => r.id === routeId) || {}).n;
  return a.entries
    .filter(e => e.kind === 'payin' && e.ago === 0 && e.amt > 0
              && (e.status || 'done') !== 'failed'
              && String(e.m || '').indexOf(name) === 0)
    .reduce((t, e) => t + e.amt, 0);
}

/* ==========================================================================
   STATE
   ========================================================================== */
const state = {
  scen: 'trader', variant: null,
  acct: SCENARIOS.trader(),
  txTab: 'payin',
  range: 30, from: null, to: null,
  act: 'add',
  hideZeros: false, openInfo: {}, collOpen: true, usedOpen: true, dsbOpen: true,
  addAmt: '', addRoute: 'upi', addBank: 'hdfc', addMsg: null,
  outBank: null,
  outAmt: '', outMsg: null,
  forcePayin: 'success', forceSubmit: 'accepted', forceEod: 'sent',
  openRoute: false, openBank: false,
  highlightId: null
};

const VARIANTS = { debt: DEBT_CAUSES, shortfall: SHORT_CAUSES };

const $ = s => document.querySelector(s);
const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function setScenario(k, v) {
  state.scen = k;
  if (v !== undefined) state.variant = v;
  else if (!VARIANTS[k] || !VARIANTS[k][state.variant]) state.variant = Object.keys(VARIANTS[k] || {})[0] || null;
  state.acct = SCENARIOS[k](state.variant);
  const dflt = defaultAdd(state.acct);
  state.addAmt = dflt.amt; state.addBank = dflt.bank;
  state.outAmt = ''; state.addMsg = null; state.outMsg = null;
  state.outBank = null;
  state.act = 'add'; state.range = 30; state.txTab = 'payin';
  render();
}


/* ==========================================================================
   INFO TOGGLES — a heading's description sits behind an (i) until asked for.
   Deliberately a real <button> with a label and a state, not a bare glyph:
   the whole point of these descriptions is that everyone can reach them.
   Open/closed is held in state, keyed by heading, so a re-render keeps it.
   ========================================================================== */
function infoBtn(key, aria) {
  const open = !!state.openInfo[key];
  return `<button class="ibtn${open ? ' on' : ''}" type="button" data-key="${esc(key)}"
    aria-label="${esc(aria || ('What does ' + key + ' mean?'))}" aria-expanded="${open}">i</button>`;
}
// Punctuation convention (Microsoft Style Guide / Apple HIG / Material):
//   one sentence  -> no full stop
//   two or more   -> full stops throughout, including the last
// A lone fragment needs no terminator; prose that drops one reads as a typo.
const punct = t => {
  const v = String(t).trim().replace(/\s*\.\s*$/, '');
  return /[.?!]\s+\S/.test(v) ? v + '.' : v;
};
function infoDesc(key, text) {
  const open = !!state.openInfo[key];
  return `<div class="infodesc" data-key="${esc(key)}"${open ? '' : ' hidden'}>${esc(punct(text))}</div>`;
}
function wireInfo(root) {
  root.querySelectorAll('.ibtn').forEach(btn => {
    btn.onclick = () => {
      const k = btn.dataset.key;
      const open = !state.openInfo[k];
      state.openInfo[k] = open;
      const sel = '[data-key="' + (window.CSS && CSS.escape ? CSS.escape(k) : k) + '"]';
      document.querySelectorAll('.ibtn' + sel).forEach(b => {
        b.classList.toggle('on', open);
        b.setAttribute('aria-expanded', String(open));
      });
      document.querySelectorAll('.infodesc' + sel).forEach(d => { d.hidden = !open; });
    };
  });
}

/* ==========================================================================
   RENDER
   ========================================================================== */
/* ---------- toast: page-level confirmation ----------
   An outcome that changes the whole page does not belong under the control that
   triggered it. Cancelling a withdrawal closes the request and updates the
   withdraw panel and the transaction list, so the confirmation is announced at
   page level, where both are visible. Withdrawable is untouched either way:
   under Rule W3 a request never held it. */
let toastN = 0;
function toast(text, kind) {
  const wrap = $('#toastWrap');
  if (!wrap) return;
  const t = el('div', 'toast' + (kind ? ' ' + kind : ''));
  const id = ++toastN;
  t.innerHTML = `<span class="ti" aria-hidden="true">${kind === 'info' ? 'i' : '\u2713'}</span>
    <div class="tt">${punct(text)}</div>
    <button class="tx" type="button" aria-label="Dismiss">\u2715</button>`;
  const kill = () => {
    if (!t.parentNode) return;
    t.classList.add('out');
    setTimeout(() => t.remove(), 180);
  };
  t.querySelector('.tx').onclick = kill;
  wrap.appendChild(t);
  setTimeout(() => { if (toastN === id || t.parentNode) kill(); }, 6000);
  return t;
}

function render() {
  const a = state.acct, d = derive(a);
  renderScenarios();
  $('#who').innerHTML = `<b>${esc(a.who)}</b>${esc(a.sub)}`;
  renderBanner(a, d);
  renderBalances(a, d);
  renderMargin(a, d);
  renderDeploy(a, d);
  renderTx(a, d);
  renderAction(a, d);
  renderUpcoming(a, d);
  renderOutcomes();
  writeUrl();
}

/* Demo chrome. A one-in-five random failure cannot be shown on request, and a
   path nobody can reach is a path nobody reviews. */
function renderOutcomes() {
  const out = state.act === 'out';
  const box = $('#outcomeBtns'); box.innerHTML = '';
  const chips = (list, cur, set) => list.forEach(o => {
    const b = el('button', o.id === cur ? 'on' : '', esc(o.label));
    b.onclick = () => { set(o.id); render(); };
    box.appendChild(b);
  });

  if (!out) {
    $('#outcomeLabel').textContent = 'Next payin';
    $('#outcomeNote').textContent = 'What happens after the user commits \u2014 field validation cannot prevent any of these';
    chips(PAYIN_OUTCOMES, state.forcePayin, v => state.forcePayin = v);
    return;
  }

  /* Withdrawal has two moments, hours apart. The first has a user in front of
     it; the second is a batch job. Separating the controls is the point — it is
     what makes it obvious that nothing in the second group can be a dialog. */
  $('#outcomeLabel').textContent = 'Withdrawal';
  $('#outcomeNote').textContent = 'The request is answered now; the payout run answers hours later, with nobody watching';

  box.appendChild(el('span', 'grp-l', 'ON SUBMIT'));
  chips(SUBMIT_OUTCOMES, state.forceSubmit, v => state.forceSubmit = v);

  box.appendChild(el('span', 'grp-l', 'AT END OF DAY'));
  chips(EOD_OUTCOMES, state.forceEod, v => state.forceEod = v);

  const run = el('button', 'runeod', 'Run end-of-day \u2192');
  run.onclick = runEOD;
  box.appendChild(run);
}

function renderScenarios() {
  const box = $('#scenBtns'); box.innerHTML = '';
  Object.keys(SCENARIOS).forEach(k => {
    const s = SCENARIOS[k]();
    const b = el('button', k === state.scen ? 'on' : '', esc(s.label));
    b.onclick = () => setScenario(k);
    box.appendChild(b);
  });

  // causes for the scenario in view, so every case is reachable
  const vs = VARIANTS[state.scen];
  const bar = $('#variantBar'), vb = $('#varBtns');
  bar.hidden = !vs;
  vb.innerHTML = '';
  if (!vs) return;
  Object.keys(vs).forEach(v => {
    const b = el('button', v === state.variant ? 'on' : '', esc(vs[v].label));
    b.onclick = () => setScenario(state.scen, v);
    vb.appendChild(b);
  });
}

/* ---------- state banner: blocked / debt / shortfall / empty ---------- */
/* ---------- banner actions go straight into payment ----------
   When the banner already names the amount, making the user re-enter it in the
   form is a step with no decision in it. UPI is the instant, no-fee rail, which
   is what a margin deadline needs (REQ-207). Dues bypass the deposit minimum
   (REQ-502), which is why this does not run the panel's own validation. */
function payNow(a, paise) {
  state.act = 'add';
  const r = routeFor(a, paise);          // not always UPI — the cap decides
  state.addRoute = r.id;
  render();
  doAdd(a, paise, r);
  // doAdd clears the field on submit; put the amount back so the Add money tab
  // shows what is being paid while it is in flight
  state.addAmt = (paise / 100).toFixed(2);
  render();
}

function renderBanner(a, d) {
  const box = $('#stateBanner'); box.innerHTML = '';

  if (!a.canReceive) {                                            // REQ-505, Rule H6
    box.appendChild(banner('bad', '!', a.blocker.name,
      `${esc(a.blocker.detail)}<p style="margin-top:6px;color:var(--ink-3)">Current state: ${esc(a.blocker.state)}</p>`,
      [{ t: a.blocker.action, f: () => alert('In the real product this goes to bank account verification.\n\nThe PRD requires the funding path be restored on return, without the user having to find it again. (REQ-505)') }]));
    return;
  }

  if (d.inDebt) {                                                 // REQ-501, Rule H1
    const owed = -d.ledger;

    box.appendChild(banner('bad', '₹', `${R(owed)} due`,
      `${(a.cause && a.cause.why) || 'Your balance is below zero'}.
       <p style="margin-top:6px">Interest is charged at <b>${esc(a.debtRate || 'a rate not yet set')}</b>. ${R(owed - 2360)} has accrued so far.</p>`,
      [{ t: `Pay ${R(owed)}`, f: () => payNow(a, owed) }]));
    return;
  }

  if (a.shortfall > 0) {                                          // REQ-506, Rule H7
    box.appendChild(banner('warnb', '!', `${R(a.shortfall)} margin shortfall`,
      `${esc(a.cause.why)}. Your positions require ${R(a.shortfall)} more margin. If you don\u2019t add
       the funds in time, we may close your positions on your behalf.
       <p style="margin-top:6px"><b>Time remaining: ${esc(a.cause.left)}</b> \u00b7 Until ${esc(a.cause.until)}</p>`,
      [{ t: `Add ${R(a.shortfall)}`, f: () => payNow(a, a.shortfall) }]));
    return;
  }

  if (d.ledger === 0 && a.entries.length === 0) {                 // REQ-504, Rule H5
    box.appendChild(banner('infob', '→', 'Add money to get started',
      `Add funds to buy shares. Once settled, your money can be withdrawn whenever you need.
       <p style="margin-top:6px">Start with <b>₹500</b></p>`,
      [{ t: 'Add money', f: () => { state.act = 'add'; render(); $('#amtInput') && $('#amtInput').focus(); } }]));
  }
}

function banner(kind, icon, title, body, btns, req) {
  const b = el('div', 'banner ' + kind);
  b.innerHTML = `<div class="bi">${icon}</div><div style="flex:1">
    <h2>${esc(title)}${req ? `<span class="req">${esc(req)}</span>` : ''}</h2><p>${body}</p><div class="bb"></div></div>`;
  const bb = b.querySelector('.bb');
  (btns || []).forEach(x => { const n = el('button', 'bbtn', esc(x.t)); n.onclick = x.f; bb.appendChild(n); });
  return b;
}

/* ---------- three balances (REQ-101) ---------- */
function renderBalances(a, d) {
  const box = $('#three'); box.innerHTML = '';
  // Labels only. What each figure is made of is spelled out in the margin card
  // below, so repeating it here just competes with it.
  const cells = [
    { k: 'Margin Available', v: d.availableMargin, cls: 'primary' + (d.availableMargin < 0 ? ' neg' : '') },
    { k: 'Margin Blocked', v: d.usedMargin, cls: 'secondary' },
    { k: 'Cash', v: d.ledger, cls: 'secondary' + (d.ledger < 0 ? ' neg' : '') },
    { k: 'Withdrawable', v: d.withdrawable, cls: 'focus', why: true }
  ];
  cells.forEach(c => {
    const n = el('div', 'bal ' + c.cls);
    const b = big(c.v);
    n.innerHTML = `<div class="k">${esc(c.k)}</div>
      <div class="v">${b.r}<span class="p">${b.p}</span></div>`;
    if (c.why) {
      const w = el('button', 'whybtn', 'See breakdown');
      w.onclick = openDerivation;
      n.appendChild(w);
    }
    box.appendChild(n);
  });

  // name the largest deduction without making the user open anything (REQ-102)
  const h = $('#gapHint');
  if (d.inDebt) {
    h.innerHTML = `<span class="i">◆</span><div>Nothing is withdrawable while the balance is below zero</div>`;
  } else { h.innerHTML = ''; }
}

/* ---------- margin decomposition (REQ-103, REQ-106) ---------- */
const unrealisedOf = a => a.unrealisedLedger || 0;

function renderMargin(a, d) {
  const have = $('#marginTail'), used = $('#usedCard');
  const empty = d.ledger === 0 && d.collateral === 0 && d.usedMargin === 0;
  have.hidden = empty; used.hidden = empty;
  if (empty) return;

  const dsPct = a.unsettledCredits ? Math.round(a.used.delivery / a.unsettledCredits * 100) : 0;

  // row kinds: grp = group label (may carry a total) · acc = collapsible parent
  //            sub = its children · c = a deduction · meta = breakdown of the group
  const haveRows = [
    ['r', 'Opening balance', d.openingBalance, 'Funds cleared and available in your account at the start of today'],
    ['r', 'Payin Today', d.payinToday, 'Funds added today']
  ];

  const collRows = [
    ['grpx', 'Delivery sell benefit', a.unsettledCredits,
      `Today\u2019s delivery sale proceeds available for trading${
        a.used.delivery ? `, of which ${R(a.used.delivery)} is held as delivery sell margin` : ''}`, 'dsbOpen'],
    ['meta', 'Blocked as delivery sell margin', a.used.delivery,
     'Blocked until the shares are delivered. Counted once, inside Margin Blocked below'],
    ['meta', 'Free to trade with', a.unsettledCredits - a.used.delivery,
     'The part of today\u2019s sale proceeds not held back \u2014 usable for trading, but not withdrawable until settlement'],
    ['end'],
    ['r', 'Unrealised Ledger', unrealisedOf(a),
     'Trading balance yet to be credited. Counts toward your margin, but is not cash and cannot be withdrawn'],
    ['r', 'Net option premium', a.optionPremium || 0,
     'Premium received from options sold. Adds to margin available, not blocked funds'],
    ['grpx', 'Collateral', d.collateral, 'Pledged holdings after their haircut', 'collOpen'],
    ['r', 'Collateral (Equity)', a.collateral.equity, 'Value of your pledged equity holdings after the applicable haircut (risk reduction)'],
    ['r', 'Collateral (Liquid Funds)', a.collateral.liquid, 'Cash-equivalent value of your pledged liquid fund holdings after the applicable haircut'],
    ['grpx', 'Margin Blocked', d.usedMargin, '', 'usedOpen']
  ];

  const usedRows = [
    ['meta', 'Cash contribution', d.matrix.posCash + d.matrix.ordCash,
     'The part of the requirement your cash had to cover'],
    ['meta', 'Collateral contribution', d.matrix.posCollat + d.matrix.ordCollat,
     'Collateral can cover at most half the requirement, so the rest comes out of cash'],
    ['c', 'SPAN margin', a.used.span, 'Margin required by the exchange for your derivatives positions'],
    ['c', 'Exposure margin', a.used.exposure, 'Additional margin required by the exchange over and above SPAN margin'],
    ['c', 'Delivery sell margin', a.used.delivery,
      dsPct ? `${dsPct}% of today\u2019s delivery sale value, held until the shares are delivered`
            : 'Margin held until your sold shares are delivered'],
    ['c', 'Order margin', a.used.orderBlocks, 'Funds blocked for pending orders']
  ];

  $('#toggleZeros').textContent = state.hideZeros ? 'Show \u20b90 rows' : 'Hide \u20b90 rows';
  $('#toggleZeros').onclick = () => { state.hideZeros = !state.hideZeros; render(); };

  // 'out' closes the group above it, so Payout sits at the end of the card
  // without being read as part of Margin Blocked
  const payoutRow = ['out', 'Payout', Math.abs(d.payoutToday),
   'Money paid out to your bank today'];

  masterInfo('#usedInfoAll', collRows.concat(usedRows).concat([payoutRow]));

  paintRows($('#marginBody'), haveRows);
  paintRows($('#usedBody'), collRows
    .concat(state.usedOpen ? usedRows : [])
    .concat([payoutRow]));
}

function masterInfo(sel, rows) {
  const ia = $(sel);
  if (!ia) return;                       // control not present on this card
  const keys = rows.filter(r => r[0] !== 'grp' && r[3]).map(r => r[1]);
  const anyOpen = keys.some(k => state.openInfo[k]);
  ia.classList.toggle('on', anyOpen);
  ia.setAttribute('aria-expanded', String(anyOpen));
  ia.setAttribute('aria-label', (anyOpen ? 'Hide' : 'Show') + ' all descriptions');
  ia.title = (anyOpen ? 'Hide' : 'Show') + ' all descriptions';
  ia.onclick = () => { keys.forEach(k => { state.openInfo[k] = !anyOpen; }); render(); };
}

function paintRows(b, rows) {
  b.innerHTML = '';
  const wrap = el('div', 'rows');
  let nested = false, groupOpen = true;     // inside a grpx group, and is it open
  rows.forEach(row => {
    const t = row[0], label = row[1], val = row[2], desc = row[3], key = row[4];
    if (t === 'grp' || t === 'grpx') {
      nested = (t === 'grpx');
      groupOpen = t === 'grpx' ? !!state[key] : true;
      const g = el('div', 'grp' + (val != null ? ' grpv' : '') + (t === 'grpx' ? ' keep' : ''));
      const tot = val != null ? `<span class="grptot">${R(val)}</span>` : '';
      const tog = t === 'grpx'
        ? `<button class="accchev grptog" type="button" aria-expanded="${groupOpen}"
             aria-label="${groupOpen ? 'Collapse' : 'Expand'} ${esc(label)}">${groupOpen ? '\u2212' : '+'}</button>`
        : '';
      g.innerHTML = `<div class="grph"><span>${esc(label)}</span>${tot}${tog}</div>${
        desc ? infoDesc(label, desc) : ''}`;
      wrap.appendChild(g);
      if (t === 'grpx') g.querySelector('.grptog').onclick =
        () => { state[key] = !state[key]; render(); };
      return;
    }
    if (t === 'end') { nested = false; return; }      // closes a group, draws nothing
    if (t === 'out') nested = false;                  // closes the current group
    if (nested && !groupOpen) return;                 // hidden with its group
    const zero = val === 0;
    if (zero && state.hideZeros && t !== 'acc') return;

    const n = el('div', 'row'
      + (t === 'sub' ? ' sub' : '') + (t === 'acc' ? ' acc' : '')
      + (t === 'x' ? ' ctx' : '') + (t === 'meta' ? ' meta' : '')
      + (nested ? ' nested' : '') + (zero ? ' zero' : ''));

    // no per-row (i): the one beside each card heading reveals them all
    const body = t === 'acc'
      ? `<button class="accbtn" type="button" aria-expanded="${state.collOpen}">
           <span>${esc(label)}</span><span class="accchev">${state.collOpen ? '\u2212' : '+'}</span>
         </button>${desc ? infoDesc(label, desc) : ''}`
      : `${esc(label)}${t === 'x' ? '<span class="exclnote">(not part of this calc.)</span>' : ''}${desc ? infoDesc(label, desc) : ''}`;

    n.innerHTML = `<div class="rk">${body}</div>
                   <div class="rv ${t === 'c' || t === 'out' || val < 0 ? 'neg' : ''}${t === 'x' ? ' muted' : ''}">${
                     t === 'c' || t === 'out' ? R(val) : val < 0 ? Rs(val) : R(val)}</div>`;

    if (t === 'acc') n.querySelector('.accbtn').onclick =
      () => { state.collOpen = !state.collOpen; render(); };
    wrap.appendChild(n);
  });

  // a heading with nothing beneath it is dropped rather than left dangling
  const kids = wrap.children.slice ? wrap.children.slice() : [...wrap.children];
  kids.forEach((n, i) => {
    if (!n.className.includes('grp') || n.className.includes('keep')) return;
    const next = kids[i + 1];
    if (!next || next.className.includes('grp')) n.remove();
  });

  b.appendChild(wrap);
  wireInfo(wrap);
}

/* ---------- deployability (REQ-105) ---------- */
function renderDeploy(a, d) {
  const card = $('#deployCard');
  if (d.availableMargin <= 0) { card.hidden = true; return; }
  card.hidden = false;
  const b = $('#deployBody'); b.innerHTML = '';
  // Rows against a shared scale, largest first: the point is that the same
  // margin buys different amounts, so the shortfall has to be comparable.
  const max = Math.max(...d.deploy.map(x => x.v), 1);
  d.deploy.slice().sort((x, y) => y.v - x.v).forEach(x => {
    const pct = Math.round(x.v / max * 100);
    b.appendChild(el('div', 'deprow',
      `<span class="dk">${esc(x.k)}</span>
       <span class="depbar"><i style="width:${pct}%"></i></span>
       <span class="dv">${R(x.v)}</span>`));
    if (x.n) b.appendChild(el('div', 'depnote', esc(x.n)));
  });
}

/* ---------- transactions (REQ-401/402/403/404/405) ---------- */
function renderTx(a, d) {
  // tabs
  $('#txTabs').querySelectorAll('button').forEach(btn => {
    btn.className = btn.dataset.tab === state.txTab ? 'on' : '';
    btn.onclick = () => { state.txTab = btn.dataset.tab; render(); };
  });

  // range chips (Rule L6 — 30 days by default)
  const rr = $('#rangeRow'); rr.innerHTML = '';
  [[7, 'Last 7 days'], [30, 'Last 30 days'], [365, 'This financial year'], ['custom', 'Custom range']].forEach(([v, t]) => {
    const c = el('button', 'chip' + (state.range === v ? ' on' : ''), esc(t));
    c.onclick = () => { state.range = v; render(); };
    rr.appendChild(c);
  });
  if (state.range === 'custom') {
    const box = el('div', 'daterange');
    box.innerHTML = `<input type="date" id="dFrom" value="${state.from}" max="${isoOf(0)}" aria-label="From date">
                     <span>to</span>
                     <input type="date" id="dTo" value="${state.to}" max="${isoOf(0)}" aria-label="To date">`;
    rr.appendChild(box);
    box.querySelector('#dFrom').onchange = ev => { state.from = ev.target.value; render(); };
    box.querySelector('#dTo').onchange = ev => { state.to = ev.target.value; render(); };
  }

  // running balance computed across ALL entries, oldest first, then filtered
  const all = a.entries.filter(e => e.status === 'done').slice().sort((x, y) => y.ago - x.ago);
  let run = 0; const bal = {};
  all.forEach(e => { run += e.amt; bal[e.id] = run; });

  let list = a.entries.slice().sort((x, y) => x.ago - y.ago);
  /* Rule L5 — two views, two questions. "Where is my money" is payin and payout.
     "Explain my account" is every entry, including the ones no one requested:
     sale proceeds, mark-to-market, brokerage, depository charges. Without the
     second view those entries exist in the balance and nowhere the user can
     read them. */
  if (state.txTab !== 'all') list = list.filter(e => e.kind === state.txTab);
  const inRange = e => state.range === 'custom'
    ? (e.ago <= agoOf(state.from) && e.ago >= agoOf(state.to))
    : e.ago <= state.range;
  const shown = list.filter(inRange);

  /* REQ-407 — a statement the user can keep and give to someone else. It exports
     exactly what is on screen: the same tab, the same period, the same running
     balance. An export that quietly returned something else would be the one
     document the user cannot check against the page they exported it from. */
  const dl = el('button', 'dlcsv',
    '<svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">' +
    '<path d="M8 1v9M4.5 6.5 8 10l3.5-3.5M2 12.5v1A1.5 1.5 0 0 0 3.5 15h9a1.5 1.5 0 0 0 1.5-1.5v-1" ' +
    'fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
    '<span>Download CSV</span>');
  dl.onclick = () => downloadCsv(a, shown);
  dl.disabled = !shown.length;
  rr.appendChild(dl);

  const b = $('#txBody'); b.innerHTML = '';

  if (!shown.length) {                                            // Rule L7
    const wider = state.range === 7 ? 30 : state.range === 30 ? 365 : 99999;
    const wname = wider === 30 ? 'last 30 days' : wider === 365 ? 'this financial year' : 'all time';
    const e = el('div', 'empty');
    const word = state.txTab === 'payin' ? 'payins' : state.txTab === 'payout' ? 'payouts' : 'transactions';
    const when = state.range === 'custom' ? 'in that date range'
               : state.range === 365 ? 'this financial year'
               : `in the last ${state.range} days`;
    e.innerHTML = a.entries.length
      ? `<b>Nothing in this period</b>There are no ${word} ${when}`
      : `<b>No transactions yet</b>Add money to get started`;
    if (a.entries.length) {
      const btn = el('button', 'chip', `Look at ${wname} instead`);
      btn.style.marginTop = '10px';
      btn.onclick = () => { state.range = wider; render(); };
      e.appendChild(btn);
    }
    b.appendChild(e);
    return;
  }

  const wrap = el('div', 'tx');
  shown.forEach(e => {
    const n = el('div', 'txr' + (e.status === 'pending' ? ' pending' : '') + (e.reversedBy ? ' reversed' : '')
                 + (state.highlightId === e.id ? ' lit' : ''));
    n.setAttribute('data-id', e.id);
    const STATE = {
      pending:   ['pend', 'In progress'],
      failed:    ['fail', 'Failed'],
      unknown:   ['pend', 'Awaiting confirmation'],
      cancelled: ['rev', 'Cancelled'],
      rejected:  ['fail', 'Rejected'],
      returned:  ['fail', 'Returned']      // was falling through to no pill at all
    };
    const pills = [];
    const st = STATE[e.status];
    if (st) pills.push(`<span class="pill ${st[0]}">${st[1]}</span>`);
    // a completed movement says so too — silence used to be the only success signal.
    // Reversed entries and reversals are excluded; their own tag is the real story.
    // A request settled for less than it asked for is not "Paid" — that reads as
    // fulfilled, and the whole point of the end-of-day model is that it may not be.
    else if (e.status === 'done' && e.outcome === 'partial')
      pills.push('<span class="pill ok">Partly paid</span>');
    // Only money the user MOVED can succeed or fail. A brokerage charge or a
    // mark-to-market settlement simply happened, so labelling it "Successful"
    // adds a word without adding a fact.
    else if (e.status === 'done' && !e.reversedBy && !e.reverses
             && (e.kind === 'payin' || e.kind === 'payout'))
      pills.push(`<span class="pill ok">${e.kind === 'payout' ? 'Paid' : 'Successful'}</span>`);
    if (e.auto) pills.push('<span class="pill auto">Automatic settlement</span>');   // Rule L4
    const orig = e.reverses ? a.entries.find(x => x.id === e.reverses) : null;
    if (e.reverses) pills.push(`<span class="pill rev">Reverses an earlier ${
      orig && orig.amt < 0 ? 'debit' : 'credit'}</span>`);                                 // Rule L2
    if (e.reversedBy) pills.push('<span class="pill rev">Reversed</span>');
    let note = e.note || '';
    if (orig && orig.ref) note += (note ? '\n' : '') + 'Original transaction: Ref. ' + orig.ref;

    n.innerHTML = `
      <div><div class="t">${esc(e.t)} ${pills.join(' ')}</div>
        <div class="m">${daysLabel(e.ago)}${e.m ? ' · ' + esc(e.m) : ''}${e.ref ? ' · Ref ' + esc(e.ref) : ''}${
          note ? infoBtn('tx' + e.id, 'More about this transaction') : ''}</div>${
          note ? infoDesc('tx' + e.id, note) : ''}</div>
      <div><div class="a ${e.amt < 0 ? 'neg' : 'pos'}">${R(e.amt)}</div>
        <div class="b">${e.status === 'done' ? 'Bal ' + Rs(bal[e.id])
                        : e.status === 'pending' ? 'Not in your balance yet'
                        : 'Did not affect your balance'}</div></div>`;
    if (e.status === 'pending' && e.kind === 'payout') {           // REQ-305
      const c = el('button', 'chip'); c.textContent = 'Cancel this withdrawal';
      c.style.marginTop = '6px';
      c.onclick = () => cancelPayout(e);
      n.querySelector('div').appendChild(c);
    }
    wrap.appendChild(n);
  });
  b.appendChild(wrap);
  wireInfo(wrap);
}

/* One field, one column, quoted the same way every time. A statement that a
   spreadsheet mis-parses is a statement the user has to re-key. */
function csvCell(v) {
  const t = String(v == null ? '' : v).replace(/\s+/g, ' ').trim();
  return /[",\n]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t;
}

function downloadCsv(a, rows) {
  const head = ['Date', 'Description', 'Type', 'Status', 'Reference', 'Details',
                'Amount (INR)', 'Balance (INR)'];
  const bal = {};
  let run = 0;
  a.entries.filter(e => e.status === 'done').slice().sort((x, y) => y.ago - x.ago)
    .forEach(e => { run += e.amt; bal[e.id] = run; });

  const body = rows.map(e => [
    isoOf(e.ago),
    e.t,
    // Debit or Credit, not the internal kind. A statement is read against a bank
    // statement, and "payin" / "trade" are our words for our own plumbing.
    e.amt < 0 ? 'Debit' : 'Credit',
    e.status || 'done',
    e.ref || '',
    e.note || e.m || '',
    (e.amt / 100).toFixed(2),
    bal[e.id] === undefined ? '' : (bal[e.id] / 100).toFixed(2)
  ].map(csvCell).join(','));

  const csv = [head.join(','), ...body].join('\r\n');
  // BOM so Excel opens the rupee amounts and names as UTF-8 rather than mojibake
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `thinq-${state.txTab}-${isoOf(0)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast(`${rows.length} transaction${rows.length === 1 ? '' : 's'} downloaded as CSV.`);
}

const isoOf = n => { const d = new Date(); d.setDate(d.getDate() - n);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
const agoOf = iso => { if (!iso) return 99999;
  const t = new Date(); t.setHours(0, 0, 0, 0);
  return Math.round((t - new Date(iso + 'T00:00:00')) / 86400000); };

const daysLabel = n => {
  if (n === 0) return 'Today';
  if (n === 1) return 'Yesterday';
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: '2-digit' });
};

/* ---------- upcoming (REQ-307, REQ-503) ---------- */
function renderUpcoming(a, d) {
  const b = $('#upcoming'); b.innerHTML = '';
  const items = [];
  // Money leaving the account on a date nobody chose only matters to someone
  // thinking about taking money out. On the Add money tab it is an answer to a
  // question the user is not asking.
  if (state.act === 'out')
    items.push(['16 Oct', 'Unused funds will be automatically returned to your bank']);
  if (d.inDebt) items.push(['Every Mon', 'Interest is added while the balance is below zero']);
  b.hidden = !items.length;
  items.forEach(([dt, t], i) => b.appendChild(el('div', 'upline',
    `${i === 0 ? '<b>Coming up</b> \u00b7 ' : ''}<span class="ud">${esc(dt)}</span> \u2014 ${t}`)));
}

/* ==========================================================================
   ACTION PANEL
   ========================================================================== */
function renderAction(a, d) {
  $('#actTabs').querySelectorAll('button').forEach(btn => {
    btn.className = btn.dataset.act === state.act ? 'on' : '';
    btn.onclick = () => { state.act = btn.dataset.act; state.addMsg = null; state.outMsg = null; render(); };
  });
  const b = $('#actBody'); b.innerHTML = '';
  b.appendChild(state.act === 'add' ? addPanel(a, d) : outPanel(a, d));
}

/* ---------- add money ---------- */
function addPanel(a, d) {
  const w = el('div', 'act');

  if (!a.canReceive) {                                            // Rule H6 — blocker replaces the path
    w.innerHTML = `<div style="text-align:center;padding:14px 4px 6px">
      <div style="font-size:15px;font-weight:660;margin-bottom:6px">You cannot add money yet</div>
      <p class="msg note" style="margin-bottom:14px">${esc(a.blocker.name)}. The deposit form is not shown because it could not complete.</p></div>`;
    const cta = el('button', 'cta', esc(a.blocker.action));
    cta.onclick = () => alert('Goes to bank account verification. (REQ-505)');
    w.appendChild(cta);
    return w;
  }

  const owed = d.inDebt ? -d.ledger : 0;
  const amt = parseAmt(state.addAmt);
  /* An amount the chosen route cannot carry has exactly one sensible answer,
     and making the user open the accordion to apply it is a step with no
     decision in it. So the route moves itself — visibly, and reversibly: if the
     amount comes back under the original route's headroom, it moves back. */
  let route = ROUTES.find(r => r.id === state.addRoute);
  let switched = null;
  if (amt > 0) {
    const room = r => roomOn(a, r.id);
    if (amt > room(route)) {
      const fits = ROUTES.find(r => r.id !== route.id && amt <= room(r));
      if (fits) { state.autoRouteFrom = route.id; switched = route; route = fits; state.addRoute = fits.id; }
    } else if (state.autoRouteFrom) {
      const back = ROUTES.find(r => r.id === state.autoRouteFrom);
      if (back && amt <= room(back)) { route = back; state.addRoute = back.id; state.autoRouteFrom = null; }
    }
  }

  const spentToday = usedTodayOn(a, route.id);
  const headroom = roomOn(a, route.id);
  const overCap = amt > 0 && amt > headroom;
  const min = LIMITS.minAdd;
  const belowMin = amt > 0 && amt < min && !(owed > 0 && amt === owed);   // Rule H3 exception

  // amount
  w.innerHTML = `<div class="amt-l">Amount to add${owed ? ' · you owe ' + R(owed) : ''}</div>`;
  const bad = amtError(state.addAmt);
  const box = el('div', 'amtbox' + (overCap || belowMin || bad ? ' err' : ''));
  box.innerHTML = `<span class="cur">₹</span><input id="amtInput" inputmode="decimal" placeholder="0" value="${esc(state.addAmt)}" aria-label="Amount to add">`;
  w.appendChild(box);
  bindAmt(box.querySelector('input'), v => { state.addAmt = v; });

  // suggestions — Rule A1 (no pre-fill) + Rule A2 (say what they do)
  const exact = el('div', 'sugs exact');
  if (owed > 0) {
    const s = el('button', 'sug dues', `Pay exactly ${R(owed)}`);
    s.onclick = () => { state.addAmt = (owed / 100).toFixed(2); render(); };
    exact.appendChild(s);
  }
  if (a.shortfall > 0) {
    const s = el('button', 'sug dues', `Cover shortfall ${R(a.shortfall)}`);
    s.onclick = () => { state.addAmt = (a.shortfall / 100).toFixed(2); render(); };
    exact.appendChild(s);
  }
  if (exact.children.length) w.appendChild(exact);

  const sugs = el('div', 'sugs');
  // These add to the field rather than replacing it, so the label carries the
  // plus. The dues chips above still set an exact figure — "Pay exactly" and
  // "Cover shortfall" name a target, and adding to it would overshoot.
  [5000, 10000, 25000, 50000].forEach(v => {
    const s = el('button', 'sug', '+₹' + v.toLocaleString('en-IN'));
    s.onclick = () => { state.addAmt = addToAmt(state.addAmt, P(v)); render(); };
    sugs.appendChild(s);
  });
  w.appendChild(sugs);

  if (bad) w.appendChild(el('div', 'msg err', bad));
  // A route that moved itself has to say so — a silent change of method, and of
  // fee, is the app deciding something on the user's behalf without telling them.
  if (switched) w.appendChild(el('div', 'msg note',
    `Switched to <b>${esc(route.n)}</b> \u2014 ${esc(switched.n)} is capped at ` +
    `\u20b9${(switched.cap / 100).toLocaleString('en-IN')} per day.` +
    (route.fee ? ` ${esc(route.n)} charges ${R(route.fee)}.` : '')));
  if (belowMin) w.appendChild(el('div', 'msg err', `The smallest amount you can add is ${R(min)}.`));
  if (overCap) {
    const alt = ROUTES.find(r => r.cap > amt);
    // A cap is a round figure, so it is written as one — paise on a limit read
    // as precision the limit does not have.
    const cap = '\u20b9' + (route.cap / 100).toLocaleString('en-IN');
    const altName = alt && alt.n === 'Net banking' ? 'net banking' : alt && alt.n;
    w.appendChild(el('div', 'msg err',
      `${esc(route.n)} transfers are capped at ${cap} per day.` +
      (spentToday > 0 ? ` You have already added ${R(spentToday)} by ${esc(route.n)} today, so ${R(headroom)} is left.` : '') +
      (alt ? ` Use <b>${esc(altName)}</b> for larger amounts.` : '')));
  }

  // routes — REQ-202
  /* Both choices start closed. The route and the account are decided once and
     then repeated, so on a return visit they are settled facts rather than open
     questions — but a closed section must still say what it settled on, or the
     user cannot tell what the button is about to do. */
  const routeHd = accordion(w, 'How the money gets there', route.n, 'openRoute');
  const rs = el('div', 'routes');
  ROUTES.forEach(r => {
    const btn = el('button', 'route' + (r.id === state.addRoute ? ' on' : ''));
    const left = roomOn(a, r.id);
    const part = r.cap !== Infinity && left < r.cap;      // already partly used today
    const tooBig = amt > 0 && amt > left;
    const limitText = r.cap === Infinity ? ' \u00b7 no limit'
      : part ? ` \u00b7 ${R(left)} left of ${R(r.cap)} today`
             : ' \u00b7 up to ' + R(r.cap) + ' a day';
    btn.innerHTML = `<span class="rad"></span>
      <span><span class="rn">${esc(r.n)}</span><span class="rd">${esc(r.d)} · ${esc(r.eta)}${limitText}</span></span>
      <span class="rf">${r.fee === 0 ? '<span class="free">No fee</span>' : R(r.fee)}${tooBig ? '<small>over today\u2019s limit</small>' : ''}</span>`;
    btn.onclick = () => { state.addRoute = r.id; render(); };
    rs.appendChild(btn);
  });
  routeHd.appendChild(rs);

  // banks — REQ-203
  const bk0 = bankOf(a, state.addBank);
  const bankHd = accordion(w, 'From your bank account',
    bk0 ? `${bk0.name} \u2022\u2022${bk0.last4}` : '', 'openBank');
  const bs = el('div', 'banks');
  a.banks.forEach(bk => {
    const btn = el('button', 'bank' + (bk.id === state.addBank ? ' on' : ''));
    btn.innerHTML = `<span class="ic">▤</span><span><span class="bn">${esc(bk.name)}</span>
      <span class="bd">•••• ${esc(bk.last4)}</span></span>${bk.def ? '<span class="df">DEFAULT</span>' : ''}`;
    btn.onclick = () => { state.addBank = bk.id; render(); };
    bs.appendChild(btn);
  });
  bankHd.appendChild(bs);
  bankHd.appendChild(el('div', 'sughint', 'Only accounts you have proven you hold can be used'));

  // summary — what actually reaches the account
  if (amt > 0 && !overCap && !belowMin) {
    const s = el('div', 'summary');
    s.innerHTML = `
      <div class="row"><div class="rk">You pay</div><div class="rv">${R(amt)}</div></div>
      <div class="row"><div class="rk">${esc(route.n)} fee</div><div class="rv">${route.fee ? '−' + R(route.fee) : 'No fee'}</div></div>
      <div class="row sum"><div class="rk">Reaches your account</div><div class="rv">${R(amt - route.fee)}</div></div>
      <div class="row"><div class="rk">Usable for trading</div><div class="rv">${esc(route.eta)}</div></div>`;
    w.appendChild(s);
  }

  const ok = amt > 0 && !overCap && !belowMin && a.banks.length && !a.stale;
  const cta = el('button', 'cta', amt > 0 ? `Add ${R(amt)}` : 'Add money');
  cta.disabled = !ok;
  cta.onclick = () => doAdd(a, amt, route);
  w.appendChild(cta);

  if (a.stale) w.appendChild(el('div', 'cta-why', 'Margin data is stale, so money cannot be moved right now.'));

  if (state.addMsg) w.appendChild(el('div', 'msg ' + (state.addMsg.k || 'note'), state.addMsg.t));
  return w;
}

/* ---------- withdraw ---------- */
function outPanel(a, d) {
  const w = el('div', 'act');

  if (d.inDebt) {                                                 // REQ-301 -> REQ-501
    w.innerHTML = `<div style="text-align:center;padding:12px 4px 4px">
      <div style="font-size:15px;font-weight:660;margin-bottom:6px">Nothing to withdraw</div>
      <p class="msg note" style="margin-bottom:14px">Your balance is ${R(-d.ledger)} below zero. Clear this amount first; any funds you add above it will become available for withdrawal once settled.</p></div>`;
    const cta = el('button', 'cta', `Pay ${R(-d.ledger)}`);
    cta.onclick = () => { state.act = 'add'; state.addAmt = ((-d.ledger) / 100).toFixed(2); render(); };
    w.appendChild(cta);
    return w;
  }

  // Only one open request at a time. Showing a form that cannot submit would
  // be the dead end Rule H6 forbids, so the pending request replaces it.
  const openReq = a.entries.find(e => e.status === 'pending' && e.kind === 'payout');
  if (openReq) {
    w.innerHTML = `<div style="text-align:center;padding:14px 4px 2px">
      <p class="msg note" style="margin-bottom:14px"><b>${R(openReq.amt)}</b> withdrawal is already in progress.
      Cancel it to request another.</p></div>`;
    const c = el('button', 'cta ghost', 'Cancel withdrawal');
    c.onclick = () => cancelPayout(openReq);
    w.appendChild(c);
    if (state.outMsg) w.appendChild(el('div', 'msg ' + (state.outMsg.k || 'note'), state.outMsg.t));
    return w;
  }

  // Nothing can be withdrawn: replace the form rather than disable it (Rule H6).
  // State, cause, and the one action that changes it — the same three beats the
  // debt case uses, with the cause and the action varying.
  if (d.withdrawable === 0) {
    const short = a.shortfall > 0, empty = d.ledger === 0;
    const why = short
      ? `Your positions need ${R(a.shortfall)} more margin. Nothing can be withdrawn until the shortfall is cleared.`
      : empty
        ? 'Your account has no funds to withdraw'
        : `All your available cash is set aside as ${
            esc((d.worst ? d.worst.n : 'margin for your open positions').toLowerCase())}`;
    w.innerHTML = `<div style="text-align:center;padding:12px 4px 4px">
      <div style="font-size:15px;font-weight:660;margin-bottom:6px">Nothing to withdraw</div>
      <p class="msg note" style="margin-bottom:14px">${why}</p></div>`;
    let c;
    if (short)      { c = el('button', 'cta', `Add ${R(a.shortfall)}`); c.onclick = () => payNow(a, a.shortfall); }
    else if (empty) { c = el('button', 'cta', 'Add money'); c.onclick = () => { state.act = 'add'; render(); }; }
    else            { c = el('button', 'cta ghost', 'See breakdown'); c.onclick = openDerivation; }
    w.appendChild(c);
    if (state.outMsg) w.appendChild(el('div', 'msg ' + (state.outMsg.k || 'note'), state.outMsg.t));
    return w;
  }

  const amt = parseAmt(state.outAmt);
  const over = amt > d.withdrawable;

  // "Withdraw all" read as if it would withdraw on the spot; it only fills the
  // field. The ceiling is now stated, and the action says what it does.
  w.innerHTML = `<div class="amt-l">Amount to withdraw
    <button class="maxbtn" id="maxOut" type="button"${d.withdrawable ? '' : ' disabled'}>Full amount</button></div>`;
  const outBad = amtError(state.outAmt);
  const box = el('div', 'amtbox' + (over || outBad ? ' err' : ''));
  box.innerHTML = `<span class="cur">₹</span><input id="outInput" inputmode="decimal" placeholder="0" value="${esc(state.outAmt)}" aria-label="Amount to withdraw" ${d.withdrawable === 0 ? 'disabled' : ''}>`;
  w.appendChild(box);
  bindAmt(box.querySelector('input'), v => { state.outAmt = v; });
  w.querySelector('#maxOut').onclick = () => { if (d.withdrawable) { state.outAmt = (d.withdrawable / 100).toFixed(2); render(); } };

  if (outBad) w.appendChild(el('div', 'msg err', outBad));
  if (over) w.appendChild(el('div', 'msg err', `You can withdraw at most ${R(d.withdrawable)} right now.`));


  // destination
  if (a.banks.length) {
    // a native select: keyboard-operable, and collapses the list to one line
    const lab = el('label', 'sec-l plain', 'Will be sent to');
    lab.setAttribute('for', 'outBankSel');
    w.appendChild(lab);

    const sel = el('select', 'bankselect');
    sel.id = 'outBankSel';
    a.banks.forEach(bk => {
      const o = document.createElement('option');
      o.value = bk.id;
      o.textContent = `${bk.name} \u2022\u2022\u2022\u2022 ${bk.last4}${bk.def ? '  \u00b7  Default' : ''}`;
      if (bk.id === outBankOf(a)) o.selected = true;
      sel.appendChild(o);
    });
    sel.onchange = ev => { state.outBank = ev.target.value; render(); };
    w.appendChild(sel);
  }

  // arrival time computed from account state (REQ-303, Rule W5)
  if (amt > 0 && !over) {
    const et = arrival(a);
    const box2 = el('div', 'summary');
    box2.innerHTML = `<div class="row"><div class="rk">Expected to reach your bank</div><div class="rv">${esc(et.when)}</div></div>`;
    w.appendChild(box2);
  }

  const ok = amt > 0 && !over && !a.stale && a.banks.length;
  const cta = el('button', 'cta ghost', amt > 0 ? `Withdraw ${R(amt)}` : 'Withdraw');
  cta.disabled = !ok;
  cta.onclick = () => doOut(a, amt);
  w.appendChild(cta);

  // disabled-with-a-reason (REQ-301, Rule W1/W2)
  if (d.withdrawable === 0 && !a.stale) {
    w.appendChild(el('div', 'cta-why',
      d.ledger === 0
        ? 'No funds available'
        : `Nothing is withdrawable right now because of <b>${esc((d.worst ? d.worst.n : 'money committed to your positions').toLowerCase())}</b>`));
  }
  if (a.stale) w.appendChild(el('div', 'cta-why', 'Margin data is stale, so money cannot be moved right now.'));

  // Rule W3a — the amount can shrink, so the user learns that BEFORE committing,
  // not on the confirmation and not when a smaller sum arrives.
  else if (d.withdrawable > 0)
    w.appendChild(el('div', 'cta-why',
      'Your request is settled at the end of the day. <b>Whatever is available then is what will be sent</b>, ' +
      'so trading during the day may reduce it.'));

  if (state.outMsg) w.appendChild(el('div', 'msg ' + (state.outMsg.k || 'note'), state.outMsg.t));
  return w;
}

function arrival(a) {
  const why = [];
  let when = 'Tomorrow, by 6:00 pm';
  if (a.tradedToday) { when = 'In 2 working days'; why.push('you placed a trade today, so this waits for end-of-day settlement'); }
  if (a.ordersOpen) { when = 'In 2 working days'; why.push('you have an order that has not executed yet'); }
  const day = new Date().getDay();
  if (day === 0 || day === 6) { when = 'On the next working day'; why.push('today is not a working day on the trading calendar'); }
  if (!why.length) why.push('nothing in your account defers it');
  return { when, why };
}

/* ==========================================================================
   ACTIONS
   ========================================================================== */
/* Strip only what is formatting. Anything else makes the input invalid rather
   than being cleaned away: the old version deleted a leading minus, so "-500"
   silently became ₹500 and the button offered to add a number the user had not
   typed. A money field must never act on a value it did not display. */
function parseAmt(v) {
  const s = String(v).replace(/[₹,\s]/g, '');
  if (!/^\d*\.?\d*$/.test(s) || s === '' || s === '.') return 0;
  const n = parseFloat(s);
  return isFinite(n) && n > 0 ? Math.round(n * 100) : 0;
}
/* Reject the keystroke, do not accept it and complain afterwards. A money field
   that lets a minus sign land and then explains why it was wrong has already
   wasted the user's time; nothing but digits and one decimal point can be typed
   or pasted. The parser stays strict all the same — state can still be reached
   through a hand-edited URL. */
function sanitiseAmt(raw) {
  let v = String(raw).replace(/[^\d.]/g, '');       // drop signs, letters, symbols
  const i = v.indexOf('.');
  if (i > -1) v = v.slice(0, i + 1) + v.slice(i + 1).replace(/\./g, '');  // one point only
  const dot = v.indexOf('.');
  if (dot > -1) v = v.slice(0, dot + 3);            // paise, and no further
  return v;
}

/* Keeps the caret where the user left it when a character is refused, rather
   than throwing it to the end of the field. */
function bindAmt(input, set) {
  input.oninput = ev => {
    const el2 = ev.target, before = el2.value, after = sanitiseAmt(before);
    if (after !== before) {
      const pos = Math.max(0, (el2.selectionStart || 0) - (before.length - after.length));
      el2.value = after;
      try { el2.setSelectionRange(pos, pos); } catch (e) {}
    }
    set(after); softRender();
  };
}

/* Adds to whatever is in the field and gives back a string the field can hold:
   whole rupees stay whole, paise survive if the user typed any. */
function addToAmt(current, paise) {
  const total = parseAmt(current) + paise;
  return total % 100 === 0 ? String(total / 100) : (total / 100).toFixed(2);
}

const isTyped = v => String(v).trim() !== '';
const looksNumeric = v => {
  const s = String(v).replace(/₹|,|\s/g, '');
  return /^\d*\.?\d*$/.test(s) && s !== '' && s !== '.';
};
const isUnparseable = v => isTyped(v) && !looksNumeric(v);
const isZero        = v => isTyped(v) && looksNumeric(v) && parseAmt(v) === 0;
const amtError = v => isUnparseable(v) ? 'Enter an amount in rupees, using digits only.'
                    : isZero(v)        ? 'Enter an amount greater than zero.'
                    : null;


/* An attempt that has ENDED is reported once, in front of the user, and
   dismissed deliberately. An inline note under the button is the right weight
   for a request still in flight; it is the wrong weight for "your money did not
   move", which the user must not be able to scroll past.
   Kept out of here on purpose: the three payout states that are not failures
   (cut-off, review, rail down). Those requests are still alive and still
   cancellable, so interrupting with a red modal would say the opposite. */
/* The same window the outcome will appear in, opened the moment the user
   commits. Reusing one surface means the user watches their answer arrive
   rather than being interrupted by it: the logo spins, then becomes the result
   in place. It also holds the screen while the request is in flight, which is
   the one moment a second submit does real damage. */
function showPending({ title, amount, note }) {
  showResult({ kind: 'load', title, amount, why: note, actions: [] });
}

function showResult({ kind, title, amount, why, actions }) {
  const ic = $('#resIc');
  ic.className = 'res-ic ' + (kind === 'warn' ? 'warn' : kind === 'load' ? 'load'
                            : kind === 'good' ? 'good' : kind === 'timer' ? 'timer warn' : 'bad');
  ic.innerHTML = kind === 'load'
    ? '<span class="ring"></span><span class="brandmark">\u20b9</span>'
    : kind === 'timer'
    ? '<span class="cdring"></span><span class="cdnum"></span>' : '';
  $('#resTitle').textContent = title;
  $('#resAmt').textContent = amount == null ? '' : R(amount);
  $('#resAmt').style.display = amount == null ? 'none' : '';
  $('#resWhy').innerHTML = why;
  const box = $('#resAct'); box.innerHTML = '';
  (actions || []).forEach((x, i) => {
    const b = el('button', i === 0
      ? 'prim' + (kind === 'warn' ? ' warn' : kind === 'good' ? ' good' : '') : 'sec', esc(x.t));
    b.onclick = () => { closeResult(); if (x.f) x.f(); };
    box.appendChild(b);
  });
  box.hidden = !box.children.length;
  state.resLocked = kind === 'load';         // nothing dismisses a request in flight
  $('#resX').hidden = state.resLocked;       // no way out while the money is in motion
  $('#resScrim').hidden = false; $('#resMod').hidden = false;
  if (box.firstChild) box.firstChild.focus();
}
/* The destination is configuration, not a decision this module makes. */
/* Takes the user to the transaction this window is about, and marks it so it
   is not left to be found among the others. */
function viewTxn(e) {
  closeResult();
  state.act = 'add';
  state.txTab = e.kind === 'payout' ? 'payout' : 'payin';
  state.range = 30;
  state.highlightId = e.id;
  render();
  const row = document.querySelector('.txr[data-id="' + e.id + '"]');
  if (row) row.scrollIntoView({ block: 'center', behavior: 'smooth' });
  // the mark fades on the next interaction; it points, it does not persist
  setTimeout(() => { if (state.highlightId === e.id) { state.highlightId = null; render(); } }, 4000);
}

function goTrade() {
  const href = LIMITS.tradeNowHref;
  if (!href || href === '#') return;
  alert('In the real product this goes to ' + href +
        '.\n\nThe destination is configuration (REQ-709), not a value this module owns.');
}

/* An unknown outcome is the one state where waiting is the correct action, so
   the window waits with the user rather than handing them a button that would
   make things worse. The count runs down in front of them; only when it reaches
   zero, and the outcome is still unknown, does a way onward appear. */
let cdTimer = null;
function stopCountdown() { if (cdTimer) { clearInterval(cdTimer); cdTimer = null; } }

function runCountdown(seconds, onDone) {
  stopCountdown();
  const ic = $('#resIc');
  let left = seconds;
  const paint = () => {
    const num = ic.querySelector('.cdnum'), ring = ic.querySelector('.cdring');
    if (!num) return stopCountdown();
    num.textContent = left;
    if (ring) ring.style.setProperty('--p', (left / seconds * 100) + '%');
  };
  paint();
  cdTimer = setInterval(() => {
    left -= 1;
    if (left <= 0) { stopCountdown(); onDone(); return; }
    paint();
  }, 1000);
}

function closeResult() {
  stopCountdown();
  if (state.resLocked) return;
  $('#resScrim').hidden = true; $('#resMod').hidden = true;
}

function doAdd(a, amt, route) {
  const net = amt - route.fee;
  // The reference exists as soon as the attempt does. A failed payment is
  // precisely the one a user rings their bank about, so it cannot be the one
  // without a number to quote (Rule C18).
  const ref = 'PAY' + (100000 + Math.floor(Math.random() * 899999));
  const e = E(0, 'Funds added', net, 'payin',
    { m: `${route.n} · ${(a.banks.find(b => b.id === state.addBank) || a.banks[0]).name}`, status: 'pending', ref });
  a.entries.push(e);
  state.addMsg = null;
  state.addAmt = '';
  render();
  showPending({ title: 'Confirming with your bank', amount: amt,
    note: 'This usually takes a few seconds. It is not in your balance yet \u2014 Rule A5.' });

  setTimeout(() => {
    const o = outcomeFor(PAYIN_OUTCOMES, state.forcePayin);
    if (o.st !== 'done') {
      e.status = o.st;
      e.t = o.st === 'unknown' ? 'Funds added — awaiting confirmation' : 'Funds added — failed';
      e.m = `${route.n} · ${o.row}`;
      /* Retry means the same route, the same amount and the same account —
         the attempt the user actually made, not a fresh form to refill.
         The alternative is whichever OTHER route can still carry the amount
         today. That test is what keeps UPI off a ₹5,00,000 retry: it is capped,
         so it is not eligible, and offering it would send the user into a
         second failure we could see coming. Headroom, not the raw cap, because
         a route may be part-used already. */
      const room = r => roomOn(a, r.id);
      /* An alternative is only offered if pressing it actually pays: a route we
         execute, with enough headroom left today. NEFT fails the first test —
         the button would promise a payment and deliver a set of instructions.
         Where nothing qualifies, Try Again stands alone and the panel behind
         still lists every route with what each one requires. */
      const alt = o.altKind === 'none' ? null
                : ROUTES.find(r => r.id !== route.id && !r.selfService && amt <= room(r));
      const why = textOf(o);
      state.addMsg = null;
      // a payment that did not go through is one the user is about to try again,
      // so put the amount back rather than making them retype it (Rule A1)
      const unknown = o.st === 'unknown';
      if (!unknown) state.addAmt = String(amt / 100);
      // No Retry on an unknown outcome — the copy tells the user not to pay
      // again, and a button that invites it would contradict the sentence above
      // it. The corner ✕ is the only way out of that one.
      const acts = unknown ? []
        : [{ t: o.retry || 'Try Again', f: () => { state.addRoute = route.id; doAdd(a, amt, route); } }]
          .concat(alt ? [{
            t: `Use ${alt.n}`,
            f: () => { state.addRoute = alt.id; doAdd(a, amt, alt); }
          }] : []);
      state.resLocked = false;
      showResult({
        kind: unknown ? 'timer' : 'bad',
        title: unknown ? 'Awaiting confirmation' : 'Transaction failed',
        amount: amt, why, actions: acts
      });
      // Rule A5 — the money is in flight, so the only honest offer is to look
      // at where it is. That offer is withheld until the wait is genuinely over.
      if (unknown) runCountdown(30, () => showResult({
        kind: 'warn', title: 'Awaiting confirmation', amount: amt, why,
        actions: [{ t: 'View Status', f: () => viewTxn(e) }]
      }));
    } else {
      e.status = 'done';
      state.addAmt = String(net / 100);          // Rule A1 — the next default
      if (a.shortfall > 0) {
        a.shortfall = Math.max(0, a.shortfall - net);
        a.used.span = Math.max(0, a.used.span);
        state.resLocked = false;
        state.addMsg = null;
        showResult({ kind: 'good', title: 'Funds added', amount: net,
          why: a.shortfall === 0
            ? 'Your shortfall is cleared and your positions are no longer at risk of being closed.'
            : `Applied to your shortfall. ${R(a.shortfall)} is still short.`,
          actions: [{ t: 'Trade Now', f: goTrade }] });
      } else {
        state.addMsg = null;
        state.resLocked = false;
        showResult({ kind: 'good', title: 'Funds added', amount: net,
          why: `Added to your account from ${esc(bankOf(a, state.addBank).name)} ` +
               `\u2022\u2022${esc(bankOf(a, state.addBank).last4)}.`,
          actions: [{ t: 'Trade Now', f: goTrade }] });
      }
    }
    render();
  }, 1500);
}

/* Rule A1 — the field opens on what the user last added, from the account they
   last added it from. Adding funds is a repeated act with a stable amount far
   more often than not, so an empty field asks the same question every time and
   accepts the same answer every time. It is a default, not a decision: the
   field is editable, the pills adjust it, and clearing it costs one keystroke. */
function lastPayin(a) {
  return a.entries
    .filter(e => e.kind === 'payin' && e.amt > 0 && (e.status || 'done') === 'done')
    .slice().sort((x, y) => x.ago - y.ago)[0] || null;
}
function defaultAdd(a) {
  const e = lastPayin(a);
  if (!e) return { amt: '', bank: null };
  const l4 = (String(e.m || '').match(/(\d{4})\s*$/) || [])[1];
  const bank = (a.banks || []).find(b => b.last4 === l4);
  return { amt: String(e.amt / 100), bank: bank ? bank.id : null };
}

/* A titled section that collapses. Closed, the header carries the current
   choice so nothing has to be opened to know what is selected. */
function accordion(parent, title, current, key) {
  const open = !!state[key];
  const hd = el('button', 'acc-hd' + (open ? ' open' : ''));
  hd.type = 'button';
  hd.setAttribute('aria-expanded', String(open));
  hd.innerHTML = `<span class="acc-t">${esc(title)}</span>` +
    (current && !open ? `<span class="acc-c">${esc(current)}</span>` : '') +
    `<span class="acc-x" aria-hidden="true"></span>`;
  hd.onclick = () => { state[key] = !state[key]; render(); };
  parent.appendChild(hd);
  const body = el('div', 'acc-bd');
  body.hidden = !open;
  parent.appendChild(body);
  return body;
}

const bankOf = (a, id) => a.banks.find(b => b.id === id) || a.banks.find(b => b.def) || a.banks[0];
const outBankOf = a => (a.banks.some(b => b.id === state.outBank) ? state.outBank
                       : (a.banks.find(b => b.def) || a.banks[0] || {}).id);

/* Submitting a withdrawal and settling it are two events separated by hours,
   and only the first one has a user in front of it. Everything the payout run
   decides — sent, sent short, not sent, returned by the bank — happens during
   end-of-day processing with nobody watching, so none of it can be a modal.
   Those outcomes reach the user the way they actually would: a message, and a
   transaction that has changed by the time they next open the screen. */
function doOut(a, amt) {
  const d = derive(a);
  if (a.entries.some(e => e.status === 'pending' && e.kind === 'payout')) {
    state.outMsg = { k: 'err', t: 'You already have a withdrawal in progress. Only one can be open at a time.' };
    return render();
  }
  if (amt > d.withdrawable) { state.outMsg = { k: 'err', t: 'That is more than you can withdraw.' }; return render(); }

  const bank = `${bankOf(a, outBankOf(a)).name} \u2022\u2022${bankOf(a, outBankOf(a)).last4}`;
  const e = E(0, 'Withdrawal', -amt, 'payout',
    { m: `To ${bank} \u00b7 ${arrival(a).when.toLowerCase()}`, status: 'pending' });
  a.entries.push(e);
  e._amt = amt; e._requested = amt;
  state.outAmt = '';
  state.outMsg = null;
  render();
  showPending({ title: 'Submitting withdrawal request', amount: amt,
    note: 'You can keep trading with this money until the end of the day.' });

  // Only what is known at submission can be shown now.
  setTimeout(() => {
    const o = outcomeFor(SUBMIT_OUTCOMES, state.forceSubmit);
    state.resLocked = false;
    if (o.row) e.m = `To ${bank} \u00b7 ${o.row}`;
    showResult({ kind: o.modal.kind, title: o.modal.title, amount: amt,
      why: textOf(o).replace('{bank}', bank), actions: [{ t: 'Done' }] });
    render();
  }, 1400);
}

/* The payout run. No modal, by definition — this is a batch job at the close of
   the day, and the user is not here. */
function runEOD() {
  const a = state.acct;
  const open = a.entries.filter(x => x.kind === 'payout' && x.status === 'pending');
  if (!open.length) { toast('No withdrawal request is open, so the payout run had nothing to settle.'); return; }
  const o = outcomeFor(EOD_OUTCOMES, state.forceEod);
  open.forEach(e => {
    const bank = `${bankOf(a, outBankOf(a)).name} \u2022\u2022${bankOf(a, outBankOf(a)).last4}`;
    const asked = e._requested || -e.amt;
    const utr = () => 'UTR' + (1000000 + Math.floor(Math.random() * 8999999));
    if (o.id === 'sent') {
      e.status = 'done'; e.ref = utr(); e.m = bank;
    } else if (o.id === 'partial') {
      const sent = Math.round(asked * 0.4);
      e.status = 'done'; e.outcome = 'partial'; e.amt = -sent; e.ref = utr();
      e.m = bank;                       // the meta line stays standard for every row
      // A shortfall the user did not choose needs its reason attached to the row
      // itself. Finding it in an email is not the same as finding it here.
      e.note = `You requested ${R(asked)}, but only ${R(sent)} was available at the end-of-day ` +
               `payout. ${R(sent)} was processed, and the request is now closed.`;
    } else if (o.id === 'nothing') {
      e.status = 'failed'; e.outcome = 'notsent'; e.m = `To ${bank}`;
      e.note = `You requested ${R(asked)}, but no funds were available at the end-of-day payout. ` +
               `Nothing was processed, and the request is now closed.`;
    } else if (o.id === 'bankreject') {
      e.status = 'returned'; e.outcome = 'notaccepted'; e.m = `To ${bank}`;
      e.note = `Your ${R(asked)} transfer was returned by your bank. Nothing was deducted. ` +
               `Please check your bank account details with your bank before requesting the ` +
               `withdrawal again.`;
    } else {                                   // still open — carried to the next run
      e.outcome = 'raildown';
      e.m = `To ${bank}`;
      e.note = `The banking network was unavailable at the end-of-day payout, so this could not be ` +
               `sent today. Your request is still open and goes out with the next payout run. ` +
               `You can still cancel it.`;
    }
  });
  state.outMsg = null;
  render();
  toast('End-of-day payout run complete. See the transaction, and <b>What Thinq sends</b> for the messages.');
}

function cancelPayout(e) {
  const a = state.acct;
  a.entries = a.entries.filter(x => x.id !== e.id);
  state.outMsg = null;
  render();
  toast('Withdrawal cancelled. No bank transfer will be made for this request.');
}

/* ==========================================================================
   DERIVATION DRAWER — REQ-102, Rule B4
   ========================================================================== */
function openDerivation() {
  const d = derive(state.acct);
  $('#drawerTitle').textContent = 'Withdrawable balance';
  const b = $('#drawerBody'); b.innerHTML = '';

  // the show/hide-all control lives beside the drawer heading
  const anyOpen = d.terms.some(t => t.x && state.openInfo[t.n]);
  const ia = $('#drawerInfoAll');
  ia.hidden = false;
  ia.classList.toggle('on', anyOpen);
  ia.setAttribute('aria-expanded', String(anyOpen));
  ia.setAttribute('aria-label', (anyOpen ? 'Hide' : 'Show') + ' all descriptions');
  ia.title = (anyOpen ? 'Hide' : 'Show') + ' all descriptions';
  ia.onclick = () => { d.terms.forEach(t => { if (t.x) state.openInfo[t.n] = !anyOpen; }); openDerivation(); };

  // Each line carries the operator that applies to it, so the arithmetic reads
  // in the list itself rather than only in the formula underneath.
  d.terms.filter(t => !t.excluded).forEach((t, i) => {
    const op = i === 0 ? '' : t.sign === -1 ? '\u2212' : t.sign === 1 ? '+' : (t.v < 0 ? '\u2212' : '+');
    const vcls = t.v < 0 ? 'neg' : t.v > 0 && t.sign === 1 ? 'pos' : '';
    b.appendChild(el('div', 'dterm',
      `<div class="dt"><span class="dop">${op}</span><span class="dn">${esc(t.n)}${t.x ? infoBtn(t.n) : ''}</span>
        <span class="dv ${vcls}">${R(t.v)}</span></div>
       ${t.x ? infoDesc(t.n, t.x) : ''}`));
  });
  wireInfo(b);

  b.appendChild(el('div', 'dsum',
    `<span class="dop">=</span><span class="dn">Withdrawable balance</span><span class="dv">${R(d.withdrawable)}</span>`));

  // shown for context, below the total, where it cannot read as a line of the sum
  d.terms.filter(t => t.excluded).forEach(t => {
    b.appendChild(el('div', 'dterm excl',
      `<div class="dt"><span class="dop">\u2014</span><span class="dn">${esc(t.n)}${t.x ? infoBtn(t.n) : ''}</span>
        <span class="dv muted">${R(t.v)}</span></div>
       <div class="dexcl">Not part of this calculation</div>
       ${t.x ? infoDesc(t.n, t.x) : ''}`));
  });

  if (d.clamped) {
    b.appendChild(el('div', 'dnote',
      `The terms above come to ${Rs(d.raw)}. Nothing is withdrawable, so the figure shown is ₹0.00 — but the arithmetic is shown as it is rather than hidden.`));
  }

  // the same sum again, as an equation the reader can add up themselves
  const W = 14;
  const frows = [];
  d.terms.forEach((t, i) => {
    if (t.excluded) return;
    const op = i === 0 ? ' ' : (t.v < 0 ? '\u2212' : '+');
    frows.push(op + ' ' + R(Math.abs(t.v)).padStart(W) + '  ' + t.n.toLowerCase());
  });
  frows.push(' ' + '\u2500'.repeat(W));
  frows.push('= ' + R(d.withdrawable).padStart(W) + '   withdrawable');
  const excl = d.terms.find(t => t.excluded);
  if (excl && excl.v !== 0) frows.push('\n ' + R(excl.v) + ' added today is tradable now,\n  but not withdrawable until tomorrow');
  b.appendChild(el('div', 'dformula', esc(frows.join('\n'))));

  b.appendChild(el('div', 'dnote',
    `<b>Why pledged margin adds rather than subtracts.</b> Your account first sets aside the full margin your positions require against cash. Because your pledged holdings covered part of it, that cash was never actually locked — so it is added back here.`));

  $('#scrim').hidden = false; $('#drawer').hidden = false;
  $('#drawerX').focus();
}
function closeDrawer() { $('#scrim').hidden = true; $('#drawer').hidden = true; }

/* ==========================================================================
   COMMUNICATIONS — product-requirements-communications.md
   REQ-621: every message is generated from the same derive() result as the
   screen. That requirement is only real if the messages are built here, from
   the same inputs, rather than written out separately and kept in step by hand.
   ========================================================================== */

/* GSM-7 is the 7-bit alphabet an SMS uses when it can. It does NOT contain ₹.
   One rupee sign forces the whole message to UCS-2, which drops the segment
   from 160 characters to 70 — so the 136-character shortfall message would
   become two. Every template says "Rs" for that reason, not for style. */
const GSM7_BASE = "@\u00a3$\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\n\u00d8\u00f8\r\u00c5\u00e5\u0394_\u03a6\u0393\u039b\u03a9\u03a0\u03a8\u03a3\u0398\u039e\u00c6\u00e6\u00df\u00c9 !\"#\u00a4%&'()*+,-./0123456789:;<=>?\u00a1ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00c4\u00d6\u00d1\u00dc\u00a7\u00bfabcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00f1\u00fc\u00e0";
const GSM7_EXT  = "\f^{}\\[~]|\u20ac";                    // each costs two characters

function smsMetrics(text) {
  let units = 0, gsm = true;
  for (const ch of text) {
    if (GSM7_BASE.indexOf(ch) > -1) units += 1;
    else if (GSM7_EXT.indexOf(ch) > -1) units += 2;
    else { gsm = false; break; }
  }
  if (!gsm) {
    const n = [...text].length;
    return { enc: 'UCS-2', units: n, cap: 70, segments: Math.ceil(n / 70) };
  }
  return { enc: 'GSM-7', units, cap: 160,
           segments: units <= 160 ? 1 : Math.ceil(units / 153) };
}

/* Rule C14 — every message carries a reference: one trailer, byte for byte,
   in every template */
const TRAILER = ref => `Ref: ${ref} -Thinq`;
const RS = p => R(p).slice(1);                    // "38,400.00" — no ₹, see above

const SMS = {
  shortfall:        (amt, by, ref) => `Your Thinq account has a margin shortfall of Rs ${amt}. Add funds by ${by} to avoid your positions being closed. ${TRAILER(ref)}`,
  shortfallCleared: (ref)          => `Margin shortfall cleared. No action needed. ${TRAILER(ref)}`,
  squaredOff:       (n, amt, ref)  => `${n} position(s) closed due to a margin shortfall of Rs ${amt}. See order book for details. ${TRAILER(ref)}`,
  dues:             (amt, ref)     => `Your Thinq account has Rs ${amt} due. Trading and withdrawals are blocked until it is cleared. ${TRAILER(ref)}`,
  duesCleared:      (amt, ref)     => `Rs ${amt} received. Your Thinq account dues are cleared and trading is enabled. ${TRAILER(ref)}`,

  /* Money movement carries no SMS at all — §6 and §7. What is left here is the
     two action states, where SMS is the channel that reaches everyone. */
};

const SUPPORT_TEL = '800-XXX-XXXX';
const last4 = e => (String(e.m || '').match(/(\d{4})\s*$/) || [, '0000'])[1];

/* One entry in, one message out. Rule C3 decides which channel carries which
   outcome and Rule C2 is why none of them is SMS; REQ-616 is why a cancelled
   withdrawal has no SMS row at all. */
function commsForEntry(e) {
  const amt = RS(e.amt), st = e.status || 'done';
  // A payout's e.ref holds the bank's UTR, which is the bank's identifier for the
  // transfer. Our own reference is a different thing and must not reuse it —
  // quoting one where the other belongs sends the user to the wrong party.
  const ref = e.kind === 'payin' ? (e.ref || 'PA4188') : 'PO7742';
  const utr = String(e.ref || 'UTR9930241').replace(/^UTR/, '');
  if (e.kind === 'payin') {
    // §6 — WhatsApp for what the user does not know, email for the receipt.
    // WhatsApp may carry ₹; only SMS is bound by GSM-7.
    // Rule C4 — where WhatsApp is the only channel, email is the fallback, not
    // the silence. The rule id stays in the comment; it is not copy.
    const wa = (rung, tpl, body, btns) => ({ rung, ch: 'WhatsApp', tpl, body, btns,
      fallback: 'Email, if this user has no WhatsApp opt-in' });
    if (e.amt < 0) return wa('Payin reversed', 'thinq_payin_reversed_v1',
      `*Your bank returned ${R(e.amt)}*\n\nThe funds added on ${daysLabel(e.ago)} have been reversed, and your balance has been adjusted. ` +
      `The reversal was initiated by your bank. If you did not expect this, please check with your bank.\n\nReference: ${ref}`,
      ['View transactions', 'Add money']);
    if (st === 'failed') return wa('Payin failed', 'thinq_payin_failed_v1',
      `*${R(e.amt)} fund addition failed*\n\nNo money was debited from your bank account. If your bank shows a debit, the amount will be automatically reversed within 3 working days.\n\nReference: ${ref}`,
      ['Try again', 'View transactions']);
    if (st === 'pending' || st === 'unknown') return wa('Payin pending', 'thinq_payin_pending_v1',
      `*${R(e.amt)} fund addition pending*\n\nYour bank has not confirmed the payment yet. If the amount was debited but the payment does not go through, it will be returned to your bank within 3 working days.\n\n` +
      `We’ll update you here once the payment is confirmed.\n\nRef ${ref}`,
      ['View transactions']);
    // Rule C19 — email is the only channel that may use structure, so this one
    // is built as markup rather than text. Before/after come from derive() so
    // the figures cannot drift from the screen (REQ-621).
    const dNow = derive(state.acct);
    const method = String(e.m || '').split(' \u00b7 ')[0] || 'UPI';
    const route = ROUTES.find(r => r.n === method);
    const fee = route && route.fee ? R(route.fee) : 'None';
    const from = String(e.m || '').split(' \u00b7 ')[1] || '';
    const row = (k, v) => `<tr><th>${esc(k)}</th><td>${esc(v)}</td></tr>`;
    const num = (k, b4, af) => `<tr><th>${esc(k)}</th><td class="n">${b4}</td><td class="n">${af}</td></tr>`;
    return { rung: 'Payin succeeded', ch: 'Email',
      subj: `${R(e.amt)} added to your Thinq account`,
      html:
        `<p>Hello Nikhil,</p><p><b>${R(e.amt)} was added to your Thinq account.</b></p>` +
        `<h4 class="eh">Transaction details</h4><table class="etbl">` +
          row('Received', daysLabel(e.ago) + ', 10:42 AM') +
          row('From', from || 'HDFC Bank \u2022\u2022\u2022\u20224471') +
          row('Method', method) + row('Fee', fee) + row('Reference', ref) +
        `</table>` +
        `<p class="ecta"><span>Start trading</span></p>` +
        // Before/after is only arithmetic for a payin that has just moved the
        // balances. For an older one the current figures reflect everything that
        // happened since, so subtracting the amount would invent a number.
        (e.ago === 0
          ? `<h4 class="eh">What this changed</h4><table class="etbl num">` +
            `<tr><td></td><th class="n">Before</th><th class="n">After</th></tr>` +
            num('Margin available', R(dNow.availableMargin - e.amt), R(dNow.availableMargin)) +
            `</table>` +
            `<p>Money added today is available for trading today, but cannot be withdrawn until it ` +
            `settles tomorrow. That\u2019s why your withdrawable amount has not changed.</p>`
          : `<p>This money has settled and is available to trade and to withdraw.</p>`) +
        `<p>\u2014 Thinq</p>` };
  }
  // §7 — every withdrawal outcome is email; only 'paid' also gets WhatsApp.
  const l4 = last4(e), when = daysLabel(e.ago);
  const dest = String(e.m || '').replace(/^To /, '');
  const mail = (rung, subj, lead, rows, tail, cta) => ({ rung, ch: 'Email', subj,
    html: `<p>Hello Nikhil,</p><p><b>${lead}</b></p>` +
      (rows ? `<h4 class="eh">${rows.h}</h4>` + (rows.lead ? `<p>${rows.lead}</p>` : '') +
        `<table class="etbl">` +
        rows.r.map(([k, v]) => `<tr><th>${esc(k)}</th><td>${esc(v)}</td></tr>`).join('') +
        `</table>` : '') +
      (cta ? `<p class="ecta"><span>${esc(cta)}</span></p>` : '') +
      (tail ? (/^\s*<p/.test(tail) ? tail : `<p>${tail}</p>`) : '') + `<p>— Thinq</p>` });

  if (st === 'cancelled') return mail('Withdrawal cancelled',
    `Withdrawal of ${R(e.amt)} cancelled`,
    `Your ${R(e.amt)} withdrawal has been cancelled.`,
    { h: 'Withdrawal details', r: [['Cancelled', when], ['To', dest], ['Reference', ref]] },
    'The amount will not be sent to your bank.');

  if (e.outcome === 'partial') {
    const asked = e._requested || -e.amt;
    return [{ rung: 'Withdrawal partly sent', ch: 'WhatsApp', tpl: 'thinq_payout_partial_v1',
      body: `*${R(e.amt)} withdrawal sent to your bank*\n\nYou requested a ${R(asked)} withdrawal. ` +
            `${R(e.amt)} was available when the withdrawal was processed at the end of the day, so ` +
            `${R(e.amt)} was sent to ${dest}.\n\n` +
            `UTR ${utr} — If the amount hasn’t arrived by tomorrow, quote this number when ` +
            `contacting your bank.\n\nReference: ${ref} — Keep this reference for your records.`,
      btns: ['View transactions'],
      // Rule C4 — email behind WhatsApp, never silence.
      fallback: 'Email carries the same event with the full transfer record' },
    mail('Withdrawal partly sent',
      `${R(e.amt)} of your ${R(asked)} withdrawal was sent`,
      `${R(e.amt)} of your ${R(asked)} withdrawal request has been sent to your bank.`,
      { h: 'Transfer details',
        lead: 'Your request was processed against the balance available at the end of the day. ' +
              'The request is now closed.',
        r: [['Amount requested', R(asked)], ['Amount sent', R(e.amt)], ['To', dest],
            ['UTR', utr], ['Reference', ref]] },
      'Place a new request whenever you are ready.', 'View transactions')];
  }

  if (e.outcome === 'notsent') return mail('Withdrawal not sent',
    `Your withdrawal of ${R(e.amt)} could not be sent`,
    `Nothing was sent to your bank because there were no available funds when your withdrawal ` +
    `was processed.`,
    { h: 'What happened',
      lead: 'Withdrawals are settled once at the end of the day based on your available-to-withdraw ' +
            'balance at that time. Trading or other activity during the day can reduce this balance.',
      r: [['Amount requested', R(e.amt)], ['Amount sent', R(0)], ['To', dest], ['Reference', ref]] },
    'Your request is now closed. Place a new one whenever you are ready.', 'Check balance');

  if (e.outcome === 'raildown') return mail('Withdrawal queued',
    `Your withdrawal of ${R(e.amt)} is queued`,
    `Your withdrawal of ${R(e.amt)} could not be sent today.`,
    { h: 'What happened',
      lead: 'The banking network was unavailable when the payout was processed. Your request is ' +
            'still open and will be sent in the next payout run once the network is available.',
      r: [['Amount requested', R(e.amt)], ['To', dest], ['Reference', ref]] },
    'No action is needed. You can still cancel the request until it is processed.', 'View withdrawal');

  /* Insufficient balance is no longer a rejection — the payout run sends what is
     there and closes the request (Rule W3). What remains is a rejection on
     review, which is a decision rather than an arithmetic outcome, so it names
     no figure and offers no retry. */
  if (st === 'rejected') return mail('Withdrawal rejected',
    `Withdrawal of ${R(e.amt)} was rejected`,
    `Your withdrawal of ${R(e.amt)} was not processed, and nothing was sent to your bank.`,
    { h: 'Withdrawal details', lead: 'This request did not clear review.',
      r: [['Amount requested', R(e.amt)], ['To', dest], ['Reference', ref]] },
    'Contact customer support if you need this explained.', 'Contact support');

  if (st === 'returned') return mail('Withdrawal returned',
    `${R(e.amt)} withdrawal was returned by your bank`,
    `Your bank could not accept the ${R(e.amt)} withdrawal, and the full amount has been returned ` +
    `to your Thinq account.`,
    { h: 'Transfer details', r: [['Attempted', when], ['To', dest], ['Reference', ref]] },
    'Please check your bank account details before requesting the withdrawal again.', 'Check bank details');

  if (st === 'pending') return mail('Withdrawal requested',
    `Withdrawal request for ${R(e.amt)} received`,
    `A withdrawal of ${R(e.amt)} has been requested from your Thinq account.`,
    { h: 'Withdrawal details', r: [['Amount requested', R(e.amt)], ['Requested', when + ', 11:04 AM'],
                                   ['To', dest], ['Expected by', 'Tomorrow'], ['Reference', ref]] },
    `<p>You can keep trading with these funds until the end of the day. The amount available at the ` +
    `end of the day will be sent to your bank and may be less than the amount requested.</p>` +
    `<p>You can cancel the withdrawal from <b>Funds</b> until it is processed.</p>` +
    `<p>If you didn\u2019t request this withdrawal, <a href="tel:${SUPPORT_TEL}">contact customer ` +
    `support</a> immediately.</p>`,
    'View withdrawal');

  /* Rule W8 is two messages for one movement: the return is announced before the
     date, executed on it, notified after it. Rule C10 governs the copy of both —
     money leaving an account nobody touched names the reason in its first clause.
     Only the announcement can still be acted on, so it goes on the action channel
     with email behind it (Rule C4); the return itself is a record, so it is email
     (Rule C3). Neither carries an SMS: money movement is not one of the two states
     SMS is reserved for (Rule C2). */
  if (e.auto) return [
    { rung: 'Announced \u2014 three working days before', ch: 'WhatsApp',
      tpl: 'thinq_rac_advance_notice_v1',
      body: `*${R(e.amt)} of unused funds will be returned to your bank on ${when}*\n\n` +
            `Brokers are required to return funds left unused, on a set calendar. You did not ` +
            `request this, nothing is wrong, and no action is needed.\n\n` +
            `It will go to ${dest}. Money you are trading with is not returned, so if you would ` +
            `rather keep it here, use it before ${when}.\n\nReference: ${ref}`,
      btns: ['View balance', 'Start trading'],
      fallback: 'Email, if this user has no WhatsApp opt-in' },
    mail('Unused funds returned',
      `${R(e.amt)} unused funds returned \u2014 monthly settlement`,
      `${R(e.amt)} of unused funds was returned to your bank as part of the mandated settlement cycle.`,
      { h: 'Transfer details', r: [['Sent', when], ['To', dest], ['UTR', utr], ['Reference', ref]] },
      'You did not request this. Brokers are required to return unused funds on a set calendar, ' +
      'and the money is yours to add back whenever you need it.', 'Add money')];

  const dNow = derive(state.acct);
  return [{ rung: 'Withdrawal paid', ch: 'WhatsApp', tpl: 'thinq_payout_paid_v1',
    body: `*Withdrawal of ${R(e.amt)} sent to your bank*\n\nYour withdrawal of ${R(e.amt)} was sent to ` +
          `${dest || 'your bank'} on ${when}. Your bank usually credits it within a few hours.\n\n` +
          `UTR ${utr} — If the amount hasn’t arrived by tomorrow, quote this number when ` +
            `contacting your bank.\n\nReference: ${ref} — Keep this reference for your records.`,
    btns: ['View transactions'],
    // Rule C4 — email behind WhatsApp, never silence.
    fallback: 'Email carries the same event with the full transfer record' },
  { rung: 'Withdrawal paid', ch: 'Email',
    subj: `${R(e.amt)} withdrawal sent to ${dest || 'your bank'}`,
    html: `<p>Hello Nikhil,</p><p><b>${R(e.amt)} has been sent to your bank.</b></p>` +
      `<h4 class="eh">Transfer details</h4><table class="etbl">` +
        `<tr><th>Requested</th><td>${esc(when)}, 11:04 AM</td></tr>` +
        `<tr><th>Sent</th><td>${esc(when)}, 4:32 PM</td></tr>` +
        `<tr><th>To</th><td>${esc(dest)}</td></tr>` +
        `<tr><th>UTR</th><td>${esc(utr)}</td></tr>` +
        `<tr><th>Reference</th><td>${esc(ref)}</td></tr></table>` +
      `<p class="ecta"><span>View withdrawal</span></p>` +
      `<p>Your bank usually credits the amount within a few hours. If it has not appeared by ` +
      `tomorrow, quote the <b>UTR</b> to your bank — it is the reference they can use to trace ` +
      `the transfer.</p>` +
      `<h4 class="eh">What this changed</h4><table class="etbl num">` +
        `<tr><td></td><th class="n">Before</th><th class="n">After</th></tr>` +
        `<tr><th>Withdrawable</th><td class="n">${R(dNow.withdrawable - e.amt)}</td>` +
        `<td class="n">${R(dNow.withdrawable)}</td></tr></table>` +
      `<p>— Thinq</p>` }];
}

const deadlineOf = a => String((a.cause && a.cause.until) || '').replace(/\s*today$/, '').toUpperCase();

/* Which messages are live for the account as it currently stands.
   Rule C1: nothing urgent goes out on one channel. Rule C17: amounts in full. */
function commsFor(a, d) {
  const out = [];
  const push = (m) => out.push(m);

  if (a.shortfall > 0) {                                        // REQ-601
    const ref = 'MS8841', amt = RS(a.shortfall), by = deadlineOf(a);
    push({ rung: 'On detection', ch: 'SMS', body: SMS.shortfall(amt, by, ref) });
    push({ rung: 'On detection', ch: 'WhatsApp', tpl: 'thinq_margin_shortfall_v1',
      body: `⚠️ *Margin shortfall — ${R(a.shortfall)}*\n\n${a.cause.why}.\n\n` +
            `Your positions need ${R(a.shortfall)} more margin. Add funds by ${by}, or we may close some positions to release margin.\n\n` +
            `${a.cause.alt || ''}\n\nRef ${ref}`,
      btns: [`Add ${R(a.shortfall)}`, 'See breakdown'] });
    push({ rung: 'On detection', ch: 'Email',
      subj: `Action needed: ${R(a.shortfall)} margin shortfall — clear by ${by} today`,
      body: `Your account is short of margin by ${R(a.shortfall)}.\n\n` +
            `WHY THIS HAPPENED\n${a.cause.why}.\n\n` +
            `WHERE THE NUMBER COMES FROM\n` +
            `  Margin your positions require   ${R(d.usedMargin + a.shortfall)}\n` +
            `  Margin available                ${R(d.availableMargin)}\n` +
            `  Shortfall                       ${R(a.shortfall)}\n\nRef ${ref}` });
    push({ rung: 'Deadline − 15 min', ch: 'SMS',
      body: SMS.shortfall(amt, by + ' (15 min left)', ref) });
    push({ rung: 'Outcome — cleared', ch: 'SMS', body: SMS.shortfallCleared(ref) });   // Rule C12
    push({ rung: 'Outcome — squared off', ch: 'SMS', body: SMS.squaredOff(2, amt, ref) });
    return out;
  }

  if (d.inDebt) {                                               // Rule C2
    const owed = -d.ledger, ref = 'DU2207', amt = RS(owed);
    const low = owed < P(500) && !(a.cause && a.cause.kind === 'payin');
    push({ rung: 'Day 0', ch: 'Email',
      subj: `${R(owed)} due — ${(a.cause && a.cause.t) || 'amount due on your account'}`,
      body: `${R(owed)} is due on your account.\n\nWHY THIS HAPPENED\n${a.cause.why}.\n\n` +
            `WHAT THIS AFFECTS\nTrading and withdrawals are on hold until the balance is back above zero.\n\n` +
            `WHAT YOU CAN DO\nPay ${R(owed)} to settle your dues.\n\nRef ${ref}` });
    if (low) {
      push({ rung: 'Day 0', ch: '—', note:
        `Nothing else goes out today. ${R(owed)} is under ${R(P(500))} and there is no push channel, ` +
        `so email carries day 0 alone and the banner does the rest.` });
      push({ rung: 'Day 14', ch: 'SMS', body: SMS.dues(amt, ref) });
    } else {
      push({ rung: 'Day 0', ch: 'SMS', body: SMS.dues(amt, ref) });
      push({ rung: 'Day 0', ch: 'WhatsApp', tpl: 'thinq_account_dues_v1',
        body: `*${R(owed)} due on your account*\n\n${a.cause.why}.\n\n${a.cause.add || ''}\n\n` +
              `Interest accrues daily until this is cleared, and trading and withdrawals stay blocked.\n\nRef ${ref}`,
        btns: [`Pay ${R(owed)}`, 'View statement'] });
    }
    push({ rung: 'Outcome — cleared', ch: 'SMS', body: SMS.duesCleared(amt, ref) });
    return out;
  }

  return out;
}

/* Money movement, generated from the account's own entries so a message can
   never disagree with the list above about an amount (REQ-621). */
function commsForMovement(a) {
  const seen = {};
  return a.entries
    .filter(e => (e.kind === 'payin' || e.kind === 'payout') && e.ago <= 30)
    .slice().sort((x, y) => x.ago - y.ago)
    .map(commsForEntry)
    .flatMap(m => Array.isArray(m) ? m : [m])
    .filter(m => { const k = m.rung + '|' + m.ch;
                   if (seen[k]) return false; seen[k] = 1; return true; });
}

function openComms() {
  const a = state.acct, d = derive(a);
  const msgs = commsFor(a, d).concat(commsForMovement(a));
  $('#drawerTitle').textContent = 'What Thinq sends';
  const ia = $('#drawerInfoAll'); if (ia) ia.hidden = true;
  const b = $('#drawerBody'); b.innerHTML = '';

  if (!msgs.length) {
    b.appendChild(el('p', 'dnote',
      'Nothing is queued for this account. Nothing has moved, and neither action state applies.'));
  }

  let rung = null;
  msgs.forEach(m => {
    if (m.rung !== rung) { rung = m.rung; b.appendChild(el('div', 'crung', esc(rung))); }
    const c = el('div', 'cmsg');
    const head = `<div class="chd"><span class="cch ${m.ch === 'SMS' ? 'sms' : m.ch === 'Email' ? 'eml' : m.ch === 'WhatsApp' ? 'wa' : 'nil'}">${esc(m.ch)}</span>` +
      (m.tpl ? `<code>${esc(m.tpl)}</code>` : '') +
      (m.ch === 'SMS' ? (() => {
        const k = smsMetrics(m.body);
        const ok = k.enc === 'GSM-7' && k.segments === 1;
        return `<span class="cseg ${ok ? '' : 'bad'}">${k.units} chars · ${k.enc} · ${k.segments} segment${k.segments > 1 ? 's' : ''}</span>`;
      })() : '') + '</div>';
    c.innerHTML = head +
      (m.subj ? `<div class="csub">${esc(m.subj)}</div>` : '') +
      (m.note ? `<p class="dnote" style="margin:0">${esc(m.note)}</p>`
              : m.html ? `<div class="cmail">${m.html}</div>`
              : `<pre class="cbody">${esc(m.body)}</pre>`) +
      (m.btns ? `<div class="cbtns">${m.btns.map(x => `<span>${esc(x)}</span>`).join('')}</div>` : '') +
      (m.fallback ? `<div class="cfall">Fallback \u00b7 ${esc(m.fallback)}</div>` : '');
    b.appendChild(c);
  });

  b.appendChild(el('div', 'dnote',
    '<b>Every figure above came from <code>derive()</code>.</b> REQ-621 asks that a message never ' +
    'disagree with the screen about an amount. Generating both from one definition is the only ' +
    'way that holds without someone remembering to check.'));

  $('#scrim').hidden = false; $('#drawer').hidden = false;
  $('#drawerX').focus();
}

/* ---------- keep focus/caret while typing ---------- */
function softRender() {
  const id = document.activeElement && document.activeElement.id;
  const pos = document.activeElement && document.activeElement.selectionStart;
  render();
  if (id) { const n = document.getElementById(id); if (n) { n.focus(); try { n.setSelectionRange(pos, pos); } catch (_) {} } }
}


/* ---------- UX-102: view state lives in the URL, so it survives a reload
     and can be linked to. Also the fix for the deep-linking problem this
     project's own competitor teardown called out. ---------- */
function readUrl() {
  const p = new URLSearchParams(location.hash.slice(1));
  const sc = p.get('scen');
  if (sc && SCENARIOS[sc]) state.scen = sc;
  const tab = p.get('tab');
  if (tab === 'payin' || tab === 'payout') state.txTab = tab;
  const rgRaw = p.get('range');
  if (rgRaw === 'custom') state.range = 'custom';
  else { const rg = parseInt(rgRaw, 10); if ([7, 30, 365].indexOf(rg) > -1) state.range = rg; }
  if (p.get('from')) state.from = p.get('from');
  if (p.get('to')) state.to = p.get('to');
  const act = p.get('act');
  if (act === 'add' || act === 'out') state.act = act;
  if (p.get('cause')) state.variant = p.get('cause');
}
function writeUrl() {
  const p = new URLSearchParams();
  p.set('scen', state.scen); p.set('tab', state.txTab);
  p.set('range', String(state.range)); p.set('act', state.act);
  if (state.variant) p.set('cause', state.variant);
  if (state.range === 'custom') { p.set('from', state.from); p.set('to', state.to); }
  history.replaceState(null, '', '#' + p.toString());
}

/* ---------- boot ---------- */
const cb = $('#commsBtn'); if (cb) cb.onclick = openComms;
$('#resScrim').onclick = closeResult;
$('#resX').onclick = closeResult;
$('#scrim').onclick = closeDrawer;
$('#drawerX').onclick = closeDrawer;
document.addEventListener('keydown', e => { if (e.key === 'Escape') { closeDrawer(); closeResult(); } });
state.from = isoOf(30); state.to = isoOf(0);   // custom range opens on the last month
readUrl();
if (VARIANTS[state.scen] && !VARIANTS[state.scen][state.variant])
  state.variant = Object.keys(VARIANTS[state.scen])[0];
state.acct = SCENARIOS[state.scen](state.variant);
{ // Rule A1 — the default applies on a cold load too, not only on a scenario switch
  const dflt = defaultAdd(state.acct);
  if (!state.addAmt) state.addAmt = dflt.amt;
  if (!state.addBank) state.addBank = dflt.bank;
}
render();
