#!/usr/bin/env python3
"""Makes Game Suite's sound effects from scratch (no recordings, so no licence questions).

Each sound in docs/SFX.md marked priority 1 is built here from tones and noise, in the
"Toy Box" style: soft, rounded, short. Run it to (re)make app/src/main/res/raw/sfx_*.ogg:

    python3 scripts/make-sounds.py

Needs numpy and ffmpeg (with libvorbis). Change a sound by changing its function and running
the script again; levels per sound are set in the app (ui/Sfx.kt), not here.
"""

import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
rng = np.random.default_rng(7)


# --- Building blocks --------------------------------------------------------------------------

class Snd(np.ndarray):
    """Samples that can be added to samples of another length: the shorter one is padded."""

    def __add__(self, other):
        if isinstance(other, np.ndarray) and other.ndim == 1 and len(other) != len(self):
            n = max(len(self), len(other))
            a = np.zeros(n)
            a[:len(self)] += np.asarray(self)
            a[:len(other)] += np.asarray(other)
            return a.view(Snd)
        return np.ndarray.__add__(self, other)

    __radd__ = __add__


def t(ms):
    return np.arange(int(RATE * ms / 1000)) / RATE


def silence(ms):
    return np.zeros(int(RATE * ms / 1000))


def env(n, attack_ms=2, decay=8.0):
    """A quick attack then an exponential decay; [decay] is how fast (bigger = shorter)."""
    x = np.arange(n) / RATE
    a = np.minimum(1, x / max(attack_ms / 1000, 1e-4))
    return a * np.exp(-decay * x)


def tone(freq, ms, decay=8.0, attack_ms=2, shape='sine', glide=1.0, vibrato=0.0):
    """A note at [freq] Hz; [glide] is the end frequency as a share of the start."""
    x = t(ms)
    f = freq * (glide ** (x / max(x[-1], 1e-6))) if len(x) else x
    if vibrato:
        f = f * (1 + 0.012 * np.sin(2 * np.pi * vibrato * x))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    if shape == 'sine':
        w = np.sin(phase)
    elif shape == 'soft':  # a rounded square: warm, toy-like
        w = np.tanh(2.5 * np.sin(phase))
    elif shape == 'saw':
        w = 2 * ((phase / (2 * np.pi)) % 1) - 1
    else:
        raise ValueError(shape)
    return (w * env(len(x), attack_ms, decay)).view(Snd)


def bell(freq, ms, decay=5.0):
    """A small bell or chime: a few inharmonic partials, the high ones dying first."""
    out = np.zeros(len(t(ms))).view(Snd)
    for ratio, amp, d in [(1, 1, 1), (2.76, 0.45, 1.8), (5.4, 0.25, 3), (8.9, 0.12, 4.5)]:
        out += amp * tone(freq * ratio, ms, decay=decay * d, attack_ms=1)
    return out


def noise(ms, decay=20.0, attack_ms=1, color=0.0):
    """White noise with an envelope; [color] 0..1 smooths it toward a dull rumble."""
    n = len(t(ms))
    w = rng.uniform(-1, 1, n)
    if color:
        w = lowpass(w, color)
    return (w * env(n, attack_ms, decay)).view(Snd)


def lowpass(x, amount):
    """One-pole low-pass; [amount] 0..1, higher is duller."""
    y = np.empty_like(x)
    a = amount
    acc = 0.0
    for i, v in enumerate(x):
        acc = a * acc + (1 - a) * v
        y[i] = acc
    return y / (np.max(np.abs(y)) or 1)


def highpass(x, amount):
    return x - lowpass(x, amount) * np.max(np.abs(x))


def wood(freq, ms=90, decay=45.0):
    """A wooden tap: two modes and a click of noise."""
    return tone(freq, ms, decay) + 0.5 * tone(freq * 2.3, ms, decay * 1.6) + 0.35 * noise(ms, decay * 4)


