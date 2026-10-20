# F-Droid metadata

`org.tidydial.yml` is a draft of the build recipe that F-Droid keeps in its
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository, at
`metadata/org.tidydial.yml`. F-Droid reads it to build TidyDial from source.
This copy is for reference: once the app is accepted, the copy in fdroiddata is
the one F-Droid uses, and its bot updates that copy when we push new release tags.

The app name, summary, description, icon, screenshots and changelogs are not in
this file. F-Droid reads them from `fastlane/metadata/android/` in this repository.

## How the build works

TidyDial uses a developer-signed reproducible build:

1. F-Droid checks out the `v1.0.0` tag and runs `assembleRelease` in `app/`.
   No signing variables are set, so this gives an unsigned APK.
2. It downloads our signed APK from the GitHub Release (`Binaries:`).
3. It checks that our APK is signed by our key (`AllowedAPKSigningKeys:`) and is
   identical to its own build once the signature is copied across.
4. If both checks pass, F-Droid publishes our signed APK. If not, it skips that
   version. It never signs TidyDial with its own key.

Because of this, the APK on F-Droid has the same signature as the one on Google
Play, and people can switch between the two stores without uninstalling.

The buildserver image runs Debian 13 (trixie), where the default JDK is
OpenJDK 21, so the recipe needs no `sudo` step. F-Droid uses its own Gradle
wrapper, which already knows Gradle 9.8.2 and has the same checksum as
`gradle/wrapper/gradle-wrapper.properties`.

## Before submitting

1. Tag `v1.0.0` and let the release workflow publish `tidydial-1.0.0.apk` to
   the GitHub Release. The download URL must be exactly
   `https://github.com/bapirex/tidydial/releases/download/v1.0.0/tidydial-1.0.0.apk`.
2. Check the two values in `org.tidydial.yml` that this repository cannot
   derive, and fill them in if they are still placeholders:
   - `AuthorEmail`: the public contact address.
   - `AllowedAPKSigningKeys`: the SHA-256 of the release signing certificate,
     in lowercase hex with no colons:

     ```sh
     apksigner verify --print-certs tidydial-1.0.0.apk \
       | sed -n 's/.*certificate SHA-256 digest: //p'
     ```

     or from the keystore:

     ```sh
     keytool -list -v -keystore release.jks -alias <alias> \
       | sed -n 's/.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'
     ```

3. Check that F-Droid's build will match ours. Build the tag from a fresh clone
   with JDK 21 and no signing variables, then compare:

   ```sh
   git clone --branch v1.0.0 https://github.com/bapirex/tidydial.git /tmp/td
   (cd /tmp/td && ./gradlew assembleRelease)
   pip install apksigcopier   # needs apksigner on PATH
   apksigcopier compare tidydial-1.0.0.apk \
     --unsigned /tmp/td/app/build/outputs/apk/release/app-release-unsigned.apk \
     && echo reproducible
   ```

   Sign release APKs through Gradle's signing config, as the release workflow
   does. F-Droid's docs warn that `apksigner` from build-tools 35 and later
   writes APKs that `apksigcopier` cannot verify. Don't re-sign by hand.

## Submitting

1. Sign in to GitLab and fork <https://gitlab.com/fdroid/fdroiddata>.
2. Clone your fork and create a branch named `org.tidydial`.
3. Copy `org.tidydial.yml` to `metadata/org.tidydial.yml`.
4. Optionally, test it with fdroidserver (`pip install fdroidserver`, or the
   `registry.gitlab.com/fdroid/fdroidserver:buildserver` container for a full build):

   ```sh
   fdroid readmeta
   fdroid rewritemeta org.tidydial    # must leave the file unchanged
   fdroid lint org.tidydial
   fdroid checkupdates --allow-dirty org.tidydial
   fdroid build -v -l org.tidydial    # full build; needs the buildserver container
   ```

5. Commit with the message `New app: TidyDial`, push, and open a merge request
   against `fdroid/fdroiddata` `master` titled `New app: TidyDial`. Your fork
   must be public and the branch must not be protected.
6. Choose the **App inclusion** merge request template. Paste the two answers
   below into its "Abstract" section, attach two or three of the fastlane
   screenshots, and tick each checklist item once it is true.
7. Wait for the CI pipelines to pass and answer reviewer questions. After the
   merge, the app usually appears within one or two days.

When a later release is tagged `vX.Y.Z` (and not something like `v1.1.0-rc1`),
F-Droid's update checker adds the new build entry. Each release needs a higher
`versionCode` and a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
file of at most 500 characters.

