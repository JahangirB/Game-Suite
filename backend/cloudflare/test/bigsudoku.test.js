import test from 'node:test';
import assert from 'node:assert/strict';
import {
  COOP_LIVES_BIG, coopPlace, createRoom, joinRoom, setConnected, setOptions, setReady, submitBoard, view,
} from '../src/room.js';
import { checkPuzzle, fits, geometry } from '../src/shapes.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;
const NEW_APP = ['sudoku', 'sudoku-big'];

// A full board of [shape]: a pattern that's a valid Sudoku in every 9 x 9 window on the boxes.
function solved(shape) {
  const g = geometry(shape);
  let s = '';
  for (let i = 0; i < g.cells; i++) {
    const r = Math.floor(i / g.size), c = i % g.size;
    s += g.on[i] ? String((((r % 3) * 3 + Math.floor(r / 3) + c) % 9) + 1) : '-';
  }
  return s;
}
// The puzzle: every other on-board square emptied.
const holed = (sol) => [...sol].map((ch, i) => (ch !== '-' && i % 2 ? '0' : ch)).join('');

function room(players = 2, games = NEW_APP) {
  const r = createRoom('BIG123', 'Alice', T0, uuid, null, null, 'sudoku', NEW_APP);
  setConnected(r, 'host', true, T0);
  for (const name of ['Bob', 'Cat', 'Dan'].slice(0, players - 1)) setConnected(r, joinRoom(r, name, null, T0, uuid, null, null, games), true, T0);
  return r;
}

test('the shapes have their cells, and a puzzle is checked against them', () => {
  assert.equal(geometry('twin').playable, 153);
  assert.equal(geometry('stairs').playable, 225);
  assert.equal(geometry('cross').playable, 369);
  assert.equal(geometry('windmill').playable, 333);
  for (const shape of ['twin', 'stairs', 'cross', 'windmill']) {
    const sol = solved(shape);
    assert.ok(fits(shape, sol));
    assert.equal(checkPuzzle(shape, holed(sol), sol), null);
  }
  const sol = solved('twin');
  assert.match(checkPuzzle('twin', holed(sol), sol.replace('1', '2')), /breaks the rules/);
  assert.match(checkPuzzle('twin', sol, sol), /too few empty/);
  assert.match(checkPuzzle('cross', holed(sol), sol), /doesn.t fit/);
  const wrongGiven = holed(sol).replace(/[1-9]/, (d) => String((Number(d) % 9) + 1));
  assert.match(checkPuzzle('twin', wrongGiven, sol), /doesn.t match/);
  assert.match(checkPuzzle('classic', sol, sol), /Unknown/);
});

test('a big race starts with the puzzle from the opener’s phone', () => {
  const r = room();
  setOptions(r, 'host', { mode: 'race', shape: 'cross' });
  assert.deepEqual(view(r, 'host', T0).options, { mode: 'race', shape: 'cross' });
  const sol = solved('cross'), puzzle = holed(sol);
  // The guest is ready first; the round waits for the opener's puzzle.
  setReady(r, 'guest', true, 'medium', T0);
  assert.equal(r.status, 'lobby');
  assert.throws(() => setReady(r, 'host', true, 'medium', T0), /Update Pip/);
  assert.throws(() => setReady(r, 'host', true, 'medium', T0, undefined, { puzzle: sol, solution: sol }), /too few/);
  setReady(r, 'host', true, 'medium', T0, undefined, { puzzle, solution: sol });
  assert.equal(r.status, 'started');
  const v = view(r, 'guest', r.startAt);
  assert.equal(v.shape, 'cross');
  assert.equal(v.puzzle, puzzle);
  assert.equal(v.coop, null);
  // Boards must keep the shape; the solved board wins.
  assert.throws(() => submitBoard(r, 'guest', '1'.repeat(81), r.startAt), /Invalid board/);
  const half = [...puzzle].map((ch, i) => (ch === '0' && i < 200 ? sol[i] : ch)).join('');
  assert.equal(submitBoard(r, 'guest', half, r.startAt), true);
  assert.ok(r.players.guest.progress > 0 && r.players.guest.progress < 100);
  submitBoard(r, 'host', sol, r.startAt + 1000);
  assert.equal(r.status, 'finished');
  assert.equal(view(r, 'host', r.startAt + 1000).winner, 'me');
  assert.equal(view(r, 'host', r.startAt + 1000).solution, sol);
});

