"""Готовит слои комнаты из экспорта дизайнеров в ресурсы приложения.

Зачем: дизайнер рисует комнату слоями на общем холсте 1440×2400, каждый предмет
уже стоит на своём месте в кадре. Класть такие слои в ресурсы как есть нельзя —
у семи из девяти больше 88% площади это прозрачный воздух, за который Android
всё равно платит четырьмя байтами на пиксель. Полными холстами комната съедала бы
66 МБ растра, обрезанная по содержимому — 14 МБ. Крайний случай: НЛО занимает
152×118 пикселей, а в полном холсте развернулось бы в те же 3.3 МБ, что и стена.

Обрезаем по непрозрачным пикселям, а положение сохраняем долями холста — так же,
как PetSkin хранит точки вращения конечностей. Скрипт печатает готовые RelRect:
числа уезжают в RoomLayers.kt копипастой, вручную их не переписывают.

Это та же беда, что была у питомцев (см. split_pet_base.py), только с другой
стороны: там лечили размером, здесь — обрезкой. drawable-nodpi сам по себе не
виноват, он тут и нужен: комната позиционируется долями и масштабируется кодом,
и автоматическое масштабирование по плотности экрана только мешало бы.

Вход:  design/exports/environment/*.PNG — как прислал дизайнер;
       design/exports/environment/interiors/<тема>/*.PNG — сменные интерьеры.
Выход: app/src/main/res/drawable-nodpi/room_*.webp + числа для RoomLayers.kt
       и RoomInteriors.kt.

Без потерь, а не q90: края вырезаны по альфе, и лоссовая альфа даёт кайму по
контуру — на чёрной обводке комнаты её видно особенно.

Запуск из корня репозитория (нужен Pillow: pip install Pillow):
    py tools/pack_room.py
    py tools/pack_room.py rugs   — только ковры игровой

Перезапускать после каждого нового экспорта — и переносить напечатанные RelRect
в RoomLayers.kt, иначе предмет уедет с места.
"""

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from measure_layers import ALPHA_FLOOR  # noqa: E402  — соседний модуль, путь добавлен строкой выше

SRC = Path("design/exports/environment")
RES = Path("app/src/main/res/drawable-nodpi")

# Холст, на котором дизайнер рисовал все слои. Доли считаются от него, а не от
# размера обрезанного файла: предмет должен вставать туда же, где стоял в кадре.
CANVAS = (1440, 2400)

# Ширина комнаты в ресурсах. 1080 — потому что на экране 1080p чёрная обводка
# толщиной 14–18 пикселей при уменьшении до 720 заметно мылится, а разница в
# памяти между 1080 и 720 здесь всего 6 МБ: слои обрезаны, платим только за
# содержимое. Масштаб общий для всех слоёв, иначе предметы разъедутся.
TARGET_WIDTH = 1080

# Исходное имя → имя в ресурсах. Папка drawable-nodpi общая с питомцами, там уже
# четыре десятка файлов, поэтому префикс room_ обязателен.
# bath_foam и bath_foam_2 — не кадры анимации, а задний и передний слой пены:
# питомец садится между ними, поэтому в именах back и front, а не 1 и 2.
LAYERS = {
    "room_backrooms": "room_back",
    "window_view_ufo": "room_window_ufo",
    "window_frame": "room_window_frame",
    "lamp": "room_lamp",
    "dinner_table": "room_table",
    "bath": "room_bath",
    "bath_foam": "room_bath_foam_back",
    "bath_foam_2": "room_bath_foam_front",
    # Капсула для сна в зале и её дверь: питомец спит между ними, стекло двери
    # полупрозрачное, и спящего видно сквозь него.
    "sleep_capsule": "room_capsule",
    "sleep_capsule_door": "room_capsule_door",
}


def pack(source: str, target: str) -> tuple[str, tuple[float, float, float, float], int]:
    return pack_file(next(SRC.glob(f"{source}.[pP][nN][gG]")), target)


