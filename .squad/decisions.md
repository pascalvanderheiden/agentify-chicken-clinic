# Squad Decisions

## Accepted Decisions (Issues #9–#12, Commit eff15d8)

### DEC-001: First Feature — Clinic Assistant

**Date:** 2026-08-25
**Status:** Accepted (completed and verified by full test suite)
**Delivered by:** Trinity (backend), Switch (testing), Morpheus (architecture/review)

**Decision:** The first feature built is a staff-facing, read-only Clinic Assistant conversational agent.

**Constraints (from safety envelope):**
- Staff-facing and read-only — no write tools, no mutations
- Single Spring Boot process — no additional services
- Framework-agnostic read-only query boundary with purpose-built records (not JPA entities)
- Default integration: Spring AI 2.0 with Microsoft Foundry OpenAI-compatible endpoint
- Answer only from retrieved PetClinic data; admit absent records; never guess identity
- No RAG, Azure AI Search, Foundry project/Agent Service, additional databases, or persistent transcripts
- No veterinary diagnosis or treatment advice

**Delivered capabilities:**
- Find owners by partial last name (case-insensitive, capped at 5 results)
- Find pets by name across all owners (capped at 5, with owner name identifying context)
- List veterinarians and answer specialty questions
- Ambiguous results prompt narrowing; absent results explicitly admitted
- All lookups recorded with concise, grounded activity trace

**Architectural seams (three-layer):**
1. **Read-only query boundary** (`ClinicQueryService` + purpose-built records) — framework-agnostic facade returning `OwnerRecord`, `PetRecord`, `VisitRecord`, `VetRecord`
2. **Spring AI adapter** — Spring-AI-aware tool definitions and `ChatClient` wiring
3. **Staff chat web controller** — Thymeleaf form, `@SessionScope` non-persistent history, navbar integration

**Package:** `org.springframework.samples.petclinic.assistant` (cohesive, decoupled from `owner`/`vet`/`vet` packages)

**Residual risks (explicitly out of scope for workshop slice):**
- Authentication, authorization, privacy, auditing
- Prompt-injection hardening, production observability
- Persistent conversations, scheduling, writes, medical advice safety at inference time

---

## Accepted Technical Decisions (Issues #9–#12)

### DEC-T9-001: Spring AI 2.0.1 via OpenAI-compatible starter

**Date:** 2026-08-25
**Issue:** #9
**Author:** Trinity
**Status:** Accepted

**Decision:** Added `spring-ai-starter-model-openai:2.0.1` directly (no BOM). Used the OpenAI-compatible starter because it supports Azure OpenAI/Foundry endpoints via `spring.ai.openai.base-url` without requiring the Azure-specific starter.

**Trade-off:** Azure-specific starter (`spring-ai-starter-model-azure-openai`) provides first-class Azure credential support via managed identity; the OpenAI-compatible path uses API-key auth only. This matches the workshop default (API key from environment variable) and avoids adding the azure-openai-sdk transitive dependency.

**Residual risk:** Production deployments should migrate to managed identity via the Azure-specific starter.

---

### DEC-T9-002: Session-scoped `AssistantChatSession` for conversation history

**Date:** 2026-08-25
**Issue:** #9
**Author:** Trinity
**Status:** Accepted

**Decision:** Used `@SessionScope` on `AssistantChatSession` to keep temporary conversation history in the HTTP session. History is lost on session expiry. No database, cache, or persistent storage is introduced.

**Trade-off:** In-memory session scope cannot survive server restart or be shared across replicas. Acceptable for the workshop slice; out-of-scope for production.

---

### DEC-T9-003: Framework-agnostic read-only query boundary

**Date:** 2026-08-25
**Issue:** #9
**Author:** Trinity
**Status:** Accepted

**Decision:** Created `ClinicQueryService` using purpose-built records (`OwnerRecord`, `PetRecord`, `VisitRecord`, `VetRecord`) rather than exposing JPA entities to the assistant layer. No Spring Data repository is called with write methods.

**Rationale:** Aligns with the safety envelope: "framework-agnostic read-only query boundary with purpose-built records rather than repositories or JPA entities".

---

