package com.homeostasis.app.ui.tasks

import com.homeostasis.app.data.model.Task

/**
 * A wrapper data class to hold a Task and its dynamically derived display properties.
 */
data class DisplayTask(
    val task: Task,
    val lastCompletedByDisplay: String?,    // User name or "N/A"
    val lastCompletedDateDisplay: String?, // Formatted date string or null
)