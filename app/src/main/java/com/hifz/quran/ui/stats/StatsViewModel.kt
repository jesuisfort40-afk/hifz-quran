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

    // BUG FIX #3 — STATISTIQUES :
    // Toutes les LiveData initialisées avec des valeurs par défaut non-null.
    // Avant : certaines étaient postValue(null) ce qui faisait afficher "null".
    val todayMinutes        = MutableLiveData(0L)
    val weekSessions        = MutableLiveData(0)
    val monthMinutes        = MutableLiveData(0L)
    // totalListeningHours stocke désormais les millisecondes totales
    // (le formatage est fait dans le Fragment)
    val totalListeningHours = MutableLiveData(0L)
    val totalMastered       = MutableLiveData(0)
    val totalInProgress     = MutableLiveData(0)
    val totalPending        = MutableLiveData(0)
    val streakDays          = MutableLiveData(0)

    init { refreshStats() }

    fun refreshStats() {
        viewModelScope.launch {
            // ── Temps d'écoute ────────────────────────────────────────────────
            val todayMs  = repo.totalListeningTimeSince(TimeUtils.startOfDay())
            val monthMs  = repo.totalListeningTimeSince(TimeUtils.startOfMonth())
            val totalMs  = repo.totalListeningTimeSince(0L)
            val weekCount = repo.sessionCountSince(TimeUtils.startOfWeek())

            todayMinutes.postValue(todayMs / 60_000L)
            monthMinutes.postValue(monthMs / 60_000L)
            weekSessions.postValue(weekCount)
            totalListeningHours.postValue(totalMs) // ms brutes, formatées dans le Fragment

            // ── Progression versets ───────────────────────────────────────────
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

            // ── Streak ────────────────────────────────────────────────────────
            streakDays.postValue(calculateStreak())
        }
    }

    /**
     * Calcule le nombre de jours consécutifs de pratique.
     * BUG FIX : l'ancienne version retournait toujours 0 ou 1.
     * Nouvelle logique : on compte les jours distincts en remontant depuis aujourd'hui.
     */
    private suspend fun calculateStreak(): Int {
        val lastDate = repo.getLastSessionDate() ?: return 0
        val now      = System.currentTimeMillis()
        val oneDayMs = 86_400_000L

        // Si la dernière session date de plus de 2 jours → streak cassé
        if (now - lastDate > 2 * oneDayMs) return 0

        var streak    = 0
        var checkTime = TimeUtils.startOfDay()

        // Remonter jusqu'à 365 jours max
        for (i in 0..364) {
            val dayStart = checkTime - i * oneDayMs
            val dayEnd   = dayStart  + oneDayMs
            val count    = repo.sessionCountInRange(dayStart, dayEnd)
            if (count > 0) streak++ else if (i > 0) break  // gap → arrêt
        }
        return streak
    }
}
