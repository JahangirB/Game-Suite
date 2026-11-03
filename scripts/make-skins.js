#!/usr/bin/env node
// Turns the Ship Battle skins in scripts/skins/skins.js into the files phones download:
//
//   node scripts/make-skins.js            skins/<id>.json and skins/index.json
//   node scripts/make-skins.js --previews also skins/<id>.png, a picture of a board for the
//                                         store (needs Playwright and its Chromium)
//
// Each skin draws on a 2D canvas (kit.js). Here it draws on a stand-in canvas that writes down
// every shape instead: an outline (SVG path data, in squares: 1 is one square of the sea) and
// how it's painted. The app paints the same list (ui/art/SkinArt.kt), so a new skin needs no
// app update: publish its file and phones list it under Available.
//
// A skin file:
//   { "id", "name", "tag", "about", "version", "kind" (only "3d": the app draws it in 3D,
//     assets/ships3d; the flat art below is for apps from before 3D),
//     "sea": [ops],            the whole 10 x 10 sea, grid lines included
//     "ships": { "carrier": [ops], ... }   each lying flat from (0, 0) to (length, 1)
//     "hit": [ops], "miss": [ops] }        each in a square from (0, 0) to (1, 1)
// and an op is one of:
//   ["f", d, paint]                 fill outline d
//   ["F", d, paint]                 fill outline d, holes even-odd
//   ["s", d, paint, width, cap, join, dash]   stroke it (cap/join: 0 butt/miter, 1 round, 2 square/bevel)
//   ["c", d]                        clip to it, until the next ")"
//   ["("] / [")"]                   save / restore
//   ["t", text, x, y, size, paint, align, bold]   a few words (align: 0 left, 1 centre)
// A paint is "#AARRGGBB", {"l": [x0, y0, x1, y1], "stops": [[t, "#AARRGGBB"], ...]} or
// {"r": [cx, cy, r], "stops": [...]}.

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'skins');
const S = 100;      // pixels to a square while drawing; everything is stored in squares
const N = 10;       // the sea is 10 x 10
const KINDS = { carrier: 5, battleship: 4, cruiser: 3, submarine: 3, destroyer: 2 };

