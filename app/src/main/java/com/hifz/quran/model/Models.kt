package com.hifz.quran.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
//  SOURATE
//  Phase 1 : ajout de sourateNumber + reciterId pour lier à la bibliothèque
// ─────────────────────────────────────────────────────────────────────────────
@Entity(tableName = "sourates")
data class Sourate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arabicName: String = "",
    val filePath: String,           // URI local OU "" si mode streaming
    val totalVersets: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val coverColor: String = "#1e3a5f",

    // ── Nouveaux champs Phase 1 ──
    val sourateNumber: Int = 0,     // 1–114 (0 = ajout manuel legacy)
    val reciterId: String = ""      // ex: "Alafasy_128kbps"  (vide = fichier local)
) {
    /** true si cette sourate vient de la bibliothèque intégrée */
    val isFromLibrary: Boolean get() = sourateNumber > 0 && reciterId.isNotEmpty()
}

// ─────────────────────────────────────────────────────────────────────────────
//  VERSET
//  Phase 1 : ajout du texte arabe + translittération
// ─────────────────────────────────────────────────────────────────────────────
@Entity(tableName = "versets")
data class Verset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val numero: Int,                // numéro du verset (1-based)
    val startMs: Long = 0L,         // position dans le fichier local (legacy)
    val endMs: Long = 0L,
    val status: VersetStatus = VersetStatus.A_APPRENDRE,
    val repeatCount: Int = 0,
    val lastPracticed: Long = 0,

    // ── Nouveaux champs Phase 1 ──
    val arabicText: String = "",    // texte arabe du verset
    val transliteration: String = "",
    val translationFr: String = ""
)

enum class VersetStatus {
    A_APPRENDRE,
    EN_COURS,
    MAITRISE
}

// Session + Reminder + PlayerState inchangés
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourateId: Long,
    val versetId: Long? = null,
    val durationMs: Long,
    val repeatsDone: Int,
    val date: Long = System.currentTimeMillis()
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
    val segmentEnd: Long = 0L
)
