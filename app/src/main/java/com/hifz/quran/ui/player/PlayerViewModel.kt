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

    private var _versets: LiveData<List<Verset>>? = null
    val versets: MutableLiveData<List<Verset>> = MutableLiveData(emptyList())

    val loopEnabled = MutableLiveData(false)
    val loopCount = MutableLiveData(3)

    val segmentStart = MutableLiveData(0L)
    val segmentEnd = MutableLiveData(0L)

    private var sessionStartTime = 0L

    fun loadSourate(id: Long) {
        viewModelScope.launch {
            val sourate = repo.getSourateById(id)
            _currentSourate.postValue(sourate)
            sourate?.let { loadVersets(it.id) }
        }
    }

    private fun loadVersets(sourateId: Long) {
        repo.getVersetsBySourate(sourateId).observeForever { list ->
            versets.postValue(list)
        }
    }

    fun toggleLoop() {
        loopEnabled.value = !(loopEnabled.value ?: false)
    }

    fun setLoopCount(count: Int) {
        loopCount.value = count
    }

    fun setSegmentStart(ms: Long) { segmentStart.value = ms }
    fun setSegmentEnd(ms: Long) { segmentEnd.value = ms }

    fun saveVerset(startMs: Long, endMs: Long) {
        val sourateId = _currentSourate.value?.id ?: return
        val nextNum = (versets.value?.size ?: 0) + 1
        viewModelScope.launch {
            repo.insertVerset(
                Verset(
                    sourateId = sourateId,
                    numero = nextNum,
                    startMs = startMs,
                    endMs = endMs
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
            repo.insertSession(Session(
                sourateId = sourateId,
                versetId = versetId,
                durationMs = duration,
                repeatsDone = repeatsDone
            ))
        }
    }
}
