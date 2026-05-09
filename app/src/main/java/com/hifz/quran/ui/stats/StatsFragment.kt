package com.hifz.quran.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.hifz.quran.databinding.FragmentStatsBinding
import com.hifz.quran.util.TimeUtils

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: StatsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        vm.todayMinutes.observe(viewLifecycleOwner) { binding.tvTodayTime.text = "${it}min aujourd'hui" }
        vm.weekSessions.observe(viewLifecycleOwner) { binding.tvWeekSessions.text = "$it sessions cette semaine" }
        vm.monthMinutes.observe(viewLifecycleOwner) { binding.tvMonthTime.text = "${it}min ce mois" }
        vm.totalMastered.observe(viewLifecycleOwner) { binding.tvMastered.text = "$it versets maîtrisés" }
        vm.totalInProgress.observe(viewLifecycleOwner) { binding.tvInProgress.text = "$it versets en cours" }
        vm.totalPending.observe(viewLifecycleOwner) { binding.tvPending.text = "$it versets à apprendre" }
        vm.streakDays.observe(viewLifecycleOwner) { binding.tvStreak.text = "$it jours consécutifs 🔥" }
        vm.totalListeningHours.observe(viewLifecycleOwner) {
            binding.tvTotalHours.text = "${it}h d'écoute totale"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
