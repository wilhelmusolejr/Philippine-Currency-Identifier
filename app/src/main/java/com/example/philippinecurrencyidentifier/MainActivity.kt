package com.example.philippinecurrencyidentifier

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale


class MainActivity : AppCompatActivity() {

    lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val continueBtn = findViewById<Button>(R.id.btnContinue)
        val termsMessage = getString(R.string.app_terms_conditions);

        val speechMessage = "Welcome to Philippine Currency Identifier. $termsMessage"
        val uniqueId = "welcoming"
        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener {
            if (it == TextToSpeech.SUCCESS) {
                val speechListener = object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) {
                    }

                    override fun onError(utteranceId: String?) {
                    }

                    override fun onStart(utteranceId: String?) {
                    }
                }

                speakNow(speechMessage, uniqueId, speechListener)
            }
        })

        continueBtn.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStop() {
        super.onStop()

        // Stops TTS when it is speaking
        if(tts.isSpeaking()) {
            tts.stop();
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shut down TTS
        if(tts != null){
            tts.shutdown();
        }
    }

    private fun speakNow(speechMessage: String, uniqueId: String, speechListener: UtteranceProgressListener) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)

        tts.language = Locale.US
        tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(speechListener)
        tts.speak(speechMessage, TextToSpeech.QUEUE_FLUSH, params, uniqueId)
    }
}