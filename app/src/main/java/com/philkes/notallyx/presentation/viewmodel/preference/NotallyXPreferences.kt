package com.philkes.notallyx.presentation.viewmodel.preference

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.utils.backup.importPreferences
import com.philkes.notallyx.utils.toCamelCase
import org.json.JSONArray
import org.json.JSONObject

class NotallyXPreferences private constructor(private val context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val encryptedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val theme = createEnumPreference(preferences, "theme", Theme.FOLLOW_SYSTEM, R.string.theme)
    val textSize =
        createEnumPreference(preferences, "textSize", TextSize.MEDIUM, R.string.text_size)
    val dateFormat =
        createEnumPreference(preferences, "dateFormat", DateFormat.RELATIVE, R.string.date_format)
    val applyDateFormatInNoteView =
        BooleanPreference("applyDateFormatInNoteView", preferences, true)

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
            0,
            10,
            R.string.max_items_to_display,
        )
    val maxLines =
        IntPreference(
            "maxLinesToDisplayInNote.v1",
            preferences,
            8,
            0,
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
    val labelsHiddenInOverview =
        BooleanPreference(
            "labelsHiddenInOverview",
            preferences,
            false,
            R.string.labels_hidden_in_overview_title,
        )
    val maxLabels =
        IntPreference(
            "maxLabelsInNavigation",
            preferences,
            5,
            1,
            20,
            R.string.max_labels_to_display,
        )

    val backupsFolder =
        StringPreference("autoBackup", preferences, EMPTY_PATH, R.string.auto_backups_folder)
    val backupOnSave =
        BooleanPreference("backupOnSave", preferences, false, R.string.auto_backup_on_save)
    val periodicBackups = PeriodicBackupsPreference(preferences)
    val periodicBackupLastExecution =
        LongPreference("periodicBackupLastExecution", preferences, -1L)

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
    val fallbackDatabaseEncryptionKey by lazy {
        ByteArrayPreference("fallback_database_encryption_key", encryptedPreferences, ByteArray(0))
    }

    val dataInPublicFolder =
        BooleanPreference("dataOnExternalStorage", preferences, false, R.string.data_in_public)

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

    fun toJsonString(): String {
        val jsonObject = JSONObject()
        for ((key, value) in preferences.all) {
            if (key in listOf(biometricLock.key, iv.key, databaseEncryptionKey.key)) {
                continue
            }
            when (value) {
                is Collection<*> -> jsonObject.put(key, JSONArray(value))
                is Enum<*> -> jsonObject.put(key, value.name.toCamelCase())
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject.toString(4)
    }

    fun import(context: Context, uri: Uri) =
        context.importPreferences(uri, preferences.edit()).also { reload() }

    fun reset() {
        preferences.edit().clear().apply()
        encryptedPreferences.edit().clear().apply()
        reload()
    }

    private fun reload() {
        setOf(
                theme,
                textSize,
                dateFormat,
                applyDateFormatInNoteView,
                notesView,
                notesSorting,
                listItemSorting,
                maxItems,
                maxLines,
                maxTitle,
                labelsHiddenInNavigation,
                labelsHiddenInOverview,
                maxLabels,
                periodicBackups,
                backupPassword,
                biometricLock,
            )
            .forEach { it.refresh() }
    }

    companion object {
        private const val TAG = "NotallyXPreferences"
        const val EMPTY_PATH = "emptyPath"

        @Volatile private var instance: NotallyXPreferences? = null

        fun getInstance(context: Context): NotallyXPreferences {
            return instance
                ?: synchronized(this) {
                    val instance = NotallyXPreferences(context)
                    Companion.instance = instance
                    return instance
                }
        }
    }
}
