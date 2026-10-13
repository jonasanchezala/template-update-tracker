# engagement-update-core

The core rules of the Engagement Update Service: how events change an engagement's record, how the
backfill writes without overwriting, how "pending" is decided, and how impact is counted. Pure Java,
no Spring, no AWS. Queues, the database and the HTTP API appear only as interfaces, with in-memory
implementations for tests. Built from `docs/implementation-spec.md` (Draft v0.3).

## Run

```
./gradlew test          # Windows: gradlew.bat test
```

Java 25 or newer. The code compiles with `--release 25`; the wrapper pins Gradle 9.8.0. The first run
downloads JUnit 5 and AssertJ.

## Layout

```
src/main/java/com/example/engagementupdate/
  domain/     value types, EngagementRecord, DecisionRecord, EngagementView, PendingRule (R7)
  events/     EngagementEvent and its four records (Created, Applied, Declined, Archived),
              TemplateVersionPublished, BackfillSnapshot
  handlers/   the worker and API interfaces (spec section 7) and their Default* implementations
  store/      EngagementStore, LatestVersionStore and the in-memory versions
src/test/java/com/example/engagementupdate/
  one test class per area, plus World, a small helper that wires the stores to the handlers
```

`EngagementEvent` is not sealed, so the `switch` expressions in `DefaultEngagementEventHandler` end
in a `default` branch that throws. A new event type would reach it at runtime, not at compile time.

## Which test proves which rule

| Rule | What it says | Tests |
|---|---|---|
| R1 | Apply only if `seq` > `lastSeq` | AC-5, AC-6, `r2_duplicateCreate`, `olderEventArrivingLateIsLost`, `InMemoryEngagementStoreTest` |
| R1a | Unknown engagement: create, or ignore an archive | AC-13, `r1a_applyCreatesRecord`, `r1a_declineCreatesRecord`, `archiveBeforeCreateIsLost` |
| R2 | Create | AC-1, `r2_duplicateCreate` |
| R3 | Apply | AC-5, `r3_applyKeepsDeclined` |
| R4 | Decline moves the baseline, not the applied version | AC-3, `r4_declineKeepsMax` |
| R4a | Archive changes only the status | `r4a_archiveOnlyStatus` |
| R5 | Latest version is a running max | AC-9, `r5_raise` |
| R6 | Backfill creates only if absent | AC-7, AC-8, `r6_seededRecordShape`, `r6_seedTwice` |
| R7 | Pending rule | `PendingRuleTest` (12-row table), AC-1 to AC-4, `r7_unknownLatest` |
| R8 | Impact count, zero-count firms included | AC-10, AC-11, `r8_countsBehindOnly`, `r8_declinedNotCounted`, `r8_noEngagements` |
| R9 | Decision history | AC-3, AC-5, `r9_historyOrder` |
| R10 | Tenant scoping | AC-12 (the interface shape itself is the rest of it) |

Acceptance criteria AC-1 to AC-13 are the `@DisplayName("AC-n: ...")` tests in
`EngagementEventHandlerTest`, `TemplateVersionAndBackfillTest` and `QueryAndImpactTest`.
`EngagementEventHandlerTest` also has `invalidValuesAreRejected`, for the value types' checks.

## What is not tested

Neither a property-based test nor a concurrency test is in this slice (spec §9 lists them as left out).

- **No property-based test.** There is no check that random orders and duplicates of events end in the
  state a simple oracle predicts. The ordering and duplicate rules (R1, R3, R4, R9) are covered by
  single examples only: AC-5, AC-6 and the `r*` tests above.
- **No concurrency test for the backfill race (FR-17).** AC-7 and AC-8 prove the seed rule on one
  thread. What makes it safe under concurrency is the store: `putIfAbsent` and `replaceIfSeqNewer` are
  atomic per engagement, and the handler re-reads and decides again when its write loses a race. That
  retry loop in `DefaultEngagementEventHandler` has no test.

## Things the spec leaves open, and what this code does

These follow from the rules as written. Items 1 and 2 are pinned by a test, so a change is a
conscious one.

1. **A late older event is dropped, not merged (R1).** If an apply with seq 3 arrives before a
   decline with seq 2, the decline is lost. Safe only if the queue delivers one engagement's events in
   order. `olderEventArrivingLateIsLost`.
2. **An archive for an unknown engagement leaves no trace (R1a).** If it arrives before the create,
   it is lost until redelivered. In production the backfill snapshot carries `status`, which recovers
   it. `archiveBeforeCreateIsLost`.
3. **A `Created` with a newer seq than an existing record replaces it.** R2 says "R1 decides", so a
   `Created` at seq 1 on top of a backfilled record (seq 0) overwrites the seeded version and clears
   any declined version. Normal ordering makes this a no-op in practice; worth a decision if not.
4. **The decision record is written after the state change, not in the same transaction.** The spec's
   `EngagementStore` has separate `replaceIfSeqNewer` and `appendDecision`. A crash between them loses
   one history row. The real database should write both in one transaction.
5. **AC-5 reads "E at v2, seq 3" and an apply at seq 3.** An apply at the record's own seq would be
   stale, so the test creates E at seq 2 and applies at seq 3.

## Out of scope

SQS/SNS, DynamoDB and IAM isolation, the HTTP API and authorizer, the Template Change Service, the
409 reviewed-version check, drift detection, feature flags and metrics.
