"""Готовит слои питомцев из экспорта дизайнеров в ресурсы приложения.

Зачем: дизайнер экспортирует состояния лица (happy/sad/dirty/sleep) вместе с уже
впечатанными руками и ногами. Чтобы шевелить конечностями отдельно, из состояния
нужно убрать те же руки и ноги — иначе под повёрнутой рукой видно исходную.
Ноги и руки обычного размера в состояниях совпадают пиксель в пиксель с
отдельными слоями, поэтому вырез получается точным.

Вырезаем не вычитанием альфы, а обратным альфа-смешиванием — см. [uncomposite].
Вычитание давало по контуру рук и ног светлый шов в пиксель шириной: на сглаженном
краю альфа делится между рукой и телом, и при обратной сборке ни одна из половин
не восстанавливала полную непрозрачность.

Вход:  design/exports/pet/<питомец>/*.png — как прислал дизайнер (docs/assets-spec.md).
Выход: app/src/main/res/drawable-nodpi/*.webp — только то, что рисует PetView.

Питомцев несколько, у каждого своя папка и свой размер холста; отличаются только
именами файлов конечностей, поэтому весь разбор описан в PETS.

PNG в ресурсы не кладём: те же картинки в WebP без потерь весят втрое меньше.
Без потерь, а не q90: базовые слои вырезаны по альфе, и лоссовая альфа даёт кайму
по краю выреза — её видно из-под повёрнутой руки.

Стадии роста: туловище — отдельный слой, голова (base_*) — без него. Для обликов
постарше туловище, руки и ноги увеличиваются здесь же, а контур утончается обратно
до прежней толщины — см. [grow]. Файлы _middle и _big от дизайнеров не нужны:
это те же слои в другом масштабе, и линия у них толще.

Здесь же собираются слои моргания — см. [blink] — и открытого рта — см. [mouth].

Запуск из корня репозитория (нужны Pillow и scipy: pip install Pillow scipy):
    python tools/split_pet_base.py                 # все питомцы
    python tools/split_pet_base.py luchik bantik   # только эти
    python tools/split_pet_base.py --small         # сжатие посильнее, для выпуска

Питомцы разбираются параллельно, по процессу на питомца.

Перезапускать после каждого нового экспорта от дизайнера.
"""

import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageMath
from scipy import ndimage

SRC = Path("design/exports/pet")
RES = Path("app/src/main/res/drawable-nodpi")

# Сторона слоя в ресурсах. Холсты у дизайнеров по 2200, и класть их в
# drawable-nodpi как есть нельзя: nodpi запрещает Android масштабировать картинку
# при загрузке, поэтому слой разворачивается в памяти целиком — 2200×2200×4 ≈ 19 МБ.
# На питомца пять слоёв, а на экране подбора их двое: выходило под 190 МБ растра
# ради кружка в 400 пикселей, и кадр падал до 200 мс (5 fps) при 60 на главном.
# 512 хватает с запасом: крупнее всего питомца рисуют примерно в 400 px.
SIDE = 512

# Насколько упорно жать WebP. Без потерь при любом значении — меняется только размер
# файла: 6 на несколько процентов меньше, но в разы дольше, и весь прогон шёл минут десять.
METHOD = 6 if "--small" in sys.argv else 4

# Сколько питомцев разбирать одновременно. Каждый процесс держит несколько холстов
# 2200×2200 во float — по несколько сотен МБ; десять окрасок разом не влезли в память.
WORKERS = 4
STATES = ("happy", "sad", "dirty", "sleep")
LIMBS = ("left_leg", "right_leg", "left_hand", "right_hand")

# Имя питомца → суффикс файлов конечностей обычного размера. У Пушистика он пустой
# (pushistik_left_hand.png), у Рогатика размер указан явно (rogatik_left_hand_small.png).
PETS = {"pushistik": "", "rogatik": "_small", "zvezdochka": "", "bantik": "", "luchik": ""}


def load(pet: str, name: str) -> Image.Image:
    return Image.open(SRC / pet / f"{pet}_{name}.png").convert("RGBA")