### DEC-T9-005: Safe declines enforced by system prompt, not application code

**Date:** 2026-08-25
**Issue:** #9
**Author:** Trinity
**Status:** Accepted

**Decision:** Decline responses for mutation, veterinary advice, and out-of-scope requests are enforced via the system prompt in `SystemPrompt.TEXT`. The controller does not apply keyword filtering or guard clauses to short-circuit requests.

**Rationale:** System-prompt enforcement is the correct Spring AI pattern; application-code filtering would be a weaker and more brittle second layer. The system prompt is tested directly in `SystemPromptTests` to confirm all three decline categories are covered.

**Residual risk:** Prompt injection could bypass system-prompt instructions. Production hardening is explicitly out of scope for this workshop slice.

---

### DEC-010-A: Extend `OwnerRepository` with contains-match queries rather than a new repository

**Date:** 2026-08-25
**Issue:** #10
**Author:** Trinity
**Status:** Accepted

**Decision:** Added two `@Query`-annotated JPQL methods to `OwnerRepository`:
- `findByLastNameContainingIgnoreCase(String lastName)` — `LOWER(o.lastName) LIKE LOWER('%...%')`, with `LEFT JOIN FETCH o.pets` to avoid N+1 and a `DISTINCT` to prevent duplicate owners when multiple pets match.
- `findByPetNameContainingIgnoreCase(String petName)` — same pattern joining pets.

**Alternatives considered:**
- A separate `PetRepository` — rejected; pets are accessed through `Owner` in the existing domain model. Introducing a new repository would cross the established aggregate boundary.
- Spring Data derived query `findByLastNameContainingIgnoreCase` — JPQL explicit query gives full control over the fetch strategy.

**Trade-off:** JPQL `DISTINCT + LEFT JOIN FETCH` is reliable for H2 and standard SQL dialects but generates a slightly wider SQL. Acceptable for the read-only assistant boundary.

---

### DEC-010-B: `PetRecord` gains an `ownerName` field

**Date:** 2026-08-25
**Issue:** #10
**Author:** Trinity
**Status:** Accepted

**Decision:** Added `String ownerName` to `PetRecord`. When built from an `OwnerRecord` context (i.e., via `toOwnerRecord`), `ownerName` is populated as `firstName + " " + lastName`. This keeps the record self-contained without referencing the `Owner` entity.

**Trade-off:** All existing call sites that construct `PetRecord` must pass `ownerName`. This is a compile-time break that forced updating `toOwnerRecord` — acceptable because the `assistant` package is internal and the change is bounded.

---

### DEC-010-C: Tool wiring via `@Tool` on `AssistantChatSession` itself

**Date:** 2026-08-25
**Issue:** #10
**Author:** Trinity
**Status:** Accepted

**Decision:** Annotated `findOwnersByLastName`, `findPetsByName`, and `listVets` directly on `AssistantChatSession` with `@Tool`. Passed `this` to `.tools(this)`. This keeps the tool definitions co-located with the session boundary, avoids extra classes, and reuses the already-injected `ClinicQueryService`.

**Safety:** All tool methods are read-only delegations to `ClinicQueryService`. No write tools were added. The system prompt retains its full mutation/medical-advice decline instructions.

---

### DEC-010-D: `@DataJpaTest + @Import(ClinicQueryService.class)` for query boundary tests

**Date:** 2026-08-25
**Issue:** #10
**Author:** Trinity
**Status:** Accepted

**Decision:** Used `@DataJpaTest` (from `org.springframework.boot.data.jpa.test.autoconfigure`) with `@Import(ClinicQueryService.class)` to get a narrow JPA slice. This imports only the repository, entity scan, and the query service — no web layer, no AI wiring.

**Trade-off:** `@DataJpaTest` does not load `@SpringBootApplication` auto-configuration for AI. That is intentional — tests must not depend on a live Azure endpoint.

---

### DEC-T11-001: Result cap set to 5 (`MAX_CANDIDATES` constant)

**Date:** 2026-08-25
**Issue:** #11
**Author:** Trinity
**Status:** Accepted

**Decision:** Reduce the result limit to 5 in `ClinicQueryService` and expose it as a package-visible constant `MAX_CANDIDATES` so tests can reference the same value.

