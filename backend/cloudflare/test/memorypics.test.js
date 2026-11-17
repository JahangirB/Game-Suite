// Memory Pairs with the players' own photos (cap and token "memory-photos"): the opener sets one photo per pair
// by one call each, each player's state carries only their ids, a phone fetches each once, and they are deleted
// with the room. The bytes are stored on their own, never in the room that is saved on every move. The same tests
// are in the app's LocalMemoryPhotosTest (nearby). Apps Script has none of this and does not send the cap.
import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPS, createRoom, joinRoom, leave, makeMove, openGame, setConnected, setOptions, setTurnReady, view } from '../src/room.js';
import * as memory from '../src/memory.js';
import { MEMPIC_INTERVAL_MS, MEMPIC_MAX, MEMPIC_SLOTS, getMemoryPic, setMemoryPic, sweepMemoryPics } from '../src/memorypics.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;
let clock = T0;
/** Each call is a little after the last, so the pacing never gets in the way of the other tests. */
const later = () => (clock += 1000);
const NEW_APP = ['memory', 'memory-photos'];
const OLD_APP = ['memory'];

/** A JPEG-shaped base64 string of about `chars` characters: FF D8 FF ... FF D9. */
function jpeg(chars = 400, fill = 0x41) {
  const n = Math.floor((chars / 4) * 3);
  const bytes = new Uint8Array(n).fill(fill);
  bytes.set([0xff, 0xd8, 0xff, 0xe0], 0);
  bytes.set([0xff, 0xd9], n - 2);
  return Buffer.from(bytes).toString('base64');
}

function fakeStore() {
  const map = new Map();
  return {
    map,
    async get(k) { return map.get(k); },
    async put(k, v) { map.set(k, v); },
    async delete(k) {
      if (Array.isArray(k)) { for (const x of k) map.delete(x); return k.length; }
      return map.delete(k);
    },
    async list({ prefix }) { return new Map([...map].filter(([k]) => k.startsWith(prefix))); },
  };
}

function lobby(guestGames = NEW_APP, hostGames = NEW_APP) {
  const room = createRoom('MEMO12', 'Alice', T0, uuid, null, null, 'memory', hostGames);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, guestGames), true, T0);
  return room;
}

/** A lobby whose opener chose the photos deck of [cards] cards. */
function photosLobby(cards = 16) {
  const room = lobby();
  setOptions(room, 'host', { cards, deck: 'photos' });
  return room;
}

async function fill(store, room, n, fillByte = 1) {
  for (let i = 0; i < n; i++) await setMemoryPic(store, room, 'host', i, jpeg(300, fillByte + i), later());
}

test('the server lists the cap, and the state says whether everyone has the token', () => {
  assert.ok(CAPS.includes('memory-photos'));
  const v = view(lobby(), 'host', T0);
  assert.ok(v.caps.includes('memory-photos'));
  assert.equal(v.memoryPhotos, true);
});

test('the size cap is 16,000 characters, and the count cap is the biggest deck', async () => {
  assert.equal(MEMPIC_MAX, 16_000);
  assert.equal(MEMPIC_SLOTS, 18);
  const room = photosLobby();
  const store = fakeStore();
  await setMemoryPic(store, room, 'host', 0, jpeg(MEMPIC_MAX), later());
  await assert.rejects(setMemoryPic(store, room, 'host', 1, jpeg(MEMPIC_MAX + 4), later()), /too big/);
  await assert.rejects(setMemoryPic(store, room, 'host', 1, jpeg(60_000), later()), /too big/);
  assert.equal(store.map.size, 1, 'nothing else was kept');
  // 16 cards are 8 pairs: places 0 to 7.
  await setMemoryPic(store, room, 'host', 7, jpeg(300), later());
  for (const slot of [8, 17, 18, -1, 1.5, 'x', null, undefined]) {
    await assert.rejects(setMemoryPic(store, room, 'host', slot, jpeg(300), later()), /no such place/, String(slot));
  }
  setOptions(room, 'host', { cards: 36, deck: 'photos' });
  await setMemoryPic(store, room, 'host', 17, jpeg(300), later());
  await assert.rejects(setMemoryPic(store, room, 'host', 18, jpeg(300), later()), /no such place/);
});

