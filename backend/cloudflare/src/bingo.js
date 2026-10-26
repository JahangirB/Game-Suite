// Bingo: everyone gets a 5 × 5 card; the numbers 1 to 75 are called in a shuffled order, one
// every CALL_MS. Column B holds 1–15, I 16–30, N 31–45, G 46–60 and O 61–75, and the centre
// square is free. The first to claim a full row, column or diagonal of called numbers wins.
//
// The calls follow from the start time, so nothing has to run on a timer while the round goes:
// the view sends the whole order and each phone shows the calls made so far.

export const NUMBERS = 75;
export const SIZE = 5;
export const FREE = 12;
export const CALL_MS = 3500;
/** After the last call, how long players have to claim before the round ends with no winner. */
export const LAST_CALL_GRACE_MS = 10_000;

function shuffle(list, random) {
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}

export const shuffledCalls = (random = Math.random) => shuffle(Array.from({ length: NUMBERS }, (_, i) => i + 1), random);

/** A card, row by row; 0 is the free centre. */
export function dealCard(random = Math.random) {
  const columns = Array.from({ length: SIZE }, (_, c) => shuffle(Array.from({ length: 15 }, (_, i) => c * 15 + i + 1), random).slice(0, SIZE));
  const card = [];
  for (let r = 0; r < SIZE; r++) for (let c = 0; c < SIZE; c++) card.push(columns[c][r]);
  card[FREE] = 0;
  return card;
}

/** How many numbers have been called at [now]. */
export function calledCount(startAt, now) {
  if (now < startAt) return 0;
  return Math.min(NUMBERS, Math.floor((now - startAt) / CALL_MS) + 1);
}

/** When the round ends if nobody claims. */
export const endsAt = (startAt) => startAt + (NUMBERS - 1) * CALL_MS + LAST_CALL_GRACE_MS;

/** The lines (as lists of squares): five rows, five columns and the two diagonals. */
export const LINES = [
  ...Array.from({ length: SIZE }, (_, r) => Array.from({ length: SIZE }, (_, c) => r * SIZE + c)),
  ...Array.from({ length: SIZE }, (_, c) => Array.from({ length: SIZE }, (_, r) => r * SIZE + c)),
  Array.from({ length: SIZE }, (_, i) => i * SIZE + i),
  Array.from({ length: SIZE }, (_, i) => i * SIZE + SIZE - 1 - i),
];

/** The first full line on [card] given the [called] numbers, or null. */
export function fullLine(card, called) {
  const set = new Set(called);
  return LINES.find((line) => line.every((i) => card[i] === 0 || set.has(card[i]))) || null;
}
