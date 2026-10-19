"""Предпросмотр тем комнаты для копилки, «Моего пути» и плана.

Собирает из готовых слоёв (задник, вид за окном, рама, торшер, живность окна)
верх комнаты — стену с окном и торшером до полоски пола, — скругляет углы
и обводит ink, как карточки UI-кита. Результат —
`res/drawable-nodpi/room_<тема>_preview.webp` без потерь, 512 px шириной.

Раньше наградой-темой рисовали один вид за окном: слой без рамы и стены,
по нему комнату было не узнать.

Координаты — те же доли холста, что в `ui/room/RoomInteriors.kt`. Поменялись
там — поменять здесь и перезапустить:

    py -I tools/make_room_previews.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
INK = (0x38, 0x2C, 0x92, 255)
WIDTH = 512
FLOOR_STRIP = 0.10  # сколько пола видно под швом — доля холста

# Слои снизу вверх: (имя слоя, left, top, right, bottom). Задник — во весь холст.
THEMES = {
    "circus": (0.4650, [
        ("window_view", 0.5340, 0.0667, 0.9368, 0.3121),
        ("wheel_stand", 0.7868, 0.2004, 0.8597, 0.2967),
        ("wheel", 0.7389, 0.1625, 0.9083, 0.2642),
        ("cabin_1", 0.7667, 0.1633, 0.7944, 0.1808),
        ("cabin_2", 0.8500, 0.1642, 0.8778, 0.1817),
        ("cabin_3", 0.7500, 0.2358, 0.7778, 0.2533),
        ("cabin_4", 0.8667, 0.2367, 0.8944, 0.2542),
        ("window_frame", 0.4688, 0.0375, 0.9847, 0.3296),
        ("lamp", 0.0389, 0.0754, 0.2986, 0.4917),
    ]),
    "honey": (0.4850, [
        ("window_view", 0.4243, 0.0508, 0.9326, 0.3217),
        ("bee", 0.6722, 0.1500, 0.7368, 0.1879),
        ("window_frame", 0.3861, 0.0362, 0.9632, 0.3329),
        ("lamp", 0.0076, 0.0679, 0.2743, 0.5158),
    ]),
    "sea": (0.4758, [
        ("window_view", 0.5104, 0.1013, 0.8938, 0.3100),
        ("fish", 0.6424, 0.1933, 0.7188, 0.2208),
        ("window_frame", 0.3757, 0.0550, 0.9861, 0.3525),
        ("lamp", 0.0111, 0.1117, 0.2660, 0.5158),
    ]),
    "forest": (0.4996, [
        ("window_view", 0.5056, 0.1292, 0.8639, 0.3367),
        ("fireflies", 0.5597, 0.1858, 0.8514, 0.3287),
        ("window_frame", 0.3792, 0.0838, 0.9951, 0.4050),
        ("lamp", 0.0361, 0.1338, 0.2694, 0.5337),
    ]),
    "town": (0.5058, [
        ("window_view", 0.5750, 0.0858, 0.8778, 0.2808),
        ("window_frame", 0.4597, 0.0375, 0.9944, 0.3162),
        ("lamp", 0.0514, 0.0954, 0.2875, 0.5254),
    ]),
}


def layer(theme: str, name: str) -> Image.Image:
    return Image.open(RES / f"room_{theme}_{name}.webp").convert("RGBA")


def preview(theme: str, floor_top: float, layers) -> Image.Image:
    room = layer(theme, "back")
    w, h = room.size
    for name, left, top, right, bottom in layers:
        part = layer(theme, name).resize((round((right - left) * w), round((bottom - top) * h)), Image.LANCZOS)
        room.alpha_composite(part, (round(left * w), round(top * h)))

    crop = room.crop((0, 0, w, round((floor_top + FLOOR_STRIP) * h)))
    out_h = round(crop.height * WIDTH / crop.width)
    small = crop.resize((WIDTH, out_h), Image.LANCZOS)

    # Обводка — 1/12 высоты карточки, как у панелей; скругление — как у RadiusCard.
    stroke = max(4, out_h // 24)
    radius = out_h // 6
    mask = Image.new("L", small.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, WIDTH - 1, out_h - 1), radius, fill=255)
    card = Image.new("RGBA", small.size, (0, 0, 0, 0))
    card.paste(small, (0, 0), mask)
    # PIL ведёт обводку внутрь от края — по тому же контуру, что и маска: углы не торчат.
    ImageDraw.Draw(card).rounded_rectangle((0, 0, WIDTH - 1, out_h - 1), radius, outline=INK, width=stroke)
    return card


def main() -> None:
    for theme, (floor_top, layers) in THEMES.items():
        out = RES / f"room_{theme}_preview.webp"
        preview(theme, floor_top, layers).save(out, "WEBP", lossless=True)
        print(out.name)


if __name__ == "__main__":
    main()