test('bad data is refused: not a string, not base64, not a JPEG', async () => {
  const room = photosLobby();
  const store = fakeStore();
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4]).toString('base64');
  for (const bad of [7, {}, ['abcd'], true, 'not base64!', 'abc', 'abcd====', png, Buffer.from('hello world!').toString('base64')]) {
    await assert.rejects(setMemoryPic(store, room, 'host', 0, bad, later()), /not usable|too big/, JSON.stringify(bad));
  }
  assert.equal(store.map.size, 0);
  assert.deepEqual(view(room, 'guest', T0).options.photos, Array(8).fill(null));
});

test('the opener sets the photos, the state carries only ids per pair, and they are fetched by id', async () => {
  const room = photosLobby();
  const store = fakeStore();
  assert.deepEqual(view(room, 'guest', T0).options, { cards: 16, deck: 'photos', photos: Array(8).fill(null) });
  const photo = jpeg(300, 9);
  await setMemoryPic(store, room, 'host', 2, photo, later());
  await setMemoryPic(store, room, 'host', 5, jpeg(300, 8), later());
  const v = view(room, 'guest', T0);
  assert.equal(v.options.photos.length, 8);
  const [a, b] = [v.options.photos[2], v.options.photos[5]];
  assert.ok(a > 0 && b > a);
  assert.equal(v.options.photos.filter((x) => x != null).length, 2);
  assert.ok(!JSON.stringify(v).includes(photo), 'the bytes are not in the state');
  assert.ok(!JSON.stringify(room).includes(photo), 'nor in the saved room');
  assert.equal(await getMemoryPic(store, room, a), photo);
  for (const id of [0, -1, 99, 'x', null, undefined, 1.5]) assert.equal(await getMemoryPic(store, room, id), null, String(id));
});

test('only the opener sets photos, and only between matches', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await assert.rejects(setMemoryPic(store, room, 'guest', 0, jpeg(), later()), /Only whoever opened/);
  await fill(store, room, 8);
  setTurnReady(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0);
  assert.equal(room.status, 'started');
  await assert.rejects(setMemoryPic(store, room, 'host', 0, jpeg(), later()), /between games/);
});

test('calls are paced', async () => {
  assert.equal(MEMPIC_INTERVAL_MS, 150);
  const room = photosLobby();
  const store = fakeStore();
  const t = later();
  await setMemoryPic(store, room, 'host', 0, jpeg(300, 1), t);
  await assert.rejects(setMemoryPic(store, room, 'host', 1, jpeg(300, 2), t + MEMPIC_INTERVAL_MS - 1), /Slow down a little/);
  assert.equal(store.map.size, 1, 'the refused one kept nothing');
  await setMemoryPic(store, room, 'host', 1, jpeg(300, 2), t + MEMPIC_INTERVAL_MS);
  assert.equal(store.map.size, 2);
});

test('a new photo for a place replaces the old one under a new id; null empties it; both take Ready back', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 8);
  const first = view(room, 'guest', T0).options.photos[0];
  setTurnReady(room, 'guest', true, T0);
  assert.equal(room.players.guest.ready, true);
  await setMemoryPic(store, room, 'host', 0, jpeg(300, 2), later());
  assert.equal(room.players.guest.ready, false, 'a changed photo takes Ready back');
  const second = view(room, 'guest', T0).options.photos[0];
  assert.ok(second > first);
  assert.equal(await getMemoryPic(store, room, first), null, 'the old one is gone');
  assert.equal(await getMemoryPic(store, room, second), jpeg(300, 2));
  assert.equal(store.map.size, 8);
  await setMemoryPic(store, room, 'host', 0, null, later());
  assert.equal(view(room, 'guest', T0).options.photos[0], null);
  assert.equal(store.map.size, 7);
});

