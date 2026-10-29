# Sound effects catalogue

Every sound Pip & Pop needs, where it plays and what it should sound like.

**Now in the app:** the 55 priority-1 sounds, made from scratch (tones and noise, no
recordings) by `scripts/make-sounds.py`; run it again after changing a sound. They are
played by `ui/Sfx.kt`, which sets each sound's level. Profile → Sound and touch turns them
on or off; the phone's silent mode doesn't (the media volume sets how loud). Better recordings can replace any file later: keep
the same file name.

## Style

- **Felt:** round boops, soft thumps and muffled taps, like toys wrapped in felt. Almost
  nothing above 1.5 kHz: the 2–5 kHz band is where people find sounds harshest (Kumar &
  Griffiths 2012), and the set keeps under 1% of its energy there (the old Toy Box set had
  17%). Nothing that would annoy a partner across the room.
- **Short:** most sounds under 300 ms; only wins, losses and a few big moments run longer.
- **Quiet by default:** players hear these hundreds of times. The loudest sounds (win
  fanfare, explosion, goal horn) sit about 6 dB under full scale; taps and ticks about 18 dB
  under.
- **Varied:** sounds heard often (taps, card slides, dice, letters) get 2–3 takes or a random
  ±5% pitch so they don't feel mechanical.
- **Only on this phone's own events?** No: other players' moves play too (a little quieter,
  about −4 dB), so you hear the game happening even when you're not looking.

## Files

- Format: OGG Vorbis, 44.1 kHz, mono, peak −1 dBFS, trimmed with no silence at the start.
- Location: `app/src/main/res/raw/sfx_<id>.ogg`. Variants add `_2`, `_3`
  (`sfx_card_slide_2.ogg`).
