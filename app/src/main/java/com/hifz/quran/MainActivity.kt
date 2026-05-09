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

        // ✅ FIX BUG NAVIGATION : on charge le fragment initial une seule fois
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), HomeFragment::class.java.simpleName)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home      -> loadFragment(HomeFragment(),       HomeFragment::class.java.simpleName)
                R.id.nav_sourates  -> loadFragment(SurahListFragment(),  SurahListFragment::class.java.simpleName)
                R.id.nav_player    -> loadFragment(PlayerFragment(),      PlayerFragment::class.java.simpleName)
                R.id.nav_stats     -> loadFragment(StatsFragment(),       StatsFragment::class.java.simpleName)
                R.id.nav_settings  -> loadFragment(SettingsFragment(),    SettingsFragment::class.java.simpleName)
            }
            true
        }
    }

    /**
     * ✅ FIX BUG NAVIGATION :
     * On réutilise l'instance existante via le tag si elle est déjà dans la back-stack.
     * Cela évite de recréer PlayerFragment (et de perdre la connexion au service audio)
     * à chaque fois que l'utilisateur change d'onglet.
     */
    fun loadFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragmentContainer, existing ?: fragment, tag)
            .commit()
    }

    fun navigateTo(navId: Int) {
        binding.bottomNav.selectedItemId = navId
    }
}
