package com.example.learnenglish

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DailyTask { Conversation, Vocab, Note }

fun DailyProgress.doneCount(): Int =
    listOf(conversationDone, vocabDone, noteDone).count { it }

data class DayStat(val date: LocalDate, val done: Int)

data class ProgressStats(
    val today: DailyProgress,
    val streak: Int,
    val week: List<DayStat>
)

fun computeStats(rows: List<DailyProgress>, todayDate: LocalDate): ProgressStats {
    val byDate = rows.associateBy { it.date }
    val todayRow = byDate[todayDate.toString()] ?: DailyProgress(date = todayDate.toString())

    fun isActive(d: LocalDate): Boolean = (byDate[d.toString()]?.doneCount() ?: 0) > 0

    var day = if (isActive(todayDate)) todayDate else todayDate.minusDays(1)
    var streak = 0
    while (isActive(day)) {
        streak++
        day = day.minusDays(1)
    }

    val week = (6 downTo 0).map { offset ->
        val d = todayDate.minusDays(offset.toLong())
        DayStat(d, byDate[d.toString()]?.doneCount() ?: 0)
    }
    return ProgressStats(todayRow, streak, week)
}

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).dailyProgressDao()

    val rows: StateFlow<List<DailyProgress>> = dao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(task: DailyTask) {
        viewModelScope.launch {
            val todayKey = LocalDate.now().toString()
            val current = dao.getByDate(todayKey) ?: DailyProgress(date = todayKey)
            val updated = when (task) {
                DailyTask.Conversation -> current.copy(conversationDone = !current.conversationDone)
                DailyTask.Vocab -> current.copy(vocabDone = !current.vocabDone)
                DailyTask.Note -> current.copy(noteDone = !current.noteDone)
            }
            dao.upsert(updated)
        }
    }
}
