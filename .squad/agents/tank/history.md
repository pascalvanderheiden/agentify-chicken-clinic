# Tank — History

## Core Context

- **Project:** Add a read-only, staff-facing Clinic Assistant conversational agent to a Spring Boot 4.1 PetClinic using Spring AI 2.0 and Azure OpenAI/Foundry.
- **Role:** Infra/DevOps
- **Joined:** 2026-08-25T08:20:21.359Z

## Learnings

<!-- Append learnings below -->

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
