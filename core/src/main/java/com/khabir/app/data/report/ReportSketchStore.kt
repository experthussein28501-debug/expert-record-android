package com.khabir.app.data.report

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the rendered site sketch in app-private storage; the report keeps only its path. */
@Singleton
class ReportSketchStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun save(bitmap: Bitmap): String {
        val dir = File(context.filesDir, "report_sketches").apply { mkdirs() }
        val file = File(dir, "site_sketch_${System.currentTimeMillis()}.png")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ المخطط" }
        }
        return file.absolutePath
    }

    fun delete(path: String) {
        if (path.isNotBlank()) runCatching { File(path).delete() }
    }
}
