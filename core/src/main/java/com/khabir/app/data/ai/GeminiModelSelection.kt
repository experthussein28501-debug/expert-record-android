package com.khabir.app.data.ai

/** Only rank names advertised for generateContent; never invent a fallback model. */
internal object GeminiModelSelection {
    fun choose(names: List<String>): String? = names
        .filter { it.startsWith("models/gemini-") && it.contains("flash") }
        .filterNot { name -> listOf("image", "audio", "tts", "live", "robotics", "computer-use").any { name.contains(it, true) } }
        .sortedWith(compareBy<String>(
            { if (it.contains("preview") || it.contains("exp")) 1 else 0 },
            { if (it.contains("flash-lite")) 0 else 1 },
            { if (it.contains("flash")) 0 else 1 }
        ).thenByDescending { it })
        .firstOrNull()
}
