package com.hifz.quran.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Represents an audio file (full sourate or any audio)
@Entity(tableName = "sourates")
data class Sourate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arabicName: String = "",
    val filePath: String,           // URI string of the audio file
    val totalVersets: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val coverColor: String = "#1e3a5f"
)

// A verset = a defined audio segment inside a sourate
@Entity(tableName = "versets")
data class Verset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val numero: Int,                // Verset number e.g. 1, 2, 3
    val startMs: Long,              // Start position in milliseconds
    val endMs: Long,                // End position in milliseconds
    val status: VersetStatus = VersetStatus.A_APPRENDRE,
    val repeatCount: Int = 0,       // How many times user has listened
    val lastPracticed: Long = 0
)

enum class VersetStatus {
    A_APPRENDRE,    // Not started
    EN_COURS,       // In progress
    MAITRISE        // Mastered
}

// Session = one listening/practice session
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val versetId: Long? = null,
    val durationMs: Long,
    val repeatsDone: Int,
    val date: Long = System.currentTimeMillis()
)

// Reminder configuration
data class Reminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEnabled: Boolean,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
)

// Player state passed between Service and UI
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
    val segmentEnd: Long = 0L
)
