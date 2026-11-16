// Players' photos (cap "faces"): one call sets your photo, each player in the room state carries
// its id, a phone fetches it once by id, and it is deleted with the room. The bytes are stored on
// their own, never in the room that is saved on every move. The same tests are in the app's
// LocalFacesTest (nearby). Apps Script has none of this and does not send the cap.
import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPS, createRoom, joinRoom, leave, openGame, setConnected, setSlidePhoto, view } from '../src/room.js';
import { FACE_INTERVAL_MS, FACE_MAX, cleanFace, getFace, setFace, sweepFaces } from '../src/faces.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;
/** Each call is a few seconds after the last, so the pacing never gets in the way of the other tests. */
let clock = T0;
const later = () => (clock += 5000);

/** A JPEG-shaped base64 string of about `chars` characters: FF D8 FF ... FF D9. */
function jpeg(chars = 400, fill = 0x41) {
  const n = Math.floor((chars / 4) * 3);
  const bytes = new Uint8Array(n).fill(fill);
  bytes.set([0xff, 0xd8, 0xff, 0xe0], 0);
  bytes.set([0xff, 0xd9], n - 2);
  return Buffer.from(bytes).toString('base64');
}

/** The storage of a Durable Object, as far as the photos use it. */
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

function lobby(game = 'sudoku') {
  const room = createRoom('FACE12', 'Alice', T0, uuid, null, null, game);
  setConnected(room, 'host', true, T0);
  setConnected(room, joinRoom(room, 'Bob', null, T0, uuid, null, null, [game]), true, T0);
  return room;
}

test('the server lists the cap', () => {
  assert.ok(CAPS.includes('faces'));
  assert.ok(view(lobby(), 'host', T0).caps.includes('faces'));
});

test('a photo is set, and each player in the state carries only its id', async () => {
  const room = lobby();
  const store = fakeStore();
  assert.equal(view(room, 'guest', T0).opponent.photoId, null, 'no photo yet');
  const photo = jpeg();
  await setFace(store, room, 'host', photo, later());
  const v = view(room, 'guest', T0);
  assert.ok(v.opponent.photoId > 0);
  assert.equal(v.me.photoId, null, 'Bob has none');
  assert.equal(view(room, 'host', T0).me.photoId, v.opponent.photoId);
  assert.equal(v.others[0].photoId, v.opponent.photoId);
  assert.ok(!JSON.stringify(v).includes(photo), 'the bytes are not in the state');
  assert.ok(!JSON.stringify(room).includes(photo), 'nor in the saved room');
  assert.equal(await getFace(store, room, v.opponent.photoId), photo);
});

test('the size cap is about 12,000 characters', async () => {
  assert.equal(FACE_MAX, 12_000);
  const room = lobby();
  const store = fakeStore();
  await setFace(store, room, 'host', jpeg(FACE_MAX), later());
  await assert.rejects(setFace(store, room, 'guest', jpeg(FACE_MAX + 4), later()), /too big/);
  await assert.rejects(setFace(store, room, 'guest', jpeg(50_000), later()), /too big/);
  assert.equal(view(room, 'host', T0).opponent.photoId, null, 'nothing was kept');
  assert.equal(store.map.size, 1);
});

test('bad data is refused: not a string, not base64, not a JPEG', async () => {
  const room = lobby();
  const store = fakeStore();
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4]).toString('base64');
  const noEnd = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 5, 6]).toString('base64');
  for (const bad of [7, {}, ['abcd'], true, 'not base64!', 'abc', 'abcd====', '/9j/ 4AAQ', png, noEnd, Buffer.from('hello world!').toString('base64')]) {
    await assert.rejects(setFace(store, room, 'host', bad, later()), /not usable|too big/, JSON.stringify(bad));
  }
  assert.throws(() => cleanFace(''), /not usable/);
  assert.equal(view(room, 'guest', T0).opponent.photoId, null);
  assert.equal(store.map.size, 0);
});

test('a photo is fetched by its id, once, and only a photo of someone in the room', async () => {
  const room = lobby();
  const store = fakeStore();
  await setFace(store, room, 'host', jpeg(300, 1), later());
  await setFace(store, room, 'guest', jpeg(300, 2), later());
  const [a, b] = [view(room, 'guest', T0).opponent.photoId, view(room, 'host', T0).opponent.photoId];
  assert.notEqual(a, b);
  assert.equal(await getFace(store, room, a), jpeg(300, 1));
  assert.equal(await getFace(store, room, b), jpeg(300, 2));
  for (const id of [0, -1, 99, 'x', null, undefined, 1.5]) assert.equal(await getFace(store, room, id), null, String(id));
});

