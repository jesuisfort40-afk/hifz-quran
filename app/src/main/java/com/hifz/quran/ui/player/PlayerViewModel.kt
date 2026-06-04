package com.hifz.quran.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.hifz.quran.db.HifzRepository
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HifzRepository(app)

    private val _currentSourate = MutableLiveData<Sourate?>(null)
    val currentSourate: LiveData<Sourate?> = _currentSourate

    // FIX #6 — Remplacement de observeForever par switchMap (lifecycle-aware)
    // Avantage : pas besoin de gérer manuellement removeObserver/onCleared
    // Le LiveData de versets se met à jour automatiquement quand sourateId change,
    // sans risque de fuite mémoire ni d'observer fantôme en cas d'appels multiples
    private val _sourateId = MutableLiveData<Long?>(null)
    val versets: LiveData<List<Verset>> = _sourateId.switchMap { id ->
        if (id == null || id <= 0L) {
            MutableLiveData(emptyList())
        } else {
            repo.getVersetsBySourate(id)
        }
    }

    val loopEnabled  = MutableLiveData(false)
    val loopCount    = MutableLiveData(3)
    val segmentStart = MutableLiveData(0L)
    val segmentEnd   = MutableLiveData(0L)

    private var sessionStartTime = 0L

    fun loadSourate(id: Long) {
        viewModelScope.launch {
            val sourate = repo.getSourateById(id)
            _currentSourate.postValue(sourate)
            // FIX #6 — on met à jour _sourateId pour déclencher switchMap
            _sourateId.postValue(id)
        }
    }

    fun toggleLoop()                     { loopEnabled.value = !(loopEnabled.value ?: false) }
    fun setLoopEnabled(enabled: Boolean) { loopEnabled.value = enabled }
    fun setLoopCount(count: Int)         { loopCount.value = count }
    fun setSegmentStart(ms: Long)        { segmentStart.value = ms }
    fun setSegmentEnd(ms: Long)          { segmentEnd.value   = ms }

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
        viewModelScope.launch {
            repo.updateVersetStatus(versetId, status)
            repo.checkAndUnlockBadges()
        }
    }

    fun incrementRepeat(versetId: Long) {
        viewModelScope.launch {
            repo.incrementVersetRepeat(versetId)
            repo.checkAndUnlockBadges()
        }
    }

    fun deleteVerset(verset: Verset) {
        viewModelScope.launch { repo.deleteVerset(verset) }
    }

    fun startSession() { sessionStartTime = System.currentTimeMillis() }

    // FIX #10 — Accepte les vraies valeurs de versetId et repeatsDone
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
            repo.checkAndUnlockBadges()
        }
    }

    // FIX #6 — onCleared() simplifié : plus besoin de removeObserver manuel
    override fun onCleared() {
        super.onCleared()
    }
}
