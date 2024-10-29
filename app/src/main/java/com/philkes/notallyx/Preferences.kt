package com.philkes.notallyx

import android.app.Application
import android.os.Build
import android.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.philkes.notallyx.data.model.toPreservedByteArray
import com.philkes.notallyx.data.model.toPreservedString
import com.philkes.notallyx.presentation.view.misc.AutoBackup
import com.philkes.notallyx.presentation.view.misc.AutoBackupMax
import com.philkes.notallyx.presentation.view.misc.AutoBackupPeriodDays
import com.philkes.notallyx.presentation.view.misc.BackupPassword
import com.philkes.notallyx.presentation.view.misc.BetterLiveData
import com.philkes.notallyx.presentation.view.misc.BiometricLock
import com.philkes.notallyx.presentation.view.misc.DateFormat
import com.philkes.notallyx.presentation.view.misc.ListInfo
import com.philkes.notallyx.presentation.view.misc.ListItemSorting
import com.philkes.notallyx.presentation.view.misc.MaxItems
import com.philkes.notallyx.presentation.view.misc.MaxLines
import com.philkes.notallyx.presentation.view.misc.MaxTitle
import com.philkes.notallyx.presentation.view.misc.NotesSorting
import com.philkes.notallyx.presentation.view.misc.SeekbarInfo
import com.philkes.notallyx.presentation.view.misc.SortDirection
import com.philkes.notallyx.presentation.view.misc.TextInfo
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.presentation.view.misc.Theme
import com.philkes.notallyx.presentation.view.misc.View
import java.security.SecureRandom
import javax.crypto.Cipher

private const val DATABASE_ENCRYPTION_KEY = "database_encryption_key"

private const val ENCRYPTION_IV = "encryption_iv"

/**
 * Custom implementation of androidx.preference library Way faster, simpler and smaller, logic of
 * storing preferences has been decoupled from their UI. It is backed by SharedPreferences but it
 * should be trivial to shift to another source if needed.
 */
