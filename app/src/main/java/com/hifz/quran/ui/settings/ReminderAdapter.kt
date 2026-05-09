package com.hifz.quran.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hifz.quran.databinding.ItemReminderBinding
import com.hifz.quran.model.Reminder

class ReminderAdapter(
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onEdit: (Reminder) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        with(holder.binding) {
            tvLabel.text = r.label
            tvTime.text = "%02d:%02d".format(r.hour, r.minute)
            switchEnabled.isChecked = r.isEnabled
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(r, checked) }
            root.setOnClickListener { onEdit(r) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Reminder>() {
            override fun areItemsTheSame(a: Reminder, b: Reminder) = a.id == b.id
            override fun areContentsTheSame(a: Reminder, b: Reminder) = a == b
        }
    }
}
