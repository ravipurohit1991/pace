# Pace — track less, quit for good

Pace is a private Android companion for cutting down and stopping smoking. It pairs honest tracking with an
**AI coach powered by Ollama Cloud** that talks you through cravings, hands you a riddle when you need a
distraction, and nudges you *before* your next scheduled cigarette instead of after.

Your history never leaves the device. The coach is opt-in, uses **your own** Ollama API key, and sends only the
handful of numbers already on your screen.

| Today | AI coach | Body recovery |
| --- | --- | --- |
| ![Today](docs/screenshots/02-today.png) | ![Coach](docs/screenshots/03-coach.png) | ![Recovery](docs/screenshots/07-recovery-timeline.png) |

---

## What it does

### Tracking that doesn't shame you

A ceiling, not a quota. One tap logs a cigarette, with a 12-second undo from the app or the home-screen
widget. Minimum spacing, a morning hold, and quiet hours shape the day; a hard day never rewrites history,
and a lower ceiling always takes effect tomorrow rather than retroactively.

### An AI coach that knows your numbers

The coach runs on Ollama Cloud with the model you pick. Before each reply Pace grounds it in your own local
stats — count against ceiling, minutes to the next window, smoke-free run, cigarettes avoided, money saved,
your most frequent triggers, and the reason you wrote down.

| Craving conversation | Distraction on demand |
| --- | --- |
| ![Coach chat](docs/screenshots/03-coach.png) | ![Riddle](docs/screenshots/04-coach-riddle.png) |

Replies stream token by token and can be stopped mid-sentence. **Riddle me** pulls a two-minute
lateral-thinking puzzle that never mentions smoking — the point is to occupy your head until the urge passes.

### Nudges before the urge, not after

When your next planned window is within 20 minutes, a background worker asks the model for one line under
20 words, tailored to your numbers, and posts it as a notification. Tapping **Reply** opens the chat so the
conversation continues where the notification left off. If the network or model is unavailable, a built-in
line is used instead, so the cue still arrives. Nudges obey quiet hours, a daily cap, and a cooldown, and
never tell you that it is time to smoke.

### Watch your body repair itself

Twelve recovery milestones on the published CDC/NHS timeline, from *heart rate settles* at 20 minutes to
*lung cancer risk halved* at 10 years. Today shows the next one you are working toward; Progress shows the
whole ladder. Alongside it: cigarettes avoided against your baseline, money saved, life regained at
11 minutes per cigarette, and your best zero-cigarette run.

### An offline toolkit

A five-minute pause timer that survives leaving the app, urge check-ins with before/after strength,
5-4-3-2-1 grounding, a scene-change reset, sequence and memory games, a share-sheet message to a trusted
person, and vetted puzzle links. All of it works with no network and no AI key.

| Onboarding | Progress | Toolkit |
| --- | --- | --- |
| ![Onboarding](docs/screenshots/01-onboarding.png) | ![Progress](docs/screenshots/05-progress.png) | ![Toolkit](docs/screenshots/08-toolkit.png) |

---

## Setting up the AI coach

