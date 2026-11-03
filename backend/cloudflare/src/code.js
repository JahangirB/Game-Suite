// Code Breaker: each player sets a secret code of PEGS colours for the other to crack.
// A guess gets black pegs (right colour, right place) and white pegs (right colour, wrong
// place). Fewest guesses wins; a tie goes to the faster. TRIES guesses each.

export const PEGS = 4;
export const COLOURS = 'rygbpo';
/** Every colour there is; the harder setting adds the seventh (teal). */
export const ALL_COLOURS = 'rygbpoc';
export const TRIES = 10;

/** The match's settings: 4 or 5 pegs, 6 or 7 colours, and whether a colour may repeat. */
export function cleanOptions(o) {
  return { pegs: Number(o?.pegs) === 5 ? 5 : 4, colours: Number(o?.colours) === 7 ? 7 : 6, repeats: o?.repeats !== false };
}

export function cleanCode(code, options = {}) {
  const { pegs, colours, repeats } = cleanOptions(options);
  const palette = ALL_COLOURS.slice(0, colours);
  const c = String(code || '').toLowerCase();
  if (c.length !== pegs || [...c].some((x) => !palette.includes(x))) {
    throw new Error(`A code is ${pegs} pegs, each one of the ${colours === 7 ? 'seven' : 'six'} colours.`);
  }
  if (!repeats && new Set(c).size !== c.length) throw new Error('No repeats: every colour in the code is different.');
  return c;
}

/** Black and white pegs for [guess] against [code]. */
export function score(guess, code) {
  let black = 0;
  const left = {};
  const rest = [];
  for (let i = 0; i < code.length; i++) {
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
