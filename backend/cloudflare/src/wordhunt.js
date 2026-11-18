// Word Hunt grid maker: the same seed gives the same grid here and in the app's WordHunt.kt
// Every grid draws from one mixed pool: all the categories of hunt.txt joined, repeats dropped (ladder.js huntPool).
// (test/wordhunt.test.js and the app's WordHuntTest share app/src/test/resources/wordhunt-cases.json).
//
// makeHuntGrid(level, backwards, seed) -> { level, width, height, backwards, seed, rows, words }
//   width = columns, height = rows (portrait: Hard 12 across by 16 down, Huge 16 by 20).
//   rows: height strings of width letters A-Z. words: [{ word, row, col, dir, length }], sorted by word;
//   (row, col) is the first letter, 0-based from the top left; dir is 'right' | 'down' | 'left' | 'up'
//   (left and up only when backwards is on).
//
// Random numbers: mulberry32 on 32-bit integers (Math.imul and >>> 0 here; Int arithmetic in Kotlin),
// reduced with `% n`. No floating point is used, so both languages agree bit for bit.

import { blocklist, huntPool } from './ladder.js';

export const HUNT_LEVELS = {
  easy: { width: 10, height: 10, words: 10 },
  medium: { width: 12, height: 12, words: 14 },
  hard: { width: 12, height: 16, words: 18 },
  huge: { width: 16, height: 20, words: 24 },
};

const DIRS = [
  { name: 'right', dr: 0, dc: 1 },
  { name: 'down', dr: 1, dc: 0 },
  { name: 'left', dr: 0, dc: -1 },
  { name: 'up', dr: -1, dc: 0 },
];

// Fill letters are weighted towards common ones: A..Z.
const WEIGHTS = [8, 2, 3, 4, 12, 2, 2, 6, 7, 1, 1, 4, 2, 7, 8, 2, 1, 6, 6, 9, 3, 1, 2, 1, 2, 1];
const WEIGHT_TOTAL = WEIGHTS.reduce((a, b) => a + b, 0);

const ATTEMPTS = 200;
const FIX_ROUNDS = 1000;

export function mulberry32(seed) {
  let a = seed | 0;
  const next = () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return (t ^ (t >>> 14)) >>> 0;
  };
  return { next, below: (n) => next() % n };
}

const reversed = (w) => [...w].reverse().join('');