def open_canvas(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if image.size != CANVAS:
        raise SystemExit(f"{path.name}: холст {image.size}, а слои комнаты ждут {CANVAS}")
    return image


def pack_file(path: Path, target: str, box: tuple[int, int, int, int] | None = None):
    image = open_canvas(path)
    if box is None:
        mask = image.split()[3].point(lambda v: 255 if v > ALPHA_FLOOR else 0)
        box = mask.getbbox()
    if box is None:
        raise SystemExit(f"{path.name}: ни одного непрозрачного пикселя")

    cropped = image.crop(box)
    scale = TARGET_WIDTH / CANVAS[0]
    size = (max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale)))
    cropped = cropped.resize(size, Image.LANCZOS)

    # Слой, закрывающий весь холст, — это фон, и альфа-канал у него четверть веса
    # впустую. Полупрозрачных пикселей там быть не должно, но в экспорте их
    # приезжает под двенадцать тысяч с альфой до 150: видимо, след кисти по краю.
    # Сквозь фон смотреть некуда, он самый нижний, — гасим канал целиком.
    if box == (0, 0, CANVAS[0], CANVAS[1]):
        cropped = cropped.convert("RGB")

    out = RES / f"{target}.webp"
    cropped.save(out, "WEBP", lossless=True, method=6)

    rel = (box[0] / CANVAS[0], box[1] / CANVAS[1], box[2] / CANVAS[0], box[3] / CANVAS[1])
    return target, rel, out.stat().st_size


# Звёздное небо за окном медленно плывёт (RoomScene.kt, WindowSky), поэтому ему
# нужна полоса, которая стыкуется сама с собой. window_view — не такая полоса:
# это одна картинка с неровным рисованным краем. Но фон у неба ровный
# (#261F46), и если резать по столбцам без звёзд, левый край полосы совпадает
# с правым пиксель в пиксель — шва на стыке нет. Неровный край и прозрачные
# углы заливаются тем же фоном: их всё равно закрывает рама.
#
# Столбцы подобраны по экспорту: звёзды занимают 693..1210, края полосы
# отступают от них на 33 и 30 пикселей — примерно как звёзды друг от друга,
# и на стыке не видно ни пустой полосы, ни тесноты.
SKY_SOURCE = "window_view"
SKY_TARGET = "room_window_sky"
SKY_COLOR = (0x26, 0x1F, 0x46)
SKY_COLUMNS = (660, 1240)


def pack_sky() -> tuple[str, tuple[float, float, float, float], int]:
    path = next(SRC.glob(f"{SKY_SOURCE}.[pP][nN][gG]"))
    image = Image.open(path).convert("RGBA")
    if image.size != CANVAS:
        raise SystemExit(f"{path.name}: холст {image.size}, а слои комнаты ждут {CANVAS}")

    top, bottom = image.getbbox()[1::2]
    left, right = SKY_COLUMNS
    strip = image.crop((left, top, right, bottom))
    for x in (0, strip.width - 1):
        column = [strip.getpixel((x, y)) for y in range(strip.height)]
        if any(p[3] > ALPHA_FLOOR and p[:3] != SKY_COLOR for p in column):
            raise SystemExit(f"{path.name}: на краю полосы неба ({left + x}) звезда — на стыке будет шов")

    flat = Image.new("RGBA", strip.size, SKY_COLOR + (255,))
    flat.alpha_composite(strip)
    scale = TARGET_WIDTH / CANVAS[0]
    flat = flat.convert("RGB").resize((round(strip.width * scale), round(strip.height * scale)), Image.LANCZOS)

    out = RES / f"{SKY_TARGET}.webp"
    flat.save(out, "WEBP", lossless=True, method=6)
    rel = (left / CANVAS[0], top / CANVAS[1], right / CANVAS[0], bottom / CANVAS[1])
    return SKY_TARGET, rel, out.stat().st_size


