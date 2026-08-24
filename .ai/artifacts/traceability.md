# Traceability Matrix — Fund Management System

> **Rules:** Stage 1 populates REQ ID + Summary only. Each subsequent stage fills
> in its own column using its own artifact as the source. No column is re-derived
> from scratch; the table is only appended to. Empty cells in later columns mean
> coverage has not yet been established by that stage.
>
> **This is a gate, not paperwork.** `hooks/stop.js` blocks the Stage 9 → Stage 10
> handoff while any in-scope requirement has an empty HLD, LLD or Code cell. Fill
> cells honestly — a fabricated coverage link defeats the gate rather than
> satisfying it.

---

## Scope: `fullstack`

Requirement IDs are sourced from the PRD index
(`docs/specs/001-fund-management-system/product-requirements.md`) plus every file
listed in its frontmatter `parts:`. Requirements the PRD places outside this
phase are held in the second table so the Stage 9 gap gate does not demand
coverage the phase never promised.

| REQ ID | Requirement Summary | HLD Coverage | LLD Coverage | Code Coverage | Test Coverage |
|--------|---------------------|--------------|--------------|---------------|---------------|
| REQ-101 | Show three distinct balances and never conflate them | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-102 | Explain the withdrawable figure line by line | hld.md#11-the-one-idea-the-architecture-is-organised-around | lld-backend.md#42-request-and-response-dtos | `backend/fund-management-service/src/main/java/com/thinq/fms/derivation/WithdrawableCalculator.java` — all six Rule B4 terms with signs, zero-valued terms included, plus `largestDeduction`. Partial: client rendering is Stage 5b, unbuilt | `WithdrawableCalculatorTest` (12, property-tested at 10,000 inputs, 100% branch), `PayoutApiTest` |
| REQ-103 | Decompose available margin into its named components | hld.md#8-api-design--network-perimeter | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-104 | Show collateral separately and never as withdrawable | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-105 | Answer how much can be deployed on each kind of trade | hld.md#8-api-design--network-perimeter | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-106 | Show blocked money by funding source and commitment state | hld.md#8-api-design--network-perimeter | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-107 | State how current every margin figure is | hld.md#7-component-breakdown | lld-backend.md#31-design-patterns-and-why-each-earns-its-place | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-108 | Keep separately-settled segments separately presented — merged this phase, segment recorded from day one | hld.md#91a-the-one-data-model-obligation-that-outlives-this-phase | lld-backend.md#balances-and-margin | **NOT IMPLEMENTED — deferred.** Blocked on TASK-11: Noren's Java-to-C++ transport and the `GetRmsLimits` signature are unresolved, so `MarginSource` has no implementation and no margin figure can be derived. No code claims this requirement. | **NOT TESTED — deferred with the code.** `MarginFigures` is at 0% coverage because nothing constructs it. Testing is blocked behind TASK-11, not behind effort. |
| REQ-201 | Let the user choose an amount without being anchored | hld.md#131-client-surfaces--what-each-owns | lld-frontend.md#73-amountinput--req-201-rule-a13 | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinOrchestrator.java` `lastSuccessfulDeposit` returns the most recent confirmed attempt, or empty for Rule A1's first-deposit case. | `PayinOrchestratorTest.lastSuccessfulDeposit` |
| REQ-202 | Disclose the route used and the arrival date before commitment | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#add-funds | `funding/FundingQuote` — route, arrival, cost and the amount credited, with an automatic route change disclosed (Rule A12); the type cannot represent a quote without an arrival date | `FundingQuoteTest` (10) — the two figures must reconcile against the cost |
| REQ-203 | Accept money only from an account the user has proven they hold | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#add-funds | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinOrchestrator.java` `start` reads Profile at the moment of the attempt, never a cached list (PR-28); a trader with no verified source is refused before any attempt row is created. | `PayinOrchestratorTest` — refuses when no verified account is on record |
| REQ-204 | Credit confirmed money at once and show money still in flight | hld.md#15-reliability--failure-handling | lld-backend.md#add-funds | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinState.java` and `PayinOrchestrator.java` — Rule A5: only CONFIRMED affects a balance, and an in-flight attempt contributes to none. Partial: the RMS push that raises available margin needs MarginSource, which is halted | `PayinLedgerNoDoubleCountTest` (5) — a deposit appears exactly once on both sides of confirmation, end to end through the real repository and query service; positive control confirms it catches a double count |
| REQ-205 | Explain a failed attempt and offer a way forward | hld.md#15-reliability--failure-handling | lld-backend.md#add-funds | `backend/fund-management-service/src/main/java/com/thinq/fms/integration/juspay/PayinOutcome.java` + `JuspayStatusMapper.java` — all six Rule A9a outcomes as distinct values, with Rule A9b's unknown state failing to "awaiting" rather than "failed". Partial: the attempt lifecycle that acts on them is TASK-29, unbuilt | `PayinOrchestratorTest.alternativesAfterFailure` |
| REQ-206 | Reverse a payin that should not have been accepted | hld.md#92-immutability | lld-backend.md#add-funds | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinAttempt.java` `reverse` — Rule A10: a confirmed payin is reversed, never deleted; the row stays and a resulting debit is a debt for the health module rather than a reason to refuse. Partial: the compensating TechExcel instruction is unwritten | `PayinOrchestratorTest` — reversal, and that a non-confirmed payin is not reversible |
| REQ-207 | Treat funding during a shortfall as urgent | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#add-funds | `funding/ShortfallFunding` — the shortfall as the suggested amount, the fastest route, and the time remaining where known | `FundingQuoteTest` — unknown deadline still prompts without inventing a countdown |
| REQ-301 | Keep a withdraw entry point always visible, disabled with a reason | hld.md#131-client-surfaces--what-each-owns | lld-frontend.md#74-actionbutton--req-301-rule-w2 | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payout/PayoutOrchestrator.java` — the request path and its refusal reasons, each a distinct typed exception so the entry point can stay visible with a reason rather than vanishing. Partial: the always-visible disabled control is Stage 5b, unbuilt The endpoint is `POST /api/v1/funds/payout` in `backend/fund-management-service/src/main/java/com/thinq/fms/api/PayoutController.java`, with every refusal a distinct code the client renders a reason from. | `PayoutOrchestratorTest` (13) — the entry point stays available and a request is refused with its reason rather than hidden; `PayoutApiTest` (12) |
| REQ-302 | Accept one withdrawal request and settle it at end of day | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#withdraw-funds | `backend/fund-management-service/src/main/resources/db/migration/V21__fms_payout_request.sql` — Rule W4 one-open-request partial unique index. Partial: accept and end-of-day settle logic unwritten Plus `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payout/PayoutOrchestrator.java` and `PayoutRequest.java` — the request is created without pre-checking Rule W4 (the index is the guarantee; a read-then-write is a race), Rule W12 pins the destination, and Rule W11 stamps the withdrawable figure at request. | `SchemaConstraintTest` — Rule W4 refused in all three open states, and a closed request does not block the next; `JdbcPayoutRequestRepositoryTest` (7) |
| REQ-303 | Tell the user when the money will arrive, from their own account state | hld.md#8-api-design--network-perimeter | lld-backend.md#22-database-schema | `settings/ArrivalDateCalculator` + `ArrivalQuote` — each deferring cause named; an uncomputable date is reported as such | `ArrivalDateCalculatorTest` (7) |
| REQ-305 | Let the user cancel a request that has not yet been sent | hld.md#8-api-design--network-perimeter | lld-backend.md#44-error-responses | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payout/PayoutOrchestrator.java` `cancel` — permitted while ACCEPTED or QUEUED_FOR_RUN (REQ-619), refused once INSTRUCTED, with `RequestNotCancellableException.reasonCode` naming why. A request belonging to another trader answers NOT_FOUND rather than forbidden Exposed as `DELETE /api/v1/funds/payout/{requestId}`; another trader's request answers NOT_FOUND rather than forbidden. | `PayoutOrchestratorTest` — cancellation before settlement |
| REQ-306 | Return the money and state the reason when a payout fails | hld.md#92-immutability | lld-backend.md#withdraw-funds | `movement/payout/PayoutReturn` — compensating return, `mayResendAutomatically()` is always false, destination-rejected flagged separately | `PayoutReturnTest` — never resent, destination-rejected distinguished |
| REQ-307 | Return unused funds on the mandated calendar and explain each return | hld.md#165-compliance-obligations-carried-by-the-design | lld-backend.md#withdraw-funds | `movement/payout/MandatedReturnSchedule` — next monthly or quarterly date, and `collidesWithOpenRequest` for Rule W9 | `PayoutReturnTest` — monthly and quarterly boundaries, and the Rule W9 collision |
| REQ-308 | Re-check eligibility before the money actually leaves | hld.md#15-reliability--failure-handling | lld-backend.md#45-mapping-the-settlement-outcome-to-a-reason-the-user-can-read | `backend/fund-management-service/src/main/java/com/thinq/fms/integration/techexcel/TechExcelPayoutRail.java` `toOutcome` — §4.5's mapping over `Reject`, `AUTH_DUE_AMT` and `RMSData`, naming the deduction for any gap; `SettlementReasonMapper` for OA-4's free text. Partial: the end-of-day run that invokes it is TASK-33, unbuilt Verified end to end against a stub vendor in `backend/fund-management-service/src/test/java/com/thinq/fms/integration/techexcel/TechExcelGatewayTest.java`. | `SettlementMappingTest` |
| REQ-401 | Record every money event in plain language | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#transactions-and-statements | `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/LedgerEntry.java` + `integration/techexcel/TechExcelLedgerGateway.java` — the structured entry fields REQ-401's plain language is generated from. Partial: `EntryDescriptionMapper` (TASK-21) unbuilt, and TechExcel's raw NARRATION is deliberately never rendered Plus `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/ConfiguredEntryDescriptionMapper.java` — classification from TechExcel's structured fields into copy keys, with Rule L3's reference kept as secondary detail and Rule L4's user-caused flag separating a requested payout from a mandated return. Unmapped combinations are counted for alerting rather than rendered. Rendered by `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/TransactionQueryService.java` through `GET /api/v1/funds/transactions`, with the reference carried as secondary detail. | `EntryDescriptionMapperTest` (9) |
| REQ-402 | Separate "where is my money" from "explain my account" | hld.md#8-api-design--network-perimeter | lld-backend.md#transactions-and-statements | `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/TransactionView.java` and `TransactionQueryService.java` — Rule L5's two views over one ledger read and one running balance, selected by a `view` parameter on `GET /api/v1/funds/transactions` so switching cannot lose the period. Rule L5a keeps sale proceeds and charges out of the movements view. **Partial: the movements view is built from ledger entries only.** A failed or pending payin is an `fms_payin_attempt` row and never reaches the ledger, so REQ-402's "current status, including items not yet complete" and Rule L8's "failed and cancelled movements stay in the history" are both unmet until TASK-29 merges attempt rows into the view and `TransactionEntry` gains a status.  **Closed 21 Aug 26:** `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinMovementSource.java` merges in-flight and failed attempts into the movements view, and `TransactionEntry.status` carries their state — so REQ-402's "including items not yet complete" and Rule L8 now hold. | `TransactionQueryServiceTest` (20) |
| REQ-403 | Let the user find a transaction by date, type and amount | hld.md#91b-who-computes-the-running-balance | lld-backend.md#transactions-and-statements | `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/TransactionPeriod.java` — Rule L6's 30-day default, Rule L7's explicit empty result with a wider period offered, and a refusal rather than truncation for a window the back office cannot answer in one call. Kind and amount filters are deferred by the PRD | `TransactionQueryServiceTest` (20), `TransactionsApiTest` (10) — date range inclusive at both ends, and the 30-day default period |
| REQ-404 | Show a running balance and pair every reversal with its original | hld.md#91b-who-computes-the-running-balance | lld-backend.md#transactions-and-statements | `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/LedgerEntry.java` `closingBalance` — carried from TechExcel's `CLOSING_AMT`, so no running balance is accumulated here (HLD §9.1b). Partial: reversal pairing is TASK-24, unbuilt Reversal pairing is in `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/TransactionQueryService.java`: a reversal links to its original and the original is flagged `reversedBy`, so a reader scanning the list does not count it twice. | `TransactionQueryServiceTest` — running balance and reversal pairing |
| REQ-405 | Track one payin or payout through its whole life | hld.md#8-api-design--network-perimeter | lld-backend.md#22-database-schema | `backend/fund-management-service/src/main/resources/db/migration/V24__fms_movement_state_event.sql` — partitioned state-event table. Partial: schema only, no state machine written | `JdbcPayinAttemptRepositoryTest` (8), `JdbcPayoutRequestRepositoryTest` (7) — every state round-trips, and a stale write is refused |
| REQ-407 | Produce a statement the user can keep and submit | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#transactions-and-statements | `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/StatementCsvWriter.java` and `StatementRow.java` — REQ-407's six columns, amounts as plain unformatted decimals derived from paise, Rule L8a's Debit/Credit wording, and Profile PR-32 enforced by refusing any field carrying a possible unmasked account number. Partial: the endpoint that streams it is unbuilt Served by `GET /api/v1/funds/statement.csv` in `backend/fund-management-service/src/main/java/com/thinq/fms/api/TransactionsController.java`, over the same view and period as the list (Rule L8a), with PR-32 validated before any byte is streamed. Statement wording comes from `backend/fund-management-service/src/main/java/com/thinq/fms/ledgerview/StatementCopy.java` — the export writes language, not the copy key, because the server cannot ask the client to resolve a key into a cell it is writing. | `StatementCsvWriterTest` (12), `StatementCopyTest` (13), `TransactionsApiTest` (10) |
| REQ-501 | Tell the user they owe money, why, and what it is costing | hld.md#8-api-design--network-perimeter | lld-backend.md#account-health | `health/AccountDebt` — a positive amount owed with its cause named, plus the accrual where a rate exists | `AccountDebtTest` (10) — Rule H1 refuses a negative balance; accrual rounds down |
| REQ-502 | Let the user clear dues exactly, below any minimum | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#account-health | `health/AccountDebt.amountToClear` with `settings/MinimumAddPolicy` — the exact figure including accrual, permitted below `minAdd` | `AccountDebtTest.theAmountToClearIncludesAccrual`, `MinimumAddPolicyTest` |
| REQ-503 | Warn before a predictable charge puts the account into debit | **NOT DESIGNED — deferred.** Blocked on EB-6: the charge-prediction source is unnominated, so there is nothing to design against. Recorded as undesigned at Stage 6 and carried unchanged. | **NOT DESIGNED — deferred.** Blocked on EB-6: the charge-prediction source is unnominated, so there is nothing to design against. Recorded as undesigned at Stage 6 and carried unchanged. | **NOT IMPLEMENTED — deferred.** Blocked on EB-6: the charge-prediction source is unnominated, so a warning before a predictable charge cannot be computed. No code claims this requirement. | **NOT TESTED — deferred with the code.** Blocked on EB-6 alongside the implementation. |
| REQ-504 | Give an empty account a purpose | hld.md#131-client-surfaces--what-each-owns | lld-frontend.md#72-healthbanner--req-501-req-504-req-505-req-506 | **NOT IMPLEMENTED — deferred.** No frontend exists: the React client in `lld-frontend.md` was never built, and this requirement has no backend expression. No code claims it. | **NOT TESTED — deferred with the code.** Unverifiable rather than unverified; see `browser-report.md`. No backend result is partial credit toward it. |
| REQ-505 | Name the blocker when the account cannot receive money | hld.md#7-component-breakdown | lld-backend.md#account-health | `platform/error/NoVerifiedSourceException`, `ProfileClient.accountsOf` — names which blocker applies on the funding path | `PayinOrchestratorTest`, `PayinDurabilityTest` — refuses with the blocker named when no verified account exists |
| REQ-506 | Warn while a shortfall can still be fixed | hld.md#8-api-design--network-perimeter | lld-backend.md#account-health | `backend/fund-management-service/src/main/java/com/thinq/fms/derivation/WithdrawableCalculator.java` — surfaces `SHORTFALL_OUTSTANDING` as a named term. Partial: the warning itself is the health module, unbuilt | `WithdrawableCalculatorTest` (12) — the shortfall term stays visible when it floors the figure; `ShortfallMessagesTest` (11) — the deadline is stated or said to be unknown |
| REQ-601 | Escalate a margin shortfall on a ladder capped at three SMS in a day | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `backend/fund-management-service/src/main/java/com/thinq/fms/messaging/MessageLadder.java` — `forMarginShortfall`, three steps, SMS+email unconditional, ₹1.00 floor | `MessageLadderTest` (28) — three steps, exactly three SMS, ₹1.00 boundary both sides |
| REQ-602 | Carry the exact amount and a way to act into every action message | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `messaging/ShortfallMessages.forStep` — the exact amount on every channel; `actionControl` is NONE on SMS (Rule C16) | `ShortfallMessagesTest` — every channel states the amount; SMS carries no control |
| REQ-603 | Show the shortfall email's arithmetic, and disclose the state in the subject | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `ShortfallMessages.forStep` — requirement, available margin and shortfall as separate named figures on email, with the cause and the subject state | `ShortfallMessagesTest.onlyEmailCarriesTheBreakdown`, `theThreeFiguresMustReconcile` |
| REQ-604 | Pair each channel to its role and drop a step rather than block the ladder | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `MessageLadder.forMarginShortfall` — an absent WhatsApp opt-in drops that step without altering the SMS/email schedule | `MessageLadderTest.noOptInDropsTheStepWithoutDelayingTheOthers` — schedules compared directly |
| REQ-608 | Band dues messaging by amount and age, never by day count alone | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `MessageLadder.forDuesOutstanding` — day 0/7/14/30 then monthly, SMS banded at ₹500 | `MessageLadderTest` — banding at ₹500 both sides, horizon boundary, account-zone day boundaries |
| REQ-609 | Confirm a cleared debt, once | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `MessageLadder.forDuesCleared`, `forShortfallCleared` — keyed on the clearance so `fms_intent_once` bounds it to one | `MessageLadderTest.aClearedShortfallIsConfirmedOnTheSameChannels`, `clearanceIsKeyedOnTheClearance` |
| REQ-611 | Chase a pending payin once at 30 minutes, once again at write-off | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `MessageLadder.forPendingPayin` (30 minutes) and `forPayinWrittenOff` (queued at write-off, not pre-scheduled) | `MessageLadderTest.aPendingPayinIsChasedOnceAtThirtyMinutes`, `theWriteOffMessageIsNotPreScheduled` |
| REQ-612 | Payin confirmation names the amount and last four digits, nothing else | hld.md#93-privacy-and-deletion | lld-backend.md#communications | `messaging/PayinMessages.confirmed` — amount, last-four `sourceMasked`, route; a source with more than four digits is refused | `PayinMessagesTest.aConfirmationDisclosesNothingFurther`, `aLongSourceIsRefused` |
| REQ-613 | Success email shows what the payin changed, including that Withdrawable did not move | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `PayinMessages.confirmed` — email, states the margin rise, that withdrawable did not move, and the date it becomes withdrawable | `PayinMessagesTest.aConfirmationStatesBothFiguresAndTheReason` |
| REQ-614 | Give each payin failure its own message and its own recovery | hld.md#91-what-fms-stores-and-what-it-does-not | lld-backend.md#add-funds | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/PayinMovementSource.java` — each failure carries its own copy key (`PAYIN_BANK_DECLINED` and so on) so a message names the specific outcome rather than a generic failure | `PayinMessagesTest` — every outcome templated, conditional-refund and cause split by outcome |
| REQ-615 | State both figures when a payin moves one and not the other | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `PayinMessages.confirmed` — both figures with their change, and `withdrawableUnchangedTerm` naming Rule B4's ADDED_TODAY | `PayinMessagesTest.aConfirmationStatesBothFiguresAndTheReason` (100% mutation score) |
| REQ-616 | Confirm a cancelled withdrawal by email only | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `messaging/PayoutMessages.cancelledByUser` — email only, `nothingMoved` | `PayoutMessagesTest.aCancellationIsEmailOnly`, `aCancellationStatesNothingMoved` |
| REQ-617 | Make a partial transfer its own message on both channels | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#63-the-end-of-day-run--full-pseudocode | `PayoutMessages.settled` — its own template for PARTLY_PAID with requested, sent, shortfall and the named deduction | `PayoutMessagesTest.aPartialTransferIsItsOwnMessage`, `aPartialTransferMustNameTheDeduction` |
| REQ-618 | Every terminal withdrawal message states where the money is now | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `PayoutMessages.settled` — `moneyLeftForBank`, `destinationMasked`, `nothingDeducted` | `PayoutMessagesTest.aTerminalMessageSaysWhereTheMoneyIs`, `noDestinationWhereTheMoneyNeverLeft` |
| REQ-619 | Give each end-of-day outcome its own message, with no dialog | hld.md#121-the-end-of-day-payout-run | lld-backend.md#63-the-end-of-day-run--full-pseudocode | `PayoutMessages.settled` — one template per outcome; only an unavailable rail leaves the request open | `PayoutMessagesTest.eachOutcomeGetsItsOwnTemplate`, `anUnavailableRailLeavesTheRequestOpen` |
| REQ-620 | Carry the bank's own reference so the user can chase it | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `PayoutMessages.settled` — `bankReference` and `fmsReference` as separate fields; substituting one for the other is refused | `PayoutMessagesTest.ourReferenceCannotBeSentAsTheBanks`, `anUnavailableBankReferenceIsSaidToBePending` |
| REQ-621 | Generate every message from the same figures as the screen | hld.md#11-the-one-idea-the-architecture-is-organised-around | lld-backend.md#communications | `messaging/MessageOutbox` + `MessageRelay.dispatch` — intents carry no figures; parameters resolve at dispatch from one derive() call | `PayinOrchestratorTest` — the queued intents carry no figures, only the template and assertion |
| REQ-622 | Queue messages against the event, not the schedule | hld.md#12-async--messaging | lld-backend.md#31-design-patterns-and-why-each-earns-its-place | `backend/fund-management-service/src/main/java/com/thinq/fms/messaging/MessageRelay.java` — the asserted state is re-checked before submission, never after, so a resolved state drops the message rather than sending and retracting; `DropReason.STATE_RESOLVED` is journalled. `StateAssertionChecker` is the seam that makes the ordering testable. Partial: the ladder schedule that creates the steps is TASK-35, unbuilt | `MessageRelayTest` (6) |
| REQ-623 | Log delivery per channel with its outcome, visible to support | hld.md#91-what-fms-stores-and-what-it-does-not | lld-backend.md#22-database-schema | `backend/fund-management-service/src/main/resources/db/migration/V26__fms_message_delivery.sql` — per-channel delivery rows with outcome; `backend/fund-management-service/src/main/java/com/thinq/fms/integration/communication/CommunicationClient.java` and `DeliveryStatus.java` — the service's ten-value vocabulary, and the per-channel submission that makes Rule C1's two channels two independently failing rows. Partial: the dispatcher and reconciler (TASK-34, TASK-36) are unbuilt The reconciler in `backend/fund-management-service/src/main/java/com/thinq/fms/messaging/DeliveryReconciler.java` decides settle, resubmit under a new request id, or alert — never a silent retry. | `MessageRelayTest` |
| REQ-624 | Require explicit WhatsApp opt-in, captured with date and surface | hld.md#91-what-fms-stores-and-what-it-does-not | lld-backend.md#communications | `messaging/ChannelPreferences.WhatsappOptIn` — date and capture surface both required | `ChannelPreferencesTest.anOptInWithoutProvenanceIsRefused` |
| REQ-625 | Version all templates; a copy change is a new version, not an edit | hld.md#91-what-fms-stores-and-what-it-does-not | lld-backend.md#22-database-schema | `backend/fund-management-service/src/main/resources/db/migration/V25_1__fms_message_intent.sql` — versioned intent rows, `fms_intent_once` uniqueness. Partial: schema only, no template store written Plus `backend/fund-management-service/src/main/java/com/thinq/fms/integration/communication/NotificationSubmission.java` — templates addressed by key, never by version id, with the resolved `template_id` stored per delivery | `CommunicationClientTest` (16) |
| REQ-626 | Cover WhatsApp and non-regulatory email in preferences; SMS stays fixed | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#communications | `ChannelPreferences.controllable()` — WhatsApp and email only; no preference can permit an optional SMS | `ChannelPreferencesTest.smsIsNotControllable`, `noPreferencePermitsAnOptionalSms` |
| REQ-627 | Keep an SMS-only user reachable and flag the account for support | hld.md#122-what-each-message-must-carry-and-which-channel-carries-it | lld-backend.md#communications | `backend/fund-management-service/src/main/java/com/thinq/fms/messaging/DeliveryReconciler.java` — a terminal failure on one channel is recorded and alerted even when the other succeeded, because a trader reachable on only one channel is a fact support needs | `DeliveryReconcilerTest` (15) |
| REQ-701 | Enforce caps per day per route against everything already sent | hld.md#8-api-design--network-perimeter | lld-backend.md#22-database-schema | `backend/fund-management-service/src/main/resources/db/migration/V23__fms_route_cap_usage.sql` — per-day per-route usage rows. Partial: cap enforcement logic unwritten Plus `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/RouteCapLedger.java` and `RouteCap.java` — headroom measured against the day's accumulation rather than per transaction, and a refusal that carries the remaining figure rather than being generic. Exposed as `GET /api/v1/funds/payin/limits` in `backend/fund-management-service/src/main/java/com/thinq/fms/api/PayinLimitsController.java`, returning figures rather than copy per Rule G1. | `RouteSelectorTest` (8), `PayinOrchestratorTest` |
| REQ-702 | Select the route automatically via the payment gateway integration | hld.md#8-api-design--network-perimeter | lld-backend.md#42-request-and-response-dtos | `backend/fund-management-service/src/main/java/com/thinq/fms/movement/payin/RouteSelector.java` — the system selects and the trader does not; preference order with automatic fallback, and `SelectedRoute.switchedFrom` carries Rule A12's obligation to disclose a re-route. Partial: `PayinOrchestrator` (TASK-29) unbuilt | `RouteSelectorTest` — automatic route selection against the cap ledger |
| REQ-703 | Waive the minimum add amount when it exactly settles an outstanding debit | hld.md#8-api-design--network-perimeter | lld-backend.md#42-request-and-response-dtos | `settings/MinimumAddPolicy` — the exact outstanding debt is permitted below `minAdd`; anything above it meets the ordinary floor | `MinimumAddPolicyTest` (10) — the waiver is exact, and the suggestion follows the debt |
| REQ-706 | Use the primary account as the default withdrawal destination | hld.md#162-money-movement-controls | lld-backend.md#configuration | `funding/FundingSource.resolve` — the primary verified account as the default for both directions | `FundingSourceTest` — an unverified primary does not win over a verified non-primary |
| REQ-706a | Name "no verified bank account" as a blocker instead of offering add-funds | hld.md#131-client-surfaces--what-each-owns | lld-backend.md#account-health | `funding/FundingSource.Availability` — NO_ACCOUNT and AWAITING_VERIFICATION named separately; `mayPresentForm()` gates the form | `FundingSourceTest` (6) — both blockers, and one verified account needing no choice |
| REQ-707 | State the expected arrival date before commitment, from the EOD boundary | hld.md#8-api-design--network-perimeter | lld-backend.md#withdraw-funds | `settings/ArrivalDateCalculator` + `ArrivalQuote` — computed against `payoutCutoff` in the account zone, with each deferring cause named | `ArrivalDateCalculatorTest` (7) — cut-off boundary, weekend skip, zone handling, and an unavailable calendar reporting no date |
| REQ-708 | Read the debit interest rate from configuration, never restate it in copy | hld.md#165-compliance-obligations-carried-by-the-design | lld-backend.md#configuration | `settings/DebitInterestRate` — configured / provisional / unavailable as three distinct states; `quotableInMessages()` is false for the latter two | `DebitInterestRateTest` (6) — the shipped default is provisional and therefore unquotable; zero is a rate, not an absence |
| REQ-709 | Offer Trade Now as the post-funding destination on the confirmation | hld.md#131-client-surfaces--what-each-owns | lld-frontend.md#75-postfundingconfirmation--req-709-req-710 | **NOT IMPLEMENTED — deferred.** No frontend exists: the React client in `lld-frontend.md` was never built, and this requirement has no backend expression. No code claims it. | **NOT TESTED — deferred with the code.** Unverifiable rather than unverified; see `browser-report.md`. No backend result is partial credit toward it. |
| REQ-710 | Fall back to a plain dismissal where no destination is configured | hld.md#131-client-surfaces--what-each-owns | lld-frontend.md#75-postfundingconfirmation--req-709-req-710 | **NOT IMPLEMENTED — deferred.** No frontend exists: the React client in `lld-frontend.md` was never built, and this requirement has no backend expression. No code claims it. | **NOT TESTED — deferred with the code.** Unverifiable rather than unverified; see `browser-report.md`. No backend result is partial credit toward it. |

