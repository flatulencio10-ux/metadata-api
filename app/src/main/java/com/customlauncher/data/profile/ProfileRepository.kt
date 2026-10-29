package com.customlauncher.data.profile

import android.content.Context
import android.net.Uri
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.customlauncher.core.model.LayoutProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/**
 * Owns the single JSON "layout profile". All UI edits go through [edit], which is atomic
 * (DataStore serialises updates), so concurrent edits from widgets and Edit Mode are safe.
 */
class ProfileRepository(private val context: Context, private val appScope: CoroutineScope) {

    private class JsonProfileSerializer(private val defaults: () -> LayoutProfile) : Serializer<LayoutProfile> {
        override val defaultValue: LayoutProfile get() = defaults()

        override suspend fun readFrom(input: InputStream): LayoutProfile = try {
            ProfileCodec.decode(input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable layout profile", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Invalid layout profile", e)
        }

        override suspend fun writeTo(t: LayoutProfile, output: OutputStream) {
            output.write(ProfileCodec.encode(t).encodeToByteArray())
        }
    }

    private val store: DataStore<LayoutProfile> = DataStoreFactory.create(
        serializer = JsonProfileSerializer { DefaultProfile.create(context) },
        corruptionHandler = ReplaceFileCorruptionHandler { DefaultProfile.create(context) },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("layout_profile.json") },
    )

    /** Null until the file has been read once. */
    val profile: StateFlow<LayoutProfile?> = store.data.stateIn(appScope, SharingStarted.Eagerly, null)

    suspend fun current(): LayoutProfile = profile.value ?: store.data.first()

    suspend fun update(transform: (LayoutProfile) -> LayoutProfile): LayoutProfile =
        store.updateData { ProfileCodec.normalize(transform(it)) }

    /** Fire-and-forget variant for UI callbacks. */
    fun edit(transform: (LayoutProfile) -> LayoutProfile) {
        appScope.launch { update(transform) }
    }

    /** Writes the current profile as pretty JSON to a SAF document. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = ProfileCodec.encode(current())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToByteArray()) }
            ?: error("Cannot open $uri for writing")
    }

    /** Reads, validates and activates a profile JSON. Throws with a readable message on error. */
    suspend fun importFrom(uri: Uri): LayoutProfile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open $uri")
        val imported = try {
            ProfileCodec.decode(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid launcher profile: ${e.message}", e)
        }
        store.updateData { imported }
    }

    suspend fun replace(profile: LayoutProfile): LayoutProfile = store.updateData { ProfileCodec.normalize(profile) }

    suspend fun resetToDefault() {
        store.updateData { DefaultProfile.create(context) }
    }
}
