// ---- Drawing helpers ------------------------------------------------------------------------
function rng(seed) {
  return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
function circle(c, x, y, r) { c.beginPath(); c.arc(x, y, r, 0, TAU); }
function dot(c, x, y, r, fill) { circle(c, x, y, r); c.fillStyle = fill; c.fill(); }
function ring(c, x, y, r, col, w) { circle(c, x, y, r); c.strokeStyle = col; c.lineWidth = w; c.stroke(); }
function ellipse(c, x, y, rx, ry, rot = 0) { c.beginPath(); c.ellipse(x, y, rx, ry, rot, 0, TAU); }
function rrect(c, x, y, w, h, r) { c.beginPath(); c.roundRect(x, y, w, h, r); }
function line(c, x1, y1, x2, y2, col, w, cap = 'round') { c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.strokeStyle = col; c.lineWidth = w; c.lineCap = cap; c.stroke(); }
function paint(c, fill, stroke, w) { if (fill) { c.fillStyle = fill; c.fill(); } if (stroke) { c.strokeStyle = stroke; c.lineWidth = w; c.lineJoin = 'round'; c.stroke(); } }
function clipped(c, draw) { c.save(); c.clip(); draw(); c.restore(); }
/** The app's hull: a stern, straight sides and a pointed bow at x = L. */
function hull(c, L, w, bow, stern) {
  c.beginPath();
  c.moveTo(stern, -w); c.lineTo(L - bow, -w);
  c.quadraticCurveTo(L - bow * 0.15, -w * 0.9, L, 0); c.quadraticCurveTo(L - bow * 0.15, w * 0.9, L - bow, w);
  c.lineTo(stern, w); c.quadraticCurveTo(0, w, 0, w * 0.45); c.lineTo(0, -w * 0.45); c.quadraticCurveTo(0, -w, stern, -w); c.closePath();
}
function grid(c, s, col, w) {
  c.strokeStyle = col; c.lineWidth = w;
  for (let i = 1; i < N; i++) {
    c.beginPath(); c.moveTo(i * s, 0); c.lineTo(i * s, N * s); c.moveTo(0, i * s); c.lineTo(N * s, i * s); c.stroke();
  }
}
function waves(c, s, col, count, seed) {
  const r = rng(seed);
  c.strokeStyle = col; c.lineWidth = s * 0.035; c.lineCap = 'round';
  for (let k = 0; k < count; k++) {
    const x = r() * N * s, y = r() * N * s, l = s * (0.2 + r() * 0.2);
    c.beginPath(); c.moveTo(x, y); c.quadraticCurveTo(x + l / 2, y - s * 0.07, x + l, y); c.stroke();
  }
}
function burst(c, cx, cy, rOut, rIn, points, fill, stroke, w) {
  c.beginPath();
  for (let k = 0; k < points * 2; k++) {
    const r = k % 2 ? rIn : rOut, a = k * TAU / (points * 2) - Math.PI / 2;
    k ? c.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r) : c.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
  }
  c.closePath(); paint(c, fill, stroke, w);
}
function kids(L, s) { return Math.round((L + s * 0.14) / s); }

