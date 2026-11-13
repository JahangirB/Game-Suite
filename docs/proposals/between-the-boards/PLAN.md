# Between the Boards: the build plan

The owner reviewed the "Between the Boards" catalogue (60 ideas) and kept 54. This file tells the
Claude session doing the work how to build them. Read sections 1 to 6 before you touch anything.
Then work through section 7 one step at a time and tick `CHECKLIST.md` as you go.

In this folder:

| | |
|---|---|
| `PLAN.md` | this file |
| `CHECKLIST.md` | every step and every idea, to tick |
| `prototypes/` | Compose mock-ups of every proposed screen. Each step names the ones to port. They were written as test code, so they hold sample data and a few raw sizes; the look is what counts. |
| `targets/` | pictures of the mock-ups (660 px wide WebP, light and dark). Open them with the Read tool. This is what each screen should look like when its step is done. |
| `new_sounds.py` | recipes for the new sounds (step 31) |
| `setup-android-sdk.sh` | installs the Android SDK (section 3) |

## 1. Three rules that must not be broken

The app is in daily use. Every installed copy updates itself from the repository's releases.

1. **Nothing reaches the live branch until the owner writes the code word `lubudum`.** The live
   branch is the repository's default branch (`ccr-d293cbfe-amy86v` when this was written; check
   with `git remote show origin | grep 'HEAD branch'`). A push to it publishes the `latest` release,
   and every phone is offered it. All work goes to the work branch `between-the-boards`: do not
   commit this work on the live branch even locally, and before every push check that
   `git branch --show-current` is the work branch. If this
   session was told to develop on the live branch, the owner's message that pointed you here
   replaces that for this work. "Looks good", "ok" or "ship it" are not the code word: ask for it.
2. **The preview app's release stays a pre-release.** The CI job `preview` in
   `.github/workflows/build.yml` publishes the preview app with `--prerelease --latest=false`.
   The real app looks for updates at GitHub's "latest release", which is never a pre-release.
   Remove either flag and every installed Pip & Pop is offered the preview. Do not edit the
   publishing lines of the `preview` or `release` jobs.
3. **Servers are never deployed from here, and every server change keeps today's app working.**
   No `wrangler deploy`, no pasting into Apps Script. The owner's live server may pick up server
   code at any time, while phones still run today's app. Section 5.5 says how to stay compatible.

If you cannot keep one of these (for example, this session can only push to the live branch),
stop and tell the owner. Do not look for a way round.

## 2. Words used here

- **Live branch**: the default branch. **Work branch**: `between-the-boards`.
- **Real app**: Pip & Pop as installed today (id `com.gamesuite.app`, the `debug` build type that CI
  publishes as `latest`).
- **Preview app**: the same code built as the `preview` build type. Its id ends in `.preview`, its
  name is "Pip & Pop Preview", its icon is coral instead of blue, and it keeps its own data. It
  installs next to the real app. The owner tests everything in it before the code word.
- **The owner**: the person you are talking to. **Partner**: the person they play with.

## 3. Step 0: before you start (no commit)

1. **Branch.**
   ```bash
   git fetch origin
   git remote show origin | grep 'HEAD branch'                 # the live branch; never push there
   git switch -c between-the-boards origin/claude/brave-faraday-w5scs4
   git push -u origin between-the-boards
   ```
   `claude/brave-faraday-w5scs4` is the live branch plus this folder and the preview app's
   groundwork. The owner has given permission to create and push `between-the-boards`. If the push
   is refused and the only branch you may push to is another one that is not the live branch, use
   that one as the work branch and change `between-the-boards` in the `if:` of the `preview` job to
   its name (the job runs for branches whose name starts with that text).
2. **Build environment.** The build needs JDK 17 or newer, the Android SDK (platform 35, build
   tools 34.0.0) and two hosts beyond the package registries: `dl.google.com` (the SDK and Google's
   Maven repository) and `gcp.mirrors.maven.org` (Robolectric's Android jars are redirected there).
   - Try `./gradlew assemblePreview`. If it works, skip to 3.
   - In a cloud session, call `get_environment`. The owner already has an environment named
     **Game-Suite**, made for this repository, with both hosts allowed and a setup script that
     installs the SDK. If it is listed for this session's repository and can be switched to, move
     this session onto it with `switch_environment`. Otherwise add the two hosts to this session's
     own environment (`update_environment`), or propose a personal one (`create_environment`), as
     those tools' descriptions say. The owner asked for this and will approve the prompts.
   - Switching changes the network only; a setup script does not run in a container that is
     already up. So install the SDK by hand:
     `bash docs/proposals/between-the-boards/setup-android-sdk.sh`
   - If a host is still refused, say which one and stop. Do not disable TLS checks or route round
     the proxy.
3. **Baseline.** Run the checks of section 4 on the untouched branch and note the result. Anything
   red before you start is not yours to fix silently: tell the owner.
4. **See the targets.** Open four or five pictures in `targets/` (start with `home-new.webp`,
   `lobby-new.webp`, `result-new.webp`, `scores-new.webp`) and read `prototypes/ProtoKit.kt`.

## 4. How to work

Work one step at a time, in order. Later steps use parts built by earlier ones.

For each step:

1. Read the step. Read the prototype functions it names and look at its target pictures.
2. Read the app files it names before changing them.
3. Build it in the app (`app/src/main/java/com/pippop`). Port the look from the prototype; write
   real state, real data and real behaviour in place of its samples.
4. Add or update tests: a screenshot test for every screen you changed (light and dark; see
   `app/src/test/java/com/pippop/ui/ScreenshotBase.kt` and its neighbours), and unit tests for
   logic (stores, records, rules of the three servers).
5. Check:
   ```bash
   scripts/check-tokens.sh
   ./gradlew testDebugUnitTest assemblePreview
   node --test backend/test/*.test.js               # when backend/Code.gs changed
   (cd backend/cloudflare && npm ci && npm test)     # when backend/cloudflare changed
   ```
   While working, run only the test classes you touched
   (`./gradlew testDebugUnitTest --tests 'com.pippop.ui.ScreenshotHomeTest'`); run everything before
   you push. Then open the new pictures in `app/build/outputs/roborazzi/` and compare them with
   the targets. A step is not done until you have looked.
6. Commit as `Boards step N: <the step's title>`, with the step ticked in `CHECKLIST.md` in the same
   commit. Push to the work branch. CI then builds and publishes the preview app, so the owner
   can follow along.
7. If a step turns out to need a decision that is the owner's (not a detail you can settle from
   the catalogue, the code or this plan), ask, and carry on with the next step that does not
   depend on the answer.

