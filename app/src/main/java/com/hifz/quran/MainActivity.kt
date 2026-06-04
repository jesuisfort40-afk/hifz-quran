package com.hifz.quran

import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences

    // ID de la dernière sourate jouée — persisté dans SharedPreferences
    // pour survivre à la fermeture complète de l'app
    private var lastPlayedSourateId: Long
        get() = prefs.getLong("last_sourate_id", -1L)
        set(value) = prefs.edit().putLong("last_sourate_id", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // BUG PERSISTANCE — SharedPreferences au lieu de savedInstanceState
        // savedInstanceState est null après fermeture complète de l'app
        // → l'ID était perdu, le lecteur renvoyait toujours vers la bibliothèque
        prefs = getSharedPreferences("hifz_prefs", MODE_PRIVATE)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), HomeFragment::class.java.simpleName)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> loadFragment(HomeFragment(),      HomeFragment::class.java.simpleName)
                R.id.nav_sourates -> loadFragment(SurahListFragment(), SurahListFragment::class.java.simpleName)

                R.id.nav_player -> {
                    val sourateId = lastPlayedSourateId
                    if (sourateId != -1L) {
                        // Réutilise l'instance existante si déjà présente (évite recréation inutile)
                        val existing = supportFragmentManager
                            .findFragmentByTag(PlayerFragment::class.java.simpleName)
                        if (existing != null) {
                            loadFragment(existing, PlayerFragment::class.java.simpleName)
                        } else {
                            loadFragment(PlayerFragment.newInstance(sourateId), PlayerFragment::class.java.simpleName)
                        }
                    } else {
                        loadFragment(SurahListFragment(), SurahListFragment::class.java.simpleName)
                        binding.bottomNav.selectedItemId = R.id.nav_sourates
                    }
                }

                R.id.nav_stats    -> loadFragment(StatsFragment(),    StatsFragment::class.java.simpleName)
                R.id.nav_settings -> loadFragment(SettingsFragment(), SettingsFragment::class.java.simpleName)
            }
            true
        }
    }

    fun loadFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, existing ?: fragment, tag)
            .commit()
    }

    /**
     * Ouvre le lecteur sur une sourate précise.
     * Mémorise l'ID dans SharedPreferences → survit à la fermeture de l'app.
     * Supprime l'ancien PlayerFragment pour forcer la recréation avec le bon ID.
     */
    fun openPlayer(sourateId: Long) {
        lastPlayedSourateId = sourateId
        // Supprimer l'ancienne instance pour que newInstance() reçoive le bon ARG_SOURATE_ID
        supportFragmentManager.findFragmentByTag(PlayerFragment::class.java.simpleName)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        loadFragment(PlayerFragment.newInstance(sourateId), PlayerFragment::class.java.simpleName)
        binding.bottomNav.selectedItemId = R.id.nav_player
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
