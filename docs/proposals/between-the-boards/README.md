# Between the Boards: prototypes

Scratch work behind the catalogue "Between the Boards": 60 suggested improvements to Pip & Pop,
each shown as a real picture of the app now and after the change. The catalogue is under review.
Nothing in this folder is part of the app, and nothing in it is built or tested by Gradle or CI.

## What is here

- `prototypes/` holds small Compose mock-ups of the proposed screens, built from the app's own
  tokens, fonts, art and parts, and the Roborazzi test that photographs them next to the app's
  current screens.
  - `ProtoKit.kt`: shared pieces. The row of choices, the chunky switch, fields and the code box,
    the picture tile, faces, the cup, the Pip and Pop loader, the toast.
  - `ProtoHome.kt`: the home. Picture tiles, the connect bar, the room bar, the month strip, the
    night's target.
  - `ProtoPlay.kt`: a game's sheet, the rules sheet, the lobby, the game screen, the result card,
    "Another game", the night's finale.
  - `ProtoScores.kt`: the scoreboard, a game's story, solo records, the month's cup, the profile,
    the colour themes.
  - `ProtoBits.kt`: reactions and lines, the dialog, the join sheet, the menu sheet, looks.
  - `CatalogueShots.kt`: the test that draws every picture, "now" from the real screens and "new"
    from the mock-ups, in light and dark.
- `new_sounds.py` makes first takes of the 17 proposed sounds with the helpers in
  `scripts/make-sounds.py`.

## Drawing the pictures again

```bash
mkdir -p app/src/test/java/com/pippop/ui/proto
cp docs/proposals/between-the-boards/prototypes/*.kt app/src/test/java/com/pippop/ui/proto/
./gradlew testDebugUnitTest --tests 'com.pippop.ui.proto.CatalogueShots'
```

The pictures land in `app/build/outputs/cat/`. Remove the copied folder afterwards: the mock-ups
are not meant to ship or to run in CI.

Two pictures in the catalogue were put together by hand after the test ran, because Robolectric
draws a stock dialog without the dimmed screen behind it: `rules-now` is `rules-raw` placed over
`golf-setup-now`, and `dialog-now` is `dialog-raw` placed over `home2-two-now`.

## Making the sound samples again

```bash
python3 docs/proposals/between-the-boards/new_sounds.py /tmp/new-sounds
```

It needs numpy and ffmpeg, as `scripts/make-sounds.py` does.