Do not skip, disable or weaken a test to get green. Do not fold several steps into one commit.
After a context reset, `CHECKLIST.md` and `git log` say where you are.

## 5. Rules for the code

### 5.1 The look

- Colours, corner radii, text sizes and spacing come from `ui/theme/Tokens.kt` and the palette
  (`AppTheme.palette`). `scripts/check-tokens.sh` fails on raw ones in screens; CI runs it. Where a
  prototype uses a raw size, use the nearest token, or add a token.
- Reuse the app's parts: `PrimaryButton`, `SecondaryButton`, `AppCard`, `pressedEdge`,
  `PlayerAvatar`, `ScreenTopBar`, `Pill`, `GameFrame`, `KeyArt`, `GameIcon`, `ResultAnimation`.
  The Toy Box look is a 2 dp ink outline, a 3 dp pressed edge, Baloo 2 for headings and digits,
  Figtree for the rest.
- Every screen works in light and dark and in all four colour themes once step 24 lands.
- Touch targets are at least 48 dp. Icon-only buttons have a content description. Text has a
  contrast of at least 4.5 to 1 against what is behind it (`readableOn` helps). Nothing is said
  by colour alone.
- Motion: reuse the durations in `ui/screens/Motion.kt` and honour `ui/theme/ReduceMotion.kt`.
  No new speeds (the UX plan rejected them). Nothing new makes a move wait.

### 5.2 Words

Follow `docs/COPY.md`: warm, playful, brief. A game is a "game", never a battle or a match. "Play
solo", "Leave", "Done", "Back to games", "Cancel" beside "Waiting for Alex…". Sentence case.
British spelling ("colour"). Say "they" for a player whose pronoun you don't know.

### 5.3 What the phone remembers

The real app's data must survive the update that follows the code word.

- Never rename or drop a stored key without reading the old one. Write a migration and a unit
  test for every stored value you change (steps 19, 20 and 23 say which).
- New values go where their neighbours are: settings in `data/ProfileStore.kt`, results in
  `data/Scores.kt`.

### 5.4 The preview app

- `BuildConfig.PREVIEW` is true only in the preview app. Use it for the "Preview app" card on the
  profile and its test tools, and for nothing that changes how the game plays.
- The preview has no Firebase, so invitations (push) are off in it. That is expected.
- The preview plays nearby only with other previews (`BuildConfig.NEARBY_SERVICE_ID`). Online it
  uses whatever server link the owner types in, which can be the live server.

### 5.5 The three servers

A room's rules exist three times and must behave the same: `backend/Code.gs` (Apps Script),
`backend/cloudflare/src/` (Cloudflare Worker; start at `room.js`) and
`app/src/main/java/com/pippop/data/nearby/LocalRoom.kt` (the phone that hosts a Nearby room).
Each has tests: `backend/test/code.test.js`, `backend/cloudflare/test/`, and
`app/src/test/java/com/pippop/data/nearby/`.

- **Additive only.** Today's app with a new server, and the new app with today's server, must
  behave exactly as today. Add optional fields and new calls; never change the meaning of an
  existing one. Unknown reaction and avatar keys are already ignored by today's app.
- **What a server can do.** Add one list to the room state every server sends:
  `"caps": ["lines", "packs", "faces", "memory-photos", "sudoku-opener"]`, holding what that server
  supports. In the app, `RoomState.caps` is that list, empty when a server sends none. A control
  that needs the server shows only when its cap is there.
- **What a phone can do.** The app already tells the server which games it knows
  (`BattleBackend.SUPPORTED_GAMES`; `sudoku-big` in it is the precedent for a feature rather than
  a game). Add a token there for each feature every phone in the room must understand
  (`sudoku-opener`, `memory-photos`). A server turns such a feature on only while every player in
  the room has the token; otherwise the room behaves as today.
- **New calls** get a default in `BattleBackend` that throws
  `ApiException("… needs the latest Pip & Pop.")`, as `setSlidePhoto` and `undoStroke` do.
- **Photos** travel like Slide Race's photo: a small base64 JPEG with a size cap, set by one
  call and fetched once by its id (`setSlidePhoto`, `getphoto`, `photoId`), never inside every
  sync. Apps Script has no photo calls today and gets none: the photo ideas (steps 29 and 30)
  work on the Cloudflare server and nearby, and Apps Script simply does not send their caps.
- Write the same test for each new rule in each server that has it.

## 6. What is not being built

**Left out by the owner. Do not build these, even where an old catalogue picture shows them:**

1. The month's score on the home (a strip under the connect bar).
2. This month's score on each home tile.
3. "Your best" on a game's sheet.
4. "How you two stand in this game" on a game's sheet.
5. "First to three": a target for the night.
6. "The night's winner": a finale for the night.

The prototypes and targets in this folder were redrawn without them.

