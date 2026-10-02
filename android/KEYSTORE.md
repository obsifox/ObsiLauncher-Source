# ObsiLauncher release signing key (FREE launcher, public key)

The launcher is free software (GPL-3.0-or-later) and has **no access key / licence gate**.
Its release keystore is public on purpose, exactly like upstream ZalithLauncher2 publishes
`zalith_launcher.jks`, so anyone can reproduce byte-comparable signed builds.

| Item | Value |
|---|---|
| Keystore | `android/overlay/ZalithLauncher/obsilauncher.jks` (copied to the app module by `scripts/patch.py`) |
| Alias | `obsilauncher` |
| Store password | `ObsiFox#1.2.0-free-launcher` |
| Key password | `ObsiFox#1.2.0-free-launcher` |
| Validity | 10950 days (30 years), RSA 4096, CN=ObsiLauncher |

`patch.py` rewrites `ZalithLauncher/build.gradle.kts` to use this keystore
(`storeFile = file("obsilauncher.jks")`, `keyAlias = "obsilauncher"`).
The build also accepts `STORE_PASSWORD` / `KEY_PASSWORD` env vars or
`.store_password.txt` / `.key_password.txt` files at the repository root.

> APK updates install only over builds signed with this same key. Never regenerate
> it casually — users would have to uninstall/reinstall.
