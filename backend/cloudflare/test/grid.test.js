import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as grid from '../src/grid.js';
import { GRID_WORDS } from '../src/gridwords.js';
import { COUNTDOWN_MS, checkTimers, createRoom, findWord, joinRoom, setConnected, setGridReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
// c a t s
// o r e d
// x x x x
// x x x x
const LETTERS = 'catsoredxxxxxxxx';

test("the server's word list matches the app's", () => {
  const app = fs.readFileSync(new URL('../../../app/src/main/assets/words/grid.txt', import.meta.url), 'utf8').trim().split('\n');
  assert.deepEqual(GRID_WORDS.split(' '), app);
});

test('words are traced through touching squares, each used once', () => {
  assert.ok(grid.traceable(LETTERS, 'cat'));
  assert.ok(grid.traceable(LETTERS, 'cats'));
  assert.ok(grid.traceable(LETTERS, 'core'));
  assert.ok(grid.traceable(LETTERS, 'red'));
  assert.ok(!grid.traceable(LETTERS, 'catt'));
  assert.ok(!grid.traceable(LETTERS, 'cd'));
  assert.throws(() => grid.checkWord(LETTERS, 'ca'), /three/);
  assert.throws(() => grid.checkWord(LETTERS, 'dog'), /grid/);
  assert.throws(() => grid.checkWord(LETTERS, 'cor'), /list/);
});

test('unique words score double', () => {
  const s = grid.score({ host: ['cats', 'red'], guest: ['red'] });
  assert.equal(s.host.score, 2 + 1);
  assert.equal(s.guest.score, 1);
  assert.equal(grid.points('scorers'), 5);
});

test('a round: the host sends the grid, words count, time ends it', () => {
  const room = createRoom('GRID', 'Alice', T0, uuid, null, null, 'grid');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['grid']), true, T0);
  assert.throws(() => setGridReady(room, 'host', true, 'abc', T0), /grid/);
  setGridReady(room, 'guest', true, null, T0);
  setGridReady(room, 'host', true, LETTERS, T0);
  assert.equal(room.status, 'started');
  assert.equal(view(room, 'guest', T0).grid.letters, null, 'hidden in the countdown');
  const now = T0 + COUNTDOWN_MS;
  assert.equal(view(room, 'guest', now).grid.letters, LETTERS);
  findWord(room, 'host', 'CATS', now);
  findWord(room, 'host', 'red', now);
  findWord(room, 'guest', 'red', now);
  assert.throws(() => findWord(room, 'guest', 'red', now), /already/);
  assert.deepEqual(view(room, 'guest', now).grid.counts, [2]);
  checkTimers(room, now + grid.ROUND_MS);
  const end = view(room, 'guest', now + grid.ROUND_MS);
  assert.equal(end.status, 'finished');
  assert.equal(end.winner, 'opponent');
  assert.equal(end.endReason, 'score');
  assert.equal(end.grid.results[0].who, 'me');
  assert.equal(end.grid.results[1].score, 3);
  assert.throws(() => findWord(room, 'host', 'cat', now + grid.ROUND_MS), /running/);
});
