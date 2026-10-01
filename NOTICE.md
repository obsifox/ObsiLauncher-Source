# Licensing & third-party notices

This repository mixes code under different terms. **Read this before distributing any build.**

## What is under which license

| Path | License | Notes |
|---|---|---|
| `core/`, `desktop/`, `tools/`, `docs/`, `.github/` | © ObsiFox Studio, **all rights reserved** until the owner picks a license | Written from scratch for this project. Add a `LICENSE` file when you decide (MIT / Apache-2.0 / GPL …). |
| `android/` (overlay + scripts) | **GPL-3.0-or-later**, with the upstream *Additional Terms* | These files modify ZalithLauncher2, so they inherit its license. |
| *Built Android APK* | **GPL-3.0** (a derivative of ZalithLauncher2) | If you give the APK to anyone, you must give them the corresponding source: the pinned upstream commit in `android/upstream.version` **plus** the `android/` overlay in this repository. |
| `desktop/src/main/resources/fonts/Vazirmatn-*.ttf` | SIL OFL 1.1 (`OFL.txt` next to the fonts) | © The Vazirmatn Project Authors. |

The desktop launcher does **not** contain ZalithLauncher2 code, so GPL does not extend to `core/` or `desktop/`.

## ZalithLauncher2 (Android base)

Upstream: <https://github.com/ZalithLauncher/ZalithLauncher2> · pinned release `2.6.1` (see `android/upstream.version`).
Licensed GPL-3.0 with *Additional Terms* under GPLv3 §7. What we do to comply:

* the product is renamed (**ObsiLauncher**) – it never uses "ZalithLauncher" / "ZL" as its name;
* a permanent card **"Unofficial Modified Version"** is shown on the home screen (`ObsiNoticeCard`), in English and Persian;
* upstream copyright notices that the app displays are kept untouched;
* the update checker is pointed away from upstream's servers, so users are not told to "update" to the original app.

Upstream bundles further third-party components (JREs, renderers such as Mesa/ANGLE/gl4es, LWJGL, …) with their own licenses; they are listed in the upstream repository and in the app's own *About* screen.

## Desktop dependencies

Kotlin & kotlinx.coroutines/serialization (Apache-2.0) · Compose Multiplatform, Skiko, Material 3 (Apache-2.0) · Vazirmatn (OFL-1.1).
Packaged apps embed an OpenJDK runtime (GPLv2 + Classpath Exception).

## Trademarks & services

*Minecraft* is a trademark of Mojang AB / Microsoft. ObsiLauncher is not approved by or associated with them.
The launcher downloads game files from Mojang's public servers, mod loaders from their official meta/Maven servers and content from the public **Modrinth API**
(<https://docs.modrinth.com>; a descriptive `User-Agent` is sent as their terms require). Respect each service's terms; modpack/mod authors keep their own licenses.
