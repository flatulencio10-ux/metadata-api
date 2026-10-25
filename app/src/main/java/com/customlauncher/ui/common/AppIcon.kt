package com.customlauncher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.customlauncher.container
import com.customlauncher.core.model.IconShape
import com.customlauncher.data.apps.AppInfo
import com.customlauncher.ui.theme.LocalLauncherTheme
import com.customlauncher.ui.theme.toShape

/** Installed apps with overrides applied, available anywhere in the UI tree. */
@Immutable
data class AppCatalog(val apps: List<AppInfo> = emptyList()) {
    val byKey: Map<String, AppInfo> = apps.associateBy { it.key }
    fun label(key: String): String = byKey[key]?.label ?: key.substringBefore('/')
}

val LocalAppCatalog = compositionLocalOf { AppCatalog() }

/**
 * App icon honouring the theme: icon pack, per-app override, and icon shape clipping.
 * Loading happens off the main thread; the in-memory cache makes re-composition free.
 */
@Composable
fun AppIcon(appKey: String, size: Dp, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    val app = LocalAppCatalog.current.byKey[appKey]
    val loader = LocalContext.current.container.iconLoader
    val px = with(LocalDensity.current) { size.roundToPx() }
    val masked = theme.iconShape == IconShape.NONE
    val overridePath = app?.customIconPath

    val icon by produceState(
        initialValue = loader.cached(appKey, theme.iconPack, overridePath, px, masked),
        appKey, theme.iconPack, overridePath, px, masked,
    ) {
        value = loader.load(appKey, theme.iconPack, overridePath, px, masked)
    }

    val loaded = icon
    if (loaded == null) {
        Box(modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
    } else {
        val clip = if (loaded.shapeable && !masked) Modifier.clip(theme.iconShape.toShape()) else Modifier
        Image(bitmap = loaded.bitmap, contentDescription = app?.label, modifier = modifier.size(size).then(clip))
    }
}
