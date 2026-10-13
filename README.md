# AutoResponder

🌐 **English** · [Français](README.fr.md)

Fabric (client-side) mod that **automatically answers** a server's chat games (formerly *autochatgames*).

## Installation

Put 3 jars in `.minecraft/mods/`:

1. The mod: `autoresponder-<version>+<mc>.jar` (e.g. `autoresponder-1.1.5+26.3.jar`)
2. **Fabric API**
3. **Fabric Language Kotlin**

Supported Minecraft versions: **26.1.2, 26.2, 26.3** (JDK 25). Use the jar that matches your version. Version history: [CHANGELOG.md](CHANGELOG.md).

Languages: the mod follows Minecraft's language (English, French, Spanish, German; any other language falls back to English). To add one, copy `src/main/resources/assets/autoresponder/lang/en_us.json` to `<language_code>.json` and translate it.

Custom config screen (no external dependency). **ModMenu** is optional: drop it in `mods/` if you want an "AutoResponder" entry in its menu, otherwise use `/ar`.

## "Chat Games »" flow (validation by command)

When a received message starts **exactly** with `Chat Games »` (surrounding spaces/newlines are ignored — some servers pad the message):

1. The mod analyses the rest of the message and looks for a matching entry in `autoresponder_questions.json`.
2. If found: shows a **client-side local** notice (never sent to the server, configurable style — see below):
   `[AutoResponder] Answer found! Automatic reply scheduled.`
   Then, after a random delay picked between `mindelay` and `maxdelay` (ms), sends the configured validation command to the server, e.g. `ar add First one to say Rebirth | Rebirth | 1000 2000`.
3. If **no** entry matches: local notice `[AutoResponder] No known answer for this Chat Games, answer it yourself!` — nothing is sent to the server, it's up to you to answer by hand.

The notice text follows Minecraft's language. No instant public answer is ever sent for these messages, in either case.

`ar add ...` is the **server plugin's** validation command, sent automatically in the background (`connection.sendCommand`, it does not go through the local chat). Never type it by hand: our mod owns the `/ar` client tree, so a hand-typed validation command would be caught by our own tree (no matching sub-command) and rejected before ever reaching the server. The automatic send bypasses the chat and is unaffected. It does not show up in tab-complete — that's expected; nothing to do on the mod side.

### `autoresponder_questions.json` config

```json
{
  "commandTemplate": "/ar add {question} | {response} | {min | max}",
  "questions": [
    {
      "trigger": "First one to say Rebirth",
      "response": "Rebirth",
      "mindelay": 1000,
      "maxdelay": 2000
    }
  ]
}
```

- `trigger`: substring searched in the message (after the `Chat Games »` prefix).
- `response`: value injected into `{response}`.
- `mindelay` / `maxdelay`: bounds, in milliseconds, of the random delay before sending (added on top of the **base cooldown**, see the config screen).
- `commandTemplate`: template of the final command. Placeholders: `{question}` (matched trigger), `{response}`, `{timer}` (the delay actually drawn, in ms — base cooldown included), `{min}` / `{max}` (entry delay bounds), `{min | max}` (both bounds).

## Other solvers (messages without the "Chat Games »" prefix)

| Message type | Answer |
|---|---|
| `First one to say X` | replies `X` |
| `Unscramble abc` | finds the word (`words.txt` dictionary) |
| `What is 9 x 6?` | computes → `54` |
| `Answer the following question: ...` | answer bank (`questions.json`) |
| entries of `responders.json` | configured fixed answer |

The answer is sent in the **public chat**, fixed delay 800-1400 ms + base cooldown. This path only applies to messages that do **not** start with `Chat Games »`. Purely static (resource files) — there is no command to add entries to it, only `/ar add` (Chat Games flow) is available.

## Commands (`/ar` or `/autoresponder`)

**Important: our `/ar` commands are NOT the server's `ar add ...` validation command.** Ours (`/ar`, `/ar on|off|reload|add`) configure the mod locally; the server's validates answers (see `commandTemplate` above) and is sent automatically in the background without going through the chat. Because both share the `/ar` prefix, never type the validation command by hand — it would be caught by our own client command tree (no matching sub-command) and rejected before ever reaching the server.

| Command | Effect |
|---|---|
| `/ar` | opens the config screen (see below) |
| `/ar on` · `/ar off` | enable / disable without opening the screen |
| `/ar reload` | reload the config |
| `/ar add <trigger> \| <response> \| [mindelay] [maxdelay]` | add/replace a "Chat Games »" trigger; optional delays (default 1000/2000), separated by `\|` or just a space |

`/autoresponder` works as an identical alias of `/ar`.

## Config screen

Custom screen (no external dependency), opened with `/ar` or from the ModMenu menu (Mods → AutoResponder → Config) if ModMenu is installed. Two tabs:

**General**
- **Enabled** — on/off switch.
- **Base cooldown (ms)** — extra delay applied to *all* scheduled answers ("Chat Games »" flow and other solvers), on top of each entry's own `mindelay`/`maxdelay`. Clamped between 2000 and 10000 ms — it is often the biggest part of the perceived delay (e.g. math solver 800-1400 ms + base cooldown ≈ 2800-3400 ms).
- **Notice style** — how to show "answer found" / "no known answer": `Chat` (message in chat), `Overlay` (text drawn directly on screen, adjustable position, disappears after 3 s), `Toast` (top-right popup, vanilla notification style), `None` (nothing). The `‹ ›` arrows change the value: click on the left = previous, on the right = next.
- **Overlay position** — where to draw the text when the style is `Overlay`: top/bottom corners/center. No effect for the other styles.

**Chat Games »**
- **Command template** — equivalent to the JSON's `commandTemplate` (`{question}`/`{response}`/`{timer}`/`{min | max}`, see above).
- **Chat Games » triggers** — text area, one entry per line in the format `trigger|response|mindelay|maxdelay` (the last two fields are optional, default 1000/2000 if omitted or invalid).

The **Save** button fully rewrites `autoresponder_questions.json` with the menu's content — any hand edit made to the file while the screen is open will be overwritten on save. **Cancel** closes without changing anything.

## Config files

Editable in `.minecraft/config/autoresponder/`:

- `autoresponder_questions.json` — "Chat Games »" triggers (trigger/response/mindelay/maxdelay + commandTemplate), fed by `/ar add` and by the config screen
- `words.txt` — unscramble dictionary (1 word/line)
- `questions.json` — questions → answers (`Answer the following question:` bank)
- `responders.json` — fixed answers (fallback, outside the "Chat Games »" flow)
- `learned.json.migrated` / `learned_questions.json.migrated` — old files from a previous version (before unified storage), kept as-is, not read

## Building

```
gradlew.bat :26.3:build :26.2:build :26.1.2:build
```
One jar per version in `versions/<mc>/build/libs/`. The mod version is set in
`stonecutter.properties.toml` (`mod.version`).

---
⚠️ Automating chat games may break the server's rules.
