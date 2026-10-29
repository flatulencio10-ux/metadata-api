package com.customlauncher.ui.stickers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.customlauncher.container
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.launch

/**
 * Share target: "Add to home screen as sticker" in any app's share sheet (Chrome, Gallery…).
 * Imports the image into the sticker library and places it on the current home page.
 */
class ShareStickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) {
            finish(); return
        }
        val c = applicationContext.container
        val app = applicationContext
        c.appScope.launch {
            val result = runCatching { c.stickerRepository.import(uri) }
            result.onSuccess { asset ->
                val aspect = if (asset.height > 0) asset.width.toFloat() / asset.height else 1f
                val (w, h) = if (aspect >= 1f) 160f to 160f / aspect else 160f * aspect to 160f
                c.profileRepository.update { p ->
                    p.updateCurrentActivity { act ->
                        val index = act.defaultPage.coerceIn(0, act.pages.lastIndex)
                        act.copy(pages = act.pages.mapIndexed { i, page ->
                            if (i != index) page else page.copy(widgets = page.widgets + WidgetInstance(
                                type = StickerWidget.id, x = 100f, y = 200f, width = w, height = h,
                                z = (page.widgets.maxOfOrNull { it.z } ?: 0) + 1,
                                style = WidgetStyle(useCardStyle = false, backgroundColor = 0x00000000, paddingDp = 0f),
                                config = StickerWidget.configFor(asset).json,
                            ))
                        })
                    }
                }
            }
            Toast.makeText(app, result.fold({ "Added to your home screen" }, { it.message ?: "Couldn't add sticker" }), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
