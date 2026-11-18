import test from 'node:test';
import assert from 'node:assert/strict';
import { GAMES, TURN_GAMES, checkTimers, createRoom, joinRoom, leave, makeMove, nextDeadline, pickWord, setConnected, setOptions, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

function room(options = { length: 4, timer: 0 }) {
  const r = createRoom('LADR1', 'Alice', T0, uuid, null, null, 'ladder');
  setConnected(r, 'host', true, T0);
  joinRoom(r, 'Bob', null, T0, uuid, null, null, ['ladder']);
  setConnected(r, 'guest', true, T0);
  setOptions(r, 'host', options);
  return r;
}
const begin = (r, now = T0) => {
  setTurnReady(r, 'host', true, now, () => 0.1);
  setTurnReady(r, 'guest', true, now, () => 0.1);
};
// The coin: below 0.5 the host's (first seat's) word is the start.
const everything = (r, role) => JSON.stringify(view(r, role, T0));

test('Word Ladder is a turn game that older apps never ask for', () => {
  assert.ok(GAMES.includes('ladder') && TURN_GAMES.includes('ladder'));
  const r = room({ length: 4, timer: 30 });
  assert.deepEqual(view(r, 'host', T0).options, { length: 4, timer: 30 });
  assert.equal(view(r, 'host', T0).maxPlayers, 2);
});

test('picks stay secret until both are in, then the target owner moves first', () => {
  const r = room();
  begin(r);
  assert.equal(r.status, 'started');
  assert.equal(r.turn, null);
  assert.throws(() => makeMove(r, 'host', { word: 'BOLD' }, T0), /Waiting for both words/);
  pickWord(r, 'host', 'cold', T0, () => 0.1);
  assert.ok(!everything(r, 'guest').includes('COLD'), 'the guest never sees the host pick');
  assert.ok(everything(r, 'host').includes('COLD'));
  assert.equal(view(r, 'guest', T0).turns.opponentPicked, true);
  assert.equal(view(r, 'host', T0).turns.turn, null);
  assert.throws(() => pickWord(r, 'guest', 'COLD', T0), /Pick another word/);
  assert.throws(() => pickWord(r, 'guest', 'ABLE', T0), /No way from ABLE to the other word/);
  assert.ok(!everything(r, 'guest').includes('COLD') && !everything(r, 'host').includes('ABLE'));
  assert.throws(() => pickWord(r, 'guest', 'ZZZZ', T0), /Not a word we can use/);
  pickWord(r, 'guest', 'TOLL', T0, () => 0.1);
  // The host's word (COLD) is the start; the guest owns the target and moves first.
  assert.equal(r.turn, 'guest');
  const g = view(r, 'guest', T0).turns;
  assert.deepEqual([g.start, g.target, g.turn, g.startOwner], ['COLD', 'TOLL', 'me', 'opponent']);
  assert.equal(view(r, 'host', T0).turns.turn, 'opponent');
  assert.throws(() => pickWord(r, 'host', 'HOLD', T0), /already started|has started/);
});

test('a full game: moves, a win for the start word owner, and a rematch', () => {
  const r = room();
  begin(r);
  pickWord(r, 'host', 'COLD', T0, () => 0.1);
  pickWord(r, 'guest', 'TOLL', T0, () => 0.1);
  assert.throws(() => makeMove(r, 'host', { word: 'CORD' }, T0), /not your turn|Not your turn/i);
  assert.throws(() => makeMove(r, 'guest', { word: 'WARM' }, T0), /Change one letter/);
  assert.throws(() => makeMove(r, 'guest', { word: 'CXLD' }, T0), /Not a word/);
  makeMove(r, 'guest', { word: 'TOLD' }, T0);
  assert.equal(r.turn, 'host');
  assert.throws(() => makeMove(r, 'host', { word: 'COLD' }, T0), /Already used/);
  makeMove(r, 'host', { word: 'TOLL' }, T0);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'host');
  assert.equal(r.endReason, 'target');
  const v = view(r, 'guest', T0);
  assert.equal(v.winner, 'opponent');
  assert.deepEqual(v.turns.chain, ['COLD', 'TOLD', 'TOLL']);
  // Rematch: ready again, fresh picks, the board is new.
  begin(r, T0 + 1000);
  assert.equal(r.status, 'started');
  assert.deepEqual(view(r, 'host', T0).turns.chain, []);
  assert.equal(view(r, 'host', T0).turns.myPick, null);
  pickWord(r, 'host', 'MOON', T0, () => 0.9);
  pickWord(r, 'guest', 'MOOD', T0, () => 0.9);
  // The guest's word (MOOD) is the start now; the host owns MOON, the target, and moves first.
  assert.equal(r.turn, 'host');
  makeMove(r, 'host', { word: 'MOON' }, T0);
  assert.equal(r.winner, 'host');
});

