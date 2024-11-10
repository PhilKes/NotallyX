package com.philkes.notallyx.presentation.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.findChildrenPositions
import com.philkes.notallyx.data.model.findParentPosition
import com.philkes.notallyx.presentation.activity.ConfigureWidgetActivity
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetProvider : AppWidgetProvider() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_NOTES_MODIFIED -> {
                val noteIds = intent.getLongArrayExtra(EXTRA_MODIFIED_NOTES)
                if (noteIds != null) {
                    updateWidgets(context, noteIds)
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
        val noteId = intent.getLongExtra(Constants.SelectedBaseNote, 0)
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        val checked =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
            } else false
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
                    if (item.isChild) {
                        changeChildChecked(note, position, checked, baseNoteDao, noteId)
                    } else {
                        val childrenPositions = note.items.findChildrenPositions(position)
                        baseNoteDao.updateChecked(noteId, childrenPositions + position, checked)
                    }
                } finally {
                    updateWidgets(context, longArrayOf(noteId))
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
        val app = context.applicationContext as Application
        val preferences = NotallyXPreferences.getInstance(app)

        appWidgetIds.forEach { id ->
            val noteId = preferences.getWidgetData(id)
            val noteType = preferences.getWidgetNoteType(id)
            updateWidget(context, appWidgetManager, id, noteId, noteType)
        }
    }

    companion object {

        fun updateWidgets(context: Context, noteIds: LongArray? = null, locked: Boolean = false) {
            val app = context.applicationContext as Application
            val preferences = NotallyXPreferences.getInstance(app)

            val manager = AppWidgetManager.getInstance(context)
            val updatableWidgets = preferences.getUpdatableWidgets(noteIds)

            updatableWidgets.forEach { (id, noteId) ->
                updateWidget(
                    context,
                    manager,
                    id,
                    noteId,
                    preferences.getWidgetNoteType(id),
                    locked = locked,
                )
            }
        }

        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            noteId: Long,
            noteType: Type?,
            locked: Boolean = false,
        ) {
            // Widgets displaying the same note share the same factory since only the noteId is
            // embedded
            val intent = Intent(context, WidgetService::class.java)
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            embedIntentExtras(intent)

            val view =
                if (!locked) {
                    RemoteViews(context.packageName, R.layout.widget).apply {
                        setRemoteAdapter(R.id.ListView, intent)
                        setEmptyView(R.id.ListView, R.id.Empty)
                        setOnClickFillInIntent(R.id.Empty, getSelectNoteIntent(id))
                        setPendingIntentTemplate(R.id.ListView, getOpenNoteIntent(context, noteId))
                    }
                } else {
                    RemoteViews(context.packageName, R.layout.widget_locked).apply {
                        noteType?.let {
                            val openNoteIntent =
                                when (it) {
                                    Type.NOTE -> Intent(context, EditNoteActivity::class.java)
                                    Type.LIST -> Intent(context, EditListActivity::class.java)
                                }.apply {
                                    putExtra(Constants.SelectedBaseNote, noteId)
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    )
                                }
                            val lockedPendingIntent =
                                PendingIntent.getActivity(
                                    context,
                                    0,
                                    openNoteIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or
                                        PendingIntent.FLAG_IMMUTABLE,
                                )
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
                }
            manager.updateAppWidget(id, view)
            if (!locked) {
                manager.notifyAppWidgetViewDataChanged(id, R.id.ListView)
            }
        }

        private fun openActivity(context: Context, originalIntent: Intent, clazz: Class<*>) {
            val id = originalIntent.getLongExtra(Constants.SelectedBaseNote, 0)
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
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            originalIntent?.let { intent.data = it.data }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent
        }

        fun sendBroadcast(app: Application, ids: LongArray) {
            val intent = Intent(app, WidgetProvider::class.java)
            intent.action = ACTION_NOTES_MODIFIED
            intent.putExtra(EXTRA_MODIFIED_NOTES, ids)
            app.sendBroadcast(intent)
        }

        fun getSelectNoteIntent(id: Int): Intent {
            return Intent(ACTION_SELECT_NOTE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
        }

        private fun getOpenNoteIntent(context: Context, noteId: Long): PendingIntent {
            val intent = Intent(context, WidgetProvider::class.java)
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            embedIntentExtras(intent)
            val flags =
                PendingIntent.FLAG_MUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT or
                    Intent.FILL_IN_ACTION
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }

        private fun embedIntentExtras(intent: Intent) {
            val string = intent.toUri(Intent.URI_INTENT_SCHEME)
            intent.data = Uri.parse(string)
        }

        private const val EXTRA_MODIFIED_NOTES = "com.philkes.notallyx.EXTRA_MODIFIED_NOTES"
        private const val ACTION_NOTES_MODIFIED = "com.philkes.notallyx.ACTION_NOTE_MODIFIED"

        const val ACTION_OPEN_NOTE = "com.philkes.notallyx.ACTION_OPEN_NOTE"
        const val ACTION_OPEN_LIST = "com.philkes.notallyx.ACTION_OPEN_LIST"
        const val ACTION_SELECT_NOTE = "com.philkes.notallyx.ACTION_SELECT_NOTE"

        const val ACTION_CHECKED_CHANGED = "com.philkes.notallyx.ACTION_CHECKED_CHANGED"
        const val EXTRA_POSITION = "com.philkes.notallyx.EXTRA_POSITION"
    }
}
