package com.hifz.quran.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentHomeBinding
import com.hifz.quran.ui.stats.StatsViewModel
import com.hifz.quran.util.TimeUtils
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var statsVm: StatsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statsVm = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        setGreeting()
        observeStats()

        binding.btnStartSession.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_player)
        }
        binding.btnMySourates.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_sourates)
        }
        binding.btnStats.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_stats)
        }
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 5..11 -> "صباح الخير 🌅\nBonne révision du matin !"
            in 12..17 -> "بعد الظهر 🌤️\nContinuez votre hifz !"
            in 18..21 -> "مساء الخير 🌙\nBonne révision du soir !"
            else -> "الليل 🌟\nQue Allah facilite votre mémorisation"
        }
    }

    private fun observeStats() {
        statsVm.todayMinutes.observe(viewLifecycleOwner) { mins ->
            binding.tvTodayTime.text = "${mins}min"
        }
        statsVm.weekSessions.observe(viewLifecycleOwner) { count ->
            binding.tvWeekSessions.text = "$count sessions"
        }
        statsVm.totalMastered.observe(viewLifecycleOwner) { count ->
            binding.tvMastered.text = "$count versets"
        }
        statsVm.streakDays.observe(viewLifecycleOwner) { days ->
            binding.tvStreak.text = "$days 🔥"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
