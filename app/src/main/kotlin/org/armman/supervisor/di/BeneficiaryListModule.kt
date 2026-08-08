package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryListRepositoryImpl
import org.armman.supervisor.ui.beneficiaries.BeneficiaryListRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Sakhi beneficiary-list data source (see [BeneficiaryListRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BeneficiaryListModule {
  @Binds
  abstract fun bindBeneficiaryListRepository(impl: BeneficiaryListRepositoryImpl): BeneficiaryListRepository

  companion object {
    @Provides
    @Singleton
    fun provideBeneficiaryListApi(retrofit: Retrofit): BeneficiaryListApi =
      retrofit.create(BeneficiaryListApi::class.java)
  }
}
