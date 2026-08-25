# Fix — Issue #13: AssistantModelConfiguration startup throw & reflection tests

**Date:** 2026-08-25
**Author:** Morpheus (Lead, independent revision)
**Status:** Applied

---

## Defects addressed

### D1: @PostConstruct throw blocks unrelated tests

**Problem:** `AssistantModelConfiguration.logStartupConfiguration()` threw `IllegalStateException` when the API key was the default placeholder `changeme`. Any `@SpringBootTest` without a real key (including `PetClinicIntegrationTests` and `PetClinicConcurrencyTests`) failed to start the context.

**Fix:** Replaced `throw` with `logger.warn(...)` + `return`. The warning message is prominent and includes the remediation step. The application starts; assistant requests still fail honestly via `AssistantChatSession`'s existing `catch` → "service call failed" path. No false success is possible.

**Rationale:** The safety envelope says "assistant requests must retain the honest service-unavailable/no-data behavior." Preventing startup exceeds that contract — it blocks the entire Inherited System, not just the assistant feature.

### D2: Reflection-based test injection

**Problem:** `AssistantModelConfigurationTests` used `Field.setAccessible(true)` to inject private `@Value` fields. This bypasses Spring's actual injection mechanism and is fragile to field renames.

**Fix:** Converted `AssistantModelConfiguration` to constructor injection (`@Value` on constructor parameters). Tests now use the public constructor directly — the same seam Spring uses. No reflection, no `setAccessible`. The placeholder test now asserts that `logStartupConfiguration()` completes without throwing (matching the D1 behavioral change).

### D3: `/oups` remains a separate generic error demo

**Evidence:** `/oups` is intentionally absent from navbar (layout.html comment, lines 61–65). `CrashController` maps `GET /oups` as a deliberate exception-throwing demo. Deterministic test evidence already exists:
- `AssistantParrotGuiTests.oupsRouteNotPresentInAssistantPage` — asserts no `href="/oups"` link on GET /assistant
- `AssistantParrotGuiTests.oupsRouteNotPresentAfterServiceUnavailable` — asserts no `/oups` link after a service failure POST
- `CrashControllerIntegrationTests` — asserts `/oups` returns 500 with expected error body (JSON + HTML)

No changes needed; the route is documented and tested as a separate demo, not an assistant error path.

---

## Files changed

| File | Change |
|------|--------|
| `AssistantModelConfiguration.java` | Constructor injection; warn-not-throw on placeholder key |
| `AssistantModelConfigurationTests.java` | Constructor-based instantiation; no reflection; updated assertion for warn behavior |

## Verification

- Focused: `AssistantModelConfigurationTests` 2/2 ✓, `AssistantParrotGuiTests` 17/17 ✓, `CrashControllerIntegrationTests` 2/2 ✓
- Full suite: `./mvnw test` — 142 passed, 0 failures, 2 skipped (MySQL)
