// Balloon Words: one player picks a secret word, the others guess its letters in turn before
// the balloons pop. Everyone picks once; the most points wins.
//
// The picker types any word (letters only, 3 to 12): names and in-jokes work. Guessers take
// turns naming a letter. A right letter scores 1 for each time it appears, and whoever fills
// in the last letter scores 3 more. A wrong letter pops a balloon; when all BALLOONS pop the
// picker scores 5. Then the next player picks.

export const BALLOONS = 7;

export function start(random = Math.random, marks = ['h', 'g']) {
  return {
    marks, round: 1, picker: 0, phase: 'pick', word: null, guessed: [], misses: 0,
    scores: Object.fromEntries(marks.map((m) => [m, 0])), last: null, lastRound: null,
    turn: marks[0],
  };
}

const pickerOf = (s) => s.marks[s.picker];

/** The guesser after [mark] (never the picker). */
function nextGuesser(s, mark) {
  const n = s.marks.length;
  let i = s.marks.indexOf(mark);
  for (let k = 0; k < n; k++) {
    i = (i + 1) % n;
    if (i !== s.picker) return s.marks[i];
  }
  return s.marks[(s.picker + 1) % n];
}

const solved = (s) => [...s.word].every((c) => s.guessed.includes(c));

const result = (turn, extra = {}) => ({ again: false, over: false, winner: null, reason: null, turn, ...extra });

function endRound(s, solvedBy) {
  s.lastRound = { word: s.word, picker: pickerOf(s), solvedBy, popped: !solvedBy };
  if (s.round >= s.marks.length) {
    const ranked = s.marks.slice().sort((a, b) => s.scores[b] - s.scores[a]);
    const tie = ranked.length > 1 && s.scores[ranked[0]] === s.scores[ranked[1]];
    s.phase = 'over';
    return { again: false, over: true, winner: tie ? null : ranked[0], reason: tie ? 'draw' : 'points', turn: null };
  }
  s.round += 1;
  s.picker = (s.picker + 1) % s.marks.length;
  Object.assign(s, { phase: 'pick', word: null, guessed: [], misses: 0 });
  return result(pickerOf(s));
}

export function play(s, mark, move) {
  if (s.phase === 'pick') {
    if (mark !== pickerOf(s)) throw new Error("It's not your turn to pick.");
    const word = String(move?.word || '').toUpperCase().replace(/\s+/g, '');
    if (!/^[A-Z]{3,12}$/.test(word)) throw new Error('Pick a word of 3 to 12 letters (A to Z only).');
    Object.assign(s, { word, phase: 'guess', guessed: [], misses: 0 });
    s.last = { mark, picked: true };
    return result(nextGuesser(s, mark));
  }
  if (s.phase !== 'guess') throw new Error('The game is over.');
  if (mark === pickerOf(s)) throw new Error('You picked this word.');
  const letter = String(move?.letter || '').toUpperCase();
  if (!/^[A-Z]$/.test(letter)) throw new Error('Pick a letter.');
  if (s.guessed.includes(letter)) throw new Error('That letter was already guessed.');
  s.guessed.push(letter);
  const hits = [...s.word].filter((c) => c === letter).length;
  s.last = { mark, letter, hits };
  if (hits) {
    s.scores[mark] += hits;
    if (solved(s)) {
      s.scores[mark] += 3;
      return endRound(s, mark);
    }
  } else {
    s.misses += 1;
    if (s.misses >= BALLOONS) {
      s.scores[pickerOf(s)] += 5;
      return endRound(s, null);
    }
  }
  return result(nextGuesser(s, mark));
}

/** The game as [mark] sees it; the picker sees the word, everyone else only what's been found. */
export function view(s, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const picker = pickerOf(s);
  const word = s.word || '';
  return {
    phase: s.phase,
    picker: s.phase === 'over' ? null : who(picker),
    pattern: [...word].map((c) => (s.guessed.includes(c) ? c : '_')).join(''),
    word: picker === mark && s.phase === 'guess' ? word : null,
    guessed: s.guessed,
    misses: s.misses,
    balloons: BALLOONS,
    scores: Object.fromEntries(s.marks.map((m) => [who(m), s.scores[m]])),
    roundNo: s.round,
    rounds: s.marks.length,
    lastMove: s.last ? { who: who(s.last.mark), letter: s.last.letter || null, hits: s.last.hits ?? null, picked: !!s.last.picked } : null,
    lastRound: s.lastRound ? {
      word: s.lastRound.word, picker: who(s.lastRound.picker), solvedBy: s.lastRound.solvedBy ? who(s.lastRound.solvedBy) : null, popped: s.lastRound.popped,
    } : null,
  };
}
