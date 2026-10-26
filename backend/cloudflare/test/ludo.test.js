import test from 'node:test';
import assert from 'node:assert/strict';
import * as ludo from '../src/ludo.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
/** A die that rolls [n]. */
const die = (n) => () => (n - 1) / 6 + 0.01;

test('two players sit opposite; four take all colours', () => {
  assert.deepEqual(ludo.start(Math.random, ['h', 'g']).colours, { h: 0, g: 2 });
  assert.deepEqual(Object.values(ludo.start(Math.random, ['h', 'g', '3', '4']).colours).sort(), [0, 1, 2, 3]);
});

test('a token needs a six to come out; no move passes the turn', () => {
  const s = ludo.start(Math.random, ['h', 'g']);
  assert.equal(ludo.play(s, 'h', { roll: true }, die(3)).turn, 'g');
  assert.equal(s.roll, null);
  const r = ludo.play(s, 'g', { roll: true }, die(6));
  assert.equal(r.turn, 'g');
  assert.deepEqual(ludo.view(s, 'g').movable, [0, 1, 2, 3]);
  assert.deepEqual(ludo.view(s, 'h').movable, []);
  assert.throws(() => ludo.play(s, 'g', { roll: true }, die(2)), /Move/);
  assert.equal(ludo.play(s, 'g', { token: 1 }).turn, 'g', 'a six rolls again');
  assert.equal(s.tokens.g[1], 0);
});

test('landing on another colour sends it home, except on safe squares', () => {
  const s = ludo.start(Math.random, ['h', 'g']);
  // Red's square 20 is ring 20; yellow (colour 2) reaches ring 20 at progress 46.
  s.tokens.h = [17, -1, -1, -1];
  s.tokens.g = [46, -1, -1, -1];
  ludo.play(s, 'h', { roll: true }, die(3));
  ludo.play(s, 'h', { token: 0 });
  assert.equal(s.tokens.g[0], -1);
  // Ring 21 is a star: safe.
  s.tokens.h = [18, -1, -1, -1];
  s.tokens.g = [47, -1, -1, -1];
  ludo.play(s, 'h', { roll: true }, die(3));
  ludo.play(s, 'h', { token: 0 });
  assert.equal(s.tokens.g[0], 47);
});

test('home needs the exact roll; all four home wins', () => {
  const s = ludo.start(Math.random, ['h', 'g']);
  s.tokens.h = [ludo.HOME, ludo.HOME, ludo.HOME, 53];
  assert.equal(ludo.play(s, 'h', { roll: true }, die(5)).turn, 'g', 'too far: no move');
  ludo.play(s, 'g', { roll: true }, die(1));
  const r1 = ludo.play(s, 'h', { roll: true }, die(3));
  assert.equal(r1.again, true);
  const r = ludo.play(s, 'h', { token: 3 });
  assert.equal(r.over, true);
  assert.equal(r.winner, 'h');
});

test('a full game in a room ends with a winner', () => {
  const room = createRoom('LUDO', 'Alice', T0, uuid, null, null, 'ludo');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat', 'Dan']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ['ludo']), true, T0);
  for (const r of ['host', 'guest', 'p3', 'p4']) setTurnReady(room, r, true, T0);
  let n = 0;
  while (room.status === 'started' && n++ < 20000) {
    const role = room.turn;
    const v = view(room, role, T0 + 6000).turns;
    if (v.roll == null) makeMove(room, role, { roll: true }, T0);
    else makeMove(room, role, { token: v.movable[v.movable.length - 1] }, T0);
  }
  assert.equal(room.status, 'finished');
  assert.equal(room.endReason, 'home');
});
