package com.philkes.notallyx.presentation.activity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity
import com.philkes.notallyx.presentation.widget.WidgetProvider

class ConfigureWidgetActivity : PickNoteActivity() {

    private val id by lazy {
        intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = Intent()
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
    }

    override fun onClick(position: Int) {
        if (position != -1) {
            val preferences = Preferences.getInstance(application)
            val noteId = (adapter.getItem(position) as BaseNote).id
            preferences.updateWidget(id, noteId)

            val manager = AppWidgetManager.getInstance(this)
            WidgetProvider.updateWidget(this, manager, id, noteId)

            val success = Intent()
            success.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            setResult(RESULT_OK, success)
            finish()
        }
    }
}
