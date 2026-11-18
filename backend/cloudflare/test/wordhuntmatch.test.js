import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { claim, cleanOptions, ends, hint, play, start, tick, view } from '../src/wordhuntmatch.js';

const cases = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/wordhuntmatch-cases.json', import.meta.url), 'utf8')).cases;
const STEP = { right: [0, 1], down: [1, 0], left: [0, -1], up: [-1, 0] };

/** The selection a step describes: a placed word's cells (first to last letter) with the step's changes. */
function selection(state, s) {
  if (s.raw) return s.raw;
  const w = state.grid.words.find((x) => x.word === s.word);
  const [dr, dc] = STEP[w.dir];
  let [[r1, c1], [r2, c2]] = ends(w);
  if (s.trim) { r2 -= dr * s.trim; c2 -= dc * s.trim; }
  if (s.endDelta) { r2 += s.endDelta[0]; c2 += s.endDelta[1]; }
  if (s.diagonal) { r2 = r1 + w.length - 1; c2 = c1 + w.length - 1; }
  return s.reverse ? { r1: r2, c1: c2, r2: r1, c2: c1 } : { r1, c1, r2, c2 };
}

function expectKeys(got, want, at) {
  for (const [k, v] of Object.entries(want)) assert.deepEqual(got[k], v, `${at}: ${k}`);
}

const VIEW_KEYS = ['me', 'level', 'backwards', 'timer', 'width', 'height', 'rows', 'phase', 'total', 'foundCount', 'found', 'marks', 'scores', 'hintsLeft', 'hints', 'endsAt', 'winner', 'winners', 'reason', 'results'].sort();
const FOUND_KEYS = ['at', 'dir', 'col', 'foundBy', 'length', 'row', 'word'].sort();

/**
 * The words are hidden: no view carries the text or length of a word nobody has found. The grid's own
 * letters (`rows`) are the only place the letters appear, so the check is on everything else.
 */
export function assertNoUnfoundText(state, v, at) {
  assert.deepEqual(Object.keys(v).sort(), VIEW_KEYS, `${at}: the view's keys`);
  const foundWords = new Set(v.found.map((f) => f.word));
  const { rows, ...rest } = v;
  const text = JSON.stringify(rest);
  for (const w of state.grid.words) {
    if (foundWords.has(w.word)) continue;
    assert.ok(!text.includes(w.word), `${at}: ${w.word} leaks`);
    assert.ok(!text.includes([...w.word].reverse().join('')), `${at}: ${w.word} reversed leaks`);
  }
  assert.equal(v.total, state.grid.words.length, `${at}: total`);
  assert.equal(v.foundCount, v.found.length, `${at}: foundCount`);
  for (const f of v.found) assert.deepEqual(Object.keys(f).sort(), FOUND_KEYS, `${at}: found entry`);
  for (const h of v.hints) assert.deepEqual(Object.keys(h).sort(), ['col', 'row'], `${at}: hint`);
  assert.equal('theme' in v, false, `${at}: no theme`);
}

for (const c of cases) {
  test(`word hunt rules: ${c.name}`, () => {
    const state = start(() => 0.5, c.marks, c.options, c.now);
    c.steps.forEach((s, n) => {
      const at = `step ${n + 1} (${s.do})`;
      if (s.do === 'view') {
        const v = view(state, s.mark);
        assertNoUnfoundText(state, v, at);
        const { found, ...rest } = s.expect;
        if (found) assert.deepEqual(v.found.map((f) => ({ word: f.word, foundBy: f.foundBy, at: f.at })), found, `${at}: found`);
        return expectKeys(v, rest, at);
      }
      if (s.do === 'tick') {
        const r = tick(state, s.now);
        return expectKeys(r, Object.fromEntries(Object.entries(s).filter(([k]) => !['do', 'now'].includes(k))), at);
      }
      const run = () => (s.do === 'hint' ? hint(state, s.mark, s.now ?? 0) : claim(state, s.mark, selection(state, s), s.now ?? 0));
      if (s.error) return assert.throws(run, { message: s.error }, at);
      expectKeys(run(), s.expect, at);
    });
    for (const m of c.marks) assertNoUnfoundText(state, view(state, m), 'end');
  });
}

test('word hunt rules: the hint is the first letter of an unfound word, from the seed', () => {
  const state = start(() => 0.5, ['h', 'g'], { level: 'hard', seed: 11 });
  const claimed = state.grid.words[0];
  const [[a, b], [c, d]] = ends(claimed);
  claim(state, 'g', { r1: c, c1: d, r2: a, c2: b });
  const h = hint(state, 'h');
  const w = state.grid.words.find((x) => x.row === h.row && x.col === h.col);
  assert.ok(w && w.word !== claimed.word);
  assert.deepEqual(view(state, 'h').hints, [{ row: h.row, col: h.col }]);
  assert.deepEqual(view(state, 'g').hints, []);
  const again = start(() => 0.5, ['h', 'g'], { level: 'hard', seed: 11 });
  claim(again, 'g', { r1: c, c1: d, r2: a, c2: b });
  assert.deepEqual(hint(again, 'h'), h);
});

