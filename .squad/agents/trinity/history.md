# Trinity — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Backend Dev
- **Joined:** 2026-08-25T08:20:21.358Z

## Learnings

### Issue #9 — Staff chat boundary (2026-08-25)

- Spring AI 2.0.1 `spring-ai-starter-model-openai` supports Azure OpenAI/Foundry
  endpoints via `spring.ai.openai.base-url` — no azure-specific starter needed for
  API-key-based auth.
- `@SessionScope` is the right scope for temporary conversation history: no extra
  infrastructure, survives the HTTP session, gone on expiry.
- Purpose-built records as a read-only query boundary keeps the AI layer fully decoupled
  from JPA entities — good pattern for any read-only agent boundary.
- Spring Java Format enforces strict formatting; always run `spring-javaformat:apply`
  before compile.
- Heuristic activity traces (keyword-based) cost nothing extra and meet the "concise trace
  line" criterion without an additional LLM call.
- `@WebMvcTest` with `@MockitoBean` on the session-scoped bean is sufficient for
  controller slice tests — no Spring AI wiring required in tests.

### Issue #11 — Ambiguity and absent records (2026-08-25)

- Cap results at 5 (`MAX_CANDIDATES` constant) rather than 20; expose as a package-visible
  constant so tests reference the same value without a magic number.
- System prompt needs both global policy sections (`AMBIGUOUS RESULTS`, `ABSENT RESULTS`)
  **and** updated `@Tool` descriptions for belt-and-suspenders coverage: the model sees the
  rule before and during invocation.
- `buildTrace` heuristic is extended to classify ambiguity (multiple matches → narrowing
  trace) and absence (empty list → not-found trace). Reply-text matching is fragile; a
  structured result envelope would be more robust but is out of scope here.
- Seam 10–12 tests are purely at the query-boundary layer (no LLM call), making them
  deterministic and fast. LLM-response assertions for narrowing/absence are Switch's
  responsibility via wording-tolerant patterns.
- When an existing test pins on a specific prompt phrase that the prompt legitimately
  changes, update the test to reflect the new, stronger contract — don't weaken the prompt
  to preserve the old test string.

### Issue #10 — Owner and pet lookup (2026-08-25)

- `@Query` JPQL with `LOWER(...) LIKE LOWER(CONCAT('%', :param, '%'))` is the right pattern
  repository implementation.
- `LEFT JOIN FETCH` with `DISTINCT` in a `@Query` prevents N+1 and duplicate rows when
  joining a collection (pets) — pair this with `.stream().limit(n)` in the service layer
  for safe result capping.
- Adding `ownerName` to `PetRecord` is the correct move for pet-search results: the record
  is self-contained and staff can identify the owner without a second lookup.
- Spring AI 2.0.1 `.tools(this)` accepts any object with `@Tool`-annotated methods — no
  `ToolCallback` wrapper needed. Co-locating tools on `AssistantChatSession` keeps the
  read-only boundary tight and avoids extra beans.
- `@DataJpaTest + @Import(ServiceClass.class)` is the right narrow slice for query
  boundary tests — loads JPA + H2 + data.sql, nothing else. Import is
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (not the old
  `boot.test.autoconfigure.orm.jpa` path used in Spring Boot < 4.x).

### Azure provider fix — managed identity (2026-08-25)

- `spring-ai-openai:2.0.1` already contains Azure managed-identity support via
  `AzureInternalOpenAiHelper` + `OpenAiSetup.azureAuthentication()`. No separate
  `spring-ai-azure-openai` starter needed (and that starter's 2.0.1 is not in Maven
  Central anyway — latest is 2.0.0-M4).
- To activate Azure/Foundry mode: set `spring.ai.openai.microsoft-foundry=true`,
  `spring.ai.openai.microsoft-deployment-name=<deployment>`, and add
  `com.azure:azure-identity` (optional dep in spring-ai-openai pom) explicitly.
- `OpenAiSetup` detects Azure by endpoint URL (`openai.azure.com`) and switches to the
  correct Azure deployment URL path — this fixes Tank's H2/H3 (wrong path, wrong name).
- Startup validation: sentinel is `UNSET_ENDPOINT` (default placeholder endpoint), not an
  API key. When endpoint is the placeholder, WARN; otherwise INFO with endpoint + deploy.
- `ClinicAssistantEvidenceTests` `@SpringBootTest(properties=...)`: remove `api-key` stub,
  add `microsoft-foundry=true` and `microsoft-deployment-name=stub-deployment`.
- Always inspect the actual jars (`javap -verbose`, `jar tf`) to know the real property
  names — Spring Boot kebab-case binding maps `microsoftDeploymentName` →
  `microsoft-deployment-name`. Do not guess from docs.
- `azure-identity` version: use the exact version declared in `spring-ai-openai`'s own pom
  (check with `unzip -p <jar> META-INF/maven/.../pom.xml | grep azure-identity`).



- `build.gradle` is a parallel build file alongside `pom.xml`. Any dependency added to
  `pom.xml` must also be added to `build.gradle` — they are not automatically synced.
- Spring AI is **not** in the Spring Boot `io.spring.dependency-management` managed set.
  Declare the version explicitly (e.g. `ext.springAiVersion = "2.0.1"`) and reference it
  in the `implementation` line. No BOM is needed for a single starter.
- `./gradlew build` runs the full test suite; a compile failure in the `assistant` package
  confirms a missing dependency immediately.

### Issue #13 — Startup diagnostics, /oups nav removal, service-unavailable tests (2026-08-25)

- `@Configuration` + `@PostConstruct` is the right pattern for startup validation in Spring Boot:
  it runs after all `@Value` fields are injected and before the app accepts requests, so a
  misconfigured deployment is detected immediately rather than on the first staff request.
- The API key must **never** be logged — only its placeholder state. Log endpoint + model at INFO
  for deployment evidence; log ERROR and throw `IllegalStateException` on `changeme`.
- `@WebMvcTest` slice does NOT load `@Configuration` classes by default; the new
  `AssistantModelConfiguration` does not affect `AssistantControllerTests` (no property override needed).
- Plain unit tests (no Spring context) using `Field.setAccessible(true)` are sufficient for
  deterministic placeholder-detection coverage — avoids spinning up a full context for a two-branch
  conditional.
- Removing a `<li>` nav entry from `layout.html` is safe to do with an explanatory comment;
  the route (`/oups`) remains technically reachable for workshop instructors but disappears from
  the staff navigation bar.
- The service-unavailable test at the controller layer should assert on the `turns` model attribute
  holding the actual failure-path `ChatTurn` (trace content included) rather than just asserting
  `attributeExists` — tighter contract, same test cost.
