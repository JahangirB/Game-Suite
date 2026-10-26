// Corner Blocks: lay your 21 pieces on a shared board; most squares placed wins.
//
// Two players use a 14 × 14 board starting on (4, 4) and (9, 9); three or four use 20 × 20,
// each starting in a corner. Your first piece covers your start square. After that each piece
// must touch one of yours corner to corner and never one of yours along an edge (other
// colours don't matter). A player who can't place anything is skipped; when nobody can, the
// game ends. A move is { piece, orient, x, y }: piece 0–20, orient 0–7 (flip for 4–7, then a
// quarter turn per step), and where the piece's top-left corner goes.

export const PIECES = [
  [[0, 0]],
  [[0, 0], [1, 0]],
  [[0, 0], [1, 0], [2, 0]],
  [[0, 0], [0, 1], [1, 1]],
  [[0, 0], [1, 0], [2, 0], [3, 0]],
  [[0, 0], [0, 1], [0, 2], [1, 2]],
  [[0, 0], [1, 0], [2, 0], [1, 1]],
  [[0, 0], [1, 0], [0, 1], [1, 1]],
  [[1, 0], [2, 0], [0, 1], [1, 1]],
  [[0, 0], [1, 0], [2, 0], [3, 0], [4, 0]],
  [[0, 0], [0, 1], [0, 2], [0, 3], [1, 3]],
  [[0, 0], [0, 1], [0, 2], [0, 3], [1, 1]],
  [[0, 0], [0, 1], [1, 1], [1, 2], [1, 3]],
  [[0, 0], [1, 0], [0, 1], [1, 1], [0, 2]],
  [[0, 0], [2, 0], [0, 1], [1, 1], [2, 1]],
  [[0, 0], [0, 1], [0, 2], [1, 2], [2, 2]],
  [[0, 0], [0, 1], [1, 1], [1, 2], [2, 2]],
  [[0, 0], [1, 0], [1, 1], [1, 2], [2, 2]],
  [[0, 0], [1, 0], [2, 0], [1, 1], [1, 2]],
  [[1, 0], [2, 0], [0, 1], [1, 1], [1, 2]],
  [[1, 0], [0, 1], [1, 1], [2, 1], [1, 2]],
];

/** [piece] turned to [orient], moved so its top-left is (0, 0), cells sorted. */
export function shape(piece, orient) {
  let cells = PIECES[piece].map(([x, y]) => [orient >= 4 ? -x : x, y]);
  for (let k = 0; k < orient % 4; k++) cells = cells.map(([x, y]) => [-y, x]);
  const mx = Math.min(...cells.map((c) => c[0]));
  const my = Math.min(...cells.map((c) => c[1]));
  return cells.map(([x, y]) => [x - mx, y - my]).sort((a, b) => a[1] - b[1] || a[0] - b[0]);
}

export const sizeFor = (players) => (players <= 2 ? 14 : 20);

export function startsFor(players) {
  if (players <= 2) return [[4, 4], [9, 9]];
  return [[0, 0], [19, 0], [19, 19], [0, 19]].slice(0, players);
}

export function start(random = Math.random, marks = ['h', 'g']) {
  const size = sizeFor(marks.length);
  return {
    marks, size, board: '.'.repeat(size * size),
    starts: Object.fromEntries(marks.map((m, i) => [m, startsFor(marks.length)[i]])),
    left: Object.fromEntries(marks.map((m) => [m, PIECES.map((_, i) => i)])),
    placed: Object.fromEntries(marks.map((m) => [m, 0])),
    last: null, turn: marks[0],
  };
}

const at = (s, x, y) => (x < 0 || y < 0 || x >= s.size || y >= s.size ? null : s.board[y * s.size + x]);

