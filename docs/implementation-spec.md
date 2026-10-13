# Implementation Spec: Engagement Update Core (Part 2 slice)

| Field | Value |
|---|---|
| Status | Draft v0.3 |
| Implements | Part of the Engagement Update Service, from `design-doc.md` v7 |
| Requirements baseline | `requirements-spec.md` v0.3 |

---

## 1. Goal and why this slice

The brief asks for "one small slice," focused on **interfaces, contracts, and correctness**, with tests as a plus, and explicitly "not a full application build."

This slice implements the **core rules of the Engagement Update Service**: how events change an engagement's record, how the backfill writes without overwriting, how pending is decided, and how impact is counted. These are the rules where a bug silently shows a wrong flag to an auditor, so they're the most valuable thing to prove with tests. They also need no AWS infrastructure to run.

Everything around the core, including queues, the database, the HTTP API, authentication, and deployment, is represented by **interfaces only**, with in-memory implementations for tests.

## 2. Scope

**In scope**

- The domain model: engagement records, latest template versions, decision history.
- Event contracts for every event the Engagement Update Service consumes.
- The event-handling rules, including the sequence-number rule.
- The backfill seeding rule: create only if absent.
- The pending rule and the firm's engagement list.
- The impact count per firm.
- A storage interface whose operations mirror the conditional writes the real database will use, plus an in-memory implementation.
- Unit tests for every rule and acceptance criterion, and a table test for the pending rule.

**Out of scope**, with where it lives in the design

- SQS, SNS, and the outbox in upstream systems. These are the transport (design §1, Events).
- DynamoDB and the IAM per-firm isolation (design §1, Structural tenant isolation). The slice reflects isolation in its interface shape only (rule R10).
- The HTTP API, API Gateway, and the authorizer.
- The Template Change Service and summaries.
- The 409 reviewed-version check, which lives in the engagement management system (design §1, Decisions).
- Drift detection on load events, feature flags, and metrics.

## 3. Traceability

| Slice rule | Design or requirement it implements |
|---|---|
| R1 Sequence-number rule | Design §1 Engagement Update Service-worker; spec FR-2, idempotent delivery |
| R2 to R4 Apply, decline, archive | FR-8, FR-10, A-1, A-2, A-10 |
| R5 Latest version | Design §1 Engagement Update Service-worker on publish; NFR-P5 |
| R6 Backfill seed | FR-15, FR-17, A-7 |
| R7 Pending rule | FR-1, FR-3, FR-4, FR-7, FR-10, NFR-P2 |
| R8 Impact count | FR-11, FR-12, NFR-O1, NFR-O2, A-10 |
| R9 Decision history | FR-9, NFR-O3 |
| R10 Tenant scoping in interfaces | NFR-SEC2, NFR-SEC3 |

## 4. Domain model

```
FirmId, EngagementId, TemplateId   value types (non-blank strings)
Version                            positive integer, comparable

EngagementRecord
  firmId, engagementId, templateId
  appliedVersion          Version
  declinedThroughVersion  Version or none
  status                  ACTIVE | ARCHIVED
  lastSeq                 long          // highest sequence number applied
  source                  HOOK | BACKFILL

DecisionRecord
  firmId, engagementId, templateId
  outcome                 APPLIED | DECLINED
  fromVersion, toVersion  Version
  actorId, occurredAt

EngagementView            // one row of a firm's engagement list
  engagementId, templateId, appliedVersion, declinedThroughVersion
  latestVersion           Version or unknown
  pending                 boolean
```

## 5. Event contracts

Every engagement event carries `firmId`, `engagementId`, `seq` (a positive long that only ever increases for one engagement), `actorId`, and `occurredAt`.

| Event | Extra fields |
|---|---|
| `EngagementCreated` | `templateId`, `version` |
| `UpdateApplied` | `templateId`, `fromVersion`, `toVersion` |
| `UpdateDeclined` | `templateId`, `fromVersion`, `toVersion` (the version declined through) |
| `EngagementArchived` | none |

The template event carries no tenant data and no sequence number.

| Event | Fields |
|---|---|
| `TemplateVersionPublished` | `templateId`, `version`, `publishedAt` |

The backfill input is a snapshot read from one engagement load.

| Input | Fields |
|---|---|
| `BackfillSnapshot` | `firmId`, `engagementId`, `templateId`, `version`, `status` |

## 6. Rules

Each rule is written so that it can be tested on its own.

- **R1. Sequence-number rule.** An engagement event is applied only if its `seq` is greater than the record's `lastSeq`. Otherwise it changes nothing and returns `IGNORED_STALE`. Applying the same event twice, or an older event after a newer one, never changes state.
- **R1a. Unknown engagement.**
  - `EngagementCreated` creates the record.
  - `UpdateApplied` or `UpdateDeclined` for an unknown engagement also creates the record from the event's own fields, because a decision can arrive before the backfill reaches that engagement.
  - `EngagementArchived` for an unknown engagement changes nothing and returns `IGNORED_UNKNOWN`, because the event carries no template.
