package com.hifz.quran.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentHomeBinding
import com.hifz.quran.ui.stats.StatsViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var statsVm: StatsViewModel
    private lateinit var badgeAdapter: BadgePreviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statsVm = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        setupBadges()
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

        statsVm.refreshStats()
    }

    private fun setupBadges() {
        badgeAdapter = BadgePreviewAdapter()
        binding.rvBadges.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvBadges.adapter = badgeAdapter
        binding.rvBadges.isNestedScrollingEnabled = false

        statsVm.unlockedBadges.observe(viewLifecycleOwner) { badges ->
            val recent = badges.take(5)
            badgeAdapter.submitList(recent)
            binding.tvNoBadges.visibility = if (badges.isEmpty()) View.VISIBLE else View.GONE
            binding.rvBadges.visibility   = if (badges.isEmpty()) View.GONE   else View.VISIBLE
        }
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (arabic, french) = when (hour) {
            in 5..11  -> "صباح الخير" to "Bonne révision du matin"
            in 12..17 -> "بعد الظهر"  to "Continuez votre hifz"
            in 18..21 -> "مساء الخير" to "Bonne révision du soir"
            else      -> "الليل"      to "Que Allah facilite votre mémorisation"
        }
        binding.tvGreeting.text    = arabic
        binding.tvSubGreeting.text = french
    }

    private fun observeStats() {
        statsVm.todayMinutes.observe(viewLifecycleOwner) { mins ->
            val v = mins ?: 0L
            binding.tvTodayTime.text = if (v >= 60) "${v / 60}h ${v % 60}min" else "${v}min"
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
