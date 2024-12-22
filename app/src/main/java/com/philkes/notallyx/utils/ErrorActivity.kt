package com.philkes.notallyx.utils

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import cat.ereza.customactivityoncrash.CustomActivityOnCrash
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.utils.Operations.reportBug

/**
 * Activity used when the app is about to crash. Implicitly used by cat.ereza:customactivityoncrash.
 */
class ErrorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            cat.ereza.customactivityoncrash.R.layout.customactivityoncrash_default_error_activity
        )
        findViewById<ImageView>(
                cat.ereza.customactivityoncrash.R.id.customactivityoncrash_error_activity_image
            )
            .apply {
                minimumWidth = 100.dp(this@ErrorActivity)
                minimumHeight = 100.dp(this@ErrorActivity)
                setImageResource(R.drawable.error)
            }
        findViewById<Button>(
                cat.ereza.customactivityoncrash.R.id
                    .customactivityoncrash_error_activity_restart_button
            )
            .apply {
                setText(
                    cat.ereza.customactivityoncrash.R.string
                        .customactivityoncrash_error_activity_restart_app
                )
                setOnClickListener {
                    CustomActivityOnCrash.restartApplication(
                        this@ErrorActivity,
                        CustomActivityOnCrash.getConfigFromIntent(intent)!!,
                    )
                }
            }
        val stackTrace = CustomActivityOnCrash.getStackTraceFromIntent(intent)
        stackTrace?.let { Operations.log(application, stackTrace = it) }
        findViewById<Button>(
                cat.ereza.customactivityoncrash.R.id
                    .customactivityoncrash_error_activity_more_info_button
            )
            .apply {
                setText(R.string.report_bug)
                setOnClickListener {
                    reportBug(CustomActivityOnCrash.getStackTraceFromIntent(intent))
                }
            }
    }
}
