package com.example.learnenglish

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.ChatStat
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.Note
import com.example.learnenglish.data.VocabProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

const val XP_PER_LEVEL = 150

private val DAY_SHORT = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

data class AppBadge(
    val emoji: String,
    val title: String,
    val need: String,
    val unlocked: Boolean,
    val progress: String
)

data class WeekDay(
    val label: String,
    val tasks: Int,
    val messages: Int,
    val words: Int
)

data class AchievementState(
    val xp: Int = 0,
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val taskXp: Int = 0,
    val chatXp: Int = 0,
    val vocabXp: Int = 0,
    val noteXp: Int = 0,
    val totalMessages: Int = 0,
    val longestStreak: Int = 0,
    val todayMessages: Int = 0,
    val todayWords: Int = 0,
    val todayMistakes: Int = 0,
    val todayGood: Int = 0,
    val week: List<WeekDay> = emptyList(),
    val badges: List<AppBadge> = emptyList()
)

private fun badge(emoji: String, title: String, need: String, value: Int, target: Int): AppBadge =
    AppBadge(
        emoji = emoji,
        title = title,
        need = need,
        unlocked = value >= target,
        progress = minOf(value, target).toString() + "/" + target
    )

private fun longestStreak(daily: List<DailyProgress>): Int {
    val days = daily
        .filter { it.doneCount() > 0 }
        .mapNotNull { runCatching { LocalDate.parse(it.date).toEpochDay() }.getOrNull() }
        .distinct()
        .sorted()
    var best = 0
    var run = 0
    var prev = Long.MIN_VALUE
    for (d in days) {
        run = if (prev != Long.MIN_VALUE && d == prev + 1) run + 1 else 1
        if (run > best) best = run
        prev = d
    }
    return best
}

private fun computeAchievements(
    daily: List<DailyProgress>,
    vocab: List<VocabProgress>,
    notes: List<Note>,
    chats: List<ChatStat>
): AchievementState {
    val taskXp = daily.sumOf { it.doneCount() } * 10
    val totalMessages = chats.sumOf { it.userMessages }
    val goodMessages = chats.sumOf { it.goodMessages }
    val chatXp = totalMessages * 5 + goodMessages * 3
    val vocabXp = vocab.sumOf { it.correctCount } * 2
    val noteXp = notes.size * 5
    val xp = taskXp + chatXp + vocabXp + noteXp

    val today = LocalDate.now()
    val todayStat = chats.firstOrNull { it.day == today.toEpochDay() }
    val dailyByDate = daily.associateBy { it.date }
    val chatByDay = chats.associateBy { it.day }

    val week = (6 downTo 0).map { offset ->
        val d = today.minusDays(offset.toLong())
        val stat = chatByDay[d.toEpochDay()]
        WeekDay(
            label = DAY_SHORT[d.dayOfWeek.value - 1],
            tasks = dailyByDate[d.toString()]?.doneCount() ?: 0,
            messages = stat?.userMessages ?: 0,
            words = stat?.userWords ?: 0
        )
    }

    val streak = longestStreak(daily)
    val mastered = vocab.count { it.box >= 3 }

    val badges = listOf(
        badge("💬", "Langkah pertama", "Kirim 1 pesan di chat", totalMessages, 1),
        badge("🗣️", "Pembicara", "Kirim 50 pesan di chat", totalMessages, 50),
        badge("🎤", "Fasih bicara", "Kirim 200 pesan di chat", totalMessages, 200),
        badge("✨", "Teliti", "10 pesan tanpa kesalahan", goodMessages, 10),
        badge("🔥", "Streak 3 hari", "Aktif 3 hari beruntun", streak, 3),
        badge("🔥", "Streak 7 hari", "Aktif 7 hari beruntun", streak, 7),
        badge("🏆", "Streak 30 hari", "Aktif 30 hari beruntun", streak, 30),
        badge("📚", "Penghafal", "10 kata dikuasai", mastered, 10),
        badge("🎓", "Ahli kata", "30 kata dikuasai", mastered, 30),
        badge("📝", "Pencatat", "Simpan 10 catatan", notes.size, 10)
    )

    return AchievementState(
        xp = xp,
        level = xp / XP_PER_LEVEL + 1,
        xpIntoLevel = xp % XP_PER_LEVEL,
        taskXp = taskXp,
        chatXp = chatXp,
        vocabXp = vocabXp,
        noteXp = noteXp,
        totalMessages = totalMessages,
        longestStreak = streak,
        todayMessages = todayStat?.userMessages ?: 0,
        todayWords = todayStat?.userWords ?: 0,
        todayMistakes = todayStat?.mistakes ?: 0,
        todayGood = todayStat?.goodMessages ?: 0,
        week = week,
        badges = badges
    )
}

class AchievementsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val chatDao = db.chatStatDao()
    private var attached = false
    private val counted = HashSet<Long>()

    val state: StateFlow<AchievementState> = combine(
        db.dailyProgressDao().observeRecent(),
        db.vocabProgressDao().observeAll(),
        db.noteDao().observeAll(),
        chatDao.observeAll()
    ) { daily, vocab, notes, chats ->
        computeAchievements(daily, vocab, notes, chats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementState())

    fun attach(chatFlow: StateFlow<ChatState>) {
        if (attached) return
        attached = true
        viewModelScope.launch {
            chatFlow.collect { chatState -> record(chatState) }
        }
    }

    private suspend fun record(chatState: ChatState) {
        var userMessages = 0
        var words = 0
        var mistakes = 0
        var good = 0

        for (m in chatState.messages) {
            if (m.isError || counted.contains(m.id)) continue
            counted.add(m.id)
            if (m.fromUser) {
                userMessages++
                words += m.text.trim().split(Regex("\\s+")).size
            } else if (m.koreksi.isNotBlank()) {
                if (m.koreksi.startsWith("Tidak ada", ignoreCase = true)) good++ else mistakes++
            }
        }
        if (userMessages + mistakes + good == 0) return

        val today = LocalDate.now().toEpochDay()
        val old = chatDao.getByDay(today) ?: ChatStat(day = today)
        chatDao.upsert(
            old.copy(
                userMessages = old.userMessages + userMessages,
                userWords = old.userWords + words,
                mistakes = old.mistakes + mistakes,
                goodMessages = old.goodMessages + good
            )
        )
    }
}

@Composable
fun AchievementsScreen(vm: AchievementsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Kembali") }
        Text("Pencapaian", style = MaterialTheme.typography.headlineMedium)

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Level ${s.level}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "${s.xp} XP total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                LinearProgressIndicator(
                    progress = { s.xpIntoLevel / XP_PER_LEVEL.toFloat() },
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )
                Text(
                    "${s.xpIntoLevel} dari $XP_PER_LEVEL XP menuju level ${s.level + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Dari mana XP-mu", style = MaterialTheme.typography.titleMedium)
                Text("💬 Chat: ${s.chatXp} XP", style = MaterialTheme.typography.bodyLarge)
                Text("✅ Tugas harian: ${s.taskXp} XP", style = MaterialTheme.typography.bodyLarge)
                Text("📚 Kosakata: ${s.vocabXp} XP", style = MaterialTheme.typography.bodyLarge)
                Text("📝 Catatan: ${s.noteXp} XP", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Aturan: pesan chat +5 (+3 kalau tanpa kesalahan), tugas harian +10, jawaban kosakata benar +2, catatan +5. Tiap $XP_PER_LEVEL XP naik satu level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Ringkasan hari ini", style = MaterialTheme.typography.titleMedium)
                Text("Pesan yang kamu kirim: ${s.todayMessages}", style = MaterialTheme.typography.bodyLarge)
                Text("Kata yang kamu ucapkan: ${s.todayWords}", style = MaterialTheme.typography.bodyLarge)
                Text("Pesan dengan kesalahan: ${s.todayMistakes}", style = MaterialTheme.typography.bodyLarge)
                Text("Pesan tanpa kesalahan: ${s.todayGood}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("7 hari terakhir", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Hari", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("Tugas", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("Pesan", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("Kata", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                }
                s.week.forEach { d ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(d.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${d.tasks}/3", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${d.messages}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${d.words}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Streak terpanjangmu: ${s.longestStreak} hari",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Lencana", style = MaterialTheme.typography.titleMedium)
        s.badges.forEach { b ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (b.unlocked) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (b.unlocked) b.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            b.need,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (b.unlocked) "✅" else b.progress,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
