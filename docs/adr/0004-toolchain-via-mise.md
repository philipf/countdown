# Manage the Android toolchain with mise

The JDK, Android SDK and Gradle are installed by mise and pinned in
`.mise.toml`. `ANDROID_HOME` points into a gitignored directory inside the repo.

Nothing is installed system wide and there is no Android Studio setup, because
this machine is not otherwise used for Android work.

The JDK is pinned to an LTS that the Android Gradle Plugin supports, which is
not always the newest LTS.
