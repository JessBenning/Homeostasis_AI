package com.homeostasis.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
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
                R.id.navigation_shopping,
                R.id.navigation_leaderboard,
                R.id.navigation_profile,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
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