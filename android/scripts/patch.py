#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Apply the ObsiLauncher branding/patches on top of a pinned ZalithLauncher2 checkout.

Every textual patch is *anchored*: if the upstream text changes and an anchor is
no longer found, the script fails loudly instead of silently producing a wrong build.

Usage: patch.py <path-to-upstream-checkout>
"""
import pathlib
import shutil
import sys

if len(sys.argv) != 2:
    sys.exit(__doc__)

ROOT = pathlib.Path(sys.argv[1]).resolve()
OVERLAY = pathlib.Path(__file__).resolve().parent.parent / "overlay"
APP = ROOT / "ZalithLauncher"
JAVA = APP / "src/main/java/com/movtery/zalithlauncher"

APPLICATION_ID = "studio.obsifox.obsilauncher"
KEY_ALIAS = "obsilauncher"
REPO_URL = "https://github.com/obsifox/ObsiLauncher-Source"


def sub(path: pathlib.Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        sys.exit(f"[patch] ANCHOR NOT FOUND in {path.relative_to(ROOT)}:\n    {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"[patch] {path.relative_to(ROOT)}  ok")


# 1) Gradle: own application id (no clash with the official app), own signing alias --------------
gradle = APP / "build.gradle.kts"
sub(gradle, "applicationId = zalithPackageName", f'applicationId = "{APPLICATION_ID}"')
sub(gradle, '        applicationIdSuffix = ".v2"\n', "")
sub(gradle, 'keyAlias = "movtery_zalith"', f'keyAlias = "{KEY_ALIAS}"')

# 2) Home screen: GPL-3.0 section 7 notice ("Unofficial Modified Version") ----------------------
sub(
    JAVA / "ui/screens/content/home/HomeCards.kt",
    "    fun systemCards(): List<SystemCard> = buildList {\n",
    "    fun systemCards(): List<SystemCard> = buildList {\n        add(ObsiNoticeCard.create())\n",
)

# 3) Never offer the *official* ZalithLauncher builds as an "update" for this fork ---------------
sub(
    JAVA / "path/UrlManager.kt",
    'const val URL_PROJECT_INFO: String = "https://api.github.com/repos/ZalithLauncher/Zalith-Info/contents/v2"',
    f'const val URL_PROJECT_INFO: String = "https://api.github.com/repos/obsifox/ObsiLauncher-Source/contents/v2"',
)
sub(
    JAVA / "viewmodel/LauncherUpgradeViewModel.kt",
    'private const val LATEST_API_CHINESE_URL = "https://repo.miawa.cn/zalith-info/v2/$LATEST_VERSION"',
    'private const val LATEST_API_CHINESE_URL = "https://invalid.obsifox.test/v2/$LATEST_VERSION"',
)

# 4) Overlay: new files / replaced resources (icons, strings, notice card) ----------------------
copied = 0
for src in sorted(OVERLAY.rglob("*")):
    if src.is_file():
        dst = ROOT / src.relative_to(OVERLAY)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        copied += 1
print(f"[patch] overlay files copied: {copied}")
print("[patch] done")
