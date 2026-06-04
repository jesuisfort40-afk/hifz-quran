package com.hifz.quran.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.data.QuranData
import com.hifz.quran.model.PlayerState
import com.hifz.quran.model.Verset
import kotlinx.coroutines.*

class AudioPlayerService : LifecycleService() {

    inner class PlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    private val binder = PlayerBinder()
    private lateinit var player: ExoPlayer

    val playerState = MutableLiveData(PlayerState())

    // ─── Boucle ───────────────────────────────────────────────────────────────
    private var loopCount        = 3
    private var currentLoop      = 0
    private var loopEnabled      = false
    private var segmentStart     = 0L
    private var segmentEnd       = 0L
    private var currentSourateId = -1L
    private var currentVersetId: Long? = null

    // ─── Streaming (bibliothèque) ─────────────────────────────────────────────
    private var streamingMode          = false
    private var streamingVersets       = listOf<Verset>()
    private var streamingReciterId     = ""
    private var streamingSourateNumber = 0
    private var streamingVersetIndex   = 0
    private var streamingReady         = false

    // ─── Plage de versets + répétition de plage ───────────────────────────────
    private var rangeStart       = -1
    private var rangeEnd         = -1
    private var rangeLoopCount   = 1
    private var rangeCurrentLoop = 0

    // ─── Retry sur erreur ─────────────────────────────────────────────────────
    private var retryCount = 0
    private val maxRetries = 2

