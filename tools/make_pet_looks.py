"""Слои платных обликов (окрасок) питомцев из картинок дизайнеров.

Окраски пришли не слоями, а плоскими JPG на чёрном фоне — по два на персонажа
(`design/exports/pet/looks/look_<питомец>_<n>.jpg`). Зато контур у них тот же,
что у базового питомца, пиксель в пиксель: меняются только заливки. Поэтому
слоёв не ждём, а перекрашиваем исходные слои питомца по картинке окраски:

1. Базовый кадр (настроение happy поверх туловища) совмещается с JPG по линиям
   контура — холсты одного размера, сдвиг только на поле вокруг.
2. Каждый слой делится на замкнутые области заливки (между линиями контура).
   Цвет области — медиана JPG по той её части, что видна в базовом кадре.
   Один цвет на всю картинку не годится: у Пушистика рога и ноги одного тёмно-синего,
   а в окраске рога кремовые, а ноги зелёные.
3. Области, которых в кадре не видно (слёзы грусти, грязь, лицо сна), берут цвет
   по таблице «базовый цвет → цвет окраски», собранной с видимых областей. Цвета,
   которого в таблице нет (грязь), не трогаем.

Перекрашенные слои кладутся во временную папку под именем облика (`look_pushistik_1`)
и проходят те же шаги, что базовый питомец в `split_pet_base.py`: вырез рук и ног,
стадии роста, моргание, рот. В ресурсы — `look_<питомец>_<n>_*.webp`.

Код к слоям — `ui/pet/PetLookSkins.kt` — пишется здесь же: десять обликов по
двадцать три картинки руками не перечислить без ошибки.

Запуск из корня репозитория (Pillow, scipy):
    py -I tools/make_pet_looks.py           # слои и код
    py -I tools/make_pet_looks.py --kotlin  # только код
"""

import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
import split_pet_base as split  # noqa: E402

SRC = Path("design/exports/pet")
LOOKS_DIR = SRC / "looks"

# Линия контура — почти чёрное. Порог split_pet_base (60) здесь велик: волосы Рогатика
# тёмно-синие (8, 0, 48), и с ним они считались линией и не перекрашивались.
INK_MAX = 24
# Области делятся и по цвету: сглаженная кайма вокруг звезды в глазу иначе склеивала
# её с соседней заливкой, и звезда брала чужой цвет. Шаг — на сколько цвет может «гулять».
STEP = 24
# Насколько цвет пикселя слоя может отличаться от кадра, чтобы считать его видимым в кадре.
SAME = 24
# Дальше этого от любого цвета таблицы пиксель оставляем как есть (грязь, слёзы).
FAR = 48
# Заливки уже на столько пикселей от линии: у самой линии JPG смазан сжатием.
CORE = 3


