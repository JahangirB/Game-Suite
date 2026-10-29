#!/usr/bin/env python3
"""Turns the sticker drawings in scripts/art/*.svg into Kotlin the app can draw.

Each sticker is a hand-drawn SVG on a 120x120 grid. This flattens it into plain shapes, in
drawing order, each with its SVG path data, flat fill and stroke, and writes them to
app/src/main/java/com/pippop/ui/art/StickerArt.kt, where ui/art/Stickers.kt paints them.

- Groups, transforms and <use> are baked into the shapes' coordinates.
- A rect with id="backdrop" becomes the sticker's backdrop (a colour or a gradient), and a
  rect with id="frame-line" is left out: the app draws the frame round any sticker with a
  backdrop. A sticker without one (Kylian) is just its figure.
- Captions (<text>) become outlines, from the fonts in scripts/art/fonts: class "cap" is the
  white meme caption in Anton with a black edge; class "comic" is Comic Neue.
- An element with class "a <name> [b|c|d]" is animated by <name> (see Stickers.kt); everything
  inside it moves together.
- A fill that is a gradient becomes its average colour.

    pip install svgelements fonttools brotli
    python3 scripts/make-stickers.py
"""

import math
import os
import re
import xml.etree.ElementTree as ET

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from svgelements import Circle, Ellipse, Matrix, Path, Polygon, Polyline, Rect

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, 'art')
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'java', 'com', 'pippop', 'ui', 'art', 'StickerArt.kt')
STICKERS = {
    'KYLIAN': 'kylian-dictador.svg',
    'SIDE_EYE': 'side-eye.svg',
    'STONKS': 'stonks.svg',
    'GALAXY_BRAIN': 'galaxy-brain.svg',
    'THIS_IS_FINE': 'this-is-fine.svg',
}
SVG = '{http://www.w3.org/2000/svg}'
XLINK = '{http://www.w3.org/1999/xlink}'
PAINT = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'stroke-dasharray',
         'fill-opacity', 'stroke-opacity', 'opacity')
VARIANTS = ('b', 'c', 'd')


class Font:
    def __init__(self, file):
        self.font = TTFont(os.path.join(ART, 'fonts', file))
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.em = self.font['head'].unitsPerEm

    def outline(self, text, size, x, y, anchor, spacing):
        """[text]'s outline as path data, its baseline starting at x, y (or centred there)."""
        s = size / self.em
        names = [self.cmap[ord(c)] for c in text]
        width = sum(self.glyphs[n].width * s + spacing for n in names) - spacing
        pen_x = x - (width / 2 if anchor == 'middle' else 0)
        pen = SVGPathPen(self.glyphs)
        for n in names:
            self.glyphs[n].draw(TransformPen(pen, (s, 0, 0, -s, pen_x, y)))
            pen_x += self.glyphs[n].width * s + spacing
        return pen.getCommands()


CAP = Font('anton-latin.woff2')
COMIC = Font('comicneue-bold-latin.woff2')


def tag(el):
    return el.tag.replace(SVG, '')


def hexcolour(value):
    value = value.strip()
    if len(value) == 4:
        value = '#' + ''.join(c * 2 for c in value[1:])
    return value.lower()


def stops(grad):
    return [(float(s.get('offset', 0)), hexcolour(s.get('stop-color'))) for s in grad.iter(SVG + 'stop')]


def average(colours):
    rgb = [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in colours]
    return '#' + ''.join(f'{round(sum(v[i] for v in rgb) / len(rgb)):02x}' for i in range(3))


def argb(value, opacity, gradients):
    """0xAARRGGBBL, or null for none."""
    if value in (None, 'none'):
        return 'null'
    m = re.match(r'url\(#(.+)\)', value)
    if m:
        value = average([c for _, c in stops(gradients[m.group(1)])])
    value = hexcolour(value)
    return f'0x{round(opacity * 255):02X}{value[1:].upper()}L'


def num(v):
    return f'{round(float(v), 3):g}'


def style(el):
    """The element's own paint attributes, from attributes and its style="..."."""
    out = {k: el.get(k) for k in PAINT if el.get(k) is not None}
    for decl in (el.get('style') or '').split(';'):
        if ':' in decl:
            k, v = (p.strip() for p in decl.split(':', 1))
            if k in PAINT:
                out[k] = v
    return out


def outline(el, defs):
    """The element's shape as an svgelements Path, in its own coordinates."""
    t = tag(el)
    a = el.attrib
    f = lambda k, d=0: float(a.get(k, d))
    if t == 'path':
        return Path(a['d'])
    if t == 'rect':
        return Path(Rect(f('x'), f('y'), f('width'), f('height'), f('rx'), f('ry', a.get('rx', 0))))
    if t == 'circle':
        return Path(Circle(f('cx'), f('cy'), f('r')))
    if t == 'ellipse':
        return Path(Ellipse(f('cx'), f('cy'), f('rx'), f('ry')))
    if t == 'polygon':
        return Path(Polygon(a['points']))
    if t == 'polyline':
        return Path(Polyline(a['points']))
    raise ValueError(f'<{t}> is not supported')


