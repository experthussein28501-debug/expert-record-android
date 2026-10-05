package com.khabir.app.data.security

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** Values keep the existing day codec, encrypted at rest with an entry-bound GCM tag. */
class AgendaVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("khabir_agenda_encrypted_v2", Context.MODE_PRIVATE)
    private val cipher = DeviceTextCipher("khabir_agenda_v2")
    init {
        val legacy = context.getSharedPreferences("khabir_agenda_days_v1", Context.MODE_PRIVATE)
        val old = legacy.all.filter { it.key.toLongOrNull() != null && it.value is String }
        if (old.isNotEmpty()) {
            val editor = prefs.edit()
            old.forEach { (key, value) -> if (!prefs.contains(key)) editor.putString(key, cipher.encrypt(value as String, key)) }
            check(editor.commit()) { "تعذر تأمين ملاحظات الأجندة" }
            check(legacy.edit().clear().commit()) { "تعذر إكمال نقل الأجندة" }
        }
    }
    fun all(): Map<String, String> = prefs.all.filterKeys { it.toLongOrNull() != null }
        .mapValues { (key, value) -> cipher.decrypt(value as String, key) }
    fun put(key: String, value: String) { check(prefs.edit().putString(key, cipher.encrypt(value, key)).commit()) { "تعذر حفظ اليوم" } }
    fun remove(key: String) { check(prefs.edit().remove(key).commit()) { "تعذر حذف اليوم" } }
    fun putAll(values:Map<String,String?>) {
        val editor=prefs.edit()
        values.forEach { (key,value) -> if(value==null) editor.remove(key) else editor.putString(key,cipher.encrypt(value,key)) }
        check(editor.commit()) {"تعذر حفظ مواعيد الأجندة"}
    }
    fun exportPortable(): String = JSONObject(all()).toString()
    fun restorePortable(json: String) {
        val source = JSONObject(json)
        val editor = prefs.edit().clear()
        source.keys().forEach { key ->
            require(key.toLongOrNull() != null)
            val parts = source.getString(key).split('\t').toMutableList()
            require(parts.size >= 4)
            // Image references are private absolute paths in the old codec. Relocate on the new device.
            val paths = String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8).lines().filter { it.isNotBlank() }
            val relocated = paths.map { path ->
                val name = path.replace('\\', '/').substringAfterLast('/')
                require(name.isNotBlank() && name != "." && name != "..")
                File(context.filesDir, "agenda-media/$name").absolutePath
            }
            parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(relocated.joinToString("\n").toByteArray())
            editor.putString(key, cipher.encrypt(parts.joinToString("\t"), key))
        }
        check(editor.commit()) { "تعذر استعادة ملاحظات الأجندة" }
    }
}
