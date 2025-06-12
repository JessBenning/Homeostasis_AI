package com.homeostasis.app.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Task
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dialog fragment for adding a new task.
 */
@AndroidEntryPoint
class AddTaskDialogFragment : DialogFragment() {

    interface AddTaskListener {
        fun onTaskAdded(task: Task)
    }

    private var listener: AddTaskListener? = null

    // UI components
    private lateinit var titleEditText: TextInputEditText
  //  private lateinit var descriptionEditText: TextInputEditText
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
        titleEditText = view.findViewById(R.id.task_title_edit_text)
       // descriptionEditText = view.findViewById(R.id.task_description_edit_text)
        pointsEditText = view.findViewById(R.id.task_points_edit_text)
        categoryDropdown = view.findViewById(R.id.task_category_dropdown)
        saveButton = view.findViewById(R.id.save_button)
        cancelButton = view.findViewById(R.id.cancel_button)

        //TODO
        // Set up category dropdown
        val categories = listOf("Household", "Work", "Personal", "Other") // Placeholder categories
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        categoryDropdown.setAdapter(adapter)

        // Set up click listeners
        saveButton.setOnClickListener {
            if (validateInputs()) {
                saveTask()
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
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

    private fun saveTask() {
        val title = titleEditText.text.toString().trim()
      //  val description = descriptionEditText.text.toString().trim()
        val points = pointsEditText.text.toString().toIntOrNull() ?: 0
        val category = categoryDropdown.text.toString().trim()

        // For now, create a simple Task object with just the basic info
        // In a real implementation, you would use the Task model with all required fields
        val task = Task(
            title = title,
            description = "",//"",
            points = points,
            categoryId = category // Using category name as ID for now
        )

        // Notify the listener
        listener?.onTaskAdded(task)

        // Show a success message
        Toast.makeText(requireContext(), "Task added successfully", Toast.LENGTH_SHORT).show()

        // Dismiss the dialog
        dismiss()
    }

//    fun setAddTaskListener(listener: AddTaskListener) {
//        this.listener = listener
//    }

    companion object {
        const val TAG = "AddTaskDialogFragment"

        fun newInstance(): AddTaskDialogFragment {
            return AddTaskDialogFragment()
        }
    }
}