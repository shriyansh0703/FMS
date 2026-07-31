# Traceability Matrix — Document Management System (DMS)

> **Rules:** Stage 1 populates REQ ID + Summary only. Each subsequent stage fills in its own column using its own artifact as source. No column is re-derived from scratch; only appended. Empty cells in later columns indicate coverage not yet established by that stage.

---

## Scope: `backend`

| REQ ID | Requirement Summary | HLD Coverage (Stage 3a) | LLD Coverage (Stage 5a) | Code Coverage (Stage 8) | Test Coverage (Stage 10) |
|--------|---------------------|------------------------|------------------------|------------------------|--------------------------|
| REQ-001 | Document ingestion & storage via API — accept PDF/JPG/PNG/TIFF ≤ 10 MB from authorized upstream services; validate user ID, tags, file size | [hld-backend.md#31](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#31-document-ingestion-workflow-f-01) | | | |
| REQ-002 | Store document binary and metadata (user ID, document ID, type, tags, timestamps) durably | [hld-backend.md#4](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#4-database-schema-design-postgresql) | | | |
| REQ-003 | Retrieve document binary + metadata by document ID within 500 ms P95 (metadata); update last_accessed_at; handle orphaned records (EC-04) | [hld-backend.md#32](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#32-retrieve-document-by-id--orphaned-record-edge-case-f-02--ec-04) | | | |
| REQ-004 | Retrieve paginated, filterable document metadata list by user ID within 500 ms P95 (default 20, max 100, ingested_at DESC) | [hld-backend.md#5](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#5-api-specification-overview) | | | |
| REQ-005 | Generate time-limited (1h–30d) shareable link — unique, unguessable token, configurable base URL | [hld-backend.md#5](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#5-api-specification-overview) | | | |
| REQ-006 | Resolve shareable link — serve document binary without API key; enforce expiry and erasure state; update last_accessed_at | [hld-backend.md#33](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#33-shareable-link-resolution-f-05) | | | |
| REQ-007 | Metadata search — filter by user ID, document type, date range, tags; paginated results within 500 ms P95 | [hld-backend.md#5](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#5-api-specification-overview) | | | |
| REQ-008 | API key authentication & authorization — enforce distinct scopes (`read`, `write`, `erasure`, `audit-read`); 60s revocation SLA | [hld-backend.md#2](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#2-component-hierarchy--module-design) | | | |
| REQ-009 | Immutable audit logging — log all events (ingest, retrieve, list, search, link-gen, link-access, erase, auth-fail, data_inconsistency); 6-year retention; query endpoint with `audit-read` scope | [hld-backend.md#4](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#4-database-schema-design-postgresql) | | | |
| REQ-010 | Right-to-erasure (GDPR Art. 17) — hard-delete documents + links by user ID or document ID within 24 h; retain audit log | [hld-backend.md#34](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#34-right-to-erasure-hard-delete-workflow-f-07) | | | |
| REQ-011 | Share-link access logging — every link access (successful or failed) logged with token, document ID, timestamp | [hld-backend.md#33](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#33-shareable-link-resolution-f-05) | | | |
| REQ-012 | Encryption at rest — all document binaries and metadata protected at rest (HIPAA §164.312(a)(2)(iv)) | [tech-stack.md#2](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/tech-stack.md#2-infrastructure--compliance-mapping) | | | |
| REQ-013 | Encryption in transit — all API calls over encrypted channels (HIPAA §164.312(e)(2)(ii)) | [tech-stack.md#2](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/tech-stack.md#2-infrastructure--compliance-mapping) | | | |
| REQ-014 | System availability ≥ 99.9% monthly | [hld-backend.md#7](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#7-scalability-latency--resilience-sla) | | | |
| REQ-015 | Peak throughput ≥ 500 req/s sustained without degradation | [hld-backend.md#7](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#7-scalability-latency--resilience-sla) | | | |
| REQ-016 | API key revocation effective within 60 s | [hld-backend.md#2](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#2-component-hierarchy--module-design) | | | |
| REQ-017 | Structured error responses (machine-readable code + human-readable message) on every non-2xx | [hld-backend.md#5](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#5-api-specification-overview) | | | |
| REQ-018 | OpenAPI spec generated and kept in sync with implementation | [tech-stack.md#1](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/tech-stack.md#1-stack-selection-overview) | | | |
| REQ-019 | Data retention policy enforcement per document type (Should Have — Phase 2) | [hld-backend.md#4](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#4-database-schema-design-postgresql) | | | |
| REQ-020 | Bulk document retrieval — up to 50 document IDs in one call (Could Have — Phase 2) | [hld-backend.md#5](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#5-api-specification-overview) | | | |

---

## Key Edge Cases Traced

| EC ID | Edge Case Summary | Covered in Feature(s) | Test Coverage |
|-------|------------------|-----------------------|---------------|
| EC-01 | Duplicate ingestion (same binary + user ID) → new document ID issued | REQ-001, REQ-002 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-02 | Ingestion during active erasure → 409 Conflict | REQ-001, REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-03 | Simultaneous retrieval by two services → both succeed, both logged | REQ-003, REQ-009 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-04 | Orphaned record (metadata exists, binary missing) → 500 + data_inconsistency log + alert | REQ-003, REQ-009 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-05 | Share link accessed simultaneously by many consumers → all logged | REQ-006, REQ-011 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-06 | Link accessed at exact expiry → expired (server clock authoritative) | REQ-006 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-07 | Document erased after link generated → 410 Gone on next access | REQ-006, REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-08 | Search with no results → 200 OK, empty array | REQ-007 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-09 | Erasure + in-flight retrieval race → in-flight may complete, new blocked | REQ-003, REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-10 | Erasure of user with no documents → 200 OK, count = 0 (idempotent) | REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-11 | Partial erasure failure → 207 Multi-Status | REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-12 | API key used with insufficient scope → 403 + logged | REQ-008, REQ-009 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |
| EC-13 | Audit log query for erased document → entries retained and queryable | REQ-009, REQ-010 | [hld-backend.md#6](file:///Users/naitik-moneylogix/Desktop/testWorklow/strict_prd_to_prod/.ai/stages/architecture/hld-backend.md#6-edge-case-handling-strategy-matrix) |

---

## Metadata

| Field | Value |
|-------|-------|
| Last updated by stage | Stage 3a — High-Level Design (Backend) |
| Scope | backend |
| Total requirements | 20 (12 Must Have NFRs/features, 1 Should Have, 1 Could Have, 6 NFR-derived) |
| Open requirements (unresolved OQs blocking Must-Haves) | 0 |
| Open questions | OQ-1 (retention periods), OQ-2 (breach notification SLA), OQ-3 (SIEM streaming) |
