// Code Breaker: each player sets a secret code of PEGS colours for the other to crack.
// A guess gets black pegs (right colour, right place) and white pegs (right colour, wrong
// place). Fewest guesses wins; a tie goes to the faster. TRIES guesses each.

export const PEGS = 4;
export const COLOURS = 'rygbpo';
export const TRIES = 10;

export function cleanCode(code) {
  const c = String(code || '').toLowerCase();
  if (c.length !== PEGS || [...c].some((x) => !COLOURS.includes(x))) {
    throw new Error(`A code is ${PEGS} pegs, each one of the six colours.`);
  }
  return c;
}

/** Black and white pegs for [guess] against [code]. */
export function score(guess, code) {
  let black = 0;
  const left = {};
  const rest = [];
  for (let i = 0; i < PEGS; i++) {
    if (guess[i] === code[i]) black++;
    else {
      left[code[i]] = (left[code[i]] || 0) + 1;
      rest.push(guess[i]);
    }
  }
  let white = 0;
  for (const g of rest) {
    if (left[g] > 0) {
      white++;
      left[g]--;
    }
  }
  return { black, white };
}
