# Between the Boards: checklist

The plan is `PLAN.md`. Tick a line in the same commit that finishes it: `[x]` done, `[-]` not built
(say why on the line). After a context reset, this file and `git log` say where you are.

Before every push, the three rules of `PLAN.md` section 1 hold:

- nothing goes to the live branch before the owner writes the code word (`git branch --show-current`
  is the work branch before every push);
- the preview's release stays a pre-release, and the publishing lines of the `preview` and
  `release` jobs are untouched;
- no server is deployed, and every server change keeps today's app working.

## Step 0: before you start

- [x] The live branch's name is known (`git remote show origin | grep 'HEAD branch'`): `ccr-d293cbfe-amy86v`
- [x] The work branch `between-the-boards` is created from `origin/claude/brave-faraday-w5scs4` and pushed
- [x] `./gradlew assemblePreview` works in this session (environment set up, SDK installed): session moved onto the "Game-Suite" environment, SDK installed with `setup-android-sdk.sh`
- [x] Baseline checks run on the untouched branch; result: all green. Design tokens OK; Android unit tests 260 run, 0 failed; Apps Script 17 pass; Cloudflare 150 pass
- [x] Looked at the targets and read `prototypes/ProtoKit.kt`

## Phase A: the preview app

- [x] **Step 1. The preview app is real**
  - [x] The "Build" run on the work branch is green, "Publish preview app" included (run 168)
  - [x] Release `preview` is a pre-release with `PipPopPreview.apk`, `PipPop.apk`, `version.json`
  - [x] The latest release is still `latest`, unchanged (1.0.163, commit e037637)
  - [x] "Preview app" card on the profile, shown only in the preview
  - [x] The owner has the download link and knows invitations are off in the preview

## Phase B: one toy box

- [x] **Step 2. Questions in the app's look** (toy-dialog)
  - [x] `ToyDialog` in `ui/screens/Toy.kt`; four `AlertDialog`s replaced
- [x] **Step 3. One sheet** (toy-sheet)
  - [x] `ToySheet`; all eight sheets use it
- [x] **Step 4. Fields and the code** (toy-fields, fix-password)
  - [x] `ToyField`, `CodeField`; all seven `OutlinedTextField`s replaced
  - [x] "Paste the message instead" is a button
  - [x] The password field's label is "Password", its explanation is under it
- [x] **Step 5. The app's own switch and choices** (toy-controls)
  - [x] `Choices`, `OptionRow`, `ToySwitch`; no `FilterChip` and no Material `Switch` left
- [x] **Step 6. Pip and Pop while you wait, and messages with a mark** (toy-loader, toy-toast)
  - [x] `PipPopLoader` in all five places; `Toast` in place of the snackbar
  - [x] `scripts/check-tokens.sh` fails on stock parts
- [x] **Step 7. A Rematch you can read** (fix-rematch)

## Phase C: the home

- [ ] **Step 8. What the home remembers**
  - [ ] Recent games, put-away games and "arranged once" are stored and tested
  - [ ] The arrange screen has its "Put away" line
- [ ] **Step 9. The home, before you connect**
  - [ ] Every game as its picture, three to a row (home-art)
  - [ ] One line to connect (home-connect)
  - [ ] Jump back in (home-recent)
  - [ ] The "Games" row: Scores, Surprise us, Arrange games; Surprise us works (home-surprise)
  - [ ] More games (home-putaway)
  - [ ] "Hold a game to move it" leaves after the first arrange (fix-hint)
  - [ ] No month strip on the home
- [ ] **Step 10. The home, connected**
  - [ ] A room bar that holds four people (home-roombar)
  - [ ] Seats on every tile; games that don't fit fade and say so (home-fit)
  - [ ] Their face on the game they are in (home-face)
  - [ ] No scores on tiles

## Phase D: a game's sheet, solo, rules

- [ ] **Step 11. A game's sheet**
  - [ ] Three facts (sheet-facts)
  - [ ] Solo starts from the sheet, with the last setup remembered, for every game with solo play (sheet-solo)
  - [ ] No level stage left in any solo screen; `LevelDialog` gone; no screen with several main buttons (fix-primary)
  - [ ] No "your best" line and no "you two" strip on the sheet
- [ ] **Step 12. Rules you can find your place in** (rules-points)
  - [ ] All twenty games' rules are four to six titled points, within the limits, tested
  - [ ] `RulesSheet` replaces `RulesDialog`; "Show me how" opens the walk-through
  - [ ] The stock-part exceptions of step 6 are removed

## Phase E: the lobby

- [ ] **Step 13. The lobby's frame**
  - [ ] The game's whole picture (lobby-art)
  - [ ] The code steps aside once someone has joined (lobby-code)
  - [ ] Ready stays under your thumb, in every lobby (lobby-ready)
