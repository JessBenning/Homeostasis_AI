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
import com.homeostasis.app.R
import androidx.fragment.app.viewModels
import android.app.AlertDialog
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import com.homeostasis.app.ui.task_history.TaskHistoryViewModel // Import TaskHistoryViewModel
import androidx.lifecycle.lifecycleScope
import com.homeostasis.app.ui.groups.AcceptInviteDialogFragment
import com.homeostasis.app.ui.groups.CreateGroupDialogFragment
import kotlinx.coroutines.launch
import com.homeostasis.app.ui.auth.AuthViewModel
import com.homeostasis.app.ui.groups.OnboardingDialogFragment.Companion.TAG


@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val profileSettingsViewModel: ProfileSettingsViewModel by viewModels() // Inject ProfileSettingsViewModel
    private val taskHistoryViewModel: TaskHistoryViewModel by viewModels() // Inject TaskHistoryViewModel
    private val authViewModel: AuthViewModel by viewModels()

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

        
                val settingsOptions = listOf(
                    SettingsListItem.Setting("Profile"),
                    SettingsListItem.Setting("Sign Out"),
                    SettingsListItem.Setting("Reset Scores and History"), // Keep Reset Scores option
                    SettingsListItem.Header("Group Settings"), // Group Settings Header
                    SettingsListItem.Setting("Create New Group"),
                    SettingsListItem.Setting("Invite Members"),
                    SettingsListItem.Setting("Accept Invite"), // Add Accept Invite option
 //                   SettingsListItem.Setting("Group Admin"),
//                    SettingsListItem.Setting("View Group Members"),
//                    SettingsListItem.Setting("Remove Member"), //not enabled for non owner
//                    SettingsListItem.Setting("Rename Group"), //not enabled for non owner
//                    SettingsListItem.Setting("Delete Group"), //not enabled for non owner
//                    SettingsListItem.Setting("Leave Group"), //not enabled for owner

                )
        
                val settingsAdapter = SettingsAdapter(settingsOptions) { settingName ->
                    when (settingName) {
                        "Profile" -> {

                            findNavController().navigate(R.id.navigation_profile)
                        }

                        "Sign Out" -> {
                            authViewModel.signOut()
                            try {
                                 findNavController().navigate(R.id.navigation_auth)
                            } catch (e: IllegalStateException) {
                                Log.e(TAG, "Error navigating to auth screen on logout: ${e.message}", e)
                            }
                        }

                        "Create New Group" -> {

                            findNavController().navigate(R.id.createGroupDialogFragment)
                        }
                        "Invite Members" -> {
                            // Assuming R.id.navigation_household_group is the correct destination for inviting members
                            findNavController().navigate(R.id.createInviteDialogFragment)

                        }
                        "Reset Scores and History" -> {
                            // Show confirmation dialog
                            AlertDialog.Builder(requireContext())
                                .setTitle("Reset Scores and History")
                                .setMessage("Are you sure you want to permanently delete all task history and reset scores? This action cannot be undone.")
                                .setPositiveButton("Reset") { dialog, _ ->
                                    taskHistoryViewModel.resetScoresAndHistory() // Call ViewModel function on TaskHistoryViewModel
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show()
                        }
                        "Accept Invite" -> {

                            findNavController().navigate(R.id.acceptInviteDialogFragment)
                        }
                    }
                }

                lifecycleScope.launch {
                    activity?.title = getString(R.string.settings_title)
                }

                settingsAdapter.fragmentManager = childFragmentManager
                settingsRecyclerView.adapter = settingsAdapter

                // Observe reset scores event from TaskHistoryViewModel
                viewLifecycleOwner.lifecycleScope.launch {
                    taskHistoryViewModel.resetScoresEvent.collect {
                        Snackbar.make(binding.root, "Task history and scores reset.", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }