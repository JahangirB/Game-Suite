// Letter Grid: trace words through a 4 × 4 grid of letters before time runs out.
//
// The host's phone rolls the grid (the 16 letters, row by row) and sends it with its Ready.
// A word is three or more letters on squares that touch (sideways or corner to corner), each
// square used once, and must be in the word list. Longer words score more; at the end a word
// nobody else found scores double. Highest score wins.

import { GRID_WORDS } from './gridwords.js';

export const SIZE = 4;
export const ROUND_MS = 120_000;
const WORDS = new Set(GRID_WORDS.split(' '));

export const isWord = (word) => WORDS.has(word);

export function checkLetters(letters) {
  const s = String(letters || '').toLowerCase();
  if (!new RegExp(`^[a-z]{${SIZE * SIZE}}$`).test(s)) throw new Error('The grid is not valid.');
  return s;
}

/** Whether [word] can be traced on [letters] through touching squares, each used once. */
export function traceable(letters, word) {
  const used = new Array(SIZE * SIZE).fill(false);
  const from = (i, k) => {
    if (letters[i] !== word[k]) return false;
    if (k === word.length - 1) return true;
    used[i] = true;
    const r = Math.floor(i / SIZE);
    const c = i % SIZE;
    for (let dr = -1; dr <= 1; dr++) {
      for (let dc = -1; dc <= 1; dc++) {
        const nr = r + dr;
        const nc = c + dc;
        const j = nr * SIZE + nc;
        if ((dr || dc) && nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && !used[j] && from(j, k + 1)) {
          used[i] = false;
          return true;
        }
      }
    }
    used[i] = false;
    return false;
  };
  for (let i = 0; i < SIZE * SIZE; i++) if (from(i, 0)) return true;
  return false;
}

/** A word's points by length: 3-4 letters 1, 5 → 2, 6 → 3, 7 → 5, 8 → 11. */
export function points(word) {
  const n = word.length;
  return n <= 4 ? 1 : n === 5 ? 2 : n === 6 ? 3 : n === 7 ? 5 : 11;
}

/** Checks a word for the grid; returns it in lower case, or throws why it doesn't count. */
export function checkWord(letters, word) {
  const w = String(word || '').toLowerCase();
  if (!/^[a-z]{3,16}$/.test(w)) throw new Error('Words need three letters or more.');
  if (!traceable(letters, w)) throw new Error("That word isn't on the grid.");
  if (!isWord(w)) throw new Error(`"${w.toUpperCase()}" isn't in the word list.`);
  return w;
}

/** Scores every player's [found] words: unique words double. Returns { role: { score, words } }. */
export function score(found) {
  const seen = {};
  for (const words of Object.values(found)) for (const w of words) seen[w] = (seen[w] || 0) + 1;
  return Object.fromEntries(Object.entries(found).map(([role, words]) => {
    const list = words.map((w) => ({ word: w, unique: seen[w] === 1, points: points(w) * (seen[w] === 1 ? 2 : 1) }));
    return [role, { score: list.reduce((s, x) => s + x.points, 0), words: list }];
  }));
}
