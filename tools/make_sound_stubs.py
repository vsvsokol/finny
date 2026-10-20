"""Заглушки звуков, которых ещё нет: синтезированные бипы с настоящими именами.

Код уже играет каждый звук из списка ниже. Пока звуковик не прислал файл, в
app/src/main/res/raw/ лежит заглушка — узнаваемый синтетический звук той же длины
и того же смысла. Настоящий файл кладётся поверх с тем же именем, код не меняется.

Скрипт пишет только те файлы, которых в res/raw ещё нет: готовые звуки он не
перезапишет. Чтобы пересоздать заглушку, удалите её файл и запустите заново.

Нужен numpy; ffmpeg в PATH — для OGG. Без ffmpeg заглушка пишется WAV (16 бит, моно):
Android играет его так же, а весит короткий звук ~20 КБ. Настоящий OGG кладётся
вместо WAV — старый файл удалить, имя ресурса то же. Запуск из корня репозитория:

    python tools/make_sound_stubs.py
"""

import shutil
import subprocess
import wave
from pathlib import Path

import numpy as np

RAW = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
RATE = 44100


def silence(ms):
    return np.zeros(int(RATE * ms / 1000))


def tone(freq, ms, decay=8.0, harmonics=(1.0, 0.3, 0.1), attack_ms=3):
    """Колокольчик: синус с обертонами и экспоненциальным затуханием."""
    t = np.arange(int(RATE * ms / 1000)) / RATE
    wave = sum(a * np.sin(2 * np.pi * freq * (i + 1) * t) for i, a in enumerate(harmonics))
    env = np.exp(-decay * t / (ms / 1000))
    attack = min(len(t), int(RATE * attack_ms / 1000))
    env[:attack] *= np.linspace(0, 1, attack)
    return wave * env


