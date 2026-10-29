package com.customlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customlauncher.actions.UiCommand
import com.customlauncher.container
import com.customlauncher.gestures.GestureCanvas
import com.customlauncher.ui.common.AppCatalog
import com.customlauncher.ui.common.AppPickerDialog
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.drawer.AppDrawer
import com.customlauncher.ui.edit.EditOverlay
import com.customlauncher.ui.edit.WidgetConfigDialog
import com.customlauncher.ui.home.FullscreenWidget
import com.customlauncher.ui.home.HomeScreen
import com.customlauncher.ui.home.LocalRootSize
import com.customlauncher.ui.onboarding.OnboardingScreen
import com.customlauncher.ui.search.SearchOverlay
import com.customlauncher.ui.stickers.StickerEditor
import com.customlauncher.ui.stickers.StickerSourceSheet
import com.customlauncher.ui.theme.LauncherTheme

/** Top-level composable of the home screen: themes, overlays and command handling. */
@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val profile = vm.profile.collectAsStateWithLifecycle().value ?: return
    val apps by vm.apps.collectAsStateWithLifecycle()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val catalog = remember(apps) { AppCatalog(apps) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val bus = LocalContext.current.container.bus
    val snackbar = remember { SnackbarHostState() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    val activity = profile.currentActivity
    val pagerState = key(activity.id) {
        rememberPagerState(initialPage = activity.defaultPage.coerceIn(0, activity.pages.lastIndex)) { activity.pages.size }
    }

    // Commands from actions / services that need the UI.
    LaunchedEffect(pagerState) {
        bus.commands.collect { cmd ->
            when (cmd) {
                UiCommand.OpenSearch -> { vm.closeOverlays(); vm.searchOpen = true }
                UiCommand.OpenDrawer -> { vm.closeOverlays(); vm.drawerOpen = true }
                UiCommand.EnterEditMode -> vm.enterEditMode()
                UiCommand.OpenGestureCanvas -> { vm.closeOverlays(); vm.gestureCanvasOpen = true }
                UiCommand.NextPage -> pagerState.animateScrollToPage((pagerState.currentPage + 1) % pagerState.pageCount.coerceAtLeast(1))
                UiCommand.PreviousPage -> pagerState.animateScrollToPage(
                    (pagerState.currentPage - 1 + pagerState.pageCount).mod(pagerState.pageCount.coerceAtLeast(1))
                )
                is UiCommand.OpenWidget -> { vm.closeOverlays(); vm.fullscreenWidgetId = cmd.instanceId }
                is UiCommand.Message -> snackbar.showSnackbar(cmd.text)
            }
        }
    }
    LaunchedEffect(vm.homePressTick) {
        if (vm.homePressTick > 0) pagerState.animateScrollToPage(activity.defaultPage.coerceIn(0, activity.pages.lastIndex))
    }

    BackHandler(enabled = vm.editMode || vm.drawerOpen || vm.searchOpen || vm.gestureCanvasOpen || vm.fullscreenWidgetId != null) {
        if (!vm.closeOverlays()) vm.exitEditMode()
    }

    LauncherTheme(profile.theme) {
        CompositionLocalProvider(LocalAppCatalog provides catalog, LocalRootSize provides rootSize) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSize = it }
            ) {
                HomeScreen(profile, vm, pagerState, resumed)
                if (vm.editMode) EditOverlay(profile, vm, pagerState)

                AnimatedVisibility(
                    visible = vm.drawerOpen,
                    enter = slideInVertically { it / 3 } + fadeIn(),
                    exit = slideOutVertically { it / 3 } + fadeOut(),
                ) { AppDrawer(profile, vm) }

                AnimatedVisibility(
                    visible = vm.searchOpen,
                    enter = slideInVertically { -it / 4 } + fadeIn(),
                    exit = fadeOut(),
                ) { SearchOverlay(profile, vm) }

                if (vm.gestureCanvasOpen) {
                    GestureCanvas(profile.gestures, onAction = vm::run, onClose = { vm.gestureCanvasOpen = false })
                }
                vm.fullscreenWidgetId?.let { FullscreenWidget(profile, it, vm) }
                vm.configuringWidgetId?.let { WidgetConfigDialog(profile, vm, it) }
                if (vm.stickerSourceSheetOpen) StickerSourceSheet(vm)
                vm.stickerEditorTarget?.let { id ->
                    val node = profile.findWidget(id)
                    if (node == null) vm.stickerEditorTarget = null else StickerEditor(node, vm)
                }
                vm.panelAddTargetId?.let { panelId ->
                    AppPickerDialog("Add apps to panel", multi = true, onDismiss = { vm.panelAddTargetId = null }) { keys ->
                        vm.addAppsToPanel(panelId, keys)
                        vm.panelAddTargetId = null
                    }
                }

                SnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 80.dp)
                )

                if (onboardingDone == false) OnboardingScreen(onFinish = { vm.completeOnboarding() })
            }
        }
    }
}
