# Game Suite: UX audit

A blunt audit of the whole app through ten lenses: strategy, journey mapping, research,
design critique, interaction design, accessibility, UX writing, design systems, visual
elevation and design ops. Every finding has the same parts:

- **Problem**: what is wrong.
- **Evidence**: where it is in the code or the numbers.
- **Why it's bad**: the principle it breaks and what it costs the player.
- **Proposal**: what to build instead.
- **Why it's better**.
- **Breaks**: what the proposal throws away or rewrites.

Nothing here was ruled out because it is a lot of work or because it rewrites the app's
logic. The cost of each change is given so it can be planned, not so it can be dodged.

**Severity** (Nielsen): 4 = catastrophe, stops the task · 3 = major · 2 = minor · 1 = cosmetic.
**Cost**: S = under a day · M = days · L = a sprint · XL = several sprints.

**How it was audited.** By reading every screen's code, measuring every colour pair against
WCAG 2.2, counting tokens and semantics in the source, and walking each flow step by step.
No device session and no real players took part. That is itself finding **O-01**, and every
severity below is a hypothesis until R-01 has run.

---

## The verdict

The games are better than the product around them. Twenty working games, live multiplayer
across three backends, solid rules engines, result animations: the engineering is strong.
The experience wrapped around them works against it.

1. **Getting two people into a game is the hardest thing in the app.** The core promise is
   "play together with someone far away", and it needs one of them to deploy a server.
2. **It is a catalogue with no point of view.** There are 21 tiles of equal weight in enum
   order, three word games and two number puzzles.
3. **It only works when both people are free at the same moment**, which is rare for the
   people it is built for (long-distance partners, often in different time zones).
4. **It is silent, still to the touch and invisible to screen readers.** It has no sound
   and no haptics. TalkBack can play none of the games: the code has 0 `semantics`
   modifiers.
5. **The main button fails contrast.** White on coral is 2.78:1 and needs 4.5:1. So do the
   warning text, the "ready" green and the opponent's orange.
6. **There is no design system, only a palette**: 93 raw hex colours, 13 corner radii and
   about 25 font sizes, all typed inline.
7. **The UI ships blind.** The animations are compile-checked only, with no screenshots, no
   device QA and no user testing.

If only ten things get done, do these, in this order:

| # | Do this | Findings | Cost |
|---|---|---|---|
| 1 | Default hosted server and tappable invite links; "custom server" moves under Advanced | S-03, J-02, W-02 | L |
| 2 | First-run onboarding and one-tap pairing; delete the Battle menu | J-01, C-06, C-07 | L |
| 3 | Contrast fixes (ink text on coral, darker status colours, dark-mode outlines) | A-02 | S |
| 4 | Design tokens (colour, type, shape, space, motion) and moving every screen onto them | D-01, D-02 | L |
| 5 | Haptics and sound with one mute switch | I-01, I-02 | M |
| 6 | TalkBack for the turn-based boards | A-01, A-08 | L |
| 7 | Async turns and a "Your turn" home | S-02, C-01 | XL |
| 8 | Home restructured around your partner, recent games and categories | C-01, C-02, C-03 | M |
| 9 | Screenshot tests and scripted device runs in CI | O-02 | M |
| 10 | Voice chart, glossary and a copy rewrite moved into strings.xml | W-01 to W-07 | M |

---

## 1. UX strategy

### S-01 · Sev 3 · Cost L: The product doesn't know what it is

**Problem.** It is called "Game Suite" (the README says it was "formerly Arcade Sudoku").
The package is still `com.arcade.sudoku`, and the home screen is a grid of 20 games.

**Evidence.** The README's first line holds the actual idea: *"an Android app for playing
together with someone far away."* Nothing on the home screen says so.

**Why it's bad.**
- "Game Suite" is generic. Nobody can search for it, and it says nothing about who it is for.
- It drops the app into the most crowded part of the market, generic casual multi-game apps
  such as GamePigeon, Plato, Papergames and Board Game Arena. It loses there on catalogue
  size, polish and network.
- The job users hire it for is not "play Ludo". It is *"feel close to my person when we are
  apart."* Seen that way, its real rivals are FaceTime, couple apps such as Paired and
  Agapé, and sending each other Wordle scores.

**Proposal.**
- Reposition as **a ritual for two**, with a name that says "two of us", not "many games".
- The pair is the hero of the home screen; the games are the means.
- Add things only a pair app can have:
  - shared history ("our 112th game");
  - a running score between the two of you;
  - a daily game you both play;
  - "thinking of you" reactions that work outside a match.

**Why it's better.**
- Value innovation through an *innovative slice*: no general game app optimises for a pair,
  so this is uncontested ground.
- Every later decision gets a test: "does this make the two of us closer?"

**Breaks.** The app name, the README, the launcher icon, the home screen's information
architecture and the store copy.

### S-02 · Sev 4 · Cost XL: Play only works if both people are free at the same moment

**Problem.** Every multiplayer game is a live room: a lobby, both tap Ready, then both play
to the end. If one leaves, the match dies.

**Evidence.**
- `room.js` rooms and the LocalRoom mirror hold state only while both players are connected.
- `MatchPopup` handles "has left the game"; there is no resume.

**Why it's bad.**
- The target users live apart, often in different time zones. Finding a shared free slot
  is the hard part, and the app needs one for every game.
- The products that won this space (Words With Friends, GamePigeon, chess.com
  correspondence) won on **async turns**.
- Without async there is no reason to open the app unless you are already together on a
  call (Zeigarnik: no unfinished business pulls you back).

**Proposal.**
- **Async mode for every turn game**: Connect Four, Dots, Tic-Tac-Toe, Dominoes, Ludo,
  Colour Clash, Ship Battle, Code Breaker, Golf, Blocks, Memory.
  - The server keeps the match in Durable Object storage.
  - A push says "Alex played. Your move".
  - The home screen lists "Your turn" and "Their turn" matches.
  - A match lasts days, not minutes.
- **A daily pair puzzle** for the solo-style games (Sudoku, Word Race, Number Cross, Letter
  Grid, Slide): the same seed for both of you each day, played whenever you like, with
  results compared once both are done ("You 4:12 · Alex 3:58").
- Live play stays as the "we're both here now" mode.

**Why it's better.**
- It turns a session app into a habit app.
- Every turn is a small, low-effort way to say "I'm here", which is the job (S-01).
- Daily seeds give the Wordle effect without needing anyone online at the same time.

**Breaks.**
- The room model: a room is now a persistent match, not a socket session.
- Nearby play stays live-only.
- New server storage, a match list, push for every turn and conflict rules. This is the
  largest item in this audit, and the most valuable.

### S-03 · Sev 4 · Cost L: Players have to run a server to play online

**Problem.** Online play needs a "Server link" pasted into Profile. That means one player
has deployed a Cloudflare Worker or a Google Apps Script by following README sections 1a
to 1c.

**Evidence.**
- `ProfileScreen`: the "Battle server" card, the placeholder
  `https://…workers.dev or https://script.google.com/…` and the "Server password (only if
  the host set one)" field.
