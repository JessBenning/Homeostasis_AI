package com.homeostasis.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.homeostasis.app.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import com.homeostasis.app.ui.settings.InviteDialogFragment
import com.homeostasis.app.R

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settingsRecyclerView = binding.settingsRecyclerView
        settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        
                val settingsOptions = listOf("Invite Members")
        
                val settingsAdapter = SettingsAdapter(settingsOptions) { settingName ->
                    when (settingName) {
                        "Invite Members" -> findNavController().navigate(R.id.navigation_household_group)
                    }
                }
                settingsAdapter.fragmentManager = childFragmentManager
        settingsRecyclerView.adapter = settingsAdapter
    }
}