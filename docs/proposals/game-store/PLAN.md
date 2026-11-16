# The Game Store round: plan

The owner's requests after "Between the Boards", in one round. Mock-ups (the owner's design canvas):
Home with a fixed top bar, long press to edit, the Game Store, a game's page, "just added", a
partner's pick you don't have, and the profile with a photo.

## 1. Rules

1. **Nothing reaches the live branch** (`ccr-d293cbfe-amy86v`) until the owner writes the code word
   from the Between the Boards plan (section 8 there). All work goes to `between-the-boards`; check
   `git branch --show-current` before every push. Every push there becomes the preview app.
2. **Servers are never deployed from here.** Any server change keeps today's app working (additive
   only), the same rule in Apps Script, Cloudflare and Nearby, with tests in each.
3. **Stored data is migrated, never dropped.** A renamed or replaced setting is read once from the
   old key and the old key is left in place.
4. **No background self-update.** The "Update by itself" work is taken out (step 1). Play Protect
   interrupts any install that does not come from the Play Store, so the app keeps today's update
   offer.

## 2. How to work

Same as Between the Boards: a builder does one step, a checker checks it against this plan, the
manager ticks `CHECKLIST.md` in the same commit (`Store step N: <title>`) and pushes. One step per
commit. Look, words and accessibility follow `docs/COPY.md` and the Between the Boards plan's
sections 5.1–5.2 (tokens only, light and dark, 48 dp targets, contrast 4.5:1, nothing by colour
alone, Motion durations, reduced motion). A screenshot test, light and dark, for every screen that
changes.

## 3. Decisions

- **Every game stays inside the app.** Android does not let an app installed from GitHub add game
  code later, so "Get" in the Store puts a game on your shelf at once (no download, no wait).
  Removing a game puts it back in the Store; its scores, records and saved games are kept.
- **The shelf a phone starts with:** Sudoku, Ship Battle, Memory Pairs, Word Race, Tic-Tac-Toe,
  Dominoes, Colour Clash, Ludo, Mini Golf. **In the Store:** Connect Four, Dots & Boxes, Draw &
  Guess, Number Cross, Air Hockey, Letter Grid, Balloon Words, Code Breaker, Slide Race, Bingo,
  Corner Blocks.
- **Phones that already have the app** also start from the nine on their first open of this
  version, whatever they played before; the rest go to the Store with scores kept. A one-time note
  says "The other games are in the Game Store". (Confirmed by the owner.)
- **The Arrange screen and its button go.** Holding a card starts editing in place.
- **Mini Golf, an unfinished hole:** a player who has not holed out when the hole ends scores the
  strokes they took plus 5. (Confirmed by the owner.)
- **Order:** this round is built on the same work branch; the code word later ships both rounds.
  (Confirmed by the owner.)

## 4. Steps

### Step 1. Take out "Update by itself"

