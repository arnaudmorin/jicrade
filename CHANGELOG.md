# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- The priority filter now also applies to the Acked and Closed tabs.

## [0.8.0] - 2026-11-27

### Added
- Priority filter for open alerts: the filter button in the alert list opens a P1 to P5 slider.
  The choice is remembered, and the list says how many lower-priority alerts are hidden.

### Changed
- Refreshed Material 3 look: indigo theme (no longer follows the wallpaper colours), softer
  priority badges, and cleaner alert rows and tabs.

## [0.7.0] - 2026-11-27

### Added
- Alert ringtone picker in settings, with a short preview of each sound.
- Alert notifications keep ringing until opened, acknowledged or dismissed (can be turned off).
- Minimum alarm volume for alerts while on call. The previous volume is
  restored once the alert is handled or the app is opened.
- "Test alert notification" button in settings.

### Changed
- Alerts are listed by creation date (most recent first) instead of by priority.
- The `@domain` part of email addresses is no longer displayed.
- The Override Do Not Disturb setting is gone: alerts ring through Do Not Disturb while you are
  on call and respect it otherwise.
- Alerts that bypass Do Not Disturb now ring on the alarm stream, so a low notification volume
  no longer hides them. Existing alert channel customisations are reset.

## [0.6.0] - 2026-11-25

### Added
- Setting to choose how far ahead your shifts are shown: 1, 3, 6 months or 1 year (default).
  It applies to My on-calls, the "You" line of each schedule and the calendar sync.

### Changed
- Release APKs are optimised builds, much smaller and faster. They are signed with the same key,
  so they install over previous versions.
- Smoother scrolling in the schedule week calendar.

## [0.5.0] - 2026-11-25

### Added
- "My on-calls" card on the on-call tab, opening your shifts for the next 6 months grouped by
  month, with shift and hour totals.
- Optional sync of your shifts to a "Jicrade on-call" calendar on the phone, kept up to date in
  the background (Settings).

### Fixed
- The schedule week view could miss Sunday shifts.

## [0.4.1] - 2026-11-25

### Fixed
- Long lists of people on call in a schedule card wrap instead of squeezing the last name.
- Two people could get nearly identical red colors; deep orange is replaced by amber.

## [0.4.0] - 2026-11-25

### Changed
- The schedule calendar shows one card per week with a row per day instead of an hourly grid.
  Each day is a 24h bar with the initials of who is on call; tap a slot to see the full name
  and hours. Weeks load as you scroll in either direction, and "Today" jumps back to the
  current week.

## [0.3.0] - 2026-11-25

### Added
- Week calendar per on-call schedule: who is on call when, with start and end hours and one
  color per person. Navigate to any past or future week with the arrows or by swiping; tap a
  shift for its details.

### Changed
- The on-call tab lists all schedules; tap one to open its calendar. Each person on call now and
  next shows their color. The text "Upcoming" list is replaced by the calendar.

## [0.2.0] - 2026-10-15

### Added
- Swipe horizontally between alert views (open, acknowledged, closed) in the alert list.

### Changed
- Team picker in settings uses Material 3 radio buttons in a wider dialog, opened from a
  navigation chevron instead of a "Change" text button.
- Notification permission and battery optimisation entries in settings are hidden once granted.
- App version is shown below the logout entry in settings.

### Fixed
- Double status bar padding at the top of screens.

## [0.1.0] - 2026-10-14

### Added
- Initial release of Jicrade, a mobile client for Jira Service Management Operations.
- Browse and manage on-call alerts (acknowledge, close, details).
- Notifications for new alerts and on-call shift tracking.

[0.8.0]: https://github.com/arnaudmorin/jicrade/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/arnaudmorin/jicrade/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/arnaudmorin/jicrade/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/arnaudmorin/jicrade/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/arnaudmorin/jicrade/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/arnaudmorin/jicrade/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/arnaudmorin/jicrade/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/arnaudmorin/jicrade/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/arnaudmorin/jicrade/releases/tag/v0.1.0
