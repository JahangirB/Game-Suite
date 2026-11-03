// Ludo's dice strip: see index.html. The app calls dice.show(state) whenever the game changes;
// a roll count that goes up throws the die for that roll. A tap on the strip (or a swipe along
// it) on your turn calls Android.roll(power).
'use strict';
(function () {
  const TAU = Math.PI * 2;
  const app = window.Android || { roll() {}, landed() {}, ready() {}, event() {} };
  const canvas = document.getElementById('view');
  const button = document.getElementById('roll');
  const strip = document.getElementById('strip');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setClearColor(0x000000, 0);
  renderer.shadowMap.enabled = true;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  scene.add(new THREE.HemisphereLight('#ffffff', '#c9c2e6', 0.85));
  const sun = new THREE.DirectionalLight('#fff7e8', 0.7);
  sun.position.set(-3, 10, 6);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 512);
  Object.assign(sun.shadow.camera, { left: -7, right: 7, top: 3, bottom: -3 });
  scene.add(sun);

  // The strip's floor in world units; its length follows the screen's shape (set in resize).
  let L = 8.6;
  const D = 1.7;
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(40, 10).rotateX(-Math.PI / 2), new THREE.ShadowMaterial({ opacity: 0.25 }));
  floor.receiveShadow = true;
  scene.add(floor);

  // The die: pips on each face, opposite faces adding up to 7; the 1 is red.
  const PIPS = { 1: [[0, 0]], 2: [[-1, -1], [1, 1]], 3: [[-1, -1], [0, 0], [1, 1]], 4: [[-1, -1], [1, -1], [-1, 1], [1, 1]], 5: [[-1, -1], [1, -1], [0, 0], [-1, 1], [1, 1]], 6: [[-1, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [1, 1]] };
  function face(n) {
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    g.fillStyle = '#FFFDF7';
    g.fillRect(0, 0, 128, 128);
    g.strokeStyle = '#d8d2c2';
    g.lineWidth = 6;
    g.strokeRect(3, 3, 122, 122);
    g.fillStyle = n === 1 ? '#C8102E' : '#2B2440';
    for (const [x, y] of PIPS[n]) {
      g.beginPath();
      g.arc(64 + x * 32, 64 + y * 32, n === 1 ? 17 : 12, 0, TAU);
      g.fill();
    }
    return new THREE.MeshStandardMaterial({ map: new THREE.CanvasTexture(c), roughness: 0.35 });
  }
  const AXES = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]].map((a) => new THREE.Vector3(...a));
  const FACES = [3, 4, 1, 6, 2, 5]; // the number on +x, -x, +y, -y, +z, -z
  const SIZE = 0.95;
  const die = new THREE.Mesh(new THREE.BoxGeometry(SIZE, SIZE, SIZE), FACES.map(face));
  die.castShadow = true;
  scene.add(die);

  // The 24 ways to turn a cube onto itself, to relabel the die so a throw lands on its number.
  const TURNS = (() => {
    const out = [];
    const q90 = (axis) => new THREE.Quaternion().setFromAxisAngle(axis, Math.PI / 2);
    const seen = new Set();
    const key = (q) => AXES.slice(0, 3).map((a) => a.clone().applyQuaternion(q).toArray().map(Math.round).join()).join('|');
    const queue = [new THREE.Quaternion()];
    while (queue.length) {
      const q = queue.shift();
      const k = key(q);
      if (seen.has(k)) continue;
      seen.add(k);
      out.push(q);
      for (const a of AXES.slice(0, 3)) queue.push(q90(a).multiply(q.clone()));
    }
    return out;
  })();

  // Physics: the floor, the strip's walls and the die. A throw is worked out to the end first,
  // then played back frame by frame.
  const world = new CANNON.World();
  world.gravity.set(0, -30, 0);
  const fm = new CANNON.Material('floor');
  const dm = new CANNON.Material('die');
  world.addContactMaterial(new CANNON.ContactMaterial(fm, dm, { friction: 0.35, restitution: 0.4 }));
  const ground = new CANNON.Body({ mass: 0, material: fm });
  ground.addShape(new CANNON.Plane());
  ground.quaternion.setFromAxisAngle(new CANNON.Vec3(1, 0, 0), -Math.PI / 2);
  world.addBody(ground);
  let walls = [];
  function buildWalls() {
    walls.forEach((w) => world.removeBody(w));
    walls = [[0, -D / 2 - 0.25, L + 2, 0.5], [0, D / 2 + 0.25, L + 2, 0.5], [-L / 2 - 0.25, 0, 0.5, D + 1], [L / 2 + 0.25, 0, 0.5, D + 1]].map(([x, z, w, d]) => {
      const b = new CANNON.Body({ mass: 0, material: fm });
      b.addShape(new CANNON.Box(new CANNON.Vec3(w / 2, 2, d / 2)));
      b.position.set(x, 1, z);
      world.addBody(b);
      return b;
    });
  }
  const body = new CANNON.Body({ mass: 1, material: dm, shape: new CANNON.Box(new CANNON.Vec3(SIZE / 2, SIZE / 2, SIZE / 2)), linearDamping: 0.12, angularDamping: 0.1 });
  world.addBody(body);

  function topAxis(q) {
    let best = 0;
    let bd = -2;
    AXES.forEach((a, i) => {
      const d = a.clone().applyQuaternion(q).y;
      if (d > bd) {
        bd = d;
        best = i;
      }
    });
    return best;
  }

  /** Works out a throw from the [dir] end (1: left to right) as hard as [power] (0-1). */
  function simulate(dir, power) {
    const r = Math.random;
    body.position.set(-dir * (L / 2 - 0.9), 0.9, (r() - 0.5) * 0.5);
    body.quaternion.setFromEuler(r() * TAU, r() * TAU, r() * TAU);
    const speed = 7.5 + power * 9 + r() * 2;
    body.velocity.set(dir * speed, 1.5, (r() - 0.5) * (1.5 + power * 2));
    body.angularVelocity.set((r() - 0.5) * 10, (r() - 0.5) * 10, -dir * (14 + r() * 8));
    const frames = [];
    let still = 0;
    for (let i = 0; i < 60 * 6 && still < 12; i++) {
      world.step(1 / 60);
      frames.push([body.position.x, body.position.y, body.position.z, body.quaternion.x, body.quaternion.y, body.quaternion.z, body.quaternion.w]);
      still = body.velocity.length() < 0.06 && body.angularVelocity.length() < 0.06 ? still + 1 : 0;
    }
    // Settle it square on its face, so the number on top is the one that counts.
    const last = frames[frames.length - 1];
    const q = new THREE.Quaternion(last[3], last[4], last[5], last[6]);
    const up = topAxis(q);
    const want = new THREE.Vector3(0, 1, 0);
    const fix = new THREE.Quaternion().setFromUnitVectors(AXES[up].clone().applyQuaternion(q), want);
    const settled = fix.multiply(q);
    for (let k = 1; k <= 8; k++) {
      const t = k / 8;
      const qq = q.clone().slerp(settled, t);
      frames.push([last[0], SIZE / 2 + (last[1] - SIZE / 2) * (1 - t), last[2], qq.x, qq.y, qq.z, qq.w]);
    }
    return { frames, up };
  }

  /** The turn of the die's faces that puts [value] on body axis [up]. */
  function relabel(value, up) {
    const from = AXES[FACES.indexOf(value)];
    return TURNS.find((t) => from.clone().applyQuaternion(t).distanceTo(AXES[up]) < 0.01) || new THREE.Quaternion();
  }

  // What the strip shows: the roll it has seen, whose turn, and the throw playing.
  const st = { count: -1, canRoll: false, throwing: null, rest: null, label: 'Roll' };
  // A die at rest at the [dir] end showing [value].
  function rest(dir, value) {
    const turn = value ? relabel(value, 2) : new THREE.Quaternion();
    const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, 0.35 * dir, 0));
    st.rest = { x: value ? 0 : -dir * (L / 2 - 0.9), q: q.multiply(turn) };
  }

  window.dice = {
    /** The number on top right now (for tests). */
    top() { return FACES[topAxis(die.quaternion)]; },
    busy() { return !!st.throwing; },
    show(s) {
      st.canRoll = !!s.canRoll;
      document.documentElement.style.setProperty('--felt', s.felt || '#7CB342');
      document.documentElement.style.setProperty('--ink', s.ink || '#2B2440');
      button.firstChild.nodeValue = s.label || 'Roll';
      button.classList.toggle('off', !st.canRoll);
      if (st.count < 0 || s.count < st.count) {
        // First look (or a new game): the last roll lies in the middle, nothing is thrown.
        st.count = s.count;
        if (s.value) rest(1, s.value);
        else rest(s.mine ? 1 : -1, 0);
        return;
      }
      if (s.count > st.count && s.value) {
        st.count = s.count;
        const dir = s.who === 'm' ? 1 : -1;
        const { frames, up } = simulate(dir, s.who === 'm' ? pendingPower : 0.3);
        st.throwing = { frames, turn: relabel(s.value, up), start: performance.now(), count: s.count, value: s.value };
        st.rest = null;
      }
    },
  };

  // Your throw: a tap anywhere on the strip, or a swipe along it (harder the faster you flick).
  let pendingPower = 0.3;
  let down = null;
  strip.addEventListener('pointerdown', (e) => {
    if (!st.canRoll || st.throwing) return;
    down = { x: e.clientX, y: e.clientY, t: performance.now() };
    button.classList.add('down');
  });
  const up = (e) => {
    button.classList.remove('down');
    if (!down) return;
    const dx = e.clientX - down.x;
    const dt = Math.max(30, performance.now() - down.t);
    down = null;
    if (!st.canRoll || st.throwing) return;
    // A flick: its speed in screen widths per second sets the strength.
    const speed = Math.abs(dx) / window.innerWidth / (dt / 1000);
    pendingPower = Math.abs(dx) > 24 ? Math.min(1, 0.35 + speed * 0.35) : 0.3;
    st.canRoll = false;
    button.classList.add('off');
    app.roll(pendingPower);
  };
  strip.addEventListener('pointerup', up);
  strip.addEventListener('pointercancel', () => { down = null; button.classList.remove('down'); });

  const camera = new THREE.PerspectiveCamera(24, 4, 0.1, 100);
  function resize() {
    const w = window.innerWidth;
    const h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    // Fit the strip's length to the screen's width at the floor.
    const dist = 7.2;
    const halfH = Math.tan((camera.fov * Math.PI) / 360) * dist;
    L = Math.max(4, halfH * camera.aspect * 2 - 0.5);
    camera.position.set(0, dist, 0.01);
    camera.lookAt(0, 0, 0);
    camera.updateProjectionMatrix();
    buildWalls();
  }
  window.addEventListener('resize', resize);
  resize();

  function frame(now) {
    requestAnimationFrame(frame);
    const th = st.throwing;
    if (th) {
      const i = Math.max(0, Math.min(th.frames.length - 1, Math.floor((now - th.start) / (1000 / 60))));
      const f = th.frames[i];
      die.position.set(f[0], f[1], f[2]);
      die.quaternion.set(f[3], f[4], f[5], f[6]).multiply(th.turn);
      if (i === th.frames.length - 1) {
        st.throwing = null;
        st.rest = { x: f[0], z: f[2], q: die.quaternion.clone() };
        app.landed(th.count);
      }
    } else if (st.rest) {
      die.position.set(st.rest.x, SIZE / 2, st.rest.z || 0);
      die.quaternion.copy(st.rest.q);
    }
    renderer.render(scene, camera);
  }
  requestAnimationFrame(frame);
  app.ready();
})();
