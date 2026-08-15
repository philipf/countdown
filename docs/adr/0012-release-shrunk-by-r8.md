# Shrink the release build with R8

The release build runs R8 with resource shrinking, and packages English only.

Without it the APK was 7.90 MB, and almost none of it was this app. The two dex
files held 15,112 classes, of which 63 were the app's own — 28 KB, three tenths
of one percent. Compose accounted for 5.6 MB: material3 alone shipped 2,415
classes so the editor could draw a few of them. The rest was the Kotlin standard
library, coroutines and the AndroidX modules Compose depends on, all carried
whole because nothing was asked to work out what the app reaches.

Shrinking takes it to 1.24 MB, a sixth of what it was, and the dex fits in one
file again rather than needing two.

Nothing needed a keep rule. The app reaches nothing by reflection: every
`::class.java` names a component declared in the manifest, which R8 keeps by
itself, and Compose and the Kotlin standard library carry their own rules as
consumer files. `app/proguard-rules.pro` exists because `proguardFiles` names it
and is deliberately empty.

The locale filter is separate from the shrinking and worth 204 KB of the saving.
The app has no translations, so the only strings the other locales carried were
AndroidX's own. Screen reader labels are unaffected — those come from the app's
`values/strings.xml`, and dropping locales drops translations, not the labels.

The cost is that a release stack trace is obfuscated. `mapping.txt` under
`app/build/outputs/mapping/release/` is what reads one, and the release workflow
does not keep it, so a trace from a published APK can only be read by rebuilding
that tag. This is a personal app whose crashes are reported by the one person
who can rebuild it, so that is a fair trade rather than a gap to close.
