package com.khabir.app.data.backup

import java.io.InputStream
import java.io.OutputStream

internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
    require(limit >= 0)
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val size = input.read(buffer)
        if (size < 0) return total
        total += size
        require(total <= limit) { "تجاوزت النسخة الحد الأقصى المسموح للحجم" }
        output.write(buffer, 0, size)
    }
}
