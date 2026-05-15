package com.hifz.quran.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hifz.quran.R
import com.hifz.quran.model.Badge

class BadgePreviewAdapter : ListAdapter<Badge, BadgePreviewAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView   = view.findViewById(R.id.tvBadgeTitle)
        val tvIcon: TextView    = view.findViewById(R.id.tvBadgeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val badge = getItem(position)
        holder.tvTitle.text = badge.titleFr
        // Icône textuelle selon l'id du badge
        holder.tvIcon.text = when {
            badge.id.contains("star")     -> "★"
            badge.id.contains("streak")   -> "◆"
            badge.id.contains("repeat")   -> "↺"
            badge.id.contains("mastered") -> "✓"
            badge.id.contains("time")     -> "◷"
            badge.id.contains("surah") || badge.id.contains("library") -> "📖"
            badge.id.contains("fatiha")   -> "◎"
            else                           -> "●"
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Badge>() {
            override fun areItemsTheSame(a: Badge, b: Badge)    = a.id == b.id
            override fun areContentsTheSame(a: Badge, b: Badge) = a == b
        }
    }
}
