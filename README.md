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
