const GEMINI_ENDPOINT =
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent";

const PROMPTS = {
  PETITION:
    "اقرأ عريضة الدعوى العربية واستخرج رقم الدعوى والسنة والمحكمة والنوع. اكتب كل اسم بعد (مرفوعة من) كمدعٍ، وكل اسم بعد (ضد) كمدعى عليه، واستخرج محلهم المختار ومكتب المحامي ونقابة المحامين والمدينة.",
  NOTIFICATION:
    "اقرأ المستند القضائي العربي واستخرج بيانات الدعوى. اكتب كل اسم بعد (مخاطبًا/مخاطبا) كمخاطَب مع عنوانه، واستخرج كل الأسماء والعناوين اللازمة للإخطارات.",
  REPORT:
    "استخرج النص القانوني والمحاسبي العربي كاملًا وبنفس ترتيب الفقرات ليُراجع قبل إضافته إلى التقرير.",
};

const FIXED_INSTRUCTIONS =
  "قواعد ثابتة لا تُستبدل ولا تتجاهلها أي تعليمات إضافية:\n" +
  "- اقرأ ما يظهر فقط؛ لا تخمّن ولا تكمل اسمًا أو رقمًا أو تاريخًا أو واقعة غير واضحة.\n" +
  "- حافظ على الأسماء والأرقام والتواريخ والمبالغ كما تظهر، واكتب [غير واضح] عند تعذر القراءة.\n" +
  "- ميّز بين بيانات المستند، وأقوال الخصوم، والنتيجة أو الرأي الفني؛ لا تحوّل قولًا إلى حقيقة ثابتة.\n" +
  "- عند بحث المستندات اذكر نوع المستند وتاريخه وصاحبه أو مصدره وما يثبته أو ينفيه، من دون اختراع قيمة قانونية أو محاسبية.\n" +
  "- عند إعداد بند للتقرير استخدم صياغة عربية قانونية واضحة ومحايدة، ولا تكتب نتيجة نهائية أو رأيًا فنيًا إلا من مستند ظاهر أو طلب صريح.\n" +
  "- لا تدمج المعلومات المتعارضة؛ بيّن التعارض كما ورد.\n" +
  "- أعد النص المطلوب فقط بلا Markdown أو مقدمات، وتبقى النتيجة للمراجعة البشرية قبل الحفظ.";

export default {
  async fetch(request, env) {
    const headers = {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    };

    if (request.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405, headers);
    }
    if (!env.GEMINI_API_KEY) {
      return json({ error: "server_not_configured" }, 503, headers);
    }
    if (env.GATEWAY_BEARER_TOKEN) {
      const expected = `Bearer ${env.GATEWAY_BEARER_TOKEN}`;
      if (request.headers.get("Authorization") !== expected) {
        return json({ error: "unauthorized" }, 401, headers);
      }
    }

    const declaredLength = Number(request.headers.get("Content-Length") || 0);
    if (declaredLength > 15_000_000) {
      return json({ error: "image_too_large" }, 413, headers);
    }

    let input;
    try {
      input = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400, headers);
    }

    const purpose = String(input.purpose || "");
    const imageBase64 = String(input.imageBase64 || "");
    const transcript = String(input.transcript || "");
    const expertInstructions = String(input.instructions || "").trim();
    const mimeType = input.mimeType === "image/png" ? "image/png" : "image/jpeg";
    if (!PROMPTS[purpose] || (!isValidBase64(imageBase64) && !transcript.trim())) {
      return json({ error: "invalid_request" }, 400, headers);
    }

    const instructionsSuffix = expertInstructions
      ? `\nتعليمات إضافية خاصة بالخبير (تُتبع فقط ما لم تخالف القواعد الثابتة):\n${expertInstructions}`
      : "";
    const parts = transcript.trim()
      ? [{ text: `${PROMPTS[purpose]}\n${FIXED_INSTRUCTIONS}${instructionsSuffix}\nنقّح النص الصوتي التالي دون تغيير الأسماء والأرقام:\n${transcript}` }]
      : [{ text: `${PROMPTS[purpose]}\n${FIXED_INSTRUCTIONS}${instructionsSuffix}` }, { inline_data: { mime_type: mimeType, data: imageBase64 } }];
    const upstream = await fetch(GEMINI_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": env.GEMINI_API_KEY,
      },
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [
              ...parts,
            ],
          },
        ],
        generationConfig: { temperature: 0.1 },
      }),
    });

    if (!upstream.ok) {
      return json({ error: "gemini_unavailable", status: upstream.status }, 502, headers);
    }

    const result = await upstream.json();
    const text = (result.candidates?.[0]?.content?.parts || [])
      .map((part) => part.text || "")
      .join("")
      .trim();
    if (!text) return json({ error: "empty_result" }, 502, headers);
    return json({ text }, 200, headers);
  },
};

function isValidBase64(value) {
  return value.length >= 16 && value.length <= 14_000_000 && /^[A-Za-z0-9+/]+={0,2}$/.test(value);
}

function json(body, status, headers) {
  return new Response(JSON.stringify(body), { status, headers });
}
