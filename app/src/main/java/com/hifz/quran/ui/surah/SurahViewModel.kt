package com.hifz.quran.ui.surah

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hifz.quran.data.QuranRepository
import com.hifz.quran.data.ReciterInfo
import com.hifz.quran.data.SurahInfo
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.Sourate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class SurahViewModel(app: Application) : AndroidViewModel(app) {

    private val repo       = HifzRepository(app)
    private val quranRepo  = QuranRepository(app)

    val sourates: LiveData<List<Sourate>> = repo.getAllSourates()
    val selectedSourateId = MutableLiveData<Long>(-1L)

    private val colors = listOf(
        "#1e3a5f", "#1a3a2a", "#3a1a3a", "#3a2a1a", "#1a2a3a",
        "#2a1a1a", "#1a3a3a", "#2d1b69", "#1a4a2a", "#3a1a2a"
    )

    fun setSelectedSourate(id: Long) {
        selectedSourateId.value = id
    }

    // ── Import depuis fichier local (legacy) ──────────────────────────────────
    fun addSourate(uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val color = colors[Random().nextInt(colors.size)]
                repo.insertSourate(
                    Sourate(
                        name       = name,
                        filePath   = uri.toString(),
                        coverColor = color
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSourate(sourate: Sourate) {
        viewModelScope.launch {
            try { repo.deleteSourate(sourate) }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateSourateName(sourate: Sourate, newName: String) {
        viewModelScope.launch { repo.updateSourate(sourate.copy(name = newName)) }
    }

    // ── Méthodes requises par SurahBrowserFragment ────────────────────────────

    /**
     * Vérifie si une sourate de la bibliothèque est déjà importée
     * (même numéro de sourate + même récitateur).
     *
     * FIX BUILD : méthode manquante → "Unresolved reference: isSurahAlreadyImported"
     */
    fun isSurahAlreadyImported(surahNumber: Int, reciterId: String): Boolean {
        // Lecture synchrone de la liste déjà chargée en mémoire via LiveData
        val list = sourates.value ?: return false
        return list.any { it.sourateNumber == surahNumber && it.reciterId == reciterId }
    }

    /**
     * Retourne l'id en base d'une sourate déjà importée, ou null si absente.
     *
     * FIX BUILD : méthode manquante → "Unresolved reference: getExistingSurahId"
     */
    fun getExistingSurahId(surahNumber: Int, reciterId: String): Long? {
        val list = sourates.value ?: return null
        return list.firstOrNull { it.sourateNumber == surahNumber && it.reciterId == reciterId }?.id
    }

    /**
     * Importe une sourate depuis la bibliothèque intégrée (streaming Alafasy/everyayah).
     * Télécharge le texte arabe, crée la Sourate + les Versets en base.
     * Appelle [onResult] avec l'id de la sourate créée (> 0) ou -1L en cas d'erreur.
     *
     * FIX BUILD : méthode manquante → "Unresolved reference: importSurahFromLibrary"
     * FIX BUILD : paramètre lambda sans type explicite → inférence impossible
     */
    fun importSurahFromLibrary(
        surah: SurahInfo,
        reciter: ReciterInfo,
        onResult: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                quranRepo.importSurahFromLibrary(surah, reciter)
            }
            // onResult est toujours appelé sur le Main thread
            onResult(id)
        }
    }
}
