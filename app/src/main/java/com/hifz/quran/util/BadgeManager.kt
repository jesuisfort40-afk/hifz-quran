package com.hifz.quran.util

import com.hifz.quran.model.Badge

/**
 * Définition de tous les badges disponibles dans l'app.
 * Débloqués automatiquement selon les actions de l'utilisateur.
 */
object BadgeManager {

    // ── Définitions des badges ────────────────────────────────────────────────
    val ALL_BADGES = listOf(

        // ── Premiers pas ──────────────────────────────────────────────────────
        Badge(
            id = "first_surah",
            titleFr = "Première Sourate",
            titleAr = "أول سورة",
            description = "Importer ta première sourate dans la bibliothèque",
            iconRes = "badge_book"
        ),
        Badge(
            id = "first_listen",
            titleFr = "Premier Écoute",
            titleAr = "أول استماع",
            description = "Écouter un verset pour la première fois",
            iconRes = "badge_headphones"
        ),
        Badge(
            id = "first_mastered",
            titleFr = "Premier Maîtrisé",
            titleAr = "أول حفظ",
            description = "Marquer ton premier verset comme Maîtrisé",
            iconRes = "badge_star"
        ),

        // ── Répétitions ───────────────────────────────────────────────────────
        Badge(
            id = "repeat_10",
            titleFr = "10 Répétitions",
            titleAr = "عشر مرات",
            description = "Écouter un verset 10 fois",
            iconRes = "badge_repeat"
        ),
        Badge(
            id = "repeat_50",
            titleFr = "50 Répétitions",
            titleAr = "خمسون مرة",
            description = "Écouter des versets 50 fois au total",
            iconRes = "badge_repeat_gold"
        ),
        Badge(
            id = "repeat_100",
            titleFr = "100 Répétitions",
            titleAr = "مئة مرة",
            description = "Écouter des versets 100 fois au total",
            iconRes = "badge_trophy"
        ),

        // ── Streak ────────────────────────────────────────────────────────────
        Badge(
            id = "streak_3",
            titleFr = "3 Jours Consécutifs",
            titleAr = "ثلاثة أيام متتالية",
            description = "Pratiquer 3 jours de suite",
            iconRes = "badge_flame"
        ),
        Badge(
            id = "streak_7",
            titleFr = "Semaine Complète",
            titleAr = "أسبوع كامل",
            description = "Pratiquer 7 jours de suite",
            iconRes = "badge_flame_gold"
        ),
        Badge(
            id = "streak_30",
            titleFr = "Un Mois de Hifz",
            titleAr = "شهر من الحفظ",
            description = "Pratiquer 30 jours de suite",
            iconRes = "badge_diamond"
        ),

        // ── Maîtrise ─────────────────────────────────────────────────────────
        Badge(
            id = "mastered_5",
            titleFr = "5 Versets Maîtrisés",
            titleAr = "خمس آيات محفوظة",
            description = "Maîtriser 5 versets",
            iconRes = "badge_quran"
        ),
        Badge(
            id = "mastered_10",
            titleFr = "10 Versets Maîtrisés",
            titleAr = "عشر آيات محفوظة",
            description = "Maîtriser 10 versets",
            iconRes = "badge_quran_gold"
        ),
        Badge(
            id = "fatiha_complete",
            titleFr = "Al-Fatiha",
            titleAr = "الفاتحة",
            description = "Maîtriser tous les versets d'Al-Fatiha",
            iconRes = "badge_moon"
        ),

        // ── Temps ─────────────────────────────────────────────────────────────
        Badge(
            id = "time_60min",
            titleFr = "1 Heure d'Écoute",
            titleAr = "ساعة استماع",
            description = "Accumuler 1 heure d'écoute au total",
            iconRes = "badge_clock"
        ),
        Badge(
            id = "time_600min",
            titleFr = "10 Heures d'Écoute",
            titleAr = "عشر ساعات استماع",
            description = "Accumuler 10 heures d'écoute au total",
            iconRes = "badge_clock_gold"
        ),

        // ── Bibliothèque ──────────────────────────────────────────────────────
        Badge(
            id = "library_5",
            titleFr = "Bibliothèque Riche",
            titleAr = "مكتبة غنية",
            description = "Importer 5 sourates",
            iconRes = "badge_library"
        )
    )

    // Vérifie quels badges doivent être débloqués selon les stats
    fun checkBadgesToUnlock(
        totalSourates: Int,
        totalListens: Int,
        totalMastered: Int,
        streakDays: Int,
        totalMinutes: Long,
        versetRepeatMax: Int,
        fatihaMastered: Boolean
    ): List<String> {
        val toUnlock = mutableListOf<String>()

        if (totalSourates >= 1)   toUnlock.add("first_surah")
        if (totalListens >= 1)    toUnlock.add("first_listen")
        if (totalMastered >= 1)   toUnlock.add("first_mastered")
        if (versetRepeatMax >= 10) toUnlock.add("repeat_10")
        if (totalListens >= 50)   toUnlock.add("repeat_50")
        if (totalListens >= 100)  toUnlock.add("repeat_100")
        if (streakDays >= 3)      toUnlock.add("streak_3")
        if (streakDays >= 7)      toUnlock.add("streak_7")
        if (streakDays >= 30)     toUnlock.add("streak_30")
        if (totalMastered >= 5)   toUnlock.add("mastered_5")
        if (totalMastered >= 10)  toUnlock.add("mastered_10")
        if (fatihaMastered)       toUnlock.add("fatiha_complete")
        if (totalMinutes >= 60)   toUnlock.add("time_60min")
        if (totalMinutes >= 600)  toUnlock.add("time_600min")
        if (totalSourates >= 5)   toUnlock.add("library_5")

        return toUnlock
    }
}
