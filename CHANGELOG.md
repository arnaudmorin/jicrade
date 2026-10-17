# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- In-app alert ringtone picker with an instant audio preview as soon as a ringtone is selected.
- Option to keep ringing until the alert is acknowledged or opened.
- Dedicated alert volume setting (with slider) on the alarm stream, so alerts stay loud even when the phone's notification volume is low.
- "Test alert notification" button in settings to check sound, volume and display in real conditions.

### Changed
- Alerts are listed by creation date (most recent first) instead of by priority.
- The `@domain` part of email addresses is no longer displayed.

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

[0.2.0]: https://github.com/arnaudmorin/jicrade/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/arnaudmorin/jicrade/releases/tag/v0.1.0
