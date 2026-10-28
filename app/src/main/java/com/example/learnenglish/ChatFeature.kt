package com.example.learnenglish

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.Locale

// ---------- Penyimpanan pengaturan (API key dan model) ----------

private val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val KEY_API = stringPreferencesKey("gemini_api_key")
private val KEY_MODEL = stringPreferencesKey("gemini_model")
private const val DEFAULT_MODEL = "gemini-2.5-flash"
private const val FALLBACK_MODEL = "gemini-2.5-flash-lite"

// ---------- Data ----------

enum class ChatTopic(
    val label: String,
    val hint: String,
    val opening: String,
    val instruction: String
) {
    Daily(
        "Sehari-hari",
        "Obrolan santai: kegiatan, hobi, makanan",
        "Hi! How was your day so far?",
        "Topic: everyday conversation (daily life, hobbies, food, plans)."
    ),
    Work(
        "Kerja & karier",
        "Pekerjaan, rapat, wawancara kerja",
        "Hi! Can you tell me a little about your work, or the job you want?",
        "Topic: work and career (daily tasks, meetings, job interviews, goals)."
    ),
    Ielts(
        "IELTS/TOEFL speaking",
        "Latihan seperti ujian speaking",
        "Let's practice speaking for the exam. Part 1: can you tell me about your hometown?",
        "Topic: IELTS/TOEFL speaking practice. Act like a friendly examiner. Start with Part 1 style questions, then Part 2 style topics where the learner speaks for longer, then Part 3 style discussion questions. Encourage answers of 3 to 5 sentences."
    )
}

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val koreksi: String = "",
    val lebihNatural: String = "",
    val raw: String = "",
    val userText: String = "",
    val saved: Boolean = false,
    val isError: Boolean = false
)

data class ChatState(
    val topic: ChatTopic? = null,
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val listening: Boolean = false,
    val partial: String = "",
    val notice: String = "",
    val speakReplies: Boolean = true
)

data class SettingsUi(
    val message: String = "",
    val models: List<String> = emptyList(),
    val busy: Boolean = false
)

private class HttpResult(val code: Int, val body: String)

private class AiReply(
    val reply: String,
    val koreksi: String,
    val natural: String,
    val raw: String
)

private class GeminiOutcome(
    val reply: AiReply?,
    val error: String?,
    val retryable: Boolean = false,
    val stop: Boolean = false,
    val usedModel: String = ""
)

private class ModelsOutcome(val names: List<String>, val error: String?)

// ---------- Suara: teks ke suara dan suara ke teks ----------

private class ChatSpeaker(application: Application) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US) ?: TextToSpeech.ERROR
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED &&
                result != TextToSpeech.ERROR
        }
    }

    fun speak(text: String) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chat")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

private fun voiceErrorText(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        "Suara tidak terdengar. Ketuk 🎙 dan bicara lebih dekat ke mikrofon."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Izin mikrofon belum diberikan."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Pengenalan suara butuh internet. Cek koneksimu."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "Layanan suara sedang sibuk. Coba lagi sebentar."
    else ->
        "Pengenalan suara gagal (kode $code). Kamu tetap bisa mengetik."
}

