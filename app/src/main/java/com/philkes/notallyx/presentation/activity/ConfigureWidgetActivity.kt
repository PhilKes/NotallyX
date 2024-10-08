package com.philkes.notallyx.presentation.activity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.databinding.ActivityConfigureWidgetBinding
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.misc.View
import com.philkes.notallyx.presentation.view.note.listitem.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.IO
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigureWidgetActivity : AppCompatActivity(), ItemListener {

    private lateinit var adapter: BaseNoteAdapter
    private val id by lazy {
        intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityConfigureWidgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = Intent()
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)

        val preferences = Preferences.getInstance(application)

        val maxItems = preferences.maxItems
        val maxLines = preferences.maxLines
        val maxTitle = preferences.maxTitle
        val textSize = preferences.textSize.value
        val dateFormat = preferences.dateFormat.value
        val imagesRoot = IO.getExternalImagesDirectory(application)

        adapter =
            BaseNoteAdapter(
                Collections.emptySet(),
                dateFormat,
                textSize,
                maxItems,
                maxLines,
                maxTitle,
                imagesRoot,
                this,
            )

        binding.RecyclerView.adapter = adapter
        binding.RecyclerView.setHasFixedSize(true)

        binding.RecyclerView.layoutManager =
            if (preferences.view.value == View.grid) {
                StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            } else LinearLayoutManager(this)

        val database = NotallyDatabase.getDatabase(application)

        val pinned = Header(getString(R.string.pinned))
        val others = Header(getString(R.string.others))

        lifecycleScope.launch {
            val notes =
                withContext(Dispatchers.IO) {
                    val raw = database.getBaseNoteDao().getAllNotes()
                    BaseNoteModel.transform(raw, pinned, others)
                }
            adapter.submitList(notes)
        }
    }

    override fun onClick(position: Int) {
        if (position != -1) {
            val preferences = Preferences.getInstance(application)
            val noteId = (adapter.currentList[position] as BaseNote).id
            preferences.updateWidget(id, noteId)

            val manager = AppWidgetManager.getInstance(this)
            WidgetProvider.updateWidget(this, manager, id, noteId)

            val success = Intent()
            success.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            setResult(RESULT_OK, success)
            finish()
        }
    }

    override fun onLongClick(position: Int) {}
}