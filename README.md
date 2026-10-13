# Jicrade

A small Android app that replaces the retired Opsgenie mobile app for teams moved to
**Jira Service Management Operations**. It talks directly to the JSM Operations REST API
(the rehosted Opsgenie API) with your own Atlassian API token.

What it does:

- lists your team's alerts (open / acked / closed) with detail, notes and activity log
- acknowledge, unacknowledge, close, snooze, add notes
- notifies you of new alerts, with Acknowledge / Close buttons on the notification
- shows who is on call now and next for every schedule of your team, your next shift,
  and the upcoming rotations

## Setup on the phone

1. Create an API token at <https://id.atlassian.com/manage-profile/security/api-tokens>.
2. Install the APK, open the app, enter your site name (`mycompany` for
   `mycompany.atlassian.net`), your Atlassian email and the token, then pick your team.
3. Allow notifications. In Settings, exempt the app from battery optimisation.
4. When you are on duty, switch **On-call mode** on: a foreground service polls every 30 s to
   5 min. Otherwise the app checks every 15 minutes in the background.

The token is stored in Android Keystore-backed encrypted preferences and only sent to
`api.atlassian.com` and `<site>.atlassian.net`.

## How alerts reach the phone

The Operations API has no push channel for third-party apps, so the app polls. New alerts
discovered by a poll are turned into high-priority notifications (alarm sound, bypasses
Do Not Disturb, full-screen for P1). A future version could replace polling by the JSM
outgoing Webhook integration feeding a relay that pushes to the phone; the notifier is
independent of how alerts are discovered.

## API reference

- Intro: <https://developer.atlassian.com/cloud/jira/service-desk-ops/rest/v2/intro/>
- OpenAPI spec: `docs/jsm-ops-openapi.json` (copy of
  <https://dac-static.atlassian.com/cloud/jira/service-desk-ops/swagger.v3.json>)
- Base URL: `https://api.atlassian.com/jsm/ops/api/{cloudId}/v1/`, Basic auth `email:token`
- cloudId: `https://<site>.atlassian.net/_edge/tenant_info`
- Account id and display names: Jira platform `/rest/api/3/myself` and `/rest/api/3/user/bulk`
- Alert search syntax: <https://support.atlassian.com/opsgenie/docs/search-queries-for-alerts/>

## Building

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35). Point `local.properties`
at the SDK (`sdk.dir=/path/to/Android/Sdk`).

```sh
./gradlew test assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
app/src/main/java/fr/amorin/jicrade/
  data/auth      credentials (encrypted prefs) and session
  data/remote    Retrofit APIs, DTOs, auth / retry interceptors
  data/local     Room entities and DAOs, DataStore settings
  data/repo      alert, on-call, user and auth repositories
  sync           WorkManager sync, on-call foreground service, notifications
  ui             Compose screens: setup, alerts, alert detail, on-call, settings
```
