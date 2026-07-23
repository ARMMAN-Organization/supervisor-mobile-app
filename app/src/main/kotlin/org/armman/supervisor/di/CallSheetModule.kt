package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.callsheet.CallSheetRepositoryImpl
import org.armman.supervisor.ui.callsheet.CallSheetRepository

/** Binds the Call Sheet data source. Currently local sample data; swaps to network calls in place. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CallSheetModule {
  @Binds
  abstract fun bindCallSheetRepository(impl: CallSheetRepositoryImpl): CallSheetRepository
}