test('a big board needs everyone on the latest app', () => {
  const r = room(2, ['sudoku']);
  setOptions(r, 'host', { mode: 'race', shape: 'twin' });
  assert.throws(() => setReady(r, 'host', true, 'easy', T0, undefined, { puzzle: holed(solved('twin')), solution: solved('twin') }), /Bob needs the latest/);
  // Back on the classic board, they can play.
  setOptions(r, 'host', { mode: 'race', shape: 'classic' });
  setReady(r, 'host', true, 'easy', T0);
  setReady(r, 'guest', true, 'easy', T0);
  assert.equal(r.status, 'started');
  assert.equal(view(r, 'guest', r.startAt).shape, 'classic');
});

test('a level change waits for a new puzzle; changing the options drops the old one', () => {
  const r = room();
  setOptions(r, 'host', { mode: 'race', shape: 'twin' });
  const sol = solved('twin');
  setReady(r, 'host', true, 'easy', T0, undefined, { puzzle: holed(sol), solution: sol });
  setReady(r, 'guest', true, 'hard', T0);
  assert.equal(r.status, 'lobby');
  setOptions(r, 'host', { mode: 'together', shape: 'twin' });
  assert.equal(r.nextBig, null);
  setReady(r, 'guest', true, 'easy', T0);
  setReady(r, 'host', true, 'easy', T0, undefined, { puzzle: holed(sol), solution: sol });
  assert.equal(r.status, 'started');
});

test('together on a big board: anyone fills anywhere, five lives', () => {
  const r = room(3);
  setOptions(r, 'host', { mode: 'together', shape: 'stairs' });
  const sol = solved('stairs'), puzzle = holed(sol);
  setReady(r, 'guest', true, 'easy', T0);
  setReady(r, 'p3', true, 'easy', T0);
  setReady(r, 'host', true, 'easy', T0, undefined, { puzzle, solution: sol });
  const now = r.startAt;
  const v = view(r, 'p3', now).coop;
  assert.equal(v.lives, COOP_LIVES_BIG);
  assert.equal(v.maxLives, COOP_LIVES_BIG);
  assert.equal(v.board.length, 441);
  // Off-board squares can't be filled; any empty square anywhere can, by anyone.
  assert.equal(coopPlace(r, 'guest', 20, 1, now), false);
  const gaps = [...puzzle].flatMap((ch, i) => (ch === '0' ? [i] : []));
  const far = gaps[gaps.length - 1];
  assert.equal(coopPlace(r, 'guest', far, Number(sol[far]), now), true);
  assert.equal(coopPlace(r, 'p3', gaps[0], Number(sol[gaps[0]]), now), true);
  assert.equal(view(r, 'host', now).coop.board[far], sol[far]);
  coopPlace(r, 'host', gaps[1], (Number(sol[gaps[1]]) % 9) + 1, now);
  assert.equal(view(r, 'host', now).coop.lives, COOP_LIVES_BIG - 1);
  assert.throws(() => coopPlace(r, 'host', 441, 1, now), /Invalid/);
  // Fill the rest between the three of them: everyone wins.
  gaps.slice(1).forEach((cell, i) => { if (cell !== far) coopPlace(r, ['host', 'guest', 'p3'][i % 3], cell, Number(sol[cell]), now + i); });
  assert.equal(r.status, 'finished');
  assert.equal(r.endReason, 'solvedTogether');
  assert.equal(view(r, 'p3', now + 1000).winner, 'me');
});
