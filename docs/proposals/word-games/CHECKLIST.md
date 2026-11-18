# Two new word games: checklist

Tick a line in the same commit as the work. A line that is not done carries a note saying why.

## Before building

- [x] The owner has confirmed the catalogue ("carry on")
- [x] The owner has answered the questions in PLAN.md section 6 (or accepted the defaults): defaults accepted

## Steps

- [x] **Step 1. Word lists**
  - [x] Ladder words (4/5/6) with neighbours; Word Hunt themes; real, right length, blocklist clean
    (4: 770 words, 5: 2038, 6: 5509; 6 themes of 40+; theme words are 3 to 8 letters, the longest the word list has)
- [x] **Step 2. Word Ladder rules** (Opus check)
  - [x] Picks, random start, path check, one-letter moves, no repeats, win, no move, timer
  - [x] Same test cases pass in Kotlin and Cloudflare
- [x] **Step 3. Word Ladder online and nearby** (Opus check)
  - [x] Full game in rooms and nearby; picks secret until the flip; rematch and leave
- [x] **Step 4. Word Ladder screens**
  - [x] Setup, pick, flip, ladder, wrong move, result, rules, tutorial; card and result art
  - [x] Screenshots light and dark; a test plays through the screen
- [x] **Step 5. Word Ladder solo**
  - [x] Easy, Medium, Hard computer, each tested
- [x] **Step 6. Word Hunt grid maker**
  - [x] Across and down (backwards by switch); every word once; no side under 10; seeded
- [x] **Step 7. Word Hunt rules** (Opus check)
  - [x] Claims, score, hint, timer, end; near-same-time claims; same tests in Kotlin and Cloudflare
- [x] **Step 8. Word Hunt online and nearby** (Opus check)
  - [x] Full 3-player game in rooms and nearby; late join sees found words
- [ ] **Step 9. Word Hunt screens**
  - [ ] Setup, play, found word, zoom with map, result, rules, tutorial; card and result art
  - [ ] Screenshots light and dark; a drag in each direction claims a word
- [ ] **Step 10. Word Hunt solo**
  - [ ] Against the clock with a best per size; computer finders
- [ ] **Step 11. In the app**
  - [ ] Both in the Game Store with "New"; game pages; scoreboard and records; README
- [ ] **Step 12. The last pass**
  - [ ] All checks pass locally; the last CI run is green; the preview is from it
  - [ ] Every new screen looked at in light and dark
  - [ ] The owner has the report, the preview link, and the note to deploy Cloudflare
