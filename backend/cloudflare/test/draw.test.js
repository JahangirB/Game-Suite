import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DRAW } from '../src/wordlists.js';
import { TURNS, TURN_MS, cleanStroke, hint, isRight } from '../src/draw.js';
import {
  COUNTDOWN_MS, addStroke, checkTimers, clearDrawing, createRoom, drawGuess, joinRoom, nextDeadline,
  setConnected, setDrawReady, view, undoStroke, setOptions,
} from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;
const LINE = { points: [[0.1, 0.1], [0.5, 0.5]], color: 2, width: 1 };

function match() {
  const room = createRoom('DRAW1', 'Alice', T0, uuid, null, null, 'draw');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['draw']);
  setConnected(room, 'guest', true, T0);
  setDrawReady(room, 'host', true, T0, () => 0.1);
  setDrawReady(room, 'guest', true, T0, () => 0.1);
  return room;
}

test('the server draw words match the app asset', () => {
  const file = readFileSync(new URL('../../../app/src/main/assets/words/draw.txt', import.meta.url), 'utf8').trim().split('\n').join(',');
  assert.equal(DRAW, file);
  assert.equal(hint('ice cream'), '___ _____');
  assert.ok(isRight(' Castle! ', 'castle'));
});

test('only the drawer sees the word; the guesser gets the letter count', () => {
  const room = match();
  assert.equal(room.drawer, 'host');
  const host = view(room, 'host', GO).draw;
  const guest = view(room, 'guest', GO).draw;
  assert.equal(host.drawer, 'me');
  assert.equal(host.word, room.drawWord);
  assert.equal(guest.word, null);
  assert.equal(guest.hint.length, room.drawWord.length);
  assert.equal(guest.turn, 1);
});

test('strokes come from the drawer only, are checked, and clear', () => {
  const room = match();
  assert.throws(() => addStroke(room, 'host', LINE, T0), /not running/);
  assert.throws(() => addStroke(room, 'guest', LINE, GO), /not your turn/);
  assert.throws(() => addStroke(room, 'host', { points: [[2, 0]] }, GO), /Bad stroke/);
  const { index, stroke } = addStroke(room, 'host', LINE, GO);
  assert.equal(index, 0);
  assert.deepEqual(stroke, LINE);
  assert.equal(view(room, 'guest', GO).draw.strokes.length, 1);
  assert.equal(view(room, 'guest', GO).draw.clears, 0);
  clearDrawing(room, 'host');
  assert.equal(view(room, 'guest', GO).draw.strokes.length, 0);
  assert.equal(view(room, 'guest', GO).draw.clears, 1);
  assert.deepEqual(cleanStroke({ points: [[0.12345, 1]], color: 99, width: 7 }, 0), { points: [[0.123, 1]], color: 0, width: 1 });
});

test('a right guess scores the seconds left and swaps the drawer', () => {
  const room = match();
  const word = room.drawWord;
  drawGuess(room, 'guest', 'nope', GO + 1000);
  assert.deepEqual(view(room, 'host', GO).draw.guesses, [{ by: 'opponent', name: 'Bob', word: 'nope' }]);
  assert.throws(() => drawGuess(room, 'host', word, GO), /drawing/);
  drawGuess(room, 'guest', word.toUpperCase(), GO + 30_000);
  assert.equal(room.score, 50);
  assert.equal(room.drawer, 'guest');
  assert.equal(room.drawTurn, 2);
  const last = view(room, 'host', GO + 30_000).draw.lastTurn;
  assert.deepEqual(last, { word, guessed: true, points: 50, drewIt: true, drawerName: 'Alice', guesserName: 'Bob', guessedIt: false, strokes: [], rights: [] });
  assert.notEqual(room.drawWord, word);
  assert.equal(view(room, 'host', GO + 30_000).draw.strokes.length, 0);
});

test('a turn that runs out moves on, and the match ends after the last turn', () => {
  const room = match();
  let now = GO;
  for (let t = 1; t <= TURNS; t++) {
    assert.equal(nextDeadline(room), room.turnEndsAt);
    now = room.turnEndsAt;
    assert.equal(checkTimers(room, now), 'changed');
  }
  assert.equal(room.status, 'finished');
  assert.equal(room.endReason, 'done');
  assert.equal(room.winner, null);
  assert.equal(view(room, 'host', now).draw.score, 0);
  assert.equal(TURN_MS, 80_000);
});

