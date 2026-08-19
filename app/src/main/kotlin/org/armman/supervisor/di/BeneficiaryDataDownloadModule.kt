package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.beneficiarydatadownload.ArogyaSakhiRosterApi
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataRepository
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataRepositoryImpl
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDownloadListApi
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryRiskApi
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryVisitApi
import org.armman.supervisor.data.beneficiarydatadownload.GatheringDownloadApi
import org.armman.supervisor.data.beneficiarydatadownload.MockUnreadyBeneficiaryDataEntities
import org.armman.supervisor.data.beneficiarydatadownload.RiskMonitoringApi
import org.armman.supervisor.data.beneficiarydatadownload.SyncPendingApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Download Beneficiary Data screen's data source. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BeneficiaryDataDownloadModule {
  @Binds
  @Singleton
  abstract fun bindBeneficiaryDataRepository(impl: BeneficiaryDataRepositoryImpl): BeneficiaryDataRepository

  companion object {
    /** [BuildConfig.DEBUG] is `false` for every release build (see `app/build.gradle.kts`'s
     * release variant), so [MockUnreadyBeneficiaryDataEntities.enabled] can never be `true`
     * outside a debug build regardless of anything set at the call site. */
    @Provides
    @Singleton
    fun provideMockUnreadyBeneficiaryDataEntities(): MockUnreadyBeneficiaryDataEntities =
      MockUnreadyBeneficiaryDataEntities(enabled = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideBeneficiaryDownloadListApi(retrofit: Retrofit): BeneficiaryDownloadListApi =
      retrofit.create(BeneficiaryDownloadListApi::class.java)

    @Provides
    @Singleton
    fun provideSyncPendingApi(retrofit: Retrofit): SyncPendingApi = retrofit.create(SyncPendingApi::class.java)

    @Provides
    @Singleton
    fun provideGatheringDownloadApi(retrofit: Retrofit): GatheringDownloadApi =
      retrofit.create(GatheringDownloadApi::class.java)

    @Provides
    @Singleton
    fun provideBeneficiaryRiskApi(retrofit: Retrofit): BeneficiaryRiskApi = retrofit.create(BeneficiaryRiskApi::class.java)

    @Provides
    @Singleton
    fun provideBeneficiaryVisitApi(retrofit: Retrofit): BeneficiaryVisitApi =
      retrofit.create(BeneficiaryVisitApi::class.java)

    @Provides
    @Singleton
    fun provideRiskMonitoringApi(retrofit: Retrofit): RiskMonitoringApi = retrofit.create(RiskMonitoringApi::class.java)

    @Provides
    @Singleton
    fun provideArogyaSakhiRosterApi(retrofit: Retrofit): ArogyaSakhiRosterApi =
      retrofit.create(ArogyaSakhiRosterApi::class.java)
  }
}
