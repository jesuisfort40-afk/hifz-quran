package com.hifz.quran.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.hifz.quran.databinding.FragmentStatsBinding

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: StatsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        // BUG FIX #3 — STATISTIQUES :
        // Avant : les observers écrivaient des chaînes brutes ("0min aujourd'hui")
        //         dans des TextView, ce qui affichait souvent "null" ou plantait
        //         si la valeur LiveData était null au premier emit.
        // Après : chaque valeur est formatée proprement avec un fallback "0".
        //         Les textes label/valeur sont séparés (label fixe dans le XML,
        //         valeur dynamique ici) → plus robuste et lisible.

        vm.todayMinutes.observe(viewLifecycleOwner) { minutes ->
            val v = minutes ?: 0L
            binding.tvTodayTime.text = formatDuration(v)
        }

        vm.weekSessions.observe(viewLifecycleOwner) { count ->
            binding.tvWeekSessions.text = "${count ?: 0}"
        }

        vm.monthMinutes.observe(viewLifecycleOwner) { minutes ->
            val v = minutes ?: 0L
            binding.tvMonthTime.text = formatDuration(v)
        }

        vm.totalListeningHours.observe(viewLifecycleOwner) { ms ->
            val v = ms ?: 0L
            binding.tvTotalHours.text = formatDurationMs(v)
        }

        vm.totalMastered.observe(viewLifecycleOwner) { count ->
            binding.tvMastered.text = "${count ?: 0}"
        }

        vm.totalInProgress.observe(viewLifecycleOwner) { count ->
            binding.tvInProgress.text = "${count ?: 0}"
        }

        vm.totalPending.observe(viewLifecycleOwner) { count ->
            binding.tvPending.text = "${count ?: 0}"
        }

        vm.streakDays.observe(viewLifecycleOwner) { days ->
            val v = days ?: 0
            binding.tvStreak.text = "$v jour${if (v > 1) "s" else ""}"
        }

        // Barre de progression des versets
        vm.totalMastered.observe(viewLifecycleOwner)  { updateProgressBar() }
        vm.totalInProgress.observe(viewLifecycleOwner) { updateProgressBar() }
        vm.totalPending.observe(viewLifecycleOwner)    { updateProgressBar() }

        binding.btnRefresh.setOnClickListener { vm.refreshStats() }
    }

    private fun updateProgressBar() {
        val mastered   = vm.totalMastered.value   ?: 0
        val inProgress = vm.totalInProgress.value ?: 0
        val pending    = vm.totalPending.value     ?: 0
        val total      = mastered + inProgress + pending

        if (total > 0) {
            val pct = (mastered * 100) / total
            binding.progressMastered.progress = pct
            binding.tvProgressPct.text = "$pct%"
            binding.tvProgressPct.visibility = View.VISIBLE
        } else {
            binding.progressMastered.progress = 0
            binding.tvProgressPct.visibility = View.GONE
        }
    }

    // Formate des minutes en "Xh Ymin" ou "Ymin"
    private fun formatDuration(minutes: Long): String = when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}min"
        else          -> "${minutes}min"
    }

    // Formate des millisecondes en "Xh Ymin"
    private fun formatDurationMs(ms: Long): String {
        val totalMinutes = ms / 60000
        return formatDuration(totalMinutes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
