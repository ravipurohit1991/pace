# Pace privacy

Pace has no account, backend, advertising, analytics, telemetry, or cloud synchronization. Android cloud backup and device-to-device transfer are disabled. The only outbound traffic comes from features the user switches on explicitly, described below.

## Data kept on the device

Cigarette and reversal records, daily plan snapshots, urge check-ins and optional notes, trigger places, achievements, coach conversations, preferences, and widget state are stored in app-private Room/DataStore storage. Other apps cannot normally read that storage. A JSON export is created only when the user chooses a destination through Android's document picker; that exported copy is then controlled by the chosen storage provider.

## Optional external actions

- **AI coach (Ollama Cloud):** off until the user enables it and saves their own API key. The key is encrypted with a hardware-backed Android Keystore AES-GCM key before storage, is never written to logs, and is excluded from JSON exports. When the user sends a message, requests a riddle, or receives a scheduled nudge or check-in, Pace sends over HTTPS to `ollama.com`: the system prompt, the recent conversation the user typed, and — unless the user turns off **Share my smoking stats** — a short grounded summary of their own figures: cigarettes today against the ceiling, minutes since the last cigarette, minutes to the next planned window, smoke-free hours, zero-cigarette streak, cigarettes avoided, money saved, their three most frequent trigger tags, their written reason, and their chosen coaching tone. Raw log history, notes, trigger-place coordinates, and backups are never transmitted. Ollama processes these requests under its own terms and privacy policy. Turning the coach off, or removing the key, stops all of this traffic.
- **Automatic check-ins:** when enabled, a periodic background task asks the model for a short message and posts it as a notification. It sends the same data described above and nothing more, respects quiet hours and the notification rate limits, and stops entirely when the toggle is off.
- **Private builds:** a personal build may pre-load an API key and prior smoking history from the developer's own git-ignored `local.properties`. Those values are compiled into that private APK only and are never present in the published source.
- **Weather:** after an explanation and foreground location permission, a user-requested lookup obtains the current location, rounds latitude and longitude to two decimal places, and sends that approximate location once to Open-Meteo over HTTPS. Pace does not save those coordinates.
- **Trigger places:** saving a place requires foreground precise location. Automatic proximity cues are off by default and require a separate explanation and background-location system permission. Saved coordinates remain in Pace's private database until edited or deleted.
- **Web content:** external games and reference sources open only from a fixed HTTPS allowlist in a browser Custom Tab. Those sites have their own privacy practices.
- **Trusted support:** sharing a message hands the action to a user-selected Android app. Pace does not request contacts, phone-call, or SMS permissions.

Notifications and vibration are optional. Pace never requests contacts, camera, microphone, broad storage access, call permissions, or SMS permissions. Permission denial leaves the core app usable.

Choosing **Delete all Pace data** removes the local database and settings, including the stored API key and coach conversations, cancels Pace-tagged scheduled work and notifications, removes registered geofences, and clears widget state. Copies previously exported by the user are outside the app and must be deleted from their selected storage separately.

Pace is a self-management aid. It is not medical treatment, emergency care, or a substitute for a clinician or cessation adviser.
