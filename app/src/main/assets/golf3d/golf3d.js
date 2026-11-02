// Mini Golf in 3D: draws the hole, the balls and the shots the app sends, and takes your touches.
//
// The app (ui/screens/Golf3D.kt) talks to this page through window.golf:
//   golf.state({ key, num, hole, balls: [{ id, x, y, color }], turn, me, meDone, canShoot, noGuide })
//     what's on the course now; while a shot is playing it waits for the shot to finish.
//   golf.play({ key, hole, id, color, frames: [[x, y, h], ...], events: [[frame, what], ...] })
//     a shot to show, 60 frames a second, worked out by the app.
// and this page answers through window.Android:
//   ready()                      the page is loaded (or "error ..." through event if it couldn't)
//   shoot(kind, power, aim)      you let go of the slider: 'roll' or 'jump', 0..1, radians
//   event(what)                  a shot reached an event (for sounds), and "played" at its end
//
// Course units: the course is 1 wide, y down the hole; S world units to one course unit. Every
// texture is drawn here on a canvas, so there's nothing to download. The rocks, palms, plants and
// posts come with the hole (Golf.kt's Props), the same on every phone, as the ball bounces off them
// in the app's own physics.
//
// Each phone keeps its own camera, behind its own ball: another player's shot is watched from
// there (swipe to look round for it). Only once you've finished the hole does the camera follow
// whoever is playing.

// The app's bridge. Not called Android here: the WebView puts window.Android in place itself, and
// a const of the same name would stop this whole script ("already been declared").
const bridge = window.Android || { ready() {}, shoot() {}, event() {} };
// Errors are told to the app by the first script in index.html.
const S = 10;
const CORNER = 0.04, CUP = 0.04, BR = 0.022;
const WATER_Y = -1.7;
const ROLL_SPEED = 2.4, ROLL_DECEL = 2.2;
const JUMP_SPEED = 0.6, JUMP_UP = 7, GRAVITY = 14;
const BALL_R = 0.2;
const JUMP_FROM = 0.55;   // the slider: below this a roll, above it a jump
const LOST_MS = 1100;
const KY_SONG = 11.05;    // how long Kylian's song plays (the app plays it)
const TAU = Math.PI * 2;
const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

let HALF = 1.6;           // half the hole's length: the course is centred on the world's origin
let HOLE = null;          // the hole on screen
const wx = (x) => (x - 0.5) * S;
const wz = (y) => (y - HALF) * S;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
/** A small seeded random generator, so everything sits in the same place every time. */
function rng(seed) {
  return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
// The world's clock (seconds) and what's going on in it.
const W = { t: 0, wobble: new Map(), sideEyeAt: -9, spotAt: -99, popAt: -9, shakeAt: -9, streakUntil: 0, quiet: false };
const isNew = () => true;

// ---- Scene ------------------------------------------------------------------------------------
const canvas = document.getElementById('view');
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
} catch (e) {
  document.body.insertAdjacentHTML('beforeend', '<div class="nogl">This phone can\'t show the 3D course.</div>');
  bridge.event('error no WebGL: ' + e.message);
  throw e;
}
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const scene = new THREE.Scene();
scene.fog = new THREE.Fog('#dff1fd', 70, 170);
const camera = new THREE.PerspectiveCamera(52, 9 / 16, 0.1, 400);
scene.add(new THREE.HemisphereLight('#e8f6ff', '#3f6b3a', 0.9));
const sun = new THREE.DirectionalLight('#fff4dc', 0.95);
sun.position.set(7, 16, 5);
sun.castShadow = true;
sun.shadow.mapSize.set(1024, 2048);
Object.assign(sun.shadow.camera, { left: -12, right: 12, top: 18, bottom: -18, near: 1, far: 60 });
scene.add(sun);

function canvasTex(w, h, draw, repeat) {
  const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
  draw(cv.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(cv); t.anisotropy = 4;
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); }
  return t;
}
const TEX = {};
const kyImg = new Image();
kyImg.src = KYLIAN_SVG;


// ---- Textures, all drawn here ------------------------------------------------------------------
function grassDraw(g, w, h, bump) {
  const r = rng(7);
  for (let i = 0; i < 2; i++) { g.fillStyle = bump ? (i ? '#8a8a8a' : '#7a7a7a') : (i ? '#62c464' : '#4ea953'); g.fillRect(0, i * h / 2, w, h / 2); }
  for (let k = 0; k < 9000; k++) {
    const x = r() * w, y = r() * h, l = 0.6 + r() * 1.6;
    g.fillStyle = bump ? `rgba(${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},0.18)` : `hsla(${100 + r() * 30},${45 + r() * 25}%,${32 + r() * 22}%,0.55)`;
    g.fillRect(x, y, l, l);
  }
  g.lineCap = 'round';
  for (let k = 0; k < 2600; k++) {
    const x = r() * w, y = r() * h, a = -Math.PI / 2 + (r() - 0.5) * 0.9, l = 2 + r() * 5;
    g.strokeStyle = bump ? `rgba(255,255,255,${0.15 + r() * 0.25})` : `hsla(${95 + r() * 30},55%,${r() > 0.5 ? 58 : 30}%,0.5)`;
    g.lineWidth = 0.6 + r() * 0.8;
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.cos(a) * l, y + Math.sin(a) * l); g.stroke();
  }
}
TEX.grassNew = canvasTex(512, 512, (g, w, h) => grassDraw(g, w, h, false), [0.25, 0.25]);
TEX.grassBump = canvasTex(256, 256, (g, w, h) => grassDraw(g, w, h, true), [0.5, 0.5]);
TEX.rock = canvasTex(256, 256, (g, w, h) => {
  const r = rng(11);
  let y = 0;
  while (y < h) {
    const bh = 10 + r() * 26;
    const l = 26 + r() * 14;
    g.fillStyle = `hsl(${24 + r() * 10},${28 + r() * 12}%,${l}%)`; g.fillRect(0, y, w, bh);
    g.fillStyle = 'rgba(0,0,0,0.18)'; g.fillRect(0, y + bh - 2, w, 2);
    y += bh;
  }
  for (let k = 0; k < 90; k++) {
    g.strokeStyle = 'rgba(30,18,10,0.45)'; g.lineWidth = 1 + r();
    let x = r() * w, yy = r() * h; g.beginPath(); g.moveTo(x, yy);
    for (let s = 0; s < 4; s++) { x += (r() - 0.5) * 14; yy += 4 + r() * 10; g.lineTo(x, yy); }
    g.stroke();
  }
  for (let k = 0; k < 160; k++) { g.fillStyle = `rgba(255,240,220,${0.08 + r() * 0.12})`; g.beginPath(); g.arc(r() * w, r() * h, 1 + r() * 2.5, 0, TAU); g.fill(); }
}, [0.5, 0.9]);
TEX.soil = canvasTex(256, 128, (g, w, h) => {
  const r = rng(12);
  g.fillStyle = '#6e4c2f'; g.fillRect(0, 0, w, h);
  for (let k = 0; k < 1400; k++) { g.fillStyle = `rgba(${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 220 : 0},${r() > 0.5 ? 180 : 0},0.12)`; g.fillRect(r() * w, r() * h, 2, 2); }
  for (let k = 0; k < 50; k++) { g.fillStyle = 'rgba(40,24,12,0.5)'; g.beginPath(); g.arc(r() * w, r() * h, 1 + r() * 3, 0, TAU); g.fill(); }
}, [0.6, 3]);
TEX.sand = canvasTex(256, 256, (g, w, h) => {
  const r = rng(5);
  g.fillStyle = '#efd58c'; g.fillRect(0, 0, w, h);
  for (let k = 0; k < 7000; k++) { g.fillStyle = `rgba(${r() > 0.5 ? 255 : 150},${r() > 0.5 ? 240 : 110},${r() > 0.5 ? 200 : 50},0.25)`; g.fillRect(r() * w, r() * h, 1.5, 1.5); }
  g.strokeStyle = 'rgba(160,120,50,0.35)'; g.lineWidth = 2;
  for (let k = 0; k < 9; k++) { g.beginPath(); g.arc(w * 0.5, h * 1.6, 120 + k * 14, Math.PI * 1.15, Math.PI * 1.85); g.stroke(); }
}, [1.2, 1.2]);
TEX.waves = canvasTex(256, 256, (g, w, h) => {
  const r = rng(21);
  g.fillStyle = '#2f8fd2'; g.fillRect(0, 0, w, h);
  g.lineCap = 'round';
  for (let k = 0; k < 120; k++) {
    const x = r() * w, y = r() * h, l = 8 + r() * 24;
    g.strokeStyle = `rgba(220,245,255,${0.18 + r() * 0.35})`; g.lineWidth = 1.2 + r() * 1.6;
    g.beginPath(); g.moveTo(x, y); g.quadraticCurveTo(x + l / 2, y - 3, x + l, y); g.stroke();
  }
}, [12, 12]);
TEX.dimples = canvasTex(256, 128, (g, w, h) => {
  g.fillStyle = '#ffffff'; g.fillRect(0, 0, w, h);
  for (let row = 0; row < 12; row++) for (let col = 0; col < 26; col++) {
    const x = (col + (row % 2) * 0.5) * (w / 26), y = (row + 0.5) * (h / 12);
    const gr = g.createRadialGradient(x, y, 0, x, y, 4.5); gr.addColorStop(0, '#a6a6a6'); gr.addColorStop(1, '#ffffff');
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, 4.5, 0, TAU); g.fill();
  }
});
TEX.sky = canvasTex(8, 256, (g, w, h) => {
  const gr = g.createLinearGradient(0, 0, 0, h);
  gr.addColorStop(0, '#4e9fe6'); gr.addColorStop(0.42, '#9fd2fa'); gr.addColorStop(0.5, '#e8f6ff'); gr.addColorStop(1, '#e8f6ff');
  g.fillStyle = gr; g.fillRect(0, 0, w, h);
});
TEX.pole = canvasTex(16, 128, (g, w, h) => { for (let i = 0; i < 8; i++) { g.fillStyle = i % 2 ? '#e5484d' : '#ffffff'; g.fillRect(0, i * h / 8, w, h / 8); } });
function beamDraw(g, w, h, base, seed) {
  const r = rng(seed);
  g.fillStyle = base; g.fillRect(0, 0, w, h);
  // Grain runs along the beam, with a knot or two.
  for (let k = 0; k < 26; k++) {
    g.strokeStyle = `rgba(60,32,12,${0.12 + r() * 0.22})`; g.lineWidth = 0.8 + r() * 1.4;
    const y0 = r() * h, amp = 1 + r() * 3, ph = r() * 6;
    g.beginPath(); for (let x = 0; x <= w; x += 6) { const y = y0 + Math.sin(x / 40 + ph) * amp; x ? g.lineTo(x, y) : g.moveTo(x, y); } g.stroke();
  }
  for (let k = 0; k < 3; k++) {
    const x = r() * w, y = r() * h;
    for (let i = 4; i > 0; i--) { g.strokeStyle = `rgba(70,38,14,${0.25 + i * 0.08})`; g.lineWidth = 1.2; g.beginPath(); g.ellipse(x, y, 4 + i * 4, 2 + i * 1.6, 0, 0, TAU); g.stroke(); }
  }
  const sh = g.createLinearGradient(0, 0, 0, h); sh.addColorStop(0, 'rgba(255,240,210,0.18)'); sh.addColorStop(0.5, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(40,20,5,0.25)');
  g.fillStyle = sh; g.fillRect(0, 0, w, h);
}
TEX.beam = canvasTex(512, 128, (g, w, h) => beamDraw(g, w, h, '#b9834a', 41));
TEX.beamDark = canvasTex(512, 128, (g, w, h) => beamDraw(g, w, h, '#8a5a30', 42));
TEX.plankA = canvasTex(256, 64, (g, w, h) => beamDraw(g, w, h, '#c08a50', 43));
TEX.plankB = canvasTex(256, 64, (g, w, h) => beamDraw(g, w, h, '#a8743f', 44));
TEX.plankC = canvasTex(256, 64, (g, w, h) => beamDraw(g, w, h, '#b57d45', 45));
TEX.rubber = canvasTex(256, 32, (g, w, h) => { g.fillStyle = '#2d5a3a'; g.fillRect(0, 0, w, h); const r = rng(9); for (let k = 0; k < 600; k++) { g.fillStyle = `rgba(255,255,255,${r() * 0.08})`; g.fillRect(r() * w, r() * h, 1, 1); } g.fillStyle = 'rgba(255,255,255,0.18)'; g.fillRect(0, 2, w, 3); });
TEX.bark = canvasTex(64, 128, (g, w, h) => {
  g.fillStyle = '#8a6a45'; g.fillRect(0, 0, w, h);
  for (let y = 0; y < h; y += 16) { g.fillStyle = 'rgba(60,40,20,0.55)'; g.fillRect(0, y, w, 3); g.fillStyle = 'rgba(255,230,190,0.15)'; g.fillRect(0, y + 4, w, 2); }
  const r = rng(6); for (let k = 0; k < 200; k++) { g.fillStyle = `rgba(40,25,10,${r() * 0.3})`; g.fillRect(r() * w, r() * h, 2, 3); }
});
TEX.ember = canvasTex(128, 64, (g, w, h) => {
  g.fillStyle = '#2a1a12'; g.fillRect(0, 0, w, h);
  const r = rng(13); g.strokeStyle = '#ff7a1f'; g.lineWidth = 1.5;
  for (let k = 0; k < 18; k++) { let x = r() * w, y = r() * h; g.beginPath(); g.moveTo(x, y); for (let s = 0; s < 4; s++) { x += (r() - 0.5) * 20; y += (r() - 0.5) * 12; g.lineTo(x, y); } g.stroke(); }
});
TEX.swirl = canvasTex(256, 256, (g, w) => {
  const c = w / 2, gr = g.createRadialGradient(c, c, 0, c, c, c);
  gr.addColorStop(0, '#fff6c8'); gr.addColorStop(0.2, '#c79bff'); gr.addColorStop(0.7, '#2a1060'); gr.addColorStop(1, 'rgba(20,8,50,0)');
  g.fillStyle = gr; g.fillRect(0, 0, w, w);
  for (let arm = 0; arm < 4; arm++) {
    g.strokeStyle = arm % 2 ? 'rgba(142,240,255,0.85)' : 'rgba(255,200,255,0.7)'; g.lineWidth = 7; g.lineCap = 'round';
    g.beginPath();
    for (let k = 0; k <= 60; k++) { const t = k / 60, a = arm * TAU / 4 + t * 4.2, rr = 10 + t * (c - 18); const x = c + Math.cos(a) * rr, y = c + Math.sin(a) * rr; k ? g.lineTo(x, y) : g.moveTo(x, y); }
    g.stroke();
  }
});
TEX.iris = canvasTex(512, 256, (g, w, h) => {
  g.fillStyle = '#fbfaf6'; g.fillRect(0, 0, w, h);
  const r = rng(23);
  for (let k = 0; k < 26; k++) { g.strokeStyle = `rgba(200,60,60,${0.15 + r() * 0.25})`; g.lineWidth = 0.8 + r(); let x = w * 0.25 + (r() - 0.5) * w * 0.9, y = r() * h; g.beginPath(); g.moveTo(x, y); for (let s = 0; s < 5; s++) { x += (w * 0.25 - x) * 0.18 + (r() - 0.5) * 14; y += (h / 2 - y) * 0.18 + (r() - 0.5) * 10; g.lineTo(x, y); } g.stroke(); }
  const cx = w * 0.25, cy = h / 2, rx = 30, ry = 30;
  const gr = g.createRadialGradient(cx, cy, 4, cx, cy, rx); gr.addColorStop(0, '#5a3418'); gr.addColorStop(0.55, '#8a5a24'); gr.addColorStop(0.9, '#4a2a10'); gr.addColorStop(1, '#2a160a');
  g.fillStyle = gr; g.beginPath(); g.ellipse(cx, cy, rx, ry, 0, 0, TAU); g.fill();
  for (let k = 0; k < 40; k++) { const a = r() * TAU; g.strokeStyle = 'rgba(220,170,90,0.35)'; g.lineWidth = 1; g.beginPath(); g.moveTo(cx + Math.cos(a) * 12, cy + Math.sin(a) * 12); g.lineTo(cx + Math.cos(a) * 27, cy + Math.sin(a) * 27); g.stroke(); }
  g.fillStyle = '#100804'; g.beginPath(); g.ellipse(cx, cy, 12, 12, 0, 0, TAU); g.fill();
  g.fillStyle = 'rgba(255,255,255,0.9)'; g.beginPath(); g.ellipse(cx - 9, cy - 9, 5, 5, 0, 0, TAU); g.fill();
});
TEX.lily = canvasTex(128, 128, (g, w) => {
  const c = w / 2; g.clearRect(0, 0, w, w);
  g.fillStyle = '#4f9a3a'; g.beginPath(); g.moveTo(c, c); g.arc(c, c, c - 4, 0.25, TAU - 0.25); g.closePath(); g.fill();
  g.strokeStyle = '#8cc95f'; g.lineWidth = 1.5;
  for (let k = 0; k < 11; k++) { const a = 0.4 + k * (TAU - 0.8) / 10; g.beginPath(); g.moveTo(c, c); g.lineTo(c + Math.cos(a) * (c - 8), c + Math.sin(a) * (c - 8)); g.stroke(); }
  g.strokeStyle = '#3a7a2a'; g.lineWidth = 3; g.beginPath(); g.arc(c, c, c - 5, 0.25, TAU - 0.25); g.stroke();
});
TEX.cupFloor = canvasTex(128, 128, (g, w) => { const c = w / 2, gr = g.createRadialGradient(c, c, 0, c, c, c); gr.addColorStop(0, '#050806'); gr.addColorStop(0.7, '#1e2620'); gr.addColorStop(0.95, '#9aa39c'); gr.addColorStop(1, '#ffffff'); g.fillStyle = gr; g.beginPath(); g.arc(c, c, c, 0, TAU); g.fill(); });
TEX.ticker = canvasTex(512, 128, (g, w, h) => {
  g.fillStyle = '#081208'; g.fillRect(0, 0, w, h);
  for (let x = 0; x < w; x += 8) for (let y = 0; y < h; y += 8) { g.fillStyle = 'rgba(40,80,40,0.35)'; g.fillRect(x + 1, y + 1, 5, 5); }
  g.font = "900 64px 'Courier New', monospace"; g.textBaseline = 'middle'; g.fillStyle = '#3ddc84'; g.shadowColor = '#3ddc84'; g.shadowBlur = 14;
  g.fillText('STONKS', 18, h / 2 + 4); g.fillStyle = '#ffd23f'; g.shadowColor = '#ffd23f'; g.fillText('▲420%', 290, h / 2 + 4);
});
// ---- Materials, sky and sea ---------------------------------------------------------------------
const M = {
  grass: new THREE.MeshStandardMaterial({ map: TEX.grassNew, bumpMap: TEX.grassBump, bumpScale: 0.025, roughness: 0.95 }),
  soil: new THREE.MeshLambertMaterial({ map: TEX.soil }),
  rock: new THREE.MeshStandardMaterial({ map: TEX.rock, bumpMap: TEX.rock, bumpScale: 0.06, roughness: 1 }),
  lip: new THREE.MeshLambertMaterial({ color: '#3f9a45' }),
  sand: new THREE.MeshStandardMaterial({ map: TEX.sand, bumpMap: TEX.sand, bumpScale: 0.02, roughness: 1 }),
  pond: new THREE.MeshPhongMaterial({ color: '#1f6fae', shininess: 140, specular: '#ffffff' }),
};
// The sky: a gradient dome with drifting clouds and a few gulls.
const skyDome = new THREE.Mesh(new THREE.SphereGeometry(300, 24, 16), new THREE.MeshBasicMaterial({ map: TEX.sky, side: THREE.BackSide, fog: false }));
scene.add(skyDome);
const clouds = new THREE.Group();
{
  const r = rng(31), mat = new THREE.MeshLambertMaterial({ color: '#ffffff', emissive: '#c9d9e8', emissiveIntensity: 0.35 });
  for (let c = 0; c < 9; c++) {
    const cloud = new THREE.Group();
    for (let k = 0; k < 5; k++) {
      const puff = new THREE.Mesh(new THREE.SphereGeometry(1, 12, 10), mat);
      puff.scale.set(2.2 + r() * 2, 1.2 + r(), 1.8 + r());
      puff.position.set((k - 2) * 2.2 + r(), r() * 0.8, (r() - 0.5) * 1.5);
      cloud.add(puff);
    }
    const a = r() * TAU, d = 45 + r() * 60;
    cloud.position.set(Math.cos(a) * d, 16 + r() * 18, Math.sin(a) * d);
    cloud.userData = { a, d, speed: 0.004 + r() * 0.006 };
    clouds.add(cloud);
  }
  scene.add(clouds);
}
const gulls = new THREE.Group();
{
  const mat = new THREE.MeshBasicMaterial({ color: '#ffffff', side: THREE.DoubleSide });
  for (let i = 0; i < 3; i++) {
    const bird = new THREE.Group();
    for (const s of [-1, 1]) {
      const wing = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.22), mat);
      wing.position.x = s * 0.45; bird.add(wing); bird.userData[s < 0 ? 'l' : 'r'] = wing;
    }
    bird.userData.phase = i * 2.1; bird.userData.rad = 9 + i * 3;
    gulls.add(bird);
  }
  scene.add(gulls);
}
// The sea: one wavy plane with drifting highlights.
const seaGeo = new THREE.PlaneGeometry(220, 220, 60, 60); seaGeo.rotateX(-Math.PI / 2);
const sea = new THREE.Mesh(seaGeo, new THREE.MeshPhongMaterial({ map: TEX.waves, color: '#ffffff', shininess: 110, specular: '#e8f8ff' }));
sea.position.y = WATER_Y; sea.receiveShadow = true; scene.add(sea);
function moveSea(t) {
  const p = seaGeo.attributes.position;
  for (let i = 0; i < p.count; i++) p.setY(i, Math.sin(p.getX(i) * 0.55 + t * 1.1) * 0.07 + Math.cos(p.getZ(i) * 0.45 + t * 1.4) * 0.07);
  p.needsUpdate = true; seaGeo.computeVertexNormals();
  TEX.waves.offset.set(t * 0.004, t * 0.002);
}
// The flag and the tee mat carry the hole's number.
function flagTex(n) {
  return canvasTex(256, 160, (g, w, h) => {
    g.fillStyle = '#e5484d'; g.fillRect(0, 0, w, h);
    g.fillStyle = '#ffffff'; g.beginPath(); g.arc(w * 0.42, h / 2, 52, 0, TAU); g.fill();
    g.fillStyle = '#e5484d'; g.font = "900 78px 'Arial Black', sans-serif"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(String(n), w * 0.42, h / 2 + 4);
  });
}
function matTex(n) {
  return canvasTex(256, 170, (g, w, h) => {
    g.fillStyle = '#2f7d3c'; g.fillRect(0, 0, w, h);
    const r = rng(4); for (let k = 0; k < 3000; k++) { g.fillStyle = `rgba(255,255,255,${r() * 0.12})`; g.fillRect(r() * w, r() * h, 1.5, 1.5); }
    g.setLineDash([10, 7]); g.strokeStyle = '#ffffff'; g.lineWidth = 5; g.strokeRect(12, 12, w - 24, h - 24);
    g.setLineDash([]); g.fillStyle = '#ffffff'; g.font = '800 40px Arial, sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(`TEE ${n}`, w / 2, h / 2);
  });
}

