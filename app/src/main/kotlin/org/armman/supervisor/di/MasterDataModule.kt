package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.masterdata.ApplicationParameterApi
import org.armman.supervisor.data.masterdata.GeographyApi
import org.armman.supervisor.data.masterdata.ItemMasterAndTrainingApi
import org.armman.supervisor.data.masterdata.MasterDataCategoryApi
import org.armman.supervisor.data.masterdata.MasterDataRepository
import org.armman.supervisor.data.masterdata.MasterDataRepositoryImpl
import org.armman.supervisor.data.masterdata.MockUnreadyMasterDataEntities
import org.armman.supervisor.data.masterdata.RiskAndFundersApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Download Master Data screen's data source. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MasterDataModule {
  @Binds
  @Singleton
  abstract fun bindMasterDataRepository(impl: MasterDataRepositoryImpl): MasterDataRepository

  companion object {
    /** [BuildConfig.DEBUG] is `false` for every release build (see `app/build.gradle.kts`'s
     * release variant), so [MockUnreadyMasterDataEntities.enabled] can never be `true` outside a
     * debug build regardless of anything set at the call site. */
    @Provides
    @Singleton
    fun provideMockUnreadyMasterDataEntities(): MockUnreadyMasterDataEntities =
      MockUnreadyMasterDataEntities(enabled = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideGeographyApi(retrofit: Retrofit): GeographyApi = retrofit.create(GeographyApi::class.java)

    @Provides
    @Singleton
    fun provideRiskAndFundersApi(retrofit: Retrofit): RiskAndFundersApi =
      retrofit.create(RiskAndFundersApi::class.java)

    @Provides
    @Singleton
    fun provideMasterDataCategoryApi(retrofit: Retrofit): MasterDataCategoryApi =
      retrofit.create(MasterDataCategoryApi::class.java)

    @Provides
    @Singleton
    fun provideItemMasterAndTrainingApi(retrofit: Retrofit): ItemMasterAndTrainingApi =
      retrofit.create(ItemMasterAndTrainingApi::class.java)

    @Provides
    @Singleton
    fun provideApplicationParameterApi(retrofit: Retrofit): ApplicationParameterApi =
      retrofit.create(ApplicationParameterApi::class.java)
  }
}
