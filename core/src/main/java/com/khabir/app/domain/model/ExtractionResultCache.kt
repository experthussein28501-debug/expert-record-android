package com.khabir.app.domain.model

/** Bounded session-only cache; credential values and failed/incomplete results are never stored. */
class ExtractionResultCache(private val capacity: Int = 40) {
    private val entries = LinkedHashMap<String,String>(capacity,0.75f,true)
    @Synchronized fun get(key:String): String? = entries[key]
    @Synchronized fun put(key:String,value:String) {
        if(value.isBlank()) return
        entries[key] = value
        while(entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() = entries.clear()
    companion object {
        const val RULES_VERSION = "legal-intake-full-v2"
        fun key(pages:List<ByteArray>,purpose:String,instructions:String,scope:String,version:String=RULES_VERSION):String =
            legalFingerprint(listOf(version,purpose,instructions,scope,pages.joinToString { bytes ->
                legalFingerprint(Base64Bytes.encode(bytes)) }
            ).joinToString("\u001f"))
    }
}
private object Base64Bytes { fun encode(bytes:ByteArray):String = java.util.Base64.getEncoder().encodeToString(bytes) }