test('the game cannot start until every place has its photo, then the cards are pairs of "p<n>"', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 5);
  assert.throws(() => setTurnReady(room, 'host', true, T0), /Choose 3 more photos to start/);
  await fill(store, room, 8);
  setTurnReady(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0);
  assert.equal(room.status, 'started');
  assert.deepEqual([...room.turnState.deck].sort(), [0, 1, 2, 3, 4, 5, 6, 7].flatMap((i) => ['p' + i, 'p' + i]).sort());
  assert.ok(view(room, 'host', T0 + 10_000).options.photos.every((id) => id > 0), 'the ids stay in the state while playing');
});

test('one missing photo is one more to pick', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 7);
  assert.throws(() => setTurnReady(room, 'host', true, T0), /Choose 1 more photo to start/);
});

test('another deck or another size takes the photos away, and the same choice keeps them', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 8);
  setOptions(room, 'host', { cards: 16, deck: 'photos' });
  assert.equal(view(room, 'host', T0).options.photos.filter((x) => x != null).length, 8, 'the same choice keeps them');
  setOptions(room, 'host', { cards: 24, deck: 'photos' });
  await sweepMemoryPics(store, room);
  assert.deepEqual(view(room, 'host', T0).options.photos, Array(12).fill(null));
  assert.equal(store.map.size, 0, 'the size changed');
  await fill(store, room, 12);
  setOptions(room, 'host', { cards: 24, deck: 'animals' });
  await sweepMemoryPics(store, room);
  assert.deepEqual(view(room, 'host', T0).options, { cards: 24, deck: 'animals' });
  assert.equal(store.map.size, 0, 'the animals deck keeps no photos');
  await assert.rejects(setMemoryPic(store, room, 'host', 0, jpeg(), later()), /need the latest/);
  // A call that leaves the deck out keeps it.
  setOptions(room, 'host', { cards: 24, deck: 'photos' });
  setOptions(room, 'host', { cards: 24 });
  assert.equal(view(room, 'host', T0).options.deck, 'photos');
});

test('photos go with the room: a closed room, another game and the sweep clean up', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 8);
  await store.put('mempic:999', 'stray');
  await sweepMemoryPics(store, room);
  assert.equal(store.map.size, 8, 'the stray one went');
  openGame(room, 'host', 'ttt', T0);
  await sweepMemoryPics(store, room);
  assert.equal(store.map.size, 0, 'another game');

  const again = photosLobby();
  const s2 = fakeStore();
  await fill(s2, again, 8);
  leave(again, 'guest');
  leave(again, 'host');
  assert.equal(again.status, 'closed');
  assert.equal(await getMemoryPic(s2, again, view(again, 'host', T0).options.photos?.[0] ?? 1), null, 'nothing is fetched from a closed room');
  await sweepMemoryPics(s2, again);
  assert.equal(s2.map.size, 0, 'a closed room keeps none');
});

test('ids never repeat in a room, even after a game was opened', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 1);
  const first = view(room, 'host', T0).options.photos[0];
  openGame(room, 'host', 'memory', T0);
  openGame(room, 'host', 'ttt', T0);
  openGame(room, 'host', 'memory', T0);
  setOptions(room, 'host', { cards: 16, deck: 'photos' });
  await fill(store, room, 1);
  assert.ok(view(room, 'host', T0).options.photos[0] > first);
});

test('token gating: with one player lacking the token there are no photos and the deck is today\'s', async () => {
  const room = lobby(OLD_APP);
  const v = view(room, 'host', T0);
  assert.equal(v.memoryPhotos, false);
  assert.deepEqual(v.options, { cards: 16 }, 'exactly today\'s options');
  assert.throws(() => setOptions(room, 'host', { cards: 16, deck: 'photos' }), /need the latest/);
  await assert.rejects(setMemoryPic(fakeStore(), room, 'host', 0, jpeg(), later()), /need the latest|no such|Only/);
  // The opener has the token and the guest does not; the same the other way round.
  assert.equal(view(lobby(NEW_APP, OLD_APP), 'host', T0).memoryPhotos, false);
});

