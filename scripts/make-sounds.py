#!/usr/bin/env python3
"""Makes Pip & Pop's sound effects from scratch (no recordings, so no licence questions).

Each sound in docs/SFX.md marked priority 1 is built here from tones and noise, in the
"Felt" style: round, low and short, kept out of the 2-5 kHz band people find harshest. Run it to (re)make app/src/main/res/raw/sfx_*.ogg:

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



def lp(x, cutoff):
    """Low-pass at [cutoff] Hz, two one-pole stages (12 dB/oct): takes the edge off."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    x = np.asarray(x, dtype=float)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - a) * v + a * acc
            y[i] = acc
        x = y
    return x.view(Snd)


# The Felt set: round boops, soft thumps and muffled taps, nothing much above 1.5 kHz, where
# the ear finds sounds harshest (2-5 kHz). Each game keeps its own idea, made soft.

def boop(freq, ms=200, decay=10.0, glide=1.0, attack_ms=12, vibrato=0.0):
    """A round, felt-covered note: a sine and a little of its octave."""
    return lp(tone(freq, ms, decay, attack_ms, glide=glide, vibrato=vibrato)
              + 0.25 * tone(freq * 2, ms, decay * 1.8, attack_ms, glide=glide), 1200)


def thump(freq=150, ms=120, decay=30.0):
    """A soft knock, like a felt mallet on a box."""
    n = len(t(ms))
    return lp(tone(freq, ms, decay, 2, glide=0.7) + 0.4 * rng.uniform(-1, 1, n) * env(n, 1, decay * 2), 900)


def tap_(freq, ms=70, decay=60.0):
    """A muffled tap: a short note with a breath of noise, a little brighter than a thump."""
    n = len(t(ms))
    return lp(tone(freq, ms, decay, 2) + 0.3 * rng.uniform(-1, 1, n) * env(n, 1, decay * 2), 1000)


def shush(ms, cutoff=1200, decay=0.0):
    """Soft noise that swells and fades: paper, cards, a pencil."""
    n = len(t(ms))
    shape = np.sin(np.pi * np.arange(n) / n) ** 2
    if decay:
        shape = shape * np.exp(-decay * np.arange(n) / RATE)
    return lp(rng.uniform(-1, 1, n) * shape, cutoff)


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
    return thump(300, 90)


@sound
def error():
    return boop(260, 300, 9, glide=0.82)


@sound
def your_turn():
    return seq(at(0, boop(hz('G4'), 180)), at(130, boop(hz('C5'), 260)))


@sound
def countdown_tick():
    return tap_(420, 70, 50)


@sound
def countdown_go():
    return mix(tap_(520, 90, 40), boop(hz('C5'), 320, 8))


@sound
def win():
    return seq(*[at(i * 140, 0.8 * boop(hz(n), 700, 5)) for i, n in enumerate(['C4', 'E4', 'G4', 'C5'])])


@sound
def lose():
    return seq(at(0, boop(hz('G4'), 320, 6)), at(300, boop(hz('F#4'), 320, 6)), at(600, boop(hz('F4'), 650, 4, glide=0.94)))


@sound
def tie():
    return seq(at(0, boop(hz('G4'), 400, 7)), at(260, boop(hz('G4'), 400, 7)))


@sound
def card_deal():
    return 0.8 * shush(90, 1000, 20)


@sound
def card_slide():
    return shush(180, 1000)


@sound
def card_flip():
    return seq(at(0, shush(50, 1100)), at(35, 0.6 * shush(60, 1000)))


@sound
def dice_roll():
    return seq(*[at(i * 50 + int(rng.integers(0, 20)), (0.9 - i * 0.06) * tap_(560 + int(rng.integers(-80, 80)), 50, 80)) for i in range(10)])


@sound
def dice_land():
    return thump(260, 110)


@sound
def piece_hop():
    return tap_(600, 60, 60)


@sound
def piece_place():
    return thump(220, 120)


@sound
def letter_type():
    return 0.7 * tap_(700, 50, 90)


@sound
def letter_drop():
    return tap_(480, 90, 45)


@sound
def letter_right():
    return boop(hz('G5'), 160, 14)


@sound
def mark():
    return shush(150, 900)


@sound
def sudoku_digit():
    return 0.8 * tap_(620, 70, 70)


@sound
def ships_fire():
    pomf = lp(noise(250, 14), 500) + tone(110, 250, 12, glide=0.6)
    return seq(at(0, pomf), at(150, 0.2 * boop(700, 250, 4, glide=0.55)))


@sound
def ships_hit():
    return mix(lp(noise(700, 6, attack_ms=3), 450), tone(70, 600, 6, glide=0.5))


@sound
def ships_miss():
    return mix(shush(450, 800, 6), 0.3 * boop(260, 200, 16, glide=1.6))


@sound
def ships_sunk():
    creak = 0.6 * lp(tone(140, 450, 2, attack_ms=60, shape='soft', glide=0.8, vibrato=11), 700)
    bubbles = seq(*[at(350 + i * 70 + int(rng.integers(0, 30)), 0.35 * boop(300 + int(rng.integers(0, 300)), 80, 35, glide=1.5, attack_ms=3)) for i in range(8)])
    return mix(creak, bubbles)


@sound
def c4_drop():
    return seq(*[at(i * 40, (0.4 + i * 0.07) * tap_(700 - i * 40, 40, 90)) for i in range(7)])


