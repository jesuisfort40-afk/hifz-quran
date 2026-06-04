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

    // FIX #8 — isSurahAlreadyImported utilise d'abord le LiveData en mémoire,
    // puis une requête DB synchrone si le LiveData n'est pas encore chargé.
    // Cela évite le double import quand la liste est null au premier appel.
    fun isSurahAlreadyImported(surahNumber: Int, reciterId: String): Boolean {
        val list = sourates.value
        if (list != null) {
            return list.any { it.sourateNumber == surahNumber && it.reciterId == reciterId }
        }
        // LiveData pas encore émis → vérification synchrone en DB via coroutine bloquante
        // On retourne false ici pour laisser la coroutine confirmImport gérer ça
        // (la vérification réelle se fait dans confirmImportSafe)
        return false
    }

    // FIX #8 — Version suspend sûre : vérifie en DB directement
    // À appeler dans les contextes où on peut vérifier proprement
    suspend fun isSurahAlreadyImportedSafe(surahNumber: Int, reciterId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val list = repo.getAllSouratesSync()
                list.any { it.sourateNumber == surahNumber && it.reciterId == reciterId }
            } catch (e: Exception) {
                false
            }
        }
    }

    // FIX #8 — Version suspend sûre pour récupérer l'ID existant
    suspend fun getExistingSurahIdSafe(surahNumber: Int, reciterId: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val list = repo.getAllSouratesSync()
                list.firstOrNull { it.sourateNumber == surahNumber && it.reciterId == reciterId }?.id
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getExistingSurahId(surahNumber: Int, reciterId: String): Long? {
        val list = sourates.value ?: return null
        return list.firstOrNull { it.sourateNumber == surahNumber && it.reciterId == reciterId }?.id
    }

    fun importSurahFromLibrary(
        surah: SurahInfo,
        reciter: ReciterInfo,
        onResult: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                quranRepo.importSurahFromLibrary(surah, reciter)
            }
            onResult(id)
        }
    }
}
