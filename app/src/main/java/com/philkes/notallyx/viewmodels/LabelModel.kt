package com.philkes.notallyx.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.philkes.notallyx.model.Label
import com.philkes.notallyx.model.NotallyDatabase

class LabelModel(app: Application) : AndroidViewModel(app) {

    private val database = NotallyDatabase.getDatabase(app)
    private val labelDao = database.getLabelDao()
    val labels = labelDao.getAll()

    fun insertLabel(label: Label, onComplete: (success: Boolean) -> Unit) =
        executeAsyncWithCallback({ labelDao.insert(label) }, onComplete)
}
