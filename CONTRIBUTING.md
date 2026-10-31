# Contributing to TidyDial

Thanks for helping out. Bug reports, translations and pull requests are all welcome.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md). To report
a security problem, please do not open a public issue: see [SECURITY.md](SECURITY.md).

## Ground rules

- **Stay offline.** TidyDial has no `INTERNET` permission and must never need one. Do not add
  network access, analytics, crash reporting, ads or any proprietary dependency.
- **Never surprise the user.** Contacts are edited only after the user has reviewed and confirmed
  the change, and every change must be undoable.
- **Keep it small.** New dependencies need a good reason and an Apache-2.0-compatible license.
  Add them to `gradle/libs.versions.toml` and credit them in `NOTICE`, in the "Open-source
  libraries" section of the About sheet and in the README.
- **No new permissions.** The app asks for `READ_CONTACTS` and `WRITE_CONTACTS`, and for
  `CALL_PHONE` only when the user calls through another app that requires it. Please open an issue
  to discuss anything that would change that before you write code.

## Development

Requirements: JDK 21 and the Android SDK (compile SDK 37). Gradle is fetched by the wrapper.

```bash
./gradlew assembleDebug          # build a debug APK (net.evopoint.tidydial.debug)
./gradlew testDebugUnitTest      # unit, Robolectric and screenshot tests
./gradlew lintDebug              # Android lint
./gradlew verifyRoborazziDebug   # compare UI against app/src/test/screenshots/
./gradlew recordRoborazziDebug   # re-record screenshots after an intended UI change
./gradlew assembleRelease        # minified release APK (unsigned)
```

The debug build has the application ID `net.evopoint.tidydial.debug`, so it can be installed next to a
release build. Test with a device or emulator whose contacts are fake: TidyDial edits the real
contacts on the device it runs on.

## Making a change

1. For anything bigger than a small fix, open an issue first so we can agree on the approach.
2. Fork the repository and create a branch from `main` (for example `fix-uk-extensions`).
3. Make your change, with tests (see below).
4. Run the same checks as CI before you push:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleRelease verifyRoborazziDebug
   ```

5. Open a pull request against `main` and fill in the checklist in the template.

### What CI checks

The **Test, lint and build** job runs on every push to `main` and on every pull request. It runs
`testDebugUnitTest`, `lintDebug`, `assembleRelease` and `verifyRoborazziDebug`, and it must pass
before a pull request can be merged. If it fails, the test and lint reports are attached to the
run as an artifact named `reports`.

For pull requests from outside contributors, a maintainer has to approve the workflow before it
runs. That is a GitHub safety measure, not a judgment on your change, so please be patient if
the check shows as waiting.

### Screenshots

The UI is covered by Roborazzi screenshot tests, and the reference images live in
`app/src/test/screenshots/`. If you change the UI on purpose:

```bash
./gradlew recordRoborazziDebug
```

Then look through the changed PNGs, commit them with your change and mention them in the pull
request. If you did not mean to change the UI and `verifyRoborazziDebug` fails, the comparison
images in `app/build/outputs/roborazzi/` show what moved.

### Tests

- Add or update tests for every behavior change. Formatting logic lives in
  `PhoneNumberFormatter` and is covered by `PhoneNumberFormatterTest`.
- Use **fake phone numbers only** in tests, screenshots, issues and pull requests (for example
  `+1 212-555-01xx`). Never share real contact data, including your own.

### Code style

- Follow the official Kotlin code style (`kotlin.code.style=official`).
- Put all user-facing text in `app/src/main/res/values/strings.xml`. Do not hard-code text in
  Kotlin.

### Commits and merging

- Keep each pull request focused on one change, and explain *why* in the description.
- Write commit messages and the pull request title in the imperative mood, as a short summary
  (for example "Keep extensions when formatting UK numbers").
- Pull requests are **squash-merged**, and the pull request title becomes the commit title on
  `main`. You do not need to tidy up your branch history; just keep the title clear.
- `main` is protected: changes land only through pull requests with a passing CI check and a
  maintainer's approval.

## Translations

Translations are very welcome.

- Strings live in `app/src/main/res/values/strings.xml`. Add a translation as
  `app/src/main/res/values-<language>/strings.xml` (for example `values-de`).
- Read the comments above each string: they explain the context for translators.
- Skip strings marked `translatable="false"`.
- Keep placeholders such as `%1$s` and `%2$d` as they are.
- Sample phone numbers in strings are fake and must keep the same format.

If you add or change an English string whose meaning is not obvious, add a comment for
translators above it.

The store listing can be translated too: copy `fastlane/metadata/android/en-US/` to a folder
named after your locale (for example `fastlane/metadata/android/de-DE/`) and translate the text
files. Keep the short description within 80 characters.

## Reporting bugs and requesting features

Please use the [issue forms](https://github.com/bapirex/tidydial/issues/new/choose). For bugs,
include your Android version, the app version (shown in the About sheet), the selected country
and format, and an example number that reproduces the problem. Replace real digits with fake ones
that show the same behavior.

## License

TidyDial is licensed under the [Apache License 2.0](LICENSE). As described in section 5 of the
license, any contribution you submit for inclusion in TidyDial is licensed under the same
Apache License 2.0, without any additional terms. There is no contributor license agreement to
sign.

## Releasing (maintainers)

Releases are cut by the maintainers from version tags. The steps are described in
[docs/RELEASING.md](docs/RELEASING.md).
