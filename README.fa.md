<p align="center"><img src="docs/assets/logo.png" width="128" alt="ObsiLauncher"></p>

<h1 align="center" dir="rtl">ObsiLauncher</h1>

<div dir="rtl">

لانچر چندپروفایلی **ماینکرافت جاوا** با اتصال مستقیم به **Modrinth** برای **اندروید، ویندوز و لینوکس**.

> مخزن عمومی · نسخه‌ی `0.1.0` (MVP). این پروژه هیچ وابستگی‌ای به Mojang یا Microsoft ندارد.

## ⬇ دانلود

فایل‌های ساخته‌شده در صفحه‌ی **[Releases ← Nightly builds](https://github.com/obsifox/ObsiLauncher-Source/releases/tag/nightly)** هستند. مخزن عمومی است؛ برای دانلود نیازی به لاگین نیست.

| پلتفرم | فایل |
|---|---|
| اندروید ۸+ (arm64) | [ObsiLauncher-0.1.0-zl2.6.1-arm64-v8a.apk](https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/ObsiLauncher-0.1.0-zl2.6.1-arm64-v8a.apk) |
| ویندوز | [نصب‌کننده (.msi)](https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/ObsiLauncher-0.1.0-windows-x64.msi) · [قابل‌حمل (.zip)](https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/ObsiLauncher-0.1.0-windows-x64-portable.zip) |
| لینوکس | [.deb](https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/ObsiLauncher-0.1.0-linux-x64.deb) · [قابل‌حمل (.tar.gz)](https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/ObsiLauncher-0.1.0-linux-x64-portable.tar.gz) |

## امکانات

- پروفایل‌های جدا (هرکدام مود، دنیا و تنظیمات خودش)
- نصب خودکار Vanilla، Fabric، Quilt، Forge و NeoForge
- حساب مایکروسافت و حساب آفلاین
- Modrinth: جستجو، نصب مود با وابستگی‌ها، بررسی به‌روزرسانی، نصب مادپک‌های `.mrpack`
- نسخه‌ی دسکتاپ: رابط **فارسی/انگلیسی با چیدمان راست‌به‌چپ** و فونت وزیرمتن، کنسول زنده‌ی بازی، پنجره‌ی گزارش کرش، آینه‌ی BMCLAPI و پروکسی
- نسخه‌ی اندروید: بر پایه‌ی ZalithLauncher2 (GPL-3.0) با برندینگ ObsiLauncher

## دانلود

فایل‌های ساخته‌شده در [Nightly](../../releases/tag/nightly) هستند: `…arm64-v8a.apk` (اندروید)، `…windows-x64.msi` یا `…portable.zip` (ویندوز)، `…linux-x64.deb` یا `…portable.tar.gz` (لینوکس).
نصب در لینوکس: `sudo apt install ./ObsiLauncher-<ver>-linux-x64.deb` (روی Debian 13 آزمایش شده؛ روی Debian 12 / Ubuntu 22.04 آزمایش نشده) یا آرشیو portable را باز کنید و `ObsiLauncher/bin/ObsiLauncher` را اجرا کنید. در ویندوز فایل `.msi` را اجرا کنید یا آرشیو portable را باز کرده و `ObsiLauncher.exe` را بزنید.
برای ساخت نسخه‌ی تازه: **Actions ← Android / Desktop ← Run workflow**.

## نکات مهم

- ورود با مایکروسافت تا وقتی `MS_CLIENT_ID` نگذاری و Mojang آن را تأیید نکند غیرفعال است (راهنما: [docs/SETUP.md](docs/SETUP.md)). حساب آفلاین همین حالا کار می‌کند.
- کلید امضای اندروید را حتماً بکاپ بگیر.
- جزئیات معماری: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · مجوزها: [NOTICE.md](NOTICE.md)

</div>
