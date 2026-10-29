package com.customlauncher.data.stickers

import kotlinx.serialization.Serializable

/** How a sticker file is decoded and drawn. */
@Serializable
enum class StickerKind { BITMAP, ANIMATED, SVG, LOTTIE }

/**
 * Detects a sticker's format from its first bytes (MIME types from other apps are often
 * wrong or missing). Framework-free so it is unit tested.
 */
object StickerFormat {

    data class Detected(val kind: StickerKind, val extension: String)

    fun detect(head: ByteArray): Detected? {
        fun at(i: Int, text: String) = head.size >= i + text.length &&
            text.indices.all { head[i + it] == text[it].code.toByte() }

        return when {
            // PNG; animated if an acTL chunk appears before the image data.
            head.size > 8 && head[0] == 0x89.toByte() && at(1, "PNG") ->
                Detected(if (containsAscii(head, "acTL")) StickerKind.ANIMATED else StickerKind.BITMAP, "png")
            head.size > 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> Detected(StickerKind.BITMAP, "jpg")
            at(0, "GIF8") -> Detected(StickerKind.ANIMATED, "gif")
            at(0, "RIFF") && at(8, "WEBP") ->
                // VP8X header with the animation flag (bit 1 of byte 20).
                Detected(if (at(12, "VP8X") && head.size > 20 && (head[20].toInt() and 0x02) != 0) StickerKind.ANIMATED else StickerKind.BITMAP, "webp")
            else -> {
                val text = head.decodeToString().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                when {
                    text.startsWith("<") && text.contains("<svg", ignoreCase = true) -> Detected(StickerKind.SVG, "svg")
                    text.startsWith("{") && text.contains("\"layers\"") && text.contains("\"v\"") -> Detected(StickerKind.LOTTIE, "json")
                    else -> null
                }
            }
        }
    }

    private fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        val n = needle.encodeToByteArray()
        outer@ for (i in 0..bytes.size - n.size) {
            for (j in n.indices) if (bytes[i + j] != n[j]) continue@outer
            return true
        }
        return false
    }
}