test('with three players the drawer goes round the table and everyone else guesses', () => {
  const room = createRoom('DRAW3', 'Alice', T0, uuid, null, null, 'draw');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ['draw']), true, T0);
  for (const role of ['host', 'guest', 'p3']) setDrawReady(room, role, true, T0, () => 0);
  assert.equal(room.drawTurns, 6);
  assert.equal(room.drawer, 'host');
  drawGuess(room, 'p3', room.drawWord, GO + 1000);
  assert.equal(room.drawer, 'guest');
  assert.equal(view(room, 'host', GO + 1000).draw.lastTurn.guesserName, 'Cat');
  drawGuess(room, 'host', room.drawWord, GO + 2000);
  assert.equal(room.drawer, 'p3');
  assert.equal(view(room, 'guest', GO + 2000).draw.drawerName, 'Cat');
});

test('the brush kit: more colours, three sizes, a fill, and undo takes back a whole line', () => {
  assert.equal(cleanStroke({ points: [[0.2, 0.2]], color: 7, width: 3, fill: true }, 0).fill, true);
  assert.throws(() => cleanStroke({ points: [[0.2, 0.2], [0.3, 0.3]], fill: true }, 0), /Bad stroke/);
  assert.equal(cleanStroke({ points: [[0.2, 0.2]], color: 12, width: 9 }, 0).color, 0);
  const room = match();
  addStroke(room, 'host', { ...LINE, start: true }, GO);
  addStroke(room, 'host', { ...LINE, start: true }, GO);
  addStroke(room, 'host', LINE, GO);
  addStroke(room, 'host', LINE, GO);
  undoStroke(room, 'host');
  assert.equal(view(room, 'guest', GO).draw.strokes.length, 1);
  assert.equal(view(room, 'guest', GO).draw.clears, 1);
  assert.throws(() => undoStroke(room, 'guest'), /not your turn/);
});

test('a finished turn keeps its drawing for the replay', () => {
  const room = match();
  addStroke(room, 'host', { ...LINE, start: true }, GO);
  drawGuess(room, 'guest', room.drawWord, GO + 1000);
  const last = view(room, 'host', GO + 1000).draw.lastTurn;
  assert.equal(last.strokes.length, 1);
  assert.equal(view(room, 'host', GO + 1000).draw.strokes.length, 0);
});

test('the guess race for four: right guesses score 80, 60, 40 in order and the drawer 20 each', () => {
  const room = createRoom('DRAW4', 'Alice', T0, uuid, null, null, 'draw');
  setConnected(room, 'host', true, T0);
  for (const n of ['Bob', 'Cy', 'Di']) setConnected(room, joinRoom(room, n, null, T0, uuid, null, null, ['draw']), true, T0);
  setOptions(room, 'host', { mode: 'race' });
  for (const r of ['host', 'guest', 'p3', 'p4']) setDrawReady(room, r, true, T0, () => 0.1);
  const drawer = room.drawer;
  const guessers = ['host', 'guest', 'p3', 'p4'].filter((r) => r !== drawer);
  drawGuess(room, guessers[0], 'nope', GO);
  drawGuess(room, guessers[1], room.drawWord, GO);
  // The one who got it sees the word; the others don't yet.
  assert.equal(view(room, guessers[1], GO).draw.word, room.drawWord);
  assert.equal(view(room, guessers[0], GO).draw.word, null);
  assert.equal(view(room, guessers[0], GO).draw.guesses.find((g) => g.right).word, null);
  assert.throws(() => drawGuess(room, guessers[1], room.drawWord, GO), /already/);
  const word = room.drawWord;
  drawGuess(room, guessers[0], word, GO);
  drawGuess(room, guessers[2], word, GO);
  const scores = Object.fromEntries(view(room, drawer, GO).draw.scores.map((s) => [s.name, s.points]));
  assert.equal(scores[room.players[guessers[1]].name], 80);
  assert.equal(scores[room.players[guessers[0]].name], 60);
  assert.equal(scores[room.players[guessers[2]].name], 40);
  assert.equal(scores[room.players[drawer].name], 60);
  // Everyone got it: the next turn has begun, with the last one's names.
  assert.equal(view(room, drawer, GO).draw.turn, 2);
  assert.equal(view(room, drawer, GO).draw.lastTurn.rights.length, 3);
});
