---
title: "Document Management System (DMS) — Internal Service"
status: draft
version: "1.1"
scope: backend
created_at: "2026-07-30"
updated_at: "2026-07-30"
---

# Product Requirements Document — Document Management System (DMS)

> **Tech-Agnostic Rule:** This document describes WHAT the system must do and WHY — never HOW it is built. No frameworks, databases, cloud providers, or architecture details appear here.

---

## Validation Checklist

### CRITICAL GATES

- [x] All required sections are complete
- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Domain Invariants Gate has been run — all 8 table-stakes items resolved (features/NFRs or explicit Out-of-Scope entries)
- [x] No Must-Have feature depends on an unresolved Open Question
- [x] Problem statement is specific and measurable
- [x] Every feature has testable acceptance criteria (EARS format)
- [x] Every primary user flow has a happy path, at least one alternate branch, and at least one error path
- [x] No contradictions between sections
- [x] No technology, architecture, or implementation detail anywhere

### QUALITY CHECKS

- [x] Problem is validated by direct team observation (internal pain point)
- [x] Context → Problem → Solution flow is coherent
- [x] Every persona has at least one user flow
- [x] MVP Scope, Future Scope, and Out of Scope are mutually exclusive
- [x] Every NFR number has a stated basis or `[PROPOSED: pending eng confirmation]`
- [x] Every metric has corresponding tracking events evidenced by a mapping table
- [x] No feature redundancy
- [x] Engineering Digest matches the detailed sections
- [x] A new team member could understand this PRD without asking what a term means

---

## Engineering Digest

> Written last; read first. No persuasive prose — features, numbers, and blockers only.

**Features at a glance:**

| # | Feature | Priority |
|---|---------|----------|
| F-01 | Document Ingestion & Storage via API — receive and durably persist document binary and structured metadata (user ID, document ID, type, tags, timestamps) | Must Have |
| F-02 | Retrieve Document by ID — return document binary and metadata for a given document ID; handles missing binary edge cases | Must Have |
| F-03 | Retrieve Documents by User ID — return paginated, filterable list of all documents belonging to a user | Must Have |
| F-04 | Shareable Link Generation — produce a time-limited, configurable-expiry URL for a document | Must Have |
| F-05 | Shareable Link Resolution — validate and serve the document binary when a link is accessed | Must Have |
| F-06 | Metadata Search — filter documents by type, date range, and tags | Must Have |
| F-07 | Hard Delete / Right-to-Erasure — permanently delete a document and all associated share links on demand | Must Have (GDPR Article 17) |
| F-08 | API Key Authentication & Authorization — service-to-service authentication with distinct scopes (`read`, `write`, `erasure`, `audit-read`) | Must Have |
| F-09 | Immutable Audit Logging — immutable log of every document access, ingestion, link generation, link access, and erasure event with query endpoint | Must Have (HIPAA/GDPR invariant) |
| F-10 | Data Retention Policy Enforcement — apply and enforce configurable retention periods per document or per document type | Should Have (Phase 2) |
| F-11 | Bulk Document Retrieval — retrieve multiple documents' binaries in a single call | Could Have (Phase 2) |

**Hard numbers:**

| Metric | Target | Basis |
|--------|--------|-------|
| Metadata retrieval latency (P95) | < 500 ms | Stated by product owner |
| Document binary download start (P95) | < 2 s | [PROPOSED: pending eng confirmation] |
| System availability | 99.9% | [PROPOSED: pending eng confirmation] |
| Peak throughput | ≥ 500 requests/second | Stated by product owner |
| Maximum document size | 10 MB | Stated by product owner |
| Maximum user ID length | 128 characters | Standard string limit |
| Maximum tags per document | 20 key-value pairs | Operational metadata constraint |
| Default page size / Max page size | 20 / 100 items | Standard pagination limit |
| Shareable link minimum expiry | 1 hour | [PROPOSED: pending eng confirmation] |
| Shareable link maximum expiry | 30 days | [PROPOSED: pending eng confirmation] |
| Audit log retention | 6 years | HIPAA minimum requirement |

**Must-Haves with unresolved dependencies:** None.

**Estimation Blockers:** See full section below. Two blockers remain open: exact retention period per document type, and breach notification SLA.

---

## Executive Summary

The Document Management System (DMS) is an internal, backend-only service that acts as the authoritative store for documents produced or consumed across the organization's service ecosystem. It receives documents from an upstream document-generating service, stores them with structured metadata, and exposes a set of APIs that allow authorized downstream services and internal consumers to retrieve, search, and share those documents. The system must satisfy both GDPR and HIPAA compliance obligations, including full audit trails, encryption guarantees, right-to-erasure, and configurable data retention. The DMS is not a user-facing product; it is a platform component accessed exclusively through authenticated, service-to-service API calls.

---

## Problem Statement

### Context

Multiple internal services generate and consume documents (PDFs, images) as part of their core workflows. Today, each service manages its own ad-hoc document storage, resulting in fragmented metadata, no shared retrieval interface, no centralized audit trail, and no governed share-link mechanism. When a downstream service needs a document that was produced upstream, there is no reliable, authorized channel to retrieve it.

### Problem

The absence of a centralized document store creates three compounding problems:
1. **Fragmentation:** Documents are scattered across multiple services with incompatible storage conventions, making cross-service retrieval manual and error-prone.
2. **No audit trail:** No service has an authoritative log of who accessed which document and when — a direct compliance gap under both GDPR and HIPAA.
3. **No governed sharing:** Share links are generated ad-hoc (or not at all), with no expiry, no access logging, and no revocation mechanism.

