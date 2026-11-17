// Mini Golf: nine holes, two to four players taking turns, fewest strokes wins.
//
// The courses and the physics live in the app: the player shooting works the shot out on
// their phone and sends it with where the ball stopped ({ kind, angle, power, x, y, holed, skip });
// the other phones replay the same shot. This module keeps the strokes and whose turn it is.
// kind is 'roll' or 'jump'. skip says the ball stopped on the Kylian sticker spot: he takes the
// player's next turn (passed over; if nobody else is still out, that turn is a shot added).
// Everyone tees off in seat order, then whoever is still out plays in turn. Nobody is picked up
// before someone is in: once the first player holes out, everyone still out gets AFTER_FIRST_IN
// more shots, and a ball still out after those is picked up and scores its strokes plus
// UNFINISHED_PENALTY (the penalty is in the stroke count itself, so older apps total it right;
// `unfinished` in the view only says which holes had it). (MAX_STROKES
// is only a safety net against a hole that never ends.) The player to start moves on a seat
// each hole.
//
// The holes come from the app's library of COURSES designed holes (assets/golf/courses.jsonl):
// this module only picks the nine numbers, so every phone plays the same holes. All nine are of
// the level whoever opened the game picked: easy (one island, holes 0-332), medium (two islands,
// 333-665) or hard (three islands, 666-999).

export const HOLES = 9;
export const MAX_STROKES = 20;
export const AFTER_FIRST_IN = 3;
/** What a ball still out when its hole ends adds to its strokes. */
export const UNFINISHED_PENALTY = 5;
export const COURSES = 1000;
const MEDIUM = 333;
const HARD = 666;

export const LEVELS = { easy: [0, MEDIUM], medium: [MEDIUM, HARD], hard: [HARD, COURSES] };

export function cleanOptions(options) {
  return { level: Object.hasOwn(LEVELS, options?.level) ? options.level : 'easy' };
}

/** Nine different holes of [level]. */
export function pickCourse(random = Math.random, level = 'easy') {
  const [from, to] = LEVELS[cleanOptions({ level }).level];
  const picked = new Set();
  while (picked.size < HOLES) picked.add(from + Math.floor(random() * (to - from)));
  return [...picked];
}

export function start(random = Math.random, marks = ['h', 'g'], options = {}) {
  return {
    marks, hole: 0, course: pickCourse(random, cleanOptions(options).level),
    strokes: Object.fromEntries(marks.map((m) => [m, [0]])),
    balls: Object.fromEntries(marks.map((m) => [m, null])),
    done: Object.fromEntries(marks.map((m) => [m, false])),
    // The stroke count each player still out must hole by, once someone is in (null till then).
    deadline: Object.fromEntries(marks.map((m) => [m, null])),
    // Players whose next turn Kylian takes.
    skip: Object.fromEntries(marks.map((m) => [m, false])),
    last: null, shots: 0,
    turn: marks[0],
  };
}

const result = (turn, extra = {}) => ({ again: false, over: false, winner: null, reason: null, turn, ...extra });
const total = (s, m) => s.strokes[m].reduce((a, b) => a + b, 0);

/**
 * Picks [m] up if the stroke count they have reached ends their hole: the shots they were given
 * after the first player was in are used up, or it is MAX_STROKES. The strokes taken plus the
 * penalty are scored, once (the ball is done after this). Returns whether it picked them up.
 */
function pickUp(s, m) {
  const taken = s.strokes[m][s.hole];
  if (s.done[m] || !((s.deadline?.[m] != null && taken >= s.deadline[m]) || taken >= MAX_STROKES)) return false;
  s.done[m] = true;
  s.strokes[m][s.hole] = taken + UNFINISHED_PENALTY;
  ((s.unfinished ||= {})[m] ||= [])[s.hole] = true;
  return true;
}

/** The players still out on this hole in turn order after [mark], [mark] (if still out) last. */
function outAfter(s, mark) {
  const n = s.marks.length;
  const i = s.marks.indexOf(mark);
  return Array.from({ length: n }, (_, k) => s.marks[(i + 1 + k) % n]).filter((m) => !s.done[m]);
}

