import test from 'node:test';
import assert from 'node:assert/strict';
import { COUNTDOWN_MS, GAMES, MAX_PLAYERS, TURN_GAMES, checkTimers, createRoom, joinRoom, leave, makeMove, nextDeadline, setConnected, setOptions, setTurnReady, view } from '../src/room.js';
import { ends } from '../src/wordhuntmatch.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;
const ROLES = ['host', 'guest', 'p3'];

function room(options = { level: 'easy', theme: 'Animals', backwards: false, timer: 0 }, players = 3) {
  const r = createRoom('HUNT1', 'Alice', T0, uuid, null, null, 'hunt');
  setConnected(r, 'host', true, T0);
  for (const [i, name] of ['Bob', 'Cara'].slice(0, players - 1).entries()) {
    joinRoom(r, name, null, T0, uuid, null, null, ['hunt']);
    setConnected(r, ROLES[i + 1], true, T0);
  }
  setOptions(r, 'host', options);
  return r;
}
const begin = (r, random = () => 0.3, roles = ROLES) => roles.forEach((role) => setTurnReady(r, role, true, T0, random));
const drag = (w, reverse = false) => {
  const [[a, b], [c, d]] = ends(w);
  return reverse ? { r1: c, c1: d, r2: a, c2: b } : { r1: a, c1: b, r2: c, c2: d };
};
const word = (r, text) => r.turnState.grid.words.find((w) => w.word === text);
const turns = (r, role, now = GO + 1000) => view(r, role, now).turns;

test('Word Hunt is a game for 2 to 4 with no turns, and the opener sets its rules', () => {
  assert.ok(GAMES.includes('hunt') && TURN_GAMES.includes('hunt'));
  assert.equal(MAX_PLAYERS.hunt, 4);
  const r = room({ level: 'medium', theme: 'Food', timer: 5 });
  assert.deepEqual(view(r, 'guest', T0).options, { level: 'medium', theme: 'Food', backwards: true, timer: 5 });
  assert.equal(view(r, 'host', T0).maxPlayers, 4);
  assert.throws(() => setOptions(r, 'guest', { level: 'easy' }), /Only the host/);
  begin(r);
  assert.equal(r.turn, null);
  assert.equal(r.status, 'started');
  assert.equal(view(r, 'host', T0 + 1).status, 'countdown');
  assert.equal(view(r, 'host', GO + 1).turns.turn, null);
});

test('a seed sent by a phone is ignored; every round gets its own', () => {
  const r = room({ level: 'easy', theme: 'Animals', backwards: false, timer: 0, seed: 12345 });
  assert.equal(r.options.seed, undefined);
  assert.equal(JSON.stringify(view(r, 'host', T0)).includes('12345'), false);
  begin(r, () => 0.3);
  const first = r.turnState.seed;
  assert.equal(first, Math.floor(0.3 * 0x7fffffff));
  assert.ok(!JSON.stringify(ROLES.map((x) => view(r, x, GO + 1))).includes(String(first)), 'no view carries the seed');
  // finish it and play again: a new seed, a new grid
  for (const w of r.turnState.grid.words) makeMove(r, 'host', drag(w), GO + 10);
  assert.equal(r.status, 'finished');
  begin(r, () => 0.6);
  assert.equal(r.status, 'started');
  assert.notEqual(r.turnState.seed, first);
  assert.equal(r.turnState.seed, Math.floor(0.6 * 0x7fffffff));
  assert.equal(r.turnState.scores.h, 0);
});

test('nothing is shown or counted before the countdown ends', () => {
  const r = room();
  begin(r);
  assert.deepEqual(view(r, 'guest', T0 + 100).turns.rows, []);
  assert.equal(view(r, 'guest', GO).turns.rows.length, 10);
  assert.throws(() => makeMove(r, 'guest', drag(r.turnState.grid.words[0]), T0 + 100), /countdown/);
  assert.throws(() => makeMove(r, 'guest', { hint: true }, T0 + 100), /countdown/);
  assert.equal(r.turnState.endsAt, 0);
});

test('a full 3-player game: racing claims, a hint, and the winner', () => {
  const r = room();
  begin(r);
  const words = r.turnState.grid.words;
  // Two players claim the same word; the first one the server sees wins.
  makeMove(r, 'guest', drag(words[0], true), GO + 100);
  assert.throws(() => makeMove(r, 'host', drag(words[0]), GO + 100), { message: 'Already found by g' });
  assert.throws(() => makeMove(r, 'p3', drag(words[0], true), GO + 101), { message: 'Already found by g' });
  assert.throws(() => makeMove(r, 'host', { r1: 0, c1: 0, r2: 1, c2: 1 }, GO + 102), { message: 'Not a word here' });
  assert.equal(turns(r, 'host').scores.g, words[0].length);
  // A hint costs two points and only its player sees the cell.
  makeMove(r, 'p3', drag(words[1]), GO + 200);
  const before = turns(r, 'p3').scores['3'];
  makeMove(r, 'p3', { hint: true }, GO + 300);
  assert.equal(turns(r, 'p3').scores['3'], Math.max(0, before - 2));
  assert.equal(turns(r, 'p3').hints.length, 1);
  assert.deepEqual(turns(r, 'host').hints, []);
  assert.throws(() => makeMove(r, 'p3', { hint: true }, GO + 301), /No hints left/);
  // The rest go to the host.
  for (const w of words.slice(2)) makeMove(r, 'host', drag(w), GO + 400);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'host');
  assert.equal(r.endReason, 'all');
  const v = turns(r, 'guest');
  assert.equal(v.phase, 'over');
  assert.deepEqual(v.results.map((x) => x.mark), ['h', 'g', '3']);
  assert.throws(() => makeMove(r, 'guest', drag(words[0]), GO + 500), /not running/);
});