private class VoiceInput(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_CLIENT) return
                onFailure(voiceErrorText(error))
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull() ?: ""
                if (text.isBlank()) {
                    onFailure("Suara tidak terdengar jelas. Ketuk 🎙 dan coba lagi.")
                } else {
                    onFinal(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    onPartial(text)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}

// ---------- Koneksi ke Gemini ----------

private fun systemPrompt(topic: ChatTopic): String = """
You are a friendly English conversation partner for an Indonesian learner. The learner's English is intermediate: they understand well but find speaking difficult.
${topic.instruction}
Use simple, clear English: short sentences and common words.
Reply ONLY with one JSON object that has exactly these string keys:
"reply": your natural reply in English, 1 to 3 short sentences. It MUST end with exactly one follow-up question.
"koreksi": a short note in simple Indonesian about grammar or word-choice mistakes in the learner's LAST message (name the mistake and the fix). If there are no mistakes, write "Tidak ada kesalahan. Bagus!".
"lebih_natural": a more natural way to say the learner's LAST message in English. If it is already natural, repeat it unchanged.
Do not write anything outside the JSON object.
The conversation started with you saying: "${topic.opening}"
""".trimIndent()

private fun buildRequest(system: String, history: List<ChatMessage>, jsonMode: Boolean): String {
    val turns = ArrayList<Pair<String, String>>()
    val usable = history.filter { !it.isError }.takeLast(16).dropWhile { !it.fromUser }
    for (m in usable) {
        val role = if (m.fromUser) "user" else "model"
        val text = if (m.fromUser) m.text else m.raw.ifBlank { m.text }
        val last = turns.lastOrNull()
        if (last != null && last.first == role) {
            turns[turns.size - 1] = Pair(role, last.second + "\n" + text)
        } else {
            turns.add(Pair(role, text))
        }
    }

    val contents = JSONArray()
    for (t in turns) {
        val part = JSONObject().put("text", t.second)
        contents.put(
            JSONObject()
                .put("role", t.first)
                .put("parts", JSONArray().put(part))
        )
    }

    val generation = JSONObject().put("temperature", 0.7)
    if (jsonMode) {
        generation.put("responseMimeType", "application/json")
    }

    val systemPart = JSONObject().put("text", system)
    return JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(systemPart)))
        .put("contents", contents)
        .put("generationConfig", generation)
        .toString()
}

private fun httpRequest(method: String, url: String, apiKey: String, body: String?): HttpResult {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        conn.setRequestProperty("x-goog-api-key", apiKey)
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return HttpResult(code, text)
    } finally {
        conn.disconnect()
    }
}

private fun apiErrorMessage(body: String): String {
    return try {
        JSONObject(body).optJSONObject("error")?.optString("message") ?: ""
    } catch (e: Exception) {
        ""
    }
}

private fun friendlyError(code: Int, apiMessage: String): String {
    val detail = if (apiMessage.isBlank()) "" else "\n(" + apiMessage.take(160) + ")"
    return when {
        code == 400 && apiMessage.contains("API key", ignoreCase = true) ->
            "API key tidak valid. Cek lagi di tab Setelan." + detail
        code == 400 ->
            "Permintaan ditolak (kode 400)." + detail
        code == 401 || code == 403 ->
            "API key ditolak atau belum aktif. Cek lagi di tab Setelan." + detail
        code == 404 ->
            "Model tidak ditemukan. Buka tab Setelan lalu ketuk 'Lihat model tersedia'." + detail
        code == 429 ->
            "Batas pemakaian gratis sedang tercapai. Tunggu sebentar lalu coba lagi." + detail
        code in 500..599 ->
            "Server Gemini sedang bermasalah. Coba lagi sebentar lagi." + detail
        else ->
            "Gagal menghubungi Gemini (kode $code)." + detail
    }
}

private fun extractText(body: String): String {
    val root = JSONObject(body)
    val parts = root.optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?: return ""
    val sb = StringBuilder()
    for (i in 0 until parts.length()) {
        sb.append(parts.optJSONObject(i)?.optString("text") ?: "")
    }
    return sb.toString()
}

private fun parseReply(raw: String): AiReply {
    val s = raw.trim()
    val start = s.indexOf('{')
    val end = s.lastIndexOf('}')
    if (start >= 0 && end > start) {
        try {
            val o = JSONObject(s.substring(start, end + 1))
            val reply = o.optString("reply").trim()
            if (reply.isNotEmpty()) {
                return AiReply(
                    reply = reply,
                    koreksi = o.optString("koreksi").trim(),
                    natural = o.optString("lebih_natural").trim(),
                    raw = raw
                )
            }
        } catch (e: Exception) {
            // jawaban bukan JSON, pakai teks apa adanya
        }
    }
    return AiReply(reply = s, koreksi = "", natural = "", raw = raw)
}

private fun cleanModel(model: String): String =
    model.trim().removePrefix("models/").replace(" ", "")

