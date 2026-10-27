// Battle rules for one room, as plain functions over a JSON-serialisable object so the
// Durable Object can store it and tests can run without Cloudflare. The rules match
// ../Code.gs; the difference is that "away" means a closed connection instead of
// missing polls.

import { generatePuzzle } from './sudoku.js';
import * as connect4 from './connect4.js';
import * as dots from './dots.js';
import * as memory from './memory.js';
import * as words from './words.js';
import * as draw from './draw.js';
import * as ttt from './ttt.js';
import * as dominoes from './dominoes.js';
import * as numbers from './numbers.js';
import * as clash from './clash.js';
import * as ludo from './ludo.js';
import * as grid from './grid.js';
import * as balloon from './balloon.js';
import * as codeGame from './code.js';
import * as slideGame from './slide.js';
import * as bingo from './bingo.js';
import * as golf from './golf.js';
import * as blocks from './blocks.js';

// Empty cells per level; must match Difficulty in the Android app.
export const DIFFICULTY_HOLES = { beginner: 30, easy: 38, medium: 46, hard: 50, expert: 54, master: 56 };
export const COUNTDOWN_MS = 5000;
// Avatar and reaction keys the app can show; anything else is ignored. Must match the app.
export const AVATARS = ['fox', 'cat', 'dog', 'panda', 'owl', 'frog', 'tiger', 'unicorn', 'robot', 'alien', 'lion', 'penguin'];
export const EMOJIS = ['thumbsup', 'clap', 'fire', 'laugh', 'wow', 'sweat', 'think', 'cool', 'heart', 'hurry'];
// At most one reaction per player this often.
export const EMOJI_INTERVAL_MS = 1500;
// A player disconnected this long during a match loses it.
export const INACTIVE_FORFEIT_MS = 45_000;
// A player disconnected this long outside a match is removed (a host closes the room).
export const LOBBY_ABANDON_MS = 5 * 60_000;
// A room with nobody connected is deleted after this long (1 minute once closed).
export const ROOM_IDLE_MS = 6 * 60 * 60_000;
// Turn games can go on across time zones, a move now and then: keep their rooms a week.
export const TURN_ROOM_IDLE_MS = 7 * 24 * 60 * 60_000;
const CLOSED_ROOM_MS = 60_000;

export const GAMES = ['sudoku', 'ships', 'connect4', 'dots', 'memory', 'words', 'draw', 'ttt', 'dominoes', 'numbers', 'hockey', 'clash', 'ludo', 'grid', 'balloon', 'code', 'slide', 'bingo', 'golf', 'blocks'];
// Seats in a room, in order. Two-player games use the first two.
export const ROLES = ['host', 'guest', 'p3', 'p4'];
// How many can play each game; anything not listed is for two.
export const MAX_PLAYERS = { sudoku: 4, words: 4, draw: 4, dots: 4, memory: 4, dominoes: 4, numbers: 4, clash: 4, ludo: 4, grid: 4, balloon: 4, slide: 4, bingo: 4, golf: 4, blocks: 4 };
// Each seat's one-letter mark on a turn game's board.
export const MARKS = { host: 'h', guest: 'g', p3: '3', p4: '4' };
const roleOfMark = (mark) => Object.keys(MARKS).find((r) => MARKS[r] === mark) || null;
export const maxPlayers = (game) => MAX_PLAYERS[game] || 2;
// What apps from before the version check could play; they don't say which games they know.
const OLD_APP_GAMES = ['sudoku', 'ships'];

export class GameError extends Error {
  /**
   * `fatal`: the room is gone for this player; the app returns to the start screen.
   * `reason`: a stable code the app can act on (not_found, closed, not_member, full, code_taken).
   */
  constructor(message, fatal = false, reason = null) {
    super(message);
    this.fatal = fatal;
    this.reason = reason;
  }
}

export function normalizeCode(code) {
  const clean = String(code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (clean.length < 4 || clean.length > 12) {
    throw new GameError('The secret code must be 4 to 12 letters or digits.', true, 'bad_code');
  }
  return clean;
}

export function cleanName(name) {
  return String(name || '').replace(/\s+/g, ' ').trim().slice(0, 16) || 'Player';
}

export function cleanAvatar(avatar) {
  return AVATARS.includes(avatar) ? avatar : null;
}

/** The phone's permanent id (used for invitations); anything odd is dropped. */
export function cleanDeviceId(deviceId) {
  return /^[A-Za-z0-9-]{8,64}$/.test(String(deviceId || '')) ? String(deviceId) : null;
}

function newPlayer(name, avatar, now, uuid, deviceId = null) {
  return {
    id: uuid(), name: cleanName(name), avatar: cleanAvatar(avatar), deviceId: cleanDeviceId(deviceId), emoji: null, emojiAt: 0, ready: false, difficulty: null, board: null,
    progress: 0, finishedAt: null, left: false, connected: false, awaySince: now,
  };
}

export function createRoom(code, hostName, now, uuid, avatar = null, deviceId = null, game = 'sudoku') {
  return {
    code, game: GAMES.includes(game) ? game : 'sudoku', createdAt: now, version: 0, emojiSeq: 0, difficulty: null, status: 'waiting', round: 0,
    puzzle: null, solution: null, startAt: null, winner: null, endReason: null,
    players: { host: newPlayer(hostName, avatar, now, uuid, deviceId), guest: null },
  };
}

/** The other player of a two-player game. */
export function otherRole(role) {
  return role === 'host' ? 'guest' : 'host';
}

/** Roles with a player in them (gone ones included until the lobby clears them). */
export function seated(room) {
  return ROLES.filter((r) => room.players[r]);
}

/** Players still in the room. */
export function present(room) {
  return seated(room).filter((r) => !room.players[r].left);
}

/** Players still playing this match: those it started with, minus any who left or timed out. */
export function playing(room) {
  return (room.inMatch || ['host', 'guest']).filter((r) => room.players[r] && !room.players[r].left && !room.players[r].out);
}

export function findRole(room, playerId) {
  if (!playerId) return null;
  for (const role of ROLES) {
    if (room.players[role] && room.players[role].id === playerId) return role;
  }
  return null;
}

/**
 * Joins as guest, or reconnects when `playerId` belongs to the room. Returns the role.
 * `games` lists the games the joining app knows (null for apps that predate the list).
 */
export function joinRoom(room, name, playerId, now, uuid, avatar = null, deviceId = null, games = null) {
  if (room.status === 'closed') throw new GameError('The host closed this battle.', true, 'closed');
  if (!(games || OLD_APP_GAMES).includes(room.game)) {
    throw new GameError('This game needs the latest Game Suite. Update the app to join.', true, 'update_needed');
  }
  const known = findRole(room, playerId);
  if (known) {
    const id = cleanDeviceId(deviceId);
    if (id) room.players[known].deviceId = id;
    return known;
  }
  if (playerId) throw new GameError('You are not part of this battle.', true, 'not_member');
  const max = maxPlayers(room.game);
  const seat = ROLES.slice(1, max).find((r) => !room.players[r] || room.players[r].left);
  if (!seat) {
    throw new GameError(max === 2 ? 'This battle already has two players.' : `This game is full: ${max} players.`, true, 'full');
  }
  if (room.status === 'started') throw new GameError('A match is being played. Join when it ends.', true, 'busy');
  room.players[seat] = newPlayer(name, avatar, now, uuid, deviceId);
  // Everyone gets ready again, so the newcomer is part of the next round.
  for (const r of seated(room)) Object.assign(room.players[r], { ready: false, difficulty: null });
  if (room.status === 'waiting') room.status = 'lobby';
  bump(room);
  return seat;
}

/** Throws unless there is someone to play with; used by every "ready". */
function checkCanReady(room) {
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('You can only get ready in the lobby or after a match.');
  }
  if (present(room).length < 2) {
    throw new GameError(seated(room).length > 1 ? 'Your opponent has left the battle.' : 'Waiting for an opponent to join.');
  }
}

