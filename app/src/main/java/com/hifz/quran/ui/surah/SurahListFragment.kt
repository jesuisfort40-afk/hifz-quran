package com.hifz.quran.ui.surah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifz.quran.R
import com.hifz.quran.databinding.FragmentSurahListBinding
import com.hifz.quran.model.Sourate
import com.hifz.quran.MainActivity

class SurahListFragment : Fragment() {

    private var _binding: FragmentSurahListBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SurahViewModel
    private lateinit var adapter: SurahAdapter

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
            (activity as? MainActivity)?.apply {
                loadFragment(SurahBrowserFragment(), SurahBrowserFragment::class.java.simpleName)
            }
        }
    }

    private fun confirmDelete(sourate: Sourate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer")
            .setMessage("Supprimer \"${sourate.name}\" et tous ses versets ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.deleteSourate(sourate) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * FIX DOUBLE LECTURE : on passe par openPlayer() de MainActivity
     * qui supprime l'ancienne instance du PlayerFragment AVANT d'en créer une nouvelle.
     * Avant ce fix, deux instances de PlayerFragment coexistaient :
     * l'ancien continuait à jouer pendant que le nouveau démarrait → double lecture.
     */
    private fun openPlayer(sourate: Sourate) {
        (activity as? MainActivity)?.openPlayer(sourate.id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
