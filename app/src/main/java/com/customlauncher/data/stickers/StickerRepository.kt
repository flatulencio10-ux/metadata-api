package com.customlauncher.data.stickers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.customlauncher.core.json.LauncherJson
import com.customlauncher.core.model.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** One imported sticker file in the library. Widgets reference it by [file] name. */
@Serializable
data class StickerAsset(
    val id: String,
    /** File name inside files/stickers/. */
    val file: String,
    val kind: StickerKind,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val favorite: Boolean = false,
    val addedAt: Long = 0,
)

/**
 * Sticker files live in app storage (files/stickers/) so they survive the original being
 * deleted. The library index (favorites, names) is device-local, next to the files.
 */
class StickerRepository(private val context: Context, private val scope: CoroutineScope) {

    val dir: File = File(context.filesDir, "stickers").apply { mkdirs() }
    private val indexFile = File(dir, "library.json")
    private val mutex = Mutex()
    private val serializer = ListSerializer(StickerAsset.serializer())

    private val _library = MutableStateFlow<List<StickerAsset>>(emptyList())
    val library: StateFlow<List<StickerAsset>> = _library.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { mutex.withLock { _library.value = loadIndex() } }
    }

    fun fileOf(name: String): File = File(dir, name)

    fun find(file: String): StickerAsset? = _library.value.firstOrNull { it.file == file }

    /** Copies any image/animation/SVG/Lottie URI into the library. Throws with a readable message. */
    suspend fun import(uri: Uri, name: String = ""): StickerAsset = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Can't open that file")
        importBytes(bytes, name)
    }

    suspend fun importBytes(bytes: ByteArray, name: String = ""): StickerAsset = withContext(Dispatchers.IO) {
        val detected = StickerFormat.detect(bytes.copyOf(minOf(bytes.size, 4096)))
            ?: throw IllegalArgumentException("Unsupported format (use PNG, WebP, JPG, GIF, APNG, SVG or Lottie JSON)")
        val id = newId()
        val file = "$id.${detected.extension}"
        fileOf(file).writeBytes(bytes)
        val (w, h) = measure(fileOf(file), detected.kind, bytes)
        val asset = StickerAsset(id, file, detected.kind, name, w, h, addedAt = System.currentTimeMillis())
        mutex.withLock {
            _library.value = _library.value + asset
            saveIndex()
        }
        asset
    }

    /** Re-saves edited pixels (background removal, cut-outs) as a new PNG asset. */
    suspend fun saveEdited(png: ByteArray, from: StickerAsset?): StickerAsset =
        importBytes(png, from?.name?.let { "$it (edited)" } ?: "")

    suspend fun update(id: String, transform: (StickerAsset) -> StickerAsset) = mutex.withLock {
        _library.value = _library.value.map { if (it.id == id) transform(it) else it }
        withContext(Dispatchers.IO) { saveIndex() }
    }

    suspend fun delete(id: String) = mutex.withLock {
        val asset = _library.value.firstOrNull { it.id == id } ?: return@withLock
        _library.value = _library.value - asset
        withContext(Dispatchers.IO) {
            fileOf(asset.file).delete()
            saveIndex()
        }
    }

    /** Rebuilds the index from files on disk, keeping known metadata (used after a zip import). */
    suspend fun rescan() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val known = loadIndex().associateBy { it.file }
            val assets = dir.listFiles().orEmpty()
                .filter { it.isFile && it.name != indexFile.name }
                .mapNotNull { f ->
                    known[f.name] ?: run {
                        val bytes = f.readBytes()
                        val kind = StickerFormat.detect(bytes.copyOf(minOf(bytes.size, 4096)))?.kind ?: return@mapNotNull null
                        val (w, h) = measure(f, kind, bytes)
                        StickerAsset(f.nameWithoutExtension, f.name, kind, width = w, height = h, addedAt = f.lastModified())
                    }
                }
            _library.value = assets
            saveIndex()
        }
    }

    private fun measure(file: File, kind: StickerKind, bytes: ByteArray): Pair<Int, Int> = when (kind) {
        StickerKind.BITMAP, StickerKind.ANIMATED -> {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            o.outWidth.coerceAtLeast(1) to o.outHeight.coerceAtLeast(1)
        }
        StickerKind.SVG -> svgSize(bytes.decodeToString())
        StickerKind.LOTTIE -> {
            val text = bytes.decodeToString()
            fun num(key: String) = Regex("\"$key\"\\s*:\\s*([0-9.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
            (num("w") ?: 512) to (num("h") ?: 512)
        }
    }

    private fun svgSize(svg: String): Pair<Int, Int> {
        val viewBox = Regex("viewBox\\s*=\\s*\"[-0-9.]+[ ,]+[-0-9.]+[ ,]+([0-9.]+)[ ,]+([0-9.]+)\"").find(svg)
        if (viewBox != null) {
            return viewBox.groupValues[1].toFloat().toInt().coerceAtLeast(1) to viewBox.groupValues[2].toFloat().toInt().coerceAtLeast(1)
        }
        fun attr(n: String) = Regex("$n\\s*=\\s*\"([0-9.]+)").find(svg)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
        return (attr("width") ?: 256) to (attr("height") ?: 256)
    }

    private fun loadIndex(): List<StickerAsset> = runCatching {
        if (indexFile.exists()) LauncherJson.decodeFromString(serializer, indexFile.readText()) else emptyList()
    }.onFailure { Log.w("StickerRepository", "Bad sticker index", it) }.getOrDefault(emptyList())

    private fun saveIndex() {
        indexFile.writeText(LauncherJson.encodeToString(serializer, _library.value))
    }
}
