package com.philkes.notallyx.presentation.view.misc

import com.philkes.notallyx.R

sealed interface TextInfo {

    val title: Int

    val key: String
    val defaultValue: String
}

object AutoBackup : TextInfo {
    const val emptyPath = "emptyPath"

    override val title = R.string.auto_backup

    override val key = "autoBackup"
    override val defaultValue = emptyPath
}

object BackupPassword : TextInfo {
    const val emptyPassword = "None"

    override val title = R.string.backup_password

    override val key = "backupPassword"
    override val defaultValue = emptyPassword
}
