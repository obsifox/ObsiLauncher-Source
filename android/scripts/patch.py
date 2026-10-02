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

# ObsiLauncher release identity. The launcher is FREE — no access key, no licence gate.
VERSION_NAME = "1.2.0"
VERSION_CODE = "10200"          # major*10000 + minor*100 + patch
STORE_FILE = "obsilauncher.jks"  # public release keystore shipped in android/overlay


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
# Sign with the ObsiLauncher keystore (upstream's zalith_launcher.jks is not ours,
# and its release passwords are upstream secrets — a fork must ship its own key).
sub(gradle, 'storeFile = file("zalith_launcher.jks")', f'storeFile = file("{STORE_FILE}")')

# 1b) Module gradle.properties: branding + FREE release version 1.2.0 ------------------------------
gprops = APP / "gradle.properties"
sub(gprops, "launcher_name=ZalithLauncher", "launcher_name=ObsiLauncher")
sub(gprops, "launcher_app_name=Zalith Launcher", "launcher_app_name=Obsi Launcher")
sub(gprops, "launcher_short_name=ZL2", "launcher_short_name=ObsiLauncher")
sub(gprops, "url_home=https://github.com/ZalithLauncher/ZalithLauncher2", f"url_home={REPO_URL}")
sub(gprops, "launcher_version_code=200043", f"launcher_version_code={VERSION_CODE}")
sub(gprops, "launcher_version_name=2.6.1", f"launcher_version_name={VERSION_NAME}")

# 2) Home screen: GPL-3.0 section 7 notice ("Unofficial Modified Version") ------------------------
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

# 4) Theme picker: register the Obsidian Fox palette label --------------------------------------
strings_xml = APP / "src/main/res/values/strings.xml"
sub(
    strings_xml,
    '<string name="theme_color_verdant_dawn">Silent Forest Dawn</string>',
    '<string name="theme_color_verdant_dawn">Silent Forest Dawn</string>\n    <string name="theme_color_obsidian_fox">Obsidian Fox</string>',
)

# 5) Factory defaults: glass-dark Obsidian Fox design, blur on, no sponsor dialog -----------------
#    (defaults only apply to fresh installs; users can change everything afterwards)
sub(
    JAVA / "setting/AllSettings.kt",
    '''    val launcherColorTheme = enumSetting(
        "launcherColorTheme",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorThemeType.DYNAMIC
        else ColorThemeType.EMBERMIRE
    )''',
    '''    val launcherColorTheme = enumSetting(
        "launcherColorTheme",
        // ObsiLauncher: glass-dark Obsidian Fox out of the box; OBSI_DYNAMIC takes
        // its colours from the active version wallpaper (see obsi/ObsiWallpaper.kt).
        ColorThemeType.OBSI_DYNAMIC
    )''',
)
sub(
    JAVA / "setting/AllSettings.kt",
    'val launcherDarkMode = enumSetting("launcherDarkMode", DarkMode.FollowSystem)',
    'val launcherDarkMode = enumSetting("launcherDarkMode", DarkMode.Enable) // ObsiLauncher: glass-dark by default',
)
sub(
    JAVA / "setting/AllSettings.kt",
    'val launcherBackgroundOpacity = intSetting("launcherBackgroundOpacity", 80, 20..100)',
    'val launcherBackgroundOpacity = intSetting("launcherBackgroundOpacity", 55, 20..100) // ObsiLauncher: let the wallpaper breathe',
)
sub(
    JAVA / "setting/AllSettings.kt",
    'val backgroundBlur = intSetting("backgroundBlur", 0, 0..40)',
    'val backgroundBlur = intSetting("backgroundBlur", 14, 0..40) // ObsiLauncher: frosted glass by default',
)
sub(
    JAVA / "setting/AllSettings.kt",
    'val showSponsorship = boolSetting("showSponsorship", true)',
    'val showSponsorship = boolSetting("showSponsorship", false) // ObsiLauncher: no sponsor dialogs',
)

# 6) BackgroundViewModel: public refresh hook for the version wallpaper sync ----------------------
sub(
    JAVA / "viewmodel/BackgroundViewModel.kt",
    "    suspend fun delete() {",
    '''    /**
     * ObsiLauncher: re-scan the background file after the version wallpaper sync
     * replaced it on disk (called by com.movtery.zalithlauncher.obsi.ObsiWallpaper).
     */
    fun reload() {
        viewModelScope.launch(Dispatchers.Main) { updateState() }
    }

    suspend fun delete() {''',
)

# 7) MainActivity: keep wallpaper + dynamic accent in sync with the selected version --------------
sub(
    JAVA / "ui/activities/MainActivity.kt",
    "import androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "import androidx.lifecycle.compose.collectAsStateWithLifecycle\n"
    "import com.movtery.zalithlauncher.obsi.ObsiWallpaper",
)
sub(
    JAVA / "ui/activities/MainActivity.kt",
    '''                    Background(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = backgroundViewModel
                    )''',
    '''                    // ObsiLauncher: apply the version-matching wallpaper (Wilderness
                    // Bound / Minecraft PC bundle / latest-release trailer) and refresh
                    // the dynamic accent whenever the selected version changes.
                    val obsiVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
                    LaunchedEffect(obsiVersion) {
                        ObsiWallpaper.autoSync(this@MainActivity, obsiVersion, backgroundViewModel)
                    }

                    Background(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = backgroundViewModel
                    )''',
)
# LaunchedEffect needs to be resolvable in MainActivity's scope
sub(
    JAVA / "ui/activities/MainActivity.kt",
    "import androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "import androidx.compose.runtime.LaunchedEffect\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle",
)

# 8) All "project" links point to the ObsiLauncher repository -------------------------------------
sub(
    JAVA / "path/UrlManager.kt",
    'const val URL_PROJECT: String = "https://github.com/ZalithLauncher/ZalithLauncher2"',
    f'const val URL_PROJECT: String = "{REPO_URL}"',
)

# 9) Overlay: new files / replaced resources (icons, strings, notice card, theme, about, obsi/*) --
copied = 0
for src in sorted(OVERLAY.rglob("*")):
    if src.is_file():
        dst = ROOT / src.relative_to(OVERLAY)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        copied += 1
print(f"[patch] overlay files copied: {copied}")
print("[patch] done")
