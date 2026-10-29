package com.example.learnenglish

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, CRASH_FILE)
        if (crashFile.exists()) {
            showCrashReport(crashFile)
            return
        }

        ThemeSettings.load(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    private fun installCrashLogger() {
        val file = File(filesDir, CRASH_FILE)
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.writeText(sw.toString())
            } catch (e: Exception) {
                // abaikan
            }
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun showCrashReport(crashFile: File) {
        val lines = crashFile.readText().lines()
        val important = lines.filter {
            val t = it.trim()
            t.isNotEmpty() && !t.startsWith("at ") && !t.startsWith("...")
        }
        val report = "APLIKASI BERHENTI (CRASH)\n" +
            "Screenshot layar ini dan kirim ke Claude.\n\n" +
            "RINGKASAN:\n" + important.take(8).joinToString("\n") +
            "\n\nDETAIL AWAL:\n" + lines.take(15).joinToString("\n")

        val textView = TextView(this).apply {
            text = report
            textSize = 11f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(textView)
        }
        val button = android.widget.Button(this).apply {
            text = "Hapus laporan, lalu coba lagi"
            setOnClickListener {
                crashFile.delete()
                recreate()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 120, 32, 32)
            addView(button)
            addView(scroll)
        }
        setContentView(root)
    }

    companion object {
        private const val CRASH_FILE = "crash.txt"
    }
}

private const val ROUTE_TODAY = "today"

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat", Icons.Filled.Face),
    Practice("practice", "Latihan", Icons.Filled.Star),
    Checklist("checklist", "Checklist", Icons.Filled.CheckCircle),
    Notes("notes", "Catatan", Icons.Filled.Edit),
    Options("options", "Setelan", Icons.Filled.Settings)
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val progressVm: ProgressViewModel = viewModel()
    val vocabVm: VocabViewModel = viewModel()
    val notesVm: NotesViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()
    val rows by progressVm.rows.collectAsStateWithLifecycle()
    val stats = computeStats(rows, LocalDate.now())

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { navigateToTab(navController, tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_TODAY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_TODAY) {
                TodayScreen(
                    stats = stats,
                    onStartPractice = { navigateToTab(navController, Tab.Chat.route) }
                )
            }
            composable(Tab.Chat.route) {
                ChatScreen(
                    vm = chatVm,
                    onOpenSettings = { navigateToTab(navController, Tab.Options.route) }
                )
            }
            composable(Tab.Practice.route) {
                PracticeScreen(vm = vocabVm)
            }
            composable(Tab.Checklist.route) {
                ChecklistScreen(stats = stats, onToggle = progressVm::toggle)
            }
            composable(Tab.Notes.route) {
                NotesScreen(vm = notesVm)
            }
            composable(Tab.Options.route) {
                SettingsPage(vm = chatVm)
            }
        }
    }
}

@Composable
fun TodayScreen(stats: ProgressStats, onStartPractice: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val idLocale = Locale.forLanguageTag("id-ID")
    val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", idLocale))
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val greeting = when (now.hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val done = stats.today.doneCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Column {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    timeText,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            "Cukup mulai 10 menit. Sisanya mengikuti.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onStartPractice,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            Text("Mulai Latihan", style = MaterialTheme.typography.headlineSmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔥 Streak", style = MaterialTheme.typography.labelLarge)
                    Text("${stats.streak}", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (stats.streak > 0) "hari beruntun" else "mulai hari ini",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✅ Tugas hari ini", style = MaterialTheme.typography.labelLarge)
                    Text("$done/3", style = MaterialTheme.typography.headlineLarge)
                    Text("selesai", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        LinearProgressIndicator(
            progress = { done / 3f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        )
    }
}

@Composable
fun PlaceholderScreen(title: String, note: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
