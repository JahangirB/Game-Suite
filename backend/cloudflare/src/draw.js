// Draw & Guess: one player draws a secret word while the other watches and guesses. You play
// as a team: a right guess scores the seconds left on the clock. Four turns, taking turns to draw.
// Strokes are short polylines in 0-1 coordinates so both screens scale them to their own size.

import { DRAW } from './wordlists.js';

export const TURNS = 4;
/** Turns in a match: everyone draws twice (at least four turns). */
export const turnsFor = (players) => Math.max(TURNS, players * 2);
export const TURN_MS = 80_000;
/** Pen colours: 0-4 ink, red, blue, green, orange; 5 the eraser (white); 6-8 yellow, purple, pink. */
export const COLORS = 9;
/** Brush sizes: thin, thick and huge. */
export const WIDTHS = [1, 2, 3];
/** The guess race (three or four of you): points for the first, second and third right guess. */
export const RACE_POINTS = [80, 60, 40];
/** The drawer's points for each right guess in the race. */
export const DRAWER_POINTS = 20;

/** Team (two take turns, one score) or race (everyone else guesses at once, each their own score). */
export const cleanOptions = (o) => ({ mode: o?.mode === 'race' ? 'race' : 'team' });
const MAX_POINTS = 120;
const MAX_STROKES = 2000;
export const words = DRAW.split(',');

/** A word not used yet this match. */
export function pickWord(used, random = Math.random) {
  const fresh = words.filter((w) => !used.includes(w));
  const pool = fresh.length ? fresh : words;
  return pool[Math.floor(random() * pool.length)];
}

/** The word as the guesser sees it: one underscore per letter. */
export function hint(word) {
  return word.replace(/[a-z]/g, '_');
}

export function isRight(guess, word) {
  return String(guess || '').toLowerCase().replace(/[^a-z]/g, '') === word;
}

/** Checks a stroke from the app; throws a plain Error the caller reports. */
export function cleanStroke(stroke, count) {
  if (count >= MAX_STROKES) throw new Error('The drawing is full. Clear it to keep drawing.');
  const points = Array.isArray(stroke?.points) ? stroke.points : [];
  if (points.length < 1 || points.length > MAX_POINTS || (stroke.fill && points.length !== 1)) throw new Error('Bad stroke.');
  const clean = points.map((pt) => {
    const [x, y] = Array.isArray(pt) ? pt.map(Number) : [NaN, NaN];
    if (!(x >= 0 && x <= 1 && y >= 0 && y <= 1)) throw new Error('Bad stroke.');
    return [Math.round(x * 1000) / 1000, Math.round(y * 1000) / 1000];
  });
  const color = Number(stroke.color);
  const width = Number(stroke.width);
  const out = { points: clean, color: Number.isInteger(color) && color >= 0 && color < COLORS ? color : 0, width: WIDTHS.includes(width) ? width : 1 };
  // A fill pours the colour into the closed shape round its one point; a start begins a new line
  // (undo takes back a whole line, which arrives in short pieces).
  if (stroke.fill) out.fill = true;
  if (stroke.start) out.start = true;
  return out;
}
