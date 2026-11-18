# Two new word games: plan

The owner asked for two new games. The look follows the catalogue (the owner's design canvas, "Two
New Word Games"): Word Ladder (a duel that changes one word into another) and Word Hunt (a race to
find words hidden in a big grid).

## 1. Rules

1. **Nothing reaches the live branch** (`ccr-d293cbfe-amy86v`) until the owner writes the code word
   (`lubudum`, as before). All work goes to `between-the-boards`; check `git branch --show-current`
   before every push. Every push there becomes the preview app.
2. **Servers are never deployed from here.** A new game on the Cloudflare server is additive: a new
   game type that older apps never ask for. The owner deploys it. Nearby and solo work without it.
3. **Stored data is migrated, never dropped.** The shelf and the Store keep working for phones that
   already have the app.
4. **No brand names**, no copied art, words or lists from other games. Word lists are made here from
   the app's own word lists (or free lists already in the repo) and checked by a test.
5. **The repo stays reusable by others**: no private data, keys or personal names in code or docs.

## 2. How to work

Same as the Game Store round: a builder (Sonnet) does one step, a checker checks it against this plan
(Opus for rules and server steps 2, 3, 7, 8; Sonnet for the rest), the manager ticks `CHECKLIST.md`
in the same commit (`Words step N: <title>`) and pushes. One step per commit. Look, words and
accessibility follow `docs/COPY.md` and the design tokens (light and dark, 48 dp targets, contrast
4.5:1, nothing by colour alone, Motion durations, reduced motion). A screenshot test, light and
dark, for every new screen. Each rule exists in the same form in every place it runs (Cloudflare,
the nearby room and solo) and shares test cases.

## 3. Decisions

The owner has not yet answered the catalogue's questions; these are the defaults the plan builds,
and any answer the owner gives later replaces them (each is one small change).

- **Word Ladder: who wins.** Whoever makes the move that spells the target word wins, whoever's word
  it was. Moving to a word one step from the target hands the win to the next player.
- **Word Ladder: who starts.** The game picks at random whose word is the start; the owner of the
  target word makes the first move.
- **Word Ladder: no move.** A player with no legal one-letter change (every real word reachable is
  already in the chain) loses.
- **Word Ladder: words.** Both words must be real, the same length (4, 5 or 6 letters by level) and
  joined by a path of real words; the game says "No way from COLD to that word yet. Pick another."
  if not. No word may appear twice in the chain. An optional turn timer (off, 30 s, 60 s); when it
  runs out, the turn is lost (the other player moves).
- **Word Hunt: grid.** A rectangle, no side under 10: Easy 10×10, Medium 12×12, Hard 12×16,
  Huge 16×20. Words run across or down only. Backwards words count from Medium up, with a switch in
  setup. Grids over 10 wide zoom with pinch, with a small map of the whole grid.
- **Word Hunt: race.** One shared grid. A finished drag (either direction) claims the word for that
  player, marked in their colour and with their initial. Score = word length. One free hint per
  player per round (it costs 2 points). Timer 3, 5 or 8 minutes, or none. The round ends when every
  word is found or time runs out; highest score wins. 2 to 4 players.
- **Word Hunt: themes.** New themed word lists made for this game (for example Animals, Food,
  Sport, Nature, Home, Mixed).
- **Solo in both.** Word Ladder against a computer (Easy, Medium, Hard). Word Hunt alone against
  the clock, or against computer finders.
- **Where they go.** Both new games start in the Game Store (not on the default shelf), marked
  "New". Phones that already have the app see them in the Store; nothing else on the shelf moves.

## 4. Steps

### Step 1. Word lists
The ladder dictionary: common real words of 4, 5 and 6 letters, built from the app's lists, with
the one-letter-change neighbours of each word worked out once. Themed lists for Word Hunt (at least
6 themes, words 3 to 10 letters). Both live in one place the app and the Cloudflare server share or
generate from. *Done when* tests show every list word is real, has the right length, nothing
offensive is in it (checked against a blocklist), and enough 4/5/6 words have a neighbour.