- **R2. Create.** A new record gets `appliedVersion = version`, no declined version, status `ACTIVE`, `lastSeq = seq`, and source `HOOK`. If a record already exists, R1 decides.
- **R3. Apply.** Sets `appliedVersion = toVersion`, leaves `declinedThroughVersion` unchanged, and appends an `APPLIED` decision record.
- **R4. Decline.** Sets `declinedThroughVersion = max(current, toVersion)`, leaves `appliedVersion` unchanged (A-2), and appends a `DECLINED` decision record.
- **R4a. Archive.** Sets status to `ARCHIVED`. Nothing else changes.
- **R5. Latest version.** `TemplateVersionPublished` sets the template's latest version to `max(current, version)`. A lower or equal version changes nothing.
- **R6. Backfill seed.** Creates a record from the snapshot **only if no record exists** for that firm and engagement. It returns `CREATED` or `SKIPPED`, never modifies an existing record, and never adds a decision record. A created record gets `lastSeq = 0` and source `BACKFILL`, so any later hook event (seq ≥ 1) applies on top of it.
- **R7. Pending rule.** An engagement is pending exactly when all three hold:
  - its status is `ACTIVE`;
  - its template's latest version is known;
  - `latestVersion > max(appliedVersion, declinedThroughVersion or 0)`.
- **R8. Impact count.** For a template and a version V, return, for **every firm that has at least one engagement on that template**, the number of `ACTIVE` engagements where `V > max(appliedVersion, declinedThroughVersion or 0)`. Firms with zero affected engagements still appear, with 0, so coverage can be checked (NFR-O2). Archived engagements are never counted (A-10).
- **R9. Decision history.** Decision records are appended only when R3 or R4 actually changes state. Duplicates and stale events add nothing. History is returned per firm, oldest first.
- **R10. Tenant scoping.** Every read that serves users takes a `FirmId` and can only return that firm's data. Cross-firm operations (event handling, seeding, impact counting) live on separate worker-side interfaces that the user-facing query interface does not expose.

## 7. Interfaces

Signatures are shown in Java. Names may change during implementation; behavior may not.

```java
// Engagement Update Service-worker side: cross-tenant, fed by the queue
interface EngagementEventHandler {
    HandleOutcome handle(EngagementEvent event);          // APPLIED | IGNORED_STALE | IGNORED_UNKNOWN
}
interface TemplateVersionHandler {
    boolean handle(TemplateVersionPublished event);        // true if the latest version changed
}
interface BackfillSeeder {
    SeedOutcome seed(BackfillSnapshot snapshot);           // CREATED | SKIPPED
}
interface ImpactCounter {
    Map<FirmId, Long> count(TemplateId templateId, Version version);
}

// Engagement Update Service-api side: always scoped to one firm (R10)
interface FirmEngagementQuery {
    List<EngagementView> engagementsFor(FirmId firmId);
    List<DecisionRecord> decisionHistoryFor(FirmId firmId);
}

// Storage: operations mirror the database's conditional writes
interface EngagementStore {
    Optional<EngagementRecord> find(FirmId firmId, EngagementId engagementId);
    boolean putIfAbsent(EngagementRecord record);                  // R6, R1a
    boolean replaceIfSeqNewer(EngagementRecord updated);           // R1: succeeds only if updated.lastSeq > stored.lastSeq
    List<EngagementRecord> findByFirm(FirmId firmId);
    List<EngagementRecord> findByTemplate(TemplateId templateId);  // Engagement Update Service-worker only
    void appendDecision(DecisionRecord decision);
    List<DecisionRecord> decisionsByFirm(FirmId firmId);
}
interface LatestVersionStore {
    Optional<Version> latest(TemplateId templateId);
    boolean raiseTo(TemplateId templateId, Version version);       // R5
}
```

The in-memory store must make `putIfAbsent` and `replaceIfSeqNewer` **atomic per engagement**, as the real database's conditional writes are. This is what keeps the backfill from beating a hook event under concurrency (FR-17).