private fun callOnce(
    apiKey: String,
    model: String,
    system: String,
    history: List<ChatMessage>
): GeminiOutcome {
    return try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        var res = httpRequest("POST", url, apiKey, buildRequest(system, history, true))
        if (res.code == 400 && !apiErrorMessage(res.body).contains("API key", ignoreCase = true)) {
            res = httpRequest("POST", url, apiKey, buildRequest(system, history, false))
        }
        val code = res.code
        if (code in 200..299) {
            val text = extractText(res.body)
            if (text.isBlank()) {
                GeminiOutcome(
                    null,
                    "AI tidak memberi jawaban (mungkin terblokir filter keamanan). Coba kalimat lain.",
                    stop = true
                )
            } else {
                GeminiOutcome(parseReply(text), null, usedModel = model)
            }
        } else {
            val msg = apiErrorMessage(res.body)
            val keyProblem = code == 401 || code == 403 ||
                (code == 400 && msg.contains("API key", ignoreCase = true))
            GeminiOutcome(
                reply = null,
                error = friendlyError(code, msg),
                retryable = code == 429 || code in 500..599,
                stop = keyProblem || code == 400
            )
        }
    } catch (e: SocketTimeoutException) {
        GeminiOutcome(null, "Gemini terlalu lama menjawab (timeout).")
    } catch (e: UnknownHostException) {
        GeminiOutcome(null, "Tidak ada koneksi internet.", stop = true)
    } catch (e: Exception) {
        GeminiOutcome(null, "Gagal terhubung ke Gemini (" + e.javaClass.simpleName + ").")
    }
}

private suspend fun askGemini(
    apiKey: String,
    model: String,
    system: String,
    history: List<ChatMessage>
): GeminiOutcome = withContext(Dispatchers.IO) {
    val primary = cleanModel(model)
    val candidates = if (primary == FALLBACK_MODEL) {
        listOf(primary)
    } else {
        listOf(primary, FALLBACK_MODEL)
    }
    var last = GeminiOutcome(null, "Terjadi kesalahan yang tidak diketahui.")
    for (m in candidates) {
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val out = callOnce(apiKey, m, system, history)
            if (out.reply != null) return@withContext out
            last = out
            if (out.stop) return@withContext out
            if (!out.retryable) break
            if (attempt < 2) delay(2500)
        }
    }
    GeminiOutcome(
        null,
        (last.error ?: "Terjadi kesalahan.") + "\nSudah dicoba ulang dan memakai model cadangan."
    )
}

private fun fetchModels(apiKey: String): ModelsOutcome {
    return try {
        val res = httpRequest(
            "GET",
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
            apiKey,
            null
        )
        if (res.code !in 200..299) {
            ModelsOutcome(emptyList(), friendlyError(res.code, apiErrorMessage(res.body)))
        } else {
            val arr = JSONObject(res.body).optJSONArray("models")
            val names = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val name = m.optString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var canChat = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") canChat = true
                        }
                    }
                    val unwanted = listOf("image", "tts", "embedding", "live", "audio", "robotics", "computer", "vision")
                    val bad = unwanted.any { name.contains(it) }
                    if (canChat && name.startsWith("gemini") && !bad) {
                        names.add(name)
                    }
                }
            }
            ModelsOutcome(names.sortedBy { if (it.contains("flash")) 0 else 1 }.take(14), null)
        }
    } catch (e: Exception) {
        ModelsOutcome(emptyList(), "Tidak ada koneksi internet atau server tidak merespons.")
    }
}

