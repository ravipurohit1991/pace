# Releasing Pace

Releases are tag-driven. Pushing a `vMAJOR.MINOR.PATCH` tag builds a signed APK in GitHub
Actions and publishes it as a GitHub Release. Nothing is built or signed on a laptop.

## One-time setup

1. **Generate the signing key.** It lives outside the repository, at
   `%USERPROFILE%\.pace-signing\pace-release.jks`, so it cannot be committed by accident.
   The generated `keystore.properties` in the repo root points at it and is git-ignored.

2. **Upload four repository secrets** (`gh secret set ...`):

   | Secret | Contents |
   | --- | --- |
   | `PACE_KEYSTORE_BASE64` | The `.jks` file, base64-encoded |
   | `PACE_KEYSTORE_PASSWORD` | Keystore password |
   | `PACE_KEY_ALIAS` | `pace` |
   | `PACE_KEY_PASSWORD` | Key password |

3. **Back the keystore up offline.** If the key is lost, no future build can ever update an
   existing install — Android refuses an APK signed by a different key, and every user would
   have to uninstall and lose their history. There is no recovery path.

## Cutting a release

```sh
# 1. Write the changelog. The filename is the versionCode: 0.2.0 -> 200.
#    IzzyOnDroid reads this file, and the workflow uses it for the release notes.
$EDITOR fastlane/metadata/android/en-US/changelogs/200.txt

git commit -am "Release 0.2.0"
git push

# 2. Tag and push. That is the whole trigger.
git tag v0.2.0
git push origin v0.2.0
```

The workflow then derives the version, runs the unit tests, builds and signs, verifies the
APK carries a v2/v3 signature, and publishes the release.

### Versioning

`versionName` is the tag without its leading `v`. `versionCode` is that semver packed into an
integer — `major * 10000 + minor * 100 + patch`, so `0.2.0` is `200` and `1.4.12` is `10412`.
It rises monotonically, which is what F-Droid clients and Obtainium use to detect updates, and
it does not depend on commit counts that rewriting history would disturb.

Neither value is stored in `app/build.gradle.kts`; the defaults there (`1` / `0.1.0`) only
apply to local builds off a bare checkout.

## Distribution

**Direct / Obtainium.** Every release attaches `pace-<version>.apk`. Users can install it
directly, or point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository to
track releases and update automatically.

**IzzyOnDroid.** A third-party F-Droid repository that most F-Droid users already have
enabled. It pulls the APK from GitHub Releases, so there is no reproducible-build requirement
and the app keeps its own signature. `fastlane/metadata/android/en-US/` holds the listing it
reads. To request inclusion, open an issue at
<https://gitlab.com/IzzyOnDroid/repo/-/issues> with the repository URL.

**Main F-Droid** is deliberately not used. F-Droid builds from source on their own
infrastructure and signs with *their* key, so migrating there later would change the app's
signature and force every existing user to uninstall and lose their local history.

## Local release build

`assembleRelease` signs automatically when `keystore.properties` is present, and produces an
unsigned APK when it is not, so a fresh clone still builds.

```sh
./gradlew assembleRelease
```

On Windows, `java` is not on `PATH`; use Android Studio's bundled runtime first:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```
