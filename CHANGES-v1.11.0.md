# Changes in v1.11.0

## The download button can no longer blow up the layout
- **Fixed the bug from v1.10.0**: the moment a download started, the big
  button swallowed all free vertical space (a `fillMaxHeight` fill box
  inside a wrap-content container), stretched from the bottom edge almost
  to the top bar and pushed the status chip, version pills and everything
  else into the top bar. The design fell apart.
- The big button now has a **fixed height (78 dp)** and the blue progress
  fill is *painted* onto it (`drawBehind`) — it can never stretch the
  layout again. The fill still grows from the language's start corner
  (right side in Farsi, left in English).

## The reference Minecraft button look
- The big button is now drawn exactly like the reference the user picked:
  a **green face (light → grass green), hard dark border, a chunky dark
  bevel along the bottom edge and bold white text**.
  - green + PLAY = installed and ready
  - gray + DOWNLOAD = not downloaded yet
  - charcoal + blue fill + % = downloading
- The same Minecraft bevel was added to **every `ObsiButton` and ghost
  button** across the launcher (install / delete / save / login …), so
  all controls share one consistent style.

## Text readability on bright backgrounds
- The bottom dashboard scrim is a touch deeper (three-step gradient) so
  labels, pills and the big button stay readable on bright artwork and
  video frames — the artwork itself stays sharp and un-blurred.
- The confusing status chip that used to float near the top bar during a
  download ("no version installed / Download") is gone while a download
  runs — the big button already shows the live progress.

## Download inspection & auto-heal (check + repair)
- Every install now ends with a **full verification pass**: the client
  jar, every library and every asset are checked against their published
  **sha1 / size**.
- Anything **missing or corrupt is deleted and re-downloaded — starting
  exactly from the first bad file** — and the cycle repeats until every
  single file is intact (max 3 passes, then a hard failure instead of a
  silently broken install).
- Pressing **DOWNLOAD again on a half-installed version resumes**: good
  files are kept and verified, only the broken ones are fetched again.
- Existing libraries are no longer trusted blindly: a library is only
  skipped when its checksum matches.

## Instant version switching
- Switching versions (e.g. 1.12.2 → 1.16.5) now updates the background
  **immediately**: the per-era bundled artwork for the new version is
  published first, with zero network calls, and the heavyweight
  minecraft.net wallpaper pack only *upgrades* the background afterwards.
- Each sync cancels the previous one (a slow old sync can no longer
  overwrite a newer switch) and resolved artwork is cached, so flipping
  back and forth between versions is instant.

## App icon fixed
- The launcher icon failed to inflate on device launchers: the adaptive
  icon's background color file was placed in `res/drawable/` instead of
  `res/values/` (a `<resources>` file is not a drawable), so launchers
  showed **no icon at all**.
- The color moved to `res/values/`, the adaptive icon now references it
  as `@color/…`, and the foreground glyph was re-fitted into the
  adaptive-icon safe zone so no mask (circle / squircle / rounded square)
  cuts it.

## Misc
- The video sound (mute) button moved into the dashboard control row
  (and the empty-state column on first run) — always visible, never
  overlapping the top bar on either language side.
- Version 1.11.0 (11100).

# تغییرات نسخه ۱.۱۱.۰

## دکمه دانلود دیگر چیدمان را خراب نمی‌کند
- **رفع باگ نسخه قبل**: به‌محض شروع دانلود، دکمه بزرگ تمام فضای عمودی
  خالی را می‌بلعید و از لبه پایین تقریباً تا نوار بالا کشیده می‌شد و چیپ
  وضعیت و قرص‌های نسخه را به بالای صفحه می‌راند.
- دکمه بزرگ حالا **ارتفاع ثابت (۷۸dp)** دارد و فیل پیشرفت با drawBehind
  روی آن نقاشی می‌شود — دیگر هرگز چیدمان را نمی‌کشد. فیل همچنان از سمت
  شروع زبان پر می‌شود (راست در فارسی، چپ در انگلیسی).

## استایل مرجع دکمه ماینکرفت
- دکمه بزرگ دقیقاً مثل تصویر مرجع شماست: **رویه سبز (روشن تا سبز چمنی)،
  حاشیه تیره، لبه سه‌بعدی تیره در پایین و متن سفید ضخیم**.
  - سبز + PLAY = نصب‌شده و آماده
  - خاکستری + DOWNLOAD = هنوز دانلود نشده
  - ذغالی + فیل آبی + درصد = در حال دانلود
- همین لبه سه‌بعدی به **تمام دکمه‌های اصلی و ثانویه** لانچر اضافه شد تا
  همه کنترل‌ها یک استایل یکپارچه داشته باشند.

## خوانایی متن روی بک‌گراندهای روشن
- اسکریم پایینی کمی عمیق‌تر شد تا برچسب‌ها و دکمه‌ها روی هر تصویری واضح
  بمانند — خود تصویر همچنان شارپ و بدون بلور است.
- چیپ گمراه‌کننده کنار نوار بالا هنگام دانلود حذف شد؛ دکمه بزرگ خودش
  پیشرفت زنده را نشان می‌دهد.

## چک و بازرسی دانلود + ترمیم خودکار
- هر نصب با یک **گذر کامل بررسی** تمام می‌شود: جار کلاینت، همه کتابخانه‌ها
  و همهassetها با **sha1 / حجم** منتشرشده چک می‌شوند.
- هر فایل **خراب یا ناقص حذف و دوباره دانلود می‌شود — دقیقاً از اولین فایل
  خراب** — و این چرخه تا سالم‌شدن تمام فایل‌ها تکرار می‌شود (حداکثر ۳
  گذر، وگرنه خطای صریح به‌جای نصب خراب خاموش).
- فشردن دوباره DOWNLOAD روی نسخه نیمه‌نصب، **ادامه‌ی دانلود** است: فایل‌های
  سالم حفظ می‌شوند و فقط فایل‌های خراب دوباره گرفته می‌شوند.

## سوییچ آنی ورژن
- تعویض ورژن (مثلاً ۱.۱۲.۲ → ۱.۱۶.۵) حالا **بلافاصله** بک‌گراند را عوض
  می‌کند: ابتدا آرت باندل‌شده‌ی همان دوره بدون هیچ درخواست شبکه منتشر
  می‌شود و پک والپیپر minecraft.net فقط بعداً آن را «ارتقا» می‌دهد.
- هر sync قبلی لغو می‌شود و نتیجه کش می‌شود؛ رفت‌وبرگشت بین ورژن‌ها آنی است.

## رفع آیکون برنامه
- آیکون لانچر روی صفحه‌گوشی‌ها نمایش داده نمی‌شد: فایل رنگ بک‌گراند آیکون
  تطبیقی اشتباهاً در `res/drawable/` بود به‌جای `res/values/` و inflate
  آیکون شکست می‌خورد.
- رنگ به `values/` منتقل شد و glyph آیکون هم در ناحیه امن آیکون تطبیقی
  جاگیری دوباره شد تا هیچ ماسکی آن را نبرد.

## متفرقه
- دکمه صدا (میوت) ویدیو به ردیف کنترل‌های داشبورد منتقل شد — همیشه پیداست
  و روی هیچ زبانی با نوار بالا تداخل ندارد.
- نسخه ۱.۱۱.۰ (۱۱۱۰۰).
