// ---- The kit: seas, ships and marks that skins are put together from ----------------------------
const INK = '#1e2a3a';
const KI = { carrier: 0, battleship: 1, cruiser: 2, submarine: 3, destroyer: 4 };
const SPR = ['#ff5a7a', '#ffd23f', '#4cc2e6', '#6ad07a', '#9d7bff', '#ffffff'];
const seed = (x, y) => rng(Math.round(x * 131 + y * 17));
function fillAll(c, s, col) { c.fillStyle = col; c.fillRect(0, 0, N * s, N * s); }
function heartPath(c, x, y, r) {
  c.beginPath(); c.moveTo(x, y + r * 0.9);
  c.bezierCurveTo(x - r * 1.3, y - r * 0.2, x - r * 0.5, y - r * 1.1, x, y - r * 0.35);
  c.bezierCurveTo(x + r * 0.5, y - r * 1.1, x + r * 1.3, y - r * 0.2, x, y + r * 0.9); c.closePath();
}
function star5(c, x, y, r) { c.beginPath(); for (let k = 0; k < 10; k++) { const rr = k % 2 ? r * 0.45 : r, a = k * Math.PI / 5 - Math.PI / 2; k ? c.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr) : c.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); } c.closePath(); }
function capsule(c, x0, y0, len, h) { rrect(c, x0, y0 - h, len, h * 2, h); }
function shade(c, path, col) { path(); clipped(c, () => { c.fillStyle = col; c.fillRect(-999, 0, 9999, 999); }); }

// ---- Seas -----------------------------------------------------------------------------------------
const SEA = {
  plain: (base, gridc = 'rgba(255,255,255,0.35)') => (c, s) => { fillAll(c, s, base); grid(c, s, gridc, 1); },
  waves: (base, wave, gridc, count = 90, sd = 3) => (c, s) => { fillAll(c, s, base); waves(c, s, wave, count, sd); grid(c, s, gridc, 1); },
  dots: (base, dotc, gridc, r = 0.07) => (c, s) => {
    fillAll(c, s, base);
    for (let i = 0; i < N * 2; i++) for (let j = 0; j < N * 2; j++) dot(c, (i + (j % 2) * 0.5 + 0.25) * s / 2, (j + 0.5) * s / 2, r * s, dotc);
    grid(c, s, gridc, 1);
  },
  stripes: (base, stripe, gridc, width = 0.22) => (c, s) => {
    fillAll(c, s, base); c.save(); c.beginPath(); c.rect(0, 0, N * s, N * s); c.clip();
    for (let x = -N * s; x < N * s * 2; x += s * width * 2) line(c, x, 0, x + N * s, N * s, stripe, s * width, 'butt');
    c.restore(); grid(c, s, gridc, 1);
  },
  gingham: (base, band, gridc) => (c, s) => {
    fillAll(c, s, base);
    for (let i = 0; i < N * 2; i += 2) { c.fillStyle = band; c.fillRect(i * s / 2, 0, s / 2, N * s); c.fillRect(0, i * s / 2, N * s, s / 2); }
    grid(c, s, gridc, 1);
  },
  sprinkles: (base, cols, gridc = 'rgba(255,255,255,0.55)', count = 70) => (c, s) => {
    fillAll(c, s, base); const r = rng(9);
    for (let k = 0; k < count; k++) { const x = r() * N * s, y = r() * N * s, a = r() * TAU; line(c, x, y, x + Math.cos(a) * s * 0.1, y + Math.sin(a) * s * 0.1, cols[k % cols.length], s * 0.035); }
    grid(c, s, gridc, 1.5);
  },
  tiles: (base, lineCol, lw = 2) => (c, s) => { fillAll(c, s, base); grid(c, s, lineCol, lw); },
  stars: (base, nebulas, gridc = 'rgba(160,170,255,0.16)') => (c, s) => {
    fillAll(c, s, base);
    nebulas.forEach(([fx, fy, col]) => { const g = c.createRadialGradient(fx * N * s, fy * N * s, 0, fx * N * s, fy * N * s, N * s * 0.45); g.addColorStop(0, col); g.addColorStop(1, 'rgba(0,0,0,0)'); c.fillStyle = g; c.fillRect(0, 0, N * s, N * s); });
    const r = rng(11); for (let k = 0; k < 150; k++) dot(c, r() * N * s, r() * N * s, 0.3 + r() * 1.1, `rgba(255,255,255,${0.4 + r() * 0.6})`);
    grid(c, s, gridc, 1);
  },
  wood: (base, plank, gridc = 'rgba(60,30,10,0.18)') => (c, s) => {
    fillAll(c, s, base); const r = rng(5);
    for (let y = 0; y < N * s; y += s * 0.5) { line(c, 0, y, N * s, y, plank, 1.5); for (let k = 0; k < 4; k++) { const x = r() * N * s; line(c, x, y + s * 0.15, x + s * 0.8, y + s * 0.18, 'rgba(60,30,10,0.12)', 1); } }
    grid(c, s, gridc, 1);
  },
  grass: (base, blade, gridc = 'rgba(255,255,255,0.25)') => (c, s) => {
    fillAll(c, s, base); const r = rng(6);
    for (let k = 0; k < 260; k++) { const x = r() * N * s, y = r() * N * s; line(c, x, y, x + (r() - 0.5) * s * 0.08, y - s * 0.12, blade, 1.2); }
    grid(c, s, gridc, 1);
  },
  paper: (base, lineCol) => (c, s) => { fillAll(c, s, base); grid(c, s, lineCol, 1); line(c, s * 0.12, 0, s * 0.12, N * s, 'rgba(225,80,80,0.45)', 1.2, 'butt'); },
  sky: (top, bottom, cloud, gridc = 'rgba(255,255,255,0.3)') => (c, s) => {
    const g = c.createLinearGradient(0, 0, 0, N * s); g.addColorStop(0, top); g.addColorStop(1, bottom); c.fillStyle = g; c.fillRect(0, 0, N * s, N * s);
    if (cloud) { const r = rng(8); for (let k = 0; k < 9; k++) { const x = r() * N * s, y = r() * N * s; for (let j = 0; j < 4; j++) dot(c, x + j * s * 0.25, y + Math.sin(j) * s * 0.08, s * (0.18 + r() * 0.12), cloud); } }
    grid(c, s, gridc, 1);
  },
  bubbles: (base, bub, gridc) => (c, s) => {
    fillAll(c, s, base); const r = rng(7);
    for (let k = 0; k < 60; k++) { const x = r() * N * s, y = r() * N * s, rr = s * (0.04 + r() * 0.09); ring(c, x, y, rr, bub, 1); dot(c, x - rr * 0.35, y - rr * 0.35, rr * 0.25, bub); }
    grid(c, s, gridc, 1);
  },
  honey: (base, lineCol) => (c, s) => {
    fillAll(c, s, base); const h = s * 0.35; c.strokeStyle = lineCol; c.lineWidth = 1.5;
    for (let row = -1; row < N * s / (h * 1.5) + 1; row++) for (let col = -1; col < N * s / (h * Math.sqrt(3)) + 1; col++) {
      const cx = col * h * Math.sqrt(3) + (row % 2) * h * Math.sqrt(3) / 2, cy = row * h * 1.5;
      c.beginPath(); for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3 + Math.PI / 6; k ? c.lineTo(cx + Math.cos(a) * h, cy + Math.sin(a) * h) : c.moveTo(cx + Math.cos(a) * h, cy + Math.sin(a) * h); } c.closePath(); c.stroke();
    }
    grid(c, s, 'rgba(120,70,0,0.2)', 1);
  },
  circuit: (base, trace) => (c, s) => {
    fillAll(c, s, base); const r = rng(12);
    for (let k = 0; k < 40; k++) { let x = Math.round(r() * N * 2) * s / 2, y = Math.round(r() * N * 2) * s / 2; c.beginPath(); c.moveTo(x, y); for (let j = 0; j < 3; j++) { r() > 0.5 ? x += (r() - 0.5) * s * 2 : y += (r() - 0.5) * s * 2; c.lineTo(x, y); } c.strokeStyle = trace; c.lineWidth = 1.5; c.stroke(); dot(c, x, y, 2.5, trace); }
    grid(c, s, 'rgba(255,255,255,0.12)', 1);
  },
  snow: (base, flake, gridc = 'rgba(120,150,190,0.25)') => (c, s) => {
    fillAll(c, s, base); const r = rng(4);
    for (let k = 0; k < 60; k++) { const x = r() * N * s, y = r() * N * s, rr = s * 0.06; for (let j = 0; j < 3; j++) { const a = j * Math.PI / 3; line(c, x - Math.cos(a) * rr, y - Math.sin(a) * rr, x + Math.cos(a) * rr, y + Math.sin(a) * rr, flake, 1); } }
    grid(c, s, gridc, 1);
  },
  lava: (base, glow, crust) => (c, s) => {
    fillAll(c, s, base); const r = rng(2);
    for (let k = 0; k < 14; k++) { const x = r() * N * s, y = r() * N * s, g = c.createRadialGradient(x, y, 0, x, y, s * 1.2); g.addColorStop(0, glow); g.addColorStop(1, 'rgba(0,0,0,0)'); c.fillStyle = g; c.fillRect(0, 0, N * s, N * s); }
    for (let k = 0; k < 12; k++) { const x = r() * N * s, y = r() * N * s; ellipse(c, x, y, s * (0.3 + r() * 0.4), s * (0.15 + r() * 0.2), r() * 3); paint(c, crust); }
    grid(c, s, 'rgba(255,200,120,0.18)', 1);
  },
  lily: (base, pad, gridc = 'rgba(220,240,230,0.15)') => (c, s) => {
    fillAll(c, s, base); const r = rng(4);
    for (let k = 0; k < 10; k++) { const x = r() * N * s, y = r() * N * s, rr = s * (0.3 + r() * 0.3), a = r() * TAU; c.beginPath(); c.moveTo(x, y); c.arc(x, y, rr, a + 0.35, a + TAU - 0.05); c.closePath(); paint(c, pad, 'rgba(0,0,0,0.2)', 1); }
    grid(c, s, gridc, 1);
  },
  chalk: (base) => (c, s) => {
    fillAll(c, s, base); const r = rng(3);
    for (let k = 0; k < 8; k++) { ellipse(c, r() * N * s, r() * N * s, s * 1.4, s * 0.5, r() * 3); paint(c, 'rgba(255,255,255,0.05)'); }
    grid(c, s, 'rgba(255,255,255,0.22)', 1);
  },
  blueprint: (base, lineCol) => (c, s) => {
    fillAll(c, s, base); c.strokeStyle = lineCol; c.globalAlpha = 0.25; c.lineWidth = 0.6;
    for (let i = 0; i < N * 4; i++) { c.beginPath(); c.moveTo(i * s / 4, 0); c.lineTo(i * s / 4, N * s); c.moveTo(0, i * s / 4); c.lineTo(N * s, i * s / 4); c.stroke(); }
    c.globalAlpha = 1; grid(c, s, lineCol, 1);
  },
  halftone: (base, dotc) => (c, s) => {
    fillAll(c, s, base);
    for (let i = 0; i < N * 3; i++) for (let j = 0; j < N * 3; j++) dot(c, (i + 0.5) * s / 3, (j + 0.5) * s / 3, s * 0.035 * (1 + Math.sin(i * 0.4 + j * 0.3)), dotc);
    grid(c, s, 'rgba(0,0,0,0.25)', 1.5);
  },
  felt: (base) => (c, s) => {
    fillAll(c, s, base); const r = rng(1);
    for (let k = 0; k < 400; k++) dot(c, r() * N * s, r() * N * s, 0.6, 'rgba(0,0,0,0.12)');
    grid(c, s, 'rgba(255,255,255,0.15)', 1);
  },
  road: (base, lane) => (c, s) => {
    fillAll(c, s, base);
    c.setLineDash([s * 0.3, s * 0.3]); for (let i = 1; i < N; i++) line(c, 0, i * s, N * s, i * s, lane, 2, 'butt'); c.setLineDash([]);
    grid(c, s, 'rgba(255,255,255,0.08)', 1);
  },
  soil: (base, furrow) => (c, s) => { fillAll(c, s, base); for (let y = s * 0.25; y < N * s; y += s * 0.5) line(c, 0, y, N * s, y, furrow, s * 0.12); grid(c, s, 'rgba(255,255,255,0.15)', 1); },
  sand: (base, ripple) => (c, s) => {
    fillAll(c, s, base);
    for (let y = s * 0.3; y < N * s; y += s * 0.6) { c.beginPath(); for (let x = 0; x <= N * s; x += s * 0.2) c.lineTo(x, y + Math.sin(x / s * 2) * s * 0.08); c.strokeStyle = ripple; c.lineWidth = 1.5; c.stroke(); }
    grid(c, s, 'rgba(120,80,30,0.15)', 1);
  },
  ice: (base, floe, gridc = 'rgba(80,120,160,0.2)') => (c, s) => {
    fillAll(c, s, base); const r = rng(10);
    for (let k = 0; k < 16; k++) { const x = r() * N * s, y = r() * N * s; c.beginPath(); for (let j = 0; j < 6; j++) { const a = j * TAU / 6 + r() * 0.5, d = s * (0.3 + r() * 0.35); j ? c.lineTo(x + Math.cos(a) * d, y + Math.sin(a) * d) : c.moveTo(x + Math.cos(a) * d, y + Math.sin(a) * d); } c.closePath(); paint(c, floe, 'rgba(120,160,200,0.6)', 1); }
    grid(c, s, gridc, 1);
  },
  night: (base, starc, moon, gridc = 'rgba(255,255,255,0.12)') => (c, s) => {
    fillAll(c, s, base); const r = rng(13);
    for (let k = 0; k < 60; k++) dot(c, r() * N * s, r() * N * s, 0.5 + r() * 0.9, starc);
    if (moon) { dot(c, N * s * 0.8, N * s * 0.15, s * 0.6, moon); dot(c, N * s * 0.83, N * s * 0.13, s * 0.5, base); }
    grid(c, s, gridc, 1);
  },
  pixel: (a, b, gridc = 'rgba(0,0,0,0.2)') => (c, s) => {
    for (let i = 0; i < N * 4; i++) for (let j = 0; j < N * 4; j++) { c.fillStyle = (i + j) % 2 ? a : b; c.fillRect(i * s / 4, j * s / 4, s / 4 + 0.5, s / 4 + 0.5); }
    grid(c, s, gridc, 1.5);
  },
  swirl: (base, swirlc, gridc = 'rgba(255,255,255,0.4)') => (c, s) => {
    fillAll(c, s, base); const r = rng(9);
    for (let k = 0; k < 22; k++) { const x = r() * N * s, y = r() * N * s; c.beginPath(); c.arc(x, y, s * (0.3 + r() * 0.4), 0.2, 2.6); c.strokeStyle = swirlc; c.lineWidth = s * 0.06; c.lineCap = 'round'; c.stroke(); }
    grid(c, s, gridc, 1.5);
  },
};