**In-scope requirement count: 66.** Nine rows were added on 20 Aug 2026 when Stage 1's iteration wrote
REQ-602 to REQ-604, REQ-608, REQ-609, REQ-614, REQ-615, REQ-619 and REQ-620 from sources that already
carried their behaviour. REQ-605 to REQ-607 and REQ-610 were withdrawn rather than written and are
deliberately absent from both tables — a withdrawn requirement is not an excluded one, it is a
requirement that never existed. REQ-108 moved into this table on 21 Aug 2026 when Stage 1 relabelled it
Must Have: its display is deferred, but its data-model obligation is not, and a deferred display with a
live obligation belongs inside the gate.

---

## Excluded from this phase

These carry a PRD-stated reason for exclusion and are deliberately outside the
Stage 9 coverage gate. Moving any row back into the matrix above is a scope
change and needs explicit user approval, per `prd-to-prod.md` Non-Negotiable
Rule 8.

| REQ ID | Requirement Summary | Exclusion recorded in the PRD |
|--------|---------------------|-------------------------------|
| REQ-304 | Offer a faster route where the account qualifies |  |  | REQ-406 | Reconcile any period end to end |  |
| REQ-704 | Add, delete, and change which bank account is primary |  | lld-backend.md#configuration | REQ-705 | Cap holdings at `maxBankAccounts` accounts |  |

