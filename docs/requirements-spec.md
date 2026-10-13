# Requirements Spec: Engagement Template Update Tracking

| Field | Value |
|---|---|
| Status | Draft v0.3 |
| Source | Caseware Take-Home Refactoring Team Design Exercise |
| Owner | Lead Architect |
| Last updated | 2026-10-12 |

Each requirement has an ID so later specs, tests, and design decisions can trace back to it. Every item carries one tag:

- **[E]** — stated explicitly in the source document.
- **[I]** — strongly implied by the source document.
- **[D]** — follows from a decision recorded in §5. The decision's ID is noted inline.

---

## 1. Functional Requirements

### 1.1 Pending-update visibility
- **FR-1 [E]** — For each firm, users can see at a glance which engagement files have pending template updates.
- **FR-2 [E]** — The pending-update state stays current. It reflects new template publishes, engagement creation, and user decisions without manual refresh or user-triggered recomputation. "Current" is defined by NFR-P5.
- **FR-3 [I]** — A newly created engagement starts at the latest template version, so it has no pending update at creation time.
- **FR-4 [I]** — Once a user applies or declines an update, that update no longer shows as pending for that engagement.

### 1.2 Change summary
- **FR-5 [E]** — For each pending update, users get a summary of the inbound changes that is detailed enough to support an apply/decline decision.
- **FR-6 [E][D: A-5]** — The summary is a **deterministic, bulleted change list grouped by template section**, readable by non-technical users. The same pair of versions always produces the same summary. A raw JSON diff is an input to the summary, not an acceptable summary on its own.
- **FR-7 [E][D: A-2]** — When several versions accumulate before a decision, the summary is **cumulative**. It covers every change between the engagement's **last applied version** and the **latest published version**.
- **FR-7a [D: A-5]** — Every bullet in the summary links to the specific raw diff entry it was derived from. The link identifies the template ID, both versions, and the location of the change in the template data, so a user can verify any bullet against the source.

### 1.3 Decisions
- **FR-8 [E][D: A-1]** — Users apply or decline per engagement, and the decision is **all-or-nothing up to the latest published version**. Applying an intermediate version is not supported.
- **FR-9 [I]** — Each decision is recorded durably, including who made it, when, for which engagement, and the from-version and to-version it covered. This is needed to support the observability requirement for decision history.
- **FR-10 [I][D: A-1, A-2]** — After an **apply**, the engagement's last applied version becomes the version the decision targeted. After a **decline**, the last applied version does not change. The system records the version that was declined so the engagement is not shown as pending again until a newer version is published. When a newer version is published, the summary baseline is still the last applied version, so it includes changes from the declined versions as well.
- **FR-14 [D: A-1]** — A decision is bound to the target version the user was shown. If a newer version is published between viewing the summary and submitting the decision, the system rejects the decision and shows the user the updated summary instead of silently applying a version they did not review.

### 1.4 Update detection and impact
- **FR-11 [E]** — When a new template version is published, the system identifies every affected engagement across all firms using that product.
- **FR-12 [E][D: A-10]** — For each published update, the system reports how many engagements it affects. The count includes **actionable engagements only**. Archived and closed engagements are excluded.

### 1.5 Initial state
- **FR-15 [D: A-3, A-7, A-9]** — Before the feature is exposed to users, a one-time backfill establishes the template ID and last applied version of every existing engagement. The backfill runs asynchronously, is scheduled **per region** during that region's off-peak hours, and never causes a user-facing wait.
- **FR-17 [D: A-7]** — The live change hooks for engagement creation, apply, and decline are active **before** the backfill starts. Any state written by a hook takes precedence over state written by the backfill. The backfill must never overwrite a record with an older version than the one already recorded, even if it loaded that engagement before the hook fired.

### 1.6 Scope boundary
- **FR-13 [E]** — Applying updated template content to an engagement is **out of scope**. The system only surfaces the update and captures the decision.
- **FR-16 [D: A-6]** — Engagement and template lifecycle edge cases are **out of scope**. These include archived or closed engagements and templates that are withdrawn or superseded before anyone decides on them. The one exception is the exclusion rule for the affected-engagement count in FR-12.

