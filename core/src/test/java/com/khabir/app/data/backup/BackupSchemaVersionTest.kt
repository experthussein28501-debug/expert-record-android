package com.khabir.app.data.backup

import com.khabir.app.data.local.AppDatabase
import com.khabir.app.data.local.KHABIR_DATABASE_VERSION
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSchemaVersionTest {
    @Test
    fun `backup schema follows the single Room schema constant`() {
        assertEquals(18, KHABIR_DATABASE_VERSION)
        assertEquals(KHABIR_DATABASE_VERSION, AppDatabase.SCHEMA_VERSION)
    }
}