Revert the two "Update by itself" commits on the work branch (`7dea362`, `92f4333`); keep today's
update offer and button exactly as before them. Delete the local, never-pushed `quiet-leave-wip`
branch. *Done when* the app has no WorkManager, no `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, the update
offer works as in 1.0.202, and all checks pass.

### Step 2. The shelf and the Store, as data

`ProfileStore` keeps which games are on the shelf and in what order (new key; the old `game_order`
and `put_away` keys are read once to build it, then left alone). Default: the nine. Adding puts a
game at the end; removing takes it off and keeps its scores. One function says what the Store
holds (every game not on the shelf). *Done when* unit tests cover a new phone, an existing phone
(the migration), add, remove, reorder and running the migration twice.

### Step 3. The fixed top bar

On the home, a top bar that stays put while the page scrolls: "Hi, <name>" small over "Pip & Pop"
(the preview keeps its own name), and on the right the avatar circle showing the player's photo if
they chose one, else their animal. Tapping it opens the profile and settings. *Done when* the
screenshot matches the mock-up in light and dark, with and without a photo, and the bar stays put
in a scrolled screenshot.

### Step 4. Hold a card to edit the shelf

Remove the Arrange screen, its button and its hint. Holding any game card starts editing in place:
the cards wiggle (not under reduced motion), each shows a round × at its top-right corner
("Remove <game>"), a card can be dragged to a new place, and a "Drag to move. Tap × to remove." bar
with "Done" sits at the top. Removing shows "<Game> is back in the Store. Undo". Drag works with
TalkBack too (move up / move down actions). With an empty shelf the home says "Your shelf is empty.
Get games from the Game Store." and Surprise us says the same, not "No game fits everyone here".
*Done when* a test drags a card to a new place and the
order is saved, × and Undo work, and holding never opens another screen.

### Step 5. The Game Store

A "Game Store · N more games" card at the end of the shelf opens the Store: a back arrow, a
Choices row (All, Puzzles, Words, Party, Quick) that really filters, each Store game with its art,
one line, players and minutes, and "Get"; below, "On your shelf" with "Open". A game's page: banner,
description, facts, how to play, "Add to my games" (then "Play" and "Remove from my games", and the
toast "<Game> is on your shelf"). "Maya has this game" where the saved partner is known to have it
(skip the line otherwise). The first open after the update shows a one-time note on the home:
"The other games are in the Game Store", with a button to it; a phone that never had the app
before does not see it. "Another game" and the switch-game sheet get a "Game Store" entry at the
end. *Done when* screenshots match the mock-ups in light and dark, and tests
cover Get, Remove, the filter and the empty Store ("Every game is on your shelf").

### Step 6. A partner picks a game you don't have

When an invite, the lobby, "Another game" or a switch-game asks you to play a game that is not on
your shelf, show the sheet from the mock-up: their face, "<Name> wants to play <Game>", "It's in
the Game Store. Add it to join.", "Add and join" / "Not now". The game list in "Another game"
offers only games on your shelf, plus "All games". *Done when* a test covers each way in.

### Step 7. Profile

The chosen photo shows in the avatar everywhere the player's own face shows (top bar, chips,
lobby, result, scoreboard), not only in the profile. Ship Battle styles leave the profile's Look
section; they live only in the game's Skins button. *Done when* screenshots show the photo in the
top bar and a chip, and the Look section without Ship Battle styles.

### Step 8. Colour Clash: the dictator card just gives +8

When the dictator card is played, the next player picks up eight cards and play goes on: no
take-over, no animation, nothing blocks the controls. Check all three servers and the phone's own
rules for any lock tied to the card; keep the card's rules otherwise as they are. *Done when* tests
in each server and on the phone show the +8 and that the next move is accepted at once.

### Step 9. Mini Golf: the Kylian statue blocks only the player who landed there

Today, when a ball stops on the Kylian spot, the statue rises and every player has to wait for its
animation; even after the statue goes down, the other players still cannot play. Only the player
whose ball stopped there should lose their next turn: the others play on at once, during the
animation and after it, and the statue's animation never locks anyone's controls. Find the cause
in every place it can live (`golf.js`, `Golf.kt`, the nearby room, and the golf screen's input and
animation locks) and fix it. *Done when* a test in each place reproduces today's bug and then
passes: the next player can shoot while the statue is still up and after it has gone down.

### Step 10. Mini Golf: an unfinished hole costs 5

A player still out when a hole ends scores the strokes they took plus 5 for that hole, in every
server and solo, shown on the score strip and the result, and said in the rules. *Done when* tests
in each place cover holed out, not holed out, and everyone out.

### Step 11. The last pass

All checks pass; the latest CI run on the work branch is green; the preview is from it; every
changed screen looked at in light and dark; README updated (the Store, editing the shelf); the
owner gets the report and the preview link.

## 5. Not in this round

- Real per-game downloads (only possible through Google Play).
- Background self-update.
- New games.

## 6. Answered by the owner

Phones that already have the app start from the nine; the golf penalty is strokes taken plus 5;
this round is built on the same work branch.
