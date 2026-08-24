/**
 * Checks for the parts of `fms-client.ts` that hold real logic: money parsing, the period guard,
 * filename extraction, and the four response shapes a generated client gets wrong — 204, an empty
 * 404, an uncapped route, and the error body.
 *
 * Run with `node --test docs/api/fms-client.test.ts` (Node 22.6+ strips the types natively).
 * No framework, no fixtures, no network: `fetch` is injected.
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  FmsError,
  assertValidPeriod,
  createFmsClient,
  filenameFromContentDisposition,
  formatMoney,
  isUncapped,
  paise,
  rupeesToPaise,
} from './fms-client.ts';
import type { Entry, PayoutRequestResponse, RouteHeadroomResponse } from './fms-client.ts';

// --- a fetch that records what it was asked for and answers from a script ---

function stubFetch(script: Array<{ status: number; body?: string; headers?: Record<string, string> }>) {
  const calls: Array<{ url: string; method: string; auth: string | null }> = [];
  let next = 0;

  const fetchImpl: typeof globalThis.fetch = async (input, init) => {
    const request = new Request(input as RequestInfo, init);
    calls.push({
      url: request.url,
      method: request.method,
      auth: request.headers.get('Authorization'),
    });
    const step = script[Math.min(next++, script.length - 1)];
    return new Response(step.body ?? null, {
      status: step.status,
      headers: { 'Content-Type': 'application/json', ...(step.headers ?? {}) },
    });
  };

  return { fetchImpl, calls };
}

const client = (script: Parameters<typeof stubFetch>[0]) => {
  const { fetchImpl, calls } = stubFetch(script);
  return {
    api: createFmsClient({ baseUrl: 'https://fms.example/', authorization: 'Basic dGVzdDpzZWNyZXQ=', fetch: fetchImpl }),
    calls,
  };
};

// --- money ------------------------------------------------------------------

test('rupees parse to paise without touching a float', () => {
  assert.equal(rupeesToPaise('123.45'), 12345);
  assert.equal(rupeesToPaise('1,234.5'), 123450);
  assert.equal(rupeesToPaise('  50 '), 5000);
  assert.equal(rupeesToPaise('-12.01'), -1201);
  // 0.1 + 0.2 territory: the string path is exact where a float multiply is not.
  assert.equal(rupeesToPaise('0.29'), 29);
});

test('a third decimal place is refused rather than rounded', () => {
  assert.throws(() => rupeesToPaise('1.234'), RangeError);
  assert.throws(() => rupeesToPaise('abc'), RangeError);
  assert.throws(() => rupeesToPaise(''), RangeError);
});

test('paise() refuses a non-integer before it can reach the server', () => {
  assert.deepEqual(paise(12345), { paise: 12345, currency: 'INR' });
  assert.throws(() => paise(100.9), RangeError);
});

test('formatMoney renders two places in the Indian numbering system', () => {
  assert.equal(formatMoney({ paise: 12345, currency: 'INR' }, { symbol: false }), '123.45');
  assert.equal(formatMoney({ paise: 10000000, currency: 'INR' }, { symbol: false }), '1,00,000.00');
});

// --- the period guard -------------------------------------------------------

test('a period needs both bounds or neither', () => {
  assert.doesNotThrow(() => assertValidPeriod());
  assert.doesNotThrow(() => assertValidPeriod('2026-07-23', '2026-08-21'));
  assert.throws(() => assertValidPeriod('2026-07-23', undefined), RangeError);
  assert.throws(() => assertValidPeriod(undefined, '2026-08-21'), RangeError);
});

test('an inverted or over-wide period is refused before the request', () => {
  assert.throws(() => assertValidPeriod('2026-08-21', '2026-08-01'), RangeError);
  assert.throws(() => assertValidPeriod('2020-01-01', '2026-08-21'), /92-day maximum/);
  // Exactly 92 days apart is the boundary and it is allowed.
  assert.doesNotThrow(() => assertValidPeriod('2026-05-21', '2026-08-21'));
});

// --- Content-Disposition ----------------------------------------------------

test('the filename comes from the server, with a fallback', () => {
  assert.equal(
    filenameFromContentDisposition('attachment; filename="statement-2026-07-23-to-2026-08-21.csv"'),
    'statement-2026-07-23-to-2026-08-21.csv',
  );
  assert.equal(filenameFromContentDisposition('attachment; filename=plain.csv'), 'plain.csv');
  assert.equal(
    filenameFromContentDisposition("attachment; filename*=UTF-8''statement%20aug.csv"),
    'statement aug.csv',
  );
  assert.equal(filenameFromContentDisposition(null), 'statement.csv');
});

// --- the four shapes a generated client gets wrong ---------------------------

test('204 on the open request becomes null, not a parse error', async () => {
  const { api } = client([{ status: 204 }]);
  assert.equal(await api.getOpenPayout(), null);
});

test('an empty-bodied 404 on the detail endpoint becomes null', async () => {
  const { api } = client([{ status: 404 }]);
  assert.equal(await api.getTransaction('NOPE'), null);
});

test('an uncapped route stays null and is not confused with zero', async () => {
  const body: RouteHeadroomResponse = {
    routes: [
      { route: 'UPI', remainingToday: { paise: 10000000, currency: 'INR' }, fee: { paise: 0, currency: 'INR' } },
      { route: 'NEFT', remainingToday: null, fee: { paise: 0, currency: 'INR' } },
    ],
  };
  const { api } = client([{ status: 200, body: JSON.stringify(body) }]);

  const limits = await api.getPayinLimits();
  assert.equal(isUncapped(limits.routes[0]), false);
  assert.equal(isUncapped(limits.routes[1]), true);
});

test('a refusal arrives as an FmsError carrying the figures behind it', async () => {
  const { api } = client([
    {
      status: 422,
      body: JSON.stringify({
        code: 'amount_exceeds_withdrawable',
        message: 'requested 50000000 against a withdrawable figure of 8000000',
        details: {
          requested: { paise: 50000000, currency: 'INR' },
          withdrawable: { paise: 8000000, currency: 'INR' },
        },
      }),
    },
  ]);

  await assert.rejects(
    () => api.requestPayout({ amount: paise(50000000), destinationRef: 'acc-4471' }),
    (error: unknown) => {
      assert.ok(error instanceof FmsError);
      assert.equal(error.code, 'amount_exceeds_withdrawable');
      assert.equal(error.status, 422);
      assert.equal(error.isRetryable, false);
      assert.equal(error.withdrawableFigures?.withdrawable.paise, 8000000);
      return true;
    },
  );
});

test('a cancel refusal exposes which of the three reasons it was', async () => {
  const { api } = client([
    {
      status: 409,
      body: JSON.stringify({
        code: 'not_cancellable',
        message: 'request 4242 has been instructed to the payout rail and can no longer be stopped',
        details: { reason: 'ALREADY_INSTRUCTED' },
      }),
    },
  ]);

  await assert.rejects(
    () => api.cancelPayout(4242),
    (error: unknown) => {
      assert.ok(error instanceof FmsError);
      assert.equal(error.cancelReason, 'ALREADY_INSTRUCTED');
      return true;
    },
  );
});

test('a 503 is marked retryable and names no vendor', async () => {
  const { api } = client([
    { status: 503, body: JSON.stringify({ code: 'upstream_unavailable', message: 'an upstream service is unavailable' }) },
  ]);

  await assert.rejects(
    () => api.listTransactions(),
    (error: unknown) => {
      assert.ok(error instanceof FmsError);
      assert.equal(error.isRetryable, true);
      return true;
    },
  );
});

test('a failure with no parseable body still throws an FmsError', async () => {
  // What a gateway ahead of this service can answer with. Parsing it as JSON would crash.
  const { api } = client([{ status: 502, body: '<html>bad gateway</html>' }]);

  await assert.rejects(
    () => api.getPayinLimits(),
    (error: unknown) => {
      assert.ok(error instanceof FmsError);
      assert.equal(error.status, 502);
      assert.equal(error.code, 'unknown');
      return true;
    },
  );
});

// --- request construction ----------------------------------------------------

test('every request carries the credential verbatim and the base URL has no double slash', async () => {
  const { api, calls } = client([{ status: 204 }]);
  await api.getOpenPayout();

  assert.equal(calls[0].url, 'https://fms.example/api/v1/funds/payout');
  // Verbatim, with no scheme added by the client: the service enforces Basic today and that is
  // provisional, so a client that composed the scheme itself would need editing when it changes.
  assert.equal(calls[0].auth, 'Basic dGVzdDpzZWNyZXQ=');
});

test('query parameters are sent only when supplied', async () => {
  const empty = JSON.stringify({ view: 'MOVEMENTS', period: { from: '', to: '' }, entries: [], suggestedWiderPeriod: null });

  const bare = client([{ status: 200, body: empty }]);
  await bare.api.listTransactions();
  assert.equal(bare.calls[0].url, 'https://fms.example/api/v1/funds/transactions');

  const full = client([{ status: 200, body: empty }]);
  await full.api.listTransactions({ view: 'ALL_ENTRIES', from: '2026-07-23', to: '2026-08-21' });
  assert.equal(
    full.calls[0].url,
    'https://fms.example/api/v1/funds/transactions?view=ALL_ENTRIES&from=2026-07-23&to=2026-08-21',
  );
});

test('a reference with URL-unsafe characters is escaped into the path', async () => {
  const { api, calls } = client([{ status: 404 }]);
  await api.getTransaction('VCH/44 71');
  assert.equal(calls[0].url, 'https://fms.example/api/v1/funds/transactions/VCH%2F44%2071');
});

test('a decimal amount is refused by the client, before any request is made', async () => {
  const { api, calls } = client([{ status: 201 }]);

  await assert.rejects(
    () => api.requestPayout({ amount: { paise: 100.9, currency: 'INR' }, destinationRef: 'acc-4471' }),
    RangeError,
  );
  assert.equal(calls.length, 0, 'nothing should have been sent');
});

test('a created payout round-trips with its state and masked destination intact', async () => {
  const created: PayoutRequestResponse = {
    requestId: 4242,
    fmsReference: 'FMS-2026-0821-4242',
    arrivalDateQuoted: '2026-08-25',
    shrinkWarningKey: 'WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT',
    state: 'ACCEPTED',
    withdrawableAtRequest: { paise: 8000000, currency: 'INR' },
    destinationMasked: '••••4471',
  };
  const { api, calls } = client([{ status: 201, body: JSON.stringify(created) }]);

  const result = await api.requestPayout({ amount: paise(5000000), destinationRef: 'acc-4471' });
  assert.equal(calls[0].method, 'POST');
  assert.deepEqual(result, created);
  assert.doesNotMatch(result.destinationMasked, /\d{9,}/);
});

test('the statement download returns the blob and the server-chosen filename', async () => {
  const { api } = client([
    {
      status: 200,
      body: 'Date,Description,Type,Reference,Amount,Balance\r\n2026-08-19,Funds added,Credit,SETT-1,50000.00,132000.00\r\n',
      headers: {
        'Content-Type': 'text/csv; charset=UTF-8',
        'Content-Disposition': 'attachment; filename="statement-2026-07-23-to-2026-08-21.csv"',
      },
    },
  ]);

  const file = await api.downloadStatement({ view: 'ALL_ENTRIES', from: '2026-07-23', to: '2026-08-21' });
  assert.equal(file.filename, 'statement-2026-07-23-to-2026-08-21.csv');
  assert.match(await file.blob.text(), /^Date,Description,Type,Reference,Amount,Balance\r\n/);
});

test('an entry keeps its reversal links and string-typed description parameters', async () => {
  const entry: Entry = {
    reference: 'VCH-4471',
    date: '2026-08-19',
    kind: 'PAYIN',
    descriptionKey: 'ENTRY_PAYIN',
    descriptionParameters: { amountPaise: '5000000', direction: 'IN', segment: 'NSE_CASH' },
    secondaryDetail: 'SETT-2026-0819-11',
    amount: { paise: 5000000, currency: 'INR' },
    direction: 'IN',
    runningBalance: { paise: 13200000, currency: 'INR' },
    segment: 'NSE_CASH',
    userCaused: true,
    reversedBy: 'VCH-4490',
    reverses: null,
  };
  const { api } = client([{ status: 200, body: JSON.stringify(entry) }]);

  const found = await api.getTransaction('VCH-4471');
  assert.equal(found?.reversedBy, 'VCH-4490');
  // A string, not a number — parse before formatting.
  assert.equal(typeof found?.descriptionParameters.amountPaise, 'string');
});
