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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifz.quran.R
import com.hifz.quran.data.QuranData
import com.hifz.quran.data.ReciterInfo
import com.hifz.quran.data.SurahInfo
import com.hifz.quran.databinding.FragmentSurahBrowserBinding
import com.hifz.quran.ui.player.PlayerFragment
import com.hifz.quran.MainActivity

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
    }

    private fun setupReciterSelector() {
        updateReciterChip()
        binding.chipReciter.setOnClickListener { showReciterPicker() }
    }

    private fun updateReciterChip() {
        binding.chipReciter.text = "🎙️ ${selectedReciter.displayName}"
    }

    private fun showReciterPicker() {
        val names = QuranData.RECITERS.map { "${it.nameArabic} — ${it.displayName}" }.toTypedArray()
        val currentIdx = QuranData.RECITERS.indexOf(selectedReciter).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir le récitateur")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                selectedReciter = QuranData.RECITERS[which]
                updateReciterChip()
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSurahs(s?.toString() ?: "")
            }
        })
    }

    private fun filterSurahs(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allSurahs
        else allSurahs.filter {
            it.nameLatin.lowercase().contains(q) ||
                it.nameFr.lowercase().contains(q) ||
                it.nameArabic.contains(q) ||
                it.number.toString() == q
        }
        browserAdapter.submitList(filtered)
        binding.tvResultCount.text = "${filtered.size} sourates"
    }

    private fun setupList() {
        browserAdapter = SurahBrowserAdapter { surah ->
            confirmImport(surah)
        }
        binding.rvBrowser.adapter = browserAdapter
        browserAdapter.submitList(allSurahs)
        binding.tvResultCount.text = "${allSurahs.size} sourates"
    }

    private fun confirmImport(surah: SurahInfo) {
        // Vérifie si elle est déjà importée
        val alreadyExists = vm.isSurahAlreadyImported(surah.number, selectedReciter.id)
        if (alreadyExists) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Déjà importée")
                .setMessage(
                    "${surah.nameArabic} (${surah.nameLatin}) est déjà dans ta bibliothèque " +
                    "avec ce récitateur.\n\nOuvrir la sourate ?"
                )
                .setPositiveButton("Ouvrir") { _, _ ->
                    val id = vm.getExistingSurahId(surah.number, selectedReciter.id)
                    if (id != null) openPlayer(id)
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Importer cette sourate ?")
            .setMessage(
                "${surah.nameArabic}\n${surah.nameLatin} — ${surah.nameFr}\n\n" +
                "📖 ${surah.verseCount} versets\n" +
                "🎙️ ${selectedReciter.displayName}\n\n" +
                "Le texte arabe sera téléchargé (~10 Ko).\n" +
                "L'audio est streamé verset par verset."
            )
            .setPositiveButton("Importer") { _, _ ->
                importSurah(surah)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun importSurah(surah: SurahInfo) {
        binding.progressImport.visibility = View.VISIBLE
        binding.tvImportStatus.visibility = View.VISIBLE
        binding.tvImportStatus.text = "⏳ Importation de ${surah.nameLatin}…"

        // FIX BUILD : importSurahFromLibrary(surah, reciter) { result -> ... }
        // Le lambda reçoit un Long, type maintenant explicite dans SurahViewModel
        vm.importSurahFromLibrary(surah, selectedReciter) { result: Long ->
            if (_binding == null) return@importSurahFromLibrary
            binding.progressImport.visibility = View.GONE
            when {
                result > 0 -> {
                    binding.tvImportStatus.text = "✅ ${surah.nameLatin} importée !"
                    binding.tvImportStatus.postDelayed({
                        if (_binding != null) binding.tvImportStatus.visibility = View.GONE
                    }, 2500)
                    openPlayer(result)
                }
                else -> {
                    binding.tvImportStatus.text = "❌ Erreur. Vérifiez votre connexion."
                    Toast.makeText(requireContext(), "Erreur d'importation", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPlayer(sourateId: Long) {
        val fragment = PlayerFragment.newInstance(sourateId)
        // FIX BUILD : loadFragment(fragment) → loadFragment(fragment, tag)
        // MainActivity.loadFragment() exige maintenant un tag (fix navigation crash)
        (activity as? MainActivity)?.apply {
            loadFragment(fragment, PlayerFragment::class.java.simpleName)
            navigateTo(R.id.nav_player)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
