package com.khabir.app.di

import com.khabir.app.data.export.DocumentExportRepositoryImpl
import com.khabir.app.data.repository.*
import com.khabir.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindCaseRepository(impl: CaseRepositoryImpl): CaseRepository
    @Binds @Singleton abstract fun bindExpertProfileRepository(impl: ExpertProfileRepositoryImpl): ExpertProfileRepository
    @Binds @Singleton abstract fun bindNotificationBatchRepository(impl: NotificationBatchRepositoryImpl): NotificationBatchRepository
    @Binds @Singleton abstract fun bindDocumentExportRepository(impl: DocumentExportRepositoryImpl): DocumentExportRepository
    @Binds @Singleton abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
    @Binds @Singleton abstract fun bindWorkMinutesRepository(impl: WorkMinutesRepositoryImpl): WorkMinutesRepository
}

