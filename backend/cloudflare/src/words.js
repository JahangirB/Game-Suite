// Word Race: everyone races to find the same secret word in six tries. Each guess colours its
// letters: 'g' right letter in the right place, 'y' in the word but elsewhere, '.' not in the
// word. Players see each other's colours, never the letters. Guesses must be real words.
//
// The host picks the level, which sets the word's length: easy 4 letters, medium 5, hard 6,
// expert 7 or 8 (a random one of the two each round).

import { ALLOWED, ANSWERS } from './wordlists.js';

export const TRIES = 6;
export const LEVELS = { easy: [4], medium: [5], hard: [6], expert: [7, 8] };

export const answers = ANSWERS.split(' ');
const byLength = {};
for (const w of answers) (byLength[w.length] ||= []).push(w);
const allowed = new Set([...ALLOWED.split(' '), ...answers]);

export function cleanOptions(options) {
  const level = String(options?.level || '').toLowerCase();
  return { level: LEVELS[level] ? level : 'medium' };
}

/** A secret word for [level]: its length, then a word of that length. */
export function randomAnswer(random = Math.random, level = 'medium') {
  const lengths = LEVELS[level] || LEVELS.medium;
  const list = byLength[lengths[Math.floor(random() * lengths.length)]];
  return list[Math.floor(random() * list.length)];
}

export function isWord(word) {
  return allowed.has(word);
}

/** Colours for `guess` against `answer`. A repeated letter only counts as often as the answer has it. */
export function score(guess, answer) {
  const n = answer.length;
  const result = Array(n).fill('.');
  const left = {};
  for (let i = 0; i < n; i++) {
    if (guess[i] === answer[i]) result[i] = 'g';
    else left[answer[i]] = (left[answer[i]] || 0) + 1;
  }
  for (let i = 0; i < n; i++) {
    if (result[i] === '.' && left[guess[i]] > 0) {
      result[i] = 'y';
      left[guess[i]]--;
    }
  }
  return result.join('');
}
