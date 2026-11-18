import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ALLOWED, ANSWERS } from '../src/wordlists.js';
import { GRID_WORDS } from '../src/gridwords.js';
import { BLOCKLIST, HUNT_THEMES, LADDER } from '../src/wordgamelists.js';
import { blocklist, distance, huntThemes, isLadderWord, ladderWords, neighbours, pathExists } from '../src/ladder.js';

const asset = (name) => readFileSync(new URL(`../../../app/src/main/assets/words/${name}.txt`, import.meta.url), 'utf8').trim().split('\n');
const valid = new Set([...ALLOWED.split(' '), ...ANSWERS.split(' '), ...GRID_WORDS.split(' ')].map((w) => w.toUpperCase()));

test('the server lists match the app assets', () => {
  assert.equal(LADDER, asset('ladder').join(' '));
  assert.equal(BLOCKLIST, asset('blocklist').join(' '));
  const themes = Object.entries(HUNT_THEMES).map(([name, list]) => `${name}|${list}`);
  assert.deepEqual(themes, asset('hunt'));
});

test('ladder words are real, 4 to 6 letters, A-Z and not blocked', () => {
  for (const w of ladderWords) {
    assert.match(w, /^[A-Z]{4,6}$/, w);
    assert.ok(valid.has(w), `${w} is not a real word`);
    assert.ok(!blocklist.has(w), `${w} is blocked`);
  }
  assert.equal(new Set(ladderWords).size, ladderWords.length);
});

function groups(length) {
  const list = ladderWords.filter((w) => w.length === length);
  const seen = new Set();
  let largest = 0;
  for (const w of list) {
    if (seen.has(w)) continue;
    const queue = [w];
    seen.add(w);
    for (let i = 0; i < queue.length; i++) for (const n of neighbours(queue[i])) if (!seen.has(n)) { seen.add(n); queue.push(n); }
    largest = Math.max(largest, queue.length);
  }
  return { total: list.length, withNeighbour: list.filter((w) => neighbours(w).length > 0).length, largest };
}

test('enough ladder words are joined to others', () => {
  const min = { 4: [600, 500], 5: [1400, 700], 6: [3000, 1500] }; // [with a neighbour, largest group]
  for (const length of [4, 5, 6]) {
    const g = groups(length);
    console.log(`ladder ${length}: ${g.total} words, ${g.withNeighbour} with a neighbour, largest group ${g.largest}`);
    assert.ok(g.withNeighbour >= min[length][0], `${length}: ${g.withNeighbour}`);
    assert.ok(g.largest >= min[length][1], `${length}: ${g.largest}`);
  }
});

test('neighbours differ by one letter and agree both ways', () => {
  assert.ok(neighbours('COLD').includes('GOLD'));
  assert.ok(neighbours('COLD').includes('BOLD'));
  assert.ok(!neighbours('COLD').includes('COLD'));
  assert.ok(!neighbours('COLD').includes('WARM'));
  assert.deepEqual(neighbours('ZZZZ'), []);
  for (const w of ladderWords.slice(0, 300)) for (const n of neighbours(w)) assert.ok(neighbours(n).includes(w));
});

test('paths and distances', () => {
  assert.ok(isLadderWord('COLD') && isLadderWord('WARM'));
  assert.equal(distance('COLD', 'COLD'), 0);
  assert.equal(distance('COLD', 'GOLD'), 1);
  assert.equal(distance('COLD', 'WARM'), 6);
  assert.equal(distance('WARM', 'COLD'), 6);
  assert.ok(pathExists('COLD', 'WARM'));
  assert.equal(distance('COLD', 'COLDER'), -1); // different lengths
  assert.equal(distance('COLD', 'ZZZZ'), -1); // not a ladder word
  assert.equal(pathExists('COLD', 'ZZZZ'), false);
});

test('word hunt themes: 6 or more, 40 or more real, clean words of 3 to 8 letters', () => {
  const names = Object.keys(huntThemes);
  for (const name of ['Animals', 'Food', 'Sport', 'Nature', 'Home', 'Mixed']) assert.ok(names.includes(name), name);
  for (const [name, list] of Object.entries(huntThemes)) {
    assert.ok(list.length >= 40, `${name} has ${list.length}`);
    assert.equal(new Set(list).size, list.length, `${name} repeats a word`);
    for (const w of list) {
      assert.match(w, /^[A-Z]{3,8}$/, w);
      assert.ok(valid.has(w), `${name}: ${w} is not a real word`);
      assert.ok(!blocklist.has(w), `${name}: ${w} is blocked`);
    }
    console.log(`theme ${name}: ${list.length}`);
  }
});
