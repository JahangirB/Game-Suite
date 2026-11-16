// Players' photos (cap "faces"): a small JPEG as base64, set by one call ('face') and fetched once by
// its id ('getface'), never inside the room state that is saved on every move. Each photo is stored on
// its own under `face:<id>`; the room keeps only the id (`player.photoId`) and a counter (`room.faceSeq`).
// The same rules are in LocalRoom.kt (nearby). Apps Script has none of this and does not send the cap.

import { GameError } from './room.js';

/** The most base64 characters a photo may take (a 96 x 96 JPEG is about 4,000). */
export const FACE_MAX = 12_000;
const PREFIX = 'face:';
/** The least time between one player's photo changes (ms); the same as the nearby host. */
export const FACE_INTERVAL_MS = 2000;

/** The photo as a clean base64 string; throws unless it is a JPEG of at most [max] (FACE_MAX) characters. */
export function cleanFace(photo, max = FACE_MAX) {
  if (typeof photo !== 'string' || photo.length === 0) throw new GameError('That photo is not usable.');
  if (photo.length > max) throw new GameError('That photo is too big.');
  if (photo.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(photo)) throw new GameError('That photo is not usable.');
  let bytes;
  try {
    bytes = atob(photo);
  } catch {
    throw new GameError('That photo is not usable.');
  }
  // A JPEG starts FF D8 FF and ends FF D9.
  const b = (i) => bytes.charCodeAt(i);
  if (bytes.length < 8 || b(0) !== 0xff || b(1) !== 0xd8 || b(2) !== 0xff || b(bytes.length - 2) !== 0xff || b(bytes.length - 1) !== 0xd9) {
    throw new GameError('That photo is not usable.');
  }
  return photo;
}

/**
 * [role] sets their photo (at most one change every FACE_INTERVAL_MS) (a string) or removes it (null or ''). [store] is the room's storage (get, put,
 * delete). The old photo is deleted, and the new one gets a new id, so a phone fetches it again once.
 */
export async function setFace(store, room, role, photo, now) {
  const player = room.players[role];
  if (!player) throw new GameError('You are not in this game.');
  const clean = photo == null || photo === '' ? null : cleanFace(photo);
  if (player.faceAt != null && now - player.faceAt < FACE_INTERVAL_MS) throw new GameError('Slow down a little.');
  player.faceAt = now;
  const old = player.photoId ?? null;
  if (clean == null) {
    player.photoId = null;
  } else {
    room.faceSeq = (room.faceSeq || 0) + 1;
    await store.put(PREFIX + room.faceSeq, clean);
    player.photoId = room.faceSeq;
  }
  if (old != null) await store.delete(PREFIX + old);
}

/** The photo with this id, or null (it was replaced, removed or never was). Only a photo of someone in the room. */
export async function getFace(store, room, id) {
  const n = Number(id);
  if (!Number.isInteger(n) || n <= 0) return null;
  const inRoom = Object.values(room.players).some((p) => p && !p.left && p.photoId === n);
  return inRoom ? (await store.get(PREFIX + n)) ?? null : null;
}

/** Deletes every stored photo that no player of the room points at (someone left, or the room closed). */
export async function sweepFaces(store, room) {
  const keep = new Set(room.status === 'closed' ? [] : Object.values(room.players).filter((p) => p && !p.left && p.photoId != null).map((p) => PREFIX + p.photoId));
  const stored = await store.list({ prefix: PREFIX });
  const drop = [...stored.keys()].filter((k) => !keep.has(k));
  if (drop.length) await store.delete(drop);
}
