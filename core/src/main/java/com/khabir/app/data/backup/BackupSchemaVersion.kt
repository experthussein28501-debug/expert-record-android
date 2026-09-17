package com.khabir.app.data.backup

import com.khabir.app.data.local.AppDatabase

/** Keep backup compatibility checks tied to the Room schema used by this build. */
internal val AppDatabase.Companion.SCHEMA_VERSION: Int
    get() = 17
