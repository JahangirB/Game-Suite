// The Ship Battle skins people can download in the game, drawn with the kit (kit.js, helpers.js).
// scripts/make-skins.js records each one into skins/<id>.json for the app; see skins/README.md.
//
// A skin is { id, name, tag, about, version, sea(c, s), ship(c, kind, L, w, s), hit(c, x, y, s),
// miss(c, x, y, s) } drawing on a 2D canvas with s pixels to a square. Raise version when you
// change a skin that's already published, so phones that have it download it again.
const S = (cat, name, tag, about, sea, ship, hit, miss) => ({ id: name.toLowerCase().replace(/[^a-z0-9]+/g, '-'), name, tag, about, version: 1, sea, ship, hit, miss });

const PASTEL = ['#ff8fb1', '#ffd23f', '#8fd3ff', '#b48cff', '#7ee0a1'];

const SKINS = [
  // Candy Cove: a pink lemonade sea, a fleet made of sweets.
  { id: 'candy-cove', version: 1, tag: 'Sweets on a pink lemonade sea', about: 'chocolate, licorice, gummy worms and jelly beans', name: 'Candy Cove', blurb: 'A pink lemonade sea with sprinkles. The fleet is made of sweets, and a hit scatters sprinkles round a cherry.',
    ships: ['chocolate bar', 'licorice twist', 'gummy worm', 'jelly bean', 'wrapped sweet'],
    hitName: 'Sprinkles and a cherry', missName: 'Marshmallow',
    sea(c, s) {
      c.fillStyle = '#ffc8de'; c.fillRect(0, 0, N * s, N * s);
      const r = rng(9);
      for (let k = 0; k < 22; k++) { const x = r() * N * s, y = r() * N * s; c.beginPath(); c.arc(x, y, s * (0.3 + r() * 0.4), 0.2, 2.4); c.strokeStyle = 'rgba(255,255,255,0.45)'; c.lineWidth = s * 0.05; c.lineCap = 'round'; c.stroke(); }
      const cols = ['#ff5a7a', '#ffd23f', '#4cc2e6', '#6ad07a', '#9d7bff'];
      for (let k = 0; k < 60; k++) { const x = r() * N * s, y = r() * N * s, a = r() * TAU; line(c, x, y, x + Math.cos(a) * s * 0.1, y + Math.sin(a) * s * 0.1, cols[k % 5], s * 0.035); }
      grid(c, s, 'rgba(255,255,255,0.6)', 1.5);
    },
    ship(c, kind, L, w, s) {
      const out = '#5a2340';
      if (kind === 'carrier') {
        rrect(c, 0, -w * 1.05, L, w * 2.1, w * 0.3); paint(c, '#6b3b23', '#3a1d10', s * 0.03);
        for (let i = 0; i < 4; i++) for (const y of [-1, 0]) { rrect(c, L * 0.2 + i * L * 0.2 + s * 0.04, y * w + s * 0.05 - (y ? 0 : 0), L * 0.2 - s * 0.08, w - s * 0.07, s * 0.05); paint(c, '#8a5232', 'rgba(58,29,16,0.6)', s * 0.015); }
        rrect(c, 0, -w * 1.08, L * 0.2, w * 2.16, w * 0.3); paint(c, '#d9dde3', '#7d838c', s * 0.02);
        for (let k = 1; k < 4; k++) line(c, L * 0.05 * k, -w, L * 0.05 * k + s * 0.03, w, 'rgba(125,131,140,0.6)', s * 0.012);
      } else if (kind === 'battleship') {
        rrect(c, 0, -w * 0.8, L, w * 1.6, w * 0.8); paint(c, '#d6283a');
        rrect(c, 0, -w * 0.8, L, w * 1.6, w * 0.8); clipped(c, () => { for (let x = -w * 2; x < L + w * 2; x += s * 0.2) line(c, x, w, x + w * 1.6, -w, '#9e1424', s * 0.06); });
        rrect(c, 0, -w * 0.8, L, w * 1.6, w * 0.8); paint(c, null, out, s * 0.03);
        line(c, w * 0.6, -w * 0.4, L - w * 0.6, -w * 0.4, 'rgba(255,255,255,0.35)', s * 0.04);
      } else if (kind === 'cruiser') {
        const cols = ['#ff6b6b', '#ffd23f', '#4cd47a'];
        const n = 7;
        for (let i = 0; i < n; i++) { const x = w * 0.9 + i * (L - w * 1.8) / (n - 1), y = Math.sin(i * 0.9) * w * 0.25; circle(c, x, y, w * 0.85); paint(c, cols[Math.floor(i / 3) % 3] + 'ee', out, s * 0.025); }
        dot(c, L - w * 0.6, -w * 0.3, w * 0.13, '#1e2a3a'); dot(c, L - w * 0.6, w * 0.3, w * 0.13, '#1e2a3a');
      } else if (kind === 'submarine') {
        c.beginPath(); c.moveTo(w, -w * 0.9); c.bezierCurveTo(L * 0.4, -w * 1.1, L * 0.6, -w * 0.4, L - w, -w * 0.9); c.arc(L - w, 0, w * 0.9, -Math.PI / 2, Math.PI / 2); c.bezierCurveTo(L * 0.6, w * 0.2, L * 0.4, w * 1.1, w, w * 0.9); c.arc(w, 0, w * 0.9, Math.PI / 2, -Math.PI / 2); c.closePath();
        paint(c, '#8e5cf0', out, s * 0.03); ellipse(c, L * 0.35, -w * 0.4, L * 0.15, w * 0.18); paint(c, 'rgba(255,255,255,0.45)');
      } else {
        for (const end of [0, 1]) { const x = end ? L : 0, d = end ? -1 : 1; c.beginPath(); c.moveTo(x + d * w * 0.9, 0); c.lineTo(x, -w * 0.9); c.lineTo(x + d * w * 0.3, 0); c.lineTo(x, w * 0.9); c.closePath(); paint(c, '#ffd6a5', out, s * 0.025); }
        ellipse(c, L / 2, 0, L / 2 - w * 0.8, w * 0.95); paint(c, '#ff8a3d', out, s * 0.03); line(c, L * 0.3, -w * 0.3, L * 0.7, -w * 0.3, 'rgba(255,255,255,0.5)', s * 0.04);
      }
    },
    hit(c, x, y, s) {
      const cols = ['#ff5a7a', '#ffd23f', '#4cc2e6', '#6ad07a', '#9d7bff', '#ffffff'];
      for (let k = 0; k < 10; k++) { const a = k * TAU / 10; line(c, x + Math.cos(a) * s * 0.2, y + Math.sin(a) * s * 0.2, x + Math.cos(a) * s * 0.34, y + Math.sin(a) * s * 0.34, cols[k % 6], s * 0.05); }
      line(c, x, y - s * 0.05, x + s * 0.1, y - s * 0.24, '#3a7a2a', s * 0.03); dot(c, x, y, s * 0.13, '#d81b3a'); dot(c, x - s * 0.04, y - s * 0.04, s * 0.04, 'rgba(255,255,255,0.7)');
    },
    miss(c, x, y, s) { rrect(c, x - s * 0.15, y - s * 0.13, s * 0.3, s * 0.3, s * 0.1); paint(c, '#ffd9e8'); rrect(c, x - s * 0.15, y - s * 0.17, s * 0.3, s * 0.3, s * 0.1); paint(c, '#ffffff', '#c98aa6', s * 0.02); },
},
  S('sweets', 'Popsicle Pier', 'Ice lollies on a summer sea', 'ice lollies on sticks', SEA.waves('#3fd2d9', 'rgba(255,255,255,0.5)', 'rgba(255,255,255,0.35)'), LONG.popsicle(['#ff6b8a', '#ffb13d', '#6ad07a', '#9d7bff', '#ff4f5e']), HIT.splat('#ff6b8a'), MISS.cube('rgba(255,255,255,0.85)')),
  S('food', 'Breakfast Table', 'Eggs, bacon and sausages on gingham', 'bacon, sausages and fried eggs', SEA.gingham('#ffffff', 'rgba(80,140,220,0.25)', 'rgba(80,140,220,0.3)'), (c, kind, L, w, s) => (kind === 'submarine' || kind === 'destroyer' ? row(ITEM.egg, PASTEL) : LONG.sausage())(c, kind, L, w, s), HIT.splat('#d6303a'), MISS.dot('#c98d48')),
  S('food', 'Bakery', 'Baguettes on a floury board', 'crusty baguettes', SEA.wood('#e8c99a', 'rgba(150,100,50,0.35)'), LONG.baguette(), HIT.puff('#ffffff'), MISS.dot('#f5e6c8')),
  S('animals', 'Caterpillar Crawl', 'Caterpillars on a sunny leaf', 'wiggly caterpillars', SEA.grass('#7cc04f', 'rgba(60,120,40,0.5)'), LONG.caterpillar(['#3ddc84', '#ff8a3d', '#9d7bff', '#4cc2e6', '#ff4f9a']), HIT.burst('#ff8fb1', '#ffd23f'), MISS.leaf('#4f9a3a')),
  S('toys', 'Brick Builders', 'Toy bricks on a green baseplate', 'toy bricks with studs', SEA.dots('#5cc46b', 'rgba(255,255,255,0.25)', 'rgba(0,0,0,0.15)', 0.1), LONG.brick(['#e5302a', '#2f6fb3', '#ffd23f', '#2fbf6a', '#ff8a1f']), HIT.burst('#ffd23f'), MISS.item(ITEM.pearl, '#ffffff')),
  S('toys', 'Train Set', 'Wooden trains on the play table', 'a locomotive and carriages', SEA.wood('#d9a86a', 'rgba(120,70,30,0.35)'), LONG.train(['#2f6fb3', '#2fbf6a', '#ffd23f', '#9d7bff', '#ff8a1f']), HIT.puff('rgba(255,255,255,0.9)'), MISS.ring('#5a3a1e')),
  S('toys', 'Pencil Case', 'Coloured pencils on graph paper', 'sharpened pencils', SEA.paper('#ffffff', 'rgba(100,170,220,0.35)'), LONG.pencil(['#ffd23f', '#4cc2e6', '#ff8fb1', '#6ad07a', '#ff9a1f']), HIT.x('#3a3a40'), MISS.ring('#3a3a40')),
  S('toys', 'Domino Run', 'Dominoes on green felt', 'dominoes with real pips', SEA.felt('#2f7a4a'), LONG.domino(), HIT.star('#ffd23f'), MISS.dot('#ffffff')),
  S('vehicles', 'Sky Fleet', 'Planes over the clouds', 'aeroplanes, wingtip to wingtip', SEA.sky('#6ec3ff', '#d6f0ff', 'rgba(255,255,255,0.75)'), LONG.plane(['#e5302a', '#2f6fb3', '#ffc61a', '#2fbf6a', '#9d7bff']), HIT.puff('rgba(70,70,80,0.75)'), MISS.item(ITEM.cloud, '#ffffff')),
  S('vehicles', 'Zeppelin Skies', 'Airships at sunset', 'airships with gondolas', SEA.sky('#ff9a6b', '#ffd6a0', 'rgba(255,255,255,0.45)'), LONG.blimp(['#c9ccd4', '#e8b923', '#b8322a', '#7a8ea0', '#9d7bff']), HIT.flame(), MISS.ring('#ffffff')),
  S('vehicles', 'Submarine Squad', 'A fleet of submarines, deep down', 'portholed submarines', SEA.bubbles('#0b4a6b', 'rgba(150,220,255,0.6)', 'rgba(150,220,255,0.15)'), LONG.sub(['#ffd23f', '#ff8a1f', '#3ddc84', '#ff4f9a', '#4cc2e6']), HIT.burst('#ff8a1f'), MISS.rings('#7fd6ff')),
  S('art', 'Steampunk Brass', 'Brass ships with gears on copper water', 'brass hulls, rivets and gears', SEA.waves('#2f5a52', 'rgba(200,170,110,0.3)', 'rgba(200,170,110,0.2)'), LONG.brassShip(), HIT.burst('#e8c070', '#5a3a10', '#5a3a10'), MISS.ring('#c9a46a')),
];
