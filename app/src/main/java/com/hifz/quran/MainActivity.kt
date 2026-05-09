package com.hifz.quran

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.hifz.quran.databinding.ActivityMainBinding
import com.hifz.quran.ui.home.HomeFragment
import com.hifz.quran.ui.player.PlayerFragment
import com.hifz.quran.ui.stats.StatsFragment
import com.hifz.quran.ui.settings.SettingsFragment
import com.hifz.quran.ui.surah.SurahListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentNavId = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), R.id.nav_home)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == currentNavId) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), item.itemId)
                R.id.nav_sourates -> loadFragment(SurahListFragment(), item.itemId)
                R.id.nav_player -> loadFragment(PlayerFragment(), item.itemId)
                R.id.nav_stats -> loadFragment(StatsFragment(), item.itemId)
                R.id.nav_settings -> loadFragment(SettingsFragment(), item.itemId)
            }
            true
        }
    }

    fun loadFragment(fragment: Fragment, navId: Int = currentNavId) {
        currentNavId = navId
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
