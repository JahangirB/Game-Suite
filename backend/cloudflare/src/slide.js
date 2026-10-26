// Slide Race: the same shuffled sliding puzzle for everyone; first to put the tiles in order wins.
//
// The host's phone shuffles (with legal slides only, so every puzzle can be solved) and sends
// the start with its Ready. Tiles are numbers row by row, 0 the gap; solved is 1, 2, ... then
// the gap last. Each phone sends its whole list of moves (the tile slid into the gap each time);
// the server replays it from the start, so only real slides count.

export const LEVELS = { easy: 3, medium: 4, hard: 5 };

export function cleanOptions(options) {
  const level = String(options?.level || '').toLowerCase();
  return { level: LEVELS[level] ? level : 'easy' };
}

export const solvedTiles = (size) => [...Array(size * size - 1).keys()].map((i) => i + 1).concat(0);

export function checkStart(tiles, size) {
  const t = Array.isArray(tiles) ? tiles.map(Number) : [];
  const want = solvedTiles(size).slice().sort((a, b) => a - b);
  if (t.length !== size * size || t.slice().sort((a, b) => a - b).join() !== want.join()) {
    throw new Error('The puzzle is not valid.');
  }
  // Only half of all orders can be solved: check this one can.
  let inversions = 0;
  const nums = t.filter((x) => x);
  for (let i = 0; i < nums.length; i++) for (let j = i + 1; j < nums.length; j++) if (nums[i] > nums[j]) inversions++;
  const gapRowFromBottom = size - Math.floor(t.indexOf(0) / size);
  const ok = size % 2 === 1 ? inversions % 2 === 0 : (inversions + gapRowFromBottom) % 2 === 1;
  if (!ok) throw new Error('That puzzle cannot be solved.');
  if (t.join() === solvedTiles(size).join()) throw new Error('The puzzle is already solved.');
  return t;
}

/** Slides [tile] into the gap if it is next to it; throws otherwise. */
export function slide(tiles, size, tile) {
  const at = tiles.indexOf(tile);
  const gap = tiles.indexOf(0);
  const near = at >= 0 && tile !== 0 && Math.abs((at % size) - (gap % size)) + Math.abs(Math.floor(at / size) - Math.floor(gap / size)) === 1;
  if (!near) throw new Error("That tile can't slide.");
  tiles[gap] = tile;
  tiles[at] = 0;
}

/** Replays [moves] from [start]; returns the tiles. */
export function replay(start, size, moves) {
  const tiles = start.slice();
  for (const m of moves) slide(tiles, size, m);
  return tiles;
}

/** Share of tiles in their place, as a percentage. */
export function progressOf(tiles, size) {
  const goal = solvedTiles(size);
  const right = tiles.filter((t, i) => t !== 0 && t === goal[i]).length;
  return Math.floor((right * 100) / (size * size - 1));
}

export const isSolved = (tiles, size) => tiles.join() === solvedTiles(size).join();
