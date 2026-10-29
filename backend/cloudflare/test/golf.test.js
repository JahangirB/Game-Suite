import test from 'node:test';
import assert from 'node:assert/strict';
import * as golf from '../src/golf.js';
import { createRoom, joinRoom, makeMove, setConnected, setOptions, setTurnReady, view } from '../src/room.js';

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

test('bad shots are refused; nobody is picked up before someone is in', () => {
  const s = golf.start(Math.random, ['h', 'g']);
  assert.throws(() => golf.play(s, 'h', { angle: 1, power: 0, x: 0.5, y: 0.5 }), /not valid/);
  assert.throws(() => golf.play(s, 'h', { angle: 1, power: 0.5, x: 9, y: 0.5 }), /not valid/);
  // Both keep missing (or falling and starting again): the hole goes on.
  for (let i = 0; i < 10; i++) {
    golf.play(s, 'h', shot(0.5, 0.5));
    golf.play(s, 'g', shot(0.5, 0.5));
  }
  assert.equal(s.hole, 0);
  assert.deepEqual(golf.view(s, 'h').golf.left, { m: null, o: null });
  // Ann holes out with her 11th: Ben gets three more, counted down.
  golf.play(s, 'h', shot(0.5, 0.2, true));
  assert.deepEqual(golf.view(s, 'g').golf.left, { m: 3, o: null });
  golf.play(s, 'g', shot(0.5, 0.5));
  assert.equal(golf.view(s, 'g').golf.left.m, 2);
  golf.play(s, 'g', shot(0.5, 0.5));
  golf.play(s, 'g', shot(0.5, 0.5));
  // Not in after those: picked up with one more, and on to the next hole.
  assert.deepEqual(s.strokes.g, [14, 0]);
  assert.deepEqual(s.strokes.h, [11, 0]);
  assert.equal(s.hole, 1);
  assert.deepEqual(golf.view(s, 'g').golf.left, { m: null, o: null });
});

test('holing out within the three shots counts as usual, and twenty is a safety net', () => {
  const s = golf.start(Math.random, ['h', 'g', '3']);
  golf.play(s, 'h', shot(0.5, 0.2, true));
  golf.play(s, 'g', shot(0.5, 0.5));
  golf.play(s, '3', shot(0.5, 0.5));
  golf.play(s, 'g', shot(0.5, 0.2, true));
  assert.deepEqual(s.strokes.g, [2]);
  golf.play(s, '3', shot(0.5, 0.5));
  golf.play(s, '3', shot(0.5, 0.5));
  assert.deepEqual(s.strokes['3'], [4, 0]);
  // Alone on a hole nobody finishes, the twentieth shot picks the ball up.
  const t = golf.start(Math.random, ['h']);
  for (let i = 0; i < golf.MAX_STROKES; i++) golf.play(t, 'h', shot(0.5, 0.5));
  assert.deepEqual(t.strokes.h, [21, 0]);
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

test('a game plays nine holes of the level picked: easy one island, medium two, hard three', () => {
  const bands = { easy: [0, 333], medium: [333, 666], hard: [666, 1000] };
  for (const [level, [from, to]] of Object.entries(bands)) {
    for (let k = 0; k < 30; k++) {
      const course = golf.pickCourse(Math.random, level);
      assert.equal(course.length, golf.HOLES);
      assert.equal(new Set(course).size, golf.HOLES);
      course.forEach((id) => assert.ok(id >= from && id < to, `${level} ${id}`));
    }
    const s = golf.start(Math.random, ['h', 'g'], { level });
    s.course.forEach((id) => assert.ok(id >= from && id < to));
    assert.deepEqual(golf.view(s, 'h').golf.course, s.course);
  }
  assert.deepEqual(golf.cleanOptions({ level: 'silly' }), { level: 'easy' });
});

test('whoever opened the golf game picks the level, and the match uses it', () => {
  const room = createRoom('GOLF', 'Alice', T0, uuid, null, null, 'golf');
  const guest = joinRoom(room, 'Bob', null, T0, uuid, null, null, ['golf']);
  setConnected(room, guest, true, T0);
  setOptions(room, 'host', { level: 'hard' });
  assert.deepEqual(view(room, 'host', T0 + 2).options, { level: 'hard' });
  setTurnReady(room, 'host', true, T0 + 3);
  setTurnReady(room, guest, true, T0 + 4);
  room.turnState.course.forEach((id) => assert.ok(id >= 666 && id < 1000));
});
