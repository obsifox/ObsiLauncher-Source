# ObsiLauncher release signing key (FREE launcher, public key)

The launcher is free software (GPL-3.0-or-later) and has **no access key / licence gate**.
Its release keystore is public on purpose, so anyone can reproduce byte-comparable
signed builds.

| Item | Value |
|---|---|
| Keystore | `android/app/obsilauncher.jks` |
| Alias | `obsilauncher` |
| Store password | `ObsiFox#1.2.0-free-launcher` |
| Key password | `ObsiFox#1.2.0-free-launcher` |
| Validity | 10950 days (30 years), RSA 4096, CN=ObsiLauncher |

`android/app/build.gradle.kts` signs with this keystore
(`storeFile = file("obsilauncher.jks")`, `keyAlias = "obsilauncher"`).

> APK updates install only over builds signed with this same key. Never regenerate
> it casually — users would have to uninstall/reinstall.
