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
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.toColorString
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.observeForeverSkipFirst
import com.philkes.notallyx.utils.getExternalMediaDirectory
import com.philkes.notallyx.utils.security.SQLCipherUtils
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@TypeConverters(Converters::class)
@Database(entities = [BaseNote::class, Label::class], version = 9)
abstract class NotallyDatabase : RoomDatabase() {

    abstract fun getLabelDao(): LabelDao

    abstract fun getCommonDao(): CommonDao

    abstract fun getBaseNoteDao(): BaseNoteDao

    fun checkpoint() {
        getBaseNoteDao().query(SimpleSQLiteQuery("pragma wal_checkpoint(FULL)"))
    }

    fun ping(): Boolean =
        try {
            getBaseNoteDao().query(SimpleSQLiteQuery("SELECT 1")) == 1
        } catch (e: Exception) {
            false
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

        private fun getCurrentDatabaseName(
            context: ContextWrapper,
            dataInPublicFolder: Boolean,
        ): String {
            return if (dataInPublicFolder) {
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

        fun getFreshDatabase(context: ContextWrapper, dataInPublic: Boolean): NotallyDatabase {
            return createInstance(
                context,
                NotallyXPreferences.getInstance(context),
                false,
                dataInPublic = dataInPublic,
            )
        }

        private fun createInstance(
            context: ContextWrapper,
            preferences: NotallyXPreferences,
            observePreferences: Boolean,
            dataInPublic: Boolean = preferences.dataInPublicFolder.value,
        ): NotallyDatabase {
            val instanceBuilder =
                Room.databaseBuilder(
                        context,
                        NotallyDatabase::class.java,
                        getCurrentDatabaseName(context, dataInPublic),
                    )
                    .addMigrations(
                        Migration2,
                        Migration3,
                        Migration4,
                        Migration5,
                        Migration6,
                        Migration7,
                        Migration8,
                        Migration9,
                    )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                System.loadLibrary("sqlcipher")
                if (preferences.isLockEnabled) {
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
            val factory = SupportOpenHelperFactory(passphrase)
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

        object Migration8 : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("SELECT id, color FROM BaseNote")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val colorString = cursor.getString(cursor.getColumnIndexOrThrow("color"))
                    val color = Color.valueOfOrDefault(colorString)
                    val hexColor = color.toColorString()
                    db.execSQL("UPDATE BaseNote SET color = ? WHERE id = ?", arrayOf(hexColor, id))
                }
                cursor.close()
            }
        }

        object Migration9 : Migration(8, 9) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `viewMode` TEXT NOT NULL DEFAULT '${NoteViewMode.EDIT.name}'"
                )
            }
        }
    }
}
