package com.example.learnenglish

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnenglish.data.AppDatabase
import com.example.learnenglish.data.DailyProgress
import com.example.learnenglish.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

val REVIEW_DAYS = longArrayOf(1L, 3L, 7L)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val noteDao = db.noteDao()
    private val dailyDao = db.dailyProgressDao()

    val notes: StateFlow<List<Note>> = noteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote(type: String, text: String, extra: String) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            noteDao.insert(
                Note(
                    type = type,
                    text = text.trim(),
                    extra = extra.trim(),
                    createdDay = today,
                    reviewsDone = 0,
                    nextReview = today + REVIEW_DAYS[0]
                )
            )

            val key = LocalDate.now().toString()
            val daily = dailyDao.getByDate(key) ?: DailyProgress(date = key)
            if (!daily.noteDone) {
                dailyDao.upsert(daily.copy(noteDone = true))
            }
        }
    }

    fun review(note: Note, remembered: Boolean) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            if (remembered) {
                val done = note.reviewsDone + 1
                val next = if (done >= REVIEW_DAYS.size) {
                    Long.MAX_VALUE
                } else {
                    maxOf(note.createdDay + REVIEW_DAYS[done], today + 1)
                }
                noteDao.update(note.copy(reviewsDone = done, nextReview = next))
            } else {
                noteDao.update(note.copy(nextReview = today + 1))
            }
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note)
        }
    }
}
