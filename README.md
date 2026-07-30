# Strict PRD-to-Prod Workflow (Google Antigravity)

This repository contains an autonomous, strict **10-Stage Software Development Life Cycle (SDLC)** for Google Antigravity. It enforces scope declaration, zero-laziness production code policies, interactive approval gates, one-hop-back review contexts, incremental traceability matrix tracking, and runtime JavaScript hook guards.

---

## 🏗️ 10-Stage Pipeline Overview

| Stage | Stage Name | Locked Skill(s) | Key Output |
|---|---|---|---|
| **1** | Requirement Analysis | `prd-generator` | `requirements.md`, `traceability.md` |
| **2** | PRD Review | `prd-reviewing` | `prd-review.md` |
| **3a** | High-Level Design (Backend) | `backend-hld-architect` | `hld-backend.md`, `tech-stack.md` |
| **3b** | High-Level Design (Frontend) | `frontend-hld-designer` | `hld-frontend.md`, `tech-stack.md` |
| **4** | HLD Review (1-Hop Back) | `hld-reviewer` | `hld-review.md` |
| **5a** | Low-Level Design (Backend) | `backend-lld-architect` | `lld-backend.md` |
| **5b** | Low-Level Design (Frontend) | `frontend-lld-designer` | `lld-frontend.md` |
| **5c** | LLD Consistency Pass | Orchestrator Check | `lld.md` |
| **6** | LLD Review (1-Hop Back) | `frontend-lld-review`, `lld-reviewer` | `lld-review.md` |
| **7** | Planning | `edited-plan-skill` | `planning.md`, `tasks.json` |
| **8** | Implementation | `trading-platform-coding` | Production Source Code |
| **9** | Code Review & Traceability Scan | `code-reviewer` | `review.md` |
| **10** | QA Testing & Browser Validation | `playwright-test-results` | `test-report.md`, `browser-report.md` |

---

## ⚡ How to Install in Any Project

To use this workflow in your project repository:

### Method 1: Clone or Copy Contents (Recommended)
1. Copy the `.ai/`, `.agents/`, and `hooks/` directories from this repo into the root folder of your project:
   ```bash
   cp -r .ai .agents hooks /path/to/your-project/
   ```
2. Google Antigravity will automatically load `.ai/workflows/prd-to-prod.md` and activate the runtime hooks in `.agents/hooks.json`.

### Method 2: Global Setup (All Projects)
Copy the skills and workflows to your local Antigravity plugin directory:
```bash
mkdir -p ~/.gemini/config/plugins/strict-sdlc/
cp -r .ai/skills ~/.gemini/config/plugins/strict-sdlc/skills
cp -r .ai/workflows ~/.gemini/config/plugins/strict-sdlc/workflows
```

---

## 🛡️ Runtime Hook Protection

This repository includes runtime JavaScript hook guards (`hooks/pre-tool.js`, `hooks/post-tool.js`, `hooks/stop.js`):
- **PreToolUse**: Blocks tool execution if stage order, skill locks, or scope rules are violated.
- **PostToolUse**: Computes SHA-256 artifact checksums and cascades staleness to downstream artifacts when upstream files change.
- **Stop**: Prevents premature workflow completion if required artifacts are unapproved or stale.