def save(image: Image.Image, pet: str, name: str) -> None:
    out = RES / f"{pet}_{name}.webp"
    if max(image.size) > SIDE:
        image = image.resize((SIDE, SIDE), Image.LANCZOS)
    if name.startswith(("base_", "blink_")) or name == "mouth":
        image = drop_head(image)
    image.save(out, "WEBP", lossless=True, method=METHOD)
    print(f"{out} — {out.stat().st_size // 1024} КБ")


def drop_head(image: Image.Image) -> Image.Image:
    """Голова в слоях ниже нарисованной на [HEAD_DROP] пикселей слоя: в старших обликах
    иначе виден зазор под подбородком. В PetSkin.kt глаза, рот и шляпа сдвинуты так же."""
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.paste(image.crop((0, 0, image.size[0], image.size[1] - HEAD_DROP)), (0, HEAD_DROP))
    return out


# Как HEAD_DROP в PetSkin.kt.
HEAD_DROP = 4


def uncomposite(state_alpha: Image.Image, limb_alpha: Image.Image) -> Image.Image:
    """Альфа тела: сколько его должно остаться, чтобы рука поверх дала исходный кадр.

    Рука кладётся на тело обычным альфа-смешиванием: state = limb + base * (1 - limb).
    Отсюда base = (state - limb) / (1 - limb). Наивное `state - limb` — это тот же
    числитель без делителя, и на сглаженном крае (limb = 0.5 при state = 1) оно даёт
    тело в половину непрозрачности вместо полной: рука возвращает свои 0.5, тело —
    половину от оставшегося, вместе 0.75, и по контуру видно светлый шов.

    Там, где рука полностью непрозрачна, тело не видно никогда и делитель обращается
    в ноль — оставляем вырез (альфа 0), иначе под повёрнутой рукой покажется исходная.
    """
    return ImageMath.lambda_eval(
        lambda v: v["convert"](
            v["min"]((v["s"] - v["l"]) * 255.0 / v["max"](255.0 - v["l"], 1.0), 255.0),
            "L",
        ),
        s=state_alpha.convert("F"),
        l=limb_alpha.convert("F"),
    )


def split(pet: str, limb_suffix: str) -> None:
    limbs = None
    for limb in LIMBS:
        image = load(pet, limb + limb_suffix)
        if limbs is None:
            limbs = Image.new("RGBA", image.size, (0, 0, 0, 0))
        limbs.alpha_composite(image)
        # В ресурсах суффикс размера не нужен: рисуется только обычный.
        save(image, pet, limb)
    limb_alpha = limbs.split()[3]

    # Туловище — отдельный слой: с уровнем оно растёт вместе с руками и ногами,
    # а голова остаётся прежней (стадии роста, ui/pet/PetGrowth.kt).
    torso = load(pet, "body" + limb_suffix)
    drawn = torso.copy()
    # Слой туловища у дизайнеров местами на пиксель-два шире, чем туловище в кадре
    # (под краем руки): обрезаем по тому, что остаётся от кадра без рук. Такое туловище
    # нужно только для разбора головы ниже.
    frame_alpha = np.asarray(uncomposite(load(pet, "happy").split()[3], limb_alpha))
    torso.putalpha(Image.fromarray(np.minimum(np.asarray(torso.split()[3]), frame_alpha)))
    # В приложение — по кадру вместе с руками: плечи под руками остаются. Их нарисовали
    # как раз на то, чтобы под поднятой рукой было туловище; обрезанное по кадру без рук,
    # оно теряло плечи, и под рукой открывался белый клин.
    drawn.putalpha(Image.fromarray(np.minimum(np.asarray(drawn.split()[3]), np.asarray(load(pet, "happy").split()[3]))))
    save(drawn, pet, "torso")
    rows = np.flatnonzero(np.asarray(torso.split()[3]).any(axis=1))
    ground = np.flatnonzero(np.asarray(load(pet, "happy").split()[3]).any(axis=1))[-1] + 1
    print(f"{pet}: туловище сверху {rows[0]}, ступни {ground} из {torso.size[1]}")

    # Облики постарше: те же туловище и конечности крупнее, линия прежней толщины.
    line = stroke_width(torso)
    # Увеличение — самое долгое в разборе; сделанное здесь берёт и check_looks.
    grown = {}
    for look, scale in LOOKS.items():
        big_torso = grow(drawn, scale, ground, line)
        big_limbs = [grow(load(pet, limb + limb_suffix), scale, ground, line, leg_raise(limb, scale, line)) for limb in LIMBS]
        save(big_torso, pet, f"torso_{look}")
        for limb, image in zip(LIMBS, big_limbs):
            save(image, pet, f"{limb}_{look}")
        grown[look] = (big_torso, big_limbs)

    for state in STATES:
        src = load(pet, state)
        base = src.copy()
        base.putalpha(uncomposite(src.split()[3], limb_alpha))
        head = head_only(pet, state, src, base, limb_alpha, torso)
        check_seamless(head, torso, limbs, src, pet, state)
        save(head, pet, f"base_{state}")
        if state == "happy":
            check_looks(pet, head, drawn, limb_suffix, ground, grown)


