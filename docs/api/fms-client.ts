/**
 * A typed client for the Fund Management System API (v1).
 *
 * Zero dependencies, `fetch` only, no build step required — Node strips the types natively and
 * every bundler handles it as ordinary TypeScript. Copy this file into the frontend and import
 * from it, or use it as the reference for whatever client your codebase already has.
 *
 * The types here are the ones `openapi.json` would generate, plus the rules a generated client
 * cannot express:
 *
 *   - `GET /payout` answers 204 with no body, which this client returns as `null`.
 *   - `GET /transactions/{reference}` answers 404 with an EMPTY body, also returned as `null`.
 *     Every other failure on the API carries an `ErrorResponse`; that one does not.
 *   - `remainingToday: null` means the route is uncapped, not that nothing is left.
 *   - `view` defaults to MOVEMENTS on the JSON endpoint and ALL_ENTRIES on the CSV export.
 *   - Money is an integer count of paise. A decimal is refused by the server with 400, not
 *     rounded, so this client refuses it before the request leaves.
 *
 * See docs/api/README.md for the full reference.
 */

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

/** An ISO calendar date, `YYYY-MM-DD`. */
export type IsoDate = string;

/**
 * A monetary amount.
 *
 * `paise` is an integer: 12345 is ₹123.45. Divide by 100 to display and never to calculate.
 * The server refuses a float or a quoted number rather than coercing it.
 */
export interface Money {
  paise: number;
  currency: 'INR';
}

export type TransactionView = 'MOVEMENTS' | 'ALL_ENTRIES';

export type Direction = 'IN' | 'OUT';

/** The eight states a withdrawal request can occupy. */
export type PayoutState =
  | 'ACCEPTED'
  | 'QUEUED_FOR_RUN'
  | 'INSTRUCTED'
  | 'PAID'
  | 'PARTLY_PAID'
  | 'NOTHING_SENT'
  | 'RETURNED'
  | 'CANCELLED';

/** The ten kinds of entry. Only the first three appear in the MOVEMENTS view. */
export type EntryKind =
  | 'PAYIN'
  | 'PAYOUT'
  | 'MANDATED_RETURN'
  | 'SALE_PROCEEDS'
  | 'PURCHASE_COST'
  | 'CHARGES'
  | 'MARGIN_MOVEMENT'
  | 'ACCOUNT_ACCRUAL'
  | 'OPENING_BALANCE'
  | 'REVERSAL';

export type PaymentRoute = 'UPI' | 'NET_BANKING' | 'NEFT';

/** Why a request could not be cancelled. The three mean different things to a trader. */
export type CancelReason = 'ALREADY_INSTRUCTED' | 'ALREADY_TERMINAL' | 'NOT_FOUND';

export interface PayoutRequestCommand {
  amount: Money;
  /** Profile's reference for a bank account already verified against this trader. */
  destinationRef: string;
}

export interface PayoutRequestResponse {
  requestId: number;
  /** What support quotes. Distinct from the bank's own transfer reference. */
  fmsReference: string;
  /** When the money should arrive, from the settlement calendar. A quote, not a guarantee. */
  arrivalDateQuoted: IsoDate;
  /**
   * A copy key, always `WITHDRAWAL_MAY_SHRINK_AT_SETTLEMENT` in this phase.
   *
   * The request reserves nothing and settles at end of day against whatever is available then,
   * so the amount can shrink. Show this before the trader commits, not after.
   */
  shrinkWarningKey: string;
  state: PayoutState;
  withdrawableAtRequest: Money;
  /** Masked. No endpoint ever returns a full account number. */
  destinationMasked: string;
}

export interface Period {
  from: IsoDate;
  to: IsoDate;
}

