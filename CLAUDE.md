# Jicrade

Android app (Kotlin, Jetpack Compose, Hilt, Room, Retrofit, WorkManager) replacing the retired
Opsgenie mobile app on top of the Jira Service Management Operations REST API. Single module
`app`, package `fr.amorin.jicrade`, minSdk 26, compileSdk 35. Development-only for now.

## Build and test

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Lint must report 0 errors; warnings about newer dependency versions are expected.
- `versionCode` = git commit count + 1, so commit before building an APK meant to install
  over a previous one.
- Machine-specific setup (JDK and SDK paths, temp dir workarounds) belongs in `CLAUDE.local.md`,
  which is not committed.

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

## Notifications and polling

- `sync/NotificationPolicy.kt` is the only place deciding whether an alert notifies and whether
  fast polling should run; keep it pure and unit-tested.
- "Am I on call" = `OnCallRepository.amIOnCall()` (live `on-calls` per schedule, 10-minute cache,
  timeline periods as fallback). `PollingController.evaluate()` starts/stops the foreground
  service and arms an exact alarm for the next shift boundary.
- Two "new alert" channels exist because Android freezes a channel's DND bypass after creation:
  `alerts_new` (bypasses DND) and `alerts_new_quiet`; the Override DND setting picks one.

## Conventions

- Keep DTO fields nullable with defaults; `Json` is configured with `ignoreUnknownKeys`.
- Wrap every network call in `api(json, "label") { ... }` so errors are user-readable.
- Settings go through `SettingsStore` (DataStore); secrets only through `CredentialsStore`.
- Tests are plain JUnit on the JVM (MockWebServer for API parsing); no instrumentation tests.