/** The cells a move covers, or a reason it isn't allowed. */
export function check(s, mark, piece, orient, x, y) {
  if (!s.left[mark].includes(piece)) return { error: 'You have played that piece.' };
  const cells = shape(piece, orient).map(([cx, cy]) => [x + cx, y + cy]);
  const first = s.left[mark].length === PIECES.length;
  let corner = false;
  for (const [cx, cy] of cells) {
    if (at(s, cx, cy) !== '.') return { error: 'That spot is taken or off the board.' };
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => at(s, cx + dx, cy + dy) === mark)) return { error: 'Your pieces may only touch at the corners.' };
    if ([[1, 1], [-1, 1], [1, -1], [-1, -1]].some(([dx, dy]) => at(s, cx + dx, cy + dy) === mark)) corner = true;
  }
  if (first) {
    const [sx, sy] = s.starts[mark];
    if (!cells.some(([cx, cy]) => cx === sx && cy === sy)) return { error: 'Your first piece must cover your start square.' };
  } else if (!corner) {
    return { error: 'Touch one of your pieces corner to corner.' };
  }
  return { cells };
}

/** Every legal move for [mark] (stopping at the first when [any]). */
export function movesFor(s, mark, any = false) {
  const first = s.left[mark].length === PIECES.length;
  // Squares a new piece could cover to make the corner touch (or the start square).
  const targets = [];
  if (first) targets.push(s.starts[mark]);
  else {
    for (let y = 0; y < s.size; y++) for (let x = 0; x < s.size; x++) {
      if (at(s, x, y) !== '.') continue;
      const diag = [[1, 1], [-1, 1], [1, -1], [-1, -1]].some(([dx, dy]) => at(s, x + dx, y + dy) === mark);
      const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => at(s, x + dx, y + dy) === mark);
      if (diag && !edge) targets.push([x, y]);
    }
  }
  const out = [];
  const seen = new Set();
  for (const piece of s.left[mark]) {
    for (let orient = 0; orient < 8; orient++) {
      const cells = shape(piece, orient);
      for (const [tx, ty] of targets) {
        for (const [cx, cy] of cells) {
          const x = tx - cx;
          const y = ty - cy;
          const key = `${piece}:${cells.join(';')}:${x},${y}`;
          if (seen.has(key)) continue;
          seen.add(key);
          if (check(s, mark, piece, orient, x, y).cells) {
            out.push({ piece, orient, x, y });
            if (any) return out;
          }
        }
      }
    }
  }
  return out;
}

export const canMove = (s, mark) => s.left[mark].length > 0 && movesFor(s, mark, true).length > 0;

const result = (turn, extra = {}) => ({ again: false, over: false, winner: null, reason: null, turn, ...extra });

export function play(s, mark, move) {
  const piece = Number(move?.piece);
  const orient = Number(move?.orient);
  const x = Number(move?.x);
  const y = Number(move?.y);
  if (![piece, orient, x, y].every(Number.isInteger) || piece < 0 || piece >= PIECES.length || orient < 0 || orient > 7) throw new Error('That move is not valid.');
  const r = check(s, mark, piece, orient, x, y);
  if (r.error) throw new Error(r.error);
  const board = s.board.split('');
  for (const [cx, cy] of r.cells) board[cy * s.size + cx] = mark;
  s.board = board.join('');
  s.left[mark] = s.left[mark].filter((p) => p !== piece);
  s.placed[mark] += r.cells.length;
  s.last = { mark, cells: r.cells };
  // The next player round the table who can still place something.
  const n = s.marks.length;
  let i = s.marks.indexOf(mark);
  for (let k = 0; k < n; k++) {
    i = (i + 1) % n;
    if (canMove(s, s.marks[i])) {
      s.turn = s.marks[i];
      return result(s.marks[i]);
    }
  }
  s.turn = null;
  const ranked = s.marks.slice().sort((a, b) => s.placed[b] - s.placed[a]);
  const tie = ranked.length > 1 && s.placed[ranked[0]] === s.placed[ranked[1]];
  return { again: false, over: true, winner: tie ? null : ranked[0], reason: tie ? 'draw' : 'squares', turn: null };
}

export function view(s, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const each = (f) => Object.fromEntries(s.marks.map((m) => [who(m), f(m)]));
  return {
    blocks: {
      size: s.size,
      board: [...s.board].map((c) => (c === '.' ? '.' : who(c))).join(''),
      starts: each((m) => s.starts[m]),
      left: each((m) => s.left[m]),
      placed: each((m) => s.placed[m]),
      last: s.last ? { who: who(s.last.mark), cells: s.last.cells } : null,
    },
  };
}
