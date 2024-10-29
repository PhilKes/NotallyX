package com.philkes.notallyx.data.imports

class ImportException(val textResId: Int, cause: Throwable? = null) : RuntimeException(cause)
