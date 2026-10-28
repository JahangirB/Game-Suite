#!/usr/bin/env python3
"""Designs Mini Golf's 1,000 holes and writes them to app/src/main/assets/golf/courses.jsonl.

The app plays these fixed holes; nothing is made up while you play. Run this again only to
replace the library (a new seed gives a different 1,000):

    python3 scripts/make-golf-courses.py [seed]

One hole per line, ids 0-999 in order: 0-332 easy (one piece of ground), 333-665 medium (two
islands and a bridge), 666-999 hard (three islands, two bridges). Each line:

    {"id": 0, "level": "easy", "par": 2, "tee": [x, y], "cup": [x, y],
     "walls": [[x0, y0, x1, y1], ...], "sand": [...], "cuts": [...],
     "water": [[x, y, rx, ry], ...], "islands": [[x0, y0, x1, y1], ...] or null, "bridges": [...]}

The course is 1 wide and 1.6 long, y down. Islands float over a drop and have round corners
(CORNER, as the app draws them); bridges are plain boxes over the gaps. Every hole is checked
playable before it's kept: a dry way from the tee round the walls and over the bridges to the
cup, wide enough for the ball. The app's tests check the same again (CoursesTest).
"""

import json
import math
import os
import random
import sys

W, H = 1.0, 1.6
R = 0.022           # the ball's radius (Golf.R)
CORNER = 0.04       # how round an island's corners are (Golf.CORNER)
CLEAR = 0.14        # room kept clear round the tee and the cup
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


def obstacles(rng, area, tee, cup, keep_clear, walls, sand, ponds):
    """Walls, sand and ponds over [area], clear of the tee, the cup, each other and [keep_clear]."""
    taken = list(keep_clear)

    def free(b):
        if any(overlaps(t, b) for t in taken):
            return False
        return not any(overlaps(b, (px - CLEAR, py - CLEAR, px + CLEAR, py + CLEAR)) for px, py in (tee, cup))

    def place(w, h, inset):
        for _ in range(30):
            x0 = rng.uniform(area[0] + inset, area[2] - inset - w)
            y0 = rng.uniform(area[1] + inset, area[3] - inset - h)
            b = box(x0, y0, x0 + w, y0 + h)
            if b[0] >= area[0] + inset and b[1] >= area[1] + inset and b[2] <= area[2] - inset and b[3] <= area[3] - inset and free(b):
                taken.append(grow(b, 0.07))
                return b
        return None

    out_walls = []
    for _ in range(walls):
        b = place(rng.uniform(0.14, 0.34), rng.uniform(0.05, 0.09), 0.06) if rng.random() < 0.5 else place(rng.uniform(0.05, 0.09), rng.uniform(0.12, 0.26), 0.06)
        if b:
            out_walls.append(b)
    out_sand = [b for b in (place(rng.uniform(0.18, 0.4), rng.uniform(0.1, 0.2), 0.05) for _ in range(sand)) if b]
    out_ponds = []
    for _ in range(ponds):
        rx, ry = rnd(rng.uniform(0.07, 0.15)), rnd(rng.uniform(0.05, 0.11))
        b = place(2 * rx, 2 * ry, 0.05)
        if b:
            out_ponds.append((rnd((b[0] + b[2]) / 2), rnd((b[1] + b[3]) / 2), rx, ry))
    return out_walls, out_sand, out_ponds


def one_island(rng):
    """Easy: one piece of ground, a few walls, maybe sand, maybe a pond, maybe a bite out of a side."""
    tee = (rnd(rng.uniform(0.2, 0.8)), rnd(rng.uniform(1.36, 1.44)))
    cup = (rnd(rng.uniform(0.18, 0.82)), rnd(rng.uniform(0.2, 0.32)))
    cuts = []
    if rng.random() < 0.35:
        y = rng.uniform(0.6, 1.0)
        reach = rng.uniform(0.35, 0.6)
        cuts = [box(0.0, y, reach, y + 0.08) if rng.random() < 0.5 else box(1.0 - reach, y, 1.0, y + 0.08)]
    walls, sand, ponds = obstacles(rng, (0.0, 0.0, W, H), tee, cup, [grow(c, 0.06) for c in cuts],
                                   rng.randint(1, 3), rng.randint(0, 1), 1 if rng.random() < 0.45 else 0)
    return dict(par=2, tee=tee, cup=cup, walls=walls, sand=sand, cuts=cuts, water=ponds, islands=None, bridges=[])


