import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { cleanOptions, isRealWord, legalMoves, play, setPick, start, tick, view } from '../src/wordladder.js';

const cases = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/wordladder-cases.json', import.meta.url), 'utf8')).cases;

for (const c of cases) {
  test(`word ladder: ${c.name}`, () => {
    const state = start(Math.random, ['h', 'g'], c.options);
    const coin = () => c.random;
    c.steps.forEach((s, n) => {
      const at = `step ${n + 1} (${s.do})`;
      if (s.do === 'view') {
        const v = view(state, s.mark);
        for (const [k, want] of Object.entries(s.expect)) assert.deepEqual(v[k], want, `${at}: ${k}`);
        return;
      }
      if (s.do === 'tick') {
        const r = tick(state, s.now);
        assert.equal(r.passed, s.passed, at);
        assert.equal(r.turn, s.turn, at);
        return;
      }
      const run = () => (s.do === 'pick' ? setPick(state, s.mark, { word: s.word }, coin, s.now ?? 0) : play(state, s.mark, { word: s.word }, s.now ?? 0));
      if (s.error) return assert.throws(run, { message: s.error }, at);
      const r = run();
      if (s.do === 'pick') assert.equal(r.revealed, s.revealed ?? false, at);
      else {
        assert.equal(r.over, s.over ?? false, at);
        assert.equal(r.winner ?? null, s.winner ?? null, at);
        assert.equal(r.reason ?? null, s.reason ?? null, at);
      }
    });
  });
}

test('word ladder: a mark that is not in the game cannot pick', () => {
  const state = start(Math.random, ['h', 'g'], { length: 4 });
  assert.throws(() => setPick(state, '3', { word: 'COLD' }, () => 0.1), /not in this game/);
  setPick(state, 'h', { word: 'COLD' }, () => 0.1);
  assert.throws(() => setPick(state, '3', { word: 'TOLL' }, () => 0.1), /not in this game/);
  assert.equal(setPick(state, 'g', { word: 'TOLL' }, () => 0.1).revealed, true);
  assert.deepEqual(state.chain, ['COLD']);
});

test('word ladder: options, moves and the real-word list', () => {
  assert.deepEqual(cleanOptions({ length: 6, timer: 60 }), { length: 6, timer: 60 });
  assert.deepEqual(cleanOptions({ length: 9, timer: 5 }), { length: 5, timer: 0 });
  assert.deepEqual(cleanOptions(), { length: 5, timer: 0 });
  assert.ok(isRealWord('CORD') && !isRealWord('DAMN') && !isRealWord('CXLD'));
  const state = start(Math.random, ['h', 'g'], { length: 4 });
  setPick(state, 'h', { word: 'MOON' }, () => 0.1);
  setPick(state, 'g', { word: 'MOOD' }, () => 0.1);
  assert.ok(legalMoves(state).includes('MUON') && legalMoves(state).includes('SOON') && !legalMoves(state).includes('MOON'));
});
