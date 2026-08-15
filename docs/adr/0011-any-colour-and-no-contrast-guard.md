# Let the owner mix any colour, and guard none of them

An Accent is a colour and any colour can be stored (ADR-0010), but until now the
owner could only have one of the seven the editor offered. Those seven were
chosen against the Dial's white disc: each one reads as the filled arc and again
as the faded track, which is why white is not among them and why the palette's
yellow is a dark gold rather than the yellow everybody means.

The editor now mixes as well as offers, and the question the palette never had to
answer arrives with it: what happens when the owner mixes white.

The answer is nothing. Every colour is allowed, none is refused, and none is
warned about or quietly moved somewhere more legible.

A contrast guard was the obvious alternative and it was considered in three
strengths, all rejected:

- **Refusing a colour under a threshold.** It makes a slider that stops, or a
  button that greys out, for a reason the owner did not ask about — and the
  threshold is arbitrary. WCAG's 3:1 against white is a rule about text; the
  Progress Arc is a thick stroke and reads well below it. A number that cannot be
  defended should not be the thing that says no.
- **Warning about one.** The warning is right once and wrong every time after,
  because the owner who mixed a pale arc on purpose has to dismiss it on every
  visit. A dialog that is always dismissed teaches the owner to dismiss dialogs.
- **Nudging one — darkening the colour until it passes.** This is the worst of
  them. The owner would settle on a colour, and the app would store a different
  one, and the mixer would open on the app's colour rather than theirs. The whole
  point of the sliders is that what is seen is what is stored.

What makes refusing unnecessary is that the mistake is visible and cheap. The
mixer draws the Event's own Dial above the sliders, so a colour that vanishes
vanishes while it is being made, in front of the owner, before it is settled on.
And if one is settled on anyway, nothing is lost: the arc is a decoration on a
number that is still there, the Event keeps its date and its title, and picking
again is three taps. This is one person's home screen, not a product with users
to protect from themselves.

The contrast rule itself stays exactly where it was: on the seven colours the
editor offers, asserted in `DialLayoutTest` as the filled arc and as the faded
track. That is a rule about what the app puts in front of someone, which is a
different question from what it will accept — the palette is the app's taste, and
the mixer is the owner's.

## Consequences

- PRD story 31 is withdrawn. It promised "the Progress Arc visible against the
  white disc whichever Accent I pick, so that no colour choice is a bad one", and
  a mixer that allows white cannot keep it. The story is left in the PRD as a
  withdrawal rather than deleted, so the promise is visibly gone rather than
  quietly missing — the same shape ADR-0009 used for v1's promise that a widget
  worked the moment it was dropped.
- "A custom colour picker" leaves the PRD's Out of Scope list. What is left on
  that line, custom fonts and a light or dark Dial, is untouched: the disc is
  white in every build, so "invisible" means one thing and not a thing that
  changes with a setting.
- White is mixable and is still not one of the seven. The palette is a set of
  good defaults and a colour that is invisible by default is not one.
- Nothing on disk changes. ADR-0010 already writes any colour as `#RRGGBB`, so a
  mixed white stores and reads back like any other colour, and an older build
  reading it draws it as asked.
- `trackColour` fades whatever it is given, so an invisible arc has an invisible
  track. There is no case where half the Dial disappears and half does not.
- A widget whose arc cannot be seen is not a bug report. If one is ever raised
  the answer is in this file: it was chosen, and it can be chosen again.
