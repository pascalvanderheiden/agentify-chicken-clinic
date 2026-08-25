# Morpheus — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Lead
- **Joined:** 2026-08-25T08:20:21.358Z

## Learnings

<!-- Append learnings below -->

### 2026-08-25 — Clinic Assistant seam reconnaissance (#9–#12)

- **Clean slice:** no AI code exists yet. PetClinic is Spring Boot 4.1.0, Java 17.
- **Build:** both `pom.xml` and `build.gradle` present; README says `./mvnw test` → Maven is authoritative. Spring AI dep must go in `pom.xml`; dual-build sync is an open question.
- Seam (1) read-only query boundary already scaffolded as `ClinicQueryService` + `OwnerRecord`/`PetRecord`/`VisitRecord`/`VetRecord`/`ChatTurn` (untracked). Matches recommended design. Seams (2) Spring AI adapter and (3) chat controller/template + navbar are still greenfield; no tests exist.
- **Web conventions:** controllers are package-private `@Controller` returning view names (see `WelcomeController`, `VetController`); navbar lives in `templates/fragments/layout.html`.
- **Test strategy:** `@WebMvcTest` + `MockMvc` + `@MockitoBean` (per `VetControllerTests`); `@DataJpaTest`/H2 for query impl. Model MUST be mocked in all tests; assert contract outcomes, tolerate wording.
- **Foundry:** Spring AI 2.0.0 OpenAI chat starter → Foundry OpenAI-compatible endpoint; model `gpt-4o-mini`/GlobalStandard; read pre-provisioned endpoint/deployment/key from env, never provision Azure. Spring AI not yet on classpath — needs `spring-ai-bom`; verify Boot 4.1 support empirically.
- **Fragile seam:** `OwnerRepository.findByLastNameStartingWith` is prefix + last-name only. #10 needs partial case-insensitive owner AND pet name match — new query methods required; no pet-name search method exists.
- Wrote proposed decision to `.squad/decisions/inbox/morpheus-clinic-assistant-seams.md`. No product files modified; no commit.

### 2026-08-25 — Concurrency test failure root cause (transitive classpath, not product)

- **Symptom:** full-suite fail `PetClinicConcurrencyTests.testDuplicatePetNameRaceConditionIsBlocked` `expected: 1 but was: 0` (line 124). Test is pre-existing (Initial commit), unmodified, not part of the assistant slice.
- **Method:** stash-toggle isolation. Clean HEAD → PASS (`Successful additions: 1`). With assistant changes, isolated, 3/3 runs → FAIL (`Successful: 0`, `Failed: 2`) but **`Final Pet Count` still 1→2**, so product dup-race protection (DB `unique_owner_pet_name`, schema.sql:55) is intact. Only the test's success *classification* breaks.
- **Root cause:** `spring-ai-starter-model-openai:2.0.1` transitively adds `spring-webflux` + `reactor-netty-http`. Boot's `RestTemplateBuilder` then picks `ReactorClientHttpRequestFactory` instead of the JDK `SimpleClientHttpRequestFactory`. Proven live: successful `POST /owners/{id}/pets/new` → **302 redirect**; reactor factory follows it by **re-POSTing** to `/owners/{id}` → **405** → `HttpClientErrorException` → counted as failure. Loser gets 200 + "is already in use" → also failure ⇒ `successCount=0`. JDK factory had followed as GET → 200 clean page ⇒ success.
- **Classification:** caused *indirectly* by assistant changes via classpath side effect; NOT pre-existing, NOT shared/env state, NOT a product regression.
- **Learning — durable:** Adding an AI/reactive starter can silently swap the RestTemplate HTTP client factory and change redirect semantics; brittle integration tests that assume `is2xxSuccessful()` after a controller redirect are exposed to this. Fix at the true seam: pin the RestTemplate request factory / redirect policy in the test harness (test-only), or exclude reactor-netty from the OpenAI starter. Proposed decision: `.squad/decisions/inbox/morpheus-concurrency-investigation.md`.
- No product files or tests modified; no commit. Scratch probes cleaned up.