/** Whether every player in the room is ready (and there are at least two). */
function allReady(room) {
  const roles = present(room);
  return roles.length >= 2 && roles.every((r) => room.players[r].ready);
}

/** Starts a match with everyone in the room; [fields] go on the room, [reset] on each player. */
function beginMatch(room, fields, reset = {}) {
  const roles = present(room);
  Object.assign(room, { round: room.round + 1, status: 'started', winner: null, endReason: null, inMatch: roles }, fields);
  // A copy each, so no two players share a list.
  for (const r of roles) Object.assign(room.players[r], { ready: false, difficulty: null, finishedAt: null, out: false }, structuredClone(reset));
}

/** Ready for a level (or not ready). The round starts when both pick the same level. */
export function setReady(room, role, ready, difficulty, now, generate = generatePuzzle) {
  checkCanReady(room);
  const me = room.players[role];
  if (ready === false) {
    me.ready = false;
    me.difficulty = null;
  } else {
    const level = String(difficulty || '').toLowerCase();
    if (!DIFFICULTY_HOLES[level]) throw new GameError('Choose a difficulty level.');
    me.ready = true;
    me.difficulty = level;
  }
  const levels = new Set(present(room).map((r) => room.players[r].difficulty));
  if (allReady(room) && levels.size === 1) startRound(room, now, generate);
  bump(room);
}

function startRound(room, now, generate) {
  // Everyone chose the same level; the host's choice stands for all.
  const difficulty = room.players.host.difficulty;
  const { puzzle, solution } = generate(DIFFICULTY_HOLES[difficulty]);
  beginMatch(room, { difficulty, puzzle, solution, startAt: now + COUNTDOWN_MS }, { board: puzzle, progress: 0 });
}


// ---------------------------------------------------------------------------------------
// Ship Battle: each player hides a fleet on a 10x10 sea; players take turns firing at the
// other's sea. A hit lets you fire again; the first to sink the whole fleet wins.

export const SEA = 10;
export const FLEET = [5, 4, 3, 3, 2];
export const FLEET_CELLS = FLEET.reduce((a, b) => a + b, 0);

/** The cells (y * 10 + x) a ship covers. */
export function shipCells(ship) {
  const cells = [];
  for (let i = 0; i < ship.len; i++) {
    cells.push(ship.horizontal ? ship.y * SEA + ship.x + i : (ship.y + i) * SEA + ship.x);
  }
  return cells;
}

/** Checks a fleet: the five classic ships, inside the sea, not overlapping. */
export function validateFleet(fleet) {
  if (!Array.isArray(fleet) || fleet.length !== FLEET.length) throw new GameError('Place all five ships.');
  const ships = fleet.map((s) => ({
    x: Number(s?.x), y: Number(s?.y), len: Number(s?.len), horizontal: !!s?.horizontal,
  }));
  const lengths = ships.map((s) => s.len).sort((a, b) => b - a);
  if (lengths.join() !== FLEET.join()) throw new GameError('The fleet must be ships of 5, 4, 3, 3 and 2.');
  const taken = new Set();
  for (const ship of ships) {
    const endX = ship.horizontal ? ship.x + ship.len - 1 : ship.x;
    const endY = ship.horizontal ? ship.y : ship.y + ship.len - 1;
    if (![ship.x, ship.y].every(Number.isInteger) || ship.x < 0 || ship.y < 0 || endX >= SEA || endY >= SEA) {
      throw new GameError('A ship is outside the sea.');
    }
    for (const cell of shipCells(ship)) {
      if (taken.has(cell)) throw new GameError('Ships cannot overlap.');
      taken.add(cell);
    }
  }
  return ships;
}

/** Ready with a fleet (or not ready). The battle starts when both fleets are in. */
export function setShipsReady(room, role, ready, fleet, now, random = Math.random) {
  checkCanReady(room);
  const me = room.players[role];
  if (ready === false) {
    me.ready = false;
    me.pendingFleet = null;
  } else {
    me.pendingFleet = validateFleet(fleet);
    me.ready = true;
  }
  if (allReady(room)) {
    beginMatch(room, { startAt: now, turn: random() < 0.5 ? 'host' : 'guest', lastShot: null });
    for (const p of [room.players.host, room.players.guest]) {
      Object.assign(p, { fleet: p.pendingFleet, pendingFleet: null, shots: [], progress: 0 });
    }
  }
  bump(room);
}