# ---------- Сменные интерьеры ----------
#
# Тема — папка в interiors/: задник, рама, вид за окном и торшер на том же холсте
# 1440×2400, плюс то, что живёт за окном. Всё меняется по отдельности, поэтому
# у каждой части своё место на холсте, а не общее с другими темами.
# preview.PNG в каждой папке — собранная тема от художника для сверки, в ресурсы
# не идёт.

INTERIORS = SRC / "interiors"
INTERIOR_PARTS = ("back", "window_frame", "window_view", "lamp")

# Что живёт за окном. Пчела, рыбка и светлячки — один слой.
INTERIOR_LIFE = {
    "circus": ("wheel", "wheel_stand"),
    "honey": ("bee",),
    "sea": ("fish",),
    "forest": ("fireflies",),
    "town": (),
    "oasis": (),
}

# Кабинки колеса обозрения нарисованы одним слоем, а вращаются по отдельности
# и при этом висят ровно — каждая становится своим файлом room_circus_cabin_N.
INTERIOR_SPLIT = {("circus", "wheel_cabins"): "cabin"}

# В этих слоях художник оставил в стороне от рисунка точки по 1–3 пикселя:
# по ним слой обрезался бы с огромным полем пустоты (у колеса — на 66 px вниз,
# у вида леса — на 175 px вправо, у вида городка — до края холста). Обрезаем
# по крупным пятнам, точки отбрасываем.
INTERIOR_SPECKS = {("circus", "wheel"), ("forest", "window_view"), ("town", "window_view")}

# Пятно мельче этой доли самого крупного в слое — мусор, а не часть рисунка.
SPECK_SHARE = 0.01

# Шаг сетки, по которой ищутся пятна: по каждому пикселю холста Python ходил бы минуту.
SPOT_STEP = 2


def spots(image: Image.Image) -> list[tuple[int, tuple[int, int, int, int]]]:
    """Связные пятна непрозрачного: (размер, рамка в пикселях холста), крупные первыми."""
    alpha = image.split()[3].load()
    w, h = image.size
    left = {(x, y) for y in range(0, h, SPOT_STEP) for x in range(0, w, SPOT_STEP) if alpha[x, y] > ALPHA_FLOOR}
    found = []
    while left:
        start = left.pop()
        stack, xs, ys = [start], [start[0]], [start[1]]
        while stack:
            x, y = stack.pop()
            for dx in (-SPOT_STEP, 0, SPOT_STEP):
                for dy in (-SPOT_STEP, 0, SPOT_STEP):
                    near = (x + dx, y + dy)
                    if near in left:
                        left.remove(near)
                        stack.append(near)
                        xs.append(near[0])
                        ys.append(near[1])
        box = (max(0, min(xs) - SPOT_STEP), max(0, min(ys) - SPOT_STEP), min(w, max(xs) + SPOT_STEP), min(h, max(ys) + SPOT_STEP))
        found.append((len(xs), box))
    return sorted(found, reverse=True)


def big_spots(path: Path) -> list[tuple[int, int, int, int]]:
    found = spots(open_canvas(path))
    return [box for size, box in found if size >= found[0][0] * SPECK_SHARE]


def union(boxes: list[tuple[int, int, int, int]]) -> tuple[int, int, int, int]:
    return min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes)