1. Create a key at [ollama.com/settings/keys](https://ollama.com/settings/keys).
2. In Pace, open **Settings → AI coach** and turn it on.
3. Paste the key and tap **Test key**. Pace calls `/api/tags` and replaces the suggested models with the ones
   your account can actually reach.
4. Pick a model, leave **Nudge me before my next window** on, and tap **Save**.

![AI settings](docs/screenshots/06-settings-ai.png)

The key is encrypted with a hardware-backed Android Keystore AES-GCM key before it is written to the
preferences store, so it is never at rest in readable form. **Remove key** clears it and disables the coach.

### How the key and your data are handled

- The key lives only on your device. It is never logged, included in JSON exports, or committed to this repository.
- Requests go to `https://ollama.com` over HTTPS. Nothing is sent until you enable the coach and save a key.
- Only the grounded figures listed above are transmitted — never your raw log history, notes, or locations.
- Reasoning traces returned by thinking models are discarded; only the reply text is kept.
- Turn the coach off and Pace is fully offline again.

---

## Build

Prerequisites: Android Studio with SDK Platform 36 and Build Tools 36, plus JDK 17 or newer (Android Studio's
bundled JetBrains Runtime works). Gradle itself does not need installing — the checked-in wrapper fetches
Gradle 9.4.1.

```bash
./gradlew :app:assembleDebug
```

Run the checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Install on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. If SDK discovery fails, create a
git-ignored `local.properties` with `sdk.dir=` pointing at your Android SDK.

The release build enables R8 minification and resource shrinking. Signing material is deliberately absent
from this repository; supply your own `keystore.properties` (git-ignored) to produce a signed release.

---

## Architecture

Single-module Kotlin app, Compose-only UI, no dependency-injection framework — a hand-rolled `AppContainer`
holds the singletons.

| Layer | Location | Contents |
| --- | --- | --- |
| UI | `feature/` | Today, Coach, Toolkit, Progress, Plan, Settings, onboarding, shared components |
| Presentation | `PaceViewModel.kt` | `PaceUiState` from combined flows, plus transient `CoachUiState` for streaming |
| Domain | `domain/` | Pacing, progress, quit metrics, badge rules, coach prompt construction |
| Data | `data/` | Room database, Proto DataStore preferences, JSON backup import/export |
| Platform | `core/` | Ollama client, Keystore vault, notifications, geofencing, weather, link allowlist |

Notable pieces:

- **`core/network/OllamaClient.kt`** — streaming NDJSON chat, one-shot completions, and model listing.
- **`core/security/SecretVault.kt`** — AES-GCM wrapping of the API key.
- **`core/coach/CoachService.kt`** — the only path from local data to the network; a no-op until you opt in.
- **`domain/CoachPrompt.kt`** — the persona and the exact facts each request may include.
- **`domain/QuitProgress.kt`** — smoke-free duration, streaks, avoided cigarettes, recovery milestones.
- **`worker/PaceWorkers.kt`** — the periodic nudge check plus rollover, widget refresh, and badge review.

Outbound HTTPS is restricted to an allowlist in `core/network/SafeLinks.kt`; cleartext traffic is disabled.

---

## Verification

The unit suite covers plan timing, cross-midnight quiet hours, weekday/weekend wake times, daylight-saving
transitions, recovery and zero-ceiling behaviour, progress and savings math, quit metrics and recovery
milestones, badge and reduction eligibility, notification suppression, URL allowlisting, coordinate rounding,
widget debounce, and backup validation. Room reversal and idempotency are covered by instrumentation tests.

The build has been exercised on a Google APIs Android 16 / API 36 emulator, including a live Ollama Cloud
round trip through the coach. Minimum supported version is Android 8 / API 26.

---

## Privacy

No account, backend, analytics, advertising, or telemetry. Android cloud backup and device transfer are off.
Cigarette records, notes, trigger places, and chat history live in app-private Room and DataStore storage.
Export happens only to a folder you choose. See [PRIVACY.md](PRIVACY.md) for the full statement and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency licenses.

Pace is a self-management aid, not medical care, and does not replace a clinician or cessation adviser. AI
replies can be wrong and are never medical advice.

---

## Credits

Approach informed by the open-source quit-smoking community, including
[SmokingYou](https://github.com/bodyaant/SmokingYou),
[awesome-smoking](https://github.com/tshwangq/awesome-smoking),
[quit-smoking-instantly](https://github.com/xiaolai/quit-smoking-instantly), and
[Quit-Smoke-App](https://github.com/trizin/Quit-Smoke-App). Recovery timings follow published CDC and NHS
cessation guidance. Weather by [Open-Meteo](https://open-meteo.com); puzzles by Gabriele Cirulli and
Simon Tatham.
