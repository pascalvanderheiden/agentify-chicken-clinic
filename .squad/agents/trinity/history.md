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

### Gradle build / Spring AI dependency sync (2026-08-25)

- `build.gradle` is a parallel build file alongside `pom.xml`. Any dependency added to
  `pom.xml` must also be added to `build.gradle` — they are not automatically synced.
- Spring AI is **not** in the Spring Boot `io.spring.dependency-management` managed set.
  Declare the version explicitly (e.g. `ext.springAiVersion = "2.0.1"`) and reference it
  in the `implementation` line. No BOM is needed for a single starter.
- `./gradlew build` runs the full test suite; a compile failure in the `assistant` package
  confirms a missing dependency immediately.
