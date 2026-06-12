package com.samyak.iptvminepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import android.content.Intent
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.samyak.iptvminepro.ui.screens.HomeScreen
import com.samyak.iptvminepro.ui.screens.PlayerScreen
import com.samyak.iptvminepro.ui.screens.CategoryScreen
import com.samyak.iptvminepro.ui.screens.CategoryDetailScreen
import com.samyak.iptvminepro.ui.screens.AboutScreen
import com.samyak.iptvminepro.ui.screens.ExtensionsScreen
import com.samyak.iptvminepro.ui.screens.MovieDetailScreen
import com.samyak.iptvminepro.ui.screens.CategoryMoviesScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samyak.iptvminepro.ui.screens.SettingsScreen
import com.samyak.iptvminepro.ui.screens.AddProviderScreen
import com.samyak.iptvminepro.ui.screens.ProviderListScreen
import com.samyak.iptvminepro.ui.theme.IPTVMineProTheme

import androidx.compose.material.icons.outlined.Movie

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVMineProTheme {
                MainApp()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "Home", { Icon(Icons.Outlined.Home, contentDescription = null) })
    object Movies : Screen("movies?category={category}", "Movies", { Icon(Icons.Outlined.Movie, contentDescription = null) })
    object Category : Screen("category", "Category", { Icon(Icons.Outlined.GridView, contentDescription = null) })
    object Settings : Screen("settings", "Settings", { Icon(Icons.Outlined.Settings, contentDescription = null) })
    object Player : Screen("player", "Player", { }) // Used for navigation but not in bottom bar
    object AddProvider : Screen("add_provider", "Add Provider", { })
    object ProviderList : Screen("provider_list", "Provider List", { })
    object CategoryDetail : Screen("category_detail/{categoryName}", "Category Detail", { })
    object About : Screen("about", "About App", { })
    object Extensions : Screen("extensions", "Extensions", { })
    object MovieDetail : Screen("movie_detail?link={link}&providerUrl={providerUrl}&scraperValue={scraperValue}", "Movie Detail", { })
    object CategoryMovies : Screen("category_movies?categoryName={categoryName}&categoryFilter={categoryFilter}&providerUrl={providerUrl}&scraperValue={scraperValue}", "Category Movies", { })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Movies,
        Screen.Category,
        Screen.Settings
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = androidx.compose.runtime.remember { com.samyak.iptvminepro.provider.ProviderRepository(context) }
    val channelsViewModel: com.samyak.iptvminepro.provider.ChannelsProvider = viewModel()
    val startDestination = if (repository.getProviders().isEmpty()) Screen.AddProvider.route else Screen.Home.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != Screen.AddProvider.route &&
                currentRoute != Screen.ProviderList.route &&
                currentRoute != Screen.CategoryDetail.route &&
                currentRoute != Screen.MovieDetail.route &&
                currentRoute != Screen.About.route &&
                currentRoute != Screen.CategoryMovies.route
            ) {
                NavigationBar(
                    containerColor = Color.White // White background
                ) {
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF26A69A), // Teal color
                                selectedTextColor = Color(0xFF26A69A),
                                unselectedIconColor = Color(0xFF6B7280), // Gray
                                unselectedTextColor = Color(0xFF6B7280),
                                indicatorColor = Color.Transparent // Removes the pill/circle indicator
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val categoryName = navBackStackEntry?.arguments?.getString("categoryName")
            
            if (currentRoute != null && 
                currentRoute != Screen.MovieDetail.route &&
                currentRoute != Screen.CategoryMovies.route
            ) {
                TopAppBar(
                    title = {
                        val title = when (currentRoute) {
                            Screen.ProviderList.route -> "Manage Providers"
                            Screen.AddProvider.route -> "Add Provider"
                            Screen.About.route -> "About App"
                            Screen.CategoryDetail.route -> categoryName ?: "Category"
                            else -> "IPTV Mine Pro"
                        }
                        Text(title)
                    },
                    navigationIcon = {
                        if (currentRoute == Screen.ProviderList.route ||
                            currentRoute == Screen.AddProvider.route ||
                            currentRoute == Screen.CategoryDetail.route ||
                            currentRoute == Screen.About.route
                        ) {
                            FilledIconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentRoute != Screen.ProviderList.route &&
                            currentRoute != Screen.AddProvider.route &&
                            currentRoute != Screen.CategoryDetail.route &&
                            currentRoute != Screen.About.route
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            IconButton(onClick = { 
                                context.startActivity(Intent(context, SearchActivity::class.java)) 
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                HomeScreen(
                    viewModel = channelsViewModel,
                    navController = navController,
                    onChannelClick = { channel ->
                        if (channel.streamUrl.isEmpty()) {
                            android.widget.Toast.makeText(context, "This match is not live yet!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                        }
                    }
                ) 
            }
            composable(
                route = Screen.Movies.route,
                arguments = listOf(navArgument("category") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                com.samyak.iptvminepro.ui.screens.MoviesScreen(
                    initialCategoryTitle = category,
                    onMovieClick = { post, scraper, provider ->
                        val encodedLink = android.net.Uri.encode(post.link)
                        val encodedProviderUrl = android.net.Uri.encode(provider.url)
                        val scraperValue = scraper.value
                        navController.navigate("movie_detail?link=$encodedLink&providerUrl=$encodedProviderUrl&scraperValue=$scraperValue")
                    }
                )
            }
            composable(Screen.Category.route) {
                CategoryScreen(
                    viewModel = channelsViewModel,
                    onCategoryClick = { category ->
                        val encodedCategory = android.net.Uri.encode(category)
                        navController.navigate("category_detail/$encodedCategory")
                    }
                )
            }
            composable(
                route = Screen.CategoryDetail.route,
                arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                CategoryDetailScreen(
                    viewModel = channelsViewModel,
                    categoryName = categoryName
                )
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    onNavigateToProviders = { navController.navigate(Screen.ProviderList.route) },
                    onNavigateToExtensions = { navController.navigate(Screen.Extensions.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) }
                ) 
            }
            composable(
                route = Screen.MovieDetail.route,
                arguments = listOf(
                    navArgument("link") { type = NavType.StringType },
                    navArgument("providerUrl") { type = NavType.StringType },
                    navArgument("scraperValue") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val link = backStackEntry.arguments?.getString("link") ?: ""
                val providerUrl = backStackEntry.arguments?.getString("providerUrl") ?: ""
                val scraperValue = backStackEntry.arguments?.getString("scraperValue") ?: ""
                MovieDetailScreen(
                    link = link,
                    providerUrl = providerUrl,
                    scraperValue = scraperValue,
                    navController = navController
                )
            }
            composable(
                route = Screen.CategoryMovies.route,
                arguments = listOf(
                    navArgument("categoryName") { type = NavType.StringType },
                    navArgument("categoryFilter") { type = NavType.StringType },
                    navArgument("providerUrl") { type = NavType.StringType },
                    navArgument("scraperValue") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                val categoryFilter = backStackEntry.arguments?.getString("categoryFilter") ?: ""
                val providerUrl = backStackEntry.arguments?.getString("providerUrl") ?: ""
                val scraperValue = backStackEntry.arguments?.getString("scraperValue") ?: ""
                CategoryMoviesScreen(
                    categoryName = categoryName,
                    categoryFilter = categoryFilter,
                    providerUrl = providerUrl,
                    scraperValue = scraperValue,
                    navController = navController,
                    onMovieClick = { post ->
                        val encodedLink = android.net.Uri.encode(post.link)
                        val encodedProviderUrl = android.net.Uri.encode(providerUrl)
                        navController.navigate("movie_detail?link=$encodedLink&providerUrl=$encodedProviderUrl&scraperValue=$scraperValue")
                    }
                )
            }
            composable(Screen.About.route) { AboutScreen() }
            composable(Screen.Extensions.route) { 
                ExtensionsScreen(onNavigateBack = { navController.popBackStack() }) 
            }
            composable(Screen.Player.route) { PlayerScreen() }
            composable(Screen.ProviderList.route) {
                ProviderListScreen(
                    viewModel = channelsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onAddProvider = { navController.navigate(Screen.AddProvider.route) }
                )
            }
            composable(Screen.AddProvider.route) {
                AddProviderScreen(
                    onProviderAdded = {
                        channelsViewModel.fetchM3UFile()
                        if (navController.previousBackStackEntry == null) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.AddProvider.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onNavigateBack = if (navController.previousBackStackEntry != null) {
                        { navController.popBackStack() }
                    } else null
                )
            }
        }
    }
}
