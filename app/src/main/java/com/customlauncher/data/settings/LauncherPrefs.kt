package com.customlauncher.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "launcher_prefs")

/** Device-local flags that must NOT travel with an exported profile. */
class LauncherPrefs(private val context: Context) {
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    val onboardingDone: Flow<Boolean> = context.prefsStore.data.map { it[onboardingKey] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.prefsStore.edit { it[onboardingKey] = done }
    }
}
