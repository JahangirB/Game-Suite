#!/usr/bin/env python3
"""Designs Mini Golf's 1,000 holes and writes them to app/src/main/assets/golf/courses.jsonl.

The app plays these fixed holes; nothing is made up while you play. Run this again only to
replace the library (a new seed gives a different 1,000):

    python3 scripts/make-golf-courses.py [seed]

One hole per line, ids 0-999 in order: 0-332 easy (one island), 333-665 medium (two islands),
666-999 hard (three islands). Each line:

    {"id": 0, "level": "easy", "par": 4, "len": 3.2, "tee": [x, y], "cup": [x, y],
     "walls": [[x0, y0, x1, y1], ...], "sand": [...], "water": [[x, y, rx, ry], ...],
     "islands": [[x0, y0, x1, y1], ...], "bridges": [...], "zones": [...]}

The course is 1 wide and LEN long, y down, the cup near the top and the tee near the bottom.
Islands float over the sea and have round corners (CORNER); bridges cross the gaps between
them, and a jump shot can clear a gap too. Walls are low wooden blocks: a roll bounces off,
a high jump flies over.

Zones are the sticker spots: none on easy holes, one or two on medium, two or three on hard.

    {"type": "kylian", "box": [...]}             stop on it: Kylian takes your next turn
    {"type": "fire", "box": [...]}               land or roll in: back to the tee
    {"type": "stonks", "box": [...], "dir": [0, -1]}   roll onto it: launched along dir
    {"type": "portal", "at": [x, y], "to": [x, y]}     always in pairs, each to the other
    {"type": "eyes", "box": [...]}               stop on it: no aim line next shot

Every hole is checked playable before it's kept: a dry way from the tee round the walls,
past the fire and over the bridges to the cup, wide enough for the ball. The app's tests check
the same again (CoursesTest).
"""

import json
import math
import os
import random
import sys

W, LEN = 1.0, 3.2
R = 0.022           # the ball's radius (Golf.R)
CORNER = 0.04       # how round an island's corners are (Golf.CORNER)
CLEAR = 0.16        # room kept clear round the tee and the cup
SIZE, MEDIUM, HARD = 1000, 333, 666
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'golf', 'courses.jsonl')


def rnd(v):
    return round(v, 3)


def box(x0, y0, x1, y1):
    return (rnd(x0), rnd(y0), rnd(x1), rnd(y1))


