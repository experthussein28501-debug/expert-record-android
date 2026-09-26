package com.khabir.app.data.backup

import com.khabir.app.data.local.AppDatabase
import com.khabir.app.data.local.KHABIR_DATABASE_VERSION

/** Keep backup compatibility checks tied to the Room schema used by this build. */
internal val AppDatabase.Companion.SCHEMA_VERSION: Int
    get() = KHABIR_DATABASE_VERSION
