#!/usr/bin/env python3
"""
Generates the ObsiLauncher mobile theme overlay ("Obsidian Fox" warm charcoal + fox orange)
by copying the pinned upstream theme files and applying exact, verified edits.

Outputs are written to android/overlay/ZalithLauncher/... (full-file replacements that the
existing overlay copy step applies). Every transformation fails loudly if its anchor is missing,
so a bumped upstream pin is detected immediately.
"""
import pathlib
import re
import shutil
import sys

import os
ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else os.environ.get("ZL2_CHECKOUT", "android/.work/upstream"))
REPO_OUT = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else (pathlib.Path(__file__).resolve().parents[1]))
UPSTREAM = ROOT / "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme"
UPSTREAM_SETTINGS = ROOT / "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/LauncherSettingsScreen.kt"
OUT = REPO_OUT / "android/overlay/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme"

# ---------------------------------------------------------------- obsidian fox palette
FOX_LIGHT = {
    "primary": "0xFFB4551E", "onPrimary": "0xFFFFFFFF", "primaryContainer": "0xFFFFDCC5", "onPrimaryContainer": "0xFF2F0700",
    "secondary": "0xFF7A5A48", "onSecondary": "0xFFFFFFFF", "secondaryContainer": "0xFFF3DFCE", "onSecondaryContainer": "0xFF301B0C",
    "tertiary": "0xFF665C2E", "onTertiary": "0xFFFFFFFF", "tertiaryContainer": "0xFFEFE0A8", "onTertiaryContainer": "0xFF1F1B00",
    "error": "0xFFBA1A1A", "onError": "0xFFFFFFFF", "errorContainer": "0xFFFFDAD6", "onErrorContainer": "0xFF410002",
    "background": "0xFFFFF8F3", "onBackground": "0xFF201B16", "surface": "0xFFFFF8F3", "onSurface": "0xFF201B16",
    "surfaceVariant": "0xFFF3DFD1", "onSurfaceVariant": "0xFF52443A", "outline": "0xFF857367", "outlineVariant": "0xFFD5C3B5",
    "scrim": "0xFF000000", "inverseSurface": "0xFF362F2A", "inverseOnSurface": "0xFFFBEEE7", "inversePrimary": "0xFFFFB68C",
    "surfaceDim": "0xFFE2D7CC", "surfaceBright": "0xFFFFF8F3", "surfaceContainerLowest": "0xFFFFFFFF",
    "surfaceContainerLow": "0xFFFBF1E8", "surfaceContainer": "0xFFF5EBE1", "surfaceContainerHigh": "0xFFEFE5DB",
    "surfaceContainerHighest": "0xFFE9E0D6",
}
FOX_DARK = {
    "primary": "0xFFFFB68C", "onPrimary": "0xFF542200", "primaryContainer": "0xFF7A3A12", "onPrimaryContainer": "0xFFFFDCC5",
    "secondary": "0xFFE4C2A8", "onSecondary": "0xFF422C1B", "secondaryContainer": "0xFF5A4230", "onSecondaryContainer": "0xFFFFDCC5",
    "tertiary": "0xFFD8C88E", "onTertiary": "0xFF39311A", "tertiaryContainer": "0xFF51472B", "onTertiaryContainer": "0xFFF5E4B2",
    "error": "0xFFFFB4AB", "onError": "0xFF690005", "errorContainer": "0xFF93000A", "onErrorContainer": "0xFFFFDAD6",
    "background": "0xFF18120D", "onBackground": "0xFFEFE1D7", "surface": "0xFF18120D", "onSurface": "0xFFEFE1D7",
    "surfaceVariant": "0xFF52443A", "onSurfaceVariant": "0xFFD5C3B5", "outline": "0xFF9D8D80", "outlineVariant": "0xFF52443A",
    "scrim": "0xFF000000", "inverseSurface": "0xFFEFE1D7", "inverseOnSurface": "0xFF362F2A", "inversePrimary": "0xFFB4551E",
    "surfaceDim": "0xFF18120D", "surfaceBright": "0xFF423830", "surfaceContainerLowest": "0xFF120D09",
    "surfaceContainerLow": "0xFF201A14", "surfaceContainer": "0xFF241E18", "surfaceContainerHigh": "0xFF2F2822",
    "surfaceContainerHighest": "0xFF3A332C",
}


def die(msg: str):
    sys.exit(f"[mobile-theme] {msg}")