---

## 2. Non-Functional Requirements

### 2.1 Scalability
- **NFR-S1 [E]** — The system supports hundreds of engagements per firm, across many products.
- **NFR-S2 [E]** — Firms of very different sizes share the same system, so large tenants must not degrade service for small ones.
- **NFR-S3 [E]** — Publish frequency is low, roughly once per week per product. However, a single publish fans out to every firm using that product at the same moment, so the system must absorb that burst.
- **NFR-S4 [I]** — The number of firms and products grows over time, and the design must not assume a fixed tenant count.
- **NFR-S5 [D: A-3, A-4]** — Neither the publish fan-out nor the backfill may overwhelm the per-firm databases or take engagement-loading capacity away from live user sessions. The work must be throttled to protect them.

### 2.2 Performance
- **NFR-P1 [E]** — Loading an engagement takes about one minute per file, and this is a **hard constraint**.
- **NFR-P2 [E]** — Opening a firm's engagement list must never depend on synchronously loading engagement files, regardless of how many engagements the firm has.
- **NFR-P3 [E]** — Learning about a newly published update must also never require a synchronous engagement load.
- **NFR-P4 [I]** — The change summary for a pending update must be available on demand without a user-facing wait. Template retrieval by ID and version, and the JSON diff, are both stated to be fast.
- **NFR-P5 [D: A-4, A-8]** — Pending-update state is **eventually consistent**, with this SLO: **P99 of affected engagements show the new pending update within 5 minutes of the template publish event.** Time is measured from the publish event to the moment the pending state is visible to the firm's users.

### 2.3 Security
- **NFR-SEC1 [E]** — Engagement content is client-confidential.
- **NFR-SEC2 [E]** — Firm-level isolation of pending-update data must be a **structural property**. Filtering in the application layer alone is not sufficient.
- **NFR-SEC3 [E]** — Only users authorized for a firm can view or act on that firm's pending updates.
- **NFR-SEC4 [E][D: A-9]** — New data must respect the data-residency requirements that already apply to the firm's engagement data. Backfill processing for a firm runs within that firm's residency region.
- **NFR-SEC5 [I]** — The template store is shared across all firms and currently holds no engagement data. Any engagement-linked state added there would mix tenant data into a shared store, so this is a constraint on where state can live.

### 2.4 Observability
- **NFR-O1 [E][D: A-10]** — Without relying on user reports, operators can see how many actionable engagements a newly published update affects.
- **NFR-O2 [E]** — Operators can verify that the pending-update check is actually running for **every** firm. This is coverage and liveness monitoring per tenant, not just aggregate health.
- **NFR-O3 [E]** — Operators can retrieve a firm's history of apply/decline decisions.
- **NFR-O4 [I]** — Problems such as stalled checks or missing firms must surface proactively through alerting rather than being found later.
- **NFR-O5 [D: A-3, A-4, A-8, A-9]** — Operators can see backfill progress per region and per firm. They can also see publish-to-visible latency, alerting when it breaches the SLO in NFR-P5.

---

## 3. Operating Constraints (Given Environment)

- **C-1** — The template database stores every version of every template, is shared across firms, and is written to on publish and read from on engagement creation.
- **C-2** — Templates are zip archives of structured JSON, and a fast, reliable JSON diff between two versions of the same template already exists.
- **C-3** — Engagements live in databases specific to each customer. They store the template ID and version used at creation, but they are not directly queryable. Reading them requires the rehydration load from NFR-P1.
- **C-4** — A single engagement management system owns loading, creation, and processing of apply/decline decisions.
- **C-5** — Hooks or events may be added to any action in either the template storage system or the engagement management system.
- **C-6** — The team works on AWS, so solutions should favor it, though any stack is allowed.
- **C-7** — Deliverables are a 1–3 page design doc covering architecture, implementation plan, testing, observability, and failure modes. An optional implementation slice and AI-session history are welcome. The time budget is 2–3 hours.

---

## 4. Open Questions

Resolve each one as either a stated assumption (record it in §5) or a clarifying question for the interviewers.

