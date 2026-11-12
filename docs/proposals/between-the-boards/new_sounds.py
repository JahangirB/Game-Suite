"""First takes of the sounds proposed in the "Between the Boards" catalogue, made with the app's own sound kit.

Usage: python3 docs/proposals/between-the-boards/new_sounds.py OUT_DIR
Writes one MP3 per sound into OUT_DIR. Needs numpy and ffmpeg, as scripts/make-sounds.py does.
"""
import importlib.util, os, subprocess, sys, tempfile, wave
import numpy as np

sys.dont_write_bytecode = True  # loading the sound kit must not leave a cache folder in scripts/
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
spec = importlib.util.spec_from_file_location('ms', os.path.join(ROOT, 'scripts', 'make-sounds.py'))
ms = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ms)
boop, thump, tap_, shush, seq, at, mix, hz = ms.boop, ms.thump, ms.tap_, ms.shush, ms.seq, ms.at, ms.mix, ms.hz
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
NEW = {}


def sound(fn):
    NEW[fn.__name__] = fn
    return fn


@sound
def sudoku_unit_done():
    return seq(*[at(i * 90, 0.8 * boop(hz(n), 300, 9)) for i, n in enumerate(['E4', 'G4', 'C5'])])


@sound
def c4_four():
    return seq(*[at(i * 75, 0.8 * boop(hz(n), 260, 11)) for i, n in enumerate(['C4', 'E4', 'G4', 'C5'])])


@sound
def ttt_board_won():
    return seq(at(0, thump(110, 180, 20)), at(0, 0.5 * shush(120, 900)), at(45, 0.6 * boop(hz('G4'), 220, 12)))


@sound
def memory_miss():
    return seq(at(0, shush(110, 900)), at(0, 0.5 * boop(hz('E4'), 160, 14)), at(130, 0.8 * shush(110, 700)), at(130, 0.5 * boop(hz('C4'), 240, 12)))


@sound
def slide_home():
    return seq(at(0, tap_(480, 60, 60)), at(40, 0.7 * boop(hz('E5'), 220, 12)))


@sound
def bingo_one_away():
    return seq(at(0, thump(90, 140, 24)), at(170, 0.8 * thump(80, 160, 22)), at(620, thump(90, 140, 24)), at(790, 0.8 * thump(80, 160, 22)))


@sound
def domino_knock():
    return seq(at(0, thump(180, 90, 40)), at(110, 0.9 * thump(170, 100, 36)))


@sound
def clash_last_card():
    return seq(at(0, boop(hz('E5'), 140, 14)), at(110, boop(hz('A5'), 360, 8)))


@sound
def player_join():
    return seq(at(0, boop(hz('C4'), 150, 12)), at(120, boop(hz('G4'), 260, 9)))


@sound
def player_leave():
    return seq(at(0, boop(hz('G4'), 150, 12)), at(120, boop(hz('C4'), 280, 8, glide=0.95)))


@sound
def ready():
    return mix(tap_(360, 70, 55), 0.6 * boop(hz('E4'), 140, 16))


@sound
def score_up():
    return seq(at(0, boop(hz('B4'), 90, 18)), at(70, boop(hz('E5'), 240, 10)))


@sound
def record():
    return seq(*[at(i * 60, 0.7 * boop(hz(n), 240, 10)) for i, n in enumerate(['G4', 'B4', 'D5', 'G5'])], at(270, 0.5 * boop(hz('D5'), 520, 5, vibrato=6)))


@sound
def decider():
    return seq(*[at(i * 55, (0.4 + 0.07 * i) * tap_(300 + 12 * i, 60, 55)) for i in range(9)], at(520, boop(hz('D4'), 520, 5)))


@sound
def night_won():
    return seq(
        *[at(i * 150, 0.8 * boop(hz(n), 650, 5)) for i, n in enumerate(['C4', 'E4', 'G4', 'C5', 'E5'])],
        at(900, 0.9 * boop(hz('G4'), 950, 3)), at(900, 0.9 * boop(hz('C5'), 950, 3)), at(900, 0.7 * boop(hz('E5'), 950, 3)),
    )


@sound
def trophy():
    return seq(at(0, thump(100, 200, 16)), at(60, 0.6 * boop(hz('C5'), 720, 4, vibrato=5)), at(60, 0.4 * boop(hz('G5'), 720, 4, vibrato=5)))


@sound
def line_sent():
    return boop(hz('F4'), 110, 20, glide=1.5)


def write(name, samples):
    samples = np.asarray(samples, dtype=float)
    samples = samples / (np.max(np.abs(samples)) or 1) * 0.89
    pcm = (samples * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        path = f.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(ms.RATE); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', path, '-c:a', 'libmp3lame', '-b:a', '64k', '-map_metadata', '-1', os.path.join(OUT, name + '.mp3')], check=True)
    os.unlink(path)
    return len(samples) / ms.RATE


for name, fn in NEW.items():
    print(name, round(write(name, fn()) * 1000), 'ms')