test('a new photo replaces the old one under a new id; null removes it', async () => {
  const room = lobby();
  const store = fakeStore();
  await setFace(store, room, 'host', jpeg(300, 1), later());
  const first = view(room, 'guest', T0).opponent.photoId;
  await setFace(store, room, 'host', jpeg(300, 2), later());
  const second = view(room, 'guest', T0).opponent.photoId;
  assert.ok(second > first);
  assert.equal(await getFace(store, room, first), null, 'the old one is gone');
  assert.equal(await getFace(store, room, second), jpeg(300, 2));
  assert.equal(store.map.size, 1);
  await setFace(store, room, 'host', null, later());
  assert.equal(view(room, 'guest', T0).opponent.photoId, null);
  assert.equal(store.map.size, 0);
  await setFace(store, room, 'host', '', later());
});

test('photos go with the room: a player leaving, a closed room and a new game clean up', async () => {
  const room = lobby();
  const store = fakeStore();
  await setFace(store, room, 'host', jpeg(300, 1), later());
  await setFace(store, room, 'guest', jpeg(300, 2), later());
  const hostId = view(room, 'guest', T0).opponent.photoId;
  leave(room, 'guest');
  await sweepFaces(store, room);
  assert.deepEqual([...store.map.keys()], [`face:${hostId}`], "Bob's photo is gone, Alice's stays");
  leave(room, 'host');
  await sweepFaces(store, room);
  assert.equal(store.map.size, 0, 'the closed room keeps none');

  const again = lobby();
  const s2 = fakeStore();
  await setFace(s2, again, 'host', jpeg(300, 3), later());
  const id = view(again, 'guest', T0).opponent.photoId;
  openGame(again, 'host', 'ttt', T0);
  assert.equal(view(again, 'guest', T0).opponent.photoId, id, 'a new game keeps the photo');
  assert.equal(await getFace(s2, again, id), jpeg(300, 3));
  const id2 = (await setFace(s2, again, 'host', jpeg(300, 4), later()), view(again, 'guest', T0).opponent.photoId);
  assert.ok(id2 > id, 'ids never repeat in the room');
});

test('someone not in the room cannot set a photo', async () => {
  const room = lobby();
  await assert.rejects(setFace(fakeStore(), room, 'p3', jpeg()), /not in this game/);
});

test("an old app is unaffected: the same state plus one null field, Slide Race's photo as before", async () => {
  const room = lobby('slide');
  const v = view(room, 'host', T0);
  const { photoId, ...rest } = v.opponent;
  assert.equal(photoId, null);
  assert.deepEqual(Object.keys(rest).sort(), ['name', 'avatar', 'deviceId', 'emoji', 'ready', 'difficulty', 'progress', 'finishMs', 'left', 'out', 'idleMs', 'online', 'at', 'pausedMs', 'restart', 'hard', 'placed'].sort());
  setSlidePhoto(room, 'host', 'abcd');
  assert.ok(view(room, 'guest', T0).slide.photoId > 0, "Slide Race's photo has its own id");
  assert.equal(view(room, 'guest', T0).opponent.photoId, null, 'and is not anybody\'s face');
});

test('a player may change their photo once every two seconds', async () => {
  assert.equal(FACE_INTERVAL_MS, 2000);
  const room = lobby();
  const store = fakeStore();
  const t = later();
  await setFace(store, room, 'host', jpeg(300, 1), t);
  await assert.rejects(setFace(store, room, 'host', jpeg(300, 2), t + 1999), /Slow down a little/);
  await assert.rejects(setFace(store, room, 'host', null, t + 1000), /Slow down a little/);
  await setFace(store, room, 'guest', jpeg(300, 3), t + 10);
  assert.equal(store.map.size, 2, 'the other player has their own pace and the refused change kept the photo');
  await setFace(store, room, 'host', jpeg(300, 2), t + 2000);
  assert.equal(await getFace(store, room, view(room, 'guest', T0).opponent.photoId), jpeg(300, 2));
});

test('a seat given up (as the timers do) takes its photo away on the next sweep', async () => {
  const room = lobby();
  const store = fakeStore();
  await setFace(store, room, 'host', jpeg(300, 1), later());
  await setFace(store, room, 'guest', jpeg(300, 2), later());
  room.players.guest = null;
  await sweepFaces(store, room);
  assert.equal(store.map.size, 1);
});
