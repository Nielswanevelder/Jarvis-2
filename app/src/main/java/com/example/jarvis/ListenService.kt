package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ListenService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var awaiting = false
    private var running = true
    private var speaking = false

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val ch = "jarvis_listen"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Jarvis listening", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Jarvis is listening")
            .setContentText("Say Jarvis, then a command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(s: Int) {
        if (s != TextToSpeech.SUCCESS) { listen(); return }
        val t = tts ?: return
        t.language = Locale.UK
        val v = t.voices?.filter { it.locale.language == "en" && it.locale.country == "GB" }
        val male = v?.firstOrNull { Regex("gbd|rjs|male").containsMatchIn(it.name.lowercase()) } ?: v?.firstOrNull()
        if (male != null) t.voice = male
        t.setPitch(0.85f); t.setSpeechRate(0.98f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { h.post { speaking = false; listen() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { h.post { speaking = false; listen() } }
        })
        ttsReady = true
        listen()
    }

    private fun speak(t: String) {
        if (!ttsReady) { listen(); return }
        speaking = true
        rec?.destroy(); rec = null
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun listen() {
        if (!running || speaking) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                awaiting = false
                rec?.destroy(); rec = null
                val busy = e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
                h.postDelayed({ listen() }, if (busy) 3000L else 600L)
            }
            override fun onResults(r: Bundle?) {
                rec?.destroy(); rec = null
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t != null) heard(t) else h.postDelayed({ listen() }, 300)
            }
        })
        rec!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    private fun heard(t: String) {
        val low = t.lowercase()
        if (awaiting) { awaiting = false; run(t); return }
        val m = Regex("\\b(jarvis|jarvas|jarvus|jervis)\\b[ ,]*").find(low)
        if (m == null) { listen(); return }
        val cmd = low.substring(m.range.last + 1).trim()
        if (cmd.isEmpty()) { awaiting = true; speak("Yes?") } else run(cmd)
    }

    private fun run(cmd: String) {
        val l = Brain.local(this, cmd)
        if (l != null) { speak(l); return }
        Thread {
            val r = Brain.askClaude(this, cmd)
            h.post { speak(r) }
        }.start()
    }

    override fun onDestroy() {
        running = false
        rec?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}