export function makeHuntGrid(level, backwards, seed) {
  const size = HUNT_LEVELS[level];
  const pool = huntPool;
  if (!size) throw new Error(`Unknown level ${level}`);
  backwards = !!backwards;
  seed |= 0;
  const { width, height } = size;
  const rng = mulberry32(seed);
  const dirs = backwards ? DIRS : DIRS.slice(0, 2);
  const blocked = [...blocklist].sort();

  // Choose the words: shuffled mixed pool, skipping any that hides inside another (either way round when
  // backwards), is a palindrome when backwards, or contains a blocklisted word.
  const shuffled = pool.slice();
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = rng.below(i + 1);
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  const inside = (a, b) => b.includes(a) || (backwards && b.includes(reversed(a)));
  const chosen = [];
  for (const w of shuffled) {
    if (chosen.length === size.words) break;
    if (backwards && w === reversed(w)) continue;
    if (blocked.some((b) => inside(b, w))) continue;
    if (chosen.some((x) => inside(x, w) || inside(w, x))) continue;
    // A word that holds another word of the vocabulary (JUMP in JUMPER) would show it, found and refused, inside itself.
    if (pool.some((p) => p !== w && p.length >= 4 && inside(p, w))) continue;
    chosen.push(w);
  }
  if (chosen.length < size.words) throw new Error(`Too few words for ${level}`);
  const order = chosen.slice().sort((a, b) => b.length - a.length || (a < b ? -1 : a > b ? 1 : 0));

  // All places `word` reads from, as [row, col, dir index].
  const find = (grid, word) => {
    const out = [];
    const first = word.charCodeAt(0);
    for (let r = 0; r < height; r++) {
      for (let c = 0; c < width; c++) {
        if (grid[r * width + c] !== first) continue;
        for (let d = 0; d < dirs.length; d++) {
          const { dr, dc } = dirs[d];
          let k = 1;
          let rr = r + dr;
          let cc = c + dc;
          while (k < word.length && rr >= 0 && rr < height && cc >= 0 && cc < width && grid[rr * width + cc] === word.charCodeAt(k)) {
            k++; rr += dr; cc += dc;
          }
          if (k === word.length) out.push([r, c, d]);
        }
      }
    }
    return out;
  };
  const cells = (word, [r, c, d]) => Array.from({ length: word.length }, (_, k) => (r + dirs[d].dr * k) * width + c + dirs[d].dc * k);

  for (let attempt = 0; attempt < ATTEMPTS; attempt++) {
    const grid = new Array(width * height).fill(0);
    const locked = new Array(width * height).fill(false);
    const placed = [];
    let ok = true;
    for (const word of order) {
      const fits = [];
      for (let d = 0; d < dirs.length; d++) {
        for (let r = 0; r < height; r++) {
          for (let c = 0; c < width; c++) {
            const er = r + dirs[d].dr * (word.length - 1);
            const ec = c + dirs[d].dc * (word.length - 1);
            if (er < 0 || er >= height || ec < 0 || ec >= width) continue;
            if (cells(word, [r, c, d]).every((cell, k) => grid[cell] === 0 || grid[cell] === word.charCodeAt(k))) fits.push([r, c, d]);
          }
        }
      }
      if (!fits.length) { ok = false; break; }
      const at = fits[rng.below(fits.length)];
      cells(word, at).forEach((cell, k) => { grid[cell] = word.charCodeAt(k); locked[cell] = true; });
      placed.push({ word, at });
    }
    if (!ok) continue;

    const letter = () => {
      let n = rng.below(WEIGHT_TOTAL);
      let i = 0;
      while (n >= WEIGHTS[i]) { n -= WEIGHTS[i]; i++; }
      return 65 + i;
    };
    for (let i = 0; i < grid.length; i++) if (!locked[i]) grid[i] = letter();

    // Re-roll fill letters until no chosen word shows a second time and no blocklisted or other pool word shows.
    const chosenSet = new Set(order);
    const problems = () => {
      const out = [];
      for (const { word, at } of placed) {
        for (const f of find(grid, word)) if (f[0] !== at[0] || f[1] !== at[1] || f[2] !== at[2]) out.push(cells(word, f));
      }
      for (const b of blocked) for (const f of find(grid, b)) out.push(cells(b, f));
      // With no list on screen, a word of 4 or more letters that the fill letters spell by chance would be found and refused, so
      // those are re-rolled too. (One wholly inside placed letters, a crossing, cannot be: the attempt fails and starts again.)
      for (const w of pool) {
        if (w.length < 4 || chosenSet.has(w)) continue;
        for (const f of find(grid, w)) out.push(cells(w, f));
      }
      return out;
    };
    let clean = false;
    for (let round = 0; round < FIX_ROUNDS && ok; round++) {
      const bad = problems();
      if (!bad.length) { clean = true; break; }
      for (const cs of bad) {
        const free = cs.filter((cell) => !locked[cell]);
        if (!free.length) { ok = false; break; }
        grid[free[rng.below(free.length)]] = letter();
      }
    }
    if (!ok || !clean) continue;

    const rows = Array.from({ length: height }, (_, r) => String.fromCharCode(...grid.slice(r * width, (r + 1) * width)));
    const words = placed
      .map(({ word, at }) => ({ word, row: at[0], col: at[1], dir: dirs[at[2]].name, length: word.length }))
      .sort((a, b) => (a.word < b.word ? -1 : a.word > b.word ? 1 : 0));
    return { level, width, height, backwards, seed, rows, words };
  }
  throw new Error('No grid found');
}
