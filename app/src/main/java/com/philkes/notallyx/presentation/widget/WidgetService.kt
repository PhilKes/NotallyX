package com.philkes.notallyx.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.presentation.view.Constants

class WidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getLongExtra(Constants.SelectedBaseNote, 0)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        return WidgetFactory(application as NotallyXApplication, id, widgetId)
    }
}
