# Scribe — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Session Logger
- **Joined:** 2026-08-25T08:20:21.359Z

## Learnings

### Batch 1 (Issues #9–#13, Commit eff15d8): Clinic Assistant + Percy Parrot GUI
- Complete feature delivery: three-layer seam architecture (read-only boundary, Spring AI adapter, controller)
- Cross-agent testing discipline: mock/stub AI, deterministic H2 contracts, post-review fixes integrated
- Post-review defect patterns: cap logic before vs. after cross-owner flatten, trace inference vs. grounded execution, stale docs
- Full test suite validation before accepting (142 passed)
- Residual risks recorded (auth, authz, privacy, observability, medical advice at inference time)

### Batch 2 (Commit 2582ed2): Azure CI/CD Workflow
- Infrastructure-as-code pattern: OIDC federated credentials (no stored secrets), concurrency safety (group + cancel-in-progress)
- Workflow approval without live green run: correctness review gates acceptance of the workflow logic; deployment run success is a separate Acceptance Gate after human repo setup
- Decision format: Proposed decisions await human gates (Commitment: decide OIDC setup; Acceptance: verify live green after secrets configured)
- Advisory vs. blocking: Tank and Morpheus distinguished pre-deploy test gate, reprovisioning, RBAC scope, and Environment rules as non-blocking human choices
- Batch completion record + decision merge into `.squad/decisions.md` (Proposed section) maintain cross-agent context for handoff to human gates

