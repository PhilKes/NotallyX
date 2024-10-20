package com.philkes.notallyx.data

import android.app.Application
import android.os.Build
import androidx.lifecycle.Observer
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.dao.CommonDao
import com.philkes.notallyx.data.dao.LabelDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.presentation.view.misc.BetterLiveData
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.utils.observeForeverSkipFirst
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import net.sqlcipher.database.SupportFactory

@TypeConverters(Converters::class)
@Database(entities = [BaseNote::class, Label::class], version = 6)
abstract class NotallyDatabase : RoomDatabase() {

    abstract fun getLabelDao(): LabelDao

    abstract fun getCommonDao(): CommonDao

    abstract fun getBaseNoteDao(): BaseNoteDao

    fun checkpoint() {
        getBaseNoteDao().query(SimpleSQLiteQuery("pragma wal_checkpoint(FULL)"))
    }

    private var observer: Observer<String>? = null

    companion object {

        const val DatabaseName = "NotallyDatabase"

        @Volatile private var instance: BetterLiveData<NotallyDatabase>? = null

        fun getDatabase(app: Application): BetterLiveData<NotallyDatabase> {
            return instance
                ?: synchronized(this) {
                    val preferences = Preferences.getInstance(app)
                    this.instance =
                        BetterLiveData(
                            createInstance(app, preferences, preferences.biometricLock.value)
                        )
                    return this.instance!!
                }
        }

        private fun createInstance(
            app: Application,
            preferences: Preferences,
            biometrickLock: String,
        ): NotallyDatabase {
            val instanceBuilder =
                Room.databaseBuilder(app, NotallyDatabase::class.java, DatabaseName)
                    .addMigrations(Migration2, Migration3, Migration4, Migration5, Migration6)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (biometrickLock == enabled) {
                    val initializationVector = preferences.iv!!
                    val cipher = getInitializedCipherForDecryption(iv = initializationVector)
                    val encryptedPassphrase = preferences.getDatabasePassphrase()
                    val passphrase = cipher.doFinal(encryptedPassphrase)
                    val factory = SupportFactory(passphrase)
                    instanceBuilder.openHelperFactory(factory)
                }
                val instance = instanceBuilder.build()
                instance.observer = Observer { newBiometrickLock ->
                    NotallyDatabase.instance?.value?.observer?.let {
                        preferences.biometricLock.removeObserver(it)
                    }
                    val newInstance = createInstance(app, preferences, newBiometrickLock)
                    NotallyDatabase.instance?.postValue(newInstance)
                    preferences.biometricLock.observeForeverSkipFirst(newInstance.observer!!)
                }
                preferences.biometricLock.observeForeverSkipFirst(instance.observer!!)
                return instance
            }
            return instanceBuilder.build()
        }

        object Migration2 : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `color` TEXT NOT NULL DEFAULT 'DEFAULT'"
                )
            }
        }

        object Migration3 : Migration(2, 3) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `images` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration4 : Migration(3, 4) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `audios` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration5 : Migration(4, 5) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `files` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration6 : Migration(5, 6) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `modifiedTimestamp` INTEGER NOT NULL DEFAULT 'timestamp'"
                )
            }
        }
    }
}
