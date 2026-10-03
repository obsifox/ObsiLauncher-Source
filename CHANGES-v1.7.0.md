# Changes in v1.7.0

## Update gate (new boot screen)
- Every launch now opens a boot/update-gate screen first (matches the official
  launcher boot flow): centered wordmark, live status ("Checking updates…" /
  "You are up to date" / "Installing runtime…"), progress bar, "Check again"
  and a bottom-right "Continue to launcher" button.
- It automatically checks GitHub for a newer launcher release. If one exists it
  offers a one-tap download + opens the system installer (REQUEST_INSTALL_PACKAGES
  + FileProvider added).
- It then checks the runtime requirements (runtime pack / JVM) and — when a
  default pack is published in the repo's `runtime.json` — installs it
  automatically without asking. Offline or unreachable networks never block
  the launcher; the gate just reports and lets you continue.

## Version screen: card grid with artwork
- Versions now show as a 3-column card grid, every card carrying the artwork
  of that version's era (cherry 1.20+, snow 1.18+, wilderness 1.13+, classic
  hills for older). Installed tab uses the same cards with a "Selected" badge.
- Tapping a card opens a compact loader-icon picker: one row of drawn loader
  icons (Vanilla grass block, Fabric feather, Forge/NeoForge anvils, Quilt
  patches, OptiFine glasses) — each loader name sits in an orange box with
  white text.
- Loader version numbers are orange chips with white text as well.

## Mods screen: cards + draggable search
- The tab strip and the big search bar are gone. Four content cards now open
  the category directly: Mods / Modpacks / Resource packs / Shader packs.
- A draggable magnifier button floats over the screen (move it anywhere);
  tapping it opens a popup asking: type → game version (optional) → name
  (optional) — searching lists top content when fields are left empty.
- Modrinth calls now send a proper Accept header, errors show with a Retry
  button, and the last search is re-runnable.

## Fixes
- Accounts screen crash fixed (LazyColumn nested inside the scrollable column
  crashed on open).
- The background now ALWAYS shows something: if minecraft.net wallpaper packs
  cannot be reached, bundled era artwork is used instead of an empty screen —
  video/wallpaper behavior unchanged otherwise.
- PLAY button is clearly gray and disabled while no version is installed.

## Housekeeping
- versionCode 10700 / versionName 1.7.0; full EN + FA strings; `runtime.json`
  manifest added to the repo root (documents zero-touch runtime provisioning).
