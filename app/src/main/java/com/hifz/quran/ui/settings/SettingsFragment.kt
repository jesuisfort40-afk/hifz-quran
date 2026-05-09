package com.hifz.quran.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hifz.quran.databinding.FragmentSettingsBinding
import com.hifz.quran.model.Reminder
import com.hifz.quran.util.ReminderManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ReminderAdapter
    private val reminders = mutableListOf<Reminder>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReminderAdapter(
            onToggle = { reminder, enabled -> toggleReminder(reminder, enabled) },
            onEdit = { reminder -> editReminderTime(reminder) }
        )
        binding.rvReminders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReminders.adapter = adapter

        loadReminders()

        binding.btnAddReminder.setOnClickListener { addNewReminder() }
    }

    private fun loadReminders() {
        reminders.clear()
        reminders.addAll(ReminderManager.getReminders(requireContext()))
        adapter.submitList(reminders.toList())
    }

    private fun toggleReminder(reminder: Reminder, enabled: Boolean) {
        val idx = reminders.indexOfFirst { it.id == reminder.id }
        if (idx < 0) return
        val updated = reminder.copy(isEnabled = enabled)
        reminders[idx] = updated
        ReminderManager.saveReminders(requireContext(), reminders)
        if (enabled) ReminderManager.scheduleReminder(requireContext(), updated)
        else ReminderManager.cancelReminder(requireContext(), updated)
        adapter.submitList(reminders.toList())
    }

    private fun editReminderTime(reminder: Reminder) {
        TimePickerDialog(requireContext(), { _, h, m ->
            val idx = reminders.indexOfFirst { it.id == reminder.id }
            if (idx < 0) return@TimePickerDialog
            val updated = reminder.copy(hour = h, minute = m)
            reminders[idx] = updated
            ReminderManager.saveReminders(requireContext(), reminders)
            if (updated.isEnabled) {
                ReminderManager.cancelReminder(requireContext(), reminder)
                ReminderManager.scheduleReminder(requireContext(), updated)
            }
            adapter.submitList(reminders.toList())
        }, reminder.hour, reminder.minute, true).show()
    }

    private fun addNewReminder() {
        val newId = (reminders.maxOfOrNull { it.id } ?: 0) + 1
        TimePickerDialog(requireContext(), { _, h, m ->
            val r = Reminder(newId, h, m, "📖 Révision Coran", false)
            reminders.add(r)
            ReminderManager.saveReminders(requireContext(), reminders)
            adapter.submitList(reminders.toList())
        }, 8, 0, true).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
