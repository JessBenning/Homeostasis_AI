package com.homeostasis.app.ui.task_history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R // Make sure this R import is correct
import com.homeostasis.app.databinding.FragmentTaskHistoryBinding // Using View Binding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskHistoryFragment : Fragment() {

    // Using View Binding for safer view access
    private var _binding: FragmentTaskHistoryBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    private val viewModel: TaskHistoryViewModel by viewModels()
    private lateinit var taskHistoryAdapter: TaskHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        taskHistoryAdapter = TaskHistoryAdapter() // Initialize the adapter
        binding.taskHistoryRecyclerView.apply { // Assuming your RecyclerView's ID is 'taskHistoryRecyclerView'
            adapter = taskHistoryAdapter
            layoutManager = LinearLayoutManager(context)
            // You can also add ItemDecoration here if needed (e.g., for dividers)
            // addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
        Log.d("TaskHistoryFragment", "RecyclerView setup complete.")
    }

    private fun observeViewModel() {
        // Use viewLifecycleOwner.lifecycleScope for UI-related coroutines
        // Use repeatOnLifecycle to ensure collection stops when the view is destroyed
        // and restarts when it's created again.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.taskHistoryItems.collect { feedItems ->
                    if (feedItems.isNotEmpty()) {
                        Log.d("TaskHistoryFragment", "Observed ${feedItems.size} feed items. First item: ${feedItems.firstOrNull()}")
                        taskHistoryAdapter.submitList(feedItems)
                        binding.taskHistoryRecyclerView.visibility = View.VISIBLE
                        binding.emptyViewTaskHistory.visibility = View.GONE // Assuming you have an empty view
                    } else {
                        Log.d("TaskHistoryFragment", "Observed empty feed items.")
                        binding.taskHistoryRecyclerView.visibility = View.GONE
                        binding.emptyViewTaskHistory.visibility = View.VISIBLE
                    }
                }
            }
        }



        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoadingValue: Boolean -> // Explicit type
                    binding.progressBarTaskHistory.visibility = if (isLoadingValue) View.VISIBLE else View.GONE
                    Log.d("TaskHistoryFragment", "isLoading state: $isLoadingValue")
                }
            }
        }

        // Observe error messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { errorMessage: String? -> // Explicit type
                    if (errorMessage != null) {
                        Log.e("TaskHistoryFragment", "Error: $errorMessage")
                        com.google.android.material.snackbar.Snackbar.make(binding.root, errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                        viewModel.clearError() // Add this method in ViewModel
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.taskHistoryRecyclerView.adapter = null // Important to clear adapter to avoid memory leaks with RecyclerView
        _binding = null // Clear the binding reference
        Log.d("TaskHistoryFragment", "View destroyed, binding cleared.")
    }
}

