// Word Hunt rules: a race on one shared grid (grid maker: wordhunt.js). Players drag from one end of
// a hidden word to the other; the first claim the server processes wins the word and scores its
// length. Each player has one hint per round (it shows where one unfound word starts and costs 2
// points). The round ends when every word is found or the timer runs out; the highest score wins.
// Same rules as the app's HuntMatch.kt; test/wordhuntmatch.test.js and the app's HuntMatchTest share
// app/src/test/resources/wordhuntmatch-cases.json.
//
// Choices (both languages):
//  - A score never goes below 0: a hint at 0 points is free but still used up.
//  - A tie for the top score is a draw: winner is null, `winners` lists everyone on the top score.
//  - Rule errors are thrown with the reason to show: "Not a word here", "Already found by <mark>"
//    (the screen turns the mark into a name) and "Too late".
//
// state: { options, marks, grid, claims: { WORD: { mark, at } }, scores, hintsUsed, hints: [{ mark, word, row, col }],
//          endsAt (ms, 0 = no timer), over, winner, winners, reason ('all' | 'time') }

import { HUNT_LEVELS, makeHuntGrid, mulberry32 } from './wordhunt.js';
import { huntThemes } from './ladder.js';

export const LEVELS = Object.keys(HUNT_LEVELS);
export const TIMERS = [0, 3, 5, 8]; // minutes
export const HINT_COST = 2;
export const MIN_PLAYERS = 2;
export const MAX_PLAYERS = 4;

/** Anything missing or invalid becomes the default; backwards defaults to off on Easy and on from Medium. */
export function cleanOptions(o) {
  const level = LEVELS.includes(o?.level) ? o.level : 'easy';
  const theme = huntThemes[o?.theme] ? o.theme : 'Animals';
  const backwards = typeof o?.backwards === 'boolean' ? o.backwards : level !== 'easy';
  const timer = TIMERS.includes(Number(o?.timer)) ? Number(o.timer) : 0;
  const seed = Number.isInteger(o?.seed) ? o.seed | 0 : null;
  return { level, theme, backwards, timer, seed };
}

/** `random` makes the seed when options has none; `now` (ms) is when the round starts. */
export function start(random = Math.random, marks = ['h', 'g'], options = {}, now = 0) {
  if (marks.length < MIN_PLAYERS || marks.length > MAX_PLAYERS) throw new Error('Word Hunt is for 2 to 4 players.');
  const o = cleanOptions(options);
  if (o.seed === null) o.seed = Math.floor(random() * 0x7fffffff);
  return {
    options: o, marks: [...marks], grid: makeHuntGrid(o.theme, o.level, o.backwards, o.seed),
    claims: {}, scores: Object.fromEntries(marks.map((m) => [m, 0])), hintsUsed: Object.fromEntries(marks.map((m) => [m, 0])),
    hints: [], endsAt: o.timer ? now + o.timer * 60000 : 0, over: false, winner: null, winners: [], reason: null,
  };
}

const find = (state, word) => state.grid.words.find((w) => w.word === word);
const STEP = { right: [0, 1], down: [1, 0], left: [0, -1], up: [-1, 0] };

/** The two end cells of a placed word: [[row, col], [row, col]]. */
export function ends(w) {
  const [dr, dc] = STEP[w.dir];
  return [[w.row, w.col], [w.row + dr * (w.length - 1), w.col + dc * (w.length - 1)]];
}

function finish(state, reason) {
  state.over = true;
  state.reason = reason;
  const top = Math.max(...state.marks.map((m) => state.scores[m]));
  state.winners = state.marks.filter((m) => state.scores[m] === top);
  state.winner = state.winners.length === 1 ? state.winners[0] : null;
  return { over: true, winner: state.winner, winners: [...state.winners], reason };
}

const running = { over: false, winner: null, winners: [], reason: null };

function check(state, mark, now) {
  if (!state.marks.includes(mark)) throw new Error("You're not in this game.");
  if (!state.over && state.endsAt && now >= state.endsAt) finish(state, 'time');
  if (state.over) throw new Error('Too late');
}

/**
 * `mark` claims the word between two cells: { r1, c1, r2, c2 }, dragged from either end. Throws
 * "Not a word here", "Already found by <mark>" or "Too late". Returns { word, mark, score, over, winner, winners, reason }.
 */