test('a player with no legal move loses', () => {
  const r = room();
  begin(r);
  pickWord(r, 'host', 'MOON', T0, () => 0.1);
  pickWord(r, 'guest', 'MOOD', T0, () => 0.1);
  makeMove(r, 'guest', { word: 'MUON' }, T0);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'guest');
  assert.equal(r.endReason, 'stuck');
});

test('the turn timer passes the turn through the alarm, and room.turn follows', () => {
  const r = room({ length: 4, timer: 30 });
  begin(r);
  assert.equal(nextDeadline(r), null); // no timer while picking
  pickWord(r, 'host', 'COLD', T0 + 1000, () => 0.1);
  pickWord(r, 'guest', 'TOLL', T0 + 2000, () => 0.1);
  assert.equal(r.turn, 'guest');
  assert.equal(nextDeadline(r), T0 + 32_000);
  assert.equal(checkTimers(r, T0 + 31_999), null);
  assert.equal(checkTimers(r, T0 + 32_000), 'changed');
  assert.equal(r.turn, 'host');
  assert.equal(view(r, 'host', T0).turns.turn, 'me');
  assert.equal(nextDeadline(r), T0 + 62_000);
  assert.throws(() => makeMove(r, 'guest', { word: 'TOLD' }, T0 + 33_000), /not your turn/i);
  makeMove(r, 'host', { word: 'TOLD' }, T0 + 40_000);
  assert.equal(r.turn, 'guest');
  assert.equal(nextDeadline(r), T0 + 70_000);
  // A finished game has no timer left.
  makeMove(r, 'guest', { word: 'TOLL' }, T0 + 41_000);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'guest');
  assert.equal(nextDeadline(r), null);
});

test('a seat that is not in the game cannot pick, and the game goes on', () => {
  const r = room();
  begin(r);
  pickWord(r, 'host', 'COLD', T0, () => 0.1);
  // A third seat (it stayed at the menu, so it is not in the match).
  r.players.p3 = { ...r.players.guest, name: 'Cy' };
  assert.throws(() => pickWord(r, 'p3', 'TOLL', T0), /not in this game/);
  assert.equal(r.turnState.chain.length, 0);
  pickWord(r, 'guest', 'TOLL', T0, () => 0.1);
  assert.deepEqual(r.turnState.chain, ['COLD']);
  assert.equal(r.turn, 'guest');
});

test('the turn timer does not run while both players are away', () => {
  const r = room({ length: 4, timer: 30 });
  begin(r);
  pickWord(r, 'host', 'COLD', T0, () => 0.1);
  pickWord(r, 'guest', 'TOLL', T0, () => 0.1);
  assert.equal(nextDeadline(r), T0 + 30_000);
  setConnected(r, 'host', false, T0 + 1000);
  setConnected(r, 'guest', false, T0 + 1000);
  assert.notEqual(nextDeadline(r), T0 + 30_000);
  assert.equal(checkTimers(r, T0 + 60_000), null);
  assert.equal(r.turn, 'guest');
  setConnected(r, 'host', true, T0 + 61_000);
  assert.equal(nextDeadline(r), T0 + 30_000);
});

test('leaving ends the game for the other player, and no message carries a pick early', () => {
  const r = room();
  begin(r);
  pickWord(r, 'host', 'COLD', T0, () => 0.1);
  const early = JSON.stringify(view(r, 'guest', T0));
  assert.ok(!early.includes('COLD'));
  leave(r, 'guest', T0);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'host');
});
