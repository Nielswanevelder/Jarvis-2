package com.example.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Brain {
    private fun plural(n: Int, w: String) = "$n $w" + if (n == 1) "" else "s"

    fun local(c: Context, q0: String): String? {
        val q = q0.lowercase().replace(Regex("[.,!?]"), "").trim()
        if (q.isEmpty()) return "I'm listening."

        Regex("^add (?:a )?(?:task|todo|reminder)(?: to)? (.+)").find(q)?.let {
            val t = MessageStore.getTasks(c); t.add(it.groupValues[1]); MessageStore.saveTasks(c, t)
            return "Added task: ${it.groupValues[1]}."
        }
        Regex("^(?:done|finish|finished|complete) (?:with )?(.+)").find(q)?.let {
            val t = MessageStore.getTasks(c)
            val w = it.groupValues[1].split(" ").filter { x -> x.length > 2 }
            val hit = t.firstOrNull { x -> w.any { y -> x.contains(y) } }
            if (hit != null) { t.remove(hit); MessageStore.saveTasks(c, t); return "Done. Removed $hit." }
            return "I couldn't find that task."
        }
        if (Regex("my tasks|to do list|todo list|what do i need").containsMatchIn(q)) {
            val t = MessageStore.getTasks(c)
            return if (t.isEmpty()) "You have no open tasks." else "You have ${plural(t.size, "task")}: ${t.joinToString(", ")}."
        }
        if (Regex("what time|the time").containsMatchIn(q))
            return "It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        if (Regex("mark.*read|clear (my )?(messages|notifications)").containsMatchIn(q)) {
            MessageStore.markRead(c); return "Done. All messages marked as read."
        }
        if (Regex("message|inbox|texted|texts|whatsapp|telegram|instagram|notification|anything new|who wrote").containsMatchIn(q))
            return messages(c, q)
        if (Regex("^(hi|hello|hey)\\b").containsMatchIn(q)) return "At your service."
        if (q.startsWith("help")) return "Ask: any messages? Read my messages. Messages from Sam. Any WhatsApp? Add task call mom. My tasks. Done with call mom. Mark messages as read."
        return null
    }

    fun messages(c: Context, q: String): String {
        val wantsOld = Regex("recent|earlier|all|old").containsMatchIn(q)
        var list = if (wantsOld) MessageStore.all(c) else MessageStore.unread(c)
        MessageStore.APPS.values.toSet().forEach { app ->
            if (q.contains(app.lowercase())) list = list.filter { it.app == app }
        }
        Regex("from (\\w+)").find(q)?.let { m ->
            val n = m.groupValues[1]
            list = list.filter { it.sender.lowercase().contains(n) }
        }
        if (list.isEmpty()) return if (wantsOld) "I have no stored messages." else "No new messages. Say recent messages to hear older ones."
        val groups = list.groupBy { it.sender + "|" + it.app }.entries.sortedByDescending { it.value.size }
        val summary = groups.take(6).joinToString(", ") {
            val f = it.value.first(); "${it.value.size} from ${f.sender} on ${f.app}"
        }
        val head = "You have ${plural(list.size, "message")}: $summary."
        return if (Regex("read|say|tell me|what did").containsMatchIn(q))
            head + " " + list.takeLast(5).joinToString(" ") { "${it.sender} on ${it.app}: ${it.text}." }
        else "$head Say read them to hear the messages."
    }

    fun askClaude(c: Context, question: String): String {
        val key = MessageStore.getKey(c)
        if (key.isEmpty()) return "I didn't catch a command. Say help for examples, or add an AI key for free-form questions."
        return try {
            val msgs = MessageStore.all(c).takeLast(20).joinToString("\n") { "${it.app} | ${it.sender}: ${it.text}" }
            val tasks = MessageStore.getTasks(c).joinToString("; ")
            val sys = "You are Jarvis, a calm, concise voice assistant. Your reply is spoken aloud: 1 to 3 short plain sentences, no markdown."
            val user = "Recent message previews:\n$msgs\n\nTasks: $tasks\n\nUser: $question"
            val body = JSONObject()
                .put("model", "claude-haiku-5-5").put("max_tokens", 300).put("system", sys)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("content-type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val ok = conn.responseCode in 200..299
            val txt = (if (ok) conn.inputStream else conn.errorStream).bufferedReader().readText()
            if (!ok) return "The AI service returned an error. Check your key."
            JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text")
        } catch (e: Exception) { "I couldn't reach the AI service." }
    }
}