test('a player without the token who joins later turns the photos off; leaving brings them back', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 8);
  const third = joinRoom(room, 'Cy', null, T0, uuid, null, null, OLD_APP);
  setConnected(room, third, true, T0);
  const v = view(room, 'host', T0);
  assert.equal(v.memoryPhotos, false);
  assert.deepEqual(v.options, { cards: 16 });
  assert.equal(await getMemoryPic(store, room, 1), null, 'nobody can fetch one now');
  for (const r of ['host', 'guest', third]) setTurnReady(room, r, true, T0);
  assert.equal(room.status, 'started');
  assert.ok(room.turnState.deck.every((c) => !/^p\d+$/.test(c)), 'the animals deck is dealt');
  assert.ok(room.turnState.deck.every((c) => typeof c === 'string'));
});

test('an old app is unaffected: the same state plus one flag, and its calls as before', () => {
  const room = lobby(OLD_APP, OLD_APP);
  const v = view(room, 'host', T0);
  assert.deepEqual(v.options, { cards: 16 });
  setOptions(room, 'host', { cards: 24 });
  assert.deepEqual(view(room, 'guest', T0).options, { cards: 24 });
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  assert.equal(room.status, 'started');
  assert.equal(room.turnState.deck.length, 24);
  const { memoryPhotos, ...rest } = v;
  assert.equal(memoryPhotos, false);
  assert.ok(!('memoryPics' in room), 'nothing added to the saved room');
});

test('the animals deck of an updated app is as before', () => {
  const room = lobby();
  assert.deepEqual(view(room, 'host', T0).options, { cards: 16, deck: 'animals' });
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  assert.ok(room.turnState.deck.every((c) => !/^p\d+$/.test(c)));
  const first = room.turn;
  makeMove(room, first, { card: 0 }, T0);
});

test('the card count can still be changed after an old phone joins; only asking for photos is refused', async () => {
  const room = photosLobby();
  const store = fakeStore();
  await fill(store, room, 8);
  setConnected(room, joinRoom(room, 'Cy', null, T0, uuid, null, null, OLD_APP), true, T0);
  setOptions(room, 'host', { cards: 24 });
  assert.deepEqual(view(room, 'host', T0).options, { cards: 24 });
  assert.equal(room.options.deck, 'photos', 'the inherited deck is kept silently');
  assert.throws(() => setOptions(room, 'host', { cards: 24, deck: 'photos' }), /need the latest/);
  assert.deepEqual(room.memoryPics ?? [], [], 'the new size took the photos away');
});

test('a phone without the token never gets a photo card: it gets the pair\'s plain animal', async () => {
  const room = photosLobby(36);
  const store = fakeStore();
  await fill(store, room, 18);
  setTurnReady(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0);
  room.turnState.owners = 'h'.repeat(36);
  const newView = view(room, 'host', T0 + 10_000).turns.faces;
  assert.ok(newView.every((f) => /^p\d+$/.test(f)));
  room.players.guest.memoryPhotos = false;
  const old = view(room, 'guest', T0 + 10_000).turns.faces;
  assert.ok(old.every((f) => !/^p\d+$/.test(f)));
  assert.equal(old[room.turnState.deck.indexOf('p0')], 'fox');
  const i12 = room.turnState.deck.indexOf('p12');
  assert.equal(old[i12], 'fox*');
  assert.equal(old[room.turnState.deck.indexOf('p3')], 'panda');
  assert.equal(memory.animalFor('owl'), 'owl');
});

test('when the opener leaves, their photos go and the deck is the animals again', async () => {
  const room = photosLobby();
  setConnected(room, joinRoom(room, 'Cy', null, T0, uuid, null, null, NEW_APP), true, T0);
  const store = fakeStore();
  await fill(store, room, 8);
  room.openedBy = 'guest';
  leave(room, 'guest');
  await sweepMemoryPics(store, room);
  assert.equal(store.map.size, 0);
  assert.equal(room.options.deck, 'animals');
  assert.deepEqual(view(room, 'host', T0).options, { cards: 16, deck: 'animals' });
});