class Preferences private constructor(app: Application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(app)
    private val editor = preferences.edit()

    private val encryptedPreferences by lazy {
        EncryptedSharedPreferences.create(
            app,
            "secret_shared_prefs",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Main thread (unfortunately)
    val view = BetterLiveData(getListPref(View))
    val theme = BetterLiveData(getListPref(Theme))
    val dateFormat = BetterLiveData(getListPref(DateFormat))

    val notesSorting = BetterLiveData(getNotesSorting(NotesSorting))

    val textSize = BetterLiveData(getListPref(TextSize))
    val listItemSorting = BetterLiveData(getListPref(ListItemSorting))
    var maxItems = getSeekbarPref(MaxItems)
    var maxLines = getSeekbarPref(MaxLines)
    var maxTitle = getSeekbarPref(MaxTitle)

    val autoBackupPath = BetterLiveData(getTextPref(AutoBackup))
    var autoBackupPeriodDays = BetterLiveData(getSeekbarPref(AutoBackupPeriodDays))
    var autoBackupMax = getSeekbarPref(AutoBackupMax)
    val backupPassword by lazy { BetterLiveData(getEncryptedTextPref(BackupPassword)) }

    val biometricLock = BetterLiveData(getListPref(BiometricLock))
    var iv: ByteArray?
        get() = preferences.getString(ENCRYPTION_IV, null)?.toPreservedByteArray
        set(value) {
            editor.putString(ENCRYPTION_IV, value?.toPreservedString)
            editor.commit()
        }

    fun getDatabasePassphrase(): ByteArray {
        val string = preferences.getString(DATABASE_ENCRYPTION_KEY, "")!!
        return string.toPreservedByteArray
    }

    fun generatePassphrase(cipher: Cipher): ByteArray {
        val random =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SecureRandom.getInstanceStrong()
            } else {
                SecureRandom()
            }
        val result = ByteArray(64)

        random.nextBytes(result)

        // filter out zero byte values, as SQLCipher does not like them
        while (result.contains(0)) {
            random.nextBytes(result)
        }

        val encryptedPassphrase = cipher.doFinal(result)
        editor.putString(DATABASE_ENCRYPTION_KEY, encryptedPassphrase.toPreservedString)
        editor.commit()
        return result
    }

    private fun getListPref(info: ListInfo) =
        requireNotNull(preferences.getString(info.key, info.defaultValue))

    private fun getNotesSorting(info: NotesSorting): Pair<String, SortDirection> {
        val sortBy = requireNotNull(preferences.getString(info.key, info.defaultValue))
        val sortDirection =
            requireNotNull(preferences.getString(info.directionKey, info.defaultValueDirection))
        return Pair(sortBy, SortDirection.valueOf(sortDirection))
    }

    private fun getTextPref(info: TextInfo) =
        requireNotNull(preferences.getString(info.key, info.defaultValue))

    private fun getEncryptedTextPref(info: TextInfo) =
        requireNotNull(encryptedPreferences!!.getString(info.key, info.defaultValue))

    private fun getSeekbarPref(info: SeekbarInfo) =
        requireNotNull(preferences.getInt(info.key, info.defaultValue))

    fun getWidgetData(id: Int) = preferences.getLong("widget:$id", 0)

    fun deleteWidget(id: Int) {
        editor.remove("widget:$id")
        editor.commit()
    }

    fun updateWidget(id: Int, noteId: Long) {
        editor.putLong("widget:$id", noteId)
        editor.commit()
    }

    fun getUpdatableWidgets(noteIds: LongArray): List<Pair<Int, Long>> {
        val updatableWidgets = ArrayList<Pair<Int, Long>>()
        val pairs = preferences.all
        pairs.keys.forEach { key ->
            val token = "widget:"
            if (key.startsWith(token)) {
                val end = key.substringAfter(token)
                val id = end.toIntOrNull()
                if (id != null) {
                    val value = pairs[key] as? Long
                    if (value != null) {
                        if (noteIds.contains(value)) {
                            updatableWidgets.add(Pair(id, value))
                        }
                    }
                }
            }
        }
        return updatableWidgets
    }

    fun savePreference(info: SeekbarInfo, value: Int) {
        editor.putInt(info.key, value)
        editor.commit()
        when (info) {
            MaxItems -> maxItems = getSeekbarPref(MaxItems)
            MaxLines -> maxLines = getSeekbarPref(MaxLines)
            MaxTitle -> maxTitle = getSeekbarPref(MaxTitle)
            AutoBackupMax -> autoBackupMax = getSeekbarPref(AutoBackupMax)
            AutoBackupPeriodDays ->
                autoBackupPeriodDays.postValue(getSeekbarPref(AutoBackupPeriodDays))
        }
    }

    fun savePreference(info: NotesSorting, sortBy: String, sortDirection: SortDirection) {
        editor.putString(info.key, sortBy)
        editor.putString(info.directionKey, sortDirection.name)
        editor.commit()
        notesSorting.postValue(getNotesSorting(info))
    }

    fun savePreference(info: ListInfo, value: String) {
        editor.putString(info.key, value)
        editor.commit()
        when (info) {
            View -> view.postValue(getListPref(info))
            Theme -> theme.postValue(getListPref(info))
            DateFormat -> dateFormat.postValue(getListPref(info))
            TextSize -> textSize.postValue(getListPref(info))
            ListItemSorting -> listItemSorting.postValue(getListPref(info))
            BiometricLock -> biometricLock.postValue(getListPref(info))
            else -> return
        }
    }

    fun savePreference(info: TextInfo, value: String) {
        val editor = if (info is BackupPassword) encryptedPreferences!!.edit() else this.editor
        editor.putString(info.key, value)
        editor.commit()
        when (info) {
            AutoBackup -> autoBackupPath.postValue(getTextPref(info))
            BackupPassword -> backupPassword.postValue(getEncryptedTextPref(info))
        }
    }

    fun showDateCreated(): Boolean {
        return dateFormat.value != DateFormat.none
    }

    companion object {

        @Volatile private var instance: Preferences? = null

        fun getInstance(app: Application): Preferences {
            return instance
                ?: synchronized(this) {
                    val instance = Preferences(app)
                    Companion.instance = instance
                    return instance
                }
        }
    }
}