## Merge request text

For "Please introduce the functions and usage of your app":

```markdown
TidyDial gives every phone number in your contacts one clean, consistent format:
International, National, E.164 or RFC 3966. Numbers without a country code are
read as local to a country that TidyDial detects from the SIM, network or
language, and you can change it. Numbers are listed by contact with their
current and new form. You choose which ones to update, confirm, and can undo the
last update, even after restarting the app. Only the phone number field is
changed. Numbers are parsed on the device with libphonenumber.

* Source code: https://github.com/bapirex/tidydial
* License: Apache-2.0
* Store text, icon, screenshots and changelogs: fastlane/metadata/android/en-US
```

For "Please elaborate why you wrote this app":

```markdown
Contacts collect numbers in many formats over the years: with and without a
country code, with spaces, dashes or brackets. TidyDial fixes them in bulk
while staying careful:

* Every change is shown before anything is written, and the last update can be
  undone.
* Numbers it cannot recognize, emergency numbers, short service numbers and
  codes such as *#06# are never changed. Extensions after a pause or wait are kept.
* Numbers synced by messaging apps are skipped by default, because those apps
  overwrite edits with their own copy. You can choose which accounts to include.
* A number that changed somewhere else after TidyDial read it is skipped, not
  overwritten.

Why it fits F-Droid:

* Free software under Apache-2.0. Every runtime dependency is free software
  (AndroidX, Jetpack Compose, Kotlin coroutines, libphonenumber), all from
  Maven Central or Google's Maven repository.
* No anti-features: no ads, tracking, analytics, Google Play Services, Firebase
  or other proprietary libraries.
* No network access: the manifest removes the INTERNET and ACCESS_NETWORK_STATE
  permissions. The only permissions are READ_CONTACTS and WRITE_CONTACTS.
* No prebuilt binaries in the repository other than the Gradle wrapper JAR.
  Dependency metadata (`dependenciesInfo`) and VCS info are left out of the APK.
* Reproducible build: the release APK on GitHub is signed with the developer key
  and pinned with `AllowedAPKSigningKeys`, so F-Droid can publish the same APK
  that Google Play ships, and users can switch between the two.

Build notes: Gradle 9.8.2, Android Gradle Plugin 9.4.1, Kotlin 2.4,
compileSdk/targetSdk 37, minSdk 26. Needs JDK 21 (`jvmToolchain(21)`), the
default JDK on the Debian 13 buildserver image. `gradle: [yes]` builds the single
`release` variant, unsigned when no signing environment variables are set.
I am the author of the app.
```

## Known risks

- **Toolchain versions.** AGP 9.4, Gradle 9.8 and compile SDK 37 are recent.
  F-Droid's buildserver downloads Gradle and SDK packages on demand, and the
  Gradle checksum is already known. If SDK platform 37 or the build-tools that
  AGP 9.4 needs are not available on the buildserver yet, the build fails and a
  reviewer has to update the image or ask us to wait. Read the MR pipeline log.
- **JDK 21.** The recipe relies on Debian 13's default JDK. If the buildserver
  ever runs an image without JDK 21, add this to the build entry:

  ```yaml
      sudo:
        - apt-get update
        - apt-get install -y openjdk-21-jdk-headless
        - update-java-alternatives -a
  ```

- **Reproducibility.** Any byte difference between our signed APK and F-Droid's
  build makes F-Droid skip that version. Known causes are the ART baseline
  profiles (`assets/dexopt/baseline.prof[m]`), different JDK or Android SDK
  versions, and building from a dirty tree. Release only from a clean checkout of
  the tag, and run the `apksigcopier compare` check above for each release.
- **The `commit` field.** The draft uses the tag `v1.0.0`, which F-Droid's update
  checker also writes for later versions. The reference docs prefer a full commit
  hash, so a reviewer may ask for `git rev-parse v1.0.0` instead.
- **Project age.** F-Droid's inclusion policy expects an app to have been actively
  developed for about three months. TidyDial's history is younger than that, so
  reviewers may ask to wait or make an exception.
- **Google's Android developer verification.** Google is rolling out a
  requirement that apps installed on certified Android devices come from a
  developer who has registered their identity, package names and signing keys
  with Google. Enforcement began in a few countries from September 2026, with a
  wider rollout planned. F-Droid opposes the requirement. An APK that F-Droid
  signs with its own key could not be registered under our developer account.
  Our developer-signed APK uses the same key as Play, so once `org.tidydial` and
  that key's fingerprint are registered in the Play Console, the F-Droid APK is
  covered too.