---

## Open items carried into design

1. ~~**Thirteen communications IDs are cited but never defined.**~~ **Closed 20 Aug 2026.** Stage 2
   raised this as a blocker and Stage 1's iteration resolved it: ten were written from the template
   list, outcome tables, channel matrix and cadence that already carried their behaviour, and four
   were withdrawn because their only basis was a passing clause. The register now reconciles with
   the files — 70 defined requirements against 66 Must-Have, 2 Should-Have and 2 excluded.
2. **Two Must-Have requirements are blocked on external inputs.** REQ-303 and REQ-707 wait on EB-9,
   the holiday calendar source; REQ-501 waits on EB-8, the confirmed debit interest rate. Stage 3
   established that EB-9 also blocks REQ-307 and Rule B4, and that Rule B4's dependency makes EB-9 a
   **Phase 1** gate rather than a Phase 3 one — see `hld.md#21-risk-analysis` R1.
3. **REQ-503's HLD cell is deliberately empty, and the gate will flag it.** That is the intended
   behaviour, not an oversight. REQ-503 requires warning before a scheduled charge takes the account
   into debit, and it cannot be designed until EB-6 establishes whether the back office exposes a
   charge *before* it posts. There is no HLD-level design for it, so the cell is empty rather than
   pointed at the open-questions section — an empty cell is a signal the gate is built to catch, and a
   citation to a section that merely records the blocker would have defeated it.

   Stage 4's review found nine cells doing exactly that, and this row is the one case where the honest
   answer is to leave it empty. It resolves one of two ways: EB-6 closes and the requirement is designed,
   or the PRD descopes REQ-503 to Phase 5 and it moves to the excluded table. Both are product calls.
