# Releasing TidyDial

This is for maintainers. A release is a `vX.Y.Z` tag: the [release workflow](../.github/workflows/release.yml)
builds the signed APK and AAB, publishes them as a GitHub Release, and uploads the AAB to the
Google Play internal track. F-Droid builds the same tag from source and checks that its build
matches the signed APK on the GitHub Release.

## Every release

1. **Bump the version** in `app/build.gradle.kts`: increase `versionCode` by one and set
   `versionName` (`X.Y.Z`). Both stores reject a `versionCode` they have already seen.
2. **Write the changelog** in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
   Keep it to 500 characters or fewer (the limit on Google Play and F-Droid; the workflow
   checks it). It becomes the GitHub Release notes and the Play "What's new" text.
3. **Run the checks** locally:

   ```sh
   ./gradlew testDebugUnitTest lintDebug verifyRoborazziDebug assembleRelease
   ```

4. **Merge** the version bump to `main` through a pull request as usual, and wait for CI.
5. **Tag and push** from the updated `main`:

   ```sh
   git switch main && git pull
   git tag -a vX.Y.Z -m "TidyDial X.Y.Z"
   git push origin vX.Y.Z
   ```

   The tag must match `versionName`, or the workflow stops. A pre-release tag such as
   `vX.Y.Z-rc1` makes a GitHub pre-release and skips Google Play; F-Droid ignores it.
6. **Approve the deployment.** The run waits for the `release` environment: open it under
   **Actions**, choose **Review deployments**, and approve.
7. **Check the GitHub Release**: it has `tidydial-X.Y.Z.apk`, `tidydial-X.Y.Z.aab`,
   `SHA256SUMS` and the changelog as notes.
8. **Promote in the Play Console**: the build lands on the internal track. Test it, then
   promote it to the closed, open or production track (**Test and release** > the track >
   **Promote release**). While the release status is `draft` (see below), first open the draft
   on the internal track and roll it out.
9. **F-Droid** needs nothing: with `UpdateCheckMode: Tags`, its checker sees the new tag,
   builds it, and publishes the APK signed by us once the builds match. This can take a few
   days. If the build does not match, F-Droid's build log shows the differing files.

## One-time setup

### 1. Create the release key

One key signs the app for both Google Play and F-Droid. **If it is lost, the app can never be
updated again on either store**, so keep it outside the repository, with its passwords in a
password manager and an offline backup (for example on an encrypted USB drive).

```sh
keytool -genkeypair -v \
  -keystore ~/tidydial-release-keys/tidydial-release.jks \
  -storetype PKCS12 \
  -alias tidydial \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`keytool` asks for a password and for the certificate's name fields. In a PKCS12 keystore the
key password is the same as the keystore password, so use that value for both secrets below.
Do not keep the key inside the repository; `.gitignore` ignores `*.jks`, `*.keystore` and
`release-keys/` only as a safety net.

### 2. Note the certificate fingerprint

F-Droid's `AllowedAPKSigningKeys` and the workflow's `EXPECTED_CERT_SHA256` both need the
certificate's SHA-256 as lowercase hex without colons:

```sh
keytool -list -v -keystore ~/tidydial-release-keys/tidydial-release.jks -alias tidydial \
  | sed -n 's/^.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'
