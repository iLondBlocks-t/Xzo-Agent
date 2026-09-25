# Xzo Agent — a free, unlimited, genuinely *agentic* AI app for Android (ARM32, Android 9+)

Not a chatbot wrapper. Xzo runs a real loop — **Plan → Act (tools) → Observe → Verify → Respond** —
routes each job to the model that is best at it, searches the live web, executes code, reads your
files and images, writes real files back to your storage, and can work on a schedule while you sleep.

Written in **Kotlin + Jetpack Compose (Material 3)**, one Gradle module, `armeabi-v7a` only,
`minSdk 28`, built entirely on **GitHub Actions** — you never need Android Studio.

---

## Table of contents

1. [Why it's different](#why-its-different)
2. [Feature matrix](#feature-matrix)
3. [The agent architecture](#the-agent-architecture)
4. [⚠️ Model catalogue reality check](#-model-catalogue-reality-check)
5. [Setup — add two secrets](#1-add-the-api-keys-as-github-secrets)
6. [Signing](#2-optional-signing-secrets)
7. [Build & install](#3-trigger-the-build)
8. [Verify the APIs really work](#5-verify-the-apis-actually-work)
9. [Project layout](#project-layout)
10. [Tests](#tests)
11. [Privacy](#privacy)

---

## Why it's different

| Most "AI chat" apps | Xzo Agent |
|---|---|
| One model for everything | **8 specialist roles** — Planner, Researcher, Analyst, Coder, Writer, Translator, Critic, Vision — each with its own model, temperature, reasoning effort and tool set |
| Answers from memory | **Live browser search** (Groq built-in, Exa-powered) + key-less DuckDuckGo/Wikipedia fallback |
| "Here's the code you asked for" | **Really executes** Python in a sandbox (E2B via Groq) and shows the actual output |
| Prints a file into the chat | **Writes the real file** to the location you pick via the Storage Access Framework |
| Hopes the answer is right | **Self-verification pass** that critiques the draft and auto-corrects it before you see it |
| Dies when the provider 429s | **Exponential backoff honouring `Retry-After`, then automatic cross-provider failover** |
| Breaks when a model is retired | **Retired-ID migration map + live `/models` discovery** |
| Needs the network for everything | **On-device OCR, translation and language ID** bundled in the APK |
| Only responds when asked | **Scheduled automations** run the agent in the background and notify you |

## Feature matrix

### Intelligence
- **Agent loop** with up to N tool iterations (configurable), full per-message trace.
- **18 tools**: `web_search`, `fetch_url`, `code_execution`, `calculator`, `create_file`, `write_file`,
  `read_file`, `list_folder`, `read_folder_file`, `analyze_image`, `ocr_image`, `translate`,
  `offline_translate`, `detect_language`, `current_datetime`, `device_info`, `remember`, `recall`,
  `summarize_text`, `delegate`.
- **`delegate`** hands a sub-task to a specialist agent (see the table above).
- **Deep Research mode** — Planner decomposes the question, 3-6 Researchers run **in parallel**,
  a Writer synthesises, a Critic attacks it, the Writer revises. All visible as a live trace.
- **Smart model routing** — short question → the 1000 tok/s model; hard reasoning → the flagship;
  long Arabic → the multilingual model; image → a vision model. Your explicit pick always wins.
- **Self-verification** with a "Verified ✓" chip, and silent auto-correction when the reviewer fails it.
- **Long-term memory** the agent writes to and recalls across chats, injected into the system prompt.

### Input & output
- **Vision** — attach or shoot a photo; real multimodal `image_url` parts for vision models, or an
  automatic route through `analyze_image` for text-only models.
- **PDF reading with no library** — Android's `PdfRenderer` rasterises each page and the bundled
  ML Kit recogniser turns it back into text, so even *scanned* PDFs work, fully offline.
- **Camera capture**, **file attach**, **folder access** (batch tasks over a whole directory).
- **Voice input** via Groq Whisper large-v3-turbo; **read-aloud** with the offline device TTS.
- **Export**: markdown, **PDF** (rendered with `PdfDocument`), or a full JSON backup of everything.

### Automation
- **Scheduled tasks** (WorkManager): "every morning at 07:30 search the news and summarise it".
  Each automation keeps its own chat, posts a notification with the result, survives reboot,
  retries with exponential backoff, and can run in Agent or Deep Research mode.
- **Home-screen widget** (new chat / dictate / prompt library), **Quick Settings tile**,
  **launcher shortcuts**, **share-to-Xzo** and **selected-text** integration.

### Interface
- Diagonal drifting gradient (gray → near-black → off-white), light/dark aware, strictly neutral palette.
- Rounded bubbles, streaming tokens, pulsing thinking dots, animated Verified chip, expandable
  agent trace with tool arguments, durations and the model's own reasoning.
- Bundled **Inter** (UI), **Noto Naskh Arabic** (proper Arabic rendering) and **JetBrains Mono** (code).
- Markdown with tables, code blocks + copy button, and tappable source links.
- **Prompt library** — 21 tool-aware starter prompts including a full Arabic pack.
- **Workspace** — files the agent created, memory manager, usage statistics.
- Chat search, pin, rename, delete, **edit & resend**, **branch a conversation**, **regenerate**.
- No ads, no paywall, no account, no telemetry.

### Reliability
- Streaming SSE with tool-call delta accumulation and true cancellation (the Stop button really
  aborts the HTTP call).
- 3 attempts per model → next model → next provider, with `Retry-After`-aware backoff.
- A `400 … tool` response retries once without built-in tools, so a provider change can't hard-fail.
- Room database with a **real migration** (nothing is ever wiped).
- 40+ unit tests, including assertions that no decommissioned model ID can ever be sent.

### What "unlimited" means
The app itself never charges you, shows ads, asks you to log in, or imposes any quota.
Your real throughput is bound by **Groq's and OpenRouter's own free-tier limits**. On `429` Xzo
shows a friendly message, backs off, retries and fails over — and the on-device OCR/translation
tools keep working regardless.

## The agent architecture

```
          ┌──────────────────────── ChatViewModel ────────────────────────┐
          │  mode: CHAT · AGENT · DEEP RESEARCH                           │
          └───────────────┬───────────────────────────┬───────────────────┘
                          │                           │
                 ┌────────▼────────┐        ┌─────────▼──────────┐
                 │   AgentEngine   │        │   DeepResearch     │
                 │ plan→act→observe│        │ plan → N parallel  │
                 │ →verify→respond │        │ researchers → write│
                 └───┬─────────┬───┘        │ → critique → revise│
                     │         │            └─────────┬──────────┘
            ┌────────▼──┐  ┌───▼────────┐             │
            │ 18 tools  │  │ AutoRouter │       ┌─────▼──────┐
            │ (SAF, web,│  │ per-message│       │Specialists │
            │ code, ML) │  │ model pick │       │ 8 roles    │
            └────┬──────┘  └───┬────────┘       └─────┬──────┘
                 │             │                      │
            ┌────▼─────────────▼──────────────────────▼─────┐
            │ LlmClient — SSE streaming, backoff, failover   │
            │   Groq  ──(429/5xx/400-tools)──▶  OpenRouter   │
            └───────────────────────────────────────────────┘
```

## ⚠️ Model catalogue reality check

Provider catalogues churn fast. Verified against Groq's own deprecation page on **25 Sep 2026**:

| Model | Status |
|---|---|
| `groq/compound`, `groq/compound-mini` | **decommissioned 21 Sep 2026** — requests now error |
| `llama-3.3-70b-versatile`, `llama-3.1-8b-instant` | retired for free/developer tiers **16 Aug 2026** |
| `qwen/qwen3-32b`, `llama-4-scout` | retired **17 Jul 2026** |
| `openai/gpt-oss-120b` / `-20b` | **current production** — built-in browser search + code interpreter |

So Xzo defaults to **`openai/gpt-oss-120b`**, which provides exactly what Compound did:
`{"type":"browser_search"}` (Exa) and `{"type":"code_interpreter"}` (E2B) sent in the `tools`
array alongside the app's own function tools. On top of that:

1. a **retired → replacement map** silently migrates stored settings and old conversations, and
2. **Settings → Models → Refresh live model list** pulls the real catalogue from both providers.

A weekly [API smoke test](#5-verify-the-apis-actually-work) warns you the moment this changes again.

---

## 1. Add the API keys as GitHub Secrets

Keys are **never** committed. They live only in encrypted repository secrets, are injected into a
git-ignored `local.properties` at build time and surface in code solely as `BuildConfig` fields.

Repository → **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Get it from |
|---|---|
| `GROQ_API_KEY` | https://console.groq.com/keys (free) |
| `OPENROUTER_API_KEY` | https://openrouter.ai/keys (free) |

CLI equivalent:

```bash
gh secret set GROQ_API_KEY       --body "gsk_…"
gh secret set OPENROUTER_API_KEY --body "sk-or-v1-…"
```

`app/build.gradle.kts`:

```kotlin
buildConfigField("String", "GROQ_API_KEY", "\"${'$'}{secret("GROQ_API_KEY")}\"")
buildConfigField("String", "OPENROUTER_API_KEY", "\"${'$'}{secret("OPENROUTER_API_KEY")}\"")
```

Users can also paste their own keys in **Settings → API keys** (stored on-device in DataStore;
an override always beats the baked-in value).

> 🔐 **If a key was ever pasted into a chat, an issue, or a commit, revoke and regenerate it**
> in the Groq / OpenRouter console before shipping.

## 2. (Optional) Signing secrets

| Secret | Value |
|---|---|
| `SIGNING_KEY` | base64 of your `.jks` keystore |
| `KEY_ALIAS` | key alias |
| `KEY_STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |

```bash
keytool -genkey -v -keystore xzo.jks -keyalg RSA -keysize 2048 -validity 10000 -alias xzo
base64 -w0 xzo.jks > xzo.jks.b64   # paste into SIGNING_KEY
```

The signing step is skipped automatically when `SIGNING_KEY` is absent.

## 3. Trigger the build

* Automatically on every push to `main`.
* Manually: **Actions → Build APK → Run workflow**.

## 4. Where the APK appears

**Actions → (your run) → Artifacts**

| Artifact | Installable as-is? |
|---|---|
| `app-debug-arm32` → `app-debug.apk`, signed with the standard debug key | ✅ **yes** — copy to the phone, allow "install from unknown sources", install |
| `app-release-arm32` → `app-release.apk` | only after you add the signing secrets |

Start with the debug APK.

## 5. Verify the APIs actually work

**Actions → API smoke test → Run workflow.** It checks, against the live services:

* the Groq catalogue still contains the model IDs the app sends,
* `browser_search` and `code_interpreter` built-in tools are accepted,
* client-side function calling returns a real `tool_calls` payload,
* SSE streaming works,
* the OpenRouter fallback answers and the free models still exist.

It also runs weekly on a schedule, so model deprecations surface as a failed run instead of a
broken phone. No key is ever printed.

---

## Project layout

```
app/src/main/java/com/xzo/agent/
├── MainActivity.kt              # Compose host, SAF/camera/mic launchers, share & shortcut intents
├── XzoApp.kt                    # hand-rolled DI container (no Hilt → smaller APK)
├── agent/
│   ├── AgentEngine.kt           # plan→act→observe→verify→respond, provider failover, routing
│   ├── DeepResearch.kt          # parallel multi-agent research pipeline
│   ├── Specialists.kt           # 8 roles: model, prompt, temperature, tools per job
│   ├── AutoRouter.kt            # per-message model selection heuristic
│   ├── Tool.kt / Trace.kt       # tool interface, JSON-schema builders, serialized traces
│   ├── FileBridge.kt            # suspending bridge to SAF + camera
│   ├── MemoryStore.kt           # long-term memory injected into the system prompt
│   ├── Prompts.kt               # system + verification prompts
│   └── tools/                   # Web, Code, File, Folder, Vision, Offline(ML Kit), Delegate, Utility
├── core/
│   ├── Settings.kt              # DataStore settings
│   ├── PromptLibrary.kt         # 21 curated agentic prompts
│   ├── VoiceIO.kt               # mic capture + offline TTS
│   ├── OnDeviceMl.kt            # ML Kit OCR / translate / language ID
│   ├── PdfReader.kt             # PdfRenderer + OCR = offline PDF text extraction
│   ├── PdfExporter.kt           # conversation → real PDF
│   ├── AutomationScheduler.kt   # WorkManager background agent runs
│   ├── Notifier.kt, BootReceiver.kt, XzoTileService.kt, XzoWidgetProvider.kt
├── data/
│   ├── db/                      # Room entities, DAOs, migrations
│   ├── remote/                  # wire types, LlmClient (SSE), WebClient, ModelCatalog
│   └── repo/                    # ChatRepository, ModelRegistry, BackupManager
├── ui/                          # theme (+bundled fonts), components, screens
└── util/                        # Calc (safe evaluator), Markdown, Images
```

## Tests

```bash
./gradlew testDebugUnitTest
```

40+ JVM tests covering the safe math evaluator, the markdown parser, the **exact JSON payloads**
sent to both providers (built-in tools, multimodal parts, `max_completion_tokens`, streaming
deltas, error shapes), model migration/fallback ordering, the auto-router's decisions, every
specialist profile, and every tool's JSON schema. CI runs them before it builds the APKs.

## Building locally (optional)

```bash
echo "GROQ_API_KEY=..."       >> local.properties
echo "OPENROUTER_API_KEY=..." >> local.properties
./gradlew assembleRelease
```

JDK 17 + Android SDK (compileSdk 34). Everything else is downloaded by Gradle.

## Privacy

No analytics, no telemetry, no account, no ads. Network calls go only to `api.groq.com`,
`openrouter.ai`, and — for the key-less search fallback — `duckduckgo.com` / `wikipedia.org`.
Conversations, traces, memories and files stay on the device; the only way data leaves is the
backup file *you* export.