/** Fires at the opponent's sea. Returns { hit, sunk }. */
export function fire(room, role, x, y, now) {
  if (room.game !== 'ships' || room.status !== 'started') throw new GameError('The battle is not running.');
  if (room.turn !== role) throw new GameError("It's not your turn.");
  x = Number(x);
  y = Number(y);
  if (![x, y].every(Number.isInteger) || x < 0 || y < 0 || x >= SEA || y >= SEA) throw new GameError('Fire inside the sea.');
  const me = room.players[role];
  const enemy = room.players[otherRole(role)];
  if (me.shots.some((s) => s.x === x && s.y === y)) throw new GameError('You already fired there.');

  const cell = y * SEA + x;
  const ship = enemy.fleet.find((s) => shipCells(s).includes(cell));
  const hit = !!ship;
  me.shots.push({ x, y, hit });
  const sunk = hit && isSunk(ship, me.shots);
  const hits = me.shots.filter((s) => s.hit).length;
  me.progress = Math.floor((hits * 100) / FLEET_CELLS);
  room.lastShot = { by: role, x, y, hit, sunk };
  if (hits === FLEET_CELLS) {
    me.finishedAt = now;
    endMatch(room, role, 'sunk');
  } else if (!hit) {
    room.turn = otherRole(role);
  }
  bump(room);
  return { hit, sunk };
}

function isSunk(ship, shots) {
  return shipCells(ship).every((c) => shots.some((s) => s.hit && s.y * SEA + s.x === c));
}

function shipsView(room, role, status) {
  const me = room.players[role];
  const opponent = room.players[otherRole(role)];
  const last = room.lastShot;
  return {
    turn: status === 'playing' ? (room.turn === role ? 'me' : 'opponent') : null,
    // Your own fleet: the one in play, or the one you are ready with.
    myFleet: me.fleet || me.pendingFleet || [],
    myShots: me.shots || [],
    theirShots: opponent?.shots || [],
    // Enemy ships are revealed one by one as they sink, and all at the end.
    sunkEnemy: opponent?.fleet ? opponent.fleet.filter((s) => isSunk(s, me.shots || [])) : [],
    enemyFleet: status === 'finished' && opponent?.fleet ? opponent.fleet : null,
    lastShot: last ? { ...last, by: last.by === role ? 'me' : 'opponent' } : null,
  };
}

// ---------------------------------------------------------------------------------------
// Turn games (Connect Four, Dots & Boxes, Memory Pairs, Ultimate Tic-Tac-Toe): everyone gets
// ready, then players take turns round the table. Each game's own rules live in its module
// (start, play, view); the player who starts moves one seat on each round. A player may close
// the app between moves: turn games are never forfeited for being away, the room just waits.

const TURN_RULES = { connect4, dots, memory, ttt, dominoes, clash, ludo, balloon, golf, blocks };
export const TURN_GAMES = Object.keys(TURN_RULES);

/** Ready (or not ready) for the next round of a turn game; it starts when both are ready. */
export function setTurnReady(room, role, ready, now, random = Math.random) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) {
    const roles = present(room);
    let first = room.firstTurn ? nextOf(roles, room.firstTurn, roles) : roles[Math.floor(random() * roles.length)];
    const turnState = TURN_RULES[room.game].start(random, roles.map((r) => MARKS[r]), room.options || {});
    // Dominoes picks its own first player (the highest double).
    if (turnState.turn) first = roleOfMark(turnState.turn);
    beginMatch(room, { startAt: now, firstTurn: first, turn: first, turnState });
  }
  bump(room);
}

/** The host's choices for the next match (Dominoes: teams, drawing, points target). Everyone gets ready again. */
export function setOptions(room, role, options) {
  if (role !== 'host') throw new GameError('Only the host can change the rules.');
  if (room.status !== 'waiting' && room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('The rules can only change between matches.');
  }
  if (room.game === 'dominoes') room.options = dominoes.cleanOptions(options);
  else if (room.game === 'numbers') room.options = numbers.cleanOptions(options);
  else if (room.game === 'words') room.options = words.cleanOptions(options);
  else if (room.game === 'slide') room.options = slideGame.cleanOptions(options);
  else if (room.game === 'ttt') room.options = ttt.cleanOptions(options);
  else throw new GameError('This game has no options.');
  for (const r of seated(room)) room.players[r].ready = false;
  bump(room);
}

/** The seat after [role] in [order] (round the table) that is in [allowed]. */
function nextOf(order, role, allowed) {
  const all = ROLES.filter((r) => order.includes(r) || r === role);
  let i = all.indexOf(role);
  for (let n = 0; n < all.length; n++) {
    i = (i + 1) % all.length;
    if (allowed.includes(all[i])) return all[i];
  }
  return allowed[0];
}

/** Plays `move` for `role`: { col } in Connect Four, { line } in Dots & Boxes, { card } in Memory Pairs, { cell } in Ultimate Tic-Tac-Toe. */
export function makeMove(room, role, move, now) {
  if (!TURN_GAMES.includes(room.game) || room.status !== 'started') throw new GameError('The game is not running.');
  if (room.turn !== role) throw new GameError("It's not your turn.");
  let result;
  try {
    result = TURN_RULES[room.game].play(room.turnState, MARKS[role], move);
  } catch (err) {
    throw new GameError(err.message);
  }
  if (result.over) {
    const winner = roleOfMark(result.winner);
    if (winner) room.players[winner].finishedAt = now;
    endMatch(room, winner, winner ? result.reason : 'draw');
  } else if (result.turn) {
    room.turn = roleOfMark(result.turn);
  } else if (!result.again) {
    room.turn = nextOf(room.inMatch || present(room), role, playing(room));
  }
  bump(room);
}

function turnView(room, role, status) {
  // Everyone else's mark shows as 'o', 'p', 'q', in the order of the view's `others`.
  const seats = {};
  others(room, role).forEach((r, i) => { seats[MARKS[r]] = 'opq'[i]; });
  return {
    turn: status === 'playing' ? (room.turn === role ? 'me' : 'opponent') : null,
    turnName: status === 'playing' ? room.players[room.turn]?.name || null : null,
    ...(room.turnState ? TURN_RULES[room.game].view(room.turnState, MARKS[role], seats) : {}),
  };
}

// ---------------------------------------------------------------------------------------
// Word Race: a race like Sudoku. Both get ready, a countdown starts, and each guesses the same
// secret word on their own. Fewest tries wins; a tie goes to the faster player. The match ends
// as soon as the result is certain.

/** Ready (or not ready) for the next Word Race round; it starts when both are ready. */
export function setWordsReady(room, role, ready, now, random = Math.random) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) {
    beginMatch(room, { startAt: now + COUNTDOWN_MS, answer: words.randomAnswer(random, words.cleanOptions(room.options).level) }, { guesses: [], solved: false });
  }
  bump(room);
}

/** Air Hockey: goals to win a match. */
export const HOCKEY_GOALS = 7;