def plastic(freq, ms=70, decay=60.0):
    """A hard plastic clack: brighter than wood."""
    return tone(freq, ms, decay) + 0.6 * tone(freq * 3.1, ms, decay * 1.5) + 0.6 * highpass(noise(ms, decay * 3), 0.6)


def seq(*parts, gap_ms=0):
    """Plays [parts] one after another (each a (start_ms, sound) pair or a sound)."""
    placed = []
    pos = 0
    for p in parts:
        if isinstance(p, tuple):
            start, s = p
            placed.append((int(RATE * start / 1000), s))
        else:
            placed.append((pos, p))
            pos += len(p) + int(RATE * gap_ms / 1000)
    end = max(s + len(p) for s, p in placed)
    out = np.zeros(end).view(Snd)
    for s, p in placed:
        out[s:s + len(p)] += p
    return out


def at(ms, s):
    return (ms, s)


def mix(*sounds):
    end = max(len(s) for s in sounds)
    out = np.zeros(end).view(Snd)
    for s in sounds:
        out[:len(s)] += s
    return out


def notes(freqs, each_ms, gap_ms=0, maker=None):
    maker = maker or (lambda f: bell(f, each_ms * 3))
    return seq(*[at(i * (each_ms + gap_ms), maker(f)) for i, f in enumerate(freqs)])


def hz(name):
    """Note name to Hz: 'C5', 'F#4'."""
    names = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}
    n, o = name[:-1], int(name[-1])
    return 440 * 2 ** ((names[n] + 12 * (o - 4)) / 12)


# --- The sounds -------------------------------------------------------------------------------

SOUNDS = {}


def sound(fn):
    """Marks [fn] as a sound: the file is sfx_<its name>.ogg."""
    SOUNDS[fn.__name__] = fn
    return fn

@sound
def tap():
    return plastic(1900, 40, 90)


@sound
def error():
    bonk = lambda: tone(190, 90, 30, shape='soft', glide=0.8) + 0.3 * noise(40, 60, color=0.7)
    return seq(at(0, bonk()), at(95, 0.8 * bonk()))


@sound
def your_turn():
    return notes([hz('E5'), hz('A5')], 110)


@sound
def countdown_tick():
    return wood(1100, 60, 55)


@sound
def countdown_go():
    return mix(wood(1650, 90, 40), 0.5 * bell(hz('E6'), 250, 9))


@sound
def win():
    horn = lambda f, ms: tone(f, ms, 2.5, attack_ms=12, shape='soft', vibrato=5)
    melody = seq(at(0, horn(hz('C5'), 180)), at(170, horn(hz('E5'), 180)), at(340, horn(hz('G5'), 180)), at(520, horn(hz('C6'), 650)))
    sparkle = seq(*[at(600 + i * 90, 0.25 * bell(f, 400, 7)) for i, f in enumerate([hz('E6'), hz('G6'), hz('C7'), hz('E7'), hz('G7')])])
    return mix(melody, sparkle)


@sound
def lose():
    kazoo = lambda f, ms, glide=1.0: lowpass(tone(f, ms, 2.0, attack_ms=20, shape='saw', glide=glide, vibrato=6), 0.55)
    return seq(at(0, kazoo(hz('G4'), 300)), at(300, kazoo(hz('F#4'), 300)), at(600, kazoo(hz('F4'), 600, 0.94)))


@sound
def tie():
    return seq(at(0, bell(hz('G5'), 500, 6)), at(260, bell(hz('G5'), 500, 6)))


@sound
def card_deal():
    return 0.8 * highpass(noise(90, 45, color=0.3), 0.5)


@sound
def card_slide():
    n = len(t(180))
    swish = rng.uniform(-1, 1, n) * np.sin(np.pi * np.arange(n) / n) ** 2
    return lowpass(swish, 0.35) + 0.4 * noise(40, 70, color=0.5)


@sound
def card_flip():
    return seq(at(0, highpass(noise(50, 60), 0.4)), at(35, 0.6 * highpass(noise(60, 55), 0.5)))


