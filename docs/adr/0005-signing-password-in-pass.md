# Keep the release signing password in pass

The password for `~/.android-keystores/countdown-release.jks` lives in `pass` at
`countdown/release-keystore/password`. `mise run keystore` asks for it, stores
it, and generates the keystore with it.

It used to be a random password written to a gitignored `keystore.properties` in
the repo root. Work happens one git worktree per ticket, so that file was the
only copy of a password nobody had ever seen, and removing the worktree deleted
it. That happened, and the key it protected had to be thrown away. `pass` is
outside the repo and already holds this machine's secrets, so a worktree can
come and go without taking the password with it.

The password is typed by the person who owns the key, not generated, so there is
a copy in a head as well as in `pass`.

The keystore path and the key name are not secret and stay plain build config,
so the only thing that has to be looked up is the password. The build looks in
`pass`, then `$COUNTDOWN_KEYSTORE_PASSWORD`, then `keystore.properties`, so a
machine without `pass` — CI, or a build with no gpg agent — still has a way in.

Gradle reads it through a `ValueSource`, which is what the configuration cache
understands: the cache is still reused, and Gradle re-runs the lookup when it
checks the entry, so a changed password is noticed instead of a stale one being
used. `gpg` runs with `--pinentry-mode error`, so a build fails quickly rather
than hanging on a passphrase prompt with nobody there to answer it.