/** Air Hockey: ready (or not). The match starts when both are ready. */
export function setHockeyReady(room, role, ready, now) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) beginMatch(room, { startAt: now + COUNTDOWN_MS, hockey: { score: [0, 0] } });
  bump(room);
}

/**
 * Air Hockey: the host's phone runs the table and reports the score [host, guest] after each
 * goal. The puck itself never comes through here as room state: it goes phone to phone as
 * small "live" messages. First to HOCKEY_GOALS wins.
 */
export function hockeyScore(room, role, score, now) {
  if (room.game !== 'hockey' || room.status !== 'started' || !room.hockey) throw new GameError('The game is not running.');
  if (role !== 'host') throw new GameError('Only the host keeps the score.');
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  const next = Array.isArray(score) ? score.map(Number) : [];
  const old = room.hockey.score;
  if (next.length !== 2 || next.some((n, i) => !Number.isInteger(n) || n < old[i] || n > HOCKEY_GOALS)) {
    throw new GameError('Invalid score.');
  }
  room.hockey.score = next;
  const winner = next[0] >= HOCKEY_GOALS ? 'host' : next[1] >= HOCKEY_GOALS ? 'guest' : null;
  if (winner) {
    room.players[winner].finishedAt = now;
    endMatch(room, winner, 'goals');
  }
  bump(room);
}

function hockeyView(room, role) {
  const score = room.hockey?.score || [0, 0];
  const mine = role === 'host' ? 0 : 1;
  return { mine: score[mine], theirs: score[1 - mine], target: HOCKEY_GOALS };
}

/**
 * Letter Grid: ready (or not). The host's Ready carries the grid, rolled on their phone; the
 * round starts when everyone is ready and lasts grid.ROUND_MS.
 */
export function setGridReady(room, role, ready, letters, now) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (role === 'host') {
    if (ready === false) room.nextGrid = null;
    else {
      try {
        room.nextGrid = grid.checkLetters(letters);
      } catch (e) {
        room.players[role].ready = false;
        throw new GameError(e.message);
      }
    }
  }
  if (allReady(room) && room.nextGrid) {
    const startAt = now + COUNTDOWN_MS;
    const letters = room.nextGrid;
    room.nextGrid = null;
    beginMatch(room, { startAt, letters, gridEndsAt: startAt + grid.ROUND_MS, gridResults: null }, { found: [] });
  }
  bump(room);
}

/** Letter Grid: a word found by [role]. */
export function findWord(room, role, word, now) {
  if (room.game !== 'grid' || room.status !== 'started') throw new GameError('The game is not running.');
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  if (now >= room.gridEndsAt) throw new GameError("Time's up.");
  const me = room.players[role];
  if (!playing(room).includes(role)) throw new GameError('You are not in this round.');
  let w;
  try {
    w = grid.checkWord(room.letters, word);
  } catch (e) {
    throw new GameError(e.message);
  }
  if (me.found.includes(w)) throw new GameError('You already found that one.');
  me.found.push(w);
  bump(room);
}

/** Letter Grid: time is up; score everyone and name the winner (none on a tie for the top). */
function finishGrid(room) {
  const roles = room.inMatch.filter((r) => room.players[r]);
  const results = grid.score(Object.fromEntries(roles.map((r) => [r, room.players[r].found || []])));
  room.gridResults = results;
  const ranked = roles.slice().sort((a, b) => results[b].score - results[a].score);
  const top = ranked[0];
  const tie = ranked.length > 1 && results[ranked[1]].score === results[top].score;
  endMatch(room, tie ? null : top, tie ? 'draw' : 'score');
}

function gridView(room, role, status) {
  const show = status === 'playing' || status === 'finished';
  const results = room.gridResults;
  const sideOf = (r) => (r === role ? 'me' : 'other');
  return {
    letters: show ? room.letters : null,
    endsAt: room.gridEndsAt || null,
    mine: room.players[role].found || [],
    // Everyone else's count during the round, in the order of the view's `others`.
    counts: others(room, role).map((r) => (room.players[r].found || []).length),
    results: results ? [role, ...others(room, role)].filter((r) => results[r]).map((r) => ({ who: sideOf(r), name: room.players[r].name, ...results[r] })) : null,
  };
}

/** One guess at the secret word. */
export function guessWord(room, role, word, now) {
  if (room.game !== 'words' || room.status !== 'started') throw new GameError('The game is not running.');
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  const me = room.players[role];
  if (!playing(room).includes(role)) throw new GameError('You are not in this round.');
  if (me.finishedAt) throw new GameError('You are done with this word.');
  word = String(word || '').toLowerCase();
  const n = room.answer.length;
  if (word.length !== n || !/^[a-z]+$/.test(word)) throw new GameError(`Guess a ${n}-letter word.`);
  if (!words.isWord(word)) throw new GameError(`"${word.toUpperCase()}" isn't in the word list.`);
  const colours = words.score(word, room.answer);
  me.guesses.push({ word, score: colours });
  if (colours === 'g'.repeat(n)) {
    me.solved = true;
    me.finishedAt = now;
  } else if (me.guesses.length >= words.TRIES) {
    me.finishedAt = now;
  }
  decideWords(room);
  bump(room);
}

// ---------------------------------------------------------------------------------------
// Code Breaker: two players. After both are ready, each sets a secret code for the other; when
// both codes are in, a countdown starts and both race to crack the code they were given.

/** Code Breaker: ready (or not). The match starts with both setting their codes. */
export function setCodeReady(room, role, ready, now) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) {
    // No countdown yet: it starts once both codes are set.
    beginMatch(room, { startAt: now, codePhase: 'set' }, { code: null, guesses: [], solved: false });
  }
  bump(room);
}

/** Code Breaker: [role] sets their code, or (once both are set) guesses the other's. */
export function codeMove(room, role, text, now) {
  if (room.game !== 'code' || room.status !== 'started') throw new GameError('The game is not running.');
  const me = room.players[role];
  if (!playing(room).includes(role)) throw new GameError('You are not in this round.');
  let c;
  try {
    c = codeGame.cleanCode(text);
  } catch (e) {
    throw new GameError(e.message);
  }
  if (room.codePhase === 'set') {
    if (me.code) throw new GameError('Your code is set.');
    me.code = c;
    if (playing(room).every((r) => room.players[r].code)) {
      room.codePhase = 'guess';
      room.startAt = now + COUNTDOWN_MS;
    }
    bump(room);
    return;
  }
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  if (me.finishedAt) throw new GameError('You are done with this code.');
  const target = room.players[others(room, role).find((r) => playing(room).includes(r))]?.code;
  if (!target) throw new GameError('The game is not running.');
  const pegs = codeGame.score(c, target);
  me.guesses.push({ word: c, ...pegs });
  if (pegs.black === codeGame.PEGS) {
    me.solved = true;
    me.finishedAt = now;
  } else if (me.guesses.length >= codeGame.TRIES) {
    me.finishedAt = now;
  }
  decideWords(room);
  bump(room);
}

