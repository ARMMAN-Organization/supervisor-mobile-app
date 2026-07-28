package org.armman.supervisor.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.auth.session.SecureKeyValueStore
import org.armman.supervisor.data.local.AppDatabase
import org.armman.supervisor.data.local.CallLogDao
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.TransactionDao
import java.security.SecureRandom
import javax.inject.Singleton

private const val SEED_TRANSACTION_ID = "txn-seed-1"
private const val SEED_SAKHI_ID = "sakhi-1"

private const val DATABASE_NAME = "armman_supervisor.db"
private const val PASSPHRASE_KEY = "local_db_passphrase"
private const val PASSPHRASE_BYTES = 32

/**
 * Provides the local encrypted database (see `data/local/AppDatabase.kt`). The SQLCipher
 * passphrase is a random value generated once and stored via [SecureKeyValueStore] (Android
 * Keystore-backed `EncryptedSharedPreferences`), matching this app's "encrypt any local database
 * with a Keystore-backed key" standard.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideAppDatabase(@ApplicationContext context: Context, secureStore: SecureKeyValueStore): AppDatabase {
    val passphrase = secureStore.getString(PASSPHRASE_KEY) ?: generatePassphrase().also {
      secureStore.putString(PASSPHRASE_KEY, it)
    }
    val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
    val builder = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
      .openHelperFactory(factory)
      // Pre-launch local store standing in for a real API; safe to drop and recreate on schema change.
      .fallbackToDestructiveMigration()
    if (BuildConfig.DEBUG) {
      // Runs exactly once, when the database file is first created — the correct place for seed
      // data, since a runtime "is the table empty" check would resurrect the seed after a user
      // deletes their only transaction (see AssignItemRepositoryImplTest for the regression case).
      // Debug-only: real supervisors must never see this fake row in a production build.
      builder.addCallback(SeedDataCallback)
    }
    return builder.build()
  }

  @Provides
  fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

  @Provides
  fun provideSupervisorEventDao(database: AppDatabase): SupervisorEventDao = database.supervisorEventDao()

  @Provides
  fun provideCallLogDao(database: AppDatabase): CallLogDao = database.callLogDao()

  private fun generatePassphrase(): String {
    val bytes = ByteArray(PASSPHRASE_BYTES)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private object SeedDataCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
      super.onCreate(db)
      db.execSQL(
        "INSERT INTO transactions (id, sakhiId, date, transactionType) VALUES (?, ?, ?, ?)",
        arrayOf(SEED_TRANSACTION_ID, SEED_SAKHI_ID, "10 Oct 2025", "CONSUMED"),
      )
      db.execSQL(
        "INSERT INTO transaction_items (transactionId, itemName, quantity) VALUES (?, ?, ?)",
        arrayOf(SEED_TRANSACTION_ID, "Sugar strips", 20),
      )
      db.execSQL(
        "INSERT INTO transaction_items (transactionId, itemName, quantity) VALUES (?, ?, ?)",
        arrayOf(SEED_TRANSACTION_ID, "HB strip", 20),
      )
    }
  }
}