@sound
def dice_roll():
    return seq(*[at(i * 50 + int(rng.integers(0, 20)), (0.9 - i * 0.06) * plastic(2400 + rng.integers(-300, 300), 50, 80)) for i in range(10)])


@sound
def dice_land():
    return plastic(1500, 90, 45)


@sound
def piece_hop():
    return wood(900, 60, 60)


@sound
def piece_place():
    return wood(520, 110, 32) + 0.4 * noise(30, 90, color=0.6)


@sound
def letter_type():
    return 0.7 * plastic(2600, 50, 110)


@sound
def letter_drop():
    return wood(760, 90, 45)


@sound
def letter_right():
    return bell(hz('A6'), 150, 14)


@sound
def mark():
    n = len(t(150))
    scratch = rng.uniform(-1, 1, n) * np.sin(np.pi * np.arange(n) / n)
    return highpass(lowpass(scratch, 0.2), 0.7)


@sound
def sudoku_digit():
    return 0.8 * wood(1500, 70, 70)


@sound
def ships_fire():
    pomf = lowpass(noise(250, 14, color=0.85), 0.8) + tone(110, 250, 12, glide=0.6)
    whistle = 0.25 * tone(1800, 250, 3, attack_ms=40, glide=0.55)
    return seq(at(0, pomf), at(150, whistle))


@sound
def ships_hit():
    boom = lowpass(noise(700, 6, attack_ms=3, color=0.9), 0.9)
    return mix(boom, tone(70, 600, 6, glide=0.5))


@sound
def ships_miss():
    n = len(t(500))
    s = rng.uniform(-1, 1, n) * (np.arange(n) / n) ** 0.3 * np.exp(-7 * np.arange(n) / RATE)
    return mix(lowpass(s, 0.5), 0.3 * tone(300, 200, 18, glide=2.0))


@sound
def ships_sunk():
    creak = 0.6 * lowpass(tone(140, 450, 2, attack_ms=60, shape='saw', glide=0.8, vibrato=11), 0.6)
    bubbles = seq(*[at(350 + i * 70 + int(rng.integers(0, 30)), 0.35 * tone(400 + rng.integers(0, 500), 70, 40, glide=1.6)) for i in range(8)])
    return mix(creak, bubbles)


@sound
def c4_drop():
    return seq(*[at(i * 40, (0.4 + i * 0.07) * plastic(1300 - i * 60, 40, 90)) for i in range(7)])


@sound
def c4_land():
    return plastic(1100, 80, 55)


@sound
def dots_box():
    return mix(wood(380, 120, 30), 0.6 * seq(at(60, bell(hz('C6'), 250, 10))))


@sound
def memory_match():
    return notes([hz('E6'), hz('A6')], 120)


@sound
def words_solved():
    return notes([hz('C6'), hz('D6'), hz('E6'), hz('G6'), hz('C7')], 80, maker=lambda f: bell(f, 300, 9))


@sound
def draw_correct():
    return mix(bell(hz('C6'), 700, 3.5), seq(at(250, 0.4 * noise(300, 10, color=0.4))))


@sound
def ttt_x():
    return seq(at(0, mark()), at(70, 0.9 * mark()))


@sound
def ttt_o():
    n = len(t(170))
    s = rng.uniform(-1, 1, n) * np.sin(np.pi * np.arange(n) / n)
    return highpass(lowpass(s, 0.25), 0.7)


@sound
def numbers_sum_right():
    return bell(hz('E6'), 250, 9)


@sound
def hockey_hit():
    return plastic(900, 70, 60)


@sound
def hockey_wall():
    return 0.8 * plastic(1600, 60, 75)


@sound
def hockey_goal():
    horn = lowpass(tone(hz('A3'), 700, 2.2, attack_ms=15, shape='saw') + tone(hz('E4'), 700, 2.2, attack_ms=15, shape='saw'), 0.5)
    crowd = 0.3 * lowpass(noise(600, 4, attack_ms=60), 0.4)
    return mix(horn, seq(at(250, crowd)))


