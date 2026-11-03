import test from 'node:test';
import assert from 'node:assert/strict';
import {
  INACTIVE_FORFEIT_MS, PAUSE_MS, checkTimers, createRoom, joinRoom, makeMove, nextDeadline, openGame,
  setConnected, setPaused, setTurnReady, setHockeyReady, view, wantRestart,
} from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

function game(kind) {
  const room = createRoom('PAUSE', 'Alice', T0, uuid, null, null, kind);
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, [kind]);
  setConnected(room, 'guest', true, T0);
  if (kind === 'hockey') {
    setHockeyReady(room, 'host', true, T0);
    setHockeyReady(room, 'guest', true, T0);
  } else {
    setTurnReady(room, 'host', true, T0, () => 0.1);
    setTurnReady(room, 'guest', true, T0, () => 0.1);
  }
  assert.equal(room.status, 'started');
  return room;
}

test('pausing keeps the match and shows the partner how long it waits', () => {
  const room = game('connect4');
  setPaused(room, 'guest', true, T0 + 1000);
  assert.equal(room.status, 'started');
  assert.equal(view(room, 'guest', T0 + 1000).me.at, null);
  const seen = view(room, 'host', T0 + 61_000).opponent;
  assert.equal(seen.pausedMs, PAUSE_MS - 60_000);
  // Moves go on meanwhile; coming back clears the pause.
  makeMove(room, 'host', { col: 3 }, T0 + 2000);
  openGame(room, 'guest', 'connect4', T0 + 3000);
  assert.equal(view(room, 'host', T0 + 3000).opponent.pausedMs, null);
  assert.equal(view(room, 'guest', T0 + 3000).me.at, 'connect4');
});

test('a pause that runs out loses the match', () => {
  const room = game('connect4');
  setPaused(room, 'guest', true, T0);
  assert.equal(nextDeadline(room), T0 + PAUSE_MS);
  assert.equal(checkTimers(room, T0 + PAUSE_MS - 1), null);
  assert.equal(checkTimers(room, T0 + PAUSE_MS), 'changed');
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, 'host');
  // The paused player sees the result when they come back.
  assert.equal(room.players.guest.at, 'connect4');
});

test('a paused player whose app closes is not forfeited for being away', () => {
  const room = game('hockey');
  setPaused(room, 'guest', true, T0 + 10);
  setConnected(room, 'guest', false, T0 + 20);
  checkTimers(room, T0 + 20 + INACTIVE_FORFEIT_MS + 1);
  assert.equal(room.status, 'started');
  checkTimers(room, T0 + 10 + PAUSE_MS);
  assert.equal(room.status, 'finished');
});

test('a new game starts once everyone asks for it, with nobody winning', () => {
  const room = game('dots');
  wantRestart(room, 'host', true, T0);
  assert.equal(room.status, 'started');
  assert.equal(view(room, 'guest', T0).opponent.restart, true);
  wantRestart(room, 'host', false, T0);
  assert.equal(view(room, 'guest', T0).opponent.restart, false);
  wantRestart(room, 'host', true, T0);
  wantRestart(room, 'guest', true, T0);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, null);
  assert.equal(room.endReason, 'restart');
  assert.equal(view(room, 'guest', T0).opponent.restart, false);
  assert.throws(() => wantRestart(room, 'host', true, T0));
});

test('pausing outside a match just goes to the menu', () => {
  const room = game('dots');
  wantRestart(room, 'host', true, T0);
  wantRestart(room, 'guest', true, T0);
  setPaused(room, 'guest', true, T0);
  assert.equal(room.players.guest.at, null);
  assert.equal(room.players.guest.pausedUntil ?? null, null);
});
