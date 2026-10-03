# ObsiLauncher — changelog v1.13.2

نسخهٔ `1.13.1 / 11301` → **`1.13.2 / 11302`** (`android/app/build.gradle.kts` + `VERSION`)

## ۱) `core/net/Http.kt` — بازنویسی شد

| مشکل در 1.13.1 | رفع |
|---|---|
| `get(): String?` هیچ‌وقت null نمی‌داد، `throw` می‌کرد → همهٔ `?: fallback`ها کد مرده بودند | `get(): String` (صریحاً throw) + `getOrNull(): String?` برای مسیرهای دارای fallback |
| 429 و «403 + X-RateLimit-Remaining: 0» مثل خطای معمولی ۳ بار دوباره زده می‌شد | کلاس `Http.RateLimited`، احترام به `Retry-After` و `X-RateLimit-Reset`، توقف زودهنگام اگر پنجرهٔ انتظار > ۱۰ ثانیه باشد |
| دانلود بدون resume (فایل `.part` ساخته می‌شد ولی هر retry از صفر) | درخواست `Range: bytes=N-`، نوشتن با `RandomAccessFile` روی `.part`، تشخیص `206`، ادامه از ۴۱۶ (`.part` کامل) و شروع مجدد فقط وقتی سرور Range را نادیده بگیرد |
| User-Agent بدون راه تماس (شرط Modrinth) | `ObsiLauncher/<version> (Android; +https://github.com/obsifox/ObsiLauncher-Source)` |
| `authedJson` هم throw می‌کرد | حالا واقعاً `null` برمی‌گرداند (فراخواننده‌ها null-safe هستند) |

تمام محل‌های فراخوانی اصلاح شد: `Modrinth.kt`, `VersionInstaller.kt`, `VersionManifest.kt`, `LoaderService.kt`, `ObsiWallpaper.kt`, `UpdateGate.kt`, `MicrosoftAuth.kt` — هر جا `?:` بود → `getOrNull`.

## ۲) `core/update/UpdateGate.kt` — بازنویسی شد

1. **آپدیت دیگر بی‌صدا شکست نمی‌خورد.** حالت جدید `Step.UpdateCheckFailed(reason)` + فیلد `lastError`.
2. **منبع اصلی بررسی نسخه بدون سهمیه:** `releases/latest/download/VERSION` (فایل استاتیک). REST API فقط fallback است.
3. **باگ قطعی رفع شد:** حلقهٔ fallback شامل `jre-21` بود که `downloadRuntime` نمی‌شناسد → کاربر بی‌خبر Internal-17 می‌گرفت. حالا فقط `jre-17`, `jre-25` امتحان می‌شوند (`DOWNLOADABLE_RUNTIME_IDS`).
4. **کد مردهٔ `resolveRuntimeUrls()` / `builtinRuntimeUrls()` حذف شد** (هر دو لینک pinnedش ۴۰۴ بودند). به‌جایش `manifestMirrors()` از `runtime.json` (نسخهٔ ۲) خوانده و **واقعاً** به نصاب پاس داده می‌شود.
5. **دانلود APK آپدیت** حالا resume دارد و اگر asset در API پیدا نشود، لینک قابل‌پیش‌بینی `releases/download/v<tag>/ObsiLauncher-<tag>-arm64-v8a.apk` استفاده می‌شود.
6. علت خطای باز نشدن رانتایم باندل‌شده از `ensureBundledRuntime()` گرفته و در `lastError` نمایش داده می‌شود.

## ۳) `core/runtime/ObsiComponents.kt`

- `ensureBundledRuntime()` حالا `Result<Unit>` برمی‌گرداند؛ قبل از استخراج **فضای آزاد** چک می‌شود (۲۶۰MB) و اگر `libjli/libjvm` بعد از استخراج نبود خطای واقعی می‌دهد — به‌جای `runCatching {}` خالیِ قبلی.
- منبع دانلود رانتایم **pin شد به کامیت** `fe5853b5bcd872c93e0dba558890a744aaaec9f2` از ZalithLauncher (قبلاً برنچ متحرک `main`) و **چند میرور** به ترتیب امتحان می‌شود:
  1. `raw.githubusercontent.com/...@<commit>`
  2. `cdn.jsdelivr.net/gh/...@<commit>`
  3. `gcore.jsdelivr.net/gh/...@<commit>`
  4. برنچ `main` به‌عنوان آخرین راه
  → در شبکه‌هایی که raw بلاک است دانلود رانتایم کار می‌کند.
- `downloadRuntime(context, id, mirrors)` — پارامتر سوم اختیاری (سازگار با `SettingsScreen` و `ObsiApp`)؛ هر part به‌ترتیب روی همهٔ میرورها امتحان می‌شود (اگر jsDelivr فایل بزرگ را 403 کرد، خودکار به raw برمی‌گردد) و برای `jre-21` پیام روشن می‌دهد.

## ۴) `ui/gate/UpdateGateScreen.kt` + رشته‌ها

- پشتیبانی از حالت جدید `UpdateCheckFailed` (نمایش ۱.۶ ثانیه، سپس ادامهٔ خودکار به مرحلهٔ رانتایم).
- در `RuntimeFailed` **علت واقعی** (`gate.lastError`) نمایش داده می‌شود نه متن عمومی.
- دکمهٔ CHECK AGAIN برای خطای بررسی آپدیت، مرحلهٔ آپدیت را هم دوباره اجرا می‌کند.
- رشته‌های `gate_update_check_failed(_sub)` به `values/strings.xml` و `values-fa/strings.xml` اضافه شد.

## ۵) `android/app/build.gradle.kts`

```kotlin
androidResources {
    // فقط تاربال ABI ساخته‌شده در APK می‌ماند
    ignoreAssetsPatterns += listOf("bin-arm.tar.xz", "bin-x86.tar.xz", "bin-x86_64.tar.xz")
}
```

- حدود **۲۰MB** از APK کم می‌شود.
- `targetSdk = 35`.
- نسخه: `1.13.2 / 11302`.

## ۶) `runtime.json`

از `"packs": []` به manifest واقعی (نسخهٔ ۲) با میرورهای pin‌شده (raw/jsDelivr/gcore) برای `jre-8/17/25`.
نکته: `jre-25/universal.tar.xz` روی jsDelivr کد 403 می‌دهد (سقف حجم CDN) — چون لیست میرورهاست، به‌طور خودکار به raw پین‌شده برمی‌گردد.

## ۷) `.github/workflows/android.yml` — علت قرمز بودن CI

`android-actions/setup-android@v3` در هر ران fail می‌شد و مرحلهٔ build اصلاً اجرا نمی‌شد. جایگزین:

- استفاده از SDK از پیش‌نصب روی `ubuntu-latest` + نصب خودکار `cmdline-tools` در صورت نبود،
- `sdkmanager --licenses` + نصب `platforms;android-37.2`, `build-tools;37.0.0`, `ndk;27.2.12479018`,
- کش Gradle،
- **مرحلهٔ راستی‌آزمایی**: `aapt2 dump badging` + `apksigner verify --print-certs` + اطمینان از وجود `jre-21/universal.tar.xz` و `bin-arm64.tar.xz` و **نبودن** تاربال‌های ABI دیگر،
- اجرا روی push به `main` و PR (نه فقط تگ) تا شکست کامپایل زود معلوم شود،
- آپلود **فایل `VERSION` به‌عنوان asset** (شرط کار کردن مسیر بدون‌سهمیهٔ بررسی آپدیت).