These gaps have been directly observed through internal service integration work and flagged during a GDPR/HIPAA readiness review.

### Why Now

A GDPR/HIPAA compliance audit has surfaced the document-handling gap as a critical finding requiring remediation. Additionally, two new internal services are being built that depend on a centralized document retrieval API. Deferring this work blocks both services and worsens the compliance posture with each additional document generated.

---

## Goals

### Product Goals

- Provide a single, authoritative source of truth for documents across the service ecosystem.
- Guarantee that every document access and share event is traceable, timestamped, and immutable.
- Enable any authorized downstream service to retrieve or search documents without knowing which upstream service produced them.
- Ensure documents can be permanently erased on demand without leaving orphaned share links or cached copies.
- Support configurable document lifecycle so that retention policies can be enforced per document type.

### Non-Goals

- The DMS does not render, transform, or perform OCR on documents — it stores and retrieves them as opaque binaries.
- The DMS does not provide a user-facing UI — all interactions are service-to-service API calls.
- The DMS does not manage document workflows (approvals, signatures, or status transitions) — it is a storage and retrieval system.
- The DMS does not generate documents — it only receives, stores, and serves them.

---

## Stakeholders

| Stakeholder | Role | Interest / Stake | Approval Needed? |
|---|---|---|---|
| Platform Engineering Team | Owner / Builder | Owns the DMS API contract and SLA | Yes |
| Upstream Document Service | Integration Partner | Pushes documents to DMS; needs a stable ingestion API | Yes (API contract) |
| Downstream Consumer Services | Integration Partner | Queries/retrieves documents via DMS; needs a stable retrieval API | Yes (API contract) |
| Compliance / Legal Team | Compliance Owner | Ensures GDPR Article 17 and HIPAA audit-trail requirements are met | Yes |
| Security Team | Security Reviewer | Reviews encryption, authentication, and access control requirements | Yes |

---

## User Personas

### Primary Persona: Upstream Service (Document Producer)

- **Type:** Internal microservice that generates documents (PDFs, images) as a byproduct of its workflow
- **Goals:** Push a newly produced document to a central store via a single, consistent API call, with confidence it will be retrievable later by any authorized consumer
- **Pain Points:** Currently must manage its own storage, generate its own retrieval mechanisms, and has no way to hand off documents to downstream services cleanly
- **Formal User Stories:**
  - As an upstream service, I want to submit a document along with its metadata (user ID, type) in a single API call, so that the document is durably stored and immediately retrievable by authorized consumers.
  - As an upstream service, I want to receive a unique document ID in response to a successful ingestion call, so that I can reference the document in my own system.

### Secondary Persona: Downstream Consumer Service (Document Consumer)

- **Type:** Internal microservice that needs to retrieve or query documents it did not produce
- **Goals:** Retrieve a specific document by ID, or query all documents belonging to a user, using a stable and authenticated API
- **Pain Points:** Has no current way to discover or retrieve documents produced by a sibling service; must rely on manual hand-offs or duplicate storage
- **Formal User Stories:**
  - As a downstream service, I want to retrieve a document binary and its metadata by document ID, so that I can render or process it in my own workflow.
  - As a downstream service, I want to list all documents belonging to a given user ID, filtered by type and date range, so that I can display or process the relevant subset.
  - As a downstream service, I want to generate a time-limited shareable link for a document, so that an end-user or external partner can access the document directly without going through the service's own infrastructure.

### Tertiary Persona: Compliance Auditor

- **Type:** Internal compliance or legal team member who reviews audit logs in response to a data access request or incident
- **Goals:** Obtain a complete, tamper-evident record of all access events for a given document or user
- **Pain Points:** Today, no such record exists in a queryable form
- **Formal User Stories:**
  - As a compliance auditor, I want to retrieve a complete audit log for a given document ID or user ID, so that I can produce an access report for a regulatory inquiry.
  - As a compliance auditor, I want to confirm that a document has been permanently erased in response to a right-to-erasure request, so that I can certify GDPR compliance to the requesting party.

---

## User Flows

### Flow 1: Document Ingestion (Happy Path & Error Paths)

- **Persona:** Upstream Service
- **Trigger:** Upstream service has produced a new document and needs to store it in the DMS
- **Preconditions:** Upstream service holds a valid API key with `write` scope; document is ≤ 10 MB; file type is PDF, JPG, PNG, or TIFF; user ID is non-empty and ≤ 128 characters

**Main Flow (Happy Path)**
1. Upstream service sends a document submission request with the document binary, user ID, document type, and any tags → System accepts the request and validates the API key and `write` scope.
2. System validates the document: checks file type (`PDF`, `JPG`, `PNG`, `TIFF`), user ID format (non-empty, ≤ 128 chars), tag limits (≤ 20 pairs, key ≤ 64 chars, value ≤ 256 chars), and file size (≤ 10 MB) → System confirms validation passes.
3. System persists the document binary and its metadata → System returns a unique document ID and an `ingested_at` timestamp to the caller.

**Alternate Flows / Branches**
- **Branch A — File type is not in the accepted list:**
  1. System rejects the request with a clear error indicating the unsupported file type → Upstream service receives a 422 Unprocessable Entity response with the rejection reason.
- **Branch B — File size exceeds 10 MB:**
  1. System rejects the request with a clear error indicating the file size limit → Upstream service receives a 413 Payload Too Large response.
- **Branch C — Validation failure on User ID or Tags:**
  1. System rejects the request with 422 Unprocessable Entity and details on invalid user ID or tag limits exceeded.