// ---- Round items, drawn one per square in a row --------------------------------------------------
const ITEM = {
  donut(c, x, y, r, col) {
    dot(c, x, y, r, '#e3a86b'); ring(c, x, y, r, '#a8703c', r * 0.08);
    c.beginPath(); for (let k = 0; k <= 24; k++) { const a = k * TAU / 24, rr = r * (0.8 + Math.sin(k * 2.3) * 0.05); k ? c.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr) : c.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); } c.closePath(); paint(c, col);
    const R = seed(x, y); for (let k = 0; k < 8; k++) { const a = R() * TAU, d = r * (0.45 + R() * 0.25), b = R() * TAU; line(c, x + Math.cos(a) * d, y + Math.sin(a) * d, x + Math.cos(a) * d + Math.cos(b) * r * 0.18, y + Math.sin(a) * d + Math.sin(b) * r * 0.18, SPR[k % 6], r * 0.08); }
    dot(c, x, y, r * 0.3, '#c98d55'); ring(c, x, y, r * 0.3, '#a8703c', r * 0.06);
  },
  cupcake(c, x, y, r, col) {
    dot(c, x, y, r, '#f6d6e4'); for (let k = 0; k < 16; k++) { const a = k * TAU / 16; line(c, x + Math.cos(a) * r * 0.75, y + Math.sin(a) * r * 0.75, x + Math.cos(a) * r, y + Math.sin(a) * r, '#d9a9bf', r * 0.06); }
    ring(c, x, y, r, '#b37a96', r * 0.06);
    for (const [k, f] of [[0, 0.78], [1, 0.58], [2, 0.38]]) { dot(c, x - k * r * 0.04, y - k * r * 0.04, r * f, k % 2 ? '#ffffff' : col); ring(c, x - k * r * 0.04, y - k * r * 0.04, r * f, 'rgba(0,0,0,0.12)', r * 0.04); }
    dot(c, x, y, r * 0.17, '#d81b3a'); dot(c, x - r * 0.05, y - r * 0.05, r * 0.05, '#ffffff');
  },
  scoop(c, x, y, r, col) {
    for (let k = 0; k < 6; k++) dot(c, x - r * 0.6 + k * r * 0.24, y + r * 0.82 + (k % 2) * r * 0.12, r * 0.18, col);
    dot(c, x, y, r * 0.92, col); ring(c, x, y, r * 0.92, 'rgba(0,0,0,0.15)', r * 0.05);
    dot(c, x - r * 0.3, y - r * 0.32, r * 0.2, 'rgba(255,255,255,0.55)');
  },
  gummy(c, x, y, r, col) {
    c.save(); c.globalAlpha = 0.88;
    ellipse(c, x - r * 0.15, y, r * 0.62, r * 0.5); paint(c, col);
    dot(c, x + r * 0.55, y, r * 0.42, col); dot(c, x + r * 0.7, y - r * 0.38, r * 0.16, col); dot(c, x + r * 0.7, y + r * 0.38, r * 0.16, col);
    for (const [dx, dy] of [[0.15, -0.55], [0.15, 0.55], [-0.55, -0.45], [-0.55, 0.45]]) dot(c, x + dx * r, y + dy * r, r * 0.2, col);
    c.restore(); dot(c, x - r * 0.25, y - r * 0.2, r * 0.15, 'rgba(255,255,255,0.6)');
  },
  lolly(c, x, y, r, col) {
    dot(c, x, y, r * 0.9, '#ffffff');
    c.beginPath(); for (let t = 0; t < 22; t += 0.2) { const rr = r * 0.85 * t / 22, a = t; t ? c.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr) : c.moveTo(x, y); } c.strokeStyle = col; c.lineWidth = r * 0.22; c.stroke();
    ring(c, x, y, r * 0.9, 'rgba(0,0,0,0.15)', r * 0.05);
  },
  macaron(c, x, y, r, col) {
    dot(c, x, y, r * 0.95, col); ring(c, x, y, r * 0.95, 'rgba(0,0,0,0.12)', r * 0.06);
    c.save(); c.setLineDash([r * 0.08, r * 0.06]); ring(c, x, y, r * 0.8, 'rgba(255,255,255,0.7)', r * 0.08); c.restore();
    dot(c, x - r * 0.3, y - r * 0.3, r * 0.18, 'rgba(255,255,255,0.5)');
  },
  cookie(c, x, y, r, col) {
    c.beginPath(); for (let k = 0; k <= 20; k++) { const a = k * TAU / 20, rr = r * (0.9 + Math.sin(k * 3.1) * 0.06); k ? c.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr) : c.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); } c.closePath(); paint(c, col, '#8a5a2c', r * 0.06);
    const R = seed(x, y); for (let k = 0; k < 6; k++) { const a = R() * TAU, d = R() * r * 0.6; dot(c, x + Math.cos(a) * d, y + Math.sin(a) * d, r * 0.12, '#4a2a14'); }
  },
  mallow(c, x, y, r, col) { rrect(c, x - r * 0.8, y - r * 0.8, r * 1.6, r * 1.6, r * 0.5); paint(c, col, 'rgba(0,0,0,0.12)', r * 0.06); dot(c, x - r * 0.3, y - r * 0.3, r * 0.2, 'rgba(255,255,255,0.7)'); },
  pearl(c, x, y, r, col) { dot(c, x, y, r * 0.75, col); dot(c, x - r * 0.25, y - r * 0.25, r * 0.2, 'rgba(255,255,255,0.55)'); ring(c, x, y, r * 0.75, 'rgba(0,0,0,0.3)', r * 0.05); },
  bean(c, x, y, r, col) { ellipse(c, x, y, r * 0.9, r * 0.55, 0.3); paint(c, col, 'rgba(0,0,0,0.2)', r * 0.05); ellipse(c, x - r * 0.25, y - r * 0.2, r * 0.3, r * 0.12, 0.3); paint(c, 'rgba(255,255,255,0.5)'); },
  maki(c, x, y, r, col) {
    dot(c, x, y, r * 0.95, '#1d2a1f'); dot(c, x, y, r * 0.78, '#fbfaf4');
    const R = seed(x, y); for (let k = 0; k < 10; k++) { const a = R() * TAU, d = r * (0.45 + R() * 0.28); ellipse(c, x + Math.cos(a) * d, y + Math.sin(a) * d, r * 0.07, r * 0.04, a); paint(c, '#e6e2d4'); }
    dot(c, x, y, r * 0.38, col);
  },
  egg(c, x, y, r) {
    c.beginPath(); for (let k = 0; k <= 16; k++) { const a = k * TAU / 16, rr = r * (0.88 + Math.sin(k * 2.7) * 0.1); k ? c.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr) : c.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); } c.closePath(); paint(c, '#ffffff', 'rgba(0,0,0,0.12)', r * 0.04);
    dot(c, x + r * 0.1, y, r * 0.38, '#ffc21a'); dot(c, x, y - r * 0.1, r * 0.12, 'rgba(255,255,255,0.6)');
  },
  fruit(c, x, y, r, col, i, n, kind) {
    if (kind === 'carrier') { dot(c, x, y, r, '#2f9e44'); dot(c, x, y, r * 0.86, '#d8f5c4'); dot(c, x, y, r * 0.78, '#ff4f5e'); for (let k = 0; k < 6; k++) { const a = k * TAU / 6; ellipse(c, x + Math.cos(a) * r * 0.45, y + Math.sin(a) * r * 0.45, r * 0.07, r * 0.04, a); paint(c, '#1b1b1f'); } return; }
    if (kind === 'battleship' || kind === 'destroyer') { const o = kind === 'battleship' ? ['#ff9a1f', '#ffd08a'] : ['#ffe14d', '#fff4b0']; dot(c, x, y, r, o[0]); dot(c, x, y, r * 0.85, o[1]); for (let k = 0; k < 8; k++) { const a = k * TAU / 8; line(c, x, y, x + Math.cos(a) * r * 0.82, y + Math.sin(a) * r * 0.82, o[0], r * 0.07); } return; }
    if (kind === 'cruiser') { dot(c, x, y, r, '#7a5a2c'); dot(c, x, y, r * 0.88, '#8fd14f'); dot(c, x, y, r * 0.3, '#f4f8d9'); for (let k = 0; k < 10; k++) { const a = k * TAU / 10; dot(c, x + Math.cos(a) * r * 0.45, y + Math.sin(a) * r * 0.45, r * 0.05, '#1b1b1f'); } return; }
    for (const [dx, dy] of [[-0.3, -0.3], [0.3, -0.3], [0, 0.25], [-0.45, 0.3], [0.45, 0.3]]) { dot(c, x + dx * r, y + dy * r, r * 0.38, '#8b4fc7'); dot(c, x + (dx - 0.1) * r, y + (dy - 0.1) * r, r * 0.08, 'rgba(255,255,255,0.4)'); }
  },
  pizza(c, x, y, r) {
    dot(c, x, y, r, '#e3a35a'); dot(c, x, y, r * 0.84, '#d9412b'); dot(c, x, y, r * 0.78, '#ffd36e');
    const R = seed(x, y); for (let k = 0; k < 5; k++) { const a = R() * TAU, d = R() * r * 0.5; dot(c, x + Math.cos(a) * d, y + Math.sin(a) * d, r * 0.15, '#b5261e'); }
  },
  dumpling(c, x, y, r) {
    c.beginPath(); c.moveTo(x - r * 0.9, y + r * 0.3); c.quadraticCurveTo(x, y - r * 1.2, x + r * 0.9, y + r * 0.3); c.quadraticCurveTo(x, y + r * 0.8, x - r * 0.9, y + r * 0.3); paint(c, '#fbf3e3', 'rgba(120,90,50,0.4)', r * 0.06);
    for (let k = -2; k <= 2; k++) line(c, x + k * r * 0.25, y - r * 0.25 + Math.abs(k) * r * 0.08, x + k * r * 0.3, y - r * 0.55 + Math.abs(k) * r * 0.12, 'rgba(120,90,50,0.45)', r * 0.06);
  },
  bean2(c, x, y, r, col) { ellipse(c, x, y, r * 0.9, r * 0.62); paint(c, col, 'rgba(0,0,0,0.3)', r * 0.05); c.beginPath(); c.moveTo(x - r * 0.7, y); c.bezierCurveTo(x - r * 0.2, y - r * 0.3, x + r * 0.2, y + r * 0.3, x + r * 0.7, y); c.strokeStyle = 'rgba(0,0,0,0.45)'; c.lineWidth = r * 0.1; c.stroke(); },
  penguin(c, x, y, r) {
    for (const sd of [-1, 1]) { ellipse(c, x - r * 0.1, y + sd * r * 0.7, r * 0.4, r * 0.14, sd * 0.4); paint(c, '#1f2430'); }
    ellipse(c, x - r * 0.1, y, r * 0.75, r * 0.6); paint(c, '#1f2430'); dot(c, x + r * 0.55, y, r * 0.42, '#1f2430');
    ellipse(c, x + r * 0.98, y, r * 0.2, r * 0.1); paint(c, '#ff9a1f'); dot(c, x + r * 0.65, y - r * 0.18, r * 0.08, '#ffffff'); dot(c, x + r * 0.65, y + r * 0.18, r * 0.08, '#ffffff');
  },
  ladybug(c, x, y, r) {
    dot(c, x + r * 0.7, y, r * 0.32, '#1b1b1f'); dot(c, x, y, r * 0.8, '#e5302a'); line(c, x - r * 0.8, y, x + r * 0.6, y, '#1b1b1f', r * 0.06);
    for (const [dx, dy] of [[0.2, -0.4], [-0.3, -0.35], [0.2, 0.4], [-0.3, 0.35], [-0.6, 0]]) dot(c, x + dx * r, y + dy * r, r * 0.13, '#1b1b1f');
    dot(c, x - r * 0.2, y - r * 0.5, r * 0.08, 'rgba(255,255,255,0.5)');
  },
  turtle(c, x, y, r, col) {
    for (const [dx, dy] of [[0.45, -0.6], [0.45, 0.6], [-0.45, -0.6], [-0.45, 0.6]]) { ellipse(c, x + dx * r, y + dy * r, r * 0.22, r * 0.13, dy > 0 ? 0.6 : -0.6); paint(c, '#7cc46b'); }
    dot(c, x + r * 0.85, y, r * 0.22, '#7cc46b'); ellipse(c, x, y, r * 0.75, r * 0.62); paint(c, col, '#3d5a2a', r * 0.05);
    for (let k = 0; k < 6; k++) { const a = k * TAU / 6; line(c, x, y, x + Math.cos(a) * r * 0.65, y + Math.sin(a) * r * 0.55, 'rgba(40,60,20,0.5)', r * 0.05); }
    dot(c, x, y, r * 0.22, 'rgba(255,255,255,0.15)');
  },
  frog(c, x, y, r) {
    for (const sd of [-1, 1]) { ellipse(c, x - r * 0.45, y + sd * r * 0.6, r * 0.35, r * 0.15, sd * 0.3); paint(c, '#3f9a3a'); }
    ellipse(c, x, y, r * 0.75, r * 0.6); paint(c, '#5cc04f', '#2e6b2a', r * 0.05);
    for (const sd of [-1, 1]) { dot(c, x + r * 0.5, y + sd * r * 0.32, r * 0.2, '#5cc04f'); dot(c, x + r * 0.55, y + sd * r * 0.32, r * 0.1, '#1b1b1f'); }
  },
  bee(c, x, y, r) {
    for (const sd of [-1, 1]) { ellipse(c, x - r * 0.05, y + sd * r * 0.55, r * 0.4, r * 0.22, sd * 0.3); paint(c, 'rgba(255,255,255,0.75)', 'rgba(0,0,0,0.2)', 1); }
    ellipse(c, x, y, r * 0.75, r * 0.5); paint(c, '#ffc61a', '#1b1b1f', r * 0.05);
    ellipse(c, x, y, r * 0.75, r * 0.5); clipped(c, () => { for (const f of [-0.3, 0.05, 0.4]) line(c, x + f * r, y - r, x + f * r, y + r, '#1b1b1f', r * 0.16, 'butt'); });
    dot(c, x + r * 0.8, y, r * 0.22, '#1b1b1f');
  },
  cat(c, x, y, r, col) {
    c.beginPath(); c.arc(x - r * 0.4, y, r * 0.75, 1.2, 5.0); c.strokeStyle = col; c.lineWidth = r * 0.18; c.lineCap = 'round'; c.stroke();
    dot(c, x, y, r * 0.75, col); ring(c, x, y, r * 0.75, 'rgba(0,0,0,0.2)', r * 0.05);
    for (const sd of [-1, 1]) { c.beginPath(); c.moveTo(x + r * 0.45, y + sd * r * 0.25); c.lineTo(x + r * 0.95, y + sd * r * 0.45); c.lineTo(x + r * 0.55, y + sd * r * 0.6); c.closePath(); paint(c, col, 'rgba(0,0,0,0.2)', r * 0.04); }
    line(c, x - r * 0.1, y - r * 0.3, x + r * 0.15, y - r * 0.2, 'rgba(0,0,0,0.25)', r * 0.08); line(c, x - r * 0.1, y + r * 0.3, x + r * 0.15, y + r * 0.2, 'rgba(0,0,0,0.25)', r * 0.08);
  },
  marble(c, x, y, r, col) {
    dot(c, x, y, r * 0.8, 'rgba(220,240,255,0.85)');
    c.beginPath(); c.arc(x, y, r * 0.45, 0.3, 3.6); c.strokeStyle = col; c.lineWidth = r * 0.22; c.stroke();
    ring(c, x, y, r * 0.8, 'rgba(0,0,0,0.25)', r * 0.05); dot(c, x - r * 0.3, y - r * 0.3, r * 0.15, '#ffffff');
  },
  die(c, x, y, r, col, i) {
    rrect(c, x - r * 0.75, y - r * 0.75, r * 1.5, r * 1.5, r * 0.25); paint(c, col, 'rgba(0,0,0,0.3)', r * 0.06);
    const pips = [[[0, 0]], [[-1, -1], [1, 1]], [[-1, -1], [0, 0], [1, 1]], [[-1, -1], [1, -1], [-1, 1], [1, 1]], [[-1, -1], [1, -1], [0, 0], [-1, 1], [1, 1]], [[-1, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [1, 1]]][i % 6];
    for (const [px, py] of pips) dot(c, x + px * r * 0.42, y + py * r * 0.42, r * 0.13, col === '#ffffff' ? '#1b1b1f' : '#ffffff');
  },
  pool(c, x, y, r, col, i) {
    dot(c, x, y, r * 0.8, col); ring(c, x, y, r * 0.8, 'rgba(0,0,0,0.3)', r * 0.04); dot(c, x, y, r * 0.38, '#ffffff');
    c.fillStyle = '#1b1b1f'; c.font = `700 ${r * 0.48}px sans-serif`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(i + 1), x, y + r * 0.03);
    dot(c, x - r * 0.35, y - r * 0.35, r * 0.1, 'rgba(255,255,255,0.7)');
  },
  ball(c, x, y, r, col, i, n, kind) {
    const rr = r * 0.8;
    if (kind === 'carrier') { dot(c, x, y, rr, '#ffffff'); for (let k = 0; k < 5; k++) { const a = k * TAU / 5; dot(c, x + Math.cos(a) * rr * 0.62, y + Math.sin(a) * rr * 0.62, rr * 0.2, '#1b1b1f'); } dot(c, x, y, rr * 0.25, '#1b1b1f'); }
    else if (kind === 'battleship') { dot(c, x, y, rr, '#f28a2a'); line(c, x - rr, y, x + rr, y, '#3a1d0e', rr * 0.08); line(c, x, y - rr, x, y + rr, '#3a1d0e', rr * 0.08); c.beginPath(); c.arc(x - rr * 1.2, y, rr * 0.9, -0.9, 0.9); c.stroke(); }
    else if (kind === 'cruiser') { dot(c, x, y, rr, '#d6f04a'); c.beginPath(); c.arc(x - rr, y, rr * 0.8, -0.9, 0.9); c.strokeStyle = '#ffffff'; c.lineWidth = rr * 0.12; c.stroke(); c.beginPath(); c.arc(x + rr, y, rr * 0.8, Math.PI - 0.9, Math.PI + 0.9); c.stroke(); }
    else if (kind === 'submarine') { dot(c, x, y, rr, '#ffffff'); for (const sd of [-1, 1]) { c.beginPath(); c.arc(x + sd * rr * 1.1, y, rr * 0.75, sd > 0 ? Math.PI - 0.8 : -0.8, sd > 0 ? Math.PI + 0.8 : 0.8); c.strokeStyle = '#d6303a'; c.lineWidth = rr * 0.08; c.setLineDash([rr * 0.08, rr * 0.08]); c.stroke(); c.setLineDash([]); } }
    else { const cols = ['#ff4f5e', '#ffd23f', '#4cc2e6', '#ffffff', '#6ad07a', '#ff9a3d']; for (let k = 0; k < 6; k++) { c.beginPath(); c.moveTo(x, y); c.arc(x, y, rr, k * TAU / 6, (k + 1) * TAU / 6); c.closePath(); paint(c, cols[k]); } }
    ring(c, x, y, rr, 'rgba(0,0,0,0.3)', rr * 0.06);
  },
  pumpkin(c, x, y, r) {
    for (const f of [-0.45, 0.45, 0]) { ellipse(c, x, y + f * r * 0.6, r * 0.85, r * 0.42); paint(c, '#ff8a1f', '#c25a0a', r * 0.05); }
    rrect(c, x + r * 0.6, y - r * 0.08, r * 0.3, r * 0.16, r * 0.05); paint(c, '#4a7a2a');
  },
  easterEgg(c, x, y, r, col) {
    ellipse(c, x, y, r * 0.9, r * 0.65); paint(c, col, 'rgba(0,0,0,0.15)', r * 0.05);
    ellipse(c, x, y, r * 0.9, r * 0.65); clipped(c, () => { c.beginPath(); for (let k = 0; k <= 8; k++) c.lineTo(x - r + k * r * 0.25, y + (k % 2 ? -1 : 1) * r * 0.15); c.strokeStyle = '#ffffff'; c.lineWidth = r * 0.12; c.stroke(); for (const sd of [-1, 1]) line(c, x - r, y + sd * r * 0.42, x + r, y + sd * r * 0.42, 'rgba(255,255,255,0.7)', r * 0.08); });
  },
  lantern(c, x, y, r, col) {
    rrect(c, x - r * 0.9, y - r * 0.25, r * 0.15, r * 0.5, 2); paint(c, '#e8b923');
    ellipse(c, x, y, r * 0.75, r * 0.6); paint(c, col, '#7a1010', r * 0.05);
    for (const f of [-0.4, 0, 0.4]) { c.beginPath(); c.ellipse(x, y, r * 0.75, Math.abs(f) * r * 0.6 + 0.01, 0, 0, TAU); c.strokeStyle = 'rgba(255,220,120,0.6)'; c.lineWidth = r * 0.04; c.stroke(); }
    rrect(c, x + r * 0.75, y - r * 0.25, r * 0.15, r * 0.5, 2); paint(c, '#e8b923'); line(c, x + r * 0.9, y, x + r * 1.15, y, '#e8b923', r * 0.05);
  },
  heart(c, x, y, r, col) { heartPath(c, x, y, r * 0.9); paint(c, col, 'rgba(0,0,0,0.2)', r * 0.05); dot(c, x - r * 0.3, y - r * 0.25, r * 0.12, 'rgba(255,255,255,0.6)'); },
  firefly(c, x, y, r) {
    const g = c.createRadialGradient(x - r * 0.3, y, 0, x - r * 0.3, y, r * 1.1); g.addColorStop(0, 'rgba(255,240,120,0.95)'); g.addColorStop(1, 'rgba(255,240,120,0)'); c.fillStyle = g; c.fillRect(x - r * 1.5, y - r * 1.2, r * 3, r * 2.4);
    ellipse(c, x + r * 0.15, y, r * 0.45, r * 0.25); paint(c, '#3a3020'); dot(c, x - r * 0.3, y, r * 0.28, '#fff6a0');
    for (const sd of [-1, 1]) { ellipse(c, x + r * 0.1, y + sd * r * 0.32, r * 0.3, r * 0.14, sd * 0.4); paint(c, 'rgba(255,255,255,0.45)'); }
  },
  fish(c, x, y, r, col) {
    c.beginPath(); c.moveTo(x - r * 0.5, y); c.lineTo(x - r * 1.0, y - r * 0.45); c.lineTo(x - r * 1.0, y + r * 0.45); c.closePath(); paint(c, col);
    ellipse(c, x + r * 0.05, y, r * 0.7, r * 0.42); paint(c, col, 'rgba(0,0,0,0.25)', r * 0.05);
    ellipse(c, x + r * 0.05, y, r * 0.7, r * 0.42); clipped(c, () => { for (const f of [-0.2, 0.2]) line(c, x + f * r, y - r, x + f * r, y + r, 'rgba(255,255,255,0.75)', r * 0.12, 'butt'); });
    dot(c, x + r * 0.45, y - r * 0.12, r * 0.08, '#1b1b1f');
  },
  cloud(c, x, y, r) { for (const [dx, dy, f] of [[-0.45, 0.1, 0.45], [0, -0.15, 0.55], [0.45, 0.1, 0.45], [0, 0.25, 0.45]]) dot(c, x + dx * r, y + dy * r, r * f, '#ffffff'); },
  coin(c, x, y, r) { dot(c, x, y, r * 0.8, '#f5c542'); ring(c, x, y, r * 0.8, '#b88a12', r * 0.08); ring(c, x, y, r * 0.58, '#d9a51e', r * 0.05); star5(c, x, y, r * 0.3); paint(c, '#d9a51e'); },
  planet(c, x, y, r, col) { dot(c, x, y, r * 0.55, col); c.beginPath(); c.ellipse(x, y, r * 0.95, r * 0.28, -0.4, 0, TAU); c.strokeStyle = '#ffe9b0'; c.lineWidth = r * 0.08; c.stroke(); dot(c, x - r * 0.18, y - r * 0.18, r * 0.12, 'rgba(255,255,255,0.4)'); },
};

