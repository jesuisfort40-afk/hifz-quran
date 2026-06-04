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

    private val _sourateId = MutableLiveData<Long?>(null)
    val versets: LiveData<List<Verset>> = _sourateId.switchMap { id ->
        if (id == null || id <= 0L) MutableLiveData(emptyList())
        else repo.getVersetsBySourate(id)
    }

    val loopEnabled  = MutableLiveData(false)
    val loopCount    = MutableLiveData(3)
    val segmentStart = MutableLiveData(0L)
    val segmentEnd   = MutableLiveData(0L)

    // PERSISTANCE ÉTAT — le ViewModel survit à la recréation du fragment (Activity scope).
    // On garde ici si la sourate a déjà été chargée dans le service, pour que
    // lorsqu'un nouveau PlayerFragment est créé avec le MÊME sourateId,
    // il ne recharge pas le service depuis zéro (évite le rechargement après navigation).
    var isSourateLoadedInService: Boolean = false
        private set

    private var sessionStartTime = 0L

    fun loadSourate(id: Long) {
        // Si même sourate déjà chargée → ne pas recharger le LiveData ni le service
        if (_sourateId.value == id && _currentSourate.value != null) return
        isSourateLoadedInService = false
        viewModelScope.launch {
            val sourate = repo.getSourateById(id)
            _currentSourate.postValue(sourate)
            _sourateId.postValue(id)
        }
    }

    /** Appelé par le Fragment quand le service a bien reçu la sourate */
    fun markSourateLoadedInService() {
        isSourateLoadedInService = true
    }

    /** Appelé quand on veut forcer le rechargement (sourate différente) */
    fun resetServiceLoadState() {
        isSourateLoadedInService = false
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

    override fun onCleared() {
        super.onCleared()
    }
}
