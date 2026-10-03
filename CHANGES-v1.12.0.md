# ObsiLauncher v1.12.0

**EN**
- **Buttons rebuilt on the supplied assets** — the big PLAY/DOWNLOAD button and the sound button are now drawn exactly like the uploaded Minecraft buttons: light top strip, flat face, dark 3D bottom bevel, black frame, bold white text. Positions follow the mock: big button anchored bottom-right, pills and info row bottom-left.
- **Three-state download button** — BLUE = not downloaded (tap to download) → GRAY = downloading, with a GREEN fill growing from the language's start corner (right in Farsi, left in English) → GREEN = installed (PLAY).
- **PLAY pre-flight (no more crash-to-desktop on PLAY)** — before the game spawns, the launcher now checks and fixes everything: missing version files are downloaded and sha1-verified, a missing or broken runtime/JVM pack (e.g. lost `libjvm.so`) is re-downloaded automatically — progress live on the big button. Only a fully provisioned device reaches the fork/exec.
- **Version switch lands on the photo** — switching between the latest release and older versions now shows that version's artwork immediately, then the trailer plays on the usual schedule (45 s later).
- **Seamless video loop** — every replay starts from 0:00 (never mid-way), and across the video's final ~1 second the version wallpaper eases in slowly, so the cut to the photo is smooth.
- **Square sound button** — green with a white note = sound on, charcoal = muted, matching the supplied icons.

**FA**
- **بازسازی دکمه‌ها بر اساس فایل‌های ارسالی** — دکمه بزرگ PLAY/DOWNLOAD و دکمه صدا دقیقاً مثل دکمه‌های ماینکرفتری که فرستادید کشیده می‌شوند: نوار روشن بالا، بدنه تخت، لبه سه‌بعدی تیره پایین، قاب مشکی و متن سفید ضخیم. جای‌چین طبق موکاپ: دکمه بزرگ گوشه پایین-راست، قرص‌ها و ردیف اطلاعات پایین-چپ.
- **دکمه دانلود سه‌حالته** — آبی = دانلود نشده (برای دانلود بزنید) ← خاکستری = در حال دانلود با پُرشدن سبز از سمت زبان (فارسی راست‌به‌چپ، انگلیسی چپ‌به‌راست) ← سبز = نصب‌شده (PLAY).
- **پیش‌-flight برای PLAY (دیگر هنگام بازی‌کردن کرش نمی‌کند)** — قبل از اجرا، لانچر همه‌چیز را چک و تعمیر می‌کند: فایل‌های جاافتاده نسخه دانلود و با sha1 تأیید می‌شوند؛ پک ران‌تایم/JVM خراب یا ناقص (مثلاً libjvm.so گم‌شده) خودکار دوباره دانلود می‌شود — با پیشرفت زنده روی همان دکمه بزرگ.
- **سوییچ نسخه روی عکس می‌نشیند** — جابه‌جایی بین آخرین نسخه و نسخه‌های قدیمی، بلافاصله artwork همان نسخه را نشان می‌دهد و بعد طبق زمان‌بندی همیشگی (۴۵ ثانیه بعد) تریلر پخش می‌شود.
- **لوپ بی‌درز ویدیو** — هر پخش از صفر شروع می‌شود (نه از وسط) و در ثانیه آخر ویدیو (حدود ۱ ثانیه) والپیپر نسخه به‌آرامی بالا می‌آید تا قطع ناگهانی نداشته باشیم.
- **دکمه مربعی صدا** — سبز با نُت سفید = صدا روشن، زغالی = بی‌صدا، مطابق آیکون‌های ارسالی.

*Free forever · GPL-3.0 · MobileGlues is the only third-party credit.*
