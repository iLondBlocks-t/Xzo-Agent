# Xzo Agent — free & unlimited AI agent for Android (ARM32, Android 9+)

A genuine **agentic** assistant that runs on low-end 32-bit Android phones.
Not a plain chatbot: it follows a real loop — **Plan → Act (tool call) → Observe → Verify → Respond** —
and it can search the live web, run code, read files you pick, and write real files to your storage.

Built entirely in **Kotlin + Jetpack Compose (Material 3)**, single Gradle module, and it is built
**on GitHub Actions** — you never need Android Studio.

---

## Highlights

| | |
|---|---|
| **Agent loop** | Plan → Act → Observe → **self-verify** → Respond, up to N tool iterations (configurable) |
| **Tools** (15) | `web_search`, `fetch_url`, `code_execution`, `calculator`, `create_file`, `write_file`, `read_file`, `list_folder`, `read_folder_file`, `translate`, `current_datetime`, `device_info`, `remember`, `recall`, `summarize_text` |
| **Providers** | **Groq** (primary, `openai/gpt-oss-120b` with server-side **browser search** + **Python sandbox**) → automatic **OpenRouter** free-model fallback on error/429 |
| **Model drift-proof** | Live `/models` discovery from both providers + an auto-migration map for retired IDs, so a decommissioned model never breaks the app |
| **Voice** | Mic button → Groq **Whisper large v3 turbo** transcription; answers can be read back with the offline device TTS |
| **Prompt library** | 21 curated agentic prompts across Research / Files / Coding / Productivity / Learning / Arabic |
| **Workspace** | Files the agent created, long-term memory manager, usage statistics |
| **Files** | Storage Access Framework only — *you* choose where every file is saved/read. No storage permissions requested |
| **Self-verification** | A second model pass critiques the draft answer; if it passes you get a **Verified ✓** chip, if it fails the answer is auto-corrected |
| **UI** | Diagonal drifting gradient (gray → near-black → off-white), light/dark aware, rounded bubbles, pulsing "thinking" dots, animated Verified chip. No colour accents, no ads, no login, no paywall |
| **Local** | Room database: conversations, messages, tool traces, artifacts, long-term memory. Everything stays on the device |
| **ABI** | `armeabi-v7a` only (`abiFilters`), `minSdk 28`, `targetSdk 34` |

### ⚠️ Model catalogue note (read this)

Provider catalogues churn fast. As of **25 Sep 2026**:

* `groq/compound` and `groq/compound-mini` were **decommissioned on 21 Sep 2026** — requests to them now error.
* `llama-3.3-70b-versatile` / `llama-3.1-8b-instant` were retired for free & developer tiers on **16 Aug 2026**.

Xzo therefore defaults to **`openai/gpt-oss-120b`**, Groq's current flagship, which provides exactly the same
agentic capability the retired Compound system did — built-in `browser_search` (powered by Exa) and
`code_interpreter` (sandboxed Python on E2B) — requested as `{"type": "browser_search"}` /
`{"type": "code_interpreter"}` entries in the `tools` array alongside the app's own function tools.

To stay safe against future churn the app also:

1. keeps a **retired-ID → replacement map** and silently migrates stored settings, and
2. can **fetch the live model list** from `https://api.groq.com/openai/v1/models` and
   `https://openrouter.ai/api/v1/models` (Settings → Models → *Refresh live model list*).

### What "unlimited" means
The app itself never charges you, never shows ads, never asks you to log in and imposes **no quota**.
Your real throughput is still bound by **Groq's and OpenRouter's own free-tier rate limits**.
When a provider answers `429`, Xzo shows a friendly message, backs off exponentially (honouring
`Retry-After`), retries, and then automatically fails over to the other provider.

---

## 1. Add the API keys as GitHub Secrets

Keys are **never** committed. They live only in encrypted repository secrets and are injected at build time.

Repository → **Settings → Secrets and variables → Actions → New repository secret**

| Secret name | Where to get it |
|---|---|
| `GROQ_API_KEY` | https://console.groq.com/keys (free) |
| `OPENROUTER_API_KEY` | https://openrouter.ai/keys (free) |

Or from the CLI:

```bash
gh secret set GROQ_API_KEY        --body "gsk_xxxxxxxxxxxxxxxx"
gh secret set OPENROUTER_API_KEY  --body "sk-or-v1-xxxxxxxxxxxxxxxx"
```

The workflow writes them into `local.properties` (git-ignored), and `app/build.gradle.kts` exposes them as:

```kotlin
buildConfigField("String", "GROQ_API_KEY", "\"${'$'}{secret("GROQ_API_KEY")}\"")
buildConfigField("String", "OPENROUTER_API_KEY", "\"${'$'}{secret("OPENROUTER_API_KEY")}\"")
```

