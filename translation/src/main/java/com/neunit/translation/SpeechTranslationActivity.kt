package com.neunit.translation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.maple.recorder.recording.AudioRecordConfig
import com.maple.recorder.recording.MsRecorder
import com.maple.recorder.recording.PullTransport
import com.maple.recorder.recording.Recorder
import com.neunit.translation.helper.FileHelper
import com.neunit.translation.helper.RiffWaveHelper
import com.neunit.winner.whisper.WhisperContext
import kotlinx.android.synthetic.main.activity_speech_translation.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class SpeechTranslationActivity : AppCompatActivity() {

    private var whisperContext: WhisperContext? = null
    private val requestAudioPermissionCode = 13
    private var recorder: Recorder? = null
    private var recordFile: File? = null

    private var resultBuff: StringBuffer = StringBuffer()

    private var chooseFileLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_translation)

        chooseFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.data?.let { uri ->
                val filePath = FileHelper.getFilePath(uri, this)
                resultBuff.append("选择文件路径:$filePath\n")
                translation_result.text = resultBuff
                filePath.let { path ->
                    if (path.endsWith(".wav", true) && File(path).exists()) {
                        startTranslation(File(path))
                    } else {
                        resultBuff.append("暂不支持此文件格式\n")
                        translation_result.text = resultBuff
                    }
                }
            }
        }

        record.setOnClickListener {
            when (record.text) {
                getString(R.string.start_record) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudioPermissionCode)
                    } else {
                        startRecord()
                    }
                }
                getString(R.string.stop_record) -> {
                    recorder?.stopRecording()
                    resultBuff.append("结束录音\n")
                    translation_result.text = resultBuff
                    record.text = getString(R.string.translating)
                    recordFile?.let {
                        startTranslation(it)
                    }

                }
            }
        }

        choose_file.setOnClickListener {
            intentChooseFile()
        }

        resultBuff.append("开始加载模型\n")
        translation_result.text = resultBuff
        CoroutineScope(Dispatchers.Main).launch {
            val loadModelResult = loadModel()
            if (loadModelResult) {
                resultBuff.append("模型加载完成\n")
                translation_result.text = resultBuff
                record.isEnabled = true
                choose_file.isEnabled = true
            } else {
                resultBuff.append("模型加载失败\n")
                translation_result.text = getString(R.string.load_model)
            }
        }

    }

    private fun intentChooseFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        chooseFileLauncher?.launch(Intent.createChooser(intent, "选择文件"))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudioPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecord()
            } else {
                Toast.makeText(this, "请开启麦克风权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        val models = application.assets.list("models/")
        if (models != null) {
            whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/${models[0]}")
            return@withContext true
        } else {
            return@withContext false
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecord() {
        recordFile = File("$filesDir${File.separator}${Date().time}.wav")
        if (!recordFile!!.exists()) {
            recordFile!!.createNewFile()
        }
        recorder = MsRecorder.wav(recordFile,
            AudioRecordConfig(MediaRecorder.AudioSource.MIC, 16000,  // 采样率，44100、22050、16000、11025 Hz
                AudioFormat.CHANNEL_IN_MONO,  // 单声道、双声道/立体声
                AudioFormat.ENCODING_PCM_16BIT // 8/16 bit
            ),
            PullTransport.Default().setOnAudioChunkPulledListener {})
        recorder?.startRecording()
        resultBuff.append("开始录音\n")
        translation_result.text = resultBuff
        record.text = getString(R.string.stop_record)
    }

    private fun startTranslation(file: File) {
        CoroutineScope(Dispatchers.Main).launch {
            if (file.exists()) {
                resultBuff.append("开始转译...\n")
                translation_result.text = resultBuff
                val floatArrayData = RiffWaveHelper.decodeWaveFile(file)
                val start = System.currentTimeMillis()
                val result = whisperContext?.transcribeData(floatArrayData)
                val elapsed = System.currentTimeMillis() - start
                resultBuff.append("转译完成:耗时(${elapsed / 1000}s)\n")
                resultBuff.append("转译结果:$result\n")
                translation_result.text = resultBuff
                record.text = getString(R.string.start_record)
            } else {
                resultBuff.append("文件不存在\n")
                translation_result.text = resultBuff
            }
        }
    }

    override fun onDestroy() {
        recorder?.stopRecording()
        recorder = null
        runBlocking {
            whisperContext?.release()
        }
        super.onDestroy()
    }
}