package com.neunit.commands

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neunit.commands.utils.*
import kotlinx.android.synthetic.main.activity_speech_commands.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SpeechCommandsActivity : AppCompatActivity() {
    private val tag = SpeechCommandsActivity::class.java.name

    private val requestAudioPermissionCode = 13

    private val isRecording: AtomicBoolean = AtomicBoolean(false)

    private var startSecond = 0
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var vadUtils: VadUtils? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_commands)

        btn_start_record.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudioPermissionCode)
            } else {
                startRecordAndTimer()
            }
        }
        btn_stop_record.setOnClickListener {
            isRecording.set(false)
            btn_start_record.text = resources.getString(R.string.start_record)
            btn_start_record.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudioPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecordAndTimer()
            } else {
                Toast.makeText(this, "请开启麦克风权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecordAndTimer() {
        btn_start_record.isEnabled = false
        startRecord()
        btn_start_record.text = String.format("Listening - %ds", startSecond)
        if (timer == null) timer = Timer()
        if (timerTask == null) timerTask = object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    startSecond += 1
                    btn_start_record.text = String.format("Listening - %ds", startSecond)
                }
            }
        }
        timer?.schedule(timerTask, 1000, 1000)
    }

    @SuppressLint("MissingPermission")
    fun startRecord() {
        isRecording.set(true)
        val thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val bufferSize =
                AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize)

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "Audio Record can't initialize!")
                return@Thread
            }
            record.startRecording()
            val pool = Executors.newSingleThreadExecutor()
            while (isRecording.get()) {
                val audioBuffer = ShortArray(320)
                record.read(audioBuffer, 0, audioBuffer.size)
                vadUtils?.checkAudioIsMute(audioBuffer, object : AudioNoMuteToMuteMuteCallBack {
                    override fun callBack(data: ShortArray) {
                        pool.execute(WorkThread(data, this@SpeechCommandsActivity, resultText))
                    }
                })
            }

            record.stop()
            record.release()
            vadUtils?.stopVad()
            pool.shutdown()
            stopTimerThread()
        }
        thread.start()
        if (vadUtils == null) {
            vadUtils = VadUtils()
        }
        vadUtils?.startVad()
    }

    private fun stopTimerThread() {
        timer?.cancel()
        timerTask?.cancel()
        timer = null
        timerTask = null
        startSecond = 0
    }

    override fun onDestroy() {
        isRecording.set(false)
        super.onDestroy()
    }

}

class WorkThread(private val shortArray: ShortArray, context: Context, private val textView: TextView) : Runnable {

    private var weakReference = WeakReference(context)

    override fun run() {
        weakReference.get()?.let {
            val result = ModuleUtils.recognize(shortArray, it)
            Log.e("WorkThread", "得到一个结果:$result")
            Handler(Looper.getMainLooper()).post {
                val text = "${textView.text}\n${ConvertCommandUtils.covertCommand(result)}"
                textView.text = text
            }
        }
    }
}