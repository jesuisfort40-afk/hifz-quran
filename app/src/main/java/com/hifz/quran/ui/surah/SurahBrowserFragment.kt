package com.hifz.quran.ui.surah

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifz.quran.data.QuranData
import com.hifz.quran.data.ReciterInfo
import com.hifz.quran.data.SurahInfo
import com.hifz.quran.databinding.FragmentSurahBrowserBinding
import com.hifz.quran.MainActivity
import kotlinx.coroutines.launch

class SurahBrowserFragment : Fragment() {

    private var _binding: FragmentSurahBrowserBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SurahViewModel
    private lateinit var browserAdapter: SurahBrowserAdapter

    private var allSurahs = QuranData.SURAHS
    private var selectedReciter: ReciterInfo = QuranData.DEFAULT_RECITER

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurahBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SurahViewModel::class.java]

        setupReciterSelector()
        setupSearch()
        setupList()
        observeImportedSurahs()
    }

    // Observer les sourates importées pour mettre à jour les badges en temps réel
    private fun observeImportedSurahs() {
        vm.sourates.observe(viewLifecycleOwner) { sourates ->
            // Construire le set des (sourateNumber, reciterId) importés
            // On passe uniquement les numéros filtrés par le récitateur sélectionné
            val importedNumbers = sourates
                .filter { it.reciterId == selectedReciter.id }
                .map { it.sourateNumber }
                .toSet()
            browserAdapter.setImportedSurahs(importedNumbers)
        }
    }

    private fun setupReciterSelector() {
        updateReciterChip()
        binding.chipReciter.setOnClickListener { showReciterPicker() }
    }

    private fun updateReciterChip() {
        binding.chipReciter.text = "🎙️ ${selectedReciter.displayName}"
    }

    private fun showReciterPicker() {
        val names = QuranData.RECITERS
            .map { "${it.nameArabic} — ${it.displayName}" }
            .toTypedArray()
        val currentIdx = QuranData.RECITERS.indexOf(selectedReciter).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir le récitateur")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                selectedReciter = QuranData.RECITERS[which]
                updateReciterChip()
                // Rafraîchir les badges pour le nouveau récitateur
                val sourates = vm.sourates.value ?: emptyList()
                val importedNumbers = sourates
                    .filter { it.reciterId == selectedReciter.id }
                    .map { it.sourateNumber }
                    .toSet()
                browserAdapter.setImportedSurahs(importedNumbers)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterSurahs(s?.toString() ?: "") }
        })
    }

    private fun filterSurahs(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allSurahs else allSurahs.filter {
            it.nameLatin.lowercase().contains(q) ||
            it.nameFr.lowercase().contains(q) ||
            it.nameArabic.contains(q) ||
            it.number.toString() == q
        }
        browserAdapter.submitList(filtered)
        binding.tvResultCount.text = "${filtered.size} sourates"
    }

    private fun setupList() {
        browserAdapter = SurahBrowserAdapter { surah -> confirmImport(surah) }
        binding.rvBrowser.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBrowser.adapter = browserAdapter
        browserAdapter.submitList(allSurahs)
        binding.tvResultCount.text = "${allSurahs.size} sourates"
    }

    private fun confirmImport(surah: SurahInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Double vérification DB (au cas où le LiveData ne soit pas encore à jour)
            val alreadyExists = vm.isSurahAlreadyImportedSafe(surah.number, selectedReciter.id)
            if (_binding == null) return@launch

            if (alreadyExists) {
                // Déjà importée → juste proposer d'ouvrir
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Déjà importée")
                    .setMessage("${surah.nameArabic} (${surah.nameLatin}) est déjà dans ta bibliothèque.")
                    .setPositiveButton("Ouvrir") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val id = vm.getExistingSurahIdSafe(surah.number, selectedReciter.id)
                            if (id != null) (activity as? MainActivity)?.openPlayer(id)
                        }
                    }
                    .setNegativeButton("Fermer", null)
                    .show()
                return@launch
            }

            val audioInfo = if (surah.verseCount <= 10)
                "🎵 Audio streamé verset par verset."
            else
                "🎵 Audio streamé · ${surah.verseCount} versets (connexion requise)."

            // Un seul bouton : "Importer" — pas de lancement automatique du lecteur
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Importer cette sourate ?")
                .setMessage(
                    "${surah.nameArabic}\n${surah.nameLatin} — ${surah.nameFr}\n\n" +
                    "📖 ${surah.verseCount} versets · 🎙️ ${selectedReciter.displayName}\n\n" +
                    "📥 Texte arabe téléchargé (~10 Ko).\n$audioInfo"
                )
                .setPositiveButton("Importer") { _, _ -> importSurah(surah) }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun importSurah(surah: SurahInfo) {
        binding.progressImport.visibility = View.VISIBLE
        binding.tvImportStatus.visibility = View.VISIBLE
        binding.tvImportStatus.text = "⏳ Importation de ${surah.nameLatin}…"

        vm.importSurahFromLibrary(surah, selectedReciter) { result: Long ->
            if (_binding == null) return@importSurahFromLibrary
            binding.progressImport.visibility = View.GONE
            if (result > 0) {
                binding.tvImportStatus.text = "✅ ${surah.nameLatin} importée !"
                // Le LiveData vm.sourates va se mettre à jour automatiquement
                // → observeImportedSurahs() va rafraîchir le badge de l'item
                binding.tvImportStatus.postDelayed({
                    if (_binding != null) binding.tvImportStatus.visibility = View.GONE
                }, 2500)
            } else {
                binding.tvImportStatus.text = "❌ Erreur. Vérifiez votre connexion."
                Toast.makeText(requireContext(), "Erreur d'importation", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
