package com.example.learnenglish

import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, CRASH_FILE)
        if (crashFile.exists()) {
            showCrashReport(crashFile)
            return
        }

        enableEdgeToEdge()
        setContent {
            LearnEnglishTheme {
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

@Composable
fun LearnEnglishTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
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
                    onStartPractice = { navigateToTab(navController, Tab.Practice.route) }
                )
            }
            composable(Tab.Chat.route) {
                PlaceholderScreen("Chat", "Ngobrol dengan AI (teks + suara) akan dibuat di tahap e.")
            }
            composable(Tab.Practice.route) {
                PracticeScreen(vm = vocabVm)
            }
            composable(Tab.Checklist.route) {
                ChecklistScreen(stats = stats, onToggle = progressVm::toggle)
            }
            composable(Tab.Notes.route) {
                PlaceholderScreen("Catatan", "Catatan dan pengingat review akan dibuat di tahap d.")
            }
            composable(Tab.Options.route) {
                PlaceholderScreen("Setelan", "Pengaturan dan API key Gemini akan ditambah nanti.")
            }
        }
    }
}

@Composable
fun TodayScreen(stats: ProgressStats, onStartPractice: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hari ini", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cukup mulai 10 menit. Sisanya mengikuti.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStartPractice,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text("Mulai Latihan", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (stats.streak > 0) {
                "🔥 Streak: ${stats.streak} hari"
            } else {
                "Mulai streak-mu hari ini"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tugas hari ini: ${stats.today.doneCount()} dari 3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