function codeView(room, role, status) {
  const me = room.players[role];
  const other = room.players[others(room, role)[0]];
  const over = status === 'finished';
  const rows = (p, withWords) => (p?.guesses || []).map((g) => (withWords ? g : { black: g.black, white: g.white }));
  return {
    phase: room.codePhase || 'set',
    tries: codeGame.TRIES,
    myCode: me.code || null,
    theySet: !!other?.code,
    mine: rows(me, true),
    theirs: rows(other, over),
    meDone: !!me.finishedAt,
    theyDone: !!other?.finishedAt,
    // The code you were cracking, once it's over.
    theirCode: over ? other?.code || null : null,
  };
}

// ---------------------------------------------------------------------------------------
// Slide Race: the host's Ready carries the shuffled start; everyone slides the same puzzle.

export function setSlideReady(room, role, ready, tiles, now) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  const size = slideGame.LEVELS[slideGame.cleanOptions(room.options).level];
  if (role === 'host') {
    if (ready === false) room.nextSlide = null;
    else {
      try {
        room.nextSlide = slideGame.checkStart(tiles, size);
      } catch (e) {
        room.players[role].ready = false;
        throw new GameError(e.message);
      }
    }
  }
  if (allReady(room) && room.nextSlide) {
    const start = room.nextSlide;
    room.nextSlide = null;
    beginMatch(room, { startAt: now + COUNTDOWN_MS, slideStart: start, slideSize: Math.round(Math.sqrt(start.length)) }, { moves: [], progress: slideGame.progressOf(start, Math.round(Math.sqrt(start.length))) });
  }
  bump(room);
}

/** Slide Race: [role]'s whole list of moves so far, comma separated. Returns true if it changed. */
export function slideMoves(room, role, text, now) {
  const me = room.players[role];
  if (room.game !== 'slide' || room.status !== 'started' || now < room.startAt || me.finishedAt) return false;
  const moves = String(text || '').split(',').filter((x) => x !== '').map(Number);
  if (moves.length === (me.moves || []).length) return false;
  let tiles;
  try {
    tiles = slideGame.replay(room.slideStart, room.slideSize, moves);
  } catch (e) {
    throw new GameError(e.message);
  }
  me.moves = moves;
  me.progress = slideGame.progressOf(tiles, room.slideSize);
  if (slideGame.isSolved(tiles, room.slideSize)) {
    me.finishedAt = now;
    endMatch(room, role, 'solved');
  }
  bump(room);
  return true;
}

function slideView(room, role, status) {
  const show = status === 'playing' || status === 'finished';
  return {
    level: slideGame.cleanOptions(room.options).level,
    size: room.slideSize || slideGame.LEVELS[slideGame.cleanOptions(room.options).level],
    start: show ? room.slideStart || null : null,
    moves: room.players[role].moves || [],
  };
}

// ---------------------------------------------------------------------------------------
// Bingo: a card each and one shared call order; the first to claim a full line wins.

/** Bingo: ready (or not); the server deals the cards and shuffles the calls. */
export function setBingoReady(room, role, ready, now, random = Math.random) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) {
    const startAt = now + COUNTDOWN_MS;
    beginMatch(room, { startAt, calls: bingo.shuffledCalls(random), bingoEndsAt: bingo.endsAt(startAt), bingoLine: null });
    for (const r of room.inMatch) room.players[r].card = bingo.dealCard(random);
  }
  bump(room);
}

/** Bingo: [role] claims a full line of numbers called so far. */
export function claimBingo(room, role, now) {
  if (room.game !== 'bingo' || room.status !== 'started') throw new GameError('The game is not running.');
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  if (!playing(room).includes(role)) throw new GameError('You are not in this round.');
  const me = room.players[role];
  const line = bingo.fullLine(me.card, room.calls.slice(0, bingo.calledCount(room.startAt, now)));
  if (!line) throw new GameError('No full line yet.');
  room.bingoLine = line;
  me.finishedAt = now;
  endMatch(room, role, 'bingo');
  bump(room);
}

function bingoView(room, role, status) {
  const show = status === 'playing' || status === 'finished';
  const winner = room.winner && room.players[room.winner];
  return {
    card: show ? room.players[role].card || null : null,
    calls: show ? room.calls || null : null,
    callMs: bingo.CALL_MS,
    endsAt: room.bingoEndsAt || null,
    // At the end: the winning card and its line.
    winCard: status === 'finished' && winner ? winner.card || null : null,
    line: status === 'finished' ? room.bingoLine || null : null,
  };
}

/** Ends the round once nobody can change the result any more. */
function decideWords(room) {
  const roles = playing(room);
  const p = (r) => room.players[r];
  // The best solver so far: fewest tries, then the fastest.
  const solvers = roles.filter((r) => p(r).solved)
    .sort((a, b) => p(a).guesses.length - p(b).guesses.length || p(a).finishedAt - p(b).finishedAt);
  const best = solvers[0];
  const open = roles.filter((r) => !p(r).finishedAt);
  if (!best) {
    if (!open.length) endMatch(room, null, 'draw');
    return;
  }
  // Someone still guessing could only win by solving in fewer tries than the best.
  if (open.every((r) => p(r).guesses.length + 1 >= p(best).guesses.length)) endMatch(room, best, 'solved');
}

// ---------------------------------------------------------------------------------------
// Draw & Guess: a team game. Each turn one player draws a secret word and the other guesses;
// a right guess adds the seconds left to the team score. The drawer swaps every turn.

/** Ready (or not ready) for a Draw & Guess match; it starts when both are ready. */
export function setDrawReady(room, role, ready, now, random = Math.random) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (allReady(room)) {
    const roles = present(room);
    beginMatch(room, {
      startAt: now + COUNTDOWN_MS, drawTurn: 0, drawTurns: draw.turnsFor(roles.length),
      drawer: roles[Math.floor(random() * roles.length)], score: 0, usedWords: [], lastTurn: null,
    });
    nextDrawTurn(room, room.startAt, random, true);
  }
  bump(room);
}

