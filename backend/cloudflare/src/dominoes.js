// Dominoes (double-six, 28 tiles) for two to four players, played to a points target.
//
// Options, set by the host before a match:
//   teams: with four players, partners sit opposite (seats 1 & 3 against 2 & 4) and share a score.
//   draw:  a player who can't play draws from the boneyard until they can (the "draw game");
//          off, they pass (the "block game"). With nothing left to draw, everyone passes.
//   target: points to win the match (50, 100 or 150).
//
// A round ends when someone plays their last tile ("domino") or nobody can play ("blocked").
// The round's winner (on a block: the lowest pip count, a tie scores nothing) scores the pips
// left in every opposing hand. The winner of a round starts the next one; the first round is
// started by whoever holds the highest double.
//
// A tile is [a, b]. The line runs left to right: its open ends are line[0][0] and line[last][1].

export const TARGETS = [50, 100, 150];

export function cleanOptions(options) {
  const target = Number(options?.target);
  return { teams: !!options?.teams, draw: options?.draw !== false, target: TARGETS.includes(target) ? target : 100 };
}

function fullSet() {
  const tiles = [];
  for (let a = 0; a <= 6; a++) for (let b = a; b <= 6; b++) tiles.push([a, b]);
  return tiles;
}

const pips = (hand) => hand.reduce((sum, [a, b]) => sum + a + b, 0);

function shuffle(tiles, random) {
  for (let i = tiles.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [tiles[i], tiles[j]] = [tiles[j], tiles[i]];
  }
  return tiles;
}

/** Which side [mark] scores for: its team ('A' or 'B') or, without teams, the mark itself. */
export function sideOf(state, mark) {
  return state.teams ? 'AB'[state.marks.indexOf(mark) % 2] : mark;
}

/** Whether two marks score together (the same player, or partners). */
export function sameSide(state, a, b) {
  return sideOf(state, a) === sideOf(state, b);
}

/** Deals a new round; returns the mark that starts it. */
function deal(state, random, starter) {
  const tiles = shuffle(fullSet(), random);
  // The draw game deals five each to three or four players, so there is something to draw.
  const each = state.draw && state.marks.length > 2 ? 5 : 7;
  state.hands = {};
  for (const m of state.marks) state.hands[m] = tiles.splice(0, each);
  // In the block game the undealt tiles are out of play.
  state.boneyard = state.draw ? tiles : [];
  state.line = [];
  state.passes = 0;
  state.last = null;
  state.roundNo = (state.roundNo || 0) + 1;
  if (starter) return starter;
  // First round: the highest double starts; with no double dealt, the heaviest tile.
  let best = null;
  for (const m of state.marks) {
    for (const [a, b] of state.hands[m]) {
      const rank = (a === b ? 100 : 0) + a + b;
      if (!best || rank > best.rank) best = { rank, mark: m };
    }
  }
  return best.mark;
}

export function start(random = Math.random, marks = ['h', 'g'], options = {}) {
  const opts = cleanOptions(options);
  const state = { marks, teams: opts.teams && marks.length === 4, draw: opts.draw, target: opts.target, scores: {}, lastRound: null };
  for (const m of marks) state.scores[sideOf(state, m)] = 0;
  state.turn = deal(state, random, null);
  return state;
}

/** The ends a tile can go on: 'left', 'right' (both on an empty line), or none. */
export function fits(line, [a, b]) {
  if (!line.length) return ['right'];
  const left = line[0][0];
  const right = line[line.length - 1][1];
  const ends = [];
  if (a === left || b === left) ends.push('left');
  if (a === right || b === right) ends.push('right');
  return ends;
}

const canPlay = (state, mark) => state.hands[mark].some((t) => fits(state.line, t).length > 0);

/**
 * Plays for [mark]: { tile, end } puts hand tile number [tile] on an end, { draw: true } draws
 * from the boneyard, { pass: true } passes. Returns { again, over, winner, reason, turn }.
 */