## 8. Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | No record for E | `EngagementCreated(E, T, v5, seq 1)` | E is recorded at applied v5, active, not pending while latest(T) ≤ 5 |
| AC-2 | E at applied v2, latest(T) = 4 | View the firm's list | E is pending |
| AC-3 | E at v2, latest 4 | `UpdateDeclined(E, 2→4, seq 2)` | Declined-through is 4, applied stays 2, E is not pending, one `DECLINED` decision recorded |
| AC-4 | AC-3, then latest becomes 5 | View the list | E is pending again, and its summary range is v2 → v5 |
| AC-5 | E at v2, seq 3 | `UpdateApplied(E, 2→5, seq 3)` delivered twice | Applied is 5, exactly one `APPLIED` decision recorded |
| AC-6 | E at seq 4 | An event with seq 3 arrives | Nothing changes, outcome `IGNORED_STALE` |
| AC-7 | E recorded by a hook at v3 | Backfill seeds E at v2 | Outcome `SKIPPED`, E stays at v3 |
| AC-8 | No record for E | Backfill seeds E at v2, then `UpdateApplied(E, 2→3, seq 1)` | E ends at v3 |
| AC-9 | Latest(T) = 5 | `TemplateVersionPublished(T, 4)` | Latest stays 5 |
| AC-10 | E pending, then archived | View the list, and count impact | E is not pending and is not counted |
| AC-11 | Firm A has engagements on T, none affected | Count impact for T, v5 | Firm A appears with 0 |
| AC-12 | Firms A and B both have data | Query for firm A | Only firm A's engagements and decisions are returned |
| AC-13 | No record for E | `EngagementArchived(E)` | Outcome `IGNORED_UNKNOWN`, no record created |

## 9. Test plan

- **Unit tests:** one test per acceptance criterion, AC-1 to AC-13.
- **Pending table test.** A table of (applied, declined-through, latest, status) cases with the expected pending result, including the edges: equal versions, no declined version, unknown latest, and archived.

**Not in this slice.** A property-based test (random order and duplicates against a hand-written oracle) and a concurrency test for the backfill race are left out. AC-7 and AC-8 prove the backfill rule on a single thread, and the store's atomic conditional writes are what make it safe under concurrency. The design's testing strategy (design §3) still calls for both against the real service.

## 10. Technology

- **Java 25** with **Gradle**, matching the design's Spring Boot stack, but **without Spring** in this slice. The slice is pure domain logic, and a framework would add setup without proving anything.
- **JUnit 5** and **AssertJ** for tests.
- Records for events and value types. The event interface is not sealed, so the handler's `switch` expressions end in a `default` that throws.

Suggested layout:

```
engagement-update-core/
  src/main/java/.../domain/      value types, records, rules
  src/main/java/.../events/      event contracts
  src/main/java/.../handlers/    event handler, version handler, seeder, impact counter, query
  src/main/java/.../store/       store interfaces + in-memory implementations
  src/test/java/...              unit and table tests
  README.md                      how to run, and which spec rule each test proves
```

## 11. Implementation plan

The slice is built in eight steps. Each step writes its acceptance tests **first**, from §8, then the code that makes them pass, so every step ends green.

| Step | What | Rules and tests |
|---|---|---|
| 1 | **Project skeleton.** Gradle, Java 25, JUnit 5, AssertJ. One empty test passes. | — |
| 2 | **Domain model and event contracts.** Value types, `EngagementRecord`, `DecisionRecord`, `EngagementView`, the `EngagementEvent` family, `TemplateVersionPublished`, `BackfillSnapshot`, and the outcome enums. | §4, §5 |
| 3 | **Store interfaces and in-memory stores.** `putIfAbsent` and `replaceIfSeqNewer` are atomic per engagement, using `ConcurrentHashMap.compute`, so they behave like DynamoDB's conditional writes. | §7 |
| 4 | **Pending rule.** A pure function, tested first with the pending table test. | R7; pending table test |
| 5 | **Engagement event handler.** Create, apply, decline, archive, the sequence-number check, the unknown-engagement cases, and decision history. | R1–R4a, R9; AC-1 to AC-6, AC-13 |
| 6 | **Template version handler and backfill seeder.** | R5, R6; AC-7 to AC-9 |
| 7 | **Firm query and impact counter.** User-facing reads take a firm; the impact count includes firms with zero. | R8, R10; AC-10 to AC-12 |
| 8 | **README.** It maps each test to its rule and lists what is not tested. | §9, §12 |

**If the slice has to be cut.** Steps 1 to 7 are the minimum deliverable: every rule implemented, with every acceptance test passing. After that, trim the README to a short rule-to-test table.

Never cut: an acceptance test.

## 12. Definition of done

- Every rule R1 to R10 is implemented, and every acceptance criterion AC-1 to AC-13 has a passing test.
- `./gradlew test` passes from a clean checkout.
- The README maps each test to the rule it proves, and lists what is not tested.
- Nothing outside §2 "In scope" is built.

## 13. Working with AI on this slice

This spec is the contract. AI tools may generate code and tests from it, but:

- **Every rule in §6 is checked by a human** against the design before code exists. Rules are where a plausible-looking mistake does real damage. Examples: letting a decline move the baseline, or letting the backfill overwrite an existing record.
- **Tests are written from the acceptance criteria, not from the generated code**, so a test can't simply confirm whatever the code happens to do.