/** Starts the next turn (the next player draws), or ends the match after the last one. */
function nextDrawTurn(room, now, random = Math.random, first = false) {
  const roles = playing(room);
  if (room.drawTurn >= (room.drawTurns || draw.TURNS) || roles.length < 2) {
    endMatch(room, null, 'done');
    return;
  }
  room.drawTurn += 1;
  if (!first || !roles.includes(room.drawer)) {
    // Round the table in seat order, skipping anyone who has gone.
    const order = room.inMatch || roles;
    let i = order.indexOf(room.drawer);
    do i = (i + 1) % order.length; while (!roles.includes(order[i]));
    room.drawer = order[i];
  }
  room.drawWord = draw.pickWord(room.usedWords, random);
  room.usedWords.push(room.drawWord);
  Object.assign(room, { strokes: [], drawClears: 0, drawGuesses: [], turnEndsAt: now + draw.TURN_MS });
}

/** The drawer adds a stroke. Returns its index; it is sent to the guesser on its own, not as a new state. */
export function addStroke(room, role, stroke, now) {
  if (room.game !== 'draw' || room.status !== 'started' || now < room.startAt) throw new GameError('The game is not running.');
  if (room.drawer !== role) throw new GameError("It's not your turn to draw.");
  let clean;
  try {
    clean = draw.cleanStroke(stroke, room.strokes.length);
  } catch (err) {
    throw new GameError(err.message);
  }
  room.strokes.push(clean);
  return { index: room.strokes.length - 1, stroke: clean };
}

export function clearDrawing(room, role) {
  if (room.game !== 'draw' || room.status !== 'started') throw new GameError('The game is not running.');
  if (room.drawer !== role) throw new GameError("It's not your turn to draw.");
  room.strokes = [];
  // Counted so a phone can tell a cleared drawing from one that is still arriving.
  room.drawClears = (room.drawClears || 0) + 1;
  bump(room);
}

/** The guesser tries a word. A right one scores the seconds left and moves to the next turn. */
export function drawGuess(room, role, word, now, random = Math.random) {
  if (room.game !== 'draw' || room.status !== 'started' || now < room.startAt) throw new GameError('The game is not running.');
  if (room.drawer === role) throw new GameError('You are drawing this turn.');
  if (!playing(room).includes(role)) throw new GameError('You are not in this round.');
  const text = String(word || '').trim().slice(0, 30);
  if (!text) throw new GameError('Type a guess.');
  if (draw.isRight(text, room.drawWord)) {
    const points = Math.max(1, Math.ceil((room.turnEndsAt - now) / 1000));
    room.score += points;
    room.lastTurn = { word: room.drawWord, guessed: true, points, drawer: room.drawer, guesser: role };
    nextDrawTurn(room, now, random);
  } else {
    room.drawGuesses.push({ by: role, word: text.toLowerCase() });
  }
  bump(room);
}

function drawView(room, role, status) {
  const playing = status === 'playing' || status === 'countdown';
  const mine = room.drawer === role;
  const last = room.lastTurn;
  const nameOf = (r) => room.players[r]?.name || null;
  return {
    turn: room.drawTurn || 0,
    turns: room.drawTurns || draw.TURNS,
    drawer: playing ? (mine ? 'me' : 'opponent') : null,
    drawerName: playing ? nameOf(room.drawer) : null,
    word: playing && mine ? room.drawWord : null,
    hint: playing && room.drawWord ? draw.hint(room.drawWord) : null,
    endsAt: playing ? room.turnEndsAt : null,
    strokes: playing ? room.strokes || [] : [],
    clears: room.drawClears || 0,
    guesses: playing ? (room.drawGuesses || []).map((g) => ({ by: g.by === role ? 'me' : 'opponent', name: nameOf(g.by), word: g.word })) : [],
    score: room.score || 0,
    lastTurn: last ? {
      word: last.word, guessed: last.guessed, points: last.points, drewIt: last.drawer === role,
      drawerName: nameOf(last.drawer), guesserName: last.guesser ? nameOf(last.guesser) : null, guessedIt: last.guesser === role,
    } : null,
  };
}

function wordsView(room, role, status) {
  const me = room.players[role];
  const opponent = room.players[others(room, role)[0]];
  const over = status === 'finished';
  const showWords = status === 'playing' || over;
  return {
    tries: words.TRIES,
    length: room.answer ? room.answer.length : words.LEVELS[words.cleanOptions(room.options).level][0],
    level: words.cleanOptions(room.options).level,
    mine: showWords ? (me.guesses || []) : [],
    // The opponent's colours during the round; their words too once it is over.
    theirs: showWords && opponent ? (opponent.guesses || []).map((g) => (over ? g : { score: g.score })) : [],
    meDone: !!me.finishedAt,
    theyDone: !!opponent?.finishedAt,
    answer: over && room.answer ? room.answer : null,
    // Everyone else, in the order of the view's `others`.
    others: others(room, role).map((r) => {
      const p = room.players[r];
      return { guesses: showWords ? (p.guesses || []).map((g) => (over ? g : { score: g.score })) : [], done: !!p.finishedAt };
    }),
  };
}

/**
 * Number Cross: ready (or not). The host's Ready carries the next puzzle, built on their phone;
 * the match starts when everyone is ready.
 */
export function setNumbersReady(room, role, ready, puzzle, now) {
  checkCanReady(room);
  room.players[role].ready = ready !== false;
  if (role === 'host') {
    if (ready === false) room.nextPuzzle = null;
    else {
      try {
        room.nextPuzzle = numbers.checkPuzzle(puzzle);
      } catch (e) {
        room.players[role].ready = false;
        throw new GameError(e.message);
      }
    }
  }
  if (allReady(room) && room.nextPuzzle) {
    const p = room.nextPuzzle;
    room.nextPuzzle = null;
    beginMatch(room, { numbers: p, startAt: now + COUNTDOWN_MS }, { entries: p.blanks.map(() => ''), progress: 0 });
  }
  bump(room);
}

/** Number Cross: stores a player's entries (one per blank). Returns true if anything changed. */
export function submitNumbers(room, role, entries, now) {
  const player = room.players[role];
  const p = room.numbers;
  if (room.status !== 'started' || !p || now < room.startAt || player.finishedAt) return false;
  let clean;
  try {
    clean = numbers.cleanEntries(p, entries);
  } catch (e) {
    throw new GameError(e.message);
  }
  if (clean.join(',') === (player.entries || []).join(',')) return false;
  player.entries = clean;
  player.progress = numbers.progressOf(p, clean);
  if (numbers.isSolved(p, clean)) {
    player.finishedAt = now;
    endMatch(room, role, 'solved');
  }
  bump(room);
  return true;
}

