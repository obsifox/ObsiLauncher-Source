# ObsiLauncher for Android

This folder does **not** contain the Android app's source. It contains what is needed to produce it from a pinned upstream:

| File | Purpose |
|---|---|
| `upstream.version` | Upstream repo, release tag and commit that CI fetches. |
| `scripts/prepare.sh` | Shallow-fetches that commit into `android/.work/upstream` and runs `patch.py`. |
| `scripts/patch.py` | Anchored text replacements (fails loudly if upstream changed) + copies `overlay/`. |
| `overlay/` | Files that are added/overwritten: the *Unofficial Modified Version* card (EN/FA), icons, string resources. |

What the overlay changes: application id `studio.obsifox.obsilauncher`, name "Obsi Launcher", fox icons, a permanent
*unofficial* card on the home screen (required by upstream's additional license terms), and update-check URLs that no longer
point at upstream. Kotlin packages and the Android `namespace` are left untouched on purpose (the native JNI symbols depend on them).

Build (CI does exactly this): see `.github/workflows/android.yml`. Locally you need JDK 21, Android SDK 37 + NDK 25.2.9519653 and about 6 GB of disk:

```bash
bash android/scripts/prepare.sh
cd android/.work/upstream && ./gradlew ZalithLauncher:assembleRelease -Darch=arm64 -Plauncher_name=ObsiLauncher
```

Licensing: everything here is GPL-3.0-or-later (see `/NOTICE.md`). Updating upstream: change `upstream.version`, run the workflow, fix any anchor `patch.py` reports.

Known gaps: only the notice card is translated to Persian – a complete `values-fa` for the whole app is future work.