export interface Entry {
  /** The back-office voucher number. This is what `getTransaction` takes. */
  reference: string;
  date: IsoDate;
  kind: EntryKind;
  /** A copy key. Resolve it against your own strings; never display it raw. */
  descriptionKey: string;
  /** Interpolation values. Every value is a string, including `amountPaise`. */
  descriptionParameters: Record<string, string>;
  /** The back-office reference. Show it beside the description, never as it. */
  secondaryDetail: string | null;
  /** Always positive. The sign lives in `direction`. */
  amount: Money;
  direction: Direction;
  /** The back office's own running balance. Do not accumulate your own — they will diverge. */
  runningBalance: Money;
  segment: string | null;
  /** Whether the trader caused this. An automatic return is not a deposit they made. */
  userCaused: boolean;
  /** The reference of a later entry reversing this one. Mark it so a reader does not double-count. */
  reversedBy: string | null;
  /** The reference of the earlier entry this one reverses. */
  reverses: string | null;
}

export interface TransactionsResponse {
  view: TransactionView;
  /** The period actually covered. Render from this, not from what you asked for. */
  period: Period;
  entries: Entry[];
  /** A wider period worth offering. Meaningful only when `entries` is empty. */
  suggestedWiderPeriod: Period | null;
}

export interface RouteHeadroom {
  route: PaymentRoute;
  /** `null` means the route is uncapped. It does not mean zero. */
  remainingToday: Money | null;
  /** ₹0 on every route in this phase, and still a figure so copy never names a number. */
  fee: Money;
}

export interface RouteHeadroomResponse {
  routes: RouteHeadroom[];
}

/** The uniform error body. Branch on `code`; `message` is developer-facing and not stable. */
export interface ErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

/**
 * Any non-2xx answer from the API.
 *
 * Branch on `code`. The `message` is a developer-facing explanation written for whoever reads the
 * logs — it is not translated, not stable between releases, and not user copy.
 */
export class FmsError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, body: ErrorBody) {
    super(body.message ?? `request failed with ${status}`);
    this.name = 'FmsError';
    this.status = status;
    this.code = body.code;
    this.details = body.details ?? {};
  }

  /** Why a cancel was refused, when this is a `not_cancellable`. */
  get cancelReason(): CancelReason | null {
    return this.code === 'not_cancellable' ? ((this.details.reason as CancelReason) ?? null) : null;
  }

  /**
   * The figures behind an `amount_exceeds_withdrawable`, so the refusal is explained without a
   * second request.
   */
  get withdrawableFigures(): { requested: Money; withdrawable: Money } | null {
    if (this.code !== 'amount_exceeds_withdrawable') return null;
    return {
      requested: this.details.requested as Money,
      withdrawable: this.details.withdrawable as Money,
    };
  }

  /** Whether retrying the same call could plausibly succeed. */
  get isRetryable(): boolean {
    return this.status === 503;
  }

  /** Field-level messages from a shape validation failure, keyed by field name. */
  get fieldErrors(): Record<string, string> {
    if (this.code !== 'invalid_request') return {};
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.details)) {
      if (typeof v === 'string') out[k] = v;
    }
    return out;
  }
}

// ---------------------------------------------------------------------------
// Money helpers
// ---------------------------------------------------------------------------

/** Build a `Money` from an integer count of paise. Throws on a non-integer. */
export function paise(value: number): Money {
  if (!Number.isSafeInteger(value)) {
    throw new RangeError(`paise must be a safe integer, got ${value}`);
  }
  return { paise: value, currency: 'INR' };
}

/**
 * Parse a rupee amount typed by a user into paise, without going through a float.
 *
 * `"1,234.5"` becomes `123450`. Anything with more than two decimal places, or that is not a
 * number at all, throws — rounding a trader's input silently is how an amount they did not type
 * gets submitted.
 */
export function rupeesToPaise(input: string): number {
  const cleaned = input.trim().replace(/,/g, '');
  const match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(cleaned);
  if (!match) throw new RangeError(`not a rupee amount with at most two decimals: ${input}`);

  const [, sign, whole, fraction = ''] = match;
  const value = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (!Number.isSafeInteger(value)) throw new RangeError(`amount out of range: ${input}`);
  return sign === '-' ? -value : value;
}

