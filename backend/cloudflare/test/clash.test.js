import test from 'node:test';
import assert from 'node:assert/strict';
import * as clash from '../src/clash.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

/** A fixed position: hands and the pile set by hand. */
function position(marks, hands, top, deck = ['r1', 'r2', 'r3', 'r4', 'r5', 'r6']) {
  const s = clash.start(() => 0.4, marks);
  return Object.assign(s, { hands, pile: [top], colour: top[0], deck: deck.slice(), direction: 1, drawn: null });
}

test('a deal: seven each and a number card on the pile', () => {
  const s = clash.start(Math.random, ['h', 'g', '3']);
  assert.equal(s.hands.h.length, 7);
  assert.equal(s.deck.length + 21 + 1, 108);
  assert.match(s.pile[0], /^[rygb][0-9]$/);
});

test('match colour or face; wilds need a colour', () => {
  const s = position(['h', 'g'], { h: ['r7', 'b5', 'g9', 'W', 'y2'], g: ['y1', 'y3'] }, 'b7');
  assert.throws(() => clash.play(s, 'h', { card: 2 }), /match/);
  assert.equal(clash.play(s, 'h', { card: 0 }).turn, 'g');
  assert.equal(s.colour, 'r');
  assert.throws(() => clash.play(s, 'g', { card: 0 }), /match/);
  const s2 = position(['h', 'g'], { h: ['W', 'r1'], g: ['y1'] }, 'b7');
  assert.throws(() => clash.play(s2, 'h', { card: 0 }), /colour/);
  clash.play(s2, 'h', { card: 0, colour: 'g' });
  assert.equal(s2.colour, 'g');
});

test('skip, reverse and draw two move the turn on', () => {
  const marks = ['h', 'g', '3', '4'];
  const hands = () => ({ h: ['rS', 'rR', 'rD', 'r1'], g: ['b1'], 3: ['b2'], 4: ['b3'] });
  let s = position(marks, hands(), 'r5');
  assert.equal(clash.play(s, 'h', { card: 0 }).turn, '3');
  s = position(marks, hands(), 'r5');
  assert.equal(clash.play(s, 'h', { card: 1 }).turn, '4');
  assert.equal(s.direction, -1);
  s = position(marks, hands(), 'r5');
  const r = clash.play(s, 'h', { card: 2 });
  assert.equal(r.turn, '3');
  assert.equal(s.hands.g.length, 3);
  // With two players a reverse is a skip.
  const two = position(['h', 'g'], { h: ['rR', 'r1'], g: ['b1'] }, 'r5');
  assert.equal(clash.play(two, 'h', { card: 0 }).turn, 'h');
});

test('draw one: play it if it fits, else the turn passes', () => {
  const s = position(['h', 'g'], { h: ['b1'], g: ['y1'] }, 'r5', ['b2', 'r9']);
  assert.throws(() => clash.play(s, 'h', { pass: true }), /Draw/);
  const r = clash.play(s, 'h', { draw: true });
  assert.equal(r.turn, 'h', 'r9 fits, so it may be played');
  assert.throws(() => clash.play(s, 'h', { card: 0 }), /drew/);
  assert.equal(clash.play(s, 'h', { pass: true }).turn, 'g');
  const r2 = clash.play(s, 'g', { draw: true });
  assert.equal(r2.turn, 'h', "b2 doesn't fit, the turn passes");
});

test('first to empty their hand wins, in a room of three', () => {
  const room = createRoom('CLSH', 'Alice', T0, uuid, null, null, 'clash');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ['clash']), true, T0);
  for (const r of ['host', 'guest', 'p3']) setTurnReady(room, r, true, T0);
  assert.equal(room.status, 'started');
  const mover = room.turn;
  const s = room.turnState;
  const top = s.pile[s.pile.length - 1];
  s.hands[{ host: 'h', guest: 'g', p3: '3' }[mover]] = [top[0] + '7'];
  makeMove(room, mover, { card: 0 }, T0);
  const v = view(room, mover, T0);
  assert.equal(v.status, 'finished');
  assert.equal(v.winner, 'me');
  assert.equal(v.endReason, 'out');
  assert.equal(view(room, mover === 'host' ? 'guest' : 'host', T0).turns.counts.o !== undefined, true);
});
