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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Configure app bar
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_tasks,
                R.id.navigation_task_history,
              //  R.id.navigation_shopping,
                R.id.navigation_profile,
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
                R.id.navigation_profile -> {
                    navController.navigate(R.id.navigation_profile)
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

    override fun onResume() {
        super.onResume()

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

        // Check if user is signed in
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            // User is not signed in, navigate to auth screen
            navController.navigate(R.id.navigation_auth)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}