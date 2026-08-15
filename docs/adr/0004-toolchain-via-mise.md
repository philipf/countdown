# Manage the Android toolchain with mise

The JDK, Android SDK and Gradle are installed by mise and pinned in
`.mise.toml`. `ANDROID_HOME` points at `$XDG_DATA_HOME/countdown/android-sdk`,
outside the repo.

The SDK was inside the repo at first. Work happens one git worktree per ticket,
so that gave each worktree its own copy: every ticket downloaded the SDK again,
and deleting a worktree deleted the SDK a running Gradle daemon was still
pointing at, which broke the next build elsewhere. One shared path fixes both.

The path is still per user, so nothing is installed system wide, and `rm -rf` on
it undoes the install. There is no Android Studio setup, because this machine is
not otherwise used for Android work.

The JDK is pinned to an LTS that the Android Gradle Plugin supports, which is
not always the newest LTS.