/**
 * Format an amount for display, in the Indian numbering system.
 *
 * `formatMoney({paise: 12345, currency: 'INR'})` gives `"₹123.45"`. Pass `symbol: false` where the
 * currency is already stated in the surrounding UI.
 */
export function formatMoney(money: Money, options: { symbol?: boolean } = {}): string {
  const { symbol = true } = options;
  return new Intl.NumberFormat('en-IN', {
    style: symbol ? 'currency' : 'decimal',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(money.paise / 100);
}

/**
 * Whether a route has no daily cap.
 *
 * The distinction matters: `remainingToday: null` rendered as ₹0 tells a trader NEFT is exhausted
 * when it has no limit at all.
 */
export function isUncapped(headroom: RouteHeadroom): boolean {
  return headroom.remainingToday === null;
}

// ---------------------------------------------------------------------------
// Internals
// ---------------------------------------------------------------------------

/** The widest window one transactions request may cover. The upstream ledger has no pagination. */
export const MAX_PERIOD_DAYS = 92;

/** The default period, applied by the server when neither bound is sent. */
export const DEFAULT_PERIOD_DAYS = 30;

/**
 * Pull the server's filename out of a `Content-Disposition` header.
 *
 * Composing your own from the period drifts the moment the server's naming changes, and the
 * server already put the period in the name. Returns `fallback` when the header is absent or in a
 * form this does not recognise.
 */
export function filenameFromContentDisposition(
  header: string | null,
  fallback = 'statement.csv',
): string {
  if (!header) return fallback;
  // RFC 5987 form first: it carries the encoding and wins where both are present.
  const encoded = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''));
    } catch {
      // A malformed encoding falls through to the plain form rather than throwing mid-download.
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1].trim() : fallback;
}

/**
 * Reject a period the server would reject anyway.
 *
 * Both bounds or neither: sending one alone is treated by the server as sending none, so a
 * half-specified period silently returns the 30-day default and the trader sees a range they did
 * not ask for. Catching it here makes the bug visible at the call site.
 */
export function assertValidPeriod(from?: IsoDate, to?: IsoDate): void {
  if (from === undefined && to === undefined) return;
  if (from === undefined || to === undefined) {
    throw new RangeError('send both `from` and `to`, or neither — one alone is ignored');
  }
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end)) {
    throw new RangeError(`dates must be YYYY-MM-DD, got ${from} and ${to}`);
  }
  if (end < start) throw new RangeError(`the period ends before it starts: ${from} to ${to}`);

  const days = Math.round((end - start) / 86_400_000);
  if (days > MAX_PERIOD_DAYS) {
    throw new RangeError(
      `a period of ${days} days exceeds the ${MAX_PERIOD_DAYS}-day maximum; walk a wider range in windows`,
    );
  }
}

function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) search.set(key, value);
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}

// ---------------------------------------------------------------------------
// The client
// ---------------------------------------------------------------------------

export interface FmsClientOptions {
  /** Host and port. The service has no context path, so no trailing segment belongs here. */
  baseUrl: string;
  /**
   * The complete `Authorization` header value, or a function returning one.
   *
   * The scheme is the caller's to supply, deliberately. The service enforces HTTP Basic today and
   * that is provisional — `openapi.json` declares it as `platformAuth` and says so — so a client
   * that hardcoded a scheme would need editing again when the platform gateway's token issuer is
   * settled. This one does not.
   *
   * ```ts
   * // What the service accepts today.
   * authorization: `Basic ${btoa(`${ucc}:${secret}`)}`
   * // What it will accept once the gateway issues tokens. No client change beyond this line.
   * authorization: () => `Bearer ${session.accessToken}`
   * ```
   *
   * Pass the function form where the credential rotates: it is read per request, so a client built
   * once at startup keeps working across a refresh.
   */
  authorization: string | (() => string | Promise<string>);
  /** Injected for tests and for environments with a wrapped fetch. Defaults to global `fetch`. */
  fetch?: typeof globalThis.fetch;
}

