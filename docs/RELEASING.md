# Releasing Pace

Releases are tag-driven. Pushing a `vMAJOR.MINOR.PATCH` tag builds a signed APK in GitHub
Actions and publishes it as a GitHub Release. Nothing is built or signed on a laptop.

## One-time setup

1. **Generate the signing key.** It lives outside the repository, at
   `%USERPROFILE%\.pace-signing\pace-release.jks`, so it cannot be committed by accident.
   The generated `keystore.properties` in the repo root points at it and is git-ignored.

2. **Upload four repository secrets** (`gh secret set NAME --body VALUE`):

   | Secret | Contents |
   | --- | --- |
   | `PACE_KEYSTORE_BASE64` | The `.jks` file, base64-encoded |
   | `PACE_KEYSTORE_PASSWORD` | Keystore password |
   | `PACE_KEY_ALIAS` | `pace` |
   | `PACE_KEY_PASSWORD` | Key password |

   Use `--body` rather than piping on stdin; `gh secret set` does not strip a trailing
   newline, and a corrupted keystore only surfaces as an opaque signing failure in CI.

3. **Back the keystore up offline.** If the key is lost, no future build can ever update an
   existing install — Android refuses an APK signed by a different key, and every user would
   have to uninstall and lose their history. There is no recovery path.

## Cutting a release

```sh
# 1. Write the changelog. The filename is the versionCode: 0.2.0 -> 200.
#    The workflow uses it verbatim as the release notes.
$EDITOR changelogs/200.txt

git commit -am "Release 0.2.0"
git push

# 2. Tag and push. That is the whole trigger.
git tag -a v0.2.0 -m "Pace 0.2.0"
git push origin v0.2.0
```

The workflow then derives the version, fails fast if any signing secret is missing rather
than publishing an unsigned APK, runs the unit tests, builds and signs, verifies the result
with `apksigner`, and publishes the release.

### Versioning

`versionName` is the tag without its leading `v`. `versionCode` is that semver packed into an
integer — `major * 10000 + minor * 100 + patch`, so `0.2.0` is `200` and `1.4.12` is `10412`.
It rises monotonically, which is what update checkers use to detect a new version, and it does
not depend on commit counts that rewriting history would disturb.

Neither value is stored in `app/build.gradle.kts`; the defaults there (`1` / `0.1.0`) only
apply to local builds off a bare checkout.

### Verifying a published APK

Every release is signed with the same key. Its fingerprint is:

```
SHA-256: 80:29:7E:4E:3A:05:AD:6C:1B:B6:B7:A3:A5:00:31:74:FB:D7:02:2A:AF:FA:19:B6:B2:FA:16:95:0E:24:63:AB
```

```sh
apksigner verify --print-certs pace-<version>.apk
```

A mismatch means the APK did not come from this pipeline.

Note that the APK carries a **v2** signature only, not v3. That is correct for `minSdk 26`:
v2 covers Android 7.0 and up, while v3 — whose only added benefit here would be signing-key
rotation — requires API 28 and would mean dropping Android 8 and 9 devices.

## Distribution

Pace is distributed **only through GitHub Releases**. Each release attaches
`pace-<version>.apk`. Users either install it directly, or point
[Obtainium](https://github.com/ImranR98/Obtainium) at this repository, which watches releases
and prompts them when a new version lands.

There is deliberately no app-store listing:

- **Google Play** costs a $25 registration fee, and personal accounts additionally need a
  12-tester, 14-day closed test before production access.
- **IzzyOnDroid** rejects apps created wholly or partly by generative AI tools, and separately
  rejects apps that front large commercial AI platforms. Pace is both, and its inclusion form
  requires disclosing the first. See their
  [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/).
- **Main F-Droid** builds from source and signs with *their* key. Migrating there would change
  the app's signature, forcing every existing user to uninstall and lose their local history —
  which for an app whose whole value is a personal record is the wrong trade.

Self-hosting an F-Droid repository remains possible if that ever becomes worth the
maintenance; nothing in this setup rules it out, since the signing key stays ours either way.

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