// Bridges' handrails: solid, like walls (the same boxes as Golf.kt's Props.rails).
const RAIL_T = 0.014;
function railsOf(b) {
  const along = (b[3] - b[1]) > (b[2] - b[0]);
  return along ? [[b[0], b[1], b[0] + RAIL_T, b[3]], [b[2] - RAIL_T, b[1], b[2], b[3]]] : [[b[0], b[1], b[2], b[1] + RAIL_T], [b[0], b[3] - RAIL_T, b[2], b[3]]];
}

// ---- Shapes and objects -------------------------------------------------------------------------
function roundedShape(b, r, grow = 0) {
  const x0 = wx(b[0] - grow), x1 = wx(b[2] + grow), z0 = wz(b[1] - grow), z1 = wz(b[3] + grow), rr = (r + grow) * S;
  const s = new THREE.Shape();
  s.moveTo(x0 + rr, -z0);
  s.lineTo(x1 - rr, -z0); s.quadraticCurveTo(x1, -z0, x1, -z0 - rr);
  s.lineTo(x1, -z1 + rr); s.quadraticCurveTo(x1, -z1, x1 - rr, -z1);
  s.lineTo(x0 + rr, -z1); s.quadraticCurveTo(x0, -z1, x0, -z1 + rr);
  s.lineTo(x0, -z0 - rr); s.quadraticCurveTo(x0, -z0, x0 + rr, -z0);
  return s;
}
function slab(shape, depth, top, materials) {
  const g = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: false });
  g.rotateX(-Math.PI / 2);
  const m = new THREE.Mesh(g, materials);
  m.position.y = top - depth; m.castShadow = true; m.receiveShadow = true;
  return m;
}
function decal(box, tex, y = 0.03) {
  const g = new THREE.PlaneGeometry((box[2] - box[0]) * S, (box[3] - box[1]) * S); g.rotateX(-Math.PI / 2);
  const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ map: tex, transparent: true }));
  m.position.set(wx((box[0] + box[2]) / 2), y, wz((box[1] + box[3]) / 2)); return m;
}
function panel(g, w, h, fill, edge) { g.beginPath(); g.moveTo(30, 6); g.arcTo(w - 6, 6, w - 6, h - 6, 24); g.arcTo(w - 6, h - 6, 6, h - 6, 24); g.arcTo(6, h - 6, 6, 6, 24); g.arcTo(6, 6, w - 6, 6, 24); g.closePath(); g.fillStyle = fill; g.fill(); g.lineWidth = 10; g.strokeStyle = edge; g.stroke(); }
function caption(g, text, x, y, size) { g.font = `900 ${size}px Impact, 'Arial Black', sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.lineWidth = size * 0.22; g.strokeStyle = '#000'; g.strokeText(text, x, y); g.fillStyle = '#fff'; g.fillText(text, x, y); }
const OM = {
  beam: (len) => { const m = new THREE.MeshStandardMaterial({ map: TEX.beam.clone(), roughness: 0.75 }); m.map.needsUpdate = true; m.map.wrapS = THREE.RepeatWrapping; m.map.repeat.set(Math.max(1, len / 1.6), 1); return m; },
  beamDark: (len) => { const m = new THREE.MeshStandardMaterial({ map: TEX.beamDark.clone(), roughness: 0.8 }); m.map.needsUpdate = true; m.map.wrapS = THREE.RepeatWrapping; m.map.repeat.set(Math.max(1, len / 1.6), 1); return m; },
  planks: [TEX.plankA, TEX.plankB, TEX.plankC].map((t) => new THREE.MeshStandardMaterial({ map: t, roughness: 0.8 })),
  rubber: new THREE.MeshStandardMaterial({ map: TEX.rubber, roughness: 0.6 }),
  brass: new THREE.MeshStandardMaterial({ color: '#c9a24a', metalness: 0.7, roughness: 0.35 }),
};
function beamBetween(a, b, y0, w, h, mat) {
  const ax = wx(a[0]), az = wz(a[1]), bx = wx(b[0]), bz = wz(b[1]), len = Math.hypot(bx - ax, bz - az);
  const m = new THREE.Mesh(new THREE.BoxGeometry(len, h, w), mat || OM.beam(len));
  m.position.set((ax + bx) / 2, y0, (az + bz) / 2); m.rotation.y = -Math.atan2(bz - az, bx - ax); m.castShadow = m.receiveShadow = true;
  return m;
}
function buildWallNew(b) {
  const lx = (b[2] - b[0]) * S, lz = (b[3] - b[1]) * S, len = Math.max(lx, lz), alongX = lx >= lz;
  const g = new THREE.Group();
  const core = new THREE.Mesh(new THREE.BoxGeometry(lx, 0.34, lz), OM.beam(len)); core.position.y = 0.23; core.castShadow = core.receiveShadow = true; g.add(core);
  if (!alongX) core.material.map.rotation = Math.PI / 2;
  const cap = new THREE.Mesh(new THREE.BoxGeometry(lx + 0.06, 0.07, lz + 0.06), OM.beamDark(len)); cap.position.y = 0.43; cap.castShadow = true; g.add(cap);
  const kick = new THREE.Mesh(new THREE.BoxGeometry(lx + 0.04, 0.09, lz + 0.04), OM.rubber); kick.position.y = 0.045; g.add(kick);
  for (const s of [-1, 1]) {
    const end = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.48, 12), OM.brass);
    end.position.set(alongX ? s * lx / 2 : 0, 0.24, alongX ? 0 : s * lz / 2); end.castShadow = true; g.add(end);
  }
  g.position.set(wx((b[0] + b[2]) / 2), 0, wz((b[1] + b[3]) / 2));
  g.userData.box = b;
  return g;
}
function buildBridgeNew(b) {
  const g = new THREE.Group();
  const along = (b[3] - b[1]) > (b[2] - b[0]);
  const w = (b[2] - b[0]) * S, d = (b[3] - b[1]) * S, cx = wx((b[0] + b[2]) / 2), cz = wz((b[1] + b[3]) / 2);
  const len = along ? d : w, span = along ? w : d;
  const r = rng(Math.round(b[0] * 1000 + b[1] * 100));
  const n = Math.max(3, Math.round(len / 0.3));
  for (let i = 0; i < n; i++) {
    const t = (i + 0.5) / n - 0.5, pl = len / n - 0.035;
    const plank = new THREE.Mesh(new THREE.BoxGeometry(along ? span : pl, 0.08, along ? pl : span), OM.planks[Math.floor(r() * 3)]);
    plank.position.set(cx + (along ? 0 : t * len), -0.045 + (r() - 0.5) * 0.015, cz + (along ? t * len : 0));
    plank.rotation.y = (r() - 0.5) * 0.03; plank.castShadow = plank.receiveShadow = true; g.add(plank);
  }
  for (const s of [-0.32, 0.32]) {
    const str = new THREE.Mesh(new THREE.BoxGeometry(along ? 0.12 : len, 0.16, along ? len : 0.12), OM.beamDark(len));
    str.position.set(cx + (along ? s * span : 0), -0.16, cz + (along ? 0 : s * span)); g.add(str);
  }
  for (const rail of railsOf(b)) {
    // Posts along the rail line, a top rail and a middle rail.
    const rg = new THREE.Group();
    const mid = [(rail[0] + rail[2]) / 2, (rail[1] + rail[3]) / 2];
    const a = along ? [mid[0], b[1] + 0.015] : [b[0] + 0.015, mid[1]], z = along ? [mid[0], b[3] - 0.015] : [b[2] - 0.015, mid[1]];
    const posts = Math.max(2, Math.round(len / 0.9));
    for (let i = 0; i <= posts; i++) {
      const t = i / posts, p = [a[0] + (z[0] - a[0]) * t, a[1] + (z[1] - a[1]) * t];
      const post = new THREE.Mesh(new THREE.BoxGeometry(0.11, 0.62, 0.11), OM.beamDark(0.6)); post.position.set(wx(p[0]), 0.26, wz(p[1])); post.castShadow = true; rg.add(post);
      const knob = new THREE.Mesh(new THREE.SphereGeometry(0.07, 10, 8), OM.beamDark(0.3)); knob.position.set(wx(p[0]), 0.6, wz(p[1])); rg.add(knob);
    }
    rg.add(beamBetween(a, z, 0.52, 0.1, 0.08));
    rg.add(beamBetween(a, z, 0.26, 0.07, 0.06, OM.beamDark(len)));
    rg.userData.box = rail; g.add(rg); fx.walls.push(rg);
  }
  for (const t of [-0.45, 0, 0.45]) for (const s of [-0.36, 0.36]) {
    const pile = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.09, 1.8, 10), new THREE.MeshStandardMaterial({ map: TEX.bark, roughness: 1 }));
    pile.position.set(cx + (along ? s * span : t * len), -1.0, cz + (along ? t * len : s * span)); g.add(pile);
  }
  return g;
}
function rockMesh(size, seed) {
  const r = rng(seed), geo = new THREE.IcosahedronGeometry(1, 1);
  const p = geo.attributes.position, cols = [];
  const seen = new Map();
  for (let i = 0; i < p.count; i++) {
    const key = `${p.getX(i).toFixed(3)},${p.getY(i).toFixed(3)},${p.getZ(i).toFixed(3)}`;
    if (!seen.has(key)) seen.set(key, 0.75 + r() * 0.45);
    const k = seen.get(key); p.setXYZ(i, p.getX(i) * k, p.getY(i) * k * 0.7, p.getZ(i) * k);
  }
  geo.computeVertexNormals();
  const nrm = geo.attributes.normal, c = new THREE.Color();
  for (let i = 0; i < p.count; i++) {
    if (nrm.getY(i) > 0.7 && r() > 0.3) c.set('#6f8f3e'); else c.setHSL(0.08, 0.08 + r() * 0.08, 0.38 + r() * 0.16);
    cols.push(c.r, c.g, c.b);
  }
  geo.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
  const m = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true, roughness: 1 }));
  m.scale.setScalar(size); m.rotation.y = r() * TAU; m.castShadow = m.receiveShadow = true;
  return m;
}
function flower(color, seed) {
  const g = new THREE.Group(), r = rng(seed), green = new THREE.MeshLambertMaterial({ color: '#3d8f3f' });
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.01, 0.012, 0.24, 5), green); stem.position.y = 0.12; g.add(stem);
  for (const s of [-1, 1]) { const lf = new THREE.Mesh(new THREE.SphereGeometry(0.045, 8, 6), green); lf.scale.set(1, 0.25, 0.45); lf.position.set(s * 0.04, 0.07, 0); lf.rotation.z = s * 0.5; g.add(lf); }
  const petalMat = new THREE.MeshLambertMaterial({ color });
  for (let i = 0; i < 5; i++) { const pt = new THREE.Mesh(new THREE.SphereGeometry(0.04, 8, 6), petalMat); pt.scale.set(1, 0.35, 0.6); const a = i * TAU / 5; pt.position.set(Math.cos(a) * 0.045, 0.25, Math.sin(a) * 0.045); pt.rotation.y = -a; g.add(pt); }
  const mid = new THREE.Mesh(new THREE.SphereGeometry(0.025, 8, 6), new THREE.MeshLambertMaterial({ color: '#ffb31a' })); mid.position.y = 0.26; g.add(mid);
  g.rotation.y = r() * TAU; return g;
}
function tuftNew(seed) {
  const g = new THREE.Group(), r = rng(seed);
  const mats = ['#3d8f3f', '#57a84a', '#2f7a34'].map((c) => new THREE.MeshLambertMaterial({ color: c }));
  for (let i = 0; i < 8; i++) { const h = 0.28 + r() * 0.26, blade = new THREE.Mesh(new THREE.ConeGeometry(0.022, h, 3), mats[i % 3]); blade.position.set((r() - 0.5) * 0.14, h / 2, (r() - 0.5) * 0.14); blade.rotation.z = (r() - 0.5) * 0.7; blade.rotation.x = (r() - 0.5) * 0.7; g.add(blade); }
  return g;
}
function flameGeo() {
  const geo = new THREE.ConeGeometry(0.16, 0.62, 8, 3), p = geo.attributes.position, cols = [], c = new THREE.Color();
  for (let i = 0; i < p.count; i++) { const k = (p.getY(i) + 0.31) / 0.62; c.set('#ffe36b').lerp(new THREE.Color('#ff3d12'), k); cols.push(c.r, c.g, c.b); }
  geo.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3)); return geo;
}
const FLAME_GEO = flameGeo();
function fireNew(box) {
  const g = new THREE.Group(), r = rng(Math.round(box[0] * 999));
  const logMat = new THREE.MeshStandardMaterial({ map: TEX.ember, emissive: '#ff5a10', emissiveMap: TEX.ember, emissiveIntensity: 0.9, roughness: 1 });
  const cx = wx((box[0] + box[2]) / 2), cz = wz((box[1] + box[3]) / 2), sw = (box[2] - box[0]) * S, sd = (box[3] - box[1]) * S;
  for (let i = 0; i < 6; i++) {
    const log = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.09, 0.9 + r() * 0.5, 8), logMat);
    log.rotation.z = Math.PI / 2; log.rotation.y = r() * Math.PI; log.position.set(cx + (r() - 0.5) * sw * 0.7, 0.08, cz + (r() - 0.5) * sd * 0.7); log.castShadow = true; g.add(log);
  }
  const flameMat = new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.92 });
  const area = (box[2] - box[0]) * (box[3] - box[1]);
  for (let k = 0; k < Math.round(area * 220); k++) {
    const f = new THREE.Mesh(FLAME_GEO, flameMat);
    f.position.set(cx + (r() - 0.5) * sw * 0.9, 0.36, cz + (r() - 0.5) * sd * 0.9); f.userData.phase = r() * 6; f.userData.box = box;
    g.add(f); fx.fires.push(f);
  }
  const light = new THREE.PointLight('#ff7a2a', 1.2, 6, 2); light.position.set(cx, 0.9, cz); g.add(light); fx.fireLight = light;
  return g;
}
function frameFor(box) {
  const g = new THREE.Group(), [x0, y0, x1, y1] = box;
  g.add(beamBetween([x0, y0], [x1, y0], 0.012, 0.12, 0.03)); g.add(beamBetween([x0, y1], [x1, y1], 0.012, 0.12, 0.03));
  g.add(beamBetween([x0, y0], [x0, y1], 0.012, 0.12, 0.03)); g.add(beamBetween([x1, y0], [x1, y1], 0.012, 0.12, 0.03));
  return g;
}
function palmToon(seed) {
  const g = new THREE.Group(), r = rng(seed);
  const light = new THREE.MeshStandardMaterial({ color: '#c99a5e', flatShading: true, roughness: 0.8 }), dark = new THREE.MeshStandardMaterial({ color: '#a8743f', flatShading: true, roughness: 0.8 });
  let top = new THREE.Vector3();
  for (let i = 0; i < 9; i++) {
    const seg = new THREE.Mesh(new THREE.CylinderGeometry(0.13 - i * 0.006, 0.16 - i * 0.006, 0.34, 7), i % 2 ? dark : light);
    const x = i * i * 0.008; seg.position.set(x, 0.17 + i * 0.31, 0); seg.rotation.z = -i * 0.035; seg.castShadow = true; g.add(seg);
    top.set(x, 0.17 + i * 0.31 + 0.15, 0);
  }
  const crown = new THREE.Group(); crown.position.copy(top); g.add(crown); g.userData.crown = crown;
  const leafShape = new THREE.Shape();
  leafShape.moveTo(0, 0);
  const pts = 7;
  for (let i = 1; i <= pts; i++) { const x = i / pts * 1.5, w = Math.sin(Math.PI * i / pts) * 0.32; leafShape.lineTo(x - 0.1, w); leafShape.lineTo(x, w * 0.7); }
  for (let i = pts; i >= 1; i--) { const x = i / pts * 1.5, w = Math.sin(Math.PI * i / pts) * 0.32; leafShape.lineTo(x, -w * 0.7); leafShape.lineTo(x - 0.1, -w); }
  leafShape.closePath();
  const leafMat = new THREE.MeshStandardMaterial({ color: '#43b54a', flatShading: true, side: THREE.DoubleSide, roughness: 0.6 }), leafDark = leafMat.clone(); leafDark.color = new THREE.Color('#2f9a3c');
  for (let i = 0; i < 6; i++) {
    const geo = new THREE.ShapeGeometry(leafShape, 3); geo.rotateX(-Math.PI / 2);
    const p = geo.attributes.position; for (let v = 0; v < p.count; v++) { const x = p.getX(v); p.setY(v, -x * x * 0.22 + Math.abs(p.getZ(v)) * 0.4); }
    geo.computeVertexNormals();
    const leaf = new THREE.Mesh(geo, i % 2 ? leafMat : leafDark); leaf.castShadow = true;
    const h = new THREE.Group(); h.add(leaf); leaf.rotation.z = 0.2; h.rotation.y = i * TAU / 6 + r() * 0.2; crown.add(h);
  }
  for (let i = 0; i < 2; i++) { const nut = new THREE.Mesh(new THREE.IcosahedronGeometry(0.1, 0), new THREE.MeshStandardMaterial({ color: '#6b4426', flatShading: true })); nut.position.set(i ? 0.08 : -0.06, -0.12, i ? 0.05 : -0.07); crown.add(nut); }
  g.userData.trunkR = 0.017;
  return g;
}
function tickerBoard(w) {
  const g = new THREE.Group();
  const board = new THREE.Mesh(new THREE.BoxGeometry(w, 0.42, 0.06), [OM.brass, OM.brass, OM.brass, OM.brass, new THREE.MeshBasicMaterial({ map: TEX.ticker }), OM.brass]);
  board.position.y = 0.95; g.add(board);
  for (const s of [-1, 1]) { const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 0.8, 8), OM.brass); leg.position.set(s * (w / 2 - 0.08), 0.4, 0); g.add(leg); }
  return g;
}
const transients = [];
function addTransient(obj, dur, update) { scene.add(obj); transients.push({ obj, start: W.t, dur, update }); update(0, obj); }
function stepTransients() {
  for (let i = transients.length - 1; i >= 0; i--) {
    const tr = transients[i], k = (W.t - tr.start) / tr.dur;
    if (k >= 1) { scene.remove(tr.obj); transients.splice(i, 1); continue; }
    tr.update(k, tr.obj);
  }
}
const WATERY = new THREE.MeshPhongMaterial({ color: '#e4f6ff', transparent: true, opacity: 0.85, shininess: 120, specular: '#ffffff', side: THREE.DoubleSide });
function ringFx(at, y, r0, r1, dur, delay, width, color = '#ffffff') {
  const m = new THREE.Mesh(new THREE.RingGeometry(1 - width, 1, 48).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color, transparent: true, depthWrite: false }));
  m.position.set(at.x, y, at.z);
  addTransient(m, dur + delay, (k, o) => { const t = clamp((k * (dur + delay) - delay) / dur, 0, 1); o.visible = t > 0; o.scale.setScalar(r0 + (r1 - r0) * t); o.material.opacity = 0.75 * (1 - t); });
}
function textSprite(text) {
  const tex = canvasTex(256, 128, (g, w, h) => {
    g.font = "800 72px 'Baloo 2', 'Arial Black', sans-serif"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.lineJoin = 'round';
    g.lineWidth = 14; g.strokeStyle = '#123a5a'; g.strokeText(text, w / 2, h / 2); g.fillStyle = '#ffffff'; g.fillText(text, w / 2, h / 2);
  });
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false })); s.scale.set(2.2, 1.1, 1); return s;
}
const timers = [];
function setTimeoutT(after, fn) { timers.push({ at: W.t + after, fn }); }
function stepTimers() { for (let i = timers.length - 1; i >= 0; i--) if (W.t >= timers[i].at) { const f = timers[i].fn; timers.splice(i, 1); f(); } }
// ---- The course ---------------------------------------------------------------------------------
let course = null, flag = null, flagPole = null, cupRing = null;
const fx = { fires: [], portals: [], tufts: [], ripples: [], walls: [], eyes: [], fireLight: null };
// The solid things on the course (from the hole's props) and the soft ones that bend.
const COLLIDERS = [];   // { x, y, r, h, kind, obj }
const PLANTS = [];      // { x, y, obj, bendAt, dir }
const MAP_POS = new THREE.Vector3(), MAP_LOOK = new THREE.Vector3();
const PETALS = ['#ffffff', '#ffd23f', '#ff8fb1', '#b48cff'];

// A portal: a turning spiral in a glowing ring (its six stones come with the hole's props).
function portalAt(at) {
  const g = new THREE.Group();
  const disc = new THREE.Mesh(new THREE.CircleGeometry(0.72, 48).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ map: TEX.swirl, transparent: true }));
  disc.position.y = 0.04; g.add(disc); fx.portals.push(disc);
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.74, 0.07, 10, 48).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: '#8ef0ff', emissive: '#6a40ff', emissiveIntensity: 0.8 }));
  ring.position.y = 0.07; ring.userData.ring = true; g.add(ring); fx.portals.push(ring);
  g.position.set(wx(at[0]), 0, wz(at[1]));
  return g;
}
// The Stonks ramp: green and red candlesticks rising like a chart under a glass slope, the ticker
// board behind. Its sides are solid in the app's physics; the front stays open.
function candleRamp(z) {
  const g = new THREE.Group(), w = (z.box[2] - z.box[0]) * S * 0.8, L = 1.15, H = 0.32;
  const n = 6, cols = ['#2fbf6a', '#2fbf6a', '#e5484d', '#2fbf6a', '#2fbf6a', '#2fbf6a'];
  for (let i = 0; i < n; i++) {
    const t = (i + 0.5) / n, hgt = 0.04 + H * t;
    const body = new THREE.Mesh(new THREE.BoxGeometry(w * 0.7, hgt, L / n * 0.62), new THREE.MeshStandardMaterial({ color: cols[i], roughness: 0.35, emissive: cols[i], emissiveIntensity: 0.15 }));
    body.position.set(0, hgt / 2, L / 2 - t * L); body.castShadow = true; g.add(body);
    const wick = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, hgt + 0.12, 6), new THREE.MeshStandardMaterial({ color: '#1b1b1b' })); wick.position.set(0, (hgt + 0.12) / 2, L / 2 - t * L); g.add(wick);
  }
  const glass = new THREE.Mesh(new THREE.BoxGeometry(w, 0.025, Math.hypot(L, H)), new THREE.MeshPhongMaterial({ color: '#cfefff', transparent: true, opacity: 0.35, shininess: 120 }));
  glass.rotation.x = Math.atan2(H, L); glass.position.set(0, H / 2 + 0.03, 0); g.add(glass);
  // The solid sides, as low glass walls.
  for (const s of [-1, 1]) { const side = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.36, L), new THREE.MeshPhongMaterial({ color: '#9fb4c4', transparent: true, opacity: 0.55 })); side.position.set(s * w / 2, 0.18, 0); g.add(side); }
  const board = tickerBoard(w * 0.95); board.position.z = -L / 2 - 0.25; g.add(board);
  g.position.set(wx((z.box[0] + z.box[2]) / 2), 0.01, wz((z.box[1] + z.box[3]) / 2));
  return g;
}
function marker() {
  const mk = new THREE.Group();
  const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.05, 0.16, 10), new THREE.MeshStandardMaterial({ color: '#ffffff', roughness: 0.4 })); post.position.y = 0.08; mk.add(post);
  const top = new THREE.Mesh(new THREE.SphereGeometry(0.1, 14, 10, 0, TAU, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: '#e5484d', roughness: 0.35 })); top.position.y = 0.16; mk.add(top);
  mk.traverse((o) => { o.castShadow = true; });
  return mk;
}

function buildHole(h, num) {
  if (course) { scene.remove(course); course.traverse((o) => { if (o.geometry) o.geometry.dispose(); }); }
  HOLE = Object.assign({ bridges: [], walls: [], sand: [], water: [], zones: [], props: [] }, h);
  const len = HOLE.len || 1.6;
  HALF = len / 2;
  MAP_POS.set(0, 14 + len * 7, len * 2.2); MAP_LOOK.set(0, 0, -len * 0.38);
  Object.assign(sun.shadow.camera, { top: len * S / 2 + 2, bottom: -(len * S / 2 + 2) }); sun.shadow.camera.updateProjectionMatrix();
  course = new THREE.Group();
  Object.assign(fx, { fires: [], portals: [], tufts: [], ripples: [], walls: [], eyes: [], fireLight: null });
  COLLIDERS.length = 0; PLANTS.length = 0; W.wobble.clear();
  for (const b of HOLE.islands) {
    course.add(slab(roundedShape(b, CORNER), 0.35, 0, [M.grass, M.soil]));
    // A lip of grass over the edge, cliffs of layered rock under it, and foam where the sea meets them.
    const lip = slab(roundedShape(b, CORNER, 0.004), 0.06, -0.02, M.lip); lip.castShadow = false; course.add(lip);
    course.add(slab(roundedShape(b, CORNER, -0.015), 1.1, -0.35, M.rock));
    const outer = roundedShape(b, CORNER, 0.045), inner = roundedShape(b, CORNER, -0.01);
    outer.holes.push(new THREE.Path(inner.getPoints(24).reverse()));
    const foamGeo = new THREE.ShapeGeometry(outer, 24); foamGeo.rotateX(-Math.PI / 2);
    const foam = new THREE.Mesh(foamGeo, new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.55 }));
    foam.position.y = WATER_Y + 0.12; foam.userData.foam = true; course.add(foam); fx.ripples.push(foam);
  }
  for (const b of HOLE.sand) { const m = slab(roundedShape(b, 0.03), 0.04, 0.02, M.sand); m.castShadow = false; course.add(m); }
  for (const p of HOLE.water) {
    const g = new THREE.CircleGeometry(1, 40); g.rotateX(-Math.PI / 2);
    const m = new THREE.Mesh(g, M.pond); m.scale.set(p[2] * S, 1, p[3] * S); m.position.set(wx(p[0]), 0.02, wz(p[1])); course.add(m);
    const rim = new THREE.Mesh(new THREE.RingGeometry(1, 1.1, 40).rotateX(-Math.PI / 2), new THREE.MeshLambertMaterial({ color: '#d9cfa8' }));
    rim.scale.copy(m.scale); rim.position.copy(m.position); rim.position.y = 0.025; course.add(rim);
    // Ripples spreading, lily pads and a water lily.
    for (let k = 0; k < 2; k++) {
      const rip = new THREE.Mesh(new THREE.RingGeometry(0.9, 1, 40).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.5 }));
      rip.position.set(wx(p[0]), 0.03, wz(p[1])); rip.userData = { pond: p, phase: k * 0.5 }; course.add(rip); fx.ripples.push(rip);
    }
    const sx = Math.min(1, p[2] * S / 0.9), sz = Math.min(1, p[3] * S / 0.7);
    for (const [dx, dz, r, rot] of [[-0.45, 0.2, 0.32, 0.6], [0.35, -0.25, 0.25, 2.4]]) {
      const pad = new THREE.Mesh(new THREE.PlaneGeometry(r * 2, r * 2).rotateX(-Math.PI / 2), new THREE.MeshLambertMaterial({ map: TEX.lily, transparent: true, alphaTest: 0.3 }));
      pad.rotation.y = rot; pad.position.set(wx(p[0]) + dx * sx, 0.035, wz(p[1]) + dz * sz); pad.scale.setScalar(Math.min(sx, sz)); course.add(pad);
    }
    const lotus = new THREE.Group();
    for (let i = 0; i < 8; i++) { const pt = new THREE.Mesh(new THREE.SphereGeometry(0.07, 8, 6), new THREE.MeshLambertMaterial({ color: i % 2 ? '#ffc2d6' : '#ff8fb1' })); pt.scale.set(0.5, 0.35, 1); const a = i * TAU / 8; pt.position.set(Math.cos(a) * 0.06, 0.05, Math.sin(a) * 0.06); pt.rotation.y = -a; pt.rotation.x = -0.5; lotus.add(pt); }
    lotus.position.set(wx(p[0]) - 0.45 * sx, 0.04, wz(p[1]) + 0.2 * sz); course.add(lotus);
  }
  for (const b of HOLE.bridges) course.add(buildBridgeNew(b));
  for (const b of HOLE.walls) { const g = buildWallNew(b); course.add(g); fx.walls.push(g); }
  buildZones();
  placeProps();
  // Tee, cup and flag.
  const tee = new THREE.Mesh(new THREE.BoxGeometry(1.0, 0.04, 0.66), [M.lip, M.lip, new THREE.MeshLambertMaterial({ map: matTex(num) }), M.lip, M.lip, M.lip]);
  tee.position.set(wx(HOLE.tee[0]), 0.02, wz(HOLE.tee[1])); tee.receiveShadow = true; course.add(tee);
  const cup = new THREE.Mesh(new THREE.CircleGeometry(CUP * S, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ map: TEX.cupFloor }));
  cup.position.set(wx(HOLE.cup[0]), 0.012, wz(HOLE.cup[1])); course.add(cup);
  cupRing = new THREE.Mesh(new THREE.RingGeometry(CUP * S, CUP * S + 0.06, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffffff' }));
  cupRing.position.copy(cup.position); cupRing.position.y = 0.014; course.add(cupRing);
  flagPole = new THREE.Group();
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.035, 2.6, 8), new THREE.MeshLambertMaterial({ map: TEX.pole }));
  pole.position.y = 1.3; pole.castShadow = true; flagPole.add(pole);
  const flagGeo = new THREE.PlaneGeometry(1.0, 0.62, 12, 3); flagGeo.translate(0.5, 0, 0);
  flag = new THREE.Mesh(flagGeo, new THREE.MeshLambertMaterial({ map: flagTex(num), side: THREE.DoubleSide }));
  flag.position.set(0.03, 2.25, 0); flag.castShadow = true; flagPole.add(flag);
  flagPole.position.set(wx(HOLE.cup[0]), 0, wz(HOLE.cup[1])); course.add(flagPole);
  scene.add(course);
  stopStatue();
}

function panelArt(g, w, h, fill, edge) { panel(g, w, h, fill, edge); }
/** The sticker spots: a sign set into the grass for each, and what stands on them. */
function buildZones() {
  for (const z of HOLE.zones) {
    if (z.type === 'kylian') {
      const asp = (z.box[3] - z.box[1]) / (z.box[2] - z.box[0]);
      const tex = canvasTex(256, Math.round(256 * asp), (g, w, h) => {
        panelArt(g, w, h, '#6f6c78', '#e2bf55');
        if (kyImg.complete && kyImg.naturalWidth) { const s = Math.min(w * 0.64, h - 70); g.drawImage(kyImg, (w - s) / 2, 12, s, s); }
        g.fillStyle = '#c8102e'; g.fillRect(14, h - 52, w - 28, 40); caption(g, 'KYLIAN', w / 2, h - 32, 30);
      });
      // The picture may still be loading: draw it in when it arrives.
      if (!(kyImg.complete && kyImg.naturalWidth)) kyImg.addEventListener('load', () => { if (st.hole) buildHole(st.hole, st.num); }, { once: true });
      course.add(decal(z.box, tex)); course.add(frameFor(z.box));
    } else if (z.type === 'eyes') {
      course.add(decal(z.box, canvasTex(256, 300, (g, w, h) => { panelArt(g, w, h, '#efc29c', '#4a2c18'); caption(g, 'HMM. SURE.', w / 2, h * 0.82, 34); })));
      course.add(frameFor(z.box));
      // Real eyes that follow the ball: a veined white with a brown iris, under heavy lids.
      for (const f of [0.3, 0.7]) {
        const eye = new THREE.Group();
        const iris = new THREE.Mesh(new THREE.SphereGeometry(0.42, 32, 24), new THREE.MeshStandardMaterial({ map: TEX.iris, roughness: 0.18 }));
        eye.add(iris);
        const lid = new THREE.Mesh(new THREE.SphereGeometry(0.45, 24, 12, 0, TAU, 0, Math.PI / 2.2), new THREE.MeshStandardMaterial({ color: '#dda077', roughness: 0.6 }));
        lid.rotation.x = -0.35; eye.add(lid);
        const sock = new THREE.Mesh(new THREE.TorusGeometry(0.43, 0.06, 8, 24).rotateX(Math.PI / 2), new THREE.MeshStandardMaterial({ color: '#c98f68' })); sock.position.y = -0.1; eye.add(sock);
        const ex = z.box[0] + (z.box[2] - z.box[0]) * f, ey = z.box[1] + (z.box[3] - z.box[1]) * 0.4;
        eye.position.set(wx(ex), 0.24, wz(ey));
        eye.userData = { iris, lid, x: ex, y: ey }; course.add(eye); fx.eyes.push(eye);
      }
    } else if (z.type === 'stonks') {
      course.add(decal(z.box, canvasTex(256, 220, (g, w, h) => {
        const gr = g.createLinearGradient(0, 0, w, h); gr.addColorStop(0, '#0d2b6b'); gr.addColorStop(1, '#1f56c0');
        panelArt(g, w, h, gr, '#ff9f1c');
        g.beginPath(); g.moveTo(w * 0.2, h * 0.85); g.lineTo(w * 0.4, h * 0.55); g.lineTo(w * 0.55, h * 0.65); g.lineTo(w * 0.72, h * 0.25);
        g.lineWidth = 16; g.strokeStyle = '#ff9f1c'; g.lineJoin = 'round'; g.lineCap = 'round'; g.stroke();
        g.beginPath(); g.moveTo(w * 0.72, h * 0.08); g.lineTo(w * 0.86, h * 0.32); g.lineTo(w * 0.6, h * 0.3); g.closePath(); g.fillStyle = '#ff9f1c'; g.fill();
        caption(g, 'STONKS', w / 2, h * 0.86, 30);
      })));
      course.add(frameFor(z.box)); course.add(candleRamp(z));
    } else if (z.type === 'fire') {
      course.add(decal(z.box, canvasTex(256, 256, (g, w, h) => {
        const gr = g.createRadialGradient(w / 2, h / 2, 10, w / 2, h / 2, w * 0.6); gr.addColorStop(0, 'rgba(255,140,40,0.95)'); gr.addColorStop(0.6, 'rgba(120,30,10,0.9)'); gr.addColorStop(1, 'rgba(60,20,10,0)');
        g.fillStyle = gr; g.fillRect(0, 0, w, h);
        const r = rng(8); for (let k = 0; k < 40; k++) { g.fillStyle = `rgba(30,15,10,${0.3 + r() * 0.4})`; g.beginPath(); g.arc(r() * w, r() * h, 3 + r() * 8, 0, TAU); g.fill(); }
      }), 0.02));
      course.add(fireNew(z.box));
    } else if (z.type === 'portal') {
      course.add(portalAt(z.at));
    }
  }
}

/** The hole's props, where the app's physics has them: [kind, x, y, r, h, seed]. */
function placeProps() {
  for (const [kind, x, y, r, h, seed] of HOLE.props) {
    let o = null;
    if (kind === 'tuft') o = tuftNew(seed);
    else if (kind === 'flower') o = flower(PETALS[Math.abs(seed) % 4], seed);
    else if (kind === 'rock' || kind === 'stone') { o = rockMesh(kind === 'stone' ? 0.1 : r * S / 0.95, seed * 7 + 3); o.position.y = 0.04; }
    else if (kind === 'palm') { o = palmToon(seed); o.userData.palm = true; }
    else if (kind === 'marker') o = marker();
    else if (kind === 'eye') {
      // The eyes are already standing on their sign: bumps go to the nearest one.
      const eye = fx.eyes.reduce((a, e) => (!a || Math.hypot(e.userData.x - x, e.userData.y - y) < Math.hypot(a.userData.x - x, a.userData.y - y) ? e : a), null);
      if (eye) COLLIDERS.push({ x, y, r, h, kind, obj: eye });
      continue;
    }
    if (!o) continue;
    o.position.x = wx(x); o.position.z = wz(y); o.userData.phase = (seed * 1.7) % 6;
    course.add(o);
    if (kind === 'tuft' || kind === 'flower') { fx.tufts.push(o); PLANTS.push({ x, y, obj: o }); }
    else { if (kind === 'palm') fx.tufts.push(o); COLLIDERS.push({ x, y, r, h, kind, obj: o }); }
  }
}

// ---- The statue and its trophies --------------------------------------------------------------
const reflectRT = new THREE.WebGLCubeRenderTarget(256, { format: THREE.RGBFormat, generateMipmaps: true, minFilter: THREE.LinearMipmapLinearFilter });
const reflectCam = new THREE.CubeCamera(0.1, 300, reflectRT); scene.add(reflectCam);
TEX.marble = canvasTex(256, 256, (g, w, h) => {
  g.fillStyle = '#f2f0ec'; g.fillRect(0, 0, w, h);
  const r = rng(29);
  for (let k = 0; k < 14; k++) {
    g.strokeStyle = `rgba(120,120,130,${0.15 + r() * 0.3})`; g.lineWidth = 0.6 + r() * 1.8;
    let x = r() * w, y = r() * h; g.beginPath(); g.moveTo(x, y);
    for (let s = 0; s < 12; s++) { x += (r() - 0.3) * 30; y += (r() - 0.5) * 30; g.lineTo(x, y); }
    g.stroke();
  }
});
TEX.plate = canvasTex(256, 64, (g, w, h) => {
  const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#f7dd8a'); gr.addColorStop(1, '#b8902e'); g.fillStyle = gr; g.fillRect(0, 0, w, h);
  g.strokeStyle = '#7a5a12'; g.lineWidth = 4; g.strokeRect(4, 4, w - 8, h - 8);
  g.fillStyle = '#4a3408'; g.font = "800 28px 'Baloo 2', serif"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('KYLIAN DICTADOR', w / 2, h / 2 + 2);
});
const FACE_BOX = [46.5, 41.5, 27, 31];
function faceTex(filter) {
  return canvasTex(256, 272, (g, w, h) => {
    if (!(kyImg.complete && kyImg.naturalWidth)) return;
    g.filter = filter || 'none';
    const s = kyImg.naturalWidth / 120;
    g.drawImage(kyImg, FACE_BOX[0] * s, FACE_BOX[1] * s, FACE_BOX[2] * s, FACE_BOX[3] * s, 0, 0, w, h);
    // Fade the edges out so the face melts into the head instead of sitting on it like a mask.
    g.filter = 'none'; g.globalCompositeOperation = 'destination-in';
    g.save(); g.translate(w / 2, h / 2); g.scale(1, h / w);
    const gr = g.createRadialGradient(0, 0, w * 0.3, 0, 0, w * 0.5); gr.addColorStop(0, 'rgba(0,0,0,1)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(-w / 2, -w / 2, w, w); g.restore();
    g.globalCompositeOperation = 'source-over';
  });
}
TEX.ribbons = canvasTex(96, 48, (g) => {
  const cols = ['#c8102e', '#e2bf55', '#2f6fb3', '#2f8f4e', '#e2bf55', '#7a3fb3', '#2f6fb3', '#ffffff', '#c8102e', '#e2bf55', '#2f8f4e', '#c8102e', '#e2bf55', '#c8102e', '#2f8f4e', '#2f6fb3', '#ffffff', '#e2bf55'];
  cols.forEach((c, i) => { g.fillStyle = c; g.fillRect((i % 6) * 16, Math.floor(i / 6) * 16, 15, 15); });
});
function lathe(points, segs = 24) { return new THREE.LatheGeometry(points.map(([r, y]) => new THREE.Vector2(r, y)), segs); }
function limb(a, b, ra, rb, mat) {
  const g = new THREE.Group(), d = b.clone().sub(a), len = d.length();
  const c = new THREE.Mesh(new THREE.CylinderGeometry(rb, ra, len, 14), mat);
  c.position.copy(a).addScaledVector(d, 0.5); c.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize());
  g.add(c);
  const j = new THREE.Mesh(new THREE.SphereGeometry(rb * 1.02, 14, 10), mat); j.position.copy(b); g.add(j);
  return g;
}
TEX.goldBallPanels = (() => {
  const W2 = 1024, H2 = 512, cv = document.createElement('canvas'); cv.width = W2; cv.height = H2;
  const g = cv.getContext('2d'), img = g.createImageData(W2, H2), d = img.data;
  const t = (1 + Math.sqrt(5)) / 2, ico = [[-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0], [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t], [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]].map((v) => new THREE.Vector3(...v).normalize());
  const faces = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8], [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]];
  // A panel's plane: pentagons sit a touch further out than hexagons (edge 1: 2.327 against 2.249).
  const planes = ico.map((n) => ({ n, k: 1 / 2.3274 })).concat(faces.map((f) => ({ n: f.reduce((a, i) => a.add(ico[i]), new THREE.Vector3()).normalize(), k: 1 / 2.249 })));
  const u = new THREE.Vector3();
  for (let py = 0; py < H2; py++) for (let px = 0; px < W2; px++) {
    const phi = (px + 0.5) / W2 * TAU, th = (py + 0.5) / H2 * Math.PI;
    u.set(-Math.cos(phi) * Math.sin(th), Math.cos(th), Math.sin(phi) * Math.sin(th));
    let a = -1, b = -1;
    for (const p of planes) { const s = u.dot(p.n) * p.k; if (s > a) { b = a; a = s; } else if (s > b) b = s; }
    const gap = (a - b) / a;                       // 0 on a seam
    const seam = Math.max(0, 1 - gap / 0.012);       // a fine engraved line
    const dome = Math.min(1, gap / 0.08);            // the panel rises away from its seams
    const i = (py * W2 + px) * 4;
    const v = Math.round(255 * (0.55 + 0.45 * dome) * (1 - 0.85 * seam));
    d[i] = v; d[i + 1] = v; d[i + 2] = v; d[i + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  const tex = new THREE.CanvasTexture(cv); tex.anisotropy = 8; return tex;
})();
TEX.goldBallColour = (() => {
  const src = TEX.goldBallPanels.image, cv = document.createElement('canvas'); cv.width = src.width; cv.height = src.height;
  const g = cv.getContext('2d'); g.drawImage(src, 0, 0);
  const img = g.getImageData(0, 0, cv.width, cv.height), d = img.data;
  for (let i = 0; i < d.length; i += 4) { const k = d[i] / 255, line = k < 0.4 ? 1 - k / 0.4 : 0; d[i] = 255 - line * 150; d[i + 1] = 255 - line * 170; d[i + 2] = 255 - line * 200; }
  g.putImageData(img, 0, 0);
  const tex = new THREE.CanvasTexture(cv); tex.anisotropy = 8; return tex;
})();
function engraved(lines, w, h, size, colour = '#5a3d06') {
  return canvasTex(w, h, (g) => {
    g.fillStyle = '#ffffff'; g.fillRect(0, 0, w, h);
    g.textAlign = 'center'; g.textBaseline = 'middle';
    lines.forEach(([text, font, y]) => { g.font = font; g.fillStyle = colour; g.fillText(text, w / 2, y * h); });
    g.strokeStyle = colour; g.lineWidth = Math.max(2, h * 0.02); g.strokeRect(h * 0.06, h * 0.06, w - h * 0.12, h - h * 0.12);
  });
}
function taperedBlock(bottom, top, h) {
  const geo = new THREE.BoxGeometry(bottom, h, bottom), p = geo.attributes.position, k = top / bottom;
  for (let i = 0; i < p.count; i++) if (p.getY(i) > 0) { p.setX(i, p.getX(i) * k); p.setZ(i, p.getZ(i) * k); }
  geo.computeVertexNormals(); geo.translate(0, h / 2, 0); return geo;
}
function faceDecal(tex, bottom, top, h, side, mat) {
  const tilt = Math.atan2((bottom - top) / 2, h), slant = h / Math.cos(tilt);
  const m = new THREE.Mesh(new THREE.PlaneGeometry(top + (bottom - top) * 0.5, slant), mat || new THREE.MeshStandardMaterial({ map: tex, transparent: true }));
  const holder = new THREE.Group(); holder.rotation.y = side * Math.PI / 2; holder.add(m);
  m.position.set(0, h / 2, (bottom + top) / 4 + 0.0015); m.rotation.x = -tilt;
  return holder;
}
function goldenBall(env) {
  const g = new THREE.Group();
  const gold = (rough, extra = {}) => new THREE.MeshStandardMaterial(Object.assign({ color: '#f6c443', metalness: 1, roughness: rough, envMap: env, envMapIntensity: 0.9, emissive: '#5c3a00', emissiveIntensity: 0.55 }, extra));
  const R = 0.112;
  // The base: a thin plinth step, then a tapered square block, its four faces engraved.
  const step = new THREE.Mesh(taperedBlock(0.2, 0.19, 0.016), gold(0.35)); g.add(step);
  const block = new THREE.Mesh(taperedBlock(0.18, 0.12, 0.13), gold(0.22)); block.position.y = 0.016; g.add(block);
  const words = engraved([['GOLDEN BALL', "700 27px Georgia, 'Times New Roman', serif", 0.42], ['PIP & POP', "600 18px Georgia, serif", 0.7]], 256, 200, 30);
  const sideWords = engraved([['★', '700 60px Georgia, serif', 0.5]], 256, 200, 30);
  const engr = (tex) => gold(0.22, { map: tex, bumpMap: tex, bumpScale: -0.002 });
  for (let s = 0; s < 4; s++) { const d = faceDecal(null, 0.18, 0.12, 0.13, s, engr(s === 0 ? words : sideWords)); d.position.y = 0.016; g.add(d); }
  // A bevelled top plate and the small cup the ball sits in.
  const plate = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.085, 0.012, 4, 1).rotateY(Math.PI / 4), gold(0.2)); plate.position.y = 0.152; g.add(plate);
  const cup = new THREE.Mesh(new THREE.LatheGeometry([[0.0, 0], [0.035, 0], [0.04, 0.012], [0.05, 0.026], [0.058, 0.032], [0.054, 0.034]].map(([r, y]) => new THREE.Vector2(r, y)), 32), gold(0.18, { side: THREE.DoubleSide }));
  cup.position.y = 0.158; g.add(cup);
  // The ball itself: engraved panels, polished.
  const ball = new THREE.Mesh(new THREE.SphereGeometry(R, 96, 64), gold(0.14, { map: TEX.goldBallColour, bumpMap: TEX.goldBallPanels, bumpScale: 0.004 }));
  ball.position.y = 0.158 + 0.034 + R - 0.012; ball.rotation.set(0.3, 0.5, 0.1); g.add(ball);
  g.traverse((o) => { o.castShadow = true; });
  return g;
}
TEX.cupBody = canvasTex(1024, 512, (g, w, h) => {
  g.fillStyle = '#ffffff'; g.fillRect(0, 0, w, h);
  const ink = 'rgba(70,78,92,0.85)';
  // The emblem: a laurel wreath round a football, squeezed to the cup's curve.
  g.save(); g.translate(w / 2, h * 0.47); g.scale(0.84, 1);
  g.strokeStyle = ink; g.fillStyle = ink; g.lineWidth = 3;
  for (const s of [-1, 1]) {
    g.beginPath(); g.arc(0, 4, 62, Math.PI / 2 + s * 0.25, Math.PI / 2 + s * 2.6, s < 0); g.stroke();
    for (let i = 0; i < 9; i++) {
      const a = Math.PI / 2 + s * (0.45 + i * 0.25), x = Math.cos(a) * 62, y = 4 + Math.sin(a) * 62;
      g.save(); g.translate(x, y); g.rotate(a + s * 0.9); g.beginPath(); g.ellipse(0, 0, 11, 4.5, 0, 0, TAU); g.fill(); g.restore();
    }
  }
  g.beginPath(); g.arc(0, 0, 28, 0, TAU); g.lineWidth = 2.5; g.stroke();
  for (let i = 0; i < 5; i++) { const a = i * TAU / 5 - Math.PI / 2; g.beginPath(); g.moveTo(Math.cos(a) * 10, Math.sin(a) * 10); g.lineTo(Math.cos(a) * 28, Math.sin(a) * 28); g.stroke(); }
  g.beginPath(); for (let i = 0; i < 5; i++) { const a = i * TAU / 5 - Math.PI / 2; g.lineTo(Math.cos(a) * 10, Math.sin(a) * 10); } g.closePath(); g.fill();
  g.restore();
  g.fillStyle = ink; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.font = "700 22px Georgia, 'Times New Roman', serif"; g.fillText('PIP & POP', w / 2, h * 0.24);
  g.font = "600 16px Georgia, serif"; g.fillText('WINNERS', w / 2, h * 0.68);
  // Names on the back: rows of small engraved lettering either side of the seam.
  const r = rng(91);
  for (const x0 of [-210, w - 210]) for (let row = 0; row < 18; row++) {
    const y = h * 0.16 + row * 19, x = x0 + 30 + r() * 30;
    g.fillStyle = 'rgba(70,78,92,0.6)';
    let cx = x; while (cx < x + 330) { const l = 4 + r() * 22; g.fillRect(cx, y, l, 5); cx += l + 4 + r() * 6; }
  }
  // Two engraved bands, at the rim and under the bowl.
  g.fillStyle = ink; g.fillRect(0, h * 0.04, w, 3); g.fillRect(0, h * 0.94, w, 3);
});
function silverCup(env, H = 0.6) {
  const g = new THREE.Group();
  const silver = (rough, extra = {}) => new THREE.MeshStandardMaterial(Object.assign({ color: '#f2f2f0', metalness: 0.92, roughness: rough + 0.08, envMap: env, envMapIntensity: 0.5, emissive: '#6b6e73', emissiveIntensity: 0.42 }, extra));
  const L = (pts, segs = 48, phi = 0) => new THREE.LatheGeometry(pts.map(([r, y]) => new THREE.Vector2(r, y)), segs, phi);
  // The round foot, in two steps with a moulded band.
  g.add(new THREE.Mesh(L([[0, 0], [0.25, 0], [0.256, 0.01], [0.252, 0.026], [0.24, 0.032], [0.236, 0.05], [0.226, 0.056], [0.2, 0.062], [0.19, 0.072], [0.17, 0.076], [0.16, 0.092], [0.14, 0.098], [0, 0.098]]), silver(0.16)));
  // The stem with its knop.
  g.add(new THREE.Mesh(L([[0, 0.098], [0.11, 0.098], [0.07, 0.112], [0.05, 0.13], [0.043, 0.16], [0.05, 0.172], [0.072, 0.18], [0.075, 0.188], [0.058, 0.198], [0.045, 0.206], [0.043, 0.218], [0.05, 0.23], [0, 0.23]]), silver(0.1)));
  // The body: a round bowl that narrows up a long waist and flares out to the rim; open at the top.
  const body = [[0.035, 0.222], [0.07, 0.24], [0.115, 0.275], [0.15, 0.32], [0.172, 0.38], [0.18, 0.45], [0.178, 0.53], [0.172, 0.61], [0.166, 0.69], [0.164, 0.77], [0.17, 0.84], [0.184, 0.9], [0.206, 0.945], [0.226, 0.972], [0.238, 0.988], [0.236, 1.0], [0.224, 0.998], [0.21, 0.978], [0.19, 0.944], [0.158, 0.87], [0.15, 0.78], [0.156, 0.66], [0.15, 0.58]];
  const bodyGeo = L(body, 64, Math.PI);
  // Map the engraving by height, so the picture isn't stretched by the profile.
  const p = bodyGeo.attributes.position, uv = bodyGeo.attributes.uv;
  for (let i = 0; i < p.count; i++) uv.setY(i, (p.getY(i) - 0.222) / 0.778);
  uv.needsUpdate = true;
  g.add(new THREE.Mesh(bodyGeo, silver(0.08, { map: TEX.cupBody, bumpMap: TEX.cupBody, bumpScale: -0.0015, side: THREE.DoubleSide })));
  // The thick rolled rim.
  const rim = new THREE.Mesh(new THREE.TorusGeometry(0.236, 0.008, 10, 64).rotateX(Math.PI / 2), silver(0.06)); rim.position.y = 0.996; g.add(rim);
  // The big handles: flat bands, joined under the rim and to the bowl, swinging out wide.
  const earPath = [[0.17, 0.88], [0.235, 0.945], [0.31, 0.965], [0.375, 0.935], [0.41, 0.86], [0.418, 0.76], [0.4, 0.64], [0.355, 0.53], [0.29, 0.45], [0.215, 0.405], [0.165, 0.395]];
  const band = new THREE.Shape(); band.moveTo(-0.012, -0.022); band.lineTo(0.012, -0.022); band.lineTo(0.012, 0.022); band.lineTo(-0.012, 0.022); band.closePath();
  for (const s of [-1, 1]) {
    const curve = new THREE.CatmullRomCurve3(earPath.map(([x, y]) => new THREE.Vector3(s * x, y, 0)));
    const ear = new THREE.Mesh(new THREE.ExtrudeGeometry(band, { steps: 80, bevelEnabled: false, extrudePath: curve }), silver(0.07));
    g.add(ear);
    // Where each ear meets the cup, a small round boss.
    for (const [x, y] of [earPath[0], earPath[earPath.length - 1]]) { const boss = new THREE.Mesh(new THREE.SphereGeometry(0.026, 16, 12), silver(0.1)); boss.position.set(s * (x + 0.005), y, 0); boss.scale.set(0.7, 1, 1.1); g.add(boss); }
  }
  g.scale.setScalar(H);
  g.traverse((o) => { o.castShadow = true; });
  return g;
}
function buildStatue(style) {
  const M2 = statueMaterials(), g = new THREE.Group(), fig = new THREE.Group();
  const add = (geo, mat, x, y, z, o = {}) => { const m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); if (o.rx) m.rotation.x = o.rx; if (o.ry) m.rotation.y = o.ry; if (o.rz) m.rotation.z = o.rz; if (o.s) m.scale.set(...o.s); (o.to || fig).add(m); return m; };
  const V = (x, y, z) => new THREE.Vector3(x, y, z);
  // The plinth with its name plate.
  add(new THREE.BoxGeometry(1.0, 0.36, 1.0), M2.plinth, 0, 0.18, 0, { to: g });
  add(new THREE.BoxGeometry(1.08, 0.06, 1.08), M2.plinth, 0, 0.39, 0, { to: g });
  add(new THREE.PlaneGeometry(0.62, 0.15), new THREE.MeshStandardMaterial({ map: TEX.plate, metalness: 0.7, roughness: 0.3 }), 0, 0.2, 0.505, { to: g });
  const B = 0.42; // the figure stands on the plinth
  // Boots and legs, feet a little apart, standing straight.
  for (const s of [-1, 1]) {
    const boot = add(lathe([[0, 0], [0.07, 0.005], [0.072, 0.09], [0.064, 0.2], [0.058, 0.26], [0, 0.26]]), M2.boots, s * 0.1, B, 0.0);
    add(new THREE.SphereGeometry(0.068, 14, 10), M2.boots, s * 0.1, B + 0.05, 0.07, { s: [1, 0.75, 1.5] });
    void boot;
    fig.add(limb(V(s * 0.1, B + 0.24, 0), V(s * 0.095, B + 0.47, 0.01), 0.068, 0.062, M2.trousers));
    fig.add(limb(V(s * 0.095, B + 0.47, 0.01), V(s * 0.085, B + 0.74, 0), 0.062, 0.085, M2.trousers));
  }
  // The tunic: hips, waist, chest and shoulders, turned smooth and flattened front to back.
  const torso = add(lathe([[0, 0], [0.19, 0.0], [0.2, 0.08], [0.175, 0.2], [0.19, 0.32], [0.215, 0.42], [0.22, 0.5], [0.17, 0.56], [0.07, 0.6], [0, 0.6]]), M2.coat, 0, B + 0.7, 0, { s: [1.12, 1, 0.78] });
  void torso;
  add(new THREE.CylinderGeometry(0.205, 0.205, 0.045, 24), M2.boots, 0, B + 0.86, 0, { s: [1.12, 1, 0.8] });
  add(new THREE.BoxGeometry(0.07, 0.05, 0.02), M2.gold, 0, B + 0.86, 0.165);
  // Collar, shirt and tie, red collar tabs.
  add(new THREE.CylinderGeometry(0.085, 0.1, 0.07, 16), M2.coat, 0, B + 1.31, 0.0);
  add(new THREE.ConeGeometry(0.05, 0.12, 3), M2.white, 0, B + 1.24, 0.15, { rx: Math.PI, s: [1, 1, 0.3] });
  add(new THREE.BoxGeometry(0.022, 0.14, 0.01), M2.boots, 0, B + 1.2, 0.163);
  for (const s of [-1, 1]) add(new THREE.BoxGeometry(0.035, 0.05, 0.01), M2.band, s * 0.055, B + 1.26, 0.15, { rz: s * 0.3 });
  // Buttons, the medal rack and stars, and the gold cord over his right shoulder.
  for (let i = 0; i < 4; i++) add(new THREE.SphereGeometry(0.016, 8, 6), M2.gold, 0.03, B + 0.92 + i * 0.09, 0.168 - (i > 2 ? 0.01 : 0));
  add(new THREE.BoxGeometry(0.11, 0.055, 0.012), M2.ribbons, -0.09, B + 1.13, 0.168);
  for (let i = 0; i < 3; i++) add(new THREE.CylinderGeometry(0.017, 0.017, 0.008, 8), M2.gold, -0.13 + i * 0.04, B + 1.07, 0.168, { rx: Math.PI / 2 });
  for (let i = 0; i < 3; i++) add(new THREE.OctahedronGeometry(0.02, 0), M2.gold, -0.13 + i * 0.04, B + 1.0, 0.168, { s: [1, 1, 0.3] });
  const cord = new THREE.CatmullRomCurve3([V(0.2, B + 1.25, 0.08), V(0.15, B + 1.12, 0.16), V(0.11, B + 1.0, 0.17), V(0.13, B + 0.97, 0.17), V(0.16, B + 1.06, 0.16), V(0.21, B + 1.22, 0.1)]);
  add(new THREE.TubeGeometry(cord, 30, 0.011, 6), M2.gold, 0, 0, 0);
  // Shoulders with epaulettes and their gold fringe.
  for (const s of [-1, 1]) {
    add(new THREE.SphereGeometry(0.075, 14, 10), M2.coat, s * 0.215, B + 1.21, 0);
    add(new THREE.CylinderGeometry(0.07, 0.07, 0.022, 16), M2.gold, s * 0.22, B + 1.28, 0, { s: [1, 1, 0.75] });
    for (let i = 0; i < 6; i++) { const a = -0.9 + i * 0.36; add(new THREE.SphereGeometry(0.014, 6, 5), M2.gold, s * (0.22 + 0.075 * Math.cos(a) * (s > 0 ? 1 : 1)) , B + 1.255, 0.06 * Math.sin(a)); }
  }
  // Arms: the left down by his side, the right raised in a salute to his cap.
  fig.add(limb(V(-0.23, B + 1.2, 0), V(-0.27, B + 0.95, 0.0), 0.06, 0.052, M2.coat));
  fig.add(limb(V(-0.27, B + 0.95, 0.0), V(-0.26, B + 0.72, 0.03), 0.052, 0.045, M2.coat));
  add(new THREE.SphereGeometry(0.048, 12, 10), M2.skin, -0.26, B + 0.67, 0.035, { s: [0.8, 1.15, 0.9] });
  fig.add(limb(V(0.23, B + 1.2, 0), V(0.36, B + 1.3, 0.12), 0.06, 0.052, M2.coat));
  fig.add(limb(V(0.36, B + 1.3, 0.12), V(0.16, B + 1.56, 0.13), 0.052, 0.045, M2.coat));
  add(new THREE.BoxGeometry(0.11, 0.025, 0.06), M2.skin, 0.1, B + 1.6, 0.13, { rz: -0.4 });
  // Neck, head and ears; the sticker's face on the front.
  add(new THREE.CylinderGeometry(0.06, 0.065, 0.1, 14), M2.skin, 0, B + 1.37, 0);
  const head = add(new THREE.SphereGeometry(0.13, 28, 20), M2.skin, 0, B + 1.5, 0, { s: [0.92, 1.12, 1] });
  void head;
  for (const s of [-1, 1]) add(new THREE.SphereGeometry(0.03, 10, 8), M2.skin, s * 0.12, B + 1.5, -0.005, { s: [0.5, 1, 0.8] });
  // The face: the front of a slightly larger sphere, mapped straight on from the sticker.
  const faceGeo = new THREE.SphereGeometry(0.1315, 28, 20, Math.PI * 0.18, Math.PI * 0.64, Math.PI * 0.2, Math.PI * 0.62);
  const fp = faceGeo.attributes.position, fuv = faceGeo.attributes.uv;
  for (let i = 0; i < fp.count; i++) fuv.setXY(i, 0.5 + fp.getX(i) / 0.21, 0.5 + (fp.getY(i) + 0.005) / 0.235);
  fuv.needsUpdate = true;
  add(faceGeo, M2.face, 0, B + 1.5, 0.004, { s: [0.92, 1.12, 1] });
  // The cap: crown, red band, black peak with gold braid, and the badge.
  add(lathe([[0, 0], [0.135, 0], [0.15, 0.04], [0.19, 0.11], [0.19, 0.13], [0.12, 0.155], [0, 0.16]]), M2.cap, 0, B + 1.59, -0.01, { s: [1, 1, 1.05] });
  add(new THREE.CylinderGeometry(0.139, 0.137, 0.04, 24), M2.band, 0, B + 1.615, -0.01);
  add(new THREE.CylinderGeometry(0.15, 0.15, 0.014, 20, 1, false, -Math.PI / 2, Math.PI), M2.boots, 0, B + 1.595, 0.035, { rx: 0.18 });
  add(new THREE.TorusGeometry(0.14, 0.007, 6, 24, Math.PI), M2.gold, 0, B + 1.6, 0.02, { rx: Math.PI / 2 + 0.1 });
  add(new THREE.CylinderGeometry(0.028, 0.028, 0.01, 14), M2.gold, 0, B + 1.68, 0.17, { rx: Math.PI / 2 + 0.35 });
  if (style === 'painted') {
    // His number on the back of the tunic.
    const ten = canvasTex(128, 128, (c, w, h) => {
      c.clearRect(0, 0, w, h); c.font = "900 96px 'Baloo 2', 'Arial Black', sans-serif"; c.textAlign = 'center'; c.textBaseline = 'middle';
      c.lineWidth = 10; c.strokeStyle = '#7a5a12'; c.strokeText('10', w / 2, h / 2 + 6); c.fillStyle = '#f2d36b'; c.fillText('10', w / 2, h / 2 + 6);
    });
    const back = new THREE.Mesh(new THREE.CylinderGeometry(0.2155, 0.2155, 0.2, 24, 1, true, Math.PI * 0.75, Math.PI * 0.5), new THREE.MeshStandardMaterial({ map: ten, transparent: true, roughness: 0.5 }));
    back.geometry.scale(1.12, 1, 0.8); back.position.set(0, B + 1.08, 0); fig.add(back);
    // The golden ball by his right foot, the silver cup by his left.
    const ball = goldenBall(reflectRT.texture); ball.position.set(-0.32, B, 0.2); ball.rotation.y = 0.35; fig.add(ball);
    const cup = silverCup(reflectRT.texture, 0.6); cup.position.set(0.33, B, 0.22); cup.rotation.y = -0.25; fig.add(cup);
  }
  g.add(fig);
  g.traverse((o) => { o.castShadow = true; });
  g.scale.setScalar(1.05);
  return g;
}
// ---- Kylian's statue -----------------------------------------------------------------------------
// While his song plays (the app plays it, on every phone), a painted statue of Kylian Dictador
// lowers slowly out of the sky onto his spot beside the ball, lands with a thump, and when the
// song ends shoots straight back up. It always faces down the course, towards the tee; nobody's
// camera moves for it, so where your ball is decides how you see him (or whether you have to turn
// round to look).
function statueMaterials() {
  const flat = (c, r = 0.6) => new THREE.MeshStandardMaterial({ color: c, roughness: r });
  return { coat: flat('#26324f'), trousers: flat('#1d2742'), skin: flat('#74462c', 0.55), face: new THREE.MeshStandardMaterial({ map: faceTex('none'), roughness: 0.55, transparent: true }), cap: flat('#26324f', 0.45), band: flat('#b81f2a'), boots: flat('#141414', 0.25), gold: new THREE.MeshStandardMaterial({ color: '#e2bf55', metalness: 0.8, roughness: 0.3 }), white: flat('#f2efe6'), ribbons: new THREE.MeshStandardMaterial({ map: TEX.ribbons, roughness: 0.5 }), plinth: new THREE.MeshStandardMaterial({ map: TEX.marble, roughness: 0.5 }) };
}
let statue = null;
const ky = { at: null, landed: false, burst: false, active: false, tick: 0 };
function refreshReflections() {
  if (!statue || !statue.visible) return;
  statue.visible = false;
  reflectCam.position.copy(statue.position).add(new THREE.Vector3(0, 1.3, 0));
  reflectCam.update(renderer, scene);
  statue.visible = true;
}
/** Kylian's statue comes down beside the ball at (x, y), on his spot. */
function startStatue(x, y) {
  const z = HOLE.zones.find((q) => q.type === 'kylian' && x >= q.box[0] - 0.05 && x <= q.box[2] + 0.05 && y >= q.box[1] - 0.05 && y <= q.box[3] + 0.05) || HOLE.zones.find((q) => q.type === 'kylian');
  if (!z) return;
  if (!statue) statue = buildStatue('painted');
  const c = [(z.box[0] + z.box[2]) / 2, (z.box[1] + z.box[3]) / 2];
  let v = [c[0] - x, c[1] - y]; const len = Math.hypot(v[0], v[1]); v = len > 1e-3 ? [v[0] / len, v[1] / len] : [0, -1];
  const sx = clamp(x + v[0] * 0.11, z.box[0] + 0.055, z.box[2] - 0.055), sy = clamp(y + v[1] * 0.11, z.box[1] + 0.055, z.box[3] - 0.055);
  ky.at = [sx, sy]; ky.landed = false; ky.burst = false; ky.active = true; W.spotAt = W.t;
  statue.position.set(wx(sx), 40, wz(sy)); statue.rotation.y = 0;
  scene.add(statue);
}
function stopStatue() { ky.active = false; if (statue) scene.remove(statue); }
function updateStatue(t) {
  if (!ky.active) return;
  const k = t - W.spotAt, D = KY_SONG, LAND = D - 0.9;
  if (k > D + 0.8) { stopStatue(); return; }
  const base = new THREE.Vector3(wx(ky.at[0]), 0, wz(ky.at[1]));
  let y;
  if (k < LAND) { const p = k / LAND; y = 16 * (1 - p) * (1 - p) * (1 - p * 0.25); }
  else if (k < D) {
    y = 0;
    if (!ky.landed) { ky.landed = true; emit(24, base.clone().setY(0.1), { speed: 2.2, up: 1.2, life: 0.7, size: 0.07, colors: ['#c9b48a', '#e2d3ae', '#ffffff'], gravity: 3, spread: 0.6 }); W.shakeAt = W.t; W.shakeK = nearness(ky.at); }
  } else {
    const q = (k - D) / 0.8; y = 40 * q * q;
    if (!ky.burst) { ky.burst = true; emit(30, base.clone().setY(0.5), { speed: 1.6, up: 3.4, life: 0.9, size: 0.05, colors: ['#ffd23f', '#fff3b0', '#e8a317'], gravity: 4 }); }
  }
  statue.position.set(base.x, Math.max(0, y) + 0.01, base.z);
  if (++ky.tick % 3 === 0) refreshReflections();
}
/** How near your ball is to [at] (course units), 1 right beside it, fading with distance: as the app sets the song's volume. */
function nearness(at) {
  const b = balls.get(st.me);
  if (st.meDone || !b || !b.mesh.visible || (st.play && st.play.id === st.me)) return 1;
  const d = Math.hypot(b.x - at[0], b.y - at[1]);
  return clamp(1 / (1 + (d / 0.6) ** 2), 0.06, 1);
}

// ---- Balls --------------------------------------------------------------------------------------
const BALL_GEO = new THREE.SphereGeometry(BALL_R, 32, 24);
const balls = new Map();   // id -> { mesh, x, y, color, squashAt, squashK, char }
function ballFor(id, color) {
  let b = balls.get(id);
  if (!b) {
    const mesh = new THREE.Mesh(BALL_GEO, new THREE.MeshStandardMaterial({ color: color || '#ffffff', map: TEX.dimples, bumpMap: TEX.dimples, bumpScale: 0.004, roughness: 0.28, metalness: 0.02 }));
    mesh.castShadow = true; scene.add(mesh);
    b = { mesh, x: 0, y: 0, color: color || '#ffffff', squashAt: -9, squashK: 0 };
    balls.set(id, b);
  }
  if (color) { b.color = color; b.mesh.material.color.set(color); }
  return b;
}

// ---- Particles: one pool of little spheres for dust, splashes, sparks and confetti --------------
// three.js r128 sizes an instanced mesh's colour list by however many instances it has the first
// time a colour is set; draw more than that later and many phones' graphics drop the whole draw
// (the aim dots simply vanish). So every colour list is made full size up front.
function fullColours(mesh, count) {
  mesh.instanceColor = new THREE.InstancedBufferAttribute(new Float32Array(count * 3).fill(1), 3);
  mesh.instanceColor.setUsage(THREE.DynamicDrawUsage);
}
let MAXP = 600;
const pMesh = new THREE.InstancedMesh(new THREE.SphereGeometry(1, 6, 5), new THREE.MeshBasicMaterial({ color: '#ffffff' }), 600);
pMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage); pMesh.frustumCulled = false; pMesh.count = 0; scene.add(pMesh);
fullColours(pMesh, 600);
const parts = [];
const tmpO = new THREE.Object3D(), tmpC = new THREE.Color(), tmpV = new THREE.Vector3(), UP = new THREE.Vector3(0, 1, 0);
function emit(n, at, opt) {
  if (reduceMotion) n = Math.ceil(n / 3);
  const r = Math.random;
  for (let i = 0; i < n && parts.length < MAXP; i++) {
    const a = r() * TAU, sp = opt.speed * (0.4 + r() * 0.8), up = opt.up * (0.5 + r() * 0.8);
    parts.push({
      x: at.x + (r() - 0.5) * (opt.spread || 0), y: at.y + (r() - 0.5) * (opt.spreadY || 0), z: at.z + (r() - 0.5) * (opt.spread || 0),
      vx: Math.cos(a) * sp, vy: up, vz: Math.sin(a) * sp,
      life: 0, max: opt.life * (0.6 + r() * 0.8), size: opt.size * (0.6 + r() * 0.8), g: opt.gravity ?? 9, drag: opt.drag ?? 1.5,
      color: opt.colors[Math.floor(r() * opt.colors.length)], grow: opt.grow || 0, spinR: opt.swirl || 0, cx: at.x, cz: at.z, ang: a, rad: opt.radius || 0, stretch: opt.stretch || 0,
    });
  }
}
function stepParticles(dt) {
  for (let i = parts.length - 1; i >= 0; i--) {
    const p = parts[i]; p.life += dt;
    if (p.life >= p.max) { parts.splice(i, 1); continue; }
    if (p.spinR) { p.ang += p.spinR * dt; p.rad = Math.max(0, p.rad - dt * 0.6); p.x = p.cx + Math.cos(p.ang) * p.rad; p.z = p.cz + Math.sin(p.ang) * p.rad; p.y += p.vy * dt; continue; }
    p.vy -= p.g * dt; const k = Math.exp(-p.drag * dt); p.vx *= k; p.vz *= k; p.vy *= p.g ? 1 : k;
    p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
  }
  pMesh.count = parts.length;
  parts.forEach((p, i) => {
    const t = p.life / p.max, s = p.size * (p.grow ? 1 + t * p.grow : 1) * (1 - t * t);
    tmpO.position.set(p.x, p.y, p.z); tmpO.scale.setScalar(Math.max(0.001, s));
    if (p.stretch) { const v = tmpV.set(p.vx, p.vy, p.vz), sp = v.length(); if (sp > 0.01) { tmpO.quaternion.setFromUnitVectors(UP, v.divideScalar(sp)); tmpO.scale.y *= 1 + sp * p.stretch; } } else tmpO.quaternion.identity();
    tmpO.updateMatrix();
    pMesh.setMatrixAt(i, tmpO.matrix); pMesh.setColorAt(i, tmpC.set(p.color));
  });
  pMesh.instanceMatrix.needsUpdate = true; pMesh.instanceColor.needsUpdate = true;
}
// The trail behind a ball in play and the aim guide: rows of dots (WebGL lines are one pixel wide).
function dots(count, r) {
  const m = new THREE.InstancedMesh(new THREE.SphereGeometry(r, 10, 8), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.85 }), count);
  m.count = 0; m.frustumCulled = false; fullColours(m, count); scene.add(m); return m;
}
const trail = dots(90, 0.07), guide = dots(90, 0.075);
function setDots(mesh, pts, scaleOf, colorOf) {
  tmpO.quaternion.identity();
  mesh.count = Math.min(pts.length, mesh.instanceMatrix.count);
  for (let i = 0; i < mesh.count; i++) {
    tmpO.position.copy(pts[i]); tmpO.scale.setScalar(scaleOf ? scaleOf(i, mesh.count) : 1); tmpO.updateMatrix(); mesh.setMatrixAt(i, tmpO.matrix);
    if (colorOf && mesh.instanceColor) mesh.setColorAt(i, tmpC.set(colorOf(i, mesh.count)));
  }
  mesh.instanceMatrix.needsUpdate = true; if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
}
const guideShadow = (() => {
  const m = new THREE.InstancedMesh(new THREE.CircleGeometry(0.075, 12).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#10301a', transparent: true, opacity: 0.35, depthWrite: false }), 90);
  m.count = 0; m.frustumCulled = false; scene.add(m); return m;
})();
const aimRing = new THREE.Mesh(new THREE.RingGeometry(0.26, 0.36, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffd23f', transparent: true, depthWrite: false }));
aimRing.visible = false; scene.add(aimRing);
const wallRing = new THREE.Mesh(new THREE.RingGeometry(0.3, 0.42, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffd23f', transparent: true }));
wallRing.visible = false; scene.add(wallRing);

// The cartoon plop: a bloop of water swells, bursts into big round drops, thick rings roll out and a PLOP! floats up.
function splash(at, y, scale) {
  const base = new THREE.Vector3(at.x, y, at.z);
  const dome = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 12, 0, TAU, 0, Math.PI / 2), WATERY.clone());
  addTransient(dome, 0.45, (k, o) => { const s = (0.25 + k * 0.4) * scale; o.scale.set(s, Math.sin(k * Math.PI) * 0.5 * scale + 0.01, s); o.position.copy(base); o.material.opacity = 0.9 * (1 - k * 0.6); });
  setTimeoutT(0.3, () => emit(Math.round(10 * scale), base.clone().setY(base.y + 0.3), { speed: 1.6 * scale, up: 3.4 * scale, life: 0.8, size: 0.11, colors: ['#ffffff', '#c9ecff'], gravity: 10 }));
  for (let i = 0; i < 3; i++) ringFx(base, base.y + 0.015, 0.2, 1.9 * scale, 1.0, 0.2 + i * 0.22, 0.16, i ? '#ffffff' : '#bfe9ff');
  const word = textSprite('PLOP!');
  addTransient(word, 1.3, (k, o) => { o.position.set(base.x, base.y + 0.8 + k * 1.2, base.z); const s = (k < 0.15 ? k / 0.15 : 1) * scale; o.scale.set(2.2 * s, 1.1 * s, 1); o.material.opacity = k > 0.7 ? (1 - k) / 0.3 : 1; });
}

// Something solid was hit: it reacts.
let lastBump = { c: null, t: -9 };
function bumped(c, at) {
  if (lastBump.c === c && W.t - lastBump.t < 0.25) return;
  lastBump = { c, t: W.t };
  c.obj.userData.shakeAt = W.t;
  if (c.kind === 'rock' || c.kind === 'stone') emit(10, at, { speed: 1.2, up: 1.2, life: 0.5, size: 0.04, colors: ['#bdb6a6', '#8d8a82', '#e2dccd'], gravity: 6 });
  if (c.kind === 'marker') emit(6, at, { speed: 1, up: 1, life: 0.4, size: 0.035, colors: ['#ffffff', '#e5484d'], gravity: 6 });
  if (c.kind === 'eye') { W.sideEyeAt = -9; c.obj.userData.blinkAt = W.t; toast('Ow!', 700); }
  if (c.kind === 'palm') {
    // A coconut drops, bounces once and rolls away.
    const top = new THREE.Vector3(); c.obj.userData.crown?.getWorldPosition(top);
    if (!top.y) top.set(wx(c.x), 2.6, wz(c.y));
    const nut = new THREE.Mesh(new THREE.SphereGeometry(0.11, 12, 10), new THREE.MeshStandardMaterial({ color: '#5a3b1e', roughness: 0.8 }));
    nut.castShadow = true;
    const dir = Math.random() * TAU, v0 = 0.7;
    addTransient(nut, 2.4, (k, o) => {
      const t = k * 2.4, fallT = Math.sqrt(2 * (top.y - 0.11) / 9.8);
      if (t < fallT) o.position.set(top.x, top.y - 4.9 * t * t, top.z);
      else { const u = t - fallT, hop = Math.max(0, 1.6 * u - 4.9 * u * u); o.position.set(top.x + Math.cos(dir) * v0 * u, 0.11 + hop, top.z + Math.sin(dir) * v0 * u); o.rotation.z = -u * 6; }
      if (k > 0.85) { o.material.transparent = true; o.material.opacity = (1 - k) / 0.15; }
    });
    emit(8, top.clone(), { speed: 0.8, up: 0.5, life: 0.8, size: 0.05, colors: ['#3d8f3f', '#5cbf4a'], gravity: 4 });
  }
}

// Everything that moves on its own: flames, portals, flag, grass, clouds, birds, sea, foam and ponds.
function ambient(t, dt, focus) {
  moveSea(t);
  for (const f of fx.fires) { const k = 1 + Math.sin(t * 9 + f.userData.phase) * 0.2; f.scale.set(1 / Math.sqrt(k), k, 1 / Math.sqrt(k)); }
  if (fx.fires.length && Math.random() < dt * 14) {
    const f = fx.fires[Math.floor(Math.random() * fx.fires.length)], w = f.getWorldPosition(new THREE.Vector3());
    emit(1, w.setY(0.5), { speed: 0.2, up: 1.6, life: 1.2, size: 0.035, colors: ['#ffd23f', '#ff8a1f'], gravity: -0.4, drag: 0.5 });
  }
  if (fx.fireLight) fx.fireLight.intensity = 1.1 + Math.sin(t * 13) * 0.25 + Math.sin(t * 7.3) * 0.2;
  for (const p of fx.portals) {
    if (p.userData.ring) { p.scale.setScalar(1 + Math.sin(t * 3) * 0.05); continue; }
    p.rotation.y = -t * 1.6;
    if (Math.random() < dt * 8) { const w = p.getWorldPosition(new THREE.Vector3()); emit(1, w.setY(0.08), { speed: 0, up: 0.5, life: 1, size: 0.035, colors: ['#b07cff', '#8ef0ff'], swirl: 4, radius: 0.7, gravity: 0 }); }
  }
  if (flag) {
    const pa = flag.geometry.attributes.position;
    for (let i = 0; i < pa.count; i++) pa.setZ(i, Math.sin(pa.getX(i) * 3.2 - t * 5) * 0.09 * pa.getX(i));
    pa.needsUpdate = true;
    // The flag jumps up for a ball in the cup.
    const k = st.play && st.play.holedAt ? t - st.play.holedAt : 9;
    flagPole.position.y = k > 0.5 && k < 1.6 ? Math.sin((k - 0.5) / 1.1 * Math.PI) * 0.9 : 0;
    cupRing.material.color.set(k < 2.5 ? '#ffd23f' : '#ffffff');
  }
  for (const tf of fx.tufts) tf.rotation.z = Math.sin(t * (tf.userData.palm ? 0.9 : 1.8) + tf.userData.phase) * (tf.userData.palm ? 0.03 : 0.12);
  if (clouds.visible) for (const c of clouds.children) { c.userData.a += c.userData.speed * dt; c.position.x = Math.cos(c.userData.a) * c.userData.d; c.position.z = Math.sin(c.userData.a) * c.userData.d; }
  if (gulls.visible) gulls.children.forEach((g, i) => {
    const a = t * 0.25 + g.userData.phase, r = g.userData.rad;
    g.position.set(Math.cos(a) * r, 9 + i * 1.2 + Math.sin(t + i) * 0.4, Math.sin(a) * r - 4);
    g.rotation.y = -a; const flap = Math.sin(t * 7 + i) * 0.5;
    g.userData.l.rotation.z = flap; g.userData.r.rotation.z = -flap;
  });
  for (const f of fx.ripples) {
    if (f.userData.foam) { f.material.opacity = 0.35 + Math.sin(t * 1.6) * 0.15; continue; }
    const p = f.userData.pond, k = ((t * 0.45 + f.userData.phase) % 1);
    f.scale.set(p[2] * S * k, 1, p[3] * S * k); f.material.opacity = 0.5 * (1 - k);
  }
  for (const [wall, at] of W.wobble) {
    const k = (t - at) / 0.6;
    if (k >= 1) { wall.scale.set(1, 1, 1); W.wobble.delete(wall); continue; }
    const s = Math.sin(k * 30) * 0.08 * (1 - k); wall.scale.set(1 + s, 1 - s, 1 + s);
  }
  // The eyes watch the ball; when one stops on them, a long look to the side.
  for (const e of fx.eyes) {
    const to = focus.clone().sub(e.position);
    let yaw = Math.atan2(to.x, to.z), pitch = clamp(-Math.atan2(to.y, Math.hypot(to.x, to.z)), -0.6, 0.5);
    if (t - W.sideEyeAt < 2.4) { yaw = -Math.PI / 2 + Math.sin((t - W.sideEyeAt) * 1.5) * 0.15; pitch = 0.1; }
    e.rotation.set(0, yaw, 0);
    e.userData.iris.rotation.x = pitch * 0.8;
    const blink = t - (e.userData.blinkAt ?? -9) < 0.35 || Math.sin(t * 0.7) > 0.985;
    e.userData.lid.rotation.x = -0.35 + (t - W.sideEyeAt < 2.4 ? 0.4 : 0) + (blink ? 1.1 : 0);
  }
  // Bent plants spring back; struck things shake.
  for (const p of PLANTS) {
    const k = t - (p.bendAt ?? -9); if (k > 1.6) continue;
    const tilt = 0.7 * Math.exp(-k * 3.5) * Math.cos(k * 12);
    p.obj.rotation.x = Math.sin(p.dir) * tilt; p.obj.rotation.z = -Math.cos(p.dir) * tilt;
  }
  for (const c of COLLIDERS) {
    const k = t - (c.obj.userData.shakeAt ?? -9); if (k > 1.2) continue;
    const s = Math.sin(k * 30) * Math.exp(-k * 4);
    if (c.kind === 'palm') c.obj.rotation.z = s * 0.05; else if (c.kind === 'marker') c.obj.rotation.z = s * 0.25; else if (c.kind === 'rock' || c.kind === 'stone') c.obj.position.y = 0.04 + Math.abs(s) * 0.02;
  }
  updateStatue(t);
}

// ---- State --------------------------------------------------------------------------------------
const st = {
  key: null, hole: null, num: 1,
  turn: null, me: 'm', meDone: false, canShoot: false, noGuide: false,
  aim: 0,            // which way you face: kept from turn to turn, never turned for you
  aims: {},          // your aim on each hole, so it's still there after the others' shots
  zoom: 1,           // pinch: right out is the map of the hole
  mode: 'roll', power: 0.5, sliding: false, slideT: 0,
  play: null,        // the shot on screen: { id, frames, events, t, next, lostAt, lostKind, holedAt }
  pending: null,     // a state that came in while a shot was playing
  overviewUntil: 0,
  fast: false,
  trail: [],
};

function myBall() { const b = balls.get(st.me); return b && b.mesh.visible ? b : null; }
function aimAtFlag() { const b = myBall(); if (b && st.hole) st.aim = Math.atan2(st.hole.cup[1] - b.y, st.hole.cup[0] - b.x); }
/** Whether you can aim and shoot now. */
function myAim() { return !!st.hole && st.canShoot && st.turn === st.me && !st.play && W.t > st.overviewUntil && !!myBall(); }
/** Whether you can turn to look round: always, on your own ball, until you've finished the hole. */
function canLook() { return !!st.hole && !st.meDone && !!myBall() && !(st.play && st.play.id === st.me) && W.t > st.overviewUntil; }

function holeKind(h) {
  if ((h.zones || []).length) return 'Sticker hole';
  const n = h.islands.length;
  return n > 1 ? `${n} islands` : 'One island';
}
function titleCard(num, h) {
  const el = document.getElementById('titleCard');
  el.querySelector('b').textContent = `Hole ${num}`;
  el.querySelector('span').textContent = `Par ${h.par} · ${holeKind(h)}`;
  el.classList.remove('on'); void el.offsetWidth; el.classList.add('on');
}
function newHole(key, h, num, flyOver) {
  st.key = key; st.hole = h; st.num = num || st.num;
  buildHole(h, st.num);
  st.overviewUntil = flyOver && !reduceMotion ? W.t + 3.2 : 0;
  st.zoom = 1;
  if (flyOver) titleCard(st.num, h);
}

function applyState(s) {
  if (s.key !== st.key) newHole(s.key, s.hole, s.num, true);
  const seen = new Set();
  for (const b of s.balls) {
    const ball = ballFor(b.id, b.color);
    ball.x = b.x; ball.y = b.y; ball.mesh.visible = true; ball.mesh.scale.setScalar(1); ball.mesh.material.color.set(ball.color);
    seen.add(b.id);
  }
  for (const [id, b] of balls) if (!seen.has(id)) b.mesh.visible = false;
  st.turn = s.turn; st.me = s.me || 'm'; st.meDone = !!s.meDone; st.canShoot = !!s.canShoot; st.noGuide = !!s.noGuide;
  // Your aim on this hole: where you left it, or at the flag the first time.
  if (st.aims[st.key] == null && myBall()) { aimAtFlag(); st.aims[st.key] = st.aim; }
  if (st.aims[st.key] != null) st.aim = st.aims[st.key];
  document.getElementById('slider').classList.toggle('off', !(st.canShoot && st.turn === st.me));
  st.trail = [];
  hint();
}

window.golf = {
  state(s) { if (st.play) st.pending = s; else applyState(s); },
  play(p) {
    // A shot on a hole not up yet (it finished the last one): show that hole first.
    if (p.key !== st.key) newHole(p.key, p.hole, p.num, false);
    const ball = ballFor(p.id, p.color);
    ball.mesh.visible = true; ball.mesh.scale.setScalar(1); ball.mesh.material.color.set(ball.color);
    const [x, y] = p.frames[0];
    ball.x = x; ball.y = y;
    st.play = { id: p.id, frames: p.frames, events: p.events || [], t: 0, next: 0, lostAt: 0, lostKind: null, holedAt: 0 };
    st.trail = []; st.fast = false; st.overviewUntil = 0; W.streakUntil = 0;
    document.getElementById('slider').classList.add('off');
    // Off it goes: a puff of dust for a jump, a few blades of grass for a roll.
    const jump = p.frames.length > 4 && p.frames[4][2] > 0.05, at = new THREE.Vector3(wx(x), 0.05, wz(y));
    ball.squashAt = W.t; ball.squashK = jump ? 0.35 : 0.12;
    if (jump) emit(16, at, { speed: 1.6, up: 1.4, life: 0.6, size: 0.07, colors: ['#c9b48a', '#e2d3ae', '#a8946a'], gravity: 3, spread: 0.2 });
    else emit(6, at, { speed: 0.8, up: 0.6, life: 0.4, size: 0.05, colors: ['#7cc96b', '#4ea953'], gravity: 5 });
    hint();
  },
};

let toastTimer = 0;
function toast(text, ms = 1600) {
  const el = document.getElementById('toast');
  el.textContent = text; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, ms);
}
function banner(text) { const el = document.getElementById('banner'); el.textContent = text; el.classList.remove('on'); void el.offsetWidth; el.classList.add('on'); }
function hint() {
  const el = document.getElementById('hint');
  const text = st.play ? 'Tap to speed up' : myAim() ? (st.zoom > 2.1 ? 'Map of the hole · Pinch in to go back' : 'Swipe to turn · Pinch to zoom · Slide up to shoot') : canLook() ? 'Swipe to look round' : '';
  if (el.textContent !== text) el.textContent = text;
}

// ---- A shot playing -------------------------------------------------------------------------------
/** Where the ball in play is now: course and world position, height, and how it's moving per frame. */
function playFrame() {
  const p = st.play, last = p.frames.length - 1;
  const i = Math.min(last, Math.floor(p.t)), j = Math.min(last, i + 1), k = Math.min(1, p.t - i);
  const a = p.frames[i], b = p.frames[j];
  const x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, h = a[2] + (b[2] - a[2]) * k;
  const back = p.frames[Math.max(0, i - 4)];
  return { pos: new THREE.Vector3(wx(x), Math.max(h, WATER_Y) + BALL_R, wz(y)), dx: x - back[0], dy: y - back[1], vx: b[0] - a[0], vy: b[1] - a[1], vh: b[2] - a[2], x, y, h, i };
}
function nearestWall(x, y) {
  let best = null, bd = 1e9;
  for (const w of fx.walls) {
    const b = w.userData.box, dx = Math.max(b[0] - x, 0, x - b[2]), dy = Math.max(b[1] - y, 0, y - b[3]), d = Math.hypot(dx, dy);
    if (d < bd) { bd = d; best = w; }
  }
  return bd < 0.08 ? best : null;
}
function nearestCollider(x, y) {
  let best = null, bd = 1e9;
  for (const c of COLLIDERS) { const d = Math.hypot(c.x - x, c.y - y) - c.r; if (d < bd) { bd = d; best = c; } }
  return bd < 0.06 ? best : null;
}
const SAYS = {
  sea: 'Splash! Back to the tee', water: 'Splash! Back to the tee', fire: 'This is fine. Back to the tee',
  stonks: 'Stonks!', portal: 'Galaxy brain!',
};
/** Something happens to the ball in play: the sound (in the app) and the picture. */
function react(what, f) {
  const p = st.play, ball = balls.get(p.id), at = f.pos, mine = p.id === st.me;
  bridge.event(what);
  if (SAYS[what]) toast(SAYS[what], 1300);
  switch (what) {
    case 'bump': { const c = nearestCollider(f.x, f.y); if (c) bumped(c, at); break; }
    case 'land': {
      ball.squashAt = W.t; ball.squashK = 0.3;
      emit(12, at.clone().setY(0.04), { speed: 1.4, up: 1, life: 0.55, size: 0.065, colors: ['#c9b48a', '#e2d3ae', '#8fbf6a'], gravity: 3, spread: 0.15 });
      break;
    }
    case 'wall': {
      const w = nearestWall(f.x, f.y);
      if (w) W.wobble.set(w, W.t);
      emit(10, at, { speed: 2.2, up: 1.6, life: 0.35, size: 0.04, colors: ['#ffd23f', '#ffffff', '#ff8a3d'], gravity: 8 });
      wallRing.position.copy(at); wallRing.userData.at = W.t; wallRing.visible = true;
      break;
    }
    case 'sea': case 'water':
      p.lostAt = W.t; p.lostKind = what;
      splash(at, what === 'sea' ? WATER_Y + 0.06 : 0.04, what === 'sea' ? 1.25 : 0.75);
      break;
    case 'fire':
      p.lostAt = W.t; p.lostKind = 'fire';
      emit(26, at, { speed: 1.4, up: 3.2, life: 1, size: 0.06, colors: ['#ffd23f', '#ff8a1f', '#ff4f1f'], gravity: -1, drag: 2 });
      emit(10, at, { speed: 0.4, up: 1.2, life: 1.6, size: 0.14, colors: ['#5a5a5a', '#7a7a7a'], gravity: -0.5, drag: 1, grow: 1.5 });
      break;
    case 'stonks':
      emit(30, at, { speed: 2, up: 2.5, life: 0.7, size: 0.06, colors: ['#3ddc84', '#ffd23f', '#ffffff'], gravity: 4 });
      W.streakUntil = W.t + 0.9;
      break;
    case 'portal': {
      st.trail = [];
      const from = p.frames[Math.max(0, f.i - 1)], to = p.frames[f.i];
      emit(24, new THREE.Vector3(wx(from[0]), 0.2, wz(from[1])), { speed: 0, up: 1.2, life: 0.8, size: 0.06, colors: ['#b07cff', '#8ef0ff', '#ffffff'], swirl: 7, radius: 0.7, gravity: 0 });
      emit(24, new THREE.Vector3(wx(to[0]), 0.2, wz(to[1])), { speed: 2.4, up: 1.5, life: 0.6, size: 0.06, colors: ['#b07cff', '#8ef0ff', '#fff6b0'], gravity: 3 });
      W.popAt = W.t;
      break;
    }
    case 'holed':
      p.holedAt = W.t; p.confetti = false;
      banner('In the cup!');
      break;
    case 'eyes':
      W.sideEyeAt = W.t;
      toast(mine ? 'Hmm. Sure. No aim line next shot' : 'Hmm. Sure.', 1800);
      break;
    case 'kylian':
      toast(mine ? 'Kylian takes your next turn' : 'Kylian takes their next turn', 2200);
      emit(30, at.clone(), { speed: 1.2, up: 2.4, life: 1.2, size: 0.05, colors: ['#ffd23f', '#fff3b0', '#e8a317'], gravity: 2, spread: 0.5 });
      startStatue(f.x, f.y);
      break;
  }
}
/** The shot on screen moves on by [dt] seconds; its events fire as it gets to them. */
function stepPlay(dt) {
  const p = st.play;
  if (p.lostAt || p.holedAt) {
    const wait = p.lostAt ? LOST_MS / 1000 : 2.4;
    if (W.t - (p.lostAt || p.holedAt) > wait) endPlay();
    return;
  }
  p.t += dt * 60 * (st.fast ? 3 : 1);
  const last = p.frames.length - 1;
  const i = Math.min(last, Math.floor(p.t));
  while (p.next < p.events.length && p.events[p.next][0] <= i) {
    const what = p.events[p.next][1];
    p.next++;
    const f = playFrame(); f.i = Math.min(last, p.events[p.next - 1][0]);
    react(what, f);
  }
  if (i >= last && !p.lostAt && !p.holedAt) endPlay();
}
function endPlay() {
  const p = st.play;
  st.play = null; st.trail = [];
  bridge.event('played');
  const b = balls.get(p.id);
  if (b) { b.mesh.scale.setScalar(1); b.mesh.material.color.set(b.color); }
  if (st.pending) { const s = st.pending; st.pending = null; applyState(s); } else if (p) {
    // Nothing new from the app yet: leave the ball where the shot ended.
    const f = p.frames[p.frames.length - 1];
    if (b) { b.x = f[0]; b.y = f[1]; }
  }
  hint();
}

/** The balls: where they are, how they turn, squash, stretch, char and sink. */
function placeBalls(dt) {
  for (const [id, b] of balls) {
    if (st.play && st.play.id === id) continue;
    b.mesh.position.set(wx(b.x), BALL_R, wz(b.y));
    const k = (W.t - b.squashAt) / 0.32, s = k < 1 ? Math.sin(k * Math.PI) * b.squashK * (1 - k) : 0;
    b.mesh.scale.set(1 + s * 0.6, 1 - s, 1 + s * 0.6);
  }
  if (!st.play) return;
  const p = st.play, b = balls.get(p.id), f = playFrame(), pos = f.pos.clone();
  let sx = 1, sy = 1;
  const k = (W.t - b.squashAt) / 0.32;
  if (k < 1) { const s = Math.sin(k * Math.PI) * b.squashK * (1 - k); sy = 1 - s; sx = 1 + s * 0.6; }
  if (f.h > 0.02) { sy *= 1 + Math.min(0.15, Math.abs(f.vh * 60) * 0.02); sx *= 0.97; }
  let scale = 1;
  if (W.t - W.popAt < 0.35) scale = Math.max(0.05, (W.t - W.popAt) / 0.35) ** 0.6;
  if (p.holedAt) {
    // Round the rim once, then down, then confetti.
    const kk = W.t - p.holedAt, c = new THREE.Vector3(wx(st.hole.cup[0]), 0, wz(st.hole.cup[1]));
    if (kk < 0.55) { const a = kk * 13, r = CUP * S * (1 - kk / 0.6) * 0.9; pos.set(c.x + Math.cos(a) * r, BALL_R - kk * 0.1, c.z + Math.sin(a) * r); }
    else { pos.copy(c); pos.y = Math.max(-0.4, BALL_R - (kk - 0.55) * 1.6); }
    if (kk > 0.5 && !p.confetti) { p.confetti = true; emit(70, c.clone().setY(0.4), { speed: 2.2, up: 5.5, life: 1.8, size: 0.06, colors: ['#ff5a7a', '#ffd23f', '#4cc2e6', '#6ad07a', '#9d7bff', '#ffffff'], gravity: 6, drag: 1.2 }); }
  }
  if (p.lostAt) {
    const kk = W.t - p.lostAt;
    if (p.lostKind === 'fire') pos.y = BALL_R; else pos.y -= Math.min(0.5, kk * 0.6);
    b.mesh.visible = kk < 0.9;
    // Charred black in the fire.
    b.mesh.material.color.set(b.color).lerp(tmpC.set('#2a2420'), p.lostKind === 'fire' ? Math.min(1, kk / 0.5) : 0);
  } else b.mesh.visible = true;
  b.mesh.position.copy(pos);
  b.mesh.scale.set(sx * scale, sy * scale, sx * scale);
  // Rolling without slipping: turn about the axis across the way it's going.
  if (!p.lostAt && !p.holedAt) {
    const sp = Math.hypot(f.vx, f.vy) * S * 60;
    if (sp > 1e-3) {
      const axis = new THREE.Vector3(f.vy, 0, -f.vx).normalize().multiplyScalar(-1);
      b.mesh.quaternion.premultiply(new THREE.Quaternion().setFromAxisAngle(axis, (f.h > 0.02 ? 0.5 : 1) * sp * dt * (st.fast ? 3 : 1) / BALL_R));
    }
    // Grass and flowers it brushes bend out of its way.
    if (f.h < 0.35) for (const pl of PLANTS) if (Math.hypot(f.x - pl.x, f.y - pl.y) < 0.028 && !(W.t - (pl.bendAt ?? -9) < 0.3)) { pl.bendAt = W.t; pl.dir = Math.atan2(f.vy, f.vx); }
    st.trail.push(pos.clone()); if (st.trail.length > 80) st.trail.shift();
  }
}
function drawTrail() {
  if (!st.play && st.trail.length) st.trail.splice(0, Math.max(1, Math.floor(st.trail.length * 0.08)));
  const tr = st.trail.filter((_, i) => i % 2 === 0);
  setDots(trail, tr, (i, n) => 0.25 + 0.75 * (i / n), (i, n) => (W.t < W.streakUntil ? '#3ddc84' : i > n * 0.7 ? '#ffffff' : '#fff1a8'));
  trail.material.opacity = 0.75;
  if (wallRing.visible) {
    const k = (W.t - wallRing.userData.at) / 0.4;
    wallRing.scale.setScalar(1 + k * 3); wallRing.material.opacity = Math.max(0, 1 - k);
    if (k >= 1) wallRing.visible = false;
  }
}

// ---- Camera -----------------------------------------------------------------------------------
// Each phone has its own camera, behind its own ball, facing where you aim; you watch the others'
// shots from there and swipe to look round. Your own shot is chased (in close for a splash, round
// the cup when it drops). Only once you've finished the hole does the camera follow the others.
const camPos = new THREE.Vector3(0, 30, 24), camLook = new THREE.Vector3();
const want = new THREE.Vector3(), wantLook = new THREE.Vector3();
let chase = [0, -1];
function behind(at, aim, z, target, look) {
  const dx = Math.cos(aim), dz = Math.sin(aim);
  target.set(at.x - dx * 5.2 * z, at.y + 3.1 * z, at.z - dz * 5.2 * z);
  look.set(at.x + dx * 4, at.y, at.z + dz * 4);
}
function chaseCamera() {
  const p = st.play, f = playFrame();
  if (p.holedAt) {
    const c = new THREE.Vector3(wx(st.hole.cup[0]), 0, wz(st.hole.cup[1])), a = (W.t - p.holedAt) * 0.5 - 0.9;
    want.set(c.x + Math.sin(a) * 3.2, 1.7, c.z + Math.cos(a) * 3.2); wantLook.copy(c).setY(0.6);
  } else if (p.lostAt) {
    const spot = new THREE.Vector3(f.pos.x, p.lostKind === 'sea' ? WATER_Y : 0.1, f.pos.z);
    if (p.lostKind !== 'fire') {
      // In close for the splash.
      const back = camPos.clone().sub(spot).setY(0).normalize();
      want.copy(spot).addScaledVector(back, p.lostKind === 'sea' ? 4.2 : 3.2); want.y = spot.y + (p.lostKind === 'sea' ? 2.6 : 1.7);
      wantLook.copy(spot).setY(spot.y + 0.5);
    } else { want.copy(camPos); wantLook.lerp(spot, 0.1); }
  } else {
    const sp = Math.hypot(f.dx, f.dy); if (sp > 1e-4) chase = [f.dx / sp, f.dy / sp];
    const near = Math.hypot(f.x - st.hole.cup[0], f.y - st.hole.cup[1]) < 0.25 && f.h <= 0.01;
    want.set(f.pos.x - chase[0] * (near ? 3.2 : 5.2), f.pos.y + (near ? 1.4 : 2.6 + Math.max(0, f.h) * 0.5), f.pos.z - chase[1] * (near ? 3.2 : 5.2));
    wantLook.set(f.pos.x + chase[0] * 2, f.pos.y, f.pos.z + chase[1] * 2);
  }
}
function placeCamera(dt, instant) {
  if (!st.hole) return;
  const mine = myBall();
  if (W.t < st.overviewUntil) {
    // The fly-over: high above, circling slowly, the whole hole in view.
    const a = W.t * 0.25, k = (st.hole.len || 1.6) / 1.6;
    want.set(Math.sin(a) * 9 * k, 21 * k, (14 + Math.cos(a) * 3) * k);
    wantLook.set(0, -1, -1);
  } else if (st.play && (st.play.id === st.me || st.meDone || !mine)) {
    chaseCamera();
  } else if (mine && !st.meDone) {
    // Your own camera: behind your ball, facing your aim. Pinch out rises to the map.
    behind(new THREE.Vector3(wx(mine.x), BALL_R, wz(mine.y)), st.aim, Math.min(st.zoom, 1.6), want, wantLook);
    const tilt = clamp((st.zoom - 1.6) / 0.8, 0, 1);
    if (tilt > 0) { want.lerp(MAP_POS, tilt); wantLook.lerp(MAP_LOOK, tilt); }
  } else if (st.turn && balls.get(st.turn)?.mesh.visible) {
    // You've finished: behind whoever's turn it is, looking up the hole.
    const b = balls.get(st.turn);
    behind(new THREE.Vector3(wx(b.x), BALL_R, wz(b.y)), Math.atan2(st.hole.cup[1] - b.y, st.hole.cup[0] - b.x), 1.2, want, wantLook);
  } else {
    want.copy(MAP_POS); wantLook.copy(MAP_LOOK);
  }
  const k = instant ? 1 : 1 - Math.exp(-dt * (ptr.kind === 'look' || pinch.on ? 18 : 4.2));
  camPos.lerp(want, k); camLook.lerp(wantLook, k);
  camera.position.copy(camPos);
  // The statue's thump shakes the view, less the further away you are.
  const sk = W.t - W.shakeAt; if (sk < 0.45) camera.position.y += Math.sin(sk * 60) * 0.06 * (1 - sk / 0.45) * (W.shakeK ?? 1);
  camera.lookAt(camLook);
}

// ---- The aim guide ------------------------------------------------------------------------------
// A short pointer at rest; while sliding, the path of the shot: dots evenly spaced from just in
// front of the ball, in a colour that warms with strength (yellow, orange, red), their shadows on
// the grass under a jump, and a ring where it lands (jump) or stops (roll).
function heatColor(power) {
  return new THREE.Color('#ffe14a').lerp(new THREE.Color('#ff7a1f'), clamp((power - 0.3) / 0.4, 0, 1)).lerp(new THREE.Color('#e5484d'), clamp((power - 0.75) / 0.25, 0, 1));
}
function hideGuide() { guide.count = 0; guide.instanceMatrix.needsUpdate = true; guideShadow.count = 0; guideShadow.instanceMatrix.needsUpdate = true; aimRing.visible = false; }
function drawGuide() {
  const b = myBall();
  if (!myAim() || st.noGuide || !b) return hideGuide();
  const pts = [], dx = Math.cos(st.aim), dy = Math.sin(st.aim);
  const START = BALL_R + 0.4, LEAD = 1.0;
  const along = (d, h = 0.08) => new THREE.Vector3(wx(b.x) + dx * d, h, wz(b.y) + dy * d);
  let end = null, small = 0;
  if (!st.sliding) for (let i = 1; i <= 9; i++) pts.push(along(0.22 * i));
  else if (st.mode === 'jump') {
    const T = 2 * JUMP_UP * st.power / GRAVITY, arc = [];
    let len = 0;
    for (let i = 0; i <= 160; i++) { const tt = T * i / 160; arc.push(along(JUMP_SPEED * st.power * tt * S, JUMP_UP * st.power * tt - GRAVITY * tt * tt / 2 + BALL_R)); if (i) len += arc[i].distanceTo(arc[i - 1]); }
    // A short hop gets its dots closer together, so there are always enough to see the arc.
    const gap = clamp(len / 9, 0.1, 0.3); let last = arc[0];
    for (const p of arc) if (p.distanceTo(arc[0]) >= BALL_R + 0.15 && p.distanceTo(last) >= gap) { pts.push(p); last = p; }
    end = along(JUMP_SPEED * st.power * T * S, 0.04);
  } else {
    const v = ROLL_SPEED * st.power, dist = v * v / (2 * ROLL_DECEL) * S;
    const gap = dist < START + LEAD ? 0.22 : 0.3;
    for (let s = START; s <= Math.max(dist, START + LEAD) + 1e-6; s += gap) { pts.push(along(s)); if (s > dist) small++; }
    end = along(dist, 0.04);
  }
  const col = st.sliding ? '#' + heatColor(st.power).getHexString() : '#ffffff';
  const firstSmall = pts.length - small;
  setDots(guide, pts, (i) => (i >= firstSmall ? 0.75 : 1) * (st.sliding ? 1.25 : 1) * (0.85 + 0.25 * Math.max(0, Math.sin(W.t * 6 - i * 0.7))), () => col);
  if (st.sliding && st.mode === 'jump') setDots(guideShadow, pts.map((q) => q.clone().setY(0.03)), (i, n) => 0.6 + 0.6 * (i / n));
  else { guideShadow.count = 0; guideShadow.instanceMatrix.needsUpdate = true; }
  aimRing.visible = !!end;
  if (end) { aimRing.position.copy(end); aimRing.material.color.set(col); aimRing.scale.setScalar(1 + Math.sin(W.t * 5) * 0.12); aimRing.material.opacity = 0.9; }
}

// ---- Loop -------------------------------------------------------------------------------------
function resize() {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!w || !h) return;
  renderer.setSize(w, h, false);
  camera.aspect = w / h; camera.updateProjectionMatrix();
}
let last = performance.now();
let sizedW = 0, sizedH = 0;
// On a phone that struggles, the extras turn down by themselves: fewer pixels, no clouds or gulls, fewer sparks.
const perf = { frames: 0, time: 0, done: false };
function watchSpeed(dt) {
  if (perf.done || !st.hole) return;
  perf.frames++; perf.time += dt;
  if (perf.frames < 120) return;
  perf.done = true;
  if (perf.time / perf.frames > 0.03) { renderer.setPixelRatio(1); resize(); clouds.visible = false; gulls.visible = false; MAXP = 250; }
}
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  W.t += dt;
  if (canvas.clientWidth !== sizedW || canvas.clientHeight !== sizedH) { sizedW = canvas.clientWidth; sizedH = canvas.clientHeight; resize(); }
  // A view with no room to draw in, for a few seconds after the hole came: say so.
  if (st.hole && (sizedW < 20 || sizedH < 20)) {
    st.flatSince = st.flatSince || now;
    if (now - st.flatSince > 3000 && !st.flatTold) { st.flatTold = true; bridge.event(`error the view is ${sizedW} x ${sizedH} pixels`); }
  } else st.flatSince = 0;
  if (st.hole) {
    if (st.play) stepPlay(dt);
    placeBalls(dt);
    drawTrail();
    const focus = st.play ? balls.get(st.play.id).mesh.position : (myBall()?.mesh.position || new THREE.Vector3());
    ambient(W.t, dt, focus);
    stepTimers(); stepTransients(); stepParticles(dt);
    drawGuide();
    placeCamera(dt, false);
    watchSpeed(dt);
  }
  renderer.render(scene, camera);
  requestAnimationFrame(frame);
}

// ---- Touch: swipe to turn, pinch to zoom, the side slider to shoot ----------------------------
const ptr = { kind: null, x0: 0, y0: 0, x: 0, y: 0, moved: false };
let lastTap = 0;
function local(e) { const r = canvas.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; }
canvas.addEventListener('pointerdown', (e) => {
  if (W.t < st.overviewUntil) st.overviewUntil = 0;   // a touch skips the fly-over
  if (pinch.on) { ptr.kind = null; return; }
  canvas.setPointerCapture(e.pointerId);
  const [x, y] = local(e);
  Object.assign(ptr, { x0: x, y0: y, x, y, moved: false });
  // Tapping your own shot speeds it up; someone else's you can still look round during.
  if (st.play && (st.play.id === st.me || st.meDone)) { st.fast = true; ptr.kind = null; return; }
  ptr.kind = 'look';
});
canvas.addEventListener('pointermove', (e) => {
  if (!ptr.kind) return;
  const [x, y] = local(e);
  const dx = x - ptr.x;
  ptr.x = x; ptr.y = y;
  if (Math.hypot(x - ptr.x0, y - ptr.y0) > 8) ptr.moved = true;
  if (ptr.moved && canLook()) { st.aim += dx * 0.007; st.aims[st.key] = st.aim; }
});
canvas.addEventListener('pointerup', () => {
  const kind = ptr.kind; ptr.kind = null;
  if (!kind || ptr.moved) return;
  if (st.play) { st.fast = true; return; }
  // A double tap faces the flag again.
  if (W.t - lastTap < 0.32 && canLook()) { lastTap = 0; st.zoom = 1; aimAtFlag(); st.aims[st.key] = st.aim; hint(); return; }
  lastTap = W.t;
});

const pinch = { on: false, fingers: new Map(), dist: 0 };
function spread() { const [a, b] = [...pinch.fingers.values()]; return Math.hypot(a[0] - b[0], a[1] - b[1]); }
canvas.addEventListener('pointerdown', (e) => {
  pinch.fingers.set(e.pointerId, [e.clientX, e.clientY]);
  if (pinch.fingers.size === 2) { pinch.on = true; pinch.dist = spread(); ptr.kind = null; }
}, true);
canvas.addEventListener('pointermove', (e) => {
  if (!pinch.fingers.has(e.pointerId)) return;
  pinch.fingers.set(e.pointerId, [e.clientX, e.clientY]);
  if (pinch.on && pinch.fingers.size === 2) {
    const d = spread();
    if (d > 0 && pinch.dist > 0) { st.zoom = clamp(st.zoom * pinch.dist / d, 0.55, 2.4); hint(); }
    pinch.dist = d;
  }
}, true);
for (const type of ['pointerup', 'pointercancel']) canvas.addEventListener(type, (e) => {
  pinch.fingers.delete(e.pointerId);
  if (pinch.fingers.size < 2) pinch.on = false;
}, true);

const slider = document.getElementById('slider'), knob = document.getElementById('knob'), fill = document.getElementById('fill'), powerTag = document.getElementById('power');
function setKnob(t) {
  knob.style.bottom = `calc(4px + ${clamp(t, 0, 1)} * (100% - 44px))`;
  slider.setAttribute('aria-valuenow', String(Math.round(clamp(t, 0, 1) * 100)));
  // The bar fills behind the knob: green for a roll, warming to orange and red for a jump.
  fill.style.height = `${clamp(t, 0, 1) * 100}%`;
  fill.style.background = t < JUMP_FROM ? 'rgba(62,155,79,.75)' : t < 0.8 ? 'rgba(255,170,61,.8)' : 'rgba(229,72,77,.85)';
}
function slideTo(e) {
  const r = slider.getBoundingClientRect();
  const t = clamp(1 - (e.clientY - r.top - 22) / (r.height - 44), 0, 1);
  setKnob(t);
  if (t < JUMP_FROM) { st.mode = 'roll'; st.power = Math.max(0.05, t / JUMP_FROM); } else { st.mode = 'jump'; st.power = 0.3 + 0.7 * (t - JUMP_FROM) / (1 - JUMP_FROM); }
  st.slideT = t;
  powerTag.textContent = `${st.mode === 'jump' ? 'Jump' : 'Roll'} ${Math.round(st.power * 100)}%`;
  powerTag.hidden = t <= 0.04;
}
slider.addEventListener('pointerdown', (e) => {
  if (W.t < st.overviewUntil) st.overviewUntil = 0;
  if (!myAim()) return;
  slider.setPointerCapture(e.pointerId);
  st.sliding = true; slideTo(e);
});
slider.addEventListener('pointermove', (e) => { if (st.sliding) slideTo(e); });
for (const type of ['pointerup', 'pointercancel']) slider.addEventListener(type, () => {
  if (!st.sliding) return;
  st.sliding = false; powerTag.hidden = true;
  // Let go to shoot; slid back to the bottom it's a change of mind.
  if (st.slideT > 0.04 && type === 'pointerup' && myAim()) {
    st.canShoot = false;
    slider.classList.add('off');
    bridge.shoot(st.mode, st.power, st.aim);
  }
  setKnob(0);
});

window.addEventListener('resize', resize);
resize();
requestAnimationFrame(frame);
bridge.ready();
