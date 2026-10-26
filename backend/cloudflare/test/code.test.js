import test from 'node:test';
import assert from 'node:assert/strict';
import * as code from '../src/code.js';
import { COUNTDOWN_MS, codeMove, createRoom, joinRoom, setCodeReady, setConnected, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

test('black for right place, white for right colour elsewhere, each peg once', () => {
  assert.deepEqual(code.score('rygb', 'rygb'), { black: 4, white: 0 });
  assert.deepEqual(code.score('bgyr', 'rygb'), { black: 0, white: 4 });
  assert.deepEqual(code.score('rrrr', 'rygb'), { black: 1, white: 0 });
  assert.deepEqual(code.score('rryy', 'yrrp'), { black: 1, white: 2 });
  assert.throws(() => code.cleanCode('rgx'), /pegs/);
});

test('both set a code, then race to crack the other one', () => {
  const room = createRoom('CODE', 'Alice', T0, uuid, null, null, 'code');
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, ['code']), true, T0);
  setCodeReady(room, 'host', true, T0);
  setCodeReady(room, 'guest', true, T0);
  assert.equal(view(room, 'host', T0).codeGame.phase, 'set');
  codeMove(room, 'host', 'rrgg', T0);
  assert.throws(() => codeMove(room, 'host', 'bbbb', T0), /set/);
  assert.equal(view(room, 'guest', T0).codeGame.theySet, true);
  codeMove(room, 'guest', 'pobb', T0);
  assert.equal(view(room, 'host', T0).codeGame.phase, 'guess');
  assert.throws(() => codeMove(room, 'host', 'pobb', T0), /countdown/);
  const go = T0 + COUNTDOWN_MS;
  codeMove(room, 'guest', 'rgrg', go);
  assert.deepEqual(view(room, 'host', go).codeGame.theirs, [{ black: 2, white: 2 }]);
  // One guess each so far: the host cracks it, and the guest can no longer do better.
  codeMove(room, 'host', 'pobb', go + 1);
  assert.throws(() => codeMove(room, 'guest', 'rrgy', go + 2), /not running/);
  const end = view(room, 'guest', go + 3);
  assert.equal(end.status, 'finished');
  assert.equal(end.winner, 'opponent');
  assert.equal(end.codeGame.theirCode, 'rrgg');
});
