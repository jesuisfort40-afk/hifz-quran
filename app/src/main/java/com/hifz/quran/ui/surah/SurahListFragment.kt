package com.hifz.quran.ui.surah

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentSurahListBinding
import com.hifz.quran.model.Sourate
import com.hifz.quran.ui.player.PlayerFragment

class SurahListFragment : Fragment() {

    private var _binding: FragmentSurahListBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SurahViewModel
    private lateinit var adapter: SurahAdapter

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // Persist permission to access URI after app restart
                requireContext().contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission not persistable, continue anyway
            }
            showAddSourateDialog(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurahListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SurahViewModel::class.java]

        adapter = SurahAdapter(
            onPlay = { sourate -> openPlayer(sourate) },
            onDelete = { sourate -> confirmDelete(sourate) }
        )
        binding.rvSourates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSourates.adapter = adapter

        vm.sourates.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener {
            pickAudio.launch("audio/*")
        }
    }

    private fun showAddSourateDialog(uri: Uri) {
        if (_binding == null) return
        val fileName = getFileName(uri) ?: "Sourate"
        val input = TextInputEditText(requireContext()).apply {
            setText(fileName.substringBeforeLast("."))
            hint = "Nom de la sourate"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ajouter une sourate")
            .setMessage("Fichier: $fileName")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text?.toString()?.trim()?.ifEmpty { fileName } ?: fileName
                vm.addSourate(uri, name)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDelete(sourate: Sourate) {
        if (_binding == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer")
            .setMessage("Supprimer \"${sourate.name}\" et tous ses versets ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.deleteSourate(sourate) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openPlayer(sourate: Sourate) {
        val activity = activity as? MainActivity ?: return
        vm.setSelectedSourate(sourate.id)
        activity.loadFragment(PlayerFragment.newInstance(sourate.id), R.id.nav_player)
        activity.navigateTo(R.id.nav_player)
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) result = uri.path?.substringAfterLast("/")
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
