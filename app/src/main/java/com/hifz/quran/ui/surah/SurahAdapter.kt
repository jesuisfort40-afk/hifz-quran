package com.hifz.quran.ui.surah

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hifz.quran.databinding.ItemSourateBinding
import com.hifz.quran.model.Sourate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SurahAdapter(
    private val onPlay: (Sourate) -> Unit,
    private val onDelete: (Sourate) -> Unit
) : ListAdapter<Sourate, SurahAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSourateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSourateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        with(holder.binding) {
            tvName.text = s.name
            tvArabicName.text = s.arabicName.ifEmpty { "بِسْمِ اللَّهِ" }
            tvDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(s.dateAdded))
            tvVersets.text = if (s.totalVersets > 0) "${s.totalVersets} versets" else "Aucun verset défini"

            try {
                cardBg.setCardBackgroundColor(Color.parseColor(s.coverColor))
            } catch (e: Exception) {
                cardBg.setCardBackgroundColor(Color.parseColor("#1e3a5f"))
            }

            btnPlay.setOnClickListener { onPlay(s) }
            btnDelete.setOnClickListener { onDelete(s) }
            root.setOnClickListener { onPlay(s) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Sourate>() {
            override fun areItemsTheSame(a: Sourate, b: Sourate) = a.id == b.id
            override fun areContentsTheSame(a: Sourate, b: Sourate) = a == b
        }
    }
}
