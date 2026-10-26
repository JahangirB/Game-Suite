import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ALLOWED, ANSWERS } from '../src/wordlists.js';
import { LEVELS, answers, isWord, randomAnswer, score } from '../src/words.js';
import { COUNTDOWN_MS, createRoom, guessWord, joinRoom, setConnected, setOptions, setWordsReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;

function race(answer = 'crane') {
  const room = createRoom('WORD1', 'Alice', T0, uuid, null, null, 'words');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['words']);
  setConnected(room, 'guest', true, T0);
  setWordsReady(room, 'host', true, T0);
  setWordsReady(room, 'guest', true, T0);
  room.answer = answer;
  return room;
}

test('the server word lists match the app assets', () => {
  const file = (name) => readFileSync(new URL(`../../../app/src/main/assets/words/${name}.txt`, import.meta.url), 'utf8').trim().split('\n').join(' ');
  assert.equal(ANSWERS, file('answers'));
  assert.equal(ALLOWED, file('allowed'));
  assert.ok(answers.length > 5000);
  assert.ok(answers.every(isWord));
  // Every level has secret words of its lengths.
  for (const [level, lengths] of Object.entries(LEVELS)) {
    for (let k = 0; k < 20; k++) assert.ok(lengths.includes(randomAnswer(Math.random, level).length), level);
  }
});

test('scoring marks greens first and counts repeated letters once per letter in the answer', () => {
  assert.equal(score('crane', 'crane'), 'ggggg');
  assert.equal(score('react', 'crane'), 'yygy.');
  assert.equal(score('speed', 'abide'), '..y.y');
  // Two e's guessed, one e left after the green: only one counts.
  assert.equal(score('eerie', 'there'), 'y.y.g');
  assert.equal(score('geese', 'those'), '...gg');
  assert.equal(score('llama', 'hello'), 'yy...');
});

test('guesses wait for the countdown and must be real words of the right length', () => {
  const room = race();
  assert.throws(() => guessWord(room, 'host', 'slate', T0), /countdown/);
  assert.throws(() => guessWord(room, 'host', 'abc', GO), /5-letter/);
  assert.throws(() => guessWord(room, 'host', 'zzzzz', GO), /word list/);
  guessWord(room, 'host', 'slate', GO);
  const guest = view(room, 'guest', GO).words;
  assert.deepEqual(guest.theirs, [{ score: score('slate', 'crane') }]);
  assert.equal(guest.answer, null);
  assert.equal(guest.length, 5);
  assert.equal(view(room, 'host', GO).words.mine[0].word, 'slate');
});

test("the host's level sets the length of the word", () => {
  const room = createRoom('WORD2', 'Alice', T0, uuid, null, null, 'words');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['words']);
  setConnected(room, 'guest', true, T0);
  setOptions(room, 'host', { level: 'expert' });
  assert.equal(view(room, 'guest', T0).options.level, 'expert');
  setWordsReady(room, 'host', true, T0);
  setWordsReady(room, 'guest', true, T0);
  assert.ok([7, 8].includes(room.answer.length));
  assert.equal(view(room, 'guest', GO).words.length, room.answer.length);
});

test('solving first ends the round when the other can no longer do better', () => {
  const room = race();
  guessWord(room, 'guest', 'slate', GO);
  guessWord(room, 'host', 'slate', GO);
  guessWord(room, 'host', 'crane', GO + 1000);
  // The guest has 1 guess; solving next would also take 2 tries but later, so the host wins now.
  assert.equal(room.status, 'finished');
  assert.equal(view(room, 'host', GO).winner, 'me');
  assert.equal(view(room, 'guest', GO).words.answer, 'crane');
  assert.equal(view(room, 'guest', GO).words.theirs[1].word, 'crane');
});

test('a slower solve in fewer tries still wins', () => {
  const room = race();
  guessWord(room, 'host', 'slate', GO);
  guessWord(room, 'host', 'react', GO + 100);
  guessWord(room, 'host', 'crane', GO + 200);
  assert.equal(room.status, 'started');
  guessWord(room, 'guest', 'crane', GO + 9000);
  assert.equal(view(room, 'guest', GO).winner, 'me');
});

test('nobody solving is a draw', () => {
  const room = race();
  for (const role of ['host', 'guest']) for (let i = 0; i < 6; i++) guessWord(room, role, 'slate', GO + i);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, null);
  assert.equal(room.endReason, 'draw');
});

test('three-way race: the round ends once nobody can beat the best solver', () => {
  const room = createRoom('WORD3', 'Alice', T0, uuid, null, null, 'words');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ['words']), true, T0);
  for (const role of ['host', 'guest', 'p3']) setWordsReady(room, role, true, T0);
  room.answer = 'crane';
  guessWord(room, 'guest', 'slate', GO);
  guessWord(room, 'guest', 'crane', GO + 1);
  // Alice and Cat (no guesses yet) could each still solve in one try.
  assert.equal(room.status, 'started');
  guessWord(room, 'p3', 'slate', GO + 2);
  assert.equal(room.status, 'started');
  guessWord(room, 'host', 'react', GO + 3);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, 'guest');
  const v = view(room, 'host', GO + 3).words;
  assert.equal(v.others.length, 2);
  assert.equal(v.others[0].guesses[1].word, 'crane');
});
