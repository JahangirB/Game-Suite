import test from 'node:test';
import assert from 'node:assert/strict';
import * as blocks from '../src/blocks.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

test('21 pieces, 89 squares and 91 different ways to lay them', () => {
  assert.equal(blocks.PIECES.length, 21);
  assert.equal(blocks.PIECES.flat().length, 89);
  let ways = 0;
  for (let p = 0; p < 21; p++) ways += new Set([0, 1, 2, 3, 4, 5, 6, 7].map((o) => JSON.stringify(blocks.shape(p, o)))).size;
  assert.equal(ways, 91);
});

test('start square first, then corners only', () => {
  const s = blocks.start(Math.random, ['h', 'g']);
  assert.equal(s.size, 14);
  assert.throws(() => blocks.play(s, 'h', { piece: 0, orient: 0, x: 0, y: 0 }), /start square/);
  // The 2 x 2 square on (4, 4).
  assert.equal(blocks.play(s, 'h', { piece: 7, orient: 0, x: 3, y: 3 }).turn, 'g');
  blocks.play(s, 'g', { piece: 7, orient: 0, x: 9, y: 9 });
  assert.throws(() => blocks.play(s, 'h', { piece: 0, orient: 0, x: 4, y: 4 }), /taken/);
  assert.throws(() => blocks.play(s, 'h', { piece: 0, orient: 0, x: 5, y: 4 }), /only touch at the corners/);
  assert.throws(() => blocks.play(s, 'h', { piece: 0, orient: 0, x: 8, y: 8 }), /corner to corner/);
  assert.throws(() => blocks.play(s, 'h', { piece: 7, orient: 0, x: 5, y: 5 }), /played that piece/);
  blocks.play(s, 'h', { piece: 0, orient: 0, x: 5, y: 5 });
  const v = blocks.view(s, 'g', { h: 'o' }).blocks;
  assert.equal(v.placed.o, 5);
  assert.equal(v.left.m.length, 20);
  assert.equal(v.board[5 * 14 + 5], 'o');
  assert.deepEqual(v.last.cells, [[5, 5]]);
});

test('random games end, skipping players who are stuck', () => {
  for (const n of [2, 3, 4]) {
    const marks = ['h', 'g', '3', '4'].slice(0, n);
    const s = blocks.start(Math.random, marks);
    let r = { turn: 'h', over: false };
    let moves = 0;
    while (!r.over) {
      const options = blocks.movesFor(s, r.turn);
      assert.ok(options.length > 0, 'the player to move can move');
      // Biggest pieces first, like a person would.
      const big = options.filter((m) => blocks.PIECES[m.piece].length === Math.max(...options.map((o) => blocks.PIECES[o.piece].length)));
      r = blocks.play(s, r.turn, big[Math.floor(Math.random() * big.length)]);
      moves++;
    }
    assert.ok(moves > 10 && moves <= 21 * n);
    for (const m of marks) assert.equal(blocks.canMove(s, m), false);
    const total = marks.reduce((a, m) => a + s.placed[m], 0);
    assert.equal([...s.board].filter((c) => c !== '.').length, total);
  }
});

test('a room of four starts in the corners', () => {
  const room = createRoom('BLOK', 'Alice', T0, uuid, null, null, 'blocks');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat', 'Dan']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ['blocks']), true, T0);
  for (const r of ['host', 'guest', 'p3', 'p4']) setTurnReady(room, r, true, T0);
  const v = view(room, 'p3', T0).turns.blocks;
  assert.equal(v.size, 20);
  assert.deepEqual(v.starts.m, [19, 19]);
  assert.equal(room.turn, 'host');
  makeMove(room, 'host', { piece: 0, orient: 0, x: 0, y: 0 }, T0);
  assert.equal(room.turn, 'guest');
  // Others go round the table from the viewer: to Bob, Alice comes last.
  assert.equal(view(room, 'guest', T0).turns.blocks.board[0], 'q');
});
