package com.example.learnenglish

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val DAY_NAMES = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

private fun dayName(date: LocalDate): String = DAY_NAMES[date.dayOfWeek.value - 1]

@Composable
fun ChecklistScreen(stats: ProgressStats, onToggle: (DailyTask) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Checklist hari ini", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (stats.streak > 0) {
                        "🔥 Streak: ${stats.streak} hari beruntun"
                    } else {
                        "Belum ada streak. Satu tugas saja sudah dihitung."
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                TaskRow("1 percakapan (tab Chat)", stats.today.conversationDone) {
                    onToggle(DailyTask.Conversation)
                }
                TaskRow("10 kosakata (tab Latihan)", stats.today.vocabDone) {
                    onToggle(DailyTask.Vocab)
                }
                TaskRow("1 catatan (tab Catatan)", stats.today.noteDone) {
                    onToggle(DailyTask.Note)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Saat ini dicentang manual. Nanti otomatis ikut fitur yang selesai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val activeDays = stats.week.count { it.done > 0 }
        val totalDone = stats.week.sumOf { it.done }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ringkasan 7 hari", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    stats.week.forEach { d ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(dayName(d.date), style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${d.done}/3",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (d.done > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
private fun TaskRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
