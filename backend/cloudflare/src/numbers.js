// Number Cross: crosswords of sums such as 4 + 5 = 9, crossing at shared numbers.
//
// The host's phone builds every puzzle (nothing is stored ahead of time) and sends it with its
// Ready: the solved grid and which number cells are blank. The server checks the grid, hides the
// blanks, and gives everyone the same bank of missing numbers to place. A board is solved when
// every blank is filled from the bank and every sum holds, so another arrangement that works counts.
//
// A grid is w × h tokens, row by row: '' (empty), a whole number, or one of + - * / =.
// Each run of tokens across or down is one sum: number, operator, number, '=', number.

export const LEVELS = ['easy', 'medium', 'hard'];
const OPS = ['+', '-', '*', '/'];
const MIN_SIZE = 5;
const MAX_SIZE = 13;

export function cleanOptions(options) {
  const level = String(options?.level || '').toLowerCase();
  return { level: LEVELS.includes(level) ? level : 'easy' };
}

const isNumber = (t) => /^[0-9]{1,3}$/.test(t);

/** Whether a sum [a, op, b, '=', c] (as tokens) holds. */
export function holds([a, op, b, eq, c]) {
  if (eq !== '=' || ![a, b, c].every(isNumber)) return false;
  const [x, y, z] = [Number(a), Number(b), Number(c)];
  if (op === '+') return x + y === z;
  if (op === '-') return x - y === z;
  if (op === '*') return x * y === z;
  if (op === '/') return y !== 0 && x === y * z;
  return false;
}

/** The sums in a grid, each as its five cell indexes. Throws if a run isn't shaped like a sum. */
export function sums(grid, w, h) {
  const runs = [];
  const scan = (starts, step, length) => {
    for (const start of starts) {
      let run = [];
      for (let k = 0; k <= length; k++) {
        const i = start + k * step;
        if (k < length && grid[i] !== '') run.push(i);
        else {
          if (run.length > 1) runs.push(run);
          run = [];
        }
      }
    }
  };
  scan([...Array(h).keys()].map((r) => r * w), 1, w);
  scan([...Array(w).keys()], w, h);
  for (const run of runs) {
    const t = run.map((i) => grid[i]);
    if (run.length !== 5 || !isNumber(t[0]) || !OPS.includes(t[1]) || !isNumber(t[2]) || t[3] !== '=' || !isNumber(t[4])) {
      throw new Error('Every line in the grid must be one sum.');
    }
  }
  return runs;
}

/** Checks a puzzle from the host's phone; returns it cleaned, or throws. */
export function checkPuzzle(puzzle) {
  const w = Number(puzzle?.w);
  const h = Number(puzzle?.h);
  if (!Number.isInteger(w) || !Number.isInteger(h) || w < MIN_SIZE || h < MIN_SIZE || w > MAX_SIZE || h > MAX_SIZE) {
    throw new Error('The puzzle is the wrong size.');
  }
  const grid = Array.isArray(puzzle.grid) ? puzzle.grid.map((t) => String(t ?? '')) : [];
  if (grid.length !== w * h || grid.some((t) => t !== '' && t !== '=' && !OPS.includes(t) && !isNumber(t))) {
    throw new Error('The puzzle grid is not valid.');
  }
  const list = sums(grid, w, h);
  if (!list.length || !list.every((s) => holds(s.map((i) => grid[i])))) throw new Error('A sum in the puzzle is wrong.');
  // Every filled cell must belong to a sum.
  const used = new Set(list.flat());
  if (grid.some((t, i) => t !== '' && !used.has(i))) throw new Error('The puzzle grid is not valid.');
  const blanks = [...new Set((Array.isArray(puzzle.blanks) ? puzzle.blanks : []).map(Number))].sort((a, b) => a - b);
  if (!blanks.length || blanks.some((i) => !Number.isInteger(i) || !isNumber(grid[i] ?? ''))) {
    throw new Error('The puzzle blanks are not valid.');
  }
  return { w, h, grid, blanks };
}

/** The missing numbers, smallest first: everyone gets the same bank. */
export const bankOf = (p) => p.blanks.map((i) => Number(p.grid[i])).sort((a, b) => a - b);

/** The puzzle as players see it: '?' in each blank, and the bank of numbers to place. */
export function puzzleView(p) {
  const blank = new Set(p.blanks);
  return { w: p.w, h: p.h, cells: p.grid.map((t, i) => (blank.has(i) ? '?' : t)), blanks: p.blanks, bank: bankOf(p) };
}

/** A player's entries, one per blank in order ('' when empty); throws if malformed. */
export function cleanEntries(p, entries) {
  if (!Array.isArray(entries) || entries.length !== p.blanks.length) throw new Error('Invalid board.');
  return entries.map((e) => {
    const s = String(e ?? '');
    if (s !== '' && !isNumber(s)) throw new Error('Invalid board.');
    return s;
  });
}

/** The grid with a player's [entries] in the blanks. */
function filled(p, entries) {
  const grid = p.grid.slice();
  p.blanks.forEach((i, k) => { grid[i] = entries[k] || ''; });
  return grid;
}

/** Percentage of the sums with a blank that hold with [entries]. */
export function progressOf(p, entries) {
  const grid = filled(p, entries);
  const blank = new Set(p.blanks);
  const open = sums(p.grid, p.w, p.h).filter((s) => s.some((i) => blank.has(i)));
  if (!open.length) return 100;
  const right = open.filter((s) => holds(s.map((i) => grid[i]))).length;
  return Math.floor((right * 100) / open.length);
}

/** Solved: every blank filled from the bank (each number used as often as it appears) and every sum holds. */
export function isSolved(p, entries) {
  if (entries.some((e) => e === '')) return false;
  const used = entries.map(Number).sort((a, b) => a - b);
  const bank = bankOf(p);
  if (used.some((n, k) => n !== bank[k])) return false;
  const grid = filled(p, entries);
  return sums(p.grid, p.w, p.h).every((s) => holds(s.map((i) => grid[i])));
}
