package com.hifz.quran.data

import android.content.Context
import android.util.Log
import com.hifz.quran.db.HifzDatabase
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Gère le chargement d'une sourate depuis la bibliothèque intégrée :
 *  1. Récupère le texte arabe depuis quran-json (CDN jsdelivr — gratuit, pas de clé API)
 *  2. Construit les objets Sourate + Verset en base
 *  3. L'audio est streamé verset par verset depuis everyayah.com
 */
class QuranRepository(private val context: Context) {

    private val db = HifzDatabase.getDatabase(context)
    private val sourateDao = db.sourateDao()
    private val versetDao = db.versetDao()

    // URL du JSON pour une sourate (texte arabe + translittération)
    // Format : { "id":67, "name":"Al-Mulk", "transliteration":"Al-Mulk",
    //            "verses":[ {"id":1,"text":"تَبَارَكَ الَّذِي..."}, ... ] }
    private fun surahJsonUrl(surahNumber: Int): String {
        val n = surahNumber.toString().padStart(3, '0') // non requis mais propre
        return "https://cdn.jsdelivr.net/npm/quran-json@3.1.2/dist/chapters/$surahNumber.json"
    }

    // ── Point d'entrée principal ───────────────────────────────────────────────
    /**
     * Importe une sourate depuis la bibliothèque dans la base locale.
     * Retourne l'id de la Sourate créée, ou -1L en cas d'erreur.
     */
    suspend fun importSurahFromLibrary(
        surahInfo: SurahInfo,
        reciter: ReciterInfo
    ): Long = withContext(Dispatchers.IO) {
        try {
            // 1. Télécharge les versets en JSON
            val verses = fetchVerseTexts(surahInfo.number)

            // 2. Crée la Sourate en base
            val sourate = Sourate(
                name = "${surahInfo.nameLatin} — ${surahInfo.nameFr}",
                arabicName = surahInfo.nameArabic,
                filePath = "",                        // pas de fichier local
                totalVersets = surahInfo.verseCount,
                coverColor = surahColorFor(surahInfo.number),
                sourateNumber = surahInfo.number,
                reciterId = reciter.id
            )
            val sourateId = sourateDao.insertSourate(sourate)

            // 3. Crée les Verset en base (startMs/endMs = 0, l'audio est streamé)
            val versetEntities = (1..surahInfo.verseCount).map { verseNum ->
                val textData = verses.getOrNull(verseNum - 1)
                Verset(
                    sourateId = sourateId,
                    numero = verseNum,
                    startMs = 0L,
                    endMs = 0L,
                    arabicText = textData?.first ?: "",
                    transliteration = textData?.second ?: "",
                    translationFr = ""
                )
            }
            versetDao.insertVersets(versetEntities)

            Log.d("QuranRepository", "Importé sourate ${surahInfo.nameLatin} (id=$sourateId)")
            sourateId

        } catch (e: Exception) {
            Log.e("QuranRepository", "Erreur import sourate ${surahInfo.number}", e)
            -1L
        }
    }

    // ── Récupère texte arabe + translittération ───────────────────────────────
    // Retourne une liste de Pair(arabicText, transliteration) indexée par verset
    private suspend fun fetchVerseTexts(surahNumber: Int): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = surahJsonUrl(surahNumber)
                val json = URL(url).readText()
                val obj = JSONObject(json)
                val versesArr = obj.getJSONArray("verses")
                val result = mutableListOf<Pair<String, String>>()
                for (i in 0 until versesArr.length()) {
                    val v = versesArr.getJSONObject(i)
                    val arabic = v.optString("text", "")
                    val translit = v.optString("transliteration", "")
                    result.add(Pair(arabic, translit))
                }
                result
            } catch (e: Exception) {
                Log.e("QuranRepository", "Impossible de charger le texte arabe ($surahNumber)", e)
                emptyList()
            }
        }

    // ── Construire l'URL audio d'un verset ────────────────────────────────────
    fun getVerseAudioUrl(reciterId: String, surahNumber: Int, verseNumber: Int): String =
        QuranData.getVerseUrl(reciterId, surahNumber, verseNumber)

    // ── Couleur par sourate (juz) ─────────────────────────────────────────────
    private fun surahColorFor(n: Int): String = when {
        n <= 9   -> "#1e3a5f"   // bleu nuit
        n <= 18  -> "#1a3a2a"   // vert forêt
        n <= 27  -> "#2d1b69"   // violet profond
        n <= 36  -> "#3a2a1a"   // brun doré
        n <= 48  -> "#1a3a3a"   // teal sombre
        n <= 60  -> "#3a1a2a"   // bordeaux
        n <= 77  -> "#1a2a3a"   // ardoise bleue
        n <= 93  -> "#2a1a3a"   // prune
        else     -> "#1a3a1a"   // vert émeraude
    }
}