**Rationale:** The acceptance criterion targets "about five" candidates. Five is small enough to be readable in a chat transcript and large enough to cover realistic partial-name collisions in a clinic of typical size. The constant avoids a magic number in both production and test code.

**Trade-off:** A clinic with many owners sharing a surname fragment (e.g. "son") will not show all matches. Staff must narrow the query. This is the intended UX.

---

### DEC-T11-002: Ambiguity and absence instructions belong in the system prompt

**Date:** 2026-08-25
**Issue:** #11
**Author:** Trinity
**Status:** Accepted

**Decision:** Added explicit `AMBIGUOUS RESULTS` and `ABSENT RESULTS` sections to `SystemPrompt.TEXT` rather than embedding instructions in the `@Tool` description alone.

**Rationale:** The system prompt governs the model's global behavior policy. Tool descriptions are scoped to invocation context. Placing policy in both layers gives belt-and-suspenders coverage: the model sees the rule before the tool call and again at invocation time.

**Trade-off:** The system prompt grows longer. Accepted — it remains within a single screen and the clarity gain outweighs the token cost.

---

### DEC-T11-004: Tests cover ambiguity cap, identifying-detail presence, absence, and non-guessing

**Date:** 2026-08-25
**Issue:** #11
**Author:** Trinity
**Status:** Accepted

**Decision:** New tests added to `ClinicQueryServiceTests` as Seams 10–12:
- Seam 10: `hasSizeLessThanOrEqualTo(MAX_CANDIDATES)` for owners and pets; multi-match includes first name, last name, city, telephone.
- Seam 11: empty result for absent names returns genuinely empty list.
- Seam 12: every returned record matches the searched fragment — no invented entries.

**Rationale:** These are the narrowest slice that provides evidence for each acceptance criterion. They test the query boundary directly, not the LLM response, keeping them deterministic and fast.

---

### DEC-S12-001: Integration evidence via `@SpringBootTest` + deep-stub `ChatClient.Builder`

**Date:** 2026-08-25
**Issue:** #12
**Author:** Switch
**Status:** Accepted

**Decision:** Implemented 6-prompt evidence fixture using `@SpringBootTest` + a `@TestConfiguration`-supplied deep-stub `ChatClient.Builder` marked `@Primary`. The fixture exercises the full HTTP, controller, chat session, query service, and H2 stack; only the AI call is replaced.

**Rationale:** This is deterministic and does not require live Azure. The stub responses are canned strings that do not reflect real model output; live model wording and alignment remain explicit residual risks documented in the test class Javadoc.

---

### DEC-S12-002: Wording-tolerant `assertContainsAny` helper in evidence test class

**Date:** 2026-08-25
**Issue:** #12
**Author:** Switch
**Status:** Accepted

**Decision:** All assertions on reply content use `assertContainsAny(reply, candidates…)` — a static helper that passes if any candidate phrase appears case-insensitively in the response. Negative assertions (`doesNotContain`) are used for contract boundaries (no mutation claimed, no fabricated name, no medical specifics).

**Trade-off:** Wording-tolerant assertions could pass even if the reply is partially wrong. The boundary is: structural contracts (record shape, read-only methods, view name, model attributes) are asserted exactly; prose content is asserted wording-tolerantly.

---

### DEC-S12-003: H2 seed "Davis" ambiguity for fixture; no seed changes

**Date:** 2026-08-25
**Issue:** #12
**Author:** Switch
**Status:** Accepted

**Decision:** Use "Davis" as the ambiguity fixture prompt (Betty Davis + Harold Davis in H2 seed). No seed modifications needed.

---

### DEC-S12-004: `@SessionScope` isolation via explicit `MockHttpSession` per test

**Date:** 2026-08-25
**Issue:** #12
**Author:** Switch
**Status:** Accepted

**Decision:** Each test that performs a POST creates a new `MockHttpSession` and passes it to `mockMvc.perform(...)`. This ensures a fresh `AssistantChatSession` is constructed for each test and avoids cross-test state pollution.

---

### DEC-I18N: i18n sync discipline for new message keys