- The Battle menu shows, in red: "No server link yet. Add the host's link in your profile".

**Why it's bad.**
- Tesler's law: the system's complexity lands entirely on the user.
- For a non-technical partner this is a dead end: a severity 4 failure at the very first
  moment of truth (J-02).
- Keeping the repo open for others to self-host is right, but it is a developer feature
  presented as a player feature.

**Proposal.**
- The shipped APK has a **default hosted server built in at build time**. The URL comes from
  a GitHub secret (like `google-services.json` today), so forks build with their own and no
  private data is in the repo.
- "Use my own server" moves to Profile → Advanced.
- Invites carry the server inside the link (J-02), so even a custom server never has to be
  typed.

**Why it's better.**
- The first online game needs zero setup.
- The open-source story stays intact: forks set one secret.

**Breaks.**
- You run one shared Worker, with real rate limits and abuse protection.
- Room codes must be unique across all users, so they need to be longer or carry a server
  prefix.

### S-04 · Sev 3 · Cost XL: Android-only, installed by hand

**Problem.**
- The app is installed from a GitHub APK and updates itself through
  `REQUEST_INSTALL_PACKAGES`.
- There is no iOS app.

**Why it's bad.**
- Sideloading starts with a security warning, which is the worst possible first impression.
- There is no store presence: no reviews and no discovery.
- Most important, **a pair app on one platform fails whenever the two people use different
  phones**. That is often the case.

**Proposal.**
- Short term: the Play Store, internal testing track first.
- Medium term: a second client. Options:
  - Kotlin Multiplatform with Compose Multiplatform, which reuses the game logic and most
    of the UI;
  - a web client (PWA) for the turn games against the same server.

**Why it's better.** It removes the "my partner has an iPhone" dead end, and the store
brings trust.

**Breaks.** The self-updater (it goes away on store builds) and the build pipeline. A
second platform doubles QA.

### S-05 · Sev 2 · Cost M: No outcome and no metrics

**Problem.** The backlog is a list of outputs ("add animation", "add game"). No measure says
whether any of it helped.

**Proposal.**
- **North Star:** pairs who play together on 3 or more days a week.
- **HEART:** track Retention (week-2 pair activity) and Happiness (one-tap rating after a
  match, sampled sparingly). Ignore the rest for now.
- **Input metrics:**
  - time from install to first game together;
  - share of invites accepted;
  - async turns answered within 24 hours.
- Collect it opt-in, as counts only, with no content and no names. That fits the open-repo,
  privacy-first stance.

**Why it's better.** Sprints can then be picked by impact on a number rather than by what
looks fun to build.

**Breaks.** A small analytics endpoint on the Worker and a consent toggle.

### S-06 · Sev 2 · Cost M: Breadth over depth

**Problem.** There are 20 games:
- three are word games (Word Race, Letter Grid, Balloon Words);
- two are number puzzles (Sudoku, Number Cross);
- three are shedding or matching card and tile games (Colour Clash, Dominoes, Corner
  Blocks).

**Why it's bad.**
- Choice overload on the home screen (C-01).
- Every new game multiplies the maintenance cost of every cross-cutting feature in this
  audit (sound, TalkBack, async, tokens) by 20.

**Proposal.**
- Pick a **core six** that get full polish: async, accessibility, tutorials and sound.
- The rest go under "More games", or rotate as a "game of the week".
- Retire or merge the weakest after R-01 shows which ones pairs actually replay.

**Why it's better.** Depth makes six games feel great; breadth makes 20 feel uneven.

**Breaks.** Some games get less attention or are hidden, which is emotionally hard after
building them.

---

## 2. Journey mapping

The main persona is **Sam**: not technical, in a long-distance relationship with Alex
(already a user). This is the current-state map of Sam's first week.

| Phase | Does | Thinks | Feels | Pain points | Moment of truth |
|---|---|---|---|---|---|
| Discover | Gets an APK link in a chat from Alex | "Is this safe? What's an APK?" | Wary | Sideload warning (S-04) | Zero moment |
| First open | Sees "Game Suite" and 21 tiles | "What do I do with Alex here?" | Lost | No onboarding, no partner, no first step (J-01) | **First moment: weak** |
| Connect | Game → sheet → "Online with a code or nearby" → Online/Nearby → Join/Host → code → *"No server link yet"* → Profile → paste URL → back → Join | "Why do I need a server?" | Frustrated, then gives up or calls Alex for help | 9+ decisions; jargon; a red error before anything has happened (J-02, S-03) | **Breaks here** |
| Lobby | Picks a level; Alex picked another; reads a 30-word warning | "Who decides?" | Awkward | Level negotiation (C-08) | |
| Play | Plays the game | "Was that my turn?" | Engaged | No sound, no haptics; rules are a text wall (I-01, C-05) | Second moment: good |
| End | Sees the win animation; taps Rematch, which becomes "Not ready" | "Did it work?" | Up, then unsure | Weak ending: no series, no history, no next step (C-13) | Peak is fine; end is weak |
| Return | Nothing pulls Sam back unless Alex sends an invite | — | Forgets the app | No async, no daily game, no streak (S-02) | **Ultimate moment: missed** |

### J-01 · Sev 4 · Cost L: No first run

**Problem.** A new user lands on the shelf. The greeting is hidden because the name is
blank. There are no partner slots and no suggested first game.

**Why it's bad.**
- The paradox of the active user: people start tapping instead of reading.
- The pairing concept is explained only in a grey footnote inside the game sheet: *"Play
  one online game with your partner and they are saved here…"*.

**Proposal.** A three-step first run:
1. Your name and avatar.
2. "Who do you play with?" offering:
   - **Send Alex a link**: a share sheet with a tappable link and a QR code;
   - **I have a link from someone**: opens the link or reads a pasted one;
   - **Just me for now**.
3. A first game suggested and started right away: something quick like Connect Four or
   Tic-Tac-Toe, live if the partner is online, otherwise async.

The partner is saved on the first link, not after the first finished match.

**Why it's better.** The origin story reaches its climax (playing with Alex) in under two
minutes instead of never.

**Breaks.** The `SavedPartner` lifecycle (it is created on pairing, not after a match) and
the home screen's empty state.

### J-02 · Sev 4 · Cost M: Invites are text to copy and paste, not links

**Problem.** The invite is a block of text that Sam has to copy, and then find the small
"Paste the invite message instead" button for.

**Evidence.**
- `Invite.toShareText()` writes "Server link: … / Server password: … / Secret code: …".
- The manifest has **no `VIEW` intent filter**, so no link opens the app.

**Why it's bad.**
- Pure excise: memory and physical work to move three values between apps.
- The server password goes out in plain text through any chat app (responsible-design
  lens; see W-08).

**Proposal.**
- An **Android App Link** (`https://<your-domain>/j/K7Q2`) whose tiny landing page falls
  back to the install page.
- Tapping it opens the app straight into "Join Alex's Connect Four?". The server and code
  live in the link, and the password becomes a signed token with an expiry.
- Show a QR code on the host screen for same-room play.

