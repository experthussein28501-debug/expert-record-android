package com.khabir.app.data.backup

import androidx.room.Database
import com.khabir.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSchemaVersionTest {
    @Test
    fun `backup schema follows Room database version`() {
        val roomVersion = AppDatabase::class.java.getAnnotation(Database::class.java)?.version
        assertEquals(18, roomVersion)
        assertEquals(roomVersion, AppDatabase.SCHEMA_VERSION)
    }
}
