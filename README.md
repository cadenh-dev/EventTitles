<img src="assets/icon.svg" width="64" height="64" alt="">

# EventTitles

A Paper plugin that gives players funny titles based on what just happened to them. The title floats
above their head and shows in chat and the tab list. Die to three creepers in a row and you're
**Creeper Magnet**; kill the Ender Dragon and you're **Dragonslayer**.

- **50 built-in trigger rules, 107 titles.** These cover deaths, boss kills, PvP, low-health clutches,
  mining and odd foods. Each trigger picks randomly from its own set of titles.
- **AI titles and chat comments (optional).** DeepSeek (cheap) or Grok (X-style roasts) writes a custom
  title plus a one-line chat comment about the event. If the AI is slow or down, a built-in title is used.
- **Titles stay** until a title of equal or higher priority replaces it, or an admin clears it. Nothing
  wears off on a timer.
- **Shown everywhere:** a floating title above the head, chat messages (`Creeper Magnet Caden: hi`),
  and the tab list. Hover a title in chat to see *why* the player got it.
- **Update notifications:** checks this repo's Releases and tells admins when a new version is out;
  optional verified auto-download that installs on the next restart (`updates.auto_download`).
- **Safe for your world:** it only writes three of its own `eventtitles:*` keys to player data, and the
  floating displays are never saved to disk.

## Requirements

- Paper **26.3**
- Java **25**

## Install

1. Download `EventTitles-x.y.z.jar` from [Releases](../../releases), or build it yourself (below).
2. Put it in `plugins/` and start the server once.
3. Edit `plugins/EventTitles/config.yml`, then run `/title reload`. No restart is needed.

### AI setup (optional)

Set `llm.provider` to `deepseek` or `grok`, then provide its API key in one of two ways:
either put it in `llm.providers.<provider>.api_key`, or (recommended) set an environment variable,
`DEEPSEEK_API_KEY` or `XAI_API_KEY`. With no key, the plugin still works using only the built-in titles.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/title info [player]` | `titles.info` (everyone) | Show a title; hover it for the reason |
| `/title set <player> <priority> <title…>` | `titles.admin` (op) | Set a title (MiniMessage, e.g. `<gold>Legend`) |
| `/title clear <player>` | `titles.admin` | Remove a player's title |
| `/title testllm <player>` | `titles.admin` | Test your AI key and preview a title and comment |
| `/title reload` | `titles.admin` | Reload `config.yml` live |

`/eventtitle` is an alias. The plugin's `/title` replaces the vanilla command, which stays available as
`/minecraft:title`.

## Config highlights

| Section | Key | Default | Notes |
|---|---|---|---|
| `llm` | `enabled`, `provider` | `true`, `deepseek` | `grok` gives spicier, X-style humour |
| `settings` | `max_title_length` | `-1` | Maximum visible characters (`-1` = unlimited) |
| `display` | `offset_y` | `0.8` | Height above the head; tune this in-game |
| `display` | `show_to_self` | `false` | Like vanilla nametags, you don't see your own |
| `display` | `interpolation_ticks` | `3` | Movement smoothing for the floating title |
| `chat_prefix` | `mode` | `replace` | Use `prefix` alongside other chat-format plugins |
| `chat` | `audience`, `cooldown_seconds` | `server`, `15` | Who sees AI comments, and how often |
| `tab_list` | `enabled` | `true` | Turn off if TAB or another plugin manages the tab list |

All formatting uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html), not `&` codes.
Anything the AI writes is filtered down to colors and text styles only, so it can't add clickable or
hover actions.

## Build

```sh
./gradlew test build      # jar ends up in build/libs/
```

Contributor rules and architecture notes are in [AGENTS.md](AGENTS.md).
