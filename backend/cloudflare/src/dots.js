// Dots & Boxes. Two to four players take turns drawing a line between two neighbouring dots;
// closing a box claims it and draws again. When every box is claimed, the most boxes wins (a
// tie for the most is a draw).
//
// Boards are w × h boxes: the usual 4 × 4, 5 × 5, 6 × 6, or a heart with gaps where there are
// no boxes. Lines are numbered: horizontal ones first (row r 0..h, column c: r * w + c), then
// vertical ones (row r, column c 0..w: H + r * (w + 1) + c, H = (h + 1) * w). Boxes are
// r * w + c. A gap is ' ' in the boxes; a line next to no box is ' ' in the lines.

const rect = (w, h) => ({ w, h, mask: Array(h).fill('#'.repeat(w)) });
export const BOARDS = {
  '4x4': rect(4, 4),
  '5x5': rect(5, 5),
  '6x6': rect(6, 6),
  // Missing corners and a notch at the top.
  heart: { w: 7, h: 6, mask: [' ## ## ', '#######', '#######', ' ##### ', '  ###  ', '   #   '] },
};
export const cleanOptions = (o) => ({ board: BOARDS[o?.board] ? o.board : '4x4' });

/** A board's shape with its line counts. */
export function geometry(key = '4x4') {
  const b = BOARDS[key] || BOARDS['4x4'];
  const H = (b.h + 1) * b.w;
  return { ...b, key: BOARDS[key] ? key : '4x4', H, lines: H + b.h * (b.w + 1) };
}

const G4 = geometry('4x4');
export const BOXES = 4;
export const LINES = 40;

const exists = (g, box) => g.mask[Math.floor(box / g.w)][box % g.w] === '#';

/** The four lines around box (r, c). */
export function boxLines(box, g = G4) {
  const r = Math.floor(box / g.w);
  const c = box % g.w;
  return [r * g.w + c, (r + 1) * g.w + c, g.H + r * (g.w + 1) + c, g.H + r * (g.w + 1) + c + 1];
}

/** The one or two (existing) boxes next to a line. */
export function lineBoxes(line, g = G4) {
  if (line < g.H) {
    const r = Math.floor(line / g.w);
    const c = line % g.w;
    return [r - 1, r].filter((row) => row >= 0 && row < g.h).map((row) => row * g.w + c).filter((b) => exists(g, b));
  }
  const r = Math.floor((line - g.H) / (g.w + 1));
  const c = (line - g.H) % (g.w + 1);
  return [c - 1, c].filter((col) => col >= 0 && col < g.w).map((col) => r * g.w + col).filter((b) => exists(g, b));
}

export function start(random = Math.random, marks = [], options = {}) {
  const g = geometry(cleanOptions(options).board);
  let lines = '';
  for (let l = 0; l < g.lines; l++) lines += lineBoxes(l, g).length ? '.' : ' ';
  let boxes = '';
  for (let b = 0; b < g.w * g.h; b++) boxes += exists(g, b) ? '.' : ' ';
  return { board: g.key, lines, boxes, last: null };
}

/** Draws { line } for `mark`. Returns { again, over, winner, reason }. */
export function play(state, mark, move) {
  const g = geometry(state.board);
  const line = Number(move?.line);
  if (!Number.isInteger(line) || line < 0 || line >= g.lines || state.lines[line] === ' ') throw new Error('Pick a line between two dots.');
  if (state.lines[line] !== '.') throw new Error('That line is already drawn.');
  state.lines = state.lines.slice(0, line) + mark + state.lines.slice(line + 1);
  state.last = { mark, line };
  let closed = 0;
  for (const box of lineBoxes(line, g)) {
    if (state.boxes[box] === '.' && boxLines(box, g).every((l) => state.lines[l] !== '.')) {
      state.boxes = state.boxes.slice(0, box) + mark + state.boxes.slice(box + 1);
      closed++;
    }
  }
  if (state.boxes.includes('.')) return { again: closed > 0, over: false, winner: null, reason: null };
  return { again: false, over: true, ...winnerOf(state.boxes), reason: 'most' };
}

/** The mark with the most claimed cells, or null when two or more tie for the most. */
export function winnerOf(owners) {
  const counts = {};
  for (const m of owners) if (m !== '.' && m !== ' ') counts[m] = (counts[m] || 0) + 1;
  const ranked = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  if (!ranked.length || (ranked[1] && ranked[1][1] === ranked[0][1])) return { winner: null };
  return { winner: ranked[0][0] };
}

/**
 * Owners as one player sees them: 'm' theirs, and for the others the letter [seats] gives
 * their mark ('o', 'p', 'q' in seat order), 'o' when there is no map (two players).
 */
export const side = (s, mark, seats = {}) => [...s].map((c) => (c === '.' || c === ' ' ? c : c === mark ? 'm' : seats[c] || 'o')).join('');

export function view(state, mark, seats = {}) {
  return {
    dotsBoard: state.board || '4x4',
    lines: side(state.lines, mark, seats),
    boxes: side(state.boxes, mark, seats),
    lastMove: state.last ? { by: state.last.mark === mark ? 'me' : 'opponent', line: state.last.line } : null,
  };
}
