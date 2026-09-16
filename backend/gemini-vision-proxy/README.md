# Gemini Vision proxy

بوابة خفيفة تربط زر «تحليل Gemini» في التطبيق بواجهة Gemini وتُبقي `GEMINI_API_KEY` خارج الـAPK.

## العقد

- `POST` فقط.
- الطلب: `imageBase64` و`mimeType` و`purpose`.
- الاستجابة الناجحة: `{ "text": "..." }`.
- لا تُسجّل البوابة الصورة ولا تخزنها، وتعيد `Cache-Control: no-store`.
- نص التعليمات ثابت في الخادم؛ لا تقبل البوابة Prompt من الهاتف.

## الإعداد

1. انسخ `wrangler.toml.example` إلى `wrangler.toml`.
2. خزّن `GEMINI_API_KEY` كسر نشر مشفر.
3. انشر البوابة، ثم ابنِ Android مع:

```properties
KHABIR_GEMINI_VISION_ENDPOINT=https://your-worker.example.workers.dev
```

`GATEWAY_BEARER_TOKEN` اختياري للاختبارات فقط، وليس بديلًا عن Firebase App Check أو طبقة مصادقة موثوقة في الإصدار العام.