def leg_raise(limb: str, scale: float, line: float) -> float:
    """На сколько поднять ногу в облике: на всю лишнюю толщину линии — сумму того,
    на что поднялся низ туловища и опустился верх ноги. Стопы отрываются от пола на
    столько же, в приложении это пиксель-два. Руки не трогаем: их стык с туловищем
    сбоку, и щели там нет."""
    return line * (scale - 1) if limb.endswith("leg") else 0.0


def head_attach(head: Image.Image) -> int:
    """Низ головы посередине — по нему голова садится на подросшее туловище.

    Не верх туловища: у Пушистика воротник — часть головы и свисает ниже, и при
    подъёме головы по верху туловища между воротником и плечами открывалась щель.
    Верх туловища при такой посадке уходит глубже под голову, а она рисуется поверх.
    """
    alpha = np.asarray(head.split()[3])
    mid = head.size[0] // 2
    return int(np.flatnonzero(alpha[:, mid - 8:mid + 8].max(axis=1) > 128)[-1])


def look_frame(head: Image.Image, torso: Image.Image, limbs: list[Image.Image], lift: int) -> Image.Image:
    """Питомец в облике: туловище, ноги и руки, голова поднята на [lift] пикселей."""
    frame = Image.new("RGBA", head.size, (0, 0, 0, 0))
    frame.alpha_composite(torso)
    for limb in limbs:
        frame.alpha_composite(limb)
    lifted = Image.new("RGBA", head.size, (0, 0, 0, 0))
    lifted.paste(head.crop((0, lift, head.size[0], head.size[1])), (0, 0))
    frame.alpha_composite(lifted)
    return frame


def gaps(frame: Image.Image) -> int:
    """Щели: пустые пиксели, со всех сторон зажатые телом, — разрез между частями.

    Считаются в том размере, в каком слой лежит в приложении ([SIDE]): на холсте
    дизайнера «щелью» оказывались кончики острых клиньев фона между прядями и у ног,
    а в приложении их сглаживает уменьшение. Разрез в пиксель шириной остаётся.
    """
    solid = np.asarray(shrink(frame).split()[3]) > 128
    closed = ndimage.binary_closing(solid, iterations=GAP_RADIUS)
    return int((closed & ~solid).sum())


def check_looks(pet: str, head: Image.Image, torso: Image.Image, limb_suffix: str, ground: int,
                grown: dict[int, tuple[Image.Image, list[Image.Image]]]) -> None:
    """Щели в каждом облике против питомца как нарисован; больше чем на GAP_PIXELS — проверить.
    [grown] — увеличенные туловище и конечности, уже сделанные в split."""
    attach = head_attach(head)
    print(f"{pet}: голова садится по {attach} из {head.size[1]}")
    limbs = [load(pet, limb + limb_suffix) for limb in LIMBS]
    before = gaps(look_frame(head, torso, limbs, 0))
    for look, scale in LOOKS.items():
        big_torso, big_limbs = grown[look]
        lift = round((ground - attach) * (scale - 1))
        found = gaps(look_frame(head, big_torso, big_limbs, lift))
        # Только отчёт: у подросшего тела другая форма, и в вогнутых углах силуэта —
        # подмышки, между ногами — «закрытие» находит пиксель-другой и без разрезов.
        # Разрез между частями даёт десятки пикселей подряд — его видно в этой строке.
        flag = "  ← проверить глазами" if found > before + GAP_PIXELS else ""
        print(f"{pet} облик {look}: щелей {found} px, у исходного {before}{flag}")