**Error / Exception Flows**
- **If API key is missing or invalid** → System returns 401 Unauthorized; no document is stored; event is logged.
- **If API key lacks `write` scope** → System returns 403 Forbidden; attempt logged.
- **If the storage layer is temporarily unavailable** → System returns 503 Service Unavailable; no partial state is written; caller may retry.
- **If the document binary is empty (zero bytes)** → System returns 422 Unprocessable Entity with a clear rejection reason.

**Postconditions / Success State**
- Document binary is durably stored.
- Metadata (user ID, document ID, type, tags, `ingested_at`) is persisted and queryable.
- An audit log entry is written: actor = upstream service identity, event = `document_ingested`, document ID, timestamp.

**Related Edge Cases**
- EC-01: Duplicate submission of an identical document binary by the same upstream service (same content, same user ID)
- EC-02: Ingestion request received for a user ID that has an active right-to-erasure request in progress

---

### Flow 2: Retrieve Document by ID

- **Persona:** Downstream Consumer Service
- **Trigger:** Downstream service needs to fetch a specific document for processing or display
- **Preconditions:** Downstream service holds a valid API key with `read` scope; document ID is known

**Main Flow (Happy Path)**
1. Downstream service sends a retrieval request with a document ID → System validates the API key and `read` scope.
2. System looks up the document by ID → System returns the document binary and metadata (user ID, type, tags, `ingested_at`, `last_accessed_at`).
3. System asynchronously updates `last_accessed_at` timestamp.
4. System writes an audit log entry for the retrieval event.

**Alternate Flows / Branches**
- **Branch A — Document has been soft-flagged for erasure (right-to-erasure in progress):**
  1. System returns 404 Not Found (document is treated as non-existent for retrieval purposes once erasure is initiated).

**Error / Exception Flows**
- **If document ID does not exist** → System returns 404 Not Found.
- **If metadata exists but binary is missing (data inconsistency / orphaned record)** → System returns 500 Internal Server Error, logs a `data_inconsistency` audit event, and triggers an operational alert.
- **If API key is invalid** → System returns 401 Unauthorized; audit log records the failed access attempt.
- **If API key lacks `read` scope** → System returns 403 Forbidden; attempt logged.
- **If storage layer is unavailable** → System returns 503 Service Unavailable.

**Postconditions / Success State**
- Caller has received the document binary and metadata.
- `last_accessed_at` timestamp is updated.
- Audit log entry written: actor = downstream service identity, event = `document_retrieved`, document ID, timestamp.

**Related Edge Cases**
- EC-03: Two downstream services request the same document simultaneously
- EC-04: Document ID exists in metadata store but binary is missing from binary store (orphaned record)

---

### Flow 3: Generate and Access a Shareable Link

- **Persona:** Downstream Consumer Service (link generator); End-user or external partner (link consumer — not an API caller)
- **Trigger:** A downstream service needs to provide a recipient with direct, time-limited access to a document without routing through its own infrastructure
- **Preconditions:** Downstream service holds a valid API key with `write` scope; document ID is valid and document is not erased; requested expiry is between 1 hour and 30 days

**Main Flow (Happy Path)**
1. Downstream service sends a link-generation request with the document ID and a desired expiry duration → System validates the API key (`write` scope) and the document ID.
2. System generates a unique, unguessable link token and stores it alongside the document ID, expiry timestamp, and the requesting service's identity.
3. System returns the fully-formed shareable URL (using base URL configured at deployment time) and its expiry timestamp to the requesting service.
4. Link consumer (end-user or partner) accesses the shareable URL before expiry → System validates the link token and checks expiry.
5. System serves the document binary directly to the consumer, asynchronously updates `last_accessed_at` on the document, and writes an audit log entry for the link access event.

**Alternate Flows / Branches**
- **Branch A — Link token is valid but expiry has passed:**
  1. System returns 410 Gone with a message indicating the link has expired → Consumer sees an expired-link error.
- **Branch B — Downstream service requests a zero-expiry link (permanent):**
  1. System rejects the request (permanent links are not supported); returns 422 with explanation.

**Error / Exception Flows**
- **If link token does not exist (invalid or tampered URL)** → System returns 404 Not Found; no document is served.
- **If the underlying document has been erased after the link was generated** → System returns 410 Gone; audit log records the access attempt against an erased document.
- **If API key is invalid or lacks `write` scope on link-generation request** → System returns 401/403; no link is created.

**Postconditions / Success State**
- A unique, time-limited link token exists in the system associated with the document.
- Every access of the link (successful or failed) is recorded in the audit log with link token, document ID, and timestamp.

**Related Edge Cases**
- EC-05: Link is accessed simultaneously by multiple consumers
- EC-06: Link-generation request for a document that is currently undergoing erasure

---

### Flow 4: Right-to-Erasure (Hard Delete)

- **Persona:** Compliance Auditor / Authorized Admin Service (initiator)
- **Trigger:** A GDPR right-to-erasure request is received for a user ID or for a specific document ID
- **Preconditions:** Caller holds a valid API key with `erasure` scope

**Main Flow (Happy Path — Erase by User ID)**
1. Authorized caller sends an erasure request for a user ID → System validates the API key and `erasure` scope.
2. System identifies all documents associated with the user ID.
3. System permanently deletes all document binaries and metadata for the identified documents.
4. System invalidates (hard-deletes) all active share links associated with those documents.
5. System writes an immutable audit log entry for each document erased: actor, event = `document_erased`, document ID, user ID, timestamp. The audit log entry itself is retained for the legally required period (6 years per HIPAA) even after the document is erased.
6. System returns a confirmation payload listing the count and IDs of erased documents.

**Alternate Flows / Branches**
- **Branch A — Erase by specific document ID (not entire user):**
  1. Steps 2–6 apply to the single identified document only.
