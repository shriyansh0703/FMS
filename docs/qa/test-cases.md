# FMS Test Case Catalogue

Every scenario this system is required to handle, written so a person can read one and know what
to do and what should happen — independent of any test framework. The cases are derived from the
PRD, the HLD and the LLD, not from reading the implementation, so a case can fail because the code
is wrong rather than only because a test is.

| | |
|---|---|
| System under test | Fund Management System, `backend/fund-management-service` |
| Sources | `docs/specs/001-fund-management-system/` (8 files) · `.ai/artifacts/hld.md` · `.ai/artifacts/lld-backend.md` · `.ai/artifacts/lld-frontend.md` |
| Cases | **613** |
| Drafted | 24 Aug 2026 |
| Execution results | `.ai/artifacts/test-report.md` |

## How to read a case

Each row is one scenario: the state the account is in, the thing that happens, and what the system
must do about it. The **Traces** column names the requirement or business rule the expectation comes
from, so a disputed expectation is settled by reading the PRD rather than by arguing about the test.
The **Evidence** column says how the case was executed.

| Evidence | Meaning |
|---|---|
| A test name | Executed automatically on every build. `Class#method` under `backend/fund-management-service/src/test/java`. |
| `BLOCKED — no client` | The behaviour is a rendering rule and no frontend exists. Not testable, not merely untested. |
| `BLOCKED — endpoint absent` | The LLD defines the endpoint; it has not been built. |
| `BLOCKED — module absent` | The mechanism itself has not been built (the end-of-day run, the margin source). |
| `MANUAL` | Requires a person, a live vendor, or a production observation. |

## Where the coverage actually is

