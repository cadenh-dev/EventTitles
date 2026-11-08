# AGENTS.md — EventTitles Guardrail Laws

These rules bind every human and AI contributor. A change that violates one is rejected at review.

## 1. Platform & Toolchain
- Paper API: `io.papermc.paper:paper-api:26.3.build.230-stable` (`compileOnly`; also `testImplementation`).
- **JDK: Java 25 toolchain.** The spec asked for Java 21, but Paper API 26.3 publishes Gradle module
  metadata with `org.gradle.jvm.version = 25`, so it cannot resolve on a 21 toolchain. Do not lower it.
- Descriptor: `src/main/resources/paper-plugin.yml`, `api-version: '26.3'`. Paper plugins cannot declare
  `commands:`; commands are registered in code with `JavaPlugin#registerCommand(..., BasicCommand)`.
- Build/verify: `./gradlew test build` (Gradle wrapper 9.5.1). Must pass with no new warnings.
- No new dependencies without the Lead Architect's approval. Gson + JUnit 5 are already available.

## 2. Formatting: Kyori Adventure MiniMessage only
- BANNED: `org.bukkit.ChatColor`, the `§` character, legacy `&` codes, `LegacyComponentSerializer`.
- All text is MiniMessage (`net.kyori.adventure.text.minimessage.MiniMessage`) → `Component`.
- Use Adventure APIs (`sender.sendMessage(Component)`, `TextDisplay#text(Component)`), not String setters.

## 3. Concurrency boundary
- `com.example.titles.api.*` (except `TitleDisplayService`), `com.example.titles.llm.*` and
  `com.example.titles.engine.*` must NEVER import `org.bukkit.*`, `io.papermc.*` or `com.destroystokyo.*`.
  Adventure (`net.kyori.*`) is allowed.
- Listeners snapshot Bukkit state on the main thread into an immutable `EventContext` *before* any async work.
- Any touch of `Player`, `Entity`, `TextDisplay`, PDC or world state from a future callback MUST be marshalled
  back with `Bukkit.getScheduler().runTask(plugin, ...)`, and must re-check `player.isOnline()` there.
- Futures returned by `TitleResolverService` / `LlmTitleGenerator` never complete exceptionally.
- No blocking calls (`join()`, `get()`, `send()`) on the main thread.

## 4. Length validation
- Visible length = `MiniMessage.miniMessage().stripTags(title).length()` — use `TitleText.visibleLength`.
- Truncation: `TitleText.truncateVisible(text, max)`; `max <= 0` means unlimited. Never hand-roll another.

## 5. Titles & lifecycle
- Priorities are 1–100. Replace only when `incoming.priority() >= current.priority()`.
- No timers may ever clear a title (no auto-decay).
- Source of truth = player PDC: `eventtitles:active_title` (STRING), `eventtitles:active_priority` (INTEGER).
- Display entities: `setPersistent(false)` (never saved to chunks) and tagged with PDC
  `eventtitles:display_owner` (STRING player UUID) so strays can be identified and purged.
- At most one display per player, ever. Every spawn path must first remove any existing one.

## 6. Ownership & contracts (do not change signatures without the Lead Architect)
Shared API lives in `com.example.titles.api` and is owned by the Lead Architect — workers must not edit it.

| Package | Class | Public constructor / contract |
|---|---|---|
| display | `PaperTitleDisplayManager implements TitleDisplayService` | `(Plugin plugin, Supplier<TitlesSettings> settings)` |
| display | `DisplayLifecycleListener implements Listener` | `(Plugin plugin, TitleDisplayService displays)` |
| llm | `ChatCompletionsTitleClient implements LlmTitleGenerator` | `(Supplier<TitlesSettings> settings)` and `(Supplier<TitlesSettings> settings, HttpClient http)` |
| engine | `StreakTracker implements StreakService` | `(Supplier<TitlesSettings> settings)` and `(Supplier<TitlesSettings> settings, Clock clock)` |
| engine | `StaticTitleRegistry` | `()`; `Optional<AwardedTitle> findStaticMatch(EventContext)`; `int size()` |
| engine | `HybridTitleResolver implements TitleResolverService` | `(StaticTitleRegistry registry, LlmTitleGenerator llm, Supplier<TitlesSettings> settings)` |
| listeners | `GameplayEventCollector implements Listener` | `(Plugin plugin, TitleResolverService resolver, TitleDisplayService displays, StreakService streaks)` |
| commands | `TitleAdminCommand implements CommandExecutor, TabCompleter, BasicCommand` | `(Plugin plugin, TitleDisplayService displays, LlmTitleGenerator llm, Supplier<TitlesSettings> settings, Runnable reloadAction)` |

`TitlesPlugin` (root package) is owned by the Lead Architect and wires everything together.

## 7. Style
- Java records/`final` fields for immutable data; no static mutable singletons.
- Log via `plugin.getLogger()` / `java.util.logging.Logger` — never `System.out`. Never log the API key.
- Each worker touches only the files in its work order.

## 8. LLM providers
- All providers speak the OpenAI-compatible `/chat/completions` protocol through the single
  `ChatCompletionsTitleClient`. Supported: `deepseek` (default; `DEEPSEEK_API_KEY`) and `grok`
  (xAI, `https://api.x.ai/v1/chat/completions`; `XAI_API_KEY`).
- Provider defaults (endpoint, model, key env var, temperature, timeout, JSON mode) live in
  `api/LlmProvider`; per-provider persona and token budget live in the client. Adding a provider =
  new enum constant + persona case + a `llm.providers.<id>` block in `config.yml`.
- `TitlesSettings` carries values already resolved for the active provider; nothing else may branch on
  provider-specific config keys.

## 9. Display positioning (supersedes the original passenger design)
- Title displays must NOT be mounted with `player.addPassenger`. Paper's `CraftPlayer#teleport0` returns
  `false` for cross-world teleports of any player with passengers *before* `PlayerTeleportEvent` fires,
  so a mounted title would silently break other plugins' teleports (Multiverse, /spawn, /home...).
- Instead `PaperTitleDisplayManager` runs one main-thread follow task (only while displays exist) that
  teleports each display to `player.getLocation() + player.getHeight()` every tick with
  `setTeleportDuration(1)` for client-side smoothing; moves over 8 blocks or across worlds respawn it.
- Titles are hidden while the player is in spectator mode.
- Vertical offset is `display.offset_y` (default 0.8, clamped to [-2, 5]) above the hitbox top, clearing
  the vanilla nametag (~+0.5). Owners don't see their own title (`display.show_to_self: false`, via
  `Player#hideEntity`); every despawn path must call `despawn(...)` so the hide-entry is cleared.