def floating(rng, count):
    """Medium and hard: [count] islands up the course, a gap between each, a narrow bridge over every gap."""
    gaps = [rng.uniform(0.13, 0.22 if count == 3 else 0.26) for _ in range(count - 1)]
    room = 1.5 - sum(gaps)
    shares = [rng.uniform(0.85, 1.15) for _ in range(count)]
    heights = [s / sum(shares) * room for s in shares]
    if any(h < 0.3 for h in heights):
        return None
    y = 0.05
    islands = []
    for i, h in enumerate(heights):
        narrow = count == 3 and i == 1
        width = rng.uniform(0.5, 0.75) if narrow else rng.uniform(0.72, 1.0)
        x0 = rng.uniform(0.0, 1.0 - width)
        islands.append(box(x0, y, x0 + width, y + h))
        y += h + (gaps[i] if i < len(gaps) else 0)
    bridges = []
    for i in range(count - 1):
        upper, lower = islands[i], islands[i + 1]
        lo = max(upper[0], lower[0]) + 0.07   # clear of the islands' round corners
        hi = min(upper[2], lower[2]) - 0.07
        width = rng.uniform(0.1, 0.15) if count == 3 else rng.uniform(0.12, 0.18)
        if hi - lo < width:
            return None
        x0 = rng.uniform(lo, hi - width)
        bridges.append(box(x0, upper[3] - 0.02, x0 + width, lower[1] + 0.02))
    top, bottom = islands[0], islands[-1]
    if top[2] - top[0] < 0.3 or bottom[2] - bottom[0] < 0.3:
        return None
    cup = (rnd(rng.uniform(top[0] + 0.14, top[2] - 0.14)), rnd(rng.uniform(top[1] + 0.12, top[1] + 0.2)))
    tee = (rnd(rng.uniform(bottom[0] + 0.14, bottom[2] - 0.14)), rnd(rng.uniform(bottom[3] - 0.18, bottom[3] - 0.12)))
    landings = [(b[0] - 0.05, b[1] - 0.1, b[2] + 0.05, b[3] + 0.1) for b in bridges]
    walls, sand, ponds = [], [], []
    for island in islands:
        w, s, p = obstacles(rng, island, tee, cup, landings + walls + sand + [(q[0] - q[2], q[1] - q[3], q[0] + q[2], q[1] + q[3]) for q in ponds],
                            rng.randint(0, 1 if count == 3 else 2), 1 if rng.random() < 0.3 else 0,
                            1 if rng.random() < (0.4 if count == 3 else 0.5) else 0)
        walls += w
        sand += s
        ponds += p
    return dict(par=count + 1, tee=tee, cup=cup, walls=walls, sand=sand, cuts=[], water=ponds, islands=islands, bridges=bridges)


def on_course(hole, x, y):
    if hole['islands'] is None:
        return True
    return any(inside_rounded(i, x, y, CORNER) for i in hole['islands']) or any(inside(b, x, y) for b in hole['bridges'])


def playable(hole):
    """A dry way from the tee to the cup: on the course, out of the water, round the walls, room for the ball."""
    g = 0.02
    cols, rows = int(W / g), int(H / g)
    solid = hole['walls'] + hole['cuts']

    def open_cell(c, r):
        x, y = (c + 0.5) * g, (r + 0.5) * g
        if x < R or x > W - R or y < R or y > H - R:
            return False
        if not all(on_course(hole, px, py) for px, py in ((x, y), (x - R, y), (x + R, y), (x, y - R), (x, y + R))):
            return False
        if any(in_pond(p, x, y) for p in hole['water']):
            return False
        return not any(x > b[0] - R and x < b[2] + R and y > b[1] - R and y < b[3] + R for b in solid)

    def cell(p):
        return min(max(int(p[0] / g), 0), cols - 1), min(max(int(p[1] / g), 0), rows - 1)

    tc, tr = cell(hole['tee'])
    cc, cr = cell(hole['cup'])
    if not open_cell(tc, tr) or not open_cell(cc, cr):
        return False
    if math.hypot(hole['tee'][0] - hole['cup'][0], hole['tee'][1] - hole['cup'][1]) < 0.5:
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
    for attempt in range(200):
        rng = random.Random(f'{seed}-{i}-{attempt}')
        hole = one_island(rng) if count == 1 else floating(rng, count)
        if hole and playable(hole):
            return hole
    raise SystemExit(f'hole {i}: no playable design')


def main():
    seed = sys.argv[1] if len(sys.argv) > 1 else 'pip-and-pop'
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        for i in range(SIZE):
            hole = design(i, seed)
            line = {'id': i, 'level': level(i), **hole}
            for key in ('tee', 'cup'):
                line[key] = list(line[key])
            for key in ('walls', 'sand', 'cuts', 'water', 'bridges'):
                line[key] = [list(v) for v in line[key]]
            if line['islands'] is not None:
                line['islands'] = [list(v) for v in line['islands']]
            f.write(json.dumps(line, separators=(',', ':')) + '\n')
    print(f'{SIZE} holes written to {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
