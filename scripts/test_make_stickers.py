#!/usr/bin/env python3
"""A tiny check of the downloadable stickers: make-stickers.py writes valid JSON for the sample,
what it writes is what is committed in stickers/, and every sticker the index lists is a file
the app will accept (the limits are in stickers/README.md).

    python3 scripts/test_make_stickers.py
"""
import importlib.util
import json
import math
import os
import re
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
STICKERS = os.path.join(HERE, '..', 'stickers')
ID = re.compile(r'^[a-z0-9-]{1,32}$')
COLOUR = re.compile(r'^#[0-9A-F]{8}$')
PATH = re.compile(r'^[MmLlHhVvCcSsQqTtAaZz0-9eE+\-., \n]+$')

spec = importlib.util.spec_from_file_location('make_stickers', os.path.join(HERE, 'make-stickers.py'))
make = importlib.util.module_from_spec(spec)
spec.loader.exec_module(make)


def check_sticker(test, o, sticker_id):
    test.assertEqual(o['id'], sticker_id)
    test.assertRegex(sticker_id, ID)
    test.assertTrue(0 < len(o['name']) <= 24)
    test.assertTrue(0 < len(o['parts']) <= make.MAX_PARTS)
    for part in o['parts']:
        test.assertTrue(0 < len(part['d']) <= 8000)
        test.assertRegex(part['d'], PATH)
        for k in ('fill', 'stroke'):
            test.assertTrue(part[k] is None or COLOUR.match(part[k]), part[k])
        test.assertTrue(math.isfinite(part['width']) and 0 <= part['width'] <= 50)
        test.assertTrue(part['dash'] is None or (len(part['dash']) <= 8 and all(math.isfinite(v) and v > 0 for v in part['dash'])))
    b = o.get('backdrop')
    if b:
        test.assertIn(b['type'], ('solid', 'linear', 'radial'))
        test.assertTrue(all(COLOUR.match(c) for c in b['colours']))


class PackStickers(unittest.TestCase):
    def test_the_sample_is_valid_json(self):
        o = json.loads(json.dumps(make.pack_json('sunny-day', os.path.join('packs', 'sunny-day.svg'), 'Sunny day')))
        check_sticker(self, o, 'sunny-day')
        self.assertEqual(o['backdrop']['type'], 'linear')
        self.assertTrue(any(p.get('anim') == 'glow' for p in o['parts']))

    def test_the_committed_file_is_what_the_script_writes(self):
        with open(os.path.join(STICKERS, 'sunny-day.json')) as f:
            committed = json.load(f)
        again = make.pack_json('sunny-day', os.path.join('packs', 'sunny-day.svg'), 'Sunny day')
        self.assertEqual(committed, json.loads(json.dumps(again)))

    def test_every_listed_sticker_has_a_valid_file(self):
        with open(os.path.join(STICKERS, 'index.json')) as f:
            listed = json.load(f)['stickers']
        self.assertTrue(listed)
        ids = [e['id'] for e in listed]
        self.assertEqual(len(ids), len(set(ids)))
        for e in listed:
            path = os.path.join(STICKERS, e['id'] + '.json')
            self.assertLessEqual(os.path.getsize(path), make.MAX_BYTES)
            if 'size' in e:
                self.assertEqual(e['size'], os.path.getsize(path))
            with open(path) as f:
                check_sticker(self, json.load(f), e['id'])


if __name__ == '__main__':
    unittest.main()