export interface TransactionQuery {
  view?: TransactionView;
  from?: IsoDate;
  to?: IsoDate;
}

export function createFmsClient(options: FmsClientOptions) {
  const http = options.fetch ?? globalThis.fetch.bind(globalThis);
  const base = options.baseUrl.replace(/\/+$/, '');

  async function credential(): Promise<string> {
    const { authorization } = options;
    return typeof authorization === 'function' ? await authorization() : authorization;
  }

  async function send(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set('Authorization', await credential());
    return http(`${base}${path}`, { ...init, headers });
  }

  /**
   * Turn a failed response into an `FmsError`.
   *
   * A body is expected but not required: the transactions detail 404 has none, and a gateway
   * failure ahead of this service may answer with something that is not this API's error shape at
   * all. Either way the caller gets an `FmsError` with a usable status rather than a parse crash.
   */
  async function fail(response: Response): Promise<never> {
    let body: ErrorBody = { code: 'unknown', message: `request failed with ${response.status}` };
    try {
      const text = await response.text();
      if (text) body = JSON.parse(text) as ErrorBody;
    } catch {
      // Left as the generic body above. A non-JSON failure is still a failure worth throwing.
    }
    throw new FmsError(response.status, body);
  }

  async function json<T>(response: Response): Promise<T> {
    if (!response.ok) await fail(response);
    return (await response.json()) as T;
  }

  return {
    // --- Withdrawals ------------------------------------------------------

    /**
     * Create the account's single open withdrawal request.
     *
     * The request reserves nothing. Show the copy behind `shrinkWarningKey` on the confirmation
     * step, before the trader commits.
     *
     * One open request per account, enforced by a database constraint rather than a service
     * check, so `request_already_open` can arrive even when a read a moment earlier said there
     * was none. Handle the 409; do not gate the button on the read.
     *
     * @throws {FmsError} 409 `request_already_open` | `withdrawable_unavailable` | `figures_stale`
     * @throws {FmsError} 422 `amount_exceeds_withdrawable` | `destination_not_verified`
     * @throws {FmsError} 503 `upstream_unavailable` | `calendar_unavailable`
     */
    async requestPayout(command: PayoutRequestCommand): Promise<PayoutRequestResponse> {
      // Caught here rather than at the server's 400, so the stack trace points at the caller that
      // built the amount instead of at the transport.
      if (!Number.isSafeInteger(command.amount.paise)) {
        throw new RangeError(`amount.paise must be a safe integer, got ${command.amount.paise}`);
      }
      const response = await send('/api/v1/funds/payout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(command),
      });
      return json<PayoutRequestResponse>(response);
    },

    /**
     * The account's open request, or `null` when there is none.
     *
     * The server answers 204 with an empty body for the `null` case — an ordinary state rather
     * than an error.
     */
    async getOpenPayout(): Promise<PayoutRequestResponse | null> {
      const response = await send('/api/v1/funds/payout');
      if (response.status === 204) return null;
      return json<PayoutRequestResponse>(response);
    },

    /**
     * Cancel a request that has not been instructed to the rail yet.
     *
     * Permitted while `ACCEPTED` or `QUEUED_FOR_RUN`, including after a rail outage deferred it.
     *
     * @throws {FmsError} 409 `not_cancellable`. Read `error.cancelReason` — the three reasons mean
     *   different things and deserve different copy.
     */
    async cancelPayout(requestId: number): Promise<PayoutRequestResponse> {
      const response = await send(`/api/v1/funds/payout/${encodeURIComponent(String(requestId))}`, {
        method: 'DELETE',
      });
      return json<PayoutRequestResponse>(response);
    },

    // --- Adding funds -----------------------------------------------------

    /**
     * Remaining daily headroom on each route.
     *
     * Check `isUncapped()` before formatting `remainingToday`. The cap is daily and measured
     * against everything already sent on that route today, so two transfers of the cap amount
     * will not both pass.
     */
    async getPayinLimits(): Promise<RouteHeadroomResponse> {
      return json<RouteHeadroomResponse>(await send('/api/v1/funds/payin/limits'));
    },

    // --- Transactions -----------------------------------------------------

    /**
     * Transactions for a period.
     *
     * Omit both bounds for the server's 30-day default. Render the period from `response.period`
     * rather than from what you asked for, and offer `suggestedWiderPeriod` when `entries` is
     * empty — "no transactions" alone is indistinguishable from a failure to load.
     *
     * There is no pagination: every entry in the period arrives in one response, which is why the
     * window is capped at 92 days.
     *
     * @throws {RangeError} before the request, for a half-specified, inverted or over-wide period
     * @throws {FmsError} 503 `upstream_unavailable`
     */
    async listTransactions(q: TransactionQuery = {}): Promise<TransactionsResponse> {
      assertValidPeriod(q.from, q.to);
      const response = await send(
        `/api/v1/funds/transactions${query({ view: q.view, from: q.from, to: q.to })}`,
      );
      return json<TransactionsResponse>(response);
    },

    /**
     * One entry by reference, or `null` when this account has no such entry in this period.
     *
     * Pass the same period the list used. A real entry outside the period answers 404 exactly as
     * an unknown reference does, and so does an entry belonging to another trader — confirming
     * existence would itself leak.
     *
     * This 404 carries an empty body, which is why it is a `null` rather than an `FmsError`.
     */
    async getTransaction(
      reference: string,
      q: { from?: IsoDate; to?: IsoDate } = {},
    ): Promise<Entry | null> {
      assertValidPeriod(q.from, q.to);
      const response = await send(
        `/api/v1/funds/transactions/${encodeURIComponent(reference)}${query({ from: q.from, to: q.to })}`,
      );
      if (response.status === 404) return null;
      return json<Entry>(response);
    },

    /**
     * The statement as CSV, with the filename the server chose.
     *
     * Pass `view` explicitly from whatever the trader is looking at: this endpoint defaults to
     * `ALL_ENTRIES` while the JSON endpoint defaults to `MOVEMENTS`, and an export is supposed to
     * return precisely what is on screen.
     *
     * Fetched rather than navigated to, because a `window.location` navigation carries no
     * `Authorization` header. See `saveStatement` below for the download itself.
     *
     * @throws {FmsError} 400 `invalid_request`. Besides a bad period, this covers an export
     *   refused because a field looked like an unmasked account number — a backend defect, and it
     *   arrives as a clean status rather than a truncated file because the check runs before any
     *   bytes are written.
     */
    async downloadStatement(q: TransactionQuery = {}): Promise<{ blob: Blob; filename: string }> {
      assertValidPeriod(q.from, q.to);
      const response = await send(
        `/api/v1/funds/statement.csv${query({ view: q.view, from: q.from, to: q.to })}`,
        { headers: { Accept: 'text/csv' } },
      );
      if (!response.ok) await fail(response);
      return {
        blob: await response.blob(),
        filename: filenameFromContentDisposition(response.headers.get('Content-Disposition')),
      };
    },
  };
}

export type FmsClient = ReturnType<typeof createFmsClient>;

/**
 * Save a downloaded statement to the user's disk. Browser only.
 *
 * Kept separate from `downloadStatement` so the fetch is testable in Node and usable outside a
 * browser, and so a caller who wants the CSV in memory is not forced through a download.
 */
export function saveStatement(file: { blob: Blob; filename: string }): void {
  const url = URL.createObjectURL(file.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.filename;
  link.click();
  URL.revokeObjectURL(url);
}
