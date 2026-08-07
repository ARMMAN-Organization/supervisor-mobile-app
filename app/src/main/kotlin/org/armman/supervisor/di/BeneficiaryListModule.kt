package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.beneficiaries.BeneficiaryListRepositoryImpl
import org.armman.supervisor.ui.beneficiaries.BeneficiaryListRepository

/** Binds the Sakhi beneficiary-list data source. Still local sample data (see
 * [BeneficiaryListRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BeneficiaryListModule {
  @Binds
  abstract fun bindBeneficiaryListRepository(impl: BeneficiaryListRepositoryImpl): BeneficiaryListRepository
}