// ---- Colours ------------------------------------------------------------------------------------
const NAMED = { white: '#ffffff', black: '#000000', transparent: 'rgba(0,0,0,0)' };
function rgba(css) {
  css = String(css).trim().toLowerCase();
  if (NAMED[css]) css = NAMED[css];
  let m = css.match(/^#([0-9a-f]{3,8})$/);
  if (m) {
    let h = m[1];
    if (h.length === 3 || h.length === 4) h = [...h].map((x) => x + x).join('');
    const n = parseInt(h.slice(0, 6), 16), a = h.length === 8 ? parseInt(h.slice(6), 16) / 255 : 1;
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a];
  }
  m = css.match(/^rgba?\(([^)]+)\)$/);
  if (m) {
    const p = m[1].split(',').map((x) => parseFloat(x));
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1];
  }
  throw new Error(`a colour I can't read: ${css}`);
}
function hex([r, g, b, a], alpha = 1) {
  const two = (v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0');
  return '#' + two(a * alpha * 255) + two(r) + two(g) + two(b);
}

// ---- The stand-in canvas ----------------------------------------------------------------------
const q = (v) => Math.round(v / S * 1000) / 1000;
class Gradient {
  constructor(kind, coords) { this.kind = kind; this.coords = coords; this.stops = []; }
  addColorStop(t, c) { this.stops.push([t, c]); }
}
class Recorder {
  constructor() {
    this.ops = [];
    this.m = [1, 0, 0, 1, 0, 0];
    this.stack = [];
    Object.assign(this, { fillStyle: '#000', strokeStyle: '#000', lineWidth: 1, lineCap: 'butt', lineJoin: 'miter', globalAlpha: 1, font: '10px sans-serif', textAlign: 'start', textBaseline: 'alphabetic', shadowBlur: 0, shadowColor: 'transparent', globalCompositeOperation: 'source-over' });
    this.dash = [];
    this.d = [];
  }
  // Transforms
  pt(x, y) { const [a, b, c, d, e, f] = this.m; return [a * x + c * y + e, b * x + d * y + f]; }
  scaleOf() { const [a, b] = this.m; return Math.hypot(a, b); }
  transform(a2, b2, c2, d2, e2, f2) {
    const [a, b, c, d, e, f] = this.m;
    this.m = [a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2, a * e2 + c * f2 + e, b * e2 + d * f2 + f];
  }
  translate(x, y) { this.transform(1, 0, 0, 1, x, y); }
  rotate(t) { this.transform(Math.cos(t), Math.sin(t), -Math.sin(t), Math.cos(t), 0, 0); }
  scale(x, y) { this.transform(x, 0, 0, y, 0, 0); }
  setTransform(a, b, c, d, e, f) { this.m = [a, b, c, d, e, f]; }
  save() {
    this.stack.push({ m: this.m.slice(), dash: this.dash.slice(), s: { fillStyle: this.fillStyle, strokeStyle: this.strokeStyle, lineWidth: this.lineWidth, lineCap: this.lineCap, lineJoin: this.lineJoin, globalAlpha: this.globalAlpha, font: this.font, textAlign: this.textAlign, textBaseline: this.textBaseline } });
    this.ops.push(['(']);
  }
  restore() {
    const top = this.stack.pop();
    if (!top) return;
    this.m = top.m; this.dash = top.dash; Object.assign(this, top.s);
    this.ops.push([')']);
  }
  // Paths, stored already transformed
  beginPath() { this.d = []; this.at = null; this.start = null; }
  moveTo(x, y) { const p = this.pt(x, y); this.d.push(`M${q(p[0])} ${q(p[1])}`); this.at = [x, y]; this.start = [x, y]; }
  lineTo(x, y) { if (!this.at) return this.moveTo(x, y); const p = this.pt(x, y); this.d.push(`L${q(p[0])} ${q(p[1])}`); this.at = [x, y]; }
  quadraticCurveTo(cx, cy, x, y) {
    if (!this.at) this.moveTo(cx, cy);
    const c = this.pt(cx, cy), p = this.pt(x, y);
    this.d.push(`Q${q(c[0])} ${q(c[1])} ${q(p[0])} ${q(p[1])}`); this.at = [x, y];
  }
  bezierCurveTo(c1x, c1y, c2x, c2y, x, y) {
    if (!this.at) this.moveTo(c1x, c1y);
    const a = this.pt(c1x, c1y), b = this.pt(c2x, c2y), p = this.pt(x, y);
    this.d.push(`C${q(a[0])} ${q(a[1])} ${q(b[0])} ${q(b[1])} ${q(p[0])} ${q(p[1])}`); this.at = [x, y];
  }
  closePath() { if (this.d.length) this.d.push('Z'); if (this.start) this.at = this.start; }
  rect(x, y, w, h) { this.moveTo(x, y); this.lineTo(x + w, y); this.lineTo(x + w, y + h); this.lineTo(x, y + h); this.closePath(); }
  ellipse(cx, cy, rx, ry, rot, a0, a1, ccw = false) {
    // As cubic curves, a quarter turn at most each.
    let sweep = a1 - a0;
    if (!ccw && sweep < 0) sweep = (sweep % (2 * Math.PI)) + 2 * Math.PI;
    if (ccw && sweep > 0) sweep = (sweep % (2 * Math.PI)) - 2 * Math.PI;
    if (Math.abs(a1 - a0) >= 2 * Math.PI) sweep = ccw ? -2 * Math.PI : 2 * Math.PI;
    const at = (t) => {
      const x = Math.cos(t) * rx, y = Math.sin(t) * ry;
      return [cx + x * Math.cos(rot) - y * Math.sin(rot), cy + x * Math.sin(rot) + y * Math.cos(rot)];
    };
    const d = (t) => {
      const x = -Math.sin(t) * rx, y = Math.cos(t) * ry;
      return [x * Math.cos(rot) - y * Math.sin(rot), x * Math.sin(rot) + y * Math.cos(rot)];
    };
    const p0 = at(a0);
    if (this.at) this.lineTo(p0[0], p0[1]); else this.moveTo(p0[0], p0[1]);
    const n = Math.max(1, Math.ceil(Math.abs(sweep) / (Math.PI / 2) - 1e-9));
    const step = sweep / n, k = 4 / 3 * Math.tan(step / 4);
    for (let i = 0; i < n; i++) {
      const t0 = a0 + i * step, t1 = t0 + step;
      const s0 = at(t0), s1 = at(t1), v0 = d(t0), v1 = d(t1);
      this.bezierCurveTo(s0[0] + v0[0] * k, s0[1] + v0[1] * k, s1[0] - v1[0] * k, s1[1] - v1[1] * k, s1[0], s1[1]);
    }
  }
  arc(cx, cy, r, a0, a1, ccw = false) { this.ellipse(cx, cy, r, r, 0, a0, a1, ccw); }
  arcTo(x1, y1, x2, y2, r) { this.lineTo(x1, y1); this.lineTo(x2, y2); }
  roundRect(x, y, w, h, r = 0) {
    if (w < 0) { x += w; w = -w; }
    if (h < 0) { y += h; h = -h; }
    r = Math.min(Array.isArray(r) ? r[0] : r, w / 2, h / 2);
    this.moveTo(x + r, y);
    this.lineTo(x + w - r, y); this.arc(x + w - r, y + r, r, -Math.PI / 2, 0);
    this.lineTo(x + w, y + h - r); this.arc(x + w - r, y + h - r, r, 0, Math.PI / 2);
    this.lineTo(x + r, y + h); this.arc(x + r, y + h - r, r, Math.PI / 2, Math.PI);
    this.lineTo(x, y + r); this.arc(x + r, y + r, r, Math.PI, Math.PI * 1.5);
    this.closePath();
  }
  // Paint
  paint(style) {
    const a = this.globalAlpha;
    if (style instanceof Gradient) {
      const stops = style.stops.map(([t, c]) => [Math.round(t * 1000) / 1000, hex(rgba(c), a)]);
      if (style.kind === 'l') {
        const [x0, y0, x1, y1] = style.coords, p = this.pt(x0, y0), r = this.pt(x1, y1);
        return { l: [q(p[0]), q(p[1]), q(r[0]), q(r[1])], stops };
      }
      // One circle, as the app draws gradients: the outer one, with the inner radius as a first stop.
      const [, , r0, x1, y1, r1] = style.coords, c = this.pt(x1, y1), k = r1 > 0 ? r0 / r1 : 0;
      return { r: [q(c[0]), q(c[1]), q(r1 * this.scaleOf())], stops: stops.map(([t, col]) => [Math.round((k + t * (1 - k)) * 1000) / 1000, col]) };
    }
    return hex(rgba(style), a);
  }
  createLinearGradient(x0, y0, x1, y1) { return new Gradient('l', [x0, y0, x1, y1]); }
  createRadialGradient(x0, y0, r0, x1, y1, r1) { return new Gradient('r', [x0, y0, r0, x1, y1, r1]); }
  setLineDash(d) { this.dash = d || []; }
  getLineDash() { return this.dash; }
  path() { return this.d.join(''); }
  fill(rule) { if (this.d.length) this.ops.push([rule === 'evenodd' ? 'F' : 'f', this.path(), this.paint(this.fillStyle)]); }
  stroke() {
    if (!this.d.length) return;
    const k = this.scaleOf();
    const op = ['s', this.path(), this.paint(this.strokeStyle), q(this.lineWidth * k), { butt: 0, round: 1, square: 2 }[this.lineCap] ?? 0, { miter: 0, round: 1, bevel: 2 }[this.lineJoin] ?? 0];
    if (this.dash.length) op.push(this.dash.map((v) => q(v * k)));
    this.ops.push(op);
  }
  clip() { this.ops.push(['c', this.path()]); }
  fillRect(x, y, w, h) { const keep = this.d; this.beginPath(); this.rect(x, y, w, h); this.fill(); this.d = keep; }
  strokeRect(x, y, w, h) { const keep = this.d; this.beginPath(); this.rect(x, y, w, h); this.stroke(); this.d = keep; }
  clearRect() {}
  fillText(text, x, y) {
    const size = parseFloat((this.font.match(/([\d.]+)px/) || [])[1] || 10);
    const bold = /\b(bold|[6-9]00)\b/.test(this.font);
    // The app draws text from its baseline: move a middle baseline down by about a third of the size.
    const p = this.pt(x, y + (this.textBaseline === 'middle' ? size * 0.35 : 0));
    this.ops.push(['t', String(text), q(p[0]), q(p[1]), q(size * this.scaleOf()), this.paint(this.fillStyle), this.textAlign === 'center' ? 1 : 0, bold ? 1 : 0]);
  }
  measureText(t) { return { width: String(t).length * parseFloat((this.font.match(/([\d.]+)px/) || [])[1] || 10) * 0.55 }; }
  drawImage() { throw new Error('skins draw shapes, not pictures'); }
}

// ---- The skins ----------------------------------------------------------------------------------
function load() {
  const ctx = { Math, console, TAU: Math.PI * 2, N };
  vm.createContext(ctx);
  for (const f of ['helpers', 'kit', 'skins']) {
    // Top-level consts stay in the script; SKINS is read back out.
    vm.runInContext(fs.readFileSync(path.join(__dirname, 'skins', f + '.js'), 'utf8').replace(/^const SKINS =/m, 'var SKINS ='), ctx, { filename: f + '.js' });
  }
  return ctx.SKINS;
}
function record(draw) { const c = new Recorder(); draw(c); return c.ops; }
function shipOps(skin, kind) {
  // As the app lays a ship: from the square's left, across its middle.
  return record((c) => { c.translate(S * 0.07, S / 2); skin.ship(c, kind, KINDS[kind] * S - S * 0.14, S * 0.34, S); });
}

function main() {
  const skins = load();
  fs.mkdirSync(OUT, { recursive: true });
  const index = [];
  for (const skin of skins) {
    const file = {
      id: skin.id, name: skin.name, tag: skin.tag, about: skin.about, version: skin.version || 1,
      sea: record((c) => skin.sea(c, S)),
      ships: Object.fromEntries(Object.keys(KINDS).map((k) => [k, shipOps(skin, k)])),
      hit: record((c) => skin.hit(c, S / 2, S / 2, S)),
      miss: record((c) => skin.miss(c, S / 2, S / 2, S)),
    };
    if (skin.kind) file.kind = skin.kind;
    const text = JSON.stringify(file);
    fs.writeFileSync(path.join(OUT, skin.id + '.json'), text);
    index.push({ id: skin.id, name: skin.name, tag: skin.tag, version: file.version, size: Buffer.byteLength(text) });
    console.log(`${skin.id}: ${Math.round(Buffer.byteLength(text) / 1024)} KB`);
  }
  fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify({ skins: index }, null, 1) + '\n');
  if (process.argv.includes('--previews')) return previews(skins);
}

