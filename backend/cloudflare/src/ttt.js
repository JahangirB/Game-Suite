// Ultimate Tic-Tac-Toe: nine small boards in a 3 × 3 big board. Players take turns marking a
// cell; the cell you pick sends your opponent to the small board in the same position. If that
// board is already decided, they may play in any open board. Three in a row wins a small board;
// three small boards in a row wins the game. A game with no line of boards is a draw.
//
// Cells are numbered board * 9 + cell, both 0-8 row by row. `boards` holds each small board's
// owner: '.' open, a player's mark, or 'd' when it filled up with no winner.

export const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];

/** The line of three [mark]s in a 9-character grid, or null. */
export function lineOf(grid, mark) {
  return LINES.find((line) => line.every((i) => grid[i] === mark)) ?? null;
}

const set = (s, i, c) => s.slice(0, i) + c + s.slice(i + 1);

export function start() {
  return { cells: '.'.repeat(81), boards: '.'.repeat(9), next: -1, winLine: null, last: null };
}

/** Marks { cell } for `mark`. Returns { again, over, winner, reason }. */
export function play(state, mark, move) {
  const cell = Number(move?.cell);
  if (!Number.isInteger(cell) || cell < 0 || cell >= 81) throw new Error('Pick a square on the board.');
  const board = Math.floor(cell / 9);
  if (state.boards[board] !== '.') throw new Error('That small board is already decided.');
  if (state.next >= 0 && board !== state.next) throw new Error('Play in the highlighted board.');
  if (state.cells[cell] !== '.') throw new Error('That square is taken.');
  state.cells = set(state.cells, cell, mark);
  state.last = { mark, cell };
  const small = state.cells.slice(board * 9, board * 9 + 9);
  if (lineOf(small, mark)) state.boards = set(state.boards, board, mark);
  else if (!small.includes('.')) state.boards = set(state.boards, board, 'd');
  const target = cell % 9;
  state.next = state.boards[target] === '.' ? target : -1;
  const line = lineOf(state.boards, mark);
  if (line) {
    state.winLine = line;
    return { again: false, over: true, winner: mark, reason: 'three' };
  }
  return { again: false, over: !state.boards.includes('.'), winner: null, reason: 'draw' };
}

const side = (s, mark) => [...s].map((c) => (c === '.' || c === 'd' ? c : c === mark ? 'm' : 'o')).join('');

export function view(state, mark) {
  return {
    board: side(state.cells, mark),
    boards: side(state.boards, mark),
    next: state.next,
    winLine: state.winLine,
    lastMove: state.last ? { by: state.last.mark === mark ? 'me' : 'opponent', cell: state.last.cell } : null,
  };
}
