package com.philkes.notallyx.data

import android.app.Application
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.presentation.viewmodel.NotallyModel.FileType
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.IO.copyToFile
import com.philkes.notallyx.utils.IO.getExternalAudioDirectory
import com.philkes.notallyx.utils.IO.getExternalFilesDirectory
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import com.philkes.notallyx.utils.IO.rename
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.getFileName
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataUtil {
    companion object {

        suspend fun addFile(
            app: Application,
            uri: Uri,
            directory: File,
            fileType: FileType,
            errorWhileRenaming: Int = R.string.error_while_renaming_file,
            proposedMimeType: String? = null,
        ): Pair<FileAttachment?, FileError?> {
            return withContext(Dispatchers.IO) {
                val document = requireNotNull(DocumentFile.fromSingleUri(app, uri))
                val displayName = document.name ?: app.getString(R.string.unknown_name)
                try {

                    /*
                    If we have reached this point, an SD card (emulated or real) exists and externalRoot
                    is not null. externalRoot.exists() can be false if the folder `Images` has been deleted after
                    the previous line, but externalRoot itself can't be null
                    */
                    val temp = File(directory, "Temp")

                    val inputStream = requireNotNull(app.contentResolver.openInputStream(uri))
                    inputStream.copyToFile(temp)

                    val originalName = app.getFileName(uri)
                    when (fileType) {
                        FileType.IMAGE -> {
                            val options = BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            BitmapFactory.decodeFile(temp.path, options)
                            val mimeType = options.outMimeType ?: proposedMimeType

                            if (mimeType != null) {
                                val extension = getExtensionForMimeType(mimeType)
                                if (extension != null) {
                                    val name = "${UUID.randomUUID()}.$extension"
                                    if (temp.rename(name)) {
                                        return@withContext Pair(
                                            FileAttachment(name, originalName ?: name, mimeType),
                                            null,
                                        )
                                    } else {
                                        // I don't expect this error to ever happen but just in
                                        // case
                                        return@withContext Pair(
                                            null,
                                            FileError(
                                                displayName,
                                                app.getString(errorWhileRenaming),
                                                fileType,
                                            ),
                                        )
                                    }
                                } else
                                    return@withContext Pair(
                                        null,
                                        FileError(
                                            displayName,
                                            app.getString(R.string.image_format_not_supported),
                                            fileType,
                                        ),
                                    )
                            } else
                                return@withContext Pair(
                                    null,
                                    FileError(
                                        displayName,
                                        app.getString(R.string.invalid_image),
                                        fileType,
                                    ),
                                )
                        }

                        FileType.ANY -> {
                            val (mimeType, fileExtension) =
                                determineMimeTypeAndExtension(
                                    proposedMimeType,
                                    uri,
                                    app.contentResolver,
                                )
                            val name = "${UUID.randomUUID()}${fileExtension}"
                            if (temp.rename(name)) {
                                return@withContext Pair(
                                    FileAttachment(name, originalName ?: name, mimeType),
                                    null,
                                )
                            } else {
                                // I don't expect this error to ever happen but just in case
                                return@withContext Pair(
                                    null,
                                    FileError(
                                        displayName,
                                        app.getString(errorWhileRenaming),
                                        fileType,
                                    ),
                                )
                            }
                        }
                    }
                } catch (exception: Exception) {
                    Operations.log(app, exception)
                    return@withContext Pair(
                        null,
                        FileError(displayName, app.getString(R.string.unknown_error), fileType),
                    )
                }
            }
        }

        private fun determineMimeTypeAndExtension(
            proposedMimeType: String?,
            uri: Uri,
            contentResolver: ContentResolver,
        ) =
            if (proposedMimeType != null && proposedMimeType.contains("/")) {
                Pair(proposedMimeType, ".${uri.lastPathSegment?.substringAfterLast(".")}")
            } else {
                val actualMimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                Pair(
                    actualMimeType,
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(actualMimeType)?.let {
                        ".${it}"
                    } ?: "",
                )
            }

        suspend fun addFile(
            app: Application,
            uri: Uri,
            proposedMimeType: String? = null,
        ): Pair<FileAttachment?, FileError?> {
            val filesRoot = app.getExternalFilesDirectory()
            requireNotNull(filesRoot) { "filesRoot is null" }
            return addFile(app, uri, filesRoot, FileType.ANY, proposedMimeType = proposedMimeType)
        }

        suspend fun addImage(
            app: Application,
            uri: Uri,
            proposedMimeType: String? = null,
        ): Pair<FileAttachment?, FileError?> {
            val imagesRoot = app.getExternalImagesDirectory()
            requireNotNull(imagesRoot) { "imagesRoot is null" }
            return addFile(
                app,
                uri,
                imagesRoot,
                FileType.IMAGE,
                proposedMimeType = proposedMimeType,
            )
        }

        suspend fun addAudio(app: Application, original: File, deleteOriginalFile: Boolean): Audio {
            return withContext(Dispatchers.IO) {
                /*
                Regenerate because the directory may have been deleted between the time of activity creation
                and audio recording
                */
                val audioRoot = app.getExternalAudioDirectory()
                requireNotNull(audioRoot) { "audioRoot is null" }

                /*
                If we have reached this point, an SD card (emulated or real) exists and audioRoot
                is not null. audioRoot.exists() can be false if the folder `Audio` has been deleted after
                the previous line, but audioRoot itself can't be null
                */
                val name = "${UUID.randomUUID()}.m4a"
                val final = File(audioRoot, name)
                val input = FileInputStream(original)
                input.copyToFile(final)

                if (deleteOriginalFile) {
                    original.delete()
                }

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(final.path)
                val duration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                Audio(name, duration?.toLong(), System.currentTimeMillis())
            }
        }

        private fun getExtensionForMimeType(type: String): String? {
            return when (type) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                else -> null
            }
        }
    }
}
