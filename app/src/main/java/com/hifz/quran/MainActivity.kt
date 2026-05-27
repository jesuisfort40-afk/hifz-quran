package com.hifz.quran

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.hifz.quran.databinding.ActivityMainBinding
import com.hifz.quran.ui.home.HomeFragment
import com.hifz.quran.ui.player.PlayerFragment
import com.hifz.quran.ui.player.PlayerViewModel
import com.hifz.quran.ui.stats.StatsFragment
import com.hifz.quran.ui.settings.SettingsFragment
import com.hifz.quran.ui.surah.SurahListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // FIX MÉMOIRE SOURATE : on garde l'ID de la dernière sourate lue
    // pour pouvoir rouvrir le lecteur directement sur la bonne sourate
    private var lastPlayedSourateId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), HomeFragment::class.java.simpleName)
        } else {
            // Restaurer l'ID mémorisé après rotation/recreation
            lastPlayedSourateId = savedInstanceState.getLong("last_sourate_id", -1L)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> loadFragment(HomeFragment(),      HomeFragment::class.java.simpleName)
                R.id.nav_sourates -> loadFragment(SurahListFragment(), SurahListFragment::class.java.simpleName)

                // FIX ANR + MÉMOIRE : si une sourate a déjà été jouée → rouvrir le lecteur dessus
                // Sinon → rediriger vers bibliothèque
                R.id.nav_player   -> {
                    val sourateId = lastPlayedSourateId
                    if (sourateId != -1L) {
                        val fragment = PlayerFragment.newInstance(sourateId)
                        loadFragment(fragment, PlayerFragment::class.java.simpleName)
                    } else {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("last_sourate_id", lastPlayedSourateId)
    }

    /**
     * FIX NAVIGATION : réutilise l'instance existante si déjà créée
     * → préserve la ServiceConnection du PlayerFragment
     */
    fun loadFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, existing ?: fragment, tag)
            .commit()
    }

    /**
     * FIX DOUBLE LECTURE : appelé depuis SurahListFragment avant d'ouvrir le lecteur.
     * On mémorise ici la sourate sélectionnée AVANT de charger le fragment,
     * ce qui garantit que nav_player retrouvera toujours la bonne sourate.
     */
    fun openPlayer(sourateId: Long) {
        lastPlayedSourateId = sourateId
        val fragment = PlayerFragment.newInstance(sourateId)
        // On efface l'ancien PlayerFragment du back stack pour éviter la double instance
        supportFragmentManager.findFragmentByTag(PlayerFragment::class.java.simpleName)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        loadFragment(fragment, PlayerFragment::class.java.simpleName)
        binding.bottomNav.selectedItemId = R.id.nav_player
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