**On hold: the decider.** The owner kept "The decider" (the lobby and the game say "Decider" when
both players are one win from the night's target) but left out the target it depends on. Do not
build it, and leave out its sound, until the owner says what it should mean without a target.

**Still not wanted** (from `docs/UX-PLAN.md`): turns played while one player is away, a daily
puzzle, a store release, links that open the app, a timeline of the pair's games, new motion
speeds.

## 7. The steps

Each step lists the ideas it covers (the ids are the catalogue's), what to build, where, what to
look at, and when it is done.

### Phase A: the preview app

#### Step 1. The preview app is real

*Ideas: the owner's test app.* Already in the branch: the `preview` build type
(`app/build.gradle.kts`), its name and icon colour (`app/src/preview/res/values/preview.xml`), its
own Nearby id (`data/nearby/NearbyCommon.kt`) and the CI job `preview`.

- After your first push of the work branch, wait for the "Build" run. The job "Publish preview
  app" must be green.
- With your GitHub tools, confirm: a release tagged `preview` exists, is a pre-release, and holds
  `PipPopPreview.apk`, `PipPop.apk` and `version.json`; and the latest release is still the one
  tagged `latest`, unchanged.
- In `ui/screens/ProfileScreen.kt`, when `BuildConfig.PREVIEW`, add a last card "Preview app":
  "This is the preview app. It keeps its own games and scores." and the version name. Steps 19 and
  21 add test buttons to it.
- Tell the owner the link: `https://github.com/<owner>/<repo>/releases/download/preview/PipPopPreview.apk`,
  that the preview updates itself after every push, and that invitations are off in it.

*Done when* the run is green, the pre-release is there, `latest` is untouched, and
`./gradlew assemblePreview` gives an APK whose id ends in `.preview`.

### Phase B: one toy box

Build the shared parts first; every later screen uses them. Put them in a new file
`ui/screens/Toy.kt`. Look at `prototypes/ProtoKit.kt` for all of phase B.

#### Step 2. Questions in the app's look

*Ideas: toy-dialog.* Target: `dialog-new`. Prototype: `DialogOver`, `LeaveDialogNew`.

- `ToyDialog`: a card with the ink outline, the heading font and real buttons, built on
  `androidx.compose.ui.window.Dialog`. The safe answer is a `SecondaryButton`; an answer that ends
  something (leave, give up) is a `PrimaryButton` in the palette's `bad` colour.
- Replace Material's `AlertDialog` in `ui/PipPopApp.kt` (two), `ui/screens/UpdateDialog.kt` and
  `ui/screens/ClashScreens.kt`. The two others go later: `LevelDialog` in `HomeScreen.kt` in step
  11, `RulesDialog` in `GameFrame.kt` in step 12.

*Done when* those four places use `ToyDialog` and a screenshot shows the leave question in light
and dark.

#### Step 3. One sheet

*Ideas: toy-sheet.* Target: `menu-new`. Prototype: `SheetOver`, `MenuSheetNew`.

- `ToySheet`: a wrapper round `ModalBottomSheet` that gives it the result sheet's look (read
  `ui/screens/ResultPopup.kt`): the card colour, the ink edge along the top, the app's handle.
- Use it in all eight places: `ui/PipPopApp.kt`, `ui/screens/SkinStoreSheet.kt`,
  `ui/screens/GameMenu.kt` (five), `ui/screens/HomeScreen.kt`.

*Done when* `ModalBottomSheet(` appears only inside `ToySheet`.

#### Step 4. Fields and the code

*Ideas: toy-fields, fix-password.* Target: `join-new`, `profile-new`. Prototype: `ToyField`,
`CodeBoxes`, `JoinSheetNew`.

- `ToyField`: the label above the field, the app's outline (the primary colour while focused), an
  optional line of help under it. Build it on `BasicTextField`.
- `CodeField`: one big box with the room code's letters spaced out, in the heading font. It takes
  4 to 12 characters, as `cleanCode` in `ui/BattleViewModel.kt` does.
- Replace all seven `OutlinedTextField`s: `DrawGuessScreen.kt`, `ProfileScreen.kt` (three),
  `BalloonScreens.kt`, `ConnectSheet.kt`, `FirstRunScreen.kt`.
- In `ConnectSheet.kt`, "Paste the message instead" becomes a `SecondaryButton`.
- The password field's label is "Password". Its explanation ("Only if the link has one. Ask
  whoever set it up.") is the line under the field.

*Done when* no `OutlinedTextField` is left and the join sheet matches the target.

#### Step 5. The app's own switch and choices

*Ideas: toy-controls.* Target: `menu-new`, `lobby-new`, `profile-new`. Prototype: `Segmented`,
`OptionRow`, `ToySwitch`.

- `Choices`: one control with two to four parts side by side, the chosen one filled (the
  prototype's `Segmented`). With more than four options it wraps into rows of equal cells.
- `OptionRow`: a label, a `Choices`, and a line under it only where a choice needs explaining.
- `ToySwitch`: the chunky switch.
- Replace every `FilterChip` that picks one of a few things (`ClashScreens.kt`, `DominoesScreens.kt`,
  `LudoScreens.kt`, `ProfileScreen.kt`, `TurnGameScreens.kt`) with `Choices` or `OptionRow`, and every
  Material `Switch` (`BlocksScreens.kt`, `GameMenu.kt`, `ProfileScreen.kt`, `WordRaceScreens.kt`) with
  `ToySwitch`. `ModeSwitch` in `HomeScreen.kt` and `OptionGroup` in `Components.kt` become `Choices`.

*Done when* no `FilterChip` and no Material `Switch` is left.

#### Step 6. Pip and Pop while you wait, and messages with a mark

*Ideas: toy-loader, toy-toast.* Target: `bits-new`. Prototype: `PipPopLoader`, `Toast`, `BitsNew`.

- `PipPopLoader`: two beads hopping in turn; two still beads when motion is reduced. Replace all
  five `CircularProgressIndicator`s (`LobbyScreen.kt`, `SoloScreen.kt`, `Components.kt`,
  `HomeScreen.kt`, `ConnectionBanners.kt`).
- `Toast`: a pill in the app's look with a mark for its kind of news, by `Tone`: a tick, an
  exclamation mark, a cross, an i. Replace the `SnackbarHost` in `ui/PipPopApp.kt`.
- Extend `scripts/check-tokens.sh` so it fails when `AlertDialog(`, `OutlinedTextField(`,
  `FilterChip(`, `CircularProgressIndicator(`, `SnackbarHost(` or Material's `Switch(` appear in
  `app/src/main/java/com/pippop`, and when `ModalBottomSheet(` appears outside `Toy.kt`. Until
  steps 11 and 12 remove them, let it pass for `LevelDialog` and `RulesDialog` by name, and take
  those exceptions out in those steps.

*Done when* the script passes and nothing stock is left but those two.

#### Step 7. A Rematch you can read

*Ideas: fix-rematch.* In `RematchButton` (`ui/screens/GameMenu.kt`), once the others are ready the
button is dark green with ink text: 2.7 to 1. Make the text white (or pick it with `readableOn`):
at least 4.5 to 1. Add a unit test for the contrast.

### Phase C: the home

#### Step 8. What the home remembers

*Ideas: the data for home-recent, home-putaway and fix-hint.* In `data/ProfileStore.kt`:

- `recentGames`: the last six different games played, newest first, each with when. Note a game
  whenever one starts: a solo game, or a room's game going to `PLAYING`.
- `putAway`: the games taken off the shelf.
- `arrangedOnce`: true after the first change of order.
- In `ui/screens/ArrangeGamesScreen.kt`, add a "Put away" line to the list. Games dragged or moved
  under it are put away; games moved above it come back. Nothing is deleted.

*Done when* unit tests cover the three values and the arrange screen has a screenshot with two
games put away.

#### Step 9. The home, before you connect

*Ideas: home-art, home-connect, home-recent, home-surprise, home-putaway, fix-hint.* Target:
`home-new`. Prototype: `ProtoHome.kt` (`HomeNew`, `HomeHeader`, `ConnectBar`), `ArtTile`, `Shelf`,
`RoundButton`, `GameBadge`. Work in `ui/screens/HomeScreen.kt`.

- **Tiles.** `GameTile` becomes the game's key art (`KeyArt`, 280 to 170) with its name under it,
  three to a row, each row still on its shelf (`ShelfRow`). Holding a tile still moves it. The
  one-line description is no longer on the tile; it stays in the game's sheet.
- **One line to connect.** `ConnectionCard` and `PartnerCard` become one bar: the Nearby/Online
  `Choices` and one `PrimaryButton`, "Connect with <partner>" when a partner is saved, "Connect"
  when not. The button opens the connect sheet, which now also holds what each way needs and the
  saved partner's details.
- **Jump back in.** A first row of up to three tiles: the Sudoku in progress first, tagged with
  how far along it is ("27% done", replacing `ContinueCard`), then the games played last, newest
  first, tagged "Today", "Yesterday", the weekday for the six days before, then the date. The row
  is absent when there is nothing to show.
- **The "Games" row.** The title, and three round buttons with content descriptions: a cup
  ("Scores", replacing the "🏆 Scores" text link), a die ("Surprise us") and a grip ("Arrange
  games"). Under the title, "Hold a game to move it" shows until `arrangedOnce`.
- **Surprise us.** A highlight flicks across the tiles for about half a second (not when motion
  is reduced), stops on a game and opens its sheet. It picks among the games on the shelf that
  fit everyone in the room; with no room, among those with solo play.
- **More games.** Put-away games leave the shelf and wait in a "More games · N" row at its end.
  Tapping the row opens it out into their tiles.

*Done when* the first screen of `ScreenshotHomeTest` shows nine games and the three recent ones,
in light and dark, and matches the target. There is no month strip (section 6).

#### Step 10. The home, connected

*Ideas: home-roombar, home-fit, home-face.* Target: `home2-new`, `home2-three`. Prototype: `RoomBar`,
`ArtTile` (`seats`, `dim`, `face`, `ring`), `Faces`.

- **Room bar.** `ConnectedCard` becomes: everyone's faces overlapping, the names on one line ("Maya",
  "Maya & Leo", "Maya, Leo & Kim", cut with an ellipsis), "Online · room K7Q2NB" or "Nearby" under
  them, "Leave" at the end. Tapping the room line shares the invite as today. A second line says
  what is happening: "<Name> is waiting in <Game>" with a Join button, or "Pick a game: whoever
  picks it sets it up."
- **Seats.** While connected, every tile shows how many it takes: "2" or "2–4" beside a person
  icon. A game that cannot take everyone in the room fades and says "2 players". Tapping it still
  opens its sheet, which explains as today ("Air Hockey is for 2.").
- **Faces.** When someone in the room is in a game (`PlayerInfo.at`), that game's tile gets a
  green outline and their face on the picture. The line and Join button in the room bar stay.

*Done when* screenshots show two and three players and match the targets. Tiles show no scores.

### Phase D: a game's sheet, solo, rules

#### Step 11. A game's sheet

*Ideas: sheet-facts, sheet-solo, fix-primary.* Target: `sheet-new`. Prototype: `GameSheetNew`, `Fact`.
Work in `GameSheet` (`HomeScreen.kt`) and every solo screen (`ui/PipPopApp.kt` lists them:
`Solo…Screen`).

- **Three facts** under the name, from table 9.2: how many play, about how long, and whether you
  take turns, race or play live. Add them to `ui/art/GameArt.kt` beside `tagline`. The minutes in
  the table are estimates: correct any that a game's own rules contradict.
- **Solo starts from the sheet.** Today "Play solo" opens a screen that asks for the level every
  time (three full-width main buttons for Connect Four, seven for Mini Golf). Move those choices
  into the sheet: one row of `Choices` when a game has a single choice ("Easy / Medium / Hard"), or
  one line that states the last setup with "Change" when it has more (Sudoku, Mini Golf, Bingo,
  Dominoes); "Change" opens a setup sheet made of `OptionRow`s. Remember the last setup of each
  game in `ProfileStore`. The button states it: "Play solo · Medium". It starts the game with no
  further screen.
- Remove each solo screen's level stage and `LevelDialog`. Where a solo game offered "change level"
  from inside, it opens the same setup sheet.

*Done when* every game with solo play starts from its sheet in one tap after the first time, no
screen shows more than one main button, and unit tests cover remembering the setup. There is no
"your best" line and no "you two" strip (section 6).

#### Step 12. Rules you can find your place in

*Ideas: rules-points.* Target: `rules-new`. Prototype: `RulesSheetNew`, `RulePoint`.

- In `ui/art/GameArt.kt`, replace `GameKind.rules: String` with a list of points, each a short
  title and a body. Rewrite all twenty games' rules. Four to six points per game; the first is
  "Goal"; a title is at most three words; a body is at most 30 words; a game's points total at
  most 120 words. Keep every rule that is true today: if one does not fit, add a point rather
  than drop it. Mini Golf's six points are written in the prototype; use them as they are.
- `RulesSheet` (a `ToySheet`) replaces `RulesDialog` in `ui/screens/GameFrame.kt` and the rules shown
  from a game's sheet ("How to play"). Its last button, "Show me how", opens the game's
  walk-through as `RulesDialog` does today (`TutorialDialog`, `ui/screens/Tutorials.kt`).
- A unit test checks the limits for every game.

*Done when* no game's rules are a paragraph and the stock-part exceptions of step 6 are gone.

### Phase E: the lobby

#### Step 13. The lobby's frame

*Ideas: lobby-art, lobby-code, lobby-ready.* Target: `lobby-new`. Prototype: `LobbyNew`, `ReadyBar`,
`ReadyButton`. Work in `ui/screens/LobbyScreen.kt` and `TurnReadyCard` (`TurnGameScreens.kt`).

- **The whole picture.** `LobbyBanner` shows the game's whole key art at the left, with the game's
  line and its three facts beside it.
- **The code steps aside.** Alone in the room, the code is big, with "Send the code" as a
  `SecondaryButton`. Once someone has joined, it folds into a small "Room K7Q2NB" label with a
  share mark in the Players card.
- **Ready stays put.** A bar pinned under the scrolling part of every lobby: the Ready button with
  everyone's face and light (as on the Rematch button), and a round React button. Not ready:
  "Ready" in coral. You are ready and waiting: "Waiting for <Name>…" with Cancel. The others are
  ready: green, "<Name> is ready: tap to start", in white. Ship Battle's fleet card keeps its own
  steps and uses the same bar.

*Done when* the Dominoes lobby fits one screen with Ready visible, in `ScreenshotLobbyTest`.

#### Step 14. The setup as rows of choices

*Ideas: lobby-rows.* Target: `lobby-new`. Prototype: `OptionRow` in `LobbyNew`.

Rebuild every options card with `OptionRow`s: `DominoOptionsCard`, `NumbersLevelCard`,
`WordLevelCard`, `SlideLevelCard`, `TttModeCard`, `MemoryDeckCard`, `CodeRulesCard`, `DotsBoardCard`,
`DrawModeCard`, the Corner Blocks "Help" card, `GolfLevelCard` (all called from `LobbyScreen.kt`),
and the "Change" sheet a result opens (`SetupSheet` in `GameMenu.kt`). Whoever opened the game
changes them; the others see them greyed with "<Name> is setting up".

*Done when* Dominoes' three options take about half their old height and no lobby uses a stack
of full-width buttons to pick a level.

#### Step 15. Sudoku: whoever opens it picks the level

*Ideas: lobby-sudoku. Servers: all three.* Target: `lobby-sudoku-new`. Prototype: `LobbyNew(sudoku = true)`.

Today both players tap a level and the game starts when the taps match (`LevelPicker`; "Maya
picked Easy. Pick Easy too to start."). New: the opener sets the level, Race or Together, and the
board with `OptionRow`s; everyone else sees them and taps Ready.

- The app adds the token `sudoku-opener` to `SUPPORTED_GAMES`; the servers add the cap of the same
  name. The servers use the opener's level while every player in the room has the token.
- In any other room (an older app is in it, or the server has no such cap), everything behaves
  as today, and the app shows today's `LevelPicker`. Keep that path and its tests.

*Done when* all three servers have a test for both paths, and the lobby matches the target.

### Phase F: in the game and at its end

#### Step 16. In the game

*Ideas: game-tonight, game-turn.* Target: `game-new`, `game-later`. Prototype: `TonightPill`,
`TurnBannerNew`, `GameNew`.

- **Tonight's score** in the title row of every game against one other player: both faces and
  the score since you connected (`BattleUiState.tally`). Have `GameFrame` show it itself, so the
  eighteen game screens do not each pass it. With three or four players it is absent.
- **"Your turn" on one line.** "Your turn" stays big; what to do is small under it, and from the
  player's third turn of a game it leaves. Apply it to `TurnLine` (`TurnGameScreens.kt`) and to
  each game that draws its own turn banner.

*Done when* the Connect Four screenshot matches both targets.

#### Step 17. The result goes under the board

*Ideas: result-under, result-faces, result-tally.* Target: `result-new`, `result-lose-new`.
Prototype: `ResultCard`, `WinnerFace`, `Crown`, `TallyRow`, `GameNew(finished = …)`.

Today a result is a sheet over the lower half of the game (`MatchPopup` and `ResultPopup` in
`ui/screens/ResultPopup.kt`, passed to `GameFrame` as `popup`). It hides most of the board,
including the winning line.

- When a game ends, what is no longer needed leaves (the turn banner, a keyboard, a number pad,
  a tray), the board moves up, and the result shows under it as a card. Nothing is covered and
  nothing dims. Give `GameFrame` a place for the result under its content and have each screen
  drop its controls once the game is over.
- **The card:** the winner's face with a crown and rays in their colour, the title (`resultTitle`)
  and its one line of detail, the game's scene (`ResultAnimation`) small at the right; then
  tonight's score as a row with both faces and big digits, where the winner's digit rolls up by
  one with a "+1"; then Rematch; then "Change" (for whoever opened the game) and "Another game".
- Solo results use the same card without the score row: "Play again" and "Back to games".
- Use the card wherever the board and the card fit a 411 × 891 dp screen without scrolling once
  the controls are gone. Games whose board is the whole screen (Mini Golf, Air Hockey, and any
  other that fails that test) keep the sheet. Note in `CHECKLIST.md` which games kept it.

*Done when* every game's result has a screenshot, won and lost, and the board's last position is
fully visible in every game that uses the card.

#### Step 18. "Another game", and the loser picks

*Ideas: result-next.* Target: `result-switch`. Prototype: `AnotherGameSheet`.

"Another game" opens a sheet: a line saying whose pick it is ("Maya lost that one, so Maya
picks. You can still pick if they'd rather."; "You lost that one, so you pick."; after a draw,
"A draw, so either of you picks."), six tiles as on the home (pictures and seats, the games that
fit the room, without the one just played), "All games" (opens the whole shelf out in the sheet)
and "Surprise us". It replaces the "OR PLAY" row (in `ResultPopup.kt`, drawn with `GameShelf` from
`GameMenu.kt` and fed by `MatchSession.nextGames`). The line is a suggestion: either player can
still pick.

### Phase G: scores and records

#### Step 19. Remember more about each game

*Ideas: the data for scores-story, scores-solo and result-record.*

- `MatchRecord` (`data/Scores.kt`) gains optional values: how long the game took; the game's own
  score for each side where it has one (goals, points, boxes, pairs); and the winner's count
  where "quickest" means fewest (discs, shots, tries). Old lines in `scores.jsonl` still load:
  test it.
- A solo records store: for each game and level, the best result, when, and how many were
  played. Table 9.3 says what "best" is per game; where a game already shows a number at its end,
  use that number. Move Sudoku's bests out of `Stats.solo` with a migration and a test.
- Record both when a game ends, online, nearby and solo.
- **Test tools** on the preview's "Preview app" card: "Fill with sample games" (about sixty
  results against "Maya" over this month and the three before, with times and scores; solo
  records for eight games; recent games), and "Clear sample games". Mark sample entries so
  clearing them never touches real ones.

*Done when* unit tests cover loading old lines, the new values, the solo store and its migration.

#### Step 20. The scoreboard

*Ideas: scores-race, scores-alltime, scores-story, scores-solo, scores-cups, fix-battles.* Target:
`scores-new`, `scores-game`, `scores-solo`. Prototype: `ProtoScores.kt` (`ScoresNew`, `SplitBar`,
`GameStorySheet`, `SoloRecords`), `TrophyCup`, `FormDots`. Work in `ui/screens/ScoreboardScreen.kt`
and `Scoreboard` (`data/Scores.kt`).

- A `Choices` at the top: This month, All time, Solo.
- **This month:** both faces, the score in the biggest digits, one bar split where you stand, the
  last five results as dots. Each game's row is one bar split the same way (not two bars scaled
  to the busiest game).
- **All time:** the same for every result kept.
- **A game's story** (tap a row): both players' records side by side: wins of all time, longest
  run, quickest win, biggest win; and the last ten as a row of winners' faces. A line is absent
  where there is nothing to say; older results count for wins only.
- **Solo:** one line per game with a solo best: the best and what it was on.
- **Cups:** gold cups with the winner's face, a silver one for a tie, empty places for the months
  still to come this year. The month and its score ("10–15") are both readable under each cup.
- Remove the profile's Statistics card and `battlesWon`/`battlesLost` from the screen ("Battles 0
  won · 0 lost" goes with it). Keep reading the stored values so nothing breaks.

*Done when* `ScoreboardTest` covers the new numbers and three screenshots match the targets.

#### Step 21. The month ends with a moment

*Ideas: scores-monthend.* Target: `scores-trophy`. Prototype: `TrophyMoment`, `Confetti`.

The first time the app is opened after a month with games against the partner has ended, show
the cup with the winner's face, "Maya takes October" ("You take October", "October is a tie"),
the score, one line about the month, and a button "Onto the shelf". Remember the last month shown
in `ProfileStore`. No confetti when motion is reduced. Add "Show last month's cup" to the
preview's test tools.

#### Step 22. A record gets a line

*Ideas: result-record.* Target: `result-new` (the gold line). Prototype: `RecordBadge`.

When a result beats an earlier best of the player's own (solo) or against this partner (quickest
win, biggest win), the result card shows one gold line: "Your quickest win at Connect Four: 11
discs". A first result in a game is not a record. No line otherwise.

