package com.vknxworks.memoctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.memoctrl.PendingAction
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.ui.auth.SignInScreen
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.LocalHazeState
import com.vknxworks.memoctrl.ui.editor.EditorScreen
import com.vknxworks.memoctrl.ui.explore.ExploreScreen
import com.vknxworks.memoctrl.ui.memos.MemoDetailScreen
import com.vknxworks.memoctrl.ui.memos.MemosScreen
import com.vknxworks.memoctrl.ui.memos.TagMemosScreen
import com.vknxworks.memoctrl.ui.search.SearchScreen
import com.vknxworks.memoctrl.ui.settings.AccountsScreen
import com.vknxworks.memoctrl.ui.settings.ArchivedScreen
import com.vknxworks.memoctrl.ui.settings.AttachmentsScreen
import com.vknxworks.memoctrl.ui.settings.SettingsScreen
import com.vknxworks.memoctrl.ui.settings.YouScreen
import com.vknxworks.memoctrl.ui.tags.TagsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val ready by c.sessions.ready.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !ready -> LoadingView()
            active == null -> SignInScreen(onDone = {}, onCancel = null)
            else -> key(active!!.account.key) {
                // A first sync for this account; later ones happen whenever the app comes back.
                LaunchedEffect(Unit) { c.syncInBackground() }
                MainScaffold()
            }
        }
    }
}

private data class TopDest(val route: Any, val item: TabItem)

private val topDestinations = listOf(
    TopDest(MemosRoute, TabItem(R.string.tab_memos, Icons.Outlined.GridView, Icons.Filled.GridView)),
    TopDest(TagsRoute, TabItem(R.string.tab_tags, Icons.Outlined.Tag, Icons.Filled.Tag)),
    TopDest(ExploreRoute, TabItem(R.string.tab_explore, Icons.Outlined.Public, Icons.Filled.Public)),
    TopDest(SearchRoute, TabItem(R.string.tab_search, Icons.Outlined.Search, Icons.Filled.Search)),
)

@Composable
private fun MainScaffold() {
    val c = LocalContext.current.container
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Shares from other apps, launcher shortcuts and widget taps.
    val pending by c.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pending, backStack != null) {
        val action = pending ?: return@LaunchedEffect
        if (backStack == null) return@LaunchedEffect
        when (action) {
            PendingAction.NewMemo -> navigator.navigateNow(EditorRoute())
            is PendingAction.Share -> navigator.navigateNow(EditorRoute(share = true))
            is PendingAction.ViewMemo -> navigator.navigateNow(MemoRoute(action.identifier))
            is PendingAction.EditMemo -> navigator.navigateNow(EditorRoute(action.identifier))
        }
        // A share is picked up by the editor itself; everything else is done.
        if (action !is PendingAction.Share) c.pendingAction.value = null
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, top ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.item.selectedIcon else top.item.icon, contentDescription = stringResource(top.item.label)) },
                                label = { Text(stringResource(top.item.label)) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Pushed screens stop above the navigation bar; the tabs scroll under the glass island.
            Box(Modifier.fillMaxSize().hazeSource(hazeState).then(if (showTabs) Modifier else Modifier.navigationBarsPadding())) {
                AppNavHost(navController)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    GlassTabBar(items = topDestinations.map { it.item }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = MemosRoute) {
        composable<MemosRoute> { MemosScreen() }
        composable<TagsRoute> { TagsScreen() }
        composable<ExploreRoute> { ExploreScreen() }
        composable<SearchRoute> { SearchScreen() }

        composable<MemoRoute> { MemoDetailScreen(it.toRoute<MemoRoute>().id) }
        composable<TagRoute> { TagMemosScreen(it.toRoute<TagRoute>().tag) }
        composable<EditorRoute> {
            val r = it.toRoute<EditorRoute>()
            EditorScreen(memoId = r.id, fromShare = r.share, initialText = r.text)
        }

        composable<YouRoute> { YouScreen() }
        composable<ArchivedRoute> { ArchivedScreen() }
        composable<AttachmentsRoute> { AttachmentsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<AccountsRoute> { AccountsScreen() }
        composable<AddAccountRoute> {
            val navigator = LocalNavigator.current
            SignInScreen(onDone = { navController.popBackStack(MemosRoute, false) }, onCancel = navigator::back)
        }
    }
}
