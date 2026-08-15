# Build the app natively in Kotlin

Android home screen widgets run inside the launcher's process and are built from
`RemoteViews`. Only native code can draw one, so the widget has to be Kotlin.

The app is a single config screen, so that is Kotlin and Compose too. Adding a
second toolchain for one form would not pay for itself.
