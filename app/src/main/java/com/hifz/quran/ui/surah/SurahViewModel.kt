package com.hifz.quran.ui.surah

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.Sourate
import kotlinx.coroutines.launch
import java.util.Random

class SurahViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HifzRepository(app)
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
                        name = name,
                        filePath = uri.toString(),
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
            try {
                repo.deleteSourate(sourate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSourateName(sourate: Sourate, newName: String) {
        viewModelScope.launch { repo.updateSourate(sourate.copy(name = newName)) }
    }
}
