// Sudoku boards made of joined grids: the same shapes as the app's game/SudokuShape.kt. Each grid
// is a 9 x 9 Sudoku at a (row, column) in cells; overlapping boxes belong to every grid covering
// them. A board is size x size characters, row-major: digits, '0' for empty, '-' off the board.

export const SHAPES = {
  classic: [[0, 0]],
  twin: [[0, 0], [6, 6]],
  stairs: [[0, 0], [6, 6], [12, 12]],
  cross: [[0, 0], [0, 12], [6, 6], [12, 0], [12, 12]],
  windmill: [[0, 3], [3, 12], [6, 6], [12, 9], [9, 0]],
};

const cache = {};
/** The shape's size, which cells are on the board, and its units (rows, columns, boxes). */
export function geometry(key) {
  if (cache[key]) return cache[key];
  const grids = SHAPES[key];
  if (!grids) return null;
  const size = Math.max(...grids.map(([r, c]) => Math.max(r, c) + 9));
  const on = new Array(size * size).fill(false);
  const units = [];
  const boxes = new Set();
  for (const [gr, gc] of grids) {
    for (let r = 0; r < 9; r++) for (let c = 0; c < 9; c++) on[(gr + r) * size + gc + c] = true;
    for (let k = 0; k < 9; k++) {
      units.push([...Array(9)].map((_, i) => (gr + k) * size + gc + i));
      units.push([...Array(9)].map((_, i) => (gr + i) * size + gc + k));
      const br = gr + Math.floor(k / 3) * 3, bc = gc + (k % 3) * 3;
      if (!boxes.has(br * size + bc)) { boxes.add(br * size + bc); units.push([...Array(9)].map((_, i) => (br + Math.floor(i / 3)) * size + bc + (i % 3))); }
    }
  }
  return (cache[key] = { key, size, cells: size * size, on, units, playable: on.filter(Boolean).length });
}

/** Whether [text] is a board of [shape]: '-' exactly off the board, digits ('0' for empty) on it. */
export function fits(shape, text) {
  const g = geometry(shape);
  if (!g || typeof text !== 'string' || text.length !== g.cells) return false;
  for (let i = 0; i < g.cells; i++) {
    const ch = text[i];
    if (g.on[i] ? !(ch >= '0' && ch <= '9') : ch !== '-') return false;
  }
  return true;
}

/**
 * Checks a puzzle a phone made: both boards fit the shape, the solution is full and breaks no
 * rule in any grid, the puzzle's digits are the solution's and it leaves something to do.
 * Returns an error message, or null when it's fine.
 */
export function checkPuzzle(shape, puzzle, solution) {
  const g = geometry(shape);
  if (!g || shape === 'classic') return 'Unknown board shape.';
  if (!fits(shape, puzzle) || !fits(shape, solution)) return 'That puzzle doesn’t fit the board.';
  if (solution.includes('0')) return 'The puzzle’s answer isn’t complete.';
  for (const unit of g.units) {
    const seen = new Set(unit.map((i) => solution[i]));
    if (seen.size !== 9) return 'The puzzle’s answer breaks the rules.';
  }
  let empty = 0;
  for (let i = 0; i < g.cells; i++) {
    if (puzzle[i] === '0') empty++;
    else if (puzzle[i] !== solution[i]) return 'The puzzle doesn’t match its answer.';
  }
  if (empty < g.playable / 4) return 'The puzzle has too few empty squares.';
  return null;
}
