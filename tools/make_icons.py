#!/usr/bin/env python3
"""
Generates every ObsiLauncher icon from ONE geometric fox definition:
  * Android adaptive icon (vector foreground, monochrome, background colour)
  * Desktop PNG / ICO icons, README logo

Run:  python3 tools/make_icons.py        (needs Pillow: pip install pillow)
"""
import pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent

ORANGE = "#FF8A2B"
ORANGE_DARK = "#D9601A"
CREAM = "#FFE9D0"
DARK = "#20123A"
BG_ANDROID = "#1B1230"

# Geometry in a 108x108 box (Android adaptive-icon canvas). Symmetric around x = 54.
SILHOUETTE = [(26, 24), (48, 40), (60, 40), (82, 24), (78, 56), (70, 76), (54, 86), (38, 76), (30, 56)]
INNER_EAR_L = [(31, 32), (42, 41), (33, 50)]
INNER_EAR_R = [(77, 32), (66, 41), (75, 50)]
MUZZLE = [(37, 68), (54, 58), (71, 68), (54, 86)]
CHEEK_L = [(30, 56), (44, 62), (37, 68), (38, 76)]
CHEEK_R = [(78, 56), (64, 62), (71, 68), (70, 76)]
EYE_L = [(40, 58), (49, 62), (47, 66), (40, 63)]
EYE_R = [(68, 58), (59, 62), (61, 66), (68, 63)]
NOSE = [(50, 80), (58, 80), (54, 86)]

LAYERS = [  # (polygon, colour) painted in order
    (SILHOUETTE, ORANGE),
    (INNER_EAR_L, DARK), (INNER_EAR_R, DARK),
    (CHEEK_L, ORANGE_DARK), (CHEEK_R, ORANGE_DARK),
    (MUZZLE, CREAM),
    (EYE_L, DARK), (EYE_R, DARK),
    (NOSE, DARK),
]


def path_data(poly):
    pts = [f"{x},{y}" for x, y in poly]
    return "M" + " L".join(pts) + " Z"


def write(path: pathlib.Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


def android():
    res = ROOT / "android/overlay/ZalithLauncher/src/main/res"
    paths = "\n".join(
        f'    <path\n        android:fillColor="{col}"\n        android:pathData="{path_data(p)}" />'
        for p, col in LAYERS
    )
    write(res / "drawable/ic_launcher_foreground.xml",
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp"\n    android:height="108dp"\n'
          '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
          f"{paths}\n</vector>\n")
    # monochrome: silhouette with the eyes / nose punched out (even-odd), tinted by the launcher
    mono = " ".join([path_data(SILHOUETTE), path_data(EYE_L), path_data(EYE_R), path_data(NOSE)])
    write(res / "drawable/ic_launcher_monochrome.xml",
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp"\n    android:height="108dp"\n'
          '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
          '    <path\n        android:fillColor="#FFFFFFFF"\n        android:fillType="evenOdd"\n'
          f'        android:pathData="{mono}" />\n</vector>\n')
    write(res / "values/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
          f'    <color name="ic_launcher_background">{BG_ANDROID}</color>\n</resources>\n')


def render(size: int, rounded: bool = True, fox_scale: float = 1.3) -> Image.Image:
    """Desktop icon: gradient obsidian tile + fox."""
    ss = 4
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    # vertical gradient tile
    tile = Image.new("RGBA", (s, s))
    top, bot = (0x2A, 0x1A, 0x4D), (0x12, 0x0B, 0x22)
    px = tile.load()
    for y in range(s):
        t = y / (s - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(s):
            px[x, y] = c
    mask = Image.new("L", (s, s), 0)
    r = int(s * 0.22) if rounded else 0
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=r, fill=255)
    img.paste(tile, (0, 0), mask)
    d = ImageDraw.Draw(img)
    k = s / 108 * fox_scale
    cx, cy = s / 2, s / 2 + s * 0.015
    for poly, col in LAYERS:
        d.polygon([((x - 54) * k + cx, (y - 55) * k + cy) for x, y in poly], fill=col)
    return img.resize((size, size), Image.LANCZOS)


def desktop():
    big = render(512)
    out = ROOT / "desktop/src/main/resources"
    out.mkdir(parents=True, exist_ok=True)
    big.save(out / "icon.png")
    pk = ROOT / "desktop/packaging"
    pk.mkdir(parents=True, exist_ok=True)
    big.save(pk / "icon.png")
    big.save(pk / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    docs = ROOT / "docs/assets"
    docs.mkdir(parents=True, exist_ok=True)
    big.save(docs / "logo.png")
    print("wrote desktop icons + docs/assets/logo.png")


if __name__ == "__main__":
    android()
    desktop()
