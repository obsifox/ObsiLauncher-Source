# Setup: secrets, Microsoft sign-in, signing

Everything here is **optional except the Android signing key**, which is already configured for this repository.
Never commit secrets. Set them under **Settings → Secrets and variables → Actions → New repository secret**, or with the GitHub CLI:

```bash
gh secret set MS_CLIENT_ID --repo obsifox/ObsiLauncher-Source        # paste the value, press Enter, Ctrl-D
```

| Secret | Used by | Required? | What it does |
|---|---|---|---|
| `MS_CLIENT_ID` | Android + Desktop | no | Azure application ID that enables **Sign in with Microsoft**. Without it only offline accounts work. |
| `CURSEFORGE_API_KEY` | Android | no | Enables CurseForge browsing in the Android app (the Modrinth integration needs no key). |
| `ANDROID_KEYSTORE_BASE64` | Android | yes (set) | The release keystore, base64-encoded. Keeps APK signatures stable so updates install over each other. |
| `ANDROID_KEYSTORE_PASSWORD` | Android | yes (set) | Store **and** key password of that keystore (alias `obsilauncher`). |

If the two `ANDROID_KEYSTORE_*` secrets are missing the workflow signs with a throw-away key and prints a warning: such APKs work, but a later build can't be installed over them.

---

## 1. Microsoft sign-in (`MS_CLIENT_ID`)

Mojang only lets *approved* applications talk to the Minecraft services, so every launcher needs its own Azure app registration. It is free:

1. Open <https://portal.azure.com> → **Microsoft Entra ID → App registrations → New registration**.
2. **Name:** `ObsiLauncher` (any name; users see it on the consent screen).
   **Supported account types:** *Personal Microsoft accounts only* (or "any directory + personal accounts").
   **Redirect URI:** platform *Public client / native* → `https://login.microsoftonline.com/common/oauth2/nativeclient`.
3. Open the new app → **Authentication** → enable **Allow public client flows** → *Yes* → Save. (The launcher uses the *device code* flow, which needs this.)
4. Copy the **Application (client) ID** from the Overview page.
5. **Ask Mojang to approve the app:** fill in the form linked from <https://aka.ms/AppRegInfo> ("Minecraft API – app registration review"). Approval is manual and can take days.
   Until it is approved, signing in ends with *"Mojang has not approved this application's client id"* (HTTP 403 from the Minecraft services). That is expected, not a bug.
6. Store the ID: `gh secret set MS_CLIENT_ID …` and re-run the **Android** / **Desktop** workflows.
   For a quick local test you can also paste the ID in the desktop app: **Settings → Microsoft application ID**.

The ID is *not* a secret (it ships inside the app); it is stored as a secret only to keep it out of the source tree.

## 2. Android signing key

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

## 3. Running the workflows

Actions → pick **Android** or **Desktop** → *Run workflow*. Nothing runs automatically on push, so builds only happen when you ask for them (a private repo has 2000 free minutes/month and Windows counts double; a public repo's minutes are free).
Outputs land in the rolling **nightly** release (and as 3-day workflow artifacts). Pushing a tag `v0.2.0` builds everything and attaches it to a release called `v0.2.0`.

## 4. Local development

```bash
# desktop app (JDK 21)
MS_CLIENT_ID=<your id> ./gradlew :desktop:run
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
* **ورود با مایکروسافت** فقط وقتی کار می‌کند که یک Application ID از Azure داشته باشی و Mojang آن را تأیید کرده باشد (فرم: `aka.ms/AppRegInfo`). مراحل: ساخت App registration با نوع «Personal Microsoft accounts»، فعال‌کردن **Allow public client flows**، کپی‌کردن Client ID، و ذخیره‌اش به‌عنوان Secret با نام `MS_CLIENT_ID`. تا زمان تأیید Mojang، خطای ۴۰۳ می‌گیری که طبیعی است. حساب **آفلاین** بدون این‌ها هم کار می‌کند.
* فایل `obsilauncher-release.jks` و رمزش را **حتماً جای امن نگه‌دار**؛ بدون آن نمی‌توانی نسخه‌ی جدیدی بدهی که روی نسخه‌ی قبلی نصب شود.
* بیلدها فقط دستی (Run workflow) یا با ساخت تگ `v*` اجرا می‌شوند تا دقیقه‌های رایگان GitHub هدر نرود.
