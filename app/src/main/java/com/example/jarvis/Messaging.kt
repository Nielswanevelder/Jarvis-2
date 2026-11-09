package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.SmsManager

object Messaging {
    private class Pending(val run: () -> String)
    private var pending: Pending? = null
    private val BAD = setOf("messages", "message", "from", "me", "my", "texts", "back", "to", "the")

    fun handle(c: Context, q0: String): String? {
        val q = q0.trim().replace(Regex("[.!?]+$"), "")
        val p = pending
        if (p != null) {
            val l = q.lowercase()
            if (Regex("^(yes|yeah|yep|send|send it|confirm|do it|go ahead)$").matches(l)) { pending = null; return p.run() }
            if (Regex("^(no|nope|cancel|stop|dont|don't|never mind|nevermind)$").matches(l)) { pending = null; return "Cancelled. Nothing was sent." }
            pending = null
        }
        val m = Regex("^(send (?:a |an )?(?:new )?(?:message|text|sms|whatsapp)(?: message)? to|text|whatsapp|reply to) (\\w+)(?: saying| that says| says| that|:|,)? (.+)$", RegexOption.IGNORE_CASE).find(q) ?: return null
        val verb = m.groupValues[1].lowercase()
        val name = m.groupValues[2]
        val body = m.groupValues[3].trim()
        if (name.lowercase() in BAD) return null
        val wa = verb.contains("whatsapp") || verb == "reply to"
        val direct = Replies.find(name)
        if (wa && direct != null) {
            val who = direct.key
            pending = Pending { if (Replies.send(c, who, body)) "Sent to $who." else "I couldn't send that reply." }
            return "Reply to $who: $body. Say yes to send, or no to cancel."
        }
        if (verb == "reply to") return "I can only reply to people who messaged you recently, and I can't see a recent message from $name."
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return "I need contacts and text permission. Open the app and tap Allow sending messages."
        val contact = find(c, name) ?: return "I couldn't find a contact called $name."
        if (wa) {
            pending = Pending { openWhatsApp(c, contact.second, body) }
            return "WhatsApp message to ${contact.first}: $body. Say yes and I will open the chat for you to tap send."
        }
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return "I need contacts and text permission. Open the app and tap Allow sending messages."
        pending = Pending { sms(c, contact.second, body, contact.first) }
        return "Text to ${contact.first}: $body. Say yes to send, or no to cancel."
    }

    private fun find(c: Context, name: String): Pair<String, String>? {
        c.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        )?.use {
            if (it.moveToFirst()) return Pair(it.getString(0), it.getString(1))
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun smsManager(c: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    private fun sms(c: Context, number: String, text: String, name: String): String = try {
        val sm = smsManager(c)
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
        "Text sent to $name."
    } catch (e: Exception) { "I couldn't send that text." }

    private fun openWhatsApp(c: Context, number: String, text: String): String = try {
        val digits = number.filter { it.isDigit() }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
        "Opened WhatsApp. Tap send."
    } catch (e: Exception) { "I couldn't open WhatsApp." }
}
