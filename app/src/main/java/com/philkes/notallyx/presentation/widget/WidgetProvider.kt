package com.philkes.notallyx.presentation.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.findChildrenPositions
import com.philkes.notallyx.data.model.findParentPosition
import com.philkes.notallyx.presentation.activity.ConfigureWidgetActivity
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.view.Constants
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
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun checkChanged(intent: Intent, context: Context) {
        val noteId = intent.getLongExtra(Constants.SelectedBaseNote, 0)
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
        val database = NotallyDatabase.getDatabase(context.applicationContext as Application)
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

    private fun updateWidgets(context: Context, noteIds: LongArray) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        val manager = AppWidgetManager.getInstance(context)
        val updatableWidgets = preferences.getUpdatableWidgets(noteIds)

        updatableWidgets.forEach { pair -> updateWidget(context, manager, pair.first, pair.second) }
    }

    private fun openActivity(context: Context, originalIntent: Intent, clazz: Class<*>) {
        val id = originalIntent.getLongExtra(Constants.SelectedBaseNote, 0)
        val intent = Intent(context, clazz)
        intent.putExtra(Constants.SelectedBaseNote, id)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        appWidgetIds.forEach { id -> preferences.deleteWidget(id) }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)

        appWidgetIds.forEach { id ->
            val noteId = preferences.getWidgetData(id)
            updateWidget(context, appWidgetManager, id, noteId)
        }
    }

    companion object {

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, noteId: Long) {
            // Widgets displaying the same note share the same factory since only the noteId is
            // embedded
            val intent = Intent(context, WidgetService::class.java)
            intent.putExtra(Constants.SelectedBaseNote, noteId)
            embedIntentExtras(intent)

            val view = RemoteViews(context.packageName, R.layout.widget)
            view.setRemoteAdapter(R.id.ListView, intent)
            view.setEmptyView(R.id.ListView, R.id.Empty)

            val selectNote = getSelectNoteIntent(context, id)
            view.setOnClickPendingIntent(R.id.Empty, selectNote)

            val openNote = getOpenNoteIntent(context, noteId)
            view.setPendingIntentTemplate(R.id.ListView, openNote)

            manager.updateAppWidget(id, view)
            manager.notifyAppWidgetViewDataChanged(id, R.id.ListView)
        }

        fun sendBroadcast(app: Application, ids: LongArray) {
            val intent = Intent(app, WidgetProvider::class.java)
            intent.action = ACTION_NOTES_MODIFIED
            intent.putExtra(EXTRA_MODIFIED_NOTES, ids)
            app.sendBroadcast(intent)
        }

        // Each widget has it's own intent since the widget id is embedded
        private fun getSelectNoteIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, ConfigureWidgetActivity::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            embedIntentExtras(intent)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, 0, intent, flags)
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

        const val ACTION_CHECKED_CHANGED = "com.philkes.notallyx.ACTION_CHECKED_CHANGED"
        const val EXTRA_POSITION = "com.philkes.notallyx.EXTRA_POSITION"
    }
}
