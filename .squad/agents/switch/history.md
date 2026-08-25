# Switch — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Tester
- **Joined:** 2026-08-25T08:20:21.359Z

## Learnings

<!-- Append learnings below -->

### 2026-08-25 — Issue #12: Six-prompt evidence fixture implemented

**Observed:**
- Spring Boot 4.x moved `@AutoConfigureMockMvc` to `org.springframework.boot.webmvc.test.autoconfigure` — the old `org.springframework.boot.test.autoconfigure.web.servlet` package does not exist.
- `ChatClient.ChatClientRequestSpec.messages()` has two overloads (varargs + List); `any()` is ambiguous — use `anyList()` for the List overload.
- `AssistantChatSession` is `@SessionScope`: each `MockMvc` test that creates a new `MockHttpSession` gets a fresh session bean, avoiding cross-test state pollution.
- H2 seed already has two Davis owners (Betty and Harold) — no seed modification needed for the ambiguity fixture.
- `RETURNS_DEEP_STUBS` Mockito mock on `ChatClient.Builder` works end-to-end: the same stub instance is returned from `builder.build()` each time, so `willReturn(…)` on the chain affects all subsequent calls.
- Spring javaformat must pass before surefire runs — always run `spring-javaformat:apply` before testing new files.

**Outcome:**
- `ClinicAssistantEvidenceTests.java` created: 16 tests covering N1–N4 (navigation/lifecycle), F1–F6 (six-prompt fixture), R1–R3 (read-only boundary), T1 (activity trace).
- All 49 assistant tests green (16 evidence + 21 query service + 6 controller + 6 system prompt).
- No production files modified; no seed changes needed.

**Decisions recorded:** `.squad/decisions/inbox/switch-issue-12.md`

### 2026-08-25 — Reconnoiter: existing conventions and verification design

