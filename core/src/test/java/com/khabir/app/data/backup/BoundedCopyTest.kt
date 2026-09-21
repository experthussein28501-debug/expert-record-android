package com.khabir.app.data.backup
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
class BoundedCopyTest {
    @Test fun exactLimitCopiesSuccessfully() {
        val output = ByteArrayOutputStream()
        assertEquals(32L, copyBounded(ByteArray(32).inputStream(), output, 32))
        assertEquals(32, output.size())
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedEntryIsRejected() {
        copyBounded(ByteArray(33).inputStream(), ByteArrayOutputStream(), 32)
    }
}
