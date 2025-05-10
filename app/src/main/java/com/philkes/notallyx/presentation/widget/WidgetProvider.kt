package com.philkes.notallyx.presentation.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.activity.ConfigureWidgetActivity
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getContrastFontColor
import com.philkes.notallyx.presentation.view.note.listitem.findChildrenPositions
import com.philkes.notallyx.presentation.view.note.listitem.findParentPosition
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.utils.embedIntentExtras
import com.philkes.notallyx.utils.getOpenNotePendingIntent
import com.philkes.notallyx.utils.isSystemInDarkMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NOTES_MODIFIED -> {
                val app = context.applicationContext as NotallyXApplication
                val noteIds = intent.getLongArrayExtra(EXTRA_MODIFIED_NOTES)
                if (noteIds != null) {
                    updateWidgets(context, noteIds, locked = app.locked.value)
                }
            }
            ACTION_OPEN_NOTE -> openActivity(context, intent, EditNoteActivity::class.java)
            ACTION_OPEN_LIST -> openActivity(context, intent, EditListActivity::class.java)
            ACTION_CHECKED_CHANGED -> checkChanged(intent, context)
            ACTION_SELECT_NOTE -> openActivity(context, intent, ConfigureWidgetActivity::class.java)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun checkChanged(intent: Intent, context: Context) {
        val noteId = intent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0)
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        var checked =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
            } else null
        val database =
            NotallyDatabase.getDatabase(
                    context.applicationContext as Application,
                    observePreferences = false,
                )
                .value
        val pendingResult = goAsync()
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val baseNoteDao = database.getBaseNoteDao()
                    val note = baseNoteDao.get(noteId)!!
                    val item = note.items[position]
                    if (checked == null) {
                        checked = !item.checked
                    }
                    if (item.isChild) {
                        changeChildChecked(note, position, checked!!, baseNoteDao, noteId)
                    } else {
                        val childrenPositions = note.items.findChildrenPositions(position)
                        baseNoteDao.updateChecked(noteId, childrenPositions + position, checked!!)
                    }
                } finally {
                    val app = context.applicationContext as NotallyXApplication
                    updateWidgets(context, longArrayOf(noteId), locked = app.locked.value)
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun changeChildChecked(
        note: BaseNote,
        childPosition: Int,
        checked: Boolean,
        baseNoteDao: BaseNoteDao,
        noteId: Long,
    ) {
        val parentPosition = note.items.findParentPosition(childPosition)!!
        val parent = note.items[parentPosition]
        val childrenPositions = note.items.findChildrenPositions(parentPosition)
        if (parent.checked != checked) {
            if (checked) {
                // If the last unchecked child is being checked also check parent
                if (childrenPositions.none { !note.items[it].checked && it != childPosition }) {
                    baseNoteDao.updateChecked(noteId, listOf(childPosition, parentPosition), true)
                } else {
                    baseNoteDao.updateChecked(noteId, childPosition, true)
                }
            } else {
                if (parent.checked) {
                    // If any child is unchecked the parent is unchecked too
                    baseNoteDao.updateChecked(noteId, listOf(childPosition, parentPosition), false)
                } else {
                    baseNoteDao.updateChecked(noteId, childPosition, false)
                }
            }
        } else {
            baseNoteDao.updateChecked(noteId, childPosition, false)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as Application
        val preferences = NotallyXPreferences.getInstance(app)

        appWidgetIds.forEach { id -> preferences.deleteWidget(id) }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.applicationContext as NotallyXApplication
        val preferences = NotallyXPreferences.getInstance(app)

        appWidgetIds.forEach { id ->
            val noteId = preferences.getWidgetData(id)
            val noteType = preferences.getWidgetNoteType(id) ?: return
            updateWidget(app, appWidgetManager, id, noteId, noteType, locked = app.locked.value)
        }
    }

    companion object {

        fun updateWidgets(context: Context, noteIds: LongArray? = null, locked: Boolean) {
            val app = context.applicationContext as Application
            val preferences = NotallyXPreferences.getInstance(app)

            val manager = AppWidgetManager.getInstance(context)
            val updatableWidgets = preferences.getUpdatableWidgets(noteIds)

            updatableWidgets.forEach { (id, noteId) ->
                updateWidget(
                    app,
                    manager,
                    id,
                    noteId,
                    preferences.getWidgetNoteType(id),
                    locked = locked,
                )
            }
        }

        fun updateWidget(
            context: ContextWrapper,
            manager: AppWidgetManager,
            id: Int,
            noteId: Long,
            noteType: Type?,
            locked: Boolean = false,
        ) {
            // Widgets displaying the same note share the same factory since only the noteId is
            // embedded
            val intent = Intent(context, WidgetService::class.java)
            intent.putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            intent.embedIntentExtras()

            MainScope().launch {
                val database = NotallyDatabase.getDatabase(context).value
                val color =
                    withContext(Dispatchers.IO) { database.getBaseNoteDao().getColorOfNote(noteId) }
                if (color == null) {
                    val app = context.applicationContext as Application
                    val preferences = NotallyXPreferences.getInstance(app)
                    preferences.deleteWidget(id)
                    val view =
                        RemoteViews(context.packageName, R.layout.widget).apply {
                            setRemoteAdapter(R.id.ListView, intent)
                            setEmptyView(R.id.ListView, R.id.Empty)
                            setOnClickPendingIntent(
                                R.id.Empty,
                                Intent(context, WidgetProvider::class.java)
                                    .apply {
                                        action = ACTION_SELECT_NOTE
                                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                                    }
                                    .asPendingIntent(context),
                            )
                            setPendingIntentTemplate(
                                R.id.ListView,
                                Intent(context, WidgetProvider::class.java).asPendingIntent(context),
                            )
                        }

                    manager.updateAppWidget(id, view)
                    manager.notifyAppWidgetViewDataChanged(id, R.id.ListView)
                    return@launch
                }
                if (!locked) {
                    val view =
                        RemoteViews(context.packageName, R.layout.widget).apply {
                            setRemoteAdapter(R.id.ListView, intent)
                            setEmptyView(R.id.ListView, R.id.Empty)
                            setOnClickPendingIntent(
                                R.id.Empty,
                                Intent(context, WidgetProvider::class.java)
                                    .apply {
                                        action = ACTION_SELECT_NOTE
                                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                                    }
                                    .asPendingIntent(context),
                            )
                            setPendingIntentTemplate(
                                R.id.ListView,
                                Intent(context, WidgetProvider::class.java).asPendingIntent(context),
                            )

                            val preferences = NotallyXPreferences.getInstance(context)
                            val (backgroundColor, _) =
                                context.extractWidgetColors(color, preferences)
                            noteType?.let {
                                setOnClickPendingIntent(
                                    R.id.Layout,
                                    Intent(context, WidgetProvider::class.java)
                                        .setOpenNoteIntent(noteType, noteId)
                                        .asPendingIntent(context),
                                )
                            }
                            setInt(R.id.Layout, "setBackgroundColor", backgroundColor)
                        }
                    manager.updateAppWidget(id, view)
                    manager.notifyAppWidgetViewDataChanged(id, R.id.ListView)
                } else {
                    val view =
                        RemoteViews(context.packageName, R.layout.widget_locked).apply {
                            noteType?.let {
                                val lockedPendingIntent =
                                    context.getOpenNotePendingIntent(noteId, noteType)
                                setOnClickPendingIntent(R.id.Layout, lockedPendingIntent)
                                setOnClickPendingIntent(R.id.Text, lockedPendingIntent)
                            }
                            setTextViewCompoundDrawablesRelative(
                                R.id.Text,
                                0,
                                R.drawable.lock_big,
                                0,
                                0,
                            )
                        }
                    manager.updateAppWidget(id, view)
                }
            }
        }

        fun getWidgetOpenNoteIntent(noteType: Type, noteId: Long): Intent {
            return Intent().setOpenNoteIntent(noteType, noteId)
        }

        fun getWidgetCheckedChangeIntent(listNoteId: Long, position: Int): Intent {
            return Intent().apply {
                action = ACTION_CHECKED_CHANGED
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_SELECTED_BASE_NOTE, listNoteId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
        }

        private fun Intent.setOpenNoteIntent(noteType: Type, noteId: Long) = apply {
            action =
                when (noteType) {
                    Type.LIST -> ACTION_OPEN_LIST
                    Type.NOTE -> ACTION_OPEN_NOTE
                }
            putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        private fun Intent.asPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        fun Context.extractWidgetColors(
            color: String,
            preferences: NotallyXPreferences,
        ): Pair<Int, Int> {
            val backgroundColor =
                if (color == BaseNote.COLOR_DEFAULT) {
                    val id =
                        when (preferences.theme.value) {
                            Theme.DARK -> R.color.md_theme_surface_dark
                            Theme.LIGHT -> R.color.md_theme_surface
                            Theme.FOLLOW_SYSTEM -> {
                                if (isSystemInDarkMode()) R.color.md_theme_surface_dark
                                else R.color.md_theme_surface
                            }
                        }
                    ContextCompat.getColor(this, id)
                } else extractColor(color)
            return Pair(backgroundColor, getContrastFontColor(backgroundColor))
        }

        private fun openActivity(context: Context, originalIntent: Intent, clazz: Class<*>) {
            val id = originalIntent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0)
            val widgetId = originalIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            context.startActivity(createIntent(context, clazz, id, widgetId, originalIntent))
        }

        private fun createIntent(
            context: Context,
            clazz: Class<*>,
            noteId: Long,
            widgetId: Int,
            originalIntent: Intent? = null,
        ): Intent {
            val intent = Intent(context, clazz)
            intent.putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            originalIntent?.let { intent.data = it.data }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent
        }

        fun sendBroadcast(context: Context, ids: LongArray) =
            Intent(context, WidgetProvider::class.java).apply {
                action = ACTION_NOTES_MODIFIED
                putExtra(EXTRA_MODIFIED_NOTES, ids)
                context.sendBroadcast(this)
            }

        fun getWidgetSelectNoteIntent(id: Int) =
            Intent(ACTION_SELECT_NOTE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

        private const val EXTRA_MODIFIED_NOTES = "com.philkes.notallyx.EXTRA_MODIFIED_NOTES"
        private const val ACTION_NOTES_MODIFIED = "com.philkes.notallyx.ACTION_NOTE_MODIFIED"

        const val ACTION_OPEN_NOTE = "com.philkes.notallyx.ACTION_OPEN_NOTE"
        const val ACTION_OPEN_LIST = "com.philkes.notallyx.ACTION_OPEN_LIST"
        const val ACTION_SELECT_NOTE = "com.philkes.notallyx.ACTION_SELECT_NOTE"

        const val ACTION_CHECKED_CHANGED = "com.philkes.notallyx.ACTION_CHECKED_CHANGED"
        const val EXTRA_POSITION = "notallyx.intent.extra.com.philkes.notallyx.EXTRA_POSITION"
    }
}
