package com.example.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Msg(val app: String, val sender: String, val text: String, val time: Long)

object MessageStore {
    val APPS = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger",
        "org.thoughtcrime.securesms" to "Signal",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Messages",
        "com.google.android.gm" to "Gmail",
        "com.microsoft.office.outlook" to "Outlook",
        "com.discord" to "Discord",
        "com.snapchat.android" to "Snapchat",
        "com.slack" to "Slack"
    )

    private fun prefs(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Msg> {
        val a = JSONArray(prefs(c).getString("msgs", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Msg(o.getString("a"), o.getString("s"), o.getString("t"), o.getLong("ts"))
        }
    }

    @Synchronized
    fun add(c: Context, m: Msg) {
        val l = all(c).toMutableList()
        val last = l.lastOrNull()
        if (last != null && last.sender == m.sender && last.text == m.text && m.time - last.time < 15000) return
        l.add(m)
        val a = JSONArray()
        l.takeLast(100).forEach {
            a.put(JSONObject().put("a", it.app).put("s", it.sender).put("t", it.text).put("ts", it.time))
        }
        prefs(c).edit().putString("msgs", a.toString()).apply()
    }

    fun lastRead(c: Context): Long = prefs(c).getLong("read", 0L)
    fun markRead(c: Context) { prefs(c).edit().putLong("read", System.currentTimeMillis()).apply() }
    fun unread(c: Context): List<Msg> = all(c).filter { it.time > lastRead(c) }

    fun getTasks(c: Context): MutableList<String> {
        val a = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until a.length()).map { a.getString(it) }.toMutableList()
    }
    fun saveTasks(c: Context, t: List<String>) {
        val a = JSONArray(); t.forEach { a.put(it) }
        prefs(c).edit().putString("tasks", a.toString()).apply()
    }
    fun getKey(c: Context): String = prefs(c).getString("key", "") ?: ""
    fun setKey(c: Context, k: String) { prefs(c).edit().putString("key", k.trim()).apply() }
}