4. **The instrumentation PRD is reviewed separately.**
   `03-instrumentation/product-requirements-events-and-funnels.md` matches the split-part filename
   pattern but is not in the index `parts:` array. Its requirement IDs are therefore not in this matrix.
   Carried as finding M6 through three PRD reviews and still open.
5. **Whether this product is already partly built.** Stage 3 found a running Spring Boot service in the
   same estate that owns a trader's money, while the PRD records demand as unevidenced because no
   version exists. Recorded at `hld.md#23-open-questions` item 7. If the answer is that they are the
   same product, several rows above may already have code coverage that predates this pipeline.

---

## LLD Coverage — what Stage 5a filled and what it deliberately did not

Stage 5a filled **60 of 66** LLD cells. The six it left are not gaps in the backend design; they are
requirements whose substance is presentation, plus one that cannot be designed yet.

| Row | Why the cell is empty |
|---|---|
| REQ-201 | Amount entry — Rule A1's pre-fill, Rule A13's keystroke refusal, Rule A2's suggestion behaviour. The backend supplies the applicable minimum and the debt waiver; everything the requirement is *about* is the field. **Stage 5b** |
| REQ-301 | The always-visible, disabled-with-a-reason entry point. The backend supplies availability and the responsible rule in `ActionAvailabilityDto`; whether the control is present, disabled and legible is the requirement. **Stage 5b** |
| REQ-504 | The empty account's single statement and one action, against Rule H5's fifteen-zeros failure. Entirely presentation. **Stage 5b** |
| REQ-709, REQ-710 | The post-funding destination and its fallback. The backend returns the configured destination or none; offering it or dismissing cleanly is the requirement. **Stage 5b** |
| REQ-503 | Not designable until EB-6 establishes whether a not-yet-posted charge is exposed. Empty in both columns, deliberately, and the Stage 9 gate will keep flagging it until it is designed or descoped | **NOT DESIGNED — deferred.** Blocked on EB-6: the charge-prediction source is unnominated, so there is nothing to design against. Recorded as undesigned at Stage 6 and carried unchanged. 

