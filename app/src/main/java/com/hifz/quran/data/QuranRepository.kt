package com.hifz.quran.data

import android.content.Context
import android.util.Log
import com.hifz.quran.db.HifzDatabase
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * Importe une sourate depuis la bibliothèque intégrée.
 *
 * BUG FIX #1 — MODE OFFLINE :
 * Avant : l'audio était streamé en direct → impossible sans connexion.
 * Après : chaque verset MP3 est téléchargé dans le dossier privé de l'app
 *         (context.filesDir/audio/<reciterId>/<surah>/<verse>.mp3).
 *         Le chemin local est stocké dans Verset.filePath.
 *         Lors de la lecture, AudioPlayerService utilise ce chemin local
 *         si le fichier existe, sinon fallback vers l'URL.
 */
class QuranRepository(private val context: Context) {

    private val db         = HifzDatabase.getDatabase(context)
    private val sourateDao = db.sourateDao()
    private val versetDao  = db.versetDao()

    // Dossier de base pour les MP3 téléchargés
    private val audioDir: File
        get() = File(context.filesDir, "audio").also { it.mkdirs() }

    // ── URL JSON texte arabe ──────────────────────────────────────────────────
    private fun surahJsonUrl(surahNumber: Int) =
        "https://cdn.jsdelivr.net/npm/quran-json@3.1.2/dist/chapters/$surahNumber.json"

    // ── Chemin local d'un fichier MP3 ─────────────────────────────────────────
    fun getLocalAudioPath(reciterId: String, surahNumber: Int, verseNumber: Int): File {
        val dir = File(audioDir, "$reciterId/${surahNumber.toString().padStart(3, '0')}")
        dir.mkdirs()
        return File(dir, "${verseNumber.toString().padStart(3, '0')}.mp3")
    }

    fun isVerseDownloaded(reciterId: String, surahNumber: Int, verseNumber: Int): Boolean =
        getLocalAudioPath(reciterId, surahNumber, verseNumber).let { it.exists() && it.length() > 0 }

    // ── Point d'entrée principal ──────────────────────────────────────────────
    suspend fun importSurahFromLibrary(
        surahInfo: SurahInfo,
        reciter:   ReciterInfo
    ): Long = withContext(Dispatchers.IO) {
        try {
            // 1. Télécharger le texte arabe
            val verses = fetchVerseTexts(surahInfo.number)

            // 2. Créer la Sourate en base
            val sourate = Sourate(
                name         = "${surahInfo.nameLatin} — ${surahInfo.nameFr}",
                arabicName   = surahInfo.nameArabic,
                filePath     = "",
                totalVersets = surahInfo.verseCount,
                coverColor   = surahColorFor(surahInfo.number),
                sourateNumber = surahInfo.number,
                reciterId    = reciter.id
            )
            val sourateId = sourateDao.insertSourate(sourate)

            // 3. Télécharger chaque verset MP3 + insérer en base
            val versetEntities = mutableListOf<Verset>()
            for (verseNum in 1..surahInfo.verseCount) {
                val textData  = verses.getOrNull(verseNum - 1)
                val localFile = getLocalAudioPath(reciter.id, surahInfo.number, verseNum)
                val audioUrl  = QuranData.getVerseUrl(reciter.id, surahInfo.number, verseNum)

                // Télécharger le MP3 si pas déjà présent
                if (!localFile.exists() || localFile.length() == 0L) {
                    downloadFile(audioUrl, localFile)
                }

                versetEntities.add(
                    Verset(
                        sourateId       = sourateId,
                        numero          = verseNum,
                        startMs         = 0L,
                        endMs           = 0L,
                        arabicText      = textData?.first  ?: "",
                        transliteration = textData?.second ?: "",
                        translationFr   = "",
                        // BUG FIX : stocker le chemin local pour la lecture offline
                        localAudioPath  = if (localFile.exists()) localFile.absolutePath else ""
                    )
                )
            }
            versetDao.insertVersets(versetEntities)

            Log.d("QuranRepository", "Importé ${surahInfo.nameLatin} (id=$sourateId, ${surahInfo.verseCount} versets)")
            sourateId

        } catch (e: Exception) {
            Log.e("QuranRepository", "Erreur import ${surahInfo.number}", e)
            -1L
        }
    }

    // ── Téléchargement d'un fichier ───────────────────────────────────────────
    private fun downloadFile(url: String, dest: File) {
        try {
            URL(url).openStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w("QuranRepository", "Impossible de télécharger $url", e)
            // On ne crash pas : la lecture se fera en streaming si le fichier manque
        }
    }

    // ── Texte arabe ───────────────────────────────────────────────────────────
    private suspend fun fetchVerseTexts(surahNumber: Int): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val json = URL(surahJsonUrl(surahNumber)).readText()
                val obj  = JSONObject(json)
                val arr  = obj.getJSONArray("verses")
                (0 until arr.length()).map { i ->
                    val v = arr.getJSONObject(i)
                    Pair(v.optString("text", ""), v.optString("transliteration", ""))
                }
            } catch (e: Exception) {
                Log.e("QuranRepository", "Erreur texte arabe $surahNumber", e)
                emptyList()
            }
        }

    fun getVerseAudioUrl(reciterId: String, surahNumber: Int, verseNumber: Int): String =
        QuranData.getVerseUrl(reciterId, surahNumber, verseNumber)

    private fun surahColorFor(n: Int) = when {
        n <= 9  -> "#1e3a5f"
        n <= 18 -> "#1a3a2a"
        n <= 27 -> "#2d1b69"
        n <= 36 -> "#3a2a1a"
        n <= 48 -> "#1a3a3a"
        n <= 60 -> "#3a1a2a"
        n <= 77 -> "#1a2a3a"
        n <= 93 -> "#2a1a3a"
        else    -> "#1a3a1a"
    }
}
