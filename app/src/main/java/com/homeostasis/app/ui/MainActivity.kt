package com.homeostasis.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast
import com.homeostasis.app.R
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import com.homeostasis.app.data.UserDao // Import UserDao
import com.homeostasis.app.data.Constants // Import Constants
import com.homeostasis.app.ui.groups.CreateGroupDialogFragment // Import CreateGroupDialogFragment
import com.homeostasis.app.ui.settings.AcceptInviteDialogFragment // Import AcceptInviteDialogFragment
import androidx.lifecycle.lifecycleScope // Import lifecycleScope
import kotlinx.coroutines.launch // Import launch
import kotlinx.coroutines.flow.first // Import first
import javax.inject.Inject


/**
 * Main activity for the Homeostasis app.
 * Hosts the navigation components and bottom navigation.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userDao: UserDao // Inject UserDao


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Get the navigation graph
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph) // Assuming nav_graph is your navigation graph file

        // Check if user is signed in on app startup
        val currentUser = userRepository.getCurrentUser()

        if (currentUser == null) {
            // User is not signed in, set start destination to auth screen
            navGraph.setStartDestination(R.id.navigation_auth)
            navController.graph = navGraph // Set graph immediately for unauthenticated users
        } else {
            // User is signed in, check their household group ID
            lifecycleScope.launch {
                val userId = currentUser.uid
                val user = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()
                val householdGroupId = user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID

                if (householdGroupId == Constants.DEFAULT_GROUP_ID) {
                    // User is in the default group, force onboarding
                    // Set start destination to a placeholder or the onboarding fragment itself if you create one
                    // For now, we'll set it to tasks but immediately show the dialog
                    navGraph.setStartDestination(R.id.navigation_tasks) // Still set tasks as start, but dialog will overlay

                    // Show the onboarding dialog
                    showOnboardingDialog()

                } else {
                    // User is in a specific group, proceed to main content
                    navGraph.setStartDestination(R.id.navigation_tasks) // Assuming navigation_tasks is your main screen destination ID
                }

                // Set the modified graph to the navController after the check
                navController.graph = navGraph
            }
        }

        // Set up bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Configure app bar
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_tasks,
                R.id.navigation_task_history,
              //  R.id.navigation_shopping,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_tasks -> {
                    navController.navigate(R.id.navigation_tasks)
                    true
                }
                R.id.navigation_task_history -> {
                    navController.navigate(R.id.navigation_task_history)
                    true
                }
                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings)
                    true
                }
                else -> false
            }
        }
    }

   private fun showOnboardingDialog() {
       MaterialAlertDialogBuilder(this)
           .setTitle("Welcome!")
           .setMessage("It looks like you're not part of a group yet. Please join an existing group or create a new one to get started.")
           .setCancelable(false) // Prevent dismissing the dialog
           .setPositiveButton("Accept Invite") { dialog, which ->
               // Show Accept Invite DialogFragment
               AcceptInviteDialogFragment().show(supportFragmentManager, AcceptInviteDialogFragment.TAG)
           }
           .setNegativeButton("Create Group") { dialog, which ->
               // Show Create Group DialogFragment
               CreateGroupDialogFragment().show(supportFragmentManager, CreateGroupDialogFragment.TAG)
           }
           .show()
   }


   override fun onResume() {
       super.onResume()

       // Existing deep link handling - might need adjustment to work with the new onboarding flow
       val data = intent?.data
       if (data != null && data.scheme == "homeostasis" && data.host == "invite") {
           val groupId = data.getQueryParameter("groupId")
           if (groupId != null) {
               // Show dialog
               MaterialAlertDialogBuilder(this)
                   .setTitle("Invite to Group")
                   .setMessage("Do you want to join group $groupId?")
                   .setPositiveButton("Join") { dialog, which ->
                       // TODO: Implement join group logic
                       Toast.makeText(this, "Joining group $groupId", Toast.LENGTH_SHORT).show()
                   }
                   .setNegativeButton("Cancel", null)
                   .show()
           }
       }
   }

   override fun onStart() {
       super.onStart()
       // Authentication check moved to onCreate for initial startup
   }

   override fun onSupportNavigateUp(): Boolean {
       return navController.navigateUp() || super.onSupportNavigateUp()
   }
}