def overlaps(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def grow(b, by):
    return (b[0] - by, b[1] - by, b[2] + by, b[3] + by)


def inside(b, x, y):
    return b[0] <= x <= b[2] and b[1] <= y <= b[3]


def inside_rounded(b, x, y, r):
    if not inside(b, x, y):
        return False
    cx = min(max(x, b[0] + r), b[2] - r)
    cy = min(max(y, b[1] + r), b[3] - r)
    return math.hypot(x - cx, y - cy) <= r


def in_pond(p, x, y):
    dx = (x - p[0]) / p[2]
    dy = (y - p[1]) / p[3]
    return dx * dx + dy * dy <= 1


class Placer:
    """Puts boxes on islands, clear of the tee, the cup and everything already placed."""

    def __init__(self, rng, islands, tee, cup, keep_clear):
        self.rng = rng
        self.islands = islands
        self.taken = list(keep_clear) + [(p[0] - CLEAR, p[1] - CLEAR, p[0] + CLEAR, p[1] + CLEAR) for p in (tee, cup)]

    def place(self, w, h, island=None, inset=0.05, gap=0.06):
        for _ in range(40):
            area = island or self.rng.choice(self.islands)
            if area[2] - area[0] - 2 * inset < w or area[3] - area[1] - 2 * inset < h:
                continue
            x0 = self.rng.uniform(area[0] + inset, area[2] - inset - w)
            y0 = self.rng.uniform(area[1] + inset, area[3] - inset - h)
            b = box(x0, y0, x0 + w, y0 + h)
            if any(overlaps(t, b) for t in self.taken):
                continue
            self.taken.append(grow(b, gap))
            return b
        return None


def islands_for(rng, count):
    """[count] islands up the course, a gap between each; one island fills the course."""
    if count == 1:
        width = rng.uniform(0.8, 1.0)
        x0 = rng.uniform(0.0, 1.0 - width)
        return [box(x0, 0.05, x0 + width, LEN - 0.05)], []
    gaps = [rng.uniform(0.16, 0.34) for _ in range(count - 1)]
    room = LEN - 0.1 - sum(gaps)
    shares = [rng.uniform(0.8, 1.2) for _ in range(count)]
    heights = [s / sum(shares) * room for s in shares]
    if any(h < 0.55 for h in heights):
        return None, None
    y = 0.05
    islands = []
    for i, h in enumerate(heights):
        narrow = count == 3 and i == 1
        width = rng.uniform(0.55, 0.8) if narrow else rng.uniform(0.75, 1.0)
        x0 = rng.uniform(0.0, 1.0 - width)
        islands.append(box(x0, y, x0 + width, y + h))
        y += h + (gaps[i] if i < len(gaps) else 0)
    # A bridge over every gap, often well off the straight line, so jumping it is the shortcut.
    bridges = []
    for i in range(count - 1):
        upper, lower = islands[i], islands[i + 1]
        lo = max(upper[0], lower[0]) + 0.07   # clear of the islands' round corners
        hi = min(upper[2], lower[2]) - 0.07
        width = rng.uniform(0.11, 0.16)
        if hi - lo < width:
            return None, None
        x0 = rng.uniform(lo, hi - width)
        bridges.append(box(x0, upper[3] - 0.02, x0 + width, lower[1] + 0.02))
    return islands, bridges


def design_once(rng, count):
    islands, bridges = islands_for(rng, count)
    if islands is None:
        return None
    top, bottom = islands[0], islands[-1]
    if top[2] - top[0] < 0.4 or bottom[2] - bottom[0] < 0.4:
        return None
    cup = (rnd(rng.uniform(top[0] + 0.15, top[2] - 0.15)), rnd(rng.uniform(top[1] + 0.14, top[1] + 0.32)))
    tee = (rnd(rng.uniform(bottom[0] + 0.15, bottom[2] - 0.15)), rnd(rng.uniform(bottom[3] - 0.2, bottom[3] - 0.12)))
    landings = [(b[0] - 0.06, b[1] - 0.12, b[2] + 0.06, b[3] + 0.12) for b in bridges]
    place = Placer(rng, islands, tee, cup, landings)

    walls = []
    for _ in range(rng.randint(3, 5) if count == 1 else rng.randint(2, 4)):
        if rng.random() < 0.6:
            b = place.place(rng.uniform(0.3, 0.6), 0.04, gap=0.08)
        else:
            b = place.place(0.04, rng.uniform(0.25, 0.5), gap=0.08)
        if b:
            walls.append(b)
    sand = [b for b in (place.place(rng.uniform(0.2, 0.4), rng.uniform(0.12, 0.22)) for _ in range(rng.randint(0, 2))) if b]
    water = []
    for _ in range(1 if rng.random() < 0.5 else 0):
        rx, ry = rnd(rng.uniform(0.07, 0.14)), rnd(rng.uniform(0.05, 0.1))
        b = place.place(2 * rx, 2 * ry)
        if b:
            water.append((rnd((b[0] + b[2]) / 2), rnd((b[1] + b[3]) / 2), rx, ry))

    zones = []
    want = 0 if count == 1 else rng.randint(1, 2) if count == 2 else rng.randint(2, 3)
    kinds = ['kylian', 'fire', 'stonks', 'portal', 'eyes']
    rng.shuffle(kinds)
    for kind in kinds[:want]:
        if kind == 'kylian':
            b = place.place(rng.uniform(0.24, 0.32), rng.uniform(0.22, 0.28))
            z = b and {'type': 'kylian', 'box': list(b)}
        elif kind == 'fire':
            b = place.place(rng.uniform(0.24, 0.36), rng.uniform(0.18, 0.3))
            z = b and {'type': 'fire', 'box': list(b)}
        elif kind == 'stonks':
            # Not on the top island: it throws the ball up the course, towards the cup.
            b = place.place(0.18, 0.15, island=rng.choice(islands[1:]))
            z = b and {'type': 'stonks', 'box': list(b), 'dir': [0, -1]}
        elif kind == 'eyes':
            b = place.place(0.2, 0.24)
            z = b and {'type': 'eyes', 'box': list(b)}
        else:
            # Two portals on different islands, each sending the ball to the other.
            a = place.place(0.12, 0.12, island=islands[-1])
            b = place.place(0.12, 0.12, island=rng.choice(islands[:-1]))
            z = None
            if a and b:
                pa = [rnd((a[0] + a[2]) / 2), rnd((a[1] + a[3]) / 2)]
                pb = [rnd((b[0] + b[2]) / 2), rnd((b[1] + b[3]) / 2)]
                zones.append({'type': 'portal', 'at': pa, 'to': pb})
                z = {'type': 'portal', 'at': pb, 'to': pa}
        if z:
            zones.append(z)
    if len({z['type'] for z in zones}) < want:
        return None
    return dict(par=3 + count, len=LEN, tee=tee, cup=cup, walls=walls, sand=sand, water=water,
                islands=islands, bridges=bridges, zones=zones)


def on_course(hole, x, y):
    return any(inside_rounded(i, x, y, CORNER) for i in hole['islands']) or any(inside(b, x, y) for b in hole['bridges'])


def playable(hole):
    """A dry way from the tee to the cup: on the course, out of the water and the fire, round the walls, room for the ball."""
    g = 0.02
    cols, rows = int(W / g), int(LEN / g)
    fire = [z['box'] for z in hole['zones'] if z['type'] == 'fire']

    def open_cell(c, r):
        x, y = (c + 0.5) * g, (r + 0.5) * g
        if not all(on_course(hole, px, py) for px, py in ((x, y), (x - R, y), (x + R, y), (x, y - R), (x, y + R))):
            return False
        if any(in_pond(p, x, y) for p in hole['water']):
            return False
        if any(inside(grow(b, R), x, y) for b in fire):
            return False
        return not any(x > b[0] - R and x < b[2] + R and y > b[1] - R and y < b[3] + R for b in hole['walls'])

    def cell(p):
        return min(max(int(p[0] / g), 0), cols - 1), min(max(int(p[1] / g), 0), rows - 1)

    tc, tr = cell(hole['tee'])
    cc, cr = cell(hole['cup'])
    if not open_cell(tc, tr) or not open_cell(cc, cr):
        return False
    seen = {(tc, tr)}
    queue = [(tc, tr)]
    while queue:
        c, r = queue.pop()
        if (c, r) == (cc, cr):
            return True
        for dc, dr in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c + dc, r + dr)
            if 0 <= n[0] < cols and 0 <= n[1] < rows and n not in seen and open_cell(*n):
                seen.add(n)
                queue.append(n)
    return False


def level(i):
    return 'easy' if i < MEDIUM else 'medium' if i < HARD else 'hard'


def design(i, seed):
    count = 1 if i < MEDIUM else 2 if i < HARD else 3
    for attempt in range(300):
        rng = random.Random(f'{seed}-{i}-{attempt}')
        hole = design_once(rng, count)
        if hole and playable(hole):
            return hole
    raise SystemExit(f'hole {i}: no playable design')


def main():
    seed = sys.argv[1] if len(sys.argv) > 1 else 'pip-and-pop-3d'
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        for i in range(SIZE):
            hole = design(i, seed)
            line = {'id': i, 'level': level(i), **hole}
            for key in ('tee', 'cup'):
                line[key] = list(line[key])
            for key in ('walls', 'sand', 'water', 'islands', 'bridges'):
                line[key] = [list(v) for v in line[key]]
            f.write(json.dumps(line, separators=(',', ':')) + '\n')
    print(f'{SIZE} holes written to {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