test('word hunt rules: moves go through play, options and players are checked', () => {
  assert.deepEqual(cleanOptions({}), { level: 'easy', backwards: false, timer: 0 });
  assert.equal(cleanOptions({ level: 'medium' }).backwards, true);
  assert.equal(cleanOptions({ level: 'huge', backwards: false }).backwards, false);
  assert.deepEqual(cleanOptions({ level: 'x', timer: 4, seed: 2.5 }), { level: 'easy', backwards: false, timer: 0 });
  assert.equal(cleanOptions({ timer: 8 }).timer, 8);
  assert.throws(() => start(Math.random, []), /1 to 4/);
  assert.equal(start(Math.random, ['h']).marks.length, 1);
  assert.throws(() => claim(start(() => 0.5, ['h', 'g'], { seed: 3 }, 1000), 'h', { r1: 0, c1: 0, r2: 0, c2: 2 }, 999), { message: 'Wait for the countdown.' });
  assert.throws(() => start(Math.random, ['h', 'g', 'o', 'p', 'q']), /1 to 4/);
  const state = start(() => 0.25, ['h', 'g'], { seed: 3 });
  assert.equal(state.seed, 3);
  assert.equal(cleanOptions({ seed: 5 }).seed, undefined);
  assert.equal(start(() => 0.25, ['h', 'g']).seed, Math.floor(0.25 * 0x7fffffff));
  assert.equal(play(state, 'h', { hint: true }).hintsLeft, 0);
  const w = state.grid.words[0];
  const [[a, b], [c, d]] = ends(w);
  assert.equal(play(state, 'g', { r1: a, c1: b, r2: c, c2: d }).word, w.word);
});

test('word hunt rules: two claims of the same word, the first processed wins', () => {
  const state = start(() => 0.5, ['h', 'g', 'o'], { seed: 3 });
  const w = state.grid.words[0];
  const [[a, b], [c, d]] = ends(w);
  assert.equal(claim(state, 'g', { r1: a, c1: b, r2: c, c2: d }, 5).mark, 'g');
  assert.throws(() => claim(state, 'h', { r1: c, c1: d, r2: a, c2: b }, 5), { message: 'Already found by g' });
  assert.throws(() => claim(state, 'o', { r1: a, c1: b, r2: c, c2: d }, 6), { message: 'Already found by g' });
  assert.equal(state.scores.g, w.length);
  assert.equal(state.scores.h + state.scores.o, 0);
});

test('word hunt rules: an older app sends a theme, it is accepted and ignored', () => {
  for (const theme of ['Animals', 'Food', 'Nope', 42, null]) {
    assert.deepEqual(cleanOptions({ level: 'medium', theme, timer: 5 }), { level: 'medium', backwards: true, timer: 5 });
  }
  // the same seed makes the same grid whatever theme was asked for
  const plain = start(() => 0.5, ['h', 'g'], { level: 'easy', seed: 9 });
  const old = start(() => 0.5, ['h', 'g'], { level: 'easy', theme: 'Sport', seed: 9 });
  assert.deepEqual(old.grid, plain.grid);
  assert.equal('theme' in old.options, false);
  assert.equal('theme' in view(old, 'h'), false);
});

test('word hunt rules: no view ever carries an unfound word, whoever looks and whenever', () => {
  const state = start(() => 0.5, ['h', 'g', 'o', 'p'], { level: 'medium', seed: 21, timer: 5 }, 0);
  const check = (at) => { for (const m of state.marks) assertNoUnfoundText(state, view(state, m), `${at} (${m})`); assertNoUnfoundText(state, view(state, 'z'), `${at} (a stranger)`); };
  check('start');
  hint(state, 'h', 1);
  hint(state, 'g', 1);
  check('after hints');
  state.grid.words.slice(0, state.grid.words.length - 1).forEach((w, i) => {
    const [[a, b], [c, d]] = ends(w);
    claim(state, state.marks[i % 4], { r1: a, c1: b, r2: c, c2: d }, 10 + i);
    check(`after ${i + 1} claims`);
  });
  tick(state, 300000);
  check('over');
});

test('word hunt rules: the view counts, and lists found words in the order they were found', () => {
  const state = start(() => 0.5, ['h', 'g'], { level: 'easy', seed: 3 });
  assert.deepEqual(view(state, 'h').found, []);
  assert.equal(view(state, 'h').total, 10);
  const [x, y] = state.grid.words;
  const click = (w, mark, now) => { const [[a, b], [c, d]] = ends(w); claim(state, mark, { r1: a, c1: b, r2: c, c2: d }, now); };
  click(y, 'g', 50);
  click(x, 'h', 20);
  const v = view(state, 'h');
  assert.equal(v.foundCount, 2);
  assert.deepEqual(v.found.map((f) => [f.word, f.foundBy, f.at]), [[y.word, 'g', 50], [x.word, 'h', 20]]);
  assert.deepEqual(v.found[0], { word: y.word, length: y.length, foundBy: 'g', row: y.row, col: y.col, dir: y.dir, at: 50 });
});
