package com.customlauncher.data.profile

import android.content.Context
import android.net.Uri
import com.customlauncher.core.model.LayoutProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Shareable setup archive: `layout.json` plus the files the layout references (stickers,
 * wallpapers, custom icons, imported fonts). Plain `.json` files still import (layout only).
 */
class ProfileArchive(private val context: Context, private val profiles: ProfileRepository) {

    /** App-storage folders that travel with an export. */
    private val folders = listOf("stickers", "wallpapers", "icons", "fonts")

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val json = ProfileCodec.encode(profiles.current())
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri for writing")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(LAYOUT))
            zip.write(json.encodeToByteArray())
            zip.closeEntry()
            folders.forEach { folder ->
                File(context.filesDir, folder).listFiles().orEmpty().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry("$folder/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Imports a .zip archive or a bare layout .json. Throws with a readable message. */
    suspend fun import(uri: Uri): LayoutProfile = withContext(Dispatchers.IO) {
        val head = context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(2).also { s.read(it) } }
            ?: error("Cannot open $uri")
        if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) return@withContext profiles.importFrom(uri)

        var layout: String? = null
        val base = context.filesDir.canonicalFile
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == LAYOUT -> layout = zip.readBytes().decodeToString()
                        folders.any { entry.name.startsWith("$it/") } -> {
                            val target = File(base, entry.name).canonicalFile
                            // Zip-slip protection: never write outside the app's files dir.
                            require(target.path.startsWith(base.path + File.separator)) { "Invalid archive entry" }
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val text = layout ?: throw IllegalArgumentException("Not a launcher archive (layout.json missing)")
        val profile = try {
            ProfileCodec.decode(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid launcher profile: ${e.message}", e)
        }
        profiles.replace(profile)
    }

    companion object { const val LAYOUT = "layout.json" }
}
