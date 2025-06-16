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
import androidx.fragment.app.viewModels // Import viewModels
import com.homeostasis.app.ui.profile.ProfileSettingsViewModel // Import ProfileSettingsViewModel
import android.app.AlertDialog // Import AlertDialog
import com.google.android.material.snackbar.Snackbar // Import Snackbar
import androidx.lifecycle.lifecycleScope // Import lifecycleScope
import kotlinx.coroutines.launch // Import launch for coroutine
import kotlinx.coroutines.flow.collect // Import collect for Flow

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val profileSettingsViewModel: ProfileSettingsViewModel by viewModels() // Inject ProfileSettingsViewModel

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

        
                val settingsOptions = listOf("Profile","Invite Members", "Reset Scores and History") // Add Reset option
        
                val settingsAdapter = SettingsAdapter(settingsOptions) { settingName ->
                    when (settingName) {
                        "Profile" -> {

                            findNavController().navigate(R.id.navigation_profile)
                        }
                        "Invite Members" -> {
                            // Assuming R.id.navigation_household_group is the correct destination for inviting members
                            findNavController().navigate(R.id.navigation_household_group)
                        }
                        "Reset Scores and History" -> {
                            // Show confirmation dialog
                            AlertDialog.Builder(requireContext())
                                .setTitle("Reset Scores and History")
                                .setMessage("Are you sure you want to permanently delete all task history and reset scores? This action cannot be undone.")
                                .setPositiveButton("Reset") { dialog, _ ->
                                    profileSettingsViewModel.resetScoresAndHistory() // Call ViewModel function
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show()
                        }
                    }
                }
                settingsAdapter.fragmentManager = childFragmentManager
                settingsRecyclerView.adapter = settingsAdapter

                // Observe reset scores event from ProfileSettingsViewModel
                viewLifecycleOwner.lifecycleScope.launch {
                    profileSettingsViewModel.resetScoresEvent.collect {
                        Snackbar.make(binding.root, "Task history and scores reset.", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }