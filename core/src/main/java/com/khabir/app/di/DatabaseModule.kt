package com.khabir.app.di

import android.content.Context
import androidx.room.Room
import com.khabir.app.data.local.AppDatabase
import com.khabir.app.data.local.dao.CaseDao
import com.khabir.app.data.local.dao.ExpertProfileDao
import com.khabir.app.data.local.dao.NotificationBatchDao
import com.khabir.app.data.local.dao.PartyDao
import com.khabir.app.data.local.dao.ReportDao
import com.khabir.app.data.local.dao.WorkMinutesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17
            )
            .build()

    @Provides fun provideCaseDao(db: AppDatabase): CaseDao = db.caseDao()
    @Provides fun providePartyDao(db: AppDatabase): PartyDao = db.partyDao()
    @Provides fun provideExpertProfileDao(db: AppDatabase): ExpertProfileDao = db.expertProfileDao()
    @Provides fun provideNotificationBatchDao(db: AppDatabase): NotificationBatchDao = db.notificationBatchDao()
    @Provides fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()
    @Provides fun provideWorkMinutesDao(db: AppDatabase): WorkMinutesDao = db.workMinutesDao()
}