**Date:** 2026-08-25
**Issue:** #12 (i18n integration fix)
**Author:** Switch
**Status:** Accepted

**Decision:** Any team member adding new keys to `messages.properties` must add translated (or at minimum placeholder) entries to all nine locale files in the same change. This is a test-enforced convention, not optional.

**Rationale:** Partial translations cause a hard test failure (`I18nPropertiesSyncTest`) that blocks the entire suite. The sync test exists precisely to catch this — it is not noise.

---

### DEC-CONC-TEST: Integration tests must use an explicit HTTP client factory

**Date:** 2026-08-25
**Issue:** #12 (concurrency test fix)
**Author:** Switch (diagnosed by Morpheus)
**Status:** Accepted

**Decision:** Integration tests that use `RestTemplate` against a live embedded server MUST construct it explicitly using `new RestTemplate(new SimpleClientHttpRequestFactory())` rather than autowiring `RestTemplateBuilder`.

**Rationale:** `RestTemplateBuilder`'s default request factory changes based on classpath presence (HttpClient 5, Reactor, etc.). This is appropriate for production code but makes tests fragile across dependency changes. `SimpleClientHttpRequestFactory` (JDK `HttpURLConnection`) has stable, well-understood redirect semantics and no transitive coupling to reactive stacks.

**Scope:** Applies to all `@SpringBootTest(webEnvironment = RANDOM_PORT)` tests in this repository that make HTTP calls via `RestTemplate`.

---

## Post-Review Fixes (Morpheus, Accepted)

### DEC-F1: Pet-name cap applied after cross-owner flatten

**Date:** 2026-08-25
**Issue:** #11 (post-review)
**Author:** Morpheus
**Status:** Accepted (fix applied)

**Defect:** `limit(MAX_CANDIDATES)` was applied to *owners* before flattening to pets. A single owner with more than 5 matching pets could yield more than 5 pet records, violating the "capped at MAX_CANDIDATES so ambiguous responses stay readable" contract.

**Fix:** Moved `.limit(MAX_CANDIDATES)` to after the pet flatten/map so it constrains the final `PetRecord` stream. Owner details on each record are preserved.

**Evidence:** `ClinicQueryServiceTests` passes with cap verification and partial-match tests (22/22).

---

### DEC-F2: Activity trace grounded in real execution, not keyword inference

**Date:** 2026-08-25
**Issue:** #9 (post-review fix)
**Author:** Morpheus
**Status:** Accepted (fix applied)

**Defect:** The trace was inferred from user/reply wording (e.g. "looked up owner records", "request declined") even when **no tool ran**. This asserts a data lookup that never happened, conflicting with the read-only evidence rule (never substitute a success-shaped/confident inference).

**Fix:** Trace is now grounded in real execution. Each `@Tool` method records a `ToolInvocation(kind, resultCount)` into a per-turn list that is cleared at the start of every turn. `buildTrace` reports:
  - call failure → "Assistant service call failed — no clinic data was retrieved.";
  - no tool invoked → "Answered without querying clinic data; no lookup tool was invoked.";
  - otherwise → per-tool lines with the actual kind and match count.
  - A concise, non-blank trace is produced for **every** response.

**Evidence:** All `activityTrace` non-blank assertions pass. Two evidence assertions (F5, F6) updated to verify the honest trace (a decline runs no lookup tool), which is the correct evidence for a decline.

---

### DEC-F3: `OwnerRepository` Javadocs corrected

**Date:** 2026-08-25
**Issue:** #10 (post-review)
**Author:** Morpheus
**Status:** Accepted (fix applied)

**Defect:** Javadocs on `findByLastNameContainingIgnoreCase` and `findByPetNameContainingIgnoreCase` claimed "capped at 20 results". No such cap exists in the query; the service caps at `MAX_CANDIDATES` (5).

**Fix:** Corrected both Javadocs to state the repository returns all matches and that result-count capping is applied by the calling service (`ClinicQueryService.MAX_CANDIDATES`).

---

## Verification & Completion

