import test from 'node:test';
import assert from 'node:assert/strict';
import * as slide from '../src/slide.js';
import { COUNTDOWN_MS, createRoom, joinRoom, setConnected, setOptions, setSlideReady, slideMoves, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
// 1 2 3 / 4 5 6 / 7 0 8: one slide (8) from solved.
const NEAR = [1, 2, 3, 4, 5, 6, 7, 0, 8];

test('starts must be solvable and not solved', () => {
  assert.deepEqual(slide.checkStart(NEAR, 3), NEAR);
  assert.throws(() => slide.checkStart([2, 1, 3, 4, 5, 6, 7, 8, 0], 3), /cannot be solved/);
  assert.throws(() => slide.checkStart(slide.solvedTiles(3), 3), /already/);
  assert.throws(() => slide.checkStart([1, 2, 3], 3), /valid/);
  const tiles = NEAR.slice();
  assert.throws(() => slide.slide(tiles, 3, 1), /slide/);
  slide.slide(tiles, 3, 8);
  assert.ok(slide.isSolved(tiles, 3));
});

test('moves are replayed; the first to solve wins', () => {
  const room = createRoom('SLID', 'Alice', T0, uuid, null, null, 'slide');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['slide']), true, T0);
  setOptions(room, 'host', { level: 'easy' });
  setSlideReady(room, 'guest', true, null, T0);
  setSlideReady(room, 'host', true, NEAR, T0);
  const go = T0 + COUNTDOWN_MS;
  assert.deepEqual(view(room, 'guest', go).slide.start, NEAR);
  assert.throws(() => slideMoves(room, 'guest', '1', go), /slide/);
  slideMoves(room, 'guest', '5,5', go);
  assert.equal(view(room, 'host', go).others[0].progress, 87);
  slideMoves(room, 'host', '8', go + 1);
  const end = view(room, 'guest', go + 2);
  assert.equal(end.status, 'finished');
  assert.equal(end.winner, 'opponent');
});
