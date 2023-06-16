package com.neunit.translation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import io.microshow.rxffmpeg.RxFFmpegCommandList
import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegInvoke.IFFmpegListener
import kotlinx.android.synthetic.main.activity_speech_translation.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class SpeechTranslationActivity : AppCompatActivity() {

    private var whisperContext: WhisperContext? = null
    private val requestExternalPermissionCode = 12
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
                val fileName = FileHelper.getFileName(uri, this)
                val filePath = FileHelper.getFilePath(fileName, uri, this)
                resultBuff.append("选择文件名称:$fileName\n")
                translation_result.text = resultBuff
                filePath.let { path ->
                    if (path.endsWith(".wav", true) && File(path).exists()) {
                        startTranslation(File(path))
                    } else {
                        changeFileFormatToWav(path)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), requestExternalPermissionCode)
            } else {
                intentChooseFile()
            }
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
        when (requestCode) {
            requestAudioPermissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecord()
                } else {
                    Toast.makeText(this, "请开启麦克风权限", Toast.LENGTH_LONG).show()
                }
            }
            requestExternalPermissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    intentChooseFile()
                } else {
                    Toast.makeText(this, "请开启读取存储卡权限", Toast.LENGTH_LONG).show()
                }
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

    /**
     * 开始转译
     */
    private fun startTranslation(file: File) {
        record.isEnabled = false
        choose_file.isEnabled = false
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
            record.isEnabled = true
            choose_file.isEnabled = true
        }
    }

    /**
     * 转换文件格式
     */
    private fun changeFileFormatToWav(filePath: String) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists() || (sourceFile.exists() && sourceFile.length() <= 0)) {
            resultBuff.append("文件已损坏\n")
            translation_result.text = resultBuff
            return
        }
        val index = filePath.lastIndexOf(".")
        val wavFilePath = if (index != -1) {
            "${filePath.substring(0, index)}.wav"
        } else {
            "$filePath.wav"
        }
        val wavFile = File(wavFilePath)
        if (wavFile.exists() && wavFile.length() > 0) {
            startTranslation(wavFile)
        } else {
            val commands = RxFFmpegCommandList().apply {
                clearCommands()
                append("ffmpeg")
                append("-i")
                append(filePath)
                append("-acodec")
                append("pcm_s16le")
                append("-ac")
                append("1")
                append("-ar")
                append("16000")
                append(wavFilePath)
            }.build()
            RxFFmpegInvoke.getInstance().runCommand(commands, object : IFFmpegListener {
                override fun onFinish() {
                    resultBuff.append("转换文件格式成功\n")
                    translation_result.text = resultBuff
                    File(filePath).delete()
                    startTranslation(wavFile)
                }

                override fun onProgress(progress: Int, progressTime: Long) {
                    Log.e("FFmpeg", "转换文件进度:$progress")
                }

                override fun onCancel() {
                    resultBuff.append("取消转换文件格式\n")
                    translation_result.text = resultBuff
                }

                override fun onError(message: String?) {
                    resultBuff.append("不支持该格式文件\n")
                    translation_result.text = resultBuff
                }

            })
        }
    }


    override fun onDestroy() {
        recorder?.stopRecording()
        recorder = null
        CoroutineScope(Dispatchers.IO).launch {
            whisperContext?.release()
        }
        super.onDestroy()
    }
}