# Technology Stack Specification — Document Management System (DMS)

**Scope:** `backend`  
**Version:** 2.0 (Simplified Service Architecture)  
**Status:** DRAFT (Stage 3a — High-Level Design Iteration)  
**Date:** 2026-07-30  

---

## 1. Architecture Overview

The Document Management System (DMS) is designed as a standalone, unauthenticated REST microservice that provides direct HTTP endpoints for document ingestion, storage, retrieval, share link management, metadata searching, right-to-erasure, and audit logging query operations.

| Layer | Selected Technology | Version / Specification | Rationale & Justification |
|---|---|---|---|
| **Runtime & Language** | **Node.js + TypeScript** | Node.js v20 LTS, TypeScript 5.x | High-throughput asynchronous I/O ideal for binary streaming and REST API handling; strong static typing for DTOs and database entities. |
| **HTTP Web Framework** | **Fastify** | Fastify v4.x | Lightweight, high performance (> 1,000 req/s), built-in JSON schema validation, and direct multipart stream handling. |
| **Database (Metadata & Audit Logs)** | **PostgreSQL** | PostgreSQL 16 | ACID compliant store for document metadata, JSONB tags, and partitioned audit log tables. |
| **Object Storage (Document Binaries)** | **AWS S3 / MinIO** | AWS SDK v3 | Scalable blob storage with server-side encryption (SSE-KMS / AES-256) and presigned URL capabilities. |
| **Cache & Token Store** | **Redis (Valkey)** | Redis 7.x | Fast in-memory token lookup for shareable link expiration and distributed locking during erasure operations. |
| **ORM / Data Access** | **Kysely** | Kysely 0.27.x | Type-safe SQL query builder with zero runtime overhead for precise control over queries and transactions. |

---

## 2. Infrastructure & Service Topology

```
                       ┌─────────────────────────────────────────────────────────────┐
                       │                     Calling Microservices                   │
                       └──────────────────────────────┬──────────────────────────────┘
                                                      │ HTTP / REST Requests (Unauthenticated)
                                                      ▼
                       ┌─────────────────────────────────────────────────────────────┐
                       │               DMS REST Microservice (Fastify)                │
                       │                                                             │
                       │   ┌─────────────────────────────────────────────────────┐   │
                       │   │                   REST Controller                   │   │
                       │   └──────────────────────────┬──────────────────────────┘   │
                       │                              │                              │
                       │   ┌──────────────────────────▼──────────────────────────┐   │
                       │   │              Core Service Business Logic            │   │
                       │   └──────────────┬───────────────────┬──────────────────┘   │
                       └──────────────────┼───────────────────┼──────────────────────┘
                                          │                   │
                     ┌────────────────────┴─────┐       ┌─────┴────────────────────┐
                     ▼                          ▼       ▼                          ▼
       ┌───────────────────────────┐    ┌───────────────────────────┐    ┌───────────────────────────┐
       │       Redis Cache         │    │    PostgreSQL Database    │    │      AWS S3 Storage       │
       │  - Share Link Tokens      │    │  - Document Metadata      │    │  - Document Binaries      │
       │  - Distributed Locks      │    │  - Partitioned Audit Logs │    │  - SSE-KMS Encrypted      │
       └───────────────────────────┘    └───────────────────────────┘    └───────────────────────────┘
```
