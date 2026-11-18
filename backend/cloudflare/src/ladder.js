// Word Ladder dictionary: the common 4, 5 and 6 letter words (wordgamelists.js), UPPERCASE, and
// the words one letter away from each. Neighbours are worked out when first asked for (about
// 4 000 words, a few milliseconds), not stored. Same rules as the app's WordLadder.kt.

import { BLOCKLIST, HUNT_THEMES, LADDER } from './wordgamelists.js';

export const LADDER_LENGTHS = [4, 5, 6];
export const ladderWords = LADDER.split(' ');
export const blocklist = new Set(BLOCKLIST.split(' '));
export const huntThemes = Object.fromEntries(Object.entries(HUNT_THEMES).map(([name, list]) => [name, list.split(' ')]));

const words = new Set(ladderWords);
const buckets = new Map(); // "C_LD" -> the words that fit it

for (const w of ladderWords) {
  for (let i = 0; i < w.length; i++) {
    const key = w.slice(0, i) + '_' + w.slice(i + 1);
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key).push(w);
  }
}

export function isLadderWord(word) {
  return words.has(word);
}

/** Ladder words that differ from `word` in exactly one letter (none for a word not in the list). */
export function neighbours(word) {
  const out = [];
  for (let i = 0; i < word.length; i++) {
    for (const w of buckets.get(word.slice(0, i) + '_' + word.slice(i + 1)) || []) if (w !== word) out.push(w);
  }
  return out;
}

/** The fewest one-letter changes from `from` to `to` (0 if the same), or -1 if there is no way. */
export function distance(from, to) {
  if (!words.has(from) || !words.has(to) || from.length !== to.length) return -1;
  if (from === to) return 0;
  const seen = new Map([[from, 0]]);
  const queue = [from];
  for (let head = 0; head < queue.length; head++) {
    const w = queue[head];
    for (const n of neighbours(w)) {
      if (seen.has(n)) continue;
      seen.set(n, seen.get(w) + 1);
      if (n === to) return seen.get(n);
      queue.push(n);
    }
  }
  return -1;
}

export function pathExists(from, to) {
  return distance(from, to) >= 0;
}
