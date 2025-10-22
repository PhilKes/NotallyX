package com.philkes.notallyx.presentation.widget

import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.widget.WidgetProvider.Companion.extractWidgetColors
import com.philkes.notallyx.presentation.widget.WidgetProvider.Companion.getWidgetCheckedChangeIntent
import com.philkes.notallyx.presentation.widget.WidgetProvider.Companion.getWidgetOpenNoteIntent
import com.philkes.notallyx.presentation.widget.WidgetProvider.Companion.getWidgetSelectNoteIntent

class WidgetFactory(
    private val app: NotallyXApplication,
    private val id: Long,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var baseNote: BaseNote? = null
    private lateinit var database: NotallyDatabase
    private val preferences = NotallyXPreferences.getInstance(app)

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
        requireNotNull(copy, { "baseNote is null" })

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
            val textSize = preferences.textSize.value
            setTextViewTextSize(R.id.Title, TypedValue.COMPLEX_UNIT_SP, textSize.displayTitleSize)
            setTextViewText(R.id.Title, note.title)

            val bodyTextSize = textSize.displayBodySize

            setTextViewTextSize(R.id.Note, TypedValue.COMPLEX_UNIT_SP, bodyTextSize)
            if (note.body.isNotEmpty()) {
                setTextViewText(R.id.Note, note.body)
                setViewVisibility(R.id.Note, View.VISIBLE)
            } else setViewVisibility(R.id.Note, View.GONE)

            setOnClickFillInIntent(R.id.ChangeNote, getWidgetSelectNoteIntent(widgetId))
            setOnClickFillInIntent(R.id.LinearLayout, getWidgetOpenNoteIntent(note.type, note.id))

            val (_, controlsColor) = app.extractWidgetColors(note.color, preferences)
            setTextViewsTextColor(listOf(R.id.Title, R.id.Note), controlsColor)
            setImageViewColor(R.id.ChangeNote, controlsColor)
        }
    }

    private fun getListHeaderView(list: BaseNote): RemoteViews {
        return RemoteViews(app.packageName, R.layout.widget_list_header).apply {
            setTextViewTextSize(
                R.id.Title,
                TypedValue.COMPLEX_UNIT_SP,
                preferences.textSize.value.displayTitleSize,
            )
            setTextViewText(R.id.Title, list.title)
            setOnClickFillInIntent(R.id.ChangeNote, getWidgetSelectNoteIntent(widgetId))
            val openNoteWidgetIntent = getWidgetOpenNoteIntent(list.type, list.id)
            setOnClickFillInIntent(R.id.LinearLayout, openNoteWidgetIntent)
            setOnClickFillInIntent(R.id.Title, openNoteWidgetIntent)

            val (_, controlsColor) = app.extractWidgetColors(list.color, preferences)
            setTextViewsTextColor(listOf(R.id.Title), controlsColor)
            setImageViewColor(R.id.ChangeNote, controlsColor)
        }
    }

    private fun getListItemView(index: Int, list: BaseNote): RemoteViews {
        val item = list.items[index]
        val view =
            if (item.isChild) {
                RemoteViews(app.packageName, R.layout.widget_list_child_item)
            } else {
                RemoteViews(app.packageName, R.layout.widget_list_item)
            }
        return view.apply {
            val (_, controlsColor) = app.extractWidgetColors(list.color, preferences)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setListItemTextView(item, R.id.CheckBox, controlsColor)
                setCompoundButtonChecked(R.id.CheckBox, item.checked)
                val checkIntent = getWidgetCheckedChangeIntent(list.id, index)
                setOnCheckedChangeResponse(
                    R.id.CheckBox,
                    RemoteViews.RemoteResponse.fromFillInIntent(checkIntent),
                )
                setColorStateList(
                    R.id.CheckBox,
                    "setButtonTintList",
                    ColorStateList.valueOf(controlsColor),
                )
            } else {
                setListItemTextView(item, R.id.CheckBoxText, controlsColor)
                setImageViewResource(
                    R.id.CheckBox,
                    if (item.checked) R.drawable.checkbox_fill else R.drawable.checkbox_outline,
                )
                setOnClickFillInIntent(
                    R.id.LinearLayout,
                    getWidgetCheckedChangeIntent(list.id, index),
                )
                setImageViewColor(R.id.CheckBox, controlsColor)
            }
            setTextViewsTextColor(listOf(R.id.Title), controlsColor)
        }
    }

    private fun RemoteViews.setListItemTextView(item: ListItem, textViewId: Int, fontColor: Int) {
        setTextViewTextSize(
            textViewId,
            TypedValue.COMPLEX_UNIT_SP,
            preferences.textSize.value.displayBodySize,
        )
        setTextViewText(textViewId, item.body)
        setInt(
            textViewId,
            "setPaintFlags",
            if (item.checked) {
                Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            } else {
                Paint.ANTI_ALIAS_FLAG
            },
        )
        setInt(textViewId, "setTextColor", fontColor)
    }

    private fun RemoteViews.setTextViewsTextColor(viewIds: List<Int>, color: Int) {
        viewIds.forEach { viewId -> setInt(viewId, "setTextColor", color) }
    }

    private fun RemoteViews.setImageViewColor(viewId: Int, color: Int) {
        setInt(viewId, "setColorFilter", color)
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
