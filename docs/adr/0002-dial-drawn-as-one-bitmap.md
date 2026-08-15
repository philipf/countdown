# Draw the whole Dial as one bitmap

`RemoteViews` has no arc primitive, so the Progress Arc has to be drawn on a
`Canvas` and handed to the widget as a bitmap either way.

Rather than combine that bitmap with `RemoteViews` `TextView`s, one renderer
draws the disc, arc, number, label and title together. The widget layout is then
a single `ImageView`, and the same renderer draws the preview on the config
screen, so the preview and the widget cannot disagree.

## Consequences

- Text in the Dial ignores the system font size setting.
- The renderer has to redraw when the widget is resized
  (`onAppWidgetOptionsChanged`).