test('views never carry the place of a word that nobody has found', () => {
  const r = room();
  begin(r);
  const words = r.turnState.grid.words;
  makeMove(r, 'host', drag(words[0]), GO + 10);
  for (const role of ROLES) {
    const v = turns(r, role);
    for (const w of v.words) {
      if (w.foundBy === null) assert.deepEqual(Object.keys(w).sort(), ['foundBy', 'length', 'word']);
    }
    const text = JSON.stringify(view(r, role, GO + 20));
    assert.ok(!text.includes('"seed"'));
    assert.ok(text.includes(`"foundBy":"h"`));
  }
  // A late joiner or a reconnect sees the found words and the grid at once.
  assert.equal(turns(r, 'p3').words.find((w) => w.word === words[0].word).foundBy, 'h');
  assert.equal(turns(r, 'p3').rows.length, 10);
});

test('the timer ends the round through checkTimers and nextDeadline', () => {
  const r = room({ level: 'easy', theme: 'Animals', backwards: false, timer: 3 });
  begin(r);
  const endsAt = GO + 3 * 60_000;
  assert.equal(r.turnState.endsAt, endsAt);
  assert.equal(nextDeadline(r), GO); // first the push that shows the grid
  assert.equal(checkTimers(r, GO), 'changed');
  assert.equal(nextDeadline(r), endsAt);
  makeMove(r, 'guest', drag(r.turnState.grid.words[0]), GO + 5000);
  assert.equal(checkTimers(r, endsAt - 1), null);
  assert.equal(checkTimers(r, endsAt), 'changed');
  assert.equal(r.status, 'finished');
  assert.equal(r.endReason, 'time');
  assert.equal(r.winner, 'guest');
  assert.equal(turns(r, 'host', endsAt).winner, 'g');
});

test('the clock does not run while everyone is away, and a tie is a draw', () => {
  const r = room({ level: 'easy', theme: 'Animals', backwards: false, timer: 3 });
  begin(r);
  const endsAt = GO + 3 * 60_000;
  checkTimers(r, GO);
  ROLES.forEach((role) => setConnected(r, role, false, GO + 1000));
  assert.notEqual(nextDeadline(r), endsAt);
  ROLES.forEach((role) => setConnected(r, role, true, GO + 2000));
  assert.equal(nextDeadline(r), endsAt);
  checkTimers(r, endsAt);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, null);
  assert.equal(r.endReason, 'draw');
  assert.deepEqual(turns(r, 'host', endsAt).winners, ['h', 'g', '3']);
});

test('a claim that comes after the time is up ends the round', () => {
  const r = room({ level: 'easy', theme: 'Animals', backwards: false, timer: 3 });
  begin(r);
  const endsAt = GO + 3 * 60_000;
  makeMove(r, 'host', drag(r.turnState.grid.words[0]), GO + 1000);
  makeMove(r, 'guest', drag(r.turnState.grid.words[1]), endsAt + 1); // too late: the round ends, the claim does not count
  assert.equal(r.status, 'finished');
  assert.equal(r.endReason, 'time');
  assert.equal(r.winner, 'host');
  assert.equal(r.turnState.scores.g, 0);
});

test('a player who left cannot claim, and the others carry on', () => {
  const r = room();
  begin(r);
  leave(r, 'p3', GO + 10);
  assert.equal(r.status, 'started');
  assert.throws(() => makeMove(r, 'p3', drag(r.turnState.grid.words[0]), GO + 20), /not in this game/);
  makeMove(r, 'guest', drag(r.turnState.grid.words[0]), GO + 30);
  assert.equal(r.turnState.scores.g, r.turnState.grid.words[0].length);
});

test('when one of two leaves, the round ends as left and keeps the results and the leaver\'s score', () => {
  const r = room(undefined, 2);
  begin(r, () => 0.3, ['host', 'guest']);
  const words = r.turnState.grid.words;
  makeMove(r, 'guest', drag(words[0]), GO + 10);
  makeMove(r, 'host', drag(words[1]), GO + 20);
  leave(r, 'guest', GO + 30);
  assert.equal(r.status, 'finished');
  assert.equal(r.winner, 'host');
  assert.equal(r.turnState.over, true);
  assert.equal(r.turnState.reason, 'left');
  const t = view(r, 'host', GO + 40).turns;
  assert.equal(t.phase, 'over');
  assert.equal(t.scores.g, words[0].length);
  assert.deepEqual(t.results.map((x) => x.mark).sort(), ['g', 'h']);
  assert.equal(t.results.find((x) => x.mark === 'g').found, 1);
});