- **Branch B — User ID has no documents:**
  1. System returns 200 OK with a count of 0 and an empty list — not an error.

**Error / Exception Flows**
- **If API key lacks `erasure` scope** → System returns 403 Forbidden; no erasure is performed; attempt is logged.
- **If erasure partially fails (some documents deleted, others not)** → System returns 207 Multi-Status detailing which documents were erased and which failed; operation is retryable for failed IDs.

**Postconditions / Success State**
- All document binaries and metadata for the requested scope are permanently destroyed.
- All associated share links are invalidated.
- Audit log entry for each erasure event is retained.

---

## Functional Requirements

### Must Have Features

#### F-01: Document Ingestion & Storage via API

- **User Story:** As an upstream service, I want to submit a document binary with metadata in a single API call, so that the document is durably stored and immediately available to authorized consumers.
- **Acceptance Criteria:**
  - [x] `WHEN` a caller with `write` scope submits a document binary with a valid user ID (non-empty, ≤ 128 characters), document type (`PDF`, `JPG`, `PNG`, or `TIFF`), and a file size ≤ 10 MB, `THE SYSTEM SHALL` persist the document and return a unique document ID and `ingested_at` timestamp within 2 seconds.
  - [x] `WHEN` a caller submits a file type not in {`PDF`, `JPG`, `PNG`, `TIFF`}, `THE SYSTEM SHALL` reject the request with a 422 Unprocessable Entity error and not store any data.
  - [x] `WHEN` a caller submits a document exceeding 10 MB, `THE SYSTEM SHALL` reject the request with a 413 Payload Too Large error and not store any data.
  - [x] `WHEN` a caller submits a zero-byte file, `THE SYSTEM SHALL` reject the request with a 422 Unprocessable Entity error.
  - [x] `WHEN` optional tag fields are provided, `THE SYSTEM SHALL` accept up to 20 key-value pairs (key ≤ 64 chars, value ≤ 256 chars) and store them as queryable metadata. If tag bounds are exceeded, `THE SYSTEM SHALL` return 422 Unprocessable Entity.
  - [x] `THE SYSTEM SHALL` write an immutable audit log entry for every ingestion attempt (successful or failed).

#### F-02: Retrieve Document by ID

- **User Story:** As a downstream service, I want to retrieve a document binary and its metadata by document ID, so that I can process or display it.
- **Acceptance Criteria:**
  - [x] `WHEN` an authorized caller with `read` scope requests a document by ID, `THE SYSTEM SHALL` return the document binary and its full metadata (user ID, type, tags, `ingested_at`, `last_accessed_at`) within 500 ms (P95 metadata; binary download start within 2 s).
  - [x] `WHEN` an authorized retrieval succeeds or a shareable link is resolved, `THE SYSTEM SHALL` asynchronously update the document's `last_accessed_at` timestamp. Metadata-only query or list operations shall not update `last_accessed_at`.
  - [x] `WHEN` the document ID does not exist, `THE SYSTEM SHALL` return a 404 response.
  - [x] `WHEN` a document is in an erased state, `THE SYSTEM SHALL` treat it as non-existent and return 404.
  - [x] `WHEN` a document ID exists in metadata but its corresponding binary is missing from binary storage (orphaned record), `THE SYSTEM SHALL` return 500 Internal Server Error, write an audit log entry with event type `data_inconsistency`, and trigger an operational alert.
  - [x] `THE SYSTEM SHALL` write an audit log entry for every retrieval attempt (successful or failed), including the caller identity.

#### F-03: Retrieve Documents by User ID

- **User Story:** As a downstream service, I want to list all documents for a user ID with optional filters, so that I can present or process the relevant document set.
- **Acceptance Criteria:**
  - [x] `WHEN` an authorized caller with `read` scope queries by user ID, `THE SYSTEM SHALL` return a paginated list of document metadata (not binaries) within 500 ms (P95) (default page size 20, max page size 100, default sort order `ingested_at DESC`).
  - [x] `THE SYSTEM SHALL` support filtering the result set by document type, date range (`ingested_at`), and one or more tags.
  - [x] `WHEN` a user ID has no documents, `THE SYSTEM SHALL` return an empty list with a 200 OK — not a 404.
  - [x] `THE SYSTEM SHALL` not include documents in an erased state in the result set.
  - [x] `THE SYSTEM SHALL` write an audit log entry for every list query, including applied filters and caller identity.

#### F-04: Shareable Link Generation

- **User Story:** As a downstream service, I want to generate a time-limited shareable link for a document, so that an authorized recipient can access the document directly.
- **Acceptance Criteria:**
  - [x] `WHEN` an authorized caller with `write` scope requests a shareable link for a valid document ID with an expiry duration between 1 hour and 30 days, `THE SYSTEM SHALL` return a unique, unguessable URL (using a configurable base URL injected at deployment time) and the exact expiry timestamp.
  - [x] `WHEN` the requested expiry is outside the permitted range (< 1 hour or > 30 days), `THE SYSTEM SHALL` reject the request with a 422 Unprocessable Entity error.
  - [x] `THE SYSTEM SHALL` generate a link token that is cryptographically random and unguessable.
  - [x] `THE SYSTEM SHALL` write an audit log entry on link generation, recording the caller identity, document ID, and expiry.

#### F-05: Shareable Link Resolution

