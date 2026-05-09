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

    private var currentSourateId: Long = -1L
    private var isSeeking = false
    private var isSegmentMode = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.PlayerBinder
            playerService = binder.getService()
            isBound = true
            observePlayerState()
            vm.currentSourate.value?.let { loadSourate(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            playerService = null
        }
    }

    companion object {
        private const val ARG_SOURATE_ID = "sourate_id"
        fun newInstance(sourateId: Long): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply { putLong(ARG_SOURATE_ID, sourateId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        currentSourateId = arguments?.getLong(ARG_SOURATE_ID, -1L) ?: -1L
        if (currentSourateId != -1L) vm.loadSourate(currentSourateId)

        setupVersetList()
        setupControls()
        observeViewModel()

        val intent = Intent(requireContext(), AudioPlayerService::class.java)
        requireContext().startForegroundService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupVersetList() {
        versetAdapter = VersetAdapter(
            onPlay = { verset -> playVerset(verset) },
            onStatusChange = { verset, status -> vm.updateStatus(verset.id, status) },
            onDelete = { verset -> confirmDeleteVerset(verset) }
        )
        binding.rvVersets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVersets.adapter = versetAdapter
    }

    private fun setupControls() {
        // Play / Pause
        binding.btnPlayPause.setOnClickListener {
            playerService?.togglePlayPause()
        }

        // SeekBar for global position
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

        // Segment start/end sliders
        binding.seekSegmentStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    vm.setSegmentStart(p.toLong())
                    binding.tvSegmentStart.text = TimeUtils.formatMs(p.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                applySegment()
            }
        })

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

        // Toggle segment mode
        binding.btnSegmentMode.setOnClickListener {
            isSegmentMode = !isSegmentMode
            binding.layoutSegment.visibility = if (isSegmentMode) View.VISIBLE else View.GONE
            binding.btnSegmentMode.isSelected = isSegmentMode
        }

        // Loop toggle
        binding.btnLoop.setOnClickListener {
            vm.toggleLoop()
        }

        // Loop count
        binding.btnLoopMinus.setOnClickListener {
            val c = (vm.loopCount.value ?: 3) - 1
            vm.setLoopCount(maxOf(0, c))
        }
        binding.btnLoopPlus.setOnClickListener {
            val c = (vm.loopCount.value ?: 3) + 1
            vm.setLoopCount(minOf(20, c))
        }

        // Speed
        binding.btnSpeed.setOnClickListener { showSpeedPicker() }

        // Mark current position as segment start
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

        // Save current segment as verset
        binding.btnSaveVerset.setOnClickListener { showSaveVersetDialog() }

        // Add verset button
        binding.btnAddVerset.setOnClickListener {
            isSegmentMode = true
            binding.layoutSegment.visibility = View.VISIBLE
            binding.btnSegmentMode.isSelected = true
        }
    }

    private fun observeViewModel() {
        vm.currentSourate.observe(viewLifecycleOwner) { sourate ->
            sourate?.let {
                binding.tvSourateName.text = it.name
                binding.tvSourateArabic.text = it.arabicName.ifEmpty { "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ" }
                if (isBound) loadSourate(it)
            } ?: run {
                binding.tvSourateName.text = "Aucune sourate sélectionnée"
                binding.tvSourateArabic.text = "Ajoutez une sourate depuis la bibliothèque"
            }
        }

        vm.versets.observe(viewLifecycleOwner) { list ->
            versetAdapter.submitList(list)
            binding.tvNoVersets.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
            if (isSeeking) return@observe
            val dur = state.duration
            binding.seekBar.max = dur.toInt()
            binding.seekBar.progress = state.currentPosition.toInt()
            binding.tvCurrentTime.text = TimeUtils.formatMs(state.currentPosition)
            binding.tvDuration.text = TimeUtils.formatMs(dur)

            if (isSegmentMode && dur > 0) {
                binding.seekSegmentStart.max = dur.toInt()
                binding.seekSegmentEnd.max = dur.toInt()
            }

            binding.btnPlayPause.setImageResource(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.tvSpeed.text = "${state.speed}x"

            if (state.loopEnabled) {
                binding.tvLoopProgress.text = "${state.loopCurrent + 1}/${if (state.loopCount == 0) "∞" else state.loopCount}"
                binding.tvLoopProgress.visibility = View.VISIBLE
            } else {
                binding.tvLoopProgress.visibility = View.GONE
            }
        }
    }

    private fun loadSourate(sourate: Sourate) {
        playerService?.loadAudio(
            Uri.parse(sourate.filePath),
            sourate.id
        )
    }

    private fun playVerset(verset: Verset) {
        val sourate = vm.currentSourate.value ?: return
        playerService?.apply {
            loadAudio(Uri.parse(sourate.filePath), sourate.id, verset.startMs, verset.endMs)
            setVersetId(verset.id)
            setLoop(vm.loopEnabled.value ?: false, vm.loopCount.value ?: 3)
            play()
        }
        vm.incrementRepeat(verset.id)
    }

    private fun applySegment() {
        val start = vm.segmentStart.value ?: 0L
        val end = vm.segmentEnd.value ?: 0L
        playerService?.setSegment(start, end)
    }

    private fun showSpeedPicker() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val values = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vitesse de lecture")
            .setItems(speeds) { _, i ->
                playerService?.setSpeed(values[i])
            }.show()
    }

    private fun showSaveVersetDialog() {
        val start = vm.segmentStart.value ?: 0L
        val end = vm.segmentEnd.value ?: 0L
        if (end <= start) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Attention")
                .setMessage("La fin doit être après le début")
                .setPositiveButton("OK", null).show()
            return
        }
        val count = (vm.versets.value?.size ?: 0) + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sauvegarder comme verset")
            .setMessage("Verset $count\nDe ${TimeUtils.formatMs(start)} à ${TimeUtils.formatMs(end)}")
            .setPositiveButton("Sauvegarder") { _, _ ->
                vm.saveVerset(start, end)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDeleteVerset(verset: Verset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer le verset ${verset.numero} ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.deleteVerset(verset) }
            .setNegativeButton("Annuler", null).show()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
