import test from 'node:test';
import assert from 'node:assert/strict';
import * as balloon from '../src/balloon.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

test('the picker picks, the others guess in turn; the word stays hidden', () => {
  const s = balloon.start(Math.random, ['h', 'g', '3']);
  assert.throws(() => balloon.play(s, 'g', { word: 'cat' }), /pick/);
  assert.throws(() => balloon.play(s, 'h', { word: 'no' }), /3 to 12/);
  assert.equal(balloon.play(s, 'h', { word: 'Hello' }).turn, 'g');
  assert.equal(balloon.view(s, 'g').word, null);
  assert.equal(balloon.view(s, 'h').word, 'HELLO');
  assert.equal(balloon.view(s, 'g').pattern, '_____');
  assert.equal(balloon.play(s, 'g', { letter: 'l' }).turn, '3');
  assert.equal(s.scores.g, 2);
  assert.equal(balloon.view(s, '3').pattern, '__LL_');
  assert.throws(() => balloon.play(s, '3', { letter: 'L' }), /already/);
  assert.equal(balloon.play(s, '3', { letter: 'z' }).turn, 'g', 'the picker is skipped');
  assert.equal(s.misses, 1);
});

test('finishing the word scores 3 more; then the next player picks', () => {
  const s = balloon.start(Math.random, ['h', 'g']);
  balloon.play(s, 'h', { word: 'aab' });
  balloon.play(s, 'g', { letter: 'a' });
  const r = balloon.play(s, 'g', { letter: 'b' });
  assert.equal(s.scores.g, 2 + 1 + 3);
  assert.equal(r.turn, 'g', 'the guesser picks next');
  assert.equal(s.lastRound.solvedBy, 'g');
});

test('all balloons popped: the picker scores; after everyone picks, the most points wins', () => {
  const s = balloon.start(Math.random, ['h', 'g']);
  balloon.play(s, 'h', { word: 'zzz' });
  for (const l of 'abcdefg') balloon.play(s, 'g', { letter: l });
  assert.equal(s.scores.h, 5);
  assert.equal(s.phase, 'pick');
  balloon.play(s, 'g', { word: 'ab' + 'c' });
  balloon.play(s, 'h', { letter: 'a' });
  balloon.play(s, 'h', { letter: 'b' });
  const r = balloon.play(s, 'h', { letter: 'c' });
  assert.equal(r.over, true);
  assert.equal(r.winner, 'h');
});

test('in a room the host picks first', () => {
  const room = createRoom('BALL', 'Alice', T0, uuid, null, null, 'balloon');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['balloon']), true, T0);
  for (const r of ['host', 'guest']) setTurnReady(room, r, true, T0);
  assert.equal(room.turn, 'host');
  makeMove(room, 'host', { word: 'secret' }, T0);
  assert.equal(room.turn, 'guest');
  assert.equal(view(room, 'guest', T0 + 6000).turns.pattern, '______');
});
