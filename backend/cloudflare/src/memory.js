// Memory Pairs: 16 face-down cards, 8 pairs of avatar pictures. On your turn flip two cards;
// a pair is yours and you go again, otherwise they stay up until the next flip (phones turn
// them back after a look) and the turn
// passes to the next player. When every pair is found, the most pairs wins (a tie is a draw). The server keeps
// the faces: players only see cards that are face up or already matched.

import { side, winnerOf } from './dots.js';

export const CARDS = 16;
/** The deck sizes to pick from: 4 x 4, 4 x 6 or 6 x 6. */
export const DECKS = [16, 24, 36];
const FACES = ['fox', 'cat', 'dog', 'panda', 'owl', 'frog', 'tiger', 'unicorn', 'robot', 'alien', 'lion', 'penguin'];

export const cleanOptions = (o) => ({ cards: DECKS.includes(Number(o?.cards)) ? Number(o.cards) : CARDS });

export function start(random = Math.random, marks = [], options = {}) {
  const cards = cleanOptions(options).cards;
  // Plain animals first; gold ones only once the plain ones run out.
  const plain = [...FACES].sort(() => random() - 0.5);
  const gold = FACES.map((f) => f + '*').sort(() => random() - 0.5);
  const faces = [...plain, ...gold].slice(0, cards / 2);
  const deck = [...faces, ...faces];
  for (let i = deck.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [deck[i], deck[j]] = [deck[j], deck[i]];
  }
  return { deck, owners: '.'.repeat(cards), up: [], last: null };
}

/** Flips { card } for `mark`. Returns { again, over, winner, reason }. */
export function play(state, mark, move) {
  const card = Number(move?.card);
  if (!Number.isInteger(card) || card < 0 || card >= state.deck.length) throw new Error('Pick a card.');
  if (state.owners[card] !== '.') throw new Error('That pair is already found.');
  // A pair that didn't match stays up until the next flip.
  if (state.up.length === 2) state.up = [];
  if (state.up.includes(card)) throw new Error('That card is already face up.');
  state.last = { mark, card };
  if (state.up.length === 0) {
    state.up = [card];
    return { again: true, over: false, winner: null, reason: null };
  }
  const first = state.up[0];
  if (state.deck[first] !== state.deck[card]) {
    state.up = [first, card];
    return { again: false, over: false, winner: null, reason: null };
  }
  state.up = [];
  for (const c of [first, card]) state.owners = state.owners.slice(0, c) + mark + state.owners.slice(c + 1);
  if (state.owners.includes('.')) return { again: true, over: false, winner: null, reason: null };
  return { again: false, over: true, ...winnerOf(state.owners), reason: 'most' };
}

export function view(state, mark, seats = {}) {
  return {
    // A card's picture when it is face up or matched, else null.
    faces: state.deck.map((face, i) => (state.owners[i] !== '.' || state.up.includes(i) ? face : null)),
    owners: side(state.owners, mark, seats),
    lastMove: state.last ? { by: state.last.mark === mark ? 'me' : 'opponent', card: state.last.card } : null,
  };
}
