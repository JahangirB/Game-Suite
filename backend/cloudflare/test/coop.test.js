import test from 'node:test';
import assert from 'node:assert/strict';
import {
  COOP_LIVES, coopCursor, coopPlace, createRoom, joinRoom, setConnected, setOptions, setReady, submitBoard, view,
} from '../src/room.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;

function together(players = 2) {
  const room = createRoom('ABC123', 'Alice', T0, uuid);
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat', 'Dan'].slice(0, players - 1)) setConnected(room, joinRoom(room, name, null, T0, uuid), true, T0);
  setOptions(room, 'host', { mode: 'together' });
  for (const r of ['host', 'guest', 'p3', 'p4'].slice(0, players)) setReady(room, r, true, 'easy', T0);
  return room;
}

const gaps = (room) => [...room.puzzle].flatMap((c, i) => (c === '0' ? [i] : []));
const wrong = (room, cell) => (Number(room.solution[cell]) % 9) + 1;

test('Together is a Sudoku option; race stays the default', () => {
  const room = createRoom('ABC123', 'Alice', T0, uuid);
  assert.deepEqual(view(room, 'host', T0).options, { mode: 'race' });
  setOptions(room, 'host', { mode: 'together' });
  assert.deepEqual(view(room, 'host', T0).options, { mode: 'together' });
  setOptions(room, 'host', { mode: 'nonsense' });
  assert.equal(view(room, 'host', T0).options.mode, 'race');
});

test('a race has no shared grid', () => {
  const room = createRoom('ABC123', 'Alice', T0, uuid);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid), true, T0);
  setReady(room, 'host', true, 'easy', T0);
  setReady(room, 'guest', true, 'easy', T0);
  assert.equal(room.coop, null);
  assert.equal(view(room, 'host', room.startAt).coop, null);
  assert.throws(() => coopPlace(room, 'host', 0, 1, room.startAt), /not running/);
});

test('right digits stay in the colour of who put them, on both phones', () => {
  const room = together();
  const now = room.startAt;
  // Not before the countdown ends.
  assert.throws(() => coopPlace(room, 'host', gaps(room)[0], 1, now - 1), /not running/);
  assert.equal(view(room, 'host', now - 1).coop, null);
  const [a, b] = gaps(room);
  assert.equal(coopPlace(room, 'host', a, Number(room.solution[a]), now), true);
  assert.equal(coopPlace(room, 'guest', b, Number(room.solution[b]), now), true);
  const mine = view(room, 'host', now).coop;
  const theirs = view(room, 'guest', now).coop;
  assert.equal(mine.board[a], room.solution[a]);
  assert.equal(theirs.board[a], room.solution[a]);
  assert.equal(mine.by[a], 'm');
  assert.equal(mine.by[b], '1');
  assert.equal(theirs.by[a], '1');
  assert.equal(theirs.by[b], 'm');
  assert.equal(mine.lives, COOP_LIVES);
  assert.equal(view(room, 'host', now).me.placed, 1);
  // A filled square can't be filled again.
  assert.equal(coopPlace(room, 'guest', a, Number(room.solution[a]), now), false);
  // Givens can't be changed and boards sent the race way are ignored.
  const given = [...room.puzzle].findIndex((c) => c !== '0');
  assert.equal(coopPlace(room, 'host', given, 1, now), false);
  assert.equal(submitBoard(room, 'host', room.solution, now), false);
  assert.equal(room.status, 'started');
  assert.throws(() => coopPlace(room, 'host', 81, 1, now), /Invalid/);
  assert.throws(() => coopPlace(room, 'host', a, 0, now), /Invalid/);
});

test('a wrong digit costs a shared life and is not kept; the third loses for everyone', () => {
  const room = together();
  const now = room.startAt;
  const cell = gaps(room)[0];
  coopPlace(room, 'guest', cell, wrong(room, cell), now);
  let v = view(room, 'host', now).coop;
  assert.equal(v.lives, COOP_LIVES - 1);
  assert.equal(v.board[cell], '0');
  assert.deepEqual(v.miss, { cell, digit: wrong(room, cell), mine: false, seq: 1 });
  assert.equal(view(room, 'guest', now).coop.miss.mine, true);
  coopPlace(room, 'host', cell, wrong(room, cell), now);
  coopPlace(room, 'host', cell, wrong(room, cell), now + 5);
  assert.equal(room.status, 'finished');
  assert.equal(room.endReason, 'outOfLives');
  v = view(room, 'host', now + 5);
  assert.equal(v.coop.lives, 0);
  assert.equal(v.coop.result, 'lost');
  assert.equal(v.winner, 'opponent');
  assert.equal(view(room, 'guest', now + 5).winner, 'opponent');
  assert.equal(v.winnerName, null);
  assert.ok(v.solution);
  assert.equal(v.me.finishMs, 5);
});

test('filling the grid wins for everyone, with who placed how many', () => {
  const room = together(3);
  const now = room.startAt;
  const roles = ['host', 'guest', 'p3'];
  gaps(room).forEach((cell, i) => coopPlace(room, roles[i % 3], cell, Number(room.solution[cell]), now + i));
  assert.equal(room.status, 'finished');
  assert.equal(room.endReason, 'solvedTogether');
  for (const r of roles) {
    const v = view(room, r, now + 100);
    assert.equal(v.winner, 'me');
    assert.equal(v.winnerName, 'Alice & Bob & Cat');
    assert.equal(v.coop.result, 'won');
    assert.equal(v.me.progress, 100);
  }
  const total = gaps(room).length;
  const v = view(room, 'host', now);
  assert.equal(v.me.placed + v.others.reduce((n, o) => n + o.placed, 0), total);
  // Starting again clears the grid and the counts.
  setReady(room, 'host', true, 'easy', now + 200);
  setReady(room, 'guest', true, 'easy', now + 200);
  setReady(room, 'p3', true, 'easy', now + 200);
  assert.equal(room.coop.board, room.puzzle);
  assert.equal(room.coop.lives, COOP_LIVES);
  assert.equal(view(room, 'host', room.startAt).me.placed, 0);
});

test('each player sees where the others are', () => {
  const room = together(3);
  const now = room.startAt;
  assert.deepEqual(view(room, 'host', now).coop.cursors, [-1, -1]);
  assert.equal(coopCursor(room, 'guest', 40, now), true);
  assert.equal(coopCursor(room, 'guest', 40, now), false);
  coopCursor(room, 'p3', 3, now);
  assert.deepEqual(view(room, 'host', now).coop.cursors, [40, 3]);
  assert.deepEqual(view(room, 'p3', now).coop.cursors, [-1, 40]);
  assert.throws(() => coopCursor(room, 'host', 99, now), /Invalid/);
  assert.equal(coopCursor(room, 'host', 2, now - 1), false);
});
