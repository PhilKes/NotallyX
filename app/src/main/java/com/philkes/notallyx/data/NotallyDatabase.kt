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
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.dao.CommonDao
import com.philkes.notallyx.data.dao.LabelDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.observeForeverSkipFirst
import com.philkes.notallyx.utils.IO.getExternalMediaDirectory
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
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

    private var biometricLockObserver: Observer<BiometricLock>? = null
    private var externalDataFolderObserver: Observer<Boolean>? = null

    companion object {

        const val DatabaseName = "NotallyDatabase"

        @Volatile private var instance: NotNullLiveData<NotallyDatabase>? = null

        fun getCurrentDatabaseFile(app: Application): File {
            return if (NotallyXPreferences.getInstance(app).dataOnExternalStorage.value) {
                getExternalDatabaseFile(app)
            } else {
                getInternalDatabaseFile(app)
            }
        }

        fun getExternalDatabaseFile(app: Application): File {
            return File(app.getExternalMediaDirectory(), DatabaseName)
        }

        fun getExternalDatabaseFiles(app: Application): List<File> {
            return listOf(
                File(app.getExternalMediaDirectory(), DatabaseName),
                File(app.getExternalMediaDirectory(), "$DatabaseName-shm"),
                File(app.getExternalMediaDirectory(), "$DatabaseName-wal"),
            )
        }

        fun getInternalDatabaseFile(app: Application): File {
            return app.getDatabasePath(DatabaseName)
        }

        fun getCurrentDatabaseName(app: Application): String {
            return if (NotallyXPreferences.getInstance(app).dataOnExternalStorage.value) {
                getExternalDatabaseFile(app).absolutePath
            } else {
                DatabaseName
            }
        }

        fun getDatabase(
            app: Application,
            observePreferences: Boolean = true,
        ): NotNullLiveData<NotallyDatabase> {
            return instance
                ?: synchronized(this) {
                    val preferences = NotallyXPreferences.getInstance(app)
                    this.instance =
                        NotNullLiveData(createInstance(app, preferences, observePreferences))
                    return this.instance!!
                }
        }

        private fun createInstance(
            app: Application,
            preferences: NotallyXPreferences,
            observePreferences: Boolean,
        ): NotallyDatabase {
            val instanceBuilder =
                Room.databaseBuilder(app, NotallyDatabase::class.java, getCurrentDatabaseName(app))
                    .addMigrations(Migration2, Migration3, Migration4, Migration5, Migration6)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (preferences.biometricLock.value == BiometricLock.ENABLED) {
                    val initializationVector = preferences.iv.value!!
                    val cipher = getInitializedCipherForDecryption(iv = initializationVector)
                    val encryptedPassphrase = preferences.databaseEncryptionKey.value
                    val passphrase = cipher.doFinal(encryptedPassphrase)
                    val factory = SupportFactory(passphrase)
                    instanceBuilder.openHelperFactory(factory)
                }
                val instance = instanceBuilder.build()
                if (observePreferences) {
                    instance.biometricLockObserver = Observer {
                        NotallyDatabase.instance?.value?.biometricLockObserver?.let {
                            preferences.biometricLock.removeObserver(it)
                        }
                        val newInstance = createInstance(app, preferences, true)
                        NotallyDatabase.instance?.postValue(newInstance)
                        preferences.biometricLock.observeForeverSkipFirst(
                            newInstance.biometricLockObserver!!
                        )
                    }
                    preferences.biometricLock.observeForeverSkipFirst(
                        instance.biometricLockObserver!!
                    )

                    instance.externalDataFolderObserver = Observer {
                        NotallyDatabase.instance?.value?.externalDataFolderObserver?.let {
                            preferences.dataOnExternalStorage.removeObserver(it)
                        }
                        val newInstance = createInstance(app, preferences, true)
                        NotallyDatabase.instance?.postValue(newInstance)
                        preferences.dataOnExternalStorage.observeForeverSkipFirst(
                            newInstance.externalDataFolderObserver!!
                        )
                    }
                    preferences.dataOnExternalStorage.observeForeverSkipFirst(
                        instance.externalDataFolderObserver!!
                    )
                }
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