    // ─── Callback stats ────────────────────────────────────────────────────────
    var onVersetPlayed: ((versetId: Long) -> Unit)? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    companion object {
        private const val TAG             = "AudioPlayerService"
        const val CHANNEL_ID              = "hifz_player_channel"
        const val NOTIF_ID                = 1001
        const val ACTION_PLAY_PAUSE       = "com.hifz.quran.PLAY_PAUSE"
        const val ACTION_STOP             = "com.hifz.quran.STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP       -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
        setupPlayerListener()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    retryCount = 0
                    if (streamingMode) handleStreamingEnd() else handleLoopOnEnd()
                }
                emitState()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
                emitState()
                updateNotification()
            }

            // FIX #3 — Gestion complète des erreurs avec retry + fallback + emitState
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Erreur lecture verset=$streamingVersetIndex : ${error.message}", error)
                if (streamingMode) {
                    retryCount++
                    if (retryCount <= maxRetries) {
                        // Retry le même verset (réseau instable)
                        Log.w(TAG, "Retry $retryCount/$maxRetries pour verset $streamingVersetIndex")
                        serviceScope.launch {
                            delay(500L * retryCount)
                            playStreamingVerset(streamingVersetIndex)
                        }
                    } else {
                        // Abandon : passer au verset suivant
                        retryCount = 0
                        Log.w(TAG, "Verset $streamingVersetIndex abandonné, passage au suivant")
                        val next = streamingVersetIndex + 1
                        if (next < streamingVersets.size) {
                            streamingVersetIndex = next
                            playStreamingVerset(next)
                        } else {
                            // Fin de liste sur erreur → pause propre
                            player.pause()
                            emitState()
                        }
                    }
                } else {
                    // Mode fichier local : signaler l'erreur via l'état
                    Log.e(TAG, "Erreur lecture fichier local : ${error.message}")
                    emitState()
                }
            }
        })
    }

    // ─── Mode fichier local ───────────────────────────────────────────────────
    fun loadAudio(uri: Uri, sourateId: Long, startMs: Long = 0L, endMs: Long = 0L) {
        if (uri.toString().isBlank()) return
        player.stop()
        player.clearMediaItems()
        streamingMode    = false
        streamingReady   = false
        currentLoop      = 0
        currentSourateId = sourateId
        segmentStart     = startMs
        segmentEnd       = endMs
        retryCount       = 0
        val mediaItem = if (endMs > startMs) {
            MediaItem.Builder().setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs).setEndPositionMs(endMs).build()
                ).build()
        } else MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (startMs > 0L && endMs == 0L) player.seekTo(startMs)
        startForeground(NOTIF_ID, buildNotification())
    }

    // ─── Mode streaming ───────────────────────────────────────────────────────
    fun loadStreaming(
        versets:          List<Verset>,
        sourateId:        Long,
        sourateNumber:    Int,
        reciterId:        String,
        startVersetIndex: Int = 0
    ) {
        if (versets.isEmpty()) return

        val isSameSourate = streamingReady &&
                streamingSourateNumber == sourateNumber &&
                streamingReciterId == reciterId

        if (!isSameSourate) {
            player.stop()
            player.clearMediaItems()
            currentLoop        = 0
            rangeStart         = -1
            rangeEnd           = -1
            rangeCurrentLoop   = 0
            retryCount         = 0
            // FIX #5 — Reset segment lors du changement de sourate
            segmentStart       = 0L
            segmentEnd         = 0L
        }

        streamingMode          = true
        streamingReady         = true
        streamingVersets       = versets
        streamingSourateNumber = sourateNumber
        streamingReciterId     = reciterId
        currentSourateId       = sourateId

        if (isSameSourate && (player.isPlaying || player.playbackState == Player.STATE_READY)) return

        streamingVersetIndex = startVersetIndex.coerceIn(0, versets.size - 1)
        currentLoop          = 0
        rangeCurrentLoop     = 0
        playStreamingVerset(streamingVersetIndex)
        startForeground(NOTIF_ID, buildNotification())
    }

    // FIX #2 — Suppression du player.stop() brutal avant chaque verset
    // On utilise setMediaItem() directement sur un player déjà préparé :
    // - si STATE_IDLE ou STATE_ENDED : prepare() est nécessaire
    // - si STATE_READY ou STATE_BUFFERING : setMediaItem() relance automatiquement
    // Cela élimine les coupures/silences entre versets
    private fun playStreamingVerset(index: Int) {
        val verset = streamingVersets.getOrNull(index) ?: return
        currentVersetId = verset.id

        val mediaItem = if (verset.localAudioPath.isNotEmpty() &&
            java.io.File(verset.localAudioPath).exists() &&
            java.io.File(verset.localAudioPath).length() > 0L
        ) {
            MediaItem.fromUri(Uri.fromFile(java.io.File(verset.localAudioPath)))
        } else {
            val url = QuranData.getVerseUrl(streamingReciterId, streamingSourateNumber, verset.numero)
            MediaItem.fromUri(Uri.parse(url))
        }

        // FIX #2 : ne pas appeler stop() ni clearMediaItems() ici
        // setMediaItem() + prepare() suffit pour changer de source sans coupure brutale
        val needsPrepare = player.playbackState == Player.STATE_IDLE ||
                           player.playbackState == Player.STATE_ENDED
        player.setMediaItem(mediaItem)
        player.playWhenReady = true
        if (needsPrepare) player.prepare()
        emitState()
    }

    // ─── Logique de fin de verset en streaming ────────────────────────────────
    private fun handleStreamingEnd() {
        currentVersetId?.let { id -> onVersetPlayed?.invoke(id) }

        val effectiveStart = if (rangeStart >= 0) rangeStart else 0
        val effectiveEnd   = if (rangeEnd   >= 0) rangeEnd   else streamingVersets.size - 1

        // Boucle sur le verset courant (répétition individuelle)
        if (loopEnabled) {
            val shouldRepeatVerset = loopCount == 0 || currentLoop < loopCount - 1
            if (shouldRepeatVerset) {
                currentLoop++
                playStreamingVerset(streamingVersetIndex)
                emitState()
                return
            } else {
                currentLoop = 0
            }
        }

        // Passer au verset suivant dans la plage
        val next = streamingVersetIndex + 1
        if (next <= effectiveEnd) {
            streamingVersetIndex = next
            playStreamingVerset(next)
        } else {
            if (rangeStart >= 0) {
                val shouldRepeatRange = rangeLoopCount == 0 || rangeCurrentLoop < rangeLoopCount - 1
                if (shouldRepeatRange) {
                    rangeCurrentLoop++
                    streamingVersetIndex = rangeStart
                    currentLoop          = 0
                    playStreamingVerset(rangeStart)
                } else {
                    rangeCurrentLoop = 0
                    streamingVersetIndex = rangeStart
                    player.pause()
                }
            } else {
                player.pause()
            }
        }
        emitState()
    }

    private fun handleLoopOnEnd() {
        if (!loopEnabled) return
        val shouldContinue = loopCount == 0 || currentLoop < loopCount - 1
        if (shouldContinue) {
            currentLoop++
            player.seekTo(segmentStart)
            player.play()
        } else {
            currentLoop = 0
            player.seekTo(segmentStart)
            player.pause()
        }
        emitState()
    }

    // ─── Contrôles ────────────────────────────────────────────────────────────
    fun play()  { player.play() }
    fun pause() { player.pause() }
    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play() }

    fun stopPlayback() {
        player.stop()
        player.clearMediaItems()
        streamingMode    = false
        streamingReady   = false
        currentLoop      = 0
        rangeStart       = -1
        rangeEnd         = -1
        rangeCurrentLoop = 0
        retryCount       = 0
        stopProgressUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(posMs: Long) { player.seekTo(posMs); emitState() }

    fun seekToVerset(versetIndex: Int) {
        if (!streamingMode || !streamingReady) return
        val idx = versetIndex.coerceIn(0, streamingVersets.size - 1)
        streamingVersetIndex = idx
        currentLoop          = 0
        retryCount           = 0
        playStreamingVerset(idx)
    }

    fun setVersetRange(startIndex: Int, endIndex: Int, loopCount: Int = 1) {
        rangeStart       = startIndex.coerceIn(0, streamingVersets.size - 1)
        rangeEnd         = endIndex.coerceIn(rangeStart, streamingVersets.size - 1)
        rangeLoopCount   = loopCount.coerceAtLeast(0)
        rangeCurrentLoop = 0
        streamingVersetIndex = rangeStart
        currentLoop      = 0
        retryCount       = 0
        playStreamingVerset(rangeStart)
        emitState()
    }

    fun clearVersetRange() {
        rangeStart       = -1
        rangeEnd         = -1
        rangeLoopCount   = 1
        rangeCurrentLoop = 0
        emitState()
    }

    fun setSegment(startMs: Long, endMs: Long) { segmentStart = startMs; segmentEnd = endMs }

    fun setLoop(enabled: Boolean, count: Int = 3) {
        loopEnabled = enabled
        loopCount   = count
        currentLoop = 0
        player.repeatMode = Player.REPEAT_MODE_OFF
        emitState()
    }

    fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed); emitState() }
    fun setVersetId(id: Long?) { currentVersetId = id }

    fun getCurrentPosition(): Long  = player.currentPosition
    fun getDuration(): Long         = if (player.duration > 0) player.duration else 0L
    fun isPlaying(): Boolean        = player.isPlaying
    fun isStreamingMode(): Boolean  = streamingMode
    fun isStreamingReady(): Boolean = streamingReady
    fun getCurrentStreamingVersetIndex(): Int = streamingVersetIndex
    fun getRangeStart(): Int        = rangeStart
    fun getRangeEnd(): Int          = rangeEnd
    fun getRangeLoopCount(): Int    = rangeLoopCount
    fun getRangeCurrentLoop(): Int  = rangeCurrentLoop

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                emitState()
                if (!streamingMode && loopEnabled && segmentEnd > 0L &&
                    player.isPlaying && player.currentPosition >= segmentEnd) {
                    handleLoopOnEnd()
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() { progressJob?.cancel(); progressJob = null }

    private fun emitState() {
        playerState.postValue(PlayerState(
            isPlaying        = player.isPlaying,
            currentPosition  = player.currentPosition.coerceAtLeast(0L),
            duration         = if (player.duration > 0L) player.duration else 0L,
            sourateId        = currentSourateId,
            versetId         = currentVersetId,
            loopEnabled      = loopEnabled,
            loopCount        = loopCount,
            loopCurrent      = currentLoop,
            speed            = player.playbackParameters.speed,
            segmentStart     = segmentStart,
            segmentEnd       = segmentEnd,
            rangeStart       = rangeStart,
            rangeEnd         = rangeEnd,
            rangeLoopCount   = rangeLoopCount,
            rangeCurrentLoop = rangeCurrentLoop
        ))
    }

    private fun buildNotification(): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(this, 0,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val isPlaying = player.isPlaying
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hifz Quran")
            .setContentText(if (isPlaying) "En lecture..." else "En pause")
            .setSmallIcon(R.drawable.ic_quran)
            .setContentIntent(mainIntent)
            .addAction(Notification.Action.Builder(null,
                if (isPlaying) "Pause" else "Reprendre", playPauseIntent).build())
            .addAction(Notification.Action.Builder(null, "Arrêter", stopIntent).build())
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Lecture Coran",
            NotificationManager.IMPORTANCE_LOW).apply { description = "Contrôle de lecture audio" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        player.release()
    }
}
