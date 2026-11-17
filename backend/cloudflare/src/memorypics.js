// Memory Pairs with the players' own photos (cap and token "memory-photos"): whoever opened the game picks as many
// photos as the deck has pairs. Each is a small JPEG as base64, set by one call ('mempic', one photo for one place in
// the deck) and fetched once by its id ('getmempic'), never inside the room state that is saved on every move. Each
// photo is stored on its own under `mempic:<id>`; the room keeps only the ids (`room.memoryPics`, one per pair, null
// where none has been sent) and a counter (`room.memSeq`). The same rules are in LocalRoom.kt (nearby). Apps Script
// has none of this and does not send the cap.

import { GameError, memoryPhotosAllowed } from './room.js';
import { cleanFace } from './faces.js';
import { DECKS, cleanDeck, cleanOptions, memoryPics, pairsOf } from './memory.js';

/**
 * The most base64 characters one photo may take. A 160 x 160 JPEG of a busy photo is about 9 to 13 KB at quality 85
 * (12,000 to 17,000 characters), about half that at quality 45, where the app's quality ladder stops: a photo of
 * at most 16,000 characters always fits, with room to spare, and a deck of 18 is under 290,000 characters.
 */
export const MEMPIC_MAX = 16_000;
/** The most places a deck has: the biggest deck's pairs (36 cards). */
export const MEMPIC_SLOTS = Math.max(...DECKS) / 2;
/** The least time between two photos in a room (ms); the app sends one at a time, a little slower than this. */
export const MEMPIC_INTERVAL_MS = 150;
const PREFIX = 'mempic:';

/**
 * The opener ([role]) sets the photo for place [slot] (a string) or empties it (null or ''); between games only, and
 * only while everyone in the room has the token and the deck is the photos. Everyone gets ready again. [store] is the
 * room's storage (get, put, delete). The old photo of the place is deleted, and the new one gets a new id.
 */
export async function setMemoryPic(store, room, role, slot, photo, now) {
  if (room.game !== 'memory') throw new GameError('Only Memory Pairs has photos.');
  const opener = room.openedBy || 'host';
  if (role !== opener) throw new GameError('Only whoever opened the game can pick the photos.');
  if (room.status === 'started') throw new GameError('The photos can only change between games.');
  if (!memoryPhotosAllowed(room) || cleanDeck(room.options) !== 'photos') throw new GameError('Your photos need the latest Pip & Pop on every phone.');
  const pairs = pairsOf(cleanOptions(room.options).cards);
  const n = slot;
  if (!Number.isInteger(n) || n < 0 || n >= pairs) throw new GameError('That deck has no such place.');
  const clean = photo == null || photo === '' ? null : cleanFace(photo, MEMPIC_MAX);
  if (room.memoryPicAt != null && now - room.memoryPicAt < MEMPIC_INTERVAL_MS) throw new GameError('Slow down a little.');
  room.memoryPicAt = now;
  const pics = memoryPics(room);
  const old = pics[n];
  if (clean == null) {
    pics[n] = null;
  } else {
    room.memSeq = (room.memSeq || 0) + 1;
    await store.put(PREFIX + room.memSeq, clean);
    pics[n] = room.memSeq;
  }
  room.memoryPics = pics;
  if (old != null) await store.delete(PREFIX + old);
  for (const r of ['host', 'guest', 'p3', 'p4']) if (room.players[r]) room.players[r].ready = false;
  room.version = (room.version || 0) + 1;
}

/** The photo with this id, or null (replaced, removed or never was). Only while the photos are in use in the room. */
export async function getMemoryPic(store, room, id) {
  const n = Number(id);
  if (!Number.isInteger(n) || n <= 0) return null;
  if (room.status === 'closed' || !memoryPhotosAllowed(room) || !memoryPics(room).includes(n)) return null;
  return (await store.get(PREFIX + n)) ?? null;
}

/** Deletes every stored photo the room does not point at: after the deck or its size changed, another game opened, or the room closed. */
export async function sweepMemoryPics(store, room) {
  const keep = new Set(room.status === 'closed' ? [] : memoryPics(room).filter((id) => id != null).map((id) => PREFIX + id));
  const stored = await store.list({ prefix: PREFIX });
  const drop = [...stored.keys()].filter((k) => !keep.has(k));
  if (drop.length) await store.delete(drop);
}
