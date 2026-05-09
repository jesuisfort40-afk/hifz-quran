package com.hifz.quran.ui.surah

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hifz.quran.data.SurahInfo
import com.hifz.quran.databinding.ItemSurahBrowserBinding

class SurahBrowserAdapter(
    private val onImport: (SurahInfo) -> Unit
) : ListAdapter<SurahInfo, SurahBrowserAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSurahBrowserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSurahBrowserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        with(holder.binding) {
            tvNumber.text = s.number.toString()
            tvArabic.text = s.nameArabic
            tvLatin.text = s.nameLatin
            tvFr.text = s.nameFr
            tvVerseCount.text = "${s.verseCount} versets"
            tvJuz.text = "Juz ${s.juz}"

            root.setOnClickListener { onImport(s) }
            btnAdd.setOnClickListener { onImport(s) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SurahInfo>() {
            override fun areItemsTheSame(a: SurahInfo, b: SurahInfo) = a.number == b.number
            override fun areContentsTheSame(a: SurahInfo, b: SurahInfo) = a == b
        }
    }
}