- **User Story:** As a document recipient, I want to access a document via a shareable link, so that I can retrieve it without needing API credentials.
- **Acceptance Criteria:**
  - [x] `WHEN` a valid, unexpired link token is accessed, `THE SYSTEM SHALL` serve the document binary without requiring an API key.
  - [x] `WHEN` a link token has passed its expiry timestamp, `THE SYSTEM SHALL` return 410 Gone and not serve the document.
  - [x] `WHEN` a link token does not exist or is malformed, `THE SYSTEM SHALL` return 404 Not Found.
  - [x] `WHEN` the underlying document has been erased after the link was created, `THE SYSTEM SHALL` return 410 Gone.
  - [x] `THE SYSTEM SHALL` write an audit log entry for every link access attempt (successful or failed), including the link token and timestamp.

#### F-06: Metadata Search

- **User Story:** As a downstream service, I want to search for documents by metadata fields, so that I can locate documents without knowing exact document IDs.
- **Acceptance Criteria:**
  - [x] `THE SYSTEM SHALL` support searching documents by any combination of: user ID, document type (`PDF`, `JPG`, `PNG`, `TIFF`), `ingested_at` date range, and one or more tag key-value pairs.
  - [x] `WHEN` a search query is submitted by an authorized caller with `read` scope, `THE SYSTEM SHALL` return paginated results within 500 ms (P95) (default page size 20, max 100, sorted `ingested_at DESC`).
  - [x] `THE SYSTEM SHALL` exclude erased documents from all search results.
  - [x] `THE SYSTEM SHALL` write an audit log entry for every search query, including the query parameters and caller identity.

#### F-07: Right-to-Erasure (Hard Delete)

- **User Story:** As a compliance actor, I want to permanently erase all documents for a user (or a specific document), so that I can fulfill a GDPR right-to-erasure request.
- **Acceptance Criteria:**
  - [x] `WHEN` an authorized erasure caller with `erasure` scope submits a hard-delete request for a user ID, `THE SYSTEM SHALL` permanently destroy all document binaries and metadata associated with that user ID.
  - [x] `WHEN` an erasure is completed, `THE SYSTEM SHALL` invalidate all active share links for those documents.
  - [x] `THE SYSTEM SHALL` support erasure by individual document ID as well as by user ID.
  - [x] `IF` the erasure caller's API key does not carry `erasure` scope, `THEN THE SYSTEM SHALL` reject the request with 403 Forbidden and log the attempt.
  - [x] `THE SYSTEM SHALL` retain immutable audit log entries for erasure events for a minimum of 6 years, even after the document data is destroyed.
  - [x] `WHEN` an erasure is complete, `THE SYSTEM SHALL` return a confirmation payload listing the count and IDs of erased documents.

#### F-08: API Key Authentication & Authorization

- **User Story:** As a service integrator, I want all DMS API endpoints to require a valid API key and enforced scope, so that unauthorized services cannot access or modify documents.
- **Acceptance Criteria:**
  - [x] `THE SYSTEM SHALL` reject any request that does not carry a valid API key with a 401 Unauthorized response.
  - [x] `THE SYSTEM SHALL` support four distinct API key scopes: `read` (document retrieval, user list, metadata search), `write` (document ingestion, shareable link generation), `erasure` (hard delete by user ID or document ID), and `audit-read` (audit log query endpoint access).
  - [x] `WHEN` an API key is used with an insufficient scope for the requested action, `THE SYSTEM SHALL` return 403 Forbidden.
  - [x] `THE SYSTEM SHALL` support revocation of individual API keys without affecting other keys, with revocation taking effect within 60 seconds.
  - [x] `THE SYSTEM SHALL` log every authentication/authorization failure (invalid key, insufficient scope) with the request timestamp and endpoint.

#### F-09: Immutable Audit Logging

- **User Story:** As a compliance auditor, I want a complete, immutable log of all document access and mutation events, so that I can produce a traceable record for regulatory inquiries.
- **Acceptance Criteria:**
  - [x] `THE SYSTEM SHALL` write an audit log entry for each of the following events: `document_ingested`, `document_retrieved`, `document_listed`, `document_searched`, `link_generated`, `link_accessed`, `link_access_failed`, `document_erased`, `api_key_auth_failed`, `data_inconsistency`.
  - [x] `EACH` audit log entry `SHALL` contain: event type, document ID (where applicable), user ID (where applicable), caller identity (API key identifier), timestamp (UTC), and outcome (success/failure).
  - [x] `THE SYSTEM SHALL` retain audit logs for a minimum of 6 years (HIPAA minimum) and prevent modification or deletion of any audit log entry through the normal API surface.
  - [x] `THE SYSTEM SHALL` expose a query endpoint for audit logs, filterable by document ID, user ID, event type, and date range, with paginated results (default page size 50, max 200, sorted timestamp DESC), accessible only to callers with `audit-read` scope.

---

### Should Have Features

#### F-10: Data Retention Policy Enforcement

- **User Story:** As a compliance manager, I want to define retention periods per document type, so that documents are automatically flagged or erased when their retention period expires.
- **Acceptance Criteria:**
  - [x] `THE SYSTEM SHALL` allow authorized callers to define a retention period (in days) per document type.
  - [x] `WHEN` a document's age exceeds its type's configured retention period, `THE SYSTEM SHALL` flag the document for review or automatic erasure, as configured.
  - [x] `THE SYSTEM SHALL` log a retention-expiry event in the audit log for every document that transitions to expired state.

---

### Could Have Features

#### F-11: Bulk Document Retrieval

- **User Story:** As a downstream service, I want to retrieve multiple document binaries in a single API call, so that I can reduce the number of round trips for batch processing.
- **Acceptance Criteria:**
  - [x] `WHEN` an authorized caller submits a list of up to 50 document IDs, `THE SYSTEM SHALL` return all accessible document binaries and their metadata in a single response.
  - [x] `THE SYSTEM SHALL` skip and report any document IDs that are erased, not found, or not accessible, without failing the entire batch.