- [ ] **Step 14. The setup as rows of choices** (lobby-rows)
  - [ ] All eleven option cards and the "Change" sheet use `OptionRow`
- [ ] **Step 15. Sudoku: whoever opens it picks the level** (lobby-sudoku)
  - [ ] Apps Script, Cloudflare and Nearby: the new path and today's path both tested
  - [ ] Today's `LevelPicker` still shows in a room where it must

## Phase F: in the game and at its end

- [ ] **Step 16. In the game**
  - [ ] Tonight's score in the title row (game-tonight)
  - [ ] "Your turn" on one line; the how-to leaves from the third turn (game-turn)
- [ ] **Step 17. The result goes under the board**
  - [ ] The result is a card under the board wherever it fits (result-under)
  - [ ] The winner's face with a crown (result-faces)
  - [ ] Tonight's score ticks up (result-tally)
  - [ ] Games that kept the sheet, and why: …
- [ ] **Step 18. "Another game", and the loser picks** (result-next)

## Phase G: scores and records

- [ ] **Step 19. Remember more about each game**
  - [ ] Results keep time, score and count where a game has them; old lines still load
  - [ ] Solo records for every game with solo play; Sudoku's bests migrated
  - [ ] Test tools in the preview: fill with sample games, clear them
- [ ] **Step 20. The scoreboard**
  - [ ] The month as a race you can see (scores-race)
  - [ ] All time, beside this month (scores-alltime)
  - [ ] Each game's story (scores-story)
  - [ ] Solo records (scores-solo)
  - [ ] Cups worth winning, with readable labels (scores-cups)
  - [ ] The Statistics card and "Battles 0 won · 0 lost" are gone (fix-battles)
- [ ] **Step 21. The month ends with a moment** (scores-monthend)
  - [ ] "Show last month's cup" in the preview's test tools
- [ ] **Step 22. A record gets a line** (result-record)

## Phase H: profile and looks

- [ ] **Step 23. The profile**
  - [ ] What you change, first (profile-order)
  - [ ] Off, Quiet, Normal, Loud, and "Their moves"; the old setting migrated (profile-volume)
- [ ] **Step 24. Colour themes** (looks-themes)
  - [ ] Lavender, Mint, Peach, Sky, each light and dark; contrast tested for all eight
- [ ] **Step 25. Card backs, frames and tiles** (looks-table)

## Phase I: reactions

- [ ] **Step 26. The whole tray at once** (react-grid)
- [ ] **Step 27. Lines of your own** (react-lines)
  - [ ] Apps Script, Cloudflare and Nearby accept a line; today's app is unaffected; tested
- [ ] **Step 28. Stickers without an app update** (react-packs)
  - [ ] `stickers/` folder, its README, `scripts/make-stickers.py` writes pack stickers
  - [ ] The three servers accept pack keys; a phone without the sticker fetches it

## Phase J: photos

- [ ] **Step 29. Your photo as your face** (profile-photo)
  - [ ] Cloudflare and Nearby; the animal is still sent; Apps Script unaffected
- [ ] **Step 30. Memory Pairs with your photos** (looks-photos)
  - [ ] Offered only when the server and every player can; photos kept out of the room object

## Phase K: sound and touch

- [ ] **Step 31. New sounds** (sound-moments, sound-people, sound-score)
  - [ ] Fifteen sounds made, in `Sound`, in `docs/SFX.md`, and wired (not `decider`: on hold)
- [ ] **Step 32. Buzzes with a feel** (sound-buzz)

## Phase L: the hand-over

- [ ] **Step 33. The last pass**
  - [ ] Every line above is ticked or explained
  - [ ] All checks pass locally; the last CI run on the work branch is green
  - [ ] The `preview` pre-release is from that run; the latest release is still `latest`
  - [ ] Every screenshot looked at in light and dark; the home in all four themes
  - [ ] `README.md` and `docs/UX-PLAN.md` updated
  - [ ] The owner has the report and has been asked for the code word

## Not built

- [-] The month's score on the home: left out by the owner
- [-] This month's score on each tile: left out by the owner
- [-] "Your best" on a game's sheet: left out by the owner
- [-] "How you two stand in this game": left out by the owner
- [-] "First to three": left out by the owner
- [-] "The night's winner": left out by the owner
- [ ] The decider: on hold until the owner says what it means without a target

## After the owner writes `lubudum`

- [ ] The live branch merged into the work branch if it had moved; all checks green again
- [ ] The work branch merged into the live branch and pushed (steps kept, not squashed)
- [ ] The "Build" run on the live branch is green; `latest` is a new release with `PipPop.apk` and `version.json`
- [ ] The owner knows: the real app will offer the update; Apps Script needs `backend/Code.gs` pasted in; the preview can be uninstalled
