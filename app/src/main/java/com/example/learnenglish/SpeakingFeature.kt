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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

// ---------- Data ----------

data class DrillSentence(val text: String, val category: String)

private val DRILL_SENTENCES = listOf(
    DrillSentence("I would like a cup of coffee, please.", "Sehari-hari"),
    DrillSentence("Could you tell me where the station is?", "Sehari-hari"),
    DrillSentence("I usually wake up early in the morning.", "Sehari-hari"),
    DrillSentence("The weather is really nice today.", "Sehari-hari"),
    DrillSentence("I am looking forward to the weekend.", "Sehari-hari"),
    DrillSentence("Can I pay by card?", "Sehari-hari"),
    DrillSentence("She works at a small company near my house.", "Sehari-hari"),
    DrillSentence("I have been learning English for a few months.", "Sehari-hari"),
    DrillSentence("Excuse me, how much is this?", "Sehari-hari"),
    DrillSentence("Let's meet in front of the library.", "Sehari-hari"),

    DrillSentence("I would like to schedule a meeting for next week.", "Kerja"),
    DrillSentence("Thank you for your quick response.", "Kerja"),
    DrillSentence("I am responsible for managing the project budget.", "Kerja"),
    DrillSentence("Could you send me the report by tomorrow afternoon?", "Kerja"),
    DrillSentence("Our team achieved the monthly target.", "Kerja"),
    DrillSentence("I have two years of experience in customer service.", "Kerja"),
    DrillSentence("I am available for an interview on Thursday morning.", "Kerja"),
    DrillSentence("We need to discuss the deadline with the client.", "Kerja"),
    DrillSentence("I appreciate your feedback on my presentation.", "Kerja"),
    DrillSentence("Please let me know if you have any questions.", "Kerja"),

    DrillSentence("I grew up in a small town near the coast.", "IELTS"),
    DrillSentence("In my opinion, technology has changed the way we communicate.", "IELTS"),
    DrillSentence("One advantage of living in a big city is better job opportunities.", "IELTS"),
    DrillSentence("I prefer studying in the morning because I can concentrate better.", "IELTS"),
    DrillSentence("Reading books is a great way to improve vocabulary.", "IELTS"),
    DrillSentence("Environmental protection is important for future generations.", "IELTS"),
    DrillSentence("I think education should be affordable for everyone.", "IELTS"),
    DrillSentence("Traveling abroad helps people understand different cultures.", "IELTS"),
    DrillSentence("Regular exercise has a significant impact on mental health.", "IELTS"),
    DrillSentence("Although it was difficult, I managed to finish the course.", "IELTS")
)

class DrillResult(
    val words: List<String>,
    val matched: List<Boolean>,
    val heard: String,
    val score: Int,
    val confidence: Int
)

data class SpeakState(
    val category: String = "Semua",
    val sentence: DrillSentence,
    val listening: Boolean = false,
    val partial: String = "",
    val result: DrillResult? = null,
    val message: String = "",
    val attempts: Int = 0,
    val scoreSum: Int = 0,
    val tips: String = "",
    val tipsLoading: Boolean = false,
    val saved: Boolean = false,
    val totalDrills: Int = 0,
    val bestScore: Int = 0
)

// ---------- Penilaian kata demi kata ----------

private fun normalizeWord(w: String): String =
    w.lowercase(Locale.US).filter { it.isLetterOrDigit() }

private fun alignWords(target: List<String>, heard: List<String>): BooleanArray {
    val n = target.size
    val m = heard.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (target[i - 1] == heard[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    val matched = BooleanArray(n)
    var i = n
    var j = m
    while (i > 0 && j > 0) {
        if (target[i - 1] == heard[j - 1]) {
            matched[i - 1] = true
            i--
            j--
        } else if (dp[i - 1][j] >= dp[i][j - 1]) {
            i--
        } else {
            j--
        }
    }
    return matched
}

private fun speakErrorText(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        "Suara tidak terdengar. Ketuk 🎙 lagi dan bicara lebih dekat ke mikrofon."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Izin mikrofon belum diberikan."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Pengenalan suara butuh internet. Cek koneksimu."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "Layanan suara sedang sibuk. Coba lagi sebentar."
    else ->
        "Pengenalan suara gagal (kode $code). Coba lagi."
}

// ---------- Suara contoh ----------

private class DrillSpeaker(application: Application) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initDone = false
    private var accent = "en-US"
    private var rate = 1.0f

    init {
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initDone = true
            applyVoice()
        }
    }

    private fun applyVoice() {
        var result = tts?.setLanguage(Locale.forLanguageTag(accent)) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED ||
            result == TextToSpeech.ERROR
        ) {
            result = tts?.setLanguage(Locale.US) ?: TextToSpeech.ERROR
        }
        ready = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED &&
            result != TextToSpeech.ERROR
        tts?.setSpeechRate(rate)
    }

    fun configure(newAccent: String, newRate: Float) {
        accent = newAccent
        rate = newRate
        if (initDone) applyVoice()
    }

    fun speak(text: String) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "drill")
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

