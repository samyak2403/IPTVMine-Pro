package com.samyak.iptvminepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import android.content.Intent
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
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
import com.samyak.iptvminepro.ui.screens.home.HomeScreen
import com.samyak.iptvminepro.ui.screens.common.PlayerScreen
import com.samyak.iptvminepro.ui.screens.tv.CategoryScreen
import com.samyak.iptvminepro.ui.screens.tv.CategoryDetailScreen
import com.samyak.iptvminepro.ui.screens.settings.AboutScreen
import com.samyak.iptvminepro.ui.screens.settings.BugReportScreen
import com.samyak.iptvminepro.ui.screens.settings.ExtensionsScreen
import com.samyak.iptvminepro.ui.screens.movies.MovieDetailScreen
import com.samyak.iptvminepro.ui.screens.movies.CategoryMoviesScreen
import com.samyak.iptvminepro.ui.screens.movies.MovieSearchScreen
import com.samyak.iptvminepro.ui.screens.movies.MoviesScreen
import com.samyak.iptvminepro.ui.screens.settings.DownloadsScreen
import com.samyak.iptvminepro.ui.screens.settings.WatchHistoryScreen
import com.samyak.iptvminepro.ui.screens.settings.LegalDocumentScreen
import com.samyak.iptvminepro.ui.screens.common.NoInternetScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samyak.iptvminepro.ui.screens.settings.SettingsScreen
import com.samyak.iptvminepro.ui.screens.provider.AddProviderScreen
import com.samyak.iptvminepro.ui.screens.provider.ProviderListScreen
import com.samyak.iptvminepro.ui.screens.settings.PairingScreen
import com.samyak.iptvminepro.ui.theme.IPTVMineProTheme
import com.samyak.iptvminepro.ui.viewmodel.MoviesViewModel
import com.samyak.iptvminepro.ui.viewmodel.HomeViewModel

