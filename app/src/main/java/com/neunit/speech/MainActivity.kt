package com.neunit.speech

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.neunit.commands.SpeechCommandsActivity
import com.neunit.translation.SpeechTranslationActivity
import com.neunit.tts.http.TTSSynthesisCallBack
import com.neunit.tts.http.TTSSynthesisInstance
import com.neunit.tts.model.TTSResultData
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private val tag = MainActivity::class.java.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speech_commands_btn.setOnClickListener {
            startActivity(Intent(this, SpeechCommandsActivity::class.java))
        }

        speech_translation_btn.setOnClickListener {
            startActivity(Intent(this, SpeechTranslationActivity::class.java))
        }

        tts_synthesis_btn.setOnClickListener {
            //startActivity(Intent(this, TTSActivity::class.java))
            val data =
                mapOf<String, Any?>("text" to "你好", "session_id" to UUID.randomUUID().toString(), "voice_type" to 0)
            val secretId = "d3578c4d2b324e68afcc3746bd39ce47"
            val secretKey = "9a0efdd06c06491d81e2953876122d63"
            TTSSynthesisInstance.ttsSynthesis(data, secretId, secretKey, object : TTSSynthesisCallBack {
                override fun callBack(code: Int, msg: String, data: TTSResultData?) {
                    Log.e(tag, "接收到合成结果:${data?.audio}")
                }
            })
        }
    }
}