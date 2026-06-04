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
import android.widget.CompoundButton
import android.widget.NumberPicker
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

    // Le flag de chargement est maintenant dans le ViewModel (survit à la recréation du fragment)
    // On garde un flag local uniquement pour la connexion courante au service
    private var serviceJustConnected = false

    // FIX #10 — tracker le verset et les répétitions pour endSession()
    private var lastActiveVersetId: Long? = null
    private var sessionRepeatsDone: Int   = 0

    private var isPlayerReady = false
        set(value) {
            field = value
            if (_binding == null) return
            binding.layoutPlayerReady.visibility   = if (value) View.VISIBLE else View.GONE
            binding.layoutPlayerLoading.visibility = if (value) View.GONE  else View.VISIBLE
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.PlayerBinder
            playerService = binder.getService()
            isBound = true

            playerService?.onVersetPlayed = { versetId ->
                lastActiveVersetId = versetId
                sessionRepeatsDone++
                vm.incrementRepeat(versetId)
            }

            if (!serviceObserverAttached) {
                observePlayerState()
                serviceObserverAttached = true
            }

            serviceJustConnected = true

            // Si la sourate est déjà chargée dans le service (retour de navigation) → juste UI
            val sourate = vm.currentSourate.value ?: return
            if (!sourate.isFromLibrary) {
                isPlayerReady = sourate.filePath.isNotBlank()
            } else if (vm.isSourateLoadedInService) {
                // Service déjà prêt → on affiche directement le lecteur sans recharger
                isPlayerReady = true
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            playerService = null
            serviceObserverAttached = false
            serviceJustConnected = false
            vm.resetServiceLoadState()
            isPlayerReady = false
        }
    }

    companion object {
        private const val ARG_SOURATE_ID = "sourate_id"
        fun newInstance(sourateId: Long) = PlayerFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SOURATE_ID, sourateId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        isPlayerReady = false
        serviceJustConnected = false

        val sourateId = arguments?.getLong(ARG_SOURATE_ID, -1L) ?: -1L
        if (sourateId != -1L) vm.loadSourate(sourateId)

        setupVersetList()
        setupControls()
        observeViewModel()

        // CRASH FIX — startForegroundService() seul peut provoquer un ANR/crash si le service
        // ne fait pas startForeground() dans les 5 secondes (avant qu'une sourate soit chargée).
        // On utilise uniquement bindService avec BIND_AUTO_CREATE : Android démarre le service
        // automatiquement si besoin, et startForeground() est appelé dans loadAudio/loadStreaming
        // seulement quand il y a vraiment quelque chose à jouer.
        val intent = Intent(requireContext(), AudioPlayerService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        vm.startSession()
    }

    private fun setupVersetList() {
        versetAdapter = VersetAdapter(
            onPlay         = { verset -> playVerset(verset) },
            onStatusChange = { verset, status -> vm.updateStatus(verset.id, status) }
        )
        binding.rvVersets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVersets.adapter = versetAdapter
        binding.rvVersets.isNestedScrollingEnabled = false
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { playerService?.togglePlayPause() }

        binding.btnPrevVerset.setOnClickListener {
            val svc = playerService ?: return@setOnClickListener
            if (svc.isStreamingMode()) svc.seekToVerset(svc.getCurrentStreamingVersetIndex() - 1)
        }
        binding.btnNextVerset.setOnClickListener {
            val svc = playerService ?: return@setOnClickListener
            if (svc.isStreamingMode()) svc.seekToVerset(svc.getCurrentStreamingVersetIndex() + 1)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = TimeUtils.formatMs(p.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) { isSeeking = false; playerService?.seekTo(sb.progress.toLong()) }
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

        // FIX #7 — Le listener du Switch est attaché séparément via setupLoopSwitch()
        // pour éviter la boucle infinie quand on met isChecked programmatiquement
        setupLoopSwitch()

        binding.btnLoopMinus.setOnClickListener { vm.setLoopCount(maxOf(0, (vm.loopCount.value ?: 3) - 1)) }
        binding.btnLoopPlus.setOnClickListener  { vm.setLoopCount(minOf(99, (vm.loopCount.value ?: 3) + 1)) }
        binding.btnSpeed.setOnClickListener     { showSpeedPicker() }

        binding.btnMarkStart.setOnClickListener {
            val pos = playerService?.getCurrentPosition() ?: 0L
            vm.setSegmentStart(pos); binding.seekSegmentStart.progress = pos.toInt()
            binding.tvSegmentStart.text = TimeUtils.formatMs(pos); applySegment()
        }
        binding.btnMarkEnd.setOnClickListener {
            val pos = playerService?.getCurrentPosition() ?: 0L
            vm.setSegmentEnd(pos); binding.seekSegmentEnd.progress = pos.toInt()
            binding.tvSegmentEnd.text = TimeUtils.formatMs(pos); applySegment()
        }

        binding.btnSelectRange.setOnClickListener { showRangePicker() }
        binding.btnClearRange.setOnClickListener  {
            playerService?.clearVersetRange()
            binding.tvRangeInfo.visibility   = View.GONE
            binding.btnClearRange.visibility = View.GONE
        }

        binding.btnSaveVerset.setOnClickListener { showSaveVersetDialog() }
        binding.btnAddVerset.setOnClickListener  {
            isSegmentMode = true
            binding.layoutSegment.visibility = View.VISIBLE
            binding.btnSegmentMode.isSelected = true
        }
    }

    // FIX #7 — Listener du Switch isolé pour éviter la boucle infinie
    // On retire le listener avant de modifier isChecked, puis on le remet
    private var loopSwitchListener: CompoundButton.OnCheckedChangeListener? = null

    private fun setupLoopSwitch() {
        loopSwitchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
            vm.setLoopEnabled(checked)
        }
        binding.switchLoop.setOnCheckedChangeListener(loopSwitchListener)
    }

    private fun observeViewModel() {
        vm.currentSourate.observe(viewLifecycleOwner) { sourate ->
            if (sourate != null) {
                binding.tvSourateName.text   = sourate.name
                binding.tvSourateArabic.text = sourate.arabicName.ifEmpty { "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ" }
                if (sourate.isFromLibrary) {
                    binding.seekBar.visibility        = View.INVISIBLE
                    binding.btnPrevVerset.visibility  = View.VISIBLE
                    binding.btnNextVerset.visibility  = View.VISIBLE
                    binding.cardSegment.visibility    = View.GONE
                    binding.btnSelectRange.visibility = View.VISIBLE
                } else {
                    binding.seekBar.visibility        = View.VISIBLE
                    binding.btnPrevVerset.visibility  = View.GONE
                    binding.btnNextVerset.visibility  = View.GONE
                    binding.cardSegment.visibility    = View.VISIBLE
                    binding.btnSelectRange.visibility = View.GONE
                }
                // Changement de sourate → reset du flag dans le ViewModel
                vm.resetServiceLoadState()
            } else {
                binding.tvSourateName.text   = "Aucune sourate sélectionnée"
                binding.tvSourateArabic.text = "Ajoutez une sourate depuis la bibliothèque"
            }
        }

        vm.versets.observe(viewLifecycleOwner) { list ->
            versetAdapter.submitList(list.toList())
            binding.tvNoVersets.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

            val sourate = vm.currentSourate.value ?: return@observe

            if (sourate.isFromLibrary) {
                if (list.isEmpty()) {
                    isPlayerReady = false
                    return@observe
                }
                if (!isBound) return@observe
                val svc = playerService ?: return@observe

                when {
                    // Sourate déjà chargée dans le service → rien à faire, juste afficher
                    vm.isSourateLoadedInService && svc.isStreamingReady() -> {
                        isPlayerReady = true
                    }
                    // Service perdu ou sourate pas encore chargée → charger
                    else -> {
                        loadSourateIntoService(sourate, list)
                        vm.markSourateLoadedInService()
                        isPlayerReady = true
                    }
                }

            } else {
                // FIX #4 — Mode fichier : vérifier que le chemin existe
                isPlayerReady = isBound && sourate.filePath.isNotBlank()
            }
        }

        // FIX #7 — Mise à jour du Switch sans déclencher le listener
        vm.loopEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.switchLoop.setOnCheckedChangeListener(null)
            binding.switchLoop.isChecked = enabled
            binding.switchLoop.setOnCheckedChangeListener(loopSwitchListener)
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
            if (dur > 0L) {
                binding.seekBar.max = dur.toInt()
                if (isSegmentMode) {
                    binding.seekSegmentStart.max = dur.toInt()
                    binding.seekSegmentEnd.max   = dur.toInt()
                }
                binding.tvDuration.text = TimeUtils.formatMs(dur)
            } else {
                binding.tvDuration.text = if (state.isPlaying) "…" else "0:00"
            }

            binding.seekBar.progress   = state.currentPosition.toInt()
            binding.tvCurrentTime.text = TimeUtils.formatMs(state.currentPosition)
            binding.btnPlayPause.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            binding.tvSpeed.text = "${"%.2f".format(state.speed)}x"

            state.versetId?.let { id ->
                lastActiveVersetId = id
                versetAdapter.setActiveVerset(id)
            }

            if (state.loopEnabled) {
                val max = if (state.loopCount == 0) "∞" else "${state.loopCount}"
                binding.tvLoopProgress.text = "${state.loopCurrent + 1}/$max"
                binding.tvLoopProgress.visibility = View.VISIBLE
            } else {
                binding.tvLoopProgress.visibility = View.GONE
            }

            val svc = playerService
            if (svc != null && svc.isStreamingMode()) {
                val idx     = svc.getCurrentStreamingVersetIndex()
                val versets = vm.versets.value ?: emptyList()
                val verset  = versets.getOrNull(idx)
                binding.tvVersetNumBadge.text     = "Verset ${idx + 1} / ${versets.size}"
                binding.cardArabicText.visibility = View.VISIBLE
                if (verset?.arabicText?.isNotEmpty() == true) {
                    binding.tvVersetArabic.text       = verset.arabicText
                    binding.tvVersetArabic.visibility = View.VISIBLE
                } else binding.tvVersetArabic.visibility = View.GONE
                if (verset?.transliteration?.isNotEmpty() == true) {
                    binding.tvVersetTranslit.text      = verset.transliteration
                    binding.tvVersetTranslit.visibility = View.VISIBLE
                } else binding.tvVersetTranslit.visibility = View.GONE

                if (state.rangeStart >= 0) {
                    val loopInfo = when {
                        state.rangeLoopCount == 0 -> " · ∞"
                        state.rangeLoopCount > 1  -> " · ${state.rangeCurrentLoop + 1}/${state.rangeLoopCount}"
                        else                       -> ""
                    }
                    binding.tvRangeInfo.text     = "Plage: V.${state.rangeStart + 1} → V.${state.rangeEnd + 1}$loopInfo"
                    binding.tvRangeInfo.visibility   = View.VISIBLE
                    binding.btnClearRange.visibility = View.VISIBLE
                }
            } else {
                binding.cardArabicText.visibility = View.GONE
            }
        }
    }

    private fun loadSourateIntoService(sourate: Sourate, versets: List<Verset>) {
        val svc = playerService ?: return
        if (sourate.isFromLibrary) {
            if (versets.isEmpty()) return
            svc.loadStreaming(
                versets       = versets,
                sourateId     = sourate.id,
                sourateNumber = sourate.sourateNumber,
                reciterId     = sourate.reciterId
            )
            isPlayerReady = true
        } else {
            if (sourate.filePath.isBlank()) return
            svc.loadAudio(Uri.parse(sourate.filePath), sourate.id)
            isPlayerReady = true
        }
    }

    private fun playVerset(verset: Verset) {
        val sourate = vm.currentSourate.value ?: return
        val svc     = playerService           ?: return

        if (sourate.isFromLibrary) {
            val versets = vm.versets.value ?: return
            val index   = versets.indexOfFirst { it.id == verset.id }
            if (index >= 0) {
                svc.clearVersetRange()
                svc.seekToVerset(index)
                svc.setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
                binding.tvRangeInfo.visibility   = View.GONE
                binding.btnClearRange.visibility = View.GONE
            }
        } else {
            if (sourate.filePath.isBlank()) return
            svc.loadAudio(Uri.parse(sourate.filePath), sourate.id, verset.startMs, verset.endMs)
            svc.setVersetId(verset.id)
            svc.setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
            svc.play()
            vm.incrementRepeat(verset.id)
        }
    }

    private fun showRangePicker() {
        val versets = vm.versets.value ?: return
        if (versets.size < 2) return

        val labels = versets.map { "V.${it.numero}" }.toTypedArray()

        val mainLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val rangeRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
        }
        val npStart = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = versets.size - 1; displayedValues = labels; value = 0
        }
        val npEnd = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = versets.size - 1; displayedValues = labels; value = versets.size - 1
        }
        val tvArrow = android.widget.TextView(requireContext()).apply {
            text = "  →  "; textSize = 16f
            setTextColor(resources.getColor(R.color.text, null))
        }
        rangeRow.addView(npStart)
        rangeRow.addView(tvArrow)
        rangeRow.addView(npEnd)

        val tvRepeatLabel = android.widget.TextView(requireContext()).apply {
            text      = "Répétitions de la plage"
            textSize  = 13f
            setTextColor(resources.getColor(R.color.muted, null))
            setPadding(0, 24, 0, 4)
        }

        val repeatRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
        }
        val repeatLabels = (1..10).map { "${it}×" }.toMutableList().apply { add("∞") }.toTypedArray()
        val npRepeat = NumberPicker(requireContext()).apply {
            minValue        = 0
            maxValue        = repeatLabels.size - 1
            displayedValues = repeatLabels
            value           = 0
        }
        repeatRow.addView(npRepeat)

        mainLayout.addView(rangeRow)
        mainLayout.addView(tvRepeatLabel)
        mainLayout.addView(repeatRow)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sélectionner une plage de versets")
            .setMessage("La plage sera répétée le nombre de fois choisi")
            .setView(mainLayout)
            .setPositiveButton("Appliquer") { _, _ ->
                val start      = npStart.value
                val end        = maxOf(npEnd.value, start)
                val loopCount  = if (npRepeat.value == repeatLabels.size - 1) 0 else npRepeat.value + 1
                playerService?.setVersetRange(start, end, loopCount)

                val loopLabel = if (loopCount == 0) "∞" else "${loopCount}×"
                binding.tvRangeInfo.text     = "Plage: V.${start + 1} → V.${end + 1} · $loopLabel"
                binding.tvRangeInfo.visibility   = View.VISIBLE
                binding.btnClearRange.visibility = View.VISIBLE
            }
            .setNegativeButton("Annuler", null)
            .show()
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

    override fun onDestroyView() {
        // FIX #10 — Sauvegarder les vraies stats de session (versetId + répétitions réelles)
        val sourate = vm.currentSourate.value
        if (sourate != null) {
            vm.endSession(sourate.id, lastActiveVersetId, sessionRepeatsDone)
        }
        if (isBound) {
            playerService?.onVersetPlayed = null
            requireContext().unbindService(serviceConnection)
            isBound = false
            playerService = null
            serviceObserverAttached = false
            serviceJustConnected = false
        }
        super.onDestroyView()
        _binding = null
    }
}