```

After a release, the same value is the `Signer #1 certificate SHA-256 digest` printed by
`apksigner verify --print-certs tidydial-X.Y.Z.apk` (in the Android SDK's `build-tools`).

Put it in `.github/workflows/release.yml` (`EXPECTED_CERT_SHA256`) and
`docs/fdroid/org.tidydial.yml` (`AllowedAPKSigningKeys`).

### 3. Add the secrets to the `release` environment

Run these yourself; `gh` reads the values from standard input or prompts for them, so they
never appear on the command line:

```sh
base64 -i ~/tidydial-release-keys/tidydial-release.jks | gh secret set TIDYDIAL_KEYSTORE_BASE64 --env release
gh secret set TIDYDIAL_KEYSTORE_PASSWORD --env release
gh secret set TIDYDIAL_KEY_ALIAS --env release        # tidydial
gh secret set TIDYDIAL_KEY_PASSWORD --env release     # same as the keystore password
gh secret set PLAY_SERVICE_ACCOUNT_JSON --env release < path/to/service-account.json
```

Until `PLAY_SERVICE_ACCOUNT_JSON` is set, the workflow skips the Play upload.

The same four `TIDYDIAL_*` variables (with `TIDYDIAL_KEYSTORE` as the path to the `.jks`)
make a local `./gradlew assembleRelease bundleRelease` produce signed outputs. Without them the
release build is unsigned, which is what F-Droid's build server uses.

### 4. Google Play App Signing

Play re-signs what users download with the app signing key. Use our own key, so the APKs on
Google Play, F-Droid and GitHub share one signature:

1. When creating the first release in the Play Console, open **App signing** and choose
   **Use existing app signing key** > **Export and upload a key from Java keystore**.
2. Download `pepk.jar` and the encryption public key from that page, then run:

   ```sh
   java -jar pepk.jar \
     --keystore="$HOME"/tidydial-release-keys/tidydial-release.jks \
     --alias=tidydial \
     --output=tidydial-private-key.zip \
     --include-cert \
     --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```

3. Upload `tidydial-private-key.zip`, then delete it.
4. Upload the first AAB (`tidydial-X.Y.Z.aab` from a GitHub Release) by hand; Play's API
   accepts uploads only after the app exists.

The same key is also the upload key, so the AAB the workflow uploads is accepted as is.

### 5. Play API access for the workflow

1. In Google Cloud, create a project, enable the **Google Play Android Developer API**, create
   a service account, and add a JSON key for it.
2. In the Play Console, **Users and permissions** > **Invite new users**: add the service
   account's email with access to TidyDial and permission to release to testing tracks and
   to production (or only testing tracks, if you promote by hand).
3. Store the JSON as `PLAY_SERVICE_ACCOUNT_JSON` (step 3), then delete the local copy.

Play accepts only `draft` releases until the app has been published once, so the workflow
uploads drafts by default. After the first release is live on any track, switch to completed
releases:

```sh
gh variable set PLAY_RELEASE_STATUS --env release --body completed
```

### 6. F-Droid

Submit `docs/fdroid/org.tidydial.yml` to [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
as `metadata/org.tidydial.yml`. Its `Binaries` entry points at the APK on the GitHub Release,
and `AllowedAPKSigningKeys` at our certificate.

## Reproducible builds

F-Droid only publishes our APK if its own unsigned build is byte-for-byte identical apart
from the signature. The build is set up for that: no version-control info or dependency
metadata in the APK, and no ART baseline profile in release builds. To check locally, copy the
tree into two directories at different paths (without `build/`, `.gradle/` and `.kotlin/`,
since stale outputs from earlier builds can change the APK), build each, and compare:

```sh
./gradlew assembleRelease   # without the TIDYDIAL_* variables, in each copy
shasum -a 256 app/build/outputs/apk/release/app-release-unsigned.apk
```

To compare an unsigned build with a signed release APK the way F-Droid does, use
[apksigcopier](https://github.com/obfusk/apksigcopier):

```sh
apksigcopier compare tidydial-X.Y.Z.apk --unsigned app/build/outputs/apk/release/app-release-unsigned.apk
```

## Store graphics

The Play feature graphic is rendered by `FeatureGraphicTest`. After changing it, record it and
copy it to the store metadata:

```sh
./gradlew recordRoborazziDebug --tests 'org.tidydial.ui.components.FeatureGraphicTest'
cp app/src/test/screenshots/feature_graphic.png fastlane/metadata/android/en-US/images/featureGraphic.png
```
