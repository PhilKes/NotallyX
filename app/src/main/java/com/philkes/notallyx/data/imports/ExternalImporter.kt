package com.philkes.notallyx.data.imports

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.model.BaseNote
import java.io.File

interface ExternalImporter {

    /**
     * Parses [BaseNote]s from [source] and copies attached files/images/audios to [destination]
     *
     * @return List of [BaseNote]s to import + folder containing attached files (if no attached
     *   files possible, return null).
     */
    fun import(
        app: Application,
        source: Uri,
        destination: File,
        progress: MutableLiveData<ImportProgress>? = null,
    ): Pair<List<BaseNote>, File?>
}