In Kotlin they are only ever read as `BuildConfig.GROQ_API_KEY` / `BuildConfig.OPENROUTER_API_KEY`.
Users can also paste their own keys in **Settings → API keys** (stored on-device in DataStore, overrides the build value).

> ⚠️ If a key was ever pasted into a chat, an issue or a commit, **revoke and regenerate it** in the Groq /
> OpenRouter console before shipping.

## 2. (Optional) Signing secrets

| Secret | Value |
|---|---|
| `SIGNING_KEY` | base64 of your `.jks` keystore |
| `KEY_ALIAS` | key alias |
| `KEY_STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |

```bash
keytool -genkey -v -keystore xzo.jks -keyalg RSA -keysize 2048 -validity 10000 -alias xzo
base64 -w0 xzo.jks > xzo.jks.b64     # paste into the SIGNING_KEY secret
```

The signing step is skipped automatically when `SIGNING_KEY` is absent — you still get an unsigned APK artifact.

## 3. Trigger the build

* **Automatically:** every push to `main`.
* **Manually:** repo → **Actions → Build APK → Run workflow**.

## 4. Where the APK appears

`Actions → (your run) → Artifacts`. Two artifacts are produced:

| Artifact | Contents | Installable as-is? |
|---|---|---|
| `app-debug-arm32` | `app-debug.apk`, signed with the standard Android debug key | ✅ **Yes** — download, copy to the phone, allow "install from unknown sources", install |
| `app-release-arm32` | `app-release.apk` (or `app-release-unsigned.apk` if no keystore secret) | Only once signed — add the signing secrets above |

Start with the **debug** APK; switch to the signed release once you have generated a keystore.

---

## Feature tour

* **Chat** — drifting gradient background, rounded bubbles, streaming tokens, pulsing thinking dots,
  per-message agent trace (every tool call, its arguments, duration and result), Verified ✓ chip,
  copy / share / read-aloud / retry on every answer.
* **Drawer** — conversation list with pin, delete, rename, full-text search across all messages.
* **Prompt library** — categorised, tool-aware starter prompts (including an Arabic pack).
* **Workspace** — Files (everything the agent saved, tap to open), Memory (what Xzo remembers about you,
  deletable), Usage (chats / messages / tokens).
* **Settings** — model + fallback pickers with live refresh, built-in-tools toggle, reasoning effort,
  temperature, max tokens, tool iterations, history window, per-tool on/off switches, persona editor,
  theme (system/light/dark), animated background, haptics, read-aloud, on-device key overrides.
* **Integrations** — share text into Xzo, "process text" selection action, launcher shortcuts
  (New chat / Prompt library).

## Project layout

```
app/src/main/java/com/xzo/agent/
├── MainActivity.kt          # Compose host, SAF launchers, share/PROCESS_TEXT intents
├── XzoApp.kt                # hand-rolled DI container (no Hilt → smaller APK)
├── agent/
│   ├── AgentEngine.kt       # the Plan→Act→Observe→Verify→Respond loop + provider fallback
│   ├── Tool.kt              # tool interface, JSON-schema builders, arg helpers
│   ├── FileBridge.kt        # suspending bridge to ACTION_CREATE_DOCUMENT / OPEN_DOCUMENT
│   ├── MemoryStore.kt       # long-term key/value memory injected into the system prompt
│   ├── Prompts.kt           # system prompt + self-verification prompt
│   ├── Trace.kt             # serialized per-message agent trace
│   └── tools/               # WebTools, CodeTools, FileTools, UtilityTools
├── core/
│   ├── Settings.kt          # DataStore-backed settings
│   ├── PromptLibrary.kt     # curated agentic prompt packs
│   └── VoiceIO.kt           # mic capture (Whisper) + offline TTS
├── data/
│   ├── db/                  # Room entities, DAOs, database
│   ├── remote/              # OpenAI-compatible wire types, LlmClient (SSE streaming), WebClient, ModelCatalog
│   └── repo/                # ChatRepository (history, export, auto-title), ModelRegistry (live /models)
├── ui/                      # theme, components (gradient, bubbles, markdown, indicators), screens
└── util/                    # Calc (safe math evaluator), Markdown parser
```

## Building locally (optional)

```bash
echo "GROQ_API_KEY=..."       >> local.properties
echo "OPENROUTER_API_KEY=..." >> local.properties
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 34). Everything else is downloaded by Gradle.

## Tests

`./gradlew testDebugUnitTest` runs the unit tests (safe math evaluator, markdown parser); CI runs them
before every APK build.

## Privacy

No analytics, no telemetry, no account. The only network calls are to `api.groq.com`,
`openrouter.ai`, and — for the key-less search fallback — `duckduckgo.com` / `wikipedia.org`.
Conversations, traces and memories never leave the device.
