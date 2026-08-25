# Trinity — Azure Provider Fix Decision Record

**Date:** 2026-08-25
**Author:** Trinity (Backend Dev)
**Status:** Implemented — human Acceptance Gate required
**Related:** Tank diagnosis `tank-diagnose-assistant-404.md`, Commitment Gate Option A

---

## Decision

Replace the generic Spring AI OpenAI API-key-based configuration with the Azure
managed-identity integration built into `spring-ai-starter-model-openai:2.0.1`.
No dependency swap required — the existing starter already contains Azure support via
`AzureInternalOpenAiHelper` and `OpenAiSetup.azureAuthentication()`, which activate when:

1. The endpoint URL contains `openai.azure.com` (auto-detected by `OpenAiSetup`)
2. `spring.ai.openai.microsoft-foundry=true` is set
3. `com.azure:azure-identity` is on the classpath (optional dep in spring-ai-openai pom)

---

## Root cause addressed (from Tank's diagnosis)

| Tank hypothesis | Fix applied |
|----------------|-------------|
| H1 — Missing `AZURE_OPENAI_API_KEY` | Removed — managed identity replaces API-key auth |
| H2 — Wrong model vs deployment name | Fixed — `microsoft-deployment-name` reads `AZURE_OPENAI_DEPLOYMENT` (deployment name, not model family) |
| H3 — Wrong URL path (`/v1/chat/completions` vs Azure deployment path) | Fixed — `microsoft-foundry=true` activates `AzureUrlPathMode` routing in `OpenAiSetup` |

---

## Changes made

### `pom.xml`
- Added `com.azure:azure-identity:1.18.2` (the version declared as optional in `spring-ai-openai:2.0.1`'s own pom)

### `build.gradle`
- Added same `azure-identity:1.18.2` dependency

### `src/main/resources/application.properties`
- Removed: `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`
- Added: `spring.ai.openai.microsoft-foundry=true`
- Added: `spring.ai.openai.microsoft-deployment-name=${AZURE_OPENAI_DEPLOYMENT:gpt-5-4-mini}`
- Kept: `spring.ai.openai.base-url=${AZURE_OPENAI_ENDPOINT:...}`

### `AssistantModelConfiguration.java`
- Removed `api-key` and `model` parameters; constructor now takes `endpoint` and `deploymentName`
- Replaced `PLACEHOLDER_KEY = "changeme"` sentinel with `UNSET_ENDPOINT` sentinel (checks endpoint, not key)
- `logStartupConfiguration()`: warns when endpoint is the local-dev placeholder; logs INFO otherwise
- No fake/stub key injected anywhere

### `AssistantModelConfigurationTests.java`
- Updated to match new two-parameter constructor: `(endpoint, deploymentName)`
- `logsWarningWithoutThrowingWhenEndpointIsUnset()` covers the placeholder-endpoint path
- `logsInfoWithoutThrowingWhenEndpointIsConfigured()` covers the configured-endpoint path
- Both tests are no-Spring-context, deterministic, no reflection

### `ClinicAssistantEvidenceTests.java`
- Updated `@SpringBootTest(properties=...)` to remove `api-key` stub, add `microsoft-foundry=true` and `microsoft-deployment-name=stub-deployment`

---

## Evidence from local Maven inspection (no Azure access required)

Inspected via `javap -verbose` and `jar tf`:

- `spring-ai-openai:2.0.1` contains `AzureInternalOpenAiHelper` using `DefaultAzureCredentialBuilder`
- `AbstractOpenAiProperties` has `getMicrosoftFoundryServiceVersion()`, `isMicrosoftFoundry()`, `getMicrosoftDeploymentName()`
- `OpenAiSetup` has `azureAuthentication()`, `resolveAzureUrlPathMode()`, `detectModelProvider()` — all internal wiring for Azure
- `OpenAiSetup` message: "Microsoft Foundry was detected, but no credential was provided. If you want to use passwordless authentication, you need to add the Azure Identity library..."
- `azure-identity:1.18.2` declared as `<optional>true</optional>` in `spring-ai-openai`'s pom — confirms explicit dep is required
- `azure-identity:1.15.2` already in local Maven cache; `1.18.2` downloaded via `./mvnw dependency:resolve`

---

## What is NOT verified

- **Live Azure call**: No live Azure/Foundry request was made. Success of managed identity
  auth depends on the App Service having the correct managed identity + RBAC role assigned
  to the Azure OpenAI resource. This is Tank's infra responsibility and was already in
  place per `tank-azure-cicd.md` / `resources.bicep`.
- **`disableLocalAuth: true`** behavior: confirmed by Tank's diagnosis; code does not
  attempt API-key auth, so this path is no longer triggered.

---

## Test results

```
Tests run: 2 — AssistantModelConfigurationTests (PASS)
Tests run: 6 — SystemPromptTests (PASS)
Tests run: 17 — ClinicAssistantEvidenceTests (PASS)
Tests run: 22 — ClinicQueryServiceTests (PASS)
Tests run: 7 — AssistantControllerTests (PASS)
Total: 54, Failures: 0, Errors: 0
BUILD SUCCESS
```

---

## Acceptance Gate (human decision required)

- [ ] Human verifies that `POST /assistant` with a real message returns an AI response in
      the deployed App Service (no "service unavailable")
- [ ] Human confirms managed identity + RBAC are in place (Tank's scope)
- [ ] Human records acceptance or residual gap in `.squad/decisions.md`
