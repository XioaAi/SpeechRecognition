package com.neunit.speechrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private val tag = MainActivity::class.java.name

    private var module: Module? = null

    private val requestAudioPermissionCode = 13
    private val audioSeconds = 2
    private val audioSampleRate = 16000
    private val recordingLength = audioSampleRate * audioSeconds

    private var start = 0
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    private val keyword = mapOf<Long, String>(0L to "yes",
        1L to "no",
        2L to "up",
        3L to "down",
        4L to "left",
        5L to "right",
        6L to "on",
        7L to "off",
        8L to "stop",
        9L to "go",
        10L to "_silence_",
        11L to "_unknown_")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecognize.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudioPermissionCode)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudioPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecord()
                btnRecognize.text = String.format("Listening - %ds", audioSeconds)
                if (timer == null) timer = Timer()
                if (timerTask == null) timerTask = object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            start += 1
                            if (audioSeconds - start > 0) {
                                btnRecognize.text = String.format("Listening - %ds", audioSeconds - start)
                            } else {
                                btnRecognize.text = getString(R.string.recognizing)
                            }
                        }
                    }
                }
                timer?.schedule(timerTask, 1000, 1000)
            } else {
                Toast.makeText(this, "请开启麦克风权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecord() {
        val thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val bufferSize = AudioRecord.getMinBufferSize(audioSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                audioSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize)

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "Audio Record can't initialize!")
                return@Thread
            }
            record.startRecording()

            var shortsRead: Long = 0
            var recordingOffset = 0
            val audioBuffer = ShortArray(bufferSize / 2)
            val recordingBuffer = ShortArray(recordingLength)

            while (shortsRead < recordingLength) {
                val numberOfShort = record.read(audioBuffer, 0, audioBuffer.size)
                shortsRead += numberOfShort.toLong()
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberOfShort)
                recordingOffset += numberOfShort
            }

            record.stop()
            record.release()
            stopTimerThread()

            val floatInputBuffer = FloatArray(recordingLength)
            for (i in 0 until recordingLength) {
                floatInputBuffer[i] = recordingBuffer[i] / Short.MAX_VALUE.toFloat()
            }

            val result: String = recognize(floatInputBuffer)

            runOnUiThread {
                btnRecognize.isEnabled = true
                btnRecognize.text = getString(R.string.start_record)
                resultText.text = result
            }
        }
        thread.start()
    }

    private fun recognize(floatInputBuffer: FloatArray): String {
        if (module == null) {
            val modelPath = assetFilePath(applicationContext, "speechcommand-demo.ptl")
            Log.e(tag, "加载的模型文件目录:$modelPath")
            module = LiteModuleLoader.load(modelPath)
        }

        val inTensor: Tensor = Tensor.fromBlob(floatInputBuffer, longArrayOf(1, recordingLength.toLong()))
        val tensor = module?.forward(IValue.from(inTensor))?.toTensor()
        Log.e(tag, "转译后结果:${tensor},${tensor!!.dataAsLongArray}")
        var result: Long? = null
        tensor.dataAsLongArray.forEach {
            Log.e(tag, "解析后结果:$it")
            result = it
        }
        return keyword[result] ?: keyword[11L]!!
    }

    private fun assetFilePath(context: Context, assetName: String): String? {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        try {
            context.assets.open(assetName).use { data ->
                FileOutputStream(file).use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (data.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
                return file.absolutePath
            }
        } catch (e: IOException) {
            Log.e(tag, assetName + ": " + e.localizedMessage)
        }
        return null
    }

    private fun stopTimerThread() {
        timer?.cancel()
        timerTask?.cancel()
        timer = null
        timerTask = null
        start = 0
    }

    override fun onDestroy() {
        stopTimerThread()
        super.onDestroy()
    }

}