package com.philkes.notallyx.utils

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cat.ereza.customactivityoncrash.CustomActivityOnCrash
import com.philkes.notallyx.databinding.ActivityErrorBinding

/**
 * Activity used when the app is about to crash. Implicitly used by
 * `cat.ereza:customactivityoncrash`.
 */
class ErrorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            RestartButton.setOnClickListener {
                CustomActivityOnCrash.restartApplication(
                    this@ErrorActivity,
                    CustomActivityOnCrash.getConfigFromIntent(intent)!!,
                )
            }

            val stackTrace = CustomActivityOnCrash.getStackTraceFromIntent(intent)
            stackTrace?.let {
                application.log(TAG, stackTrace = it)
                ExceptionTitle.text = stackTrace.lines().firstOrNull()?.replaceFirst(":", ":\n")
                ExceptionDetails.text = stackTrace.lines().drop(1).joinToString("\n")
                CopyButton.setOnClickListener { copyToClipBoard(stackTrace) }
            }
            ReportButton.setOnClickListener { reportBug(stackTrace) }
            ViewLogsButton.setOnClickListener { viewLogs() }
        }
    }

    companion object {
        private const val TAG = "ErrorActivity"
    }
}
