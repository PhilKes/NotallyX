package com.philkes.notallyx.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.LabelDao
import com.philkes.notallyx.data.model.Label

class LabelModel(app: Application) : AndroidViewModel(app) {

    private lateinit var labelDao: LabelDao
    lateinit var labels: LiveData<List<String>>

    init {
        NotallyDatabase.getDatabase(app).observeForever {
            labelDao = it.getLabelDao()
            labels = labelDao.getAll()
        }
    }

    fun insertLabel(label: Label, onComplete: (success: Boolean) -> Unit) =
        executeAsyncWithCallback({ labelDao.insert(label) }, onComplete)
}