@sound
def c4_land():
    return thump(300, 100)


@sound
def dots_box():
    return mix(thump(380, 120), 0.6 * seq(at(60, boop(hz('C5'), 260, 10))))


@sound
def memory_match():
    return seq(at(0, 0.9 * boop(440, 140, 10, glide=1.15)), at(110, 0.9 * boop(587, 240)))


@sound
def words_solved():
    return seq(*[at(i * 80, boop(hz(n), 300, 9)) for i, n in enumerate(['C4', 'D4', 'E4', 'G4', 'C5'])])


@sound
def draw_correct():
    return mix(boop(hz('C5'), 700, 3.5), seq(at(250, 0.3 * shush(300, 1000))))


@sound
def ttt_x():
    return seq(at(0, mark()), at(70, 0.9 * mark()))


@sound
def ttt_o():
    return shush(170, 900)


@sound
def numbers_sum_right():
    return boop(hz('E5'), 250, 9)


@sound
def hockey_hit():
    return thump(400, 70, 40)


@sound
def hockey_wall():
    return 0.8 * tap_(500, 60, 75)


@sound
def hockey_goal():
    horn = lp(tone(hz('A3'), 700, 2.2, attack_ms=15, shape='soft') + tone(hz('E4'), 700, 2.2, attack_ms=15, shape='soft'), 800)
    return mix(horn, seq(at(250, 0.3 * shush(600, 700))))


@sound
def ludo_out():
    return seq(at(0, boop(300, 90, 30, glide=2.0, attack_ms=3)), at(90, tap_(600, 70, 55)))


@sound
def ludo_capture():
    return seq(at(0, boop(220, 120, 22, glide=0.7, attack_ms=3)), at(110, 0.4 * boop(700, 450, 2.5, glide=0.4)))


@sound
def ludo_home():
    return seq(*[at(i * 90, boop(hz(n), 260, 10)) for i, n in enumerate(['G4', 'C5', 'E5'])])


@sound
def grid_trace():
    return 0.7 * boop(hz('G5'), 70, 40, attack_ms=3)


@sound
def grid_word():
    return mix(boop(hz('G4'), 300, 8), 0.5 * boop(hz('D5'), 300, 9))


@sound
def balloon_stretch():
    return 0.5 * lp(tone(300, 330, 0.5, attack_ms=50, shape='soft', glide=2.0, vibrato=14), 900)


@sound
def balloon_pop():
    # Still a pop, but a cushioned one.
    return mix(lp(noise(160, 40, attack_ms=0.5), 1800), 0.5 * tone(160, 120, 30, glide=0.5))


@sound
def code_peg():
    return tap_(650, 70, 80)


@sound
def code_pin_red():
    return tap_(780, 80, 70)


@sound
def code_pin_white():
    return 0.7 * tap_(560, 80, 80)


@sound
def code_cracked():
    spring = 0.4 * boop(450, 300, 6, glide=1.8, vibrato=12)
    return seq(at(0, thump(300, 160, 25)), at(120, spring), at(300, 0.5 * boop(hz('E5'), 300, 8)))


@sound
def slide_solved():
    ticks = seq(*[at(i * 55, (0.5 + 0.05 * i) * tap_(400 + i * 50, 60, 60)) for i in range(7)])
    return mix(ticks, seq(at(420, boop(hz('C5'), 350, 7))))


@sound
def bingo_ball():
    roll = seq(*[at(i * 45, (0.5 - i * 0.03) * tap_(450 + int(rng.integers(-50, 50)), 45, 90)) for i in range(10)])
    return mix(roll, seq(at(470, thump(300, 120))))


@sound
def bingo_dab():
    return thump(240, 90, 40)


@sound
def bingo_line_ready():
    return mix(*[seq(at(i * 50, 0.3 * boop(hz(n), 300, 8))) for i, n in enumerate(['C5', 'E5', 'G5', 'C6'])])


@sound
def bingo_shout():
    n = int(RATE * 0.9)
    return mix(win()[:n] * np.linspace(1, 0, n), 0.3 * shush(900, 700))


@sound
def golf_putt():
    return tap_(700, 100, 50) + 0.3 * thump(350, 80)


@sound
def golf_wall():
    return boop(260, 80, 35, glide=0.85, attack_ms=2)


@sound
def golf_cup():
    rattle = seq(*[at(i * 45, (0.7 - i * 0.1) * tap_(500 + i * 40, 40, 90)) for i in range(5)])
    return mix(rattle, seq(at(260, 0.6 * boop(hz('E5'), 250, 9))))


@sound
def golf_splash():
    # A plop, then a soft wash of water and two bubbles.
    n = len(t(420))
    wash = lp(rng.uniform(-1, 1, n) * np.exp(-9 * np.arange(n) / RATE), 900)
    bubbles = seq(at(170, 0.35 * boop(500, 70, 40, glide=1.6, attack_ms=2)), at(260, 0.3 * boop(620, 60, 45, glide=1.5, attack_ms=2)))
    return mix(boop(420, 100, 28, glide=0.5, attack_ms=2), 0.6 * wash, bubbles)


@sound
def golf_drop():
    # A falling whistle, down and away.
    return 0.8 * boop(700, 520, 3.5, glide=0.3)


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
