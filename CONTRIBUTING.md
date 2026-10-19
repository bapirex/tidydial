# Contributing to TidyDial

Thanks for helping out. Bug reports, translations and pull requests are all welcome.

## Ground rules

- **Stay offline.** TidyDial has no `INTERNET` permission and must never need one. Do not add
  network access, analytics, crash reporting, ads or any proprietary dependency.
- **Never surprise the user.** Contacts are edited only after the user has reviewed and confirmed
  the change, and every change must be undoable.
- **Keep it small.** New dependencies need a good reason and an Apache-2.0-compatible license.
  Add them to `gradle/libs.versions.toml` and credit them in `NOTICE`.

## Development

Requirements: JDK 21 and the Android SDK (compile SDK 37).

```bash
./gradlew assembleDebug          # build a debug APK (org.tidydial.debug)
./gradlew testDebugUnitTest      # unit, Robolectric and screenshot tests
./gradlew lintDebug              # Android lint
./gradlew verifyRoborazziDebug   # compare UI against app/src/test/screenshots/
./gradlew recordRoborazziDebug   # re-record screenshots after an intended UI change
```

CI runs `testDebugUnitTest lintDebug assembleRelease` and `verifyRoborazziDebug` on every push
and pull request.

## Pull requests

- Keep each PR focused on one change, and explain *why* in the description.
- Add or update tests. Formatting logic lives in `PhoneNumberFormatter` and is covered by
  `PhoneNumberFormatterTest`.
- If you change the UI, re-record the screenshots and commit the updated PNGs.
- Put all user-facing text in `app/src/main/res/values/strings.xml`, and add a comment for
  translators when the context is not obvious.
- Use fake phone numbers in tests, screenshots and issues (for example `+1 212-555-01xx`).
  Never share real contact data.
- Follow the official Kotlin code style (`kotlin.code.style=official`).

## Reporting bugs

Please include your Android version, the selected country and format, and an example number
that reproduces the problem. Replace real digits with fake ones that show the same behavior.

By contributing you agree that your contributions are licensed under the Apache License 2.0.