@sound
def ludo_out():
    pop = tone(500, 80, 40, glide=2.2)
    return seq(at(0, pop), at(90, wood(900, 70, 55)))


@sound
def ludo_capture():
    bonk = tone(220, 120, 22, shape='soft', glide=0.7)
    slide = 0.4 * tone(1400, 450, 2.5, attack_ms=20, glide=0.3)
    return seq(at(0, bonk), at(110, slide))


@sound
def ludo_home():
    return notes([hz('G5'), hz('C6'), hz('E6')], 90, maker=lambda f: tone(f, 250, 10, shape='soft'))


@sound
def grid_trace():
    return 0.7 * bell(hz('C7'), 60, 40)


@sound
def grid_word():
    return mix(bell(hz('G5'), 300, 8), 0.5 * bell(hz('D6'), 300, 9))


@sound
def balloon_stretch():
    return 0.5 * lowpass(tone(500, 330, 0.5, attack_ms=50, shape='saw', glide=2.0, vibrato=14), 0.4)


@sound
def balloon_pop():
    return mix(noise(250, 30, attack_ms=0.3), 0.5 * tone(160, 120, 30, glide=0.5))


@sound
def code_peg():
    return plastic(2100, 70, 80)


@sound
def code_pin_red():
    return plastic(2600, 80, 70)


@sound
def code_pin_white():
    return 0.7 * plastic(1800, 80, 80)


@sound
def code_cracked():
    clunk = wood(300, 160, 25)
    spring = 0.4 * tone(900, 300, 6, glide=1.8, vibrato=18)
    return seq(at(0, clunk), at(120, spring), at(300, 0.5 * bell(hz('E6'), 300, 8)))


@sound
def slide_solved():
    ticks = seq(*[at(i * 55, (0.5 + 0.05 * i) * wood(800 + i * 90, 60, 60)) for i in range(7)])
    return mix(ticks, seq(at(420, bell(hz('C6'), 350, 7))))


@sound
def bingo_ball():
    roll = seq(*[at(i * 45, (0.5 - i * 0.03) * plastic(700 + rng.integers(-80, 80), 45, 90)) for i in range(10)])
    return mix(roll, seq(at(470, wood(600, 120, 35))))


@sound
def bingo_dab():
    return 0.9 * lowpass(noise(90, 40, color=0.7), 0.6) + 0.3 * tone(240, 80, 40)


@sound
def bingo_line_ready():
    return mix(*[seq(at(i * 50, 0.3 * bell(f, 300, 8))) for i, f in enumerate([hz('C6'), hz('E6'), hz('G6'), hz('C7')])])


@sound
def bingo_shout():
    return mix(win()[:int(RATE * 0.9)] * np.linspace(1, 0, int(RATE * 0.9)), 0.3 * lowpass(noise(900, 3, attack_ms=50), 0.4))


@sound
def golf_putt():
    return plastic(2900, 120, 45) + 0.3 * tone(1450, 120, 30)


@sound
def golf_wall():
    return tone(260, 80, 35, shape='soft', glide=0.85)


@sound
def golf_cup():
    rattle = seq(*[at(i * 45, (0.7 - i * 0.1) * plastic(1700 + i * 100, 40, 90)) for i in range(5)])
    return mix(rattle, seq(at(260, 0.6 * bell(hz('E6'), 250, 9))))


# --- Writing the files ------------------------------------------------------------------------

def write(name, samples):
    samples = samples / (np.max(np.abs(samples)) or 1) * 0.89  # -1 dBFS peak
    pcm = (samples * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        path = f.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    out = os.path.join(OUT, f'sfx_{name}.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', path, '-c:a', 'libvorbis', '-q:a', '4', '-map_metadata', '-1', out], check=True)
    os.unlink(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    only = set(sys.argv[1:])
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        write(name, fn())
        print(name)


if __name__ == '__main__':
    main()