Stage 5a's own coverage was verified the way Stage 4 verified the HLD: every cell cites a section of
`lld-backend.md` that **names the requirement**, checked by parsing rather than by reading. An earlier
draft named only 14 of 70, and rather than repoint the cells at sections that merely sounded relevant,
§7.8 was written to deliver the contract-depth coverage §1.1 had promised.

## Column ownership

| Column | Filled by | Source artifact |
|---|---|---|
| REQ ID / Summary | Stage 1 | `product-requirements.md` (+ all `parts:` files) |
| HLD Coverage | Stage 3 | `hld.md` |
| LLD Coverage | Stage 5a / 5b | `lld-backend.md` / `lld-frontend.md` |
| Code Coverage | Stage 8 | source files |
| Test Coverage | Stage 10 | `test-report.md` |

Cell format: a link to the specific section or file that covers the requirement,
e.g. `hld.md#12-async--messaging` or `src/services/PayoutService.java`.

---

## Metadata

- **Created:** 2026-08-20, Stage 1
- **Last updated:** 2026-08-21, Stage 3 iteration 1 — HLD Coverage re-derived after Stage 4 found nine
  fabricated citations. Every cell now points at a section that **names the requirement it claims to
  cover**, verified mechanically rather than by reading. 65 of 66 filled; REQ-503 empty by decision,
  see open item 3