### 2026-08-25 — Post-review revision of Clinic Assistant (3 high-confidence defects)

- **F1 candidate-cap bug:** `findPetsByName` limited *owners* before flattening → one owner with >5 matching pets could return >5 pets. Moved `.limit(MAX_CANDIDATES)` after the pet flatten/map. Owner details preserved.
- **F2 fabricated trace:** `buildTrace` inferred lookup/decline claims from user+reply keywords even when no tool ran — violates read-only evidence rule. Reworked to be grounded in real execution: each `@Tool` records a per-turn `ToolInvocation(kind, resultCount)` (list cleared at turn start); trace reports call-failure / no-tool-invoked / actual per-tool match counts. Narrowed broad `catch(Exception)` → `catch(RuntimeException)` with an explicit failure flag (no silent success fallback). Every turn still gets a concise non-blank trace.
- **Test coupling learning:** two evidence assertions (F5/F6 decline fixtures) were written to the *old fabricated* "declined" trace. Because the ChatClient is deep-stubbed, no tool actually executes for declines, so the honest grounded trace is "no lookup tool invoked". Updated those two trace assertions (reply-content decline checks untouched) — the test had encoded the very defect under review. Durable: when a trace is grounded in execution, tests that assert wording-derived trace strings must be realigned to execution reality.
- **F3 stale Javadoc:** `OwnerRepository` contains-queries claimed "capped at 20"; no cap in query, service caps at 5. Corrected both Javadocs to say capping is the service's responsibility (`MAX_CANDIDATES`).
- **Validation:** `spring-javaformat:apply` required (formatter gate runs before tests). Focused suite green: ClinicQueryServiceTests 22, ClinicAssistantEvidenceTests 17, AssistantControllerTests 6, SystemPromptTests 6.
- Only the 3 flagged production files + 2 directly-related test assertions changed. No unrelated user changes reverted; no commit. Decision: `.squad/decisions/inbox/morpheus-reviewed-assistant-fixes.md`.

### 2026-08-25 — Template validator: allow required Spring AI config, guard hardcoded secrets

- **Symptom:** `Validate Template` CI failed `template baseline invalid: Spring AI application property is present in src/main/resources/application.properties`. Cause: `application.properties` guard used blunt `grep -Fq 'spring.ai.'`, rejecting *all* Spring AI config — including the Clinic Assistant's *required* env-var-driven scaffolding (`spring.ai.openai.*=${ENV:default}`).
- **Contract found:** `docs/workshop/attendee-baseline.md` says the upstream *template* `main` must have no `spring.ai.*` (solution belongs on `reference/clinic-assistant`). But this repo committed the full Assistant to `main` (DEC-001, eff15d8/2171dc3), so the validator's *other* guards (assistant dir, spring-ai in pom/gradle, UI markers) also fire. Scoped task = fix only the `application.properties` guard; must NOT silence the others. Surfaced this tension in the decision rather than resolving the whole baseline.
- **Fix (smallest correct):** renamed `contains_spring_ai_application_property` → `contains_hardcoded_spring_ai_secret`. New regex flags a credential-bearing key (`spring.ai.*(api-key|secret|password|token)`) whose value is NOT a `${...}` placeholder (incl. commented lines); env-var-driven values pass. Message → `hardcoded Spring AI secret is present ...`. Guard is *sharper* (now covers secret/password/token, and commented literals), not weaker.
- **Red→green proven:** updated focused test asserts new message for the two hardcoded-key cases and adds a positive case (three required `${ENV:default}` lines must stay `expect_clean`). Against pre-fix validator the test fails at the exact reported message; against the fix the suite prints `template baseline validator tests passed`.
- **Env learning — durable:** macOS BSD `awk`/`sed`/`sha256sum` break the nested `validate-copilot-assets.sh` (GNU-only ternary awk) and the test's `sed -i`/hash checks. Pre-existing, unrelated to the change; CI is Ubuntu/GNU. Validate locally by prepending Homebrew `gawk`+`gnu-sed`+`coreutils` gnubin to PATH. Don't mistake these tool-compat failures for validator/product defects.
- **Design learning:** a "block everything matching X" baseline guard is fragile when part of X is legitimately required. Sharpen to the actual risk (a *literal committed secret*), key off the safe shape (`${...}` placeholder), so required scaffolding passes while the meaningful protection stays.
- Only the 2 validator/test files changed. No product behavior, deps, or other guards touched. No commit. Decision: `.squad/decisions/inbox/morpheus-template-validator.md`.