### Step 2. Word Ladder rules
The rules in Kotlin (nearby and solo) and in the Cloudflare server: secret picks, the random start,
the path check, one-letter moves, no repeats, the win, no move = loss, the turn timer. *Done when*
the same test cases pass in both places: a legal move, each kind of illegal move, a win, a win by
the player whose word was the start, no move left, the timer.

### Step 3. Word Ladder online and nearby
The game in rooms: picks stay secret until both are in (never sent to the other phone early), the
coin flip, turns, rematch, leave. *Done when* tests in the room code and the nearby room show a full
game, and that a pick is not visible to the other player before the flip.

### Step 4. Word Ladder screens
Setup sheet, secret word pick with a keyboard and a word check, the coin flip, the ladder (the
changed letter highlighted, the target pinned), a wrong move, the result with the whole ladder, the
rules and a tutorial. Card art for the shelf and Store, and result art. *Done when* screenshots of
each screen in light and dark match the catalogue, and a test plays a short game through the screen.

### Step 5. Word Ladder solo
Against the computer: Easy picks any legal move, Medium avoids handing over the win, Hard plays the
shortest safe path. *Done when* tests show each level's choice in a fixed position and a solo game
ends with a result.

### Step 6. Word Hunt grid maker
Places a theme's words across and down (backwards when the switch is on) in the chosen size, fills
the rest with letters, and avoids putting any list word in twice. *Done when* tests over many seeds
show every word placed once, no side under 10, and the same seed makes the same grid.

### Step 7. Word Hunt rules
Claims (first finished drag wins the word), scoring, the hint, the timer, the end and the winner,
in Kotlin and the Cloudflare server. *Done when* the same test cases pass in both places, including
two players claiming the same word at nearly the same time (the first the server sees wins).

### Step 8. Word Hunt online and nearby
2 to 4 players: the grid is sent once, claims go back and forth, late joiners see the found words.
*Done when* tests in the room code and the nearby room show a full game with 3 players.

### Step 9. Word Hunt screens
Setup, the play screen (grid, word list with who found what, player chips, the drag in either
direction), the found-word moment, zoom with the small map on big grids, the result, the rules and a
tutorial. Card art and result art. *Done when* screenshots in light and dark match the catalogue,
and a test drags a word in each direction and claims it.

### Step 10. Word Hunt solo
Alone against the clock (a best time per size), or against computer finders that find words at a
set pace. *Done when* tests show a solo round ends and records a best.

### Step 11. In the app
Both games in the Game Store with "New", their pages (description, facts, how to play), the
scoreboard and records, partner pick, "Another game", the README. *Done when* tests show both games
in the Store and Get puts them on the shelf.

### Step 12. The last pass
All checks pass; the latest CI run on the work branch is green; the preview is from it; every new
screen looked at in light and dark; the owner gets the report and the preview link. The owner
deploys the Cloudflare server for online play.

### Step 13. Word Hunt: hidden words, one mixed vocabulary (owner's change)

From the owner's try of the preview: no word list on the screen (the words are hidden; finding
them is the game) and no theme. Every grid draws from one mixed vocabulary made of all the
categories, and more categories are added (at least 12, 40+ words each). The setup loses its
theme row. Each player's chip shows how many words they have found ("7"), and tapping a chip
opens that player's found words. A line shows how many are left ("9 of 24 found"). The view
never sends the text of an unfound word; the server and nearby accept and ignore an old app's
theme option. *Done when* tests show no unfound word text in any view, the grid draws from the
mixed pool, the chip count and the list match, and screenshots show the new play screen.

## 5. Not in this round

- Diagonal words in Word Hunt.
- Paid hints, ads or coins.
- Daily puzzles (a later idea).

## 6. To answer (from the catalogue)

1. Word Ladder: the move that spells the target wins, whoever's word it is? (default yes)
2. Word Ladder: who makes the first move? (default: the owner of the target word)
3. Word Ladder: no move left = lose or pass? (default lose)
4. Word Hunt: backwards words? (default from Medium up, with a switch)
5. Word Hunt: biggest grid 16 or 20 cells long? (default 16×20)
6. Word Hunt: new themed lists? (default yes)
7. Solo in both games? (default yes)
