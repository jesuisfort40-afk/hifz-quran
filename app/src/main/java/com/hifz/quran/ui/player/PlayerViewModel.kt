package com.hifz.quran.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #8 — Memory leak : observeForever sans removeObserver
    //
    // AVANT : loadVersets() utilisait observeForever() sur la LiveData Room.
    //         Chaque appel à loadSourate() ajoutait un nouvel observateur
    //         qui ne sera jamais supprimé → fuite mémoire + doublons de données.
    //
    // APRÈS : on expose directement la LiveData Room via _versetsLiveData.
    //         Le fragment observe via viewLifecycleOwner → l'observer est
    //         automatiquement supprimé à la destruction du fragment.
    // ─────────────────────────────────────────────────────────────────────────
    private val _versetsLiveData = MutableLiveData<LiveData<List<Verset>>>()
    val versets: MutableLiveData<List<Verset>> = MutableLiveData(emptyList())
    private var currentVersetObserver: androidx.lifecycle.Observer<List<Verset>>? = null
    private var currentVersetLiveData: LiveData<List<Verset>>? = null

    val loopEnabled = MutableLiveData(false)
    val loopCount   = MutableLiveData(3)

    val segmentStart = MutableLiveData(0L)
    val segmentEnd   = MutableLiveData(0L)

    private var sessionStartTime = 0L

    fun loadSourate(id: Long) {
        viewModelScope.launch {
            val sourate = repo.getSourateById(id)
            _currentSourate.postValue(sourate)
            sourate?.let { loadVersets(it.id) }
        }
    }

    private fun loadVersets(sourateId: Long) {
        // Supprimer l'ancien observer avant d'en créer un nouveau
        val oldObserver = currentVersetObserver
        val oldLiveData = currentVersetLiveData
        if (oldObserver != null && oldLiveData != null) {
            oldLiveData.removeObserver(oldObserver)
        }

        val newObserver = androidx.lifecycle.Observer<List<Verset>> { list ->
            versets.postValue(list)
        }
        val newLiveData = repo.getVersetsBySourate(sourateId)
        newLiveData.observeForever(newObserver)
        currentVersetObserver = newObserver
        currentVersetLiveData = newLiveData
    }

    fun toggleLoop() {
        loopEnabled.value = !(loopEnabled.value ?: false)
    }

    fun setLoopCount(count: Int) {
        loopCount.value = count
    }

    fun setSegmentStart(ms: Long) { segmentStart.value = ms }
    fun setSegmentEnd(ms: Long)   { segmentEnd.value   = ms }

    fun saveVerset(startMs: Long, endMs: Long) {
        val sourateId = _currentSourate.value?.id ?: return
        val nextNum   = (versets.value?.size ?: 0) + 1
        viewModelScope.launch {
            repo.insertVerset(
                Verset(
                    sourateId = sourateId,
                    numero    = nextNum,
                    startMs   = startMs,
                    endMs     = endMs
                )
            )
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
            repo.insertSession(
                Session(
                    sourateId   = sourateId,
                    versetId    = versetId,
                    durationMs  = duration,
                    repeatsDone = repeatsDone
                )
            )
        }
    }

    override fun onCleared() {
        // Nettoyer l'observeForever restant
        val obs = currentVersetObserver
        val ld  = currentVersetLiveData
        if (obs != null && ld != null) ld.removeObserver(obs)
        super.onCleared()
    }
}
