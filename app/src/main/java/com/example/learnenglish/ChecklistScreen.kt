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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val DAY_NAMES = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

private fun dayName(date: LocalDate): String = DAY_NAMES[date.dayOfWeek.value - 1]

@Composable
fun ChecklistScreen(stats: ProgressStats, onToggle: (DailyTask) -> Unit) {
    val done = stats.today.doneCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Checklist hari ini", style = MaterialTheme.typography.headlineMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (stats.streak > 0) {
                        "🔥 Streak: ${stats.streak} hari beruntun"
                    } else {
                        "Belum ada streak. Satu tugas saja sudah dihitung."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondary
                )
                Text(
                    "$done dari 3 tugas selesai hari ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondary
                )
                LinearProgressIndicator(
                    progress = { done / 3f },
                    color = MaterialTheme.colorScheme.onSecondary,
                    trackColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }
        }

        TaskCard(
            emoji = "💬",
            title = "1 percakapan",
            hint = "Tercentang otomatis setelah 3 pesan di tab Chat",
            checked = stats.today.conversationDone,
            container = MaterialTheme.colorScheme.primaryContainer
        ) { onToggle(DailyTask.Conversation) }

        TaskCard(
            emoji = "📚",
            title = "10 kosakata",
            hint = "Tercentang otomatis setelah menjawab 10 kata di tab Latihan",
            checked = stats.today.vocabDone,
            container = MaterialTheme.colorScheme.tertiaryContainer
        ) { onToggle(DailyTask.Vocab) }

        TaskCard(
            emoji = "📝",
            title = "1 catatan",
            hint = "Tercentang otomatis setelah menyimpan 1 catatan",
            checked = stats.today.noteDone,
            container = MaterialTheme.colorScheme.secondaryContainer
        ) { onToggle(DailyTask.Note) }

        Text(
            "Kamu juga bisa mencentang atau membatalkan tugas dengan mengetuknya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val activeDays = stats.week.count { it.done > 0 }
        val totalDone = stats.week.sumOf { it.done }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📅 Ringkasan 7 hari", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stats.week.forEach { d ->
                        val bg: Color
                        val fg: Color
                        if (d.done >= 3) {
                            bg = MaterialTheme.colorScheme.tertiary
                            fg = MaterialTheme.colorScheme.onTertiary
                        } else if (d.done > 0) {
                            bg = MaterialTheme.colorScheme.secondaryContainer
                            fg = MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            bg = MaterialTheme.colorScheme.surfaceVariant
                            fg = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(bg, RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                dayName(d.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = fg
                            )
                            Text(
                                "${d.done}/3",
                                style = MaterialTheme.typography.titleSmall,
                                color = fg
                            )
                        }
                    }
                }
                Text(
                    "Hari aktif: $activeDays dari 7. Tugas selesai: $totalDone dari 21.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    emoji: String,
    title: String,
    hint: String,
    checked: Boolean,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (checked) "✅ Selesai" else "⬜ Belum",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}
