package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.registrations.RegistrationsRepositoryImpl
import org.armman.supervisor.ui.registrations.RegistrationsRepository

/** Binds the Registrations detail-screen data source (see [RegistrationsRepositoryImpl]). Reuses
 * [org.armman.supervisor.data.beneficiaries.BeneficiaryListApi], provided by
 * [BeneficiaryListModule]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RegistrationsModule {
  @Binds
  abstract fun bindRegistrationsRepository(impl: RegistrationsRepositoryImpl): RegistrationsRepository
}
