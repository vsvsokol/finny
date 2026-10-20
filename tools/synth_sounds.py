"""Мягкие звуки Финни без сторонних записей: python tools/synth_sounds.py.

Нужен numpy. С ffmpeg в PATH сохраняется OGG Vorbis, иначе WAV PCM 16 бит.
Повторный запуск воспроизводит те же эффекты и заменяет только имена из SOUNDS.
Параметр --output-dir позволяет сначала послушать результат в отдельной папке.
"""

import argparse
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np


RATE = 44_100
PEAK = 10 ** (-3 / 20)
RAW = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def timeline(seconds):
    return np.arange(round(seconds * RATE)) / RATE


def soften(samples, attack=0.004, release=0.025):
    samples = samples.copy()
    a = min(round(attack * RATE), len(samples) // 2)
    r = min(round(release * RATE), len(samples) // 2)
    samples[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    samples[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return samples


def bell(hz, seconds, decay=4.5, wooden=False):
    t = timeline(seconds)
    # Верхние обертоны затухают раньше основного тона: блеск остаётся мягким.
    modes = ((1, 1), (2, 0.22), (3, 0.07)) if not wooden else ((1, 1), (1.47, 0.25), (2.09, 0.08))
    signal = sum(
        gain * np.sin(2 * np.pi * hz * ratio * t)
        * np.exp(-(decay + i * 1.7) * t / seconds)
        for i, (ratio, gain) in enumerate(modes)
    )
    return soften(signal)


def glide(start, end, seconds, decay=2.5, vibrato=0):
    t = timeline(seconds)
    progress = (1 - np.cos(np.pi * t / seconds)) / 2
    hz = start * (end / start) ** progress
    hz *= 1 + vibrato * np.sin(2 * np.pi * 7 * t)
    phase = 2 * np.pi * np.cumsum(hz) / RATE
    return soften((np.sin(phase) + 0.1 * np.sin(phase * 2)) * np.exp(-decay * t / seconds))


def rustle(seconds, low=350, high=2600, seed=1, peak=0.3):
    t = timeline(seconds)
    rng = np.random.default_rng(seed)
    spectrum = np.fft.rfft(rng.standard_normal(len(t)))
    frequencies = np.fft.rfftfreq(len(t), 1 / RATE)
    # Плавные склоны полосы не оставляют звона от резкого обрезания спектра.
    band = (1 - np.exp(-(frequencies / low) ** 4)) * np.exp(-(frequencies / high) ** 6)
    noise = np.fft.irfft(spectrum * band, len(t))
    noise /= max(np.sqrt(np.mean(noise ** 2)), 1e-12)
    x = t / seconds
    envelope = np.where(x < peak, x / peak, (1 - x) / (1 - peak))
    return soften(noise * np.maximum(envelope, 0) ** 1.4)


def scene(seconds, *events):
    out = np.zeros(round(seconds * RATE))
    for start, samples, gain in events:
        offset = round(start * RATE)
        count = min(len(samples), len(out) - offset)
        out[offset:offset + count] += samples[:count] * gain
    return out


def melody(notes, step, tail, gains=None):
    gains = gains or [1] * len(notes)
    return scene(
        (len(notes) - 1) * step + tail,
        *((i * step, bell(hz, tail), gains[i]) for i, hz in enumerate(notes)),
    )


def coin():
    return scene(0.29, (0, bell(1046.5, 0.23), 0.8), (0.055, bell(1568, 0.235), 0.6))


def purchase():
    return scene(0.36, (0, rustle(0.22, seed=2), 0.14),
                 (0.055, bell(784, 0.24), 0.65), (0.12, bell(1046.5, 0.24), 0.65))


def pour():
    return scene(0.38, (0, rustle(0.36, 250, 1500, seed=3), 0.12),
                 *((i * 0.065, glide(hz, hz * 0.72, 0.12), 0.6)
                   for i, hz in enumerate((440, 520, 610, 710, 820))))


def star():
    return scene(0.4, (0, bell(1318.5, 0.4, decay=3.5), 1),
                 (0.045, bell(1975.5, 0.30), 0.23), (0.11, bell(2637, 0.29), 0.09))


def grow():
    return scene(1.0, (0, rustle(0.95, 300, 2400, seed=4, peak=0.65), 0.08),
                 (0, glide(330, 990, 0.9, decay=0.8), 0.15),
                 *((i * 0.12, bell(hz, 1.0 - i * 0.12, decay=3.8), 0.6)
                   for i, hz in enumerate((392, 523.25, 659.25, 784, 1046.5, 1318.5))))


def new_thing():
    return scene(0.6, (0, bell(523.25, 0.24), 0.6),
                 (0.13, bell(659.25, 0.47, decay=3.6), 0.6),
                 (0.13, bell(784, 0.47, decay=3.6), 0.45),
                 (0.13, bell(1046.5, 0.47, decay=3.6), 0.35))


def jar_lid():
    return scene(0.25, (0, bell(230, 0.13, wooden=True), 0.9),
                 (0.008, bell(1174.7, 0.242), 0.55),
                 (0, rustle(0.07, 500, 2200, seed=5), 0.07))


def capsule_door():
    return scene(0.38, (0, rustle(0.29, 180, 1300, seed=6, peak=0.6), 0.2),
                 (0.21, bell(196, 0.17, wooden=True), 0.6),
                 (0, glide(310, 220, 0.30), 0.15))


def toy_drop():
    return scene(0.17, (0, bell(180, 0.17, wooden=True), 1),
                 (0, rustle(0.045, 500, 1800, seed=7), 0.1))


def toy_squeak():
    return scene(0.24, (0, glide(760, 1160, 0.14, decay=3.5), 0.8),
                 (0.11, glide(1160, 860, 0.13, decay=4.5), 0.4))


def toy_click():
    return scene(0.18, (0, bell(580, 0.11, wooden=True), 1),
                 (0.065, bell(730, 0.115, wooden=True), 0.65),
                 (0, rustle(0.04, 600, 2300, seed=8), 0.07))


SOUNDS = {
    "sfx_coin": coin,
    "sfx_purchase": purchase,
    "sfx_game_correct": lambda: melody((784, 1046.5), 0.065, 0.225),
    "sfx_game_wrong": lambda: glide(440, 294, 0.28, decay=3.5),
    "sfx_game_win": lambda: melody((523.25, 659.25, 784, 1046.5), 0.13, 0.65),
    "sfx_game_fail": lambda: melody((659.25, 587.33, 523.25), 0.07, 0.26, (0.7, 0.65, 0.9)),
    "sfx_level_up": lambda: melody((523.25, 659.25, 784, 1046.5, 1318.5), 0.11, 0.7),
    "sfx_pour": pour,
    "sfx_wash_clean": lambda: melody((1046.5, 1318.5, 1568), 0.055, 0.27),
    "sfx_capsule_door": capsule_door,
    "sfx_ufo": lambda: glide(430, 860, 0.4, decay=3.8, vibrato=0.035),
    "sfx_toy_pickup": lambda: glide(320, 640, 0.14, decay=3),
    "sfx_toy_drop": toy_drop,
    "sfx_toy_bounce": lambda: glide(620, 220, 0.24, decay=3),
    "sfx_toy_squeak": toy_squeak,
    "sfx_toy_click": toy_click,
    "sfx_toy_rustle": lambda: rustle(0.3, 700, 2800, seed=9),
    "sfx_star": star,
    "sfx_whoosh": lambda: scene(0.35, (0, rustle(0.35, 350, 2100, seed=10, peak=0.45), 0.25),
                               (0, glide(300, 700, 0.35, decay=1.5), 0.12)),
    "sfx_grow": grow,
    "sfx_new": new_thing,
    "sfx_jar_lid": jar_lid,
}


def prepare(samples):
    samples = samples - np.mean(samples)
    frequencies = np.fft.rfftfreq(len(samples), 1 / RATE)
    spectrum = np.fft.rfft(samples)
    spectrum *= (1 - np.exp(-(frequencies / 65) ** 4)) * np.exp(-(frequencies / 4300) ** 8)
    samples = soften(np.fft.irfft(spectrum, len(samples)), release=0.018)
    samples /= max(np.abs(samples).max(), 1e-12)
    # У шороха пики заметно выше средней громкости. Мягкое ограничение выравнивает
    # его с колокольчиками, не срезая вершины и не делая шум громче мелодии.
    target = 10 ** (-14 / 20) / PEAK
    low, high = 0.01, 12.0
    for _ in range(32):
        drive = (low + high) / 2
        shaped = np.tanh(samples * drive) / np.tanh(drive)
        if np.sqrt(np.mean(shaped ** 2)) < target:
            low = drive
        else:
            high = drive
    samples = np.tanh(samples * high) / np.tanh(high) * PEAK
    # Отбрасываем только неслышимый хвост; короткие плавные фронты защищают от щелчков.
    audible = np.flatnonzero(np.abs(samples) > 10 ** (-65 / 20))
    return samples[max(0, audible[0] - 1):min(len(samples), audible[-1] + 2)]


def write_sound(path, samples, ffmpeg):
    # Сначала полный новый файл, затем замена: при ошибке кодека старый звук остаётся.
    with tempfile.NamedTemporaryFile(dir=path.parent, suffix=path.suffix, delete=False) as pending:
        temporary = Path(pending.name)
    try:
        if ffmpeg:
            subprocess.run(
                [ffmpeg, "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
                 "-i", "-", "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", str(temporary)],
                input=samples.astype("<f4").tobytes(), check=True,
            )
        else:
            with wave.open(str(temporary), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(np.rint(samples * 32767).astype("<i2").tobytes())
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
    alternative = path.with_suffix(".wav" if ffmpeg else ".ogg")
    alternative.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=RAW, help="Папка для готовых звуков")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    ffmpeg = shutil.which("ffmpeg")
    for name, synthesize in SOUNDS.items():
        samples = prepare(synthesize())
        path = args.output_dir / (name + (".ogg" if ffmpeg else ".wav"))
        write_sound(path, samples, ffmpeg)
        peak = 20 * np.log10(np.abs(samples).max())
        rms = 20 * np.log10(np.sqrt(np.mean(samples ** 2)))
        print(f"{path.name}: {len(samples) / RATE:.3f} с, пик {peak:.1f} dBFS, RMS {rms:.1f} dBFS")


if __name__ == "__main__":
    main()
