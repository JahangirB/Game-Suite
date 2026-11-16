# The Game Store round: checklist

Tick a line in the same commit as the work. A line that is not done carries a note saying why.

## Before building

- [x] The owner has confirmed the plan and the three questions in PLAN.md section 6

## Steps

- [x] **Step 1. Take out "Update by itself"**
  - [x] Both commits reverted; the update offer works as before; `quiet-leave-wip` deleted
- [x] **Step 2. The shelf and the Store, as data**
  - [x] The nine by default; old order and put-away keys migrated and left in place; tested
- [ ] **Step 3. The fixed top bar**
  - [ ] "Hi, name", app name, avatar with photo; stays put when scrolling; opens the profile
- [ ] **Step 4. Hold a card to edit the shelf**
  - [ ] Arrange screen, button and hint gone
  - [ ] Wiggle, drag to move (saved), × to remove with Undo; TalkBack move actions
  - [ ] An empty shelf says so and points to the Store
- [ ] **Step 5. The Game Store**
  - [ ] Store list with working filter, Get, "On your shelf"
  - [ ] A game's page, "Add to my games", then Play / Remove
  - [ ] The one-time note after the update; a "Game Store" entry in Another game and switch-game
- [ ] **Step 6. A partner picks a game you don't have**
  - [ ] "Add and join" sheet from invite, lobby, Another game and switch-game
- [ ] **Step 7. Profile**
  - [ ] Photo in every own avatar
  - [ ] Ship Battle styles only in the game's Skins button
- [ ] **Step 8. Colour Clash: the dictator card just gives +8**
  - [ ] No lock, no animation; +8 to the next player; tested in all servers and on the phone
- [ ] **Step 9. Mini Golf: the Kylian statue blocks only the player who landed there**
  - [ ] Bug reproduced by a test, then fixed, in Cloudflare, nearby, solo and the screen's locks
- [ ] **Step 10. Mini Golf: an unfinished hole costs 5**
  - [ ] Scoring, score strip, result and rules; tested in every place
- [ ] **Step 11. The last pass**
  - [ ] All checks pass locally; the last CI run is green; the preview is from it
  - [ ] Every changed screen looked at in light and dark
  - [ ] README updated
  - [ ] The owner has the report and the preview link