function numbersView(room, role, status) {
  const p = room.numbers;
  const show = p && (status === 'countdown' || status === 'playing' || status === 'finished');
  return {
    level: numbers.cleanOptions(room.options).level,
    puzzle: show && status !== 'countdown' ? numbers.puzzleView(p) : null,
    entries: show ? room.players[role].entries || null : null,
    solution: status === 'finished' && p ? p.grid : null,
  };
}

/** Stores a player's board during a match. Returns true if anything changed. */
export function submitBoard(room, role, board, now) {
  const player = room.players[role];
  if (room.status !== 'started' || now < room.startAt || player.finishedAt) return false;
  board = String(board || '');
  if (board === player.board) return false;
  if (!/^[0-9]{81}$/.test(board)) throw new GameError('Invalid board.');
  for (let i = 0; i < 81; i++) {
    if (room.puzzle[i] !== '0' && board[i] !== room.puzzle[i]) {
      throw new GameError('Invalid board: a given number was changed.');
    }
  }
  player.board = board;
  player.progress = progressOf(board, room.puzzle, room.solution);
  if (board === room.solution) {
    player.finishedAt = now;
    endMatch(room, role, 'solved');
  }
  bump(room);
  return true;
}

/** Percentage of the empty cells that hold the correct digit. */
export function progressOf(board, puzzle, solution) {
  let empty = 0;
  let correct = 0;
  for (let i = 0; i < 81; i++) {
    if (puzzle[i] !== '0') continue;
    empty++;
    if (board[i] === solution[i]) correct++;
  }
  return empty === 0 ? 100 : Math.floor((correct * 100) / empty);
}

function endMatch(room, winnerRole, reason) {
  room.winner = winnerRole;
  room.endReason = reason;
  room.status = 'finished';
  for (const role of seated(room)) {
    room.players[role].ready = false;
    room.players[role].difficulty = null;
  }
}

/**
 * Takes [role] out of the match being played (they left or lost their connection). With one
 * player left, that player wins; with more, the match goes on without them.
 */
function dropOut(room, role, reason, now) {
  if (room.status !== 'started' || !playing(room).includes(role)) return;
  const rest = playing(room).filter((r) => r !== role);
  if (rest.length < 2) return endMatch(room, rest[0] || null, reason);
  // A dominoes, Colour Clash or Ludo game (or team) can't play on short: the match stops there.
  if (['dominoes', 'clash', 'ludo', 'balloon', 'golf', 'blocks'].includes(room.game)) return endMatch(room, null, 'left');
  room.players[role].out = true;
  if (room.game === 'words') decideWords(room);
  if (TURN_GAMES.includes(room.game) && room.turn === role) room.turn = nextOf(room.inMatch, role, playing(room));
  if (room.game === 'draw' && room.drawer === role) {
    room.lastTurn = { word: room.drawWord, guessed: false, points: 0, drawer: role };
    nextDrawTurn(room, now);
  }
}

export function leave(room, role, now = Date.now()) {
  dropOut(room, role, 'forfeit', now);
  if (role === 'host') {
    room.status = 'closed';
    room.players.host.left = true;
  } else if (room.status === 'finished' || room.status === 'started') {
    room.players[role].left = true;
  } else {
    room.players[role] = null;
    if (present(room).length < 2) room.status = 'waiting';
    for (const r of seated(room)) Object.assign(room.players[r], { ready: false, difficulty: null });
  }
  bump(room);
}

/** Shows a predefined reaction to the opponent. Throws when it is not allowed. */
export function sendEmoji(room, role, emoji, now) {
  if (!EMOJIS.includes(emoji)) throw new GameError('Unknown reaction.');
  if (present(room).length < 2) throw new GameError('Nobody to send it to yet.');
  const player = room.players[role];
  if (now - (player.emojiAt || 0) < EMOJI_INTERVAL_MS) throw new GameError('Slow down a little.');
  room.emojiSeq = (room.emojiSeq || 0) + 1;
  player.emoji = { key: emoji, seq: room.emojiSeq };
  player.emojiAt = now;
  bump(room);
}

export function setConnected(room, role, connected, now) {
  const player = room.players[role];
  if (!player) return;
  player.connected = connected;
  player.awaySince = connected ? null : now;
  bump(room);
}

/**
 * Applies time-based rules: forfeits for players away during a match and removal of
 * players away from a lobby. Returns 'changed', 'expired' (delete the room) or null.
 */
export function checkTimers(room, now) {
  const result = roomTimers(room, now);
  if (result) return result;
  if (needsStartPush(room) && now >= room.startAt) {
    room.startPushedRound = room.round;
    bump(room);
    return 'changed';
  }
  return null;
}

function roomTimers(room, now) {
  const roles = seated(room);
  const away = (r) => room.players[r].connected ? 0 : now - room.players[r].awaySince;

  if (roles.every((r) => !room.players[r].connected)) {
    const awayFor = Math.min(...roles.map(away));
    if (awayFor >= idleLimit(room)) return 'expired';
  }
  if (room.status === 'closed' || TURN_GAMES.includes(room.game)) return null;

  if (room.status === 'started' && room.game === 'grid' && now >= room.gridEndsAt) {
    finishGrid(room);
    bump(room);
    return 'changed';
  }
  if (room.status === 'started' && room.game === 'bingo' && now >= room.bingoEndsAt) {
    endMatch(room, null, 'draw');
    bump(room);
    return 'changed';
  }
  if (room.status === 'started' && room.game === 'draw' && now >= room.turnEndsAt) {
    room.lastTurn = { word: room.drawWord, guessed: false, points: 0, drawer: room.drawer };
    nextDrawTurn(room, now);
    bump(room);
    return 'changed';
  }
  if (room.status === 'started' && now >= room.startAt) {
    // The player away the longest loses; time before the start does not count.
    const gone = playing(room)
      .filter((r) => !room.players[r].connected && now - Math.max(room.players[r].awaySince, room.startAt) >= INACTIVE_FORFEIT_MS)
      .sort((a, b) => room.players[a].awaySince - room.players[b].awaySince);
    if (gone.length) {
      dropOut(room, gone[0], 'timeout', now);
      bump(room);
      return 'changed';
    }
    return null;
  }
  if (room.status !== 'started') {
    if (!room.players.host.connected && away('host') >= LOBBY_ABANDON_MS) {
      leave(room, 'host', now);
      return 'changed';
    }
    const gone = roles.find((r) => r !== 'host' && !room.players[r].left && !room.players[r].connected && away(r) >= LOBBY_ABANDON_MS);
    if (gone) {
      leave(room, gone, now);
      return 'changed';
    }
  }
  return null;
}

