package com.example.learnenglish

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnenglish.data.Note
import java.time.LocalDate

private val NOTE_TYPES = listOf("Kata", "Kalimat", "Kesalahan")

private fun noteEmoji(type: String): String = when (type) {
    "Kata" -> "📖"
    "Kalimat" -> "💬"
    else -> "⚠️"
}

@Composable
private fun noteContainer(type: String): Color = when (type) {
    "Kata" -> MaterialTheme.colorScheme.primaryContainer
    "Kalimat" -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun noteAccent(type: String): Color = when (type) {
    "Kata" -> MaterialTheme.colorScheme.primary
    "Kalimat" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun noteOnAccent(type: String): Color = when (type) {
    "Kata" -> MaterialTheme.colorScheme.onPrimary
    "Kalimat" -> MaterialTheme.colorScheme.onTertiary
    else -> MaterialTheme.colorScheme.onError
}

private fun statusText(note: Note, today: Long): String = when {
    note.nextReview == Long.MAX_VALUE -> "Review selesai ✅"
    note.nextReview <= today -> "Perlu direview"
    note.nextReview == today + 1 -> "Review besok"
    else -> "Review ${note.nextReview - today} hari lagi"
}

@Composable
fun NotesScreen(vm: NotesViewModel) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    val due = notes.filter { it.nextReview <= today }

    var type by remember { mutableStateOf(NOTE_TYPES[0]) }
    var text by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "📝 Catatan",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "${notes.size} catatan · ${due.size} perlu direview hari ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        ReminderCard()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🔔 Review hari ini", style = MaterialTheme.typography.titleMedium)
                if (due.isEmpty()) {
                    Text(
                        "Tidak ada catatan yang perlu direview. 🎉",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    due.forEach { note ->
                        key(note.id) {
                            DueItem(
                                note = note,
                                onRemember = { vm.review(note, true) },
                                onForgot = { vm.review(note, false) }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✏️ Tambah catatan", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NOTE_TYPES.forEach { t ->
                        if (t == type) {
                            Button(
                                onClick = { type = t },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = noteAccent(t),
                                    contentColor = noteOnAccent(t)
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(noteEmoji(t) + " " + t) }
                        } else {
                            OutlinedButton(
                                onClick = { type = t },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(noteEmoji(t) + " " + t) }
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = {
                        Text(
                            when (type) {
                                "Kata" -> "Kata baru"
                                "Kalimat" -> "Kalimat"
                                else -> "Kalimat yang salah"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = {
                        Text(
                            if (type == "Kesalahan") "Koreksi (versi yang benar)"
                            else "Arti atau catatan (opsional)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.addNote(type, text, extra)
                        text = ""
                        extra = ""
                    },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = noteAccent(type),
                        contentColor = noteOnAccent(type)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Simpan catatan")
                }
            }
        }

        Text(
            "📚 Semua catatan (${notes.size})",
            style = MaterialTheme.typography.titleMedium
        )
        if (notes.isEmpty()) {
            Text(
                "Belum ada catatan. Simpan satu kata atau kalimat yang baru kamu pelajari.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            notes.forEach { note ->
                key(note.id) {
                    NoteRow(
                        note = note,
                        today = today,
                        onDelete = { vm.delete(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCard() {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= 33
    var granted by remember {
        mutableStateOf(
            !needsPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⏰ Pengingat harian", style = MaterialTheme.typography.titleMedium)
            if (granted) {
                Text(
                    "Pengingat aktif. Atur jamnya di tab Setelan.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { Reminders.showNow(context) }) {
                    Text("Tes pengingat sekarang")
                }
            } else {
                Text(
                    "Izinkan notifikasi supaya pengingat review bisa muncul.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Izinkan notifikasi")
                }
                Text(
                    "Kalau tombol tidak bereaksi, buka Setelan HP, Aplikasi, Learn English Practice, lalu Notifikasi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DueItem(note: Note, onRemember: () -> Unit, onForgot: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val needsReveal = note.extra.isNotBlank()
    val revealed = shown || !needsReveal

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                noteEmoji(note.type) + " " + note.type +
                    " · review ke-" + (note.reviewsDone + 1) + " dari " + REVIEW_DAYS.size,
                style = MaterialTheme.typography.labelMedium,
                color = noteAccent(note.type)
            )
            Text(note.text, style = MaterialTheme.typography.titleLarge)
            if (revealed && needsReveal) {
                Text(note.extra, style = MaterialTheme.typography.bodyLarge)
            }
            if (!revealed) {
                OutlinedButton(
                    onClick = { shown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lihat jawaban")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onForgot, modifier = Modifier.weight(1f)) {
                        Text("Belum ingat")
                    }
                    Button(onClick = onRemember, modifier = Modifier.weight(1f)) {
                        Text("Ingat")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, today: Long, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = noteContainer(note.type))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                noteEmoji(note.type) + " " + note.type + " · " + statusText(note, today),
                style = MaterialTheme.typography.labelMedium,
                color = noteAccent(note.type)
            )
            Text(note.text, style = MaterialTheme.typography.titleMedium)
            if (note.extra.isNotBlank()) {
                Text(
                    note.extra,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!confirm) {
                TextButton(onClick = { confirm = true }) { Text("Hapus") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDelete) { Text("Ya, hapus") }
                    TextButton(onClick = { confirm = false }) { Text("Batal") }
                }
            }
        }
    }
}
