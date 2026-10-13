# Jicrade

Android app (Kotlin, Jetpack Compose, Hilt, Room, Retrofit, WorkManager) replacing the retired
Opsgenie mobile app on top of the Jira Service Management Operations REST API. Single module
`app`, package `fr.amorin.jicrade`, minSdk 26, compileSdk 35. Development-only for now.

## Build and test

```sh
export JAVA_HOME=$HOME/apps/jdk21        # JDK with javac; system JRE has none
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Android SDK lives in `~/Android/Sdk` (see `local.properties`, not committed).
- `/tmp` is noexec on this machine; `~/.gradle/gradle.properties` redirects JVM temp dirs so
  Room's KSP query verifier works. Do not move that into the repo.
- Lint must report 0 errors; warnings about newer dependency versions are expected.
- `versionCode` = git commit count + 1, so commit before building an APK meant to install
  over a previous one.

## Layout

```
data/auth    Credentials + Session, EncryptedSharedPreferences store, SessionHolder (in-memory)
data/remote  OpsApi (JSM Ops), JiraApi (@Url calls on <site>.atlassian.net), DTOs, interceptors,
             ApiException + api() helper, Lenient.kt for array-or-object responses
data/local   Room entities/DAOs (alerts, users, schedules, on_call_periods), DataStore settings
data/repo    AlertRepository (sync, optimistic actions), OnCallRepository, UserRepository, AuthRepository
sync         AlertSyncWorker + SyncScheduler, OnCallPollingService (foreground, specialUse),
             NewAlertNotifier, Notifications, NotificationActionReceiver, BootReceiver
ui           Compose screens per feature with Hilt ViewModels; AppNav holds bottom navigation
```

## API facts that matter

- Base URL `https://api.atlassian.com/jsm/ops/api/{cloudId}/v1/`, Basic auth `email:apiToken`.
  Spec copy in `docs/jsm-ops-openapi.json`; the live API sometimes deviates (e.g. `took` is a
  decimal, some lists come back as bare arrays), so parse leniently and add a test when a
  deviation is found.
- Alert list filter uses Opsgenie search syntax by team **name**: `teams: "Name" AND status: open`.
- Ack/close/snooze are async 202s; repositories apply an optimistic local update then refetch.
- User ids are Atlassian account ids; names come from Jira `/rest/api/3/user/bulk`.
- No push for third-party apps: polling only. `NewAlertNotifier` takes a `SyncResult` so a push
  relay can be added later without touching notification code.

## Conventions

- Keep DTO fields nullable with defaults; `Json` is configured with `ignoreUnknownKeys`.
- Wrap every network call in `api(json, "label") { ... }` so errors are user-readable.
- Settings go through `SettingsStore` (DataStore); secrets only through `CredentialsStore`.
- Tests are plain JUnit on the JVM (MockWebServer for API parsing); no instrumentation tests.