# Щель — трещина в 1–2 пикселя слоя в приложении.
GAP_RADIUS = 1
GAP_PIXELS = 12


# Облики стадий роста: номер облика → во сколько раз туловище, руки и ноги крупнее
# обычного. Должно совпадать с TorsoScale в ui/pet/PetGrowth.kt.
LOOKS = {2: 1.25, 3: 1.5}

# Линия — тёмные непрозрачные пиксели. Контур у всех питомцев почти чёрный.
INK_MAX = 60

# На сколько пикселей холста вокруг линии пересчитывается её сглаженная кайма.
EDGE = 3


def ink_mask(image: np.ndarray) -> np.ndarray:
    return (image[..., 3] > 128) & (image[..., :3].max(axis=2) < INK_MAX)


def stroke_width(layer: Image.Image) -> float:
    """Толщина контура в пикселях холста: медиана по гребню линии."""
    ink = ink_mask(pixels(layer))
    dt = ndimage.distance_transform_edt(ink)
    ridge = (dt > 0) & (dt == ndimage.maximum_filter(dt, size=3))
    return float(np.median(dt[ridge]) * 2)


def grow(layer: Image.Image, scale: float, ground: int, line: float, raise_by: float = 0.0) -> Image.Image:
    """Слой крупнее в [scale] раз от ступней, а контур — прежней толщины [line].

    Просто увеличенный рисунок даёт линию в [scale] раз толще: на старших стадиях
    обводка тела становилась жирнее, чем у головы. Поэтому линию после увеличения
    утончаем обратно — с обеих сторон на половину лишней ширины. Освободившиеся
    пиксели берут цвет ближайшего пикселя не-линии: снаружи это пустота, и силуэт
    не раздувается, внутри — заливка тела. Край новой линии мягкий, как у рисунка.

    Утончать только внутрь пробовали: увеличенный силуэт оставался целиком, части
    выглядели раздутыми, а замкнутый по верху туловища контур лёг чёрными линиями
    внутри заливок. Щели на стыках, которые даёт утончение с двух сторон, закрывает
    посадка головы по низу подбородка — см. [head_attach].

    [raise_by] — поднять слой на столько пикселей холста. Нужно ногам: утончение
    с двух сторон поднимает низ туловища и опускает верх ног на половину лишней
    толщины линии каждое, и между ними открывался разрез — см. [leg_raise].
    """
    w, h = layer.size
    cx = w / 2
    # Обратное преобразование для Image.transform: точка результата → точка исходника.
    big = layer.transform(
        (w, h), Image.AFFINE,
        (1 / scale, 0, cx - cx / scale, 0, 1 / scale, ground - ground / scale + raise_by / scale),
        resample=Image.BICUBIC,
    )
    img = pixels(big)
    core = ink_mask(img)
    # Сглаженная кайма линии светлее порога: пересчитываем и её, иначе она осталась бы
    # на старом месте бледным кольцом вокруг новой, тонкой линии.
    region = ndimage.binary_dilation(core, iterations=EDGE) & (img[..., 3] > 0)
    _, (iy, ix) = ndimage.distance_transform_edt(region, return_indices=True)
    under = img[iy, ix].astype(float)
    dt = ndimage.distance_transform_edt(core)
    keep = np.clip(dt - line * (scale - 1) / 2 + 0.5, 0.0, 1.0)
    colour = np.median(img[core][:, :3], axis=0)
    a_under = under[..., 3] / 255.0
    a = keep + a_under * (1 - keep)
    rgb = (colour * keep[..., None] + under[..., :3] * (a_under * (1 - keep))[..., None]) / np.maximum(a, 1e-6)[..., None]
    result = np.where(region[..., None], np.dstack([rgb, a * 255.0]), img)
    return Image.fromarray(np.clip(np.rint(result), 0, 255).astype(np.uint8), "RGBA")