**Why it's better.**
- One tap instead of about ten steps.
- It removes the "Paste the invite message instead" workaround and the whole Join form.

**Breaks.**
- The invite format and parsing (`Invite.parse`).
- It needs a domain and `assetlinks.json`, and a password replaced by tokens means server
  changes.

### J-03 · Sev 3 · Cost M: The week after has no story

**Problem.** After the first session nothing changes. There is no shared history, no
"last time Alex won" and no anniversary moments.

**Proposal.** A **pair timeline**: games played, a running score per game ("Alex leads
Ludo 7–5"), streaks, and milestones such as "100 games together" or "first Golf
hole-in-one" turned into keepsake cards you can share.

**Why it's better.**
- Peak-end is about memory: the app should *remember* the peaks for the pair.
- It adds identity and social value, which are among the five value types journey maps
  usually miss.

**Breaks.** New persistent per-pair stats. Today's Statistics card only counts battles and
levels.

### J-04 · Sev 2 · Cost S: Nearby play is explained with breadcrumbs

**Problem.** The host lobby says: *"Your friend opens Battle a friend → Nearby, taps Find and
picks your name."*

**Why it's bad.** A navigation path written into the copy means the flow is too deep for
the UI to carry.

**Proposal.** Show a QR code on the host, plus "Nearby: Alex's phone" appearing on the
guest's home screen automatically while the host is advertising.

---

## 3. UX research

### R-01 · Sev 4 · Cost M: No player has ever been watched using the app

**Problem.** Every decision so far, this audit included, comes from the builders' intuition
and reference images. There are no usability tests, no interviews, no analytics and no
in-app feedback.

**Why it's bad.** You cannot tell taste from evidence. The severity ratings here are
educated guesses until someone watches a real Sam.

**Proposal.** A guerrilla programme a solo team can run:

1. **Usability test, round 1.** Five non-technical pairs, in person or remote, 30 minutes
   each, a facilitator who did not build the app, and the same day's debrief.
   - Task A: *"Alex sent you a link. Start a game together."*
   - Task B: *"Find out how to play Dominoes."*
   - Task C: *"You want a rematch with different rules."*
   - Success criteria, set before testing: 4 of 5 complete Task A in under 2 minutes
     without help; SEQ of 5 or more on each task.
2. **Diary study.** Four long-distance pairs over two weeks. One question a day: *"Did you
   play together today? What got in the way?"*. Story-based, about past behaviour, never
   "would you use…".
3. **JTBD switch interviews** with 5–8 pairs who already play games together in some other
   app. Map push, pull, anxiety and habit.
4. **Continuous:** one pair conversation a week, kept in an Opportunity Solution Tree
   (R-02).

**Screener (behavioural, not giving away the target):**
- How often do you message someone who lives in another city or country?
- Which apps did you use with them in the last month?
- Exclude developers and anyone who has used this app.

**Why it's better.** Five users find most usability problems, and the riskiest assumption
here (that pairs want many games rather than one ritual) gets tested before XL work starts.

**Breaks.** Nothing in code; it costs time and incentives.

### R-02 · Sev 2 · Cost S: The backlog has no evidence trail

**Problem.** Items such as "Mini Golf graphics like the reference" or "Sudoku wave
highlight" are solutions with no stated opportunity.

**Proposal.**
- An Opportunity Solution Tree with the outcome from S-05 at the root.
- Opportunities written from the player's side ("I don't know whose turn it is when I look
  away").
- Each backlog item names the opportunity it serves and its severity × frequency.

**Why it's better.** Animation polish then competes on equal terms with "my partner can't
connect", and it would lose.

---

## 4. Design critique (heuristics, screen by screen)

### Home

#### C-01 · Sev 3 · Cost M: 21 tiles of equal weight in enum order

**Evidence.** `HomeScreen`: `GameKind.entries + null`, chunked into twos, so 11 rows of the
same card.

**Why it's bad.**
- **Hick's law and choice overload:** 21 equal options with no grouping, filter or
  favourites.
- **Serial position:** the newest games (Golf, Blocks) are buried at the bottom.
- **Organisation by internal structure:** the order is whatever order the enum was written
  in.
- **Von Restorff:** nothing stands out, not even the game you played yesterday.

**Proposal.** The home screen, top to bottom:
1. **Partner hero**: Alex's avatar, presence ("online now", "in a Ludo lobby", "last
   played 2 h ago") and one primary action ("Play with Alex").
2. **Your turn / their turn**: the async matches (S-02).
3. **Today's pair puzzle** (S-02).
4. **Jump back in**: the last three games played.
5. **Categories**:
   - Quick duels (Connect Four, Tic-Tac-Toe, Dots, Ships, Code Breaker);
   - Words (Word Race, Letter Grid, Balloon);
   - Numbers (Sudoku, Number Cross, Slide);
   - Party, 3–4 players (Ludo, Dominoes, Clash, Bingo, Blocks, Golf, Memory);
   - Action (Air Hockey, Draw & Guess).
6. "All games" at the end.

**Why it's better.** Recognition over recall, and less to choose between. The primary
action becomes the thing the app exists for.

**Breaks.** The home layout. The per-game sheet stays.

#### C-02 · Sev 3 · Cost S: The "Join with a code" tile is a fake game and assumes Sudoku

**Evidence.** `CodeTile` calls `onBattle(GameKind.SUDOKU)`. It sits on the shelf styled
exactly like a game (law of similarity, broken).

**Why it's bad.**
- A joiner does not choose a game: the host already did.
- Defaulting to Sudoku is wrong for 19 of 20 cases.
- Disguising an action as content confuses the shelf.

**Proposal.** "Join" becomes a global action: a top-bar button, or a small FAB with a QR
icon. It asks for the code or link and nothing else, and the game comes from the room.

**Breaks.** The tile, and `BattleMenuScreen`'s join path.

#### C-03 · Sev 3 · Cost M: "Invite Alex" or "Join Alex's game"? The user cannot know which

**Evidence.** `GameSheet` shows both buttons when push is available.

**Why it's bad.** The right choice depends on what Alex is doing, which the app knows (or
could know) and hides. Visibility of system status, broken.

**Proposal.**
- Presence on the partner card, and **one smart button**: "Alex is waiting in Connect Four
  → Join" or "Invite Alex to Connect Four".
- Push opens straight into the lobby.

**Breaks.** It needs presence (a heartbeat to the server) and changes to the invite flow.

#### C-04 · Sev 2 · Cost S: Solo is called five different things

**Evidence.**
- "Play solo" (Sudoku), "Play alone" (8 games), "Play the computer" (the rest).
- "Today's word or practice" (Words).
- A grey sentence instead of a button for Draw & Guess.

**Why it's bad.** Consistency and standards: the same concept needs the same label.

**Proposal.**
- Always "Play solo". The detail line underneath says "vs computer · Easy, Medium, Hard",
  "Daily or practice" and so on.
- Draw & Guess shows a disabled "Play solo" with the reason ("needs 2+ players") rather than
  a missing button.

#### C-05 · Sev 3 · Cost L: Rules are a wall of text nobody reads