// ---------- Otak fitur chat ----------

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val db = AppDatabase.get(application)
    private val noteDao = db.noteDao()
    private val dailyDao = db.dailyProgressDao()
    private val speaker = ChatSpeaker(application)
    private var idCounter = 1L
    private var sessionNo = 0

    private val _chat = MutableStateFlow(ChatState())
    val chat: StateFlow<ChatState> = _chat

    private val _settingsUi = MutableStateFlow(SettingsUi())
    val settingsUi: StateFlow<SettingsUi> = _settingsUi

    val apiKey: StateFlow<String> = application.chatDataStore.data
        .map { prefs -> prefs[KEY_API] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val model: StateFlow<String> = application.chatDataStore.data
        .map { prefs -> (prefs[KEY_MODEL] ?: "").ifBlank { DEFAULT_MODEL } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MODEL)

    private val voice = VoiceInput(
        context = application,
        onPartial = { text -> _chat.update { it.copy(partial = text) } },
        onFinal = { text -> handleVoiceResult(text) },
        onFailure = { message ->
            _chat.update { it.copy(listening = false, partial = "", notice = message) }
        }
    )

    fun speak(text: String) {
        speaker.speak(text)
    }

    fun setNotice(text: String) {
        _chat.update { it.copy(notice = text) }
    }

    fun setSpeakReplies(on: Boolean) {
        if (!on) speaker.stop()
        _chat.update { it.copy(speakReplies = on) }
    }

    fun startTopic(topic: ChatTopic) {
        sessionNo++
        val opening = ChatMessage(id = idCounter++, fromUser = false, text = topic.opening)
        _chat.update {
            it.copy(
                topic = topic,
                messages = listOf(opening),
                loading = false,
                listening = false,
                partial = "",
                notice = ""
            )
        }
        if (_chat.value.speakReplies) speaker.speak(topic.opening)
    }

    fun resetConversation() {
        sessionNo++
        voice.stop()
        speaker.stop()
        _chat.update { ChatState(speakReplies = it.speakReplies) }
    }

    fun startListening() {
        speaker.stop()
        if (!voice.isAvailable()) {
            _chat.update {
                it.copy(notice = "Pengenalan suara tidak tersedia di HP ini. Kamu tetap bisa mengetik.")
            }
            return
        }
        _chat.update { it.copy(listening = true, partial = "", notice = "") }
        voice.start()
    }

    fun stopListening() {
        voice.finish()
    }

    private fun handleVoiceResult(text: String) {
        _chat.update { it.copy(listening = false, partial = "", notice = "") }
        send(text)
    }

    private fun addError(message: String) {
        val err = ChatMessage(id = idCounter++, fromUser = false, text = message, isError = true)
        _chat.update { it.copy(messages = it.messages + err, loading = false) }
    }

    fun send(raw: String) {
        val text = raw.trim()
        val current = _chat.value
        val topic = current.topic ?: return
        if (text.isEmpty() || current.loading) return

        val mySession = sessionNo
        val userMsg = ChatMessage(id = idCounter++, fromUser = true, text = text)
        _chat.update {
            it.copy(messages = it.messages + userMsg, loading = true, partial = "", notice = "")
        }
        val history = _chat.value.messages

        viewModelScope.launch {
            val prefs = app.chatDataStore.data.first()
            val key = (prefs[KEY_API] ?: "").trim()
            val modelName = (prefs[KEY_MODEL] ?: "").trim().ifBlank { DEFAULT_MODEL }

            if (key.isEmpty()) {
                if (mySession == sessionNo) {
                    addError("Belum ada API key. Buka tab Setelan, isi API key Gemini, lalu coba lagi.")
                }
                return@launch
            }

            val outcome = askGemini(key, modelName, systemPrompt(topic), history)
            if (mySession != sessionNo) return@launch

            val reply = outcome.reply
            if (reply == null) {
                addError(outcome.error ?: "Terjadi kesalahan yang tidak diketahui.")
                return@launch
            }

            val aiMsg = ChatMessage(
                id = idCounter++,
                fromUser = false,
                text = reply.reply,
                koreksi = reply.koreksi,
                lebihNatural = reply.natural,
                raw = reply.raw,
                userText = text
            )
            val usedFallback = outcome.usedModel.isNotBlank() &&
                outcome.usedModel != cleanModel(modelName)
            val info = if (usedFallback) {
                "Model utama gagal, memakai model cadangan (" + outcome.usedModel + ")."
            } else {
                ""
            }
            _chat.update {
                it.copy(messages = it.messages + aiMsg, loading = false, notice = info)
            }
            if (_chat.value.speakReplies) speaker.speak(reply.reply)
            markConversationIfEnough()
        }
    }

    private suspend fun markConversationIfEnough() {
        val userCount = _chat.value.messages.count { it.fromUser }
        if (userCount < 3) return
        val key = LocalDate.now().toString()
        val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
        if (!daily.conversationDone) {
            dailyDao.upsert(daily.copy(conversationDone = true))
        }
    }

    fun saveToNotes(messageId: Long) {
        val msg = _chat.value.messages.firstOrNull { it.id == messageId } ?: return
        if (msg.saved || msg.fromUser || msg.isError) return

        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val noMistake = msg.koreksi.startsWith("Tidak ada", ignoreCase = true)
            val note = if (noMistake) {
                Note(
                    type = "Kalimat",
                    text = msg.lebihNatural.ifBlank { msg.userText },
                    extra = "",
                    createdDay = today,
                    reviewsDone = 0,
                    nextReview = today + 1
                )
            } else {
                Note(
                    type = "Kesalahan",
                    text = msg.userText.ifBlank { msg.text },
                    extra = ("Lebih natural: " + msg.lebihNatural + "\n" + msg.koreksi).trim(),
                    createdDay = today,
                    reviewsDone = 0,
                    nextReview = today + 1
                )
            }
            noteDao.insert(note)

            val key = LocalDate.now().toString()
            val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
            if (!daily.noteDone) {
                dailyDao.upsert(daily.copy(noteDone = true))
            }

            _chat.update { st ->
                st.copy(
                    messages = st.messages.map {
                        if (it.id == messageId) it.copy(saved = true) else it
                    }
                )
            }
        }
    }

    fun saveSettings(key: String, modelName: String) {
        viewModelScope.launch {
            app.chatDataStore.edit { prefs ->
                prefs[KEY_API] = key.trim()
                prefs[KEY_MODEL] = modelName.trim()
            }
            _settingsUi.update { it.copy(message = "Tersimpan ✅") }
        }
    }

    fun loadModels(typedKey: String) {
        val key = typedKey.trim()
        if (key.isEmpty()) {
            _settingsUi.update { it.copy(message = "Isi API key dulu.") }
            return
        }
        _settingsUi.update { it.copy(busy = true, message = "Memeriksa key dan daftar model...") }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { fetchModels(key) }
            _settingsUi.update {
                if (outcome.error != null) {
                    it.copy(busy = false, models = emptyList(), message = outcome.error)
                } else if (outcome.names.isEmpty()) {
                    it.copy(busy = false, models = emptyList(), message = "Key valid, tapi tidak ada model yang cocok ditemukan.")
                } else {
                    it.copy(busy = false, models = outcome.names, message = "Key valid ✅ Ketuk salah satu model, lalu Simpan.")
                }
            }
        }
    }

    override fun onCleared() {
        voice.stop()
        speaker.shutdown()
        super.onCleared()
    }
}