// ---------- Tips pengucapan dari Gemini ----------

private fun askTips(apiKey: String, model: String, prompt: String): String {
    val primary = model.trim().removePrefix("models/").replace(" ", "")
    val candidates = listOf(primary, "gemini-2.5-flash-lite").distinct()
    var lastError = "Gagal menghubungi Gemini."

    for (m in candidates) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 45000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.doOutput = true

                val part = JSONObject().put("text", prompt)
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(part))
                        )
                    )
                    .put("generationConfig", JSONObject().put("temperature", 0.4))
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val out = JSONObject(text)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: ""
                    if (out.isNotBlank()) return out.trim()
                    lastError = "AI tidak memberi jawaban."
                } else if (code == 400 || code == 401 || code == 403) {
                    return "API key ditolak atau tidak valid (kode $code). Cek di tab Setelan."
                } else {
                    lastError = "Gemini sedang sibuk atau bermasalah (kode $code). Coba lagi sebentar lagi."
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            lastError = "Tidak bisa terhubung ke Gemini (" + e.javaClass.simpleName + ")."
        }
    }
    return lastError
}

// ---------- Otak latihan ucapan ----------

class SpeakingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val db = AppDatabase.get(application)
    private val noteDao = db.noteDao()
    private val dailyDao = db.dailyProgressDao()
    private val prefs = application.getSharedPreferences("speaking_prefs", Context.MODE_PRIVATE)
    private val speaker = DrillSpeaker(application)
    private var recognizer: SpeechRecognizer? = null
    private var pool: List<DrillSentence> = DRILL_SENTENCES.shuffled()
    private var poolIndex = 0

    private val _state = MutableStateFlow(
        SpeakState(
            sentence = pool[0],
            totalDrills = prefs.getInt("total", 0),
            bestScore = prefs.getInt("best", 0)
        )
    )
    val state: StateFlow<SpeakState> = _state

    fun configureVoice(accent: String, rate: Float) {
        speaker.configure(accent, rate)
    }

    fun setMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun hearTarget() {
        speaker.speak(_state.value.sentence.text)
    }

    fun hearWord(word: String) {
        speaker.speak(word)
    }

    fun selectCategory(category: String) {
        stopAll()
        pool = (if (category == "Semua") {
            DRILL_SENTENCES
        } else {
            DRILL_SENTENCES.filter { it.category == category }
        }).shuffled()
        poolIndex = 0
        _state.update {
            it.copy(
                category = category,
                sentence = pool[0],
                result = null,
                partial = "",
                message = "",
                tips = "",
                saved = false
            )
        }
    }

    fun nextSentence() {
        stopAll()
        poolIndex = (poolIndex + 1) % pool.size
        _state.update {
            it.copy(
                sentence = pool[poolIndex],
                result = null,
                partial = "",
                message = "",
                tips = "",
                saved = false
            )
        }
    }

    fun startListening() {
        speaker.stop()
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            setMessage("Pengenalan suara tidak tersedia di HP ini.")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
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
                _state.update {
                    it.copy(listening = false, partial = "", message = speakErrorText(error))
                }
            }

            override fun onResults(results: Bundle?) {
                handleResults(results)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    _state.update { it.copy(partial = text) }
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        _state.update {
            it.copy(
                listening = true,
                partial = "",
                result = null,
                message = "",
                tips = "",
                saved = false
            )
        }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun stopAll() {
        recognizer?.destroy()
        recognizer = null
        speaker.stop()
        _state.update { it.copy(listening = false, partial = "") }
    }

    private fun handleResults(results: Bundle?) {
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val heard = list?.firstOrNull() ?: ""
        val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: -1f

        if (heard.isBlank()) {
            _state.update {
                it.copy(
                    listening = false,
                    partial = "",
                    message = "Suara tidak terdengar jelas. Ketuk 🎙 lagi dan bicara lebih dekat ke mikrofon."
                )
            }
            return
        }

        val sentenceText = _state.value.sentence.text
        val words = sentenceText.split(" ").filter { normalizeWord(it).isNotEmpty() }
        val target = words.map { normalizeWord(it) }
        val heardWords = heard.split(" ").map { normalizeWord(it) }.filter { it.isNotEmpty() }
        val matched = alignWords(target, heardWords)
        val matchedCount = matched.count { it }
        val score = if (target.isEmpty()) 0 else matchedCount * 100 / target.size

        val total = prefs.getInt("total", 0) + 1
        val best = maxOf(prefs.getInt("best", 0), score)
        prefs.edit().putInt("total", total).putInt("best", best).apply()

        val result = DrillResult(
            words = words,
            matched = matched.toList(),
            heard = heard,
            score = score,
            confidence = if (conf > 0f) (conf * 100).toInt() else -1
        )
        _state.update {
            it.copy(
                listening = false,
                partial = "",
                result = result,
                message = "",
                attempts = it.attempts + 1,
                scoreSum = it.scoreSum + score,
                totalDrills = total,
                bestScore = best
            )
        }
    }

    private fun missedWords(res: DrillResult): List<String> =
        res.words
            .filterIndexed { i, _ -> !res.matched[i] }
            .map { normalizeWord(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    fun loadTips(apiKey: String, model: String) {
        val st = _state.value
        val res = st.result ?: return
        val missed = missedWords(res)
        if (missed.isEmpty()) {
            _state.update { it.copy(tips = "Semua katamu sudah terdengar jelas ✅") }
            return
        }
        if (apiKey.isBlank()) {
            _state.update { it.copy(tips = "Isi API key Gemini di tab Setelan dulu.") }
            return
        }
        _state.update { it.copy(tipsLoading = true, tips = "") }

        val prompt = "You are an English pronunciation coach for an Indonesian learner. " +
            "The learner tried to say: \"" + st.sentence.text + "\". " +
            "A speech recognizer heard: \"" + res.heard + "\". " +
            "Words the recognizer did not catch: " + missed.joinToString(", ") + ". " +
            "For each of these words give: how to say it in simple syllables with the stressed syllable in CAPITAL letters, " +
            "and one short tip about the hardest sound for Indonesian speakers. " +
            "Write the answer in simple Indonesian, at most 2 lines per word, with no introduction."

        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { askTips(apiKey, model, prompt) }
            _state.update { it.copy(tipsLoading = false, tips = text) }
        }
    }

    fun saveHardWords() {
        val st = _state.value
        val res = st.result ?: return
        if (st.saved) return
        val missed = missedWords(res)
        if (missed.isEmpty()) return

        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            for (w in missed) {
                noteDao.insert(
                    Note(
                        type = "Kata",
                        text = w,
                        extra = "Sulit diucapkan. Kalimat: " + st.sentence.text,
                        createdDay = today,
                        reviewsDone = 0,
                        nextReview = today + 1
                    )
                )
            }
            val key = LocalDate.now().toString()
            val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
            if (!daily.noteDone) {
                dailyDao.upsert(daily.copy(noteDone = true))
            }
            _state.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        recognizer?.destroy()
        speaker.shutdown()
        super.onCleared()
    }
}

// ---------- Layar Latihan ucapan ----------

private fun scoreLabel(score: Int): String = when {
    score >= 90 -> "🎉 Sangat jelas!"
    score >= 70 -> "👍 Bagus, hampir sempurna"
    score >= 50 -> "🙂 Cukup. Ulangi dan perhatikan kata merah"
    else -> "💪 Coba lagi pelan-pelan"
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 90 -> MaterialTheme.colorScheme.tertiary
    score >= 70 -> MaterialTheme.colorScheme.primary
    score >= 50 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
fun SpeakingPage(vm: SpeakingViewModel, chatVm: ChatViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val apiKey by chatVm.apiKey.collectAsStateWithLifecycle()
    val model by chatVm.model.collectAsStateWithLifecycle()
    val accent by chatVm.accent.collectAsStateWithLifecycle()
    val rate by chatVm.rate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(accent, rate) {
        vm.configureVoice(accent, rate.toFloatOrNull() ?: 1.0f)
    }

    DisposableEffect(Unit) {
        onDispose { vm.stopAll() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.stopAll()
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startListening()
        } else {
            vm.setMessage("Izin mikrofon ditolak. Aktifkan di Setelan HP, Aplikasi, Learn English Practice, Izin.")
        }
    }

    fun onMicClick() {
        if (s.listening) {
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

    val result = s.result
    val avg = if (s.attempts > 0) s.scoreSum / s.attempts else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Kembali") }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🎤 Latihan ucapan",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Ucapkan kalimat dengan jelas. Skor = seberapa banyak katamu berhasil dikenali pengenal suara. Ini ukuran bisa-dimengerti, bukan nilai resmi ujian.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Sesi ini: ${s.attempts} kalimat · rata-rata $avg%   |   Total: ${s.totalDrills} · rekor ${s.bestScore}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Semua", "Sehari-hari", "Kerja", "IELTS").forEach { c ->
                CategoryChip(label = c, selected = s.category == c) { vm.selectCategory(c) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Ucapkan kalimat ini (${s.sentence.category})",
                    style = MaterialTheme.typography.labelLarge
                )
                if (result == null) {
                    Text(
                        s.sentence.text,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val okColor = MaterialTheme.colorScheme.tertiary
                    val badColor = MaterialTheme.colorScheme.error
                    val annotated = buildAnnotatedString {
                        result.words.forEachIndexed { i, w ->
                            if (result.matched[i]) {
                                withStyle(SpanStyle(color = okColor, fontWeight = FontWeight.Medium)) {
                                    append(w)
                                }
                            } else {
                                withStyle(SpanStyle(color = badColor, fontWeight = FontWeight.Bold)) {
                                    append(w)
                                }
                            }
                            append(" ")
                        }
                    }
                    Text(
                        annotated,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(onClick = { vm.hearTarget() }) {
                    Text("🔊 Dengar contoh")
                }
            }
        }

        Button(
            onClick = { onMicClick() },
            colors = if (s.listening) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            Text(
                if (s.listening) "⏹ Selesai bicara" else "🎙 Ucapkan",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (s.listening) {
            Text(
                if (s.partial.isBlank()) "🎙 Mendengarkan… ucapkan kalimatnya sekarang" else "Terdengar: " + s.partial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (s.message.isNotEmpty()) {
            Text(
                s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (result != null) {
            val missed = result.words
                .filterIndexed { i, _ -> !result.matched[i] }
                .map { normalizeWord(it) }
                .filter { it.isNotEmpty() }
                .distinct()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${result.score}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = scoreColor(result.score)
                    )
                    Text(scoreLabel(result.score), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Yang terdengar: \"${result.heard}\"",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (result.confidence > 0) {
                        Text(
                            "Keyakinan pengenal suara: ${result.confidence}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Hijau = terdengar jelas, merah = tidak dikenali.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (missed.isNotEmpty()) {
                Text("Kata yang perlu dilatih:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    missed.forEach { w ->
                        OutlinedButton(onClick = { vm.hearWord(w) }) { Text("🔊 $w") }
                    }
                }

                Button(
                    onClick = { vm.loadTips(apiKey, model) },
                    enabled = !s.tipsLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (s.tipsLoading) "Meminta tips…" else "💡 Tips pengucapan (AI)")
                }

                if (s.tips.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(
                            s.tips,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { vm.saveHardWords() },
                    enabled = !s.saved,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (s.saved) "Tersimpan di Catatan ✅" else "📝 Simpan kata merah ke Catatan")
                }
            }

            Button(
                onClick = { vm.nextSentence() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Kalimat berikutnya →")
            }
        } else {
            OutlinedButton(
                onClick = { vm.nextSentence() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lewati, kalimat lain")
            }
        }
    }
}
