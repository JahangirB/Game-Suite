// Stickers from the downloadable packs (cap "packs"). A pack sticker travels as a reaction under
// the key 'p:<id>' (id: ^[a-z0-9-]{1,32}$). Today's app drops a reaction key it does not know, so
// it ignores one. The same tests are in backend/test/code.test.js and the app's LocalPacksTest.
import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPS, EMOJI_INTERVAL_MS, EMOJIS, createRoom, joinRoom, sendEmoji, setConnected, view } from '../src/room.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;

function lobby() {
  const room = createRoom('PKS123', 'Alice', T0, uuid);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid), true, T0);
  return room;
}

test('the server lists the cap', () => {
  assert.ok(CAPS.includes('packs'));
  assert.ok(view(lobby(), 'host', T0).caps.includes('packs'));
});

test('a valid key reaches the other player as it was sent', () => {
  for (const key of ['p:sunny-day', 'p:a', 'p:0', 'p:' + 'a'.repeat(32), 'p:a-b-9']) {
    const room = lobby();
    sendEmoji(room, 'host', key, T0);
    assert.deepEqual(view(room, 'guest', T0).opponent.emoji, { key, seq: 1 }, key);
    assert.deepEqual(view(room, 'host', T0).me.emoji, { key, seq: 1 });
  }
  assert.ok(!EMOJIS.some((k) => k.startsWith('p:')), "today's app reads the key against its reactions and drops one it does not know");
});

test('a key of the wrong shape is refused like an unknown reaction', () => {
  const room = lobby();
  for (const key of ['p:', 'p:Sunny', 'p:' + 'a'.repeat(33), 'p:a/b', 'p:a.b', 'p:../x', 'p:a b', 'p:a\n', 'P:abc', 'p', 'pack:abc', 'p:é', '', undefined, null, 7, ['p:abc'], { toString: () => 'p:abc' }]) {
    assert.throws(() => sendEmoji(room, 'host', key, T0), /Unknown reaction/, String(JSON.stringify(key)));
  }
  assert.equal(room.emojiSeq || 0, 0, 'nothing was shown');
  assert.equal(view(room, 'guest', T0).opponent.emoji, null);
});

test('the same pace limit as reactions, in either order', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'p:one', T0);
  assert.throws(() => sendEmoji(room, 'host', 'p:two', T0 + 100), /Slow down/);
  assert.throws(() => sendEmoji(room, 'host', 'fire', T0 + 100), /Slow down/);
  sendEmoji(room, 'host', 'p:two', T0 + EMOJI_INTERVAL_MS);
  assert.equal(view(room, 'guest', T0).opponent.emoji.seq, 2);
  assert.throws(() => sendEmoji(room, 'host', 'fire', T0 + EMOJI_INTERVAL_MS + 100), /Slow down/);
});

test('nobody to send it to yet is refused', () => {
  const room = createRoom('PKA123', 'Alice', T0, uuid);
  assert.throws(() => sendEmoji(room, 'host', 'p:one', T0), /Nobody to send it to/);
});

test('a takeover reaction blocks pack stickers too, as it blocks reactions', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'kylian', T0);
  assert.throws(() => sendEmoji(room, 'guest', 'p:one', T0 + 5000), /has the floor/);
});

test('normal reactions are unchanged', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'fire', T0);
  assert.deepEqual(view(room, 'guest', T0).opponent.emoji, { key: 'fire', seq: 1 });
  assert.throws(() => sendEmoji(room, 'host', 'bomb', T0 + EMOJI_INTERVAL_MS), /Unknown reaction/);
});
