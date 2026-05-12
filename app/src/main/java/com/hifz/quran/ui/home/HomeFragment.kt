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

        // Actualiser les stats à chaque visite de l'accueil
        statsVm.refreshStats()
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // BUG FIX #3 — Pas de stickers dans le greeting
        binding.tvGreeting.text = when (hour) {
            in 5..11  -> "صباح الخير\nBonne révision du matin"
            in 12..17 -> "بعد الظهر\nContinuez votre hifz"
            in 18..21 -> "مساء الخير\nBonne révision du soir"
            else      -> "الليل\nQue Allah facilite votre mémorisation"
        }
    }

    private fun observeStats() {
        // BUG FIX #3 — Stats qui ne comptent pas :
        // CAUSE : StatsViewModel.refreshStats() n'était pas appelé depuis HomeFragment,
        //         donc les LiveData restaient à leur valeur initiale (0).
        //         refreshStats() est maintenant appelé dans onViewCreated().

        statsVm.todayMinutes.observe(viewLifecycleOwner) { mins ->
            val v = mins ?: 0L
            binding.tvTodayTime.text = when {
                v >= 60 -> "${v / 60}h ${v % 60}min"
                else    -> "${v}min"
            }
        }

        statsVm.weekSessions.observe(viewLifecycleOwner) { count ->
            val v = count ?: 0
            binding.tvWeekSessions.text = "$v session${if (v > 1) "s" else ""}"
        }

        statsVm.totalMastered.observe(viewLifecycleOwner) { count ->
            val v = count ?: 0
            binding.tvMastered.text = "$v verset${if (v > 1) "s" else ""}"
        }

        statsVm.streakDays.observe(viewLifecycleOwner) { days ->
            val v = days ?: 0
            binding.tvStreak.text = "$v jour${if (v > 1) "s" else ""}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
