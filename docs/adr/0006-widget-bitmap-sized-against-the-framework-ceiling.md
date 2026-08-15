# Size the widget bitmap against the framework's ceiling, not the Binder buffer

The Dial was first capped at 320 pixels square, on the reasoning that the bitmap
travels inside the one-mebibyte Binder transaction buffer and should take no more
than 40% of it. That reasoning was wrong. A bitmap over 16KB is written to
anonymous shared memory and the parcel carries a file descriptor, so the
transaction sees a handful of bytes however big the Dial is.

The limit that does apply is the framework's own. `AppWidgetService` works out a
ceiling of one and a half screens' worth of bitmap per `RemoteViews` — `6 * width
* height` bytes, being 1.5 × 4 bytes a pixel — and refuses an update over it. On
a 1080 × 2400 phone that is 15,552,000 bytes, fifteen times the transaction
buffer, which is the clearest sign the buffer was never the constraint.

A square Dial cannot breach that ceiling. The widget is no bigger than the
screen, so the Dial's side is at most the screen's short side, and
`4 * min(w, h)²` is at most `4 * w * h` — two thirds of the allowance. A third of
it is spare at the largest size any launcher can offer.

The cap is therefore not a safety limit but a judgement about how many pixels are
worth drawing, and it is 1080: the short side of a 1080p phone, which is the
screen this is built for. The Dial is drawn pixel for pixel there at every size,
up to a widget filling the screen's width.

## Consequences

- On a screen whose short side is over 1080 pixels — a 1440p phone, a tablet — a
  widget big enough to fill it gets a Dial scaled up by up to a third or a half.
  Drawing 1440 square instead would cost 8,294,400 bytes a redraw for pixels only
  the largest widget on the densest phone would show.
- A full-width widget costs a 4.4MB bitmap per redraw, held briefly in this
  process and then in the launcher's.
- A refused update is retried at half the size rather than left as a blank square,
  because the reasoning above is about another process and the failure would land
  where no test can see it.
