package com.hifz.quran.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.model.PlayerState
import kotlinx.coroutines.*

class AudioPlayerService : LifecycleService() {

    inner class PlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    private val binder = PlayerBinder()
    private lateinit var player: ExoPlayer

    val playerState = MutableLiveData(PlayerState())

    private var loopCount = 3
    private var currentLoop = 0
    private var loopEnabled = false
    private var segmentStart = 0L
    private var segmentEnd = 0L
    private var currentSourateId = -1L
    private var currentVersetId: Long? = null

    private val progressJob = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressUpdate: Job? = null

    companion object {
        const val CHANNEL_ID = "hifz_player_channel"
        const val NOTIF_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.hifz.quran.PLAY_PAUSE"
        const val ACTION_STOP = "com.hifz.quran.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
        setupPlayerListener()
    }

    // ✅ FIX BUG AUDIO CONTINUE + NOTIFICATION BLOQUÉE :
    // onStartCommand gère maintenant les actions PLAY_PAUSE et STOP
    // envoyées depuis les boutons de la notification.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        if (loopEnabled && segmentEnd > 0) {
                            currentLoop++
                            if (loopCount == 0 || currentLoop < loopCount) {
                                player.seekTo(segmentStart)
                                player.play()
                            } else {
                                currentLoop = 0
                                player.pause()
                            }
                        }
                        emitState()
                    }
                    else -> emitState()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
                emitState()
                updateNotification()
            }
        })
    }

    fun loadAudio(uri: Uri, sourateId: Long, startMs: Long = 0L, endMs: Long = 0L) {
        currentSourateId = sourateId
        segmentStart = startMs
        segmentEnd = endMs

        val mediaItem = if (endMs > startMs) {
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                ).build()
        } else {
            MediaItem.fromUri(uri)
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        if (startMs > 0 && endMs == 0L) player.seekTo(startMs)
        startForeground(NOTIF_ID, buildNotification())
    }

    fun play() { player.play() }
    fun pause() { player.pause() }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    // ✅ FIX BUG AUDIO CONTINUE + NOTIFICATION BLOQUÉE :
    // stopPlayback() arrête proprement le player, retire la notification
    // et stoppe le service foreground.
    fun stopPlayback() {
        player.stop()
        stopProgressUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(posMs: Long) {
        player.seekTo(posMs)
        emitState()
    }

    fun setSegment(startMs: Long, endMs: Long) {
        segmentStart = startMs
        segmentEnd = endMs
    }

    fun setLoop(enabled: Boolean, count: Int = 3) {
        loopEnabled = enabled
        loopCount = count
        currentLoop = 0
        if (enabled && segmentEnd > segmentStart) {
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        emitState()
    }

    fun setVersetId(id: Long?) { currentVersetId = id }

    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration.coerceAtLeast(0L)
    fun isPlaying(): Boolean = player.isPlaying

    private fun startProgressUpdates() {
        progressUpdate?.cancel()
        progressUpdate = progressJob.launch {
            while (true) {
                emitState()
                if (loopEnabled && segmentEnd > 0 && player.currentPosition >= segmentEnd) {
                    currentLoop++
                    if (loopCount == 0 || currentLoop < loopCount) {
                        player.seekTo(segmentStart)
                    } else {
                        currentLoop = 0
                        player.pause()
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() { progressUpdate?.cancel() }

    private fun emitState() {
        playerState.postValue(
            PlayerState(
                isPlaying = player.isPlaying,
                currentPosition = player.currentPosition,
                duration = player.duration.coerceAtLeast(0L),
                sourateId = currentSourateId,
                versetId = currentVersetId,
                loopEnabled = loopEnabled,
                loopCount = loopCount,
                loopCurrent = currentLoop,
                speed = player.playbackParameters.speed,
                segmentStart = segmentStart,
                segmentEnd = segmentEnd
            )
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lecture Coran",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Contrôle de lecture audio" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ FIX BUG NOTIFICATION BLOQUÉE :
        // Les PendingIntent pointent maintenant vers le SERVICE avec une action explicite
        // (pas un broadcast implicite ignoré sur Android 8+).
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hifz Quran")
            .setContentText(if (player.isPlaying) "En lecture..." else "En pause")
            .setSmallIcon(R.drawable.ic_quran)
            .setContentIntent(mainIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (player.isPlaying) "Pause" else "Play",
                    playPauseIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(null, "Stop", stopIntent).build()
            )
            .setOngoing(player.isPlaying) // ✅ ongoing seulement si en lecture
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob.cancel()
        player.release()
    }
}