### Phase H: profile and looks

#### Step 23. The profile

*Ideas: profile-order, profile-volume.* Target: `profile-new`. Prototype: `ProfileNew`.

- Order: You (name, picture), Look (light or dark, the colour theme of step 24), Sound and touch,
  then "Playing online" as one section at the end: the link's state on one line with "Change",
  and the saved partner. The preview's card stays last.
- **Loudness:** Off, Quiet, Normal, Loud, in place of the on/off switch. Normal is today's level
  (migrate "on" to Normal and "off" to Off, with a test); Quiet is about 0.45 of it; Loud raises
  each sound's place in the mix by 1.4, capped at full.
- **Their moves:** a switch. Off, the sounds of other players' events do not play (in
  `ui/Sfx.kt` these are the calls made at the lower "others" volume).

#### Step 24. Colour themes

*Ideas: looks-themes.* Target: `theme-mint`, `theme-peach`, `theme-sky`. Prototype: `Themes` and
`Swatch` in `ProtoScores.kt`.

Four colourways of the same Toy Box, each with a dark version: Lavender (today's), Mint, Peach,
Sky. The three new light palettes are in the prototype; derive each dark one from `DarkPalette`
with the same shift. Ink, cards and the games' own colours stay, so boards look the same. Each
phone picks its own in the profile's Look section (four swatches). A unit test checks, for all
eight palettes, 4.5 to 1 for text and muted text on the background and on cards, and for the
text on the primary colour.

#### Step 25. Card backs, frames and tiles

*Ideas: looks-table.* Target: `looks-new`. Prototype: `LooksSheet`, `CardBack`, `FrameSwatch`,
`TileSwatch` in `ProtoBits.kt`.

A "Looks" row in the menu (`GameMenu.kt`) of Memory Pairs, Colour Clash, Connect Four and Dominoes
opens a sheet: four card backs (Memory Pairs and Colour Clash share them), four frames for
Connect Four (`ConnectFourBoard` in `TurnGameScreens.kt`), three kinds of tile for Dominoes
(`drawDomino` in `DominoesScreens.kt`). The choice is kept on the phone and changes nothing for
the other player.

### Phase I: reactions

#### Step 26. The whole tray at once

*Ideas: react-grid.* Target: `react-new`. Prototype: `ReactTrayNew`. Work in `ui/screens/Reactions.kt`.

Two rows of eight in place of one row that scrolls sideways, the ones used last first (keep the
order in `ProfileStore`). The pencil (lines, step 27) is first; the "+" (packs, step 28) is last.
What a reaction that takes the game over shows today ("Kylian is watching") stays.

#### Step 27. Lines of your own

*Ideas: react-lines. Servers: all three (cap `lines`).* Target: `react-lines`. Prototype:
`LinesSheet`, `LineChip`, `Bubble`.

- Six short lines the player writes (24 characters each at most, kept on the phone, with six to
  start from: "Nice one", "Hurry up", "Lucky", "Good game", "One more?", "Your turn"). The pencil
  opens a sheet to edit them. A tap sends one.
- On the other phones it shows as a speech bubble by the sender's chip for as long as a reaction
  does, with the sound `line_sent`.
- Servers: a new optional text with the reaction call. Trim it, strip control characters, cap it
  at 24, refuse it empty; the same pace limit as reactions. Today's app ignores it.

#### Step 28. Stickers without an app update

*Ideas: react-packs. Servers: all three (cap `packs`).* Model: Ship Battle's skins
(`data/SkinStore.kt`, `ui/screens/SkinStoreSheet.kt`, `skins/README.md`, `SKINS_BASE_URL`).

- A `stickers/` folder at the repository's root: `index.json`, and for each sticker `<id>.json`
  (its shapes, in the form `ui/art/StickerArt.kt` holds; teach `scripts/make-stickers.py` to write
  it) and, if it has one, `<id>.ogg`. Add `stickers/README.md` saying how to add one.
- `STICKERS_BASE_URL` in `app/build.gradle.kts`, built as `SKINS_BASE_URL` is in both build types
  (the default branch for the real app, the work branch for the preview).
- The "+" in the tray opens the packs; a downloaded sticker joins the tray.
- A pack sticker is sent as `p:<id>` (`id`: lower-case letters, digits and hyphens, 32 at most).
  Servers accept any key of that shape. A phone that receives one it does not have fetches it,
  keeps it and shows it; if that takes more than two seconds, it shows the sticker's name in a
  bubble. Today's app ignores it.
- Put one simple sticker of your own in the first pack to prove the path. The owner adds real ones.

### Phase J: photos

#### Step 29. Your photo as your face

*Ideas: profile-photo. Servers: Cloudflare and Nearby (cap `faces`).* Target: `profile-new` ("Use a
photo").

- "Use a photo" in the profile: Android's photo picker, a crop to a circle, then a 96 × 96 JPEG
  kept on the phone. The animals stay, and the chosen animal is still sent as today, so phones
  and servers without the feature show it.
- A new call sets the player's photo in the room (a cap of about 12,000 base64 characters); each
  player in the room state carries a photo id; phones fetch a photo once per id. Follow Slide
  Race's photo (section 5.5).
- `PlayerAvatar` shows the photo where it has one: chips, lobby, result, room bar. Keep the
  partner's last photo with the saved partner so the scoreboard and the cups can show it.
- On an Apps Script link the photo shows on the player's own phone only; say so under the button.

#### Step 30. Memory Pairs with your photos

*Ideas: looks-photos. Servers: Cloudflare and Nearby (cap and token `memory-photos`).* Target:
`memory-photos`. Prototype: `LobbyMemoryPhotos`, `PhotoThumb`.

- In the Memory Pairs lobby, whoever opened the game can set Deck to "Your photos" and pick as
  many photos as the deck has pairs. Each is cropped square, made 160 × 160 JPEG and sent once.
  Phones fetch each once; a card shows a plain face until its photo has arrived.
- Offered only while the server has the cap and every player has the token.
- Look at how the Worker keeps a room (`backend/cloudflare/src/index.js`): photos must not sit
  inside the room object that is saved on every move. Store each on its own and delete them with
  the room. Cap each photo and their number.

### Phase K: sound and touch

#### Step 31. New sounds

*Ideas: sound-moments, sound-people, sound-score.* Table 9.4 lists the fifteen sounds and where
each plays. Their recipes are in `new_sounds.py`: move them into `scripts/make-sounds.py` in its
own style, generate the files, add them to `Sound` in `ui/Sfx.kt` with a place in the mix, and add
them to `docs/SFX.md`. Wire each to its moment with `SoundOnChange` where an animation starts.
Leave out `decider`, which the file marks as on hold (section 6).

#### Step 32. Buzzes with a feel

*Ideas: sound-buzz.* Add five to `Buzz` in `ui/Haptics.kt`, from table 9.5, with the same
fallback for phones without amplitude control as the others have, and wire them.

### Phase L: the hand-over

#### Step 33. The last pass

- Every line of `CHECKLIST.md` is ticked or carries a note saying why not.
- All checks of section 4 pass; the latest CI run on the work branch is green; the `preview`
  pre-release is from that run; the latest release is still `latest`, untouched.
- Look at every screenshot once more in light and dark, and at the home in all four themes.
- Update `README.md` (the games' new features, the preview app in "Development") and add a short
  section to `docs/UX-PLAN.md` saying this round is built.
- Report to the owner: the preview's link; what to try, in the order of the phases; what cannot
  be tried in the preview (invitations); that the partner needs the preview too for what two
  phones share; and that lines, sticker packs, photos and Sudoku's opener level show online only
  on a server that runs the new code, so before the code word they can be tried nearby, or
  online on a second server the owner deploys from the work branch. Then wait for the code word.

## 8. When the owner writes `lubudum`

Only that word, written by the owner in the chat, starts this. It is the owner's permission to
change the live branch.

1. `git fetch origin`. If the live branch has moved, merge it into the work branch, resolve, and
   run every check again.
2. Confirm the last CI run on the work branch is green.
3. Merge the work branch into the live branch and push it. If this session cannot push there
   directly, open a pull request from the work branch and merge it; the code word asks for that.
   Do not squash: keep the steps.
4. Watch the "Build" run on the live branch until the job "Publish download" is green, and confirm
   the latest release is a new `latest` holding `PipPop.apk` and `version.json`.
5. Tell the owner: the real app will offer the update; an Apps Script server needs the new
   `backend/Code.gs` pasted in by hand (the README says how); a Cloudflare server connected to the
   repository redeploys itself, any other needs redeploying; the preview app can be uninstalled.
   Ask before deleting the `preview` pre-release or the work branch.

If anything fails after the push, say so at once with the run's output. Do not push a second
change to the live branch to "try something"; fix it on the work branch first.

## 9. Reference

### 9.1 Where each idea is built

| Idea (catalogue id) | Step | | Idea (catalogue id) | Step |
|---|---|---|---|---|
| home-art, home-connect, home-recent, home-surprise | 8, 9 | | result-under, result-faces, result-tally | 17 |
| home-putaway | 8, 9 | | result-next | 18 |
| home-fit, home-face, home-roombar | 10 | | result-record | 19, 22 |
| sheet-solo, sheet-facts | 11 | | scores-race, scores-alltime, scores-story, scores-solo, scores-cups | 19, 20 |
| rules-points | 12 | | scores-monthend | 21 |
| lobby-art, lobby-code, lobby-ready | 13 | | profile-order, profile-volume | 23 |
| lobby-rows | 14 | | profile-photo | 29 |
| lobby-sudoku | 15 | | looks-themes | 24 |
| game-tonight, game-turn | 16 | | looks-table | 25 |
| toy-dialog | 2 | | looks-photos | 30 |
| toy-sheet | 3 | | react-grid | 26 |
| toy-fields | 4 | | react-lines | 27 |
| toy-controls | 5 | | react-packs | 28 |
| toy-loader, toy-toast | 6 | | sound-moments, sound-people, sound-score | 31 |
| fix-rematch | 7 | | sound-buzz | 32 |
| fix-password | 4 | | fix-hint | 8, 9 |
| fix-primary | 11 | | fix-battles | 20 |
| night-decider | on hold | | | |

### 9.2 Three facts for each game

Players come from `GameKind.maxPlayers`. "Take turns" is every `turnBased` game and Ship Battle;
"Live" is Air Hockey, Draw & Guess and Bingo; the rest "Race". Check the last two columns against
each game's rules and correct them where they are wrong.

| Game | Players | About | How |
|---|---|---|---|
| Sudoku | 2–4 | 10 min | Race |
| Ship Battle | 2 | 10 min | Take turns |
| Connect Four | 2 | 5 min | Take turns |
| Dots & Boxes | 2–4 | 10 min | Take turns |
| Memory Pairs | 2–4 | 5 min | Take turns |
| Word Race | 2–4 | 5 min | Race |
| Draw & Guess | 2–4 | 10 min | Live |
| Tic-Tac-Toe | 2 | 5 min | Take turns |
| Dominoes | 2–4 | 15 min | Take turns |
| Number Cross | 2–4 | 5 min | Race |
| Air Hockey | 2 | 5 min | Live |
| Colour Clash | 2–4 | 10 min | Take turns |
| Ludo | 2–4 | 20 min | Take turns |
| Letter Grid | 2–4 | 3 min | Race |
| Balloon Words | 2–4 | 10 min | Take turns |
| Code Breaker | 2 | 10 min | Race |
| Slide Race | 2–4 | 5 min | Race |
| Bingo | 2–4 | 10 min | Live |
| Mini Golf | 2–4 | 15 min | Take turns |
| Corner Blocks | 2–4 | 15 min | Take turns |

### 9.3 What a solo best is

Every game but Draw & Guess has solo play. Keep one best per game and level (or size, or
course). Where a game's solo end screen already shows a number, that number is the best. This
table is the intent; adjust a row to what the game really counts.

| Game | Best is | Better is |
|---|---|---|
| Sudoku | time, per level | lower |
| Ship Battle | shots to sink the fleet | lower |
| Connect Four | discs in the quickest win, per level | lower |
| Dots & Boxes | boxes ahead at the end, per level | higher |
| Memory Pairs | flips to clear, per deck | lower |
| Word Race | tries, and words solved in a row | lower, higher |
| Tic-Tac-Toe | wins in a row | higher |
| Dominoes | points ahead at the end | higher |
| Number Cross | time, per level | lower |
| Air Hockey | goals ahead at the end, per level | higher |
| Colour Clash | wins in a row | higher |
| Ludo | wins in a row | higher |
| Letter Grid | points, and the longest word | higher |
| Balloon Words | points | higher |
| Code Breaker | guesses, per setting | lower |
| Slide Race | time, per size | lower |
| Bingo | balls drawn before the bingo | lower |
| Mini Golf | strokes, per course and level | lower |
| Corner Blocks | squares placed | higher |

### 9.4 The new sounds

| Sound | Plays when |
|---|---|
| `sudoku_unit_done` | a row, column or box is finished, with its gold sweep |
| `c4_four` | the line through four discs is drawn |
| `ttt_board_won` | a small board is won in Ultimate |
| `memory_miss` | a pair that does not match turns back |
| `slide_home` | a tile lands in its place |
| `bingo_one_away` | a card is first one away from a line |
| `domino_knock` | a player passes |
| `clash_last_card` | a player is down to one card |
| `player_join` | someone else joins the room |
| `player_leave` | someone else leaves the room |
| `ready` | someone else taps Ready or Rematch |
| `score_up` | tonight's score ticks up on the result |
| `record` | the record line shows |
| `trophy` | the month's cup shows |
| `line_sent` | a line arrives |

### 9.5 The new buzzes

Each is a list of (milliseconds, strength from 0 to 1); strength 0 is a pause.

| Buzz | Pattern | Use for |
|---|---|---|
| a hit | (60, 1) | a hit in Ship Battle, a capture in Ludo |
| a finished line | (14, 0.5) (60, 0) (14, 0.5) | a finished Sudoku unit, four in a row, a Bingo line, a small board won |
| the dice | (10, 0.35) (26, 0) (10, 0.35) (26, 0) (10, 0.35) (26, 0) (10, 0.35) (34, 0) (24, 0.8) | a roll in Ludo |
| one away | (28, 0.6) (80, 0) (40, 1) (200, 0) (28, 0.6) (80, 0) (40, 1) | one away in Bingo, one card left in Colour Clash |
| a record | (22, 0.45) (50, 0) (44, 1) | the record line |
