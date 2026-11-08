package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var access: TextView
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private var rec: SpeechRecognizer? = null
    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16)); fitsSystemWindows = true
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { f() }
        }
        root.addView(TextView(this).apply { text = "Jarvis"; textSize = 26f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        access = TextView(this).apply { textSize = 13f }
        root.addView(access)
        val row = LinearLayout(this)
        row.addView(btn("Enable message access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(btn("AI key") { keyDialog() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.addView(row)
        root.addView(btn("Brief me on my messages") { handle("read my messages") })
        scroll = ScrollView(this)
        log = TextView(this).apply { textSize = 15f; setPadding(0, dp(8), 0, dp(8)) }
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 13f; text = "Tap the mic and speak" }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "\uD83C\uDF99  Tap to talk"; textSize = 20f; isAllCaps = false
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#2F6BFF"))
            setOnClickListener { listen() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(72)))
        val inRow = LinearLayout(this)
        input = EditText(this).apply { hint = "Or type a command"; maxLines = 1; inputType = InputType.TYPE_CLASS_TEXT }
        inRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inRow.addView(btn("Send") { val t = input.text.toString(); input.setText(""); if (t.isNotBlank()) handle(t) })
        root.addView(inRow)
        setContentView(root)
        tts = TextToSpeech(this, this)
        reply("Good day. I'm Jarvis. Enable message access, then ask me: any messages?")
    }

    override fun onInit(s: Int) {
        if (s != TextToSpeech.SUCCESS) return
        ttsReady = true
        tts.language = Locale.UK
        val v = tts.voices?.filter { it.locale.language == "en" && it.locale.country == "GB" }
        val male = v?.firstOrNull { Regex("gbd|rjs|male").containsMatchIn(it.name.lowercase()) } ?: v?.firstOrNull()
        if (male != null) tts.voice = male
        tts.setPitch(0.85f); tts.setSpeechRate(0.98f)
    }

    override fun onResume() {
        super.onResume()
        val on = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        access.text = if (on) "Message access: ON (WhatsApp, SMS, Telegram, Instagram...)" else "Message access: OFF. Tap Enable, then switch on Jarvis."
    }

    private fun reply(t: String) {
        log.append("Jarvis: $t\n\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        if (ttsReady) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun handle(q: String) {
        log.append("You: $q\n\n")
        val l = Brain.local(this, q)
        if (l != null) { reply(l); return }
        status.text = "Thinking..."
        Thread {
            val r = Brain.askClaude(this, q)
            runOnUiThread { status.text = "Tap the mic and speak"; reply(r) }
        }.start()
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { reply("Speech recognition isn't available on this phone."); return }
        if (ttsReady) tts.stop()
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text = "Listening..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Tap the mic and speak" }
            override fun onError(e: Int) { status.text = "Didn't catch that. Tap and try again." }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t != null) handle(t)
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        rec!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(c, p, g)
        if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
    }

    private fun keyDialog() {
        val e = EditText(this).apply { hint = "Anthropic API key (optional)"; setText(MessageStore.getKey(this@MainActivity)) }
        AlertDialog.Builder(this)
            .setTitle("AI brain (optional)")
            .setMessage("With a key, questions Jarvis doesn't recognise go to Claude, along with your recent message previews and tasks.")
            .setView(e)
            .setPositiveButton("Save") { _, _ -> MessageStore.setKey(this, e.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() { rec?.destroy(); tts.shutdown(); super.onDestroy() }
}
