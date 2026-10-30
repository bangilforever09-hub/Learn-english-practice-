package com.example.learnenglish

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun topicEmoji(t: ChatTopic): String = when (t) {
    ChatTopic.Daily -> "☕"
    ChatTopic.Work -> "💼"
    ChatTopic.Ielts -> "🎓"
    ChatTopic.Restaurant -> "🍽️"
    ChatTopic.Interview -> "🤝"
    ChatTopic.Airport -> "✈️"
    ChatTopic.Doctor -> "🩺"
}

@Composable
private fun topicColor(t: ChatTopic): Color = when (t) {
    ChatTopic.Daily, ChatTopic.Restaurant -> MaterialTheme.colorScheme.primaryContainer
    ChatTopic.Work, ChatTopic.Interview -> MaterialTheme.colorScheme.secondaryContainer
    ChatTopic.Ielts, ChatTopic.Airport -> MaterialTheme.colorScheme.tertiaryContainer
    ChatTopic.Doctor -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun ChatPage(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()

    if (chat.topic == null) {
        TopicPage(
            hasKey = apiKey.isNotBlank(),
            onPick = { vm.startTopic(it) },
            onOpenSettings = onOpenSettings
        )
    } else {
        ConversationPage(vm = vm, chat = chat)
    }
}

@Composable
private fun TopicPage(
    hasKey: Boolean,
    onPick: (ChatTopic) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🤖 Ngobrol dengan AI",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Pilih topik, lalu bicara (🎙), ketik, atau ketuk 📞 untuk panggilan suara. AI menjawab dengan teks dan suara, lalu memberi koreksi singkat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

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

        Text("💬 Obrolan", style = MaterialTheme.typography.titleMedium)
        ChatTopic.entries.filter { !it.roleplay }.forEach { topic ->
            TopicCard(topic = topic, onPick = onPick)
        }

        Text("🎭 Role-play (berpura-pura di situasi nyata)", style = MaterialTheme.typography.titleMedium)
        ChatTopic.entries.filter { it.roleplay }.forEach { topic ->
            TopicCard(topic = topic, onPick = onPick)
        }
    }
}

@Composable
private fun TopicCard(topic: ChatTopic, onPick: (ChatTopic) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(topic) },
        colors = CardDefaults.cardColors(containerColor = topicColor(topic))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(topicEmoji(topic), style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(topic.label, style = MaterialTheme.typography.titleMedium)
                Text(topic.hint, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ConversationPage(vm: ChatViewModel, chat: ChatState) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showAvatar by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startListening()
        } else {
            vm.setNotice("Izin mikrofon ditolak. Aktifkan di Setelan HP, Aplikasi, Learn English Practice, Izin.")
        }
    }

    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startCall()
        } else {
            vm.setNotice("Izin mikrofon ditolak. Aktifkan di Setelan HP, Aplikasi, Learn English Practice, Izin.")
        }
    }

    LaunchedEffect(chat.messages.size, chat.loading) {
        if (chat.messages.isNotEmpty()) {
            listState.animateScrollToItem(chat.messages.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.endCallIfActive() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.endCallIfActive()
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun onMicClick() {
        if (chat.listening) {
            vm.stopListening()
        } else if (hasMicPermission()) {
            vm.startListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onCallClick() {
        if (chat.callMode) {
            vm.endCall()
        } else if (hasMicPermission()) {
            vm.startCall()
        } else {
            callLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val topic = chat.topic

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onCallClick() }) {
                    Text(if (chat.callMode) "📵 Akhiri" else "📞 Panggilan")
                }
                TextButton(onClick = { showAvatar = !showAvatar }) {
                    Text("🎭")
                }
                TextButton(
                    onClick = { vm.setSpeakReplies(!chat.speakReplies) },
                    enabled = !chat.callMode
                ) {
                    Text(if (chat.speakReplies) "🔊" else "🔇")
                }
            }
        }
        if (topic != null) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 6.dp)
                    .background(topicColor(topic), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    topicEmoji(topic) + " " + topic.label,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (chat.callMode || showAvatar) {
            val mood = when {
                chat.listening -> AvatarMood.Listening
                chat.loading -> AvatarMood.Thinking
                chat.speaking -> AvatarMood.Speaking
                else -> AvatarMood.Idle
            }
            CharacterPanel(mood = mood, modifier = Modifier.padding(vertical = 6.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chat.messages, key = { it.id }) { msg ->
                BubbleItem(
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

        if (chat.callMode) {
            val status = when {
                chat.loading -> "AI sedang berpikir… (kalau Google sibuk, aplikasi mencoba ulang sendiri)"
                chat.listening ->
                    if (chat.partial.isBlank()) "🎙 Giliranmu, bicaralah dalam bahasa Inggris…"
                    else "🎙 " + chat.partial
                chat.speaking -> "🔊 AI sedang berbicara…"
                else -> "Panggilan dijeda. Ketuk 🎙 Bicara untuk lanjut."
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "📞 Panggilan suara aktif",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onMicClick() },
                            enabled = chat.listening || (!chat.loading && !chat.speaking),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (chat.listening) "⏹ Selesai bicara" else "🎙 Bicara")
                        }
                        Button(
                            onClick = { vm.endCall() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Akhiri")
                        }
                    }
                }
            }
        } else {
            if (chat.loading) {
                Text(
                    "AI sedang menulis… (kalau Google sibuk, aplikasi mencoba ulang sendiri)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (chat.listening) {
                Text(
                    "🎙 Mendengarkan… bicaralah dalam bahasa Inggris, ketuk ⏹ kalau sudah selesai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                    enabled = !chat.listening,
                    shape = RoundedCornerShape(20.dp)
                )
                Button(
                    onClick = { onMicClick() },
                    enabled = !chat.loading || chat.listening,
                    colors = if (chat.listening) {
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
}

@Composable
private fun BubbleItem(
    msg: ChatMessage,
    onSpeak: (String) -> Unit,
    onSave: () -> Unit
) {
    var showTranslation by remember(msg.id) { mutableStateOf(false) }
    val userShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 4.dp,
        bottomStart = 18.dp
    )
    val aiShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 4.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (msg.fromUser) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = userShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else if (msg.isError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    "⚠️ " + msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🤖", style = MaterialTheme.typography.headlineSmall)
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = aiShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSpeak(msg.text) }) { Text("🔊 Dengar") }
                            if (msg.terjemahan.isNotBlank()) {
                                TextButton(onClick = { showTranslation = !showTranslation }) {
                                    Text(if (showTranslation) "Sembunyikan" else "🌐 Terjemahkan")
                                }
                            }
                        }
                        if (showTranslation && msg.terjemahan.isNotBlank()) {
                            Text(
                                msg.terjemahan,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (msg.koreksi.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📝 Koreksi: ${msg.koreksi}", style = MaterialTheme.typography.bodyMedium)
                        if (msg.lebihNatural.isNotBlank()) {
                            Text(
                                "✨ Lebih natural: ${msg.lebihNatural}",
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
}