**Observed:**
- All controller tests use `@WebMvcTest(XController.class)` + `MockMvc` + `@MockitoBean`; annotated `@DisabledInNativeImage` + `@DisabledInAotMode`.
- Integration tests use `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `RestTemplate` against H2.
- Assertions: Hamcrest matchers in MockMvc chains; AssertJ elsewhere.
- Spring AI is **not yet in `pom.xml`** — Trinity must add it; tests need `@MockitoBean ChatClient` until then.
- No `assistant` package or templates exist yet — production code creation starts with Trinity.

**Design decisions:**
- Three test files planned: `ClinicAssistantControllerTests` (slice), `AssistantQueryBoundaryTests` (unit), `ClinicAssistantIntegrationTests` (integration, with stubbed LLM).
- All LLM-response assertions use wording-tolerant contains-any / does-not-contain patterns — never exact string match.
- Six-prompt evidence fixture maps to issues #9–#12 acceptance criteria.
- Primary coordination seam with Trinity: the `AssistantQueryBoundary` interface contract (method signatures + record shapes) must be settled before `AssistantQueryBoundaryTests` is written.

**Fragile observations:**
- H2 seed may need a "multiple Smiths" entry for ambiguity fixture #3 — verify once Trinity delivers the boundary.
- Live Azure/Foundry call must be stubbed in CI tests.

**Artifact:** `.squad/agents/switch/test-seams.md`

### 2026-08-25 — I18nPropertiesSyncTest regression fix

**Observed:**
- Six `assistant.*` keys added to `messages.properties` (by Trinity as part of Clinic Assistant UI work) were missing from all nine non-English locale files (`de`, `es`, `fa`, `hi`, `ja`, `ko`, `pt`, `ru`, `tr`).
- `I18nPropertiesSyncTest.checkI18nPropertyFilesAreInSync` enforces that every key in the base file exists in every non-English locale file (`messages_en.properties` is explicitly exempted).
- The fix is mechanical: append the six translated keys to each locale file. No production logic, no schema change.

**Outcome:**
- All nine locale files updated; `I18nPropertiesSyncTest` passes.
- Convention: whenever new i18n keys are added to `messages.properties`, they must be simultaneously added to every locale file to keep the sync test green.

**Decision recorded:** `.squad/decisions/inbox/switch-i18n-sync.md`

### 2026-08-25 — Post-review evidence strengthening (three review findings)

**Review findings addressed:**

1. **Safe-decline trace assertions (F5, F6):** Review found traces were not asserted, only reply wording. Production `buildTrace` is invocation-based: turns that call no tool produce `"Answered without querying clinic data; no lookup tool was invoked."` — a keyword-based assertion would have been wrong. Added `assertContainsAny(trace, "no lookup tool", "without querying clinic data", "no clinic data")` to both F5 and F6.

2. **Owner-name-to-pet lookup path (F2b):** Review found F2 stubbed a canned reply without verifying the real `findOwnersByLastName("Franklin")` → `OwnerRecord.pets` path. Added `fixture2OwnerNameToPetLookupPath` to `ClinicAssistantEvidenceTests` using the real autowired `ClinicQueryService` + H2 — no additional stub needed. Asserts George Franklin → Leo with ownerName containing "Franklin".

3. **Cap after flattening:** Review found `findPetsByNameCapsCandidatesAtMaxCandidates` used `"a"` (non-deterministic) without proving flatMap is capped post-expansion. Added `findPetsByNameCapIsAppliedAfterFlatteningAcrossOwners` using `"Lucky"` (two owners in H2 seed), asserting size ≤ MAX_CANDIDATES, all records match, and ≥ 2 distinct ownerNames.

**Key observation:** `buildTrace` was updated by Trinity to be invocation-based (not keyword-based from message text). This is a stronger, non-fabricating contract. Any trace assertion must match the actual invocation-derived strings, not the old keyword-inferred strings.

**Outcome:** 39 tests green (17 evidence + 22 query service). No production code modified.

**Decision recorded:** `.squad/decisions/inbox/switch-reviewed-evidence.md`


**Observed:**
- Adding Spring AI to `pom.xml` transitively brings WebFlux/Reactor onto the classpath.
- Spring Boot's `RestTemplateBuilder` auto-detects `ReactorClientHttpRequestFactory` when Reactor is present and uses it as the default request factory.
- `ReactorClientHttpRequestFactory` re-POSTs 302 redirects (unlike `HttpURLConnection`), causing the controller's POST-Redirect-GET success path to return 405 instead of the redirected GET page.
- The original test success check (`is2xxSuccessful() && !body.contains("is already in use")`) relied on the redirect being followed to a 200 — both threads went to `failureCount` under the new factory, giving `successCount = 0`.
- `SimpleClientHttpRequestFactory` (JDK `HttpURLConnection`) does NOT follow POST redirects either — it returns the raw 302 with no body.
- However `HttpURLConnection` correctly reports 302 (not 405), so the fix is to treat a 302 response as "save succeeded" (POST-Redirect-GET semantic), which is correct: the controller redirects only on successful save.

**Fix applied:**
- Removed `@Autowired RestTemplateBuilder` and direct `RestTemplate` construction using `new RestTemplate(new SimpleClientHttpRequestFactory())` with an absolute base URL.
- Updated the success condition: `status == 302 || (2xx && !body.contains("is already in use"))`.
- No production code changed; no assertions weakened (still asserts exactly 1 success, exactly 1 pet added, exactly 1 pet with the duplicate name).

**Outcome:**
- `PetClinicConcurrencyTests.testDuplicatePetNameRaceConditionIsBlocked` passes: `successCount=1`, `failureCount=1`.
- Database unique-constraint guarantee validated unchanged.

**Convention:**
- Integration tests that use `RestTemplate` against a live server must explicitly construct it with `SimpleClientHttpRequestFactory` (or another deterministic factory) rather than autowiring `RestTemplateBuilder` — auto-configured factories change when new starters (e.g. Spring AI / WebFlux) are added to the classpath.
- POST-Redirect-GET success is a 302, not a 200; test success checks must account for both branches.

**Decision recorded:** `.squad/decisions/inbox/switch-concurrency-test.md`
