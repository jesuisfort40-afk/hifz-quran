package com.hifz.quran.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.VersetStatus
import com.hifz.quran.util.TimeUtils
import kotlinx.coroutines.launch

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HifzRepository(app)

    val todayMinutes = MutableLiveData(0L)
    val weekSessions = MutableLiveData(0)
    val monthMinutes = MutableLiveData(0L)
    val totalMastered = MutableLiveData(0)
    val totalInProgress = MutableLiveData(0)
    val totalPending = MutableLiveData(0)
    val streakDays = MutableLiveData(0)
    val totalListeningHours = MutableLiveData(0L)

    init { refreshStats() }

    fun refreshStats() {
        viewModelScope.launch {
            val todayMs = repo.totalListeningTimeSince(TimeUtils.startOfDay())
            todayMinutes.postValue(todayMs / 60000)

            val weekCount = repo.sessionCountSince(TimeUtils.startOfWeek())
            weekSessions.postValue(weekCount)

            val monthMs = repo.totalListeningTimeSince(TimeUtils.startOfMonth())
            monthMinutes.postValue(monthMs / 60000)

            // Get stats from all sourates
            val sourates = repo.getAllSouratesSync()
            var mastered = 0; var inProgress = 0; var pending = 0
            sourates.forEach { s ->
                mastered += repo.countVersetsByStatus(s.id, VersetStatus.MAITRISE)
                inProgress += repo.countVersetsByStatus(s.id, VersetStatus.EN_COURS)
                pending += repo.countVersetsByStatus(s.id, VersetStatus.A_APPRENDRE)
            }
            totalMastered.postValue(mastered)
            totalInProgress.postValue(inProgress)
            totalPending.postValue(pending)

            // Streak
            streakDays.postValue(calculateStreak())

            // Total hours
            val allTime = repo.totalListeningTimeSince(0L)
            totalListeningHours.postValue(allTime / 3600000)
        }
    }

    private suspend fun calculateStreak(): Int {
        val lastDate = repo.getLastSessionDate() ?: return 0
        val now = System.currentTimeMillis()
        val diffDays = (now - lastDate) / (1000 * 60 * 60 * 24)
        return if (diffDays <= 1) {
            // Simple: just check if practiced today or yesterday
            val sessionCount = repo.sessionCountSince(TimeUtils.startOfDay() - 86400000L)
            if (sessionCount > 0) 1 else 0
        } else 0
    }
}
