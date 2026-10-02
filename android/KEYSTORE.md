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
>
> **Note (1.4.0):** the keystore file was regenerated with the *same* alias and
> passwords after the original build machine was lost. Builds from 1.4.0 onward
> are signed with the key shipped in this repository
> (`android/app/obsilauncher.jks`, committed on purpose). Installing 1.4.0 over
> 1.2.0/1.3.0 requires a one-time uninstall/reinstall — after that, updates
> install in place again.
