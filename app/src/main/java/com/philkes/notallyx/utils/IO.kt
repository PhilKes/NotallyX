package com.philkes.notallyx.utils

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.model.Attachment
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.isImage
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.widget.WidgetProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files

object IO {

    const val SUBFOLDER_IMAGES = "Images"
    const val SUBFOLDER_FILES = "Files"
    const val SUBFOLDER_AUDIOS = "Audios"

    fun Application.getExternalImagesDirectory() = getExternalDirectory(SUBFOLDER_IMAGES)

    fun Application.getExternalAudioDirectory() = getExternalDirectory(SUBFOLDER_AUDIOS)

    fun Application.getExternalFilesDirectory() = getExternalDirectory(SUBFOLDER_FILES)

    fun Context.getTempAudioFile(): File {
        return File(externalCacheDir, "Temp.m4a")
    }

    fun InputStream.copyToFile(destination: File) {
        val output = FileOutputStream(destination)
        copyTo(output)
        close()
        output.close()
    }

    fun File.rename(newName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val source = toPath()
            val destination = source.resolveSibling(newName)
            Files.move(source, destination)
            true // If move failed, an exception would have been thrown
        } else {
            val destination = resolveSibling(newName)
            renameTo(destination)
        }
    }

    fun File.clearDirectory() {
        val files = listFiles()
        if (files != null) {
            for (file in files) {
                file.delete()
            }
        }
    }

    fun Application.deleteAttachments(
        attachments: Collection<Attachment>,
        ids: LongArray? = null,
        progress: MutableLiveData<Progress>? = null,
    ) {
        if (attachments.isNotEmpty()) {
            progress?.postValue(Progress(0, attachments.size))
            val imageRoot = getExternalImagesDirectory()
            val audioRoot = getExternalAudioDirectory()
            val fileRoot = getExternalFilesDirectory()
            attachments.forEachIndexed { index, attachment ->
                val file =
                    when (attachment) {
                        is Audio ->
                            if (audioRoot != null) File(audioRoot, attachment.name) else null

                        is FileAttachment -> {
                            val root = if (attachment.isImage) imageRoot else fileRoot
                            if (root != null) File(root, attachment.localName) else null
                        }
                    }
                if (file != null && file.exists()) {
                    file.delete()
                }
                progress?.postValue(Progress(index + 1, attachments.size))
            }
        }
        if (ids?.isNotEmpty() == true) {
            WidgetProvider.sendBroadcast(this, ids)
        }
        progress?.postValue(Progress(inProgress = false))
    }

    fun Application.getBackupDir() = getEmptyFolder("backup")

    fun Application.getExportedPath() = getEmptyFolder("exported")

    private fun Application.getExternalDirectory(name: String): File? {
        var file: File? = null

        try {
            val mediaDir = externalMediaDirs.firstOrNull()
            if (mediaDir != null) {
                file = File(mediaDir, name)
                if (file.exists()) {
                    if (!file.isDirectory) {
                        file.delete()
                        file.createDirectory()
                    }
                } else file.createDirectory()
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

        return file
    }

    private fun File.createDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.createDirectory(toPath())
        } else mkdir()
    }

    private fun Application.getEmptyFolder(name: String): File {
        val folder = File(cacheDir, name)
        if (folder.exists()) {
            folder.clearDirectory()
        } else folder.mkdir()
        return folder
    }
}