// ---------- Layar Chat ----------

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()

    if (chat.topic == null) {
        TopicPicker(
            hasKey = apiKey.isNotBlank(),
            onPick = { vm.startTopic(it) },
            onOpenSettings = onOpenSettings
        )
    } else {
        ConversationView(vm = vm, chat = chat)
    }
}

@Composable
private fun TopicPicker(
    hasKey: Boolean,
    onPick: (ChatTopic) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ngobrol dengan AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pilih topik, lalu bicara (🎙) atau ketik. AI menjawab dengan teks dan suara, lalu memberi koreksi singkat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Belum ada API key Gemini. Chat butuh key gratis dari Google.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onOpenSettings) { Text("Buka Setelan") }
                }
            }
        }

        ChatTopic.entries.forEach { topic ->
            Button(
                onClick = { onPick(topic) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(topic.label, style = MaterialTheme.typography.titleMedium)
                    Text(topic.hint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ConversationView(vm: ChatViewModel, chat: ChatState) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startListening()
        } else {
            vm.setNotice("Izin mikrofon ditolak. Aktifkan di Setelan HP, Aplikasi, Learn English Practice, Izin.")
        }
    }

    LaunchedEffect(chat.messages.size, chat.loading) {
        if (chat.messages.isNotEmpty()) {
            listState.animateScrollToItem(chat.messages.size - 1)
        }
    }

    fun onMicClick() {
        if (chat.listening) {
            vm.stopListening()
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            vm.startListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.resetConversation() }) { Text("← Topik") }
            Text(chat.topic?.label ?: "", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { vm.setSpeakReplies(!chat.speakReplies) }) {
                Text(if (chat.speakReplies) "🔊 Nyala" else "🔇 Mati")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chat.messages, key = { it.id }) { msg ->
                MessageItem(
                    msg = msg,
                    onSpeak = { vm.speak(it) },
                    onSave = { vm.saveToNotes(msg.id) }
                )
            }
        }

        if (chat.notice.isNotEmpty()) {
            Text(
                chat.notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (chat.loading) {
            Text(
                "AI sedang menulis… (kalau Google sibuk, aplikasi mencoba ulang sendiri)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (chat.listening) {
            Text(
                "🎙 Mendengarkan… bicaralah dalam bahasa Inggris, ketuk ⏹ kalau sudah selesai",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = if (chat.listening) chat.partial else input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik atau ketuk 🎙") },
                maxLines = 3,
                enabled = !chat.listening
            )
            OutlinedButton(
                onClick = { onMicClick() },
                enabled = !chat.loading || chat.listening,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (chat.listening) "⏹" else "🎙")
            }
            Button(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !chat.loading && !chat.listening,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Kirim")
            }
        }
    }
}

@Composable
private fun MessageItem(
    msg: ChatMessage,
    onSpeak: (String) -> Unit,
    onSave: () -> Unit
) {
    val align = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val colors = when {
        msg.isError -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        msg.fromUser -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
            Card(modifier = Modifier.widthIn(max = 320.dp), colors = colors) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                    if (!msg.fromUser && !msg.isError) {
                        TextButton(onClick = { onSpeak(msg.text) }) { Text("🔊 Dengar") }
                    }
                }
            }
        }

        if (!msg.fromUser && !msg.isError && msg.koreksi.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Koreksi: ${msg.koreksi}", style = MaterialTheme.typography.bodyMedium)
                    if (msg.lebihNatural.isNotBlank()) {
                        Text(
                            "Lebih natural: ${msg.lebihNatural}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onSpeak(msg.lebihNatural) }) {
                            Text("🔊 Dengar versi natural")
                        }
                    }
                    TextButton(onClick = onSave, enabled = !msg.saved) {
                        Text(if (msg.saved) "Tersimpan di Catatan ✅" else "Simpan ke Catatan")
                    }
                }
            }
        }
    }
}