/**
 * Who plays after [mark]: the next player still out, passing over each one whose turn Kylian
 * took (once each). If every one still out lost a turn, they are all given back and play goes on
 * in order; a player alone on the hole has the turn lost as a shot added instead.
 */
function nextTurn(s, mark) {
  const out = outAfter(s, mark);
  if (!out.length) return null;
  const at = out.findIndex((m) => !s.skip?.[m]);
  if (at < 0) {
    out.forEach((m) => { s.skip[m] = false; });
    if (out.length === 1) {
      // The turn lost is a shot added, which can be the one that ends their hole.
      s.strokes[out[0]][s.hole] += 1;
      if (pickUp(s, out[0])) return null;
    }
    return out[0];
  }
  out.slice(0, at).forEach((m) => { s.skip[m] = false; });
  return out[at];
}

// The holes are 1 wide and 3.2 long (older ones 1.6).
const unit = (v) => typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= 4;

export function play(s, mark, move) {
  if (s.hole >= HOLES) throw new Error('The game is over.');
  if (s.done[mark]) throw new Error("You're in the hole already.");
  const angle = Number(move?.angle);
  const power = Number(move?.power);
  const x = Number(move?.x);
  const y = Number(move?.y);
  if (!Number.isFinite(angle) || !(power > 0 && power <= 1) || !unit(x) || !unit(y)) throw new Error('That shot is not valid.');
  const holed = move?.holed === true;
  const kind = move?.kind === 'jump' ? 'jump' : 'roll';
  const skipped = move?.skip === true && !holed;
  const from = s.balls[mark];
  s.deadline ||= {};
  s.strokes[mark][s.hole] += 1;
  s.balls[mark] = [x, y];
  if (holed) {
    s.done[mark] = true;
    // The first one in: everyone still out gets AFTER_FIRST_IN more shots.
    for (const m of s.marks) {
      if (!s.done[m] && s.deadline[m] == null) s.deadline[m] = s.strokes[m][s.hole] + AFTER_FIRST_IN;
    }
  } else {
    pickUp(s, mark);
  }
  s.skip ||= {};
  if (skipped && !s.done[mark]) s.skip[mark] = true;
  s.shots += 1;
  s.last = { mark, hole: s.hole, from, kind, angle, power, to: [x, y], holed, skip: skipped, seq: s.shots };
  const next = nextTurn(s, mark);
  if (next) return result(next);
  // Everyone is in: the next hole, or the end.
  if (s.hole + 1 >= HOLES) {
    s.hole = HOLES;
    const ranked = s.marks.slice().sort((a, b) => total(s, a) - total(s, b));
    const tie = ranked.length > 1 && total(s, ranked[0]) === total(s, ranked[1]);
    return { again: false, over: true, winner: tie ? null : ranked[0], reason: tie ? 'draw' : 'strokes', turn: null };
  }
  s.hole += 1;
  for (const m of s.marks) {
    s.strokes[m].push(0);
    s.balls[m] = null;
    s.done[m] = false;
    s.deadline[m] = null;
    s.skip[m] = false;
  }
  return result(s.marks[s.hole % s.marks.length]);
}

/** The game as [mark] sees it; everyone sees everything. */
export function view(s, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const each = (f) => Object.fromEntries(s.marks.map((m) => [who(m), f(m)]));
  return {
    golf: {
      hole: Math.min(s.hole, HOLES - 1),
      holes: HOLES,
      course: s.course,
      maxStrokes: MAX_STROKES,
      strokes: each((m) => s.strokes[m]),
      balls: each((m) => s.balls[m]),
      done: each((m) => s.done[m]),
      // Which holes each player was picked up on (their strokes there include the penalty).
      unfinished: each((m) => s.strokes[m].map((_, i) => s.unfinished?.[m]?.[i] === true)),
      // Shots left for each player still out once someone is in; null before that.
      left: each((m) => (s.done[m] || s.deadline?.[m] == null ? null : s.deadline[m] - s.strokes[m][s.hole])),
      last: s.last ? { ...s.last, mark: undefined, who: who(s.last.mark) } : null,
    },
  };
}
