import test from 'node:test';
import assert from 'node:assert/strict';
import { COUNTDOWN_MS, createRoom, hockeyScore, joinRoom, setConnected, setHockeyReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

function match() {
  const room = createRoom('PUCK', 'Alice', T0, uuid, null, null, 'hockey');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['hockey']), true, T0);
  setHockeyReady(room, 'host', true, T0);
  setHockeyReady(room, 'guest', true, T0);
  return room;
}

test('two players; the match starts when both are ready', () => {
  const room = match();
  assert.equal(room.status, 'started');
  assert.equal(view(room, 'guest', T0).maxPlayers, 2);
  assert.deepEqual(view(room, 'guest', T0 + COUNTDOWN_MS).hockey, { mine: 0, theirs: 0, target: 7 });
});

test('only the host keeps the score, and it only goes up', () => {
  const room = match();
  const now = T0 + COUNTDOWN_MS;
  assert.throws(() => hockeyScore(room, 'host', [1, 0], T0), /countdown/);
  assert.throws(() => hockeyScore(room, 'guest', [0, 1], now), /host/);
  hockeyScore(room, 'host', [1, 0], now);
  assert.equal(view(room, 'guest', now).hockey.theirs, 1);
  assert.throws(() => hockeyScore(room, 'host', [0, 0], now), /Invalid/);
  assert.throws(() => hockeyScore(room, 'host', [1, 8], now), /Invalid/);
});

test('first to seven goals wins', () => {
  const room = match();
  const now = T0 + COUNTDOWN_MS;
  hockeyScore(room, 'host', [3, 7], now);
  const v = view(room, 'guest', now);
  assert.equal(v.status, 'finished');
  assert.equal(v.winner, 'me');
  assert.equal(v.endReason, 'goals');
});
