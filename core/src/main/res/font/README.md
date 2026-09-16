# خط Cairo العربي — مُفعّل

الخط شغال بالفعل في `KhabirTheme.kt` (نظرة على `KhabirFontFamily`).
الملفات الموجودة هنا (`cairo_regular.ttf`, `cairo_medium.ttf`,
`cairo_bold.ttf`) اتأكد إنها بتغطي الحروف العربية كاملة قبل تفعيلها.

لو عايز تغيّر الخط لاحقًا لخط تاني (Amiri, IBM Plex Sans Arabic...):
1. استبدل الملفات هنا بنفس الأسماء، أو بأسماء جديدة
2. عدّل مراجع `R.font.xxx` في `KhabirFontFamily` داخل `KhabirTheme.kt`
   لو غيّرت الأسماء