export function play(state, mark, move, random = Math.random) {
  const hand = state.hands[mark];
  if (move?.draw) {
    if (!state.draw) throw new Error('This game has no drawing: pass instead.');
    if (canPlay(state, mark)) throw new Error('You have a tile you can play.');
    if (!state.boneyard.length) throw new Error('The boneyard is empty: pass instead.');
    hand.push(state.boneyard.pop());
    state.last = { mark, drew: true };
    return { again: true, over: false, winner: null, reason: null };
  }
  if (move?.pass) {
    if (canPlay(state, mark)) throw new Error('You have a tile you can play.');
    if (state.draw && state.boneyard.length) throw new Error('Draw from the boneyard first.');
    state.passes += 1;
    state.last = { mark, passed: true };
    if (state.passes >= state.marks.length) return endRound(state, null, random);
    return { again: false, over: false, winner: null, reason: null };
  }
  const index = Number(move?.tile);
  if (!Number.isInteger(index) || index < 0 || index >= hand.length) throw new Error('Pick one of your tiles.');
  const tile = hand[index];
  const ends = fits(state.line, tile);
  if (!ends.length) throw new Error("That tile doesn't match either end.");
  const end = ends.includes(move?.end) ? move.end : ends[0];
  const [a, b] = tile;
  if (!state.line.length) {
    state.line.push([a, b]);
  } else if (end === 'left') {
    const left = state.line[0][0];
    state.line.unshift(b === left ? [a, b] : [b, a]);
  } else {
    const right = state.line[state.line.length - 1][1];
    state.line.push(a === right ? [a, b] : [b, a]);
  }
  hand.splice(index, 1);
  state.passes = 0;
  state.last = { mark, tile: [a, b], end };
  if (!hand.length) return endRound(state, mark, random);
  return { again: false, over: false, winner: null, reason: null };
}

/** Scores a finished round ([winner] played out, or null for a block) and deals the next. */
function endRound(state, winner, random) {
  const blocked = !winner;
  if (blocked) {
    // The side with the fewest pips wins; a tie for the fewest scores nothing.
    const totals = {};
    for (const m of state.marks) totals[sideOf(state, m)] = (totals[sideOf(state, m)] || 0) + pips(state.hands[m]);
    const ranked = Object.entries(totals).sort((x, y) => x[1] - y[1]);
    if (ranked.length < 2 || ranked[0][1] !== ranked[1][1]) {
      const side = ranked[0][0];
      // The lightest hand on that side stands for it.
      winner = state.marks.filter((m) => sideOf(state, m) === side).sort((x, y) => pips(state.hands[x]) - pips(state.hands[y]))[0];
    }
  }
  let points = 0;
  if (winner) {
    const side = sideOf(state, winner);
    for (const m of state.marks) if (sideOf(state, m) !== side) points += pips(state.hands[m]);
    state.scores[side] += points;
  }
  state.lastRound = { winner, points, blocked, hands: Object.fromEntries(state.marks.map((m) => [m, state.hands[m]])) };
  if (winner && state.scores[sideOf(state, winner)] >= state.target) {
    return { again: false, over: true, winner, reason: 'points' };
  }
  // The round's winner starts the next; after a drawn block, the player after the last to pass.
  const next = winner || state.marks[(state.marks.indexOf(state.last.mark) + 1) % state.marks.length];
  const turn = deal(state, random, next);
  return { again: false, over: false, winner: null, reason: null, turn };
}

/** The game as [mark] sees it; others show as the letters [seats] gives ('o', 'p', 'q'). */
export function view(state, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const scoreOf = (m) => state.scores[sideOf(state, m)] || 0;
  const last = state.last;
  const round = state.lastRound;
  return {
    line: state.line,
    hand: state.hands[mark] || [],
    // How many tiles each player holds, and their (or their team's) score, by letter.
    counts: Object.fromEntries(state.marks.map((m) => [who(m), state.hands[m].length])),
    scores: Object.fromEntries(state.marks.map((m) => [who(m), scoreOf(m)])),
    // With teams: which letters are on your side.
    partners: state.teams ? state.marks.filter((m) => m !== mark && sameSide(state, m, mark)).map(who) : [],
    boneyard: state.boneyard.length,
    teams: state.teams,
    draw: state.draw,
    target: state.target,
    roundNo: state.roundNo,
    lastMove: last ? {
      by: last.mark === mark ? 'me' : 'opponent', who: who(last.mark), tile: last.tile || null, end: last.end || null,
      drew: !!last.drew, passed: !!last.passed,
    } : null,
    // The round just finished: who won it, the points, and everyone's leftover tiles.
    lastRound: round ? {
      winner: round.winner ? who(round.winner) : null, points: round.points, blocked: round.blocked,
      hands: Object.fromEntries(Object.entries(round.hands).map(([m, h]) => [who(m), h])),
    } : null,
  };
}
