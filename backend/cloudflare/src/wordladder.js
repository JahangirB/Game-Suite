// Word Ladder: a duel that changes one word into another. Each player secretly picks a word; a coin
// flip decides whose word is the START, the other is the TARGET, and the owner of the TARGET moves
// first. A move changes exactly one letter of the last word in the chain into a real word that is
// not already in it. Whoever's move spells the TARGET wins, whoever's word it was. A player with no
// legal move loses. With a turn timer, a turn that runs out passes to the other player.
//
// Picks must be in the ladder list (ladder.js); moves may be any real word of that length (the
// Word Race lists), so players aren't punished for words like CORD.
// Same rules as the app's LadderMatch.kt; test/wordladder.test.js and the app's test share
// app/src/test/resources/wordladder-cases.json.
//
// state: { length, timer (seconds, 0 = off), marks, picks: { h: 'COLD'|null, g: ... }, chain: [],
//          startOwner, targetOwner, turn, turnEndsAt (ms, 0 = none), over, winner, reason }

import { isWord } from './words.js';
import { blocklist, isLadderWord, pathExists } from './ladder.js';

export const LENGTHS = [4, 5, 6];
export const TIMERS = [0, 30, 60];

export function cleanOptions(o) {
  const length = Number(o?.length);
  const timer = Number(o?.timer);
  return { length: LENGTHS.includes(length) ? length : 5, timer: TIMERS.includes(timer) ? timer : 0 };
}

/** A real word a move may use: 4 to 6 letters (UPPERCASE), not blocklisted, in the Word Race lists. */
export const isRealWord = (word) => word.length >= 4 && word.length <= 6 && !blocklist.has(word) && isWord(word.toLowerCase());

const other = (state, mark) => state.marks.find((m) => m !== mark);

export function start(random = Math.random, marks = ['h', 'g'], options = {}) {
  const { length, timer } = cleanOptions(options);
  return {
    length, timer, marks: [...marks], picks: Object.fromEntries(marks.map((m) => [m, null])), chain: [],
    startOwner: null, targetOwner: null, turn: null, turnEndsAt: 0, over: false, winner: null, reason: null,
  };
}

/** The real, unused words one letter away from the last word in the chain. */
export function legalMoves(state) {
  const last = state.chain[state.chain.length - 1];
  const out = [];
  for (let i = 0; i < last.length; i++) {
    for (let c = 65; c < 91; c++) {
      const w = last.slice(0, i) + String.fromCharCode(c) + last.slice(i + 1);
      if (w !== last && !state.chain.includes(w) && isRealWord(w)) out.push(w);
    }
  }
  return out;
}

const turnEnd = (state, now) => (state.timer ? now + state.timer * 1000 : 0);

/** The player to move has no legal change: they lose. */
function endIfStuck(state) {
  if (legalMoves(state).length) return null;
  state.over = true;
  state.winner = other(state, state.turn);
  state.reason = 'stuck';
  state.turnEndsAt = 0;
  return { again: false, over: true, winner: state.winner, reason: 'stuck', turn: null };
}

/**
 * `mark` secretly picks { word }. Throws a reason the screen can show. Once both picks are in, the
 * coin flip (`random() < 0.5`: the first mark's word is the start) and the first move are set up.
 * Returns { revealed, over, winner, reason }.
 */
export function setPick(state, mark, move, random = Math.random, now = 0) {
  if (!state.marks.includes(mark)) throw new Error("You're not in this game.");
  if (state.chain.length) throw new Error('The game has started.');
  const word = String(move?.word ?? '').toUpperCase();
  if (word.length !== state.length) throw new Error(`Pick a ${state.length}-letter word.`);
  if (!isLadderWord(word) || blocklist.has(word)) throw new Error('Not a word we can use. Pick another.');
  const rival = state.picks[other(state, mark)];
  if (rival) {
    if (rival === word) throw new Error('Pick another word.');
    // The reason names only the sender's own word, so it never gives the other pick away.
    if (!pathExists(word, rival)) throw new Error(`No way from ${word} to the other word yet. Pick another.`);
  }
  state.picks[mark] = word;
  if (!rival) return { revealed: false, over: false, winner: null, reason: null, turn: null };
  const startOwner = random() < 0.5 ? state.marks[0] : state.marks[1];
  state.startOwner = startOwner;
  state.targetOwner = other(state, startOwner);
  state.chain = [state.picks[startOwner]];
  state.turn = state.targetOwner;
  state.turnEndsAt = turnEnd(state, now);
  const stuck = endIfStuck(state);
  return { revealed: true, over: false, winner: null, reason: null, turn: state.turn, ...stuck };
}

/** `mark` changes one letter: { word }. Returns { again, over, winner, reason ('target' or 'stuck') }. */
export function play(state, mark, move, now = 0) {
  if (!state.chain.length) throw new Error('Waiting for both words.');
  if (state.over) throw new Error('The game is over.');
  if (state.turn !== mark) throw new Error('Not your turn.');
  const word = String(move?.word ?? '').toUpperCase();
  const last = state.chain[state.chain.length - 1];
  const changed = word.length === last.length ? [...word].filter((c, i) => c !== last[i]).length : -1;
  if (changed !== 1) throw new Error('Change one letter');
  if (!isRealWord(word)) throw new Error('Not a word');
  if (state.chain.includes(word)) throw new Error('Already used');
  state.chain.push(word);
  if (word === state.picks[state.targetOwner]) {
    state.over = true;
    state.winner = mark;
    state.reason = 'target';
    state.turnEndsAt = 0;
    return { again: false, over: true, winner: mark, reason: 'target', turn: null };
  }
  state.turn = other(state, mark);
  state.turnEndsAt = turnEnd(state, now);
  return endIfStuck(state) ?? { again: false, over: false, winner: null, reason: null, turn: state.turn };
}

/** Call when the clock reaches `turnEndsAt`: the turn passes. Returns { passed, turn }. */
export function tick(state, now) {
  if (state.over || !state.turnEndsAt || now < state.turnEndsAt) return { passed: false, turn: state.turn };
  state.turn = other(state, state.turn);
  state.turnEndsAt = turnEnd(state, now);
  return { passed: true, turn: state.turn };
}

/** The game as `mark` sees it. Before the reveal, only `mark`'s own pick is in it. */
export function view(state, mark) {
  const revealed = state.chain.length > 0;
  const me = (m) => (m === mark ? 'me' : 'opponent');
  return {
    length: state.length,
    timer: state.timer,
    phase: state.over ? 'over' : revealed ? 'playing' : 'picking',
    myPick: state.picks[mark],
    opponentPicked: !!state.picks[other(state, mark)],
    start: revealed ? state.chain[0] : null,
    target: revealed ? state.picks[state.targetOwner] : null,
    startOwner: revealed ? me(state.startOwner) : null,
    targetOwner: revealed ? me(state.targetOwner) : null,
    chain: revealed ? [...state.chain] : [],
    turn: revealed && !state.over ? me(state.turn) : null,
    turnEndsAt: state.turnEndsAt,
    winner: state.over ? me(state.winner) : null,
    reason: state.reason,
  };
}
