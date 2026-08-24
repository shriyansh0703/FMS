/* Emits the message catalogue for product-requirements-communications.md
   straight out of app.js, so the PRD cannot drift from what the module sends.
   Run:  ./gen-comms.sh                                                        */
const out = [];
const p = s => out.push(s);
const bar = s => String(s).replace(/\|/g, '\\|');

p('<!-- BEGIN GENERATED — do not edit by hand. Run ./web/gen-comms.sh -->');
p('');
p('## 4. The message catalogue');
p('');
p('Generated from `web/app.js`. Every string below is the string the module sends: the');
p('catalogue is emitted from the same definitions the running code reads, so a copy change');
p('lands here without anyone remembering to update it.');
p('');

/* ---------- SMS ---------- */
p('### 4.1 SMS — the two action states only');
p('');
p('Money movement carries no SMS at all (§5, §6). What remains is margin shortfall and dues,');
p('where the message has to reach everyone.');
p('');
p('| Template | Chars | Encoding | Segments |');
p('|---|---:|---|---:|');
const smsList = [
  ['THINQ_MARGIN_SHORTFALL', SMS.shortfall('38,400.00', '2:30 PM', 'MS8841')],
  ['THINQ_SHORTFALL_CLEARED', SMS.shortfallCleared('MS8841')],
  ['THINQ_SQUARED_OFF', SMS.squaredOff(2, '38,400.00', 'MS8841')],
  ['THINQ_DUES_OUTSTANDING', SMS.dues('24.37', 'DU2207')],
  ['THINQ_DUES_CLEARED', SMS.duesCleared('24.37', 'DU2207')]
];
smsList.forEach(([tpl, body]) => {
  const k = smsMetrics(body);
  p(`| \`${tpl}\` | ${k.units} | ${k.enc} | ${k.segments} |`);
});
p('');
smsList.forEach(([tpl, body]) => {
  p(`**\`${tpl}\`**`);
  p('');
  p('```');
  p(body);
  p('```');
  p('');
});

/* ---------- payin ---------- */
p('### 4.2 Adding funds — outcomes');
p('');
p('| Outcome | Entry becomes | Primary action | Message |');
p('|---|---|---|---|');
PAYIN_OUTCOMES.filter(o => o.t).forEach(o => {
  p(`| ${bar(o.label)} | \`${o.st}\` | ${bar(o.retry || 'Try Again')} | ${bar(textOf(o))} |`);
});
p('');
p('The alternative action offered beside it is whichever **other route we can execute** still has');
p('headroom for the amount today. A self-service route is never offered as a one-tap recovery —');
p('the button would promise a payment and deliver a set of instructions.');
p('');

/* ---------- withdrawal ---------- */
p('### 4.3 Withdrawal — on submit');
p('');
p('The only moment with a user in front of it.');
p('');
p('| Outcome | Window title | Message |');
p('|---|---|---|');
SUBMIT_OUTCOMES.forEach(o => {
  p(`| ${bar(o.label)} | ${bar(o.modal.title)} | ${bar(textOf(o).replace('{bank}', 'the destination account'))} |`);
});
p('');
p('### 4.4 Withdrawal — at end of day');
p('');
p('Decided by the payout run hours later, with nobody watching. **None of these is a dialog.**');
p('They reach the user as a message and as a transaction that has changed by the time they');
p('next open the screen.');
p('');
p('| Outcome | Transaction shows | Request |');
p('|---|---|---|');
const closes = { sent: 'closed', partial: 'closed', nothing: 'closed',
                 bankreject: 'closed', raildown: '**stays open and cancellable**' };
EOD_OUTCOMES.forEach(o => {
  p(`| ${bar(o.label)} | ${bar(o.row || 'sent in full')} | ${closes[o.id]} |`);
});
p('');

/* ---------- mandated settlement ---------- */
p('### 4.5 Mandated settlement \u2014 announced before, notified after');
p('');
p('Rule W8 is two messages for one movement: the return is **announced before the date**,');
p('executed on it, and **notified after it**. Rule C10 governs the copy of both. Neither carries');
p('an SMS \u2014 money movement is not one of the two states SMS is reserved for (Rule C2). The');
p('settlement date itself comes from the mandated calendar, so it appears below as a slot.');
p('');
p('| Rung | Channel | Template | Fallback |');
p('|---|---|---|---|');
const racEntry = SCENARIOS.trader().entries.find(e => e.kind === 'payout' && e.auto);
const racDate = daysLabel(racEntry.ago);
const slot = t => String(t).split(racDate).join('{settlement date}');
const rac = [].concat(commsForEntry(racEntry));
rac.forEach(m => {
  p(`| ${bar(m.rung)} | ${m.ch} | ${m.tpl ? '`' + m.tpl + '`' : '\u2014'} | ${bar(m.fallback || '\u2014')} |`);
});
p('');
rac.forEach(m => {
  p(m.tpl ? `**\`${m.tpl}\`**` : `**Email \u2014 subject line**`);
  p('');
  p('```');
  p(slot(m.body || m.subj));
  p('```');
  p('');
});

p('<!-- END GENERATED -->');

console.log(out.join('\n'));