/** Games whose view hides the round (letters, tiles, card, puzzle) until the countdown ends. */
const REVEALED_AT_START = ['words', 'grid', 'slide', 'bingo', 'numbers'];

/**
 * True while a round of such a game has started but not yet been pushed at its start time.
 * Without that push a player sees the countdown end with nothing on the board until their
 * own next message brings a fresh view.
 */
function needsStartPush(room) {
  return room.status === 'started' && REVEALED_AT_START.includes(room.game) && room.startPushedRound !== room.round;
}

/** When checkTimers should run next, or null if nothing is pending. */
export function nextDeadline(room) {
  const roles = seated(room);
  const deadlines = [];
  if (roles.every((r) => !room.players[r].connected)) {
    const lastAway = Math.max(...roles.map((r) => room.players[r].awaySince));
    deadlines.push(lastAway + idleLimit(room));
  }
  if (needsStartPush(room)) deadlines.push(room.startAt);
  if (room.status === 'started' && room.game === 'draw') deadlines.push(room.turnEndsAt);
  if (room.status === 'started' && room.game === 'grid') deadlines.push(room.gridEndsAt);
  if (room.status === 'started' && room.game === 'bingo') deadlines.push(room.bingoEndsAt);
  if (room.status !== 'closed' && !TURN_GAMES.includes(room.game)) {
    for (const role of roles) {
      const player = room.players[role];
      if (player.connected || player.left) continue;
      if (room.status === 'started') {
        if (!playing(room).includes(role)) continue;
        deadlines.push(Math.max(player.awaySince, room.startAt) + INACTIVE_FORFEIT_MS);
      } else {
        deadlines.push(player.awaySince + LOBBY_ABANDON_MS);
      }
    }
  }
  return deadlines.length ? Math.min(...deadlines) : null;
}

function idleLimit(room) {
  if (room.status === 'closed') return CLOSED_ROOM_MS;
  return TURN_GAMES.includes(room.game) ? TURN_ROOM_IDLE_MS : ROOM_IDLE_MS;
}

/** Whether [role] shares the win: they won, or (Dominoes teams) their partner did. */
function wonWith(room, role) {
  if (room.winner === role) return true;
  const state = room.turnState;
  return room.game === 'dominoes' && !!state?.teams && dominoes.sameSide(state, MARKS[room.winner], MARKS[role]);
}

/** The winner's name, or both partners' names for a Dominoes team. */
function winnerNames(room) {
  return seated(room).filter((r) => wonWith(room, r)).map((r) => room.players[r].name).join(' & ') || null;
}

/** The other players as [role] sees them: seat order starting after their own seat. */
export function others(room, role) {
  const roles = seated(room);
  const i = roles.indexOf(role);
  return [...roles.slice(i + 1), ...roles.slice(0, i)];
}

function bump(room) {
  room.version = (room.version || 0) + 1;
}

/** What one player is allowed to see; the same shape the Apps Script backend sends. */
export function view(room, role, now) {
  let status = room.status;
  if (status === 'started') status = now < room.startAt ? 'countdown' : 'playing';
  const me = room.players[role];
  const otherRoles = others(room, role);
  const opponent = room.players[otherRoles[0]];
  const showPuzzle = status === 'countdown' || status === 'playing' || (status === 'finished' && room.round > 0);
  return {
    code: room.code,
    game: room.game || 'sudoku',
    version: room.version,
    role,
    status,
    difficulty: room.difficulty,
    round: room.round,
    startAt: room.startAt,
    puzzle: showPuzzle ? room.puzzle : null,
    solution: status === 'finished' ? room.solution : null,
    winner: room.winner ? (wonWith(room, role) ? 'me' : 'opponent') : null,
    winnerName: room.winner ? winnerNames(room) : null,
    endReason: room.endReason,
    maxPlayers: maxPlayers(room.game),
    options: room.game === 'dominoes' ? dominoes.cleanOptions(room.options) : room.game === 'numbers' ? numbers.cleanOptions(room.options) : room.game === 'words' ? words.cleanOptions(room.options) : room.game === 'slide' ? slideGame.cleanOptions(room.options) : room.game === 'ttt' ? ttt.cleanOptions(room.options) : null,
    // In Ship Battle both players' progress (share of the enemy fleet hit) is public.
    me: playerView(me, room, status === 'finished' || room.game === 'ships', now),
    opponent: opponent ? playerView(opponent, room, true, now) : null,
    // Everyone else in seat order; `opponent` is the first of them, for two-player games.
    others: otherRoles.map((r) => playerView(room.players[r], room, true, now)),
    ships: room.game === 'ships' ? shipsView(room, role, status) : null,
    turns: TURN_GAMES.includes(room.game) ? turnView(room, role, status) : null,
    words: room.game === 'words' ? wordsView(room, role, status) : null,
    draw: room.game === 'draw' ? drawView(room, role, status) : null,
    numbers: room.game === 'numbers' ? numbersView(room, role, status) : null,
    hockey: room.game === 'hockey' ? hockeyView(room, role) : null,
    grid: room.game === 'grid' ? gridView(room, role, status) : null,
    codeGame: room.game === 'code' ? codeView(room, role, status) : null,
    slide: room.game === 'slide' ? slideView(room, role, status) : null,
    bingo: room.game === 'bingo' ? bingoView(room, role, status) : null,
  };
}

function playerView(player, room, includeProgress, now) {
  return {
    name: player.name,
    avatar: player.avatar || null,
    deviceId: player.deviceId || null,
    // Latest reaction; the app shows it when seq goes up.
    emoji: player.emoji || null,
    ready: player.ready,
    difficulty: player.difficulty,
    // Your own progress stays hidden during a match so it can't be used to test guesses.
    progress: includeProgress ? player.progress : null,
    finishMs: player.finishedAt ? player.finishedAt - room.startAt : null,
    left: !!player.left,
    out: !!player.out,
    idleMs: player.connected ? 0 : Math.max(0, now - player.awaySince),
    // Whether the player has a live connection right now.
    online: !!player.connected,
  };
}
