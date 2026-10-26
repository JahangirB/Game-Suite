import test from 'node:test';
import assert from 'node:assert/strict';
import * as numbers from '../src/numbers.js';
import { COUNTDOWN_MS, createRoom, joinRoom, setConnected, setNumbersReady, setOptions, submitNumbers, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const ALL = ['numbers'];

// 3 + 4 = 7 across the top, 3 * 2 = 6 and 7 - 5 = 2 down the sides, 6 - 4 = 2 across the bottom.
const GRID = [
  '3', '+', '4', '=', '7',
  '*', '', '', '', '-',
  '2', '', '', '', '5',
  '=', '', '', '', '=',
  '6', '-', '4', '=', '2',
];
const PUZZLE = { w: 5, h: 5, grid: GRID, blanks: [2, 14, 20] };

test('sums are read across and down, and checked', () => {
  const p = numbers.checkPuzzle(PUZZLE);
  assert.equal(numbers.sums(p.grid, 5, 5).length, 4);
  assert.ok(numbers.holds(['8', '/', '2', '=', '4']));
  assert.ok(!numbers.holds(['8', '/', '0', '=', '0']));
  assert.deepEqual(numbers.puzzleView(p).bank, [4, 5, 6]);
  assert.equal(numbers.puzzleView(p).cells[2], '?');
});

test('a wrong or malformed puzzle is refused', () => {
  assert.throws(() => numbers.checkPuzzle({ ...PUZZLE, grid: GRID.map((t, i) => (i === 4 ? '8' : t)) }), /wrong/);
  assert.throws(() => numbers.checkPuzzle({ ...PUZZLE, grid: GRID.map((t, i) => (i === 6 ? '1' : t)) }), /not valid|sum/);
  assert.throws(() => numbers.checkPuzzle({ ...PUZZLE, blanks: [1] }), /blanks/);
  assert.throws(() => numbers.checkPuzzle({ ...PUZZLE, w: 3 }), /size/);
});

test('solved means every sum holds using the bank', () => {
  const p = numbers.checkPuzzle(PUZZLE);
  assert.ok(numbers.isSolved(p, ['4', '5', '6']));
  assert.ok(!numbers.isSolved(p, ['4', '6', '5']));
  assert.ok(!numbers.isSolved(p, ['4', '5', '']));
  assert.equal(numbers.progressOf(p, ['4', '', '']), 25);
});

test('the host sends the puzzle with Ready; the first to solve wins', () => {
  const room = createRoom('NUM1', 'Alice', T0, uuid, null, null, 'numbers');
  setConnected(room, 'host', true, T0);
  for (const name of ['Bob', 'Cat']) setConnected(room, joinRoom(room, name, null, T0, uuid, null, null, ALL), true, T0);
  assert.throws(() => setOptions(room, 'guest', { level: 'hard' }), /host/);
  setOptions(room, 'host', { level: 'hard' });
  assert.equal(view(room, 'p3', T0).options.level, 'hard');
  assert.throws(() => setNumbersReady(room, 'host', true, { ...PUZZLE, blanks: [] }, T0), /blanks/);
  assert.equal(room.players.host.ready, false);
  setNumbersReady(room, 'guest', true, null, T0);
  setNumbersReady(room, 'p3', true, null, T0);
  assert.equal(room.status, 'lobby');
  setNumbersReady(room, 'host', true, PUZZLE, T0);
  assert.equal(room.status, 'started');
  const now = T0 + COUNTDOWN_MS;
  const v = view(room, 'guest', now).numbers;
  assert.equal(v.puzzle.cells[14], '?');
  assert.equal(v.solution, null);
  assert.deepEqual(v.entries, ['', '', '']);
  assert.throws(() => submitNumbers(room, 'guest', ['x', '', ''], now), /Invalid/);
  submitNumbers(room, 'guest', ['4', '', ''], now);
  assert.equal(view(room, 'host', now).others[0].progress, 25);
  submitNumbers(room, 'p3', ['4', '5', '6'], now + 1);
  const end = view(room, 'guest', now + 2);
  assert.equal(end.status, 'finished');
  assert.equal(end.winnerName, 'Cat');
  assert.equal(end.numbers.solution[14], '5');
});
