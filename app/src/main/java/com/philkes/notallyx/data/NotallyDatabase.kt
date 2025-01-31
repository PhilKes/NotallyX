package com.philkes.notallyx.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.RequiresApi
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
import com.philkes.notallyx.utils.getExternalMediaDirectory
import com.philkes.notallyx.utils.security.SQLCipherUtils
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@TypeConverters(Converters::class)
@Database(entities = [BaseNote::class, Label::class], version = 7)
abstract class NotallyDatabase : RoomDatabase() {

    abstract fun getLabelDao(): LabelDao

    abstract fun getCommonDao(): CommonDao

    abstract fun getBaseNoteDao(): BaseNoteDao

    fun checkpoint() {
        getBaseNoteDao().query(SimpleSQLiteQuery("pragma wal_checkpoint(FULL)"))
    }

    private var biometricLockObserver: Observer<BiometricLock>? = null
    private var dataInPublicFolderObserver: Observer<Boolean>? = null

    companion object {

        const val DATABASE_NAME = "NotallyDatabase"

        @Volatile private var instance: NotNullLiveData<NotallyDatabase>? = null

        fun getCurrentDatabaseFile(context: ContextWrapper): File {
            return if (NotallyXPreferences.getInstance(context).dataInPublicFolder.value) {
                getExternalDatabaseFile(context)
            } else {
                getInternalDatabaseFile(context)
            }
        }

        fun getExternalDatabaseFile(context: ContextWrapper): File {
            return File(context.getExternalMediaDirectory(), DATABASE_NAME)
        }

        fun getExternalDatabaseFiles(context: ContextWrapper): List<File> {
            return listOf(
                File(context.getExternalMediaDirectory(), DATABASE_NAME),
                File(context.getExternalMediaDirectory(), "$DATABASE_NAME-shm"),
                File(context.getExternalMediaDirectory(), "$DATABASE_NAME-wal"),
            )
        }

        fun getInternalDatabaseFile(context: Context): File {
            return context.getDatabasePath(DATABASE_NAME)
        }

        fun getInternalDatabaseFiles(context: ContextWrapper): List<File> {
            val directory = context.getDatabasePath(DATABASE_NAME).parentFile
            return listOf(
                File(directory, DATABASE_NAME),
                File(directory, "$DATABASE_NAME-shm"),
                File(directory, "$DATABASE_NAME-wal"),
            )
        }

        private fun getCurrentDatabaseName(context: ContextWrapper): String {
            return if (NotallyXPreferences.getInstance(context).dataInPublicFolder.value) {
                getExternalDatabaseFile(context).absolutePath
            } else {
                DATABASE_NAME
            }
        }

        fun getDatabase(
            context: ContextWrapper,
            observePreferences: Boolean = true,
        ): NotNullLiveData<NotallyDatabase> {
            return instance
                ?: synchronized(this) {
                    val preferences = NotallyXPreferences.getInstance(context)
                    this.instance =
                        NotNullLiveData(createInstance(context, preferences, observePreferences))
                    return this.instance!!
                }
        }

        fun getFreshDatabase(context: ContextWrapper): NotallyDatabase {
            return createInstance(context, NotallyXPreferences.getInstance(context), false)
        }

        private fun createInstance(
            context: ContextWrapper,
            preferences: NotallyXPreferences,
            observePreferences: Boolean,
        ): NotallyDatabase {
            val instanceBuilder =
                Room.databaseBuilder(
                        context,
                        NotallyDatabase::class.java,
                        getCurrentDatabaseName(context),
                    )
                    .addMigrations(
                        Migration2,
                        Migration3,
                        Migration4,
                        Migration5,
                        Migration6,
                        Migration7,
                    )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SQLiteDatabase.loadLibs(context)
                if (preferences.biometricLock.value == BiometricLock.ENABLED) {
                    if (
                        SQLCipherUtils.getDatabaseState(getCurrentDatabaseFile(context)) ==
                            SQLCipherUtils.State.ENCRYPTED
                    ) {
                        initializeDecryption(preferences, instanceBuilder)
                    } else {
                        preferences.biometricLock.save(BiometricLock.DISABLED)
                    }
                } else {
                    if (
                        SQLCipherUtils.getDatabaseState(getCurrentDatabaseFile(context)) ==
                            SQLCipherUtils.State.ENCRYPTED
                    ) {
                        preferences.biometricLock.save(BiometricLock.ENABLED)
                        initializeDecryption(preferences, instanceBuilder)
                    }
                }
                val instance = instanceBuilder.build()
                if (observePreferences) {
                    instance.biometricLockObserver = Observer {
                        NotallyDatabase.instance?.value?.biometricLockObserver?.let {
                            preferences.biometricLock.removeObserver(it)
                        }
                        val newInstance = createInstance(context, preferences, true)
                        NotallyDatabase.instance?.postValue(newInstance)
                        preferences.biometricLock.observeForeverSkipFirst(
                            newInstance.biometricLockObserver!!
                        )
                    }
                    preferences.biometricLock.observeForeverSkipFirst(
                        instance.biometricLockObserver!!
                    )

                    instance.dataInPublicFolderObserver = Observer {
                        NotallyDatabase.instance?.value?.dataInPublicFolderObserver?.let {
                            preferences.dataInPublicFolder.removeObserver(it)
                        }
                        val newInstance = createInstance(context, preferences, true)
                        NotallyDatabase.instance?.postValue(newInstance)
                        preferences.dataInPublicFolder.observeForeverSkipFirst(
                            newInstance.dataInPublicFolderObserver!!
                        )
                    }
                    preferences.dataInPublicFolder.observeForeverSkipFirst(
                        instance.dataInPublicFolderObserver!!
                    )
                }
                return instance
            }
            return instanceBuilder.build()
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun initializeDecryption(
            preferences: NotallyXPreferences,
            instanceBuilder: Builder<NotallyDatabase>,
        ) {
            val initializationVector = preferences.iv.value!!
            val cipher = getInitializedCipherForDecryption(iv = initializationVector)
            val encryptedPassphrase = preferences.databaseEncryptionKey.value
            val passphrase = cipher.doFinal(encryptedPassphrase)
            val factory = SupportFactory(passphrase)
            instanceBuilder.openHelperFactory(factory)
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

        object Migration7 : Migration(6, 7) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `reminders` TEXT NOT NULL DEFAULT `[]`"
                )
            }
        }
    }
}