| ID | Question | Affects | Resolution |
|---|---|---|---|
| OQ-1 | Partial decisions on accumulated updates. | FR-7, FR-8, FR-10 | Resolved → A-1 |
| OQ-2 | Decline semantics and summary baseline. | FR-7, FR-10 | Resolved → A-2 |
| OQ-3 | Establishing the version of engagements that already exist. | FR-1, FR-15, NFR-P1 | Resolved → A-3 |
| OQ-4 | Definition of "up to date." | FR-2, NFR-P5 | Resolved → A-4 |
| OQ-5 | Summary granularity and trust. | FR-5, FR-6, FR-7a | Resolved → A-5 |
| OQ-6 | Lifecycle edge cases. | FR-16 | Resolved → A-6 |
| OQ-7 | Backfill cutover gap. | FR-15, FR-17 | Resolved → A-7 |
| OQ-8 | Staleness target. | NFR-P5, NFR-O5 | Resolved → A-8 |
| OQ-9 | Off-peak across regions. | FR-15, NFR-S5, NFR-SEC4 | Resolved → A-9 |
| OQ-10 | Affected count under the lifecycle exclusion. | FR-12, NFR-O1, FR-16 | Resolved → A-10 |
| OQ-11 | **Source of archived status.** FR-12 excludes archived and closed engagements, but the only engagement events in scope are create, apply, and decline. How does the system learn that an engagement was archived or closed? Is an archive/close hook in scope, or is status captured only once by the backfill? | FR-12, FR-16, FR-17 | _Open_ |
| OQ-12 | **SLO unit and population.** Is the P99 measured across all affected engagements in a publish, or across firms? A P99 over all engagements could hide one large firm that is entirely late, which conflicts with NFR-S2. | NFR-P5, NFR-S2, NFR-O5 | _Open_ |

---

## 5. Assumptions and Decisions

- **A-1 (closes OQ-1)** — Decisions are all-or-nothing up to the latest published version. Applying an intermediate version is not supported, to avoid the complexity of a multi-version state machine.
- **A-2 (closes OQ-2)** — Versions are cumulative relative to the engagement's current state. The summary baseline is always the last applied version. A decline does not move the baseline.
- **A-3 (closes OQ-3)** — The one-minute load cannot be absorbed at runtime. A one-time asynchronous backfill populates the initial state during off-peak hours.
- **A-4 (closes OQ-4)** — Eventual consistency is acceptable. A staleness of a few minutes after a publish is fine, and it allows asynchronous fan-out without overwhelming the databases.
- **A-5 (closes OQ-5)** — The summary is a deterministic, bulleted change list per section. Every bullet carries a traceability link to the raw diff data, to maintain professional trust.
- **A-6 (closes OQ-6)** — Lifecycle edge cases are outside the provided requirements and are explicitly excluded from the design.
- **A-7 (closes OQ-7)** — Order of operations: live hooks are deployed and active first, and the backfill runs second.
- **A-8 (closes OQ-8)** — SLO: P99 of pending-update state is updated within 5 minutes of a template publish event.
- **A-9 (closes OQ-9)** — Because tenant data is subject to data-residency requirements, backfill jobs are scheduled per region.
- **A-10 (closes OQ-10)** — Archived and closed engagements are excluded from the affected-engagements metric. The count reflects actionable engagements only.

---

## 6. Change Log

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-12 | Initial extraction of FRs, NFRs, constraints, and open questions from the source document. |
| 0.2 | 2026-10-12 | Resolved OQ-1 to OQ-6 as A-1 to A-6. Refined FR-6, FR-7, FR-8 and FR-10. Added FR-7a, FR-14, FR-15, FR-16, NFR-S5, NFR-P5 and NFR-O5. Added the [D] tag. Opened OQ-7 to OQ-10. |
| 0.3 | 2026-10-12 | Resolved OQ-7 to OQ-10 as A-7 to A-10. Added FR-17 (hook state takes precedence over the backfill). Quantified NFR-P5 with the SLO. Updated FR-12, FR-15, FR-16, NFR-SEC4, NFR-O1 and NFR-O5. Opened OQ-11 and OQ-12. |
