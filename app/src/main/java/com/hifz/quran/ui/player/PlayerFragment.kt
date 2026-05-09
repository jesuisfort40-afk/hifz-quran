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
    private var isBound = false
    private lateinit var versetAdapter: VersetAdapter

    private var isSeeking    = false
    private var isSegmentMode = false
    private var serviceObserverAttached = false

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #1 — Navigation crash (ServiceConnection)
    //
    // AVANT : onStop() appelait unbindService() → à chaque changement d'onglet
    //         playerService passait à null. Au retour, bindService() relançait
    //         une connexion asynchrone. Entre temps, tout clic sur Play crashait
    //         avec NullPointerException.
    //
    // APRÈS : onStop() ne fait RIEN (on garde la connexion vivante).
    //         unbindService() est uniquement dans onDestroyView() (destruction réelle).
    //         Effet : playerService reste valide entre les changements d'onglet.
    // ─────────────────────────────────────────────────────────────────────────
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.PlayerBinder
            playerService = binder.getService()
            isBound = true
            // Attacher l'observer d'état une seule fois
            if (!serviceObserverAttached) {
                observePlayerState()
                serviceObserverAttached = true
            }
            // Charger la sourate si déjà sélectionnée
            vm.currentSourate.value?.let { sourate ->
                // Ne pas re-déclencher loadAudio si le service joue déjà cette sourate
                val svc = playerService ?: return
                if (svc.getCurrentPosition() == 0L && !svc.isPlaying()) {
                    loadSourate(sourate)
                }
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

        // Démarrer et binder le service audio
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

        // SeekBar position globale
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

        // Segment start
        binding.seekSegmentStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    vm.setSegmentStart(p.toLong())
                    binding.tvSegmentStart.text = TimeUtils.formatMs(p.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { applySegment() }
        })

        // Segment end
        binding.seekSegmentEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    vm.setSegmentEnd(p.toLong())
                    binding.tvSegmentEnd.text = TimeUtils.formatMs(p.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { applySegment() }
        })

        // Mode segment
        binding.btnSegmentMode.setOnClickListener {
            isSegmentMode = !isSegmentMode
            binding.layoutSegment.visibility = if (isSegmentMode) View.VISIBLE else View.GONE
            binding.btnSegmentMode.isSelected = isSegmentMode
        }

        // Boucle toggle
        binding.btnLoop.setOnClickListener { vm.toggleLoop() }

        // Compteur de boucles
        binding.btnLoopMinus.setOnClickListener {
            vm.setLoopCount(maxOf(0, (vm.loopCount.value ?: 3) - 1))
        }
        binding.btnLoopPlus.setOnClickListener {
            vm.setLoopCount(minOf(20, (vm.loopCount.value ?: 3) + 1))
        }

        // Vitesse
        binding.btnSpeed.setOnClickListener { showSpeedPicker() }

        // Marquer position start/end depuis le player
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

        // Sauvegarder verset
        binding.btnSaveVerset.setOnClickListener { showSaveVersetDialog() }

        // Ajouter verset → ouvrir mode segment
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
                // Charger seulement si le service est prêt
                if (isBound) loadSourate(sourate)
            } else {
                binding.tvSourateName.text   = "Aucune sourate sélectionnée"
                binding.tvSourateArabic.text = "Ajoutez une sourate depuis la bibliothèque"
            }
        }

        vm.versets.observe(viewLifecycleOwner) { list ->
            versetAdapter.submitList(list)
            binding.tvNoVersets.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // ─────────────────────────────────────────────────────────────────────
        // BUG FIX #6 — Boucle s'arrête après 1x
        //
        // AVANT : loopEnabled/loopCount changeaient dans le ViewModel mais
        //         setLoop() sur le service n'était appelé qu'une fois à l'observer.
        //         Si l'utilisateur changeait loopCount APRÈS avoir lancé la lecture,
        //         le service gardait l'ancien compte → s'arrêtait trop tôt.
        //
        // APRÈS : à chaque changement de loopEnabled ou loopCount, setLoop()
        //         est re-appelé sur le service avec les valeurs actuelles.
        // ─────────────────────────────────────────────────────────────────────
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
            if (_binding == null) return@observe
            if (isSeeking) return@observe

            val dur = state.duration
            if (dur > 0L) {
                binding.seekBar.max = dur.toInt()
                if (isSegmentMode) {
                    binding.seekSegmentStart.max = dur.toInt()
                    binding.seekSegmentEnd.max   = dur.toInt()
                }
            }
            binding.seekBar.progress = state.currentPosition.toInt()
            binding.tvCurrentTime.text = TimeUtils.formatMs(state.currentPosition)
            binding.tvDuration.text    = TimeUtils.formatMs(dur)

            binding.btnPlayPause.setImageResource(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.tvSpeed.text = "${"%.2f".format(state.speed)}x"

            if (state.loopEnabled) {
                val max = if (state.loopCount == 0) "∞" else "${state.loopCount}"
                binding.tvLoopProgress.text = "${state.loopCurrent + 1}/$max"
                binding.tvLoopProgress.visibility = View.VISIBLE
            } else {
                binding.tvLoopProgress.visibility = View.GONE
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #7 — Crash lecture après import
    // On vérifie que le filePath est non vide avant de passer à loadAudio
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadSourate(sourate: Sourate) {
        if (sourate.filePath.isBlank()) return
        playerService?.loadAudio(Uri.parse(sourate.filePath), sourate.id)
    }

    private fun playVerset(verset: Verset) {
        val sourate = vm.currentSourate.value ?: return
        if (sourate.filePath.isBlank()) return
        playerService?.apply {
            loadAudio(
                uri       = Uri.parse(sourate.filePath),
                sourateId = sourate.id,
                startMs   = verset.startMs,
                endMs     = verset.endMs
            )
            setVersetId(verset.id)
            setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
            play()
        }
        vm.incrementRepeat(verset.id)
    }

    private fun applySegment() {
        val start = vm.segmentStart.value ?: 0L
        val end   = vm.segmentEnd.value   ?: 0L
        playerService?.setSegment(start, end)
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #1 — unbindService déplacé de onStop() vers onDestroyView()
    // onStop() est retiré complètement : la connexion est conservée entre onglets.
    // ─────────────────────────────────────────────────────────────────────────
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
