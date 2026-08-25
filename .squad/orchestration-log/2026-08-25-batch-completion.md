# Batch Completion: Issues #9–#12 (Add read-only Clinic Assistant)

**Date:** 2026-08-25  
**Completion timestamp:** 12:10 UTC+2  
**Commit:** `eff15d8` ("Add read-only Clinic Assistant")

---

## Overview

Issues #9, #10, #11, #12 have been completed and merged to main. This entry records the work breakdown, cross-agent contributions, verification, and decision merges.

---

## Work Breakdown

| Issue | Owner(s) | Scope | Status |
|-------|----------|-------|--------|
| #9 | Trinity | Staff chat boundary, Spring AI wiring, safe-decline policy | Completed |
| #10 | Trinity | Owner/pet lookup, query-boundary extension, tool wiring | Completed |
| #11 | Trinity | Ambiguity cap (5), absence admissions, narrowing logic | Completed |
| #12 | Switch | Integration evidence (6-prompt fixture), H2 wording-tolerant assertions | Completed |

---

## Cross-Agent Contributions

### Trinity (Backend Dev)

- **#9:** Established chat controller, `@SessionScope` session history holder, system-prompt safe-decline enforcement (mutation, medical advice, out-of-scope), Spring AI 2.0.1 via OpenAI-compatible starter integration, `AssistantChatSession` wiring with tools, heuristic activity trace.
- **#10:** Extended `ClinicQueryService` with `findByLastNameContainingIgnoreCase` and `findByPetNameContainingIgnoreCase` JPQL queries (LEFT JOIN FETCH, DISTINCT to avoid N+1). Added `ownerName` field to `PetRecord`. Tool wiring via `@Tool` on session. Query-boundary unit tests (`@DataJpaTest`).
- **#11:** Capped results at `MAX_CANDIDATES = 5`. Added explicit `AMBIGUOUS RESULTS` and `ABSENT RESULTS` sections to system prompt. Updated `buildTrace` to reflect ambiguity and absence states heuristically. Seam tests 10–12 covering cap, identifying details, absence, and non-guessing.
- **Spec review:** Verified #9–#11 against all acceptance criteria. Confirmed system prompt policy compliance, query boundary contract integrity, trace accuracy.

### Switch (Tester)

- **#12:** Built 6-prompt integration evidence fixture using `@SpringBootTest` + deep-stub `ChatClient.Builder` (no live Azure call). Implemented `assertContainsAny` wording-tolerant assertion helper. Used H2 seed "Davis" ambiguity without seed changes. Managed `@SessionScope` isolation via explicit `MockHttpSession` per test.
- **i18n sync:** Fixed `I18nPropertiesSyncTest` breakage by adding six new `assistant.*` key translations to all nine locale files. Documented sync discipline as test-enforced convention.
- **Concurrency test fix:** Diagnosed and fixed `PetClinicConcurrencyTests` failure (RestTemplate factory incompatibility from Spring AI transitive deps). Applied fix: use explicit `SimpleClientHttpRequestFactory` in test harness to isolate HTTP redirect semantics from classpath changes. Test now stable.
- **Reviewed evidence:** Applied three post-review fixes:
  - **F5, F6 (trace assertions):** Updated safe-decline trace assertions to verify `activityTrace` wording-tolerantly against real production trace contract.
  - **F2b (owner-to-pet path):** Added direct integration test of `queryService.findOwnersByLastName("Franklin")` → Leo lookup to ground evidence in the real path.
  - **Cap-after-flatten:** Added test proving cap is applied after cross-owner pet flattening, not before.

### Morpheus (Lead)

- **Architecture/seams reconnaissance:** Mapped the three-seam layering (read-only query boundary, Spring AI adapter, staff chat controller). Confirmed existing `ClinicQueryService` + records scaffold alignment. Documented Foundry endpoint config constraints and integration-test strategy (mock/stub AI, deterministic H2 contracts).
- **Concurrency investigation:** Diagnosed Spring AI transitive classpath effect on `RestTemplate` redirect handling. Classified root cause: reactor-netty factory on classpath changes redirect semantics from JDK default. Proved: product behavior (duplicate-race protection) unchanged; test harness assumption broken. Proposed fix (test-only, explicit factory).
- **Standards review:** Verified three high-confidence defects post-implementation:
  - **F1 (pet cap):** `.limit(MAX_CANDIDATES)` applied before pet flatten → capacity overflow. Moved to after flatten.
  - **F2 (trace fabrication):** `buildTrace` inferred lookups from keywords even when no tool ran. Refactored to ground trace in real execution (explicit `ToolInvocation` recording, no inference).
  - **F3 (stale Javadocs):** Repository docs claimed a non-existent 20-result cap. Updated to clarify: repository returns all matches; service applies `MAX_CANDIDATES`.

---

## Verification

- **Full test suite:** `./mvnw test` PASSED after all review fixes.
  - `ClinicQueryServiceTests`: 22/22 ✓
  - `ClinicAssistantEvidenceTests`: 17/17 ✓
  - `AssistantControllerTests`: 6/6 ✓
  - `SystemPromptTests`: 6/6 ✓
  - Concurrency + all other legacy tests: ✓
  - Total: all green.
- **Review-gates:** All findings addressed in-scope. Residual risks (auth, authz, privacy, auditing, prompt-injection, production observability, persistent conversations, medical advice at inference time) documented as explicitly out of scope for the workshop slice.
- **Workspace state:** Unrelated pre-existing changes (e.g., chickens image, script updates, agent scaffolding) left intentionally unstaged. Only the `.squad/` decision merges are staged for commit.

---

## Decision Merges

