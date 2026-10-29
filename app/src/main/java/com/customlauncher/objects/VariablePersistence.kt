package com.customlauncher.objects

import android.content.Context
import com.customlauncher.core.json.CompactJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Keeps behaviour variables (tap counters…) across restarts in files/variables.json. */
object VariablePersistence {
    private val serializer = MapSerializer(String.serializer(), Double.serializer())

    fun load(context: Context, scope: CoroutineScope): VariableStore {
        val file = File(context.filesDir, "variables.json")
        val initial = runCatching { CompactJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
        var pending: Job? = null
        return VariableStore(initial) { snapshot ->
            // Debounced write: counters may change many times a second.
            pending?.cancel()
            pending = scope.launch(Dispatchers.IO) {
                delay(1000)
                runCatching { file.writeText(CompactJson.encodeToString(serializer, snapshot)) }
            }
        }
    }
}