**Full test suite:** `./mvnw test` PASSED with all review fixes applied.
- `ClinicQueryServiceTests`: 22/22 ✓
- `ClinicAssistantEvidenceTests`: 17/17 ✓
- `AssistantControllerTests`: 6/6 ✓
- `SystemPromptTests`: 6/6 ✓
- `PetClinicConcurrencyTests`: stable ✓
- All other legacy tests: ✓

**Review gates:** All findings addressed in-scope. Residual risks (auth, authz, privacy, auditing, prompt-injection, production observability, persistent conversations, medical advice at inference time) documented as explicitly out of scope for the workshop slice.

---

## Accepted Decisions (Issue #13, Commit TBD)

### DEC-T13-001: AssistantModelConfiguration startup validation

**Date:** 2026-08-25
**Issue:** #13
**Author:** Trinity
**Status:** Accepted (completed; Morpheus revised to warn-not-throw)

**Decision:** Added `AssistantModelConfiguration` with `@PostConstruct` that validates `spring.ai.openai.api-key` at startup.
- Placeholder key (`changeme`): logs WARN + returns (does not throw).
- Real key: logs endpoint and model at INFO.

**Rationale:** User stories 22–23 require deployment failures to be visible. The warn-not-throw approach respects the inherited system: failures are honest, not blocked. Constructor injection used; no reflection in tests.

---

### DEC-T13-002: Remove `/oups` from staff navigation

**Date:** 2026-08-25
**Issue:** #13
**Author:** Trinity
**Status:** Accepted

**Decision:** Removed `/oups` from the navbar in `layout.html` with an HTML comment explaining it is a framework error-demo, not a staff destination.

**Rationale:** User stories 28–29 exclude `/oups` as a navigation or assistant recovery path. The route remains reachable for instructors; just not in the main nav bar.

---

### DEC-T13-003: AssistantControllerTests service-unavailable case

**Date:** 2026-08-25
**Issue:** #13
**Author:** Trinity
**Status:** Accepted

**Decision:** Added test asserting that when `AssistantChatSession.chat()` returns the failure-path turn, the controller model holds that turn with trace "Assistant service call failed — no clinic data was retrieved."

**Rationale:** Ensures the failure state is distinct and honest (does not imply data retrieval).

---

### DEC-T13-004: Percy parrot GUI CSS pseudo-element

**Date:** 2026-08-25
**Issue:** #13
**Author:** Tank
**Status:** Accepted

**Decision:** Percy emoji (`🦜`) rendered via CSS `content: "\1F99C"` on `.percy-parrot-icon::before` rather than inline HTML.

**Rationale:** Avoids `I18nPropertiesSyncTest` linting failure on literal text; spans remain empty with `aria-hidden="true"`.

**Trade-off:** Visual depends on CSS loading; acceptable for decorative persona.

---

### DEC-T13-005: Percy intro bubble always visible

**Date:** 2026-08-25
**Issue:** #13
**Author:** Tank
**Status:** Accepted

**Decision:** The intro greeting bubble is rendered unconditionally on every page load, above conversation history.

**Rationale:** Serves as empty-state affordance and persona anchor throughout the conversation.

---

### DEC-T13-006: `assistant.intro` in all locale files

**Date:** 2026-08-25
**Issue:** #13
**Author:** Tank
**Status:** Accepted

**Decision:** Added `assistant.intro` key with locale-appropriate translations to `messages.properties` and all nine locale variants. `I18nPropertiesSyncTest` enforces this discipline.

---

### DEC-T13-007: AssistantParrotGuiTests — 17 MockMvc tests

**Date:** 2026-08-25
**Issue:** #13
**Author:** Switch
**Status:** Accepted

**Decision:** Added `AssistantParrotGuiTests` covering parrot GUI observable contract, service-unavailable state, and `/oups` exclusion at the `/assistant` public seam.

**Coverage (17 tests):**
- Percy identity, avatar, intro bubble, input/send controls
- Empty state vs. turn rendering
- Turn structure, staff/assistant messages, activity trace
- Service unavailable: distinct wording, honest trace
- `/oups` link absent both on fresh GET and post-failure

**Rationale:** Browser-facing seam with Thymeleaf rendering; no live Azure calls. Assertions pinned to percy-* structural classes (stable identifiers), not CSS utilities or animation names.

---

