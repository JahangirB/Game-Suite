import test from 'node:test';
import assert from 'node:assert/strict';
import * as dominoes from '../src/dominoes.js';
import { createRoom, joinRoom, makeMove, setConnected, setOptions, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const ALL = ['dominoes'];

/** A fixed position: hands and line set by hand, so the rules can be checked exactly. */
function position(marks, hands, line = [], options = {}) {
  const state = dominoes.start(() => 0.3, marks, options);
  state.hands = hands;
  state.line = line;
  state.boneyard = options.draw === false ? [] : (options.boneyard || []);
  return state;
}

test('a full set is dealt: seven each to two, five each to four in the draw game', () => {
  const two = dominoes.start(Math.random, ['h', 'g']);
  assert.equal(two.hands.h.length + two.hands.g.length + two.boneyard.length, 28);
  assert.equal(two.hands.h.length, 7);
  const four = dominoes.start(Math.random, ['h', 'g', '3', '4']);
  assert.equal(four.hands.h.length, 5);
  assert.equal(four.boneyard.length, 8);
  const block = dominoes.start(Math.random, ['h', 'g', '3', '4'], { draw: false });
  assert.equal(block.hands.h.length, 7);
  assert.equal(block.boneyard.length, 0);
});

test('the highest double starts the first round', () => {
  const s = dominoes.start(Math.random, ['h', 'g', '3']);
  const holder = s.marks.find((m) => s.hands[m].some(([a, b]) => a === b && a === Math.max(
    ...s.marks.flatMap((x) => s.hands[x].filter(([p, q]) => p === q).map(([p]) => p)))));
  if (holder) assert.equal(s.turn, holder);
});

test('tiles must match an end and turn to fit', () => {
  const s = position(['h', 'g'], { h: [[3, 5], [1, 1]], g: [[6, 6]] }, [[2, 3]]);
  assert.throws(() => dominoes.play(s, 'h', { tile: 1 }), /match/);
  dominoes.play(s, 'h', { tile: 0, end: 'right' });
  assert.deepEqual(s.line, [[2, 3], [3, 5]]);
  assert.deepEqual(s.hands.h, [[1, 1]]);
});

test('draw game: draw until you can play; block game: pass', () => {
  const s = position(['h', 'g'], { h: [[0, 0]], g: [[6, 6]] }, [[4, 5]], { boneyard: [[5, 6], [1, 2]] });
  assert.throws(() => dominoes.play(s, 'h', { pass: true }), /Draw/);
  assert.equal(dominoes.play(s, 'h', { draw: true }).again, true);
  assert.deepEqual(s.hands.h, [[0, 0], [1, 2]]);
  dominoes.play(s, 'h', { draw: true });
  assert.throws(() => dominoes.play(s, 'h', { draw: true }), /can play/);
  const b = position(['h', 'g'], { h: [[0, 0]], g: [[6, 6]] }, [[4, 5]], { draw: false });
  assert.throws(() => dominoes.play(b, 'h', { draw: true }), /no drawing/);
  assert.equal(dominoes.play(b, 'h', { pass: true }).again, false);
});

test('going out scores the pips left in the other hands, and the target ends the match', () => {
  const s = position(['h', 'g'], { h: [[3, 4]], g: [[6, 6], [5, 5]] }, [[1, 3]], { target: 50 });
  const r = dominoes.play(s, 'h', { tile: 0, end: 'right' });
  assert.equal(s.scores.h, 22);
  assert.equal(r.over, false);
  assert.equal(r.turn, 'h');
  assert.equal(s.lastRound.points, 22);
  s.scores.h = 45;
  s.hands = { h: [[2, 2]], g: [[6, 6]] };
  s.line = [[2, 0]];
  const end = dominoes.play(s, 'h', { tile: 0, end: 'left' });
  assert.deepEqual([end.over, end.winner], [true, 'h']);
});

test('a blocked round goes to the lightest hand; teams pool their pips', () => {
  const s = position(['h', 'g', '3', '4'], { h: [[0, 1]], g: [[6, 6]], 3: [[0, 0]], 4: [[5, 5]] }, [[2, 2]], { teams: true, draw: false });
  for (const m of ['h', 'g', '3']) dominoes.play(s, m, { pass: true });
  dominoes.play(s, '4', { pass: true });
  // Team A (h, 3) holds 1 pip, team B (g, 4) 22: A scores 22.
  assert.equal(s.scores.A, 22);
  assert.equal(s.lastRound.blocked, true);
  assert.ok(dominoes.sameSide(s, 'h', '3'));
});

test('a room of four plays teams: partners share the win', () => {
  const room = createRoom('DOM4', 'Alice', T0, uuid, null, null, 'dominoes');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat', 'Dan']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ALL), true, T0);
  assert.throws(() => setOptions(room, 'guest', { teams: true }), /host/);
  setOptions(room, 'host', { teams: true, target: 50 });
  for (const r of ['host', 'guest', 'p3', 'p4']) setTurnReady(room, r, true, T0);
  assert.equal(room.status, 'started');
  assert.equal(room.turnState.teams, true);
  const v = view(room, 'guest', T0);
  assert.equal(v.options.target, 50);
  assert.equal(v.turns.hand.length, 5);
  assert.deepEqual(v.turns.partners, ['p']);
  // The player to move goes out with the target in reach: their partner sees a win, the others a loss.
  const mover = room.turn;
  const side = ['host', 'p3'].includes(mover) ? 'A' : 'B';
  const mark = { host: 'h', guest: 'g', p3: '3', p4: '4' }[mover];
  room.turnState.scores[side] = 49;
  room.turnState.hands[mark] = [[1, 1]];
  room.turnState.line = [[1, 2]];
  for (const m of ['h', 'g', '3', '4']) if (m !== mark) room.turnState.hands[m] = [[6, 6]];
  makeMove(room, mover, { tile: 0, end: 'left' }, T0);
  assert.equal(room.status, 'finished');
  const partner = { host: 'p3', p3: 'host', guest: 'p4', p4: 'guest' }[mover];
  const foe = { host: 'guest', p3: 'guest', guest: 'host', p4: 'host' }[mover];
  assert.equal(view(room, partner, T0).winner, 'me');
  assert.equal(view(room, foe, T0).winner, 'opponent');
  assert.match(view(room, 'host', T0).winnerName, / & /);
});