**Evidence.**
- About **1,450 words** of rules prose in `GameKind.rules`.
- They are shown in a plain `AlertDialog` (`RulesDialog`) or behind a "How to play" text
  toggle.
- Only Code Breaker has examples.

**Why it's bad.**
- The paradox of the active user: players start, they don't study.
- Text dialogs are modal, cannot be glanced at, and are disconnected from the board.

**Proposal.**
- **A 20–40 second interactive tutorial** the first time each game is played, on the real
  board with scripted moves ("Tap here to drop a disc").
- **Rule cards**: three illustrated panels (goal, a turn, how to win) reusing the game's
  Canvas art.
- **Contextual hints** in-game, such as "Your turn: draw a line" or "Pins: red = right
  colour, right place".
- Rules in-game become an overlay that shows the board's *current* situation highlighted.

**Why it's better.** Learning by doing, and recognition rather than recall. This is how
every top-grossing casual game teaches.

**Breaks.**
- A tutorial engine (scripted state, coach marks) per game. That is large × 20, which is
  why S-06's core six matter.

### Battle menu

#### C-06 · Sev 4 · Cost L: The Battle menu should not exist

**Evidence.** `BattleMenuScreen` has:
- two nested toggles (Online / Nearby, then Join / Host);
- a host game chooser of **20 horizontally scrolling FilterChips**, a false floor where most
  options are off-screen;
- an editable "Secret code" with a refresh icon;
- a "Server" card;
- a Primary button whose label flips between "Host battle" and "Join battle".

**Why it's bad.**
- Four decisions, three of them system internals (transport, role, server), before the user
  gets to what they want: *play this game with that person.*
- Too many modes, and the button's meaning changes with a toggle.

**Proposal.** Delete the screen.
- **Hosting** starts from a game: "Play with a friend" creates a room instantly and shows
  the share sheet, QR code and code.
- **Nearby** is automatic: advertise and discover in the background while the lobby is
  open, and show nearby players in the same lobby next to "Share link".
- **Joining** is the global Join from C-02.

**Why it's better.** No navigation excise, and no modes. Transport is chosen by the system,
not the user (design for the probable).

**Breaks.**
- `BattleMenuScreen`, `GameChooser` and the `hosting`/`nearby` state.
- `BattleViewModel.host()`, `join()` and `nearbyHost()` merge behind one "create room" call.
- Running nearby discovery and online at the same time needs permission timing worked out.

#### C-07 · Sev 2 · Cost S: Users type and edit room codes

**Evidence.** The host code is an editable `OutlinedTextField` with a "New code" button.
The join field requires the exact code.