# Насколько цвет пикселя может отличаться от туловища, чтобы считаться туловищем:
# только для Пушистика, у которого нет отдельного слоя головы.
TORSO_TOLERANCE = 24


def head_only(pet: str, state: str, src: Image.Image, base: Image.Image, limb_alpha: Image.Image, torso: Image.Image) -> Image.Image:
    """Голова с лицом настроения — без туловища, рук и ног.

    Если у питомца есть слой головы, силуэт берётся из него, а из кадра настроения —
    только то, что лежит внутри головы и не закрыто рукой: лицо, брови, грязь. Там, где
    в кадре голову закрывала рука, и по сглаженному краю берём пиксель слоя головы:
    иначе в голове осталась бы рука или полоска туловища у подбородка.

    У Пушистика слоя головы нет: из кадра без рук убираем пиксели, совпадающие
    с туловищем. Голова лежит поверх туловища, поэтому совпадает только его видимая часть.
    """
    s = pixels(src)
    name = "head_dirty" if state == "dirty" and (SRC / pet / f"{pet}_head_dirty.png").exists() else "head"
    path = SRC / pet / f"{pet}_{name}.png"
    if path.exists():
        h = pixels(Image.open(path).convert("RGBA"))
        from_head = (np.asarray(limb_alpha) > 0) | (h[..., 3] < 255)
        out = np.where(from_head[..., None], h, s)
        # Прозрачность — из слоя головы, но где под ней нет ни туловища, ни рук,
        # из кадра: сглаженный край у слоя и кадра местами расходится на пару единиц.
        alone = (h[..., 3] > 0) & (pixels(torso)[..., 3] == 0) & (np.asarray(limb_alpha) == 0)
        out[..., 3] = np.where(alone, s[..., 3], h[..., 3])
    else:
        t = pixels(torso)
        out = pixels(base)
        is_torso = (
            (t[..., 3] > 0)
            # Прозрачность — без рук: на стыке их сглаженный край добавляет альфу.
            & (np.abs(out[..., 3] - t[..., 3]) <= 3)
            & (np.abs(s[..., :3] - t[..., :3]).max(axis=2) <= TORSO_TOLERANCE)
        )
        # Чёрная обводка головы там, где она легла на обводку туловища, совпадает с ним
        # по цвету — её возвращаем, если рядом заливка головы: иначе у воротника дырки.
        ink = ink_mask(s)
        head_fill = (s[..., 3] > 128) & ~is_torso & ~ink
        near_head = ndimage.distance_transform_edt(~head_fill) <= stroke_width(torso)
        is_torso &= ~(ink & near_head)
        out[..., 3] = np.where(is_torso, 0, out[..., 3])
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def check_seamless(head: Image.Image, torso: Image.Image, limbs: Image.Image, src: Image.Image, pet: str, state: str) -> None:
    """Собранный обратно кадр должен совпасть с тем, что прислал дизайнер.

    Порядок как в PetView: туловище, руки и ноги, голова. Стережёт от шва: если голова
    или вырез когда-нибудь начнут «съедать» альфу по контуру, силуэт разойдётся
    с оригиналом и сборка упадёт здесь, а не в приложении.
    """
    rebuilt = Image.new("RGBA", src.size, (0, 0, 0, 0))
    rebuilt.alpha_composite(torso)
    rebuilt.alpha_composite(limbs)
    rebuilt.alpha_composite(head)
    diff = np.abs(np.asarray(rebuilt).astype(int) - pixels(src))
    seam = int((diff[..., 3] > SEAM_ALPHA).sum())
    seen = pixels(src)[..., 3] > 0
    colour = (diff[..., :3].max(axis=2)[seen] > 48).mean()
    print(f"{pet} {state}: по силуэту разошлось {seam} px, по цвету {colour:.3%}")
    if seam > SEAM_PIXELS:
        raise SystemExit(f"{pet} {state}: силуэт разошёлся с оригиналом на {seam} пикселях — шов по контуру")