def noise(ms, low, high, seed=1):
    """Полосовой шум: шорох, всплеск, вжух."""
    n = int(RATE * ms / 1000)
    spectrum = np.fft.rfft(np.random.default_rng(seed).standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    spectrum[(freqs < low) | (freqs > high)] = 0
    return np.fft.irfft(spectrum, n)


def seq(*parts):
    return np.concatenate(parts)


def mix(*parts):
    out = np.zeros(max(len(p) for p in parts))
    for p in parts:
        out[: len(p)] += p
    return out


def hump(n, peak=0.5):
    """Огибающая «нарастает и гаснет», пик на доле [peak] длины."""
    x = np.linspace(0, 1, n)
    return np.where(x < peak, x / peak, (1 - x) / (1 - peak))


def coin():
    return seq(tone(1319, 70, decay=5), tone(1760, 220, decay=6))


def purchase():
    rattle = noise(120, 3000, 9000, seed=2) * hump(int(RATE * 0.12), 0.1) * 0.5
    return seq(rattle, mix(tone(1047, 350, decay=5), tone(1319, 350, decay=5), tone(1568, 350, decay=5)))


def game_correct():
    return seq(tone(1047, 80, decay=4), tone(1568, 200, decay=6))


def game_wrong():
    # Мягко и без упрёка: два тихих круглых тона вниз, без жужжания.
    soft = (1.0, 0.15)
    return seq(tone(523, 110, decay=4, harmonics=soft), tone(392, 220, decay=5, harmonics=soft)) * 0.6


def game_win():
    notes = [523, 659, 784]
    return seq(*[tone(f, 110, decay=3) for f in notes], mix(tone(1047, 700, decay=5), tone(784, 700, decay=5) * 0.5))


def game_fail():
    # «Почти!»: тёплое мажорное завершение, а не проигрыш.
    soft = (1.0, 0.2)
    return seq(
        tone(659, 140, decay=3, harmonics=soft),
        tone(587, 140, decay=3, harmonics=soft),
        mix(tone(523, 600, decay=5, harmonics=soft), tone(392, 600, decay=5, harmonics=soft) * 0.6),
    ) * 0.7


def level_up():
    run = [tone(f, 70, decay=3) for f in (523, 659, 784, 1047, 1319)]
    chord = mix(*[tone(f, 1100, decay=5) for f in (1047, 1319, 1568)])
    sparkle = seq(silence(250), tone(2637, 120, decay=5) * 0.3, tone(3136, 160, decay=5) * 0.3)
    return seq(*run, mix(chord, sparkle))


def wash_clean():
    return seq(*[tone(f, 70, decay=4, harmonics=(1.0,)) for f in (2093, 2637, 3136)], tone(4186, 200, decay=6, harmonics=(1.0,))) * 0.6


def capsule_door():
    slide = noise(220, 200, 1500, seed=4) * hump(int(RATE * 0.22), 0.7) * 0.4
    thud = tone(110, 160, decay=6, harmonics=(1.0, 0.5))
    return seq(slide, thud)


def ufo():
    t = np.arange(int(RATE * 1.4)) / RATE
    freq = 700 + 250 * np.sin(2 * np.pi * 0.6 * t) + 30 * np.sin(2 * np.pi * 7 * t)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    return np.sin(phase) * hump(len(t), 0.5) * 0.35


def pour():
    stream = noise(420, 600, 2500, seed=5) * hump(int(RATE * 0.42), 0.2) * 0.35
    blips = seq(*[seq(silence(40), tone(f, 60, decay=6, harmonics=(1.0,)) * 0.3) for f in (520, 610, 700, 820, 900)])
    return mix(stream, blips)


# Игрушки (плейтест 28.09: «предметы валяются и не имеют функционала», звуков у них не было).
# У каждой игрушки свой звук встряски, чтобы по звуку было слышно, что в руке.


def sweep(f0, f1, ms, decay=5.0, harmonics=(1.0, 0.25)):
    """Тон, плавно съезжающий с f0 на f1: «боинг», писк."""
    t = np.arange(int(RATE * ms / 1000)) / RATE
    freq = f0 + (f1 - f0) * (t / t[-1])
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    wave_ = sum(a * np.sin(phase * (i + 1)) for i, a in enumerate(harmonics))
    env = np.exp(-decay * t / (ms / 1000))
    attack = int(RATE * 0.004)
    env[:attack] *= np.linspace(0, 1, attack)
    return wave_ * env


def toy_pickup():
    # Взял в руку — мягкий «поп» вверх.
    return sweep(380, 720, 90, decay=4, harmonics=(1.0, 0.1)) * 0.8


def toy_drop():
    # Упала на пол — глухой «тук» с коротким стуком дерева.
    knock = noise(25, 800, 3000, seed=7) * hump(int(RATE * 0.025), 0.05) * 0.25
    return mix(tone(150, 170, decay=7, harmonics=(1.0, 0.4, 0.1)), knock)


def toy_bounce():
    # Мячик — «боинг»: тон падает, как мяч отскакивает.
    return sweep(620, 240, 200, decay=4, harmonics=(1.0, 0.3, 0.1))


def toy_squeak():
    # Мишка, уточка — резиновая пищалка: вверх и чуть назад, с «носовыми» обертонами.
    up = sweep(1150, 1650, 90, decay=0.5, harmonics=(1.0, 0.5, 0.35, 0.2))
    down = sweep(1650, 1350, 90, decay=5, harmonics=(1.0, 0.5, 0.35, 0.2))
    return seq(up, down) * 0.7


def toy_click():
    # Кубики, конструктор — два деревянных щелчка.
    def clack(seed):
        return mix(noise(18, 1800, 6000, seed=seed) * hump(int(RATE * 0.018), 0.05), tone(1900, 40, decay=8, harmonics=(1.0,)) * 0.4)
    return seq(clack(8), silence(45), clack(9) * 0.7)


def toy_rustle():
    # Книжка — шелест страниц.
    n = int(RATE * 0.26)
    flutter = 0.6 + 0.4 * np.sin(2 * np.pi * 22 * np.arange(n) / RATE)
    return noise(260, 2500, 9000, seed=10) * hump(n, 0.3) * flutter * 0.6


def pet_leap():
    # Питомец прыжком уходит в соседнюю комнату — мультяшная свистулька вверх
    # с дрожью и лёгкий «вжух» воздуха: уход за край экрана нарочно смешной.
    t = np.arange(int(RATE * 0.26)) / RATE
    freq = 320 + 700 * (t / t[-1]) ** 1.6 + 25 * np.sin(2 * np.pi * 18 * t)
    whistle = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * hump(len(t), 0.7) * 0.7
    air = noise(260, 700, 3500, seed=11) * hump(len(t), 0.6) * 0.35
    return mix(whistle, air)


def pet_land():
    # Впрыгнул в новую комнату — мягкий «пум» лапами о пол и маленькая пружинка.
    thump = mix(tone(110, 190, decay=7, harmonics=(1.0, 0.5, 0.15)),
                noise(30, 200, 1200, seed=12) * hump(int(RATE * 0.03), 0.1) * 0.4)
    t = np.arange(int(RATE * 0.22)) / RATE
    freq = 430 - 120 * (t / t[-1]) + 30 * np.sin(2 * np.pi * 14 * t)
    spring = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.exp(-6 * t / t[-1]) * 0.3
    return mix(thump, seq(silence(30), spring))


STUBS = {
    "sfx_coin": coin,
    "sfx_purchase": purchase,
    "sfx_game_correct": game_correct,
    "sfx_game_wrong": game_wrong,
    "sfx_game_win": game_win,
    "sfx_game_fail": game_fail,
    "sfx_level_up": level_up,
    "sfx_wash_clean": wash_clean,
    "sfx_capsule_door": capsule_door,
    "sfx_ufo": ufo,
    "sfx_pour": pour,
    "sfx_toy_pickup": toy_pickup,
    "sfx_toy_drop": toy_drop,
    "sfx_toy_bounce": toy_bounce,
    "sfx_pet_leap": pet_leap,
    "sfx_pet_land": pet_land,
    "sfx_toy_squeak": toy_squeak,
    "sfx_toy_click": toy_click,
    "sfx_toy_rustle": toy_rustle,
}

# Пик по таблице громкости в design/exports/sounds/README.md: эффекты интерфейса −9 dBFS,
# остальные −4…−6. Игрушку трясут часто — чуть тише, чтобы серия не резала слух.
PEAK_DB = {name: -7.0 for name in STUBS if name.startswith("sfx_toy_")}


def prepare(samples, peak_db):
    samples = samples * (10 ** (peak_db / 20) / np.abs(samples).max())
    fade = min(len(samples) // 4, int(RATE * 0.01))
    samples[-fade:] *= np.linspace(1, 0, fade)
    return samples


def write_ogg(samples, path, peak_db=-4.0):
    samples = prepare(samples, peak_db)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ac", "1", "-ar", str(RATE), "-i", "-",
         "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", str(path)],
        input=samples.astype(np.float32).tobytes(),
        check=True,
    )


def write_wav(samples, path, peak_db=-4.0):
    samples = prepare(samples, peak_db)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((samples * 32767).astype("<i2").tobytes())


def main():
    RAW.mkdir(parents=True, exist_ok=True)
    ogg = shutil.which("ffmpeg") is not None
    for name, make in STUBS.items():
        existing = [p for p in (RAW / f"{name}.ogg", RAW / f"{name}.wav") if p.exists()]
        if existing:
            print(f"есть     {existing[0].name}")
            continue
        path = RAW / f"{name}.{'ogg' if ogg else 'wav'}"
        (write_ogg if ogg else write_wav)(make(), path, PEAK_DB.get(name, -4.0))
        print(f"заглушка {path.name}")


if __name__ == "__main__":
    main()