### DEC-F13-001: Startup warn-not-throw, constructor injection (Morpheus fix)

**Date:** 2026-08-25
**Issue:** #13 (post-review)
**Author:** Morpheus
**Status:** Accepted (fix applied)

**Defect:** Trinity's `@PostConstruct throw` blocked all `@SpringBootTest` contexts. Reflection-based test injection was fragile.

**Fix:**
- `AssistantModelConfiguration`: Converted to constructor injection; warn-not-throw on placeholder.
- `AssistantModelConfigurationTests`: Constructor-based instantiation; no reflection; assertion updated for warn behavior.

**Rationale:** Startup failure exceeds the safety envelope contract. Assistant requests fail honestly via existing `catch` → "service call failed" path. Inherited system unblocked.

**Evidence:** Full `./mvnw test` — 142 passed, 0 failures, 2 skipped (MySQL).

---

## Verification & Completion (Issue #13)

**Full test suite:** `./mvnw test` PASSED.
- `AssistantModelConfigurationTests`: 2/2 ✓
- `AssistantParrotGuiTests`: 17/17 ✓
- `CrashControllerIntegrationTests`: 2/2 ✓
- `ClinicAssistantEvidenceTests`: 17/17 ✓
- `AssistantControllerTests`: 6/6 ✓
- All other legacy tests: ✓
- **Total: 142 passed, 0 failures, 2 skipped**

**Residual gap:** Azure endpoint/model/quota/credential live reachability is not proven. Placeholder-key warning is log-only. Human Acceptance Gate remains open; do not close issue #13.

---

---

## Proposed Decisions (Commit 2582ed2 — awaiting human gates)

### DEC-INFRA-001: Azure CI/CD Workflow with OIDC Federated Credentials

**Date:** 2026-08-25  
**Status:** Proposed — awaiting human Commitment Gate (OIDC setup, repo config)  
**Author:** Tank (Infra/DevOps)  
**Reviewed by:** Morpheus (approved)

**Decision:** Created `.github/workflows/deploy-azure.yml` to automate `azd up --no-prompt` on every push to `main`.

**Key choices:**

| Area | Choice | Rationale |
|------|--------|-----------|
| Auth | OIDC via `azure/login` + `azd auth login --federated-credential-provider github` | No stored secrets; least-privilege federated credential |
| Trigger | `push: branches: [main]` only | A merged PR produces a push to main — one trigger covers both cases, no duplicates |
| Concurrency | `group: deploy-azure-main`, `cancel-in-progress: true` | Prevents overlapping deploys; latest commit always wins |
| Permissions | `id-token: write`, `contents: read` | Minimum required for OIDC + checkout |
| AZD env name | `vars.AZURE_ENV_NAME` (repository variable) | The local env name `workshop-preflight-20260824125634` is not suitable for CI as-is; must be set explicitly as a repo variable |
| Infrastructure | Unchanged — uses existing `azure.yaml` + `infra/` Bicep | Task constraint |

**Deployment status:**
- Workflow file committed and pushed (commit `2582ed2`)
- GitHub Actions queued on `main` push; run status not yet confirmed successful

**Required human setup:**

1. **Azure OIDC federated credential (one-time):**
   ```
   az ad app create --display-name "agentify-chicken-clinic-cicd"
   az ad sp create --id <appId>
   # Add federated credential: issuer=https://token.actions.githubusercontent.com
   #   subject=repo:<org>/agentify-chicken-clinic:ref:refs/heads/main
   az role assignment create --role Contributor \
     --assignee <appId> --scope /subscriptions/53a3db51-5b77-4e0e-bb8b-b287f39ac108
   ```

2. **GitHub repo secrets** (`Settings → Secrets and variables → Actions → Secrets`):
   - `AZURE_CLIENT_ID` — app registration client ID (OIDC)
   - `AZURE_TENANT_ID` — `16b3c013-d300-468d-ac64-7eda0820b6d3`
   - `AZURE_SUBSCRIPTION_ID` — `53a3db51-5b77-4e0e-bb8b-b287f39ac108`

