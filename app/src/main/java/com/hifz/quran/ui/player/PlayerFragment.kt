package com.hifz.quran.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentPlayerBinding
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus
import com.hifz.quran.service.AudioPlayerService
import com.hifz.quran.util.TimeUtils

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: PlayerViewModel
    private var playerService: AudioPlayerService? = null
    private var isBound                 = false
    private var serviceObserverAttached = false
    private lateinit var versetAdapter: VersetAdapter

    private var isSeeking     = false
    private var isSegmentMode = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.PlayerBinder
            playerService = binder.getService()
            isBound = true

            if (!serviceObserverAttached) {
                observePlayerState()
                serviceObserverAttached = true
            }

            // Charger la sourate si déjà disponible ET si le service ne joue pas déjà
            val sourate = vm.currentSourate.value ?: return
            val svc = playerService ?: return
            val alreadyPlaying = svc.isPlaying() &&
                    svc.isStreamingMode() == sourate.isFromLibrary
            if (!alreadyPlaying) {
                loadSourateIntoService(sourate, vm.versets.value ?: emptyList())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            playerService = null
            serviceObserverAttached = false
        }
    }

    companion object {
        private const val ARG_SOURATE_ID = "sourate_id"
        fun newInstance(sourateId: Long) = PlayerFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SOURATE_ID, sourateId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        val sourateId = arguments?.getLong(ARG_SOURATE_ID, -1L) ?: -1L
        if (sourateId != -1L) vm.loadSourate(sourateId)

        setupVersetList()
        setupControls()
        observeViewModel()

        val intent = Intent(requireContext(), AudioPlayerService::class.java)
        requireContext().startForegroundService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupVersetList() {
        versetAdapter = VersetAdapter(
            onPlay         = { verset -> playVerset(verset) },
            onStatusChange = { verset, status -> vm.updateStatus(verset.id, status) },
            onDelete       = { verset -> confirmDeleteVerset(verset) }
        )
        binding.rvVersets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVersets.adapter = versetAdapter
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { playerService?.togglePlayPause() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = TimeUtils.formatMs(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeeking = false
                playerService?.seekTo(sb.progress.toLong())
            }
        })

        binding.seekSegmentStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { vm.setSegmentStart(p.toLong()); binding.tvSegmentStart.text = TimeUtils.formatMs(p.toLong()) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { applySegment() }
        })

        binding.seekSegmentEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { vm.setSegmentEnd(p.toLong()); binding.tvSegmentEnd.text = TimeUtils.formatMs(p.toLong()) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { applySegment() }
        })

        binding.btnSegmentMode.setOnClickListener {
            isSegmentMode = !isSegmentMode
            binding.layoutSegment.visibility = if (isSegmentMode) View.VISIBLE else View.GONE
            binding.btnSegmentMode.isSelected = isSegmentMode
        }

        binding.btnLoop.setOnClickListener { vm.toggleLoop() }
        binding.btnLoopMinus.setOnClickListener { vm.setLoopCount(maxOf(0, (vm.loopCount.value ?: 3) - 1)) }
        binding.btnLoopPlus.setOnClickListener  { vm.setLoopCount(minOf(20, (vm.loopCount.value ?: 3) + 1)) }
        binding.btnSpeed.setOnClickListener { showSpeedPicker() }

        binding.btnMarkStart.setOnClickListener {
            val pos = playerService?.getCurrentPosition() ?: 0L
            vm.setSegmentStart(pos)
            binding.seekSegmentStart.progress = pos.toInt()
            binding.tvSegmentStart.text = TimeUtils.formatMs(pos)
            applySegment()
        }
        binding.btnMarkEnd.setOnClickListener {
            val pos = playerService?.getCurrentPosition() ?: 0L
            vm.setSegmentEnd(pos)
            binding.seekSegmentEnd.progress = pos.toInt()
            binding.tvSegmentEnd.text = TimeUtils.formatMs(pos)
            applySegment()
        }

        binding.btnSaveVerset.setOnClickListener { showSaveVersetDialog() }
        binding.btnAddVerset.setOnClickListener {
            isSegmentMode = true
            binding.layoutSegment.visibility = View.VISIBLE
            binding.btnSegmentMode.isSelected = true
        }
    }

    private fun observeViewModel() {
        vm.currentSourate.observe(viewLifecycleOwner) { sourate ->
            if (sourate != null) {
                binding.tvSourateName.text   = sourate.name
                binding.tvSourateArabic.text = sourate.arabicName.ifEmpty {
                    "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"
                }
                // Masquer le seekbar en mode streaming (la durée par verset varie)
                // et afficher un indicateur "Verset X / Y" à la place
                if (sourate.isFromLibrary) {
                    binding.seekBar.visibility = View.INVISIBLE
                    binding.tvStreamingHint.visibility = View.VISIBLE
                } else {
                    binding.seekBar.visibility = View.VISIBLE
                    binding.tvStreamingHint.visibility = View.GONE
                }
            } else {
                binding.tvSourateName.text   = "Aucune sourate sélectionnée"
                binding.tvSourateArabic.text = "Ajoutez une sourate depuis la bibliothèque"
            }
        }

        vm.versets.observe(viewLifecycleOwner) { list ->
            versetAdapter.submitList(list)
            binding.tvNoVersets.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

            // BUG FIX streaming ne démarre pas :
            // On lance loadStreaming() ici, quand les versets sont disponibles.
            // C'est le bon endroit car : sourate chargée + versets en base + service bindé.
            val sourate = vm.currentSourate.value ?: return@observe
            if (sourate.isFromLibrary && list.isNotEmpty() && isBound) {
                val svc = playerService ?: return@observe
                // Ne relancer que si pas déjà en train de streamer cette sourate
                if (!svc.isStreamingReady() || !svc.isPlaying()) {
                    loadSourateIntoService(sourate, list)
                }
            }
        }

        vm.loopEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.btnLoop.isSelected = enabled
            playerService?.setLoop(enabled, vm.loopCount.value ?: 3)
        }

        vm.loopCount.observe(viewLifecycleOwner) { count ->
            binding.tvLoopCount.text = if (count == 0) "∞" else "$count"
            playerService?.setLoop(vm.loopEnabled.value ?: false, count)
        }
    }

    private fun observePlayerState() {
        playerService?.playerState?.observe(viewLifecycleOwner) { state ->
            if (_binding == null || isSeeking) return@observe

            val dur = state.duration

            // BUG FIX 0:00 / 0:00 en mode streaming :
            // En streaming, la durée est connue seulement après buffering.
            // On met à jour le seekbar uniquement si la durée est réelle (> 0).
            if (dur > 0L) {
                binding.seekBar.max = dur.toInt()
                if (isSegmentMode) {
                    binding.seekSegmentStart.max = dur.toInt()
                    binding.seekSegmentEnd.max   = dur.toInt()
                }
                binding.tvDuration.text = TimeUtils.formatMs(dur)
            } else {
                // Durée inconnue → afficher "..." pour indiquer le chargement
                binding.tvDuration.text = if (state.isPlaying || state.currentPosition > 0)
                    "…" else "0:00"
            }

            binding.seekBar.progress   = state.currentPosition.toInt()
            binding.tvCurrentTime.text = TimeUtils.formatMs(state.currentPosition)

            binding.btnPlayPause.setImageResource(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.tvSpeed.text = "${"%.2f".format(state.speed)}x"

            // Surbrillance du verset actif dans la liste
            state.versetId?.let { versetAdapter.setActiveVerset(it) }

            // Indicateur boucle
            if (state.loopEnabled) {
                val max = if (state.loopCount == 0) "∞" else "${state.loopCount}"
                binding.tvLoopProgress.text = "${state.loopCurrent + 1}/$max"
                binding.tvLoopProgress.visibility = View.VISIBLE
            } else {
                binding.tvLoopProgress.visibility = View.GONE
            }

            // Indicateur verset en cours (mode streaming)
            val svc = playerService
            if (svc != null && svc.isStreamingMode()) {
                val idx   = svc.getCurrentStreamingVersetIndex()
                val total = vm.versets.value?.size ?: 0
                binding.tvStreamingHint.text = "Verset ${idx + 1} / $total"
                binding.tvStreamingHint.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Charge la sourate dans le service audio.
     * Mode streaming si isFromLibrary, fichier local sinon.
     */
    private fun loadSourateIntoService(sourate: Sourate, versets: List<Verset>) {
        val svc = playerService ?: return
        if (sourate.isFromLibrary) {
            if (versets.isEmpty()) return
            svc.loadStreaming(
                versets        = versets,
                sourateId      = sourate.id,
                sourateNumber  = sourate.sourateNumber,
                reciterId      = sourate.reciterId
            )
        } else {
            if (sourate.filePath.isBlank()) return
            svc.loadAudio(Uri.parse(sourate.filePath), sourate.id)
        }
    }

    private fun playVerset(verset: Verset) {
        val sourate = vm.currentSourate.value ?: return
        val svc     = playerService           ?: return

        if (sourate.isFromLibrary) {
            // Mode streaming : chercher l'index du verset et sauter dessus
            val versets = vm.versets.value ?: return
            val index = versets.indexOfFirst { it.id == verset.id }
            if (index >= 0) {
                svc.seekToVerset(index)
                svc.setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
            }
        } else {
            // Mode fichier local
            if (sourate.filePath.isBlank()) return
            svc.loadAudio(
                uri       = Uri.parse(sourate.filePath),
                sourateId = sourate.id,
                startMs   = verset.startMs,
                endMs     = verset.endMs
            )
            svc.setVersetId(verset.id)
            svc.setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
            svc.play()
        }
        vm.incrementRepeat(verset.id)
    }

    private fun applySegment() {
        playerService?.setSegment(vm.segmentStart.value ?: 0L, vm.segmentEnd.value ?: 0L)
    }

    private fun showSpeedPicker() {
        val labels = arrayOf("0.5×", "0.75×", "1.0×", "1.25×", "1.5×", "2.0×")
        val values = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vitesse de lecture")
            .setItems(labels) { _, i -> playerService?.setSpeed(values[i]) }
            .show()
    }

    private fun showSaveVersetDialog() {
        val start = vm.segmentStart.value ?: 0L
        val end   = vm.segmentEnd.value   ?: 0L
        if (end <= start) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Attention")
                .setMessage("Le point de fin doit être après le point de début.")
                .setPositiveButton("OK", null).show()
            return
        }
        val num = (vm.versets.value?.size ?: 0) + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sauvegarder comme verset")
            .setMessage("Verset $num\nDe ${TimeUtils.formatMs(start)} à ${TimeUtils.formatMs(end)}")
            .setPositiveButton("Sauvegarder") { _, _ -> vm.saveVerset(start, end) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDeleteVerset(verset: Verset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer le verset ${verset.numero} ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.deleteVerset(verset) }
            .setNegativeButton("Annuler", null).show()
    }

    override fun onDestroyView() {
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
            playerService = null
            serviceObserverAttached = false
        }
        super.onDestroyView()
        _binding = null
    }
}