def rgba(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGBA")).astype(np.float32)


def over(top: np.ndarray, bottom: np.ndarray) -> np.ndarray:
    a = top[..., 3:] / 255
    b = bottom[..., 3:] / 255
    out_a = a + b * (1 - a)
    out = np.zeros_like(top)
    out[..., :3] = (top[..., :3] * a + bottom[..., :3] * b * (1 - a)) / np.maximum(out_a, 1e-6)
    out[..., 3:] = out_a * 255
    return out


def fill_mask(image: np.ndarray) -> np.ndarray:
    return (image[..., 3] > 250) & (image[..., :3].max(axis=2) >= INK_MAX)


def regions(image: np.ndarray, mask: np.ndarray, least: int = 20):
    """Замкнутые области одного цвета внутри [mask]: (срез, маска области в срезе)."""
    key = (image[..., :3] // STEP).astype(np.int32)
    key = (key[..., 0] * 64 + key[..., 1]) * 64 + key[..., 2]
    values, counts = np.unique(key[mask], return_counts=True)
    for value in values[counts >= least]:
        labels, _ = ndimage.label(mask & (key == value))
        for idx, sl in enumerate(ndimage.find_objects(labels), start=1):
            if sl is not None:
                yield sl, labels[sl] == idx


def offset(jpg: np.ndarray, frame: np.ndarray) -> tuple[int, int]:
    """Сдвиг JPG относительно слоя — по совпадению линий контура.

    По рамке заливок совмещать нельзя: у второй окраски Бантика верх волос тёмный,
    на чёрном фоне JPG его не отличить от фона, и рамка уезжала на 40 px. Линии же
    у окраски и слоя одни и те же. Ищем грубо в уменьшенном виде, потом точно.
    """
    line = (frame[..., 3] > 250) & (frame[..., :3].max(axis=2) < INK_MAX)
    jline = jpg.max(axis=2) < INK_MAX
    h, w = line.shape
    cy, cx = (jpg.shape[0] - h) // 2, (jpg.shape[1] - w) // 2

    def score(dy: int, dx: int, step: int) -> float:
        m = 200  # поле: у края только фон
        a = line[m:h - m:step, m:w - m:step]
        y0, x0 = m + dy, m + dx
        if y0 < 0 or x0 < 0 or y0 + h - 2 * m > jpg.shape[0] or x0 + w - 2 * m > jpg.shape[1]:
            return -1.0
        b = jline[y0:y0 + h - 2 * m:step, x0:x0 + w - 2 * m:step]
        return float((a & b).sum()) / max(int(a.sum()), 1)

    best = max(((score(cy + dy, cx + dx, 8), cy + dy, cx + dx)
                for dy in range(-96, 97, 4) for dx in range(-96, 97, 4)))
    best = max(((score(best[1] + dy, best[2] + dx, 2), best[1] + dy, best[2] + dx)
                for dy in range(-4, 5) for dx in range(-4, 5)))
    if best[0] < 0.9:
        raise SystemExit(f"окраска не совпала с питомцем по линиям: {best[0]:.0%}")
    return best[1], best[2]


def aligned(jpg: np.ndarray, frame: np.ndarray) -> np.ndarray:
    """JPG, сдвинутый на холст слоя."""
    dy, dx = offset(jpg, frame)
    h, w = frame.shape[:2]
    out = np.zeros((h, w, 3), np.float32)
    src = jpg[max(dy, 0):dy + h, max(dx, 0):dx + w]
    out[max(-dy, 0):max(-dy, 0) + src.shape[0], max(-dx, 0):max(-dx, 0) + src.shape[1]] = src
    return out


class Recolor:
    """Перекраска слоёв одного питомца в одну окраску."""

    def __init__(self, frame: np.ndarray, jpg: np.ndarray):
        self.frame = frame
        self.target = aligned(jpg, frame)
        # Таблица цветов: базовый цвет видимой области → цвет окраски. Берём самую
        # крупную область каждого цвета — мелкие часто попадают на смазанные края.
        core = ndimage.binary_erosion(fill_mask(frame), iterations=CORE)
        table: dict[tuple, tuple] = {}
        for sl, region in regions(frame, fill_mask(frame), least=50):
            m = region & core[sl]
            if m.sum() < 50:
                continue
            src = np.median(frame[sl][m][:, :3], axis=0)
            dst = np.median(self.target[sl][m], axis=0)
            key = tuple(np.round(src / 8).astype(int))
            if key not in table or table[key][2] < m.sum():
                table[key] = (src, dst, m.sum())
        self.src = np.array([v[0] for v in table.values()])
        self.dst = np.array([v[1] for v in table.values()])

    def layer(self, image: np.ndarray) -> np.ndarray:
        out = image.copy()
        rgb = image[..., :3]
        fills = (image[..., 3] > 0) & (rgb.max(axis=2) >= INK_MAX)
        # Видно в кадре: тот же цвет на том же месте.
        visible = fills & (np.abs(rgb - self.frame[..., :3]).max(axis=2) <= SAME) & fill_mask(self.frame)
        core = visible & ndimage.binary_erosion(fill_mask(self.frame), iterations=CORE)
        done = np.zeros(fills.shape, bool)
        for sl, region in regions(image, fills):
            seen = region & core[sl]
            if seen.sum() < 20:
                continue
            src = np.median(rgb[sl][seen], axis=0)
            dst = np.median(self.target[sl][seen], axis=0)
            part = out[sl]
            part[region, :3] = dst + (rgb[sl][region] - src)
            done[sl] |= region
        # Остальное — по таблице, со сглаживанием к линии: доля цвета против чёрного.
        rest = (image[..., 3] > 0) & ~done & (rgb.max(axis=2) > 0)
        if rest.any() and len(self.src):
            p = rgb[rest]
            d = ((p[:, None, :] - self.src[None]) ** 2).sum(axis=2)
            i = d.argmin(axis=1)
            s, t = self.src[i], self.dst[i]
            k = np.clip((p * s).sum(axis=1) / np.maximum((s * s).sum(axis=1), 1), 0, 1)[:, None]
            # Пиксель кайма между заливкой и линией: тот же оттенок, темнее на долю k.
            ink_edge = np.abs(p - s * k).max(axis=1) <= SAME
            same = np.sqrt(d.min(axis=1)) <= FAR
            new = np.where(same[:, None], t + (p - s), np.where(ink_edge[:, None], t * k, p))
            out[rest, :3] = new
        return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def make(pet: str, number: int, work: Path) -> str:
    name = f"look_{pet}_{number}"
    folder = SRC / pet
    frame = rgba(folder / f"{pet}_happy.png")
    body = next((p for p in (folder / f"{pet}_body.png", folder / f"{pet}_body_small.png") if p.exists()), None)
    if body is not None:
        frame = over(frame, rgba(body))
    jpg = np.asarray(Image.open(LOOKS_DIR / f"{name}.jpg").convert("RGB")).astype(np.float32)
    recolor = Recolor(frame, jpg)
    out = work / name
    out.mkdir()
    for path in sorted(folder.glob(f"{pet}_*.png")):
        layer = recolor.layer(rgba(path))
        Image.fromarray(layer, "RGBA").save(out / path.name.replace(pet, name, 1))
    return name


KOTLIN = Path("app/src/main/java/ru/finney/pet/ui/pet/PetLookSkins.kt")


def write_kotlin() -> None:
    """Скин облика — скин персонажа с картинками облика: точки, шея и шляпа те же."""
    def body(name: str, suffix: str) -> str:
        parts = ["torso", "left_hand", "right_hand", "left_leg", "right_leg"]
        return "PetBody(" + ", ".join(f"R.drawable.{name}_{p}{suffix}" for p in parts) + ")"

    cases = []
    for pet in split.PETS:
        for number in (1, 2):
            n = f"look_{pet}_{number}"
            cases.append(f"""        "{n}" -> base.copy(
            happy = R.drawable.{n}_base_happy,
            sad = R.drawable.{n}_base_sad,
            dirty = R.drawable.{n}_base_dirty,
            sleep = R.drawable.{n}_base_sleep,
            blinkHappy = R.drawable.{n}_blink_happy,
            blinkSad = R.drawable.{n}_blink_sad,
            blinkDirty = R.drawable.{n}_blink_dirty,
            mouth = R.drawable.{n}_mouth,
            bodies = listOf(
                {body(n, "")},
                {body(n, "_2")},
                {body(n, "_3")},
            ),
        )""")
    KOTLIN.write_text(f"""package ru.finney.pet.ui.pet

import ru.finney.pet.R

// Создано tools/make_pet_looks.py — не править руками, перезапустить инструмент.

/**
 * Скин платной окраски [lookId] поверх скина персонажа [base]: те же точки вращения,
 * шея, глаза и посадка шляпы, другие картинки. null — облик не этого персонажа или незнаком.
 */
internal fun lookSkin(lookId: String, base: PetSkin): PetSkin? =
    if (!lookId.startsWith("look_") || base != lookCharacterSkin(lookId)) null else when (lookId) {{
{chr(10).join(cases)}
        else -> null
    }}

/** Голова в окраске [lookId] — картинка награды цели; null — облик незнаком. */
internal fun lookArt(lookId: String): Int? = when (lookId) {{
{chr(10).join(f'        "look_{pet}_{n}" -> R.drawable.look_{pet}_{n}_base_happy' for pet in split.PETS for n in (1, 2))}
        else -> null
    }}

private fun lookCharacterSkin(lookId: String): PetSkin? =
    ru.finney.pet.domain.model.PetLooks.character(lookId)?.skin
""", encoding="utf-8")
    print(KOTLIN)


def main() -> None:
    if "--kotlin" in sys.argv:
        write_kotlin()
        return
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        split.SRC = work
        for pet, limb_suffix in split.PETS.items():
            for number in (1, 2):
                name = make(pet, number, work)
                print(f"— {name}")
                split.split(name, limb_suffix)
                split.blink(name)
                split.mouth(name)
    write_kotlin()


if __name__ == "__main__":
    main()