def sub_exact(text: str, old: str, new: str, path: str) -> str:
    if text.count(old) != 1:
        die(f"anchor not unique in {path}: {old[:80]!r}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------- Color.kt
color_kt = (UPSTREAM / "Color.kt").read_text(encoding="utf-8")
pattern = re.compile(r"val (\w+?)(Light|Dark) = ColorTheme\((Color\(0x[0-9A-Fa-f]{8}\)(?:, Color\(0x[0-9A-Fa-f]{8}\))*)\)")

def fox_for(role: str, mode: str) -> str:
    table = FOX_LIGHT if mode == "Light" else FOX_DARK
    if role not in table:
        die(f"no fox colour for role {role}")
    return f"Color({table[role]})"

count = 0
def repl(m):
    global count
    role, mode, args = m.group(1), m.group(2), m.group(3)
    n_args = args.count("Color(")
    if n_args != 7:
        die(f"unexpected arg count {n_args} for {role}{mode}")
    count += 1
    return f"val {role}{mode} = ColorTheme({args}, {fox_for(role, mode)})"

color_kt_new = pattern.sub(repl, color_kt)
if count != 70:
    die(f"expected 70 ColorTheme constructors, found {count}")
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "Color.kt").write_text(color_kt_new, encoding="utf-8")
print(f"[mobile-theme] Color.kt: {count} constructors extended")

# ---------------------------------------------------------------- ColorTheme.kt
ct = (UPSTREAM / "ColorTheme.kt").read_text(encoding="utf-8")
ct = sub_exact(ct, "    VERDANT_DAWN,\n    CUSTOM", "    VERDANT_DAWN,\n    OBSIDIAN_FOX,\n    CUSTOM", "ColorTheme.kt enum")
ct = sub_exact(ct, "    val verdantDawn: Color\n)", "    val verdantDawn: Color,\n    val obsidianFox: Color\n)", "ColorTheme.kt data class")
(OUT / "ColorTheme.kt").write_text(ct, encoding="utf-8")
print("[mobile-theme] ColorTheme.kt: enum + field added")

# ---------------------------------------------------------------- Theme.kt
theme = (UPSTREAM / "Theme.kt").read_text(encoding="utf-8")

fox_light_scheme = "\n\n// Obsidian Fox (ObsiLauncher default): warm charcoal + fox orange\nprivate val obsidianFoxLight = lightColorScheme(" + ",\n".join(
    f"    {role} = {role}Light.obsidianFox" for role in FOX_LIGHT) + ",\n)\n"
fox_dark_scheme = "\n\nprivate val obsidianFoxDark = darkColorScheme(" + ",\n".join(
    f"    {role} = {role}Dark.obsidianFox" for role in FOX_DARK) + ",\n)\n"

anchor = "    surfaceContainerHighest = surfaceContainerHighestDark.verdantDawn,\n)"
theme = sub_exact(theme, anchor, anchor + fox_light_scheme + fox_dark_scheme, "Theme.kt schemes")

theme = sub_exact(theme, "                ColorThemeType.VERDANT_DAWN -> verdantDawnDark",
                  "                ColorThemeType.VERDANT_DAWN -> verdantDawnDark\n                ColorThemeType.OBSIDIAN_FOX -> obsidianFoxDark",
                  "Theme.kt dark dispatch")
theme = sub_exact(theme, "                ColorThemeType.VERDANT_DAWN -> verdantDawnLight",
                  "                ColorThemeType.VERDANT_DAWN -> verdantDawnLight\n                ColorThemeType.OBSIDIAN_FOX -> obsidianFoxLight",
                  "Theme.kt light dispatch")
(OUT / "Theme.kt").write_text(theme, encoding="utf-8")
print("[mobile-theme] Theme.kt: schemes + dispatch added")

# ---------------------------------------------------------------- NativeThemeUtils.kt
ntu = (UPSTREAM / "NativeThemeUtils.kt").read_text(encoding="utf-8")
anchor = "        ColorThemeType.VERDANT_DAWN -> if (darkTheme) 0xFF8ED88E.toInt() else 0xFF004814.toInt()"
ntu = sub_exact(ntu, anchor, anchor + "\n        ColorThemeType.OBSIDIAN_FOX -> if (darkTheme) 0xFFFFB68C.toInt() else 0xFFB4551E.toInt()",
                "NativeThemeUtils.kt seed colours")
OUT2 = OUT  # NativeThemeUtils.kt lives in the same ui/theme directory
(OUT2 / "NativeThemeUtils.kt").write_text(ntu, encoding="utf-8")
print("[mobile-theme] NativeThemeUtils.kt: seed colour added")

# ---------------------------------------------------------------- LauncherSettingsScreen.kt
lss = UPSTREAM_SETTINGS.read_text(encoding="utf-8")
anchor = "                                ColorThemeType.VERDANT_DAWN -> stringResource(R.string.theme_color_verdant_dawn)"
lss = sub_exact(lss, anchor, anchor + "\n                                ColorThemeType.OBSIDIAN_FOX -> stringResource(R.string.theme_color_obsidian_fox)",
                "LauncherSettingsScreen.kt theme label")
OUT3 = OUT.parent.parent / "screens" / "content" / "settings"
OUT3 = pathlib.Path(str(OUT).replace("ui/theme", "ui/screens/content/settings"))
OUT3.mkdir(parents=True, exist_ok=True)
(OUT3 / "LauncherSettingsScreen.kt").write_text(lss, encoding="utf-8")
print("[mobile-theme] LauncherSettingsScreen.kt: label added")

print("[mobile-theme] done — overlay files written")
