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
    private var currentVersetObserver: Observer<List<Verset>>? = null
    private var currentVersetLiveData: LiveData<List<Verset>>? = null
    private var expectedVersetCount = 0

    // FIX : loopEnabled expose setLoopEnabled() en plus de toggleLoop()
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
                expectedVersetCount = sourate.totalVersets
                loadVersets(sourate.id)
            }
        }
    }

    private fun loadVersets(sourateId: Long) {
        currentVersetObserver?.let { currentVersetLiveData?.removeObserver(it) }
        val newObserver = Observer<List<Verset>> { list -> versets.postValue(list) }
        val newLiveData = repo.getVersetsBySourate(sourateId)
        newLiveData.observeForever(newObserver)
        currentVersetObserver = newObserver
        currentVersetLiveData = newLiveData
    }

    fun toggleLoop()             { loopEnabled.value = !(loopEnabled.value ?: false) }
    // FIX : méthode directe pour le Switch
    fun setLoopEnabled(enabled: Boolean) { loopEnabled.value = enabled }
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
        viewModelScope.launch {
            repo.updateVersetStatus(versetId, status)
            // Vérifier les badges après changement de statut
            repo.checkAndUnlockBadges()
        }
    }

    // FIX STATS : incrementRepeat utilisé aussi depuis le service (lecture auto)
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
        currentVersetObserver?.let { currentVersetLiveData?.removeObserver(it) }
        super.onCleared()
    }
}
