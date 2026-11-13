import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPS, createRoom, joinRoom, setConnected, setOptions, setReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;
const NEW_APP = ['sudoku', 'sudoku-big', 'sudoku-opener'];
const OLD_APP = ['sudoku', 'sudoku-big'];

function room(guestGames, hostGames = NEW_APP) {
  const r = createRoom('OPEN12', 'Alice', T0, uuid, null, null, 'sudoku', hostGames);
  setConnected(r, 'host', true, T0);
  setConnected(r, joinRoom(r, 'Bob', null, T0, uuid, null, null, guestGames), true, T0);
  return r;
}

test('every room state lists what the server can do', () => {
  const r = room(NEW_APP);
  assert.deepEqual(view(r, 'host', T0).caps, CAPS);
  assert.ok(view(r, 'guest', T0).caps.includes('sudoku-opener'));
});

test('where everyone has the token, the opener’s level is the room’s and the others only tap Ready', () => {
  const r = room(NEW_APP);
  assert.equal(view(r, 'guest', T0).sudokuOpener, true);
  setOptions(r, 'host', { mode: 'race', shape: 'classic', level: 'hard' });
  assert.deepEqual(view(r, 'guest', T0).options, { mode: 'race', shape: 'classic', level: 'hard' });
  // The guest is ready with no level at all; the opener is ready with theirs.
  setReady(r, 'guest', true, null, T0);
  assert.equal(r.status, 'lobby');
  // A level the guest tapped is not used either.
  setReady(r, 'host', true, 'hard', T0, () => ({ puzzle: '0'.repeat(81), solution: '1'.repeat(81) }));
  assert.equal(r.status, 'started');
  assert.equal(r.difficulty, 'hard');
});

test('the others’ own level is ignored, and a rule call from an older app keeps the level', () => {
  const r = room(NEW_APP);
  setOptions(r, 'host', { level: 'expert' });
  setOptions(r, 'host', { mode: 'together' });
  assert.equal(view(r, 'host', T0).options.level, 'expert');
  setReady(r, 'host', true, 'expert', T0, (holes) => ({ puzzle: '0'.repeat(81), solution: '1'.repeat(81), holes }));
  setReady(r, 'guest', true, 'beginner', T0, (holes) => ({ puzzle: '0'.repeat(81), solution: '1'.repeat(81), holes }));
  assert.equal(r.difficulty, 'expert');
});

test('changing the level takes everyone’s Ready back', () => {
  const r = room(NEW_APP);
  setReady(r, 'guest', true, null, T0);
  assert.equal(r.players.guest.ready, true);
  setOptions(r, 'host', { level: 'medium' });
  assert.equal(r.players.guest.ready, false);
});

test('with one player on an older app, taps must match as before', () => {
  const r = room(OLD_APP);
  assert.equal(view(r, 'host', T0).sudokuOpener, false);
  assert.deepEqual(view(r, 'host', T0).options, { mode: 'race', shape: 'classic' });
  assert.throws(() => setReady(r, 'guest', true, null, T0), /Choose a difficulty/);
  setReady(r, 'host', true, 'easy', T0);
  setReady(r, 'guest', true, 'medium', T0);
  assert.equal(r.status, 'lobby');
  setReady(r, 'guest', true, 'easy', T0);
  assert.equal(r.status, 'started');
  assert.equal(r.difficulty, 'easy');
});

test('an older host with a newer guest is the old path too', () => {
  const r = room(NEW_APP, OLD_APP);
  assert.equal(view(r, 'guest', T0).sudokuOpener, false);
});

test('opening another game and coming back keeps what each app understands', async () => {
  const { openGame } = await import('../src/room.js');
  const r = room(NEW_APP);
  openGame(r, 'host', 'ttt', T0);
  openGame(r, 'host', 'sudoku', T0);
  assert.equal(view(r, 'guest', T0).sudokuOpener, true);
  assert.equal(r.players.guest.bigSudoku, true);
});

test('a player without the token joining mid-lobby clears Ready; when they leave, opener mode is back with the level', async () => {
  const { leave: leaveRoom } = await import('../src/room.js');
  const r = createRoom('MID123', 'Alice', T0, uuid, null, null, 'sudoku', NEW_APP);
  setConnected(r, 'host', true, T0);
  setConnected(r, joinRoom(r, 'Bob', null, T0, uuid, null, null, NEW_APP), true, T0);
  setOptions(r, 'host', { level: 'expert' });
  setReady(r, 'guest', true, null, T0);
  const cat = joinRoom(r, 'Cat', null, T0, uuid, null, null, OLD_APP);
  setConnected(r, cat, true, T0);
  assert.equal(r.players.guest.ready, false);
  assert.equal(view(r, 'host', T0).sudokuOpener, false);
  assert.equal(view(r, 'host', T0).options.level, undefined);
  leaveRoom(r, cat, T0);
  assert.equal(view(r, 'host', T0).sudokuOpener, true);
  assert.equal(view(r, 'host', T0).options.level, 'expert');
});

test('a rejoin from an app without the token drops opener mode', () => {
  const r = room(NEW_APP);
  const id = r.players.guest.id;
  joinRoom(r, 'Bob', id, T0, uuid, null, null, OLD_APP);
  assert.equal(view(r, 'host', T0).sudokuOpener, false);
});

test('a guest who opened the game is the opener', async () => {
  const { openGame } = await import('../src/room.js');
  const r = room(NEW_APP);
  openGame(r, 'guest', 'ttt', T0);
  openGame(r, 'guest', 'sudoku', T0);
  assert.equal(r.openedBy, 'guest');
  assert.throws(() => setOptions(r, 'host', { level: 'hard' }));
  setOptions(r, 'guest', { level: 'hard' });
  setReady(r, 'host', true, null, T0);
  setReady(r, 'guest', true, 'hard', T0, () => ({ puzzle: '0'.repeat(81), solution: '1'.repeat(81) }));
  assert.equal(r.status, 'started');
  assert.equal(r.difficulty, 'hard');
});
