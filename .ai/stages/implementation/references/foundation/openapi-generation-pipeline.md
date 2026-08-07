# OpenAPI Generation, Verification & API Documentation Pipeline

This file covers **execution**: how the OpenAPI specification is actually generated, validated, served, and turned into human-readable API documentation, plus the automatic repair loop that runs when any of those steps fails.

It does **not** restate what a good contract contains. `api-contract-design.md` remains the single source of truth for the *design* rules — required metadata, security schemes, operation fields, trading-precision typing (money as decimal strings, never floats), and the OpenAPI review checklist. That file is read **before** implementing an endpoint; this file runs **after** implementation, on the code that now exists. Read both for any endpoint work; do not duplicate rules between them.

## When this pipeline is mandatory

Any task that **creates, modifies, renames, or removes** a backend/API endpoint triggers this pipeline in full. That includes changes that are not obviously "an endpoint change" but alter the wire contract: a changed request/response DTO, a new query parameter, a changed status code, a new auth requirement on an existing route, or a removed field.

A backend task that touches an endpoint is **never complete until Swagger verification succeeds** (Step 4/5 below) and the API documentation artifact has been generated (Step 7). This is a completion gate, not a reporting nicety. A task with no HTTP surface at all (a pure matching-engine change, an internal library refactor, frontend-only work) reports `N/A — no REST endpoints` and skips the pipeline; that exemption is for genuinely absent HTTP surface, not for "the endpoint barely changed."

---

## Step 1 — Detect the backend language and framework

Detect from the repository itself, not from assumption. Read the manifest and the server bootstrap file:

| Signal | Framework conclusion |
|---|---|
| `Cargo.toml` + `axum`/`actix-web`/`poem` dependency | Rust — Axum / Actix-web / Poem |
| `go.mod` + `gin-gonic/gin`, `labstack/echo`, `go-chi/chi` | Go — Gin / Echo / Chi |
| `pom.xml` / `build.gradle` + `spring-boot-starter-web` | Java — Spring Boot |
| `package.json` + `@nestjs/core` / `express` / `fastify` | Node — NestJS / Express / Fastify |
| `requirements.txt` / `pyproject.toml` + `fastapi` / `django` / `flask` | Python — FastAPI / DRF / Flask |

If more than one backend service is in scope, run this pipeline **once per service**, and report one verification block per service. If the framework genuinely cannot be determined from the repository, that is a `clarification-protocol.md` question — do not guess a toolchain and install dependencies against the guess.

## Step 2 — Bootstrap OpenAPI support if it does not exist (automatic, no prompt)

If the detected service has no OpenAPI/Swagger support, install and wire it automatically, then continue — do not stop to ask which library to use. This is the same automatic-install rule as `SKILL.md` Section 15's "Dependency installation" (dependencies install without asking) and it inherits that section's mandatory pairing: **every install runs through `../compliance-safety/security-scanning/dependency-vulnerability-scanning.md`, and any Critical/High/Medium/Low finding triggers that section's Dependency Review pause.** Auto-install never means unscanned install.

Per-framework bootstrap (matching `api-contract-design.md` Section 3, which stays authoritative on the annotation style each one expects):

| Framework | Library | Wiring required |
|---|---|---|
| Rust — Axum / Actix | `utoipa` + `utoipa-swagger-ui` | Derive `ToSchema`/`IntoParams`, register `#[utoipa::path]` handlers in `OpenApi` derive, mount `SwaggerUi` route on server init |
| Rust — Poem | `poem-openapi` | `OpenApiService::new(...).swagger_ui()` mounted on the app router |
| Go — Gin / Echo / Chi | `swaggo/swag` + `http-swagger`/`echo-swagger` | `swag` annotations on handlers, `swag init` generates `docs/`, mount the swagger handler |
| Java — Spring Boot | `springdoc-openapi-starter-webmvc-ui` | Dependency alone auto-serves; add an `OpenAPI` bean for `info` + `SecurityScheme` |
| Node — NestJS | `@nestjs/swagger` | `DocumentBuilder` + `SwaggerModule.setup('docs', app, document)` in `main.ts`; `@ApiProperty` on DTOs |
| Node — Express / Fastify | `swagger-jsdoc` + `swagger-ui-express` (or `@fastify/swagger` + `@fastify/swagger-ui`) | JSDoc `@openapi` blocks on routes; serve UI on `/docs` |
| Python — FastAPI | native | Already serves `/docs`; enrich via `FastAPI(title=..., version=...)` and per-route `responses=`/`response_model=` |
| Python — DRF / Flask | `drf-spectacular` / `flasgger` | Register the schema view and the UI route |

Registration of the documentation endpoint is part of bootstrapping, not a follow-up task: the route must be mounted so the UI serves on backend start with zero further user configuration, per `api-contract-design.md` Section 1's standard routes.

## Step 3 — Generate the specification with the framework's native mechanism

Use the framework's own generator; do not hand-write a spec file that the framework would otherwise produce, and do not maintain a second spec by hand alongside a generated one.

| Framework | Generation command / mechanism | Spec artifact |
|---|---|---|
| Rust (utoipa) | `cargo build` (derive-time) — dump via the app's spec route or a small `--dump-openapi` bin | `openapi.json` |
| Go (swaggo) | `swag init -g <entrypoint>.go -o docs` | `docs/swagger.json`, `docs/swagger.yaml` |
| Spring Boot | run the app, fetch `/v3/api-docs` (or `springdoc.outputDir` at build time) | `openapi.json` |
| NestJS | `SwaggerModule.createDocument(...)` — write to disk in a bootstrap script | `openapi.json` |
| Express | `swagger-jsdoc` CLI/script | `openapi.json` |
| FastAPI | `app.openapi()` dumped to file | `openapi.json` |