**Proposal.**
- Codes are generated, never edited.
- Use an unambiguous alphabet (no 0/O or 1/I/L).
- Accept lowercase, spaces, dashes and a whole pasted invite message in the code field
  itself, detected automatically (Postel's law).

**Breaks.** Custom codes. They were a workaround for the missing links.

### Lobby

#### C-08 · Sev 3 · Cost M: Everyone has to pick the same level, by negotiation

**Evidence.**
- `LevelPicker`, `levelStatus()` and `groupLevelStatus()`.
- On a mismatch: *"Levels don't match: you chose Hard, Alex chose Easy. Both players must
  choose the same level before the game can start."*

**Why it's bad.**
- A coordination problem turned into UI.
- With four players it is a vote with no chair.
- It is excise every single time, even though most pairs settle on one level and keep it.

**Proposal.**
- **The host's choice is the room's choice.** It is pre-filled with the pair's last level.
- Guests see "Hard · picked by Alex" and can tap "Suggest another".
- The match auto-starts with a 3-second countdown as soon as everyone is in, and anyone can
  cancel it.

**Why it's better.** It designs for the probable case: the same level as last time, with
zero taps.

**Breaks.** The server ready logic (`chooseLevel` needs everyone ready on the same level)
and the lobby protocol for all race games.

#### C-09 · Sev 2 · Cost S: The lobby shows the server URL

**Evidence.** `LobbyScreen` shows `profile.scriptUrl` under the code.

**Why it's bad.** Match between system and real world: a URL means nothing to a player, and
it adds noise next to the one thing they need (the code).

**Proposal.** Remove it. If needed, show it in a "Connection details" sheet.

### In game

#### C-10 · Sev 3 · Cost M: The dock holds meta actions, not game actions

**Evidence.** `battleDock()` is Leave / React / Rules in every game, while each game's real
verbs live elsewhere in its own layout.

**Why it's bad.**
- "Tools close at hand": the thumb zone is spent on actions used once per match.
- Leave (the ejector seat) is the first button, with the same weight as Rules.

**Proposal.**
- The dock holds each game's **verbs**: Sudoku notes, erase and hint; Ships fire and
  "auto-place"; Clash draw; Code Breaker submit; Golf aim aid.
- React moves to a floating bubble by the partner's chip.
- Rules and Leave go to an overflow (⋯) at the top.

**Breaks.** The `GameFrame` dock contract and every game screen's layout.

#### C-11 · Sev 2 · Cost S: Swipe-to-focus is a hidden gesture

**Evidence.** In Ship Battle, swiping up hides the chips. The hint "⌄ swipe down for the
players" appears only *after* the gesture.

**Proposal.**
- Show a visible collapse chevron on the player row.
- Or collapse automatically when it is your turn to fire, and expand on the opponent's turn.

**Why it's better.** Gestures should never be the only path.

#### C-12 · Sev 3 · Cost M: Whose turn it is lives in a glow

**Evidence.** `PlayerChip`: `active` changes the border from 2 to 3 dp, adds the game colour
and colours the detail text.

**Why it's bad.**
- This is the most important state in a turn game, shown with the weakest signal: a colour
  change. It fails for colour-blind players (A-03) and anyone glancing at the phone.

**Proposal.**
- A turn banner: "Your turn", with the avatar scaling in.
- A haptic tick and a sound.
- The other player's chip dims.
- If the app is in the background, a notification.
- For async, the app icon badge.

### Results

#### C-13 · Sev 2 · Cost M: Good peak, weak ending

**Evidence.**
- `ResultPopup` has a strong animation and title.
- Its buttons are "Finish", and "Rematch", which becomes "**Not ready**" after tapping.
- Elsewhere the same concept is called "Exit" or "Leave".

**Why it's bad.**
- Peak-end rule: the end is the part people remember.
- "Not ready" reads as a status, not an action.
- There is no series context, no stats, nothing to keep or share, and no bridge to the next
  game.

**Proposal.**
- Series score: "Alex 3 – 2 You · this week".
- Personal stats: "Fastest Sudoku yet".
- A shareable result card (image).
- "Rematch" changes to "Waiting for Alex… · Cancel".
- **"Play something else together"** with two suggestions.

**Breaks.** Per-pair stats (J-03) and the popup API.

---

## 5. Interaction design

### I-01 · Sev 3 · Cost M: No haptics at all

**Evidence.** `grep -ri "haptic\|vibrat"` finds **0** results.

**Why it's bad.**
- On a phone, touch without a physical response feels like glass.
- Haptics is the cheapest way to signal turn, error and success without looking (the
  feedback part of Saffer's trigger → rules → feedback model).

**Proposal.** One haptics layer with a small vocabulary:

| Event | Android API |
|---|---|
| Place or tap | `CLOCK_TICK` |
| Valid move | `CONFIRM` |
| Invalid move | `REJECT` |
| Your turn | short double pulse |
| Win | `VibrationEffect.Composition` rising primitives |
| Countdown | ticks |

It respects the system haptics setting and has a toggle in Profile.

**Breaks.** A new layer, and every game's move callbacks need wiring.

### I-02 · Sev 3 · Cost M: Silent

**Evidence.** Backlog #38 holds the 111 sounds catalogued in docs/SFX.md, but nothing plays.

**Proposal.**
- Raise #38 to *Next*.
- Ship the 55 priority-1 sounds first.
- One mute switch, plus separate music and effects volumes.
- Follow ringer mode.
- **Every sound gets a visual equivalent** (A-10).

### I-03 · Sev 3 · Cost S: Ignores "Remove animations"; motion never calms down

**Evidence.**
- `Motion.kt` uses fixed durations (450, 380 and 300 ms, plus spring bounces), with no
  check of the system animator scale.
- The Sudoku wave, shakes and dropIn bounces play at full strength every time.

**Why it's bad.**
- Vestibular safety (WCAG 2.3.3).
- Saffer: feedback should get quieter with repetition. The 200th Connect Four drop doesn't
  need the 450 ms show that the first one had.

**Proposal.**
- Read `Settings.Global.ANIMATOR_DURATION_SCALE`.
  - At 0, every `enterFrom`, `dropIn`, `shake` and `FlipCard` snaps.
  - Otherwise, multiply the durations by the scale.
- Add a Profile setting: "Motion: Full / Reduced".
- Shorten routine moves to 200–250 ms and keep the long animations for peaks (wins, sinks,
  bingo).

**Breaks.** Nothing structural. `Motion.kt` is already the right choke point.

### I-04 · Sev 3 · Cost M: Error states are dead ends

**Evidence.**
- `showMessage(e.message ?: "Something went wrong.")`.
- "Could not look for nearby players." (no reason why: Bluetooth? permission? location
  off?).
- "Could not reach your partner's battle. Try again."

**Proposal.**
- Name each failure and attach a one-tap fix: "Bluetooth is off · Turn on", "Location
  permission needed for Nearby · Allow".
- Make the connection state explicit as a state machine: connecting → connected →
  reconnecting (shown) → lost (offer "Wait" or "Leave").

### I-05 · Sev 3 · Cost M: The "away" opponent is a suffix on a name

**Evidence.** `battleChips()` appends `" (away)"` or `" (left)"` to the player's name.

**Why it's bad.**
- State is shown as text glued onto a name, and it truncates on small chips.
- There is no rule for *what happens next*. Does the match wait forever?

**Proposal.**
- A presence badge on the avatar.
- After 60 seconds away, a calm banner: "Alex's phone went quiet. Waiting… · Pause match ·
  Claim win after 2:00".
- In async mode this simply becomes "their turn".

**Breaks.** Server timeout rules per game.

### I-06 · Sev 2 · Cost L: No pass-and-play, and portrait is locked

**Evidence.** The manifest has `screenOrientation="portrait"`, and every game needs one
device per player.

**Why it's bad.**
- When the pair *is* together, or with family, one phone or tablet should be enough.
- Air Hockey on a tablet between two people is the best version of that game.
- A portrait lock also fails WCAG 1.3.4 unless orientation is essential.

**Proposal.**
- "Same device" mode for the turn games (pass the phone, with a privacy screen between
  turns for Ships and Clash).
- Split-screen Air Hockey.
- Landscape and tablet layouts through `BoardArea`.

**Breaks.** Assumptions in `LocalRoom` that each seat is a different device, and the
orientation lock.

### I-07 · Sev 2 · Cost S: Notification permission asked at the wrong time

**Evidence.** `AskForNotifications` fires on the home screen at launch, "ask once per
launch", saved with `rememberSaveable`. So it asks again on every cold start until Android
stops showing the prompt.

**Why it's bad.** The permission pattern says to explain the benefit in context. Android
blocks the prompt for good after two denials, so a premature ask burns it.

**Proposal.**
- Ask right after the first invite is sent or received, with a primer: "Get a buzz when
  Alex plays their turn · Allow".
- Remember the denial across launches.

---

## 6. Accessibility (WCAG 2.2 AA, plus the Game Accessibility Guidelines)

### A-01 · Critical · Level A · Cost L: Screen readers can play no game

**Evidence.**
- **0** uses of `semantics` in the whole app, and only 2 content descriptions.
- Every board (Sudoku, Connect Four, Ships, Dots, Tic-Tac-Toe, Code Breaker, Number Cross,
  Memory, Dominoes, Blocks) is a Canvas, or a grid of plain Boxes with no role, state or
  label.

**WCAG.** 1.1.1 Non-text content, 1.3.1 Info and relationships, 4.1.2 Name, role, value.

**Proposal.** For the turn-based and puzzle boards, which can be fully accessible:
- Give each cell a semantics node: `contentDescription = "Row 3, column 5, empty"` or
  `"Column 4, 3 discs, yours on top"`, plus `onClick`, `selected` and
  `collectionItemInfo`.
- Use custom accessibility actions ("Fire here", "Place 7", "Flip").
- Announce opponent moves through a live region ("Alex dropped in column 2").

For Air Hockey, Golf and Draw & Guess, say honestly that they are not screen-reader games,
and offer alternatives (for example, a Draw & Guess role where you only guess by voice).

**Breaks.** Each board's composable (it needs real nodes, not only pixels). It is large,
but each board is self-contained.

### A-02 · High · Level AA · Cost S: Contrast fails on the most important elements

Measured against WCAG 2.2 (1.4.3 for text, 1.4.11 for graphics):

| Pair | Ratio | Needs | Where |
|---|---|---|---|
| White on coral `#FF6B6B` | **2.78** | 4.5 | **Every PrimaryButton**, "Continue" badge |
| Coral text on accent `#F4F0FF` | **2.48** | 4.5 | Level buttons, TextButtons ("How to play", "Change in profile") |
| Opponent orange `#F2994A` text on white | **2.23** | 4.5 | "Alex's pick" under levels |
| Warning amber `#E08A00` on white | **2.69** | 4.5 | The level-mismatch message, the most important lobby text |
| Good green `#2EAD6B` on white | **2.87** | 4.5 | "Ready" pills, "Starting…" |
| White on Memory `#F2A33A` | **2.08** | 4.5 | Game name chip on the result popup |
| White on Slide `#FF8A3D` | **2.35** | 4.5 | Same |
| Dark mode: ink `#0B0816` outline on `#17132A` | **1.10** | 3.0 | Every card, button and chip outline (the whole Toy Box look disappears) |
| Sudoku thin lines `#BEC6D4` on white | **1.72** | 3.0 | Cell boundaries |

**Proposal.**
- **Keep the coral and put ink text on it.** `#2B2440` on `#FF6B6B` is **5.30:1**, keeps
  the brand and passes AA. Or darken the coral to `#D63A3A` for white text (4.64).
- Use darker text versions of the status colours:
  - amber `#B45309` (5.02 on white);
  - green `#047857` (5.48);
  - opponent orange `#B4530A` (5.02).
  Keep the bright versions for fills only.
- Game name chips use ink text on the game colour.
- Dark mode outline: `#8A80B8` (5.04 on the dark background), or drop outlines and use
  elevation.
- Sudoku thin lines: about `#8A95A8` or darker; re-measure to reach at least 3:1.

**Breaks.** The palette values, and nothing else once D-01 has landed.

### A-03 · High · Level A · Cost M: Identity and game meaning carried by colour alone

**Evidence.**
- Seat colours: orange, violet `#7B6CF0` and green `#20A47B`.
- **The same hexes are reused as game colours**: Dots and Letter Grid are `#7B6CF0`; Words
  and Ludo are `#20A47B`; Sudoku and Corner Blocks are both `#3D7BF7`.
- Code Breaker pegs, Colour Clash cards, Ludo tokens, Bingo daubs and the red/white pins are
  told apart by hue alone.

**WCAG.** 1.4.1 Use of colour. Also the Game Accessibility Guidelines: "ensure no essential
information is conveyed by colour alone".

**Proposal.**
- Each seat gets a **shape glyph** as well as a colour (● ▲ ■ ◆) on chips, tokens and
  pieces.
- Code Breaker pegs and Colour Clash cards get symbols (ColorADD-style, or ★ ● ▲ ■ ✚ ♥).
- Add a colour-blind palette option (tested with deuteranopia, protanopia and tritanopia
  simulators).
- No game colour may equal a seat colour.

**Breaks.** The art for every piece. Code Breaker and Clash rules stay the same.

### A-04 · High · Level AA · Cost M: Text does not scale

**Evidence.**
- About 25 different inline `fontSize = N.sp` values inside fixed-size boxes: dock circles
  of 46 dp, chips with `maxLines = 1` and an ellipsis, tiles with `minLines = 2`.
- Nothing reads `fontScale`.

**WCAG.** 1.4.4 Resize text: content and function must survive 200%.

**Proposal.**
- Move to type tokens (D-01).
- Let containers grow.
- Run screenshot tests at font scales 1.0, 1.3 and 2.0 (O-02).
- Names wrap to 2 lines instead of truncating.

### A-05 · Moderate · Level AA · Cost M: Drag-only moves and small hit areas

**Evidence.**
- Ship placement moves ships by drag; tap only rotates.
- Dots & Boxes lines are thin targets between dots on a 5 × 5 grid.
- Slide Race now has swipe, which is good, and tap stays as the fallback.

**WCAG.** 2.5.7 Dragging movements; 2.5.8 Target size of at least 24 × 24 dp, and 48 dp per
Material.

**Proposal.**
- Ships: tap a ship to pick it up, then tap where it goes.
- Dots: a tap snaps to the nearest free line within the box, with a preview highlight
  before confirming.
- Audit every board at a 48 dp minimum hit area (visuals can stay smaller).

### A-06 · Moderate · Level A · Cost S: Time limits you cannot change

**Evidence.**
- Bingo calls every 3.5 s.
- Letter Grid gives 2 minutes, and Draw & Guess and the races have timers, even in **solo**.

**WCAG.** 2.2.1 Timing adjustable. Live competitive play is exempt as "essential"; solo is
not.

**Proposal.** In solo, a "Relaxed" option (timer off, or slower calls). In multiplayer, the
host can set the pace (slow, normal, fast).

### A-07 · Moderate · Cost S: Reduced motion is not respected

This is the same fix as I-03, listed here because it is an accessibility requirement
(WCAG 2.3.3 and Android "Remove animations"), not a polish item.

### A-08 · High · Level AA · Cost M: Status changes are never announced

**Evidence.** Turn changes, opponent moves, "Starting…", results and reconnects are all
visual-only.

**WCAG.** 4.1.3 Status messages.

**Proposal.**
- `LiveRegionMode.Polite` on the turn banner (C-12) and on the status lines.
- An `announceForAccessibility` call on results.

### A-09 · Moderate · Cost S: Reactions and avatars have no text alternative

**Evidence.** `ReactionImage` and `AvatarImage` are drawn art with no description.

**Proposal.** Reactions are announced as "Alex reacted: fire". Avatars are decorative
(`null`) next to a name, and labelled where they stand alone.

### A-10 · Moderate · Cost S (when sounds land): Sound must never be the only signal

The SFX catalogue adds 111 sounds. Every gameplay-relevant one needs a visual or haptic
partner, for example "Bingo call" shown big on screen and "your turn" shown as the banner
(Game Accessibility Guidelines: provide visual equivalents for important sounds).

---

## 7. UX writing

### W-01 · Sev 2 · Cost S: No voice

**Problem.** The copy mixes three voices:
- playful toy-box: "Dab the calls, shout first";
- procedural manual: "Both players choose a level. The game starts when you both pick the
  same one.";
- system console: "Server link", "Test link".

**Proposal.** A voice chart with three traits:

| Trait | Is | Isn't |
|---|---|---|
| Warm | Talks to a pair: "you two", "together" | Gushy or cutesy in errors |
| Playful | A wink on wins and taglines | Jokes when something fails |
| Brief | 2–4-word buttons, sentences of 14 words or fewer | Paragraphs in a lobby |

Tone moves along these: celebration is louder, errors are calm and specific, and rules are
plain.

### W-02 · Sev 3 · Cost S: System jargon in a toy

**Evidence.** "Server link", "Server password (only if the host set one)", "Battle
server", "Test link", "Host", the `workers.dev` placeholder, "Secret code" and the URL in
the lobby.

**Proposal.** Players never see "server" (see S-03). "Host / Join" becomes "Start a game /
Join a game". "Secret code" becomes "Game code".

### W-03 · Sev 2 · Cost S: Instructions written as paragraphs

**Evidence.**
- The level mismatch message is 31 words.
- The nearby host text is 25 words with an arrow path.
- The partner footnote is 20 words.

**Rewrites:**

| Now | Proposed |
|---|---|
| "Levels don't match: you chose Hard, Alex chose Easy. Both players must choose the same level before the game can start." | "Alex picked Easy. Switch to Easy?" with the button **Play Easy** |
| "You chose Hard. Waiting for Alex to choose a level… (tap it again to cancel)" | "Waiting for Alex…" with the button **Cancel** |
| "Play one online game with your partner and they are saved here, ready for one-tap games and invitations." | "Add your partner to play in one tap." with the button **Add partner** |
| "Your friend opens Battle a friend → Nearby, taps Find and picks your name. Keep Bluetooth and Wi‑Fi on; no internet is needed." | "Ask your friend to scan this code." (plus a QR code) |
| "Draw & Guess needs two players: play with your partner, online or nearby." | "Needs 2 or more players" (disabled solo button) |

### W-04 · Sev 2 · Cost S: One concept, many words

| Concept | Words in use now | Pick one |
|---|---|---|
| A session | battle, match, game, round | **game** (a "round" only inside a game) |
| Quitting | Leave, Exit, Finish | **Leave** in a game; **Done** on a result |
| The other person | opponent, partner, friend, "the host" | **their name**; "partner" for the saved one |
| Alone | solo, alone, the computer | **solo** |
| Readiness | Ready, Not ready, Rematch, Choosing… | Rematch → "Waiting for Alex… · Cancel" |

This goes into docs as a glossary that all new copy must use.

### W-05 · Sev 3 · Cost S: Errors without cause or cure

**Pattern:** [What failed]. [Why]. [Fix].

| Now | Proposed |
|---|---|
| "Something went wrong." | Banned. Every catch names the failure. |
| "Could not look for nearby players." | "Can't search nearby. Bluetooth is off." with **Turn on** |
| "Could not reach your partner's battle. Try again." | "Alex's game has ended or the code changed." with **Invite again** |
| "No invite found on the clipboard." | "Copy Alex's invite first, then tap Paste." |
| "Couldn't send the score; it goes with the next goal." | Keep it: it's honest and says what happens next. |

### W-06 · Sev 2 · Cost S: Labels that lie

**Evidence.**
- The label "Ultimate Tic-Tac-Toe" and the tagline "Nine boards, one big game" stay, even
  though Classic 3 × 3 is now a mode.
- "Rematch" becomes "Not ready".

**Proposal.** Rename to "Tic-Tac-Toe", with a "Classic · Ultimate" mode line, and make the
tagline mode-neutral.

### W-07 · Sev 3 · Cost M: Every string is hard-coded in Kotlin

**Evidence.** `res/values/strings.xml` has **one** entry, `app_name`.

**Why it's bad.**
- No translation is possible. A pair app is especially likely to be used by bilingual pairs.
- No string review is possible either, and there is no plural handling ("1 more" vs
  "2 more").

**Proposal.** Move to `stringResource` with plurals. The glossary (W-04) becomes the source
of the string keys.

**Breaks.** It touches every screen, but it is mechanical. Do it alongside the copy rewrite
so each string is touched once.

### W-08 · Responsible · Cost S: The invite sends a password in plain text

**Evidence.** `toShareText()` includes "Server password: …" for whatever chat app,
clipboard history or cloud backup to keep.

**Proposal.** Signed, expiring join tokens inside the link (J-02). Until then, don't put the
password in the share text, and leave it to the host to tell people separately.

---

## 8. Design systems

### D-01 · Sev 3 · Cost L: A palette, not a system (maturity level 1–2)

**Evidence.**
- `Theme.kt` defines `SudokuPalette`, which is a good start.
- But the UI code contains:
  - **93** distinct raw `Color(0x…)` values;
  - **13** corner radii (3, 4, 5, 6, 8, 10, 12, 14, 16, 18, 20, 22, 26 dp);
  - about **25** font sizes;
  - **41** different dp values.
- `AppTypography` is defined and then bypassed by inline `fontSize`.
- Colours are scattered: `Warning` is private in `LobbyScreen`, `SeatColors` lives in
  `GameFrame`, and `OpponentColor` is in `Theme`.

**Why it's bad.**
- Every fix in this audit (contrast, font scale, dark mode, the colour-blind option) has to
  be made in hundreds of places instead of one.
- Inconsistency shows up as "it feels a bit off" without anyone being able to say why.

**Proposal.** Three tiers of tokens, as Kotlin objects (and optionally W3C `tokens.json` for
design tools):

| Token family | Values |
|---|---|
| Colour, raw | coral-500, ink-900… |
| Colour, semantic | action-primary, text-muted, status-warning-text, status-warning-fill, seat-1…4, outline, surface-raised |
| Colour, per game | `GameTheme(kind)` → container, onContainer, pattern |
| Type | 7 steps: display-l 34, display-m 26, title 20, body-l 16, body 14, label 12, micro 11 |
| Shape | radius-s 8, m 12, l 20, full |
| Space | 4, 8, 12, 16, 20, 24, 32 |
| Motion | quick 150, move 250, flip 380, celebrate 900, plus easing tokens (extend `Motion`) |
| Elevation | pressed-edge-s, m, l (the signature Toy Box shadow, made official) |

Add a lint rule (detekt custom rule or a grep in CI) that fails on `Color(0x`, raw `.sp` or
`RoundedCornerShape(N.dp)` outside the theme package.

**Breaks.** Touches every screen. It is mechanical, and a prerequisite for A-02, A-04 and
E-03.

### D-02 · Sev 2 · Cost M: Components reinvented screen by screen

**Evidence.**
- `GameTile`, `CodeTile`, `PartnerCard` and `PlayerChip` each re-implement outline + radius
  + `pressedEdge`.
- The options cards are one-offs: `DominoOptionsCard`, `NumbersLevelCard`,
  `WordLevelCard`, `SlideLevelCard`, `TttModeCard`.
- Every per-game lobby branch is a hand-written `if`.

**Proposal.**
- Core components: `ToyCard(variant = raised | flat | selected)`, `ToyButton(primary |
  secondary | ghost | danger)`, `Chip`, `OptionGroup`, `PlayerBadge`, `TurnBanner`,
  `StatusLine(tone)`.
- **Server-driven game options**: each rules module declares its options schema (name,
  type, choices, default), and one generic `GameOptionsCard` renders it.

**Breaks.** The five options cards and the lobby `when`. The server's `setOptions` becomes
schema-validated.

### D-03 · Sev 2 · Cost S: Game identities collide

**Evidence.** Six games share three colours, and those colours equal the seat colours
(A-03). The icons are small Canvas glyphs in same-shaped coloured squares.

**Proposal.**
- Game identity = **colour + pattern + key art**.
- Twenty distinct hues is not achievable while staying accessible. Group by category hue
  (Words = greens, Numbers = blues, Party = warm) and tell games apart by pattern and art,
  not hue alone.

### D-04 · Sev 2 · Cost M: No catalogue, no previews

**Evidence.** **0** `@Preview` functions in the app, and no component gallery.

**Proposal.**
- A debug-only "Design system" screen that renders every component in every state, light
  and dark, at font scales 1.0 and 2.0.
- Also feeds the screenshot tests (O-02).

### D-05 · Sev 2 · Cost S: Dark mode is an inversion

**Evidence.** In `DarkPalette`, the ink is almost the background colour (A-02), so the
outline-and-pressed-edge language disappears. Sudoku keeps its own blue world in both
themes.

**Proposal.** Design dark mode as its own theme:
- lifted surfaces instead of outlines, or a light ink edge;
- tuned game colours (less saturated on dark);
- the Sudoku board brought into the same token set (E-04).

---

## 9. Visual design (elevation)

### E-01 · Sev 2 · Cost M: Everything has the same chunky weight

**Problem.** Tiles, the partner card, chips, dock buttons, result cards, level buttons and
cards all have a 2 dp ink border and a pressed edge.

**Why it's bad.** Von Restorff: when everything is bold, nothing is. The Toy Box signature
loses its meaning through overuse.

**Proposal.**
- Keep the chunky pressed edge for **things you press that matter** (the primary button,
  the game tiles, your playing pieces).
- Information surfaces (the status card, the lobby player list) go flat with a hairline.

**Why it's better.** Clear hierarchy, and the signature regains its punch.

### E-02 · Sev 2 · Cost L: The shelf has no shelf appeal

**Problem.** Each game is a 28 dp glyph in a 46 dp square, with a name and two lines of
grey. Twenty of them look alike when you scroll.

**Proposal.** Give every game its own **key art** at hero and tile sizes, the way Apple
Arcade and Nintendo Switch Online present their games: distinct compositions (a domino
snake, a golf green, an ocean grid), drawn in the existing Canvas art style. Show it big on
the game sheet and in the result popup.

**Breaks.** Art time for 20 games (or for the core six from S-06).

### E-03 · Sev 2 · Cost S: The type scale is ad hoc

**Evidence.** Display sizes in use include 19, 20, 22, 24, 26, 28, 34 and 36 sp, and line
heights are set by hand (`lineHeight = 20.sp` on 19 sp text). Baloo 2 and Figtree are a
good pairing, used without a scale.

**Proposal.**
- The seven-step scale from D-01.
- Tight tracking on display sizes.
- `tabular-nums` on every timer, score and percentage (they jitter today as digits change).

### E-04 · Sev 1 · Cost M: Sudoku is a stranger in its own app

**Evidence.** In `Theme.kt`, the Sudoku board "keeps its clean blue-on-white colours". It
was the original app, and it now looks pasted in.

**Proposal.** Re-skin the Sudoku board in Toy Box tokens (ink lines, card surface, a coral
or Sudoku-blue selection), keeping the legibility-first decisions (the peer and same-digit
highlights).

### E-05 · Sev 1 · Cost S: First impression assets

**Problem.** The launcher icon, the splash screen and the name all say "generic suite".

**Proposal.** After S-01's renaming, design the icon around "two" (two pieces, two
avatars), and add an Android 12+ splash with an animated icon.

---

## 10. Design ops

### O-01 · Sev 4 · Cost M: UI ships without anyone seeing it

**Evidence.** Each animation release so far was checked by compiling only. There is no
emulator run, no screenshot and no device session before the APK goes out.

**Why it's bad.**
- Visual and interaction bugs reach players first.
- A movement animation that was never watched is a guess, and so is this audit's code
  reading.

**Proposal.** See O-02. Also a weekly ten-minute on-device pass by the product owner,
following a written script.

### O-02 · Sev 3 · Cost M: No visual regression or accessibility checks in CI

**Proposal.**
- **Roborazzi or Paparazzi screenshot tests** for every screen and every game board in its
  key states (start, mid-game, win, lose, tie). Run them in light and dark, at font scales
  1.0 and 2.0. Upload the images as a CI artifact on every push, so a reviewer sees every
  change.
- **Compose accessibility checks** (ATF via `enableAccessibilityChecks()` in UI tests). They
  fail the build on missing labels, small targets and low contrast.
- **Maestro flows** on an emulator in CI, recording video:
  - first run;
  - pair by link;
  - play one game of each core game to the end.

**Why it's better.** The team sees the product on every commit. Design QA becomes
automatic, not heroic.

### O-03 · Sev 2 · Cost S: Definition of done has no design criteria

**Proposal.** Every backlog item has to pass this list:
- [ ] All states designed: loading, empty, error, waiting, away, win, lose, tie
- [ ] Tokens only (the lint rule passes)
- [ ] Contrast ≥ 4.5:1 for text and ≥ 3:1 for graphics
- [ ] TalkBack: every control named; status announced
- [ ] Font scale 200% screenshot reviewed
- [ ] Dark mode screenshot reviewed
- [ ] Reduced motion respected
- [ ] Haptics and sound hooked up (once I-01 and I-02 exist)
- [ ] Copy uses the glossary and lives in strings.xml

### O-04 · Sev 2 · Cost S: Design decisions live in chat

**Problem.** The reference images, the choices made (for example the Toy Box style
options 1A and 2B) and the reasons behind them are scattered through conversation history.

**Proposal.** A `docs/design/` folder holding:
- `principles.md` (5 principles, such as "two people first" and "never make them type");
- `voice.md` (W-01);
- `glossary.md` (W-04);
- `tokens.md` (D-01);
- `decisions/NNN-title.md`, one per significant UX decision (context, options, choice,
  evidence);
- `references/`, holding the reference images.

### O-05 · Sev 2 · Cost S: No feedback channel from players

**Proposal.**
- "Send feedback" in Profile, which opens a prefilled email or GitHub issue form with the
  app version and game.
- An optional one-tap "How was that game?" (👍 / 👎) after every tenth match, feeding
  S-05's Happiness metric.

---

## Roadmap (now / next / later)

**Now (the next 2 sprints): stop the bleeding**
- A-02 contrast · I-03/A-07 reduced motion · W-02, W-03, W-05, W-06 copy fixes · C-02 Join
  as a global action · C-04 solo label · C-09 hide the URL · I-07 permission timing ·
  W-08 no password in the share text
- D-01 tokens and the lint rule (the foundation for everything else)
- O-02 screenshot tests · O-03 definition of done
- R-01 round 1: five pairs, first-connection task

**Next (sprints 3–6): fix the core journey**
- S-03 default server · J-02 app links and QR · J-01 onboarding · C-06 delete the Battle
  menu · C-08 host picks the level
- I-01 haptics · I-02 sound (priority 1) · C-12 turn banner · A-08 announcements
- A-01 TalkBack on the core six · A-03 shape glyphs and the colour-blind option
- C-01 home restructure · C-03 presence and the smart CTA · C-13 result ending
- W-07 strings.xml and the glossary

**Later (the big bets, validate with R-01 and R-02 first)**
- S-02 async turns and the daily pair puzzle
- S-01 rename and repositioning · J-03 pair timeline · E-02 key art
- C-05 interactive tutorials · C-10 game-verb dock · I-06 pass-and-play and tablets
- S-04 Play Store, then an iOS or web client
- S-06 core six and retiring games

**Sources:**
- Nielsen's 10 usability heuristics; Laws of UX (Hick, Fitts, Von Restorff, Peak-End,
  Zeigarnik, Tesler, Postel, Jakob).
- WCAG 2.2; the Game Accessibility Guidelines (gameaccessibilityguidelines.com); the Xbox
  Accessibility Guidelines; Android accessibility and haptics documentation; Material 3
  motion and touch-target guidance.
- Cooper, *About Face* (posture, excise, perpetual intermediates).
- Saffer, *Microinteractions*.
- Lichaw, *The User's Journey*.
- Torres, *Continuous Discovery Habits*.
- Kalbach, *The Jobs To Be Done Playbook*.
- Levy, *UX Strategy*.
- Google HEART.
- Frost, *Atomic Design*; Kholmatova, *Design Systems*; the W3C Design Tokens draft.
- Krug, *Don't Make Me Think*.
