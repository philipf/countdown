# Countdown

An Android app that shows, on the home screen, how many days are left until one
date. See `CONTEXT.md` for the vocabulary and `docs/PRD.md` for the spec.

## Setup

```sh
mise install
```

That is the whole setup. It provides the JDK, Gradle and the Android command
line tools, then fills `ANDROID_HOME` with the platform, build tools and
`platform-tools`.

The SDK lands in `$XDG_DATA_HOME/countdown/android-sdk`, usually
`~/.local/share/countdown/android-sdk`. It is outside the repo, so every
worktree shares one copy and `mise install` in a second worktree downloads
nothing. It is still per user, so nothing is installed system-wide and
`rm -rf ~/.local/share/countdown` undoes it.

There is no Gradle wrapper. Gradle is pinned in `.mise.toml` and comes from
`mise`, so there is one version to keep in step rather than two.

### Versions

| Tool | Version | Why |
| --- | --- | --- |
| JDK | Temurin 21 | The Android Gradle Plugin does not support Java 25, the newer LTS. |
| Gradle | 9.7.0 | |
| AGP | 9.3.1 | Has built-in Kotlin support, so there is no separate Kotlin plugin. |
| Android platform | 37.1 (Android 17) | The current platform. Checked against `sdkmanager`, not assumed. |

`minSdk` is 26, which gives `java.time` without desugaring. `targetSdk` is 37.

## Signing

The release keystore lives outside the repo and its password lives in `pass`.
Set both up once:

```sh
mise run keystore
```

It asks for the password twice, without echoing it, stores it at
`countdown/release-keystore/password`, then generates
`~/.android-keystores/countdown-release.jks` with it. That entry is the only
copy of the password, and the keystore is the only copy of the key: back both
up, or the next build cannot install over the one on the phone.

Re-running is safe. With the keystore and the password both in place it does
nothing; with only one of them it fills in the other, asking for the password
if that is the missing half.

Neither the keystore path nor the key name is secret, so both are plain build
config in `app/build.gradle.kts`. Only the password is looked up, in this
order:

1. `pass show countdown/release-keystore/password`
2. `$COUNTDOWN_KEYSTORE_PASSWORD`
3. `storePassword` in a repo-root `keystore.properties` — gitignored; see
   `keystore.properties.example`

The keystore and `pass` are both outside the repo, so a fresh worktree needs no
signing setup and removing one takes nothing with it.

The build never opens a passphrase prompt: it runs `gpg` in a mode that fails
rather than asking, so a headless build cannot hang. If gpg cannot unlock your
key, run `pass show countdown/release-keystore/password` once and the agent
caches it for the build. On a machine with no `pass` at all, set
`COUNTDOWN_KEYSTORE_PASSWORD`.

A release build with no keystore, no password, or a password that does not open
the keystore fails on `verifyReleaseSigning`, before anything is compiled, and
says which of those it is.

To use a keystore you already have, put its path and key name in
`keystore.properties`.

## Building and running

```sh
mise run build      # signed release APK in app/build/outputs/apk/release/
mise run install    # install over adb and launch it
mise run test       # JVM unit tests
```

## CI

`.github/workflows/ci.yml` runs the tests, lint and a debug build on every push
to `main` and every pull request. It needs no secrets: a debug build is signed
with the throwaway key AGP generates. A docs-only change skips it.

Lint runs here as early warning. It is `lintDebug`, not the `lintVitalRelease` a
release build runs, so it is not the same check — the release workflow runs the
real one itself.

## Releasing

A release is a run of `.github/workflows/release.yml`, given the version to cut:

```sh
gh workflow run release.yml -f version=0.2.0
```

It runs the tests, builds and signs the APK, and only then tags the commit and
publishes the release with generated notes. The tag is the last thing that
happens, not the trigger, so a run that fails a test, trips lint or cannot sign
leaves the repo untouched — fix it and run again. Nothing needs unpicking, and
there is never a tag pointing at a version that was never built.

The version is still the tag and the tag is still the version; the workflow
creates it rather than you. `versionName` is the version you gave it, and
`versionCode` is computed as
`major * 10000 + minor * 100 + patch`, so `0.2.0` is `200`. That keeps the
number rising as long as neither the minor nor the patch part reaches 100; the
build refuses a version that would break the ordering.

The tag is made on the runner, so `git fetch --tags` after a release to see it
locally.

A local build is given no version, so it is `0.0.0-dev` with `versionCode` 1.
That is below every release, so a release APK installs over a local build but
not the reverse — `adb uninstall ninja.notnot.countdown` first when going back.

### Secrets

The workflow needs two repository secrets. The keystore is the same one
`mise run keystore` made, base64 encoded so it survives as text:

```sh
base64 -w0 ~/.android-keystores/countdown-release.jks |
  gh secret set RELEASE_KEYSTORE_BASE64

# printf, not head alone: a trailing newline would be stored as part of the
# password. The password stays in a pipe, never on a command line.
printf '%s' "$(pass show countdown/release-keystore/password | head -1)" |
  gh secret set RELEASE_KEYSTORE_PASSWORD
```

The workflow writes the keystore back to
`~/.android-keystores/countdown-release.jks` on the runner and passes the
password as `$COUNTDOWN_KEYSTORE_PASSWORD`, which is the second of the three
sources the build already looks in. Nothing about the build is CI-specific.

### The runner

`jdx/mise-action` reads the same `.mise.toml`, so CI and a laptop build with
one set of versions. The Android SDK it installs is cached against that file's
hash, so only a toolchain change pays for the download again.