// ---------- Layar Setelan ----------

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    val savedModel by vm.model.collectAsStateWithLifecycle()
    val ui by vm.settingsUi.collectAsStateWithLifecycle()

    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var model by remember(savedModel) { mutableStateOf(savedModel) }
    var showKey by remember { mutableStateOf(false) }
    var reminderHour by remember { mutableStateOf(Reminders.getHour(context)) }
    var reminderMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Setelan", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("API key Gemini (gratis)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. Buka aistudio.google.com/apikey di Chrome dan login dengan akun Google.\n" +
                        "2. Ketuk Create API key.\n" +
                        "3. Salin key-nya, lalu tempel di bawah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Sembunyikan key" else "Tampilkan key")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Model", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Nama model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { model = "gemini-2.5-flash" },
                        modifier = Modifier.weight(1f)
                    ) { Text("2.5 Flash") }
                    OutlinedButton(
                        onClick = { model = "gemini-2.5-flash-lite" },
                        modifier = Modifier.weight(1f)
                    ) { Text("2.5 Flash-Lite") }
                }
                Button(
                    onClick = { vm.loadModels(key) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lihat model tersedia")
                }
                ui.models.forEach { name ->
                    OutlinedButton(
                        onClick = { model = name },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(name) }
                }
            }
        }

        Button(
            onClick = { vm.saveSettings(key, model) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan")
        }
        if (ui.message.isNotEmpty()) {
            Text(ui.message, style = MaterialTheme.typography.bodyMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Pengingat harian", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Jam pengingat: " + reminderHour.toString().padStart(2, '0') + ":00",
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            reminderHour = (reminderHour + 23) % 24
                            reminderMsg = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("− 1 jam") }
                    OutlinedButton(
                        onClick = {
                            reminderHour = (reminderHour + 1) % 24
                            reminderMsg = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("+ 1 jam") }
                }
                Button(
                    onClick = {
                        Reminders.setHour(context, reminderHour)
                        reminderMsg = "Tersimpan ✅ Pengingat berikutnya pukul " +
                            reminderHour.toString().padStart(2, '0') + ":00."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simpan jam")
                }
                if (reminderMsg.isNotEmpty()) {
                    Text(reminderMsg, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Waktu tampil bisa meleset beberapa menit karena penghematan baterai HP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Catatan privasi", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Isi chat dikirim ke server Google. Di paket gratis, Google dapat memakai isinya untuk meningkatkan produknya, jadi jangan menulis data pribadi atau rahasia. API key hanya disimpan di HP ini. Jangan bagikan atau screenshot key-mu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
