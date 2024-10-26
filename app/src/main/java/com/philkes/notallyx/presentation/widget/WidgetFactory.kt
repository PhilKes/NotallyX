package com.philkes.notallyx.presentation.widget

import android.app.Application
import android.content.Intent
import android.graphics.Paint
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.presentation.widget.WidgetProvider.Companion.getSelectNoteIntent
import com.philkes.notallyx.utils.displayFormattedTimestamp

class WidgetFactory(private val app: Application, private val id: Long, private val widgetId: Int) :
    RemoteViewsService.RemoteViewsFactory {

    private var baseNote: BaseNote? = null
    private lateinit var database: NotallyDatabase
    private val preferences = Preferences.getInstance(app)

    init {
        NotallyDatabase.getDatabase(app).observeForever { database = it }
    }

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun getCount(): Int {
        val copy = baseNote
        return if (copy != null) {
            when (copy.type) {
                Type.NOTE -> 1
                Type.LIST -> 1 + copy.items.size
            }
        } else 0
    }

    override fun onDataSetChanged() {
        baseNote = database.getBaseNoteDao().get(id)
    }

    override fun getViewAt(position: Int): RemoteViews {
        val copy = baseNote
        requireNotNull(copy)

        return when (copy.type) {
            Type.NOTE -> getNoteView(copy)
            Type.LIST -> {
                if (position > 0) {
                    getListItemView(position - 1, copy)
                } else getListHeaderView(copy)
            }
        }
    }

    private fun getNoteView(note: BaseNote): RemoteViews {
        return RemoteViews(app.packageName, R.layout.widget_note).apply {
            setTextViewTextSize(
                R.id.Title,
                TypedValue.COMPLEX_UNIT_SP,
                TextSize.getDisplayTitleSize(preferences.textSize.value),
            )
            setTextViewText(R.id.Title, note.title)

            val bodyTextSize = TextSize.getDisplayBodySize(preferences.textSize.value)

            setTextViewTextSize(R.id.Date, TypedValue.COMPLEX_UNIT_SP, bodyTextSize)
            displayFormattedTimestamp(R.id.Date, note.timestamp, preferences.dateFormat.value)

            setTextViewTextSize(R.id.Note, TypedValue.COMPLEX_UNIT_SP, bodyTextSize)
            if (note.body.isNotEmpty()) {
                setTextViewText(R.id.Note, note.body)
                setViewVisibility(R.id.Note, View.VISIBLE)
            } else setViewVisibility(R.id.Note, View.GONE)

            val intent = Intent(WidgetProvider.ACTION_OPEN_NOTE)
            setOnClickFillInIntent(R.id.LinearLayout, intent)

            setOnClickFillInIntent(R.id.ChangeNote, getSelectNoteIntent(widgetId))
        }
    }

    private fun getListHeaderView(list: BaseNote): RemoteViews {
        return RemoteViews(app.packageName, R.layout.widget_list_header).apply {
            setTextViewTextSize(
                R.id.Title,
                TypedValue.COMPLEX_UNIT_SP,
                TextSize.getDisplayTitleSize(preferences.textSize.value),
            )
            setTextViewText(R.id.Title, list.title)

            val bodyTextSize = TextSize.getDisplayBodySize(preferences.textSize.value)

            setTextViewTextSize(R.id.Date, TypedValue.COMPLEX_UNIT_SP, bodyTextSize)
            displayFormattedTimestamp(R.id.Date, list.timestamp, preferences.dateFormat.value)

            val intent = Intent(WidgetProvider.ACTION_OPEN_LIST)
            setOnClickFillInIntent(R.id.LinearLayout, intent)

            setOnClickFillInIntent(R.id.ChangeNote, getSelectNoteIntent(widgetId))
        }
    }

    private fun getListItemView(index: Int, list: BaseNote): RemoteViews {
        val item = list.items[index]
        val view =
            if (item.isChild) {
                // Since RemoteViews.view.setViewLayoutMargin is only available with API Level >= 31
                // use other layout that uses marginStart to indent child
                RemoteViews(app.packageName, R.layout.widget_list_child_item)
            } else {
                RemoteViews(app.packageName, R.layout.widget_list_item)
            }
        return view.apply {
            setTextViewTextSize(
                R.id.CheckBox,
                TypedValue.COMPLEX_UNIT_SP,
                TextSize.getDisplayBodySize(preferences.textSize.value),
            )
            setTextViewText(R.id.CheckBox, item.body)
            setInt(
                R.id.CheckBox,
                "setPaintFlags",
                if (item.checked) {
                    Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
                } else {
                    Paint.ANTI_ALIAS_FLAG
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setCompoundButtonChecked(R.id.CheckBox, item.checked)
                val intent = Intent(WidgetProvider.ACTION_CHECKED_CHANGED)
                intent.putExtra(WidgetProvider.EXTRA_POSITION, index)
                val response = RemoteViews.RemoteResponse.fromFillInIntent(intent)
                setOnCheckedChangeResponse(R.id.CheckBox, response)
            } else {
                val intent = Intent(WidgetProvider.ACTION_OPEN_LIST)
                if (item.checked) {
                    setTextViewCompoundDrawablesRelative(
                        R.id.CheckBox,
                        R.drawable.checkbox_fill,
                        0,
                        0,
                        0,
                    )
                } else
                    setTextViewCompoundDrawablesRelative(
                        R.id.CheckBox,
                        R.drawable.checkbox_outline,
                        0,
                        0,
                        0,
                    )
                setOnClickFillInIntent(R.id.CheckBox, intent)
            }
        }
    }

    override fun getViewTypeCount() = 3

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return 1
    }
}
