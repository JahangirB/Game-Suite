// Ship Battle in 3D: one 10 x 10 sea with its ships, pegs, fires and shots.
//
// The app calls fleet.state({...}) whenever what this sea shows changes:
//   ships:   [{ kind, x, y, len, h, sunk }]   (an enemy sea lists only the ships already sunk)
//   shots:   [{ x, y, hit }]                   shots that landed here
//   last:    { x, y, hit } | null              the newest shot: it flies in when it changes
//   replay:  n                                 going up plays the last shot again
//   pending: [x, y] | null                     a shot on its way (a target marker)
//   ghost:   { x, y, len, h, ok, i } | null    placing: where the dragged ship would land
//   picked:  i | null                          placing: the ship picked up
//   enemy:   true when this is the sea you fire at (shells come from your side)
//   edit:    true while placing (touches go down, move, up instead of taps)
// and hears back Android.cell('tap' | 'down' | 'move' | 'up', x, y, moved).
// The sea is calm: no waves. The ships lie still; only their radars turn.
const fleet = (() => {
  const N = 10;
  const canvas = document.getElementById('view');
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  } catch (e) {
    Android.event('error no 3D on this phone');
    return { state() {} };
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  const scene = new THREE.Scene();
  scene.background = new THREE.Color('#86c3e6');
  scene.fog = new THREE.Fog('#a5d3ee', 18, 40);
  scene.add(new THREE.HemisphereLight('#eef7ff', '#1d4a66', 0.7));
  const sun = new THREE.DirectionalLight('#fff2d6', 0.95);
  sun.position.set(8, 14, 6);
  scene.add(sun);
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 200);

  const mat = (c, o = {}) => new THREE.MeshStandardMaterial(Object.assign({ color: c, roughness: 0.55, metalness: 0.15 }, o));
  const M = { hull: mat('#5f6b76'), hullDark: mat('#47515b'), boot: mat('#a8323a'), deck: mat('#8f8a7a', { roughness: 0.8 }), white: mat('#e9edf1'), black: mat('#1d2126'), steel: mat('#7e8a95', { metalness: 0.4 }), glass: mat('#2a3946', { metalness: 0.6, roughness: 0.2 }) };

  // ---- The sea: a flat, calm plane with the grid on it and a buoy at each corner ----
  const water = new THREE.Mesh(new THREE.PlaneGeometry(40, 40).rotateX(-Math.PI / 2), new THREE.MeshPhongMaterial({ color: '#0f5a86', shininess: 80, specular: '#a9dcf7' }));
  scene.add(water);
  const board = new THREE.Mesh(new THREE.PlaneGeometry(N, N).rotateX(-Math.PI / 2), new THREE.MeshPhongMaterial({ color: '#1677a8', shininess: 60, specular: '#bfe6ff' }));
  board.position.y = 0.01;
  scene.add(board);
  {
    const pts = [];
    for (let i = 0; i <= N; i++) { const v = i - N / 2; pts.push(-N / 2, 0, v, N / 2, 0, v, v, 0, -N / 2, v, 0, N / 2); }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3));
    const grid = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.4 }));
    grid.position.y = 0.03;
    scene.add(grid);
    for (const [a, b] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const buoy = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.22, 0.5, 12), mat('#ffb400'));
      buoy.position.set(a * (N / 2 + 0.4), 0.15, b * (N / 2 + 0.4));
      scene.add(buoy);
    }
  }
  // The middle of square (q, r).
  const cell = (q, r) => new THREE.Vector3(q - N / 2 + 0.5, 0, r - N / 2 + 0.5);

  // ---- The ships, by class ----
  function hull(L, B, H) {
    const s = new THREE.Shape();
    s.moveTo(-L / 2 + 0.2, -B / 2); s.lineTo(L / 2 - L * 0.3, -B / 2); s.quadraticCurveTo(L / 2 - 0.05, -B * 0.32, L / 2, 0); s.quadraticCurveTo(L / 2 - 0.05, B * 0.32, L / 2 - L * 0.3, B / 2); s.lineTo(-L / 2 + 0.2, B / 2); s.quadraticCurveTo(-L / 2, B / 2, -L / 2, 0); s.quadraticCurveTo(-L / 2, -B / 2, -L / 2 + 0.2, -B / 2);
    const g = new THREE.Group();
    const body = new THREE.Mesh(new THREE.ExtrudeGeometry(s, { depth: H, bevelEnabled: true, bevelThickness: 0.04, bevelSize: 0.03, bevelSegments: 2 }).rotateX(-Math.PI / 2), M.hull); body.position.y = -0.2; g.add(body);
    const boot = new THREE.Mesh(new THREE.ExtrudeGeometry(s, { depth: 0.1, bevelEnabled: false }).rotateX(-Math.PI / 2), M.boot); boot.scale.set(1.012, 1, 1.03); boot.position.y = -0.2; g.add(boot);
    const deck = new THREE.Mesh(new THREE.ShapeGeometry(s).rotateX(-Math.PI / 2), M.deck); deck.scale.set(0.97, 1, 0.9); deck.position.y = -0.2 + H + 0.045; g.add(deck);
    g.userData.top = -0.2 + H + 0.05;
    return g;
  }
  function turret(n = 2) {
    const g = new THREE.Group();
    g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.17, 0.19, 0.08, 16), M.steel));
    const house = new THREE.Mesh(new THREE.BoxGeometry(0.32, 0.12, 0.26), M.steel); house.position.set(-0.02, 0.09, 0); g.add(house);
    for (let i = 0; i < n; i++) { const b = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.026, 0.42, 8).rotateZ(Math.PI / 2), M.black); b.position.set(0.3, 0.11, (i - (n - 1) / 2) * 0.07); g.add(b); }
    return g;
  }
  function tower(w, d, levels) {
    const g = new THREE.Group(); let y = 0;
    levels.forEach((h, i) => { const k = 1 - i * 0.18; const b = new THREE.Mesh(new THREE.BoxGeometry(w * k, h, d * k), i === levels.length - 1 ? M.glass : M.white); b.position.y = y + h / 2; g.add(b); y += h; });
    const mast = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.025, 0.55, 6), M.black); mast.position.y = y + 0.27; g.add(mast);
    const yard = new THREE.Mesh(new THREE.BoxGeometry(0.02, 0.02, 0.3), M.black); yard.position.y = y + 0.42; g.add(yard);
    const radar = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.05, 0.03), M.steel); radar.position.y = y + 0.55; g.add(radar); radar.userData.radar = true;
    return g;
  }
  function funnel() { const g = new THREE.Group(); const f = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.12, 0.38, 14), M.hullDark); f.position.y = 0.19; f.rotation.z = 0.12; g.add(f); const band = new THREE.Mesh(new THREE.CylinderGeometry(0.095, 0.095, 0.06, 14), M.black); band.position.set(-0.02, 0.36, 0); g.add(band); return g; }
  const flightDeck = (() => { const c = document.createElement('canvas'); c.width = 512; c.height = 128; const g = c.getContext('2d'); g.fillStyle = '#4b5156'; g.fillRect(0, 0, 512, 128); g.strokeStyle = '#f2f2f2'; g.lineWidth = 3; g.setLineDash([22, 16]); g.beginPath(); g.moveTo(20, 64); g.lineTo(492, 64); g.stroke(); g.setLineDash([]); g.strokeStyle = '#ffd23f'; g.beginPath(); g.moveTo(30, 100); g.lineTo(300, 40); g.stroke(); return new THREE.CanvasTexture(c); })();
  const SHIPS = {
    carrier(L) {
      const g = hull(L, 0.66, 0.34), y = g.userData.top;
      const fd = new THREE.Mesh(new THREE.BoxGeometry(L - 0.1, 0.06, 0.86), [M.hullDark, M.hullDark, new THREE.MeshStandardMaterial({ map: flightDeck, roughness: 0.9 }), M.hullDark, M.hullDark, M.hullDark]); fd.position.set(-0.05, y + 0.06, 0.06); g.add(fd);
      const island = tower(0.5, 0.2, [0.22, 0.16, 0.12]); island.position.set(0.3, y + 0.09, -0.32); g.add(island);
      for (const [px, pz] of [[-1.4, 0.1], [-0.9, 0.25], [-1.8, 0.3]]) { const plane = new THREE.Group(); plane.add(new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.04, 0.05), M.steel), new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.02, 0.28), M.steel)); plane.position.set(px * L / 5, y + 0.11, pz); g.add(plane); }
      return g;
    },
    battleship(L) {
      const g = hull(L, 0.62, 0.32), y = g.userData.top;
      const t1 = turret(3); t1.position.set(L * 0.3, y, 0); g.add(t1);
      const t2 = turret(3); t2.position.set(L * 0.15, y + 0.1, 0); t2.scale.setScalar(0.95); g.add(t2);
      const t3 = turret(3); t3.position.set(-L * 0.33, y, 0); t3.rotation.y = Math.PI; g.add(t3);
      const tw = tower(0.5, 0.36, [0.16, 0.14, 0.12, 0.1]); tw.position.set(-0.05, y, 0); g.add(tw);
      const f = funnel(); f.position.set(-0.55, y, 0); g.add(f);
      return g;
    },
    cruiser(L) {
      const g = hull(L, 0.56, 0.3), y = g.userData.top;
      const t1 = turret(2); t1.position.set(L * 0.28, y, 0); g.add(t1);
      const t2 = turret(2); t2.position.set(-L * 0.32, y, 0); t2.rotation.y = Math.PI; g.add(t2);
      const tw = tower(0.42, 0.32, [0.14, 0.12, 0.1]); tw.position.set(0.05, y, 0); g.add(tw);
      const f = funnel(); f.position.set(-0.4, y, 0); g.add(f);
      return g;
    },
    submarine(L) {
      const g = new THREE.Group();
      const body = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, L - 0.5, 20).rotateZ(Math.PI / 2), M.hullDark); body.position.y = -0.04; g.add(body);
      for (const s of [-1, 1]) { const end = new THREE.Mesh(new THREE.SphereGeometry(0.22, 20, 14), M.hullDark); end.scale.set(s > 0 ? 1.4 : 1.1, 1, 1); end.position.set(s * (L - 0.5) / 2, -0.04, 0); g.add(end); }
      const sail = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.3, 0.13), M.hullDark); sail.position.set(0.2, 0.25, 0); g.add(sail);
      const planes = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.02, 0.42), M.hullDark); planes.position.set(0.25, 0.28, 0); g.add(planes);
      const peri = new THREE.Mesh(new THREE.CylinderGeometry(0.015, 0.015, 0.25, 6), M.black); peri.position.set(0.25, 0.5, 0); g.add(peri);
      const tail = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.26, 0.02), M.hullDark); tail.position.set(-(L - 0.3) / 2, 0.05, 0); g.add(tail);
      return g;
    },
    destroyer(L) {
      const g = hull(L, 0.46, 0.28), y = g.userData.top;
      const t1 = turret(1); t1.position.set(L * 0.27, y, 0); t1.scale.setScalar(0.85); g.add(t1);
      const tw = tower(0.32, 0.26, [0.12, 0.1]); tw.position.set(0.02, y, 0); g.add(tw);
      const f = funnel(); f.position.set(-0.3, y, 0); f.scale.setScalar(0.85); g.add(f);
      return g;
    },
  };
  // A sunk ship turns dark: each of its materials, a scorched copy.
  function scorch(group) {
    group.traverse((o) => {
      if (!o.isMesh) return;
      const one = (m) => { const c = m.clone(); if (c.color) c.color.multiplyScalar(0.45); return c; };
      o.material = Array.isArray(o.material) ? o.material.map(one) : one(o.material);
    });
  }
  // Where a ship sits: the middle of its squares, turned along them.
  const centre = (s) => (s.h ? cell(s.x + (s.len - 1) / 2, s.y) : cell(s.x, s.y + (s.len - 1) / 2));

  // ---- Pegs, fires, markers and the shot's effects ----
  const pegMat = { hit: mat('#e5484d', { emissive: '#7a1018', emissiveIntensity: 0.4 }), miss: mat('#f4f6f8') };
  const pegGeo = { body: new THREE.CylinderGeometry(0.11, 0.14, 0.24, 12), cap: new THREE.SphereGeometry(0.11, 12, 8) };
  function peg(hit) {
    const g = new THREE.Group();
    const m = new THREE.Mesh(pegGeo.body, hit ? pegMat.hit : pegMat.miss); m.position.y = 0.12; g.add(m);
    const cap = new THREE.Mesh(pegGeo.cap, m.material); cap.position.y = 0.25; g.add(cap);
    scene.add(g);
    return g;
  }
  const puffGeo = new THREE.SphereGeometry(0.14, 8, 6);
  function fire(pos) {
    const f = { pos: pos.clone(), puffs: [], smokeOnly: false };
    for (let i = 0; i < 12; i++) {
      const m = new THREE.Mesh(puffGeo, new THREE.MeshBasicMaterial({ color: '#ff8a1f', transparent: true, depthWrite: false }));
      scene.add(m);
      f.puffs.push({ m, a: i * 1.9, s: 0.5 + (i % 4) * 0.18, smoke: i % 3 === 0 });
    }
    return f;
  }
  function dropFire(f) { for (const p of f.puffs) { scene.remove(p.m); p.m.material.dispose(); } }
  const shell = new THREE.Mesh(new THREE.SphereGeometry(0.1, 10, 8), M.black); scene.add(shell);
  const trail = []; for (let i = 0; i < 12; i++) { const m = new THREE.Mesh(new THREE.SphereGeometry(0.07, 6, 5), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, depthWrite: false })); scene.add(m); trail.push(m); }
  const splash = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.4, 1, 14, 1, true), new THREE.MeshPhongMaterial({ color: '#ffffff', transparent: true, side: THREE.DoubleSide, depthWrite: false })); scene.add(splash);
  const ripple = new THREE.Mesh(new THREE.RingGeometry(0.3, 0.42, 24).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, depthWrite: false })); scene.add(ripple);
  const flash = new THREE.Mesh(new THREE.SphereGeometry(0.5, 14, 10), new THREE.MeshBasicMaterial({ color: '#ffb13d', transparent: true, depthWrite: false })); scene.add(flash);
  const target = new THREE.Mesh(new THREE.RingGeometry(0.34, 0.46, 4).rotateX(-Math.PI / 2).rotateY(Math.PI / 4), new THREE.MeshBasicMaterial({ color: '#ffd23f' })); scene.add(target);
  const lastRing = new THREE.Mesh(new THREE.RingGeometry(0.38, 0.46, 32).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ff6b5a' })); scene.add(lastRing);
  // Placing: the squares the dragged ship would cover, green where it may go, red where not.
  const ghostTiles = [];
  for (let i = 0; i < 5; i++) { const t = new THREE.Mesh(new THREE.PlaneGeometry(0.94, 0.94).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#35bd88', transparent: true, opacity: 0.5, depthWrite: false })); t.position.y = 0.04; scene.add(t); ghostTiles.push(t); }
  // The picked ship's squares glow coral, so it's clear which one moves.
  const pickedTiles = [];
  for (let i = 0; i < 5; i++) { const t = new THREE.Mesh(new THREE.PlaneGeometry(0.94, 0.94).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: '#ff6b5a', transparent: true, opacity: 0.55, depthWrite: false })); t.position.y = 0.04; scene.add(t); pickedTiles.push(t); }

  // ---- What the sea shows, and how it changes ----
  let st = { ships: [], shots: [], last: null, replay: 0, pending: null, ghost: null, picked: null, enemy: false, edit: false };
  const ships = [];          // { kind, len, group, at: Vector3, turn, sunk, sinkAt, shown }
  const pegs = new Map();    // "x,y" -> peg
  const fires = new Map();   // "x,y" -> fire
  let shot = null;           // the shot flying in: { x, y, hit, at, from }
  let lastKey = null, lastReplay = 0, first = true;
  const now = () => performance.now() / 1000;
  const lerp = (a, b, t) => a + (b - a) * t;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const ease = (t) => t * t * (3 - 2 * t);
  const SHOT_S = 1.4, SINK_S = 2.4;

  function shipAt(i, s) {
    let e = ships[i];
    if (!e || e.kind !== s.kind || e.len !== s.len || e.sunk !== !!s.sunk) {
      const wasAfloat = e && !e.sunk && s.sunk;
      if (e) scene.remove(e.group);
      const group = (SHIPS[s.kind] || SHIPS.destroyer)(s.len - 0.12);
      if (s.sunk) scorch(group);
      scene.add(group);
      const c = centre(s);
      e = { kind: s.kind, len: s.len, group, at: c.clone(), turn: s.h ? 0 : -Math.PI / 2, sunk: !!s.sunk, sinkAt: null };
      // A ship that goes down while you watch shows itself burning, rolls over and settles low.
      if (s.sunk && !first && (wasAfloat || st.enemy)) e.sinkAt = now() + (shot ? Math.max(0, shot.at + SHOT_S * 0.55 - now()) : 0);
      ships[i] = e;
    }
    e.goal = centre(s);
    e.goalTurn = s.h ? 0 : -Math.PI / 2;
    return e;
  }

  function state(next) {
    const prev = st;
    st = Object.assign({ ships: [], shots: [], last: null, replay: 0, pending: null, ghost: null, picked: null, enemy: false, edit: false }, next);
    const newShot = st.last && (st.last.x + ',' + st.last.y + ',' + st.last.hit + ',' + st.shots.length);
    const replayed = st.replay !== lastReplay;
    if (!first && st.last && (newShot !== lastKey || replayed)) {
      shot = { x: st.last.x, y: st.last.y, hit: st.last.hit, at: now(), from: st.enemy ? new THREE.Vector3(0, 3.5, 9) : new THREE.Vector3(0, 3.5, -9) };
    }
    lastKey = newShot;
    lastReplay = st.replay;
    st.ships.forEach((s, i) => shipAt(i, s));
    while (ships.length > st.ships.length) scene.remove(ships.pop().group);
    // Pegs and fires for the shots here; a new game clears the old ones.
    const keys = new Set(st.shots.map((s) => s.x + ',' + s.y));
    for (const [k, p] of pegs) if (!keys.has(k)) { scene.remove(p); pegs.delete(k); }
    for (const [k, f] of fires) if (!keys.has(k)) { dropFire(f); fires.delete(k); }
    const sunkCells = new Set();
    st.ships.forEach((s) => { if (s.sunk) for (let j = 0; j < s.len; j++) sunkCells.add(s.h ? (s.x + j) + ',' + s.y : s.x + ',' + (s.y + j)); });
    for (const s of st.shots) {
      const k = s.x + ',' + s.y;
      if (!pegs.has(k)) { const p = peg(s.hit); p.position.copy(cell(s.x, s.y)); pegs.set(k, p); }
      if (s.hit && !fires.has(k)) fires.set(k, fire(cell(s.x, s.y).setY(0.25)));
      const f = fires.get(k);
      if (f) f.smokeOnly = sunkCells.has(k);
    }
    first = false;
    void prev;
    wake();
  }

  // ---- The camera: looking down at the sea at a slant, the whole board in view ----
  function fit() {
    const w = window.innerWidth || 300, h = window.innerHeight || 300;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    // Closer for a square view, further back for a narrow one, so the board always fits.
    const tilt = 0.72; // radians from straight down
    let d = 12;
    for (let k = 0; k < 24; k++) {
      camera.position.set(0, Math.cos(tilt) * d, Math.sin(tilt) * d + 0.6);
      camera.lookAt(0, 0, 0.6);
      camera.updateProjectionMatrix();
      camera.updateMatrixWorld();
      let worst = 0;
      for (const [x, z] of [[-5.6, -5.6], [5.6, -5.6], [5.6, 5.6], [-5.6, 5.6]]) {
        const p = new THREE.Vector3(x, 0.4, z).project(camera);
        worst = Math.max(worst, Math.abs(p.x), Math.abs(p.y));
      }
      d *= worst / 0.95;
    }
    wake();
  }
  window.addEventListener('resize', fit);

  // ---- Touches: which square a finger is on ----
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
  const ground = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  function squareAt(e) {
    const r = canvas.getBoundingClientRect();
    ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, camera);
    const p = new THREE.Vector3();
    if (!ray.ray.intersectPlane(ground, p)) return null;
    return [clamp(Math.floor(p.x + N / 2), 0, N - 1), clamp(Math.floor(p.z + N / 2), 0, N - 1)];
  }
  let touch = null;
  const SLOP = 12;
  canvas.addEventListener('pointerdown', (e) => {
    const sq = squareAt(e);
    if (!sq) return;
    touch = { id: e.pointerId, x: e.clientX, y: e.clientY, moved: false, sq };
    canvas.setPointerCapture(e.pointerId);
    if (st.edit) Android.cell('down', sq[0], sq[1], false);
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!touch || e.pointerId !== touch.id) return;
    if (!touch.moved && Math.hypot(e.clientX - touch.x, e.clientY - touch.y) > SLOP) touch.moved = true;
    if (!touch.moved || !st.edit) return;
    const sq = squareAt(e);
    if (sq && (sq[0] !== touch.sq[0] || sq[1] !== touch.sq[1])) { touch.sq = sq; Android.cell('move', sq[0], sq[1], true); }
  });
  const up = (e) => {
    if (!touch || e.pointerId !== touch.id) return;
    const t = touch;
    touch = null;
    if (st.edit) Android.cell('up', t.sq[0], t.sq[1], t.moved);
    else if (!t.moved && e.type === 'pointerup') Android.cell('tap', t.sq[0], t.sq[1], false);
  };
  canvas.addEventListener('pointerup', up);
  canvas.addEventListener('pointercancel', up);

  // ---- Drawing ----
  let running = false, lastFrame = 0;
  function wake() { if (!running) { running = true; requestAnimationFrame(frame); } }
  function shotAt(from, to, u) {
    return new THREE.Vector3(lerp(from.x, to.x, u), lerp(from.y, 0.2, u) + Math.sin(u * Math.PI) * 4.2, lerp(from.z, to.z, u));
  }
  function frame(ms) {
    // Thirty frames a second is plenty for a calm sea, and kinder to the battery.
    if (ms - lastFrame < 30) { requestAnimationFrame(frame); return; }
    lastFrame = ms;
    const t = now();
    let busy = false;
    // Ships glide to where they belong; the one being dragged lifts off the water.
    st.ships.forEach((s, i) => {
      const e = ships[i];
      if (!e) return;
      const g = st.ghost && st.ghost.i === i ? st.ghost : null;
      const goal = g ? centre(g).setY(0.7) : e.goal;
      const turn = g ? (g.h ? 0 : -Math.PI / 2) : e.goalTurn;
      e.at.lerp(goal, 0.3);
      e.turn = lerp(e.turn, turn, 0.3);
      if (e.at.distanceTo(goal) > 0.01 || Math.abs(e.turn - turn) > 0.01) busy = true;
      e.group.position.copy(e.at);
      // Turned first, then rolled about its own length: the order matters.
      e.group.rotation.order = 'YXZ';
      e.group.rotation.set(0, e.turn, 0);
      if (e.sunk) {
        const k = e.sinkAt == null ? 1 : clamp((t - e.sinkAt) / SINK_S, 0, 1);
        if (k < 1) busy = true;
        // Burning on the water at first, then it rolls over and settles low in the sea.
        const s2 = ease(clamp((k - 0.25) / 0.75, 0, 1));
        e.group.position.y = e.at.y - s2 * 0.2;
        e.group.rotation.x = s2 * 0.45;
        e.group.rotation.z = -s2 * 0.1;
        e.group.visible = e.sinkAt == null || t >= e.sinkAt;
      }
      e.group.traverse((o) => { if (o.userData.radar && !e.sunk) o.rotation.y = t * 3; });
    });
    // The shot: a target marker, the shell's arc with its trail, then a splash or a flash.
    let landed = 1;
    if (shot) {
      const k = (t - shot.at) / SHOT_S;
      const to = cell(shot.x, shot.y);
      const fly = clamp((k - 0.12) / 0.43, 0, 1), after = clamp((k - 0.55) / 0.45, 0, 1);
      landed = after;
      target.visible = k < 0.6; target.position.set(to.x, 0.06, to.z); target.scale.setScalar(1 + Math.sin(k * 40) * 0.08);
      shell.visible = fly > 0 && fly < 1; shell.position.copy(shotAt(shot.from, to, fly));
      trail.forEach((m, i) => { const u = fly - (i + 1) * 0.025; m.visible = shell.visible && u > 0; if (m.visible) { m.position.copy(shotAt(shot.from, to, u)); m.material.opacity = 0.5 * (1 - i / trail.length); m.scale.setScalar(1 - (i / trail.length) * 0.6); } });
      splash.visible = !shot.hit && after > 0 && after < 1; splash.position.set(to.x, Math.sin(after * Math.PI) * 0.8, to.z); splash.scale.set(1 + after, Math.max(0.01, Math.sin(after * Math.PI) * 1.8), 1 + after); splash.material.opacity = 0.85 * (1 - after);
      ripple.visible = after > 0 && after < 1; ripple.position.set(to.x, 0.05, to.z); ripple.scale.setScalar(1 + after * 3); ripple.material.opacity = 0.8 * (1 - after);
      flash.visible = shot.hit && after > 0 && after < 0.35; flash.position.set(to.x, 0.4, to.z); flash.scale.setScalar(0.4 + after * 3); flash.material.opacity = 1 - after / 0.35;
      if (k >= 1) shot = null; else busy = true;
    } else {
      target.visible = shell.visible = splash.visible = ripple.visible = flash.visible = false;
      trail.forEach((m) => { m.visible = false; });
    }
    // The newest shot's peg (and fire) appear as it lands.
    const lastK = st.last ? st.last.x + ',' + st.last.y : null;
    for (const [k, p] of pegs) p.visible = k !== lastK || landed > 0;
    for (const [k, f] of fires) {
      const lit = k !== lastK || landed > 0;
      f.puffs.forEach((pf) => {
        pf.m.visible = lit && (!f.smokeOnly || pf.smoke);
        if (!pf.m.visible) return;
        const life = (t * pf.s + pf.a) % 1;
        pf.m.position.set(f.pos.x + Math.sin(pf.a) * 0.18, f.pos.y + life * (pf.smoke ? 2 : 0.8), f.pos.z + Math.cos(pf.a) * 0.15);
        pf.m.scale.setScalar(pf.smoke ? 1.1 + life * 2.2 : 1.1 - life * 0.8);
        pf.m.material.color.set(pf.smoke ? '#4d4d4d' : life < 0.35 ? '#ffd23f' : '#ff5a1f');
        pf.m.material.opacity = pf.smoke ? 0.5 * (1 - life) : 0.95 * (1 - life);
      });
    }
    // A shot on its way: the target pulses until the answer comes.
    if (st.pending && !shot) {
      const to = cell(st.pending[0], st.pending[1]);
      target.visible = true; target.position.set(to.x, 0.06, to.z); target.scale.setScalar(1 + Math.sin(t * 8) * 0.1);
      busy = true;
    }
    lastRing.visible = !!st.last && !shot && !st.edit;
    if (st.last) lastRing.position.copy(cell(st.last.x, st.last.y).setY(0.05));
    // Placing: the squares under the dragged ship, and a ring round the picked one.
    const g = st.ghost;
    ghostTiles.forEach((tile, i) => {
      tile.visible = !!g && i < g.len;
      if (!tile.visible) return;
      const q = g.h ? g.x + i : g.x, r = g.h ? g.y : g.y + i;
      tile.visible = q >= 0 && q < N && r >= 0 && r < N;
      tile.position.set(q - N / 2 + 0.5, 0.04, r - N / 2 + 0.5);
      tile.material.color.set(g.ok ? '#35bd88' : '#e5484d');
    });
    const pk = st.picked != null && !g ? st.ships[st.picked] : null;
    pickedTiles.forEach((tile, i) => {
      tile.visible = !!pk && i < pk.len;
      if (!tile.visible) return;
      tile.position.set((pk.h ? pk.x + i : pk.x) - N / 2 + 0.5, 0.04, (pk.h ? pk.y : pk.y + i) - N / 2 + 0.5);
      tile.material.opacity = 0.4 + Math.sin(t * 6) * 0.2;
    });
    if (pk) busy = true;
    renderer.render(scene, camera);
    // Fires and radars keep moving; a sea with nothing going on rests until it changes.
    const alive = busy || fires.size > 0 || st.ships.some((s) => !s.sunk);
    if (alive) requestAnimationFrame(frame); else running = false;
  }

  fit();
  wake();
  setTimeout(() => { try { Android.ready(); } catch (e) { /* not in the app */ } }, 0);
  return { state, fit };
})();