import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Info
import com.samyak.iptvminepro.ui.screens.tv.TelevisionScreen
import com.samyak.iptvminepro.ui.screens.provider.AddProviderHelpScreen
import android.content.Context
import androidx.compose.material3.MaterialTheme
import com.psoffritti.taptargetcompose.TapTargetCoordinator
import com.psoffritti.taptargetcompose.TapTargetStyle
import com.psoffritti.taptargetcompose.TextDefinition


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.samyak.iptvminepro.download.DownloadManager.init(applicationContext)
        setContent {
            IPTVMineProTheme {
                MainApp()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "Home", { Icon(painterResource(id = R.drawable.ic_home), contentDescription = null) })
    object Television : Screen("television", "Television", { Icon(Icons.Outlined.Tv, contentDescription = null) })
    object Movies : Screen("movies?category={category}", "Movies", { Icon(Icons.Outlined.Movie, contentDescription = null) })
    object Category : Screen("category", "Category", { Icon(painterResource(id = R.drawable.ic_category), contentDescription = null) })
    object Settings : Screen("settings", "Settings", { Icon(painterResource(id = R.drawable.ic_settings), contentDescription = null) })
    object Player : Screen("player", "Player", { }) // Used for navigation but not in bottom bar
    object AddProvider : Screen("add_provider", "Add Provider", { })
    object AddProviderHelp : Screen("add_provider_help", "Add Provider Sources", { })
    object ProviderList : Screen("provider_list", "Provider List", { })
    object CategoryDetail : Screen("category_detail/{categoryName}", "Category Detail", { })
    object About : Screen("about", "About App", { })
    object Extensions : Screen("extensions", "Extensions", { })
    object MovieDetail : Screen("movie_detail?link={link}&providerUrl={providerUrl}&scraperValue={scraperValue}", "Movie Detail", { })
    object CategoryMovies : Screen("category_movies?categoryName={categoryName}&categoryFilter={categoryFilter}&providerUrl={providerUrl}&scraperValue={scraperValue}", "Category Movies", { })
    object MovieSearch : Screen("movie_search", "Movie Search", { })
    object Downloads : Screen("downloads", "Downloads", { })
    object BugReport : Screen("bug_report", "Report Bug", { })
    object WatchHistory : Screen("watch_history", "Watch History", { })
    object Legal : Screen("legal?docType={docType}", "Legal Information", { })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var firstTimeHelp by remember { mutableStateOf(sharedPrefs.getBoolean("first_time_add_provider_help", true)) }
    val showHelpTapTarget = firstTimeHelp && currentRoute == Screen.AddProvider.route

    var onWatchHistoryClearClick by remember { mutableStateOf<(() -> Unit)?>(null) }
    val items = listOf(
        Screen.Home,
        Screen.Television,
        Screen.Movies,
        Screen.Category,
        Screen.Settings
    )
    val repository = remember { com.samyak.iptvminepro.provider.ProviderRepository(context) }
    val channelsViewModel: com.samyak.iptvminepro.provider.ChannelsProvider = viewModel()
    // Activity-scoped: survives ALL navigation including back from MovieDetail
    val moviesViewModel: MoviesViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val startDestination = if (repository.getProviders().isEmpty()) Screen.AddProvider.route else Screen.Home.route

    val isConnected by remember(context) {
        com.samyak.iptvminepro.utils.NetworkUtils.observeConnectivity(context)
    }.collectAsState(initial = com.samyak.iptvminepro.utils.NetworkUtils.isNetworkAvailable(context))

    if (!isConnected) {
        NoInternetScreen(
            onRetry = {
                if (!com.samyak.iptvminepro.utils.NetworkUtils.isNetworkAvailable(context)) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.msg_still_offline),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    } else {
        TapTargetCoordinator(
            showTapTargets = showHelpTapTarget,
            onComplete = {
                sharedPrefs.edit().putBoolean("first_time_add_provider_help", false).apply()
                firstTimeHelp = false
            }
        ) {
            val tapTargetScope = this
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != Screen.AddProvider.route &&
                currentRoute != Screen.AddProviderHelp.route &&
                currentRoute != Screen.ProviderList.route &&
                currentRoute != Screen.CategoryDetail.route &&
                currentRoute != Screen.MovieDetail.route &&
                currentRoute != Screen.About.route &&
                currentRoute != Screen.CategoryMovies.route &&
                currentRoute != Screen.Extensions.route &&
                currentRoute != Screen.MovieSearch.route &&
                currentRoute != Screen.Downloads.route &&
                currentRoute != Screen.BugReport.route &&
                currentRoute != Screen.WatchHistory.route &&
                currentRoute != Screen.Legal.route &&
                currentRoute != "pairing"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    // Thin top border/divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(72.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                            val scale by animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "SelectedIndicatorScale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (!selected) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (scale > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(scale)
                                            .clip(CircleShape)
                                            .background(Color(0xFF26A69A))
                                    )
                                }

                                CompositionLocalProvider(
                                    LocalContentColor provides (if (selected) Color.White else Color(0xFF6B7280))
                                ) {
                                    val iconScale = if (selected) 1.05f else 1f
                                    Box(modifier = Modifier.scale(iconScale)) {
                                        screen.icon()
                                    }
                                }
                            }
                        }
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
                currentRoute != Screen.CategoryMovies.route &&
                currentRoute != Screen.Extensions.route &&
                currentRoute != Screen.MovieSearch.route
            ) {
                TopAppBar(
                    title = {
                        val title = when (currentRoute) {
                            Screen.Television.route -> "Television"
                            Screen.ProviderList.route -> "Manage Providers"
                            Screen.AddProvider.route -> "Add Provider"
                            Screen.AddProviderHelp.route -> "Add Provider Sources"
                            Screen.About.route -> "About App"
                            "pairing" -> "TV Pairing"
                            Screen.CategoryDetail.route -> categoryName ?: "Category"
                            Screen.Downloads.route -> "Downloads"
                            Screen.BugReport.route -> "Report Bug"
                            Screen.WatchHistory.route -> "Watch History"
                            Screen.Legal.route -> {
                                val docType = navBackStackEntry?.arguments?.getString("docType") ?: "privacy"
                                when (docType) {
                                    "privacy" -> "Privacy Policy"
                                    "terms" -> "Terms & Conditions"
                                    "disclaimer" -> "Disclaimer"
                                    else -> "Legal Information"
                                }
                            }
                            else -> "IPTV Mine Pro"
                        }
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    },
                    navigationIcon = {
                        if (currentRoute == Screen.ProviderList.route ||
                            currentRoute == Screen.AddProvider.route ||
                            currentRoute == Screen.AddProviderHelp.route ||
                            currentRoute == Screen.CategoryDetail.route ||
                            currentRoute == Screen.About.route ||
                            currentRoute == Screen.Downloads.route ||
                            currentRoute == Screen.BugReport.route ||
                            currentRoute == Screen.WatchHistory.route ||
                            currentRoute == Screen.Legal.route ||
                            currentRoute == "pairing"
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
                        if (currentRoute == Screen.WatchHistory.route) {
                            IconButton(onClick = { onWatchHistoryClearClick?.invoke() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = Color.White)
                            }
                        } else if (currentRoute == Screen.ProviderList.route) {
                            IconButton(onClick = { navController.navigate("pairing") }) {
                                Icon(Icons.Outlined.Tv, contentDescription = "Pair with TV", tint = Color.White)
                            }
                        } else if (currentRoute == Screen.AddProvider.route) {
                            IconButton(
                                onClick = { navController.navigate(Screen.AddProviderHelp.route) },
                                modifier = with(tapTargetScope) {
                                    Modifier.tapTarget(
                                        precedence = 0,
                                        title = TextDefinition(
                                            text = "Need Help adding providers?",
                                            textStyle = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        ),
                                        description = TextDefinition(
                                            text = "Tap here to see step-by-step instructions and copy default provider source links.",
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        ),
                                        tapTargetStyle = TapTargetStyle(
                                            backgroundColor = Color(0xFF26A69A),
                                            tapTargetHighlightColor = Color.White,
                                            backgroundAlpha = 0.95f
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = "Help", tint = Color.White)
                            }
                        } else if (currentRoute != Screen.ProviderList.route &&
                            currentRoute != Screen.AddProvider.route &&
                            currentRoute != Screen.AddProviderHelp.route &&
                            currentRoute != Screen.CategoryDetail.route &&
                            currentRoute != Screen.About.route &&
                            currentRoute != Screen.Downloads.route &&
                            currentRoute != Screen.BugReport.route &&
                            currentRoute != Screen.WatchHistory.route &&
                            currentRoute != Screen.Legal.route &&
                            currentRoute != Screen.Television.route
                        ) {
                            IconButton(onClick = { 
                                navController.navigate(Screen.MovieSearch.route)
                            }) {
                                Icon(painterResource(id = R.drawable.ic_search), contentDescription = "Search")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF26A69A),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
    ) { innerPadding ->
        // Full-screen routes should draw behind the status bar (no top padding)
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isFullScreenRoute = currentRoute == Screen.MovieDetail.route ||
            currentRoute == Screen.CategoryMovies.route ||
            currentRoute == Screen.Extensions.route ||
            currentRoute == Screen.MovieSearch.route

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (isFullScreenRoute) {
                // No top padding – content extends behind the transparent status bar
                // Full-screen routes are always full-width so start/end = 0
                Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            } else {
                Modifier.padding(innerPadding)
            }
        ) {
            composable(Screen.Home.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                HomeScreen(
                    viewModel = channelsViewModel,
                    homeViewModel = homeViewModel,
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
            composable(Screen.Television.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                TelevisionScreen(
                    viewModel = channelsViewModel,
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
                MoviesScreen(
                    viewModel = moviesViewModel,
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
                    onNavigateToAbout = { navController.navigate(Screen.About.route) },
                    onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                    onNavigateToBugReport = { navController.navigate(Screen.BugReport.route) },
                    onNavigateToWatchHistory = { navController.navigate(Screen.WatchHistory.route) },
                    onNavigateToLegal = { docType -> navController.navigate("legal?docType=$docType") }
                ) 
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    navController = navController
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
            composable(Screen.BugReport.route) { BugReportScreen() }
            composable(Screen.WatchHistory.route) {
                WatchHistoryScreen(
                    navController = navController,
                    onClearClickRegistered = { onWatchHistoryClearClick = it }
                )
            }
            composable(
                route = Screen.Legal.route,
                arguments = listOf(
                    navArgument("docType") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "privacy"
                    }
                )
            ) { backStackEntry ->
                val docType = backStackEntry.arguments?.getString("docType") ?: "privacy"
                LegalDocumentScreen(docType = docType)
            }
            composable(Screen.MovieSearch.route) {
                MovieSearchScreen(
                    navController = navController,
                    onMovieClick = { post, scraper, provider ->
                        val encodedLink = android.net.Uri.encode(post.link)
                        val encodedProviderUrl = android.net.Uri.encode(provider.url)
                        val scraperValue = scraper.value
                        navController.navigate("movie_detail?link=$encodedLink&providerUrl=$encodedProviderUrl&scraperValue=$scraperValue")
                    }
                )
            }
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
            composable("pairing") {
                PairingScreen(
                    onNavigateBack = { navController.popBackStack() }
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
            composable(Screen.AddProviderHelp.route) {
                AddProviderHelpScreen()
            }
        }
    }
    }
    }
}
