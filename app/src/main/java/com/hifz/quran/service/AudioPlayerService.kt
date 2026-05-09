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

    // ─── État boucle ──────────────────────────────────────────────────────────
    private var loopCount    = 3      // nombre de répétitions voulu (0 = infini)
    private var currentLoop  = 0      // compteur de répétitions effectuées
    private var loopEnabled  = false
    private var segmentStart = 0L
    private var segmentEnd   = 0L
    private var currentSourateId  = -1L
    private var currentVersetId: Long? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    companion object {
        const val CHANNEL_ID         = "hifz_player_channel"
        const val NOTIF_ID           = 1001
        const val ACTION_PLAY_PAUSE  = "com.hifz.quran.PLAY_PAUSE"
        const val ACTION_STOP        = "com.hifz.quran.STOP"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #4 — Audio continue après fermeture de l'app
    // BUG FIX #5 — Notification ne se ferme pas (bouton Stop ignoré)
    //
    // AVANT : onStartCommand() absent → les actions ACTION_STOP et ACTION_PLAY_PAUSE
    //         envoyées depuis les boutons de notification n'étaient jamais traitées.
    //         De plus les PendingIntent utilisaient getBroadcast() avec un Intent
    //         implicite, ignoré silencieusement sur Android 8+ (OREO).
    //
    // APRÈS : onStartCommand() intercepte les actions. stopPlayback() appelle
    //         stopForeground(STOP_FOREGROUND_REMOVE) + stopSelf() → le service
    //         s'arrête proprement et la notification disparaît.
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #6 — Boucle s'arrête après 1 lecture au lieu de N
    //
    // AVANT : le listener onPlaybackStateChanged traitait STATE_ENDED UNIQUEMENT
    //         si segmentEnd > 0, ce qui excluait la lecture d'une sourate complète
    //         sans segment défini. De plus le compteur currentLoop n'était pas
    //         remis à zéro entre deux appels à loadAudio() différents.
    //         Résultat : après 1 lecture, la boucle pensait avoir déjà atteint
    //         son quota et s'arrêtait.
    //
    // APRÈS : STATE_ENDED gère tous les cas (avec ou sans segment). currentLoop
    //         est remis à 0 dans loadAudio() et setLoop(). La logique de répétition
    //         dans le coroutine progressJob est unifiée avec celle du listener.
    // ─────────────────────────────────────────────────────────────────────────
    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    handleLoopOnEnd()
                }
                emitState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
                emitState()
                updateNotification()
            }
        })
    }

    /**
     * Logique de boucle appelée quand la lecture se termine naturellement (STATE_ENDED)
     * ou quand le segment de fin est atteint (depuis le progressJob).
     */
    private fun handleLoopOnEnd() {
        if (!loopEnabled) return

        val shouldContinue = loopCount == 0 || currentLoop < loopCount - 1
        if (shouldContinue) {
            currentLoop++
            player.seekTo(segmentStart)
            player.play()
        } else {
            // Toutes les répétitions effectuées
            currentLoop = 0
            player.seekTo(segmentStart)
            player.pause()
        }
        emitState()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #7 — Crash à la lecture après ajout bibliothèque
    //
    // AVANT : loadAudio() était appelé depuis observeViewModel() dès que
    //         currentSourate changeait, MÊME si playerService n'était pas encore
    //         connecté (isBound = false). Cela provoquait une lecture sur un
    //         ExoPlayer non préparé, ou une tentative de lire une URI invalide.
    //
    // APRÈS : loadAudio() vérifie que l'URI est non vide avant de préparer le
    //         player. Le player est stop() avant chaque nouveau setMediaItem()
    //         pour éviter les états incohérents.
    // ─────────────────────────────────────────────────────────────────────────
    fun loadAudio(uri: Uri, sourateId: Long, startMs: Long = 0L, endMs: Long = 0L) {
        if (uri.toString().isBlank()) return

        // Remettre à zéro le compteur de boucle pour le nouveau fichier
        currentLoop      = 0
        currentSourateId = sourateId
        segmentStart     = startMs
        segmentEnd       = endMs

        // Arrêter proprement l'éventuelle lecture en cours avant de charger le nouveau media
        player.stop()

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
        if (startMs > 0L && endMs == 0L) player.seekTo(startMs)
        startForeground(NOTIF_ID, buildNotification())
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stopPlayback() {
        player.stop()
        currentLoop = 0
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
        segmentEnd   = endMs
    }

    fun setLoop(enabled: Boolean, count: Int = 3) {
        loopEnabled  = enabled
        loopCount    = count
        currentLoop  = 0          // ← reset crucial pour éviter bug "s'arrête après 1x"
        player.repeatMode = Player.REPEAT_MODE_OFF
        emitState()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        emitState()
    }

    fun setVersetId(id: Long?) { currentVersetId = id }

    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration.coerceAtLeast(0L)
    fun isPlaying(): Boolean = player.isPlaying

    // ─────────────────────────────────────────────────────────────────────────
    // Coroutine qui met à jour la position toutes les 250 ms
    // et détecte le dépassement du segment de fin pour la boucle.
    // ─────────────────────────────────────────────────────────────────────────
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                emitState()

                // Détection de fin de segment (pour la boucle dans un segment)
                if (loopEnabled && segmentEnd > 0L &&
                    player.isPlaying &&
                    player.currentPosition >= segmentEnd
                ) {
                    handleLoopOnEnd()
                }

                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun emitState() {
        playerState.postValue(
            PlayerState(
                isPlaying       = player.isPlaying,
                currentPosition = player.currentPosition,
                duration        = player.duration.coerceAtLeast(0L),
                sourateId       = currentSourateId,
                versetId        = currentVersetId,
                loopEnabled     = loopEnabled,
                loopCount       = loopCount,
                loopCurrent     = currentLoop,
                speed           = player.playbackParameters.speed,
                segmentStart    = segmentStart,
                segmentEnd      = segmentEnd
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #5 — Notification ne se ferme pas
    //
    // AVANT : PendingIntent.getBroadcast() avec Intent implicite → ignoré Android 8+
    //         setOngoing(true) en dur → notification impossible à balayer même en pause
    //
    // APRÈS : PendingIntent.getService() avec Intent EXPLICITE (composant défini).
    //         setOngoing() dynamique : true seulement si en lecture, false en pause.
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildNotification(): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Intent explicite vers le service lui-même (fonctionne sur tous les Android)
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

        val isPlaying = player.isPlaying

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hifz Quran")
            .setContentText(if (isPlaying) "En lecture..." else "En pause")
            .setSmallIcon(R.drawable.ic_quran)
            .setContentIntent(mainIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (isPlaying) "Pause" else "Reprendre",
                    playPauseIntent
                ).build()
            )
            .addAction(Notification.Action.Builder(null, "Arrêter", stopIntent).build())
            .setOngoing(isPlaying)  // ← balayable en pause, bloquée en lecture
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lecture Coran",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Contrôle de lecture audio" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        player.release()
    }
}
