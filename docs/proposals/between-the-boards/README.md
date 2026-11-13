# Between the Boards

A round of improvements to what happens between the boards of Pip & Pop: choosing a game, setting
it up, its result, the score between two players, and the last stock Android parts. The owner
reviewed a catalogue of 60 ideas and kept 54. This folder is everything needed to build them.

**Start with `PLAN.md`.** It holds the rules (the real app is in use and must not change until the
owner says so), the order of work and what each step must deliver. `CHECKLIST.md` is its list to
tick.

## What is here

- `PLAN.md`: the build plan, for the Claude session doing the work.
- `CHECKLIST.md`: every step and idea, to tick as they land.
- `targets/`: pictures of the proposed screens (WebP, 660 px wide, light and dark where it
  matters). They were drawn from the prototypes after the review, so they show only what was kept.
- `prototypes/`: the Compose mock-ups behind those pictures, built from the app's own tokens,
  fonts, art and parts, and the Roborazzi test that draws them next to the app's current screens.
  They are reference code, not part of the app: nothing here is built or tested by Gradle or CI.
  - `ProtoKit.kt`: shared pieces. The row of choices, the chunky switch, fields and the code box,
    the picture tile, faces, the cup, the Pip and Pop loader, the toast.
  - `ProtoHome.kt`: the home. Picture tiles, the connect bar, the room bar.
  - `ProtoPlay.kt`: a game's sheet, the rules sheet, the lobby, the game screen, the result card,
    "Another game".
  - `ProtoScores.kt`: the scoreboard, a game's story, solo records, the month's cup, the profile,
    the colour themes.
  - `ProtoBits.kt`: reactions and lines, the dialog, the join sheet, the menu sheet, looks.
  - `CatalogueShots.kt`: the test that draws every picture, "now" from the real screens and "new"
    from the mock-ups.
- `new_sounds.py`: first takes of the proposed sounds, made with the helpers in
  `scripts/make-sounds.py`.
- `setup-android-sdk.sh`: installs the Android SDK for a session whose container has none.

## Drawing the pictures again

```bash
mkdir -p app/src/test/java/com/pippop/ui/proto
cp docs/proposals/between-the-boards/prototypes/*.kt app/src/test/java/com/pippop/ui/proto/
./gradlew testDebugUnitTest --tests 'com.pippop.ui.proto.CatalogueShots'
rm -r app/src/test/java/com/pippop/ui/proto
```

The pictures land in `app/build/outputs/cat/`. The mock-ups compile against the app as it was
before this round; once the app's screens change they may need the commit this folder was added
in. They are not meant to ship or to run in CI, which is why the copy is removed again.

## Making the sound samples again

```bash
python3 docs/proposals/between-the-boards/new_sounds.py /tmp/new-sounds
```

It needs numpy and ffmpeg, as `scripts/make-sounds.py` does.