---

### Won't Have (This Phase)

- Document versioning (multiple versions of the same document ID)
- OCR or full-text content indexing
- User-facing UI or dashboard
- Workflow management (approvals, signatures, status transitions)
- Document generation or transformation
- Permanent (non-expiring) shareable links
- Cross-region replication (may be addressed in Future Scope)
- Client-side ingestion idempotency key tracking (deferred to Phase 2)
- Ingress per-client rate limiting (handled at API Gateway / infra layer)

---

## Non-Functional Requirements

- **Performance:**
  - Metadata retrieval (by ID or by user ID) must complete within 500 ms at P95 under peak load (basis: stated by product owner).
  - Document binary download must begin streaming within 2 s at P95 (basis: [PROPOSED: pending eng confirmation]).
  - The system must sustain ≥ 500 API requests/second at peak without degradation (basis: stated by product owner).

- **Reliability / Availability:**
  - The system must maintain 99.9% uptime measured monthly (basis: [PROPOSED: pending eng confirmation]).
  - No single-point-of-failure architecture; the system must continue serving read requests if the ingestion path is temporarily degraded.

- **Security & Privacy (outcomes only):**
  - All documents and metadata must be protected from unauthorized access at rest and in transit — any party without a valid, scoped API key must receive no document data.
  - Share links must be unguessable; a recipient who knows one valid link must gain no information about any other document or link.
  - An API key revocation must take effect within 60 seconds of being issued (basis: [PROPOSED: pending eng confirmation]).
  - The identity of the service that accessed a given document must be determinable from audit logs alone, without needing application logs.

- **Compliance:**
  - The system must satisfy GDPR Article 17 (right to erasure): any document must be permanently destroyable within 24 hours of a valid erasure request (basis: GDPR Article 17 maximum response obligation).
  - The system must satisfy HIPAA audit trail requirements: audit logs must be tamper-evident, retained for a minimum of 6 years, and queryable by document ID or user ID (basis: 45 CFR §164.312(b)).
  - All data at rest must be encrypted using a mechanism that satisfies HIPAA §164.312(a)(2)(iv) (basis: HIPAA Security Rule) — described as an outcome, not an implementation.
  - All data in transit between services and the DMS must be encrypted (basis: HIPAA §164.312(e)(2)(ii)).

- **Scalability (outcomes only):**
  - The system must support a document corpus growing to and beyond 1 million documents without requiring architectural changes (basis: stated by product owner).
  - The system must support adding new authorized service integrations without downtime.

- **Usability (API consumer experience):**
  - Every API endpoint must return a structured error response with a machine-readable error code and a human-readable message for every non-2xx response.
  - API documentation (OpenAPI spec) must be generated and kept in sync with the implementation at all times.

---

## Detailed Feature Specifications

### Feature: Shareable Link Generation & Resolution (F-04 / F-05)

**Description:** The DMS generates a unique, cryptographically random URL token that maps to a specific document. The token carries an embedded or associated expiry. When a link consumer accesses the URL before expiry, the DMS serves the document binary directly. After expiry (or after document erasure), the URL becomes permanently invalid and returns 410 Gone.

**Business Rules:**
- Rule SL-1: The minimum configurable expiry for a generated link is 1 hour; the maximum is 30 days. Any request outside this range is rejected.
- Rule SL-2: A link becomes permanently invalid the moment its expiry timestamp passes — there is no grace period.
- Rule SL-3: When a document is erased (F-07), all share links associated with that document are simultaneously invalidated, regardless of their individual expiry timestamps.
- Rule SL-4: A single document may have multiple simultaneously active share links (e.g., generated for different recipients) — each link has its own token, expiry, and audit trail.
- Rule SL-5: Share link access does NOT require an API key — the unguessable token is the sole access credential for link-resolution requests.
- Rule SL-6: The generating service's identity is recorded at link-generation time and appears in the audit log for every access of that link.

**Feature-Specific Edge Cases:**
- EC-06: Link accessed at the exact expiry millisecond → System treats it as expired (server time is authoritative).
- EC-07: Same link accessed simultaneously by 50 consumers → All valid accesses succeed; all are individually logged.
- EC-08: Link generated for document that is erased within the link's validity window → Next access returns 410 Gone.

---

### Feature: Right-to-Erasure / Hard Delete (F-07)

**Description:** An authorized caller (holding `erasure` scope) can request the permanent destruction of all documents associated with a user ID, or a single document by its ID. Erasure is irreversible: binaries, metadata, and share links are all destroyed. The audit log entry for the erasure event itself is retained indefinitely (minimum 6 years) to provide evidence of compliance.

**Business Rules:**
- Rule ER-1: Erasure is permanent and irreversible — no soft-delete, no recycle bin.
- Rule ER-2: Erasure of a user ID erases ALL documents for that user — there is no partial user erasure.
- Rule ER-3: Erasure cascades immediately to all active share links for the erased documents.
- Rule ER-4: If an ingestion request is received for a user ID that is currently being erased, the ingestion request must be rejected with a 409 Conflict until the erasure is complete.
- Rule ER-5: Audit log entries for the erased documents are retained for 6 years minimum even after the document data is destroyed.
- Rule ER-6: Erasure scope is a separate, explicitly granted permission on an API key — read or write scope alone does not confer erasure authorization.

