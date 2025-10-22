package com.philkes.notallyx.presentation.activity.note

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.databinding.ActivityPlayAudioBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.view.note.audio.AudioControlView
import com.philkes.notallyx.utils.audio.AudioPlayService
import com.philkes.notallyx.utils.audio.LocalBinder
import com.philkes.notallyx.utils.getExternalAudioDirectory
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayAudioActivity : LockedActivity<ActivityPlayAudioBinding>() {

    private var service: AudioPlayService? = null
    private lateinit var connection: ServiceConnection
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var audio: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        audio =
            requireNotNull(
                intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_AUDIO, Audio::class.java) },
                { "PlayAudioActivity intent has no '$EXTRA_AUDIO' extra" },
            )
        binding.AudioControlView.setDuration(audio.duration)

        val intent = Intent(this, AudioPlayService::class.java)
        startService(intent)

        connection =
            object : ServiceConnection {

                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = (binder as LocalBinder<AudioPlayService>).getService()
                    service.initialise(audio)
                    service.onStateChange = { updateUI(service) }
                    this@PlayAudioActivity.service = service
                    updateUI(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

        bindService(intent, connection, BIND_AUTO_CREATE)

        binding.Play.setOnClickListener { service?.play() }

        audio.duration?.let {
            binding.AudioControlView.onSeekComplete = { milliseconds ->
                service?.seek(milliseconds)
            }
        }

        setupToolbar(binding)

        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> writeAudioToUri(uri) }
                }
            }
    }

    /**
     * Configures the activity for edge-to-edge display, handling status bar and navigation bar
     * colors, and applying appropriate insets to layout elements.
     */
    private fun configureEdgeToEdgeInsets() {
        // 1. Enable edge-to-edge display for the activity window.
        // This makes the content draw behind the system bars (status bar and navigation bar).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 4. Apply window insets to specific views to prevent content from being obscured.
        // Set an OnApplyWindowInsetsListener on the root layout.
        // This listener will be called whenever the system insets change (e.g., status bar, nav
        // bar, keyboard).
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            // Get the system bars insets (status bar and navigation bar).
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Get the IME (Input Method Editor - keyboard) insets.
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Adjust the top margin of the Toolbar to account for the status bar.
            // This pushes the toolbar down so it's not hidden behind the status bar.
            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout() // Request a layout pass to apply the new margin
            }

            // Adjust the bottom margins of elements aligned to the bottom of the screen.
            // This ensures they are pushed up above the navigation bar and the software keyboard.

            // Convert original 32dp margins to pixels
            val originalPlayButtonBottomMarginPx = 32.dp
            val originalAudioControlViewBottomMarginPx = 32.dp

            // Calculate the total bottom inset (navigation bar + keyboard)
            val totalBottomInset = systemBarsInsets.bottom + imeInsets.bottom

            // Apply adjusted bottom margin to the Play button
            binding.Play.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    originalPlayButtonBottomMarginPx + totalBottomInset
                requestLayout() // Request a layout pass to apply the new margin
            }

            // AudioControlView is above Play button, its margin needs to consider Play button's new
            // position
            // Its own bottom margin is also increased by the totalBottomInset
            binding.AudioControlView.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    originalAudioControlViewBottomMarginPx + totalBottomInset
                requestLayout() // Request a layout pass to apply the new margin
            }

            // The Error TextView will naturally adjust its height due to layout_above and
            // layout_below
            // its bottom will be correctly above AudioControlView, which is now inset-aware.
            // No explicit padding for Error TextView needed here unless it's scrollable content.

            // Return the insets to allow them to be dispatched to child views if necessary.
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (service != null) {
            unbindService(connection)
            requireNotNull(service, { "service is null" }).onStateChange = null
            service = null
        }
        if (isFinishing) {
            val intent = Intent(this, AudioPlayService::class.java)
            stopService(intent)
        }
    }

    private fun setupToolbar(binding: ActivityPlayAudioBinding) {
        binding.Toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.Toolbar.menu.apply {
            add(R.string.share, R.drawable.share) { share() }
            add(R.string.save_to_device, R.drawable.save) { saveToDevice() }
            add(R.string.delete, R.drawable.delete) { delete() }
        }
    }

    private fun share() {
        val audioRoot = application.getExternalAudioDirectory()
        val file = if (audioRoot != null) File(audioRoot, audio.name) else null
        if (file != null && file.exists()) {
            val uri = getUriForFile(file)
            val intent =
                Intent(Intent.ACTION_SEND)
                    .apply {
                        type = "audio/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                    .wrapWithChooser(this@PlayAudioActivity)
            startActivity(intent)
        }
    }

    private fun delete() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_audio_recording_forever)
            .setCancelButton()
            .setPositiveButton(R.string.delete) { _, _ ->
                val intent = Intent()
                intent.putExtra(EXTRA_AUDIO, audio)
                setResult(RESULT_OK, intent)
                finish()
            }
            .show()
    }

    private fun saveToDevice() {
        val audioRoot = application.getExternalAudioDirectory()
        val file = if (audioRoot != null) File(audioRoot, audio.name) else null
        if (file != null && file.exists()) {
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .apply {
                        type = "audio/mp4"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    .wrapWithChooser(this)

            val formatter = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)
            val title = formatter.format(audio.timestamp)

            intent.putExtra(Intent.EXTRA_TITLE, title)
            exportFileActivityResultLauncher.launch(intent)
        }
    }

    private fun writeAudioToUri(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val audioRoot = application.getExternalAudioDirectory()
                val file = if (audioRoot != null) File(audioRoot, audio.name) else null
                if (file != null && file.exists()) {
                    val output = contentResolver.openOutputStream(uri) as FileOutputStream
                    output.channel.truncate(0)
                    val input = FileInputStream(file)
                    input.copyTo(output)
                    input.close()
                    output.close()
                }
            }
            Toast.makeText(this@PlayAudioActivity, R.string.saved_to_device, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun updateUI(service: AudioPlayService) {
        binding.AudioControlView.setCurrentPosition(service.getCurrentPosition())
        when (service.getState()) {
            AudioPlayService.PREPARED,
            AudioPlayService.PAUSED,
            AudioPlayService.COMPLETED -> {
                binding.Play.setText(R.string.play)
                binding.AudioControlView.setStarted(false)
            }
            AudioPlayService.STARTED -> {
                binding.Play.setText(R.string.pause)
                binding.AudioControlView.setStarted(true)
            }
            AudioPlayService.ERROR -> {
                binding.Error.text =
                    getString(
                        R.string.something_went_wrong_audio,
                        service.getErrorType(),
                        service.getErrorCode(),
                    )
            }
        }
    }

    companion object {
        const val EXTRA_AUDIO = "notallyx.intent.extra.AUDIO"
    }
}
