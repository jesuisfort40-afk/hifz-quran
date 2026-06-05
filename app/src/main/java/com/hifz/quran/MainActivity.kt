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

    private var lastPlayedSourateId: Long
        get() = prefs.getLong("last_sourate_id", -1L)
        set(value) = prefs.edit().putLong("last_sourate_id", value).apply()

    // Fragments gardés en vie (show/hide) pour ne pas détruire le PlayerFragment
    // pendant la navigation → la lecture continue sans interruption
    private var homeFragment:    Fragment? = null
    private var souratesFragment: Fragment? = null
    private var playerFragment:  PlayerFragment? = null
    private var statsFragment:   Fragment? = null
    private var settingsFragment: Fragment? = null

    private var activeTag = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("hifz_prefs", MODE_PRIVATE)

        if (savedInstanceState == null) {
            showFragment(R.id.nav_home)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
            true
        }
    }

    private fun showFragment(navId: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()

        // Cacher tous les fragments actifs
        fm.fragments.forEach { tx.hide(it) }

        when (navId) {
            R.id.nav_home -> {
                if (homeFragment == null) {
                    homeFragment = HomeFragment()
                    tx.add(R.id.fragmentContainer, homeFragment!!, HomeFragment::class.java.simpleName)
                } else tx.show(homeFragment!!)
                activeTag = HomeFragment::class.java.simpleName
            }
            R.id.nav_sourates -> {
                if (souratesFragment == null) {
                    souratesFragment = SurahListFragment()
                    tx.add(R.id.fragmentContainer, souratesFragment!!, SurahListFragment::class.java.simpleName)
                } else tx.show(souratesFragment!!)
                activeTag = SurahListFragment::class.java.simpleName
            }
            R.id.nav_player -> {
                val sourateId = lastPlayedSourateId
                if (sourateId != -1L) {
                    if (playerFragment == null) {
                        playerFragment = PlayerFragment.newInstance(sourateId)
                        tx.add(R.id.fragmentContainer, playerFragment!!, PlayerFragment::class.java.simpleName)
                    } else tx.show(playerFragment!!)
                    activeTag = PlayerFragment::class.java.simpleName
                } else {
                    // Pas encore de sourate → aller à la bibliothèque
                    if (souratesFragment == null) {
                        souratesFragment = SurahListFragment()
                        tx.add(R.id.fragmentContainer, souratesFragment!!, SurahListFragment::class.java.simpleName)
                    } else tx.show(souratesFragment!!)
                    activeTag = SurahListFragment::class.java.simpleName
                    tx.commit()
                    binding.bottomNav.selectedItemId = R.id.nav_sourates
                    return
                }
            }
            R.id.nav_stats -> {
                if (statsFragment == null) {
                    statsFragment = StatsFragment()
                    tx.add(R.id.fragmentContainer, statsFragment!!, StatsFragment::class.java.simpleName)
                } else tx.show(statsFragment!!)
                activeTag = StatsFragment::class.java.simpleName
            }
            R.id.nav_settings -> {
                if (settingsFragment == null) {
                    settingsFragment = SettingsFragment()
                    tx.add(R.id.fragmentContainer, settingsFragment!!, SettingsFragment::class.java.simpleName)
                } else tx.show(settingsFragment!!)
                activeTag = SettingsFragment::class.java.simpleName
            }
        }
        tx.commit()
    }

    /**
     * Ouvre le lecteur sur une sourate précise.
     * Si une autre sourate était en cours → recrée le PlayerFragment avec le nouvel ID.
     * Sinon (même sourate) → simple navigation.
     */
    fun openPlayer(sourateId: Long) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()

        lastPlayedSourateId = sourateId

        // Si PlayerFragment existe déjà avec une autre sourate → le remplacer
        val existingPlayer = playerFragment
        if (existingPlayer != null) {
            tx.remove(existingPlayer)
        }

        fm.fragments.filter { it != existingPlayer }.forEach { tx.hide(it) }

        val newPlayer = PlayerFragment.newInstance(sourateId)
        playerFragment = newPlayer
        tx.add(R.id.fragmentContainer, newPlayer, PlayerFragment::class.java.simpleName)
        tx.commit()

        binding.bottomNav.selectedItemId = R.id.nav_player
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
