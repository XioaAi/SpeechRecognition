package com.neunit.speech

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neunit.commands.SpeechCommandsActivity
import com.neunit.translation.SpeechTranslationActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speech_commands_btn.setOnClickListener {
            startActivity(Intent(this, SpeechCommandsActivity::class.java))
        }

        speech_translation_btn.setOnClickListener {
            startActivity(Intent(this, SpeechTranslationActivity::class.java))
        }
    }
}