The following inbox entries are merged into `.squad/decisions.md` in the **Accepted** section:

### Trinity Decisions

- **DEC-T9-001:** Spring AI 2.0.1 via OpenAI-compatible starter (API-key auth, not Azure-specific)
- **DEC-T9-002:** Session-scoped `AssistantChatSession` for non-persistent conversation history
- **DEC-T9-003:** Framework-agnostic read-only query boundary with purpose-built records
- **DEC-T9-004:** Heuristic activity trace (keyword-based, not LLM-generated) [SUPERSEDED by Morpheus F2]
- **DEC-T9-005:** Safe declines enforced by system prompt, not application code
- **DEC-010-A:** JPQL contains-match queries on `OwnerRepository` (LEFT JOIN FETCH, DISTINCT for N+1 avoidance)
- **DEC-010-B:** `PetRecord.ownerName` field for self-contained pet lookup results
- **DEC-010-C:** Tool wiring via `@Tool` on `AssistantChatSession` (co-located with boundary)
- **DEC-010-D:** `@DataJpaTest` + `@Import(ClinicQueryService.class)` for query-boundary unit tests
- **DEC-T11-001:** Result cap set to 5 (`MAX_CANDIDATES` constant for prod + test consistency)
- **DEC-T11-002:** Ambiguity and absence instructions in system prompt (policy layer, not tool-level)
- **DEC-T11-003:** `buildTrace` updated for ambiguity/absence states [SUPERSEDED by Morpheus F2]
- **DEC-T11-004:** Tests for cap, identifying details, absence, and non-guessing (Seams 10–12)

### Switch Decisions

- **DEC-S12-001:** Integration evidence via `@SpringBootTest` + deep-stub `ChatClient.Builder` (no live endpoint required)
- **DEC-S12-002:** Wording-tolerant `assertContainsAny` helper for LLM-generated text assertions
- **DEC-S12-003:** H2 seed "Davis" ambiguity (Betty + Harold) for fixture; no seed changes needed
- **DEC-S12-004:** `@SessionScope` isolation via explicit `MockHttpSession` per test
- **DEC-I18N:** i18n sync discipline: all new `messages.properties` keys must have locale translations (test-enforced)
- **DEC-CONC-TEST:** Integration tests using `RestTemplate` must use explicit `SimpleClientHttpRequestFactory` to isolate from classpath auto-configuration

### Morpheus Decisions (Post-Review, Accepted Fixes)

- **DEC-F1:** Pet-name cap moved to after cross-owner flatten (not before), preserving owner details
- **DEC-F2:** Activity trace refactored to ground in real execution (`ToolInvocation` recording) rather than fabricate from keywords
- **DEC-F3:** `OwnerRepository` Javadocs corrected: repository returns all matches; `ClinicQueryService.MAX_CANDIDATES` applies the cap

### Architectural Decision (Morpheus Reconnaissance, Confirmed by Delivery)

- **DEC-001 (confirmed):** Three-seam layering for Clinic Assistant:
  1. Read-only query boundary (records-based facade)
  2. Spring AI adapter (Spring-AI-aware tool/chat wiring)
  3. Staff chat web controller (form POST, session history, navbar integration)
- Package: `org.springframework.samples.petclinic.assistant` (cohesive, framework-independent)
- Integration test strategy: mock/stub AI, deterministic H2 contracts, no live Foundry calls in CI
- Residual risks documented (out of scope for workshop slice): auth, authz, privacy, auditing, prompt-injection, observability, persistent conversations, medical advice

---

## Outcome

**Status:** Completed  
**Result:** All 12 acceptance criteria (Issues #9–#12) met and verified by full test suite and independent code review.  
**Commit:** eff15d8 on main.  
**Decisions status:** All inbox entries reviewed, cross-agent findings integrated, residual risks recorded. Ready for human Acceptance Gate and Learning Gate.


## Issue #13: Percy Parrot GUI & Startup Diagnostics — Completed

**Date:** 2026-08-25 14:43 UTC  
**Agents:** Trinity (backend), Tank (UI), Switch (testing), Morpheus (review/fix)  
**Scope:** Staff-facing diagnostic UI, Percy persona, startup validation, `/oups` removal  
**Status:** COMPLETED — Full suite 142/142 tests passed, 0 failures

### Sequence

1. **Trinity (backend):** Implemented `AssistantModelConfiguration` with startup API-key validation (initially throw; revised by Morpheus to warn-not-throw). Removed `/oups` from navbar. Added controller test for service-unavailable case.

2. **Tank (UI):** Integrated Percy parrot GUI from wayfinder-proto artifact into `assistant/chat.html`. Added CSS pseudo-element for parrot emoji, intro bubble, i18n keys across all 10 locale files.

3. **Switch (testing):** Added `AssistantParrotGuiTests` (17 MockMvc tests) covering parrot identity, bubble visibility, turn rendering, service-unavailable signal, `/oups` exclusion.

4. **Morpheus (review):** Identified startup throw blocking inherited tests; revised `AssistantModelConfiguration` to constructor injection + warn-not-throw. Updated `AssistantModelConfigurationTests` to use constructor directly (no reflection). Verified `/oups` remains a separate demo route, not assistant error path.

### Evidence

- **Test coverage:** 142 passed (17 parrot GUI, 2 model config, 6 controller, 17 evidence, plus all legacy)
- **Build:** `./mvnw test --no-pager` clean  
- **Git diff:** Checked; committed awaiting final acceptance gate
- **Residual:** Azure endpoint/model/quota/credential live reachability not proven; placeholder-key warning is log-only

### Human Acceptance Gate

Residual risks documented. Issue #13 remains open pending human confirmation of acceptable residual gaps.

