package com.hifz.quran.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hifz.quran.R
import com.hifz.quran.databinding.ItemVersetBinding
import com.hifz.quran.model.Verset
import com.hifz.quran.model.VersetStatus
import com.hifz.quran.util.TimeUtils

class VersetAdapter(
    private val onPlay: (Verset) -> Unit,
    private val onStatusChange: (Verset, VersetStatus) -> Unit,
    private val onDelete: (Verset) -> Unit
) : ListAdapter<Verset, VersetAdapter.VH>(DIFF) {

    // ID du verset actuellement en lecture
    private var activeVersetId: Long = -1L

    fun setActiveVerset(id: Long) {
        val old = currentList.indexOfFirst { it.id == activeVersetId }
        activeVersetId = id
        val new = currentList.indexOfFirst { it.id == id }
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    inner class VH(val binding: ItemVersetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVersetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        val isActive = v.id == activeVersetId
        with(holder.binding) {

            // ── Numéro ────────────────────────────────────────────────────────
            tvVersetNum.text = "V.${v.numero}"

            // ── Temps ou texte arabe ──────────────────────────────────────────
            if (v.arabicText.isNotEmpty()) {
                // Mode bibliothèque : afficher le texte arabe
                tvTimeRange.visibility = View.GONE
                tvArabicSnippet.visibility = View.VISIBLE
                // Afficher les 60 premiers caractères comme aperçu dans la liste
                tvArabicSnippet.text = if (v.arabicText.length > 60)
                    v.arabicText.take(60) + "…"
                else
                    v.arabicText
            } else {
                // Mode legacy : afficher les temps
                tvTimeRange.visibility = View.VISIBLE
                tvArabicSnippet.visibility = View.GONE
                tvTimeRange.text = "${TimeUtils.formatMs(v.startMs)} → ${TimeUtils.formatMs(v.endMs)}"
            }

            tvRepeats.text = "${v.repeatCount}× écouté"

            // ── Statut ────────────────────────────────────────────────────────
            val (color, label) = when (v.status) {
                VersetStatus.A_APPRENDRE -> Pair(R.color.status_pending,  "À apprendre")
                VersetStatus.EN_COURS    -> Pair(R.color.status_progress, "En cours")
                VersetStatus.MAITRISE   -> Pair(R.color.status_done,     "Maîtrisé ✓")
            }
            tvStatus.text = label
            tvStatus.setTextColor(ContextCompat.getColor(root.context, color))

            // ── Surbrillance verset actif ────────────────────────────────────
            root.setBackgroundResource(
                if (isActive) R.drawable.bg_verset_item_active else R.drawable.bg_verset_item
            )
            tvVersetNum.setTextColor(
                ContextCompat.getColor(root.context,
                    if (isActive) R.color.accent else R.color.accent)
            )

            // ── Icône lecture animée ──────────────────────────────────────────
            ivPlaying.visibility = if (isActive) View.VISIBLE else View.GONE

            // ── Listeners ────────────────────────────────────────────────────
            btnPlay.setOnClickListener { onPlay(v) }
            btnDelete.setOnClickListener { onDelete(v) }
            root.setOnClickListener { onPlay(v) }
            tvStatus.setOnClickListener {
                val next = when (v.status) {
                    VersetStatus.A_APPRENDRE -> VersetStatus.EN_COURS
                    VersetStatus.EN_COURS    -> VersetStatus.MAITRISE
                    VersetStatus.MAITRISE   -> VersetStatus.A_APPRENDRE
                }
                onStatusChange(v, next)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Verset>() {
            override fun areItemsTheSame(a: Verset, b: Verset) = a.id == b.id
            override fun areContentsTheSame(a: Verset, b: Verset) = a == b
        }
    }
}
