# Keep rules for the release build's R8 pass.
#
# Empty on purpose. The app reaches nothing by reflection: the only ::class.java
# uses name components declared in the manifest, which R8 keeps by itself, and
# Compose and the Kotlin stdlib carry their own rules as consumer files. Add
# here only what a stack trace from a minified build proves is missing.
