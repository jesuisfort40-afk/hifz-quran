package com.hifz.quran

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivLogo     = findViewById<ImageView>(R.id.ivLogo)
        val viewGlow   = findViewById<View>(R.id.viewGlow)
        val tvDeveloper = findViewById<TextView>(R.id.tvDeveloper)

        // ── 1. Fondu du logo ──────────────────────────────────────────────────
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.splash_logo_fadein)
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation) {
                ivLogo.alpha = 1f
                viewGlow.alpha = 1f
            }
            override fun onAnimationRepeat(a: Animation) {}
            override fun onAnimationEnd(a: Animation) {
                // ── 2. Glow doré pulsant après le fondu ───────────────────────
                val glowAnim = AnimationUtils.loadAnimation(
                    this@SplashActivity, R.anim.splash_logo_glow
                )
                ivLogo.startAnimation(glowAnim)
                viewGlow.startAnimation(glowAnim)
            }
        })
        ivLogo.startAnimation(fadeIn)
        viewGlow.startAnimation(fadeIn)

        // ── 3. Texte SDSLABS glisse du bas ────────────────────────────────────
        val textAnim = AnimationUtils.loadAnimation(this, R.anim.splash_text_slidein)
        textAnim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation) { tvDeveloper.alpha = 1f }
            override fun onAnimationRepeat(a: Animation) {}
            override fun onAnimationEnd(a: Animation) {}
        })
        tvDeveloper.startAnimation(textAnim)

        // ── 4. Lancer MainActivity après 3.5s (fondu 1.2s + glow 3×0.7s) ─────
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            // Transition : fondu sortant
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3500)
    }
}