- **PRD index:** `docs/specs/001-fund-management-system/product-requirements.md`
  (symlinked to `02-requirements/product-requirements.md`, which stays the
  editable source)
- **PRD parts:** `product-requirements-balances-and-margin.md`,
  `product-requirements-add-funds.md`, `product-requirements-withdraw-funds.md`,
  `product-requirements-transactions-and-statements.md`,
  `product-requirements-account-health.md`,
  `product-requirements-communications.md`,
  `product-requirements-configuration.md`

| SECURITY-APPROVAL | Stage 11 security review accepted with open findings | — | — | `.ai/artifacts/security-review.md` — verdict APPROVED_WITH_CONDITIONS on 2026-08-22; findings accepted: 1 MEDIUM, 1 LOW (LOW-1 and LOW-3 closed 2026-08-22); MEDIUM-1 closed within the stage | **Approver name PENDING** — the approval record in `security-review.md` is incomplete until a named individual is recorded, and the acceptance is provisional until then |

---

## Stage 10 re-run — 24 Aug 2026, requirements-derived test catalogue

Appended, not re-derived. The Test Coverage column above is unchanged; this section records what
executing it against a full scenario enumeration established, and is the entry point for anyone
asking "which requirements are actually verified?"

| Artefact | Value |
|---|---|
| Catalogue | `docs/qa/test-cases.md` — 613 cases, each tracing to a REQ ID or business rule |
| Suite | 740 tests, 0 failures, 0 errors, 0 skipped (`mvn clean test`) |
| Coverage | 94.5% instruction, 85.4% branch (JaCoCo 0.8.12, command line) |
| Added this pass | 141 tests in `com.thinq.fms.qa` — four value-contract classes |
| Reports | `test-report.md`, `browser-report.md` |

