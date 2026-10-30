package com.example.learnenglish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnenglish.data.Word
import java.time.LocalDate

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Sehari-hari" -> MaterialTheme.colorScheme.primaryContainer
    "Kerja" -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

private fun categoryEmoji(category: String): String = when (category) {
    "Sehari-hari" -> "🏠"
    "Kerja" -> "💼"
    else -> "🎓"
}

@Composable
fun PracticeScreen(vm: VocabViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val rows by vm.progress.collectAsStateWithLifecycle()
    val stats = computeVocabStats(rows, LocalDate.now().toEpochDay())
    val mode = session.mode

    if (mode == null) {
        PracticeMenu(stats = stats, onStart = { vm.start(it) })
        return
    }
    if (session.finished) {
        SummaryView(session = session, mode = mode, stats = stats, vm = vm)
        return
    }
    val q = session.questions.getOrNull(session.index)
    if (q == null) {
        PracticeMenu(stats = stats, onStart = { vm.start(it) })
        return
    }
    when (mode) {
        PracticeMode.Cards -> CardView(session, q, vm)
        PracticeMode.Choice -> ChoiceView(session, q, vm)
        PracticeMode.Typing -> TypingView(session, q, vm)
    }
}

@Composable
private fun PracticeMenu(stats: VocabStats, onStart: (PracticeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    "📚 Latihan kosakata",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Kata hari ini: ${stats.answeredToday} dari $SESSION_SIZE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                LinearProgressIndicator(
                    progress = { minOf(stats.answeredToday, SESSION_SIZE) / SESSION_SIZE.toFloat() },
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip(
                label = "Kata baru",
                value = "${stats.fresh}",
                container = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatChip(
                label = "Diulang",
                value = "${stats.due}",
                container = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatChip(
                label = "Dikuasai",
                value = "${stats.mastered}/${stats.total}",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "Satu sesi = $SESSION_SIZE kata. Jawab $SESSION_SIZE kata berbeda hari ini, maka tugas kosakata di Checklist tercentang otomatis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ModeCard(
            emoji = "🃏",
            title = "Kartu kata",
            subtitle = "Lihat kata, ingat artinya, lalu nilai dirimu",
            container = MaterialTheme.colorScheme.primaryContainer
        ) { onStart(PracticeMode.Cards) }
        ModeCard(
            emoji = "✅",
            title = "Kuis pilihan ganda",
            subtitle = "Pilih arti yang benar",
            container = MaterialTheme.colorScheme.tertiaryContainer
        ) { onStart(PracticeMode.Choice) }
        ModeCard(
            emoji = "⌨️",
            title = "Kuis isian",
            subtitle = "Tulis kata bahasa Inggrisnya",
            container = MaterialTheme.colorScheme.secondaryContainer
        ) { onStart(PracticeMode.Typing) }
    }
}

@Composable
private fun StatChip(label: String, value: String, container: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(container, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    container: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SessionHeader(index: Int, total: Int, onExit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kata ${index + 1} dari $total", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onExit) { Text("Keluar") }
        }
        LinearProgressIndicator(
            progress = { index / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

@Composable
private fun ExampleBlock(word: Word, onSpeak: (String) -> Unit) {
    var showTranslation by remember(word.id) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            word.example,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onSpeak(word.example) }) { Text("🔊 Dengar") }
            if (word.exampleId.isNotBlank()) {
                TextButton(onClick = { showTranslation = !showTranslation }) {
                    Text(if (showTranslation) "Sembunyikan" else "🌐 Terjemahkan")
                }
            }
        }
        if (showTranslation && word.exampleId.isNotBlank()) {
            Text(
                word.exampleId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CardView(session: SessionState, q: Question, vm: VocabViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SessionHeader(session.index, session.questions.size) { vm.exit() }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = categoryColor(q.word.category))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    categoryEmoji(q.word.category) + " " + q.word.category,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    q.word.word,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = { vm.speak(q.word.word) }) {
                    Text("🔊 Dengar")
                }
                if (session.revealed) {
                    Text(
                        q.word.meaning,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    ExampleBlock(q.word) { vm.speak(it) }
                }
            }
        }

        if (!session.revealed) {
            Button(
                onClick = { vm.reveal() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text("Tampilkan arti")
            }
        } else {
            Text("Seberapa ingat kamu?", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { vm.markCard(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Belum hafal")
                }
                Button(
                    onClick = { vm.markCard(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Hafal")
                }
            }
        }
    }
}

@Composable
private fun ChoiceView(session: SessionState, q: Question, vm: VocabViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SessionHeader(session.index, session.questions.size) { vm.exit() }

        Text("Apa arti kata ini?", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = categoryColor(q.word.category))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    categoryEmoji(q.word.category) + " " + q.word.category,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    q.word.word,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = { vm.speak(q.word.word) }) {
                    Text("🔊 Dengar")
                }
            }
        }

        q.options.forEach { option ->
            val container: Color
            val content: Color
            if (!session.answered) {
                container = MaterialTheme.colorScheme.secondaryContainer
                content = MaterialTheme.colorScheme.onSecondaryContainer
            } else if (option == q.word.meaning) {
                container = MaterialTheme.colorScheme.tertiary
                content = MaterialTheme.colorScheme.onTertiary
            } else if (option == session.selected) {
                container = MaterialTheme.colorScheme.error
                content = MaterialTheme.colorScheme.onError
            } else {
                container = MaterialTheme.colorScheme.surfaceVariant
                content = MaterialTheme.colorScheme.onSurfaceVariant
            }
            val label = when {
                !session.answered -> option
                option == q.word.meaning -> "✅ $option"
                option == session.selected -> "❌ $option"
                else -> option
            }
            Button(
                onClick = { vm.answerChoice(option) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = content
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }
        }

        if (session.answered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (session.lastCorrect) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (session.lastCorrect) "🎉 Benar!" else "Belum tepat. Jawaban: ${q.word.meaning}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    ExampleBlock(q.word) { vm.speak(it) }
                }
            }
            Button(
                onClick = { vm.next() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (session.index + 1 >= session.questions.size) "Selesai" else "Lanjut")
            }
        }
    }
}

@Composable
private fun TypingView(session: SessionState, q: Question, vm: VocabViewModel) {
    var text by remember(session.index) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SessionHeader(session.index, session.questions.size) { vm.exit() }

        Text("Tulis kata bahasa Inggrisnya:", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = categoryColor(q.word.category))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    categoryEmoji(q.word.category) + " " + q.word.category,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    q.word.meaning,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Petunjuk: diawali huruf ${q.word.word.first()}, ${q.word.word.length} huruf",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!session.answered) {
            Text(
                q.word.example.replace(q.word.word, "_____", ignoreCase = true),
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Jawabanmu") },
            singleLine = true,
            enabled = !session.answered,
            modifier = Modifier.fillMaxWidth()
        )

        if (!session.answered) {
            Button(
                onClick = { vm.answerTyping(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Periksa")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (session.lastCorrect) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (session.lastCorrect) "🎉 Benar!" else "Belum tepat. Jawaban: ${q.word.word}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { vm.speak(q.word.word) }) { Text("🔊 Dengar kata") }
                    ExampleBlock(q.word) { vm.speak(it) }
                }
            }
            Button(
                onClick = { vm.next() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (session.index + 1 >= session.questions.size) "Selesai" else "Lanjut")
            }
        }
    }
}

@Composable
private fun SummaryView(
    session: SessionState,
    mode: PracticeMode,
    stats: VocabStats,
    vm: VocabViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Sesi selesai 🎉",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSecondary
                )
                Text(
                    "${if (mode == PracticeMode.Cards) "Hafal" else "Benar"}: ${session.correct} dari ${session.questions.size}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondary
                )
                Text(
                    if (stats.answeredToday >= SESSION_SIZE) {
                        "✅ Tugas kosakata hari ini selesai. Cek di tab Checklist."
                    } else {
                        "Kata hari ini: ${stats.answeredToday} dari $SESSION_SIZE"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        Button(
            onClick = { vm.start(mode) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp)
        ) {
            Text("Latihan lagi")
        }
        OutlinedButton(
            onClick = { vm.exit() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Kembali ke menu")
        }
    }
}
