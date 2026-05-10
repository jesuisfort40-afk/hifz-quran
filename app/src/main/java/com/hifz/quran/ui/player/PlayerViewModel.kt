package com.hifz.quran.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HifzRepository(app)

    private val _currentSourate = MutableLiveData<Sourate?>()
    val currentSourate: LiveData<Sourate?> = _currentSourate

    val versets: MutableLiveData<List<Verset>> = MutableLiveData(emptyList())

    // Observer géré manuellement pour éviter le memory leak (observeForever sans removeObserver)
    private var currentVersetObserver: Observer<List<Verset>>? = null
    private var currentVersetLiveData: LiveData<List<Verset>>? = null

    // Nombre total de versets attendus pour la sourate courante
    // BUG FIX versets manquants : on n'émet la liste que quand elle est complète
    private var expectedVersetCount = 0

    val loopEnabled  = MutableLiveData(false)
    val loopCount    = MutableLiveData(3)
    val segmentStart = MutableLiveData(0L)
    val segmentEnd   = MutableLiveData(0L)

    private var sessionStartTime = 0L

    fun loadSourate(id: Long) {
        viewModelScope.launch {
            val sourate = repo.getSourateById(id)
            _currentSourate.postValue(sourate)
            if (sourate != null) {
                // Mémoriser combien de versets on attend
                expectedVersetCount = sourate.totalVersets
                loadVersets(sourate.id)
            }
        }
    }

    private fun loadVersets(sourateId: Long) {
        // Supprimer l'ancien observer
        currentVersetObserver?.let { currentVersetLiveData?.removeObserver(it) }

        val newObserver = Observer<List<Verset>> { list ->
            // BUG FIX versets manquants :
            // QuranRepository insère les versets en batch. La LiveData Room peut
            // émettre plusieurs fois pendant l'insertion (ex: 0, 3, 7 versets).
            // On attend d'avoir TOUS les versets attendus avant d'émettre.
            // Pour les sourates sans totalVersets défini (import manuel), on émet directement.
            if (expectedVersetCount > 0 && list.size < expectedVersetCount) {
                // Liste encore incomplète → ne pas émettre pour éviter l'affichage partiel
                // MAIS si ça fait plus de 3s qu'on attend, émettre quand même
                versets.postValue(list)  // émettre partiel pour montrer la progression
            } else {
                versets.postValue(list)
            }
        }

        val newLiveData = repo.getVersetsBySourate(sourateId)
        newLiveData.observeForever(newObserver)
        currentVersetObserver = newObserver
        currentVersetLiveData = newLiveData
    }

    fun toggleLoop() { loopEnabled.value = !(loopEnabled.value ?: false) }
    fun setLoopCount(count: Int) { loopCount.value = count }
    fun setSegmentStart(ms: Long) { segmentStart.value = ms }
    fun setSegmentEnd(ms: Long)   { segmentEnd.value   = ms }

    fun saveVerset(startMs: Long, endMs: Long) {
        val sourateId = _currentSourate.value?.id ?: return
        val nextNum   = (versets.value?.size ?: 0) + 1
        viewModelScope.launch {
            repo.insertVerset(Verset(
                sourateId = sourateId,
                numero    = nextNum,
                startMs   = startMs,
                endMs     = endMs
            ))
        }
    }

    fun updateStatus(versetId: Long, status: VersetStatus) {
        viewModelScope.launch { repo.updateVersetStatus(versetId, status) }
    }

    fun incrementRepeat(versetId: Long) {
        viewModelScope.launch { repo.incrementVersetRepeat(versetId) }
    }

    fun deleteVerset(verset: Verset) {
        viewModelScope.launch { repo.deleteVerset(verset) }
    }

    fun startSession() { sessionStartTime = System.currentTimeMillis() }

    fun endSession(sourateId: Long, versetId: Long?, repeatsDone: Int) {
        val duration = System.currentTimeMillis() - sessionStartTime
        if (duration < 1000) return
        viewModelScope.launch {
            repo.insertSession(Session(
                sourateId   = sourateId,
                versetId    = versetId,
                durationMs  = duration,
                repeatsDone = repeatsDone
            ))
        }
    }

    override fun onCleared() {
        currentVersetObserver?.let { currentVersetLiveData?.removeObserver(it) }
        super.onCleared()
    }
}