// Pictures for the store, drawn from the same skins in a real browser: a game in progress.
async function previews(skins) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  await page.setContent('<canvas id="c" width="240" height="240"></canvas>');
  const code = ['helpers', 'kit', 'skins'].map((f) => fs.readFileSync(path.join(__dirname, 'skins', f + '.js'), 'utf8')).join('\n');
  await page.addScriptTag({ content: `const TAU = Math.PI * 2, N = ${N};\n${code}\nwindow.SKINS_ = SKINS;` });
  for (const skin of skins) {
    if (skin.kind === '3d') {
      fs.writeFileSync(path.join(OUT, skin.id + '.png'), await preview3d(browser));
      continue;
    }
    const png = await page.evaluate((id) => {
      const skin = window.SKINS_.find((k) => k.id === id);
      const cv = document.getElementById('c'), c = cv.getContext('2d'), s = 24;
      c.setTransform(1, 0, 0, 1, 0, 0); c.clearRect(0, 0, 240, 240);
      skin.sea(c, s);
      const fleet = [['carrier', 1, 1, 5, true], ['battleship', 8, 0, 4, false], ['cruiser', 2, 5, 3, true], ['submarine', 6, 6, 3, false], ['destroyer', 1, 8, 2, true]];
      for (const [kind, x, y, len, h] of fleet) {
        c.save(); c.translate(x * s, y * s);
        if (!h) { c.translate(s, 0); c.rotate(Math.PI / 2); }
        c.translate(s * 0.07, s / 2); skin.ship(c, kind, len * s - s * 0.14, s * 0.34, s); c.restore();
      }
      for (const [x, y] of [[0, 3], [5, 3], [7, 4], [4, 7], [9, 8], [3, 9]]) skin.miss(c, (x + 0.5) * s, (y + 0.5) * s, s);
      for (const [x, y] of [[2, 1], [3, 1], [6, 7]]) skin.hit(c, (x + 0.5) * s, (y + 0.5) * s, s);
      return cv.toDataURL('image/png').split(',')[1];
    }, skin.id);
    fs.writeFileSync(path.join(OUT, skin.id + '.png'), Buffer.from(png, 'base64'));
  }
  await browser.close();
  console.log('previews written');
}

