package com.philkes.notallyx.presentation.activity.note

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.ActivityViewImageBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.getUriForFile
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.note.image.ImageAdapter
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewImageActivity : LockedActivity<ActivityViewImageBinding>() {

    private var currentImage: FileAttachment? = null
    private lateinit var deletedImages: ArrayList<FileAttachment>
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedList =
            savedInstanceState?.let {
                BundleCompat.getParcelableArrayList(it, DELETED_IMAGES, FileAttachment::class.java)
            }
        deletedImages = savedList ?: ArrayList()

        val resultIntent = Intent()
        resultIntent.putExtra(DELETED_IMAGES, deletedImages)
        setResult(RESULT_OK, resultIntent)

        val savedImage =
            savedInstanceState?.let {
                BundleCompat.getParcelable(it, CURRENT_IMAGE, FileAttachment::class.java)
            }
        if (savedImage != null) {
            currentImage = savedImage
        }

        binding.RecyclerView.apply {
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(this@ViewImageActivity, RecyclerView.HORIZONTAL, false)
            PagerSnapHelper().attachToRecyclerView(binding.RecyclerView)
        }

        val initial = intent.getIntExtra(POSITION, 0)
        binding.RecyclerView.scrollToPosition(initial)

        val database = NotallyDatabase.getDatabase(application)
        val id = intent.getLongExtra(Constants.SelectedBaseNote, 0)

        database.observe(this@ViewImageActivity) {
            lifecycleScope.launch {
                val json = withContext(Dispatchers.IO) { it.getBaseNoteDao().getImages(id) }
                val original = Converters.jsonToFiles(json)
                val images = ArrayList<FileAttachment>(original.size)
                original.filterNotTo(images) { image -> deletedImages.contains(image) }

                val mediaRoot = application.getExternalImagesDirectory()
                val adapter = ImageAdapter(mediaRoot, images)
                binding.RecyclerView.adapter = adapter
                setupToolbar(binding, adapter)
            }
        }

        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> writeImageToUri(uri) }
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putParcelable(CURRENT_IMAGE, currentImage)
            putParcelableArrayList(DELETED_IMAGES, deletedImages)
        }
    }

    private fun setupToolbar(binding: ActivityViewImageBinding, adapter: ImageAdapter) {
        binding.Toolbar.setNavigationOnClickListener { finish() }

        val layoutManager = binding.RecyclerView.layoutManager as LinearLayoutManager
        adapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    val position = layoutManager.findFirstVisibleItemPosition()
                    binding.Toolbar.title = "${position + 1} / ${adapter.itemCount}"
                }
            }
        )

        binding.RecyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val position = layoutManager.findFirstVisibleItemPosition()
                    binding.Toolbar.title = "${position + 1} / ${adapter.itemCount}"
                }
            }
        )
        binding.Toolbar.menu.apply {
            add(R.string.share, R.drawable.share) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    val image = adapter.items[position]
                    share(image)
                }
            }
            add(R.string.save_to_device, R.drawable.save) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    val image = adapter.items[position]
                    saveToDevice(image)
                }
            }
            add(R.string.delete, R.drawable.delete) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    delete(position, adapter)
                }
            }
        }
    }

    private fun share(image: FileAttachment) {
        val mediaRoot = application.getExternalImagesDirectory()
        val file = if (mediaRoot != null) File(mediaRoot, image.localName) else null
        if (file != null && file.exists()) {
            val uri = getUriForFile(file)

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = image.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)

                    // Necessary for sharesheet to show a preview of the image
                    // Check ->
                    // https://commonsware.com/blog/2021/01/07/action_send-share-sheet-clipdata.html
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser = Intent.createChooser(intent, null)
            startActivity(chooser)
        }
    }

    private fun saveToDevice(image: FileAttachment) {
        val mediaRoot = application.getExternalImagesDirectory()
        val file = if (mediaRoot != null) File(mediaRoot, image.localName) else null
        if (file != null && file.exists()) {
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = image.mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, "NotallyX Image")
                }
            currentImage = image
            exportFileActivityResultLauncher.launch(intent)
        }
    }

    private fun writeImageToUri(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val mediaRoot = application.getExternalImagesDirectory()
                val file =
                    if (mediaRoot != null) File(mediaRoot, requireNotNull(currentImage).localName)
                    else null
                if (file != null && file.exists()) {
                    val output = contentResolver.openOutputStream(uri) as FileOutputStream
                    output.channel.truncate(0)
                    val input = FileInputStream(file)
                    input.copyTo(output)
                    input.close()
                    output.close()
                }
            }
            Toast.makeText(this@ViewImageActivity, R.string.saved_to_device, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun delete(position: Int, adapter: ImageAdapter) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_image_forever)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val image = adapter.items.removeAt(position)
                deletedImages.add(image)
                adapter.notifyItemRemoved(position)
                if (adapter.items.isEmpty()) {
                    finish()
                }
            }
            .show()
    }

    companion object {
        const val POSITION = "POSITION"
        const val CURRENT_IMAGE = "CURRENT_IMAGE"
        const val DELETED_IMAGES = "DELETED_IMAGES"
    }
}
