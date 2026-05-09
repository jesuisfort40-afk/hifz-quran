package com.hifz.quran.db

import android.content.Context
import androidx.lifecycle.LiveData
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus

class HifzRepository(context: Context) {

    private val db = HifzDatabase.getDatabase(context)
    private val sourateDao = db.sourateDao()
    private val versetDao = db.versetDao()
    private val sessionDao = db.sessionDao()

    // ---------- Sourate ----------
    fun getAllSourates(): LiveData<List<Sourate>> = sourateDao.getAllSourates()
    suspend fun getAllSouratesSync(): List<Sourate> = sourateDao.getAllSouratesSync()
    suspend fun getSourateById(id: Long): Sourate? = sourateDao.getSourateById(id)
    suspend fun insertSourate(sourate: Sourate): Long = sourateDao.insertSourate(sourate)
    suspend fun updateSourate(sourate: Sourate) = sourateDao.updateSourate(sourate)
    suspend fun deleteSourate(sourate: Sourate) {
        sourateDao.deleteSourate(sourate)
        versetDao.deleteVersetsBySourate(sourate.id)
    }

    // ---------- Verset ----------
    fun getVersetsBySourate(sourateId: Long): LiveData<List<Verset>> =
        versetDao.getVersetsBySourate(sourateId)

    suspend fun getVersetsBySourateSync(sourateId: Long): List<Verset> =
        versetDao.getVersetsBySourateSync(sourateId)

    suspend fun getVersetById(id: Long): Verset? = versetDao.getVersetById(id)
    suspend fun insertVerset(verset: Verset): Long = versetDao.insertVerset(verset)
    suspend fun insertVersets(versets: List<Verset>) = versetDao.insertVersets(versets)
    suspend fun updateVerset(verset: Verset) = versetDao.updateVerset(verset)
    suspend fun deleteVerset(verset: Verset) = versetDao.deleteVerset(verset)
    suspend fun updateVersetStatus(id: Long, status: VersetStatus) =
        versetDao.updateStatus(id, status)
    suspend fun incrementVersetRepeat(id: Long) = versetDao.incrementRepeat(id)
    suspend fun countVersetsByStatus(sourateId: Long, status: VersetStatus): Int =
        versetDao.countByStatus(sourateId, status)

    // ---------- Session ----------
    fun getRecentSessions(): LiveData<List<Session>> = sessionDao.getRecentSessions()
    suspend fun insertSession(session: Session) = sessionDao.insertSession(session)
    suspend fun totalListeningTimeSince(since: Long): Long = sessionDao.totalListeningTimeSince(since) ?: 0L
    suspend fun sessionCountSince(since: Long): Int = sessionDao.sessionCountSince(since)
    suspend fun totalRepeatsBySourate(sourateId: Long): Int = sessionDao.totalRepeatsBySourate(sourateId) ?: 0
    suspend fun getLastSessionDate(): Long? = sessionDao.getLastSessionDate()
}