// ---- Ships made of items, or of one long object ------------------------------------------------------
/** One [item] per square, the front one a little bigger. */
const row = (item, cols, lead = true) => (c, kind, L, w, s) => {
  const n = kids(L, s), step = L / n;
  for (let i = n - 1; i >= 0; i--) item(c, L - (i + 0.5) * step, 0, w * (lead && i === 0 ? 1.12 : 1.0), cols[KI[kind]], i, n, kind, s);
};
const LONG = {
  candyCane: (cols) => (c, kind, L, w, s) => {
    const h = w * 0.42, body = () => capsule(c, 0, 0, L * 0.85, h);
    body(); paint(c, '#ffffff'); body(); clipped(c, () => { for (let x = -L; x < L * 2; x += s * 0.25) line(c, x, h * 2, x + h * 3, -h * 2, cols[KI[kind]], s * 0.08, 'butt'); });
    body(); paint(c, null, 'rgba(0,0,0,0.2)', 1);
    c.beginPath(); c.arc(L * 0.85, -w * 0.45, w * 0.45, Math.PI * 0.5, -Math.PI * 0.6, true); c.strokeStyle = '#ffffff'; c.lineWidth = h * 2; c.lineCap = 'round'; c.stroke();
    c.save(); c.setLineDash([s * 0.08, s * 0.1]); c.strokeStyle = cols[KI[kind]]; c.stroke(); c.restore();
  },
  popsicle: (cols) => (c, kind, L, w, s) => {
    rrect(c, -s * 0.05, -w * 0.2, L * 0.25, w * 0.4, w * 0.15); paint(c, '#e8c58a', '#b8935a', 1);
    const col = cols[KI[kind]]; rrect(c, L * 0.12, -w * 0.95, L * 0.88, w * 1.9, w * 0.8); paint(c, col);
    rrect(c, L * 0.12, -w * 0.95, L * 0.88, w * 1.9, w * 0.8); clipped(c, () => { c.fillStyle = 'rgba(255,255,255,0.35)'; c.fillRect(L * 0.12, -w * 0.95, L * 0.3, w * 2); c.fillStyle = 'rgba(0,0,0,0.12)'; c.fillRect(L * 0.75, -w * 0.95, L * 0.3, w * 2); });
    dot(c, L, w * 0.4, w * 0.45, 'rgba(0,0,0,0)'); line(c, L * 0.3, -w * 0.5, L * 0.85, -w * 0.5, 'rgba(255,255,255,0.5)', s * 0.04);
  },
  sausage: () => (c, kind, L, w, s) => {
    if (kind === 'cruiser' || kind === 'carrier') {
      for (let y = -1; y <= 1; y += 2) { c.beginPath(); for (let x = 0; x <= L; x += s * 0.1) c.lineTo(x, y * w * 0.4 + Math.sin(x / s * 5) * w * 0.15); c.strokeStyle = y > 0 ? '#b33a2a' : '#e86a50'; c.lineWidth = w * 0.7; c.lineCap = 'round'; c.stroke(); }
      c.beginPath(); for (let x = 0; x <= L; x += s * 0.1) c.lineTo(x, Math.sin(x / s * 5) * w * 0.15); c.strokeStyle = '#fbe3c8'; c.lineWidth = w * 0.25; c.stroke(); return;
    }
    capsule(c, 0, 0, L, w * 0.65); paint(c, '#a8452a', '#6b2414', s * 0.025);
    for (let x = L * 0.2; x < L * 0.9; x += s * 0.35) line(c, x, -w * 0.5, x + s * 0.15, w * 0.5, '#5a1e10', s * 0.03);
    line(c, L * 0.1, -w * 0.3, L * 0.9, -w * 0.3, 'rgba(255,255,255,0.3)', s * 0.04);
  },
  hotdog: () => (c, kind, L, w, s) => {
    capsule(c, 0, 0, L, w * 0.95); paint(c, '#f0b860', '#b8833a', s * 0.02);
    capsule(c, -s * 0.06, 0, L + s * 0.12, w * 0.45); paint(c, '#b8452a', '#6b2414', s * 0.02);
    c.beginPath(); for (let x = L * 0.1, k = 0; x < L * 0.9; x += s * 0.12, k++) c.lineTo(x, (k % 2 ? -1 : 1) * w * 0.25); c.strokeStyle = '#ffd21f'; c.lineWidth = s * 0.05; c.lineJoin = 'round'; c.stroke();
  },
  baguette: () => (c, kind, L, w, s) => {
    ellipse(c, L / 2, 0, L / 2, w * 0.7); paint(c, '#e0a55a', '#a8703a', s * 0.02);
    for (let x = L * 0.18; x < L * 0.85; x += s * 0.38) { ellipse(c, x, 0, s * 0.14, w * 0.25, -0.6); paint(c, '#f5d39a'); }
  },
  carrot: (cols) => (c, kind, L, w, s) => {
    if (kind === 'submarine') { for (let x = 0; x < L; x += s * 0.16) for (const y of [-0.4, 0, 0.4]) { rrect(c, x, y * w - w * 0.18, s * 0.14, w * 0.36, 3); paint(c, '#ffd23f', '#d9a51e', 1); } c.beginPath(); c.moveTo(0, -w); c.quadraticCurveTo(-s * 0.3, 0, 0, w); paint(c, '#7cc46b'); return; }
    const green = kind === 'cruiser';
    c.beginPath(); c.moveTo(0, -w * 0.75); c.lineTo(L * 0.9, -w * (green ? 0.65 : 0.2)); c.quadraticCurveTo(L, 0, L * 0.9, w * (green ? 0.65 : 0.2)); c.lineTo(0, w * 0.75); c.closePath(); paint(c, green ? '#4f9a3a' : '#ff8a1f', green ? '#2e5f22' : '#c25a0a', s * 0.02);
    for (let x = L * 0.15; x < L * 0.85; x += s * 0.25) line(c, x, -w * 0.4, x + s * 0.05, -w * 0.1, green ? '#8fd16f' : '#c25a0a', s * 0.025);
    if (!green) for (const a of [-0.5, 0, 0.5]) { c.beginPath(); c.moveTo(0, 0); c.quadraticCurveTo(-s * 0.2, a * w, -s * 0.35, a * w * 1.6); c.strokeStyle = '#4f9a3a'; c.lineWidth = s * 0.06; c.stroke(); }
  },
  taco: () => (c, kind, L, w, s) => {
    const n = kids(L, s), step = L / n;
    for (let i = 0; i < n; i++) { const x = (i + 0.5) * step; c.beginPath(); c.arc(x, w * 0.3, w * 1.0, Math.PI, 0); c.closePath(); paint(c, '#f5c040', '#c7902a', s * 0.02); for (const [dx, col] of [[-0.4, '#6ad07a'], [0, '#e8412b'], [0.4, '#ffe58a']]) dot(c, x + dx * w, -w * 0.2, w * 0.22, col); }
  },
  crayon: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]], h = w * 0.45;
    rrect(c, 0, -h, L * 0.84, h * 2, h * 0.3); paint(c, col, 'rgba(0,0,0,0.3)', 1);
    rrect(c, L * 0.15, -h, L * 0.55, h * 2, 1); paint(c, 'rgba(255,255,255,0.18)'); line(c, L * 0.2, 0, L * 0.65, 0, 'rgba(0,0,0,0.3)', s * 0.03);
    c.beginPath(); c.moveTo(L * 0.84, -h); c.lineTo(L, 0); c.lineTo(L * 0.84, h); c.closePath(); paint(c, col, 'rgba(0,0,0,0.3)', 1);
  },
  pencil: (cols) => (c, kind, L, w, s) => {
    const h = w * 0.45;
    rrect(c, 0, -h, L * 0.12, h * 2, h * 0.4); paint(c, '#ff8fa3'); rrect(c, L * 0.1, -h, L * 0.06, h * 2, 0); paint(c, '#c9ccd4');
    c.fillStyle = cols[KI[kind]]; c.fillRect(L * 0.16, -h, L * 0.64, h * 2); line(c, L * 0.16, -h * 0.33, L * 0.8, -h * 0.33, 'rgba(0,0,0,0.15)', 1); line(c, L * 0.16, h * 0.33, L * 0.8, h * 0.33, 'rgba(0,0,0,0.15)', 1);
    c.beginPath(); c.moveTo(L * 0.8, -h); c.lineTo(L, 0); c.lineTo(L * 0.8, h); c.closePath(); paint(c, '#f2d3a0'); c.beginPath(); c.moveTo(L * 0.94, -h * 0.3); c.lineTo(L, 0); c.lineTo(L * 0.94, h * 0.3); c.closePath(); paint(c, '#3a3a40');
  },
  domino: () => (c, kind, L, w, s) => {
    rrect(c, 0, -w * 0.95, L, w * 1.9, w * 0.3); paint(c, '#fbfaf4', '#1b1b1f', s * 0.03);
    line(c, L / 2, -w * 0.8, L / 2, w * 0.8, '#1b1b1f', s * 0.03);
    const R = rng(L * 10 | 0); for (const half of [0, 1]) { const n = 1 + Math.floor(R() * 6); for (let k = 0; k < n; k++) dot(c, L * (0.1 + half * 0.5) + (k % 3) * L * 0.13, -w * 0.45 + Math.floor(k / 3) * w * 0.9, s * 0.05, '#1b1b1f'); }
  },
  brick: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]]; rrect(c, 0, -w * 1.0, L, w * 2, s * 0.05); paint(c, col, 'rgba(0,0,0,0.35)', s * 0.025);
    const n = Math.round(L / (s * 0.5)); for (let i = 0; i < n; i++) for (const y of [-0.5, 0.5]) { dot(c, (i + 0.5) * L / n, y * w, s * 0.11, col); ring(c, (i + 0.5) * L / n, y * w, s * 0.11, 'rgba(0,0,0,0.3)', s * 0.02); dot(c, (i + 0.5) * L / n - s * 0.03, y * w - s * 0.03, s * 0.03, 'rgba(255,255,255,0.5)'); }
  },
  train: (cols) => (c, kind, L, w, s) => {
    const n = kids(L, s), step = L / n, col = cols[KI[kind]];
    for (let i = 0; i < n; i++) {
      const x0 = i * step + s * 0.04, len = step - s * 0.1, loco = i === n - 1;
      if (i > 0) line(c, x0 - s * 0.08, 0, x0, 0, '#3a3a40', s * 0.05);
      rrect(c, x0, -w * 0.9, len, w * 1.8, s * 0.06); paint(c, loco ? '#e5302a' : col, 'rgba(0,0,0,0.4)', s * 0.02);
      if (loco) { dot(c, x0 + len * 0.6, 0, w * 0.35, '#2b2b30'); dot(c, x0 + len * 0.6, 0, w * 0.18, '#5a5a62'); rrect(c, x0 + len * 0.05, -w * 0.7, len * 0.3, w * 1.4, 3); paint(c, '#2b6fb3'); }
      else { rrect(c, x0 + len * 0.15, -w * 0.55, len * 0.7, w * 1.1, 3); paint(c, 'rgba(255,255,255,0.35)'); }
    }
  },
  vehicle: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]], ww = w * 1.05;
    for (const fx of [0.14, 0.86]) for (const sd of [-1, 1]) { rrect(c, L * fx - s * 0.1, sd * ww * 0.95 - s * 0.05, s * 0.2, s * 0.1, 2); paint(c, '#1b1b1f'); }
    rrect(c, 0, -ww * 0.9, L, ww * 1.8, s * 0.12); paint(c, col, 'rgba(0,0,0,0.4)', s * 0.025);
    rrect(c, L * 0.72, -ww * 0.7, L * 0.14, ww * 1.4, s * 0.06); paint(c, '#bfe3ff', 'rgba(0,0,0,0.3)', 1);
    if (L > s * 2.5) for (let x = L * 0.12; x < L * 0.66; x += s * 0.42) { rrect(c, x, -ww * 0.85, s * 0.3, ww * 0.25, 2); paint(c, '#bfe3ff'); rrect(c, x, ww * 0.6, s * 0.3, ww * 0.25, 2); paint(c, '#bfe3ff'); }
    else { rrect(c, L * 0.2, -ww * 0.6, L * 0.4, ww * 1.2, s * 0.06); paint(c, 'rgba(0,0,0,0.12)'); }
    for (const sd of [-1, 1]) dot(c, L - s * 0.03, sd * ww * 0.6, s * 0.06, '#fff6a0');
  },
  plane: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    c.beginPath(); c.moveTo(L * 0.55, -w * 0.3); c.lineTo(L * 0.4, -w * 2.4); c.lineTo(L * 0.3, -w * 2.4); c.lineTo(L * 0.32, -w * 0.3); c.lineTo(L * 0.32, w * 0.3); c.lineTo(L * 0.3, w * 2.4); c.lineTo(L * 0.4, w * 2.4); c.lineTo(L * 0.55, w * 0.3); c.closePath(); paint(c, col, 'rgba(0,0,0,0.3)', 1);
    c.beginPath(); c.moveTo(L * 0.1, -w * 0.2); c.lineTo(L * 0.02, -w * 1.0); c.lineTo(L * 0.08, -w * 1.0); c.lineTo(L * 0.18, -w * 0.2); c.lineTo(L * 0.18, w * 0.2); c.lineTo(L * 0.08, w * 1.0); c.lineTo(L * 0.02, w * 1.0); c.lineTo(L * 0.1, w * 0.2); c.closePath(); paint(c, col, 'rgba(0,0,0,0.3)', 1);
    capsule(c, 0, 0, L, w * 0.42); paint(c, '#f4f6fb', 'rgba(0,0,0,0.3)', 1); ellipse(c, L * 0.88, 0, w * 0.3, w * 0.22); paint(c, '#5a7fb3');
  },
  blimp: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    for (const sd of [-1, 1]) { c.beginPath(); c.moveTo(L * 0.18, sd * w * 0.5); c.lineTo(0, sd * w * 1.2); c.lineTo(L * 0.05, sd * w * 0.3); c.closePath(); paint(c, '#e8e1d0', 'rgba(0,0,0,0.3)', 1); }
    ellipse(c, L / 2, 0, L / 2, w * 0.9); paint(c, col, 'rgba(0,0,0,0.3)', s * 0.02);
    ellipse(c, L / 2, -w * 0.3, L * 0.4, w * 0.25); paint(c, 'rgba(255,255,255,0.25)');
    rrect(c, L * 0.45, -w * 0.18, L * 0.15, w * 0.36, 3); paint(c, '#3a3a40');
  },
  rocket: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    const g = c.createRadialGradient(0, 0, 0, 0, 0, w * 1.4); g.addColorStop(0, 'rgba(255,200,80,0.95)'); g.addColorStop(1, 'rgba(255,90,30,0)'); c.fillStyle = g; c.fillRect(-w * 1.4, -w * 1.4, w * 2.8, w * 2.8);
    for (const sd of [-1, 1]) { c.beginPath(); c.moveTo(L * 0.25, sd * w * 0.55); c.lineTo(L * 0.02, sd * w * 1.3); c.lineTo(L * 0.05, sd * w * 0.4); c.closePath(); paint(c, col); }
    c.beginPath(); c.moveTo(L * 0.05, -w * 0.6); c.lineTo(L * 0.72, -w * 0.6); c.quadraticCurveTo(L * 0.95, -w * 0.4, L, 0); c.quadraticCurveTo(L * 0.95, w * 0.4, L * 0.72, w * 0.6); c.lineTo(L * 0.05, w * 0.6); c.closePath(); paint(c, '#f4f6fb', 'rgba(0,0,0,0.35)', s * 0.02);
    c.beginPath(); c.moveTo(L * 0.72, -w * 0.6); c.quadraticCurveTo(L * 0.95, -w * 0.4, L, 0); c.quadraticCurveTo(L * 0.95, w * 0.4, L * 0.72, w * 0.6); c.closePath(); paint(c, col);
    dot(c, L * 0.5, 0, w * 0.25, '#5ad1ff'); ring(c, L * 0.5, 0, w * 0.25, '#2a3045', 1.5);
  },
  scull: (cols) => (c, kind, L, w, s) => {
    const n = kids(L, s);
    for (let i = 0; i < n; i++) { const x = L * (0.2 + 0.6 * (i + 0.5) / n); line(c, x, -w * 2.2, x + s * 0.08, w * 2.2, '#c9a46a', s * 0.03); for (const sd of [-1, 1]) { ellipse(c, x - s * 0.02, sd * w * 2.1, s * 0.08, s * 0.04); paint(c, cols[KI[kind]]); } }
    c.beginPath(); c.moveTo(0, 0); c.quadraticCurveTo(L * 0.5, -w * 0.8, L, 0); c.quadraticCurveTo(L * 0.5, w * 0.8, 0, 0); paint(c, '#fbfaf4', 'rgba(0,0,0,0.3)', 1);
    for (let i = 0; i < n; i++) dot(c, L * (0.2 + 0.6 * (i + 0.5) / n), 0, w * 0.22, cols[KI[kind]]);
  },
  sub: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    ellipse(c, L / 2, 0, L / 2, w * 0.7); paint(c, col, 'rgba(0,0,0,0.35)', s * 0.025);
    rrect(c, L * 0.5, -w * 0.3, L * 0.18, w * 0.6, w * 0.25); paint(c, col, 'rgba(0,0,0,0.35)', 1);
    for (const f of [0.25, 0.8]) { dot(c, L * f, 0, w * 0.16, '#bfe3ff'); ring(c, L * f, 0, w * 0.16, 'rgba(0,0,0,0.4)', 1.5); }
    line(c, L * 0.02, -w * 0.5, L * 0.02, w * 0.5, 'rgba(0,0,0,0.5)', s * 0.04);
  },
  log: () => (c, kind, L, w, s) => {
    capsule(c, 0, 0, L, w * 0.75); paint(c, '#8a5a33', '#4a2a14', s * 0.02);
    for (let x = L * 0.1; x < L * 0.9; x += s * 0.3) line(c, x, -w * 0.5, x + s * 0.2, -w * 0.45, '#5a3a1e', 1.2);
    ellipse(c, L - w * 0.3, 0, w * 0.3, w * 0.7); paint(c, '#d9b07a', '#4a2a14', 1); ring(c, L - w * 0.3, 0, w * 0.3, '#b08050', 1);
  },
  icebreaker: () => (c, kind, L, w, s) => {
    hull(c, L, w, w * 1.6, w * 0.4); paint(c, '#d6303a', '#5a1414', s * 0.025);
    rrect(c, L * 0.25, -w * 0.6, L * 0.3, w * 1.2, w * 0.2); paint(c, '#ffffff', '#5a1414', 1);
    dot(c, L * 0.42, 0, w * 0.22, '#f2c14e'); line(c, L * 0.6, 0, L * 0.9, 0, 'rgba(255,255,255,0.5)', s * 0.03);
  },
  rockRaft: () => (c, kind, L, w, s) => {
    const R = rng(L | 0); c.beginPath(); for (let k = 0; k <= 12; k++) { const t = k / 12, x = t * L, y = -w * (0.7 + R() * 0.3); k ? c.lineTo(x, y) : c.moveTo(x, y); } for (let k = 12; k >= 0; k--) { const t = k / 12; c.lineTo(t * L, w * (0.7 + R() * 0.3)); } c.closePath(); paint(c, '#3a3436', '#1b1617', s * 0.02);
    for (let k = 0; k < 4; k++) line(c, L * (0.15 + k * 0.2), -w * 0.4, L * (0.25 + k * 0.2), w * 0.3, '#ff7a1f', s * 0.025);
  },
  sandYacht: (cols) => (c, kind, L, w, s) => {
    line(c, 0, 0, L, 0, '#5a4a3a', s * 0.05); line(c, L * 0.2, -w * 1.2, L * 0.2, w * 1.2, '#5a4a3a', s * 0.04);
    for (const sd of [-1, 1]) dot(c, L * 0.2, sd * w * 1.2, s * 0.08, '#1b1b1f'); dot(c, L * 0.95, 0, s * 0.08, '#1b1b1f');
    c.beginPath(); c.moveTo(L * 0.35, 0); c.lineTo(L * 0.9, -w * 0.2); c.lineTo(L * 0.4, -w * 1.4); c.closePath(); paint(c, cols[KI[kind]], 'rgba(0,0,0,0.3)', 1);
  },
  leafBoat: (cols) => (c, kind, L, w, s) => {
    c.beginPath(); c.moveTo(0, 0); c.quadraticCurveTo(L * 0.5, -w * 1.6, L, 0); c.quadraticCurveTo(L * 0.5, w * 1.6, 0, 0); paint(c, cols[KI[kind]], 'rgba(0,0,0,0.3)', 1);
    line(c, -s * 0.1, 0, L * 0.95, 0, 'rgba(80,40,10,0.55)', s * 0.025);
    for (let x = L * 0.2; x < L * 0.85; x += s * 0.3) { line(c, x, 0, x + s * 0.2, -w * 0.6, 'rgba(80,40,10,0.35)', 1); line(c, x, 0, x + s * 0.2, w * 0.6, 'rgba(80,40,10,0.35)', 1); }
  },
  surfboard: (cols) => (c, kind, L, w, s) => {
    ellipse(c, L / 2, 0, L / 2, w * 0.75); paint(c, cols[KI[kind]], 'rgba(0,0,0,0.3)', s * 0.02);
    line(c, L * 0.05, 0, L * 0.95, 0, '#ffffff', s * 0.05); ellipse(c, L * 0.35, w * 0.3, L * 0.08, w * 0.15); paint(c, '#ffffff');
  },
  shark: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    c.beginPath(); c.moveTo(L * 0.12, 0); c.lineTo(0, -w * 0.9); c.lineTo(L * 0.05, 0); c.lineTo(0, w * 0.9); c.closePath(); paint(c, col);
    for (const sd of [-1, 1]) { c.beginPath(); c.moveTo(L * 0.6, sd * w * 0.4); c.lineTo(L * 0.45, sd * w * 1.3); c.lineTo(L * 0.5, sd * w * 0.4); c.closePath(); paint(c, col); }
    c.beginPath(); c.moveTo(L * 0.1, 0); c.quadraticCurveTo(L * 0.5, -w * 0.9, L, 0); c.quadraticCurveTo(L * 0.5, w * 0.9, L * 0.1, 0); paint(c, col, 'rgba(0,0,0,0.3)', 1);
    line(c, L * 0.2, 0, L * 0.85, 0, 'rgba(255,255,255,0.25)', s * 0.05); dot(c, L * 0.88, -w * 0.25, s * 0.03, '#1b1b1f'); dot(c, L * 0.88, w * 0.25, s * 0.03, '#1b1b1f');
  },
  caterpillar: (cols) => (c, kind, L, w, s) => {
    const n = Math.max(4, Math.round(L / (s * 0.35))), col = cols[KI[kind]];
    for (let i = 0; i < n; i++) { const x = (i + 0.5) * L / n, y = Math.sin(i * 1.2) * w * 0.2; dot(c, x, y, w * 0.6, i % 2 ? col : '#ffd23f'); ring(c, x, y, w * 0.6, 'rgba(0,0,0,0.25)', 1); }
    const hx = L - L / n / 2, hy = Math.sin((n - 1) * 1.2) * w * 0.2; dot(c, hx, hy, w * 0.72, col); for (const sd of [-1, 1]) { line(c, hx + w * 0.3, hy + sd * w * 0.3, hx + w * 0.9, hy + sd * w * 0.8, '#1b1b1f', 1.5); dot(c, hx + w * 0.3, hy + sd * w * 0.3, w * 0.1, '#1b1b1f'); }
  },
  snake: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    c.beginPath(); for (let x = 0; x <= L * 0.9; x += s * 0.05) c.lineTo(x, Math.sin(x / s * 2.6) * w * 0.55); c.strokeStyle = col; c.lineWidth = w * 0.85; c.lineCap = 'round'; c.stroke();
    c.save(); c.setLineDash([s * 0.08, s * 0.12]); c.strokeStyle = 'rgba(0,0,0,0.35)'; c.lineWidth = w * 0.4; c.stroke(); c.restore();
    const hy = Math.sin(L * 0.9 / s * 2.6) * w * 0.55; ellipse(c, L * 0.93, hy, w * 0.6, w * 0.48); paint(c, col, 'rgba(0,0,0,0.3)', 1);
    for (const sd of [-1, 1]) dot(c, L * 0.97, hy + sd * w * 0.22, w * 0.08, '#1b1b1f'); line(c, L * 1.0, hy, L * 1.0 + s * 0.12, hy, '#e5302a', 1.5);
  },
  sausageDog: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    for (const fx of [0.15, 0.82]) for (const sd of [-1, 1]) { ellipse(c, L * fx, sd * w * 0.75, s * 0.08, s * 0.05); paint(c, col); }
    line(c, L * 0.05, 0, -s * 0.15, -w * 0.4, col, s * 0.05);
    capsule(c, L * 0.03, 0, L * 0.84, w * 0.62); paint(c, col, 'rgba(0,0,0,0.35)', s * 0.02);
    ellipse(c, L * 0.92, 0, w * 0.55, w * 0.45); paint(c, col, 'rgba(0,0,0,0.35)', 1);
    for (const sd of [-1, 1]) { ellipse(c, L * 0.88, sd * w * 0.48, w * 0.3, w * 0.16, sd * 0.4); paint(c, '#4a2a14'); }
    dot(c, L * 1.0, 0, w * 0.12, '#1b1b1f');
  },
  whale: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    c.beginPath(); c.moveTo(L * 0.12, 0); c.lineTo(0, -w * 1.0); c.quadraticCurveTo(L * 0.06, 0, 0, w * 1.0); c.closePath(); paint(c, col);
    ellipse(c, L * 0.55, 0, L * 0.45, w * 0.85); paint(c, col, 'rgba(0,0,0,0.3)', s * 0.02);
    for (let k = 0; k < 3; k++) ring(c, L * 0.8, 0, w * (0.2 + k * 0.15), 'rgba(255,255,255,0.5)', 1);
  },
  gift: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]];
    rrect(c, 0, -w * 0.95, L, w * 1.9, s * 0.05); paint(c, col, 'rgba(0,0,0,0.3)', s * 0.02);
    line(c, 0, 0, L, 0, '#ffd23f', s * 0.08, 'butt'); line(c, L * 0.5, -w * 0.95, L * 0.5, w * 0.95, '#ffd23f', s * 0.08, 'butt');
    for (const sd of [-1, 1]) { ellipse(c, L * 0.5 + sd * s * 0.12, 0, s * 0.14, s * 0.08, sd * 0.5); paint(c, '#ffd23f', '#c99a10', 1); }
  },
  tetris: (cols) => (c, kind, L, w, s) => {
    const n = kids(L, s), col = cols[KI[kind]], cell = (L) / n;
    for (let i = 0; i < n; i++) { const x = i * cell + 1; c.fillStyle = col; c.fillRect(x, -cell / 2 + 1, cell - 2, cell - 2); c.fillStyle = 'rgba(255,255,255,0.4)'; c.fillRect(x, -cell / 2 + 1, cell - 2, 3); c.fillRect(x, -cell / 2 + 1, 3, cell - 2); c.fillStyle = 'rgba(0,0,0,0.3)'; c.fillRect(x, cell / 2 - 4, cell - 2, 3); }
  },
  pixelShip: (cols) => (c, kind, L, w, s) => {
    const p = s / 8, col = cols[KI[kind]];
    for (let x = 0; x < L; x += p) { const t = x / L, half = t > 0.8 ? (1 - t) / 0.2 * 2 : 2; for (let y = -half; y < half; y++) { c.fillStyle = Math.abs(y + 0.5) > half - 1 ? '#1b1b1f' : col; c.fillRect(x, y * p, p + 0.3, p + 0.3); } }
    for (let x = L * 0.3; x < L * 0.6; x += p) { c.fillStyle = '#ffffff'; c.fillRect(x, -p, p + 0.3, p * 2); }
  },
  chalkShip: () => (c, kind, L, w, s) => {
    for (let k = 0; k < 2; k++) { c.save(); c.translate(k * 0.8, k * -0.6); hull(c, L, w, w * 1.5, w * 0.4); paint(c, null, `rgba(255,255,255,${0.85 - k * 0.4})`, s * 0.03); c.restore(); }
    line(c, L * 0.1, 0, L * 0.85, 0, 'rgba(255,255,255,0.5)', s * 0.015); for (const f of [0.3, 0.6]) ring(c, L * f, 0, w * 0.3, 'rgba(255,255,255,0.75)', s * 0.02);
  },
  blueprintShip: () => (c, kind, L, w, s) => {
    hull(c, L, w, w * 1.6, w * 0.4); paint(c, 'rgba(255,255,255,0.08)', '#ffffff', s * 0.02);
    line(c, L * 0.05, 0, L * 0.95, 0, 'rgba(255,255,255,0.6)', 1); c.setLineDash([3, 3]); ring(c, L * 0.3, 0, w * 0.35, '#ffffff', 1); rrect(c, L * 0.45, -w * 0.5, L * 0.2, w, 2); paint(c, null, '#ffffff', 1); c.setLineDash([]);
    line(c, 0, w * 1.4, L, w * 1.4, 'rgba(255,255,255,0.7)', 0.8); line(c, 0, w * 1.2, 0, w * 1.6, '#ffffff', 0.8); line(c, L, w * 1.2, L, w * 1.6, '#ffffff', 0.8);
  },
  glassShip: (cols) => (c, kind, L, w, s) => {
    hull(c, L, w, w * 1.6, w * 0.4); clipped(c, () => { const R = rng(L | 0); for (let x = 0; x < L; x += s * 0.35) for (const y of [-1, 0]) { c.fillStyle = ['#e5302a', '#2f6fb3', '#f2c14e', '#3f9a3a', '#9b59b6'][Math.floor(R() * 5)]; c.globalAlpha = 0.85; c.fillRect(x, y * w, s * 0.35, w); } c.globalAlpha = 1; for (let x = 0; x < L; x += s * 0.35) line(c, x, -w, x + s * 0.1, w, '#1b1b1f', s * 0.03); line(c, 0, 0, L, 0, '#1b1b1f', s * 0.03); });
    hull(c, L, w, w * 1.6, w * 0.4); paint(c, null, '#1b1b1f', s * 0.045);
  },
  watercolorShip: (cols) => (c, kind, L, w, s) => {
    const col = cols[KI[kind]]; c.save(); c.globalAlpha = 0.35; const R = rng(L | 0);
    for (let k = 0; k < 7; k++) { ellipse(c, L * (0.2 + R() * 0.6), (R() - 0.5) * w, L * (0.3 + R() * 0.2), w * (0.6 + R() * 0.4)); paint(c, col); }
    c.restore(); hull(c, L, w, w * 1.6, w * 0.4); paint(c, null, 'rgba(60,60,80,0.35)', 1);
  },
  comicShip: (cols) => (c, kind, L, w, s) => {
    for (let k = 0; k < 3; k++) line(c, -s * (0.15 + k * 0.08), (k - 1) * w * 0.5, -s * 0.5 - k * s * 0.1, (k - 1) * w * 0.5, '#1b1b1f', s * 0.025);
    hull(c, L, w, w * 1.6, w * 0.4); paint(c, cols[KI[kind]]);
    hull(c, L, w, w * 1.6, w * 0.4); clipped(c, () => { for (let x = 0; x < L; x += s * 0.12) for (let y = -w; y < w; y += s * 0.12) dot(c, x, y, s * 0.025, 'rgba(0,0,0,0.25)'); });
    hull(c, L, w, w * 1.6, w * 0.4); paint(c, null, '#1b1b1f', s * 0.06); rrect(c, L * 0.35, -w * 0.5, L * 0.22, w, 3); paint(c, '#ffffff', '#1b1b1f', s * 0.03);
  },
  brassShip: () => (c, kind, L, w, s) => {
    hull(c, L, w, w * 1.6, w * 0.4); const g = c.createLinearGradient(0, -w, 0, w); g.addColorStop(0, '#e8c070'); g.addColorStop(1, '#9a6a24'); paint(c, g, '#5a3a10', s * 0.025);
    for (let x = L * 0.12; x < L * 0.9; x += s * 0.2) { dot(c, x, -w * 0.75, s * 0.025, '#5a3a10'); dot(c, x, w * 0.75, s * 0.025, '#5a3a10'); }
    const gear = (gx, gr) => { for (let k = 0; k < 8; k++) { const a = k * TAU / 8; rrect(c, gx + Math.cos(a) * gr - s * 0.03, Math.sin(a) * gr - s * 0.03, s * 0.06, s * 0.06, 1); paint(c, '#7a5a2a'); } dot(c, gx, 0, gr, '#b8873a'); ring(c, gx, 0, gr, '#5a3a10', 1); dot(c, gx, 0, gr * 0.35, '#5a3a10'); };
    gear(L * 0.3, w * 0.45); gear(L * 0.6, w * 0.35); line(c, L * 0.4, 0, L * 0.52, 0, '#5a3a10', s * 0.05);
  },
  chip: () => (c, kind, L, w, s) => {
    for (let x = L * 0.1; x < L * 0.92; x += s * 0.16) { line(c, x, -w * 0.8, x, -w * 1.15, '#c9ccd4', s * 0.04, 'butt'); line(c, x, w * 0.8, x, w * 1.15, '#c9ccd4', s * 0.04, 'butt'); }
    rrect(c, 0, -w * 0.85, L, w * 1.7, s * 0.04); paint(c, '#1f2228', '#000000', 1); dot(c, L * 0.08, -w * 0.5, s * 0.04, '#555a66');
    c.fillStyle = 'rgba(255,255,255,0.65)'; c.font = `700 ${s * 0.16}px monospace`; c.textBaseline = 'middle'; c.fillText('PP-' + (L / s * 100 | 0), L * 0.2, 0);
  },
};

