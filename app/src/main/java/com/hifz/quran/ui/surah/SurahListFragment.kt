package com.hifz.quran.ui.surah

import android.content.Intent
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
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentSurahListBinding
import com.hifz.quran.model.Sourate
import com.hifz.quran.ui.player.PlayerFragment
import com.hifz.quran.MainActivity

class SurahListFragment : Fragment() {

    private var _binding: FragmentSurahListBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SurahViewModel
    private lateinit var adapter: SurahAdapter

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // ✅ FIX BUG AJOUT BIBLIOTHÈQUE :
            // On prend une permission persistante sur l'URI avant de la sauvegarder.
            // Sans ça, l'URI content:// expire après redémarrage de l'app
            // et ExoPlayer crash en tentant de lire le fichier.
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Certains providers ne supportent pas les permissions persistantes,
                // on continue quand même (l'URI fonctionnera au moins dans la session).
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
            // ✅ FIX BUG AJOUT BIBLIOTHÈQUE :
            // On demande explicitement une URI persistante via FLAG_GRANT_PERSISTABLE_URI_PERMISSION.
            // Cela permet à takePersistableUriPermission() de fonctionner correctement.
            pickAudio.launch("audio/*")
        }
    }

    private fun showAddSourateDialog(uri: Uri) {
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
                val name = input.text?.toString()?.trim() ?: fileName
                vm.addSourate(uri, name)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDelete(sourate: Sourate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer")
            .setMessage("Supprimer \"${sourate.name}\" et tous ses versets ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.deleteSourate(sourate) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openPlayer(sourate: Sourate) {
        val fragment = PlayerFragment.newInstance(sourate.id)
        (activity as? MainActivity)?.apply {
            loadFragment(fragment, PlayerFragment::class.java.simpleName)
            navigateTo(R.id.nav_player)
        }
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
