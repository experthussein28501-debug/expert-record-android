# خط Cairo العربي — مُفعّل

الخط شغال بالفعل في `KhabirTheme.kt` عبر `KhabirFontFamily`.
الملفات المستخدمة داخل `core/src/main/res/font/` هي `cairo_regular.ttf` و`cairo_medium.ttf` و`cairo_bold.ttf`.

لتغيير الخط لاحقًا:
1. استبدل ملفات الخط داخل `res/font` بملفات خطوط Android صالحة.
2. عدّل مراجع `R.font.xxx` داخل `KhabirFontFamily` إذا تغيّرت أسماء الملفات.

> ملاحظة: لا توضع ملفات Markdown داخل `res/font` لأن Android Resource Merger يقبل فقط ملفات الخطوط أو XML في هذا المسار.
