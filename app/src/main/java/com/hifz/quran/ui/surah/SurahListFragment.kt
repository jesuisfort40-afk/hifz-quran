package com.hifz.quran.ui.surah

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #2 — Permission stockage non demandée
    //
    // AVANT : l'app utilisait ActivityResultContracts.GetContent() directement
    //         sans vérifier/demander la permission READ_MEDIA_AUDIO (Android 13+)
    //         ou READ_EXTERNAL_STORAGE (Android ≤12). Sur certains appareils/versions
    //         le file picker s'ouvrait mais la lecture du fichier crashait ensuite
    //         avec SecurityException.
    //
    // APRÈS : on vérifie la permission au runtime avant d'ouvrir le picker.
    //         Si elle manque, on la demande. Une fois accordée, on lance le picker.
    // ─────────────────────────────────────────────────────────────────────────

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchPicker()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission nécessaire pour accéder aux fichiers audio",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX #3 — URI audio perdue après redémarrage app (crash à la lecture)
    //
    // AVANT : on sauvegardait l'URI content:// brute en base de données.
    //         Sans takePersistableUriPermission(), cette permission temporaire
    //         expire dès que l'app est fermée. Au relancement, ExoPlayer tentait
    //         de lire un URI invalide → SecurityException → crash.
    //
    // APRÈS : takePersistableUriPermission() est appelé immédiatement à la
    //         réception de l'URI, avant l'insertion en base. Le système OS
    //         mémorise cette permission de façon permanente.
    // ─────────────────────────────────────────────────────────────────────────

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Certains providers ne supportent pas les permissions persistantes.
                // On continue : l'URI fonctionnera au moins pendant la session en cours.
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
            onPlay   = { sourate -> openPlayer(sourate) },
            onDelete = { sourate -> confirmDelete(sourate) }
        )
        binding.rvSourates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSourates.adapter = adapter

        vm.sourates.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener {
            checkPermissionAndPickAudio()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vérifie la permission selon la version Android, puis lance le picker
    // ─────────────────────────────────────────────────────────────────────────
    private fun checkPermissionAndPickAudio() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO          // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE     // Android ≤ 12
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                    PackageManager.PERMISSION_GRANTED -> {
                // Permission déjà accordée → on ouvre directement le picker
                launchPicker()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // L'utilisateur a refusé une fois → expliquer pourquoi c'est nécessaire
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Permission requise")
                    .setMessage(
                        "Hifz Quran a besoin d'accéder à vos fichiers audio " +
                        "pour importer des sourates dans la bibliothèque."
                    )
                    .setPositiveButton("Autoriser") { _, _ -> requestPermission.launch(permission) }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            else -> {
                // Première demande
                requestPermission.launch(permission)
            }
        }
    }

    private fun launchPicker() {
        pickAudio.launch("audio/*")
    }

    private fun showAddSourateDialog(uri: Uri) {
        val fileName = getFileName(uri) ?: "Sourate"
        val input = TextInputEditText(requireContext()).apply {
            setText(fileName.substringBeforeLast("."))
            hint = "Nom de la sourate"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ajouter une sourate")
            .setMessage("Fichier : $fileName")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text?.toString()?.trim()?.ifEmpty { fileName } ?: fileName
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
