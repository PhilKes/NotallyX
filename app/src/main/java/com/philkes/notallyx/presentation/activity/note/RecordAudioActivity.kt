package com.philkes.notallyx.presentation.activity.note

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ActivityRecordAudioBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.utils.IO.getTempAudioFile
import com.philkes.notallyx.utils.audio.AudioRecordService
import com.philkes.notallyx.utils.audio.LocalBinder
import com.philkes.notallyx.utils.audio.Status

@RequiresApi(24)
class RecordAudioActivity : LockedActivity<ActivityRecordAudioBinding>() {

    private var service: AudioRecordService? = null
    private lateinit var connection: ServiceConnection
    private lateinit var serviceStatusObserver: Observer<Status>
    private lateinit var cancelRecordCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, AudioRecordService::class.java)
        startService(intent)

        connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    service = (binder as LocalBinder<AudioRecordService>).getService()
                    service?.status?.observe(this@RecordAudioActivity, serviceStatusObserver)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

        bindService(intent, connection, BIND_AUTO_CREATE)

        binding.Main.setOnClickListener {
            val service = this.service
            if (service != null) {
                when (service.status.value) {
                    Status.PAUSED -> service.resume()
                    Status.READY -> service.start()
                    Status.RECORDING -> service.pause()
                }
            }
        }

        binding.Stop.setOnClickListener {
            val service = this.service
            if (service != null) {
                stopRecording(service)
            }
        }

        binding.Toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        cancelRecordCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    MaterialAlertDialogBuilder(this@RecordAudioActivity)
                        .setMessage(R.string.save_recording)
                        .setPositiveButton(R.string.save) { _, _ -> stopRecording(service!!) }
                        .setNegativeButton(R.string.discard) { _, _ -> discard(service!!) }
                        .show()
                }
            }
        onBackPressedDispatcher.addCallback(cancelRecordCallback)
        serviceStatusObserver = Observer { status ->
            updateUI(binding, service!!)
            cancelRecordCallback.isEnabled = status != Status.READY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service?.let {
            unbindService(connection)
            it.status.removeObserver(serviceStatusObserver)
            service = null
        }
        if (isFinishing) {
            val intent = Intent(this, AudioRecordService::class.java)
            stopService(intent)
        }
    }

    private fun discard(service: AudioRecordService) {
        service.stop()
        getTempAudioFile().delete()
        finish()
    }

    private fun stopRecording(service: AudioRecordService) {
        service.stop()
        setResult(RESULT_OK)
        finish()
    }

    private fun updateUI(binding: ActivityRecordAudioBinding, service: AudioRecordService) {
        binding.Timer.base = service.getBase()
        when (service.status.value) {
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
