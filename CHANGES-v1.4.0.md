# Changes in 1.4.0 — Modrinth, all loaders, dependency engine

Everything below is still ObsiLauncher's **own from-scratch code** (GPL-3.0-or-later).
Free as always: no access key, no locked features.

## Android

### Modrinth is built in

- **Mods · Modpacks · Resource packs · Shader packs** — full Modrinth API v2 client:
  search, project pages, version lists filtered by the instance's game version and
  loader, one-tap install with progress.
- **Dependency engine** — before a mod is installed, ObsiLauncher resolves its
  Modrinth dependencies recursively and shows a confirmation dialog:
  required dependencies are **locked** (checked and untouchable), optional ones are
  **tickable**, already-installed ones are skipped, and every entry shows who pulled it in.
- **Installed-content manager** (instance → Manage → Content) — per-instance list of
  mods / resource packs / shaders with enable/disable switches, update checks, and
  **dependency-aware deletion**: files that other installed content depends on cannot
  be deleted (the delete button is grayed out and tapping it shows *required by …*).
- **Modpacks** — `.mrpack` import: vanilla base + loader + every indexed file is
  installed and the new instance is created automatically.

### All versions, all loaders

- Vanilla: releases, snapshots, old-beta, old-alpha (full Mojang manifest).
- **Fabric, Forge, NeoForge, Quilt** — meta/maven version lists; Fabric and Quilt
  install from their meta profiles, Forge and NeoForge run their official installer
  headlessly on the runtime pack's JVM.
- **OptiFine** — downloads the official jar and patches the client with
  `optifine.Patcher`, then generates the version profile.

### Accounts & cosmetics

- **Microsoft login** (free device-code flow, in-app) alongside offline profiles —
  tokens refresh automatically before launch.
- **Custom skin & cape, local-only**: pick a PNG per account, wide or slim model.
  Applied client-side through CustomSkinLoader's LocalSkin provider, so **only you**
  see the skin — other players keep seeing the default one.

### Instances & settings

- **Named instances** — create one per version/loader, custom names, per-instance
  memory and JVM argument overrides; 1.3.0's selected version migrates automatically.
- **Fix & repair** — verifies the client jar, every library and every asset against
  published checksums and re-downloads anything missing or corrupt.
- **Language** — English / فارسی in-app, plus the runtime pack manager, Obsi Dynamic
  theming, version wallpapers and the trailer background from 1.3.0.

## Desktop

Unchanged; the shared core already had Modrinth + loaders.

## Signing note

The release keystore file was regenerated (same alias and passwords, documented in
`android/KEYSTORE.md`) after the original build machine was lost, and is now **shipped
in the repository** as intended. Installing 1.4.0 over 1.2.0/1.3.0 needs a one-time
uninstall/reinstall; afterwards updates install in place again.
