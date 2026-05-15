package com.hifz.quran.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.Badge
import com.hifz.quran.model.VersetStatus
import com.hifz.quran.util.TimeUtils
import kotlinx.coroutines.launch

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HifzRepository(app)

    val todayMinutes        = MutableLiveData(0L)
    val weekSessions        = MutableLiveData(0)
    val monthMinutes        = MutableLiveData(0L)
    val totalListeningHours = MutableLiveData(0L)
    val totalMastered       = MutableLiveData(0)
    val totalInProgress     = MutableLiveData(0)
    val totalPending        = MutableLiveData(0)
    val streakDays          = MutableLiveData(0)

    // Badges débloqués (pour l'accueil et l'écran stats)
    val unlockedBadges: LiveData<List<Badge>> = repo.getUnlockedBadges()
    val allBadges: LiveData<List<Badge>>      = repo.getAllBadges()

    init {
        viewModelScope.launch { repo.initBadges() }
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val todayMs = repo.totalListeningTimeSince(TimeUtils.startOfDay())
            val monthMs = repo.totalListeningTimeSince(TimeUtils.startOfMonth())
            val totalMs = repo.totalListeningTimeSince(0L)
            val weekCnt = repo.sessionCountSince(TimeUtils.startOfWeek())

            todayMinutes.postValue(todayMs / 60_000L)
            monthMinutes.postValue(monthMs / 60_000L)
            weekSessions.postValue(weekCnt)
            totalListeningHours.postValue(totalMs)

            val sourates = repo.getAllSouratesSync()
            var mastered = 0; var inProgress = 0; var pending = 0
            sourates.forEach { s ->
                mastered   += repo.countVersetsByStatus(s.id, VersetStatus.MAITRISE)
                inProgress += repo.countVersetsByStatus(s.id, VersetStatus.EN_COURS)
                pending    += repo.countVersetsByStatus(s.id, VersetStatus.A_APPRENDRE)
            }
            totalMastered.postValue(mastered)
            totalInProgress.postValue(inProgress)
            totalPending.postValue(pending)
            streakDays.postValue(calculateStreak())

            repo.checkAndUnlockBadges()
        }
    }

    private suspend fun calculateStreak(): Int {
        val lastDate = repo.getLastSessionDate() ?: return 0
        val now      = System.currentTimeMillis()
        if (now - lastDate > 2 * 86_400_000L) return 0
        var streak    = 0
        val oneDayMs  = 86_400_000L
        val checkTime = TimeUtils.startOfDay()
        for (i in 0..364) {
            val dayStart = checkTime - i * oneDayMs
            val dayEnd   = dayStart + oneDayMs
            if (repo.sessionCountInRange(dayStart, dayEnd) > 0) streak++
            else if (i > 0) break
        }
        return streak
    }
}
