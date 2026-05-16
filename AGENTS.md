# jetbrains-opencode

IntelliJ IDEA plugin that integrates [OpenCode](https://github.com/anomalyco/opencode) CLI into IDE terminals.

## Commands

| Command | Purpose |
|---------|---------|
| `./gradlew buildPlugin` | Full build |
| `./gradlew runIde` | Launch dev IDE instance |
| `./gradlew check` | Run tests |
| `./gradlew verifyPlugin` | Plugin Verifier (intellijPlatform compat checks) |
| `./gradlew publishPlugin` | Publish to JetBrains Marketplace |

## Architecture

- Single-module Gradle project (Kotlin 2.2.20, Gradle 9.5)
- Package: `com.github.wenzewoo.opencode.*`
  - `actions/` — IDE actions + intentions (editor/project view popup menus)
  - `events/` — SSE listener, event handler
  - `launcher/` — OpenCode CLI process management
  - `services/` — `OpenCodeService` (session fetch, prompt send via HTTP)
  - `settings/` — `OpenCodeSettings` (persistent state), `OpenCodeConfigurable`
  - `views/` — tool window, inline chat, session picker
  - `utils/` — `FileRefUtils`
- Plugin ID: `com.github.wenzewoo.jetbrains-opencode`
- Depends on `com.intellij.modules.platform` + `org.jetbrains.plugins.terminal`
- Resource bundle: `messages.MessageBundle` (`.properties` + `_zh`)

## Key conventions

- Use `intellijPlatform` Gradle DSL (not legacy `intellij` extension)
- `OpenCodeSettings` is an `ApplicationService` (`PersistentStateComponent`), state stored in `OpenCodeSettings.xml`
- OpenCode binary resolved via: settings `cliPath` → `$SHELL -l -c which opencode` → throws
- All HTTP calls to OpenCode TUI API run on pooled thread, results dispatched to EDT via `invokeLater`
- `ToolWindowFactory` registers factory class in `plugin.xml` under `<extensions>` (not `toolWindow` action)
- Actions registered in `<actions>` block, intention actions in `<extensions><intentionAction>`
- All `update()` overrides use `ActionUpdateThread.BGT` (background thread, not EDT)
- `InlineChatIntentionAction` overrides `startInWriteAction() = false` — no write lock needed
- `SessionMode` sealed class: `New`, `Continue`, `Fork(sessionId, title)`, `History(sessionId, title)`
- Terminal port and session metadata tracked via `content.putUserData(OPENCODE_PORT_KEY / SESSION_ID_KEY / SESSION_TITLE_KEY)`
- Terminal widgets tracked in a `WeakHashMap<Project, TerminalWidget>`
- Launch command built on pooled thread → terminal widget created on EDT (`install()` → `installTerminal()`)
- Escape key forwarded to terminal TTY via both `CustomShortcutSet` (when terminal component focused) and global `KeyEventDispatcher` (when focus is inside terminal)
- SSE listener retries up to 30 times (2s interval, ~60s) to establish connection to OpenCode port
- File ref format: `@relative/path` (no selection), `@relative/path:line` (single line), `@relative/path:start-end` (range)

## Testing

- No tests exist yet (`src/test/kotlin/` is empty)
- Test framework: JUnit 4 (via `libs.junit`)
- IDE test framework: `TestFrameworkType.Platform`
- Logs: `.intellijPlatform/sandbox/*/*/log-test/idea.log`

## Gotchas

- Requires `opencode` CLI installed externally; not bundled
- `buildSearchableOptions = false` — skip options indexing during build
- Configuration cache enabled (`org.gradle.configuration-cache=true`)
- `.intellijPlatform/`, `.gradle/`, `.idea/` are gitignored
- JetBrains Plugin Verifier runs via `verifyPlugin` task, not as part of `check`
- Skill file at `.agents/skills/jetbrains-plugin-development/SKILL.md` for IntelliJ Platform API guidance
