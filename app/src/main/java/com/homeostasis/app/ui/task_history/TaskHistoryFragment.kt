package com.homeostasis.app.ui.task_history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R
import com.homeostasis.app.data.AppDatabase
import com.homeostasis.app.data.model.TaskHistory
import com.homeostasis.app.data.model.UserScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp

class TaskHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var taskHistoryAdapter: TaskHistoryAdapter
    private lateinit var appDatabase: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
                val view = inflater.inflate(R.layout.fragment_task_history, container, false)
                recyclerView = view.findViewById(R.id.task_history_recycler_view)
                recyclerView.layoutManager = LinearLayoutManager(context)

                // Initialize AppDatabase (replace with your actual database initialization)
                appDatabase = AppDatabase.getDatabase(requireContext())

        
                taskHistoryAdapter = TaskHistoryAdapter(mutableListOf(), appDatabase)
                recyclerView.adapter = taskHistoryAdapter
        
                return view
            }
        
            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)
        
                recyclerView.adapter = taskHistoryAdapter
                super.onViewCreated(view, savedInstanceState)
        
                // Load data from database and update the adapter
                loadTaskHistory()
            }
        
            private fun loadTaskHistory() {
                CoroutineScope(Dispatchers.Main).launch {
                    val taskHistory = withContext(Dispatchers.IO) {
                        appDatabase.taskHistoryDao().getAllTaskHistory()
                    }
        
                    val dataSet = mutableListOf<Any>()
                    // Create dummy data for testing
//                    dataSet.add(UserScore("1", "John Doe", "https://example.com/john.jpg", 100))
//                    dataSet.add(TaskHistory("1", "Task 1", "User 1", Timestamp.now(), 10))
//
//                    dataSet.add(UserScore("2", "Jane Smith", "https://example.com/jane.jpg", 120))
//                    dataSet.add(TaskHistory("2", "Task 2", "User 2", Timestamp.now(), 20))

                    dataSet.addAll(getUserScores())
                    val taskHistoryList = mutableListOf<TaskHistory>()
                    taskHistory.collect { taskHistoryList.addAll(it) }
                    dataSet.addAll(taskHistoryList)
        
                    taskHistoryAdapter.dataSet.clear()
                    taskHistoryAdapter.dataSet.addAll(dataSet)
                    taskHistoryAdapter.notifyDataSetChanged()
                }
            }
    private fun getUserScores(): List<UserScore> {
        // Replace with actual logic to fetch user scores
        return listOf(
            UserScore("1", "John Doe", "https://example.com/john.jpg", 100),
            UserScore("2", "Jane Smith", "https://example.com/jane.jpg", 120)
        )
    }
}