- Played through one small `SoundPool` wrapper (see [Wiring](#wiring)), loaded when the app
  starts; loops (`loop` in the tables) use `SoundPool`'s loop flag.
- Licence: the repository is public, so only **CC0** (or self-made) audio. Good CC0 sources:
  [Kenney audio packs](https://kenney.nl/assets?q=audio) (Interface Sounds, Casino Audio,
  Impact Sounds, Digital Audio, UI Audio), CC0-filtered [Freesound](https://freesound.org),
  and [jsfxr](https://sfxr.me) for blips and chimes made from scratch. Record each
  replaced file's source in this document (Android doesn't allow other files in `res/raw`).

Columns: **ID** (the file name without `sfx_`), **When** (the event that plays it), **Sound**
(what to look for or make), **ms** (rough length), **Pri** (1 ship first, 2 next, 3 nice to
have).

## Shared: app and every game

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `tap` | Any button or chip | Soft plastic click | 40 | 1 |
| `tap_back` | Back, close, cancel | Lower, softer click | 40 | 2 |
| `toggle` | Switches, level and mode chips | Two-step click (on/off pitch differs) | 80 | 2 |
| `sheet_open` | Game sheet, rules, popups slide up | Short airy whoosh up | 200 | 2 |
| `sheet_close` | …and down | Whoosh down | 180 | 3 |
| `error` | A move the rules refuse, a word not in the list | Gentle double "bonk", low | 180 | 1 |
| `your_turn` | Turn passes to you (turn games) | Bright two-note chime | 300 | 1 |
| `countdown_tick` | Each second of a race countdown | Wooden tick | 60 | 1 |
| `countdown_go` | The race starts | Higher tick + spark | 250 | 1 |
| `timer_warning` | Last 10 s of a timed round (Letter Grid, Draw & Guess) | Soft clock tick, speeds up | 80 | 2 |
| `reaction` | A reaction pops up | Bubble pop | 120 | 2 |
| `player_join` | Someone joins the room | Rising "boop-bip" | 250 | 2 |
| `player_leave` | Someone leaves or drops out | Falling "bip-boop" | 250 | 2 |
| `ready` | You or a partner taps Ready / Rematch | Light confirm click | 120 | 2 |
| `win` | Result popup, you won | Short fanfare: toy horn + sparkle, about 1.5 s | 1500 | 1 |
| `lose` | Result popup, you lost | Friendly descending "wah-wah" on a kazoo or toy piano | 1200 | 1 |
| `tie` | Result popup, a draw | Two even notes, neutral | 700 | 1 |
| `score_up` | Points added (boxes, pairs, golf strokes counted) | Coin-like blip | 150 | 2 |

## Shared by game families

These keep cards sounding like cards everywhere, and so on.

| ID | Used by | When | Sound | ms | Pri |
|---|---|---|---|---|---|
| `card_deal` | Colour Clash, Dominoes (tiles dealt), Memory | Each card or tile dealt in (staggered) | Quick card flick | 90 | 1 |
| `card_slide` | Colour Clash | A card flies to the pile | Card swish onto felt | 180 | 1 |
| `card_flip` | Colour Clash, Memory, Code Breaker reveal | A card turns over | Crisp paper flip | 120 | 1 |
| `card_draw` | Colour Clash | Drawing from the deck | Slide off a stack | 150 | 2 |
| `dice_roll` | Ludo | The die tumbles | Plastic die rattling in a cup, about 0.5 s | 560 | 1 |
| `dice_land` | Ludo | The die settles | Single clack | 90 | 1 |
| `piece_hop` | Ludo (each square), Slide Race (each tile) | A token hops or a tile slides one square | Tiny wooden tap, rising pitch per step | 60 | 1 |
| `piece_place` | Dominoes, Corner Blocks, Number Cross | A tile or piece set down | Wooden clack on a table | 110 | 1 |
| `letter_type` | Word Race, Balloon Words, Letter Grid keyboard | Typing a letter | Soft key tick (3 takes) | 50 | 1 |
| `letter_drop` | Word Race, Balloon Words | A letter drops into its tile | Light "tok" | 90 | 1 |
| `letter_right` | Word Race (green), Balloon Words (a hit) | A letter is in the right place / in the word | Bright "ting" | 150 | 1 |
| `letter_close` | Word Race (yellow) | Right letter, wrong place | Softer, lower "ting" | 150 | 2 |
| `letter_wrong` | Word Race (grey) | Letter not in the word | Muted thud | 100 | 2 |
| `mark` | Tic-Tac-Toe, Dots & Boxes | A mark or line drawn | Marker/chalk stroke | 150 | 1 |

## Per game

### Sudoku

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `sudoku_select` | Selecting a cell (with the highlight wave) | Soft "whum" swelling out | 300 | 3 |
| `sudoku_digit` | Placing a digit | Pencil tap | 70 | 1 |
| `sudoku_note` | Toggling a pencil mark | Lighter pencil tap | 50 | 2 |
| `sudoku_unit_done` | A row, column or box completed correctly | Three rising notes | 400 | 2 |
| Uses | `error` (wrong digit), `countdown_*`, `win` / `lose` | | | |

### Ship Battle

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `ships_sea` | Battle screen, quiet background | Gentle waves, seamless loop, very low | loop | 3 |
| `ships_fire` | You or the enemy fires | Toy cannon "pomf" + whistle | 400 | 1 |
| `ships_hit` | Shot hits (with the fireball) | Boomy explosion, rounded, no crackle | 700 | 1 |
| `ships_miss` | Shot misses (with the splash) | Water splash "ploosh" | 500 | 1 |
| `ships_sunk` | A ship sinks | Creak + bubbling down | 1000 | 1 |
| `ships_place` | Moving or turning a ship in the fleet editor | Wooden thunk | 90 | 2 |
| `ships_place_bad` | Ship dropped where it can't go (snaps back) | `error` | | |

### Connect Four

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `c4_drop` | Disc falls (pitch higher for higher rows) | Plastic disc rattling down a slot | 300 | 1 |
| `c4_land` | Disc lands (with the bounce) | Plastic clack | 80 | 1 |
| `c4_four` | Four in a row glows | Four quick rising notes | 450 | 2 |

### Dots & Boxes

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| Uses `mark` | Drawing a line | | | |
| `dots_box` | Closing a box (go again) | Stamp + small chime | 250 | 1 |

### Memory Pairs

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| Uses `card_flip` | Turning a card | | | |
| `memory_match` | A pair found | Two-note "ding-ding" | 350 | 1 |
| `memory_miss` | A pair turns back face down | Soft double flip, lower | 250 | 2 |

### Word Race

Uses `letter_type`, `letter_drop`, `letter_right`, `letter_close`, `letter_wrong`, `error`
(word not in the list) and `countdown_*`.

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `words_solved` | All green | Five quick rising tings | 600 | 1 |
| `words_partner_row` | A partner scores a row | Soft paper rustle | 150 | 3 |

### Draw & Guess

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `draw_stroke` | While drawing (loop, follows the pen) | Felt-tip squeak, very soft | loop | 2 |
| `draw_clear` | Clear the drawing | Paper crumple | 300 | 2 |
| `draw_correct` | Someone guesses the word | Bell "ding!" + applause blip | 700 | 1 |
| `draw_guess` | A wrong guess arrives | Paper slip | 120 | 3 |
| Uses | `timer_warning`, `your_turn` (your turn to draw) | | | |

### Tic-Tac-Toe (normal and Ultimate)

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `ttt_x` | An X | Two chalk strokes | 160 | 1 |
| `ttt_o` | An O | One round chalk stroke | 160 | 1 |
| `ttt_board_won` | Ultimate: a small board won | Stamp | 200 | 2 |

### Dominoes

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| Uses `card_deal` (tiles dealt), `piece_place` (tile set on the line) | | | | |
| `domino_draw` | Drawing from the boneyard | Tiles shuffled on the table | 300 | 2 |
| `domino_pass` | Pass | Knuckle knock on the table | 150 | 2 |
| `domino_round` | Round won, points counted | Tiles swept together | 600 | 2 |

### Number Cross

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| Uses `piece_place` (a number placed), `error` | | | | |
| `numbers_sum_right` | A sum filled and correct (turns green) | Soft bell | 250 | 1 |
| `numbers_sum_wrong` | A sum filled and wrong (turns red) | Muted buzz | 200 | 2 |
| `numbers_stone` | Tapping a stone block (nothing happens) | Dull stone tap | 60 | 3 |

### Air Hockey

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `hockey_hit` | Paddle hits the puck (louder for faster) | Plastic "tock" | 70 | 1 |
| `hockey_wall` | Puck bounces off a wall | Higher, thinner tock | 60 | 1 |
| `hockey_goal` | A goal | Short buzzer/horn + crowd blip | 900 | 1 |
| `hockey_air` | Table running (loop, very low) | Air-table hiss | loop | 3 |

### Colour Clash

Uses `card_deal`, `card_slide`, `card_flip`, `card_draw`.

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `clash_skip` | Skip played | Quick "zip" | 200 | 2 |
| `clash_reverse` | Reverse played | Whoosh that turns around | 300 | 2 |
| `clash_draw2` | +2 / +4 hits someone | Two / four quick card slaps | 400 | 2 |
| `clash_wild` | Wild colour chosen | Colour "shimmer" | 350 | 2 |
| `clash_last_card` | A player is down to one card | Bright alert ping | 250 | 2 |

### Ludo

Uses `dice_roll`, `dice_land`, `piece_hop` (each square).

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `ludo_out` | A 6 brings a token out | Pop + step | 200 | 1 |
| `ludo_capture` | A token is knocked back | Bonk + slide whistle down | 600 | 1 |
| `ludo_home` | A token reaches home | Little fanfare blip | 400 | 1 |
| `ludo_safe` | Landing on a star | Soft sparkle | 200 | 3 |

### Letter Grid

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `grid_trace` | Each letter added while tracing (pitch rises along the word) | Glassy tick | 60 | 1 |
| `grid_word` | A word accepted (longer word, richer chime) | Chime | 300 | 1 |
| `grid_repeat` | Already found | Soft "nope" | 150 | 2 |
| Uses | `error` (not a word), `timer_warning`, `countdown_*` | | | |

### Balloon Words

Uses `letter_type`, `letter_drop`, `letter_right`.

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `balloon_stretch` | A balloon swells and trembles before popping | Rubber squeak, rising | 330 | 1 |
| `balloon_pop` | The pop (with the flash) | Balloon pop, satisfying, not too sharp | 250 | 1 |
| `balloon_scraps` | Scraps land, string drops | Soft rubber patter | 400 | 3 |
| `balloon_solved` | Word completed | Party blower "toot" | 600 | 2 |
| `balloon_all_popped` | Last balloon pops | Deflating raspberry | 800 | 2 |

### Code Breaker

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `code_peg` | Putting a peg in a hole | Plastic peg click | 70 | 1 |
| `code_peg_out` | Taking a peg out, Clear | Reverse click | 70 | 2 |
| `code_pin_red` | Each red pin revealed (one after another) | Bright click | 80 | 1 |
| `code_pin_white` | Each white pin revealed | Softer click | 80 | 1 |
| `code_cracked` | Four red | Lock springing open | 600 | 1 |
| `code_set` | Secret code locked in | Lock closing | 300 | 2 |

### Slide Race

Uses `piece_hop` for each tile slid.

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `slide_home` | A tile lands in its right place (turns orange) | Small click-ting | 120 | 2 |
| `slide_solved` | Puzzle solved | Tiles cascading "tik-tik-tik" + chime | 700 | 1 |

### Bingo

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `bingo_ball` | A new number rolls in | Ball rolling out of a cage + stop | 600 | 1 |
| `bingo_dab` | Dabbing a square | Wet marker "dab" | 90 | 1 |
| `bingo_undab` | Taking a dab off | Lighter dab | 70 | 3 |
| `bingo_line_ready` | The Bingo button lights up | Rising shimmer | 400 | 1 |
| `bingo_shout` | Bingo! | Short cheer / "Bingo!" vocal blip | 900 | 1 |

### Mini Golf

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `golf_aim` | Pulling back (loop, pitch rises with power) | Elastic stretch | loop | 2 |
| `golf_putt` | Shot (soft putter tap, louder with power; 2 takes) | Putter "tink" | 120 | 1 |
| `golf_wall` | Ball bounces off a wall or block | Rubber bumper "bonk" | 80 | 1 |
| `golf_sand` | Ball runs into sand | Sandy "shff" | 300 | 2 |
| `golf_roll` | Ball rolling (loop, fades as it slows) | Soft grass roll | loop | 3 |
| `golf_cup` | Ball drops in | Rattle in the cup + ting | 500 | 1 |
| `golf_lip_out` | Ball too fast, rolls over the cup | Quick rattle | 200 | 2 |
| `golf_splash` | Ball slows on a pond and sinks (back to the tee) | Plop, wash, two bubbles | 420 | 1 |
| `golf_drop` | Ball rolls off an island into the drop (back to the tee) | Falling whistle | 520 | 1 |
| `golf_hole_in_one` | In with the first stroke | Small fanfare | 1200 | 2 |
| `golf_flag` | New hole appears | Flag flutter | 300 | 3 |

### Corner Blocks

Uses `piece_place` (a placed piece lands).

| ID | When | Sound | ms | Pri |
|---|---|---|---|---|
| `blocks_pick` | Picking a piece from the tray | Plastic pickup | 80 | 2 |
| `blocks_turn` | Turn or Flip | Quick plastic rotate | 100 | 2 |
| `blocks_fit` | The preview turns green (fits) | Very soft tick | 50 | 3 |
| `blocks_skip` | A player has no move and is skipped | `player_leave`-style descending blip | 250 | 2 |

## Count

111 sounds: 18 for the app and every game, 14 shared by game families, and 79 for single
games. The 55 at priority 1 give every game its core sounds.

## Wiring

- **`Sfx` object** (`ui/sound/Sfx.kt`): loads all `sfx_*` files into one `SoundPool` (up to
  8 streams) at start; `Sfx.play(id, volume = 1f, pitch = 1f)` picks a random variant and
  applies ±5% pitch on the families marked as varied; `Sfx.loop(id)` / `Sfx.stop(id)` for
  loops. Other players' events pass `volume = 0.6f`.
- **Settings:** Sound on/off and a volume slider in the profile, saved with the profile; also
  Vibrate on/off (haptics paired with `error`, `ships_hit`, `golf_putt`, `hockey_hit`,
  `dice_land`, `bingo_shout`). Sound follows the app's own switch only, not the phone's silent mode.
- **Where to call it:** where the animations already start, so sound and movement line up:
  `Motion` helpers (`enterFrom`, `dropIn`, `shake`, `FlipCard`) take an optional sound id;
  game screens' `LaunchedEffect`s that start a blast, a pop, a hop or a roll play the matching
  sound at the same moment; view models play `error` when a move is refused and `your_turn`
  when the turn comes back.
- **Mix check:** play a full round of each game with sound on and adjust the levels in
  `Sfx` (one volume per ID), not in the files.
