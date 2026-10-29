package com.example.learnenglish

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun two(n: Int): String = n.toString().padStart(2, '0')

@Composable
private fun SettingSection(
    title: String,
    summary: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(label) }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(two(value), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
fun SettingsPage(vm: ChatViewModel) {
    val context = LocalContext.current
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    val savedModel by vm.model.collectAsStateWithLifecycle()
    val accent by vm.accent.collectAsStateWithLifecycle()
    val rate by vm.rate.collectAsStateWithLifecycle()
    val ui by vm.settingsUi.collectAsStateWithLifecycle()
    val themeMode by ThemeSettings.mode.collectAsStateWithLifecycle()
    val dynamic by ThemeSettings.dynamic.collectAsStateWithLifecycle()

    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var model by remember(savedModel) { mutableStateOf(savedModel) }
    var showKey by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(Reminders.getHour(context)) }
    var minute by remember { mutableStateOf(Reminders.getMinute(context)) }
    var reminderMsg by remember { mutableStateOf("") }

    val themeLabel = when (themeMode) {
        "light" -> "Terang"
        "dark" -> "Gelap"
        else -> "Ikuti HP"
    }
    val accentLabel = when (accent) {
        "en-GB" -> "Inggris"
        "en-AU" -> "Australia"
        else -> "Amerika"
    }
    val rateLabel = if (rate == "0.8") "Pelan" else "Normal"
    val canUseDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Setelan", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ketuk judul bagian untuk membuka atau menutupnya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingSection(
            title = "🎨 Tampilan",
            summary = "Tema: " + themeLabel + " · " +
                (if (dynamic) "warna wallpaper" else "warna aplikasi")
        ) {
            Text("Tema", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("system" to "Ikuti HP", "light" to "Terang", "dark" to "Gelap")
                    .forEach { (value, label) ->
                        ChoiceButton(
                            label = label,
                            selected = themeMode == value,
                            onClick = { ThemeSettings.setMode(context, value) },
                            modifier = Modifier.weight(1f)
                        )
                    }
            }
            Text("Warna", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceButton(
                    label = "Warna aplikasi",
                    selected = !dynamic,
                    onClick = { ThemeSettings.setDynamic(context, false) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceButton(
                    label = "Dari wallpaper",
                    selected = dynamic,
                    onClick = { ThemeSettings.setDynamic(context, true) },
                    modifier = Modifier.weight(1f),
                    enabled = canUseDynamic
                )
            }
            if (!canUseDynamic) {
                Text(
                    "Warna dari wallpaper butuh Android 12 ke atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Bahasa aplikasi: saat ini Indonesia. Pilihan bahasa yang mengikuti HP menyusul di paket berikutnya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingSection(
            title = "🤖 Gemini (API key dan model)",
            summary = (if (savedKey.isBlank()) "API key belum diisi" else "API key sudah diisi") +
                " · " + savedModel
        ) {
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
            Button(
                onClick = { vm.saveSettings(key, model) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan")
            }
            if (ui.message.isNotEmpty()) {
                Text(ui.message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SettingSection(
            title = "🔊 Suara AI",
            summary = "Aksen " + accentLabel + " · kecepatan " + rateLabel
        ) {
            Text("Aksen", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("en-US" to "Amerika", "en-GB" to "Inggris", "en-AU" to "Australia")
                    .forEach { (code, label) ->
                        ChoiceButton(
                            label = label,
                            selected = accent == code,
                            onClick = { vm.saveVoice(code, rate) },
                            modifier = Modifier.weight(1f)
                        )
                    }
            }
            Text("Kecepatan bicara", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("0.8" to "Pelan", "1.0" to "Normal")
                    .forEach { (value, label) ->
                        ChoiceButton(
                            label = label,
                            selected = rate == value,
                            onClick = { vm.saveVoice(accent, value) },
                            modifier = Modifier.weight(1f)
                        )
                    }
            }
            OutlinedButton(
                onClick = { vm.speak("Hello! This is how I sound. Nice to meet you.") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔊 Tes suara")
            }
            Text(
                "Kalau aksen terpilih belum terpasang di HP, suara otomatis memakai Amerika.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingSection(
            title = "⏰ Pengingat harian",
            summary = "Setiap hari pukul " + two(Reminders.getHour(context)) + ":" +
                two(Reminders.getMinute(context))
        ) {
            Text(
                "Waktu dipilih: " + two(hour) + ":" + two(minute),
                style = MaterialTheme.typography.bodyLarge
            )
            StepperRow(
                label = "Jam",
                value = hour,
                onMinus = {
                    hour = (hour + 23) % 24
                    reminderMsg = ""
                },
                onPlus = {
                    hour = (hour + 1) % 24
                    reminderMsg = ""
                }
            )
            StepperRow(
                label = "Menit (per 5)",
                value = minute,
                onMinus = {
                    minute = (minute + 55) % 60
                    reminderMsg = ""
                },
                onPlus = {
                    minute = (minute + 5) % 60
                    reminderMsg = ""
                }
            )
            Button(
                onClick = {
                    Reminders.setTime(context, hour, minute)
                    reminderMsg = "Tersimpan ✅ Pengingat berikutnya pukul " +
                        two(hour) + ":" + two(minute) + "."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan waktu")
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

        SettingSection(
            title = "🔒 Catatan privasi",
            summary = ""
        ) {
            Text(
                "Isi chat dikirim ke server Google. Di paket gratis, Google dapat memakai isinya untuk meningkatkan produknya, jadi jangan menulis data pribadi atau rahasia. API key hanya disimpan di HP ini. Jangan bagikan atau screenshot key-mu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
