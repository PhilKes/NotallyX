package com.philkes.notallyx.presentation.activity.note

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ActivityRecordAudioBinding
import com.philkes.notallyx.utils.IO
import com.philkes.notallyx.utils.audio.AudioRecordService
import com.philkes.notallyx.utils.audio.LocalBinder
import com.philkes.notallyx.utils.audio.Status

@RequiresApi(24)
class RecordAudioActivity : AppCompatActivity() {

    private var service: AudioRecordService? = null
    private lateinit var connection: ServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecordAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, AudioRecordService::class.java)
        startService(intent)

        connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    service = (binder as LocalBinder<AudioRecordService>).getService()
                    updateUI(binding, requireNotNull(service))
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

        bindService(intent, connection, BIND_AUTO_CREATE)

        binding.Main.setOnClickListener {
            val service = this.service
            if (service != null) {
                when (service.status) {
                    Status.PAUSED -> service.resume()
                    Status.READY -> service.start()
                    Status.RECORDING -> service.pause()
                }
                updateUI(binding, service)
            }
        }

        binding.Stop.setOnClickListener {
            val service = this.service
            if (service != null) {
                stopRecording(service)
            }
        }

        binding.Toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (service != null) {
            unbindService(connection)
            service = null
        }
        if (isFinishing) {
            val intent = Intent(this, AudioRecordService::class.java)
            stopService(intent)
        }
    }

    override fun onBackPressed() {
        val service = this.service
        if (service != null) {
            if (service.status != Status.READY) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.save_recording)
                    .setPositiveButton(R.string.save) { _, _ -> stopRecording(service) }
                    .setNegativeButton(R.string.discard) { _, _ -> discard(service) }
                    .show()
            } else super.onBackPressed()
        } else super.onBackPressed()
    }

    private fun discard(service: AudioRecordService) {
        service.stop()
        IO.getTempAudioFile(this).delete()
        finish()
    }

    private fun stopRecording(service: AudioRecordService) {
        service.stop()
        setResult(RESULT_OK)
        finish()
    }

    private fun updateUI(binding: ActivityRecordAudioBinding, service: AudioRecordService) {
        binding.Timer.base = service.getBase()
        when (service.status) {
            Status.READY -> {
                binding.Stop.isEnabled = false
                binding.Main.setText(R.string.start)
            }
            Status.RECORDING -> {
                binding.Timer.start()
                binding.Stop.isEnabled = true
                binding.Main.setText(R.string.pause)
            }
            Status.PAUSED -> {
                binding.Timer.stop()
                binding.Stop.isEnabled = true
                binding.Main.setText(R.string.resume)
            }
        }
    }
}
