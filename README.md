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

The release keystore lives outside the repo. Generate it once:

```sh
mise run keystore
```

That writes the keystore to `~/.android-keystores/countdown-release.jks` and a
gitignored `keystore.properties` pointing at it. To wire up a keystore you
already have, copy `keystore.properties.example` to `keystore.properties` and
fill it in instead.

Back the keystore up. Losing it means the next build cannot install over the
one on the phone.

A release build with no signing details fails on `verifyReleaseSigning`, before
anything is compiled, and says what is missing.

## Building and running

```sh
mise run build      # signed release APK in app/build/outputs/apk/release/
mise run install    # install over adb and launch it
mise run test       # JVM unit tests
```