### 2026-08-25 — Issue #13 independent revision: warn-not-throw + constructor injection

- **D1 — @PostConstruct throw:** `AssistantModelConfiguration` threw `IllegalStateException` on placeholder API key, preventing Spring context startup for all `@SpringBootTest` tests without a live key. Replaced with `logger.warn` + early return. The assistant feature degrades gracefully (existing catch → "service call failed" in `AssistantChatSession`); the Inherited System starts normally.
- **D2 — Reflection tests:** Converted `@Value` fields to constructor parameters. Tests now call the public constructor directly — same seam Spring uses. Removed all `Field.setAccessible` reflection. Placeholder test updated to assert successful completion (matching D1 behavioral change).
- **D3 — `/oups` evidence:** Confirmed `/oups` is a separate generic error demo: absent from navbar, tested as such by `AssistantParrotGuiTests` (2 assertions) and `CrashControllerIntegrationTests` (2 tests). No scope creep; no changes needed.
- **Learning — durable:** A `@Configuration` class that throws from `@PostConstruct` on a missing optional credential blocks the entire application context, not just the feature. When the downstream call path already handles the failure honestly (service-unavailable), a prominent warn log is sufficient and preserves system availability. Constructor injection on `@Configuration` classes with `@Value` parameters is the natural testable seam — no reflection needed.
- Full suite: 142 passed, 0 failures. Decision: `.squad/decisions/inbox/morpheus-issue13-fix.md`.

### 2026-08-25 — Review: Azure CI/CD deploy workflow (Tank) — APPROVED

- **Scope reviewed:** `.github/workflows/deploy-azure.yml`, `azure.yaml`, `.azure/deployment-plan.md`. No files modified; no commit.
- **Request met:** Auto-deploy on push to `main` covers direct commits AND merged PRs (a merge is one push to main → no dupes). Uses existing AZD settings (`azure.yaml` bicep + `infra/`, unchanged). Verdict: meets the request.
- **AZD auth semantics — the key correctness check:** `azd auth login --federated-credential-provider github` needs `AZURE_CLIENT_ID` + `AZURE_TENANT_ID` in env and an OIDC-requestable runner. Both are set at job-level `env` (visible to the step); `permissions: id-token: write` provides `ACTIONS_ID_TOKEN_REQUEST_URL/TOKEN`. Correct, documented pattern. ✅
- **OIDC / no secrets:** No stored credentials; only client/tenant/subscription IDs (identifiers, not secrets) via GH secrets. `azure/login@v2` step is technically redundant for azd (azd does its own token exchange) but harmless — leave it. ✅
- **Least privilege:** `id-token: write` + `contents: read` only — minimal. RBAC (Contributor at subscription scope) lives outside the repo; noted as the one place to tighten later. ✅
- **Concurrency:** `group: deploy-azure-main`, `cancel-in-progress: true` — prevents overlapping deploys, latest wins. ✅
- **Consistency:** Java 21 in workflow matches `azure.yaml` Maven `prepackage` package step / plan's Java 21 App Service. ✅
- **Non-blocking observations (advisory only):** (1) no pre-deploy test gate — `azd up` ships without running the suite; (2) `azd up` re-provisions every push (idempotent but slower) — could switch to `azd deploy` post-first-provision; (3) `environment: production` is present but only gates if a GH Environment protection rule is configured. None block the request.
- **Learning — durable:** For AZD-on-GitHub OIDC, the correctness hinge is that `AZURE_CLIENT_ID`/`AZURE_TENANT_ID` are in scope at the `azd auth login` step and `id-token: write` is granted; the `azure/login` action is optional sugar. Verified here.
- Decision: `.squad/decisions/inbox/morpheus-azure-cicd-review.md`.