Write the generated artifact to a stable, committed location (`docs/api/openapi.json` unless the project already has a convention — follow the existing one, per `project-structure.md`).

## Step 4 — Validate the generated specification

Every item below is checked explicitly. A check that cannot be performed is reported as unverified — never assumed to pass.

- [ ] **Specification exists** at the expected path and is non-empty
- [ ] **Specification is valid** OpenAPI (parses; schema-valid — e.g. `swagger-cli validate`, `redocly lint`, or the framework's own validator)
- [ ] **Every endpoint is documented** — the count of routes registered by the router equals the count of operations in the spec; any route present in code but absent from the spec is a failure, not a warning
- [ ] **Request schemas exist** for every operation with a body
- [ ] **Response schemas exist** for every declared status code, success and error alike
- [ ] **Authentication is documented** — protected operations carry a `security` requirement
- [ ] **Security schemes exist** in `components/securitySchemes` and are referenced, not merely declared
- [ ] **Validation rules are documented** — `required`, `minimum`/`maximum`, `pattern`, `enum`, `format` reflect what the implementation actually enforces
- [ ] **Examples exist** for every request and response where the framework supports them

Content-level correctness (money as decimal strings, enum coverage, RFC 3339 timestamps, pagination headers) is validated against `api-contract-design.md` Section 4's checklist — run that checklist here rather than restating it.

## Step 5 — Verify the documentation endpoint is reachable

Start the service (or use the already-running instance) and confirm the documentation and specification URLs actually respond — an HTTP 200 from `/docs`, `/swagger`, `/swagger-ui/index.html`, or the framework's route from the table in `api-contract-design.md` Section 1, plus a 200 and valid JSON from the spec URL (`/v3/api-docs`, `/openapi.json`, `/swagger/doc.json`).

"The route is registered in the source" is not verification. If the service cannot be started in this environment, say so explicitly in the report's `validation status` as *not verified — service could not be started*, with the reason; do not report a reachable UI that was never fetched.

## Step 6 — Swagger Verification Report (required output)

```
## Swagger Verification Report
Detected framework: <language + framework, and how it was detected>
Swagger/OpenAPI library: <library + version>
Generation command: <exact command run>
Generated file location: <path>
Documentation URL: <url> — <HTTP status observed>
Specification URL: <url> — <HTTP status observed>
Endpoint count: <operations in spec> / <routes registered in code>
Schema count: <components/schemas count>
Validation status: <PASS | FAIL — which Step 4 check failed> 
Warnings: <non-blocking issues: missing examples where optional, undocumented optional params, etc., or "none">
```

One block per service when multiple services are in scope.

## Step 7 — Generate human-readable API documentation (required artifact)

Once verification passes, generate human-readable API documentation **from the verified specification** — the spec is the source, so the documentation cannot drift from a hand-written second description of the same API.

Use the project's existing documentation format and tooling if it has one (an existing `docs/` site, Docusaurus, MkDocs, an existing Markdown convention). Only when the project has no preference, default to a generated Markdown file at `docs/api/api-documentation.md` (via `widdershins`, `redocly build-docs`, or direct rendering of the spec) — this follows the "match existing conventions" rule in `SKILL.md` Section 2 rather than imposing a new toolchain.

The documentation must include:

- API overview · version · base URL(s)
- Authentication and security requirements
- Endpoint summary (grouped by tag)
- Request parameters (path, query, header) per endpoint
- Request examples
- Response examples
- Error responses
- Schemas
- Validation rules
- Status codes

**Synchronization is mandatory.** Whenever the specification changes, the documentation is regenerated in the same task — a spec change with stale documentation is an incomplete task. Because generation is from the spec, keeping them in sync means re-running the generator, not hand-editing prose. The generated documentation is a **completion artifact**: it is named in the Verification report and a backend endpoint task is not done without it.

## Step 8 — Automatic repair loop on failure

If generation fails, validation fails, or the documentation endpoint is unreachable, do not report the task complete and do not report the gate as skipped. Repair and retry:

1. **Determine the root cause** from the actual error output — the generator's message, the validator's finding, the server's startup log. Do not guess.
2. **Repair the specific cause**, in the order the evidence points to:
   - *Configuration* — missing generator config, wrong entrypoint/scan path, output directory, doc route not mounted
   - *Dependencies* — missing or version-incompatible OpenAPI library, missing codegen tool on PATH (install per Step 2, scanned per that step's rule)
   - *Annotations* — missing/incorrect handler annotations, DTOs without schema derivation, undocumented status codes, missing security attributes
3. **Regenerate** and re-run Steps 4 and 5 — re-run the check that failed, not just an eyeball of the fix.
4. **Repeat until successful, or until no automated repair is possible.**
5. If the same failure survives **3 repair attempts**, stop looping and surface it to the user with the root-cause evidence and what was tried — the identical escalation rule as `SKILL.md`'s "Failure loop-back" step 5. A silent retry loop that never asks for help is its own failure mode.

When no automated repair is possible (a framework that genuinely cannot express the contract, a blocked dependency install), report the gate as **FAIL with the reason and the manual step required** — never as a pass, and never omitted from the Verification report.
