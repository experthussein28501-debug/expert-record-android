package com.khabir.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khabir.app.data.local.dao.CaseDao
import com.khabir.app.data.local.dao.ExpertProfileDao
import com.khabir.app.data.local.dao.NotificationBatchDao
import com.khabir.app.data.local.dao.PartyDao
import com.khabir.app.data.local.dao.ReportDao
import com.khabir.app.data.local.dao.WorkMinutesDao
import com.khabir.app.data.local.entity.CaseEntity
import com.khabir.app.data.local.entity.ExpertProfileEntity
import com.khabir.app.data.local.entity.NotificationBatchEntity
import com.khabir.app.data.local.entity.NotificationRecipientEntity
import com.khabir.app.data.local.entity.PartyEntity
import com.khabir.app.data.local.entity.ReportEntity
import com.khabir.app.data.local.entity.WorkMinutesEntity

@Database(
    entities = [CaseEntity::class, PartyEntity::class, ExpertProfileEntity::class, NotificationBatchEntity::class, NotificationRecipientEntity::class, ReportEntity::class, WorkMinutesEntity::class],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun partyDao(): PartyDao
    abstract fun expertProfileDao(): ExpertProfileDao
    abstract fun notificationBatchDao(): NotificationBatchDao
    abstract fun reportDao(): ReportDao
    abstract fun workMinutesDao(): WorkMinutesDao

    companion object {
        const val DB_NAME = "khabir.db"
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `work_minutes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`caseId` INTEGER,`caseNo` TEXT NOT NULL DEFAULT '',`caseYear` TEXT NOT NULL DEFAULT '',`court` TEXT NOT NULL DEFAULT '',`plaintiffsSummary` TEXT NOT NULL DEFAULT '',`defendantsSummary` TEXT NOT NULL DEFAULT '',`entriesSpec` TEXT NOT NULL DEFAULT '',`copiesCount` INTEGER NOT NULL DEFAULT 1,`createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0,`updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(`caseId`) REFERENCES `cases`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_minutes_caseId` ON `work_minutes` (`caseId`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_recipients ADD COLUMN isAuthorityNotice INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notification_recipients ADD COLUMN incomingNoSnapshot TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `reports_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`caseId` INTEGER,`caseNo` TEXT NOT NULL DEFAULT '',`caseYear` TEXT NOT NULL DEFAULT '',`court` TEXT NOT NULL DEFAULT '',`partiesSummary` TEXT NOT NULL DEFAULT '',`subjectOfCase` TEXT NOT NULL DEFAULT '',`assignment` TEXT NOT NULL DEFAULT '',`documentsSubmitted` TEXT NOT NULL DEFAULT '',`facts` TEXT NOT NULL DEFAULT '',`technicalOpinion` TEXT NOT NULL DEFAULT '',`conclusion` TEXT NOT NULL DEFAULT '',`attachmentsNote` TEXT NOT NULL DEFAULT '',`depositDateEpochDay` INTEGER,`createdAtEpochMillis` INTEGER NOT NULL,`updatedAtEpochMillis` INTEGER NOT NULL,FOREIGN KEY(`caseId`) REFERENCES `cases`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""".trimIndent())
                db.execSQL("""INSERT INTO `reports_new` (`id`,`caseId`,`caseNo`,`caseYear`,`court`,`partiesSummary`,`subjectOfCase`,`assignment`,`documentsSubmitted`,`facts`,`technicalOpinion`,`conclusion`,`attachmentsNote`,`depositDateEpochDay`,`createdAtEpochMillis`,`updatedAtEpochMillis`) SELECT r.`id`,r.`caseId`,COALESCE(c.`caseNo`,''),COALESCE(c.`caseYear`,''),COALESCE(c.`court`,''),'',r.`subjectOfCase`,r.`assignment`,r.`documentsSubmitted`,r.`facts`,r.`technicalOpinion`,r.`conclusion`,r.`attachmentsNote`,r.`depositDateEpochDay`,r.`createdAtEpochMillis`,r.`updatedAtEpochMillis` FROM `reports` r LEFT JOIN `cases` c ON c.`id`=r.`caseId`""".trimIndent())
                db.execSQL("DROP TABLE `reports`")
                db.execSQL("ALTER TABLE `reports_new` RENAME TO `reports`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reports_caseId` ON `reports` (`caseId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `proceedings` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `partyStatements` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `inspection` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `research` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notification_batches` ADD COLUMN `requestedDocuments` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notification_recipients` ADD COLUMN `plaintiffsSummarySnapshot` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notification_recipients` ADD COLUMN `defendantsSummarySnapshot` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `manualHeader` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `receiptDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `preliminaryJudgmentDateEpochDay` INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `calculationsTable` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expert_profile` ADD COLUMN `jobTitle` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `expert_profile` ADD COLUMN `attendancePhrase` TEXT NOT NULL DEFAULT 'الرجاء الحضور إلى مكتب خبراء وزارة العدل'")
                db.execSQL("ALTER TABLE `expert_profile` ADD COLUMN `phone` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `expert_profile` ADD COLUMN `email` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `witnessStatements` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `templateId` TEXT NOT NULL DEFAULT 'built_in_civil'")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `templateName` TEXT NOT NULL DEFAULT 'مدني'")
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `templateSectionsSpec` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notification_batches` ADD COLUMN `ministryOrSectorSnapshot` TEXT NOT NULL DEFAULT 'وزارة العدل'")
                db.execSQL("ALTER TABLE `notification_batches` ADD COLUMN `departmentSnapshot` TEXT NOT NULL DEFAULT 'إدارة خبراء أسوان'")
                db.execSQL("ALTER TABLE `notification_batches` ADD COLUMN `expertJobTitleSnapshot` TEXT NOT NULL DEFAULT 'الخبير المحالة إليه المأمورية'")
                db.execSQL("ALTER TABLE `notification_batches` ADD COLUMN `attendancePhraseSnapshot` TEXT NOT NULL DEFAULT 'الرجاء الحضور إلى مكتب خبراء وزارة العدل'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `customSectionContentsSpec` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reports` ADD COLUMN `siteSketchPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `subjectOfCase` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `preliminaryMission` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `finalRequests` TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notification_recipients` ADD COLUMN `subjectOfCaseSnapshot` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notification_recipients` ADD COLUMN `preliminaryJudgmentDateEpochDay` INTEGER")
            }
        }
    }
}
