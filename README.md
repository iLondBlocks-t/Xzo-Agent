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
| **Tools** | `web_search`, `fetch_url`, `code_execution`, `calculator`, `create_file`, `write_file`, `read_file`, `current_datetime`, `device_info`, `remember`, `recall`, `summarize_text` |
| **Providers** | **Groq** (primary, `groq/compound` with server-side web search + Python sandbox) → automatic **OpenRouter** free-model fallback on error/429 |
| **Files** | Storage Access Framework only — *you* choose where every file is saved/read. No storage permissions requested |
| **Self-verification** | A second model pass critiques the draft answer; if it passes you get a **Verified ✓** chip, if it fails the answer is auto-corrected |
| **UI** | Diagonal drifting gradient (gray → near-black → off-white), light/dark aware, rounded bubbles, pulsing "thinking" dots, animated Verified chip. No colour accents, no ads, no login, no paywall |
| **Local** | Room database: conversations, messages, tool traces, artifacts, long-term memory. Everything stays on the device |
| **ABI** | `armeabi-v7a` only (`abiFilters`), `minSdk 28`, `targetSdk 34` |

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

`Actions → (your run) → Artifacts → app-release-arm32` → download the zip → `app-release*.apk`
→ copy to the phone → allow "install from unknown sources" → install.
Unsigned APKs must be signed (or use the debug build) before Android will install them.

---

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
├── core/Settings.kt         # DataStore-backed settings
├── data/
│   ├── db/                  # Room entities, DAOs, database
│   ├── remote/              # OpenAI-compatible wire types, LlmClient (SSE streaming), WebClient, ModelCatalog
│   └── repo/                # ChatRepository (history, export, auto-title)
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

## Privacy

No analytics, no telemetry, no account. The only network calls are to `api.groq.com`,
`openrouter.ai`, and — for the key-less search fallback — `duckduckgo.com` / `wikipedia.org`.
Conversations, traces and memories never leave the device.
