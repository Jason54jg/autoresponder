# Changelog

🌐 **English** · [Français](CHANGELOG.fr.md)

One entry per request: the mod version goes up with every request (1.1.1, 1.1.2, 1.1.3, ...).
The version is defined in a single place: `mod.version` in `stonecutter.properties.toml`.

## 1.1.5

### Fixes
- **Default `commandTemplate` follows the server command**: the server no longer uses `/cg valid ...`
  but an `/ar add ...` command, so the default is `"/ar add {question} | {response} | {min | max}"`
  (restored to the originally provided value). The handler now substitutes every placeholder:
  `{question}` (matched trigger), `{response}`, `{timer}` (actual drawn delay, base cooldown included),
  `{min}` / `{max}` (entry bounds) and `{min | max}` (both bounds) — nothing is ever sent literally.
  Note: since the server shares our `/ar` prefix, never type the validation command by hand (our client
  tree would intercept it); the automatic send bypasses the chat and is unaffected. If your config file
  was already created with the `/cg valid ...` template from 1.1.5, update its `commandTemplate` line
  (or delete the file to regenerate it — your triggers will be lost).
- **Base cooldown default**: fresh `settings.json` started at 500 ms although the documented and
  enforced range is 2000–10000 ms. Default is now 2000, and `load()` coerces any out-of-range value
  (old 500 files, hand edits) back into range.
- **Per-version `minecraft` dependency**: `fabric.mod.json` declared `"minecraft": "*"`, so a 26.3 jar
  loaded silently on 26.1 and crashed later on a missing method. It now expands to the version's
  `mod.mc_compat` (`~26.1` / `~26.2` / `~26.3`), so the loader rejects mismatched jars cleanly.
- **Scheduler cleared on disconnect**: scheduled answers no longer leak to the next server after a
  server switch / disconnect.
- **`First one to say` multi-word answers**: the handler only captured the first token
  (`"Golden Apple"` → `"Golden"`). It now captures the whole target
  (trailing `!`/`.` stripped).
- **Static responders are now case-insensitive** like every other handler, and entries with an empty
  `pattern` never match (before, `contains("")` matched everything).
- **Corrupt config files no longer crash** the init or `/ar reload` / `/ar add`: each JSON file falls
  back to its default, and `Responder` entries get the same defensive deserializer as
  `ValidationQuestion` (explicit `null`s handled too).
- Removed a duplicated `Unscramble wparneS` default entry; fixed the `words.txt` comment pointing to
  the old `/chatgames reload` command.

## 1.1.4

### Documentation
- **README and changelog in two languages**: English is the base version (`README.md`, `CHANGELOG.md`),
  French lives in `README.fr.md` and `CHANGELOG.fr.md`. Each file links to the other language at the top.
- README: fixed the Config section (it still mentioned "the YACL screen", removed in 1.1.1), updated the
  jar example, documented the `‹ ›` arrows and the translated labels of the config screen.
- No mod code change.

## 1.1.3

### Languages
- All the mod's text now follows the **language chosen in Minecraft**: config screen (tabs, labels,
  buttons, notice styles and overlay positions), the "answer found" / "no known answer" notices, the
  feedback of the `/ar` commands (`on`, `off`, `reload`, `add`) and the "toggle" key message. Before, these
  texts were hard-coded (in French).
- Languages provided: **English (en_us, default), French (fr_fr), Spanish (es_es), German (de_de)**.
  Any other Minecraft language falls back to English.
- Adding a language = copy `assets/autoresponder/lang/en_us.json` to `<code>.json` and translate it.
- Proper names ("AutoResponder", "Chat Games »") and the content of `autoresponder_questions.json` are
  not translated (they depend on the server).

## 1.1.2

- **Dropped 1.20.1 and 1.21.1** (user request): removed from the build (`settings.gradle.kts`,
  `stonecutter.properties.toml`). Supported versions: **26.1.2, 26.2, 26.3**. No mod code change.
- A plain `gradlew build` therefore only targets these three versions.

## 1.1.1

### New Minecraft versions
- **Added Minecraft 26.3** (Fabric API 0.161.0+26.3, Fabric Loader 0.19.5, ModMenu 21.0.0).
- Multi-version build through Stonecutter: one jar per version (`autoresponder-1.1.1+<mc>.jar`).
  Versions that build and load: **26.1.2, 26.2, 26.3**.
- 1.20.1 and 1.21.1 were declared in the build but not supported (dropped in 1.1.2).

### 26.3 compatibility
- 26.3 moved from GLFW to SDL: the "toggle" key no longer uses `GLFW.*` or `InputConstants.Type.KEYSYM`
  (removed). `KeyMapping` 3-argument constructor + `InputConstants.UNKNOWN`, valid from 26.1 to 26.3.
- Config screen: no hard-coded mouse button code, vanilla EditBox (already compatible with the new SDL
  `TextInputManager`).
- Verified: clean build for 26.1.2 / 26.2 / 26.3, and the 26.3 client starts with the mod loaded
  (resources reloaded, GUI atlas created without sprite errors).

### Config screen (`/ar`)
- Complete redesign, without YACL: dark palette, rounded corners through 9-slice textures
  (`textures/gui/sprites/rounded_rect.png`, `pill.png`) tinted on the fly, animated hover.
- Fixed: the "Chat Games" tab's frames were drawn over the "General" tab.
- Fixed: field text (cooldown, template) stuck to the top/left — an unbordered `EditBox` does not center
  its text; offset compensated.
- Fixed: the on/off switch texture showed as a "missing texture" (9-slice border = exactly half the
  image, rejected by the atlas).
- Fixed: `‹ ›` chevrons of the choice fields — they are now on both edges, left click = previous value,
  right click = next (before: always "next").
- The panel adapts to the resolution / GUI scale.
- "AutoResponder" title centered above the panel, bold with a shadow.

### Detection / notices
- "Chat Games" detection is robust to spaces and line breaks around the prefix.
- Ignores round-result messages ("X answered Y in 2.1s!").
- Configurable notice style: chat / overlay (adjustable position) / toast / none.

### Commands
- `/ar` opens the config screen; `/ar on|off`, `/ar reload`, `/ar add <trigger> | <response> | [min] [max]`.
- A single data file: `autoresponder_questions.json`.

## 1.1.0
- Starting version of this changelog.
