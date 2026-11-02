// Mini Golf in 3D: draws the hole, the balls and the shots the app sends, and takes your touches.
//
// The app (ui/screens/Golf3D.kt) talks to this page through window.golf:
//   golf.state({ key, hole, balls: [{ id, x, y, color }], turn, me, canShoot, noGuide })
//     what's on the course now; while a shot is playing it waits for the shot to finish.
//   golf.play({ key, hole, id, color, frames: [[x, y, h], ...], events: [[frame, what], ...] })
//     a shot to show, 60 frames a second, worked out by the app.
// and this page answers through window.Android:
//   ready()                      the page is loaded
//   shoot(kind, power, aim)      you let go of the slider: 'roll' or 'jump', 0..1, radians
//   event(what)                  a shot reached an event (for sounds), and "played" at its end
//
// Course units: the course is 1 wide, y down the hole; S world units to one course unit.

const Android = window.Android || { ready() {}, shoot() {}, event() {} };
const S = 10;
const CORNER = 0.04, CUP = 0.04;
const WATER_Y = -1.7;
const ROLL_SPEED = 2.4, ROLL_DECEL = 2.2;
const JUMP_SPEED = 0.6, JUMP_UP = 7, GRAVITY = 14;
const BALL_R = 0.2;
const JUMP_FROM = 0.55;   // the slider: below this a roll, above it a jump
const LOST_MS = 1100;
const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

let HALF = 1.6;           // half the hole's length: the course is centred on the world's origin
const wx = (x) => (x - 0.5) * S;
const wz = (y) => (y - HALF) * S;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
/** A small seeded random generator, so flames sit in the same places every time. */
function rng(seed) {
  return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

// ---- Scene ------------------------------------------------------------------------------------
const canvas = document.getElementById('view');
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
} catch (e) {
  document.body.insertAdjacentHTML('beforeend', '<div class="nogl">This phone can\'t show the 3D course.</div>');
  throw e;
}
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const scene = new THREE.Scene();
scene.background = new THREE.Color('#bfe6ff');
scene.fog = new THREE.Fog("#bfe6ff", 70, 170);
const camera = new THREE.PerspectiveCamera(52, 9 / 16, 0.1, 260);

scene.add(new THREE.HemisphereLight('#e8f6ff', '#3f6b3a', 0.9));
const sun = new THREE.DirectionalLight('#fff4dc', 0.95);
sun.position.set(7, 16, 5);
sun.castShadow = true;
sun.shadow.mapSize.set(1024, 2048);
Object.assign(sun.shadow.camera, { left: -12, right: 12, top: 18, bottom: -18, near: 1, far: 60 });
scene.add(sun);

// The sea under the islands, gently moving.
const seaGeo = new THREE.PlaneGeometry(120, 120, 48, 48);
seaGeo.rotateX(-Math.PI / 2);
const sea = new THREE.Mesh(seaGeo, new THREE.MeshPhongMaterial({ color: '#2f8fd2', shininess: 90, specular: '#cfefff' }));
sea.position.y = WATER_Y;
sea.receiveShadow = true;
scene.add(sea);
function moveSea(t) {
  const p = seaGeo.attributes.position;
  for (let i = 0; i < p.count; i++) p.setY(i, Math.sin(p.getX(i) * 0.55 + t * 1.1) * 0.07 + Math.cos(p.getZ(i) * 0.45 + t * 1.4) * 0.07);
  p.needsUpdate = true;
  seaGeo.computeVertexNormals();
}

function stripes() {
  const c = document.createElement('canvas'); c.width = 64; c.height = 64;
  const g = c.getContext('2d');
  g.fillStyle = '#5cbf5f'; g.fillRect(0, 0, 64, 64);
  g.fillStyle = '#4fae53'; g.fillRect(0, 0, 64, 32);
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(0.5, 0.5);
  return tex;
}
const GRASS = new THREE.MeshLambertMaterial({ map: stripes() });
const SIDE = new THREE.MeshLambertMaterial({ color: '#7b5a3c' });
const ROCK = new THREE.MeshLambertMaterial({ color: '#5f4a3a' });
const WOOD = new THREE.MeshLambertMaterial({ color: '#a8743f' });
const WOOD_DARK = new THREE.MeshLambertMaterial({ color: '#7a4f2a' });
const SAND = new THREE.MeshLambertMaterial({ color: '#f0d98c' });
const POND = new THREE.MeshPhongMaterial({ color: '#2a86c9', shininess: 120, specular: '#ffffff' });