**Feature-Specific Edge Cases:**
- EC-09: Erasure request received while a retrieval for the same document is in-flight → Retrieval that has already started may complete; no new retrievals are served after erasure is initiated.
- EC-10: Erasure request for a user ID that has already been erased → System returns 200 OK with count = 0; this is idempotent.
- EC-11: Erasure partially fails (some documents erased, others not due to transient error) → System returns 207 Multi-Status; failed IDs are listed for retry.

---

## Edge Cases

- [x] EC-01: Duplicate ingestion of identical binary + user ID combination → System stores as a new, separate document with a new document ID (no deduplication in MVP).
- [x] EC-02: Ingestion request for a user ID with an active erasure in progress → System rejects with 409 Conflict.
- [x] EC-03: Simultaneous retrieval of the same document by two downstream services → Both succeed independently; two audit log entries are written.
- [x] EC-04: Document ID exists in metadata store but binary is missing (data inconsistency) → System returns 500 Internal Server Error; an alert is triggered for operational investigation; the audit log records the `data_inconsistency` event.
- [x] EC-05: Shareable link accessed simultaneously by many consumers → All valid accesses succeed concurrently; each access is individually logged.
- [x] EC-06: Link accessed exactly at expiry timestamp → System treats as expired (server-side clock is authoritative).
- [x] EC-07: Document erased after share link is generated → Next link access returns 410 Gone.
- [x] EC-08: Search query with no matching results → System returns 200 OK with empty array and pagination metadata.
- [x] EC-09: Erasure in progress while retrieval is in-flight → In-flight retrieval may complete; new retrievals blocked once erasure is committed.
- [x] EC-10: Erasure for a user ID with no documents → Returns 200 OK, count = 0 (idempotent).
- [x] EC-11: Partial erasure failure → 207 Multi-Status with failed IDs for retry.
- [x] EC-12: API key used against an endpoint with insufficient scope → 403 Forbidden; attempt logged.
- [x] EC-13: Audit log query for a document that has been erased → Audit log entries are retained and remain queryable even after document erasure.

---

## MVP Scope

The MVP delivers the following capabilities as a production-ready, GDPR/HIPAA-compliant backend service:

1. **Document Ingestion & Storage** (F-01): Accept PDF, JPG, PNG, TIFF documents ≤ 10 MB from authorized upstream services via API; store binary and metadata.
2. **Retrieve Document by ID** (F-02): Return document binary and metadata by document ID within 500 ms (P95 metadata); update `last_accessed_at`.
3. **Retrieve Documents by User ID** (F-03): Return paginated, filterable metadata list by user ID.
4. **Shareable Link Generation & Resolution** (F-04 / F-05): Generate time-limited (1 hour–30 days) links; serve documents via those links without API key.
5. **Metadata Search** (F-06): Search by user ID, type, date range, tags.
6. **Right-to-Erasure** (F-07): Hard-delete all documents and links for a user or document ID; retain audit evidence.
7. **API Key Authentication & Authorization** (F-08): `read`, `write`, `erasure`, and `audit-read` scopes enforced on every endpoint.
8. **Immutable Audit Logging** (F-09): Log all events; expose query endpoint for compliance auditors with `audit-read` scope.

Non-functional bar for launch: 99.9% uptime, 500 req/s sustained, P95 metadata latency ≤ 500 ms, data encrypted at rest and in transit, audit logs HIPAA-compliant.

---

## Future Scope

- **Phase 2 — Data Retention Automation** (F-10): Automated document expiry and erasure based on per-type retention policies.
- **Phase 2 — Bulk Retrieval** (F-11): Retrieve up to 50 document binaries in a single API call.
- **Phase 2 — Ingestion Idempotency Key**: Support client-provided idempotency keys on ingestion.
- **Phase 3 — Document Versioning**: Multiple versions of a document under a single canonical ID.
- **Phase 3 — Cross-Region Replication**: Replicate document store across multiple geographic regions for latency and resilience.
- **Phase 4 — Full-Text Search**: OCR pipeline and content-indexed search across document bodies.

---

## Out of Scope

| Excluded Capability | Reason |
|---|---|
| User-facing UI or dashboard | This is a backend platform service; UI is not needed and is not planned |
| Document generation or transformation (OCR, conversion) | DMS is a storage/retrieval system; content processing belongs to the upstream producer |
| Workflow management (approvals, signatures, status transitions) | Out of DMS mandate; belongs in a separate orchestration service |
| Permanent (non-expiring) share links | Excluded to enforce compliance best practice; a permanent link with no revocation mechanism is a HIPAA/GDPR risk |
| Content deduplication | Adds complexity without a clear compliance or operational requirement in MVP |
| Direct end-user authentication (OAuth, SSO) | DMS is service-to-service only; end-user identity management is the responsibility of calling services |
| Ingress per-client rate limiting | Handled at API Gateway / Infrastructure layer |

---

## Estimation Blockers

| # | What can't be sized yet | Why | Owner | Needed by |
|---|---|---|---|---|
| 1 | Exact retention periods per document type | No business input received on how long each document type must be retained; determines schema design for retention policy enforcement | Compliance / Legal Team | Before Phase 2 design begins |
| 2 | Breach notification SLA | HIPAA requires a defined window for notifying covered entities of a breach — the specific SLA (hours vs. days) has not been confirmed; affects audit log alerting requirements | Compliance / Legal Team | Before MVP production readiness sign-off |

---

## Success Metrics / Business Metrics

### Key Performance Indicators

