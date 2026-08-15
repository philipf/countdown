# Store an Accent by name when the palette offers it, as a hex when it does not

An Accent used to be one of a closed set, stored as the name of an enum constant
— `BLUE`, `MID_GREY` — and read back by looking that name up, with anything
unrecognised falling back to the default. It is now a colour, and the editor
offering seven of them is the editor's business. A colour nobody named has to be
storable too, and every value written by every build so far has to keep reading
back as the colour it meant.

Storing every Accent as a hex was the first idea and was rejected. It is one
shape instead of two, but it makes the name of a colour a thing the app used to
believe rather than a thing it stores, and that costs something real: yellow was
retuned to a dark gold while it was being added, and a phone holding `YELLOW`
followed the correction. A phone holding `#FFEB3B` would have kept the arc nobody
could see. Names are also what an older build reads, so writing hexes for the
seven would recolour every Event on any phone that went back a version. The
reader has to know the names either way, so the second shape buys nothing.

Storing a name and a colour together — the name when there is one, the colour
beside it — was rejected as two fields where the app has one, and as two things
that can disagree. There is nothing sensible to do with a store that says `RED`
and `#00FF00`.

So there are two shapes under the one `accent` key, and which one is written
follows from the colour rather than from how the owner arrived at it:

- One of the offered colours is written under its constant's name, exactly as
  before. Nothing on disk changes for an Event that already exists, and nothing
  needs rewriting on upgrade.
- Any other colour is written as `#RRGGBB`, upper case.

The `#` is what keeps them apart. A constant's name is letters and underscores,
so no name can be read as a colour and no colour as a name, and there is no value
that is ambiguously both. Reading is: a name the palette offers, or a `#` and
exactly six hex digits, or the default. Both cases are read since a hex digit is
a hex digit; only upper case is written.

There is no alpha in the stored colour because an Accent has none. The arc is
drawn on the Dial's white disc and not through it, so a half-transparent Accent
would come out as a paler colour than the one that was picked. `Accent.of` puts
the alpha back to `FF` whatever it is handed, which makes six digits the whole of
a colour and makes an eight-digit value one of the things that reads as the
default.

## Consequences

- Renaming one of the constants in `NamedAccent` loses that colour on every phone
  that has it: the name on disk stops matching and the Event reads as blue.
  Changing the *shade* of one recolours those Events on purpose, which is the
  behaviour that made a name worth storing. `AccentTest` names both the constants
  and the shades so neither can happen quietly.
- Dropping a colour from the palette has the same effect as renaming it. A colour
  that is retired rather than replaced would need its name kept as something the
  reader still understands.
- A colour and its name are the same Accent, so picking red out of the palette
  and mixing the same red by hand store identically. The picker in #19 does not
  have to know which colours have names.
- The seven offered colours are held to a contrast rule against the white disc,
  as the filled arc and as the faded track. That rule is on what the app offers,
  not on what an Accent can be — a colour arriving from anywhere else is drawn as
  asked. Whether a picker refuses a colour that cannot be seen is #19's decision.
- `trackColour` was already the Accent's own hue with the app's own alpha, so it
  needed no change to work on a colour that was never in the palette.
