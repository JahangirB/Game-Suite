// Colour Clash: match the colour or the number, first to empty their hand wins.
//
// A card is a string: a colour letter (r, y, g, b) and a face: 0-9, S (skip), R (reverse),
// D (draw two) or K (Kylian Dictador: draw eight). Wild cards have no colour: W (wild) and F
// (wild draw four); the player names the colour. The deck is the classic 108 plus four Kylians:
// per colour one 0, two of 1-9, two each of S, R, D and one K; and four W and four F.
//
// Moves: { card: i, colour } plays hand card i (colour for a wild); { draw: true } takes one
// card when you want or need to; then { pass: true } keeps it, or you play that card.
// Skip and draw cards skip the next player; reverse turns round the order (with two players it
// works like skip). Kylian Dictador makes the next player draw eight. Every result says whose turn is next.

export const COLOURS = ['r', 'y', 'g', 'b'];

function fullDeck() {
  const deck = [];
  for (const c of COLOURS) {
    deck.push(c + '0');
    for (const f of ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'S', 'R', 'D']) deck.push(c + f, c + f);
    deck.push(c + 'K');
  }
  for (let i = 0; i < 4; i++) deck.push('W', 'F');
  return deck;
}

function shuffle(cards, random) {
  for (let i = cards.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [cards[i], cards[j]] = [cards[j], cards[i]];
  }
  return cards;
}

export const isWild = (card) => card === 'W' || card === 'F';
/** How many cards each draw card makes the next player take. */
export const DRAWS = { D: 2, F: 4, K: 8 };
const face = (card) => (isWild(card) ? card : card[1]);

/** Whether [card] can go on the pile, whose top shows [colour] and [top]'s face. */
export function fits(card, top, colour) {
  return isWild(card) || card[0] === colour || face(card) === face(top);
}

export function start(random = Math.random, marks = ['h', 'g']) {
  const deck = shuffle(fullDeck(), random);
  const hands = {};
  for (const m of marks) hands[m] = deck.splice(0, 7);
  // The first card on the pile is a number card.
  const at = deck.findIndex((c) => !isWild(c) && /[0-9]/.test(c[1]));
  const [top] = deck.splice(at, 1);
  return { marks, hands, deck, pile: [top], colour: top[0], direction: 1, drawn: null, last: null };
}

/** The player [steps] seats on from [mark] in the current direction. */
function after(state, mark, steps = 1) {
  const n = state.marks.length;
  const i = state.marks.indexOf(mark);
  return state.marks[(((i + state.direction * steps) % n) + n) % n];
}

/** Takes [n] cards from the deck, reshuffling the pile under the top card when it runs out. */
function take(state, n, random) {
  const got = [];
  for (let k = 0; k < n; k++) {
    if (!state.deck.length) {
      const top = state.pile.pop();
      state.deck = shuffle(state.pile, random);
      state.pile = [top];
    }
    if (!state.deck.length) break;
    got.push(state.deck.pop());
  }
  return got;
}

export function play(state, mark, move, random = Math.random) {
  const hand = state.hands[mark];
  const top = state.pile[state.pile.length - 1];
  if (move?.draw) {
    if (state.drawn === mark) throw new Error('You already drew: play it or pass.');
    const [card] = take(state, 1, random);
    if (!card) throw new Error('There are no cards left to draw: pass.');
    hand.push(card);
    state.last = { mark, drew: 1 };
    // A card that fits may be played straight away; otherwise the turn passes.
    if (fits(card, top, state.colour)) {
      state.drawn = mark;
      return { again: true, over: false, winner: null, reason: null, turn: mark };
    }
    state.drawn = null;
    return { again: false, over: false, winner: null, reason: null, turn: after(state, mark) };
  }
  if (move?.pass) {
    if (state.drawn !== mark && (state.deck.length || state.pile.length > 1)) throw new Error('Draw a card first.');
    state.drawn = null;
    state.last = { mark, passed: true };
    return { again: false, over: false, winner: null, reason: null, turn: after(state, mark) };
  }
  const index = Number(move?.card);
  if (!Number.isInteger(index) || index < 0 || index >= hand.length) throw new Error('Pick one of your cards.');
  // After drawing, only the drawn card (the last in the hand) may be played.
  if (state.drawn === mark && index !== hand.length - 1) throw new Error('Play the card you drew, or pass.');
  const card = hand[index];
  if (!fits(card, top, state.colour)) throw new Error("That card doesn't match.");
  let colour = card[0];
  if (isWild(card)) {
    if (!COLOURS.includes(move?.colour)) throw new Error('Choose a colour.');
    colour = move.colour;
  }
  hand.splice(index, 1);
  state.pile.push(card);
  state.colour = colour;
  state.drawn = null;
  state.last = { mark, card, colour };
  if (!hand.length) return { again: false, over: true, winner: mark, reason: 'out', turn: null };
  let next = after(state, mark);
  const f = face(card);
  if (f === 'R') {
    state.direction = -state.direction;
    // With two players a reverse brings the turn straight back.
    next = state.marks.length === 2 ? mark : after(state, mark);
  } else if (f === 'S') {
    next = after(state, mark, 2);
  } else if (DRAWS[f]) {
    const victim = after(state, mark);
    state.hands[victim].push(...take(state, DRAWS[f], random));
    state.last.made = { who: victim, drew: DRAWS[f] };
    next = after(state, mark, 2);
  }
  return { again: next === mark, over: false, winner: null, reason: null, turn: next };
}

/** The game as [mark] sees it; others show as the letters [seats] gives ('o', 'p', 'q'). */
export function view(state, mark, seats = {}) {
  const who = (m) => (m === mark ? 'm' : seats[m] || 'o');
  const last = state.last;
  return {
    top: state.pile[state.pile.length - 1],
    colour: state.colour,
    hand: state.hands[mark] || [],
    counts: Object.fromEntries(state.marks.map((m) => [who(m), state.hands[m].length])),
    // Who plays after whom, by letter, in the current direction.
    order: state.marks.map(who),
    direction: state.direction,
    deck: state.deck.length,
    drawn: state.drawn === mark,
    lastMove: last ? {
      by: last.mark === mark ? 'me' : 'opponent', who: who(last.mark), card: last.card || null, colour: last.colour || null,
      drew: last.drew || 0, passed: !!last.passed,
      made: last.made ? { who: who(last.made.who), drew: last.made.drew } : null,
    } : null,
  };
}
