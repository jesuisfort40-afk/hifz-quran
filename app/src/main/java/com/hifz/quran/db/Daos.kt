package com.hifz.quran.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hifz.quran.model.Badge
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus

@Dao
interface SourateDao {
    @Query("SELECT * FROM sourates ORDER BY dateAdded DESC")
    fun getAllSourates(): LiveData<List<Sourate>>

    @Query("SELECT * FROM sourates ORDER BY dateAdded DESC")
    suspend fun getAllSouratesSync(): List<Sourate>

    @Query("SELECT * FROM sourates WHERE id = :id")
    suspend fun getSourateById(id: Long): Sourate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourate(sourate: Sourate): Long

    @Update
    suspend fun updateSourate(sourate: Sourate)

    @Delete
    suspend fun deleteSourate(sourate: Sourate)
}

@Dao
interface VersetDao {
    @Query("SELECT * FROM versets WHERE sourateId = :sourateId ORDER BY numero ASC")
    fun getVersetsBySourate(sourateId: Long): LiveData<List<Verset>>

    @Query("SELECT * FROM versets WHERE sourateId = :sourateId ORDER BY numero ASC")
    suspend fun getVersetsBySourateSync(sourateId: Long): List<Verset>

    @Query("SELECT * FROM versets WHERE id = :id")
    suspend fun getVersetById(id: Long): Verset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerset(verset: Verset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersets(versets: List<Verset>)

    @Update
    suspend fun updateVerset(verset: Verset)

    @Delete
    suspend fun deleteVerset(verset: Verset)

    @Query("DELETE FROM versets WHERE sourateId = :sourateId")
    suspend fun deleteVersetsBySourate(sourateId: Long)

    @Query("UPDATE versets SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: VersetStatus)

    @Query("UPDATE versets SET repeatCount = repeatCount + 1, lastPracticed = :now WHERE id = :id")
    suspend fun incrementRepeat(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM versets WHERE sourateId = :sourateId AND status = :status")
    suspend fun countByStatus(sourateId: Long, status: VersetStatus): Int

    @Query("SELECT SUM(repeatCount) FROM versets WHERE sourateId = :sourateId")
    suspend fun totalRepeatsBySourate(sourateId: Long): Int?
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: Session)

    @Query("SELECT * FROM sessions ORDER BY date DESC LIMIT 50")
    fun getRecentSessions(): LiveData<List<Session>>

    @Query("SELECT SUM(durationMs) FROM sessions WHERE date >= :since")
    suspend fun totalListeningTimeSince(since: Long): Long?

    @Query("SELECT COUNT(*) FROM sessions WHERE date >= :since")
    suspend fun sessionCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE date >= :from AND date < :to")
    suspend fun sessionCountInRange(from: Long, to: Long): Int

    @Query("SELECT date FROM sessions ORDER BY date DESC LIMIT 1")
    suspend fun getLastSessionDate(): Long?

    @Query("SELECT SUM(repeatsDone) FROM sessions")
    suspend fun totalRepeatsDone(): Int?
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges ORDER BY unlockedAt DESC")
    fun getAllBadges(): LiveData<List<Badge>>

    @Query("SELECT * FROM badges WHERE isUnlocked = 1 ORDER BY unlockedAt DESC")
    fun getUnlockedBadges(): LiveData<List<Badge>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(badge: Badge)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadges(badges: List<Badge>)

    @Query("UPDATE badges SET isUnlocked = 1, unlockedAt = :time WHERE id = :id AND isUnlocked = 0")
    suspend fun unlockBadge(id: String, time: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM badges WHERE isUnlocked = 1")
    suspend fun countUnlocked(): Int

    @Query("SELECT * FROM badges WHERE id = :id")
    suspend fun getBadgeById(id: String): Badge?
}
