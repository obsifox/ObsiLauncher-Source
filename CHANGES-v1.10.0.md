# Changes in v1.10.0

## Bundled trailer video (no more downloads)
- The background trailer is now **bundled inside the APK**
  (`res/raw/video_wallpaper.mp4`, H.264 1080p — decodes on every Android
  device, unlike the old HEVC download). No YouTube fetching, no
  downloading screen, no failure states: the video is always ready.
- The video plays only for the latest Minecraft release (26.3 or newer —
  with an offline fallback so the gate never dies when Mojang's manifest
  is unreachable) and only when the user picked the **video** background.

## The 45-second cycle
- Exactly as briefed: the video plays **once**, then the **static version
  wallpaper holds the screen for 45 seconds**, then the video plays again —
  an endless loop of video → wallpaper (45 s) → video.

## Sound + mute button
- The trailer now plays **with sound** (the old engine was hard-muted).
- A round **speaker button** sits just under the floating top bar on the
  end side of the Home dashboard: tap it to **mute / unmute** the
  background video. The choice is remembered in settings, also available
  under Settings → Background → **Video sound** (On / Muted).
- Audio pauses automatically when the launcher goes to the background and
  resumes when it comes back.

## Cleanup
- The YouTube resolver (`YouTube.kt`) and the trailer download/progress
  UI are removed — the APK grew only by the video itself.
- Version 1.10.0 (11000).

# تغییرات نسخه 1.10.0

## تریلر همراه برنامه (بدون دانلود)
- ویدیوی پس‌زمینه حالا **داخل خود APK** قرار دارد
  (`res/raw/video_wallpaper.mp4`، با کدک H.264 و کیفیت 1080p — روی همه‌ی
  دستگاه‌های اندرویدی پخش می‌شود). دیگر هیچ دانلودی از یوتیوب وجود ندارد و
  ویدیو همیشه آماده است.
- ویدیو فقط برای آخرین نسخه‌ی ماینکرافت (26.3 یا جدیدتر — با فال‌بک آفلاین)
  و فقط وقتی پخش می‌شود که کاربر نوع بک‌گراند را **ویدیو** انتخاب کرده باشد.

## چرخه‌ی ۴۵ ثانیه‌ای
- دقیقاً مثل خواسته: ویدیو **یک بار** پخش می‌شود، بعد **والپیپر ثابتِ نسخه
  ۴۵ ثانیه** روی صفحه می‌ماند و بعد دوباره ویدیو پخش می‌شود — لوپ بی‌نهایت.

## صدا و دکمه‌ی میوت
- تریلر حالا **با صدا** پخش می‌شود (موتور قبلی همیشه بی‌صدا بود).
- یک **دکمه‌ی گرد بلندگو** درست زیر نوار بالاییِ صفحه‌ی اصلی نشسته: با یک
  لمس ویدیوی پس‌زمینه را **بی‌صدا / با صدا** کنید. انتخاب در تنظیمات ذخیره
  می‌شود و در تنظیمات ← بک‌گراند ← **صدای ویدیو** هم هست (پخش صدا / بی‌صدا).
- وقتی لانچر به پس‌زمینه می‌رود صدا خودکار قطع و با برگشت ادامه پیدا می‌کند.

## پاک‌سازی
- رزولور یوتیوب و رابط دانلود تریلر حذف شدند؛ حجم APK فقط به اندازه‌ی خود
  ویدیو بزرگ شده است.
- نسخه 1.10.0 (11000).
