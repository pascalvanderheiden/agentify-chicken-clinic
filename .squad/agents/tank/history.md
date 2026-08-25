# Tank — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Infra/DevOps
- **Joined:** 2026-08-25T08:20:21.359Z

## Learnings

<!-- Append learnings below -->

---

## Assistant 404 Diagnosis (2026-08-25)

**Finding:** Two confirmed root causes for "service unavailable" on POST /assistant:

1. **Wrong Spring AI starter / URL path mismatch.** `spring-ai-openai` sends requests to `<base-url>/v1/chat/completions`. Azure OpenAI (`*.openai.azure.com`) expects `/openai/deployments/<name>/chat/completions?api-version=...`. This produces a **404** from Azure — the exact error the user sees.

2. **No API key set + `disableLocalAuth: true`.** Infra never sets `AZURE_OPENAI_API_KEY`; `application.properties` defaults it to `changeme`. The Foundry resource disables local auth, so every request is rejected 401/403.

**Key learning:** `spring-ai-openai` and `spring-ai-azure-openai` are different starters with incompatible URL conventions. Using the generic one against an Azure OpenAI endpoint is a silent misconfiguration — the app starts fine but every AI call 404s. Always match the starter to the Azure service type. Also: `disableLocalAuth: true` is correct Azure posture but requires managed identity auth in the client (Azure OpenAI starter, not generic OpenAI starter).

**Decision filed:** `.squad/decisions/inbox/tank-diagnose-assistant-404.md`

---

## Azure CI/CD Workflow (2026-08-25)

**Work done:** Created `.github/workflows/deploy-azure.yml` — OIDC-authenticated `azd up` on push to main.

**Key learnings:**
- `azd auth login --federated-credential-provider github` reuses the OIDC token already obtained by `azure/login`; no extra credential needed.
- A merged PR produces a push to main — a single `push: branches: [main]` trigger covers both direct commits and merged PRs with no duplicate runs.
- The local AZD env name (`workshop-preflight-20260824125634`) is timestamped and not portable to CI; always parameterise `AZURE_ENV_NAME` via a repository variable.
- `concurrency: cancel-in-progress: true` is essential; without it, two rapid pushes can deploy out-of-order or race on the same App Service slot.
- `deployment-plan.md` correctly identified the gap ("deployment workflow targets only k8s/**"); this workflow closes it. Plan status is a human gate — not changed.

**Residual items for human decision:**
- Create OIDC app registration + federated credential in Azure (documented in inbox decision).
- Set repo secrets (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`) and variables (`AZURE_ENV_NAME`, `AZURE_LOCATION`).
- Decide whether to keep the `production` GitHub Environment for approval gates.
- Consider adding a test gate before deploy.

---

## Azure Identity/Role Fix (2026-08-25)

**Task:** Inspect infra for Option A (Spring AI Azure OpenAI + managed identity). Implement smallest Bicep changes to fix role and deployment name exposure.

**Finding:** No Bicep change required. All three conditions are already satisfied:
1. System-assigned managed identity on the App Service — ✅ present
2. Foundry User role (`53ca6127-db72-4b80-b1b0-d745d6d5456d`) assigned to that identity at the Foundry resource scope — ✅ present (confirmed by `.workshop-evidence/preflight-20260824T132449Z.md`)
3. `AZURE_OPENAI_ENDPOINT` and `AZURE_OPENAI_DEPLOYMENT` emitted as app settings — ✅ present

**Key learning:** The preflight evidence file is the ground truth for deployed role state. The "Foundry User" role grants data-plane inference without a key when `disableLocalAuth: true`. The infra emits `AZURE_OPENAI_DEPLOYMENT=gpt-5-4-mini` (deployment name) which is what Spring AI Azure OpenAI needs for `deployment-name`; `AZURE_OPENAI_MODEL=gpt-5.4-mini` is the model family name and informational only for the Azure provider.

**Fix location:** Trinity's Java/config domain. The resolved implementation keeps `spring-ai-starter-model-openai:2.0.1` (no separate Azure starter — `spring-ai-azure-openai-spring-boot-starter` was considered but 2.0.1 is not in Maven Central) and activates Azure mode via `spring.ai.openai.microsoft-foundry=true`, `spring.ai.openai.microsoft-deployment-name`, and explicit `com.azure:azure-identity:1.18.2` dependency. User selected managed identity Option A. Live Azure acceptance remains open (human Acceptance Gate).

**Decision filed:** `.squad/decisions/inbox/tank-azure-identity-fix.md`

---

## Issue #13 — Percy Parrot GUI Integration (2026-08-25)

**Work done:** Integrated the parrot prototype visual into `assistant/chat.html`.

**Key decisions:**
- Parrot emoji rendered via CSS `::before` (`content: "\1F99C"`) to satisfy `I18nPropertiesSyncTest` which rejects literal emoji text in HTML elements.
- Percy intro bubble always visible; conversation uses staff (right/blue) and Percy (left/yellow-border) chat bubbles with activity trace in amber dashed box.
- Added `assistant.intro` key to all 10 locale files (sync test enforces this).
- No JavaScript added; animation is CSS-only keyframe on heading icon.
- Preserved GET/POST contract, `turns` model attribute, `activityTrace`, responsive layout, and `aria-live`/accessible labels.

**Tests verified:** `AssistantControllerTests` 7/7 ✓, `I18nPropertiesSyncTest` 2/2 ✓

**Residual risks:** Per-turn pop-in animation omitted (requires JS); Percy visual degrades gracefully if CSS fails to load (decorative only).

**Files owned:** `chat.html`, `petclinic.css`, `messages*.properties`
