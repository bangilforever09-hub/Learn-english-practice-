package com.example.learnenglish

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.VocabData
import com.example.learnenglish.data.VocabProgress
import com.example.learnenglish.data.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

const val SESSION_SIZE = 10
private const val MASTERED_BOX = 3
private val INTERVALS = intArrayOf(0, 1, 3, 7, 14)

enum class PracticeMode { Cards, Choice, Typing }

data class Question(val word: Word, val options: List<String>)

data class SessionState(
    val mode: PracticeMode? = null,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val correct: Int = 0,
    val revealed: Boolean = false,
    val answered: Boolean = false,
    val lastCorrect: Boolean = false,
    val selected: String? = null,
    val finished: Boolean = false
)

data class VocabStats(
    val total: Int,
    val fresh: Int,
    val due: Int,
    val mastered: Int,
    val answeredToday: Int
)

fun computeVocabStats(rows: List<VocabProgress>, today: Long): VocabStats {
    val total = VocabData.words.size
    return VocabStats(
        total = total,
        fresh = total - rows.size,
        due = rows.count { it.nextReview <= today },
        mastered = rows.count { it.box >= MASTERED_BOX },
        answeredToday = rows.count { it.lastAnswered == today }
    )
}

class Speaker(application: Application) : TextToSpeech.OnInitListener {
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
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learn_english")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

private fun pickWords(rows: List<VocabProgress>, today: Long): List<Word> {
    val byId = rows.associateBy { it.wordId }
    val all = VocabData.words
    val due = all
        .filter { (byId[it.id]?.nextReview ?: Long.MAX_VALUE) <= today }
        .sortedBy { byId[it.id]?.nextReview ?: 0L }
    val fresh = all.filter { byId[it.id] == null }
    val picked = (due + fresh).take(SESSION_SIZE)
    val filler = all
        .filter { w -> picked.none { it.id == w.id } }
        .shuffled()
        .take(SESSION_SIZE - picked.size)
    return (picked + filler).shuffled()
}

private fun makeQuestion(word: Word): Question {
    val distractors = VocabData.words
        .filter { it.id != word.id && it.meaning != word.meaning }
        .shuffled()
        .take(3)
        .map { it.meaning }
    return Question(word, (distractors + word.meaning).shuffled())
}

class VocabViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val vocabDao = db.vocabProgressDao()
    private val dailyDao = db.dailyProgressDao()
    private val speaker = Speaker(application)

    val progress: StateFlow<List<VocabProgress>> = vocabDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session

    fun speak(text: String) {
        speaker.speak(text)
    }

    fun start(mode: PracticeMode) {
        viewModelScope.launch {
            val rows = vocabDao.observeAll().first()
            val today = LocalDate.now().toEpochDay()
            val words = pickWords(rows, today)
            _session.value = SessionState(
                mode = mode,
                questions = words.map { makeQuestion(it) }
            )
        }
    }

    fun exit() {
        _session.value = SessionState()
    }

    fun reveal() {
        _session.value = _session.value.copy(revealed = true)
    }

    fun markCard(known: Boolean) {
        val s = _session.value
        val q = s.questions.getOrNull(s.index) ?: return
        recordAnswer(q.word, known)
        _session.value = s.copy(correct = s.correct + (if (known) 1 else 0))
        next()
    }

    fun answerChoice(option: String) {
        val s = _session.value
        if (s.answered) return
        val q = s.questions.getOrNull(s.index) ?: return
        val ok = option == q.word.meaning
        recordAnswer(q.word, ok)
        _session.value = s.copy(
            answered = true,
            lastCorrect = ok,
            selected = option,
            correct = s.correct + (if (ok) 1 else 0)
        )
    }

    fun answerTyping(text: String) {
        val s = _session.value
        if (s.answered) return
        val q = s.questions.getOrNull(s.index) ?: return
        val ok = text.trim().equals(q.word.word, ignoreCase = true)
        recordAnswer(q.word, ok)
        _session.value = s.copy(
            answered = true,
            lastCorrect = ok,
            correct = s.correct + (if (ok) 1 else 0)
        )
    }

    fun next() {
        val s = _session.value
        val nextIndex = s.index + 1
        _session.value = if (nextIndex >= s.questions.size) {
            s.copy(finished = true)
        } else {
            s.copy(
                index = nextIndex,
                revealed = false,
                answered = false,
                lastCorrect = false,
                selected = null
            )
        }
    }

    private fun recordAnswer(word: Word, isCorrect: Boolean) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val old = vocabDao.getById(word.id)
            val oldBox = old?.box ?: 0
            val newBox = if (isCorrect) minOf(oldBox + 1, INTERVALS.size - 1) else 0
            val nextReview = if (isCorrect) today + INTERVALS[newBox] else today
            vocabDao.upsert(
                VocabProgress(
                    wordId = word.id,
                    box = newBox,
                    nextReview = nextReview,
                    lastAnswered = today,
                    correctCount = (old?.correctCount ?: 0) + (if (isCorrect) 1 else 0),
                    wrongCount = (old?.wrongCount ?: 0) + (if (isCorrect) 0 else 1)
                )
            )

            if (vocabDao.countAnsweredOn(today) >= SESSION_SIZE) {
                val key = LocalDate.now().toString()
                val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
                if (!daily.vocabDone) {
                    dailyDao.upsert(daily.copy(vocabDone = true))
                }
            }
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