export function claim(state, mark, move, now = 0) {
  check(state, mark, now);
  const [r1, c1, r2, c2] = [move?.r1, move?.c1, move?.r2, move?.c2];
  const w = [r1, c1, r2, c2].every(Number.isInteger)
    ? state.grid.words.find((x) => {
      const [[a, b], [c, d]] = ends(x);
      return (a === r1 && b === c1 && c === r2 && d === c2) || (a === r2 && b === c2 && c === r1 && d === c1);
    })
    : null;
  if (!w) throw new Error('Not a word here');
  if (state.claims[w.word]) throw new Error(`Already found by ${state.claims[w.word].mark}`);
  state.claims[w.word] = { mark, at: now };
  state.scores[mark] += w.length;
  const done = Object.keys(state.claims).length === state.grid.words.length;
  return { word: w.word, mark, score: state.scores[mark], ...(done ? finish(state, 'all') : running) };
}

/**
 * `mark` spends its one hint: the first letter's cell of one unfound word, chosen by the seed (words
 * nobody has been hinted about come first). Costs 2 points, never below 0. Only that player sees it.
 * Returns { mark, word?: no, row, col, score, hintsLeft }.
 */
export function hint(state, mark, now = 0) {
  check(state, mark, now);
  if (state.hintsUsed[mark] >= 1) throw new Error('No hints left');
  const unfound = state.grid.words.filter((w) => !state.claims[w.word]);
  const fresh = unfound.filter((w) => !state.hints.some((h) => h.word === w.word));
  const pool = fresh.length ? fresh : unfound;
  const w = pool[mulberry32((state.options.seed + state.hints.length * 40503) | 0).below(pool.length)];
  state.hintsUsed[mark] += 1;
  state.scores[mark] = Math.max(0, state.scores[mark] - HINT_COST);
  state.hints.push({ mark, word: w.word, row: w.row, col: w.col });
  return { mark, row: w.row, col: w.col, score: state.scores[mark], hintsLeft: 1 - state.hintsUsed[mark] };
}

/** One move from a player: a hint ({ hint: true }) or a claim. */
export function play(state, mark, move, now = 0) {
  return move?.hint ? hint(state, mark, now) : claim(state, mark, move, now);
}

/** Call when the clock reaches `endsAt`. Returns { ended, ...result }. */
export function tick(state, now) {
  if (state.over || !state.endsAt || now < state.endsAt) return { ended: false, over: state.over, winner: state.winner, winners: [...state.winners], reason: state.reason };
  return { ended: true, ...finish(state, 'time') };
}

/** Everyone's place once the round is over: [{ mark, score, found, rank }], best first (ties share a rank). */
export function results(state) {
  if (!state.over) return [];
  const found = (m) => Object.values(state.claims).filter((c) => c.mark === m).length;
  const order = state.marks.map((m, i) => ({ mark: m, score: state.scores[m], found: found(m), i }))
    .sort((a, b) => b.score - a.score || a.i - b.i);
  return order.map((r, k) => ({ mark: r.mark, score: r.score, found: r.found, rank: k > 0 && order[k - 1].score === r.score ? 0 : k + 1 }))
    .reduce((out, r) => { out.push({ ...r, rank: r.rank || out[out.length - 1].rank }); return out; }, []);
}

/**
 * The round as `mark` sees it. Unfound words are listed by text and length only (no cells); found
 * ones say who found them and where. `hints` holds only this player's own hint cells for words
 * still unfound. Marks are real marks ('h', 'g', 'o', ...); `me` is the viewer's.
 */
export function view(state, mark) {
  const { width, height, rows } = state.grid;
  return {
    me: mark, level: state.options.level, theme: state.options.theme, backwards: state.options.backwards,
    timer: state.options.timer, width, height, rows: [...rows],
    phase: state.over ? 'over' : 'playing',
    words: state.grid.words.map((w) => {
      const c = state.claims[w.word];
      return c
        ? { word: w.word, length: w.length, foundBy: c.mark, row: w.row, col: w.col, dir: w.dir }
        : { word: w.word, length: w.length, foundBy: null };
    }),
    marks: [...state.marks],
    scores: { ...state.scores },
    hintsLeft: Object.fromEntries(state.marks.map((m) => [m, 1 - state.hintsUsed[m]])),
    hints: state.hints.filter((h) => h.mark === mark && !state.claims[h.word]).map((h) => ({ row: h.row, col: h.col })),
    endsAt: state.endsAt,
    winner: state.over ? state.winner : null,
    winners: state.over ? [...state.winners] : [],
    reason: state.reason,
    results: results(state),
  };
}
