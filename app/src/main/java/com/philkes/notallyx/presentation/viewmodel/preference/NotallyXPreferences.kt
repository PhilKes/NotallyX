package com.philkes.notallyx.presentation.viewmodel.preference

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY

class NotallyXPreferences private constructor(app: Application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(app)

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
    val theme = createEnumPreference(preferences, "theme", Theme.FOLLOW_SYSTEM, R.string.theme)
    val textSize =
        createEnumPreference(preferences, "textSize", TextSize.MEDIUM, R.string.text_size)
    val dateFormat =
        createEnumPreference(preferences, "dateFormat", DateFormat.RELATIVE, R.string.date_format)

    val notesView = createEnumPreference(preferences, "view", NotesView.LIST, R.string.view)
    val notesSorting = NotesSortPreference(preferences)
    val listItemSorting =
        createEnumPreference(
            preferences,
            "checkedListItemSorting",
            ListItemSort.NO_AUTO_SORT,
            R.string.checked_list_item_sorting,
        )

    val maxItems =
        IntPreference(
            "maxItemsToDisplayInList.v1",
            preferences,
            4,
            1,
            10,
            R.string.max_items_to_display,
        )
    val maxLines =
        IntPreference(
            "maxLinesToDisplayInNote.v1",
            preferences,
            8,
            1,
            10,
            R.string.max_lines_to_display,
        )
    val maxTitle =
        IntPreference(
            "maxLinesToDisplayInTitle",
            preferences,
            1,
            1,
            10,
            R.string.max_lines_to_display_title,
        )
    val labelsHiddenInNavigation =
        StringSetPreference("labelsHiddenInNavigation", preferences, setOf())
    val maxLabels =
        IntPreference(
            "maxLabelsInNavigation",
            preferences,
            5,
            1,
            20,
            R.string.max_labels_to_display,
        )

    val autoBackup = AutoBackupPreference(preferences)

    val backupPassword by lazy {
        StringPreference(
            "backupPassword",
            encryptedPreferences,
            PASSWORD_EMPTY,
            R.string.backup_password,
        )
    }

    val biometricLock =
        createEnumPreference(
            preferences,
            "biometricLock",
            BiometricLock.DISABLED,
            R.string.biometric_lock,
        )

    val iv = ByteArrayPreference("encryption_iv", preferences, null)
    val databaseEncryptionKey =
        EncryptedPassphrasePreference("database_encryption_key", preferences, ByteArray(0))

    val dataOnExternalStorage =
        BooleanPreference("dataOnExternalStorage", preferences, false, R.string.data_on_external)

    fun getWidgetData(id: Int) = preferences.getLong("widget:$id", 0)

    fun getWidgetNoteType(id: Int) =
        preferences.getString("widgetNoteType:$id", null)?.let { Type.valueOf(it) }

    fun deleteWidget(id: Int) {
        preferences.edit(true) {
            remove("widget:$id")
            remove("widgetNoteType:$id")
        }
    }

    fun updateWidget(id: Int, noteId: Long, noteType: Type) {
        preferences.edit(true) {
            putLong("widget:$id", noteId)
            putString("widgetNoteType:$id", noteType.name)
            commit()
        }
    }

    fun getUpdatableWidgets(noteIds: LongArray? = null): List<Pair<Int, Long>> {
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
                        if (noteIds == null || noteIds.contains(value)) {
                            updatableWidgets.add(Pair(id, value))
                        }
                    }
                }
            }
        }
        return updatableWidgets
    }

    fun showDateCreated(): Boolean {
        return dateFormat.value != DateFormat.NONE
    }

    companion object {
        const val EMPTY_PATH = "emptyPath"

        @Volatile private var instance: NotallyXPreferences? = null

        fun getInstance(app: Application): NotallyXPreferences {
            return instance
                ?: synchronized(this) {
                    val instance = NotallyXPreferences(app)
                    Companion.instance = instance
                    return instance
                }
        }
    }
}
