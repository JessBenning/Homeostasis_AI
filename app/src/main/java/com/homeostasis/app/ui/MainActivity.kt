package com.homeostasis.app.ui // Your package

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.homeostasis.app.data.Constants // Assuming this is where DEFAULT_GROUP_ID is
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.data.sync.FirebaseSyncManager // Assuming needed for other parts
import com.homeostasis.app.data.sync.RemoteToLocalSyncHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.homeostasis.app.R

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView // Class member for BottomNav
    private lateinit var navGraph: NavGraph // Keep a reference to the inflated graph

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager

    @Inject
    lateinit var remoteToLocalSyncHandler: RemoteToLocalSyncHandler

    // Define your authentication destination IDs (and other screens that hide bottom nav)
    private val destinationsWithoutBottomNav = setOf(
        R.id.navigation_auth, // Your primary auth destination/graph
        R.id.onboardingDialogFragment // Onboarding also hides it
        // Add any other full-screen or auth-related fragment IDs here
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize NavController and BottomNavigationView
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        bottomNavigationView = findViewById(R.id.bottom_navigation) // Use the ID from your layout

        // Inflate the navigation graph once
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        // 2. Setup Destination Change Listener for BottomNav Visibility (do this early)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destinationsWithoutBottomNav.contains(destination.id)) {
                bottomNavigationView.visibility = View.GONE
                Log.d("MainActivity", "Hiding BottomNav for ${destination.label}")
            } else {
                bottomNavigationView.visibility = View.VISIBLE
                Log.d("MainActivity", "Showing BottomNav for ${destination.label}")
            }
        }

        // 3. Handle Initial Navigation and Graph Setup
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            Log.d("MainActivity", "User not authenticated. Setting start destination to Auth screen.")
            setupGraphForUnauthenticatedUser()
            setupMainUiComponents() // Setup UI after graph is set
        } else {
            Log.d("MainActivity", "User authenticated (UID: ${currentUser.uid}). Processing user data and navigation.")
            processAuthenticatedUser(currentUser.uid)
            // setupMainUiComponents() will be called from within processAuthenticatedUser
            // after async operations and graph setup are complete.
        }
    }

    private fun setupGraphForUnauthenticatedUser() {
        navGraph.setStartDestination(R.id.navigation_auth)
        navController.graph = navGraph
    }

    private fun processAuthenticatedUser(userId: String) {
        lifecycleScope.launch {
            // STEP A: Sync remote user data (simplified for brevity, keep your detailed logging)
            Log.d("MainActivity", "Starting sync for user $userId")
            val syncedUser = remoteToLocalSyncHandler.fetchAndCacheRemoteUser(userId)
            if (syncedUser?.householdGroupId?.takeIf { it.isNotEmpty() }?.let { it != Constants.DEFAULT_GROUP_ID } == true) {
                remoteToLocalSyncHandler.fetchAndCacheGroupById(userId, syncedUser.householdGroupId!!)
            }

            // STEP B: Get local user for navigation decision
            val localUserForNav = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()
            val householdGroupIdForNav = localUserForNav?.householdGroupId?.takeIf { it.isNotEmpty() }
                ?: Constants.DEFAULT_GROUP_ID
            Log.d("MainActivity", "Local user's householdGroupId for nav: $householdGroupIdForNav")

            // STEP C: Set graph and navigate if needed
            if (householdGroupIdForNav == Constants.DEFAULT_GROUP_ID) {
                Log.d("MainActivity", "User authenticated but no group. Navigating to onboarding.")
                navGraph.setStartDestination(R.id.navigation_tasks) // Base is tasks
                navController.graph = navGraph
                navigateToOnboardingIfNeeded()
            } else {
                Log.d("MainActivity", "User authenticated and has group '$householdGroupIdForNav'. Tasks screen is primary.")
                navGraph.setStartDestination(R.id.navigation_tasks)
                navController.graph = navGraph
                navigateToTasksFromAuthIfNeeded()
            }

            // IMPORTANT: Setup UI components after all graph decisions and potential navigations
            // within this authenticated flow are done.
            setupMainUiComponents()
        }
    }

    private fun navigateToOnboardingIfNeeded() {
        if (navController.currentDestination?.id != R.id.onboardingDialogFragment &&
            navController.currentDestination?.id != R.id.navigation_auth
        ) {
            try {
                navController.navigate(R.id.onboardingDialogFragment)
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation to onboardingDialogFragment failed.", e)
            }
        } else {
            Log.d("MainActivity", "Already at/navigating to onboarding or auth. No redundant nav from Main.")
        }
    }

    private fun navigateToTasksFromAuthIfNeeded() {
        if (navController.currentDestination?.id == R.id.navigation_auth) {
            Log.d("MainActivity", "Current destination is Auth, navigating to Tasks and popping Auth.")
            navController.navigate(
                R.id.navigation_tasks, null,
                NavOptions.Builder().setPopUpTo(R.id.navigation_auth, true).build()
            )
        }
    }

    // Helper function to setup BottomNav and ActionBar
    private fun setupMainUiComponents() {
        // Ensure graph is actually set before proceeding.
        // This check is a safeguard; by this point, the graph should be set.
        if (navController.graph.nodes.isEmpty()) {
            Log.e("MainActivity", "Cannot setup Main UI: NavController graph is empty or not yet initialized.")
            // Consider a retry mechanism if this happens unexpectedly, though the flow should prevent it.
            // For example, post to run later:
            // bottomNavigationView.post { setupMainUiComponents() }
            return
        }

        Log.d("MainActivity", "Executing setupMainUiComponents. Graph Start: ${navController.graph.findNode(navController.graph.startDestinationId)?.label}")

        // Setup BottomNavigationView with NavController
        bottomNavigationView.setupWithNavController(navController)

        // Setup ActionBar
        val appBarConfiguration = AppBarConfiguration(
            // Define top-level destinations for AppBar (screens that don't show Up button)
            // These should NOT include your auth screens if you use a separate toolbar there
            // or if the main ActionBar should not be present on auth screens.
            setOf(
                R.id.navigation_tasks,
                R.id.navigation_task_history,
                R.id.navigation_auth
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Your custom OnItemSelectedListener for BottomNavigationView (if needed beyond default behavior)
        // Note: setupWithNavController handles basic navigation.
        // This is for custom behavior or if you're not using standard menu item IDs for navigation.
        bottomNavigationView.setOnItemSelectedListener { item ->
            val builder = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true) // Good for bottom nav
                // Decide if you want to pop up to the start destination of the graph
                // This prevents a deep back stack when switching tabs.
                .setPopUpTo(navController.graph.findNode(navController.graph.startDestinationId)!!.id, false)


            val options = builder.build()
            try {
                when (item.itemId) {
                    R.id.navigation_tasks -> {
                        navController.navigate(R.id.navigation_tasks, null, options)
                        true
                    }
                    R.id.navigation_task_history -> {
                        navController.navigate(R.id.navigation_task_history, null, options)
                        true
                    }
                    R.id.navigation_settings -> {
                        navController.navigate(R.id.navigation_settings, null, options)
                        true
                    }
                    // Add other items if any
                    else -> false
                }
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "Navigation failed for item ${item.title}: ${e.message}")
                // Handle cases where the destination might not be part of the current graph,
                // though with a single graph setup this should be less common.
                false
            }
        }
        Log.d("MainActivity", "Main UI components (BottomNav, ActionBar) setup complete.")
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}