3. **GitHub repo variables** (`Settings → Secrets and variables → Actions → Variables`):
   - `AZURE_ENV_NAME` — recommended: `workshop` or a stable name (not the preflight timestamped name)
   - `AZURE_LOCATION` — `swedencentral`

**Residual items (non-blocking, human's call):**
- No pre-deploy test gate; consider gating on `maven-build.yml` or adding a test step
- `azd up` re-provisions on every push (idempotent but slower); could switch to `azd deploy` after first provision
- RBAC scope at subscription level; can tighten to resource-group scope later
- `environment: production` is no-op unless GH Environment protection rule is configured

---

### DEC-INFRA-R1: Review & Approval — Azure CI/CD Workflow

**Date:** 2026-08-25  
**Status:** Approved (no blocking issues)  
**Author:** Morpheus (Lead)  
**Subject:** Tank's `deploy-azure.yml` (DEC-INFRA-001)

**Verdict:** No blocking correctness issues. All key requirements met.

**Checked:**

| Requirement | Result | Evidence |
|---|---|---|
| Auto-deploy on changes to `main` | ✅ | `on: push: branches: [main]` |
| Merged PRs deploy | ✅ | A merge = one push to `main`; single trigger, no duplicate runs |
| Uses existing AZD settings | ✅ | `azure.yaml` (bicep + `infra/`) unchanged; `azd up` reads it |
| Secure OIDC auth | ✅ | `id-token: write`; federated `azd auth login --federated-credential-provider github`; no stored credentials |
| Correct AZD auth semantics | ✅ | `AZURE_CLIENT_ID` + `AZURE_TENANT_ID` set at job `env`, in scope at the `azd auth login` step; OIDC token request vars provided by `id-token: write` |
| Least privilege | ✅ | Only `id-token: write` + `contents: read` |
| Concurrency safety | ✅ | `group: deploy-azure-main`, `cancel-in-progress: true` |
| No secrets in repo | ✅ | Only client/tenant/subscription **IDs** (identifiers) via GH secrets; no keys/passwords |

**Correctness hinge:** `azd auth login --federated-credential-provider github` depends on `AZURE_CLIENT_ID`/`AZURE_TENANT_ID` being present in the step environment and on the runner being able to mint an OIDC token. Both env vars are declared at job level (visible to the step) and `permissions.id-token: write` is granted. This is the documented, correct AZD-on-GitHub OIDC pattern.

**Advisory note:** The separate `azure/login@v2` step is redundant for azd itself (azd performs its own token exchange) but harmless; keep it if any future step uses the `az` CLI.

---

## Governance

- All meaningful changes require team consensus
- Document architectural decisions here
- Keep history focused on work, decisions focused on direction
- Inbox decisions are merged to this file after completion and independent review

---

### DEC-T-FOUNDRY-PROPS: Spring AI 2.0.1 foundry property binding — model name alignment

**Date:** 2026-08-25
**Author:** Trinity
**Status:** Proposed — pending Pascal's Acceptance Gate
**Evidence:** `.squad/decisions/inbox/trinity-foundry-properties.md`

**Finding:** `spring.ai.openai.microsoft-foundry` (prefix `spring.ai.openai`) is the correct and sufficient key — verified by bytecode analysis of `spring-ai-autoconfigure-model-openai 2.0.1`. The `spring.ai.openai.chat.*` prefix also works via OR-merge in `resolveCommonProperties`, but is not required.

**Root cause of outage:** Missing `spring.ai.openai.chat.model` and `spring.ai.openai.model`. `OpenAiChatProperties` defaults to `gpt-4o` which does not match the Azure deployment name, causing a 404 from Foundry.

**Fix applied:**
- Added `spring.ai.openai.model`, `spring.ai.openai.chat.model`, `spring.ai.openai.timeout=60s`, `spring.ai.openai.max-retries=2` to `application.properties`.
- Made `spring.ai.openai.microsoft-foundry` override-able via `${AZURE_OPENAI_MICROSOFT_FOUNDRY:true}`.
- Added `isMicrosoftFoundry()` seam and `FoundryPropertyBindingIT` `@SpringBootTest` regression test.

**Residual risk:** Live Azure acceptance (correct Foundry routing, managed identity credential exchange) requires redeployment — not verified locally.
