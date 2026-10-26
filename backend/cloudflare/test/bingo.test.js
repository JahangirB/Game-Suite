import test from 'node:test';
import assert from 'node:assert/strict';
import { CALL_MS, FREE, LINES, calledCount, dealCard, endsAt, fullLine, shuffledCalls } from '../src/bingo.js';
import { COUNTDOWN_MS, checkTimers, claimBingo, createRoom, joinRoom, setBingoReady, setConnected, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;

function round() {
  const room = createRoom('BING1', 'Alice', T0, uuid, null, null, 'bingo');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['bingo']);
  setConnected(room, 'guest', true, T0);
  setBingoReady(room, 'host', true, T0);
  setBingoReady(room, 'guest', true, T0);
  return room;
}

test('cards follow the columns and calls are 1 to 75 once each', () => {
  for (let k = 0; k < 50; k++) {
    const card = dealCard();
    assert.equal(card[FREE], 0);
    card.forEach((n, i) => { if (i !== FREE) assert.equal(Math.floor((n - 1) / 15), i % 5); });
    assert.equal(new Set(card).size, 25);
  }
  assert.deepEqual(shuffledCalls().sort((a, b) => a - b), Array.from({ length: 75 }, (_, i) => i + 1));
  assert.equal(LINES.length, 12);
  assert.equal(calledCount(GO, GO - 1), 0);
  assert.equal(calledCount(GO, GO), 1);
  assert.equal(calledCount(GO, GO + CALL_MS), 2);
  assert.equal(calledCount(GO, GO + 1e9), 75);
});

test('a line counts the free centre', () => {
  const card = dealCard();
  // The middle row needs only its four numbers.
  assert.equal(fullLine(card, [card[10], card[11], card[13]]), null);
  assert.deepEqual(fullLine(card, [card[10], card[11], card[13], card[14]]), [10, 11, 12, 13, 14]);
});

test('only a line of called numbers wins', () => {
  const room = round();
  const v = view(room, 'host', GO).bingo;
  assert.equal(v.calls.length, 75);
  assert.equal(v.card.length, 25);
  assert.equal(view(room, 'host', T0).bingo.calls, null);
  assert.throws(() => claimBingo(room, 'host', T0), /countdown/);
  assert.throws(() => claimBingo(room, 'host', GO), /No full line/);
  // Bob's first column, once all its numbers are called.
  const card = room.players.guest.card;
  const needed = [0, 5, 10, 15, 20].map((i) => card[i]);
  // Call that column first.
  room.calls = [...needed, ...room.calls.filter((n) => !needed.includes(n))];
  const last = 4;
  assert.throws(() => claimBingo(room, 'guest', GO + (last - 1) * CALL_MS), /No full line/);
  claimBingo(room, 'guest', GO + last * CALL_MS);
  const end = view(room, 'host', GO + last * CALL_MS);
  assert.equal(end.status, 'finished');
  assert.equal(end.winner, 'opponent');
  assert.equal(end.endReason, 'bingo');
  assert.deepEqual(end.bingo.winCard, card);
  assert.deepEqual(end.bingo.line, [0, 5, 10, 15, 20]);
});

test('nobody claiming ends the round with no winner', () => {
  const room = round();
  assert.equal(checkTimers(room, endsAt(GO) - 1), null);
  assert.equal(checkTimers(room, endsAt(GO)), 'changed');
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, null);
});
