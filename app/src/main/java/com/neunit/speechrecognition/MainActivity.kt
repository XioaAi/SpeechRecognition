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
import java.nio.FloatBuffer
import java.util.*

class MainActivity : AppCompatActivity() {
    private val tag = MainActivity::class.java.name

    private var module: Module? = null

    private val requestAudioPermissionCode = 13
    private val audioSeconds = 6
    private val audioSampleRate = 16000
    private val recordingLength = audioSampleRate * audioSeconds

    private var start = 1
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

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
                            btnRecognize.text = String.format("Listening - %ds", audioSeconds - start)
                            start += 1
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

            runOnUiThread { btnRecognize.text = getString(R.string.recognizing) }

            val floatInputBuffer = FloatArray(recordingLength)

            for (i in 0 until recordingLength) {
                floatInputBuffer[i] = recordingBuffer[i] / Short.MAX_VALUE.toFloat()
            }

            val result: String? = recognize(floatInputBuffer)

            runOnUiThread {
                btnRecognize.isEnabled = true
                btnRecognize.text = getString(R.string.start_record)
                resultText.text = result ?: ""
            }
        }
        thread.start()
    }

    private fun recognize(floatInputBuffer: FloatArray): String? {
        if (module == null) {
//            String filePath = Environment.getExternalStorageDirectory().getAbsolutePath();
//            File file = new File(filePath + File.separator + "wav2vec2.ptl");
//            Log.e(tag", "文件是否存在:" + file.exists());
//            module = LiteModuleLoader.load(file.getAbsolutePath());
            module = LiteModuleLoader.load(assetFilePath(applicationContext, "wav2vec2.ptl"))
        }
        val wavInput = DoubleArray(recordingLength)
        for (n in 0 until recordingLength) wavInput[n] = floatInputBuffer[n].toDouble()
        val inTensorBuffer: FloatBuffer = Tensor.allocateFloatBuffer(recordingLength)
        for (data in wavInput) inTensorBuffer.put(data.toFloat())
        val inTensor: Tensor = Tensor.fromBlob(inTensorBuffer, longArrayOf(1, recordingLength.toLong()))
        return module?.forward(IValue.from(inTensor))?.toStr()
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
        start = 1
    }

    override fun onDestroy() {
        stopTimerThread()
        super.onDestroy()
    }

}