def flatten(svg_file):
    """The sticker's backdrop (or None) and its parts, in drawing order."""
    root = ET.parse(os.path.join(ART, svg_file)).getroot()
    defs = {el.get('id'): el for d in root.iter(SVG + 'defs') for el in d.iter() if el.get('id')}
    gradients = {k: v for k, v in defs.items() if tag(v) in ('linearGradient', 'radialGradient')}
    backdrop = None
    parts = []
    groups = [0]

    def walk(el, matrix, paint, anim, group):
        t = tag(el)
        if t in ('defs', 'clipPath', 'title', 'desc') or el.get('id') == 'frame-line':
            return
        if el.get('transform'):
            matrix = Matrix(el.get('transform')) * matrix
        paint = {**paint, **style(el)}
        classes = (el.get('class') or '').split()
        if 'a' in classes:
            names = [c for c in classes if c not in ('a', 'comic', 'cap') and c not in VARIANTS]
            variant = next((c for c in classes if c in VARIANTS), '')
            anim = (names[0] + (' ' + variant if variant else '')) if names else anim
            groups[0] += 1
            group = groups[0]
        if t in ('svg', 'g'):
            for child in el:
                walk(child, matrix, paint, anim, group)
            return
        if t == 'use':
            ref = defs[(el.get('href') or el.get(XLINK + 'href'))[1:]]
            walk(ref, matrix, paint, anim, group)
            return
        if el.get('id') == 'backdrop':
            nonlocal backdrop
            backdrop = make_backdrop(paint.get('fill'), gradients)
            return
        scale = math.sqrt(abs(matrix.a * matrix.d - matrix.b * matrix.c))
        if t == 'text':
            for d, fill, stroke, width, join in text_outlines(el, paint, classes):
                p = Path(d) * matrix
                p.reify()
                parts.append((p.d(), fill, stroke, width * scale, None, join, join, anim, group))
            return
        p = outline(el, defs) * matrix
        p.reify()
        opacity = float(paint.get('opacity', 1))
        fill = argb(paint.get('fill', '#000'), float(paint.get('fill-opacity', 1)) * opacity, gradients)
        stroke = argb(paint.get('stroke'), float(paint.get('stroke-opacity', 1)) * opacity, gradients)
        width = float(paint.get('stroke-width', 1)) * scale
        dash = paint.get('stroke-dasharray')
        dash = [float(v) * scale for v in re.split(r'[\s,]+', dash.strip())] if dash else None
        parts.append((p.d(), fill, stroke, width, dash, paint.get('stroke-linecap') == 'round',
                      paint.get('stroke-linejoin') == 'round', anim, group))

    walk(root, Matrix(), {}, None, 0)
    return backdrop, parts


def text_outlines(el, paint, classes):
    """A caption's outlines: (path, fill, stroke, width, round) to draw in order."""
    css = el.get('style') or ''
    size = re.search(r'(\d+(?:\.\d+)?)px', css)
    x, y = float(el.get('x', 0)), float(el.get('y', 0))
    anchor = el.get('text-anchor', 'middle' if 'cap' in classes else 'start')
    if 'cap' in classes:
        d = CAP.outline(el.text, float(size.group(1)) if size else 14, x, y, anchor, 0.4)
        # The meme look: a black edge under white letters.
        return [(d, 'null', '0xFF000000L', 3.2, True), (d, '0xFFFFFFFFL', 'null', 1, True)]
    font = COMIC if 'comic' in classes or 'comic' in css else CAP
    d = font.outline(el.text, float(size.group(1)) if size else 10, x, y, anchor, 0)
    return [(d, argb(paint.get('fill', '#000'), 1, {}), 'null', 1, False)]


def make_backdrop(fill, gradients):
    m = re.match(r'url\(#(.+)\)', fill or '')
    if not m:
        return f'Backdrop.solid({argb(fill, 1, {})})'
    g = gradients[m.group(1)]
    colours = ', '.join(argb(c, 1, {}) for _, c in stops(g))
    if tag(g) == 'linearGradient':
        return f'Backdrop.linear(listOf({colours}), {num(g.get("x1"))}f, {num(g.get("y1"))}f, {num(g.get("x2"))}f, {num(g.get("y2"))}f)'
    return f'Backdrop.radial(listOf({colours}), {num(g.get("cx"))}f, {num(g.get("cy"))}f, {num(g.get("r"))}f)'


def kotlin(name, svg_file):
    backdrop, parts = flatten(svg_file)
    lines = [f'    /** From scripts/art/{svg_file}. */', f'    val {name} = Sticker(', f'        {backdrop or "null"},', '        listOf(']
    for d, fill, stroke, width, dash, cap, join, anim, group in parts:
        dash = 'floatArrayOf(' + ', '.join(f'{num(v)}f' for v in dash) + ')' if dash else 'null'
        extra = f', "{anim}", {group}' if anim else ''
        lines.append(f'            Part("{d}", {fill}, {stroke}, {num(width)}f, {dash}, {str(cap).lower()}, {str(join).lower()}{extra}),')
    lines += ['        ),', '    )']
    return lines


def main():
    lines = [
        '// Generated by scripts/make-stickers.py from scripts/art/*.svg. Change the drawings there, not here.',
        'package com.pippop.ui.art',
        '',
        'internal object StickerArt {',
    ]
    for name, svg_file in STICKERS.items():
        lines += kotlin(name, svg_file)
    lines.append('}')
    with open(OUT, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    print(os.path.normpath(OUT))


if __name__ == '__main__':
    main()