| Section | Cases | Executed | Blocked | Manual |
|---|---:|---:|---:|---:|
| [TC-BAL — Balances & margin](#tc-bal--balances--margin) | 60 | 51 | 9 | 0 |
| [TC-ADD — Adding funds](#tc-add--adding-funds) | 68 | 58 | 10 | 0 |
| [TC-WDR — Withdrawing funds](#tc-wdr--withdrawing-funds) | 84 | 68 | 16 | 0 |
| [TC-TXN — Transactions & statements](#tc-txn--transactions--statements) | 68 | 60 | 8 | 0 |
| [TC-HLT — Account health](#tc-hlt--account-health) | 34 | 24 | 10 | 0 |
| [TC-COM — Communications](#tc-com--communications) | 117 | 114 | 2 | 1 |
| [TC-CFG — Configuration](#tc-cfg--configuration) | 26 | 22 | 4 | 0 |
| [TC-API — API contract & edge layer](#tc-api--api-contract--edge-layer) | 30 | 30 | 0 | 0 |
| [TC-SEC — Security & disclosure](#tc-sec--security--disclosure) | 35 | 32 | 1 | 2 |
| [TC-DATA — Persistence & constraints](#tc-data--persistence--constraints) | 30 | 30 | 0 | 0 |
| [TC-INT — Vendor integrations](#tc-int--vendor-integrations) | 33 | 33 | 0 | 0 |
| [TC-NFR — Concurrency, resilience, performance](#tc-nfr--concurrency-resilience-performance) | 28 | 18 | 4 | 6 |
| **Total** | **613** | **540** | **64** | **9** |

The blocked count is the honest headline. Sixty-four scenarios cannot be executed at all, and they
are not spread evenly: they concentrate in the three places where the product's promise lives —
what the trader sees on the funds screen, what happens in the end-of-day payout run, and where
margin figures come from. A green suite says nothing about any of them.

Concentration matters more than the total. Withdrawal carries sixteen blocked cases and every one of
them describes the end-of-day run, which is the only path where being wrong moves money
irreversibly. Account health carries ten, and all ten are the states a trader is actually in when
they arrive. Communications carries two, because that module was built.

---

## TC-BAL — Balances & margin

Rule B4's derivation is the most consequential rule in the PRD, and it is the one place where the
calculation is complete and the presentation is entirely absent. Every case below that describes a
figure being *computed* is executed; every case that describes a figure being *shown* is blocked on
a client that was designed and never built.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-BAL-001 | An account with a settled balance, no deductions | Compute the withdrawable figure | All six of Rule B4's terms are present, including those valued at zero | REQ-102, B4 | `WithdrawableCalculatorTest#everyTermIsPresentIncludingZeroValued` |
| TC-BAL-002 | Each of the six inputs set to a distinct value | Compute the derivation | Each input appears against its own term and no other | B4 | `WithdrawableCalculatorTest#everyInputIsWiredToItsOwnTerm` |
| TC-BAL-003 | Any set of inputs | Sum the six signed terms | The sum equals the pre-floor figure exactly | B4, B12 | `WithdrawableCalculatorTest#termsSumToPreFloorFigure` |
| TC-BAL-004 | Deductions exceeding the balance | Compute the withdrawable figure | The figure is zero, never negative | B9 | `WithdrawableCalculatorTest#withdrawableIsNeverNegative` |
| TC-BAL-005 | Deductions less than the balance | Compute the withdrawable figure | The floor does not engage; the figure is the raw sum | B9 | `WithdrawableCalculatorTest#flooringEngagesOnlyBelowZero` |
| TC-BAL-006 | A shortfall larger than the balance | Compute the withdrawable figure | The figure is zero and the shortfall term is still shown at full value | REQ-506, B4 | `WithdrawableCalculatorTest#shortfallTermRemainsVisibleWhenItFloorsTheFigure` |
| TC-BAL-007 | Collateral covered part of the margin requirement | Compute the withdrawable figure | The collateral-met term is added, not deducted | B4 | `WithdrawableCalculatorTest#collateralMetIsAdditive` |
| TC-BAL-008 | A negative settled ledger balance | Compute the derivation | The balance is carried as a minus on its own term, not clamped | B9 | `WithdrawableCalculatorTest#negativeLedgerBalanceIsCarriedAsAMinusOnItsOwnTerm` |
| TC-BAL-009 | Several deductions of different sizes | Compute the derivation | The largest deduction is named without the trader opening the panel | REQ-102 | `WithdrawableCalculatorTest#largestDeductionIsTheLargest` |
| TC-BAL-010 | No deductions at all | Compute the derivation | No largest deduction is named, rather than one named at zero | REQ-102 | `WithdrawableCalculatorTest#noDeductionMeansNoLargestDeduction` |
| TC-BAL-011 | An amount in paise | Convert to and from the vendor's rupee decimal | The value round-trips exactly, with no float anywhere | R5 | `MoneyTest#vendorDecimalsConvertBothWays` |
| TC-BAL-012 | Two monetary amounts | Add and subtract them | The arithmetic is exact at paise precision | R5 | `MoneyTest#arithmeticIsExact` |
| TC-BAL-013 | An amount at the long boundary | Add or subtract past the boundary | The operation throws rather than wrapping silently | R5 | `MoneyTest#additionOverflowThrows`, `#subtractionOverflowThrows` |
| TC-BAL-014 | The smallest representable amount | Negate it | The operation throws rather than returning itself | R5 | `MoneyTest#negatingTheSmallestLongThrows` |
| TC-BAL-015 | A negative, zero and positive amount | Ask each predicate | The three predicates partition the number line with no overlap | B9 | `MoneyTest#predicatesPartitionTheNumberLine` |
| TC-BAL-016 | A negative amount | Apply the zero floor | Only the negative side clamps; zero and positive are returned unchanged | B9 | `MoneyTest#flooredAtZeroClampsOnlyTheNegativeSide` |
| TC-BAL-017 | Amounts of differing size | Order them | Ordering is by paise, not by string or decimal | R5 | `MoneyTest#orderingIsByPaise` |
| TC-BAL-018 | A vendor decimal string | Read it as money | It is read exactly, with no rounding introduced | R5 | `MoneyTest#vendorDecimalIsReadExactly` |
| TC-BAL-019 | The V25 migration's margin-source vocabulary | Compare it to the enum | The two agree exactly | B12 | `VocabularyDriftTest#marginSourceKindMatchesV25` |
| TC-BAL-020 | The V25 migration's verdict vocabulary | Compare it to the enum | The two agree exactly | B12 | `VocabularyDriftTest#withdrawableVerdictMatchesV25` |
| TC-BAL-021 | The V25 migration's derivation-context vocabulary | Compare it to the enum | The two agree exactly | Rule W11 | `VocabularyDriftTest#derivationContextMatchesV25` |
| TC-BAL-022 | Two of the three balances are equal | Render the funds view | They are still presented as two named figures, not collapsed into one | REQ-101 | BLOCKED — no client |
| TC-BAL-023 | An account holding no money at all | Render the funds view | One statement of the account's state, not a zero repeated per component | REQ-101, H5 | BLOCKED — no client |
| TC-BAL-024 | Any account | Read the three balances anywhere they appear | Each figure has exactly one name on every surface | REQ-101, B2 | BLOCKED — no client |
| TC-BAL-025 | The withdrawable figure is below the balance | Render the funds view | The largest single deduction is named without opening the derivation | REQ-102 | BLOCKED — no client |
| TC-BAL-026 | A settlement holiday extends a deduction | Render the derivation | The holiday is named as the cause of the larger deduction | B6 | BLOCKED — no client |
| TC-BAL-027 | A margin component cannot be obtained from RMS | Render the margin breakdown | The component reads *unavailable* with its last-known instant, never ₹0.00 | B10 | BLOCKED — endpoint absent |
| TC-BAL-028 | A margin component is negative | Render the margin breakdown | It is shown as negative, not clamped to zero | B9 | BLOCKED — endpoint absent |
| TC-BAL-029 | An account holding collateral and no cash | Render the funds view | Available margin is positive, withdrawable is zero, and both are explained | REQ-104, B5 | BLOCKED — no client |
| TC-BAL-030 | Margin figures older than the refresh interval | Attempt an action that commits money | The action is refused with staleness stated as the reason | REQ-107 | BLOCKED — module absent |
| TC-BAL-031 | A margin snapshot reporting a negative shortfall | Construct it | Refused: a shortfall is a magnitude, and a surplus belongs in available margin | B7, B9 | `qa.DerivationContractTest#aNegativeShortfallIsRefused` |
| TC-BAL-032 | A margin snapshot with a negative collateral value | Construct it | Refused | B7 | `qa.DerivationContractTest#aNegativeCollateralValueIsRefused` |
| TC-BAL-033 | A negative collateral-met portion | Construct the snapshot | Refused | B4 | `qa.DerivationContractTest#aNegativeCollateralMetPortionIsRefused` |
| TC-BAL-034 | Any margin field absent | Construct the snapshot | Refused, rather than defaulted to zero | B10 | `qa.DerivationContractTest#everyMarginFieldIsRequired` |
| TC-BAL-035 | A shortfall of exactly zero | Ask whether a shortfall exists | No — a zero shortfall is not a shortfall | REQ-506 | `qa.DerivationContractTest#aShortfallExistsOnlyAboveZero` |
| TC-BAL-036 | Used margin exceeding available margin | Construct the snapshot | Accepted: a fully deployed account is an ordinary state | B9 | `qa.DerivationContractTest#usedMarginMayExceedAvailableMargin` |
| TC-BAL-037 | Negative available margin after an adverse move | Construct the snapshot | Accepted and carried as negative | B9 | `qa.DerivationContractTest#availableMarginMayBeNegative` |
| TC-BAL-038 | A negative settled ledger balance | Build the derivation inputs | Accepted — a debit balance is a real state | B9, H1 | `qa.DerivationContractTest#theSettledLedgerBalanceMayBeNegative` |
| TC-BAL-039 | Any deduction input given a negative value | Build the derivation inputs | Refused, naming the field | B4 | `qa.DerivationContractTest#everyDeductionInputIsAMagnitude` |
| TC-BAL-040 | A derivation input absent | Build the inputs | Refused rather than defaulted to zero | B10 | `qa.DerivationContractTest#aMissingInputIsRefused` |
| TC-BAL-041 | A term given a negative magnitude | Construct the term | Refused: direction lives in the sign, not the amount | REQ-102 | `qa.DerivationContractTest#aTermWithANegativeMagnitudeIsRefused` |
| TC-BAL-042 | A PLUS and a MINUS term of equal magnitude | Read each contribution | They contribute in opposite directions | REQ-102 | `qa.DerivationContractTest#aTermsSignedContributionFollowsItsSign` |
| TC-BAL-043 | A term valued at zero | Read its direction | It still declares whether it increases or reduces the figure | REQ-102 | `qa.DerivationContractTest#aZeroValuedTermStillDeclaresItsDirection` |
| TC-BAL-044 | A PLUS term | Ask whether it is a deduction | No | REQ-102 | `qa.DerivationContractTest#onlyAMinusTermIsADeduction` |
| TC-BAL-045 | The two signs | Read their multipliers | +1 and −1 | REQ-102 | `qa.DerivationContractTest#theTwoSignsMultiplyInOppositeDirections` |
| TC-BAL-046 | A derivation built with five terms | Construct it | Refused: Rule B4 has six and all are shown | REQ-102, B4 | `qa.DerivationContractTest#aDerivationMissingATermIsRefused` |
| TC-BAL-047 | A derivation whose figure does not follow from its terms | Construct it | Refused: the figure and its explanation are one object | B12 | `qa.DerivationContractTest#aDerivationWhoseFigureDoesNotFollowIsRefused` |
| TC-BAL-048 | A pre-floor sum below zero | Construct the derivation with a negative figure | Refused; the same sum with a zero figure is accepted | B9 | `qa.DerivationContractTest#aNegativePreFloorSumMustPresentAsZero` |
| TC-BAL-049 | A genuinely zero balance, and one driven below zero | Ask each whether it floored | Only the second reports flooring | B9, REQ-102 | `qa.DerivationContractTest#flooringIsDistinguishableFromAZeroBalance` |
| TC-BAL-050 | A derivation built from a caller's list | Mutate the caller's list afterwards | The derivation is unchanged and its terms cannot be modified | B12 | `qa.DerivationContractTest#aDerivationsTermsCannotBeMutated` |
| TC-BAL-051 | Rule B4 as written in the PRD | Compare it to the term vocabulary | Six terms, in the order the trader reads them | B4 | `qa.DerivationContractTest#ruleB4sSixTermsAreTheEnumInOrder` |
| TC-BAL-052 | A reconciled verdict with no derivation attached | Construct the result | Refused | B12 | `qa.DerivationContractTest#aReconciledResultWithoutItsDerivationIsRefused` |
| TC-BAL-053 | Each of the three verdicts | Ask for a figure the trader may act on | Only RECONCILED yields one | REQ-102 | `qa.DerivationContractTest#onlyReconciledYieldsAnActionableFigure` |
| TC-BAL-054 | The derivation and RMS disagree | Ask for the withdrawable figure | Empty — RMS's answer is not silently substituted | OA-1 | `qa.DerivationContractTest#aDivergentVerdictDoesNotResolveToRms` |
| TC-BAL-055 | A divergent verdict | Ask for the derivation | Still present, so the disagreement can be explained | REQ-102 | `qa.DerivationContractTest#aDivergentResultStillCarriesItsDerivation` |
| TC-BAL-056 | A source outage | Read the unavailable result | It still states when and by which source it was computed | REQ-107 | `qa.DerivationContractTest#anUnavailableResultStillStatesItsProvenance` |
| TC-BAL-057 | A result with no verdict | Construct it | Refused | B12 | `qa.DerivationContractTest#aVerdictIsRequiredOnEveryResult` |
| TC-BAL-058 | Each verdict in turn | Compare actionability against the figure's presence | The two never disagree | REQ-102 | `qa.DerivationContractTest#actionabilityAndAPresentFigureAgree` |
| TC-BAL-059 | The market-open handover | Read which source answered | One of exactly two, so a stepped figure reads as a handover | REQ-107 | `qa.DerivationContractTest#theSourceIsOneOfTheTwo` |
| TC-BAL-060 | The design's three verdict states | Compare to the enum | Exactly RECONCILED, DIVERGENT, UNAVAILABLE | OA-1 | `qa.DerivationContractTest#theThreeVerdictsAreExactlyTheStatesDefined` |

---

## TC-ADD — Adding funds

Money entering the account: choosing an amount, the route chosen for the trader, the journey while
in flight, and the six distinct ways a well-formed payment can still fail. The orchestration and the
outcome vocabulary are complete and heavily exercised; the two things missing are the HTTP surface
that would start an attempt and the screen that would collect the amount.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-ADD-001 | An account with a prior successful deposit | Open the funds summary | The last successfully added amount is supplied, so the field can open on it | REQ-201, A1 | `PayinOrchestratorTest#lastSuccessfulDeposit` |
| TC-ADD-002 | An account with no successful deposit, only abandoned attempts | Open the funds summary | No amount is supplied — an abandoned attempt is not a fact about what this trader funds | A1 | `PayinOrchestratorTest#lastSuccessfulDeposit` |
| TC-ADD-003 | An amount within the selected route's headroom | Select a route | The first-choice route is used, unchanged | REQ-702 | `RouteSelectorTest#withinHeadroomTakesTheFirstChoice` |
| TC-ADD-004 | An amount above the first route's remaining headroom, another route can carry it | Select a route | The route changes automatically and the change is disclosed | A12, REQ-702 | `RouteSelectorTest#automaticRerouteDisclosesTheSwitch` |
| TC-ADD-005 | An amount above every route's headroom | Select a route | Refused, stating the remaining headroom rather than a generic message | REQ-701, A12 | `RouteSelectorTest#refusalStatesRemainingHeadroom` |
| TC-ADD-006 | Half the daily cap already sent today on a route | Enter the same amount again | Refused — the cap is measured against today's usage, not per transaction | REQ-701 | `RouteSelectorTest#capIsDailyAndNotPerTransaction` |
| TC-ADD-007 | A route with no configured cap | Select it for a large amount | It carries the amount; an absent cap is unbounded, not zero | REQ-701 | `RouteSelectorTest#uncappedRouteCarriesTheRest` |
| TC-ADD-008 | A route absent from configuration | Attempt to select it | It is not executable and is not offered | A3 | `RouteSelectorTest#unconfiguredRouteIsNotExecutable` |
| TC-ADD-009 | More sent today than the cap allows | Read the remaining headroom | Zero, and the over-cap condition is flagged separately for an operator | REQ-701 | `RouteSelectorTest#headroomFloorsAtZeroAndFlagsBeingOverCap` |
| TC-ADD-010 | A payment has failed | Offer an alternative route | Only a route that can be executed and has headroom today is offered | A9d | `RouteSelectorTest#alternativesMustBeAbleToWork` |
| TC-ADD-011 | A trader with no verified bank account | Start a payin | Refused, naming the absent verified source as the blocker | REQ-203, REQ-505 | `PayinOrchestratorTest#noVerifiedSourceIsRefused` |
| TC-ADD-012 | A payin started | Read the three balances | The in-flight attempt affects none of them | REQ-204, A5 | `PayinOrchestratorTest#inFlightAffectsNoBalance` |
| TC-ADD-013 | A payin confirmed once | Deliver the same confirmation again | Nothing changes; the money is recorded once | REQ-204, A6 | `PayinOrchestratorTest#repeatConfirmationIsANoOp` |
| TC-ADD-014 | A payin confirmed once | Deliver the same confirmation again | No second message is queued either | A6, REQ-622 | `PayinOrchestratorTest#aRepeatConfirmationQueuesNothingFurther` |
| TC-ADD-015 | A confirmation arriving for a reference this system does not hold | Process it | Refused rather than credited | A4, A6 | `PayinOrchestratorTest#unknownReferenceIsRefused` |
| TC-ADD-016 | A gateway answering with a different payment reference than the one asked about | Process it | Refused — the answer does not match the question | A6 | `PayinOrchestratorTest#aGatewayAnsweringForADifferentReferenceIsRefused` |
| TC-ADD-017 | The trader abandoned the attempt and left | A confirmation arrives afterwards | The money is recorded and the trader is told | A7 | `PayinOrchestratorTest#lateConfirmationStillLands` |
| TC-ADD-018 | A payin already recorded as failed | A confirmation arrives | The transition is refused rather than silently overwriting the outcome | A9, L2 | `PayinOrchestratorTest#failedCannotBecomeConfirmed` |
| TC-ADD-019 | The gateway does not answer | Read the attempt's outcome | *Unknown*, distinct from failed, and the trader is told not to pay again | A9b, REQ-611 | `PayinOrchestratorTest#unknownOutcomeIsAwaitingNotFailed` |
| TC-ADD-020 | An outcome that is still unknown | Ask what to announce | Nothing is announced yet | A9b, REQ-622 | `PayinOrchestratorTest#anUnresolvedOutcomeAnnouncesNothing` |
| TC-ADD-021 | A confirmed deposit later found invalid | Reverse it | A compensating entry is added and both entries remain | REQ-206, A10 | `PayinOrchestratorTest#reversalKeepsBothEntries` |
| TC-ADD-022 | A payin that was never confirmed | Attempt to reverse it | Refused — only a confirmed payin is reversible | A10 | `PayinOrchestratorTest#onlyConfirmedCanBeReversed` |
| TC-ADD-023 | A payin started | Inspect the outbox | One chase message is queued for 30 minutes out | REQ-611 | `PayinOrchestratorTest#startingAPayinQueuesTheChase` |
| TC-ADD-024 | A payin confirmed | Inspect the outbox | The confirmation message is queued | REQ-612, REQ-613 | `PayinOrchestratorTest#aConfirmedPayinQueuesItsConfirmation` |
| TC-ADD-025 | A payin failed | Inspect the outbox | The message for that specific outcome is queued | REQ-614 | `PayinOrchestratorTest#aFailedPayinQueuesItsOwnOutcome` |
| TC-ADD-026 | A payment that failed for any reason | Read today's route usage | No headroom was consumed by a failure | REQ-701 | `PayinOrchestratorTest#failuresConsumeNoHeadroom` |
| TC-ADD-027 | The gateway call times out after the row was written | Look the attempt up | The attempt is still addressable by its reference | A9b, F-35 | `PayinOrchestratorTest#aGatewayTimeoutStillLeavesTheAttemptAddressable` |
| TC-ADD-028 | No route has headroom for the amount | Quote the payment | Refused before payment is attempted, with the figures stated, consuming no attempt | REQ-202, A3 | `PayinOrchestratorTest#noHeadroomRefusesWithFigures` |
| TC-ADD-029 | An attempt in each non-terminal state | Ask whether it is reversible | Only a confirmed attempt is | A10 | `PayinStateTest#onlyAConfirmedPayinIsReversible` |
| TC-ADD-030 | Every state in the payin machine | Walk every transition | Only the legal ones are permitted; an illegal one is refused by name | A9 | `PayinStateTest#allowedNextAgreesWithCanTransitionTo`, `PayinAttemptTest#anIllegalTransitionIsRefused` |
| TC-ADD-031 | Any payin state | Ask whether it affects a balance | Only a confirmed payin does | A5 | `PayinStateTest#onlyConfirmedAffectsBalance` |
| TC-ADD-032 | A terminal payin state | Attempt any onward transition | Refused | A9 | `PayinStateTest#aTerminalStateGoesNowhere` |
| TC-ADD-033 | An attempt awaiting the bank | Attempt to cancel it | Refused — it can resolve but not be cancelled | A9b | `PayinStateTest#awaitingBankResolvesButCannotBeCancelled` |
| TC-ADD-034 | An attempt for a non-positive amount | Create it | Refused | REQ-201 | `PayinAttemptTest#anAttemptIsForAPositiveAmount` |
| TC-ADD-035 | An attempt | Record its funding source | Recorded masked, never as a full account number | C15, PR-31 | `PayinAttemptTest#theFundingSourceIsRecordedMasked` |
| TC-ADD-036 | An attempt whose reference was not minted with it | Assign one afterwards | Refused — the reference exists from the moment the attempt does | C18 | `PayinAttemptTest#theReferenceCannotBeAssignedLate` |
| TC-ADD-037 | An attempt undergoing a state change | Read its version | The version advances, so a stale write cannot overwrite it | F-38 | `PayinAttemptTest#everyStateChangeIncrementsTheVersion` |
| TC-ADD-038 | A confirmed, a failed and a reversed attempt | Build the movements list | Each appears exactly once and no confirmed deposit is double-counted | F-31, L1 | `PayinLedgerNoDoubleCountTest#aConfirmedPayinAppearsExactlyOnce`, `#manyAttemptsEachAppearExactlyOnce` |
| TC-ADD-039 | A reversed deposit | Read the movements list | It reads as reversed, not as money still held | REQ-206, L2 | `PayinLedgerNoDoubleCountTest#aReversedPayinShowsAsOneReversedMovement` |
| TC-ADD-040 | A failed deposit | Read the movements list | It is still there, with its reason | L8 | `PayinLedgerNoDoubleCountTest#aFailedPayinStaysVisible` |
| TC-ADD-041 | A gateway timeout inside a transaction | Read the database afterwards | The attempt row is committed and addressable, not rolled back | F-35 | `PayinDurabilityTest#aGatewayTimeoutLeavesACommittedAddressableRow` |
| TC-ADD-042 | A confirmation arriving after a timeout | Process it | It still lands against the committed attempt | A7, F-35 | `PayinDurabilityTest#theConfirmationThatFollowsATimeoutStillLands` |
| TC-ADD-043 | A second attempt reusing one gateway reference | Persist it | The database refuses it — Rule A6 is an index, not a check | A6 | `JdbcPayinAttemptRepositoryTest#ruleA6IsEnforcedThroughTheRepository` |
| TC-ADD-044 | Two accounts with attempts | Read one account's attempts | Only that account's are returned | LLD §4.3 | `JdbcPayinAttemptRepositoryTest#findForIsScopedByAccount` |
| TC-ADD-045 | A deposit exactly equal to an outstanding debt below the minimum | Check the applicable minimum | Permitted — the minimum applies to funding, not to settling a debt | REQ-502, REQ-703, H3 | `MinimumAddPolicyTest#theExactDebtIsPermitted`, `#theWaiverIsExact` |
| TC-ADD-046 | A route with no configured cap | Read its headroom | Empty, meaning unbounded — never rendered as zero | REQ-701 | `qa.MovementContractTest#anAbsentCapMeansUnboundedRatherThanZero` |
| TC-ADD-047 | A route configured with a cap of zero | Load the configuration | Refused — a zero cap disables the route silently | G1, G3 | `qa.MovementContractTest#aZeroCapIsRefused` |
| TC-ADD-048 | A route configured with a negative cap | Load the configuration | Refused | G1 | `qa.MovementContractTest#aNegativeCapIsRefused` |
| TC-ADD-049 | A route configured with a negative fee | Load the configuration | Refused | REQ-202 | `qa.MovementContractTest#aNegativeRouteFeeIsRefused` |
| TC-ADD-050 | Part of a cap already used today | Read the remaining headroom | Cap less today's usage | REQ-701 | `qa.MovementContractTest#headroomIsTheCapLessTodaysUsage` |
| TC-ADD-051 | More used today than the cap | Read the remaining headroom | Zero, not a negative figure shown to a trader | REQ-701 | `qa.MovementContractTest#headroomFloorsAtZero` |
| TC-ADD-052 | A cap lowered below today's usage | Ask whether the route is over cap | Yes — reported separately from a merely exhausted cap | G3 | `qa.MovementContractTest#beingOverCapIsReportedSeparately` |
| TC-ADD-053 | A cap used to the last paise | Read headroom and the over-cap flag | Zero headroom, and not over cap | REQ-701 | `qa.MovementContractTest#exhaustingACapExactlyIsNotOverCap` |
| TC-ADD-054 | A route chosen without a switch | Ask what to disclose | Nothing — there was no re-route | A12 | `qa.MovementContractTest#aRouteThatWasNotSwitchedReportsNoSwitch` |
| TC-ADD-055 | An automatic re-route | Ask what to disclose | The route it moved away from is named | A12, REQ-702 | `qa.MovementContractTest#anAutomaticRerouteNamesWhatItMovedFrom` |
| TC-ADD-056 | A selection recording a switch from itself | Construct it | Refused | A12 | `qa.MovementContractTest#aRouteCannotSwitchFromItself` |
| TC-ADD-057 | An uncapped route selected | Read its headroom | Empty rather than zero | REQ-701 | `qa.MovementContractTest#anUncappedSelectedRouteCarriesEmptyHeadroom` |
| TC-ADD-058 | The route vocabulary | Compare against Rule A9d | Only rails this system can execute exist; no self-service rail | A9d | `qa.MovementContractTest#onlyExecutableRoutesExist` |
| TC-ADD-059 | An amount entry field | Paste a value containing a leading minus or letters | Refused at the keystroke, not corrected afterwards | A13 | BLOCKED — no client |
| TC-ADD-060 | Suggested amount pills offered | Select one | The pill states whether it sets or adds, and behaves as stated | A2 | BLOCKED — no client |
| TC-ADD-061 | A trader who has entered nothing | Open the amount field | The minimum is stated before anything is typed | REQ-201 | BLOCKED — no client |
| TC-ADD-062 | Three attempts failing in succession | Read the failure screen | A support route is offered alongside the retry | REQ-205 | BLOCKED — no client |
| TC-ADD-063 | A trader entering an amount | Request a quote over HTTP | The route, arrival date, cost and applicable minimum are returned | REQ-202, LLD §4.1 | BLOCKED — endpoint absent |
| TC-ADD-064 | A trader authorising a payment | Start an attempt over HTTP with an idempotency key | The attempt is created; a missing key is refused with `missing_idempotency_key` | LLD §4.3 | BLOCKED — endpoint absent |
| TC-ADD-065 | A gateway confirmation posted to the callback | Verify the signature before the body is parsed | An unsigned or wrongly signed body is refused with `bad_signature` | LLD §4.3 | BLOCKED — endpoint absent |
| TC-ADD-066 | A payin confirmed | Read the funding confirmation screen | An action leading to the configured post-funding destination is offered | REQ-709 | BLOCKED — no client |
| TC-ADD-067 | No post-funding destination configured | Read the funding confirmation screen | A plain dismissal, never a control that leads nowhere | REQ-710, H6 | BLOCKED — no client |
| TC-ADD-068 | A shortfall outstanding | Open the funding path | The shortfall amount leads as the suggestion, with the fastest route | REQ-207, A11 | BLOCKED — no client |

---

## TC-WDR — Withdrawing funds

The only path where being wrong moves money irreversibly. Requesting, refusing and cancelling are
built and tested down to the database constraint that carries Rule W4. What decides the money —
the end-of-day run — is not built, so every case describing what happens hours after the trader
left is blocked. That gap is the single largest in this catalogue and it sits on the most
consequential path.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-WDR-001 | A withdrawable figure of ₹10,000, no open request | Request ₹5,000 | Accepted, with the figure at request recorded and the arrival date quoted | REQ-302, W11 | `PayoutOrchestratorTest#requestWithinWithdrawableIsAccepted` |
| TC-WDR-002 | A withdrawable figure of ₹5,000 | Request ₹5,001 | Refused, carrying the figure so the refusal explains itself | REQ-302, REQ-102 | `PayoutOrchestratorTest#amountAboveWithdrawableIsRefusedWithTheFigure` |
| TC-WDR-003 | An open request already exists | Submit a second | Refused as already open — enforced by the index, not by a prior read | W4 | `PayoutOrchestratorTest#ruleW4IsTheIndexsToEnforce`, `#constraintViolationSurfacesAsAlreadyOpen` |
| TC-WDR-004 | Two requests arriving in the same instant | Both attempt to insert | Exactly one succeeds; the other is refused with no window between check and write | W4, LLD §7.1 | `SchemaConstraintTest#ruleW4RefusesASecondOpenRequest` |
| TC-WDR-005 | A destination that Profile no longer holds as verified | Request a withdrawal | Refused — the list is read live, never from a cache | REQ-301, PR-28 | `PayoutOrchestratorTest#unverifiedDestinationIsRefused`, `#destinationIsReadAtTheMomentOfRequest` |
| TC-WDR-006 | Nothing withdrawable *and* an unverified destination | Request a withdrawal | The more fundamental refusal wins: the trader is told about the figure, not the bank account | REQ-301 | `PayoutOrchestratorTest#theMoreFundamentalRefusalWins` |
| TC-WDR-007 | The derivation and RMS disagree | Request a withdrawal | Refused — no money leaves against a figure the system cannot stand behind | OA-1 | `PayoutOrchestratorTest#divergentVerdictBlocksTheRequest` |
| TC-WDR-008 | Another trader's request id | Attempt to cancel it | Not found, rather than forbidden — confirming existence would leak | LLD §4.3 | `PayoutOrchestratorTest#anotherTradersRequestIsNotFound` |
| TC-WDR-009 | An accepted request | Cancel it | Closed, and no figure moves because none was ever held | REQ-305, W3 | `PayoutOrchestratorTest#cancellationFollowsTheStateMachine` |
| TC-WDR-010 | A cancelled request | Inspect the outbox | An email confirmation is queued, in the same unit of work | REQ-616, REQ-622 | `PayoutOrchestratorTest#cancellingQueuesTheEmailConfirmation` |
| TC-WDR-011 | A request in a state the machine forbids leaving | Force the transition | An invariant failure that pages someone, not a message rendered to a trader | LLD §7.5 | `PayoutOrchestratorTest#illegalTransitionIsAnInvariantFailure` |
| TC-WDR-012 | A request that has been instructed | Attempt to cancel it | Refused, stating that the money is already on its way | REQ-305 | `PayoutStateTest#cancellationIsPermittedOnlyWhileOpen` |
| TC-WDR-013 | A request deferred by a rail outage | Attempt to cancel it | Permitted — a trader whose payout was deferred has more reason to stop it | REQ-619 | `PayoutStateTest#cancellationIsPermittedOnlyWhileOpen` |
| TC-WDR-014 | A paid request | Attempt to requeue it | Refused | LLD §7.5 | `PayoutStateTest#aPaidRequestIsNeverRequeued` |
| TC-WDR-015 | A paid request refused later by the bank | Transition to returned | Permitted — the one post-terminal transition, and it adds an entry rather than deleting one | W7, L2 | `PayoutStateTest#returnedIsTheOnlyPostTerminalTransition` |
| TC-WDR-016 | Every payout state | Classify each as open or terminal | Each is exactly one, with no state in both or neither | LLD §7.5 | `PayoutStateTest#everyStateIsExactlyOneOfTerminalOrOpen` |
| TC-WDR-017 | A new request | Check its initial state | Only ACCEPTED is a legal starting state | LLD §7.5 | `PayoutStateTest#onlyAcceptedIsALegalInitialState` |
| TC-WDR-018 | The V21 migration's state vocabulary | Compare against the enum | The two agree exactly | LLD §7.5 | `PayoutStateTest#migrationVocabularyMatchesTheEnum` |
| TC-WDR-019 | The V21 index's open-state predicate | Compare against the enum's own | The two agree, so the index covers every open state | W4 | `PayoutStateTest#migrationOpenStatePredicateMatchesIsOpen`, `SchemaConstraintTest#ruleW4CoversEveryOpenState` |
| TC-WDR-020 | A closed request | Submit a new one | Permitted — Rule W4 bounds concurrency, not lifetime volume | W4a | `SchemaConstraintTest#ruleW4LetsAClosedRequestBeFollowedByANewOne` |
| TC-WDR-021 | A cancelled request | Read the account's open request | None — cancellation frees the account immediately | W4a | `JdbcPayoutRequestRepositoryTest#aCancelledRequestFreesTheAccount` |
| TC-WDR-022 | Requests written by two different accounts | Read one account's open request | Only that account's | LLD §4.3 | `JdbcPayoutRequestRepositoryTest#openForReturnsNothingOnceClosed` |
| TC-WDR-023 | A request settled with every settlement field populated | Persist and reload it | Every field round-trips, including both withdrawable figures | W11 | `JdbcPayoutRequestRepositoryTest#roundTripsThroughEverySettlementField` |
| TC-WDR-024 | A request whose bank reference equals the FMS reference | Persist it | The database refuses it | C8 | `JdbcPayoutRequestRepositoryTest#ruleC8IsEnforcedOnWrite`, `SchemaConstraintTest#ruleC8RefusesABankReferenceEqualToOurs` |
| TC-WDR-025 | A duplicate that is not a second open request | Persist it | Reported as its own violation, not misattributed to Rule W4 | W4 | `JdbcPayoutRequestRepositoryTest#anUnrelatedDuplicateIsNotReportedAsRuleW4` |
| TC-WDR-026 | Several open requests across accounts | Scan for the run | Every open state is returned, oldest first | LLD §6.3 | `JdbcPayoutRequestRepositoryTest#theRunSeesEveryOpenStateOldestFirst` |
| TC-WDR-027 | A request loaded and modified concurrently | Write the stale copy | Refused by the version check | F-38 | `JdbcPayoutRequestRepositoryTest#aStaleWriteIsRefused` |
| TC-WDR-028 | A request placed before the day's cut-off | Quote the arrival date | The next working day, with the boundary named | REQ-303, REQ-707, W5 | `ArrivalDateCalculatorTest#beforeTheCutoffArrivesSameDay` |
| TC-WDR-029 | A request placed exactly at the cut-off | Quote the arrival date | Today's run has been missed; the date moves out | REQ-707, G5 | `ArrivalDateCalculatorTest#atTheCutoffTheRequestHasMissedTodaysRun` |
| TC-WDR-030 | A request placed on a Friday evening | Quote the arrival date | The weekend is skipped and named as the cause | W5 | `ArrivalDateCalculatorTest#aWeekendIsSkippedAndNamed` |
| TC-WDR-031 | The trader traded today, or has an order outstanding | Quote the arrival date | Each factor defers arrival and each is named | REQ-303, W5 | `ArrivalDateCalculatorTest#tradingAndOutstandingOrdersEachDefer` |
| TC-WDR-032 | No trading calendar nominated | Quote the arrival date | No date is produced; the quote fails rather than defaulting | REQ-303, OA-5 | `ArrivalDateCalculatorTest#anUnavailableCalendarReportsNoDate`, `ConfiguredTradingCalendarTest#anUnconfiguredCalendarMakesTheDateUncomputable` |
| TC-WDR-033 | A calendar with no working day ahead | Quote the arrival date | The calculator gives up rather than looping or guessing | OA-5 | `ArrivalDateCalculatorTest#aCalendarWithNoWorkingDayGivesUp` |
| TC-WDR-034 | A trader in a different zone from the server | Evaluate the cut-off | The cut-off is read in the account's zone | G5 | `ArrivalDateCalculatorTest#theCutoffIsReadInTheAccountsZone` |
| TC-WDR-035 | A configured settlement holiday | Ask whether it is a working day | No, and the arrival date skips it | B6, W5 | `ConfiguredTradingCalendarTest#aConfiguredHolidayIsNotAWorkingDay`, `#withHolidaysConfiguredTheCalculatorSkipsThem` |
| TC-WDR-036 | Any weekend date | Ask whether it is a working day | Never | W5 | `ConfiguredTradingCalendarTest#weekendsAreNeverWorkingDays` |
| TC-WDR-037 | The back office authorises the full amount | Map the settlement outcome | PAID | REQ-308 | `SettlementMappingTest#fullAuthorisationIsPaid` |
| TC-WDR-038 | The back office authorises less, with margin blocked | Map the settlement outcome | PARTLY_PAID, reason MARGIN_BLOCKED, quantified | REQ-308, W10 | `SettlementMappingTest#partialWithRmsDataIsMarginBlocked` |
| TC-WDR-039 | The back office authorises less, with no margin figure | Map the settlement outcome | PARTLY_PAID with the reason taken from the rejection phrase | OA-4 | `SettlementMappingTest#partialWithoutRmsDataUsesTheReasonPhrase` |
| TC-WDR-040 | The back office rejects outright | Map the settlement outcome | NOTHING_SENT, and rejection beats any margin figure present | REQ-308 | `SettlementMappingTest#rejectMeansNothingSent`, `#rejectBeatsRmsData` |
| TC-WDR-041 | A rejection phrase the mapping table does not know | Map the settlement outcome | UNSPECIFIED, with the phrase retained for an alert and never shown to the trader | OA-4 | `SettlementMappingTest#unmappedPhraseIsUnspecifiedAndRetained` |
| TC-WDR-042 | A vendor reporting more sent than requested | Map the settlement outcome | Refused — a settlement may send less but never more | W10 | `SettlementMappingTest#cannotSendMoreThanRequested` |
| TC-WDR-043 | A vendor rupee decimal amount | Convert it | Exact paise, with no rounding | R5 | `SettlementMappingTest#decimalRupeesBecomeExactPaise` |
| TC-WDR-044 | An instruction key for a request and run date | Build it | The same inputs always produce the same key, and different run dates never collide | LLD §6.3a | `InstructionKeyTest#sameInputsProduceSameKey`, `#differentRunDateProducesDifferentKey`, `#distinctPairsNeverCollide` |
| TC-WDR-045 | A sequence at or beyond the encodable maximum | Build the key | Refused rather than truncated — a truncated key is a valid key for another instruction | LLD §6.3a | `InstructionKeyTest#oneAboveMaximumIsRefusedEverywhere`, `#overflowThrowsRatherThanTruncating` |
| TC-WDR-046 | A settlement reporting more sent than requested | Construct the outcome | Refused | W10 | `qa.MovementContractTest#aSettlementNeverSendsMoreThanRequested` |
| TC-WDR-047 | A settlement reporting a negative amount sent | Construct the outcome | Refused | W10 | `qa.MovementContractTest#aNegativeAmountSentIsRefused` |
| TC-WDR-048 | A settlement reporting a non-terminal state | Construct the outcome | Refused — an outcome is terminal by definition | LLD §7.5 | `qa.MovementContractTest#anOutcomeMustReportATerminalState` |
| TC-WDR-049 | ₹100 requested and ₹65 sent | Ask what accounts for the gap | ₹35, stated rather than left to be worked out | REQ-308, REQ-617 | `qa.MovementContractTest#theGapBetweenRequestedAndSentIsStated` |
| TC-WDR-050 | The full amount sent | Ask what accounts for the gap | Nothing | REQ-308 | `qa.MovementContractTest#aFullSettlementLeavesNoGap` |
| TC-WDR-051 | Nothing was sent | Read the bank reference | Absent, never substituted with the FMS reference | C8, REQ-620 | `qa.MovementContractTest#anAbsentBankReferenceReadsAsAbsent` |
| TC-WDR-052 | Money reached the bank | Read the outcome | The bank's own reference and the credited date are both carried | REQ-620, REQ-303 | `qa.MovementContractTest#aCreditedDateIsCarried` |
| TC-WDR-053 | An unmapped vendor phrase | Read the outcome | Code UNSPECIFIED with the phrase retained beside it | OA-4 | `qa.MovementContractTest#anUnmappedVendorPhraseIsRetained` |
| TC-WDR-054 | Each terminal state in turn | Express it as a settlement outcome | All five are expressible | REQ-619 | `qa.MovementContractTest#everyTerminalStateIsExpressible` |
| TC-WDR-055 | Nothing available at the run | Build an instruction for zero | Refused — the run declines to instruct rather than sending a zero the rail will reference | W4a | `qa.MovementContractTest#aZeroAmountInstructionIsRefused` |
| TC-WDR-056 | An instruction without a pinned destination | Build it | Refused | W12 | `qa.MovementContractTest#anInstructionWithoutADestinationIsRefused` |
| TC-WDR-057 | An instruction missing its key or run date | Build it | Refused | LLD §6.3a | `qa.MovementContractTest#anInstructionWithoutItsRunDateOrKeyIsRefused` |
| TC-WDR-058 | A monthly return frequency, mid-month | Ask for the next return date | The last day of the month, leap years included | REQ-307, W8 | `qa.MovementContractTest#theMonthlyReturnFallsOnTheLastDayOfTheMonth` |
| TC-WDR-059 | Today is the return date | Ask for the next return date | Today — not rolled forward while the money is still leaving | REQ-307 | `qa.MovementContractTest#askingOnTheDateItselfReturnsThatDate` |
| TC-WDR-060 | A mandated return falling on the run date of an open request | Check for a collision | Detected, so both settle from one balance | W9 | `qa.MovementContractTest#aMandatedReturnOnTheRunDateIsACollision` |
| TC-WDR-061 | A quarterly return frequency | Ask for the next return date | The Indian financial quarter ends: March, June, September, December | REQ-307 | `qa.MovementContractTest#theQuarterlyCycleUsesIndianFinancialQuarterEnds` |
| TC-WDR-062 | Two payout rails configured | Start the service | It refuses to start — two rails would instruct independently and void Rule W9 | OA-3, W9 | `qa.MovementContractTest#aSecondPayoutRailStopsTheServiceStarting` |
| TC-WDR-063 | No payout rail configured | Start the service | It refuses to start rather than accepting requests it can never settle | OA-3 | `qa.MovementContractTest#noPayoutRailAlsoStopsTheServiceStarting` |
| TC-WDR-064 | Exactly one rail configured | Start the service | It starts, and that rail is the one the run would use | OA-3 | `qa.MovementContractTest#exactlyOneRailStarts` |
| TC-WDR-065 | A failed payout | Attempt an automatic resend | Never resent automatically to the same destination | W7 | `PayoutReturnTest#aFailedPayoutIsNeverResentAutomatically` |
| TC-WDR-066 | A payout refused by the destination bank | Read the return | The destination is flagged as needing attention before another request | REQ-306 | `PayoutReturnTest#aRejectedDestinationNeedsAttention` |
| TC-WDR-067 | A returned payout | Read the entries | The return compensates a specific payout; the original stands | W7, L2 | `PayoutReturnTest#aReturnCompensatesASpecificPayout` |
| TC-WDR-068 | Any date and frequency | Ask for the next mandated return | Never a date in the past | REQ-307 | `PayoutReturnTest#theNextReturnIsNeverInThePast` |
| TC-WDR-069 | Open requests at the end of the day | Execute the payout run | Each is instructed once, with eligibility re-evaluated immediately beforehand | REQ-308, LLD §6.3 | BLOCKED — module absent |
| TC-WDR-070 | A mandated return and an open request on one date | Execute the payout run | One instruction, one payout, the same money never sent twice | W9, REQ-307 | BLOCKED — module absent |
| TC-WDR-071 | The withdrawable figure fell between request and run | Execute the payout run | What is available is sent, and the gap is named on the transaction itself | W10, W4c | BLOCKED — module absent |
| TC-WDR-072 | Nothing available at the run | Execute the payout run | Nothing is sent, the request closes, and the trader is told | W4a | BLOCKED — module absent |
| TC-WDR-073 | The banking rail is unavailable at the run | Execute the payout run | The request stays open and cancellable rather than closing | REQ-619 | BLOCKED — module absent |
| TC-WDR-074 | A run re-executed after a crash | Re-instruct | The prior instruction's status is read before reissuing; no second payment | OA-7, LLD §6.3 | BLOCKED — module absent |
| TC-WDR-075 | The trader cancels while the run is instructing | Both proceed | Whichever acquires the row first wins; the loser is refused with its reason | LLD §7.4 | BLOCKED — module absent |
| TC-WDR-076 | A mandated return date arriving | Execute the sweep | Unused funds are returned, announced before and notified after | REQ-307, W8 | BLOCKED — module absent |
| TC-WDR-077 | Funds retained against an outstanding order | Execute the sweep | Only the unused portion is returned, and the retention is stated | REQ-307 | BLOCKED — module absent |
| TC-WDR-078 | A trader entering an amount | Request a payout quote over HTTP | The arrival date and the Rule W3a shrink warning are returned before commitment | REQ-303, W3a | BLOCKED — endpoint absent |
| TC-WDR-079 | Any account, nothing withdrawable | Render the funds view | The withdraw entry point is present and disabled, naming the responsible deduction | REQ-301, W1, W2 | BLOCKED — no client |
| TC-WDR-080 | A disabled withdraw control | Interact with it | The interaction is not silently absorbed; the reason is presented | W2 | BLOCKED — no client |
| TC-WDR-081 | A trader about to submit | Read the confirmation | It states before commitment that the amount sent is whatever is available at end of day | W3a, C7 | BLOCKED — no client |
| TC-WDR-082 | Margin figures are stale | Request a withdrawal | Refused with staleness stated as the reason | REQ-107 | BLOCKED — module absent |
| TC-WDR-083 | A withdrawal of the exact withdrawable figure to the last paise | Submit it | Accepted — a rounding difference that refuses an exact maximum is a defect | Edge cases | BLOCKED — module absent |
| TC-WDR-084 | An account blocked from moving money after a request was accepted | Reach the run | Nothing is sent and the request closes with the blocker named | REQ-505 | BLOCKED — module absent |

---

## TC-TXN — Transactions & statements

The best-covered area in the system, and the only one with a complete path from vendor row to
downloadable file. Rule L8a — an export returns precisely what is on screen — is executed rather
than asserted, by rendering both from the same rows.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-TXN-001 | No period chosen | Open the transaction list | The last 30 days, with the period echoed back | REQ-403, L6 | `TransactionQueryServiceTest#defaultPeriodIsThirtyDays`, `TransactionsApiTest#periodIsEchoed` |
| TC-TXN-002 | A period containing no entries | Open the transaction list | It states the period is empty and offers a wider one, never blank space | L7 | `TransactionQueryServiceTest#emptyPeriodOffersAWiderOne`, `TransactionsApiTest#emptyPeriodOffersAWiderOne` |
| TC-TXN-003 | A period containing entries | Open the transaction list | No wider period is offered, so results never read as incomplete | L7 | `TransactionQueryServiceTest#nonEmptyPeriodOffersNothing` |
| TC-TXN-004 | A period wider than the back office can answer | Request it | Refused as a client error rather than truncated silently | OA-6 | `TransactionQueryServiceTest#tooWideAPeriodIsRefused`, `TransactionsApiTest#badPeriodIsAClientError` |
| TC-TXN-005 | Entries across several days | Open the list | Newest first, with a stable order | REQ-404 | `TransactionQueryServiceTest#newestFirst` |
| TC-TXN-006 | The movements view | Open it | Only money the trader moved in or out; trading outcomes are excluded | REQ-402, L5 | `TransactionQueryServiceTest#movementsViewExcludesTradingOutcomes`, `TransactionsApiTest#movementsViewIsMoneyInAndOut` |
| TC-TXN-007 | The all-entries view | Open it | Every entry, including sale proceeds, mark-to-market and charges | L5a | `TransactionQueryServiceTest#bothViewsAgreeOnTheRunningBalance`, `TransactionsApiTest#allEntriesViewCarriesEverything` |
| TC-TXN-008 | Both views over one period | Compare the running balance | They agree — one running balance, shared | L5a, B12 | `TransactionQueryServiceTest#bothViewsAgreeOnTheRunningBalance` |
| TC-TXN-009 | Any entry | Read the running balance | It comes from the back office, never accumulated locally | REQ-404, HLD §9.1b | `TransactionQueryServiceTest#runningBalanceComesFromTheBackOffice` |
| TC-TXN-010 | A movement not yet posted | Read the list | It appears, carrying no running balance | A5, REQ-405 | `TransactionQueryServiceTest#unpostedMovementsCarryNoBalance` |
| TC-TXN-011 | In-flight and failed movements | Read the list | Both appear rather than being hidden until they succeed | L8, REQ-405 | `TransactionQueryServiceTest#inFlightAndFailedMovementsAppear` |
| TC-TXN-012 | A reversal and its original | Read the list | They are paired, and the original is flagged as reversed | REQ-404, L2 | `TransactionQueryServiceTest#reversalIsPairedAndOriginalFlagged` |
| TC-TXN-013 | A reversal whose original is outside the period | Read the list | The reversal is still returned rather than dropped | L2 | `TransactionQueryServiceTest#unmatchedReversalIsStillReturned` |
| TC-TXN-014 | An entry whose narration contains an ordinary word | Attempt to pair it | Ordinary words are not treated as references, so no false pairing occurs | L2 | `TransactionQueryServiceTest#ordinaryWordsAreNotTreatedAsReferences`, `#lowercaseTokensWithDigitsAreNotReferences` |
| TC-TXN-015 | An entry that appears to reference itself | Attempt to pair it | Not paired to itself | L2 | `TransactionQueryServiceTest#selfReferenceIsNotPaired` |
| TC-TXN-016 | A near-miss reference | Attempt to pair it | A false pair cannot flag an unrelated entry as reversed | L2 | `TransactionQueryServiceTest#aFalsePairCannotFlagAnUnrelatedEntry` |
| TC-TXN-017 | A genuine reference pair | Read the list | They still pair — the guard against false pairs did not break real ones | L2 | `TransactionQueryServiceTest#realReferencesStillPair` |
| TC-TXN-018 | An entry the movements view filters out | Fetch it by reference | Still reachable — detail is looked up across all entries | REQ-405 | `TransactionQueryServiceTest#detailReachesAnEntryOutsideTheMovementsView`, `TransactionsApiTest#detailReachesFilteredEntries` |
| TC-TXN-019 | A period at the gateway's window bound | Query it | The window the service uses matches the gateway's own bound | OA-6 | `TransactionQueryServiceTest#windowBoundMatchesTheGateway` |
| TC-TXN-020 | An exported statement and the list on screen | Compare them | Identical rows, same period, same running balance | L8a | `TransactionQueryServiceTest#statementRowsMatchTheList`, `TransactionsApiTest#exportMatchesTheList` |
| TC-TXN-021 | A payin entry from the back office | Map its description | Plain language, with the vendor reference as secondary detail only | REQ-401, L3 | `EntryDescriptionMapperTest#referenceIsSecondaryDetailAndNeverTheDescription` |
| TC-TXN-022 | Sale proceeds | Map the entry | Recognised as not a payin, so the movements view does not claim it | L5a | `EntryDescriptionMapperTest#saleProceedsAreNotAPayin` |
| TC-TXN-023 | A payout we did not originate | Map the entry | Distinguished from a trader-requested withdrawal | L4, W8 | `EntryDescriptionMapperTest#ruleL4SeparatesRequestedPayoutsFromMandatedReturns`, `#payoutWithoutOurReferenceIsNotUserCaused` |
| TC-TXN-024 | A reversal entry | Map it | Its own kind, not folded into the entry it reverses | L2 | `EntryDescriptionMapperTest#reversalIsItsOwnKind` |
| TC-TXN-025 | An opening-balance entry | Map it | The opening-balance classification wins over any other reading | REQ-406 | `EntryDescriptionMapperTest#openingBalanceWins` |
| TC-TXN-026 | An entry type the table does not know | Map it | Explicitly marked as having no plain description, and counted for an alert | L3 | `EntryDescriptionMapperTest#unmappedEntryIsExplicitRatherThanIllegible`, `#unmappedCombinationsAreCounted` |
| TC-TXN-027 | Every entry kind | Ask for its wording | Each has copy; none falls through to a raw key | REQ-401, L3 | `StatementCopyTest#everyKindHasWording`, `#wordingIsNeverACopyKey` |
| TC-TXN-028 | An unmapped entry in a statement | Read its description | It says a description is unavailable rather than printing the reference | L3 | `StatementCopyTest#unavailableSaysSoRatherThanSubstitutingTheReference` |
| TC-TXN-029 | An exported statement | Read the amounts | Plain unformatted numbers, summable without cleaning | REQ-407 | `StatementCsvWriterTest#amountsArePlainAndSummable`, `#paiseConvertExactly` |
| TC-TXN-030 | An exported statement | Read the header | It names every required column | REQ-407 | `StatementCsvWriterTest#headerNamesTheRequiredColumns` |
| TC-TXN-031 | An exported statement | Read the type column | Debit or Credit, the words a bank statement uses | L8a | `StatementCsvWriterTest#typeColumnUsesBankStatementWords` |
| TC-TXN-032 | An empty period | Export it | A file with a header and no rows, not an error | L7, REQ-407 | `StatementCsvWriterTest#emptyPeriodStillHasAHeader` |
| TC-TXN-033 | A description containing a separator or quote | Export it | Escaped correctly, so the file still parses | REQ-407 | `StatementCsvWriterTest#separatorsAreEscaped` |
| TC-TXN-034 | A description beginning with a spreadsheet formula character | Export it | Neutralised, so opening the file does not execute it | Security | `StatementCsvWriterTest#formulaInjectionIsNeutralised` |
| TC-TXN-035 | A very large balance | Export it | Exported exactly, with no scientific notation or truncation | R5 | `StatementCsvWriterTest#largeBalanceExportsCleanly` |
| TC-TXN-036 | A ledger row carrying both a debit and a credit | Read it | Refused — an entry is one or the other | L1 | `qa.LedgerViewContractTest#anEntryCannotBeBothADebitAndACredit` |
| TC-TXN-037 | A ledger row with a negative debit or credit | Read it | Refused — a reversal is its own entry, not a sign flip | L2 | `qa.LedgerViewContractTest#aNegativeDebitOrCreditIsRefused` |
| TC-TXN-038 | A ledger row with neither a debit nor a credit | Read it | Accepted as a zero movement | L1 | `qa.LedgerViewContractTest#aZeroEntryIsAccepted` |
| TC-TXN-039 | A debit row and a credit row | Read the signed effect | Credit adds, debit subtracts | L1 | `qa.LedgerViewContractTest#theSignedEffectIsCreditLessDebit` |
| TC-TXN-040 | A row carrying a settlement pay-in date | Classify it | A trade contract note, which is what makes the settlement calendar relevant | B4, B6 | `qa.LedgerViewContractTest#aSettlementPayinDateMarksATransactionBill` |
| TC-TXN-041 | A ledger row missing its identifier or date | Read it | Refused | L1 | `qa.LedgerViewContractTest#anEntryWithoutItsRequiredFieldsIsRefused` |
| TC-TXN-042 | A statement row built with an internal kind in the type column | Construct it | Refused — Rule L8a names two words and this is the defect it was written against | L8a | `qa.LedgerViewContractTest#aStatementRowIsDebitOrCredit` |
| TC-TXN-043 | A statement row typed in lower case, or with no type | Construct it | Refused | L8a | `qa.LedgerViewContractTest#theColumnIsCaseSensitive` |
| TC-TXN-044 | A credit ledger row | Convert it to a statement row | Credit, carrying the credit amount and the plain description | L8a | `qa.LedgerViewContractTest#aCreditEntryBecomesACreditRow` |
| TC-TXN-045 | A debit ledger row | Convert it to a statement row | Debit, carrying the debit amount | L8a | `qa.LedgerViewContractTest#aDebitEntryBecomesADebitRow` |
| TC-TXN-046 | Rows with, without and with a blank settlement number | Read the reference column | The settlement number where there is one, the voucher number otherwise | L3 | `qa.LedgerViewContractTest#theSettlementNumberIsPreferredAsTheReference` |
| TC-TXN-047 | Any statement row | Read the resulting balance | The back office's closing amount, carried through unchanged | REQ-404, B12 | `qa.LedgerViewContractTest#theRunningBalanceIsCarriedThrough` |
| TC-TXN-048 | A page with entries and a wider period attached | Construct it | Refused — offering one alongside results implies the results are incomplete | L7 | `qa.LedgerViewContractTest#aWiderPeriodIsOfferedOnlyWhenEmpty` |
| TC-TXN-049 | An empty page | Read it | It reports empty and carries the wider period to offer | L7 | `qa.LedgerViewContractTest#anEmptyPageOffersTheWiderPeriod` |
| TC-TXN-050 | A populated page | Read it | No wider period | L7 | `qa.LedgerViewContractTest#aPopulatedPageOffersNoWiderPeriod` |
| TC-TXN-051 | A page built from a caller's list | Mutate the list afterwards | The page is unchanged | L1 | `qa.LedgerViewContractTest#aPagesEntriesCannotBeMutated` |
| TC-TXN-052 | A page missing its view or period | Construct it | Refused — the period is echoed, so it cannot be absent | REQ-403 | `qa.LedgerViewContractTest#aPageWithoutItsViewOrPeriodIsRefused` |
| TC-TXN-053 | The default period | Measure it | Thirty days, inclusive of today | L6 | `qa.LedgerViewContractTest#theDefaultPeriodIsThirtyInclusiveDays` |
| TC-TXN-054 | A period that ends before it starts | Construct it | Refused | REQ-403 | `qa.LedgerViewContractTest#anInvertedPeriodIsRefused` |
| TC-TXN-055 | A single-day period | Measure it | One day, not zero | REQ-403 | `qa.LedgerViewContractTest#aSingleDayPeriodIsOneDay` |
| TC-TXN-056 | A period one day past the maximum window | Construct it | Refused; the maximum itself is accepted | OA-6 | `qa.LedgerViewContractTest#aTooWidePeriodIsRefused` |
| TC-TXN-057 | Only one period bound supplied | Resolve the period | The 30-day default applies rather than a half-invented window | L6 | `qa.LedgerViewContractTest#eitherBoundMissingMeansTheDefault` |
| TC-TXN-058 | Periods of several widths | Widen each | Every widened period stays inside the maximum the gateway accepts | L7, OA-6 | `qa.LedgerViewContractTest#theWidenedPeriodStaysInsideTheMaximum` |
| TC-TXN-059 | The period type's maximum | Compare against the gateway's constant | The two agree | OA-6 | `qa.LedgerViewContractTest#theMaximumWindowMatchesTheGateway` |
| TC-TXN-060 | Rule L5's two questions | Compare against the view vocabulary | Exactly two views exist | L5 | `qa.LedgerViewContractTest#bothViewsExistAndOnlyThose` |
| TC-TXN-061 | A chosen period | Ask for its opening and closing balances | Both returned, each stamped with the moment it was taken | REQ-406 retained | BLOCKED — endpoint absent |
| TC-TXN-062 | Entries not summing to the difference between the endpoints | Read the period | The discrepancy is stated rather than a total adjusted | L9 | BLOCKED — endpoint absent |
| TC-TXN-063 | A statement download | Read the file's own content | The period covered and the moment of production appear inside the file | REQ-407 | BLOCKED — no client |
| TC-TXN-064 | A statement download | Read the filename | It names the account and the period, so several are distinguishable unopened | REQ-407 | BLOCKED — no client |
| TC-TXN-065 | A movement whose status changes while displayed | Watch the list | The view updates and states that it changed | Edge cases | BLOCKED — no client |
| TC-TXN-066 | Switching between the two views | Change view | The selected period survives the switch | REQ-402 | BLOCKED — no client |
| TC-TXN-067 | A financial-year preset | Select it | The whole year is presented and exported | REQ-407 | BLOCKED — no client |
| TC-TXN-068 | An entry belonging to an earlier trading day | Filter by period | The period filter uses the day it belongs to, not the day it was recorded | Edge cases | BLOCKED — endpoint absent |

---

## TC-HLT — Account health

The states traders are actually in when they arrive: empty, blocked, in debt, or about to be forced
out of a position. The arithmetic of a debt and the rules about quoting a rate are built; the screens
that would present any of it are not, and neither is the `/funds/health` endpoint that would feed
them.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-HLT-001 | A negative ledger balance | Read the debt | An amount owed, expressed positively — never an availability figure | REQ-501, H1 | `AccountDebtTest#aDebtIsAPositiveAmountOwed` |
| TC-HLT-002 | A debt with no cause recorded | Construct it | Refused — the entry that created it must be nameable | REQ-501 | `AccountDebtTest#theCauseMustBeNamed` |
| TC-HLT-003 | A debt on the day it arose | Read the accrual | Nothing has accrued yet | REQ-501 | `AccountDebtTest#aDebtOnItsFirstDayHasAccruedNothing` |
| TC-HLT-004 | A debt several days old | Read the accrual | Computed daily at the configured rate | REQ-501, REQ-708 | `AccountDebtTest#accrualIsComputedDaily` |
| TC-HLT-005 | An accrual with a fractional paise | Read it | Rounded down, never up against the trader | REQ-501 | `AccountDebtTest#accrualRoundsDown` |
| TC-HLT-006 | A debt with an age given as negative | Construct it | Refused | REQ-501 | `AccountDebtTest#negativeDaysAreRefused` |
| TC-HLT-007 | A debt about to be cleared | Ask what to pay | The amount owed plus accrual to this moment, so nothing is left behind | REQ-502 | `AccountDebtTest#theAmountToClearIncludesAccrual` |
| TC-HLT-008 | The configured rate is a stand-in | Compute the accrual | It computes, and is marked as not quotable in any message | REQ-708, EB-8 | `AccountDebtTest#aProvisionalRateComputesButIsNotQuoted`, `DebitInterestRateTest#aProvisionalRateIsNeverQuoted` |
| TC-HLT-009 | No rate available at all | Read the debt | The debt still stands and is stated; the accrual is reported unavailable | REQ-708 | `AccountDebtTest#withNoRateTheDebtStillStands`, `DebitInterestRateTest#anUnavailableRateComputesNothing` |
| TC-HLT-010 | A configured, non-provisional rate | Ask whether it may be quoted | Yes | REQ-708 | `DebitInterestRateTest#aConfiguredRateMayBeQuoted` |
| TC-HLT-011 | A rate of zero | Load it | A rate, not an absent one — zero is a value someone chose | REQ-708 | `DebitInterestRateTest#aZeroRateIsARate` |
| TC-HLT-012 | A negative rate in configuration | Load it | Refused | REQ-708 | `DebitInterestRateTest#aNegativeRateIsRefused` |
| TC-HLT-013 | The rate shipped in configuration today | Read it | Provisional, so no production message may quote it | EB-8 | `DebitInterestRateTest#theShippedDefaultIsProvisional`, `qa.PlatformContractTest#theShippedRateMayNotBeQuoted` |
| TC-HLT-014 | A debt of ₹24.37 and a minimum of ₹100 | Pay the exact amount | Permitted — the minimum applies to funding, not to settling a debt | REQ-502, H3, REQ-703 | `MinimumAddPolicyTest#theExactDebtIsPermitted` |
| TC-HLT-015 | A debt | Ask for the suggested deposit | The exact amount owed | REQ-502 | `MinimumAddPolicyTest#theSuggestionFollowsTheDebt` |
| TC-HLT-016 | An amount that is neither at the minimum nor the exact debt | Check it | Refused, with the applicable minimum stated | REQ-703 | `MinimumAddPolicyTest#belowTheMinimumIsRefusedWithNoDebt`, `#theWaiverIsExact` |
| TC-HLT-017 | An amount of zero or less | Check it | Never permitted, debt or no debt | REQ-201 | `MinimumAddPolicyTest#aNonPositiveAmountIsNeverPermitted` |
| TC-HLT-018 | A trader with no bank account at all | Read the funding blocker | The absent bank account is named as the blocker | REQ-505, REQ-706a | `FundingSourceTest#noAccountNamesThatBlocker` |
| TC-HLT-019 | A trader whose only account is unverified | Read the funding blocker | A different blocker from having none, named as such | REQ-505 | `FundingSourceTest#unverifiedIsADifferentBlocker` |
| TC-HLT-020 | A trader with exactly one verified account | Open the funding path | It is used without presenting a choice | REQ-706a | `FundingSourceTest#oneVerifiedAccountNeedsNoChoice` |
| TC-HLT-021 | A trader with a verified primary account | Open the funding path | The primary is the default | REQ-706 | `FundingSourceTest#thePrimaryIsTheDefault` |
| TC-HLT-022 | A primary account whose verification was withdrawn | Open the funding path | It does not win the default; verification is checked first | REQ-203, REQ-706 | `FundingSourceTest#anUnverifiedPrimaryDoesNotWin` |
| TC-HLT-023 | Several verified accounts and no primary | Open the funding path | A choice is required rather than one silently picked | REQ-706 | `FundingSourceTest#severalWithNoPrimaryNeedsAChoice` |
| TC-HLT-024 | A shortfall outstanding | Read the withdrawable derivation | It is deducted as its own named term, so figure and derivation reconcile | REQ-506, B4 | `WithdrawableCalculatorTest#shortfallTermRemainsVisibleWhenItFloorsTheFigure` |
| TC-HLT-025 | A negative balance | Read the funds view | The debt is presented under a treatment distinct from a positive balance | H1 | BLOCKED — no client |
| TC-HLT-026 | An empty account | Read the funds view | One statement of the state, what funding unlocks, the smallest useful amount, one action | REQ-504, H5 | BLOCKED — no client |
| TC-HLT-027 | An empty account that has held money before | Read the funds view | Its history is reachable from the empty state | H5 | BLOCKED — no client |
| TC-HLT-028 | An account that cannot receive money | Read the funds view | The blocker replaces the funding path rather than disabling it in place | REQ-505, H6 | BLOCKED — no client |
| TC-HLT-029 | More than one blocker | Read the funds view | The one to resolve first is named, so the trader has one action | REQ-505 | BLOCKED — endpoint absent |
| TC-HLT-030 | A blocker resolved | Return to the funds view | The funding path is restored without the trader hunting for it | REQ-505 | BLOCKED — no client |
| TC-HLT-031 | A shortfall outstanding | Read the funds view | The amount short and the time remaining before positions may be closed | REQ-506, H7 | BLOCKED — endpoint absent |
| TC-HLT-032 | Dues and a shortfall at once | Read the funds view | The shortfall is presented first, because it carries a deadline | Edge cases | BLOCKED — no client |
| TC-HLT-033 | A scheduled charge that would take the balance below zero | Read the warning | Announced before the charge date with the amount needed to prevent it | REQ-503, H4 | BLOCKED — EB-6 unresolved |
| TC-HLT-034 | Positions closed to resolve a shortfall | Read the resulting entries | Each states that as its cause | H8 | BLOCKED — module absent |

---

## TC-COM — Communications

What is sent when the trader is not looking at the screen. The ladder, the channel rules, the
suppression logic and the delivery reconciliation are all built and are the most exhaustively tested
part of the system — 117 cases below, because a message that does not arrive is indistinguishable
from one that was never owed.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-COM-001 | A margin shortfall identified | Queue the ladder | Three steps, escalating | REQ-601 | `MessageLadderTest#aShortfallEscalatesInThreeSteps` |
| TC-COM-002 | A margin shortfall | Count the SMS in one day | Exactly three, never more | REQ-601, C12 | `MessageLadderTest#theLadderSendsExactlyThreeSms` |
| TC-COM-003 | A margin shortfall, any preferences | Queue each step | SMS and email both go on every step, regardless of preference | C1, C13 | `MessageLadderTest#everyStepCarriesSmsAndEmail` |
| TC-COM-004 | No WhatsApp opt-in | Queue the ladder | The WhatsApp step is dropped silently and the others are neither blocked nor delayed | REQ-604, REQ-624 | `MessageLadderTest#noOptInDropsTheStepWithoutDelayingTheOthers` |
| TC-COM-005 | A shortfall under ₹1.00 | Queue the ladder | Nothing is sent — a rounding artefact is not an event | REQ-601 | `MessageLadderTest#aTrivialShortfallProducesNoMessage` |
| TC-COM-006 | A shortfall of exactly ₹1.00 | Queue the ladder | Sent — the floor is inclusive | REQ-601 | `MessageLadderTest#oneRupeeIsAtTheFloorAndSends` |
| TC-COM-007 | A shortfall cleared | Queue the confirmation | Sent on the same channels the ladder used | REQ-609, C1 | `MessageLadderTest#aClearedShortfallIsConfirmedOnTheSameChannels` |
| TC-COM-008 | A debt above ₹500 on day 0 | Queue the dues messages | SMS goes on day 0 rather than being deferred | REQ-608 | `MessageLadderTest#aLargeDebtIsChasedBySmsFromDayZero` |
| TC-COM-009 | A debt at or below ₹500 | Queue the dues messages | SMS waits until day 14 | REQ-608 | `MessageLadderTest#aSmallDebtWaitsUntilDayFourteenForSms` |
| TC-COM-010 | A debt outstanding | Queue the dues schedule | Day 0, 7, 14, 30, then monthly — never daily | REQ-608 | `MessageLadderTest#duesFollowTheBandedSchedule` |
| TC-COM-011 | A debt outstanding | Queue the dues schedule | Email is sent on every dues day | REQ-608 | `MessageLadderTest#emailIsSentOnEveryDuesDay` |
| TC-COM-012 | A debt above ₹500 and a WhatsApp opt-in | Queue the dues message | WhatsApp is used; either condition alone is not enough | REQ-608, REQ-624 | `MessageLadderTest#whatsappOnDuesNeedsBoth` |
| TC-COM-013 | A trader in a different time zone | Compute the dues day count | Measured in the account's own zone | REQ-608 | `MessageLadderTest#duesDaysAreMeasuredInTheAccountsZone` |
| TC-COM-014 | The dues horizon boundary | Queue the schedule | The boundary day is included rather than skipped | REQ-608 | `MessageLadderTest#theDuesHorizonIsInclusive` |
| TC-COM-015 | Nothing owed | Queue the dues sequence | Nothing is queued | REQ-609 | `MessageLadderTest#nothingOwedQueuesNothing` |
| TC-COM-016 | A debt cleared | Queue the clear-down | Keyed on the clearance, so it cannot be sent twice for one event | REQ-609 | `MessageLadderTest#clearanceIsKeyedOnTheClearance` |
| TC-COM-017 | A clear-down or write-off with no opt-in | Queue it | The opt-in is honoured on these too | REQ-624 | `MessageLadderTest#clearDownAndWriteOffHonourTheOptIn` |
| TC-COM-018 | A pending payin | Queue the chase | One message at 30 minutes, and no others in between | REQ-611, C12 | `MessageLadderTest#aPendingPayinIsChasedOnceAtThirtyMinutes` |
| TC-COM-019 | A payin written off | Queue the message | Sent at that point, not pre-scheduled when the attempt began | REQ-611 | `MessageLadderTest#theWriteOffMessageIsNotPreScheduled` |
| TC-COM-020 | A payin chase with no WhatsApp opt-in | Queue it | Falls back to email rather than sending nothing | C4 | `MessageLadderTest#thePayinChaseFallsBackToEmail` |
| TC-COM-021 | Any queued message | Inspect the intent | It carries the occurrence it belongs to, and one is required | REQ-622 | `MessageLadderTest#anOccurrenceReferenceIsRequired`, `#everyEntryPointRequiresAnOccurrenceReference` |
| TC-COM-022 | A ladder for one shortfall | Inspect every step | All steps share the one occurrence reference | REQ-622, C14 | `MessageLadderTest#everyIntentSharesTheOccurrence` |
| TC-COM-023 | A queued step whose state resolved before dispatch | Run the relay | Dropped rather than sent and retracted | REQ-622 | `MessageRelayTest#resolvedStateDropsBeforeSubmitting` |
| TC-COM-024 | A queued step whose state still holds | Run the relay | Submitted | REQ-622 | `MessageRelayTest#holdingStateIsSubmitted` |
| TC-COM-025 | Any dropped message | Inspect the record | The drop and its reason are recorded against the account | REQ-623 | `MessageRelayTest#everyDropIsRecorded` |
| TC-COM-026 | An intent with no occurrence reference | Write it | Refused | REQ-622 | `MessageRelayTest#assertedRefIsRequired` |
| TC-COM-027 | An intent not yet due | Claim it | Refused — the relay claims only what is due | REQ-622 | `MessageRelayTest#notYetDueIsRefused` |
| TC-COM-028 | A trader with no address on the channel | Dispatch | Recorded as a drop with its reason, not as a delivery failure | REQ-623, REQ-624 | `MessageRelayTest#missingAddressIsADropNotAFailure` |
| TC-COM-029 | Intents across several due times | Claim a batch | Only due intents, in schedule order, bounded in size | LLD §2.2 | `JdbcMessageOutboxTest#onlyDueIntentsAreReturnedInScheduleOrder`, `#theBatchIsBounded` |
| TC-COM-030 | One event processed twice | Write the intents | Nothing is written twice | REQ-622 | `JdbcMessageOutboxTest#reProcessingAnEventWritesNothingTwice` |
| TC-COM-031 | The same template on two channels | Write both intents | Both exist — the channel distinguishes them | C1 | `JdbcMessageOutboxTest#channelDistinguishesIntents`, `SchemaConstraintTest#messageIntentDistinguishesChannelAndOccurrence` |
| TC-COM-032 | The same template for two occurrences | Write both intents | Both exist | REQ-622 | `JdbcMessageOutboxTest#occurrenceDistinguishesIntents` |
| TC-COM-033 | An intent already dispatched or dropped | Claim it again | Not returned a second time | REQ-622 | `JdbcMessageOutboxTest#aResolvedIntentIsNotReturnedAgain` |
| TC-COM-034 | An intent with an empty occurrence reference | Persist it | The database refuses it | LLD §2.2 | `SchemaConstraintTest#emptyAssertedReferenceIsRefused` |
| TC-COM-035 | A duplicate intent for one occurrence and channel | Persist it | The database refuses it, so a ladder step cannot send twice | REQ-622 | `SchemaConstraintTest#messageIntentIsUniquePerOccurrence` |
| TC-COM-036 | A shortfall email | Read it | The requirement, the available margin and the shortfall as three named figures that reconcile | REQ-603 | `ShortfallMessagesTest#theThreeFiguresMustReconcile`, `#onlyEmailCarriesTheBreakdown` |
| TC-COM-037 | A shortfall on any channel | Read the message | The amount short is stated on every channel | REQ-601, H7 | `ShortfallMessagesTest#everyChannelStatesTheAmount` |
| TC-COM-038 | A shortfall SMS | Read it | No action control and no link | REQ-602, C16 | `ShortfallMessagesTest#smsCarriesNoActionControl` |
| TC-COM-039 | A shortfall on WhatsApp or email | Read it | The action control is carried, with the exact amount | REQ-602 | `ShortfallMessagesTest#richerChannelsCarryTheAction` |
| TC-COM-040 | A shortfall caused by a market move | Read the message | The cause is distinguished from one the trader caused | REQ-603, B8 | `ShortfallMessagesTest#theCauseIsDistinguished` |
| TC-COM-041 | A shortfall whose deadline is not known | Read the message | It says the deadline is unknown rather than inventing one | H7 | `ShortfallMessagesTest#anUnknownDeadlineIsSaidToBeUnknown` |
| TC-COM-042 | No shortfall | Build a shortfall message | Refused — no message without the state it asserts | REQ-622 | `ShortfallMessagesTest#noMessageWithoutAShortfall` |
| TC-COM-043 | A payin confirmed | Read the confirmation | The amount, the masked source and the route that carried it | REQ-612, REQ-702 | `PayinMessagesTest#aConfirmationNamesAmountSourceAndRoute` |
| TC-COM-044 | A payin confirmed | Read the confirmation | Available margin rose, the withdrawable figure did not, and the term responsible is named | REQ-613, REQ-615 | `PayinMessagesTest#aConfirmationStatesBothFiguresAndTheReason` |
| TC-COM-045 | A payin confirmation | Read it for anything further | No balance figure and nothing beyond what the requirement lists | REQ-612 | `PayinMessagesTest#aConfirmationDisclosesNothingFurther` |
| TC-COM-046 | Each channel's wire value | Read it back | Every channel round-trips | C1 | `qa.PlatformContractTest#everyChannelRoundTrips` |
| TC-COM-047 | A channel value with odd case or spacing | Read it | Matched anyway | C1 | `qa.PlatformContractTest#aChannelIsReadCaseInsensitively` |
| TC-COM-048 | An unknown channel value | Read it | Refused rather than guessed at | C2 | `qa.PlatformContractTest#anUnknownChannelIsRefused` |
| TC-COM-049 | The channel vocabulary | Compare against the design | SMS, email and WhatsApp — and push is deliberately not one | C2 | `qa.PlatformContractTest#theThreeChannelsAreTheOnesDefined` |
| TC-COM-050 | A message spec with a blank template key | Construct it | Refused | REQ-625 | `qa.PlatformContractTest#aSpecWithoutATemplateKeyIsRefused` |
| TC-COM-051 | A spec built from a caller's parameter map | Mutate the map afterwards | The spec is unchanged, so the submission cannot drift from what was declared | R4 | `qa.PlatformContractTest#aSpecsParametersCannotBeMutated` |
| TC-COM-052 | A spec with no channel | Construct it | Refused — one submission carries exactly one channel | C1 | `qa.PlatformContractTest#aSpecRequiresAChannel` |
| TC-COM-053 | A payin failure of each kind | Read the message | Each of the six outcomes gets its own template | REQ-614, A9a | `PayinMessagesTest#everyFailureOutcomeGetsItsOwnTemplate` |
| TC-COM-054 | A decline that may have landed after a debit | Read the message | The refund is stated conditionally rather than asserted | C5 | `PayinMessagesTest#whereTheBankMayHaveDebitedTheRefundIsConditional` |
| TC-COM-055 | A payment that never reached the bank | Read the message | The flat assertion that nothing was debited is used, because it is safe | C5 | `PayinMessagesTest#whereNothingReachedTheBankTheRefundIsNotConditional` |
| TC-COM-056 | An outage on our side | Read the message | It says so rather than blaming the trader's bank | A9c | `PayinMessagesTest#ourOwnOutageIsOwned` |
| TC-COM-057 | A bank decline | Read the message | Stated as failed | A9a | `PayinMessagesTest#aBankDeclineIsFailed` |
| TC-COM-058 | No answer from the bank | Read the message | Stated as unknown, not failed | A9b | `PayinMessagesTest#anUnresolvedPayinIsUnknownNotFailed` |
| TC-COM-059 | A confirmed payin | Attempt to send it as a failure | Refused | REQ-614 | `PayinMessagesTest#aConfirmedOutcomeCannotBeSentAsAFailure` |
| TC-COM-060 | A failure message | Read the alternative offered | Only a route that can be executed with headroom today | A9d | `PayinMessagesTest#onlyExecutableRoutesAreOffered` |
| TC-COM-061 | A payin confirmation | Read the channel | Email, the only channel that can carry the effect on two figures | REQ-613, C19 | `PayinMessagesTest#theConfirmationGoesOnEmail` |
| TC-COM-062 | A payin failure with no WhatsApp opt-in | Read the channel | Email, rather than silence | C4 | `PayinMessagesTest#aFailureFallsBackToEmail` |
| TC-COM-063 | An over-long masked source | Build the confirmation | Refused | C15 | `PayinMessagesTest#aLongSourceIsRefused` |
| TC-COM-064 | A withdrawal cancelled by the trader | Read the channel | Email only — no SMS, no WhatsApp | REQ-616, C2 | `PayoutMessagesTest#aCancellationIsEmailOnly` |
| TC-COM-065 | A cancellation message | Read it | It states that no figure moved, because none was ever held | REQ-616, W3 | `PayoutMessagesTest#aCancellationStatesNothingMoved` |
| TC-COM-066 | A partial settlement | Read the message | Its own message, naming the amount requested, the amount sent and the deduction | REQ-617 | `PayoutMessagesTest#aPartialTransferIsItsOwnMessage`, `#aPartialTransferMustNameTheDeduction` |
| TC-COM-067 | Any terminal outcome | Read the message | It states where the money physically is, not only a status | REQ-618 | `PayoutMessagesTest#aTerminalMessageSaysWhereTheMoneyIs` |
| TC-COM-068 | Money that never left | Read the message | No destination is claimed and no bank reference is quoted | REQ-618, REQ-620 | `PayoutMessagesTest#noDestinationWhereTheMoneyNeverLeft`, `#nothingDeductedCarriesNoBankReference` |
| TC-COM-069 | A settled payout | Read the references | The bank's and ours are separate named fields | C8, REQ-620 | `PayoutMessagesTest#theTwoReferencesAreSeparateFields` |
| TC-COM-070 | A bank reference not yet available | Read the message | It says so rather than substituting ours | REQ-620, C8 | `PayoutMessagesTest#anUnavailableBankReferenceIsSaidToBePending`, `#ourReferenceCannotBeSentAsTheBanks` |
| TC-COM-071 | Each end-of-day outcome | Read the message | Each gets its own template, and every outcome reaches email | REQ-619 | `PayoutMessagesTest#eachOutcomeGetsItsOwnTemplate`, `#everyOutcomeReachesEmail` |
| TC-COM-072 | An outcome where money moved | Read the WhatsApp channel | Only those outcomes carry WhatsApp | C2 | `PayoutMessagesTest#whatsappCarriesOnlyTheOutcomesWhereMoneyMoved` |
| TC-COM-073 | The banking rail unavailable | Read the message | The request stays open and cancellable, and is not announced as closed | REQ-619 | `PayoutMessagesTest#anUnavailableRailLeavesTheRequestOpen` |
| TC-COM-074 | Any other outcome | Read the message | The request is stated as closed | W4a | `PayoutMessagesTest#everyOtherOutcomeClosesTheRequest` |
| TC-COM-075 | A non-terminal state | Attempt to announce it | Refused — there is nothing terminal to report | C6 | `PayoutMessagesTest#aNonTerminalStateIsNotAnnounced` |
| TC-COM-076 | A non-partial outcome | Read the message | It still names its reason | W4c | `PayoutMessagesTest#aNonPartialOutcomeNamesItsReason` |
| TC-COM-077 | A trader with no WhatsApp opt-in | Attempt to send on WhatsApp | Refused; an explicit opt-in is required | REQ-624 | `ChannelPreferencesTest#whatsappNeedsAnExplicitOptIn` |
| TC-COM-078 | An opt-in with no provenance recorded | Accept it | Refused — consent must be evidenced, not asserted | REQ-624 | `ChannelPreferencesTest#anOptInWithoutProvenanceIsRefused` |
| TC-COM-079 | A trader who turned everything off | Send a shortfall SMS | Sent — preferences cannot suppress a regulatory message | C13, REQ-626 | `ChannelPreferencesTest#smsIsNotControllable` |
| TC-COM-080 | A trader who disabled optional email | Send an optional email | Suppressed | REQ-626 | `ChannelPreferencesTest#optionalEmailCanBeTurnedOff` |
| TC-COM-081 | A trader with no preference recorded | Send an optional SMS | Permitted | REQ-626 | `ChannelPreferencesTest#noPreferencePermitsAnOptionalSms` |
| TC-COM-082 | A bouncing email and no WhatsApp opt-in | Assess reachability | SMS is the only reachable channel, and the account is flagged | REQ-627 | `ChannelPreferencesTest#bouncingEmailWithNoOptInLeavesSmsOnly` |
| TC-COM-083 | A working email | Assess reachability | Not SMS-only | REQ-627 | `ChannelPreferencesTest#workingEmailIsNotSmsOnly` |
| TC-COM-084 | A WhatsApp opt-in present | Assess reachability | The account stays reachable | REQ-627 | `ChannelPreferencesTest#anOptInKeepsTheAccountReachable` |
| TC-COM-085 | A submission that succeeded | Poll its delivery | Recorded and polling stops | REQ-623 | `DeliveryReconcilerTest#deliveredAndSentSettle`, `#settledBeatsStuck` |
| TC-COM-086 | A terminal non-delivery | Reconcile it | Resubmitted under a new request id, with the attempt chain recorded | LLD §7.9 | `DeliveryReconcilerTest#terminalNonDeliveryResubmits` |
| TC-COM-087 | A notification stuck past the poll window | Reconcile it | A human is alerted rather than a retry issued | LLD §7.9 | `DeliveryReconcilerTest#stuckMessageAlertsRatherThanResubmits`, `#stuckIsReadRatherThanInferred` |
| TC-COM-088 | One channel succeeding, the other failing | Assess the intimation | Made, and the failing channel is still recorded and alerted | C1, REQ-627 | `DeliveryReconcilerTest#oneChannelSucceedingMakesTheIntimation`, `#failedChannelIsRecordedEvenWhenTheOtherSucceeded` |
| TC-COM-089 | Both channels failing while the shortfall stands | Assess the intimation | A human is paged — the deadline is live and no message carried | C1, LLD §7.9 | `DeliveryReconcilerTest#bothFailingWhileTheStateStandsPagesAHuman` |
| TC-COM-090 | An SMS reported as delivered | Read the record | Vendor acceptance, never treated as proof the trader saw it | LLD §7.9 | `DeliveryReconcilerTest#smsDeliveredIsNotProofOfReceipt` |
| TC-COM-091 | A notification with no outcome recorded | Reconcile it | Not treated as success | REQ-623 | `DeliveryReconcilerTest#missingOutcomeIsNotSuccess` |
| TC-COM-092 | A notification still in flight | Reconcile it | Polling continues | REQ-623 | `DeliveryReconcilerTest#notStuckStillPolls` |
| TC-COM-093 | A submission | Read the delivery record | The exact template version the service resolved is stored | REQ-625 | `CommunicationClientTest#templateVersionIsCaptured` |
| TC-COM-094 | A submission | Read the response | The notification id is returned and recorded | REQ-623 | `CommunicationClientTest#submissionReturnsTheNotificationId` |
| TC-COM-095 | A replayed submission | Send it | Flagged as a replay rather than sent again | LLD §2.2 | `CommunicationClientTest#replayedSubmissionIsFlagged` |
| TC-COM-096 | An ambiguous 500 from the service | Handle it | Resubmitted under the same key, so it cannot send twice | LLD §7.9 | `CommunicationClientTest#ambiguous500IsResubmittedWithTheSameKey`, `#other500sAreNotResubmitted` |
| TC-COM-097 | A 403 from the service | Handle it | The distinct reasons are told apart rather than collapsed | OA-2 | `CommunicationClientTest#the403ReasonsAreDistinguished` |
| TC-COM-098 | A template problem | Handle it | Not treated as an outage | REQ-625 | `CommunicationClientTest#templateProblemsAreNotOutages` |
| TC-COM-099 | An unrecognised failure reason | Handle it | Treated as an outage rather than guessed at | LLD §7.9 | `CommunicationClientTest#unrecognisedReasonIsAnOutage`, `#gatewayErrorIsAnOutage` |
| TC-COM-100 | An acceptance carrying no notification id | Handle it | Pages someone — an accepted message nothing can track is not accepted | REQ-623 | `CommunicationClientTest#acceptedWithoutAnIdPages` |
| TC-COM-101 | A malformed payload | Send it | Classified as our bug, not the vendor's outage | R4 | `CommunicationClientTest#badPayloadIsOurBug` |
| TC-COM-102 | A deployment with no sender identity | Submit | Reported as a deployment fault | REQ-623 | `CommunicationClientTest#missingIdentityIsADeploymentFault` |
| TC-COM-103 | A status read from the service | Map it | The vendor's vocabulary maps onto ours completely | LLD §7.9 | `CommunicationClientTest#statusReadMapsTheVocabulary`, `#stuckAndAddressKnownAreRead` |
| TC-COM-104 | A missing address the service knows about | Handle it | Treated conservatively rather than as a delivery | REQ-624 | `CommunicationClientTest#missingAddressKnownIsConservative` |
| TC-COM-105 | Parallel arrays in a vendor response | Read them | Paired by position, as the contract specifies | LLD §7.9 | `CommunicationClientTest#arraysOfOnePairedByPosition` |
| TC-COM-106 | An ordinary email address | Validate it | Accepted, with the local part's case preserved | §6 addressing | `NotificationSubmissionTest#anOrdinaryEmailIsAccepted`, `#anEmailLocalPartKeepsItsCase` |
| TC-COM-107 | A plain E.164 number | Validate it | Accepted | §6 addressing | `NotificationSubmissionTest#plainE164IsAccepted` |
| TC-COM-108 | A number without a leading plus, or with punctuation | Validate it | Refused rather than rewritten | §6 addressing | `NotificationSubmissionTest#aNumberWithoutAPlusIsRefused`, `#parenthesesAreRefused`, `#aMalformedNumberIsRefused` |
| TC-COM-109 | A blank or non-address value | Validate it | Refused | §6 addressing | `NotificationSubmissionTest#aBlankAddressIsRefused`, `#aNonAddressIsRefused` |
| TC-COM-110 | Any address | Submit it | Never rewritten on the way to the vendor | §6 addressing | `NotificationSubmissionTest#noAddressIsRewritten` |
| TC-COM-111 | A WhatsApp address | Validate it | The same rule as SMS applies | §6 addressing | `NotificationSubmissionTest#whatsappUsesTheSameRule` |
| TC-COM-112 | The V26 migration's delivery-status vocabulary | Compare to the enum | The two agree | REQ-623 | `VocabularyDriftTest#deliveryStatusMatchesV26` |
| TC-COM-113 | The V26 channel constraint | Compare to the enum | WhatsApp is deliberately excluded, so no delivery row can exist for an unsendable channel | OA-2 | `VocabularyDriftTest#channelConstraintExcludesWhatsappDeliberately` |
| TC-COM-114 | A delivery row keyed by outbox id and channel | Persist a duplicate | Refused | REQ-623 | `SchemaConstraintTest#theChannelIsComparedExactly` |
| TC-COM-115 | The preference surface | Read it | It states which messages cannot be turned off rather than offering a control that does nothing | REQ-626 | BLOCKED — no client |
| TC-COM-116 | An SMS-only account in an action state | Read the funds banner | Present and not dismissible | REQ-627 | BLOCKED — no client |
| TC-COM-117 | A registered SMS template | Submit it for approval | Approved by the aggregator under the six-character header | C-Q4 | MANUAL |

---

## TC-CFG — Configuration

Every value here is policy rather than logic, changed by someone who is not an engineer. The cases
check that each is read at the point of use rather than compiled in, and that a value nobody set
fails loudly instead of defaulting quietly.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-CFG-001 | A route's daily cap | Enforce it | Measured against everything sent on that route today, not per transaction | REQ-701 | `RouteSelectorTest#capIsDailyAndNotPerTransaction`, `JdbcRouteCapLedgerTest#usageIsPerDay` |
| TC-CFG-002 | Usage on two routes | Read each route's headroom | Usage is tracked per route | REQ-701 | `JdbcRouteCapLedgerTest#usageIsPerRoute` |
| TC-CFG-003 | Usage by two accounts | Read each account's headroom | Usage is tracked per account | REQ-701 | `JdbcRouteCapLedgerTest#usageIsPerAccount` |
| TC-CFG-004 | Several payments on one route today | Read the usage | They accumulate | REQ-701 | `JdbcRouteCapLedgerTest#repeatedRecordsAccumulate` |
| TC-CFG-005 | A route not used today | Read its headroom | The whole cap | REQ-701 | `JdbcRouteCapLedgerTest#anUnusedRouteHasItsWholeCap` |
| TC-CFG-006 | A route with no cap configured | Read its headroom | Unbounded, reported as absent rather than zero | REQ-701 | `JdbcRouteCapLedgerTest#anUncappedRouteReportsEmpty`, `#anUnconfiguredRouteReportsUnbounded` |
| TC-CFG-007 | Usage exceeding the cap | Read the headroom | Floors at zero | REQ-701 | `JdbcRouteCapLedgerTest#headroomFloorsAtZero` |
| TC-CFG-008 | A negative usage figure | Record it | Refused | REQ-701 | `JdbcRouteCapLedgerTest#negativeUsageIsRefused` |
| TC-CFG-009 | Usage recorded | Read the headroom | Reduced by exactly what was recorded | REQ-701 | `JdbcRouteCapLedgerTest#recordedUsageReducesHeadroom` |
| TC-CFG-010 | Concurrent payments on one route | Record both | Both land; no update is lost | REQ-701 | `JdbcRouteCapLedgerTest#concurrentRecordsAllLand` |
| TC-CFG-011 | An amount and today's headroom | Select a route | Chosen server-side, against amount and headroom | REQ-702 | `RouteSelectorTest#withinHeadroomTakesTheFirstChoice` |
| TC-CFG-012 | A client attempting to name a route | Inspect the API surface | No route parameter exists to name | REQ-702 | `OpenApiSpecTest#specDescribesEveryEndpoint` |
| TC-CFG-013 | A debt below the minimum | Check the waiver | Applied only to the exact amount that settles the debt | REQ-703 | `MinimumAddPolicyTest#theWaiverIsExact` |
| TC-CFG-014 | Any other funding amount | Check the minimum | The floor applies | REQ-703 | `MinimumAddPolicyTest#atOrAboveTheMinimumIsPermitted`, `#belowTheMinimumIsRefusedWithNoDebt` |
| TC-CFG-015 | The primary bank account | Read the default source and destination | The primary is used in both directions | REQ-706 | `FundingSourceTest#thePrimaryIsTheDefault` |
| TC-CFG-016 | The `payoutCutoff` value | Compute an arrival date | The date is derived from the configured boundary, not a compiled constant | REQ-707, G5 | `ArrivalDateCalculatorTest#atTheCutoffTheRequestHasMissedTodaysRun` |
| TC-CFG-017 | The `debitInterestRate` value | Read it for a message | Taken from configuration; a provisional value is never quoted | REQ-708, G1 | `DebitInterestRateTest#aProvisionalRateIsNeverQuoted` |
| TC-CFG-018 | A minimum add of zero or less | Load the configuration | Refused — it would disable the floor silently | G1 | `qa.PlatformContractTest#aZeroMinimumAddIsRefused` |
| TC-CFG-019 | Any configured value absent | Load the configuration | Refused rather than defaulted in place | G2 | `qa.PlatformContractTest#everyConfiguredValueIsRequired` |
| TC-CFG-020 | The shipped configuration | Read it | ₹100 minimum, a 3:00 PM cut-off, and a provisional 18% rate | Config §2, §4, §5 | `qa.PlatformContractTest#theShippedDefaultsAreTheConfiguredValues` |
| TC-CFG-021 | The provisional rate | Ask whether a production message may quote it | No | EB-8 | `qa.PlatformContractTest#theShippedRateMayNotBeQuoted` |
| TC-CFG-022 | A cap lowered below what a trader already sent today | Read the existing request | It completes under the rules it was made under | G3 | `qa.MovementContractTest#beingOverCapIsReportedSeparately` |
| TC-CFG-023 | A configured value | Read message copy that mentions it | The copy reads the value rather than restating it | G1 | BLOCKED — no client |
| TC-CFG-024 | A trader at the bank-account limit | Attempt to add another | The limit reached is named rather than a control disabled | REQ-705 | BLOCKED — owned by Profile |
| TC-CFG-025 | An account with an open withdrawal | Attempt to delete it in Profile | Refused, using the open-withdrawal fact FMS exposes | G4, REQ-706 | BLOCKED — endpoint absent |
| TC-CFG-026 | A configured post-funding destination | Read the funds summary | The configured value, or null where none is set | REQ-709, REQ-710 | BLOCKED — endpoint absent |

---

## TC-API — API contract & edge layer

Three endpoints exist of the thirteen the LLD defines. These cases cover the ones that do, plus the
published specification itself — which is the artefact every generated client is built from, and
which drifted from the filter chain once already.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-API-001 | A valid withdrawal command | POST the request | 201 with the request, its reference and its arrival date | LLD §4.1 | `PayoutApiTest#validRequestIsCreated` |
| TC-API-002 | An amount above the withdrawable figure | POST the request | 422, carrying the derivation so the client need not re-fetch | LLD §4.4 | `PayoutApiTest#aboveWithdrawableIsUnprocessableAndExplains` |
| TC-API-003 | An open request already exists | POST a second | 409 `request_already_open` | LLD §4.4 | `PayoutApiTest#secondOpenRequestIsConflict` |
| TC-API-004 | The derivation and RMS disagree | POST the request | 409 rather than a figure the system cannot stand behind | LLD §4.4 | `PayoutApiTest#divergentVerdictIsConflict` |
| TC-API-005 | An unverified destination | POST the request | 422 `destination_not_verified` | LLD §4.4 | `PayoutApiTest#unverifiedDestinationIsUnprocessable` |
| TC-API-006 | A zero or negative amount | POST the request | Refused at the edge | LLD §4.3 | `PayoutApiTest#nonPositiveAmountIsRefused` |
| TC-API-007 | A malformed body | POST it | 400, and nothing about the internals leaks | LLD §4.4 | `PayoutApiTest#malformedBodyIsBadRequest`, `HostileBodyApiTest#malformedBodyLeaksNothing` |
| TC-API-008 | Any request | Read where the account came from | The principal, never the path or the body | LLD §4.3 | `PayoutApiTest#accountComesFromThePrincipal`, `OpenApiSpecTest#noRequestAcceptsAnAccountIdentifier` |
| TC-API-009 | Another trader's request id | DELETE it | Not found rather than forbidden | LLD §4.3 | `PayoutApiTest#anotherTradersRequestIsNotFound` |
| TC-API-010 | An open request | GET then DELETE it | The lifecycle is visible end to end and the cancellation is reflected | REQ-305 | `PayoutApiTest#cancelAndOpenRequestLifecycle` |
| TC-API-011 | Any money field on the wire | Read it | An integer count of paise | R5 | `PayoutApiTest#moneyIsPaiseOnTheWire`, `OpenApiSpecTest#moneyIsNeverADecimal` |
| TC-API-012 | An uncapped route | GET the payin limits | A null remaining amount, meaning unbounded, not zero | REQ-701 | `PayoutApiTest#uncappedRouteIsNullNotZero` |
| TC-API-013 | A decimal or quoted paise value | POST it | Refused | R5 | `HostileBodyApiTest#decimalPaiseIsRefused`, `#quotedNumberIsRefused`, `#wholeNumberFloatIsAlsoRefused` |
| TC-API-014 | An integer paise value | POST it | Accepted | R5 | `HostileBodyApiTest#integerPaiseStillWorks` |
| TC-API-015 | A currency other than INR | POST it | Refused | LLD §4.3 | `HostileBodyApiTest#wrongCurrencyIsRefused` |
| TC-API-016 | An unknown field in the body | POST it | Ignored rather than failing the request | LLD §4.3 | `HostileBodyApiTest#unknownFieldIsIgnored` |
| TC-API-017 | Eleven shapes of malformed body | POST each | Every one is a client error, never a 500 | LLD §4.4 | `HostileBodyApiTest#malformedBodiesAreClientErrors` |
| TC-API-018 | A period that will not parse | GET the transactions | 400, naming the parameter without echoing the value | LLD §4.4 | `TransactionsApiTest#badPeriodIsAClientError` |
| TC-API-019 | A statement request | GET it | Served as a download, matching the list exactly | REQ-407, L8a | `TransactionsApiTest#exportIsADownload`, `#exportMatchesTheList` |
| TC-API-020 | A statement whose content would carry an account number | GET it | The export fails rather than leaking it | PR-32 | `TransactionsApiTest#exportFailsRatherThanLeakAnAccountNumber` |
| TC-API-021 | A statement request | Read the descriptions | Plain language, carried through to the file | REQ-401 | `TransactionsApiTest#exportCarriesPlainLanguage` |
| TC-API-022 | The published specification | Compare against the controllers | Every endpoint the controllers expose is described | Stage 8 gate | `OpenApiSpecTest#specDescribesEveryEndpoint` |
| TC-API-023 | The published specification | Read the error shape | Published, so clients branch on the code rather than the message | LLD §4.4 | `OpenApiSpecTest#errorShapeIsPublished` |
| TC-API-024 | Every documented failure status | Read its response body | Each references the error schema | LLD §4.4 | `OpenApiSpecTest#failureResponsesReferenceTheErrorSchema` |
| TC-API-025 | The declared security scheme | Compare against what the filter chain accepts | They agree, and a bearer credential is refused because bearer is not what is declared | Stage 8 gate | `OpenApiSpecTest#securitySchemeMatchesWhatIsEnforced` |
| TC-API-026 | The running service | Request the interactive documentation UI | Not shipped | Security | `OpenApiSpecTest#swaggerUiIsNotShipped` |
| TC-API-027 | A stale-figures failure | Trigger it | 409 carrying the computed-at instant and the source | REQ-107, LLD §4.4 | `ApiExceptionHandlerTest#staleFiguresCarryTheirProvenance` |
| TC-API-028 | An upstream outage | Trigger it | 503 naming which upstream, never which vendor | LLD §4.4 | `ApiExceptionHandlerTest#anUpstreamOutageDoesNotNameTheVendor` |
| TC-API-029 | An unnominated calendar | Trigger it | 503 rather than a guessed date | OA-5 | `ApiExceptionHandlerTest#anUnavailableCalendarFailsSafe` |
| TC-API-030 | An invariant violation | Trigger it | Nothing about the internal state reaches the client | LLD §7.7 | `ApiExceptionHandlerTest#anInvariantViolationLeaksNothing` |

---

## TC-SEC — Security & disclosure

Who may read what, what may never appear in an outbound artefact, and what happens to a request
that arrives without a credential. The masking rules are structural — a type that cannot hold a full
account number cannot leak one — which is why several of these cases are constructor tests rather
than endpoint tests.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-SEC-001 | No credential | Call any funds endpoint | Refused | NFR Security | `ApiSecurityTest#unauthenticatedRequestsAreRefused` |
| TC-SEC-002 | No credential | Call an unknown path under the API prefix | Also refused, rather than 404 disclosing the shape of the surface | NFR Security | `ApiSecurityTest#unknownApiPathsAreAlsoProtected` |
| TC-SEC-003 | No credential | POST a body | Refused before the body is read | NFR Security | `ApiSecurityTest#aWriteRefusesBeforeReadingTheBody` |
| TC-SEC-004 | A refused request | Read the response | Nothing about the system leaks | NFR Security | `ApiSecurityTest#aRefusalLeaksNothing` |
| TC-SEC-005 | The health probe | Call it without a credential | Reachable, because a liveness probe that needs a credential is not a liveness probe | Ops | `ApiSecurityTest#theHealthProbeStaysReachable` |
| TC-SEC-006 | A cross-origin preflight | Send it | No CORS grant is issued | NFR Security | `PayoutCsrfSurfaceTest#noCorsPreflightIsGranted` |
| TC-SEC-007 | A form-encoded cross-origin content type | POST it | Refused | NFR Security | `PayoutCsrfSurfaceTest#crossOriginFormContentTypesAreRefused` |
| TC-SEC-008 | A JSON body from a legitimate caller | POST it | Reaches the handler | NFR Security | `PayoutCsrfSurfaceTest#jsonReachesTheHandler` |
| TC-SEC-009 | A gateway callback | Verify it | The verification names what it checked, and the escape hatch is conspicuous and demands a reason | NFR Security | `VerifiedGatewayCallbackTest#aVerificationMustNameWhatItChecked`, `#theEscapeHatchIsObvious`, `#theEscapeHatchStillDemandsAReason` |
| TC-SEC-010 | A payment request to the gateway | Read what is sent | The amount exactly, and nothing personal | R4 | `JuspayGatewayTest#requestCarriesAmountExactlyAndNothingPersonal` |
| TC-SEC-011 | A statement export | Search it for an account number | An unmasked number in any field fails the export rather than being redacted | PR-32 | `StatementCsvWriterTest#unmaskedAccountNumberInDescriptionFailsTheExport`, `#unmaskedAccountNumberInReferenceFailsTheExport` |
| TC-SEC-012 | A masked tail and ordinary dates in an export | Run the guard | Not flagged — the guard does not fire on legitimate content | PR-32 | `StatementCsvWriterTest#maskedTailAndDatesAreNotFlagged` |
| TC-SEC-013 | A nine-digit reference | Export it | The guard trips by design rather than letting a long digit run through | PR-32 | `StatementCsvWriterTest#nineDigitReferenceTripsTheGuardByDesign` |
| TC-SEC-014 | A well-formed account identifier | Construct it | Accepted | R4 | `qa.PlatformContractTest#aWellFormedUccIsAccepted` |
| TC-SEC-015 | An account identifier with punctuation, spaces or over-length | Construct it | Refused | R4 | `qa.PlatformContractTest#aMalformedAccountIdentifierIsRefused` |
| TC-SEC-016 | A rejected identifier that might be a regulated value | Read the refusal message | It does not echo the value | R4 | `qa.PlatformContractTest#theRefusalDoesNotEchoTheValue` |
| TC-SEC-017 | A null account identifier | Construct it | Refused | R4 | `qa.PlatformContractTest#aNullAccountIdentifierIsRefused` |
| TC-SEC-018 | An account identity | Render it | Its UCC and nothing else | R4 | `qa.PlatformContractTest#anAccountRendersAsItsUccAndNothingElse` |
| TC-SEC-019 | A PAN-shaped value | Construct an account identity | Accepted — the type bounds charset and length only. **Finding QA-01** | R4 | `qa.PlatformContractTest#theTypeCannotItselfExcludeARegulatedIdentifier` |
| TC-SEC-020 | An authenticated principal | Resolve the account | Taken from the subject claim | LLD §4.3 | `qa.PlatformContractTest#theAccountComesFromThePrincipal` |
| TC-SEC-021 | A principal with a blank or null subject | Resolve the account | An outage to fix, not a refusal to render | LLD §4.3 | `qa.PlatformContractTest#aPrincipalWithNoSubjectIsAnOutage` |
| TC-SEC-022 | No principal at all | Resolve the account | Refused rather than defaulted | LLD §4.3 | `qa.PlatformContractTest#anAbsentPrincipalIsRefused` |
| TC-SEC-023 | A subject that is not a well-formed identifier | Resolve the account | Refused | R4 | `qa.PlatformContractTest#aSubjectThatIsNotAUccDoesNotBecomeAnAccount` |
| TC-SEC-024 | A bank account whose masked form carries a full number | Construct it | Refused at the boundary, so it can never be persisted or rendered | PR-31 | `qa.PlatformContractTest#anUnmaskedAccountNumberIsRefused` |
| TC-SEC-025 | The ordinary masked form | Construct it | Accepted | PR-31 | `qa.PlatformContractTest#theOrdinaryMaskedFormIsAccepted` |
| TC-SEC-026 | Six digits, then seven | Construct each | Six is the boundary and is accepted; seven is refused | PR-31 | `qa.PlatformContractTest#sixDigitsIsTheBoundary` |
| TC-SEC-027 | A bank account missing its reference or mask | Construct it | Refused | PR-31 | `qa.PlatformContractTest#anAccountWithoutItsReferenceOrMaskIsRefused` |
| TC-SEC-028 | A primary account whose verification lapsed | Read its two flags | Independent — primary does not imply verified | REQ-203, REQ-706 | `qa.PlatformContractTest#verifiedAndPrimaryAreIndependent` |
| TC-SEC-029 | An attempt's funding source | Persist it | Stored masked | C15 | `PayinAttemptTest#theFundingSourceIsRecordedMasked` |
| TC-SEC-030 | A payin confirmation message | Read it | The last four digits only, and no balance figure | C15, REQ-612 | `PayinMessagesTest#aConfirmationDisclosesNothingFurther` |
| TC-SEC-031 | A payout message | Read it | No full account number anywhere | C15, REQ-618 | `PayoutMessagesTest#aTerminalMessageSaysWhereTheMoneyIs` |
| TC-SEC-032 | A CSV opened in a spreadsheet | Read a description beginning with a formula character | Neutralised rather than executed | Security | `StatementCsvWriterTest#formulaInjectionIsNeutralised` |
| TC-SEC-033 | A withdrawal request | Check for an out-of-band confirmation | None exists — the account-takeover gap C-Q8 is open and blocks Phase 3 | C-Q8, C9 | BLOCKED — authentication owns it |
| TC-SEC-034 | The declared HTTP Basic scheme | Assess it for production | Credentials on every request, no expiry, revocation, rotation or scope | Security review MEDIUM-2 | MANUAL |
| TC-SEC-035 | The deployed gateway | Confirm it validates and strips the platform token before FMS sees a request | Confirmed against the deployment, not assumed | LLD §4.3 | MANUAL |

---

## TC-DATA — Persistence & constraints

The business rules this system enforces in schema rather than in code. A stub repository has no
constraints to violate, so every case here runs against a real PostgreSQL started for the build.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-DATA-001 | A migrated database | Compare against the migration set | Every migration applied, none skipped | Schema | `SchemaConstraintTest#everyMigrationApplied` |
| TC-DATA-002 | An open request | Insert a second for the same account | Refused by the partial unique index | W4 | `SchemaConstraintTest#ruleW4RefusesASecondOpenRequest` |
| TC-DATA-003 | Requests in each open state | Check the index predicate | Every open state is covered, not only ACCEPTED | W4 | `SchemaConstraintTest#ruleW4CoversEveryOpenState` |
| TC-DATA-004 | A closed request | Insert a new one | Permitted | W4a | `SchemaConstraintTest#ruleW4LetsAClosedRequestBeFollowedByANewOne` |
| TC-DATA-005 | A payout state outside the vocabulary | Insert it | Refused — the vocabulary is closed at the database | LLD §7.5 | `SchemaConstraintTest#payoutStateVocabularyIsClosed` |
| TC-DATA-006 | A row whose bank reference equals the FMS reference | Insert it | Refused | C8 | `SchemaConstraintTest#ruleC8RefusesABankReferenceEqualToOurs` |
| TC-DATA-007 | A second attempt reusing a gateway reference | Insert it | Refused | A6 | `SchemaConstraintTest#ruleA6RefusesADuplicateGatewayReference` |
| TC-DATA-008 | Many attempts with no gateway reference yet | Insert them | All permitted — the constraint is on assigned references only | A6 | `SchemaConstraintTest#ruleA6PermitsManyAttemptsWithoutAReference` |
| TC-DATA-009 | An attempt for a non-positive amount | Insert it | Refused by the check constraint | REQ-201 | `SchemaConstraintTest#payinAmountMustBePositive` |
| TC-DATA-010 | An intent with an empty occurrence reference | Insert it | Refused | REQ-622 | `SchemaConstraintTest#emptyAssertedReferenceIsRefused` |
| TC-DATA-011 | A duplicate intent for one occurrence | Insert it | Refused | REQ-622 | `SchemaConstraintTest#messageIntentIsUniquePerOccurrence` |
| TC-DATA-012 | Intents differing only by channel or occurrence | Insert both | Both permitted | C1 | `SchemaConstraintTest#messageIntentDistinguishesChannelAndOccurrence` |
| TC-DATA-013 | A channel compared case-insensitively | Insert it | The comparison is exact, so a case variant is not silently accepted | REQ-623 | `SchemaConstraintTest#theChannelIsComparedExactly` |
| TC-DATA-014 | An attempt with every mutable field set | Save and reload it | Every field round-trips | REQ-405 | `JdbcPayinAttemptRepositoryTest#roundTripsThroughEveryMutableField` |
| TC-DATA-015 | An attempt loaded from the database | Inspect its transitions | It does not replay the transitions that produced it | REQ-405 | `JdbcPayinAttemptRepositoryTest#aLoadedAttemptDoesNotReplayItsTransitions` |
| TC-DATA-016 | Two writers holding one attempt | Write the stale copy | Refused; the winning write re-anchors the version | F-38 | `JdbcPayinAttemptRepositoryTest#aStaleWriteIsRefused`, `#aSuccessfulWriteReAnchorsTheVersion` |
| TC-DATA-017 | Attempts spanning a period's end dates | Query the period | Both end dates are included | REQ-403 | `JdbcPayinAttemptRepositoryTest#inPeriodIncludesBothEndDates` |
| TC-DATA-018 | Confirmed and unconfirmed attempts | Ask for the last confirmed deposit | Unconfirmed attempts are ignored | A1 | `JdbcPayinAttemptRepositoryTest#lastConfirmedForIgnoresUnconfirmedAttempts` |
| TC-DATA-019 | A gateway reference reused across accounts | Insert it | Rule A6 is enforced through the repository, not only in the service | A6 | `JdbcPayinAttemptRepositoryTest#ruleA6IsEnforcedThroughTheRepository` |
| TC-DATA-020 | Concurrent reference generation | Generate many | Every reference is distinct and no two callers collide | C18 | `SequentialFmsReferenceGeneratorTest#everyReferenceIsDistinct`, `#concurrentCallersNeverCollide` |
| TC-DATA-021 | A generated reference | Read it | It fits its column and is legible | C14 | `SequentialFmsReferenceGeneratorTest#theReferenceFitsAndReads` |
| TC-DATA-022 | A generated reference | Compare it to a bank reference | Distinguishable, so the two can never be confused | C8 | `SequentialFmsReferenceGeneratorTest#theReferenceIsDistinguishableFromABankReference` |
| TC-DATA-023 | A data source present | Start the module | Every bean is registered | F-39 | `FundsModuleConfigurationTest#theModuleAssemblesWhenADataSourceIsPresent` |
| TC-DATA-024 | No data source | Start the module | Nothing is registered, so the web layer can still be tested | F-39 | `FundsModuleConfigurationTest#withoutADataSourceNothingIsRegistered` |
| TC-DATA-025 | A ledger source present or absent | Start the module | The transaction list is present or absent accordingly | F-39 | `FundsModuleConfigurationTest#theTransactionListAssemblesWithALedgerSource`, `#withoutALedgerSourceTheTransactionListIsAbsent` |
| TC-DATA-026 | The in-flight movement source | Resolve it | It is the payin source, not a second implementation | F-31 | `FundsModuleConfigurationTest#theInFlightSourceIsThePayinSource` |
| TC-DATA-027 | The local-only stub profile | Start with and without it | The stubs exist only under the profile | Ops | `LocalOnlyStubConfigurationTest#withTheProfileTheStubsArePresent`, `#withoutTheProfileNoStubExists` |
| TC-DATA-028 | The stub payout rail | Attempt to settle through it | It refuses, so a local run cannot appear to move money | Ops | `LocalOnlyStubConfigurationTest#theStubRailRefusesToSettle` |
| TC-DATA-029 | Every enum backed by a migration constraint | Compare the two | All four vocabularies agree with their migrations | Schema | `VocabularyDriftTest` (5 cases) |
| TC-DATA-030 | An outbox batch claim | Run it | Bounded, ordered by schedule, and resolved rows are not re-claimed | REQ-622 | `JdbcMessageOutboxTest` (6 cases) |

---

## TC-INT — Vendor integrations

Four vendors, none of them under this team's control, and each with a failure mode that has to be
read correctly rather than guessed at. The single most expensive available mistake — reading a
TechExcel refusal as "already paid" — is guarded by a vocabulary that deliberately has no code for it.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-INT-001 | No session token held | Call the back office | It logs in lazily, and only once | Vendor contract | `TechExcelSessionTest#tokenLogsInLazilyAndOnce` |
| TC-INT-002 | Several callers needing a token at once | Refresh | The refreshes coalesce onto one login | Vendor contract | `TechExcelSessionTest#concurrentRefreshCoalescesOntoOneLogin` |
| TC-INT-003 | A caller arriving after a refresh | Read the token | It reuses the new token rather than logging in again | Vendor contract | `TechExcelSessionTest#alreadyRefreshedCallerReusesTheNewToken` |
| TC-INT-004 | A token invalidated before a refresh | Refresh | Coalescing does not hand back the invalidated token | Vendor contract | `TechExcelSessionTest#invalidateBeforeRefreshDefeatsCoalescing`, `#coalescingAssumesDistinctTokenValues` |
| TC-INT-005 | A login that returns no token | Handle it | Treated as an outage | Vendor contract | `TechExcelSessionTest#loginWithoutATokenIsAnOutage` |
| TC-INT-006 | An expired token mid-call | Make the call | The token refreshes once and the call proceeds | Vendor contract | `TechExcelGatewayTest#expiredTokenRefreshesOnce` |
| TC-INT-007 | A successful back-office call | Make it | It succeeds and emits exactly one vendor-call metric | Observability | `TechExcelGatewayTest#aSuccessfulCallSucceeds`, `#instructEmitsASingleVendorCallMetric` |
| TC-INT-008 | Calls with differing outcomes | Read the metric | The outcome reflects what actually happened | Observability | `TechExcelGatewayTest#metricOutcomeReflectsWhatHappened` |
| TC-INT-009 | A vendor error code | Handle it | Becomes a typed exception rather than a parsed string at the call site | LLD §7.7 | `TechExcelGatewayTest#vendorErrorCodeBecomesAnException` |
| TC-INT-010 | A 5xx from the back office | Handle it | Treated as an outage | LLD §4.4 | `TechExcelGatewayTest#serverErrorIsAnOutage` |
| TC-INT-011 | A payout rejection | Read it | Reported as ambiguous and said to be so, never inferred as duplication | OA-7 | `TechExcelGatewayTest#payoutRejectionIsAmbiguousAndSaysSo` |
| TC-INT-012 | The same code on a different endpoint | Read it | Not ambiguous there, so the ambiguity is not over-applied | OA-7 | `TechExcelGatewayTest#sameCodeElsewhereIsNotAmbiguous` |
| TC-INT-013 | An instruction held pending authorisation | Read the result | Not an outcome — a caller must not close a request the rail has not acted on | LLD §6.3 | `TechExcelGatewayTest#pendingAuthorisationIsNotAnOutcome`, `#rejectionBeatsPendingAuthorisation` |
| TC-INT-014 | An instruction | Read what was sent | The key and the amount, exactly | LLD §6.3a | `TechExcelGatewayTest#instructionCarriesTheKeyAndAmount` |
| TC-INT-015 | No status row for a key | Read the status | Empty, which means nothing was sent under it | OA-7 | `TechExcelGatewayTest#absentStatusRowIsEmpty` |
| TC-INT-016 | A gateway order creation | Make it | Succeeds and returns the order | Vendor contract | `JuspayGatewayTest#createOrderSucceeds` |
| TC-INT-017 | A gateway 4xx | Handle it | Classified as ours rather than theirs | A9c | `JuspayGatewayTest#badRequestIsOursNotTheirs` |
| TC-INT-018 | A gateway 503 | Handle it | Treated as an outage | A9a | `JuspayGatewayTest#serviceUnavailableIsAnOutage` |
| TC-INT-019 | A gateway status this system does not know | Map it | Unknown, never failed | A9b | `JuspayGatewayTest#unrecognisedStatusIsUnknownNotFailed`, `JuspayStatusMapperTest#anUnrecognisedStatusIsUnmapped` |
| TC-INT-020 | Only a charged status | Ask whether money may be credited | Only that one is creditable | A5 | `JuspayGatewayTest#onlyChargedIsCreditable` |
| TC-INT-021 | Every documented gateway status | Map each | All map to a defined outcome | A9a | `JuspayStatusMapperTest#everyKnownStatusMaps` |
| TC-INT-022 | A status in an odd case or with spacing | Map it | Matched, without a false drift alert | A9a | `JuspayStatusMapperTest#caseAndSpaceDoNotCauseFalseDrift` |
| TC-INT-023 | An absent status | Map it | Unknown | A9b | `JuspayStatusMapperTest#aMissingStatusIsUnknown` |
| TC-INT-024 | An in-progress status | Map it | Never failed | A9b | `JuspayStatusMapperTest#inProgressIsNeverFailed`, `#unknownIsHeldOpenRatherThanFailed` |
| TC-INT-025 | An HTTP call to any vendor | Make it | Headers and body are sent as specified, and a redirect is not followed | Security | `JsonHttpTest#headersAndBodyAreSent`, `#redirectIsNotFollowed` |
| TC-INT-026 | Every documented back-office error code | Map from the wire | Each maps to its own value | OA-7 | `qa.PlatformContractTest#everyDocumentedCodeMaps` |
| TC-INT-027 | An unfamiliar code | Map it | Stays unrecognised rather than being guessed at | OA-7 | `qa.PlatformContractTest#anUnrecognisedCodeStaysUnrecognised` |
| TC-INT-028 | Both contract spellings of each token error | Map them | Both accepted; neither assumed canonical | Vendor contract | `qa.PlatformContractTest#bothTokenSpellingsAreAccepted` |
| TC-INT-029 | A code with odd case or padding | Map it | Matched | Vendor contract | `qa.PlatformContractTest#aCodeIsMatchedCaseInsensitively` |
| TC-INT-030 | Each error code | Ask whether a retry could succeed | Only the token errors are session problems | Vendor contract | `qa.PlatformContractTest#onlyTokenErrorsAreSessionProblems` |
| TC-INT-031 | The back-office error vocabulary | Search it for a duplication code | None exists — no code may be read as "already paid" | OA-7 | `qa.PlatformContractTest#thereIsNoAlreadyPaidCode` |
| TC-INT-032 | A non-success response | Read it | Status and body are both carried, and transience is classified by status | LLD §4.4 | `JsonHttpTest#nonSuccessCarriesStatusAndBody`, `#transienceIsClassifiedByStatus` |
| TC-INT-033 | A success with an empty body | Read it | Treated as a failure rather than an empty result | LLD §4.4 | `JsonHttpTest#emptyBodyOnSuccessIsAFailure` |

---

## TC-NFR — Concurrency, resilience, performance

The properties that only fail under load or under a vendor outage. The concurrency cases are
executed against a real database; the performance targets are not measured anywhere, and are
recorded here as manual rather than quietly omitted.

| ID | Precondition | Action | Expected result | Traces | Evidence |
|---|---|---|---|---|---|
| TC-NFR-001 | Two withdrawal requests in the same instant | Submit both | Exactly one succeeds, with no window between check and write | LLD §7.1 | `SchemaConstraintTest#ruleW4RefusesASecondOpenRequest` |
| TC-NFR-002 | Two confirmations of one payment | Deliver both | Credited once; the second collides at the index | LLD §7.2 | `SchemaConstraintTest#ruleA6RefusesADuplicateGatewayReference` |
| TC-NFR-003 | Concurrent route-usage writes | Record them | All land; no update is lost to a read-modify-write | REQ-701 | `JdbcRouteCapLedgerTest#concurrentRecordsAllLand` |
| TC-NFR-004 | Concurrent reference generation | Generate | No two callers collide | C18 | `SequentialFmsReferenceGeneratorTest#concurrentCallersNeverCollide` |
| TC-NFR-005 | Concurrent token refreshes | Refresh | One login, shared | Vendor contract | `TechExcelSessionTest#concurrentRefreshCoalescesOntoOneLogin` |
| TC-NFR-006 | Two writers on one attempt or request | Write both | The stale write is refused rather than silently overwriting a money row | F-38 | `JdbcPayinAttemptRepositoryTest#aStaleWriteIsRefused`, `JdbcPayoutRequestRepositoryTest#aStaleWriteIsRefused` |
| TC-NFR-007 | A gateway timeout mid-transaction | Recover | The attempt row is committed and addressable | F-35 | `PayinDurabilityTest#aGatewayTimeoutLeavesACommittedAddressableRow` |
| TC-NFR-008 | A caller spending its per-account budget | Keep calling | Refused once the budget is spent | Security | `PerAccountRateLimitTest#aCallerIsRefusedOnceTheBudgetIsSpent` |
| TC-NFR-009 | Two accounts calling at once | Call | One account's usage does not affect the other | Security | `PerAccountRateLimitTest#oneAccountDoesNotAffectAnother` |
| TC-NFR-010 | Read and write budgets | Spend one | They are separate, and writes are treated as money movement by default | Security | `PerAccountRateLimitTest#theBudgetsAreSeparate`, `#aWriteIsMoneyMovementByDefault` |
| TC-NFR-011 | A re-dispatched request | Count the permits | A second permit is not spent | Security | `PerAccountRateLimitTest#aReDispatchDoesNotSpendASecondPermit` |
| TC-NFR-012 | No budget configured | Call | Nothing is blocked | Security | `PerAccountRateLimitTest#noBudgetBlocks` |
| TC-NFR-013 | The shipped budgets | Compare them | Ordered sensibly rather than accidentally | Security | `PerAccountRateLimitTest#theShippedBudgetsAreOrdered` |
| TC-NFR-014 | A vendor that stops answering | Keep calling | The breaker opens and the outage is reported as an outage | LLD §4.4 | `TechExcelGatewayTest#serverErrorIsAnOutage`, `JuspayGatewayTest#serviceUnavailableIsAnOutage` |
| TC-NFR-015 | A message the vendor never sends | Reconcile it | Detected by polling, because the vendor never calls back | LLD §7.9 | `DeliveryReconcilerTest#stuckMessageAlertsRatherThanResubmits` |
| TC-NFR-016 | A relay retry after a crash | Resubmit | The same request id replays and the service returns the original result | LLD §2.2 | `CommunicationClientTest#ambiguous500IsResubmittedWithTheSameKey` |
| TC-NFR-017 | An unbounded ledger window | Request it | Refused, because the vendor has no pagination | OA-6 | `TransactionQueryServiceTest#tooWideAPeriodIsRefused` |
| TC-NFR-018 | An outbox batch | Claim it | Bounded in size | REQ-622 | `JdbcMessageOutboxTest#theBatchIsBounded` |
| TC-NFR-019 | A trade and a withdrawal in the same instant | Perform both | Both succeed; the request holds nothing | W3, LLD §7.3 | BLOCKED — module absent |
| TC-NFR-020 | The run instructing while a trader cancels | Perform both | Whichever acquires the row first wins; the loser is refused with its reason | LLD §7.4 | BLOCKED — module absent |
| TC-NFR-021 | ~500 requests in one end-of-day run | Execute the run | All are decided within the run window | HLD §5 | BLOCKED — module absent |
| TC-NFR-022 | The hourly integrity check | Run it | A ledger whose entries do not sum to its balance is detected and no money leaves | HLD §16.4 | BLOCKED — module absent |
| TC-NFR-023 | A funds view request | Measure the time to the first balance figure | Within 1.5 s at p95 | NFR Performance | MANUAL |
| TC-NFR-024 | A confirmed payin | Measure the time until available margin reflects it | Within 30 s at p95 | NFR Performance | MANUAL |
| TC-NFR-025 | A payout status change | Measure the time until it is visible | Within 1 minute | NFR Performance | MANUAL |
| TC-NFR-026 | A month of production | Measure availability | 99.5% or better | NFR Reliability | MANUAL |
| TC-NFR-027 | Sustained ~21 rps with ~60 rps bursts | Load test the read path | Served without degradation | HLD §5 | MANUAL |
| TC-NFR-028 | The deployed JVM | Compare against the build target | The tests run on the JVM the service is deployed on | Build | MANUAL |
