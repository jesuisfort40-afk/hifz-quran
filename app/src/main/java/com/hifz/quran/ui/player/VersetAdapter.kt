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

    inner class VH(val binding: ItemVersetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemVersetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        with(holder.binding) {
            tvVersetNum.text = "Verset ${v.numero}"
            tvTimeRange.text = "${TimeUtils.formatMs(v.startMs)} → ${TimeUtils.formatMs(v.endMs)}"
            tvRepeats.text = "${v.repeatCount}× écouté"

            // Status badge
            val (color, label) = when (v.status) {
                VersetStatus.A_APPRENDRE -> Pair(R.color.status_pending, "À apprendre")
                VersetStatus.EN_COURS -> Pair(R.color.status_progress, "En cours")
                VersetStatus.MAITRISE -> Pair(R.color.status_done, "Maîtrisé ✓")
            }
            tvStatus.text = label
            tvStatus.setTextColor(ContextCompat.getColor(root.context, color))

            btnPlay.setOnClickListener { onPlay(v) }
            btnDelete.setOnClickListener { onDelete(v) }

            // Cycle status on tap
            tvStatus.setOnClickListener {
                val next = when (v.status) {
                    VersetStatus.A_APPRENDRE -> VersetStatus.EN_COURS
                    VersetStatus.EN_COURS -> VersetStatus.MAITRISE
                    VersetStatus.MAITRISE -> VersetStatus.A_APPRENDRE
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
