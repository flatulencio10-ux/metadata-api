package com.customlauncher.widgets.sticker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.data.stickers.StickerAsset
import com.customlauncher.data.stickers.StickerKind
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * A free-form image: no card, border, padding or clip; transparent pixels stay transparent
 * and let touches through. Supports PNG/WebP/JPG, animated GIF/WebP/APNG, SVG and Lottie.
 */
object StickerWidget : LauncherWidget {
    override val id = "sticker"
    override val name = "Sticker"
    override val description = "Any image, GIF, SVG or Lottie animation, placed freely"
    override val icon = Icons.Filled.EmojiEmotions
    override val defaultSize = DpSize(160.dp, 160.dp)
    override val minSize = DpSize(16.dp, 16.dp)
    override val chromeless = true

    const val FILE = "file"
    const val KIND = "kind"
    /** JSON-encoded [Action] run when the sticker is tapped. */
    const val TAP_ACTION = "tapAction"

    override val configSchema = listOf<ConfigField>(
        ConfigField.Info("info", "Edit the image, effects and animations from the sticker's Edit button in Edit Mode."),
    )

    fun configFor(asset: StickerAsset) = WidgetConfig().with(FILE, asset.file).with(KIND, asset.kind.name)

    fun kindOf(config: WidgetConfig) =
        runCatching { StickerKind.valueOf(config.string(KIND)) }.getOrDefault(StickerKind.BITMAP)

    fun tapAction(config: WidgetConfig): Action =
        (config.json[TAP_ACTION] as JsonElement?)?.let {
            runCatching { Json.decodeFromJsonElement(Action.serializer(), it) }.getOrNull()
        } ?: Action.None

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val context = LocalContext.current
        val host = LocalWidgetHost.current
        val file = remember(config) { context.container.stickerRepository.fileOf(config.string(FILE)) }
        val kind = kindOf(config)
        if (!file.exists()) {
            Box(Modifier.fillMaxSize()) { Text("Missing sticker") }
            return
        }
        val mask by produceState<AlphaMask?>(null, file.path) {
            value = if (kind == StickerKind.BITMAP || kind == StickerKind.ANIMATED) withContext(Dispatchers.IO) { AlphaMask.load(file) } else null
        }
        val action = remember(config) { tapAction(config) }
        val tappable = !host.isEditing

        Box(Modifier.fillMaxSize().alphaTappable(mask, tappable) { if (action != Action.None) host.runAction(action) }) {
            StickerImage(file, kind, size, playing = host.isActive, modifier = Modifier.fillMaxSize())
        }
    }

    /** Draws just the image, decoded at the displayed size (not full resolution). */
    @Composable
    fun StickerImage(file: java.io.File, kind: StickerKind, size: DpSize, playing: Boolean, modifier: Modifier = Modifier, iterations: Int = LottieConstants.IterateForever) {
        val context = LocalContext.current
        val density = LocalDensity.current
        when (kind) {
            StickerKind.LOTTIE -> {
                val composition by rememberLottieComposition(LottieCompositionSpec.File(file.path))
                LottieAnimation(composition, modifier = modifier, iterations = iterations, isPlaying = playing)
            }
            else -> {
                val px = with(density) { maxOf(size.width.roundToPx(), size.height.roundToPx(), 1) }
                val request = remember(file.path, kind, px, playing) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .size(px)
                        .apply {
                            when (kind) {
                                StickerKind.SVG -> decoderFactory(SvgDecoder.Factory())
                                // Off screen, decode the first frame only so nothing animates.
                                StickerKind.ANIMATED -> if (playing) decoderFactory(ImageDecoderDecoder.Factory())
                                else -> Unit
                            }
                        }
                        .build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
            }
        }
    }
}
