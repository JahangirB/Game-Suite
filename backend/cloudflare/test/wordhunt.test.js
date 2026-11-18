import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { HUNT_LEVELS, makeHuntGrid, mulberry32 } from '../src/wordhunt.js';
import { blocklist, huntThemes } from '../src/ladder.js';

const cases = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/wordhunt-cases.json', import.meta.url), 'utf8')).cases;
const STEP = { right: [0, 1], down: [1, 0], left: [0, -1], up: [-1, 0] };

test('mulberry32 gives the known numbers (the app test checks the same)', () => {
  const r = mulberry32(1);
  assert.deepEqual([r.next(), r.next(), r.next()], [2693262067, 11749833, 2265367787]);
  const neg = mulberry32(-1);
  assert.equal(neg.next(), mulberry32(4294967295).next());
});

for (const c of cases) {
  test(`word hunt shared case: ${c.theme} ${c.level} ${c.backwards ? 'backwards' : 'forwards'} seed ${c.seed}`, () => {
    const g = makeHuntGrid(c.theme, c.level, c.backwards, c.seed);
    assert.deepEqual(g.rows, c.rows);
    assert.deepEqual(g.words, c.words);
  });
}

const letterAt = (rows, r, c) => rows[r][c];

/** Every place `word` reads in the grid, as "row,col,dir" strings. */
function occurrences(rows, word, backwards) {
  const out = [];
  const dirs = Object.entries(STEP).filter(([name]) => backwards || name === 'right' || name === 'down');
  for (let r = 0; r < rows.length; r++) for (let c = 0; c < rows[0].length; c++) for (const [name, [dr, dc]] of dirs) {
    let k = 0;
    while (k < word.length) {
      const rr = r + dr * k, cc = c + dc * k;
      if (rr < 0 || rr >= rows.length || cc < 0 || cc >= rows[0].length || letterAt(rows, rr, cc) !== word[k]) break;
      k++;
    }
    if (k === word.length) out.push(`${r},${c},${name}`);
  }
  return out;
}

function check(g, level, backwards) {
  const { width, height, words: count } = HUNT_LEVELS[level];
  assert.equal(g.rows.length, height);
  for (const row of g.rows) assert.match(row, new RegExp(`^[A-Z]{${width}}$`));
  assert.equal(g.words.length, count);
  assert.equal(new Set(g.words.map((w) => w.word)).size, count);
  for (const w of g.words) {
    assert.ok(huntThemes[g.theme].includes(w.word));
    assert.equal(w.length, w.word.length);
    assert.ok(backwards || w.dir === 'right' || w.dir === 'down', `${w.word} ${w.dir}`);
    const [dr, dc] = STEP[w.dir];
    for (let k = 0; k < w.length; k++) assert.equal(letterAt(g.rows, w.row + dr * k, w.col + dc * k), w.word[k], `${w.word} letter ${k}`);
    // placed exactly once: its only occurrence in any allowed direction is the recorded one
    assert.deepEqual(occurrences(g.rows, w.word, backwards), [`${w.row},${w.col},${w.dir}`], w.word);
    // not hiding inside another chosen word
    for (const o of g.words) if (o !== w) {
      assert.ok(!o.word.includes(w.word), `${w.word} inside ${o.word}`);
      if (backwards) assert.ok(!o.word.includes([...w.word].reverse().join('')), `${w.word} reversed inside ${o.word}`);
    }
  }
  for (const b of blocklist) assert.deepEqual(occurrences(g.rows, b, backwards), [], `blocked ${b}`);
}

const themeNames = Object.keys(huntThemes);
for (const level of Object.keys(HUNT_LEVELS)) {
  for (const backwards of [false, true]) {
    test(`word hunt ${level} ${backwards ? 'backwards' : 'forwards'}: 500 seeds are valid and the same twice`, () => {
      let worst = 0, total = 0;
      for (let seed = 0; seed < 500; seed++) {
        const theme = themeNames[seed % themeNames.length];
        const t = performance.now();
        const g = makeHuntGrid(theme, level, backwards, seed);
        const ms = performance.now() - t;
        total += ms; worst = Math.max(worst, ms);
        check(g, level, backwards);
        if (seed % 50 === 0) assert.deepEqual(makeHuntGrid(theme, level, backwards, seed), g);
      }
      console.log(`word hunt ${level} ${backwards ? 'back' : 'fwd '}: mean ${(total / 500).toFixed(1)} ms, worst ${worst.toFixed(1)} ms per grid`);
      assert.ok(worst < 1000);
    });
  }
}

test('the sizes are columns by rows, portrait, no side under 10', () => {
  assert.deepEqual(Object.values(HUNT_LEVELS).map((l) => [l.width, l.height]), [[10, 10], [12, 12], [12, 16], [16, 20]]);
});