def pack_interiors() -> dict[str, list[tuple[str, str, tuple[float, float, float, float], int]]]:
    """По теме: (часть, имя ресурса, место на холсте, байт на диске)."""
    themes = {}
    for theme, life in INTERIOR_LIFE.items():
        folder = INTERIORS / theme
        packed = []
        for part in INTERIOR_PARTS + life:
            path = next(folder.glob(f"{part}.[pP][nN][gG]"))
            box = union(big_spots(path)) if (theme, part) in INTERIOR_SPECKS else None
            packed.append((part, *pack_file(path, f"room_{theme}_{part}", box)))
        for (split_theme, source), name in INTERIOR_SPLIT.items():
            if split_theme != theme:
                continue
            path = next(folder.glob(f"{source}.[pP][nN][gG]"))
            # Слева направо, сверху вниз — порядок номеров не зависит от обхода пятен.
            boxes = sorted(big_spots(path), key=lambda b: (b[1] // 100, b[0]))
            for i, box in enumerate(boxes, start=1):
                packed.append((f"{name}_{i}", *pack_file(path, f"room_{theme}_{name}_{i}", box)))
        themes[theme] = packed
    return themes


# ---------- Ковры игровой ----------
#
# Ковёр — не слой холста: художник рисует его отдельно, уже в перспективе пола, на
# квадрате 2000×2000. Место на полу ему выбирает код (RoomRugs.kt) по ширине экрана,
# как ящику для игрушек, поэтому здесь только обрезка и размер. Ширина — чуть меньше
# экрана 1080p: на телефоне ковёр почти во всю ширину видимой части комнаты.
#
# Имя файла — id темы.

RUGS = SRC / "rugs"
RUG_THEMES = ("default", "circus", "honey", "sea", "forest", "town", "oasis")
RUG_WIDTH = 1000


def pack_rugs() -> list[tuple[str, float, int]]:
    """По теме: (имя ресурса, ширина к высоте, байт на диске)."""
    packed = []
    for theme in RUG_THEMES:
        path = next(RUGS.glob(f"{theme}.[pP][nN][gG]"))
        image = Image.open(path).convert("RGBA")
        box = image.split()[3].point(lambda v: 255 if v > ALPHA_FLOOR else 0).getbbox()
        if box is None:
            raise SystemExit(f"{path.name}: ни одного непрозрачного пикселя")
        cropped = image.crop(box)
        height = round(cropped.height * RUG_WIDTH / cropped.width)
        cropped = cropped.resize((RUG_WIDTH, height), Image.LANCZOS)
        out = RES / f"room_rug_{theme}.webp"
        cropped.save(out, "WEBP", lossless=True, method=6)
        packed.append((out.stem, RUG_WIDTH / height, out.stat().st_size))
    return packed


def kotlin_rect(rel: tuple[float, float, float, float]) -> str:
    return f"RelRect({rel[0]:.4f}f, {rel[1]:.4f}f, {rel[2]:.4f}f, {rel[3]:.4f}f)"


def main() -> None:
    # Git Bash на Windows отдаёт stdout в cp1251, и кириллица в выводе бьётся.
    sys.stdout.reconfigure(encoding="utf-8")

    RES.mkdir(parents=True, exist_ok=True)
    rugs = pack_rugs()
    print("Для RoomRugs.kt (ширина к высоте):\n")
    for target, aspect, _ in rugs:
        print(f"    R.drawable.{target} — {aspect:.4f}f")
    print(f"\nКовров {len(rugs)}, {sum(size for *_, size in rugs) // 1024} КБ на диске\n")
    # Только ковры: остальные слои не трогаем, их числа уже в RoomLayers.kt.
    if sys.argv[1:] == ["rugs"]:
        return

    packed = [pack(source, target) for source, target in LAYERS.items()] + [pack_sky()]
    themes = pack_interiors()

    total_disk = sum(size for _, _, size in packed)
    theme_disk = sum(size for layers in themes.values() for *_, size in layers)
    print(f"Упаковано {len(packed)} слоёв, {total_disk // 1024} КБ на диске")
    print(f"Интерьеров {len(themes)}, слоёв {sum(map(len, themes.values()))}, {theme_disk // 1024} КБ на диске\n")
    print("Для RoomLayers.kt:\n")
    for target, rel, _ in packed:
        camel = "".join(part.capitalize() for part in target.removeprefix("room_").split("_")) or "Back"
        print(f"    val {camel} = RoomLayer(R.drawable.{target}, {kotlin_rect(rel)})")

    print("\nДля RoomInteriors.kt:")
    for theme, layers in themes.items():
        print(f"\n    // {theme}")
        for part, target, rel, _ in layers:
            camel = "".join(word.capitalize() for word in part.split("_"))
            print(f"    private val {camel} = RoomLayer(R.drawable.{target}, {kotlin_rect(rel)})")


if __name__ == "__main__":
    main()
