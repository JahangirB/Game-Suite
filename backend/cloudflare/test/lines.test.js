// Lines of the player's own (cap "lines"). A line travels as a reaction under the key 'line' with
// its words in `text`. Today's app drops a reaction key it does not know, so it never shows one.
// The same rule and tests are in backend/test/code.test.js and the app's LocalLinesTest.
import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPS, EMOJI_INTERVAL_MS, EMOJIS, TAKEOVER_MS, createRoom, joinRoom, sendEmoji, setConnected, view } from '../src/room.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;
const EMOJI2 = '\u{1F600}';

function lobby() {
  const room = createRoom('LIN123', 'Alice', T0, uuid);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid), true, T0);
  return room;
}
const say = (room, text, now = T0) => { sendEmoji(room, 'host', 'line', now, text); return view(room, 'host', now).me.emoji; };

test('the server lists the cap', () => {
  assert.ok(CAPS.includes('lines'));
  assert.ok(view(lobby(), 'host', T0).caps.includes('lines'));
});

test('a line reaches the other player as a reaction under the key "line"', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'line', T0, 'Nice one');
  assert.deepEqual(view(room, 'guest', T0).opponent.emoji, { key: 'line', seq: 1, text: 'Nice one' });
  // Today's app reads the key against its reactions and drops one it does not know.
  assert.ok(!EMOJIS.includes('line'));
});

test('control characters are stripped, the ends trimmed, and a line with nothing left is refused', () => {
  const room = lobby();
  assert.equal(say(room, '  Hi\u0007 th\u0000ere\n\t ').text, 'Hi there');
  for (const empty of ['', '   ', '\u0007\u0000\n', undefined, null]) {
    assert.throws(() => sendEmoji(room, 'host', 'line', T0 + 10_000, empty), /Write something/, JSON.stringify(empty));
  }
  assert.equal(room.emojiSeq, 1, 'a refused line leaves no reaction');
});

test('at most 24 characters, counted as code points so an emoji is not split', () => {
  const room = lobby();
  let now = T0;
  const next = (text) => say(room, text, (now += EMOJI_INTERVAL_MS)).text;
  assert.equal(next('abcdefghij'.repeat(3)), 'abcdefghijabcdefghijabcd');
  // 23 letters and an emoji is exactly 24 code points: kept whole.
  assert.equal(next('a'.repeat(23) + EMOJI2 + 'zzz'), 'a'.repeat(23) + EMOJI2);
  // The emoji is the 25th: dropped, and never cut in half.
  assert.equal(next('a'.repeat(24) + EMOJI2), 'a'.repeat(24));
  // A space at the cut is trimmed away.
  assert.equal(next('a'.repeat(23) + ' tail'), 'a'.repeat(23));
});

test('the same pace limit as reactions, in either order', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'line', T0, 'One');
  assert.throws(() => sendEmoji(room, 'host', 'line', T0 + 100, 'Two'), /Slow down/);
  assert.throws(() => sendEmoji(room, 'host', 'fire', T0 + 100), /Slow down/);
  sendEmoji(room, 'host', 'line', T0 + EMOJI_INTERVAL_MS, 'Two');
  assert.equal(view(room, 'guest', T0).opponent.emoji.seq, 2);
  assert.throws(() => sendEmoji(room, 'host', 'fire', T0 + EMOJI_INTERVAL_MS + 100), /Slow down/);
});

test('a reaction without text is unchanged, and text sent with one is ignored', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'fire', T0);
  assert.deepEqual(view(room, 'guest', T0).opponent.emoji, { key: 'fire', seq: 1 });
  sendEmoji(room, 'host', 'clap', T0 + EMOJI_INTERVAL_MS, 'sneaky');
  assert.deepEqual(view(room, 'guest', T0).opponent.emoji, { key: 'clap', seq: 2 });
  assert.throws(() => sendEmoji(room, 'host', 'bomb', T0 + 10_000, 'x'), /Unknown/);
});

test('invisible format characters go, the joiner stays, and "a\\u202Eb\\u200Bc" becomes "abc"', () => {
  const room = lobby();
  let now = T0;
  const next = (text) => say(room, text, (now += EMOJI_INTERVAL_MS)).text;
  assert.equal(next('a‮b​c'), 'abc');
  assert.equal(next('­a؜b‎c‏d e f‪g⁠h⁤i⁦j⁩k﻿l'), 'abcdefghijkl');
  const family = '\u{1F468}‍\u{1F469}';
  assert.equal(next(family), family);
});

test('a text that is not a string is refused, and a huge one is only read to 256 units', () => {
  const room = lobby();
  let now = T0;
  for (const odd of [12345, { a: 1 }, ['x'], true]) {
    assert.throws(() => sendEmoji(room, 'host', 'line', (now += EMOJI_INTERVAL_MS), odd), /Write something/, JSON.stringify(odd));
  }
  assert.equal(say(room, 'a'.repeat(100000), (now += EMOJI_INTERVAL_MS)).text, 'a'.repeat(24));
  assert.throws(() => sendEmoji(room, 'host', 'line', (now += EMOJI_INTERVAL_MS), ' '.repeat(300) + 'hi'), /Write something/);
});

test('a lone high surrogate counts as one character, so all three servers keep 24', () => {
  assert.equal(say(lobby(), 'a'.repeat(23) + '\uD83D' + 'bbbb').text, 'a'.repeat(23) + '\uD83D');
});

test('a line is refused while Kylian Dictador has the floor, and a line takes nothing over', () => {
  const room = lobby();
  sendEmoji(room, 'host', 'kylian', T0);
  assert.throws(() => sendEmoji(room, 'guest', 'line', T0 + 5000, 'Nice one'), /floor/);
  assert.throws(() => sendEmoji(room, 'host', 'line', T0 + TAKEOVER_MS.kylian - 1, 'Nice one'), /floor/);
  sendEmoji(room, 'guest', 'line', T0 + TAKEOVER_MS.kylian, 'Nice one');
  assert.equal(view(room, 'host', T0).opponent.emoji.text, 'Nice one');
  // A line itself takes nothing over: the sender may be answered at once after the pace limit.
  sendEmoji(room, 'host', 'fire', T0 + TAKEOVER_MS.kylian + EMOJI_INTERVAL_MS);
});
