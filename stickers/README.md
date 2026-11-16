# Stickers

Extra stickers players can add to the reactions tray (the **+** at the end of it). The app lists
`index.json` and downloads `<id>.json`. Nothing about a sticker is built into the app, so a new
one needs no app update: once its files are on the repository's default branch, phones list it.
A phone that is sent a sticker it hasn't got fetches it by itself.

Builds made by this repository's GitHub Actions read this folder of the same repository
(`https://raw.githubusercontent.com/<owner>/<repo>/HEAD/stickers`; the preview app reads the branch
it was built from). Local builds have none. The **+** only shows in a room whose server can carry
a pack sticker (the `packs` cap); it travels as the reaction key `p:<id>`.

## Adding a sticker

1. Draw it as an SVG on a 120 x 120 grid, in `scripts/art/packs/<id>.svg`, from plain shapes
   (`path`, `rect`, `circle`, `ellipse`, `polygon`, `polyline`). Copy `sunny-day.svg`. A rect with
   `id="backdrop"` is the card behind it (a colour or a gradient), and one with `id="frame-line"`
   is left out (the app draws the frame). Original drawings only: no brand names, no copyrighted
   characters. An element with `class="a glow"` (or another animation named in
   `ui/art/Stickers.kt`) moves; an unknown name just stays still.
2. **The id** is the file's name: lower-case letters, digits and hyphens, 1 to 32 characters
   (`^[a-z0-9-]{1,32}$`). Ids are never reused for a different sticker, because phones keep what
   they have downloaded. The sticker travels as `p:` followed by the id.
3. Run `python3 scripts/make-stickers.py` (needs `pip install svgelements fonttools brotli`). It
   writes `stickers/<id>.json`, and prints its size.
4. Add its entry to `index.json`:
   `{ "id": "...", "name": "Sunny day", "tag": "one short line", "version": 1, "size": 2270 }`.
   `name` is what a player reads (24 characters at most; it is also what a phone shows in a bubble
   if the sticker is slow to arrive). `size` is the file's bytes, and optional. Raise `version`
   when you change a sticker that is already published, so phones that have it are offered the update.
5. Optional: `<id>.ogg`, a short sound for the sticker, and `"sound": true` in its entry. Apps do
   not play it yet.
6. Commit the files. The scripts' check (`python3 scripts/test_make_stickers.py`) and the app's
   tests read every sticker listed.

## Limits

The app refuses a file outside these and simply ignores it (it never crashes):

- `<id>.json` is at most 100,000 bytes, with at most 400 shapes, each path at most 8,000 characters.
- Numbers are finite; a line is at most 50 wide; a dash list has at most 8 entries.
- A colour is `#AARRGGBB` (or `null` for none). A shape is data: path text made of the SVG path
  commands, numbers and separators, nothing else. There is no code in a sticker.
- `id` inside the file is the file's name. Keep the whole sticker well under the limit: it is
  downloaded on a phone's data plan, once.

## The file

```json
{
  "id": "sunny-day", "name": "Sunny day", "version": 1,
  "backdrop": { "type": "linear", "colours": ["#FF7CC8F5", "#FFD9F1FF"], "x1": 60, "y1": 0, "x2": 60, "y2": 120 },
  "parts": [
    { "d": "M 90,58 A 30,30 0 0,1 ...", "fill": "#FFFFC93C", "stroke": "#FF14121C", "width": 3,
      "dash": null, "roundCap": false, "roundJoin": false, "anim": "glow", "group": 1 }
  ]
}
```

`backdrop` is optional (without one the sticker is just its figure). Its `type` is `solid`
(one colour), `linear` (`x1 y1 x2 y2`) or `radial` (`cx cy r`). Shapes are drawn in order. `anim`
and `group` are optional: shapes with the same group move together.