| Requirement group | Catalogue cases | Executed | Blocked, and on what |
|---|---:|---:|---|
| REQ-101 to REQ-108 — balances & margin | 60 | 51 | 9 — the absent client and the halted margin source (TASK-11) |
| REQ-201 to REQ-207 — adding funds | 68 | 58 | 10 — the absent client, and the payin endpoints the LLD defines but that are unbuilt |
| REQ-301 to REQ-308 — withdrawing funds | 84 | 68 | 16 — **all sixteen are the end-of-day run**, which does not exist |
| REQ-401 to REQ-407 — transactions & statements | 68 | 60 | 8 — the absent client and the stamped period endpoints |
| REQ-501 to REQ-506 — account health | 34 | 24 | 10 — the absent client and `/funds/health` |
| REQ-601 to REQ-627 — communications | 117 | 114 | 2 — the preference surface and the funds banner |
| REQ-701 to REQ-710 — configuration | 26 | 22 | 4 — Profile-owned screens and the summary endpoint |

**Coverage claims corrected by this pass.** The row for REQ-101 and REQ-103 to REQ-108 previously
read "`MarginFigures` is at 0% coverage because nothing constructs it." That is no longer true:
`MarginFigures` is at 100% instruction and branch, along with every other derivation value type.
The requirements themselves remain **NOT IMPLEMENTED** and blocked on TASK-11 — what changed is that
the types the implementation will produce now have an executable contract rather than none. The
distinction matters for the gate: these cells are empty because the code does not exist, not because
the code exists untested.

**Findings raised, carried into the next stage:**

| ID | Severity | Requirement touched | Summary |
|---|---|---|---|
| QA-01 | LOW | Taxonomy R4 | `AccountRef` bounds charset and length only, so a PAN-shaped value is accepted. R4's protection rests on the gateway's subject claim, which nothing in this repository asserts. Pinned by a test that documents the limitation |
| QA-02 | MEDIUM | REQ-401, REQ-404, Rule L9 | `TechExcelLedgerGateway` at 42.7% instruction and 21.9% branch — the read-through under every entry and running balance the trader sees. Its vendor failure paths are unexercised |
| QA-03 | PROCESS | — | The working tree was modified by another process mid-run, producing one transient failure. No defect; recorded because a test result is only evidence if the tree was still when it ran |

- **Last updated:** 2026-08-24, Stage 10 re-run — catalogue authored, 141 tests added, suite and
  coverage re-measured. Verdict unchanged: **NO-GO**, on the frontend, the end-of-day run, and the
  margin source.
