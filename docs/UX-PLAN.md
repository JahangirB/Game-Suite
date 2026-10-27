# UX plan

What was decided after the [UX audit](UX-AUDIT.md), and the order the work is done in. Each
step is one commit, pushed and checked on CI.

## Direction

- **Who it's for:** made for two people who play together when they're both free. Being
  easy to start or habit-forming is not a goal.
- **If it ever goes public:** an offline game suite for everyone, phone to phone over
  Nearby (flights, trains, no internet). A hosted server would come only if it gets popular.
- **Not wanted:** async turns, a daily puzzle, a Play Store or iOS release for now, app
  links, a pair timeline.

## Design catalogues (approved)

- [Foundations](https://claude.ai/artifact/SBF75mefUb5tyFVG1jPEC3):
  - **Accepted:**
    - contrast;
    - dark mode;
    - type scale;
    - tokens;
    - visual hierarchy;
    - shapes for colour-blind players.
  - **Rejected:** new motion speeds. Today's speeds stay; only the phone's "Remove
    animations" setting is honoured.
- [Structure](https://claude.ai/artifact/2AK6P8dGX2GghZNRLmEejV): all accepted.
  - **Nearby uses a room code**, like online. Only someone who can see the code joins, and
    the room is saved so the same code reconnects later.
  - **No suggest-and-accept step.** Whoever picks a game goes to its lobby. The others see
    where they are and join the same game; that person sets the game's options.
- [Game moments](https://claude.ai/artifact/X6pgSzGJ2AuwMkt8HvMmBH): all accepted.
  - Rules use **option 1**, the animated tutorial on the real board.
  - The icon waits for the new name.
  - The new colours are fine.

## Steps

Steps 1 to 20 are done. Online play across games needs the updated Cloudflare Worker
(redeploy it, see "Updating the server" in the README) and the new app on every phone.

Started first:

1. Ludo: the die stays white so its dots show in dark mode.
2. A race countdown's end is pushed to everyone (Nearby host and server).
3. Foundations catalogue.
4. Structure catalogue.
5. Game moments catalogue.

Foundations in code:

6. Screenshot tests in CI, light and dark, so every later step can be looked at.
7. Design tokens, plus a check that fails on raw colours, sizes and corner radii outside the
   theme.
8. Contrast fixes and the dark-mode palette.
9. Type scale; fixed-width digits for timers and scores.
10. Core components (card, button, chip, option group, status line) and visual hierarchy.
11. A design-system screen in test builds showing every component in every state.
12. Distinct game colours; shapes for seats and pieces.
13. Honour "Remove animations".

Connection and navigation rebuild:

14. Session: one connection that switches games without reconnecting (server, Nearby
    host), with tests.
15. Nearby rooms with a code; saved rooms to reconnect.
16. Online/Offline switch with a saved preference.
17. New home:
    - game catalogue with hold-and-drag ordering;
    - Join as a button on the home screen;
    - the Battle menu removed.
18. Back to the menu keeps the connection; see where your partner is and join (C-03);
    an explicit Leave.
19. First-run screens.
20. Invite without the password; no system words such as "server".

In-game:

21. Haptics.
22. Priority-1 sounds, a mute switch, a visual cue for every sound.
23. Turn banner; visible collapse in Ship Battle; notification permission asked at the
    right time.
24. Away state and errors with a fix.
25. Result ending: score for the session, "Back to games", "Play something else".
26. Relaxed pace in solo; tap instead of drag; bigger hit areas.
27. Tutorials for the first 10 games.
28. Tutorials for the other 10.

Copy and polish:

29. Copy pass: one word per idea, shorter instructions, labels that tell the truth.
30. Key art for the first 10 games.
31. Key art for the other 10.
32. Sudoku in the Toy Box look.
33. New name, icon and splash, once the name is chosen.
