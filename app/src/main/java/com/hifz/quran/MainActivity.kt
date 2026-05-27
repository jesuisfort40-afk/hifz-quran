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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), HomeFragment::class.java.simpleName)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> loadFragment(HomeFragment(),      HomeFragment::class.java.simpleName)
                R.id.nav_sourates -> loadFragment(SurahListFragment(), SurahListFragment::class.java.simpleName)
                R.id.nav_player -> {
    val vm = ViewModelProvider(this)[PlayerViewModel::class.java]
    if (vm.currentSourate.value != null) {
        loadFragment(PlayerFragment(), PlayerFragment::class.java.simpleName)
    } else {
        // Pas de sourate chargée → aller à la bibliothèque
        loadFragment(SurahListFragment(), SurahListFragment::class.java.simpleName)
        binding.bottomNav.selectedItemId = R.id.nav_sourates
    }
}
                R.id.nav_stats    -> loadFragment(StatsFragment(),     StatsFragment::class.java.simpleName)
                R.id.nav_settings -> loadFragment(SettingsFragment(),  SettingsFragment::class.java.simpleName)
            }
            true
        }
    }

    /**
     * BUG FIX #1 — Navigation crash
     * AVANT : nouvelle instance à chaque clic → PlayerFragment perdait son ServiceConnection
     *         → playerService = null → NPE au clic Play
     * APRÈS : findFragmentByTag réutilise l'instance existante, la connexion service est préservée
     */
    fun loadFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, existing ?: fragment, tag)
            .commit()
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
