# Setup: secrets & signing

Everything here is **optional except the Android signing key**, which is already configured for this repository.
Never commit secrets. Set them under **Settings → Secrets and variables → Actions → New repository secret**, or with the GitHub CLI:

```bash
gh secret set <NAME> --repo obsifox/ObsiLauncher-Source        # paste the value, press Enter, Ctrl-D
```

| Secret | Used by | Required? | What it does |
|---|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | Android | yes (set) | The release keystore, base64-encoded. Keeps APK signatures stable so updates install over each other. |
| `ANDROID_KEYSTORE_PASSWORD` | Android | yes (set) | Store **and** key password of that keystore (alias `obsilauncher`). |

If the two `ANDROID_KEYSTORE_*` secrets are missing the workflow signs with a throw-away key and prints a warning: such APKs work, but a later build can't be installed over them.

---

## 1. Android signing key

Already generated and uploaded. Facts worth knowing:

* PKCS12 keystore, alias `obsilauncher`, RSA-4096, valid for ~100 years, subject `CN=ObsiLauncher, O=ObsiFox Studio, C=PL`.
* **Back it up.** If you lose the keystore you can never publish an update that installs over existing installs. GitHub secrets cannot be read back.
* To rotate / recreate:
  ```bash
  keytool -genkeypair -keystore obsilauncher-release.jks -storetype PKCS12 -alias obsilauncher \
          -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=ObsiLauncher, O=ObsiFox Studio, C=PL"
  base64 -w0 obsilauncher-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo obsifox/ObsiLauncher-Source
  printf '%s' "$PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo obsifox/ObsiLauncher-Source
  ```
  (use the same password for the store and the key).

## 2. Running the workflows

Actions → pick **Android** or **Desktop** → *Run workflow*. Nothing runs automatically on push, so builds only happen when you ask for them (a private repo has 2000 free minutes/month and Windows counts double; a public repo's minutes are free).
Outputs land in the rolling **nightly** release (and as 3-day workflow artifacts). Pushing a tag `v0.2.0` builds everything and attaches it to a release called `v0.2.0`.

## 3. Local development

```bash
# desktop app (JDK 21)
./gradlew :desktop:run
# headless CLI against a scratch directory
OBSI_HOME=/tmp/obsi ./gradlew -q :core:printClasspath > /tmp/cp && java -cp "$(cat /tmp/cp)" studio.obsifox.launcher.core.cli.CliMainKt versions
# tests: offline by default, real servers with -Pnet=true
./gradlew :core:test
./gradlew :core:test -Pnet=true --tests '*NetworkIntegrationTest*'
```

Data lives in `%APPDATA%\ObsiLauncher` (Windows) or `~/.local/share/ObsiLauncher` (Linux); override with `OBSI_HOME`.

---

## راهنمای فارسی (خلاصه)

* همه‌ی Secretها اختیاری‌اند، به‌جز کلید امضای اندروید که **قبلاً ساخته و تنظیم شده**.
* حساب‌ها **فقط محلی/آفلاین** هستند؛ ورود مایکروسافت و CurseForge از محصول حذف شده‌اند و هیچ Secret مربوطی لازم نیست.
* فایل `obsilauncher-release.jks` و رمزش را **حتماً جای امن نگه‌دار**؛ بدون آن نمی‌توانی نسخه‌ی جدیدی بدهی که روی نسخه‌ی قبلی نصب شود.
* بیلدها فقط دستی (Run workflow) یا با ساخت تگ `v*` اجرا می‌شوند تا دقیقه‌های رایگان GitHub هدر نرود.