function roundedShape(b, r) {
  const x0 = wx(b[0]), x1 = wx(b[2]), z0 = wz(b[1]), z1 = wz(b[3]), rr = r * S;
  const s = new THREE.Shape();
  // Drawn flat, then turned to lie on the ground: the shape's y is the world's -z.
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
  m.position.y = top - depth;
  m.castShadow = true; m.receiveShadow = true;
  return m;
}

const MAP_POS = new THREE.Vector3(), MAP_LOOK = new THREE.Vector3();
let course = null, flag = null;
const fires = [], portals = [];
function canvasTex(w, h, draw) { const cv = document.createElement('canvas'); cv.width = w; cv.height = h; draw(cv.getContext('2d'), w, h); const t = new THREE.CanvasTexture(cv); t.anisotropy = 4; return t; }
function decal(box, tex, y = 0.03) {
  const g = new THREE.PlaneGeometry((box[2] - box[0]) * S, (box[3] - box[1]) * S); g.rotateX(-Math.PI / 2);
  const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ map: tex, transparent: true }));
  m.position.set(wx((box[0] + box[2]) / 2), y, wz((box[1] + box[3]) / 2));
  return m;
}
function roundRect(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }
function roundedPanel(g, w, h, fill, edge, r = 24) { roundRect(g, 6, 6, w - 12, h - 12, r); g.fillStyle = fill; g.fill(); g.lineWidth = 10; g.strokeStyle = edge; g.stroke(); }
function caption(g, text, x, y, size) { g.font = `900 ${size}px Impact, 'Arial Black', sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.lineWidth = size * 0.22; g.strokeStyle = '#000'; g.strokeText(text, x, y); g.fillStyle = '#fff'; g.fillText(text, x, y); }
const kyImg = new Image();
kyImg.src = KYLIAN_SVG;

/** The sticker spots: a picture on the grass for each, and flames or a portal where they move. */
function buildZones(h, group) {
  fires.length = 0; portals.length = 0;
  for (const z of h.zones || []) {
    if (z.type === 'kylian') {
      const asp = (z.box[3] - z.box[1]) / (z.box[2] - z.box[0]);
      const tex = canvasTex(256, Math.round(256 * asp), (g, w, hh) => {
        roundedPanel(g, w, hh, '#6f6c78', '#e2bf55');
        const draw = () => { const side = Math.min(w * 0.64, hh - 70); g.drawImage(kyImg, (w - side) / 2, 12, side, side); };
        if (kyImg.complete && kyImg.naturalWidth) draw();
        g.fillStyle = '#c8102e'; g.fillRect(14, hh - 52, w - 28, 40); caption(g, 'KYLIAN', w / 2, hh - 32, 30);
      });
      // The picture may still be loading: draw it in when it arrives.
      if (!(kyImg.complete && kyImg.naturalWidth)) kyImg.addEventListener('load', () => { if (st.hole) buildHole(st.hole); }, { once: true });
      group.add(decal(z.box, tex));
    } else if (z.type === 'eyes') {
      group.add(decal(z.box, canvasTex(256, 300, (g, w, hh) => {
        roundedPanel(g, w, hh, '#efc29c', '#4a2c18');
        for (const cx of [w * 0.3, w * 0.7]) {
          g.beginPath(); g.ellipse(cx, hh * 0.42, 44, 30, 0, 0, Math.PI * 2); g.fillStyle = '#fff'; g.fill(); g.lineWidth = 6; g.strokeStyle = '#3a2416'; g.stroke();
          g.beginPath(); g.arc(cx + 18, hh * 0.44, 15, 0, Math.PI * 2); g.fillStyle = '#3a2416'; g.fill();
          g.beginPath(); g.ellipse(cx, hh * 0.42, 48, 34, 0, Math.PI, 0); g.fillStyle = '#dda077'; g.fill();
        }
        caption(g, 'HMM. SURE.', w / 2, hh * 0.82, 34);
      })));
    } else if (z.type === 'stonks') {
      const tex = canvasTex(256, 220, (g, w, hh) => {
        const gr = g.createLinearGradient(0, 0, w, hh); gr.addColorStop(0, '#0d2b6b'); gr.addColorStop(1, '#1f56c0');
        roundedPanel(g, w, hh, gr, '#ff9f1c');
        g.beginPath(); g.moveTo(w * 0.2, hh * 0.85); g.lineTo(w * 0.4, hh * 0.55); g.lineTo(w * 0.55, hh * 0.65); g.lineTo(w * 0.72, hh * 0.25);
        g.lineWidth = 16; g.strokeStyle = '#ff9f1c'; g.lineJoin = 'round'; g.lineCap = 'round'; g.stroke();
        g.beginPath(); g.moveTo(w * 0.72, hh * 0.08); g.lineTo(w * 0.86, hh * 0.32); g.lineTo(w * 0.6, hh * 0.3); g.closePath(); g.fillStyle = '#ff9f1c'; g.fill();
        caption(g, 'STONKS', w / 2, hh * 0.86, 30);
      });
      const m = decal(z.box, tex);
      // The picture's top points along the ramp's arrow.
      const dir = z.dir || [0, -1];
      m.rotation.y = Math.atan2(dir[0], -dir[1]) * -1;
      group.add(m);
    } else if (z.type === 'fire') {
      group.add(decal(z.box, canvasTex(256, 256, (g, w, hh) => {
        const gr = g.createRadialGradient(w / 2, hh / 2, 10, w / 2, hh / 2, w * 0.6); gr.addColorStop(0, 'rgba(255,140,40,0.95)'); gr.addColorStop(0.6, 'rgba(120,30,10,0.9)'); gr.addColorStop(1, 'rgba(60,20,10,0)');
        g.fillStyle = gr; g.fillRect(0, 0, w, hh);
      }), 0.02));
      const r = rng(Math.round(z.box[0] * 1000 + z.box[1] * 37)), area = (z.box[2] - z.box[0]) * (z.box[3] - z.box[1]);
      for (let k = 0; k < Math.round(area * 260); k++) {
        const outer = new THREE.Mesh(new THREE.ConeGeometry(0.16, 0.6, 8), new THREE.MeshBasicMaterial({ color: '#ff6a1f' }));
        const inner = new THREE.Mesh(new THREE.ConeGeometry(0.08, 0.36, 8), new THREE.MeshBasicMaterial({ color: '#ffd23f' }));
        inner.position.y = -0.1; outer.add(inner);
        outer.position.set(wx(z.box[0] + r() * (z.box[2] - z.box[0])), 0.3, wz(z.box[1] + r() * (z.box[3] - z.box[1])));
        outer.userData.phase = r() * 6; group.add(outer); fires.push(outer);
      }
    } else if (z.type === 'portal') {
      const tex = canvasTex(256, 256, (g, w) => {
        const gr = g.createRadialGradient(w / 2, w / 2, 4, w / 2, w / 2, w / 2); gr.addColorStop(0, '#fff6b0'); gr.addColorStop(0.3, '#b07cff'); gr.addColorStop(0.75, '#3a1a7a'); gr.addColorStop(1, 'rgba(58,26,122,0)');
        g.fillStyle = gr; g.fillRect(0, 0, w, w);
        g.strokeStyle = 'rgba(142,240,255,0.9)'; g.lineWidth = 6;
        for (let k = 0; k < 3; k++) { g.beginPath(); g.arc(w / 2, w / 2, w * (0.18 + k * 0.1), k, k + 4); g.stroke(); }
      });
      const m = new THREE.Mesh(new THREE.CircleGeometry(0.75, 40).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ map: tex, transparent: true }));
      m.position.set(wx(z.at[0]), 0.035, wz(z.at[1])); group.add(m); portals.push(m);
    }
  }
}

function buildHole(h) {
  if (course) {
    scene.remove(course);
    course.traverse((o) => { if (o.geometry) o.geometry.dispose(); });
  }
  const len = h.len || 1.6;
  HALF = len / 2;
  MAP_POS.set(0, 14 + len * 7, len * 2.2); MAP_LOOK.set(0, 0, -len * 0.38);
  Object.assign(sun.shadow.camera, { top: len * S / 2 + 2, bottom: -(len * S / 2 + 2) }); sun.shadow.camera.updateProjectionMatrix();
  course = new THREE.Group();
  for (const b of h.islands) {
    course.add(slab(roundedShape(b, CORNER), 0.35, 0, [GRASS, SIDE]));
    const inset = [b[0] + 0.02, b[1] + 0.02, b[2] - 0.02, b[3] - 0.02];
    course.add(slab(roundedShape(inset, CORNER), 1.0, -0.35, ROCK));
  }
  for (const b of h.sand || []) {
    const m = slab(roundedShape(b, 0.03), 0.04, 0.02, SAND);
    m.castShadow = false; course.add(m);
  }
  for (const p of h.water || []) {
    const g = new THREE.CircleGeometry(1, 40); g.rotateX(-Math.PI / 2);
    const m = new THREE.Mesh(g, POND); m.scale.set(p[2] * S, 1, p[3] * S); m.position.set(wx(p[0]), 0.02, wz(p[1]));
    course.add(m);
    const rim = new THREE.Mesh(new THREE.RingGeometry(1, 1.08, 40).rotateX(-Math.PI / 2), new THREE.MeshLambertMaterial({ color: '#cfe9d0' }));
    rim.scale.copy(m.scale); rim.position.copy(m.position); rim.position.y = 0.025; course.add(rim);
  }
  for (const b of h.bridges || []) {
    const along = (b[3] - b[1]) > (b[2] - b[0]);
    const w = (b[2] - b[0]) * S, d = (b[3] - b[1]) * S;
    const deck = new THREE.Mesh(new THREE.BoxGeometry(w, 0.14, d), WOOD);
    deck.position.set(wx((b[0] + b[2]) / 2), -0.07, wz((b[1] + b[3]) / 2)); deck.castShadow = deck.receiveShadow = true; course.add(deck);
    const n = Math.floor((along ? d : w) / 0.45);
    for (let i = 1; i < n; i++) {
      const seam = new THREE.Mesh(new THREE.BoxGeometry(along ? w : 0.04, 0.02, along ? 0.04 : d), WOOD_DARK);
      seam.position.set(along ? deck.position.x : wx(b[0]) + i * w / n, 0.005, along ? wz(b[1]) + i * d / n : deck.position.z);
      course.add(seam);
    }
    for (const side of [-1, 1]) {
      const post = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 1.6, 8), WOOD_DARK);
      post.position.set(deck.position.x + (along ? side * w / 2 : 0), -0.9, deck.position.z + (along ? 0 : side * d / 2));
      course.add(post);
    }
  }
  for (const b of h.walls || []) {
    const wall = new THREE.Mesh(new THREE.BoxGeometry((b[2] - b[0]) * S, 0.45, (b[3] - b[1]) * S), WOOD);
    wall.position.set(wx((b[0] + b[2]) / 2), 0.225, wz((b[1] + b[3]) / 2)); wall.castShadow = wall.receiveShadow = true; course.add(wall);
  }
  buildZones(h, course);
  // The tee mat, and the cup with its flag.
  const tee = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.03, 0.6), new THREE.MeshLambertMaterial({ color: '#e8f3ea' }));
  tee.position.set(wx(h.tee[0]), 0.015, wz(h.tee[1])); tee.receiveShadow = true; course.add(tee);
  const cupHole = new THREE.Mesh(new THREE.CircleGeometry(CUP * S, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#16201a' }));
  cupHole.position.set(wx(h.cup[0]), 0.012, wz(h.cup[1])); course.add(cupHole);
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.035, 2.6, 8), new THREE.MeshLambertMaterial({ color: '#f5f5f5' }));
  pole.position.set(wx(h.cup[0]), 1.3, wz(h.cup[1])); pole.castShadow = true; course.add(pole);
  const flagGeo = new THREE.PlaneGeometry(1.0, 0.62, 12, 3); flagGeo.translate(0.5, 0, 0);
  flag = new THREE.Mesh(flagGeo, new THREE.MeshLambertMaterial({ color: '#e5484d', side: THREE.DoubleSide }));
  flag.position.set(wx(h.cup[0]) + 0.03, 2.25, wz(h.cup[1])); flag.castShadow = true; course.add(flag);
  scene.add(course);
}
function waveFlag(t) {
  if (!flag) return;
  const p = flag.geometry.attributes.position;
  for (let i = 0; i < p.count; i++) p.setZ(i, Math.sin(p.getX(i) * 3.2 - t * 5) * 0.09 * p.getX(i));
  p.needsUpdate = true;
}

// Balls (one per player), a trail behind the ball in play, and the aim guide.
const BALL_GEO = new THREE.SphereGeometry(BALL_R, 28, 20);
const balls = new Map();   // id -> { mesh, x, y }
function ballFor(id, color) {
  let b = balls.get(id);
  if (!b) {
    const mesh = new THREE.Mesh(BALL_GEO, new THREE.MeshStandardMaterial({ color: color || '#ffffff', roughness: 0.35 }));
    mesh.castShadow = true; scene.add(mesh);
    b = { mesh, x: 0, y: 0 };
    balls.set(id, b);
  }
  if (color) b.mesh.material.color.set(color);
  return b;
}
// Lines in WebGL are one pixel wide, so the trail and the guide are rows of small dots.
function dots(count, r, opacity) {
  const m = new THREE.InstancedMesh(new THREE.SphereGeometry(r, 10, 8), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity }), count);
  m.count = 0; m.frustumCulled = false; scene.add(m);
  return m;
}
const trail = dots(80, 0.06, 0.7);
const guide = dots(40, 0.075, 0.95);
const dummy = new THREE.Object3D();
function setDots(mesh, pts) {
  mesh.count = Math.min(pts.length, mesh.instanceMatrix.count);
  for (let i = 0; i < mesh.count; i++) { dummy.position.copy(pts[i]); dummy.updateMatrix(); mesh.setMatrixAt(i, dummy.matrix); }
  mesh.instanceMatrix.needsUpdate = true;
}
const splash = new THREE.Mesh(new THREE.RingGeometry(0.3, 0.42, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true }));
splash.visible = false; scene.add(splash);

// ---- State --------------------------------------------------------------------------------------
const st = {
  key: null, hole: null,
  turn: null, me: 'm', canShoot: false, noGuide: false,
  aim: 0,            // which way you face: kept from turn to turn, never turned for you
  aims: {},          // your aim on each hole, so it's still there after the others' shots
  zoom: 1,           // pinch: right out is the map of the hole
  mode: 'roll', power: 0.5, sliding: false, slideT: 0,
  play: null,        // the shot on screen: { id, frames, events, t, next, lostAt, holedAt }
  pending: null,     // a state that came in while a shot was playing
  overviewUntil: 0,
  fast: false,
  trail: [],
};

function myBall() { return balls.get(st.me); }
function aimAtFlag() { const b = myBall(); if (b && st.hole) st.aim = Math.atan2(st.hole.cup[1] - b.y, st.hole.cup[0] - b.x); }
/** Whether you can aim and shoot now. */
function myAim() { return !!st.hole && st.canShoot && st.turn === st.me && !st.play && performance.now() > st.overviewUntil; }

function applyState(s) {
  const now = performance.now();
  const newHole = s.key !== st.key;
  if (newHole) {
    st.key = s.key; st.hole = s.hole;
    buildHole(s.hole);
    st.overviewUntil = reduceMotion ? 0 : now + 3200;
    st.zoom = 1;
  }
  const seen = new Set();
  for (const b of s.balls) {
    const ball = ballFor(b.id, b.color);
    ball.x = b.x; ball.y = b.y; ball.mesh.visible = true;
    seen.add(b.id);
  }
  for (const [id, b] of balls) if (!seen.has(id)) b.mesh.visible = false;
  st.turn = s.turn; st.me = s.me || 'm'; st.canShoot = !!s.canShoot; st.noGuide = !!s.noGuide;
  // Your aim on this hole: where you left it, or at the flag the first time.
  if (st.aims[st.key] == null && balls.has(st.me)) { aimAtFlag(); st.aims[st.key] = st.aim; }
  if (st.aims[st.key] != null) st.aim = st.aims[st.key];
  document.getElementById('slider').classList.toggle('off', !(st.canShoot && st.turn === st.me));
  st.trail = [];
  hint();
}

window.golf = {
  state(s) { if (st.play) st.pending = s; else applyState(s); },
  play(p) {
    if (p.key !== st.key) {
      // A shot on a hole not up yet (it finished the last one): show that hole first.
      st.key = p.key; st.hole = p.hole; buildHole(p.hole); st.overviewUntil = 0;
    }
    const ball = ballFor(p.id, p.color);
    ball.mesh.visible = true;
    const [x, y] = p.frames[0];
    ball.x = x; ball.y = y;
    st.play = { id: p.id, frames: p.frames, events: p.events || [], t: 0, next: 0, lostAt: 0, holedAt: 0 };
    st.trail = []; st.fast = false; st.overviewUntil = 0;
    document.getElementById('slider').classList.add('off');
    hint();
  },
};

let toastTimer = 0;
function toast(text, ms = 1600) {
  const el = document.getElementById('toast');
  el.textContent = text; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, ms);
}
function hint() {
  const el = document.getElementById('hint');
  const text = st.play ? 'Tap to speed up' : myAim() ? (st.zoom > 2.1 ? 'Map of the hole · Pinch in to go back' : 'Swipe to turn · Pinch to zoom · Slide up to shoot') : '';
  if (el.textContent !== text) el.textContent = text;
}

const SAYS = {
  sea: 'Splash! Back to the tee', water: 'Splash! Back to the tee', fire: 'This is fine. Back to the tee',
  stonks: 'Stonks!', portal: 'Galaxy brain!', holed: 'In the cup!', eyes: 'Hmm. Sure. No aim line next shot',
};
/** The shot on screen moves on by [dt] seconds; its events fire as it gets to them. */
function stepPlay(dt, now) {
  const p = st.play;
  if (p.lostAt || p.holedAt) {
    const wait = p.lostAt ? LOST_MS : 1400;
    if (now - (p.lostAt || p.holedAt) > wait) endPlay();
    return;
  }
  p.t += dt * 60 * (st.fast ? 3 : 1);
  const last = p.frames.length - 1;
  const i = Math.min(last, Math.floor(p.t));
  while (p.next < p.events.length && p.events[p.next][0] <= i) {
    const what = p.events[p.next][1];
    p.next++;
    Android.event(what);
    if (SAYS[what]) toast(SAYS[what], what === 'holed' ? 2000 : 1300);
    if (what === 'portal') st.trail = [];
    if (what === 'sea' || what === 'water' || what === 'fire') {
      const f = p.frames[i];
      splash.position.set(wx(f[0]), what === 'sea' ? WATER_Y + 0.08 : 0.08, wz(f[1]));
      splash.material.color.set(what === 'fire' ? '#ff7a1f' : '#ffffff');
      splash.visible = true; splash.userData.at = now;
      p.lostAt = now;
    }
    if (what === 'holed') p.holedAt = now;
  }
  if (i >= last && !p.lostAt && !p.holedAt) endPlay();
}
function endPlay() {
  const p = st.play;
  st.play = null; splash.visible = false; st.trail = [];
  Android.event('played');
  if (st.pending) { const s = st.pending; st.pending = null; applyState(s); } else if (p) {
    // Nothing new from the app yet: leave the ball where the shot ended.
    const f = p.frames[p.frames.length - 1];
    const b = balls.get(p.id); if (b) { b.x = f[0]; b.y = f[1]; }
  }
  hint();
}
/** Where the ball in play is, in the world, and which way it's going. */
function playFrame() {
  const p = st.play, last = p.frames.length - 1;
  const i = Math.min(last, Math.floor(p.t)), j = Math.min(last, i + 1), k = Math.min(1, p.t - i);
  const a = p.frames[i], b = p.frames[j];
  const x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, h = a[2] + (b[2] - a[2]) * k;
  const back = p.frames[Math.max(0, i - 4)];
  return { pos: new THREE.Vector3(wx(x), Math.max(h, WATER_Y) + BALL_R, wz(y)), dx: x - back[0], dy: y - back[1], x, y, h };
}

// ---- Camera -----------------------------------------------------------------------------------
const camPos = new THREE.Vector3(0, 30, 24), camLook = new THREE.Vector3();
const want = new THREE.Vector3(), wantLook = new THREE.Vector3();
function restingAt(id) { const b = balls.get(id); return b ? new THREE.Vector3(wx(b.x), BALL_R, wz(b.y)) : new THREE.Vector3(); }
function behind(at, aim, z, target, look) {
  const dx = Math.cos(aim), dz = Math.sin(aim);
  target.set(at.x - dx * 5.2 * z, at.y + 3.1 * z, at.z - dz * 5.2 * z);
  look.set(at.x + dx * 4, at.y, at.z + dz * 4);
}
function placeCamera(now, instant) {
  if (!st.hole) return;
  if (now < st.overviewUntil) {
    // The fly-over: high above, circling slowly, the whole hole in view.
    const a = (now / 1000) * 0.25, k = (st.hole.len || 1.6) / 1.6;
    want.set(Math.sin(a) * 9 * k, 21 * k, (14 + Math.cos(a) * 3) * k);
    wantLook.set(0, -1, -1);
  } else if (st.play && st.play.holedAt) {
    const c = new THREE.Vector3(wx(st.hole.cup[0]), 0, wz(st.hole.cup[1]));
    const a = (now - st.play.holedAt) / 1000 * 0.5;
    want.set(c.x + Math.sin(a) * 3.5, 1.8, c.z + Math.cos(a) * 3.5);
    wantLook.copy(c).setY(0.6);
  } else if (st.play && st.play.lostAt) {
    want.copy(camPos);
  } else if (st.play) {
    // Chasing the ball, lower and closer near the cup.
    const f = playFrame();
    const sp = Math.hypot(f.dx, f.dy);
    if (sp > 1e-4) { st.chaseX = f.dx / sp; st.chaseY = f.dy / sp; }
    const dx = st.chaseX || 0, dz = st.chaseY || -1;
    const near = Math.hypot(f.x - st.hole.cup[0], f.y - st.hole.cup[1]) < 0.25 && f.h <= 0.01;
    want.set(f.pos.x - dx * (near ? 3.2 : 5.2), f.pos.y + (near ? 1.4 : 2.6 + Math.max(0, f.h) * 0.5), f.pos.z - dz * (near ? 3.2 : 5.2));
    wantLook.set(f.pos.x + dx * 2, f.pos.y, f.pos.z + dz * 2);
  } else if (st.turn === st.me && balls.has(st.me)) {
    // Your turn: behind your ball, facing where you left it. Pinch out rises to the map.
    behind(restingAt(st.me), st.aim, Math.min(st.zoom, 1.6), want, wantLook);
    const tilt = clamp((st.zoom - 1.6) / 0.8, 0, 1);
    if (tilt > 0) { want.lerp(MAP_POS, tilt); wantLook.lerp(MAP_LOOK, tilt); }
  } else if (st.turn && balls.has(st.turn)) {
    // Someone else's turn: behind their ball, looking up the hole.
    const b = balls.get(st.turn);
    behind(restingAt(st.turn), Math.atan2(st.hole.cup[1] - b.y, st.hole.cup[0] - b.x), 1.2, want, wantLook);
  } else {
    want.copy(MAP_POS); wantLook.copy(MAP_LOOK);
  }
  const k = instant ? 1 : (ptr.kind === 'look' || pinch.on ? 0.3 : 0.07);
  camPos.lerp(want, k); camLook.lerp(wantLook, k);
  camera.position.copy(camPos); camera.lookAt(camLook);
}

// The dashed guide: a short pointer at rest, then while sliding an arc for a jump or a line for a roll.
function drawGuide() {
  const b = myBall();
  if (!myAim() || st.noGuide || !b) { guide.count = 0; guide.instanceMatrix.needsUpdate = true; return; }
  const pts = [];
  const dx = Math.cos(st.aim), dy = Math.sin(st.aim);
  if (!st.sliding) {
    for (let i = 1; i <= 9; i++) pts.push(new THREE.Vector3(wx(b.x + dx * 0.022 * i), 0.08, wz(b.y + dy * 0.022 * i)));
  } else if (st.mode === 'jump') {
    const T = 2 * JUMP_UP * st.power / GRAVITY;
    for (let i = 0; i <= 30; i++) {
      const t = T * i / 30;
      pts.push(new THREE.Vector3(wx(b.x + dx * JUMP_SPEED * st.power * t), JUMP_UP * st.power * t - GRAVITY * t * t / 2 + BALL_R, wz(b.y + dy * JUMP_SPEED * st.power * t)));
    }
  } else {
    const v = ROLL_SPEED * st.power, d = v * v / (2 * ROLL_DECEL);
    for (let i = 1; i <= 24; i++) pts.push(new THREE.Vector3(wx(b.x + dx * d * i / 24), 0.08, wz(b.y + dy * d * i / 24)));
  }
  setDots(guide, pts);
}

// ---- Loop -------------------------------------------------------------------------------------
function resize() {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!w || !h) return;
  renderer.setSize(w, h, false);
  camera.aspect = w / h; camera.updateProjectionMatrix();
}
let last = performance.now();
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  if (st.play) stepPlay(dt, now);
  const t = now / 1000;
  moveSea(t); waveFlag(t);
  for (const f of fires) { const k = 1 + Math.sin(t * 9 + f.userData.phase) * 0.2; f.scale.set(1 / Math.sqrt(k), k, 1 / Math.sqrt(k)); }
  for (const p of portals) p.rotation.y = t * 1.5;
  for (const [id, b] of balls) if (!(st.play && st.play.id === id)) b.mesh.position.set(wx(b.x), BALL_R, wz(b.y));
  if (st.play) {
    const p = st.play, b = balls.get(p.id), f = playFrame();
    b.mesh.position.copy(f.pos);
    b.mesh.visible = !p.lostAt || now - p.lostAt < 300;
    if (p.holedAt) b.mesh.position.y = Math.max(-0.3, BALL_R - (now - p.holedAt) / 600);
    if (!p.lostAt && !p.holedAt) { st.trail.push(f.pos.clone()); if (st.trail.length > 70) st.trail.shift(); }
  }
  trail.count = 0;
  if (st.trail.length > 1) setDots(trail, st.trail.filter((_, i) => i % 2 === 0)); else trail.instanceMatrix.needsUpdate = true;
  if (splash.visible) { const k = (now - splash.userData.at) / LOST_MS; splash.scale.setScalar(1 + k * 3); splash.material.opacity = Math.max(0, 1 - k); }
  drawGuide();
  placeCamera(now, false);
  renderer.render(scene, camera);
  requestAnimationFrame(frame);
}

// ---- Touch: swipe to turn, pinch to zoom, the side slider to shoot ----------------------------
const ptr = { kind: null, x0: 0, y0: 0, x: 0, y: 0, moved: false };
let lastTap = 0;
function local(e) { const r = canvas.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; }
canvas.addEventListener('pointerdown', (e) => {
  if (performance.now() < st.overviewUntil) st.overviewUntil = 0;   // a touch skips the fly-over
  if (pinch.on) { ptr.kind = null; return; }
  canvas.setPointerCapture(e.pointerId);
  const [x, y] = local(e);
  Object.assign(ptr, { x0: x, y0: y, x, y, moved: false });
  if (st.play) { st.fast = true; ptr.kind = null; return; }
  ptr.kind = 'look';
});
canvas.addEventListener('pointermove', (e) => {
  if (!ptr.kind) return;
  const [x, y] = local(e);
  const dx = x - ptr.x;
  ptr.x = x; ptr.y = y;
  if (Math.hypot(x - ptr.x0, y - ptr.y0) > 8) ptr.moved = true;
  if (ptr.moved && myAim()) { st.aim += dx * 0.007; st.aims[st.key] = st.aim; }
});
canvas.addEventListener('pointerup', () => {
  const kind = ptr.kind; ptr.kind = null;
  if (!kind || ptr.moved) return;
  // A double tap faces the flag again.
  const now = performance.now();
  if (now - lastTap < 320 && myAim()) { lastTap = 0; st.zoom = 1; aimAtFlag(); st.aims[st.key] = st.aim; hint(); return; }
  lastTap = now;
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

const slider = document.getElementById('slider'), knob = document.getElementById('knob'), powerTag = document.getElementById('power');
function setKnob(t) {
  knob.style.bottom = `calc(4px + ${clamp(t, 0, 1)} * (100% - 44px))`;
  slider.setAttribute('aria-valuenow', String(Math.round(clamp(t, 0, 1) * 100)));
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
  if (performance.now() < st.overviewUntil) st.overviewUntil = 0;
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
    Android.shoot(st.mode, st.power, st.aim);
  }
  setKnob(0);
});

window.addEventListener('resize', resize);
resize();
requestAnimationFrame(frame);
Android.ready();
