# Changelog

All notable changes to Edge Performance Control are documented here. Only the `root` flavor is
distributed to testers and versioned below; `shizuku` is a dev-only comparison build.

## [Unreleased]
- Added a "View source on GitHub" link in the app, pointing at this repo.

## [0.1.2] - 2026-09-13
- Added in-app update checking: an automatic silent check on launch, plus a "Check for Updates"
  button that reports status either way and can download and install a newer build.
- Added an explicit "Grant Root Permission" button, so requesting root is a visible action
  instead of only happening silently in the background on launch.

## [0.1.1] - 2026-09-13
- Fixed manual fan speed getting silently overridden by the system's own `thermal-engine`
  (specifically its `FAN-MITIGATION` monitor re-driving the fan from a temperature sensor).
  Only fixed when the Performance thermal profile is active.
- Removed the "(Shizuku)" suffix from the `shizuku` flavor's app name, since only `root` ships.

## [0.1.0] - 2026-09-12
- Initial tester build.
- Rebranded to "Edge Performance Control": Razer green accents, true-OLED-black dark theme
  (with a light theme too), custom adaptive launcher icon.
- Split into `root` and `shizuku` Gradle product flavors - `root` supports the full feature set
  via `su`/a root manager; `shizuku` is non-root and only supports manual fan speed.
- Manual fan speed control (Quiet/Balanced/Perf/Max presets or a custom percentage) via direct
  i2c control of the MAX31760 fan controller.
- Thermal Profile switching (Stock/Performance) via a proper Magisk/APatch module overlay -
  loosens the skin-temp throttle curve without ever writing to `/vendor` live.