| KPI | Target | Measurement Window |
|-----|--------|-------------------|
| Ingestion API success rate | ≥ 99.5% of valid submissions stored without error | Monthly |
| Retrieval P95 latency (metadata) | ≤ 500 ms | Weekly |
| Audit log coverage | 100% of tracked event types present in logs for all requests | Weekly (sampled audit) |
| Erasure SLA compliance | 100% of erasure requests completed within 24 hours | Per-request |
| Share link expiry enforcement | 0 expired links successfully resolved | Weekly |
| API availability | ≥ 99.9% uptime | Monthly |

### Tracking Requirements

| Event | Properties | Purpose |
|-------|------------|---------|
| `document_ingested` | document_id, user_id, document_type, file_size_bytes, caller_key_id, outcome, timestamp | Measure ingestion success rate; detect file-type/size rejection patterns |
| `document_retrieved` | document_id, user_id, caller_key_id, latency_ms, outcome, timestamp | Track retrieval latency and success rate |
| `document_listed` | user_id, filter_type, filter_date_range, filter_tags, result_count, caller_key_id, latency_ms, timestamp | Understand query patterns; detect empty-result trends |
| `document_searched` | query_params (sanitized), result_count, caller_key_id, latency_ms, timestamp | Measure search usage and performance |
| `link_generated` | link_token_id, document_id, caller_key_id, expiry_timestamp, timestamp | Track link generation volume and expiry distributions |
| `link_accessed` | link_token_id, document_id, outcome (success/expired/erased/not_found), timestamp | Measure link access patterns; detect expired-link access attempts |
| `document_erased` | document_id (or user_id), erased_document_ids, caller_key_id, outcome, timestamp | Compliance evidence for GDPR erasure fulfillment |
| `api_key_auth_failed` | caller_ip (hashed), endpoint, failure_reason, timestamp | Detect brute-force or misconfigured integration attempts |
| `data_inconsistency` | document_id, details, caller_key_id, timestamp | Track orphaned metadata / missing binary operational incidents |

---

## Timeline & Roadmap

| Phase | Milestone | Target Timing | Scope |
|-------|-----------|---------------|-------|
| Phase 1 — MVP | All Must-Have features (F-01 through F-09) production-ready | To be determined after planning stage | Full MVP as described in MVP Scope section |
| Phase 2 | Retention policy automation (F-10) + Bulk retrieval (F-11) + Ingestion Idempotency | Post-MVP, after compliance sign-off | Should-Have and Could-Have features |
| Phase 3 | Document versioning + cross-region replication | Long-term roadmap | TBD |
| Phase 4 | Full-text search (OCR pipeline) | Long-term roadmap | TBD |

---

## Risks & Constraints

### Constraints

- The DMS must be GDPR + HIPAA compliant from day one — there is no grace period for compliance features.
- Compliance / Legal team must approve the retention-period values before Phase 2 can be designed.
- The breach notification SLA must be defined before the MVP is deployed to production.

### Assumptions

- Upstream services will include a stable, unique user ID with every document submission; the DMS will not perform user identity resolution.
- Downstream services are responsible for mapping their own internal identities to user IDs before querying the DMS.
- Document IDs generated by the DMS are the authoritative reference; upstream services must store the returned document ID if they wish to retrieve documents later.
- All callers are trusted internal services operating on an internal network; the DMS API is not publicly internet-exposed.

### Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Retention period values not confirmed before Phase 2 | High — blocks Phase 2 design | Medium | Escalate to Compliance/Legal with a firm deadline before Phase 2 planning begins |
| HIPAA breach notification SLA undefined at MVP launch | High — compliance gap | Medium | Treat as a launch-blocking item; require sign-off before production deployment |
| Upstream service sends malformed or inconsistent user IDs | Medium — data quality issues at query time | Medium | Validate user ID format on ingestion (non-empty, ≤ 128 chars); reject invalid formats |
| Share link tokens are too short or predictable | High — security breach risk | Low (if CSPRNG used) | Enforce minimum token entropy requirements in the LLD |
| Simultaneous erasure + retrieval race condition | Medium — data integrity | Low | Define and implement an erasure-lock mechanism in LLD |

---

## Open Questions

- [ ] **OQ-1 (owner: Compliance/Legal, needed by: Phase 2 planning):** What is the required retention period per document type? Options: 1 year, 3 years, 6 years, or longer — each type may have a different answer.
- [ ] **OQ-2 (owner: Compliance/Legal, needed by: MVP production sign-off):** What is the organization's defined breach notification SLA (hours from detection to notification of covered entities)?
- [ ] **OQ-3 (owner: Platform Engineering):** Should audit log entries also be streamed asynchronously to a SIEM/compliance data store in addition to the DMS query endpoint in F-09?

---

## Supporting Research

### Domain Invariants — Resolution Log

The following 8 table-stakes items for a GDPR/HIPAA document management system were identified during the Domain Invariants Gate and resolved as follows:

| Invariant | Resolution |
|-----------|-----------|
| 1. Immutable audit logging of every access | → F-09 (Must Have) |
| 2. Encryption at rest | → NFR Security & Privacy section |
| 3. Encryption in transit | → NFR Security & Privacy section |
| 4. Right to erasure (GDPR Art. 17) | → F-07 (Must Have) |
| 5. Minimum-necessary access (scoped API keys) | → F-08 (Must Have) |
| 6. Share-link access logging | → F-04/F-05 acceptance criteria + F-09 |
| 7. Data retention controls | → F-10 (Should Have — Phase 2) |
| 8. Breach notification readiness (sufficient audit data) | → F-09 + OQ-2 (open blocker) |

### Competitive Analysis

Not applicable — this is an internal platform service, not a market-facing product.

### User Research

Requirements gathered through a structured interview with the platform engineering team (2026-07-30). Direct observation of the current fragmented document-handling gap confirmed by a GDPR/HIPAA readiness review finding.
