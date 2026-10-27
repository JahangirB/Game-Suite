import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createRoom, joinRoom, makeMove, openGame, setConnected, setOptions, setReady, setTurnReady, toMenu, view,
} from '../src/room.js';

let ids = 0;
const uuid = () => `s-${++ids}`;
const T0 = 1_700_000_000_000;
const ALL = ['sudoku', 'connect4', 'dots', 'ttt', 'clash'];

/** A Connect Four room with Alice (host) and Bob, both connected. */
function pair(game = 'connect4') {
  const room = createRoom('PAIR1', 'Alice', T0, uuid, 'fox', null, game);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, 'owl', null, ALL), true, T0);
  return room;
}

/** Plays Connect Four to a win for whoever moves first. */
function playToWin(room) {
  const first = room.turn;
  for (const col of [0, 1, 0, 1, 0, 1, 0]) makeMove(room, room.turn, { col }, T0);
  assert.equal(room.status, 'finished');
  return first;
}

test('everyone starts at the game the room was made for', () => {
  const room = pair();
  assert.equal(view(room, 'host', T0).me.at, 'connect4');
  assert.equal(view(room, 'host', T0).opponent.at, 'connect4');
  assert.equal(view(room, 'host', T0).setsOptions, true);
  assert.equal(view(room, 'guest', T0).setsOptions, false);
});

test('opening another game keeps the room, seats and connections, and the others stay put', () => {
  const room = pair();
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0, () => 0.1);
  playToWin(room);
  const ids = [room.players.host.id, room.players.guest.id];
  const round = room.round;

  openGame(room, 'guest', 'dots', T0 + 1);
  assert.equal(room.game, 'dots');
  assert.equal(room.status, 'lobby');
  assert.equal(room.code, 'PAIR1');
  assert.deepEqual([room.players.host.id, room.players.guest.id], ids);
  assert.equal(room.players.host.connected, true);
  assert.equal(room.players.guest.avatar, 'owl');
  // Nothing of the old game is left over.
  assert.equal(room.turnState, undefined);
  assert.equal(room.winner, null);
  assert.equal(room.round, round);

  // Alice is still on the Connect Four screen; her phone can see Bob went to Dots.
  const alice = view(room, 'host', T0 + 1);
  assert.equal(alice.game, 'dots');
  assert.equal(alice.me.at, 'connect4');
  assert.equal(alice.opponent.at, 'dots');
  // Whoever opened the game sets its options.
  assert.equal(alice.setsOptions, false);
  assert.equal(view(room, 'guest', T0 + 1).setsOptions, true);
});

test('a match starts only once the others come to the game', () => {
  const room = pair();
  openGame(room, 'host', 'dots', T0);
  assert.throws(() => setTurnReady(room, 'host', true, T0), /come to this game/);
  openGame(room, 'guest', 'dots', T0);
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  assert.equal(room.status, 'started');
  assert.equal(room.round, 1);
  assert.deepEqual(room.inMatch, ['host', 'guest']);
});

test('only the player who opened the game changes its options', () => {
  const room = pair();
  openGame(room, 'guest', 'ttt', T0);
  assert.throws(() => setOptions(room, 'host', { mode: 'classic' }), /Bob can change the rules/);
  setOptions(room, 'guest', { mode: 'classic' });
  assert.equal(view(room, 'guest', T0).options.mode, 'classic');
});

test('the menu keeps you in the room; leaving a match for it gives the match up', () => {
  const room = pair();
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0, () => 0.1);
  toMenu(room, 'guest', T0 + 5);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, 'host');
  assert.equal(room.players.guest.at, null);
  assert.equal(room.players.guest.left, false);
  assert.equal(view(room, 'host', T0 + 5).opponent.at, null);
  // Back from the menu to the same game, for a rematch.
  openGame(room, 'guest', 'connect4', T0 + 6);
  assert.equal(room.players.guest.at, 'connect4');
  setTurnReady(room, 'host', true, T0 + 6);
  setTurnReady(room, 'guest', true, T0 + 6);
  assert.equal(room.status, 'started');
});

test('another game cannot open during a match, but a player can return to theirs', () => {
  const room = pair();
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0, () => 0.1);
  assert.throws(() => openGame(room, 'host', 'dots', T0), /being played/);
  room.players.host.at = null;
  openGame(room, 'host', 'connect4', T0);
  assert.equal(room.players.host.at, 'connect4');
});

test('a two-player game is for the first two seats; a game holds no more than its players', () => {
  const room = createRoom('FOUR4', 'Alice', T0, uuid, null, null, 'clash');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ALL), true, T0);
  assert.throws(() => openGame(room, 'p3', 'connect4', T0), /for two/);
  openGame(room, 'host', 'connect4', T0);
  openGame(room, 'guest', 'connect4', T0);
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  // Cat, in the menu, isn't part of the match.
  assert.deepEqual(room.inMatch, ['host', 'guest']);
  assert.equal(view(room, 'p3', T0).me.at, 'clash');
});

test('a race game counts only the players at it', () => {
  const room = pair();
  openGame(room, 'host', 'sudoku', T0);
  openGame(room, 'guest', 'sudoku', T0);
  setReady(room, 'host', true, 'easy', T0);
  setReady(room, 'guest', true, 'easy', T0);
  assert.equal(room.status, 'started');
  assert.equal(room.game, 'sudoku');
});

test('rooms stored before the menu existed treat everyone as at the open game', () => {
  const room = pair();
  delete room.players.host.at;
  delete room.players.guest.at;
  assert.equal(view(room, 'host', T0).me.at, 'connect4');
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  assert.equal(room.status, 'started');
});