# Порог шва: пиксель разошёлся больше чем на SEAM_ALPHA из 255, и таких больше SEAM_PIXELS.
# Шов от вычитания альфы давал 64 по всему контуру рук — тысячи пикселей.
SEAM_ALPHA = 16
SEAM_PIXELS = 16  # TODO: вернуть 2 — у Звёздочки 11–12 px под краем правой руки


# Маска моргания в пикселях слоя (SIDE): до BLINK_CORE от глаз — сплошная, дальше
# за BLINK_FEATHER сходит на нет. Ядро Lanczos при уменьшении размазывает разницу
# глаз пиксели на три, сплошная часть это накрывает, а растушёвка лежит там, где
# открытые и закрытые глаза уже совпадают до бита.
BLINK_CORE = 4
BLINK_FEATHER = 4
# Столько же отступаем от рта: он в моргании остаётся от настроения.
BLINK_MOUTH_GAP = 3


def blink(pet: str) -> None:
    """Слой моргания: закрытые глаза из сна, положенные поверх открытых.

    Отдельных слоёв глаз дизайнеры не присылают, а у Пушистика нет и слоя лица.
    Зато открытые и закрытые глаза уже есть в состояниях: sleep отличается от happy
    только глазами и ртом. Разница этих двух кадров и есть маска глаз — рот из неё
    выкидываем, пусть во время моргания остаётся от текущего настроения.

    Слой на каждое настроение свой, потому что брови грусти и грязь лежат поверх глаз:
    там, где настроение отличается от happy, в слое остаётся пиксель настроения,
    а в остальной маске — пиксель сна.

    Маска накладывается на уже уменьшенные кадры, а не уменьшается вместе с ними.
    Жёсткий край альфы Lanczos раскачивает: у почти прозрачных пикселей цвет после
    обратного деления на альфу уходит в белое, и на каждом моргании вокруг глаз
    вспыхивал светлый контур маски.
    """
    full = {state: load(pet, state) for state in STATES}
    changed = np.abs(pixels(full["happy"]) - pixels(full["sleep"])).max(axis=2) > 0

    # Разница распадается на три пятна: два глаза и рот. Каждый глаз нарисован
    # несколькими штрихами, поэтому пятна ищем по чуть расширенной разнице. Рот
    # отделять по столбцам нельзя: у Звёздочки он заходит под глаз по горизонтали.
    parts, count = ndimage.label(ndimage.binary_dilation(changed, iterations=12))
    if count != 3:
        raise SystemExit(f"{pet}: ждали глаз, глаз и рот — нашлось {count} частей лица")
    sizes = ndimage.sum(changed, parts, range(1, count + 1))
    eyes = np.isin(parts, np.argsort(sizes)[-2:] + 1) & changed
    mouth = changed & ~eyes

    to_eyes = ndimage.distance_transform_edt(~shrink_mask(eyes))
    to_mouth = ndimage.distance_transform_edt(~shrink_mask(mouth))
    mask = np.clip((BLINK_CORE + BLINK_FEATHER - to_eyes) / BLINK_FEATHER, 0.0, 1.0)
    mask *= np.clip((to_mouth - BLINK_MOUTH_GAP) / BLINK_MOUTH_GAP, 0.0, 1.0)

    # Полоса глаз по высоте — в её пределах в PetSkin опускается веко (eyesTop, eyesBottom).
    rows = np.flatnonzero((mask > 0).any(axis=1))
    print(f"{pet}: глаза по высоте {rows[0]}..{rows[-1] + 1} из {SIDE}")

    frames = {state: pixels(shrink(image)) for state, image in full.items()}
    for state in ("happy", "sad", "dirty"):
        mood = frames[state]
        own = np.abs(mood - frames["happy"]).max(axis=2) > 0
        layer = np.where(own[..., None], mood, frames["sleep"])
        layer[..., 3] = np.rint(layer[..., 3] * mask)
        save(Image.fromarray(layer.astype(np.uint8), "RGBA"), pet, f"blink_{state}")


