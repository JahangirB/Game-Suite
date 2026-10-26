import test from 'node:test';
import assert from 'node:assert/strict';
import * as ttt from '../src/ttt.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

test('a move sends the opponent to the matching small board', () => {
  const s = ttt.start();
  ttt.play(s, 'h', { cell: 4 }); // board 0, centre
  assert.equal(s.next, 4);
  assert.throws(() => ttt.play(s, 'g', { cell: 9 }), /highlighted board/);
  ttt.play(s, 'g', { cell: 36 }); // board 4, top left
  assert.equal(s.next, 0);
  assert.throws(() => ttt.play(s, 'h', { cell: 4 }), /taken/);
});

test('winning a small board claims it, and a decided board frees the next move', () => {
  const s = ttt.start();
  s.cells = 'hh' + '.'.repeat(79);
  s.next = 0;
  ttt.play(s, 'h', { cell: 2 }); // top row of board 0
  assert.equal(s.boards[0], 'h');
  assert.equal(s.next, 2);
  ttt.play(s, 'g', { cell: 18 }); // points at board 0, which is decided
  assert.equal(s.next, -1);
  assert.throws(() => ttt.play(s, 'h', { cell: 5 }), /already decided/);
  ttt.play(s, 'h', { cell: 40 });
});

test('three small boards in a row win the game, and the view hides marks behind m and o', () => {
  const room = createRoom('TTT01', 'Alice', T0, uuid, null, null, 'ttt');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['ttt']);
  setConnected(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0, () => 0.1);
  setTurnReady(room, 'guest', true, T0, () => 0.1);
  assert.equal(room.turn, 'host');
  // Give the host boards 0 and 1, then win board 2 for three across the top.
  room.turnState.boards = 'hh.......';
  room.turnState.cells = '.'.repeat(18) + 'hh' + '.'.repeat(61);
  room.turnState.next = 2;
  makeMove(room, 'host', { cell: 20 }, T0);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, 'host');
  assert.equal(room.endReason, 'three');
  const v = view(room, 'guest', T0).turns;
  assert.deepEqual(v.winLine, [0, 1, 2]);
  assert.equal(v.boards.slice(0, 3), 'ooo');
  assert.deepEqual(v.lastMove, { by: 'opponent', cell: 20 });
});

test('a board that fills with no line is drawn, and all boards decided with no line is a draw', () => {
  const s = ttt.start();
  s.cells = 'hghhgggh.' + '.'.repeat(72);
  s.boards = '.ghhggghh';
  s.next = 0;
  const result = ttt.play(s, 'h', { cell: 8 });
  assert.equal(s.boards[0], 'd');
  assert.equal(s.next, -1);
  assert.deepEqual(result, { again: false, over: true, winner: null, reason: 'draw' });
});
