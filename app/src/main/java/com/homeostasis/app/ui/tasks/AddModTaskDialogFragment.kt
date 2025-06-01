package com.homeostasis.app.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Task
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dialog fragment for adding or modifying a task.
 */
@AndroidEntryPoint
class AddModTaskDialogFragment : DialogFragment() {

    interface AddModTaskListener {
        fun onTaskAdded(task: Task)
        fun onTaskModified(task: Task)
    }

    private val viewModel: TaskListViewModel by viewModels()
    private var listener: AddModTaskListener? = null
    private var existingTask: Task? = null

    // UI components
    private lateinit var dialogTitle: TextView
    private lateinit var titleEditText: TextInputEditText
    private lateinit var descriptionEditText: TextInputEditText
    private lateinit var pointsEditText: TextInputEditText
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var saveButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        dialogTitle = view.findViewById(R.id.dialog_title)
        titleEditText = view.findViewById(R.id.task_title_edit_text)
        descriptionEditText = view.findViewById(R.id.task_description_edit_text)
        pointsEditText = view.findViewById(R.id.task_points_edit_text)
        categoryDropdown = view.findViewById(R.id.task_category_dropdown)
        saveButton = view.findViewById(R.id.save_button)
        cancelButton = view.findViewById(R.id.cancel_button)

        // Set up category dropdown
        val categories = listOf("Household", "Work", "Personal", "Other") // Placeholder categories
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        categoryDropdown.setAdapter(adapter)

        // Set up UI based on whether we're adding or editing a task
        setupUI()

        // Set up click listeners
        saveButton.setOnClickListener {
            if (validateInputs()) {
                if (existingTask == null) {
                    saveNewTask()
                } else {
                    updateExistingTask()
                }
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupUI() {
        val isEditMode = existingTask != null

        // Set dialog title
        dialogTitle.text = if (isEditMode) getString(R.string.task_edit) else getString(R.string.task_new)

        // Set save button text
        saveButton.text = if (isEditMode) getString(R.string.edit) else getString(R.string.save)

        // Populate fields if editing an existing task
        if (isEditMode && existingTask != null) {
            titleEditText.setText(existingTask!!.title)
            descriptionEditText.setText(existingTask!!.description)
            pointsEditText.setText(existingTask!!.points.toString())
            categoryDropdown.setText(existingTask!!.categoryId)
        }
    }

    private fun validateInputs(): Boolean {
        val title = titleEditText.text.toString().trim()
        val points = pointsEditText.text.toString().trim()
        val category = categoryDropdown.text.toString().trim()

        if (title.isEmpty()) {
            titleEditText.error = "Title is required"
            return false
        }

        if (points.isEmpty()) {
            pointsEditText.error = "Points are required"
            return false
        }

        if (category.isEmpty()) {
            categoryDropdown.error = "Category is required"
            return false
        }

        return true
    }

    private fun saveNewTask() {
        val title = titleEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val points = pointsEditText.text.toString().toIntOrNull() ?: 0
        val category = categoryDropdown.text.toString().trim()

        // Create a new Task object
        val task = Task(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            description = description,
            points = points,
            categoryId = category, // Using category name as ID for now
            createdBy = "current_user", // In a real app, this would come from authentication
            createdAt = Timestamp.now(),
            lastModifiedAt = Timestamp.now()
        )

        // Add the task to ViewModel
        viewModel.addTask(task)

        // Notify the listener
        listener?.onTaskAdded(task)

        // Show a success message
        Toast.makeText(requireContext(), "Task added successfully", Toast.LENGTH_SHORT).show()

        // Dismiss the dialog
        dismiss()
    }

    private fun updateExistingTask() {
        val title = titleEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val points = pointsEditText.text.toString().toIntOrNull() ?: 0
        val category = categoryDropdown.text.toString().trim()

        // Make sure we have an existing task to update
        if (existingTask == null) {
            Toast.makeText(requireContext(), "Error: No task to update", Toast.LENGTH_SHORT).show()
            return
        }

        // Create an updated Task object, preserving the original ID and other fields
        val updatedTask = existingTask!!.copy(
            title = title,
            description = description,
            points = points,
            categoryId = category,
            lastModifiedAt = Timestamp.now()
            // All other fields (id, createdBy, createdAt, completion history, etc.) are preserved
        )

        // Update the task in ViewModel
        viewModel.updateTask(updatedTask)

        // Notify the listener
        listener?.onTaskModified(updatedTask)

        // Show a success message
        Toast.makeText(requireContext(), "Task updated successfully", Toast.LENGTH_SHORT).show()

        // Dismiss the dialog
        dismiss()
    }

    fun setAddModTaskListener(listener: AddModTaskListener) {
        this.listener = listener
    }

    companion object {
        const val TAG = "AddModTaskDialogFragment"

        fun newInstance(): AddModTaskDialogFragment {
            return AddModTaskDialogFragment()
        }

        fun newInstance(task: Task): AddModTaskDialogFragment {
            return AddModTaskDialogFragment().apply {
                existingTask = task
            }
        }
    }
}