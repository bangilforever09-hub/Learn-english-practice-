package com.example.learnenglish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Latihan kosakata", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Kata hari ini: ${stats.answeredToday} dari $SESSION_SIZE",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Kata baru: ${stats.fresh}", style = MaterialTheme.typography.bodyLarge)
                Text("Perlu diulang: ${stats.due}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Sudah dikuasai: ${stats.mastered} dari ${stats.total}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Text(
            "Satu sesi = $SESSION_SIZE kata. Jawab $SESSION_SIZE kata berbeda hari ini, maka tugas kosakata di Checklist tercentang otomatis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ModeButton("Kartu kata", "Lihat kata, ingat artinya, lalu nilai dirimu") {
            onStart(PracticeMode.Cards)
        }
        ModeButton("Kuis pilihan ganda", "Pilih arti yang benar") {
            onStart(PracticeMode.Choice)
        }
        ModeButton("Kuis isian", "Tulis kata bahasa Inggrisnya") {
            onStart(PracticeMode.Typing)
        }
    }
}

@Composable
private fun ModeButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SessionHeader(index: Int, total: Int, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Kata ${index + 1} dari $total", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onExit) { Text("Keluar") }
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    q.word.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        textAlign = TextAlign.Center
                    )
                    Text(
                        q.word.example,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = { vm.speak(q.word.example) }) {
                        Text("🔊 Dengar kalimat")
                    }
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
                OutlinedButton(
                    onClick = { vm.markCard(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Belum hafal")
                }
                Button(
                    onClick = { vm.markCard(true) },
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
            val label = when {
                !session.answered -> option
                option == q.word.meaning -> "✅ $option"
                option == session.selected -> "❌ $option"
                else -> option
            }
            OutlinedButton(
                onClick = { vm.answerChoice(option) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }
        }

        if (session.answered) {
            Text(
                if (session.lastCorrect) "Benar!" else "Belum tepat. Jawaban: ${q.word.meaning}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                q.word.example,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic
            )
            OutlinedButton(onClick = { vm.speak(q.word.example) }) {
                Text("🔊 Dengar kalimat")
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Text(
                if (session.lastCorrect) "Benar!" else "Belum tepat. Jawaban: ${q.word.word}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                q.word.example,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic
            )
            OutlinedButton(onClick = { vm.speak(q.word.word) }) {
                Text("🔊 Dengar kata")
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
        Text("Sesi selesai 🎉", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${if (mode == PracticeMode.Cards) "Hafal" else "Benar"}: ${session.correct} dari ${session.questions.size}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            if (stats.answeredToday >= SESSION_SIZE) {
                "✅ Tugas kosakata hari ini selesai. Cek di tab Checklist."
            } else {
                "Kata hari ini: ${stats.answeredToday} dari $SESSION_SIZE"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(
            onClick = { vm.start(mode) },
            modifier = Modifier
                .fillMaxWidth()
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
