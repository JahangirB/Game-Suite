// Ludo for two to four: roll, race your four tokens round the board and home, knock others back.
//
// Each player has a colour 0-3 (red, green, yellow, blue round the board; two players sit
// opposite). A token's progress: -1 in the yard, 0-50 round the 52-square ring (its colour's
// start is ring square colour * 13), 51-55 up its home column, 56 home.
//
// A turn is two moves: { roll: true } (the server rolls), then { token: i }. A 6 brings a token
// out of the yard and earns another roll, but a third 6 in a row ends the turn. Landing on
// another colour's token sends it back to its yard, except on the safe squares (the starts and
// the stars eight squares after them). A token must land exactly home. With no move to make,
// the turn passes by itself. First with all four home wins.

export const HOME = 56;
const RING = 52;
const SAFE = new Set([0, 8, 13, 21, 26, 34, 39, 47]);

/** Colours for [n] players: opposite corners for two. */
export const coloursFor = (n) => (n === 2 ? [0, 2] : [0, 1, 2, 3].slice(0, n));

export function start(random = Math.random, marks = ['h', 'g']) {
  const colours = coloursFor(marks.length);
  return {
    marks,
    colours: Object.fromEntries(marks.map((m, i) => [m, colours[i]])),
    tokens: Object.fromEntries(marks.map((m) => [m, [-1, -1, -1, -1]])),
    roll: null, sixes: 0, last: null,
  };
}

/** The ring square a token of [colour] at [progress] stands on, or null off the ring. */
export function square(colour, progress) {
  return progress >= 0 && progress <= 50 ? (colour * 13 + progress) % RING : null;
}

/** Which of [mark]'s tokens can move [roll] squares. */
export function movable(state, mark, roll) {
  return state.tokens[mark].map((p, i) => i).filter((i) => {
    const p = state.tokens[mark][i];
    if (p === -1) return roll === 6;
    return p !== HOME && p + roll <= HOME;
  });
}

const next = (state, mark) => state.marks[(state.marks.indexOf(mark) + 1) % state.marks.length];
const result = (turn) => ({ again: false, over: false, winner: null, reason: null, turn });

export function play(state, mark, move, random = Math.random) {
  if (move?.roll) {
    if (state.roll != null) throw new Error('Move a token first.');
    const roll = 1 + Math.floor(random() * 6);
    state.sixes = roll === 6 ? state.sixes + 1 : 0;
    state.last = { mark, roll };
    if (state.sixes === 3) {
      // Three sixes in a row: the turn is over.
      state.sixes = 0;
      state.last.thirdSix = true;
      return result(next(state, mark));
    }
    if (!movable(state, mark, roll).length) {
      state.sixes = 0;
      state.last.stuck = true;
      return result(roll === 6 ? mark : next(state, mark));
    }
    state.roll = roll;
    return { ...result(mark), again: true };
  }
  const roll = state.roll;
  if (roll == null) throw new Error('Roll the die first.');
  const i = Number(move?.token);
  if (!movable(state, mark, roll).includes(i)) throw new Error("That token can't move.");
  const tokens = state.tokens[mark];
  const from = tokens[i];
  const to = from === -1 ? 0 : from + roll;
  tokens[i] = to;
  state.roll = null;
  state.last = { mark, roll, token: i, from, to, captured: [] };
  // Knock back other colours' tokens on the square, unless it is safe.
  const sq = square(state.colours[mark], to);
  if (sq != null && !SAFE.has(sq)) {
    for (const m of state.marks) {
      if (m === mark) continue;
      state.tokens[m].forEach((p, j) => {
        if (square(state.colours[m], p) === sq) {
          state.tokens[m][j] = -1;
          state.last.captured.push(m);
        }
      });
    }
  }
  if (tokens.every((p) => p === HOME)) return { again: false, over: true, winner: mark, reason: 'home', turn: null };
  if (roll === 6) return { ...result(mark), again: true };
  state.sixes = 0;
  return result(next(state, mark));
}

/** The board as [mark] sees it; others show as the letters [seats] gives ('o', 'p', 'q'). */
export function view(state, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const last = state.last;
  return {
    colours: Object.fromEntries(state.marks.map((m) => [who(m), state.colours[m]])),
    tokens: Object.fromEntries(state.marks.map((m) => [who(m), state.tokens[m]])),
    roll: state.roll,
    // The tokens you may move with the roll you just made.
    movable: state.roll != null && state.last?.mark === mark ? movable(state, mark, state.roll) : [],
    lastMove: last ? {
      by: last.mark === mark ? 'me' : 'opponent', who: who(last.mark), roll: last.roll,
      token: last.token ?? null, from: last.from ?? null, to: last.to ?? null,
      captured: (last.captured || []).map(who), stuck: !!last.stuck, thirdSix: !!last.thirdSix,
    } : null,
  };
}
