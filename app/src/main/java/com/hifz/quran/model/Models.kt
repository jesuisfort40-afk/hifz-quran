package com.hifz.quran.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sourates")
data class Sourate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arabicName: String = "",
    val filePath: String,
    val totalVersets: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val coverColor: String = "#1e3a5f",
    val sourateNumber: Int = 0,
    val reciterId: String = ""
) {
    val isFromLibrary: Boolean get() = sourateNumber > 0 && reciterId.isNotEmpty()
}

@Entity(tableName = "versets")
data class Verset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val numero: Int,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val status: VersetStatus = VersetStatus.A_APPRENDRE,
    val repeatCount: Int = 0,
    val lastPracticed: Long = 0,
    val arabicText: String = "",
    val transliteration: String = "",
    val translationFr: String = "",
    val localAudioPath: String = ""
)

enum class VersetStatus { A_APPRENDRE, EN_COURS, MAITRISE }

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val versetId: Long? = null,
    val durationMs: Long,
    val repeatsDone: Int,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "badges")
data class Badge(
    @PrimaryKey val id: String,
    val titleFr: String,
    val titleAr: String,
    val description: String,
    val iconRes: String,
    val unlockedAt: Long = 0L,
    val isUnlocked: Boolean = false
)

data class Reminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEnabled: Boolean,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val sourateId: Long = -1L,
    val versetId: Long? = null,
    val loopEnabled: Boolean = false,
    val loopCount: Int = 3,
    val loopCurrent: Int = 0,
    val speed: Float = 1.0f,
    val segmentStart: Long = 0L,
    val segmentEnd: Long = 0L,
    val rangeStart: Int = -1,
    val rangeEnd: Int = -1,
    // FIX NOUVEAU : répétition de plage
    val rangeLoopCount: Int = 1,    // nombre de fois à répéter la plage (0 = infini)
    val rangeCurrentLoop: Int = 0   // compteur courant
)