# Маска рта в пикселях слоя: сплошная до MOUTH_CORE от любого из ртов, дальше
# за MOUTH_FEATHER сходит на нет. Запас нужен небольшой: слой при еде растягивают,
# а до линии подбородка у Пушистика от рта всего пикселей пять.
MOUTH_CORE = 2
MOUTH_FEATHER = 2


def mouth(pet: str) -> None:
    """Слой открытого рта: рот из сна поверх рта любого настроения.

    Рисованного «рта для еды» у дизайнеров нет, но во сне у всех пятерых рот
    приоткрыт — его и берём. Вокруг рта лицо залито ровно, поэтому пиксели сна
    в маске закрывают улыбку или грустную дугу без шва. Маска — все рты сразу:
    разница с кадром сна у happy, sad и dirty около рта.

    Слой один на питомца, а не на настроение: под маской в нём только кожа
    и рот, а они у настроений общие. Центр рта печатается — это точка, куда
    летит еда, и центр растяжения рта (mouthX, mouthY в PetSkin).
    """
    full = {state: load(pet, state) for state in STATES}
    sleep = pixels(full["sleep"])
    changed = np.abs(pixels(full["happy"]) - sleep).max(axis=2) > 0
    parts, count = ndimage.label(ndimage.binary_dilation(changed, iterations=12))
    sizes = ndimage.sum(changed, parts, range(1, count + 1))
    near_mouth = parts == np.argmin(sizes) + 1

    mouths = np.zeros_like(changed)
    for state in ("happy", "sad", "dirty"):
        mouths |= np.abs(pixels(full[state]) - sleep).max(axis=2) > 0
    mouths &= near_mouth

    ys, xs = np.nonzero(mouths & changed)
    side = full["sleep"].size[0]
    print(f"{pet}: рот в ({(xs.min() + xs.max()) / 2 / side:.4f}, {(ys.min() + ys.max()) / 2 / side:.4f})")

    to_mouth = ndimage.distance_transform_edt(~shrink_mask(mouths))
    mask = np.clip((MOUTH_CORE + MOUTH_FEATHER - to_mouth) / MOUTH_FEATHER, 0.0, 1.0)
    layer = pixels(shrink(full["sleep"]))
    layer[..., 3] = np.rint(layer[..., 3] * mask)
    save(Image.fromarray(layer.astype(np.uint8), "RGBA"), pet, "mouth")


def pixels(image: Image.Image) -> np.ndarray:
    return np.asarray(image).astype(int)


def shrink(image: Image.Image) -> Image.Image:
    """Уменьшение ровно как в [save] — чтобы слой моргания совпал с базовым до бита."""
    return image.resize((SIDE, SIDE), Image.LANCZOS) if max(image.size) > SIDE else image


def shrink_mask(mask: np.ndarray) -> np.ndarray:
    """Маска в размер слоя: пиксель попадает, если задет хоть одним пикселем холста."""
    image = Image.fromarray(mask.astype(np.uint8) * 255)
    return np.asarray(image.resize((SIDE, SIDE), Image.BOX)) > 0


def chosen(names: list[str]) -> list[str]:
    """Питомцы из командной строки; без имён — все."""
    asked = [a for a in names if not a.startswith("--")]
    unknown = [a for a in asked if a not in PETS]
    if unknown:
        sys.exit(f"Нет таких питомцев: {', '.join(unknown)}. Есть: {', '.join(PETS)}")
    return asked or list(PETS)


def build(pet: str) -> None:
    print(f"— {pet}")
    split(pet, PETS[pet])
    blink(pet)
    mouth(pet)


def main() -> None:
    RES.mkdir(parents=True, exist_ok=True)
    with ProcessPoolExecutor(max_workers=WORKERS) as pool:
        list(pool.map(build, chosen(sys.argv[1:])))


if __name__ == "__main__":
    main()
