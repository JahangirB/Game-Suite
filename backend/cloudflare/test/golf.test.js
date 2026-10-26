import test from 'node:test';
import assert from 'node:assert/strict';
import * as golf from '../src/golf.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const shot = (x, y, holed = false) => ({ angle: 1, power: 0.5, x, y, holed });

test('everyone plays the hole out in turn, then the next starts a seat on', () => {
  const s = golf.start(Math.random, ['h', 'g', '3']);
  assert.equal(s.turn, 'h');
  assert.equal(golf.play(s, 'h', shot(0.5, 0.5)).turn, 'g');
  assert.equal(golf.play(s, 'g', shot(0.5, 0.2, true)).turn, '3');
  assert.equal(golf.play(s, '3', shot(0.5, 0.4)).turn, 'h');
  assert.throws(() => golf.play(s, 'g', shot(0.5, 0.2)), /in the hole/);
  assert.equal(golf.play(s, 'h', shot(0.5, 0.2, true)).turn, '3');
  // The last one in moves everyone to hole 2, started by the next seat.
  assert.equal(golf.play(s, '3', shot(0.5, 0.2, true)).turn, 'g');
  assert.equal(s.hole, 1);
  const v = golf.view(s, 'g', { h: 'o', 3: 'p' }).golf;
  assert.deepEqual(v.strokes.m, [1, 0]);
  assert.deepEqual(v.strokes.o, [2, 0]);
  assert.equal(v.balls.m, null);
  assert.equal(v.last.who, 'p');
  assert.equal(v.last.holed, true);
});

test('bad shots are refused and a ball out after six is picked up with seven', () => {
  const s = golf.start(Math.random, ['h', 'g']);
  assert.throws(() => golf.play(s, 'h', { angle: 1, power: 0, x: 0.5, y: 0.5 }), /not valid/);
  assert.throws(() => golf.play(s, 'h', { angle: 1, power: 0.5, x: 9, y: 0.5 }), /not valid/);
  golf.play(s, 'h', shot(0.5, 0.2, true));
  for (let i = 0; i < 6; i++) golf.play(s, 'g', shot(0.5, 0.5));
  assert.deepEqual(s.strokes.g, [7, 0]);
  assert.equal(s.hole, 1);
});

test('nine holes in a room; fewest strokes wins', () => {
  const room = createRoom('GOLF', 'Alice', T0, uuid, null, null, 'golf');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['golf']), true, T0);
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  for (let h = 0; h < golf.HOLES; h++) {
    // Bob always needs two, Alice one.
    const first = room.turn;
    const order = first === 'host' ? ['host', 'guest', 'guest'] : ['guest', 'host', 'guest'];
    for (const r of order) {
      const holed = r === 'host' || room.turnState.strokes.g[h] === 1;
      makeMove(room, r, shot(0.5, 0.2, holed), T0 + 1000);
    }
  }
  assert.equal(room.status, 'finished');
  assert.equal(view(room, 'host', T0 + 2000).winner, 'me');
  assert.equal(room.endReason, 'strokes');
});