// ---- Hit and miss marks --------------------------------------------------------------------------
const HIT = {
  burst: (fill, core = '#ffffff', stroke = INK) => (c, x, y, s) => { burst(c, x, y, s * 0.38, s * 0.17, 8, fill, stroke, s * 0.035); dot(c, x, y, s * 0.11, core); },
  splat: (col) => (c, x, y, s) => { const R = seed(x, y); dot(c, x, y, s * 0.24, col); for (let k = 0; k < 7; k++) { const a = R() * TAU, d = s * (0.22 + R() * 0.15); dot(c, x + Math.cos(a) * d, y + Math.sin(a) * d, s * (0.05 + R() * 0.07), col); } },
  x: (col) => (c, x, y, s) => { line(c, x - s * 0.28, y - s * 0.28, x + s * 0.28, y + s * 0.28, col, s * 0.09); line(c, x + s * 0.28, y - s * 0.28, x - s * 0.28, y + s * 0.28, col, s * 0.09); },
  flame: () => (c, x, y, s) => { const g = c.createRadialGradient(x, y, 0, x, y, s * 0.45); g.addColorStop(0, 'rgba(255,214,120,0.95)'); g.addColorStop(0.45, 'rgba(255,120,40,0.8)'); g.addColorStop(1, 'rgba(255,90,30,0)'); c.fillStyle = g; c.fillRect(x - s / 2, y - s / 2, s, s); dot(c, x + s * 0.12, y - s * 0.14, s * 0.11, 'rgba(40,40,45,0.55)'); },
  heart: (col) => (c, x, y, s) => { heartPath(c, x, y, s * 0.3); paint(c, col, '#ffffff', s * 0.04); },
  star: (col) => (c, x, y, s) => { star5(c, x, y, s * 0.36); paint(c, col, INK, s * 0.035); },
  pow: (col) => (c, x, y, s) => { burst(c, x, y, s * 0.46, s * 0.26, 10, col, '#1b1b1f', s * 0.035); c.fillStyle = '#1b1b1f'; c.font = `900 ${s * 0.22}px sans-serif`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('POW', x, y + 1); },
  pixel: (col) => (c, x, y, s) => { const p = s / 8; for (const [dx, dy] of [[0, 0], [-1, 0], [1, 0], [0, -1], [0, 1], [-2, -2], [2, -2], [-2, 2], [2, 2]]) { c.fillStyle = Math.abs(dx) + Math.abs(dy) > 2 ? '#ffd23f' : col; c.fillRect(x + dx * p - p / 2, y + dy * p - p / 2, p, p); } },
  spark: (col) => (c, x, y, s) => { c.save(); c.shadowColor = col; c.shadowBlur = s * 0.3; for (let k = 0; k < 8; k++) { const a = k * TAU / 8; line(c, x + Math.cos(a) * s * 0.1, y + Math.sin(a) * s * 0.1, x + Math.cos(a) * s * 0.36, y + Math.sin(a) * s * 0.36, col, s * 0.05); } dot(c, x, y, s * 0.1, '#ffffff'); c.restore(); },
  crack: (col) => (c, x, y, s) => { for (let k = 0; k < 6; k++) { const a = k * TAU / 6 + 0.3; c.beginPath(); c.moveTo(x, y); c.lineTo(x + Math.cos(a + 0.2) * s * 0.18, y + Math.sin(a + 0.2) * s * 0.18); c.lineTo(x + Math.cos(a) * s * 0.36, y + Math.sin(a) * s * 0.36); c.strokeStyle = col; c.lineWidth = s * 0.04; c.stroke(); } dot(c, x, y, s * 0.07, col); },
  puff: (col) => (c, x, y, s) => { for (const [dx, dy, r] of [[-0.15, 0.05, 0.17], [0.12, -0.08, 0.2], [0.15, 0.14, 0.15], [-0.08, -0.16, 0.13]]) dot(c, x + dx * s, y + dy * s, r * s, col); },
  item: (fn, col) => (c, x, y, s) => fn(c, x, y, s * 0.36, col, 0, 1, 'battleship', s),
};
const MISS = {
  ring: (col) => (c, x, y, s) => { ring(c, x, y, s * 0.16, col, s * 0.035); dot(c, x, y, s * 0.05, col); },
  rings: (col) => (c, x, y, s) => { ring(c, x, y, s * 0.1, col, s * 0.03); ring(c, x, y, s * 0.22, col, s * 0.025); },
  dot: (col) => (c, x, y, s) => dot(c, x, y, s * 0.09, col),
  bubbles: (col) => (c, x, y, s) => { for (const [dx, dy, r] of [[-0.12, 0.06, 0.12], [0.12, -0.02, 0.09], [0.02, -0.17, 0.06]]) ring(c, x + dx * s, y + dy * s, r * s, col, s * 0.025); },
  sprinkles: (cols) => (c, x, y, s) => { for (let k = 0; k < 5; k++) { const a = k * TAU / 5 + 0.3; line(c, x + Math.cos(a) * s * 0.06, y + Math.sin(a) * s * 0.06, x + Math.cos(a) * s * 0.2, y + Math.sin(a) * s * 0.2, cols[k % cols.length], s * 0.05); } },
  flake: (col) => (c, x, y, s) => { for (let j = 0; j < 3; j++) { const a = j * Math.PI / 3; line(c, x - Math.cos(a) * s * 0.18, y - Math.sin(a) * s * 0.18, x + Math.cos(a) * s * 0.18, y + Math.sin(a) * s * 0.18, col, s * 0.035); } },
  leaf: (col) => (c, x, y, s) => { c.beginPath(); c.moveTo(x - s * 0.18, y + s * 0.1); c.quadraticCurveTo(x, y - s * 0.3, x + s * 0.18, y - s * 0.1); c.quadraticCurveTo(x, y + s * 0.2, x - s * 0.18, y + s * 0.1); paint(c, col); },
  cube: (col) => (c, x, y, s) => { rrect(c, x - s * 0.13, y - s * 0.13, s * 0.26, s * 0.26, s * 0.06); paint(c, col, 'rgba(0,0,0,0.2)', 1); },
  item: (fn, col) => (c, x, y, s) => fn(c, x, y, s * 0.2, col, 0, 1, 'battleship', s),
  pixel: (col) => (c, x, y, s) => { const p = s / 8; c.fillStyle = col; c.fillRect(x - p / 2, y - p / 2, p, p); c.fillRect(x - p * 2, y - p / 2, p, p); c.fillRect(x + p, y - p / 2, p, p); },
  puff: (col) => (c, x, y, s) => { for (const [dx, dy, r] of [[-0.08, 0.03, 0.1], [0.07, -0.05, 0.12], [0.08, 0.08, 0.08]]) dot(c, x + dx * s, y + dy * s, r * s, col); },
};