// The 3D Fleet's picture: the app's own 3D page with a game in progress, as a phone draws it.
async function preview3d(browser) {
  const page = await browser.newPage({ viewport: { width: 240, height: 240 } });
  await page.addInitScript(() => { window.Android = { ready() {}, event() {}, cell() {} }; });
  await page.goto('file://' + path.join(ROOT, 'app/src/main/assets/ships3d/index.html'));
  await page.waitForFunction(() => typeof fleet !== 'undefined');
  await page.evaluate(() => fleet.state({
    ships: [
      { kind: 'carrier', x: 1, y: 1, len: 5, h: true }, { kind: 'battleship', x: 8, y: 0, len: 4, h: false },
      { kind: 'cruiser', x: 2, y: 5, len: 3, h: true, sunk: true }, { kind: 'submarine', x: 6, y: 6, len: 3, h: false },
      { kind: 'destroyer', x: 1, y: 8, len: 2, h: true },
    ],
    shots: [[0, 3, 0], [5, 3, 0], [7, 4, 0], [4, 8, 0], [2, 1, 1], [3, 1, 1], [2, 5, 1], [3, 5, 1], [4, 5, 1]].map(([x, y, h]) => ({ x, y, hit: !!h })),
  }));
  await page.waitForTimeout(800);
  const png = await page.screenshot();
  await page.close();
  return png;
}

module.exports = { Recorder, load };
if (require.main === module) Promise.resolve(main()).catch((e) => { console.error(e); process.exit(1); });
