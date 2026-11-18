# Two new word games: checklist

Tick a line in the same commit as the work. A line that is not done carries a note saying why.

## Before building

- [x] The owner has confirmed the catalogue ("carry on")
- [x] The owner has answered the questions in PLAN.md section 6 (or accepted the defaults): defaults accepted

## Steps

- [x] **Step 1. Word lists**
  - [x] Ladder words (4/5/6) with neighbours; Word Hunt themes; real, right length, blocklist clean
    (4: 770 words, 5: 2038, 6: 5509; 6 themes of 40+; theme words are 3 to 8 letters, the longest the word list has)
- [ ] **Step 2. Word Ladder rules** (Opus check)
  - [ ] Picks, random start, path check, one-letter moves, no repeats, win, no move, timer
  - [ ] Same test cases pass in Kotlin and Cloudflare
- [ ] **Step 3. Word Ladder online and nearby** (Opus check)
  - [ ] Full game in rooms and nearby; picks secret until the flip; rematch and leave
- [ ] **Step 4. Word Ladder screens**
  - [ ] Setup, pick, flip, ladder, wrong move, result, rules, tutorial; card and result art
  - [ ] Screenshots light and dark; a test plays through the screen
- [ ] **Step 5. Word Ladder solo**
  - [ ] Easy, Medium, Hard computer, each tested
- [ ] **Step 6. Word Hunt grid maker**
  - [ ] Across and down (backwards by switch); every word once; no side under 10; seeded
- [ ] **Step 7. Word Hunt rules** (Opus check)
  - [ ] Claims, score, hint, timer, end; near-same-time claims; same tests in Kotlin and Cloudflare
- [ ] **Step 8. Word Hunt online and nearby** (Opus check)
  - [ ] Full 3-player game in rooms and nearby; late join sees found words
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
