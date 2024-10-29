package com.philkes.notallyx.data.imports

import android.app.Application
import android.net.Uri
import com.philkes.notallyx.data.model.BaseNote
import java.io.File

interface ExternalImporter {

    fun importFrom(uri: Uri, app: Application): Pair<List<BaseNote>, File>
}
