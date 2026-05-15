package com.hifz.quran.db

import android.content.Context
import androidx.lifecycle.LiveData
import com.hifz.quran.model.Badge
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus
import com.hifz.quran.util.BadgeManager
import com.hifz.quran.util.TimeUtils

class HifzRepository(context: Context) {

    private val db         = HifzDatabase.getDatabase(context)
    private val sourateDao = db.sourateDao()
    private val versetDao  = db.versetDao()
    private val sessionDao = db.sessionDao()
    private val badgeDao   = db.badgeDao()

    // ── Sourate ───────────────────────────────────────────────────────────────
    fun getAllSourates(): LiveData<List<Sourate>>     = sourateDao.getAllSourates()
    suspend fun getAllSouratesSync(): List<Sourate>   = sourateDao.getAllSouratesSync()
    suspend fun getSourateById(id: Long): Sourate?   = sourateDao.getSourateById(id)
    suspend fun insertSourate(sourate: Sourate): Long = sourateDao.insertSourate(sourate)
    suspend fun updateSourate(sourate: Sourate)       = sourateDao.updateSourate(sourate)
    suspend fun deleteSourate(sourate: Sourate) {
        sourateDao.deleteSourate(sourate)
        versetDao.deleteVersetsBySourate(sourate.id)
    }

    // ── Verset ────────────────────────────────────────────────────────────────
    fun getVersetsBySourate(sourateId: Long): LiveData<List<Verset>> =
        versetDao.getVersetsBySourate(sourateId)

    suspend fun getVersetsBySourateSync(sourateId: Long): List<Verset> =
        versetDao.getVersetsBySourateSync(sourateId)

    suspend fun insertVerset(verset: Verset): Long   = versetDao.insertVerset(verset)
    suspend fun insertVersets(versets: List<Verset>) = versetDao.insertVersets(versets)
    suspend fun deleteVerset(verset: Verset)         = versetDao.deleteVerset(verset)

    suspend fun updateVersetStatus(id: Long, status: VersetStatus) =
        versetDao.updateStatus(id, status)

    suspend fun incrementVersetRepeat(id: Long) = versetDao.incrementRepeat(id)

    suspend fun countVersetsByStatus(sourateId: Long, status: VersetStatus): Int =
        versetDao.countByStatus(sourateId, status)

    suspend fun totalRepeatsBySourate(sourateId: Long): Int =
        versetDao.totalRepeatsBySourate(sourateId) ?: 0

    // ── Session ───────────────────────────────────────────────────────────────
    fun getRecentSessions(): LiveData<List<Session>>  = sessionDao.getRecentSessions()
    suspend fun insertSession(session: Session)        = sessionDao.insertSession(session)

    suspend fun totalListeningTimeSince(since: Long): Long =
        sessionDao.totalListeningTimeSince(since) ?: 0L

    suspend fun sessionCountSince(since: Long): Int   = sessionDao.sessionCountSince(since)

    suspend fun sessionCountInRange(from: Long, to: Long): Int =
        sessionDao.sessionCountInRange(from, to)

    suspend fun getLastSessionDate(): Long?            = sessionDao.getLastSessionDate()

    suspend fun totalRepeatsDone(): Int = sessionDao.totalRepeatsDone() ?: 0

    // ── Badges ────────────────────────────────────────────────────────────────
    fun getAllBadges(): LiveData<List<Badge>>     = badgeDao.getAllBadges()
    fun getUnlockedBadges(): LiveData<List<Badge>> = badgeDao.getUnlockedBadges()

    suspend fun initBadges() {
        BadgeManager.ALL_BADGES.forEach { badgeDao.insertBadge(it) }
    }

    /**
     * Vérifie toutes les conditions de badges et débloque ceux qui sont atteints.
     * Appelé après chaque action significative (écoute, maîtrise, session).
     */
    suspend fun checkAndUnlockBadges() {
        // Initialiser les badges si pas encore fait
        initBadges()

        val sourates      = getAllSouratesSync()
        val totalSourates = sourates.size
        val totalListens  = sourates.sumOf { totalRepeatsBySourate(it.id) }
        val totalMastered = sourates.sumOf { countVersetsByStatus(it.id, VersetStatus.MAITRISE) }
        val totalMinutes  = totalListeningTimeSince(0L) / 60_000L
        val streakDays    = calculateStreak()

        // Max répétitions sur un seul verset
        var versetRepeatMax = 0
        sourates.forEach { s ->
            val versets = getVersetsBySourateSync(s.id)
            val max     = versets.maxOfOrNull { it.repeatCount } ?: 0
            if (max > versetRepeatMax) versetRepeatMax = max
        }

        // Al-Fatiha : toutes les versets maîtrisés
        val fatiha = sourates.firstOrNull { it.sourateNumber == 1 }
        val fatihaMastered = fatiha != null &&
                countVersetsByStatus(fatiha.id, VersetStatus.MAITRISE) >= 7

        val toUnlock = BadgeManager.checkBadgesToUnlock(
            totalSourates   = totalSourates,
            totalListens    = totalListens,
            totalMastered   = totalMastered,
            streakDays      = streakDays,
            totalMinutes    = totalMinutes,
            versetRepeatMax = versetRepeatMax,
            fatihaMastered  = fatihaMastered
        )

        toUnlock.forEach { badgeDao.unlockBadge(it) }
    }

    private suspend fun calculateStreak(): Int {
        val lastDate = getLastSessionDate() ?: return 0
        val now      = System.currentTimeMillis()
        if (now - lastDate > 2 * 86_400_000L) return 0
        var streak    = 0
        val oneDayMs  = 86_400_000L
        val checkTime = TimeUtils.startOfDay()
        for (i in 0..364) {
            val dayStart = checkTime - i * oneDayMs
            val dayEnd   = dayStart + oneDayMs
            if (sessionCountInRange(dayStart, dayEnd) > 0) streak++
            else if (i > 0) break
        }
        return streak
    }
}
