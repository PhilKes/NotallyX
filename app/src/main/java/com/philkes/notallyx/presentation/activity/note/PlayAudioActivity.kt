package com.philkes.notallyx.presentation.activity.note

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.databinding.ActivityPlayAudioBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.getUriForFile
import com.philkes.notallyx.utils.IO.getExternalAudioDirectory
import com.philkes.notallyx.utils.audio.AudioPlayService
import com.philkes.notallyx.utils.audio.LocalBinder
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

        audio =
            requireNotNull(
                intent?.let { IntentCompat.getParcelableExtra(it, AUDIO, Audio::class.java) }
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

    override fun onDestroy() {
        super.onDestroy()
        if (service != null) {
            unbindService(connection)
            requireNotNull(service).onStateChange = null
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
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }

            val chooser = Intent.createChooser(intent, null)
            startActivity(chooser)
        }
    }

    private fun delete() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_audio_recording_forever)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val intent = Intent()
                intent.putExtra(AUDIO, audio)
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
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "audio/mp4"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

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
        const val AUDIO = "AUDIO"
    }
}
