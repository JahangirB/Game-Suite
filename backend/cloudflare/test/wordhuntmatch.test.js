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

/** No view may carry the place of a word nobody has found. */
function assertNoLeak(state, v, at) {
  for (const w of v.words) {
    if (w.foundBy === null) assert.deepEqual(Object.keys(w).sort(), ['foundBy', 'length', 'word'], `${at}: ${w.word} leaks`);
  }
}

for (const c of cases) {
  test(`word hunt rules: ${c.name}`, () => {
    const state = start(() => 0.5, c.marks, c.options, c.now);
    c.steps.forEach((s, n) => {
      const at = `step ${n + 1} (${s.do})`;
      if (s.do === 'view') {
        const v = view(state, s.mark);
        assertNoLeak(state, v, at);
        const { foundBy, ...rest } = s.expect;
        if (foundBy) assert.deepEqual(Object.fromEntries(v.words.map((w) => [w.word, w.foundBy])), foundBy, `${at}: foundBy`);
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
    for (const m of c.marks) assertNoLeak(state, view(state, m), 'end');
  });
}

test('word hunt rules: the hint is the first letter of an unfound word, from the seed', () => {
  const state = start(() => 0.5, ['h', 'g'], { level: 'hard', theme: 'Mixed', seed: 11 });
  const claimed = state.grid.words[0];
  const [[a, b], [c, d]] = ends(claimed);
  claim(state, 'g', { r1: c, c1: d, r2: a, c2: b });
  const h = hint(state, 'h');
  const w = state.grid.words.find((x) => x.row === h.row && x.col === h.col);
  assert.ok(w && w.word !== claimed.word);
  assert.deepEqual(view(state, 'h').hints, [{ row: h.row, col: h.col }]);
  assert.deepEqual(view(state, 'g').hints, []);
  const again = start(() => 0.5, ['h', 'g'], { level: 'hard', theme: 'Mixed', seed: 11 });
  claim(again, 'g', { r1: c, c1: d, r2: a, c2: b });
  assert.deepEqual(hint(again, 'h'), h);
});

test('word hunt rules: moves go through play, options and players are checked', () => {
  assert.deepEqual(cleanOptions({}), { level: 'easy', theme: 'Animals', backwards: false, timer: 0 });
  assert.equal(cleanOptions({ level: 'medium' }).backwards, true);
  assert.equal(cleanOptions({ level: 'huge', backwards: false }).backwards, false);
  assert.deepEqual(cleanOptions({ level: 'x', theme: 'Nope', timer: 4, seed: 2.5 }), { level: 'easy', theme: 'Animals', backwards: false, timer: 0 });
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
