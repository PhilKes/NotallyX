package com.philkes.notallyx.data.imports

import android.app.Application
import java.io.File
import java.io.InputStream

interface ExternalImporter {

    fun importFrom(inputStream: InputStream, app: Application): Pair<NotesImport, File>
}
